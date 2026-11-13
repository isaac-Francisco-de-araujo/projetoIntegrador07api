/* Somente H2/acervo sintéticos. Nunca configura SMTP real. */
'use strict';
const {chromium,expect}=require('playwright/test');
const fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const {spawn}=require('node:child_process');
const assert=require('node:assert/strict');
const {pacoteSintetico,configuracao}=require('./central-fixture.cjs');
const [jar,out,modo]=process.argv.slice(2);
if(!jar||!out)throw Error('Uso: node scripts/testar-central-envios.cjs JAR SAIDA [--antes]');
fs.mkdirSync(out,{recursive:true});
const base=fs.mkdtempSync(path.join(os.tmpdir(),'rotabrasil-central-'));
const logPath=path.join(out,'servidor.log'),log=fs.openSync(logPath,'w');
const server=spawn('java',['-Xmx512m','-jar',path.resolve(jar),'--server.address=127.0.0.1','--server.port=0',
  '--spring.datasource.url=jdbc:h2:file:'+base.replaceAll('\\','/')+'/banco;WRITE_DELAY=0',
  '--spring.flyway.enabled=true','--spring.h2.console.enabled=false','--rotabrasil.email.modo=SIMULADO',
  '--rotabrasil.notificacoes.modo=SIMULADO','--rotabrasil.teste-email.modo=SIMULADO',
  '--rotabrasil.arquivos.viagens='+base+'/acervo','--rotabrasil.exportacao.raiz='+base+'/exportacao'],{stdio:['ignore',log,log]});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
let browser,page;const checks=[],errors=[],requests=[];
async function main(){
  let url;
  for(let i=0;i<600;i++){
    const ports=[...fs.readFileSync(logPath,'utf8').matchAll(/Tomcat started on port (\d+)/g)];
    if(ports.length){url='http://127.0.0.1:'+ports.at(-1)[1];try{if((await fetch(url+'/api/sessao')).ok)break;}catch{}}
    if(server.exitCode!==null)throw Error('Servidor encerrou');await sleep(100);
  }
  browser=await chromium.launch({channel:'chrome',headless:true});
  page=await browser.newPage({viewport:{width:1440,height:1000},locale:'pt-BR'});
  page.on('pageerror',e=>errors.push(e.message));
  page.on('response',r=>{if(r.url().includes('/api/'))requests.push({rota:new URL(r.url()).pathname,status:r.status(),metodo:r.request().method()});});
  // Usa exclusivamente a fixture explicitamente sintética versionada.
  const buffer=pacoteSintetico(),arquivo={name:'central-sintetica.zip',mimeType:'application/zip',buffer};
  const inspecao=await page.request.post(url+'/api/pacotes/inspecao',{multipart:{arquivo}});assert.equal(inspecao.status(),200);
  const imported=await page.request.post(url+'/api/pacotes/confirmacao',{multipart:{arquivo,configuracao:{name:'configuracao.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(configuracao(await inspecao.json())))}}});
  assert.equal(imported.status(),200);const importacao=await imported.json(),trip=String(importacao.documentos[0].viagemId);
  await page.goto(url+'/painel',{waitUntil:'networkidle'});
  await page.locator('#abertura').waitFor({state:'hidden'});
  async function navegar(hash){await page.evaluate(h=>{location.hash=h;},hash);await expect(page.locator('main')).toHaveAttribute('data-pagina-atual',hash==='documentos'?'viagens':hash);}
  await navegar('documentos');await page.locator('#viagem-selecionada').selectOption(trip);
  await page.locator('#lista-documentos input').check();for(const box of await page.locator('#lista-anexos input').all())await box.check();await navegar('email');
  await expect(page.locator('#selecao-info')).toContainText('3 documento(s)');
  assert.equal(await page.locator('#destinatario').inputValue(),'');
  await expect(page.locator('#contexto-email')).toContainText('66213');
  if(modo==='--antes'){
    await expect(page.locator('#destinatario')).toBeDisabled();await expect(page.locator('#gerar-previa')).toBeDisabled();
    const conf=await page.request.post(url+`/api/viagens/${trip}/conferencias-email`,{data:{selecao:{destinatario:'conferencia@example.invalid',documentoIds:importacao.documentos.map(d=>d.id),anexoIds:importacao.anexos.map(a=>a.id)}}});
    assert.equal(conf.status(),200);assert((await conf.json()).id);
    await page.screenshot({path:path.join(out,'antes-bloqueio.png'),fullPage:true});
    checks.push('Defeito reproduzido: UI bloqueia destinatário/prévia em SIMULADO; API de conferência persistida responde 200 sem transporte. Campo vazio, endereço é placeholder.');
  }else{
    for(const asset of ['painel-email.js','painel-documentos.js'])assert.equal(await(await page.request.get(url+'/'+asset)).text(),fs.readFileSync(path.join(__dirname,'../front',asset),'utf8'),'O JAR deve servir o frontend atual: '+asset);
    checks.push('Módulos efetivamente servidos pelo JAR idênticos às fontes atuais em front/');
    await expect(page.locator('#destinatario')).toBeEnabled();await expect(page.locator('#gerar-previa')).toBeDisabled();
    await expect(page.locator('#motivo-previa')).toContainText('destinatário válido');
    await page.locator('#destinatario').fill('invalido');await expect(page.locator('#gerar-previa')).toBeDisabled();
    await page.locator('#destinatario').fill('conferencia@example.invalid');await expect(page.locator('#gerar-previa')).toBeEnabled();
    await page.locator('#destinatario').press('Tab');await expect(page.locator('#gerar-previa')).toBeFocused();
    await page.screenshot({path:path.join(out,'preparacao-desktop.png'),fullPage:true});
    await page.locator('#gerar-previa').click();await expect(page.locator('#resultado-previa')).toContainText('central-sintetico.xml');
    await expect(page.locator('#resultado-previa')).toContainText('Nenhum e-mail enviado');
    await page.locator('#conferi-envio').check();await expect(page.locator('#simular-envio')).toBeDisabled();
    await page.screenshot({path:path.join(out,'conferencia-desktop.png'),fullPage:true});
    checks.push('Seleção preservada Viagens → Envios, vazio/inválido bloqueados; conferência persistida com integridade sem SMTP, confirmação real bloqueada');
    await page.locator('#destinatario').fill('outro@example.invalid');await expect(page.locator('#resultado-previa')).toBeHidden();
    await navegar('documentos');await page.locator('#lista-documentos input').uncheck();for(const box of await page.locator('#lista-anexos input').all())await box.uncheck();await navegar('email');
    await expect(page.locator('#motivo-previa')).toContainText('documento');
    await expect(page.locator('#contexto-email')).toBeVisible();await expect(page.locator('#gerar-previa')).toBeDisabled();
    assert.equal(await page.locator('#destinatario').inputValue(),'outro@example.invalid');
    await navegar('documentos');await page.locator('#lista-documentos input').check();await navegar('email');
    assert.equal(await page.locator('#destinatario').inputValue(),'outro@example.invalid');
    checks.push('Alterações invalidam conferência; alteração da seleção preserva destinatário');
    await page.route('**/api/envio-documental',r=>r.fulfill({status:503,contentType:'application/json',body:'{}'}));
    await page.locator('#recarregar-envio').click();await expect(page.locator('#modo-email')).toContainText('Não foi possível consultar');
    await expect(page.locator('#gerar-previa')).toBeEnabled();await page.locator('#gerar-previa').click();
    await expect(page.locator('#resultado-previa')).toContainText('central-sintetico.xml');await expect(page.locator('#simular-envio')).toBeDisabled();
    await page.screenshot({path:path.join(out,'falha-configuracao.png'),fullPage:true});
    await page.unroute('**/api/envio-documental');await page.locator('#recarregar-envio').click();
    await expect(page.locator('#bloqueios-envio')).toContainText('Transporte real não configurado');
    checks.push('Falha de configuração não é ausência; recuperação funciona e preparação permanece independente');
    await page.locator('#alternar-tema').click();await page.screenshot({path:path.join(out,'conferencia-escuro.png'),fullPage:true});await page.locator('#alternar-tema').click();
    await page.setViewportSize({width:390,height:844});await page.waitForTimeout(400);await page.screenshot({path:path.join(out,'conferencia-mobile.png'),fullPage:true});
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    await page.locator('#destinatario').fill('');await page.screenshot({path:path.join(out,'bloqueio-mobile.png'),fullPage:true});
    checks.push('Desktop e celular sem overflow, temas claro/escuro e acesso por teclado; conferência e bloqueios capturados');
    // Resposta antiga de conferência não pode reaparecer após alterar destinatário.
    await page.setViewportSize({width:1440,height:1000});await page.locator('#destinatario').fill('tardio@example.invalid');
    await page.route('**/conferencias-email',async r=>{const response=await r.fetch();await sleep(500);await r.fulfill({response});},{times:1});
    await page.locator('#gerar-previa').click();await navegar('documentos');await page.locator('#lista-documentos input').uncheck();
    await sleep(800);await expect(page.locator('#resultado-previa')).toBeHidden();checks.push('Resposta assíncrona obsoleta descartada após mudança de seleção');
    const versao=await(await page.request.get(url+'/api/versao')).json();
    await page.route('**/api/versao',r=>r.fulfill({json:{...versao,estaticosSha256:{...versao.estaticosSha256,'painel-documentos.js':'0'.repeat(64)}}}));
    await page.reload({waitUntil:'networkidle'});await expect(page.locator('#aviso')).toContainText('incompatíveis');
    await page.screenshot({path:path.join(out,'versao-incompativel.png'),fullPage:true});
    await page.unroute('**/api/versao');await page.reload({waitUntil:'networkidle'});await page.locator('#abertura').waitFor({state:'hidden'});
    await expect(page.locator('#versao-envios')).toContainText('recursos conferidos');
    checks.push('Frontend/backend com hashes diferentes bloqueados explicitamente; recarga do mesmo JAR recupera a inicialização');
  }
  assert.deepEqual(errors,[]);
  const identidade={jar:path.resolve(jar),sha256:require('node:crypto').createHash('sha256').update(fs.readFileSync(jar)).digest('hex'),versao:await(await page.request.get(url+'/api/versao')).json()};
  fs.writeFileSync(path.join(out,'resultado.json'),JSON.stringify({identidade,checks,errors,requests,envioExterno:false},null,2));console.log(JSON.stringify({identidade,checks,errors},null,2));
}
main().catch(async e=>{console.error(e);if(page)await page.screenshot({path:path.join(out,'falha.png'),fullPage:true}).catch(()=>{});process.exitCode=1;}).finally(async()=>{
  if(browser)await browser.close();server.kill();await Promise.race([new Promise(r=>server.once('exit',r)),sleep(15000)]);fs.closeSync(log);
  fs.rmSync(base,{recursive:true,force:true,maxRetries:10,retryDelay:500});
});
