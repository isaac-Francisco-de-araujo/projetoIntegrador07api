/* Fluxo de navegador exclusivamente sintético; requer playwright disponível no Node. */
'use strict';
const { chromium, expect } = require('playwright/test');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');
const args = process.argv.slice(2);
const jar = path.resolve(args[0] || '');
const output = path.resolve(args[1] || '');
if (args.length < 2) throw new Error('Uso: node scripts/testar-envio-manual.cjs JAR SAIDA_EXTERNA');
fs.mkdirSync(output, { recursive: true });
const root = path.resolve(__dirname, '..');
const base = fs.mkdtempSync(path.join(os.tmpdir(), 'rotabrasil-email-ui-'));
const log = fs.openSync(path.join(output, 'servidor.log'), 'w');
const checks = [];
const errors = [];
const sleep = ms => new Promise(r => setTimeout(r, ms));

// ZIP sem compressão, CRC válido; nenhum arquivo real é lido.
function zip(entries) {
  const locals = [], centrals = []; let offset = 0;
  for (const [name, text] of entries) {
    const data = Buffer.from(text), filename = Buffer.from(name);
    let crc = 0xffffffff;
    for (const b of data) { crc ^= b; for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0); }
    crc = (crc ^ 0xffffffff) >>> 0;
    const local = Buffer.alloc(30); local.writeUInt32LE(0x04034b50); local.writeUInt16LE(20, 4);
    local.writeUInt32LE(crc, 14); local.writeUInt32LE(data.length, 18); local.writeUInt32LE(data.length, 22); local.writeUInt16LE(filename.length, 26);
    const central = Buffer.alloc(46); central.writeUInt32LE(0x02014b50); central.writeUInt16LE(20, 4); central.writeUInt16LE(20, 6);
    central.writeUInt32LE(crc, 16); central.writeUInt32LE(data.length, 20); central.writeUInt32LE(data.length, 24); central.writeUInt16LE(filename.length, 28); central.writeUInt32LE(offset, 42);
    locals.push(local, filename, data); centrals.push(central, filename); offset += local.length + filename.length + data.length;
  }
  const central = Buffer.concat(centrals), end = Buffer.alloc(22); end.writeUInt32LE(0x06054b50);
  end.writeUInt16LE(entries.length, 8); end.writeUInt16LE(entries.length, 10); end.writeUInt32LE(central.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, central, end]);
}

let processServer, browser, page;
const requisicoes=[];
async function start(extra = []) {
  processServer = spawn(process.env.JAVA || 'java', ['-Xmx512m','-jar', jar, '--server.port=0',
    '--spring.datasource.url=jdbc:h2:file:' + base.replaceAll('\\', '/') + '/banco;WRITE_DELAY=0',
    '--spring.flyway.enabled=true', '--spring.h2.console.enabled=false',
    '--rotabrasil.arquivos.viagens=' + base + '/viagens', ...extra], { cwd: root, stdio: ['ignore', log, log] });
  for (let i = 0; i < 600; i++) {
    const matches = [...fs.readFileSync(path.join(output, 'servidor.log'), 'utf8').matchAll(/Tomcat started on port (\d+)/g)];
    if (matches.length) {
      const url = 'http://127.0.0.1:' + matches.at(-1)[1];
      try { const resposta=await fetch(url+'/api/sessao', { signal: AbortSignal.timeout(1000) }); if ([200,401].includes(resposta.status)) return url; } catch {}
    }
    if (processServer.exitCode !== null || processServer.signalCode !== null) throw new Error('Servidor terminou: confira o log sintético externo');
    await sleep(100);
  }
  throw new Error('Servidor não ficou disponível em 60 segundos');
}
async function main() {
  let url = await start();
  browser = await chromium.launch({ ...(process.env.ROTABRASIL_BROWSER_CHANNEL==='chromium'?{}:{channel:'chrome'}), headless: true });
  page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, locale: 'pt-BR' });
  page.on('request',r=>{if(r.url().includes('/api/'))requisicoes.push({metodo:r.method(),url:r.url(),evento:'solicitada'});});
  page.on('response',r=>{if(r.url().includes('/api/'))requisicoes.push({metodo:r.request().method(),url:r.url(),status:r.status()});});
  page.on('requestfailed',r=>{if(r.url().includes('/api/'))requisicoes.push({url:r.url(),falha:r.failure()?.errorText});});
  async function navegar(id){await page.evaluate(hash=>{location.hash=hash;},id);await page.waitForFunction(route=>document.querySelector('main').dataset.paginaAtual===route,id==='documentos'?'viagens':id);}
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(url, { waitUntil: 'networkidle' });
  await page.locator('#abertura').waitFor({state:'hidden'});await navegar('pacotes');
  await expect(page.locator('#recuperar-confirmacao')).toBeHidden();
  const xml = fs.readFileSync(path.join(root, 'src/main/resources/email/cte-sintetico.xml'), 'utf8');
  const chave = '35250412345678000195570020000662131001747719';
  const nova = chave.slice(0, 25) + '000066214' + chave.slice(34, 43);
  let soma = 0; [...nova].reverse().forEach((c, i) => { soma += Number(c) * (2 + i % 8); });
  const dv = 11 - soma % 11;
  const segundo = xml.replaceAll(chave, nova + (dv >= 10 ? 0 : dv)).replace('<nCT>66213</nCT>', '<nCT>66214</nCT>');
  const entradas = [['ctes/cte-1.xml', xml], ['ctes/cte-2.xml', segundo], ['cobrancas/fatura-compartilhada.pdf', 'invalido'], ['viagem-a/recibo.pdf', '%PDF-recibo-a'], ['viagem-b/recibo.pdf', '%PDF-recibo-b']];
  await page.locator('#arquivo-zip').setInputFiles({ name: 'documentos.zip', mimeType: 'application/zip', buffer: zip(entradas) });
  await page.locator('#inspecionar-zip').click();
  await expect(page.locator('#resumo-zip')).toContainText('Conferência com pendências');
  await expect(page.locator('#diagnosticos-zip > li')).toHaveCount(5);
  await expect(page.locator('#total-viagens')).toHaveText('0');
  await expect(page.locator('#confirmar-zip')).toBeDisabled();
  await page.locator('#filtro-diagnostico').selectOption('erros');
  await expect(page.locator('#diagnosticos-zip > li')).toHaveCount(1);
  await expect(page.locator('#diagnosticos-zip')).toContainText('documentos.zip > cobrancas/fatura-compartilhada.pdf');
  await expect(page.locator('#diagnosticos-zip')).toContainText('VALIDACAO_PDF');
  await expect(page.locator('#diagnosticos-zip')).toContainText('PDF_NAO_RECONHECIDO');
  await expect(page.locator('#diagnosticos-zip')).toContainText('obtenha uma cópia válida');
  await expect(page.locator('#localizacao-zip')).toContainText('Nenhum documento publicado');
  await page.screenshot({ path: path.join(output, 'erro-pdf.png'), fullPage: true });
  checks.push('ZIP com dois XMLs e três PDFs: erro identificado por arquivo, caminho, etapa, situação e orientação, sem importar parcialmente');
  await page.locator('#filtro-diagnostico').selectOption('pendentes');
  await expect(page.locator('#diagnosticos-zip > li')).toHaveCount(2);
  await expect(page.locator('#diagnosticos-zip')).toContainText('viagem-a/recibo.pdf');
  await expect(page.locator('#diagnosticos-zip')).toContainText('viagem-b/recibo.pdf');
  const operacaoAnterior = await page.locator('#resumo-zip').textContent();
  await page.route('**/api/pacotes/inspecao*', async route => { await sleep(700); await route.continue(); }, { times: 1 });
  const reconferido = page.waitForResponse(r=>r.url().includes('/api/pacotes/inspecao'));
  await page.locator('#reconferir-zip').click();
  await expect(page.locator('#diagnosticos-zip > li')).toHaveCount(2);
  await expect(page.locator('#confirmar-zip')).toBeDisabled();
  await reconferido;
  await expect(page.locator('#resumo-zip')).not.toHaveText(operacaoAnterior);
  checks.push('Filtros distinguem erros e pendências; reconferência mantém diagnóstico anterior visível durante a requisição');
  entradas[2][1] = '%PDF-1.4\n% sintetico\n%%EOF';
  const buffer = zip(entradas);
  await page.locator('#arquivo-zip').setInputFiles({ name: 'corrigido.zip', mimeType: 'application/zip', buffer });
  await expect(page.locator('#conferencia-zip')).toBeHidden();
  await expect(page.locator('#diagnosticos-zip > li')).toHaveCount(0);
  await expect(page.locator('#aviso')).toBeHidden();
  await page.locator('#inspecionar-zip').click();
  await expect(page.locator('#resumo-zip')).toContainText('2 viagem(ns) e 3 PDF(s)');
  await expect(page.locator('#confirmar-zip')).toBeDisabled();
  await page.locator('#vinculo-a2 select').selectOption('FATURA');
  for (const box of await page.locator('#vinculo-a2 input').all()) await box.check();
  await page.locator('#vinculo-a3 select').selectOption('VIAGEM');
  await page.locator('#vinculo-a3 input').first().check();
  await expect(page.locator('#confirmar-zip')).toBeDisabled();
  await page.locator('#vinculo-a4 select').selectOption('VIAGEM');
  await page.locator('#vinculo-a4 input').nth(1).check();
  await expect(page.locator('#confirmar-zip')).toBeEnabled();
  checks.push('Pacote corrigido descarta resultados/vínculos antigos e exige associação explícita de todos os PDFs');
  const importacaoConfirmada=page.waitForResponse(r=>r.url().includes('/api/pacotes/confirmacao')&&r.request().method()==='POST');
  await page.locator('#confirmar-zip').click();assert.equal((await importacaoConfirmada).status(),200);
  await expect(page.locator('#total-viagens')).toHaveText('2');
  await expect(page.locator('#diagnosticos-zip > li')).toHaveCount(5);
  await expect(page.locator('#resumo-zip')).toContainText('Importação confirmada');
  assert.equal((await (await page.request.get(url + '/api/documentos')).json()).length, 2);
  checks.push('ZIP com duas viagens e fatura compartilhada associada manualmente');
  const historicos = await (await page.request.get(url + '/api/processamentos')).json();
  const problema = historicos.find(p => p.SITUACAO === 'CONFERENCIA_COM_PENDENCIAS');
  assert(problema);
  await navegar('processamentos');await page.locator('#filtro-processamentos').selectOption('TODAS');await page.locator('#atualizar-processamentos').click();
  const linhaProblema = page.locator(`#lista-processamentos article[data-operacao-id="${problema.ID}"]`);
  await linhaProblema.locator(':scope > details > summary').click();
  await expect(linhaProblema).toContainText('cobrancas/fatura-compartilhada.pdf');
  await expect(linhaProblema).toContainText('PDF_NAO_RECONHECIDO');
  const escolha=page.waitForEvent('filechooser');await linhaProblema.getByRole('button', { name: 'Selecionar ZIP corrigido', exact: true }).click();
  await(await escolha).setFiles({ name: 'corrigido.zip', mimeType: 'application/zip', buffer });
  await expect(page.locator('#confirmar-zip')).toBeDisabled();
  await page.locator('#inspecionar-zip').click();
  await expect(page.locator('#confirmar-zip')).toBeEnabled();
  assert.equal(await page.locator('#vinculo-a2 input:checked').count(), 2);
  checks.push('Diagnóstico histórico consultável, correção explicitamente relacionada e rascunho recuperado somente para o mesmo SHA-256');
  await page.locator('#confirmar-zip').click();
  await expect(page.locator('#aviso')).toContainText('já registrado, sem duplicação');
  const correcao = (await (await page.request.get(url + '/api/processamentos')).json()).find(p => p.ANTERIOR_ID === problema.ID && p.FASE === 'CONFERENCIA');
  assert(correcao);
  assert.equal((await (await page.request.get(url + '/api/documentos')).json()).length, 2);
  const original = await page.request.get(url + '/api/documentos/1/arquivo');
  assert.equal(original.status(), 200); assert(original.headers()['content-disposition'].includes('attachment'));
  checks.push('Correção e repetição preservam os originais; download controlado fornece os bytes do documento');
  await page.route('**/api/pacotes/inspecao*', async route => { try { await sleep(1200); await route.continue(); } catch {} }, { times: 1 });
  await page.locator('#reconferir-zip').click();
  await expect(page.locator('#vinculo-a2 select')).toBeDisabled();
  await page.locator('#cancelar-conferencia').click();
  await expect(page.locator('#estado-conferencia-zip')).toContainText('interrompida');
  await expect(page.locator('#confirmar-zip')).toBeDisabled();
  checks.push('Conferência pode ser cancelada sem perder o diagnóstico; vínculos ficam bloqueados durante processamento');
  await navegar('documentos');await page.locator('#lista-documentos input').check();
  await page.locator('#lista-anexos input').first().check();
  await navegar('email');await page.locator('#destinatario').fill('conferencia@example.invalid');
  await page.locator('#gerar-previa').click();
  await expect(page.locator('#resultado-previa')).toContainText('FATURA COMPARTILHADA (2 viagens)');
  await expect(page.locator('#simular-envio')).toBeDisabled();
  await page.locator('#conferi-envio').check();
  await page.locator('#destinatario').fill('outro@example.invalid');
  await expect(page.locator('#resultado-previa')).toBeHidden();
  checks.push('Prévia mostra compartilhamento e mudança de destinatário invalida confirmação');
  await page.locator('#gerar-previa').click();
  await page.locator('#conferi-envio').check();
  await navegar('documentos');await page.locator('#lista-anexos input').first().uncheck();
  await expect(page.locator('#resultado-previa')).toBeHidden();
  await page.locator('#lista-anexos input').first().check();
  await navegar('email');await expect(page.locator('#resultado-previa')).toBeHidden();
  await page.locator('#gerar-previa').click();
  await page.locator('#conferi-envio').check();
  checks.push('Mudança de anexos exige nova conferência');
  // Servidor processa; a primeira resposta HTTP é perdida deliberadamente pelo navegador.
  let perdeu = false, confirmacaoOriginal;
  await page.route('**/tentativas-email', async route => {
    if (route.request().method() === 'POST' && !perdeu) { perdeu = true; confirmacaoOriginal = route.request().postDataJSON(); await route.fetch(); await route.abort(); }
    else await route.continue();
  });
  await page.locator('#simular-envio').click();
  await expect(page.locator('#recuperar-confirmacao')).toBeVisible();
  await page.locator('#recuperar-confirmacao').click();
  await expect(page.locator('#resultado-tentativa')).toContainText('SIMULAÇÃO concluída');
  await expect(page.locator('#tentativas section')).toHaveCount(1);
  await expect(page.locator('#recuperar-confirmacao')).toBeHidden();
  const first = await (await page.request.get(url + '/api/viagens/1/tentativas-email')).json();
  assert.equal(first.length, 1); assert.equal(first[0].snapshot.destinatario, 'outro@example.invalid');
  assert.equal((await (await page.request.get(url + '/api/viagens/2/tentativas-email')).json()).length, 0);
  checks.push('Resposta perdida recuperada com mesma confirmação; uma tentativa, sem disparos para segunda viagem');
  await page.getByRole('button', { name: 'Preparar reenvio confirmado', exact: true }).click();
  await page.locator('#gerar-previa').click();
  await page.locator('#conferi-envio').check();
  await page.getByRole('button', { name: 'Simular reenvio confirmado', exact: true }).click();
  await expect(page.locator('#tentativas section')).toHaveCount(2);
  const attempts = await (await page.request.get(url + '/api/viagens/1/tentativas-email')).json();
  assert.equal(attempts[1].anteriorId, first[0].id);
  await page.reload({ waitUntil: 'networkidle' });
  await page.locator('#abertura').waitFor({state:'hidden'});await navegar('documentos');await page.locator('#viagem-selecionada').selectOption('1');
  await expect(page.locator('#tentativas section')).toHaveCount(2);
  await navegar('email');
  checks.push('Reenvio deliberado confirmado e vinculado; histórico preservado após recarga');
  await page.goto('about:blank');
  processServer.kill();
  await Promise.race([new Promise(resolve => processServer.once('exit', resolve)), sleep(15000)]);
  if (processServer.exitCode === null && processServer.signalCode === null) throw new Error('Servidor anterior não encerrou; reinicialização cancelada');
  url = await start();
  await page.goto(url, { waitUntil: 'networkidle' });
  await page.locator('#abertura').waitFor({state:'hidden'});await navegar('documentos');await page.locator('#viagem-selecionada').selectOption('1');
  await expect(page.locator('#tentativas section')).toHaveCount(2);
  await navegar('email');
  assert.deepEqual(await (await page.request.get(url + '/api/viagens/1/tentativas-email')).json(), attempts);
  const repetida = await page.request.post(url + '/api/viagens/1/tentativas-email', { data: confirmacaoOriginal });
  assert.equal(repetida.status(), 200); assert.deepEqual(await repetida.json(), first[0]);
  assert.equal((await (await page.request.get(url + '/api/viagens/1/tentativas-email')).json()).length, 2);
  checks.push('Banco em arquivo mantém snapshots e resultados após encerrar e reiniciar o servidor');
  assert((await (await page.request.get(url + '/api/processamentos/' + problema.ID)).json()).DIAGNOSTICO.diagnosticos.some(d => d.codigo === 'PDF_NAO_RECONHECIDO'));
  await page.screenshot({ path: path.join(output, 'desktop.png'), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  await page.screenshot({ path: path.join(output, 'mobile.png'), fullPage: true });
  checks.push('Desktop e celular sem overflow');
  const local={total:checks.length,verificacoes:checks,errosJavaScript:errors,envioRealExecutado:false};
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(output,'resultado-local.json'),JSON.stringify(local,null,2));
  if(args.includes('--somente-local')){console.log(JSON.stringify(local,null,2));return;}
  // Provisionamento apenas na base sintética de desenvolvimento, seguido de reinício autenticado.
  const senha = 'SenhaSinteticaParaBrowser12!';
  for (const [usuario, papel] of [['admin-teste', 'ADMIN'], ['leitor-teste', 'CONSULTA']]) {
    const criado = await page.request.post(url + '/api/admin/usuarios', { data: { usuario, senha, papel } });
    assert.equal(criado.status(), 200);
  }
  await page.goto('about:blank'); processServer.kill();
  await Promise.race([new Promise(resolve => processServer.once('exit', resolve)), sleep(15000)]);
  if (processServer.exitCode === null && processServer.signalCode === null) throw new Error('Servidor anterior não encerrou');
  url = await start(['--rotabrasil.seguranca.ativa=true']);
  assert.equal((await page.request.get(url + '/api/viagens')).status(), 401);
   await page.setViewportSize({ width: 1440, height: 1000 });
   const paginaLogin=await page.goto(url+'/login', { waitUntil: 'networkidle' });
   if(await page.locator('input[name="username"]').count()!==1){
     await page.screenshot({path:path.join(output,'login-indisponivel.png'),fullPage:true});
     throw new Error(`O servidor protegido não forneceu o formulário de login em /login (HTTP ${paginaLogin.status()}); fluxo autenticado não validado`);
   }
  async function entrar(nome) {
    await page.locator('input[name="username"]').fill(nome); await page.locator('input[name="password"]').fill(senha);
    await page.locator('button[type="submit"]').click(); await page.waitForLoadState('networkidle');
    await expect(page.locator('#sessao-usuario')).toContainText(nome);
  }
   await entrar('admin-teste'); await expect(page.locator('.sidebar nav a[href="#administracao"]')).toHaveCount(1);
   assert.equal((await (await page.request.get(url+'/api/admin/usuarios')).json()).length,2);
  await page.locator('#sair').click(); await page.waitForURL('**/login'); await entrar('leitor-teste');
  await expect(page.locator('#administracao')).toBeHidden(); await expect(page.locator('#inspecionar-zip')).toBeDisabled();
  const sessao = await (await page.request.get(url + '/api/sessao')).json();
  const proibido = await page.request.post(url + '/api/pacotes/inspecao', { headers: { [sessao.csrfHeader]: sessao.csrf }, multipart: { arquivo: { name: 'sintetico.zip', mimeType: 'application/zip', buffer } } });
  assert.equal(proibido.status(), 403);
  assert.equal((await page.request.get(url + '/api/documentos/1/arquivo')).status(), 200);
  checks.push('Login e logout funcionam no navegador; administrador gerencia operadores e CONSULTA lê documentos sem poder importar');
  await page.screenshot({ path: path.join(output, 'consulta-autenticada.png'), fullPage: true });
  assert.deepEqual(errors, []);
  const result = { total: checks.length, verificacoes: checks, errosJavaScript: errors, envioRealExecutado: false };
  fs.writeFileSync(path.join(output, 'resultado-ui.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
}
main().catch(async error => { console.error(error);if(page&&!page.isClosed())await page.screenshot({path:path.join(output,'falha.png'),fullPage:true}).catch(()=>{});fs.writeFileSync(path.join(output,'resultado-ui-parcial.json'),JSON.stringify({total:checks.length,verificacoes:checks,errosJavaScript:errors,requisicoes,erro:error.message,envioRealExecutado:false},null,2)); process.exitCode = 1; }).finally(async () => {
  if (browser) await browser.close();
  if (processServer && processServer.exitCode === null && processServer.signalCode === null) {
    processServer.kill();
    await Promise.race([new Promise(resolve => processServer.once('exit', resolve)), sleep(15000)]);
  }
  fs.closeSync(log);
  fs.rmSync(base, { recursive: true, force: true, maxRetries: 10, retryDelay: 500 });
});
