/* Diagnóstico/recuperação: backend real, H2/arquivos sintéticos e um servidor isolado por execução. */
'use strict';
const {chromium}=require('playwright'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {spawn}=require('node:child_process');const {createHash}=require('node:crypto');
const [jarArg,outArg]=process.argv.slice(2);if(!jarArg||!outArg)throw Error('Uso: node scripts/testar-diagnostico-recuperacao.cjs JAR SAIDA_EXTERNA');
const root=path.resolve(__dirname,'..'),out=path.resolve(outArg);if(out===root||out.startsWith(root+path.sep))throw Error('Use saída externa');
fs.mkdirSync(out,{recursive:true});const isolated=fs.mkdtempSync(path.join(out,'.isolado-')),destino=path.join(isolated,'exportados');fs.mkdirSync(destino);
let server,logs='',url,browser,page;const checks=[],errors=[],mutations=[];const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const digest=b=>createHash('sha256').update(b).digest('hex');
async function iniciar(novo){let atual='';server=spawn(process.env.JAVA||'java',['-Xmx512m','-jar',path.resolve(jarArg),'--server.address=127.0.0.1','--server.port=0',
  '--spring.profiles.active=desenvolvimento','--rotabrasil.seguranca.ativa=false','--spring.datasource.url=jdbc:h2:file:'+path.join(isolated,'banco').replaceAll('\\','/')+';WRITE_DELAY=0'+(novo?'':';IFEXISTS=TRUE'),
  '--spring.flyway.enabled='+novo,'--spring.h2.console.enabled=false','--rotabrasil.email.modo=SIMULADO','--rotabrasil.arquivos.viagens='+path.join(isolated,'acervo'),'--rotabrasil.exportacao.raiz='+destino],{cwd:root});
  server.stdout.on('data',data=>{atual+=data;logs+=data;});server.stderr.on('data',data=>{atual+=data;logs+=data;});
  for(let i=0;i<1200;i++){const port=atual.match(/Tomcat started on port (\d+)/)?.[1];if(port){url='http://127.0.0.1:'+port;return;}if(server.exitCode!==null)throw Error('Servidor terminou');await wait(100);}throw Error('Servidor não iniciou');
}
async function parar(){if(!server||server.exitCode!==null)return;const fim=new Promise(resolve=>server.once('exit',resolve));server.kill();await Promise.race([fim,wait(15000)]);}
async function abrir(){page=await browser.newPage({viewport:{width:1440,height:1000},locale:'pt-BR',reducedMotion:'reduce'});page.on('pageerror',error=>errors.push(error.name));page.on('request',r=>{if(r.url().includes('/api/')&&['POST','PUT','DELETE'].includes(r.method()))mutations.push({url:r.url(),method:r.method()});});
  await page.addInitScript(()=>{Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async text=>{window.suporteCopiado=text;}}});});
  await page.goto(url,{waitUntil:'networkidle'});await page.locator('#abertura').waitFor({state:'hidden'});
}
async function get(rota){const r=await page.request.get(url+rota);assert.equal(r.status(),200);return r.json();}
async function navegar(hash){await page.evaluate(h=>{location.hash=h;},hash);await page.waitForFunction(h=>document.querySelector('main').dataset.paginaAtual===h,hash);}
async function atualizarCentral(filtro='PRECISA_ACAO'){await navegar('processamentos');await page.locator('#filtro-processamentos').selectOption(filtro);await page.locator('#atualizar-processamentos').click();await page.waitForFunction(()=>document.getElementById('lista-processamentos').getAttribute('aria-busy')==='false');}
async function contraste(locator){return locator.evaluate(el=>{
  const c=getComputedStyle(el),rgb=s=>s.match(/[\d.]+/g).slice(0,3).map(Number),lum=a=>a.map(x=>{x/=255;return x<=.04045?x/12.92:((x+.055)/1.055)**2.4;}).reduce((s,x,i)=>s+x*[.2126,.7152,.0722][i],0);
  const a=lum(rgb(c.color)),b=lum(rgb(c.backgroundColor));return {contraste:(Math.max(a,b)+.05)/(Math.min(a,b)+.05),borda:rgb(c.borderLeftColor)};
});}
async function run(){
  await iniciar(true);browser=await chromium.launch({channel:process.env.ROTABRASIL_BROWSER_CHANNEL||'chrome',headless:true});await abrir();
  const xml=fs.readFileSync(path.join(root,'back/src/main/resources/email/cte-sintetico.xml'),'utf8');
  const pacotes=await page.evaluate(async xml=>{
    const {PDFDocument,StandardFonts}=await import('/webjars/pdf-lib/1.17.1/dist/pdf-lib.esm.min.js');const d=await PDFDocument.create(),font=await d.embedFont(StandardFonts.Helvetica);d.addPage().drawText('PDF SINTETICO - SEM VALOR FISCAL',{x:40,y:700,font,size:14});const pdf=await d.save();
    const {zipSync,strToU8}=await import('/webjars/fflate/0.8.3/esm/browser.js');const arquivos={'ctes/viagem.xml':strToU8(xml),'cobrancas/fatura.pdf':strToU8('invalido'),'outros/fatura.pdf':pdf};
    const ruim=Array.from(zipSync(arquivos,{level:0}));arquivos['cobrancas/fatura.pdf']=pdf;return {ruim,bom:Array.from(zipSync(arquivos,{level:0}))};
  },xml);
  await navegar('pacotes');await page.locator('#arquivo-zip').setInputFiles({name:'documentos.zip',mimeType:'application/zip',buffer:Buffer.from(pacotes.ruim)});await page.locator('#inspecionar-zip').click();
  const erro=page.locator('#resultado-operacional-zip > .diag-erro');await erro.waitFor();assert.match(await erro.textContent(),/Importação bloqueada/);assert.equal(await page.locator('#confirmar-zip').isDisabled(),true);
  assert.match(await page.locator('#diagnosticos-zip').textContent(),/cobrancas\/fatura.pdf/);assert.match(await page.locator('#diagnosticos-zip').textContent(),/outros\/fatura.pdf/);assert.equal((await get('/api/documentos')).length,0);
  checks.push('PDF inválido identificado por arquivo/caminho/etapa, outros arquivos examinados e importação bloqueada sem gravação parcial');
  for(const tema of ['escuro','claro']){
    if(await page.locator('html').getAttribute('data-tema')!==tema)await page.locator('#alternar-tema').click();
    const c=await contraste(erro);assert.ok(c.contraste>=4.5,`Contraste ${tema}: ${c.contraste}`);assert.ok(c.borda[0]>c.borda[1]&&c.borda[0]>c.borda[2]);await page.screenshot({path:path.join(out,`erro-${tema}.png`),fullPage:true});
  }
  const details=erro.locator(':scope > details');await details.locator(':scope > summary').focus();await page.keyboard.press('Enter');assert.equal(await details.evaluate(e=>e.open),true);
  await erro.getByRole('button',{name:'Copiar informações para suporte'}).click();const suporte=JSON.parse(await page.evaluate(()=>window.suporteCopiado));assert.ok(suporte.operacaoId&&suporte.correlacaoId&&suporte.codigo&&suporte.etapa&&suporte.versao);assert.equal('mensagem' in suporte,false);assert.equal('destinatario' in suporte,false);
  assert.match(await page.locator('#anuncios-diagnostico').textContent(),/Erro/);assert.equal((await get('/api/pendencias')).total,1);
  checks.push('Vermelho com contraste WCAG nos dois temas, texto/ícone, anúncio acessível, detalhes por teclado e suporte sem documentos/tokens');
  await page.locator('#reconferir-zip').click();await page.waitForFunction(()=>document.getElementById('form-pacote').getAttribute('aria-busy')==='false');assert.equal((await get('/api/pendencias')).total,1);
  await atualizarCentral();const anterior=(await get('/api/pendencias')).itens[0].operacaoId;assert.equal(await page.locator('#lista-processamentos > .diag-card').count(),1);
  await atualizarCentral('RESOLVIDO');assert.match(await page.locator('#lista-processamentos').textContent(),/Nenhuma operação/);assert.equal(await page.locator('#lista-processamentos .diag-erro').count(),0);
  await atualizarCentral();const chooser=page.waitForEvent('filechooser');await page.locator('#lista-processamentos').getByRole('button',{name:'Selecionar ZIP corrigido'}).click();await(await chooser).setFiles({name:'corrigido.zip',mimeType:'application/zip',buffer:Buffer.from(pacotes.bom)});
  assert.equal(await page.locator('#resultado-operacional-zip').textContent(),'');assert.equal(await page.locator('#diagnosticos-zip').textContent(),'');assert.equal(await page.locator('#confirmar-zip').isDisabled(),true);
  await page.locator('#inspecionar-zip').click();await page.locator('#vinculo-a2 select').waitFor();await page.waitForFunction(()=>document.getElementById('form-pacote').getAttribute('aria-busy')==='false');
  assert.equal((await get(`/api/diagnosticos/PROCESSAMENTO/${anterior}`)).estado,'RESOLVIDO');assert.match(JSON.stringify(await get(`/api/diagnosticos/PROCESSAMENTO/${anterior}`)),/PDF_NAO_RECONHECIDO/);
  await page.locator('#resultado-operacional-zip').getByRole('button',{name:'Revisar associações'}).click();assert.equal(await page.evaluate(()=>document.activeElement.tagName),'SELECT');
  for(const id of ['a1','a2']){await page.locator(`#vinculo-${id} select`).selectOption('VIAGEM');await page.locator(`#vinculo-${id} input[type=checkbox]`).check();}
  checks.push('Requisições repetidas não duplicam pendências; correção vinculada preserva diagnóstico original, limpa erros antigos e permite revisar associações');
  await navegar('configuracoes');await page.locator('#config-auto-exportacao').check();const salva=page.waitForResponse(r=>r.url().endsWith('/api/configuracoes')&&r.request().method()==='PUT');await page.locator('#salvar-configuracoes').click();assert.equal((await salva).status(),200);
  fs.renameSync(destino,destino+'-offline');await navegar('pacotes');const importResponse=page.waitForResponse(r=>r.url().includes('/api/pacotes/confirmacao')&&r.request().method()==='POST');await page.locator('#confirmar-zip').click();const imported=await(await importResponse).json();
  await page.locator('#resultado-exportacao .diag-erro').first().waitFor();assert.equal((await get('/api/documentos')).length,1);assert.match(await page.locator('#resultado-exportacao').textContent(),/Não é necessário importar novamente/);
   await atualizarCentral();assert.equal((await get('/api/pendencias')).itens.filter(i=>i.origem==='EXPORTACAO').length,1);assert.equal(await page.locator('#contador-pendencias').textContent(),'1');
  await page.locator('#lista-processamentos').getByRole('button',{name:'Abrir configurações da pasta'}).click();await page.waitForFunction(()=>document.querySelector('main').dataset.paginaAtual==='configuracoes');
  fs.renameSync(destino+'-offline',destino);await page.locator('#testar-config-pasta').click();await page.waitForFunction(()=>document.getElementById('config-resultados-testes').textContent.includes('GRAVAVEL'));
  await atualizarCentral();await page.locator('#lista-processamentos').getByRole('button',{name:'Tentar somente a exportação'}).click();await page.locator('#resultado-recuperacao-central > .diag-sucesso').waitFor();await page.waitForFunction(()=>document.getElementById('lista-processamentos').getAttribute('aria-busy')==='false');
  assert.equal((await get('/api/pendencias')).total,0);assert.equal(mutations.filter(m=>m.url.includes('/api/pacotes/confirmacao')).length,1);
  const e=(await get(`/api/pacotes/${imported.pacoteId}/exportacoes`))[0];assert.equal(e.situacao,'CONCLUIDA');for(const item of e.arquivos)assert.equal(digest(fs.readFileSync(path.join(destino,e.subpasta,item.localizacao))),item.sha256);
  const d=await get(`/api/diagnosticos/EXPORTACAO/${e.id}`);assert.equal(d.recuperacoes.length,2);assert.match(JSON.stringify(d.recuperacoes[0]),/PASTA_INEXISTENTE/);assert.equal((await get(`/api/viagens/${imported.documentos[0].viagemId}/tentativas-email`)).length,0);
  checks.push('Importação preservada com exportação pendente, contador real, abertura das configurações e retomada somente da cópia, com confirmação e histórico da resolução');
  const viagem=imported.documentos[0].viagemId;
  const conferencia=await page.request.post(url+`/api/viagens/${viagem}/conferencias-email`,{data:{selecao:{destinatario:'fixture@example.invalid',documentoIds:[imported.documentos[0].id],anexoIds:[]}}});assert.equal(conferencia.status(),200);
  const tentativa=await page.request.post(url+`/api/viagens/${viagem}/tentativas-email`,{data:{conferenciaId:(await conferencia.json()).id,confirmado:true}});assert.equal(tentativa.status(),200);const envio=await tentativa.json();
  await atualizarCentral('RESOLVIDO');const cardEnvio=page.locator(`#lista-processamentos [data-operacao-id="${envio.id}"]`);await cardEnvio.getByRole('button',{name:'Consultar tentativa de envio'}).click();
  await page.waitForFunction(()=>document.querySelector('main').dataset.paginaAtual==='email');await page.locator('#consulta-tentativa').getByText(new RegExp(envio.id)).first().waitFor({state:'attached'});
  await page.locator('#consulta-tentativa').getByText('Destinatário: fixture@example.invalid',{exact:true}).waitFor();
  assert.equal((await get(`/api/viagens/${viagem}/tentativas-email`)).length,1);
  checks.push('Consultar tentativa usa o histórico real por GET, sem criar reenvio nem alterar a tentativa simulada existente');
  await page.route('**/api/pendencias?filtro=TODAS',async route=>{await wait(250);await route.continue().catch(()=>{});});
  await page.evaluate(async()=>{const {api}=await import('/painel-base.js'),{apresentarErro}=await import('/painel-diagnostico.js');try{await api('/api/pendencias?filtro=TODAS',{timeoutMs:30});}catch(error){apresentarErro(document.getElementById('diagnostico-global'),error);}});
   assert.match(await page.locator('#diagnostico-global .diag-erro').textContent(),/Não foi possível carregar os processamentos/);assert.doesNotMatch(await page.locator('#diagnostico-global').textContent(),/resultado ainda não foi confirmado/);await page.unroute('**/api/pendencias?filtro=TODAS');
  await page.evaluate(async()=>{const {api}=await import('/painel-base.js');await api('/api/pendencias?filtro=TODAS');});assert.equal(await page.locator('#diagnostico-global').textContent(),'');
  await page.route('**/api/pendencias?filtro=TODAS',route=>route.fulfill({status:200,contentType:'text/html',body:'<b>CONTEUDO_PRIVADO_NAO_EXIBIR</b>'}));
  await page.evaluate(async()=>{const {api}=await import('/painel-base.js'),{apresentarErro}=await import('/painel-diagnostico.js');try{await api('/api/pendencias?filtro=TODAS');}catch(error){apresentarErro(document.getElementById('diagnostico-global'),error);}});
   assert.match(await page.locator('#diagnostico-global .diag-erro').textContent(),/Não foi possível carregar os processamentos/);assert.equal((await page.locator('body').textContent()).includes('CONTEUDO_PRIVADO_NAO_EXIBIR'),false);await page.unroute('**/api/pendencias?filtro=TODAS');
  await page.evaluate(async()=>{const {api}=await import('/painel-base.js');await api('/api/pendencias?filtro=TODAS');});
   checks.push('Timeout e resposta inválida de GET são falhas de consulta em vermelho, sem resultado incerto nem vazamento de resposta privada; consulta válida limpa erro transitório');
  await page.close();await parar();await iniciar(false);await abrir();await atualizarCentral('RESOLVIDO');assert.match(await page.locator('#lista-processamentos').textContent(),/Correção comprovada/);
  await page.setViewportSize({width:390,height:844});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await page.screenshot({path:path.join(out,'central-mobile.png'),fullPage:true});
  await page.evaluate(()=>{window.dispatchEvent(new ErrorEvent('error',{message:'TOKEN_SINTETICO_NAO_EXIBIR',error:new Error('TOKEN_SINTETICO_NAO_EXIBIR')}));window.dispatchEvent(new ErrorEvent('error',{message:'segundo erro'}));});
  assert.equal(await page.locator('#diagnostico-global > .diag-card').count(),1);assert.match(await page.locator('#diagnostico-global').textContent(),/Erro na interface/);assert.equal((await page.locator('body').textContent()).includes('TOKEN_SINTETICO_NAO_EXIBIR'),false);
  checks.push('Resoluções e tentativas persistem após reinício; central móvel sem overflow e falha JavaScript global tem orientação segura sem ciclos de alertas');
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'resultado.json'),JSON.stringify({checks,errors},null,2));console.log(JSON.stringify({checks,errors},null,2));
}
run().catch(async error=>{console.error(error);fs.writeFileSync(path.join(out,'servidor.log'),logs);if(page&&!page.isClosed())await page.screenshot({path:path.join(out,'falha.png'),fullPage:true}).catch(()=>{});process.exitCode=1;}).finally(async()=>{await browser?.close();await parar();fs.rmSync(isolated,{recursive:true,force:true,maxRetries:5,retryDelay:500});});
