/* Integração do JAR, falhas de consulta, sidebar e alteração de resultado incerto. */
'use strict';
const {chromium}=require('playwright'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {spawn,execFileSync}=require('node:child_process'),{createHash}=require('node:crypto');
const [jarArg,outArg,antigoArg]=process.argv.slice(2),root=path.resolve(__dirname,'..');
if(!jarArg||!outArg)throw Error('Uso: node scripts/testar-estabilizacao.cjs JAR SAIDA_EXTERNA [JAR_ANTIGO]');
const out=path.resolve(outArg);if(out===root||out.startsWith(root+path.sep))throw Error('Use saída externa');
fs.mkdirSync(out,{recursive:true});const isolated=fs.mkdtempSync(path.join(out,'.isolado-'));fs.mkdirSync(path.join(isolated,'copias'));
let server,browser,page,url,logs='';const checks=[],requests=[],wait=ms=>new Promise(r=>setTimeout(r,ms)),hash=b=>createHash('sha256').update(b).digest('hex');
async function start(jar,antigo=false){
  let atual='';server=spawn('java',['-Xmx512m','-jar',path.resolve(jar),'--server.address=127.0.0.1','--server.port=0','--spring.profiles.active=desenvolvimento',
    '--spring.datasource.url=jdbc:h2:file:'+path.join(isolated,antigo?'antigo':'novo').replaceAll('\\','/')+';WRITE_DELAY=0','--spring.flyway.enabled=true','--spring.h2.console.enabled=false',
    '--rotabrasil.email.modo=SIMULADO','--rotabrasil.arquivos.viagens='+path.join(isolated,antigo?'acervo-antigo':'acervo'),'--rotabrasil.exportacao.raiz='+path.join(isolated,'copias'),
    ...(antigo?['--spring.web.resources.static-locations=file:'+root.replaceAll('\\','/')+'/src/main/resources/static/']:[])],{cwd:root});
  server.stdout.on('data',b=>{atual+=b;logs+=b;});server.stderr.on('data',b=>{atual+=b;logs+=b;});
  for(let i=0;i<1200;i++){const port=atual.match(/Tomcat started on port (\d+)/)?.[1];if(port){url='http://127.0.0.1:'+port;return;}if(server.exitCode!==null)throw Error('Servidor terminou');await wait(100);}throw Error('Inicialização excedeu 120s');
}
async function stop(){if(!server||server.exitCode!==null||server.signalCode!==null)return;let encerrou=false;const fim=new Promise(r=>server.once('exit',()=>{encerrou=true;r();}));server.kill();await Promise.race([fim,wait(15000)]);if(!encerrou)throw Error('Instância própria não encerrou');}
async function central(){await page.locator('#atualizar-processamentos').click();await page.waitForFunction(()=>document.getElementById('lista-processamentos').getAttribute('aria-busy')==='false');}
async function layout(){const r=await page.locator('a[href="#processamentos"]').first().evaluate(a=>{const b=a.querySelector('.pending-count').getBoundingClientRect(),s=a.closest('aside').getBoundingClientRect(),range=document.createRange();range.selectNodeContents(a.querySelector('.pending-label'));const label=range.getBoundingClientRect();return {inside:b.right<=s.right&&b.left>=s.left,overlap:getComputedStyle(a.querySelector('.pending-label')).fontSize!=='0px'&&label.right>b.left};});assert.ok(r.inside);assert.equal(r.overlap,false);}
async function run(){
  if(antigoArg){
    const entries=execFileSync('jar',['tf',path.resolve(antigoArg)],{encoding:'utf8'});
    assert.equal(entries.includes('controller/ConfiguracaoOperacaoController.class'),false);assert.equal(entries.includes('controller/DiagnosticoOperacaoController.class'),false);
    await start(antigoArg,true);
    for(const rota of ['/api/configuracoes','/api/pendencias?pagina=0&tamanho=20&filtro=PRECISA_ACAO']){const r=await fetch(url+rota,{signal:AbortSignal.timeout(5000)});requests.push({cenario:'JAR antigo + estáticos atuais',url:url+rota,metodo:'GET',status:r.status});assert.equal(r.status,404);}
    const html=await(await fetch(url,{signal:AbortSignal.timeout(5000)})).text();assert.match(html,/multioperador-V14-20261102/);
    browser=await chromium.launch({channel:process.env.ROTABRASIL_BROWSER_CHANNEL||'chrome',headless:true});page=await browser.newPage();await page.goto(url,{waitUntil:'networkidle'});await page.locator('#abertura-erro').waitFor({state:'visible'});assert.match(await page.locator('#abertura-erro').textContent(),/Backend desatualizado/);await browser.close();browser=null;page=null;
    checks.push('Causa reproduzida: JAR antigo sem controllers novos + frontend do checkout produz os dois GET 404; inicialização detecta backend desatualizado explicitamente');await stop();
  }
  await start(jarArg);browser=await chromium.launch({channel:process.env.ROTABRASIL_BROWSER_CHANNEL||'chrome',headless:true});page=await browser.newPage({viewport:{width:1440,height:1000},reducedMotion:'reduce'});const errors=[];page.on('pageerror',e=>errors.push(e.message));
  page.on('response',r=>{if(/\/api\/(configuracoes|pendencias)/.test(r.url()))requests.push({url:r.url(),metodo:r.request().method(),status:r.status()});});
  await page.goto(url,{waitUntil:'networkidle'});await page.locator('#abertura').waitFor({state:'hidden'});
  const versao=await(await page.request.get(url+'/api/versao')).json();assert.equal(versao.contrato,'multioperador-V14-20261102');
  for(const [nome,sha] of Object.entries(versao.estaticosSha256)){const r=await page.request.get(url+'/'+nome);assert.equal(hash(await r.body()),sha);assert.match(r.headers()['cache-control'],/no-store/);}
  fs.writeFileSync(path.join(out,'versao.json'),JSON.stringify({...versao,jarSha256:hash(fs.readFileSync(jarArg))},null,2));
  checks.push('Rotas novas respondem 200; bytes de HTML/JS/CSS servidos coincidem com hashes do JAR e Cache-Control no-store');
  await page.evaluate(()=>{location.hash='configuracoes';});
  await page.route('**/api/configuracoes',route=>route.fulfill({status:404,contentType:'application/json',body:JSON.stringify({mensagem:'Recurso não encontrado'})}));
  await page.locator('#recarregar-configuracoes').click();await page.locator('#config-diagnostico .diag-erro').waitFor();assert.match(await page.locator('#config-diagnostico').textContent(),/Não foi possível carregar as configurações/);assert.doesNotMatch(await page.locator('#config-diagnostico').textContent(),/RESULTADO_NAO_CONFIRMADO/);
  assert.equal(await page.locator('#config-diagnostico [data-acao-recuperacao="ABRIR_CENTRAL"]').count(),0);await page.unroute('**/api/configuracoes');await page.locator('#config-diagnostico').getByRole('button',{name:'Tentar novamente'}).click();await page.waitForFunction(()=>!document.getElementById('config-diagnostico').textContent);
  checks.push('GET 404 de Configurações é erro vermelho de consulta com Tentar novamente; recuperação usa GET real sem falsa hipótese de banco');
  await page.evaluate(()=>{location.hash='processamentos';});
  await page.route('**/api/pendencias?*',route=>route.abort('connectionfailed'));await central();assert.equal(await page.locator('#contador-pendencias').textContent(),'!');assert.match(await page.locator('#contador-pendencias').getAttribute('aria-label'),/indisponível/);assert.equal(await page.locator('#lista-processamentos [data-acao-recuperacao="ABRIR_CENTRAL"]').count(),0);await layout();
  await page.locator('#menu-compactar').click();await layout();await page.locator('#menu-compactar').click();await page.setViewportSize({width:390,height:844});await page.locator('#menu-abrir').click();await layout();await page.screenshot({path:path.join(out,'sidebar-mobile-indisponivel.png')});await page.locator('#menu-fechar').click();await page.setViewportSize({width:1440,height:1000});
  await page.unroute('**/api/pendencias?*');
  await page.route('**/api/pendencias?*',async route=>{const r=await route.fetch(),b=await r.json();b.resumo.falhas=130;b.resumo.atencao=7;b.resumo.emProcessamento=12;await route.fulfill({response:r,json:b});});await central();assert.equal(await page.locator('#contador-pendencias').textContent(),'99+');assert.match(await page.locator('#resumo-central').textContent(),/130 falha.*7 pendência.*12 em processamento/);await layout();await page.locator('#menu-compactar').click();await layout();await page.screenshot({path:path.join(out,'sidebar-recolhida-99.png')});await page.locator('#menu-compactar').click();
  for(const width of [1024,800,390]){await page.setViewportSize({width,height:900});if(width===390)await page.locator('#menu-abrir').click();await layout();if(width===390)await page.locator('#menu-fechar').click();}
  await page.unroute('**/api/pendencias?*');await page.setViewportSize({width:1440,height:1000});await central();assert.equal(await page.locator('#contador-pendencias').textContent(),'0');
  checks.push('Sidebar aberta/recolhida/celular e 800/1024px: contador único 99+, divisão interna, indisponibilidade acessível sem ? nem zero falso, sem sobreposição');
  // Alteração realmente executada, mas resposta retida: não repetir o PUT após timeout.
  const c=await(await page.request.get(url+'/api/configuracoes')).json();let completed;
  const commit=new Promise(resolve=>completed=resolve);await page.route('**/api/configuracoes',async route=>{if(route.request().method()!=='PUT')return route.continue();const r=await route.fetch();completed(r.status());await wait(300);await route.fulfill({response:r}).catch(()=>{});});
  const uncertain=await page.evaluate(async c=>{const {api}=await import('/painel-base.js'),{apresentarErro}=await import('/painel-diagnostico.js');try{await api('/api/configuracoes',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({versao:c.versao,remetente:'incerto@example.invalid',subpasta:'',exportacaoAutomatica:false}),timeoutMs:100});}catch(e){apresentarErro(document.getElementById('diagnostico-global'),e);return e.incerto;}},c);
  assert.equal(uncertain,true);assert.equal(await commit,200);assert.match(await page.locator('#diagnostico-global .diag-atencao').textContent(),/resultado ainda não foi confirmado/);await wait(400);await page.unroute('**/api/configuracoes');assert.equal((await(await page.request.get(url+'/api/configuracoes')).json()).remetente,'incerto@example.invalid');
  const recusada=await page.evaluate(async c=>{const {api}=await import('/painel-base.js'),{apresentarErro}=await import('/painel-diagnostico.js');try{await api('/api/configuracoes',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({versao:c.versao,remetente:'recusado@example.invalid',subpasta:'',exportacaoAutomatica:false})});}catch(e){apresentarErro(document.getElementById('diagnostico-global'),e);return {status:e.status,incerto:e.incerto};}},c);
  assert.equal(recusada.status,409);assert.equal(recusada.incerto,false);await page.locator('#diagnostico-global .diag-erro').waitFor();assert.equal((await(await page.request.get(url+'/api/configuracoes')).json()).remetente,'incerto@example.invalid');
  checks.push('PUT com resposta atrasada permanece incerto após commit, GET confirma persistência sem repetir; recusa 409 é erro conhecido em vermelho e preserva os dados');
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'resultado.json'),JSON.stringify({checks,requests,errors},null,2));console.log(JSON.stringify({checks,requests,errors},null,2));
}
run().catch(async e=>{console.error(e);fs.writeFileSync(path.join(out,'servidor.log'),logs);if(page)await page.screenshot({path:path.join(out,'falha.png')}).catch(()=>{});process.exitCode=1;}).finally(async()=>{await browser?.close();await stop();fs.rmSync(isolated,{recursive:true,force:true,maxRetries:5,retryDelay:500});});
