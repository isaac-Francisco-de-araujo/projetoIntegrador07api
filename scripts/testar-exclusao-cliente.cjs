/* Exclusão de cliente: navegador, backend real, H2 e arquivos exclusivamente sintéticos. */
'use strict';
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const [jarArg,outArg]=process.argv.slice(2);
if(!jarArg||!outArg)throw Error('Uso: node scripts/testar-exclusao-cliente.cjs JAR SAIDA_EXTERNA');
const root=path.resolve(__dirname,'..'),out=path.resolve(outArg);
if(out===root||out.startsWith(root+path.sep))throw Error('Use saída externa');
fs.mkdirSync(out,{recursive:true});const isolated=fs.mkdtempSync(path.join(out,'.isolado-'));
let logs='',browser,page;const checks=[],errors=[];
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const server=spawn(process.env.JAVA||'java',['-Xmx512m','-jar',path.resolve(jarArg),
  '--server.address=127.0.0.1','--server.port=0','--spring.profiles.active=desenvolvimento',
  '--rotabrasil.seguranca.ativa=false','--spring.datasource.url=jdbc:h2:mem:exclusao_cliente_ui',
  '--spring.flyway.enabled=true','--spring.h2.console.enabled=false','--rotabrasil.email.modo=SIMULADO',
  '--rotabrasil.arquivos.viagens='+isolated+'/viagens'],{cwd:root});
server.stdout.on('data',data=>logs+=data);server.stderr.on('data',data=>logs+=data);
async function run(){
  let port;for(let i=0;i<900;i++){port=logs.match(/Tomcat started on port (\d+)/)?.[1];if(port)break;if(server.exitCode!==null)throw Error('Servidor terminou');await wait(100);}
  if(!port)throw Error('Servidor não iniciou');const url='http://127.0.0.1:'+port;
  browser=await chromium.launch({channel:process.env.ROTABRASIL_BROWSER_CHANNEL||'chrome',headless:true});
  page=await browser.newPage({viewport:{width:1440,height:1000},locale:'pt-BR',reducedMotion:'reduce'});
  page.on('pageerror',error=>errors.push(error.message));
  async function get(rota){const r=await page.request.get(url+rota);assert.equal(r.status(),200);return r.json();}
  async function navegar(hash){await page.evaluate(h=>{location.hash=h;},hash);await page.waitForFunction(h=>document.querySelector('main').dataset.paginaAtual===h,hash);}
  await page.goto(url,{waitUntil:'networkidle'});await page.locator('#abertura').waitFor({state:'hidden'});
  const xml=fs.readFileSync(path.join(root,'back/src/main/resources/email/cte-sintetico.xml'),'utf8');
  const key='35250412345678000195570020000662131001747719';
  const base=key.slice(0,25)+'000066214'+key.slice(34,43);let soma=0;[...base].reverse().forEach((c,i)=>soma+=Number(c)*(2+i%8));const dv=11-soma%11;
  const secondXml=xml.replaceAll(key,base+(dv>=10?0:dv)).replace('<nCT>66213</nCT>','<nCT>66214</nCT>')
    .replace('<CNPJ>00000000000000</CNPJ>','<CNPJ>11111111111111</CNPJ>').replace('CLIENTE SINTETICO - SEM VALOR FISCAL','OUTRO CLIENTE SINTETICO');
  const zip=Buffer.from(await page.evaluate(async({xml,secondXml})=>{
    const {PDFDocument,StandardFonts}=await import('/webjars/pdf-lib/1.17.1/dist/pdf-lib.esm.min.js');
    const doc=await PDFDocument.create();const font=await doc.embedFont(StandardFonts.Helvetica);doc.addPage().drawText('PDF SINTETICO - SEM VALOR FISCAL',{x:40,y:700,font,size:14});const pdf=await doc.save();
    const {zipSync,strToU8}=await import('/webjars/fflate/0.8.3/esm/browser.js');
    return Array.from(zipSync({'primeiro.xml':strToU8(xml),'segundo.xml':strToU8(secondXml),'compartilhado.pdf':pdf,'exclusivo.pdf':pdf},{level:0}));
  },{xml,secondXml}));
  await navegar('pacotes');await page.locator('#arquivo-zip').setInputFiles({name:'clientes-sinteticos.zip',mimeType:'application/zip',buffer:zip});await page.locator('#inspecionar-zip').click();
  await page.locator('#vinculo-a2 select').selectOption('FATURA');for(const box of await page.locator('#vinculo-a2 input[type=checkbox]').all())await box.check();
  await page.locator('#vinculo-a3 select').selectOption('VIAGEM');await page.locator('#vinculo-a3 input[type=checkbox]').first().check();
  await page.locator('#zip-avancar').click();const importedResponse=page.waitForResponse(r=>r.url().includes('/api/pacotes/confirmacao')&&r.request().method()==='POST');await page.locator('#confirmar-zip').click();
  const imported=await(await importedResponse).json();await page.waitForFunction(()=>!document.getElementById('atualizar').disabled);
  const clientes=await get('/api/clientes'),cliente=clientes.find(c=>c.cnpj==='00000000000000');assert.equal(clientes.length,2);
  const shared=imported.anexos.find(a=>a.finalidade==='FATURA'),sole=imported.anexos.find(a=>a.finalidade==='VIAGEM');
  const originalShared=await(await page.request.get(url+`/api/anexos/${shared.id}/arquivo`)).body();
  checks.push('ZIP real importa dois clientes, dois XMLs, PDF exclusivo e fatura compartilhada');
  await navegar('clientes');async function excluirPelosDetalhes(){await page.locator('#lista-clientes tr').filter({hasText:cliente.nome}).getByRole('button',{name:'Detalhes',exact:true}).click();await page.locator(`[data-excluir-cliente="${cliente.id}"]`).click();}await excluirPelosDetalhes();
  await page.locator('#confirmar-exclusao').waitFor({state:'visible'});
  assert.match(await page.locator('#descricao-exclusao').textContent(),/1 viagem\(ns\), 1 documento\(s\), 1 PDF\(s\)/);
  assert.match(await page.locator('#regra-exclusao').textContent(),/1 PDF\(s\) compartilhado/);
  await page.locator('#cancelar-exclusao').click();assert.equal((await get('/api/clientes')).length,2);
  checks.push('Diálogo mostra contagens e compartilhamento; cancelar preserva todos os registros');
  await excluirPelosDetalhes();await page.locator('#confirmar-exclusao').waitFor({state:'visible'});
  const nova=await page.request.post(url+'/api/viagens',{data:{numero:'66215',clienteId:cliente.id}});assert.equal(nova.status(),201);
  const stale=page.waitForResponse(r=>r.url().includes(`/api/clientes/${cliente.id}?`)&&r.request().method()==='DELETE');await page.locator('#executar-exclusao').click();assert.equal((await stale).status(),409);
  await page.locator('#confirmar-exclusao').waitFor({state:'hidden'});assert.equal((await get('/api/clientes')).length,2);
  checks.push('Mudança de vínculos após abrir a confirmação exige nova conferência (409)');
  await excluirPelosDetalhes();await page.locator('#confirmar-exclusao').waitFor({state:'visible'});await page.screenshot({path:path.join(out,'confirmacao-cliente.png')});
  const deleted=page.waitForResponse(r=>r.url().includes(`/api/clientes/${cliente.id}?`)&&r.request().method()==='DELETE');await page.locator('#executar-exclusao').click();assert.equal((await deleted).status(),204);
  await page.locator('#confirmar-exclusao').waitFor({state:'hidden'});await page.waitForFunction(()=>!document.getElementById('atualizar').disabled);
  assert.equal((await get('/api/clientes')).length,1);assert.equal((await get('/api/viagens')).length,1);assert.equal((await get('/api/documentos')).length,1);
  assert.equal(await page.locator(`[data-excluir-cliente="${cliente.id}"]`).count(),0);
  assert.equal((await page.request.get(url+`/api/anexos/${sole.id}/arquivo`)).status(),404);
  assert.deepEqual(await(await page.request.get(url+`/api/anexos/${shared.id}/arquivo`)).body(),originalShared);
  const outraViagem=(await get('/api/viagens'))[0];const anexos=await get(`/api/viagens/${outraViagem.id}/anexos`);assert.deepEqual(anexos[0].viagemIds,[outraViagem.id]);
  checks.push('Confirmar remove cliente, todas as suas viagens e documentos; outro cliente e PDF compartilhado permanecem utilizáveis');
  await page.reload({waitUntil:'networkidle'});await page.locator('#abertura').waitFor({state:'hidden'});assert.equal((await get('/api/clientes')).length,1);
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>{location.hash='clientes';});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
  checks.push('Dados permanecem corretos após recarga e a tela de clientes funciona em largura móvel');
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'resultado.json'),JSON.stringify({checks,errors},null,2));console.log(JSON.stringify({checks,errors},null,2));
}
run().catch(async error=>{console.error(error);fs.writeFileSync(path.join(out,'servidor.log'),logs);if(page)await page.screenshot({path:path.join(out,'falha.png'),fullPage:true}).catch(()=>{});process.exitCode=1;}).finally(async()=>{
  await browser?.close();server.kill();await Promise.race([new Promise(resolve=>server.once('exit',resolve)),wait(10000)]);fs.rmSync(isolated,{recursive:true,force:true,maxRetries:5,retryDelay:500});
});
