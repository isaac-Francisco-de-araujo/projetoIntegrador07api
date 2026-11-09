/* Exclusões e navegação: servidor real, H2 e documentos exclusivamente sintéticos. */
'use strict';
const { chromium }=require('playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {createHash}=require('node:crypto');const {spawn}=require('node:child_process');
const [jarArg,outArg]=process.argv.slice(2);if(!jarArg||!outArg)throw Error('Uso: node scripts/testar-exclusoes-paginas.cjs JAR SAIDA_EXTERNA');
const root=path.resolve(__dirname,'..'),out=path.resolve(outArg);if(out===root||out.startsWith(root+path.sep))throw Error('Use saída externa');
fs.mkdirSync(out,{recursive:true});const isolated=fs.mkdtempSync(path.join(out,'.isolado-'));
const checks=[],errors=[],mutations=[];let logs='',browser,page;
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const server=spawn(process.env.JAVA||'java',['-Xmx512m','-jar',path.resolve(jarArg),'--server.address=127.0.0.1','--server.port=0',
  '--spring.profiles.active=desenvolvimento','--rotabrasil.seguranca.ativa=false','--spring.datasource.url=jdbc:h2:mem:exclusoes_paginas',
  '--spring.flyway.enabled=true','--spring.h2.console.enabled=false','--rotabrasil.email.modo=SIMULADO','--rotabrasil.arquivos.viagens='+isolated+'/viagens'],{cwd:root});
server.stdout.on('data',data=>logs+=data);server.stderr.on('data',data=>logs+=data);
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
async function run(){
  let port;for(let i=0;i<900;i++){port=logs.match(/Tomcat started on port (\d+)/)?.[1];if(port)break;if(server.exitCode!==null)throw Error('Servidor terminou');await wait(100);}if(!port)throw Error('Servidor não iniciou');
  const url='http://127.0.0.1:'+port;
  browser=await chromium.launch({channel:process.env.ROTABRASIL_BROWSER_CHANNEL||'chrome',headless:true});
  page=await browser.newPage({viewport:{width:1440,height:1000},locale:'pt-BR',reducedMotion:'reduce'});
  page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(r.url().includes('/api/')&&['POST','DELETE','PUT'].includes(r.method()))mutations.push({method:r.method(),url:r.url()});});
  const routes=['inicio','pacotes','importacao','viagens','clientes','email','processamentos'];
  const alias={documentos:'viagens'};
  async function navegar(id){await page.evaluate(hash=>{location.hash=hash;},id);await page.waitForFunction(route=>document.querySelector('main').dataset.paginaAtual===route,alias[id]||id);}
  async function get(route){const response=await page.request.get(url+route);assert.equal(response.status(),200);return response.json();}
  async function estabilizar(){await page.waitForFunction(()=>!document.getElementById('atualizar').disabled&&document.getElementById('form-pacote').getAttribute('aria-busy')==='false');}
  await page.goto(url,{waitUntil:'networkidle'});await page.locator('#abertura').waitFor({state:'hidden'});
  for(const route of routes){
    await page.locator(`.sidebar nav a[href="#${route}"]`).click();
    const visible=await page.locator('main [data-pagina]').evaluateAll(items=>[...new Set(items.filter(e=>!e.hidden&&e.getClientRects().length).map(e=>e.dataset.pagina))]);
    assert.deepEqual(visible,[route]);assert.equal(await page.locator(`.sidebar nav a[href="#${route}"]`).getAttribute('aria-current'),'page');
  }
  await page.goBack();await page.waitForFunction(()=>document.querySelector('main').dataset.paginaAtual==='email');
  await page.goForward();await page.waitForFunction(()=>document.querySelector('main').dataset.paginaAtual==='processamentos');
  checks.push('Sete páginas isoladas, menu ativo, títulos, links diretos e voltar/avançar do navegador');

  const xml=fs.readFileSync(path.join(root,'src/main/resources/email/cte-sintetico.xml'),'utf8');
  const key='35250412345678000195570020000662131001747719';assert.ok(xml.includes(key));
  const keyBase=key.slice(0,25)+'000066214'+key.slice(34,43);let sum=0;[...keyBase].reverse().forEach((c,i)=>sum+=Number(c)*(2+i%8));const dv=11-sum%11;
  const secondXml=xml.replaceAll(key,keyBase+(dv>=10?0:dv)).replace('<nCT>66213</nCT>','<nCT>66214</nCT>');
  const pdf=Buffer.from(await page.evaluate(async()=>{
    const {PDFDocument,StandardFonts}=await import('/webjars/pdf-lib/1.17.1/dist/pdf-lib.esm.min.js');const d=await PDFDocument.create();const font=await d.embedFont(StandardFonts.Helvetica);
    d.addPage([595,842]).drawText('DOCUMENTO SINTETICO - SEM VALOR FISCAL',{x:40,y:750,font,size:14});return Array.from(await d.save());
  }));
  const zip=Buffer.from(await page.evaluate(async({xml,secondXml,pdf})=>{
    const {zipSync,strToU8}=await import('/webjars/fflate/0.8.3/esm/browser.js');return Array.from(zipSync({'cte.xml':strToU8(xml),'outro.xml':strToU8(secondXml),'fatura-sintetica.pdf':new Uint8Array(pdf),'viagem-sintetica.pdf':new Uint8Array(pdf)},{level:0}));
  },{xml,secondXml,pdf:Array.from(pdf)}));
  await navegar('importacao');let before=mutations.length;
  await page.locator('#arquivo').setInputFiles({name:'sintetico.xml',mimeType:'application/xml',buffer:Buffer.from(xml)});
  await navegar('clientes');await navegar('importacao');assert.equal(await page.locator('#arquivo').evaluate(e=>e.files[0].name),'sintetico.xml');
  await page.locator('#remover-arquivo-selecao').click();assert.equal(await page.locator('#arquivo').evaluate(e=>e.files.length),0);
  await page.locator('#arquivo').setInputFiles({name:'sintetico.pdf',mimeType:'application/pdf',buffer:pdf});await page.locator('#remover-arquivo-selecao').click();assert.equal(await page.locator('#opcoes-pdf-avulso').isHidden(),true);
  await navegar('pacotes');await page.locator('#arquivo-zip').setInputFiles({name:'original.zip',mimeType:'application/zip',buffer:zip});await page.locator('#remover-zip-selecao').click();
  assert.equal(await page.locator('#arquivo-zip').evaluate(e=>e.files.length),0);assert.equal(mutations.length,before);
  checks.push('Seleções de ZIP, XML e PDF são removidas localmente; navegar preserva o arquivo escolhido sem enviar mutações');

  await navegar('importacao');
  await page.locator('#arquivo').setInputFiles({name:'cte-sintetico.xml',mimeType:'application/xml',buffer:Buffer.from(xml)});
  let xmlResponse=page.waitForResponse(r=>r.url().includes('/api/processamento/xml')&&r.request().method()==='POST');
  await page.locator('#importar').click();assert.equal((await xmlResponse).status(),200);
  await page.waitForFunction(()=>!document.getElementById('importar').disabled);
  const xmlDocs=await get('/api/documentos');assert.equal(xmlDocs.length,1);
  assert.equal(digest(await(await page.request.get(url+`/api/documentos/${xmlDocs[0].id}/arquivo`)).body()),digest(Buffer.from(xml)));
  xmlResponse=page.waitForResponse(r=>r.url().includes('/api/processamento/xml')&&r.request().method()==='POST');
  await page.locator('#importar').click();assert.equal((await xmlResponse).status(),200);
  await page.waitForFunction(()=>!document.getElementById('importar').disabled);
  assert.equal((await get('/api/documentos')).length,1);
  checks.push('XML individual importa, baixa com SHA-256 idêntico e não duplica ao repetir');
  await navegar('pacotes');
  await page.locator('#arquivo-zip').setInputFiles({name:'original.zip',mimeType:'application/zip',buffer:zip});await page.locator('#inspecionar-zip').click();
  await page.locator('#revisar-vinculos-zip').waitFor();
  assert.match(await page.locator('#orientacao-avancar-zip').textContent(),/Associe os 2 PDFs pendentes/);
  assert.equal(await page.locator('#confirmar-zip').isDisabled(),true);
  await page.locator('#revisar-vinculos-zip').click();
  assert.equal(await page.locator('#vinculo-a2 select').evaluate(e=>e===document.activeElement),true);
  await page.locator('#vinculo-a2 select').selectOption('FATURA');for(const checkbox of await page.locator('#vinculo-a2 input[type=checkbox]').all())await checkbox.check();
  await page.locator('#vinculo-a3 select').selectOption('VIAGEM');await page.locator('#vinculo-a3 input[type=checkbox]').first().check();
  assert.match(await page.locator('#orientacao-avancar-zip').textContent(),/Associações conferidas/);
  assert.equal(await page.locator('#confirmar-zip').isEnabled(),true);
  await page.locator('#zip-avancar').click();
  const response=page.waitForResponse(r=>r.url().includes('/api/pacotes/confirmacao')&&r.request().method()==='POST');await page.locator('#confirmar-zip').click();
  const imported=await (await response).json();await estabilizar();const firstId=imported.pacoteId,docId=imported.documentos[0].id,tripId=imported.documentos[0].viagemId,tripTwo=imported.documentos[1].viagemId;
  const sharedPdf=imported.anexos.find(a=>a.finalidade==='FATURA'),solePdf=imported.anexos.find(a=>a.finalidade==='VIAGEM');
  const paths=[...imported.documentos.map(d=>`/api/documentos/${d.id}/arquivo`),...imported.anexos.map(a=>`/api/anexos/${a.id}/arquivo`)];const hashes=new Map();for(const p of paths)hashes.set(p,digest(await (await page.request.get(url+p)).body()));
  await navegar('pacotes-importados');await page.waitForFunction(id=>Boolean(document.querySelector(`[data-pacote-id="${id}"]`)),firstId);

  await navegar('importacao');await page.locator('#arquivo').setInputFiles({name:'adicional.pdf',mimeType:'application/pdf',buffer:pdf});
  await page.locator('#pdf-viagem-base').selectOption(String(tripId));await page.waitForFunction(()=>!document.getElementById('pdf-xml-base').disabled);await page.locator('#pdf-xml-base').selectOption(String(docId));
  await page.locator('#importar').click();await page.waitForFunction(()=>document.querySelector('main').dataset.paginaAtual==='pacotes');await page.locator('#vinculo-a1 select').selectOption('VIAGEM');await page.locator('#vinculo-a1 input[type=checkbox]').check();
  await page.locator('#zip-avancar').click();const extraResponse=page.waitForResponse(r=>r.url().includes('/api/pacotes/confirmacao')&&r.request().method()==='POST');await page.locator('#confirmar-zip').click();const extra=await (await extraResponse).json();await estabilizar();
  assert.equal((await get(`/api/viagens/${tripId}/documentos`)).length,1);
  checks.push('Importação ZIP com fatura compartilhada e PDF avulso preservadas entre páginas, sem duplicar o XML base');

  await navegar('documentos');await page.locator('#viagem-selecionada').selectOption(String(tripId));await page.locator('#lista-documentos .delete-action').waitFor();
  before=mutations.length;await page.locator('#lista-documentos .delete-action').click();await page.keyboard.press('Escape');assert.equal(mutations.length,before);
  await page.locator('#lista-documentos .delete-action').click();await page.locator('#executar-exclusao').click();await page.locator('#confirmar-exclusao').waitFor({state:'hidden'});assert.equal((await get(`/api/viagens/${tripId}/documentos`)).length,0);
  await page.locator('#mostrar-arquivados').check();await page.locator('#lista-documentos button.row-action').click();await page.waitForFunction(()=>!document.getElementById('atualizar').disabled&&document.getElementById('lista-documentos').textContent.includes('Nenhum documento arquivado'));assert.equal((await get(`/api/viagens/${tripId}/documentos`))[0].id,docId);
  await page.locator('#mostrar-arquivados').uncheck();await page.getByRole('button',{name:'Excluir PDF fatura-sintetica.pdf',exact:true}).click();
  assert.match(await page.locator('#descricao-exclusao').textContent(),/todas as viagens/);await page.locator('#executar-exclusao').click();await page.locator('#confirmar-exclusao').waitFor({state:'hidden'});
  assert.equal((await get(`/api/viagens/${tripTwo}/anexos`)).some(a=>a.id===sharedPdf.id),false);
  await page.locator('#mostrar-arquivados').check();await page.getByRole('button',{name:'Restaurar PDF fatura-sintetica.pdf',exact:true}).click();await page.waitForFunction(()=>!document.getElementById('atualizar').disabled);await page.locator('#mostrar-arquivados').uncheck();
  checks.push('Excluir XML/PDF exige confirmação; Escape não exclui, PDF compartilhado afeta seus vínculos e restauração mantém os IDs');

  await navegar('pacotes-importados');await page.locator('#atualizar-pacotes').click();await page.locator(`#lista-pacotes-importados [data-pacote-id="${firstId}"] .delete-action`).waitFor();
  before=mutations.length;await page.locator(`#lista-pacotes-importados [data-pacote-id="${firstId}"] .delete-action`).click();await page.locator('.package-impact-item').first().waitFor();
  assert.match(await page.locator('#impacto-excluir-pacote').textContent(),new RegExp(`ZIPs #${extra.pacoteId}`));assert.match(await page.locator('#impacto-excluir-pacote').textContent(),/PDF compartilhado/);
  assert.equal(await page.locator('#executar-excluir-pacote').isDisabled(),true);await page.locator('#fechar-excluir-pacote').click();assert.equal(mutations.length,before);
  await page.locator(`#lista-pacotes-importados [data-pacote-id="${firstId}"] .delete-action`).click();await page.locator('#confirmar-excluir-pacote').waitFor({state:'visible'});
  await page.request.delete(url+`/api/anexos/${solePdf.id}`);await page.locator('#confirmar-excluir-pacote').check();
  const staleResponse=page.waitForResponse(r=>r.url().includes(`/api/pacotes/${firstId}?`)&&r.request().method()==='DELETE');await page.locator('#executar-excluir-pacote').click();assert.equal((await staleResponse).status(),409);
  await page.waitForFunction(()=>document.getElementById('estado-excluir-pacote').textContent.includes('mudaram'));assert.equal(await page.locator('#executar-excluir-pacote').isDisabled(),true);
  assert.equal((await get(`/api/viagens/${tripId}/documentos`)).length,1);
  await page.locator('#reconferir-excluir-pacote').click();await page.waitForFunction(()=>!document.getElementById('confirmar-excluir-pacote').disabled);
  assert.equal(await page.locator('#confirmar-excluir-pacote').isChecked(),false);await page.screenshot({path:path.join(out,'excluir-zip-compartilhado.png')});
  const history=await get('/api/processamentos');await page.locator('#confirmar-excluir-pacote').check();await page.locator('#executar-excluir-pacote').click();await page.locator('#excluir-pacote').waitFor({state:'hidden'});await page.waitForFunction(()=>!document.getElementById('atualizar').disabled);
  assert.equal((await get('/api/consulta/resumo')).documentos,0);assert.deepEqual(await get('/api/processamentos'),history);
  assert.equal((await get('/api/pacotes')).some(p=>p.id===firstId),false);
  checks.push('Exclusão do ZIP lista vínculos compartilhados, bloqueia conferência vencida e arquiva atomicamente sem apagar histórico');

  await page.locator('#mostrar-pacotes-arquivados').check();await page.locator(`#lista-pacotes-importados [data-pacote-id="${firstId}"] button.row-action`).click();await page.locator('[data-restaurar-arquivo]').first().waitFor();
  await page.locator(`[data-restaurar-arquivo="documento-${docId}"]`).click();await page.waitForFunction(()=>!document.getElementById('reconferir-excluir-pacote').disabled);
  await page.locator(`[data-restaurar-arquivo="anexo-${sharedPdf.id}"]`).click();await page.waitForFunction(()=>!document.getElementById('reconferir-excluir-pacote').disabled);await page.locator('#fechar-excluir-pacote').click();
  assert.equal((await get('/api/consulta/resumo')).documentos,1);assert.equal((await get(`/api/viagens/${tripId}/anexos?arquivados=true`)).some(a=>a.id===solePdf.id),true);
  for(const [p,hash] of hashes)assert.equal(digest(await (await page.request.get(url+p)).body()),hash);
  checks.push('Restauração por arquivo não restaura os demais e todos os bytes XML/PDF permanecem idênticos');

  await navegar('pacotes');await page.locator('#zip-voltar-conferencia').click();await page.locator('[data-previa-pdf="a1"]').click();await page.waitForFunction(()=>document.getElementById('pdf-paginacao').textContent.includes('Página 1'));
  const trecho=await page.locator('#pdf-trecho option').evaluateAll(items=>items.find(i=>i.textContent.includes('DOCUMENTO SINTETICO')).value);await page.locator('#pdf-trecho').selectOption(trecho);await page.locator('#pdf-texto').fill('REVISAO SINTETICA');await page.locator('#pdf-largura').fill('400');await page.locator('#pdf-adicionar').click();await page.locator('#pdf-salvar').click();await page.locator('#editor-pdf').waitFor({state:'hidden'});
  before=mutations.filter(r=>r.method==='DELETE').length;await page.locator('#remover-zip-selecao').click();await page.locator('#cancelar-exclusao').click();assert.equal(await page.locator('#revisoes-pdf').isVisible(),true);
  await page.locator('#remover-zip-selecao').click();await page.locator('#executar-exclusao').click();await page.locator('#confirmar-exclusao').waitFor({state:'hidden'});assert.equal(await page.locator('#arquivo-zip').evaluate(e=>e.files.length),0);assert.equal(await page.locator('#revisoes-pdf').isHidden(),true);assert.equal(mutations.filter(r=>r.method==='DELETE').length,before);
  checks.push('Remover ZIP com revisão pendente exige confirmação, preserva a revisão ao cancelar e não exclui registros importados');

  await navegar('viagens');await page.locator('#buscar-viagem').fill('66213');await navegar('clientes');await navegar('viagens');assert.equal(await page.locator('#buscar-viagem').inputValue(),'66213');
  await navegar('clientes');await page.reload({waitUntil:'networkidle'});await page.locator('#abertura').waitFor({state:'hidden'});assert.equal(await page.locator('main').getAttribute('data-pagina-atual'),'clientes');
  checks.push('Filtros e seleções são mantidos entre páginas e o link direto continua válido após recarga');
  for(const width of [360,390,768,1440]){
    await page.setViewportSize({width,height:900});for(const route of routes){await navegar(route);assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),`Overflow ${route} em ${width}`);}
    if(width===390||width===1440){await navegar('inicio');await page.screenshot({path:path.join(out,`inicio-${width}.png`),fullPage:true});await navegar('pacotes-importados');await page.screenshot({path:path.join(out,`zips-${width}.png`),fullPage:true});}
  }
  await page.setViewportSize({width:390,height:844});await page.locator('#menu-abrir').click();await page.locator('.sidebar nav a[href="#clientes"]').click();assert.equal(await page.locator('main').getAttribute('data-pagina-atual'),'clientes');assert.equal(await page.locator('main').evaluate(e=>e.inert),false);
  checks.push('Todas as páginas responsivas de 360 a 1440 px, com drawer móvel e navegação acessível');
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'resultado.json'),JSON.stringify({checks,errors},null,2));console.log(JSON.stringify({checks,errors},null,2));
}
run().catch(async error=>{console.error(error);fs.writeFileSync(path.join(out,'servidor.log'),logs);if(page)await page.screenshot({path:path.join(out,'falha.png'),fullPage:true}).catch(()=>{});process.exitCode=1;}).finally(async()=>{
  await browser?.close();server.kill();await Promise.race([new Promise(resolve=>server.once('exit',resolve)),wait(10000)]);fs.rmSync(isolated,{recursive:true,force:true,maxRetries:5,retryDelay:500});
});
