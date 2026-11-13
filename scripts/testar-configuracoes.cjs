/* Configurações/exportação: backend real, banco em arquivo isolado, reinício e SMTP simulado. */
'use strict';
const {chromium}=require('playwright');const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {spawn}=require('node:child_process');const {createHash}=require('node:crypto');
const [jarArg,outArg]=process.argv.slice(2);if(!jarArg||!outArg)throw Error('Uso: node scripts/testar-configuracoes.cjs JAR SAIDA_EXTERNA');
const root=path.resolve(__dirname,'..'),out=path.resolve(outArg);if(out===root||out.startsWith(root+path.sep))throw Error('Use saída externa');
fs.mkdirSync(out,{recursive:true});const isolated=fs.mkdtempSync(path.join(out,'.isolado-')),exportRoot=path.join(isolated,'exportados');fs.mkdirSync(exportRoot);
let server,logs='',url,browser,page;const checks=[],errors=[],wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
async function iniciar(novo){
  let atual='';server=spawn(process.env.JAVA||'java',['-Xmx512m','-jar',path.resolve(jarArg),'--server.address=127.0.0.1','--server.port=0',
    '--spring.profiles.active=desenvolvimento','--rotabrasil.seguranca.ativa=false',
    '--spring.datasource.url=jdbc:h2:file:'+path.join(isolated,'banco').replaceAll('\\','/')+';WRITE_DELAY=0'+(novo?'':';IFEXISTS=TRUE'),
    '--spring.flyway.enabled='+novo,'--spring.h2.console.enabled=false','--rotabrasil.email.modo=SIMULADO',
    '--rotabrasil.arquivos.viagens='+path.join(isolated,'acervo'),'--rotabrasil.exportacao.raiz='+exportRoot],{cwd:root});
  server.stdout.on('data',data=>{atual+=data;logs+=data;});server.stderr.on('data',data=>{atual+=data;logs+=data;});
  for(let i=0;i<1200;i++){const port=atual.match(/Tomcat started on port (\d+)/)?.[1];if(port){url='http://127.0.0.1:'+port;return;}if(server.exitCode!==null)throw Error('Servidor terminou');await wait(100);}throw Error('Servidor não iniciou');
}
async function parar(){if(!server||server.exitCode!==null)return;const fim=new Promise(resolve=>server.once('exit',resolve));server.kill();await Promise.race([fim,wait(15000)]);}
async function abrir(){page=await browser.newPage({viewport:{width:1440,height:1000},locale:'pt-BR',reducedMotion:'reduce'});page.on('pageerror',e=>errors.push(e.message));await page.goto(url+'/painel',{waitUntil:'networkidle'});await page.locator('#abertura').waitFor({state:'hidden'});}
async function navegar(hash){await page.evaluate(h=>{location.hash=h;},hash);await page.waitForFunction(h=>document.querySelector('main').dataset.paginaAtual===h,hash);if(hash==='configuracoes')await page.waitForFunction(()=>!document.getElementById('campos-configuracoes').disabled);}
async function get(rota){const r=await page.request.get(url+rota);assert.equal(r.status(),200);return r.json();}
async function salvar(){const r=page.waitForResponse(r=>r.url().endsWith('/api/configuracoes')&&r.request().method()==='PUT');await page.locator('#salvar-configuracoes').click();assert.equal((await r).status(),200);await page.waitForFunction(()=>document.getElementById('estado-configuracoes').textContent.startsWith('Configuração salva'));}
async function revelarTexto(alvo){await alvo.waitFor({state:'attached'});await alvo.evaluate(n=>{for(let p=n.parentElement;p;p=p.parentElement)if(p.tagName==='DETAILS')p.open=true;});await alvo.waitFor({state:'visible'});}
async function run(){
  await iniciar(true);browser=await chromium.launch({channel:process.env.ROTABRASIL_BROWSER_CHANNEL||'chrome',headless:true});await abrir();await navegar('configuracoes');
  assert.match(await page.locator('#config-local-pasta').textContent(),/pasta autorizada/);assert.match((await get('/api/configuracoes')).localDaPasta,/backend/);assert.equal(await page.locator('#config-remetente').inputValue(),'');
  await page.locator('#config-remetente').fill('operacao@example.invalid');assert.match(await page.locator('#estado-configuracoes').textContent(),/ainda não salvas/);
  await page.route('**/api/configuracoes',async route=>{if(route.request().method()==='PUT')await route.fulfill({status:500,contentType:'application/json',body:JSON.stringify({mensagem:'Falha sintética ao salvar'})});else await route.continue();});
  await page.locator('#salvar-configuracoes').click();await page.waitForFunction(()=>document.getElementById('estado-configuracoes').textContent.includes('Falha sintética'));
  assert.equal((await get('/api/configuracoes')).remetente,null);await page.unroute('**/api/configuracoes');await salvar();
  checks.push('Oitava tela própria, indicação do computador backend, alterações não salvas e falha de backend sem falso sucesso');
  await page.locator('#testar-config-email').evaluate(n=>n.closest('details').open=true);
  await page.locator('#config-nova-subpasta').evaluate(n=>n.closest('details').open=true);
  await page.locator('#config-nova-subpasta').fill('conferidos');await page.locator('#criar-config-subpasta').click();await page.waitForFunction(()=>document.getElementById('estado-configuracoes').textContent.includes('Subpasta criada'));
  await page.locator('#config-auto-exportacao').check();await salvar();await page.locator('#testar-config-pasta').click();await page.waitForFunction(()=>document.getElementById('config-resultados-testes').textContent.includes('GRAVAVEL'));
  await page.locator('#config-email-teste').fill('teste@example.invalid');await page.locator('#testar-config-email').click();await page.waitForFunction(()=>document.getElementById('config-resultados-testes').textContent.includes('SIMULADO'));
  await page.locator('#testar-config-email').click();await page.waitForFunction(()=>!document.getElementById('testar-config-email').disabled);
  assert.equal((await get('/api/configuracoes/testes')).filter(t=>t.tipo==='EMAIL').length,1);
  checks.push('Subpasta criada e salva, teste real de gravação e simulação de e-mail idempotente sem SMTP');
  const xml=fs.readFileSync(path.join(root,'back/src/main/resources/email/cte-sintetico.xml'),'utf8'),key='35250412345678000195570020000662131001747719';
  const base=key.slice(0,25)+'000066214'+key.slice(34,43);let soma=0;[...base].reverse().forEach((c,i)=>soma+=Number(c)*(2+i%8));const dv=11-soma%11;
  const secondXml=xml.replaceAll(key,base+(dv>=10?0:dv)).replace('<nCT>66213</nCT>','<nCT>66214</nCT>');
  const zip=Buffer.from(await page.evaluate(async({xml,secondXml})=>{
    const {PDFDocument,StandardFonts}=await import('/webjars/pdf-lib/1.17.1/dist/pdf-lib.esm.min.js');const d=await PDFDocument.create(),font=await d.embedFont(StandardFonts.Helvetica);d.addPage().drawText('FATURA SINTETICA - SEM VALOR FISCAL',{x:40,y:700,font,size:14});
    const {zipSync,strToU8}=await import('/webjars/fflate/0.8.3/esm/browser.js');return Array.from(zipSync({'cte.xml':strToU8(xml),'outro.xml':strToU8(secondXml),'fatura.pdf':await d.save()},{level:0}));
  },{xml,secondXml}));
   fs.writeFileSync(path.join(out,'fixture-sintetica.zip'),zip);
   await navegar('pacotes');await page.locator('#arquivo-zip').setInputFiles({name:'fixture.zip',mimeType:'application/zip',buffer:zip});
  // O upload cotidiano não expõe campos opcionais legados. Exercitar o contrato
  // explícito pela API preserva a cobertura sem reabrir controles ocultos na interface.
  const recebida=await page.request.post(url+'/api/pacotes/inspecao?destinatarioConfirmacao=origem%40example.invalid&simularConfirmacao=true',{multipart:{arquivo:{name:'fixture.zip',mimeType:'application/zip',buffer:zip}}});assert.equal(recebida.status(),200);
  const recebimento=await get('/api/recebimentos/'+recebida.headers()['x-recebimento-id']);assert.equal(recebimento.destinatario,'origem@example.invalid');assert.ok(recebimento.notificacoes.some(n=>n.mensagem.includes('não confirma leitura')));
  await page.locator('#inspecionar-zip').click();
  await page.locator('#vinculo-a2 select').selectOption('FATURA');for(const box of await page.locator('#vinculo-a2 input[type=checkbox]').all())await box.check();
  assert.equal(await page.locator('#destinatario').inputValue(),'');
  await page.locator('#zip-avancar').click();
  checks.push('Destinatário explícito do upload separado do cliente; aviso de recebimento não promete importação');
  fs.renameSync(exportRoot,exportRoot+'-indisponivel');
  const importResponse=page.waitForResponse(r=>r.url().includes('/api/pacotes/confirmacao')&&r.request().method()==='POST');await page.locator('#confirmar-zip').click();const imported=await(await importResponse).json();
  await page.waitForFunction(()=>document.getElementById('resultado-exportacao').textContent.includes('Importado; exportação pendente'));
  assert.equal((await get('/api/documentos')).length,2);const id=imported.pacoteId;
  fs.renameSync(exportRoot+'-indisponivel',exportRoot);await page.locator('#resultado-exportacao').getByRole('button',{name:'Tentar somente a exportação'}).click();
  await page.waitForFunction(()=>document.getElementById('resultado-exportacao').textContent.includes('Exportação concluída'));
  const e=(await get(`/api/pacotes/${id}/exportacoes`))[0];assert.equal(e.situacao,'CONCLUIDA');
  for(const item of e.arquivos)assert.equal(digest(fs.readFileSync(path.join(exportRoot,e.subpasta,item.localizacao))),item.sha256);
  assert.equal(e.arquivos.filter(a=>a.origem==='ANEXO').length,1);assert.match(e.arquivos.find(a=>a.origem==='ANEXO').viagens,/66213/);assert.match(e.arquivos.find(a=>a.origem==='ANEXO').viagens,/66214/);
  checks.push('Falha automática preserva importação; retomada copia XMLs/PDF sem reimportar e mantém fatura compartilhada única com SHA-256');
  await navegar('pacotes-importados');const row=page.locator(`[data-pacote-id="${id}"]`);await row.getByRole('button',{name:'Exportar arquivos',exact:true}).click();await revelarTexto(row.locator('.export-result').getByText('Exportação concluída na pasta autorizada do backend.',{exact:true}));
  assert.equal((await get(`/api/pacotes/${id}/exportacoes`)).length,1);
  await row.getByRole('button',{name:'Ver avisos',exact:true}).click();await row.getByText('Eventos internos — testes sem envio',{exact:true}).click();await row.locator('.export-result').getByText(/EXPORTACAO_CONCLUIDA/).waitFor();
  checks.push('Ação manual idempotente e consulta dos eventos separados no pacote importado');
  await navegar('configuracoes');await page.locator('#config-nova-subpasta').fill('nova-saida');await page.locator('#criar-config-subpasta').click();await page.waitForFunction(()=>document.getElementById('estado-configuracoes').textContent.includes('Subpasta criada'));
  await page.locator('#config-auto-exportacao').uncheck();await salvar();assert.equal((await get(`/api/pacotes/${id}/exportacoes`)).length,1);
  assert.equal(fs.readdirSync(path.join(exportRoot,'nova-saida')).length,0);
  checks.push('Alteração de destino e desativação da cópia automática não exportam o acervo retroativamente');
  await page.screenshot({path:path.join(out,'configuracoes-desktop.png'),fullPage:true});await page.close();await parar();await iniciar(false);await abrir();await navegar('configuracoes');
  assert.equal(await page.locator('#config-remetente').inputValue(),'operacao@example.invalid');assert.equal(await page.locator('#config-subpasta').inputValue(),'nova-saida');assert.equal(await page.locator('#config-auto-exportacao').isChecked(),false);
  await page.locator('#config-historico-testes > summary').click();await page.waitForFunction(()=>document.getElementById('config-resultados-testes').textContent.includes('SIMULADO'));assert.match(await page.locator('#config-resultados-testes').textContent(),/SIMULADO/);assert.equal((await get(`/api/pacotes/${id}/exportacoes`))[0].id,e.id);
  checks.push('Configuração, testes e histórico de exportação sobrevivem ao encerramento/reinício com Flyway desativado');
  await page.setViewportSize({width:390,height:844});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await page.screenshot({path:path.join(out,'configuracoes-mobile.png'),fullPage:true});
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'resultado.json'),JSON.stringify({checks,errors},null,2));console.log(JSON.stringify({checks,errors},null,2));
}
run().catch(async error=>{console.error(error);fs.writeFileSync(path.join(out,'servidor.log'),logs);if(page&&!page.isClosed())await page.screenshot({path:path.join(out,'falha.png'),fullPage:true}).catch(()=>{});process.exitCode=1;}).finally(async()=>{await browser?.close();await parar();fs.rmSync(isolated,{recursive:true,force:true,maxRetries:5,retryDelay:500});});
