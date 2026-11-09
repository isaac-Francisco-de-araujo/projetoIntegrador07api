/* Validação funcional do frontend com API real, H2 e fixtures sintéticas. */
'use strict';
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');
const [jarArg,outArg] = process.argv.slice(2);
if (!jarArg || !outArg) throw Error('Uso: node scripts/testar-modernizacao.cjs JAR SAIDA_EXTERNA');
const root=path.resolve(__dirname,'..'),out=path.resolve(outArg);
if(out===root||out.startsWith(root+path.sep))throw Error('A saída deve ficar fora do repositório');
fs.mkdirSync(out,{recursive:true});
const isolated=fs.mkdtempSync(path.join(out,'.acervo-'));
let logs='',browser;const checks=[],errors=[],adminRequests=[];
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const server=spawn(process.env.JAVA||'java',['-Xmx512m','-jar',path.resolve(jarArg),'--server.address=127.0.0.1','--server.port=0',
  '--spring.profiles.active=desenvolvimento','--rotabrasil.seguranca.ativa=false','--spring.datasource.url=jdbc:h2:mem:modernizacao_ui',
  '--spring.flyway.enabled=true','--spring.h2.console.enabled=false','--rotabrasil.email.modo=SIMULADO','--rotabrasil.arquivos.viagens='+isolated+'/viagens'],{cwd:root});
server.stdout.on('data',d=>logs+=d);server.stderr.on('data',d=>logs+=d);
function cnpj(base){const dv=text=>{let weight=text.length-7,sum=0;for(const digit of text){sum+=Number(digit)*weight--;if(weight<2)weight=9;}const rest=sum%11;return rest<2?0:11-rest;};const first=base+dv(base);return first+dv(first);}
async function run(){
  let port;for(let i=0;i<900;i++){port=logs.match(/Tomcat started on port (\d+)/)?.[1];if(port)break;if(server.exitCode!==null)throw Error('Servidor terminou');await wait(100);}
  if(!port)throw Error('Servidor não iniciou');const url='http://127.0.0.1:'+port;
  browser=await chromium.launch({channel:process.env.ROTABRASIL_BROWSER_CHANNEL||'chrome',headless:true});
  const page=await browser.newPage({viewport:{width:1440,height:1000},locale:'pt-BR',reducedMotion:'reduce'});
  async function navegar(id){await page.evaluate(hash=>{location.hash=hash;},id);await page.waitForFunction(route=>document.querySelector('main').dataset.paginaAtual===route,id==='documentos'?'viagens':id);}
  page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(r.url().includes('/api/admin/'))adminRequests.push(r.url());});
  await page.addInitScript(()=>document.addEventListener('workspace-pronto',()=>{window.__readyAt=performance.now();}));
  await page.goto(url,{waitUntil:'domcontentloaded'});await page.locator('#abertura').waitFor({state:'visible'});
  await page.locator('#abertura').waitFor({state:'hidden'});assert.ok(await page.evaluate(()=>window.__readyAt>=5000));
  assert.equal(await page.locator('html').getAttribute('data-tema'),'escuro');
  assert.equal(await page.locator('#administracao,.workspace,.sidebar-note').count(),0);
  assert.equal((await page.locator('body').innerText()).includes('Ambiente de testes'),false);
  await page.reload({waitUntil:'domcontentloaded'});await page.waitForFunction(()=>window.__readyAt>=5000);await page.locator('#abertura').waitFor({state:'hidden'});
  checks.push('Loading mínimo de cinco segundos em visitas repetidas; tema escuro e blocos solicitados removidos');
  assert.ok(await page.locator('.hero-banner').evaluate(e=>e.getBoundingClientRect().height<350));
  assert.equal(await page.locator('#total-viagens').textContent(),'0');
  assert.ok((await page.locator('.stat-card-link').evaluateAll(items=>items.map(e=>getComputedStyle(e).backgroundColor))).every(color=>color==='rgba(0, 0, 0, 0)'));
  await page.locator('.stats article').first().click({position:{x:12,y:12}});assert.equal(new URL(page.url()).hash,'#clientes');
  checks.push('Banner compacto e card inteiro acessível como link para sua seção');

  const xml=fs.readFileSync(path.join(root,'src/main/resources/email/cte-sintetico.xml'));
  await navegar('importacao');
  await page.locator('#arquivo').setInputFiles({name:'cte-sintetico.xml',mimeType:'application/xml',buffer:xml});
  await page.locator('#importar').click();await page.waitForFunction(()=>document.getElementById('total-viagens').textContent==='1');
  const baseTrip=(await (await page.request.get(url+'/api/viagens')).json()).find(t=>t.numero==='66213');
  const initialDocs=await (await page.request.get(url+`/api/viagens/${baseTrip.id}/documentos`)).json();
  const clients=[];
  for(let i=0;i<3;i++){
    const r=await page.request.post(url+'/api/clientes',{data:{nome:`Cliente de Cenário ${i+1}`,cnpj:cnpj('12345678'+String(i+2).padStart(4,'0')),email:`cliente${i+1}@example.invalid`,telefone:'1100000000'}});
    assert.equal(r.status(),201);clients.push(await r.json());
  }
  for(let i=0;i<27;i++){
    const r=await page.request.post(url+'/api/viagens',{data:{numero:String(90000+i),clienteId:clients[i%3].id,data:i===26?null:`2026-10-${String(i+1).padStart(2,'0')}`,status:i%2?'Conferida':'Recebida'}});assert.equal(r.status(),201);
  }
  await page.locator('#atualizar').click();await page.waitForFunction(()=>document.getElementById('total-viagens').textContent==='28');
  assert.equal(await page.locator('#lista-ultimas-viagens tr').count(),5);assert.equal(await page.locator('#lista-viagens tr').count(),20);
  await navegar('viagens');
  await page.locator('#viagens-proxima').click();assert.equal(await page.locator('#lista-viagens tr').count(),8);
  await page.locator('#filtro-cliente').selectOption(String(clients[0].id));assert.equal(await page.locator('#lista-viagens tr').count(),9);
  await page.locator('#filtro-situacao').fill('Conferida');assert.equal(await page.locator('#lista-viagens tr').count(),4);
  await page.locator('#limpar-filtros').click();await page.locator('[data-ordenar="numero"]').click();
  assert.equal(await page.locator('#lista-viagens tr:first-child td:first-child strong').textContent(),'66213');
  await page.locator('[data-ordenar="numero"]').click();assert.equal(await page.locator('#lista-viagens tr:first-child td:first-child strong').textContent(),'90026');
  await page.locator('#viagens details').filter({has:page.locator('#filtro-inicio')}).locator('summary').click();await page.locator('#filtro-inicio').fill('2026-10-20');await page.locator('#filtro-inicio').dispatchEvent('change');
  await page.locator('#filtro-fim').fill('2026-10-10');await page.locator('#filtro-fim').dispatchEvent('change');
  assert.match(await page.locator('#resultado-consulta-viagens').textContent(),/Revise/);await page.locator('#limpar-filtros').click();
  checks.push('XML preservado; últimas viagens, paginação, filtro global por cliente/status, datas e ordenação funcionam com 28 viagens reais da fixture');

  await navegar('clientes');
  await page.locator('#buscar-cliente').fill(clients[1].cnpj.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/,'$1.$2.$3/$4-$5'));assert.equal(await page.locator('#lista-clientes tr').count(),1);
  await page.locator('#buscar-cliente').fill('Cliente de Cenário 2');assert.equal(await page.locator('#lista-clientes tr').count(),1);
  await page.locator('#lista-clientes button').filter({hasText:'Detalhes'}).click();
  await page.waitForFunction(()=>document.getElementById('detalhes-titulo').textContent==='Cliente de Cenário 2');
  assert.match(await page.locator('#detalhes-conteudo').textContent(),/cliente2@example.invalid/);
  await page.locator('#acao-detalhes').click();assert.equal(await page.locator('#filtro-cliente').inputValue(),String(clients[1].id));
  assert.equal(await page.locator('#lista-viagens tr').count(),9);await page.locator('#limpar-filtros').click();
  await page.locator('#lista-viagens button').filter({hasText:'Detalhes'}).first().click();
  await page.locator('#acao-detalhes').waitFor({state:'visible'});await page.locator('#acao-detalhes').click();
  assert.match(await page.locator('#contexto-documentos').textContent(),/Viagem/);
  checks.push('Busca e detalhes de cliente via API, viagens associadas e detalhes da viagem com acesso aos documentos');

  const pdf=Buffer.from(await page.evaluate(async()=>{
    const {PDFDocument,StandardFonts}=await import('/webjars/pdf-lib/1.17.1/dist/pdf-lib.esm.min.js');
    const doc=await PDFDocument.create();const font=await doc.embedFont(StandardFonts.Helvetica);doc.addPage([595,842]).drawText('PDF AVULSO SINTETICO - SEM VALOR FISCAL',{x:40,y:750,font,size:14});return Array.from(await doc.save());
  }));
  async function prepararPdf(){
    await navegar('importacao');
    await page.locator('#arquivo').setInputFiles({name:'comprovante-sintetico.pdf',mimeType:'application/pdf',buffer:pdf});
    await page.locator('#pdf-viagem-base').selectOption(String(baseTrip.id));
    await page.waitForFunction(()=>!document.getElementById('pdf-xml-base').disabled);
    await page.locator('#pdf-xml-base').selectOption(String(initialDocs[0].id));
    const response=page.waitForResponse(r=>r.url().includes('/api/pacotes/inspecao')&&r.request().method()==='POST');
    await page.locator('#importar').click();return (await response).json();
  }
  async function confirmarPdf(){
    await page.locator('#vinculo-a1 select').selectOption('VIAGEM');await page.locator('#vinculo-a1 input[type=checkbox]').check();
    const response=page.waitForResponse(r=>r.url().includes('/api/pacotes/confirmacao')&&r.request().method()==='POST');
    await page.locator('#confirmar-zip').click();const result=await (await response).json();
    await page.waitForFunction(()=>!document.getElementById('atualizar').disabled&&document.getElementById('form-pacote').getAttribute('aria-busy')==='false'&&document.getElementById('resumo-zip').textContent.includes('Importação confirmada'));return result;
  }
  const first=await prepararPdf();assert.equal(await page.locator('#confirmar-zip').isDisabled(),true);await confirmarPdf();
  const second=await prepararPdf();assert.equal(first.sha256,second.sha256);assert.equal((await confirmarPdf()).reutilizado,true);
  const docs=await (await page.request.get(url+`/api/viagens/${baseTrip.id}/documentos`)).json();const annexes=await (await page.request.get(url+`/api/viagens/${baseTrip.id}/anexos`)).json();
  assert.equal(docs.length,1);assert.equal(docs[0].sha256,initialDocs[0].sha256);assert.equal(annexes.length,1);
  checks.push('PDF avulso reutiliza CT-e escolhido sem modificar XML; passa pela conferência ZIP e repetição explícita não duplica o anexo');

  await navegar('documentos');await page.locator('#viagem-selecionada').selectOption(String(baseTrip.id));
  await page.locator('#lista-documentos input[type=checkbox]').first().check();await page.locator('#lista-anexos input[type=checkbox]').first().check();
  await navegar('email');await page.locator('#destinatario').fill('conferencia@example.invalid');await page.locator('#gerar-previa').click();
  await page.locator('#resultado-previa').waitFor({state:'visible'});assert.match(await page.locator('#contexto-email').textContent(),/66213/);
  assert.equal(await page.locator('#simular-envio').isDisabled(),true);await page.locator('#conferi-envio').check();await page.locator('#simular-envio').click();
  await page.waitForFunction(()=>document.getElementById('resultado-tentativa').textContent.includes('SIMULADO'));
  checks.push('Central de envios identifica a viagem, confere XML/PDF e registra somente simulação após confirmação explícita');

  await page.route('**/api/clientes',route=>route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({mensagem:'Fixture: consulta indisponível.'})}));
  await page.locator('#atualizar').click();await page.waitForFunction(()=>document.getElementById('indicadores').classList.contains('has-error'));
  assert.equal(await page.locator('#total-viagens').textContent(),'—');assert.equal(await page.locator('#gerar-previa').isDisabled(),true);
  assert.match(await page.locator('#lista-ultimas-viagens').textContent(),/Não foi possível/);
  await page.unroute('**/api/clientes');await page.locator('#atualizar').click();await page.waitForFunction(()=>document.getElementById('total-viagens').textContent==='28');
  checks.push('Falha de API limpa indicadores e seleção de envio, mostra erro e recupera dados reais na atualização');

  await page.locator('#menu-compactar').click();assert.ok(await page.locator('body').evaluate(b=>b.classList.contains('menu-compacto')));
  assert.ok(await page.locator('.sidebar').evaluate(e=>e.getBoundingClientRect().width<100));await page.locator('#menu-compactar').click();
  await navegar('inicio');
  for(const width of [360,390,768,1024,1440,1920]){
    await page.setViewportSize({width,height:1000});await page.evaluate(()=>window.scrollTo(0,0));
    const overflow=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth,items:[...document.querySelectorAll('body>*,main>*,.topbar>*,.topbar-tools>*,.filters>*,.upload-card>*,.bottom-grid>*')].map(e=>({tag:e.tagName,id:e.id,cls:e.className,right:e.getBoundingClientRect().right,width:e.getBoundingClientRect().width,scroll:e.scrollWidth})).filter(e=>e.right>innerWidth+1)}));
    if(overflow.scroll>width+1){await page.screenshot({path:path.join(out,'overflow.png'),fullPage:true});console.log(JSON.stringify(overflow,null,2));console.log(await page.evaluate(()=>[...document.querySelectorAll('body *')].filter(e=>e.getBoundingClientRect().right>innerWidth+1).map(e=>({tag:e.tagName,id:e.id,cls:e.className,right:e.getBoundingClientRect().right,parent:e.parentElement?.className,overflow:getComputedStyle(e.parentElement).overflowX})).slice(0,90)));}
    assert.ok(overflow.scroll<=width+1,`Overflow em ${width}`);
    if(width===390||width===1440)await page.screenshot({path:path.join(out,`dashboard-${width}.png`),fullPage:true});
  }
  await page.setViewportSize({width:390,height:844});assert.equal(await page.locator('#menu-lateral').evaluate(e=>e.inert),true);
  await page.locator('#menu-abrir').click();assert.equal(await page.locator('main').evaluate(e=>e.inert),true);
  await page.locator('.sidebar nav a[href="#email"]').click();assert.equal(await page.locator('main').evaluate(e=>e.inert),false);
  assert.equal(await page.locator('#menu-lateral').evaluate(e=>e.inert),true);await page.locator('#menu-abrir').click();await page.keyboard.press('Escape');
  assert.equal(await page.evaluate(()=>document.activeElement.id),'menu-abrir');
  checks.push('Menu desktop recolhível, drawer mobile com teclado/foco e layout sem overflow de 360 a 1920 px');
  await page.keyboard.press('Control+k');await page.locator('#buscar-comando').fill('clientes');await page.keyboard.press('Enter');
  await page.locator('#central-comandos').waitFor({state:'hidden'});checks.push('Central de atalhos preservada');
  assert.deepEqual(errors,[]);assert.deepEqual(adminRequests,[]);
  checks.push('Nenhum erro JavaScript e nenhuma chamada à administração removida da interface');
  fs.writeFileSync(path.join(out,'resultado.json'),JSON.stringify({checks,errors,adminRequests},null,2));console.log(JSON.stringify({checks,errors,adminRequests},null,2));
}
run().catch(error=>{console.error(error);fs.writeFileSync(path.join(out,'servidor.log'),logs);process.exitCode=1;}).finally(async()=>{
  await browser?.close();server.kill();await Promise.race([new Promise(resolve=>server.once('exit',resolve)),wait(10000)]);fs.rmSync(isolated,{recursive:true,force:true,maxRetries:5,retryDelay:500});
});
