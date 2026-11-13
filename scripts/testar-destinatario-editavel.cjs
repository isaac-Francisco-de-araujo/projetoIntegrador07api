/* Interações reais Chrome: clique, Tab, teclas e Ctrl+V. Somente fixtures e H2 novos. */
'use strict';
const {chromium,expect}=require('playwright/test');
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),crypto=require('node:crypto');
const {spawn}=require('node:child_process');
const assert=require('node:assert/strict');
const {pacoteSintetico,configuracao}=require('./central-fixture.cjs');
const [jar,out,modo]=process.argv.slice(2);
if(!jar||!out)throw Error('Uso: node scripts/testar-destinatario-editavel.cjs JAR SAIDA [--diagnosticar]');
fs.mkdirSync(out,{recursive:true});
const base=fs.mkdtempSync(path.join(os.tmpdir(),'rotabrasil-digitacao-'));
const logPath=path.join(out,'servidor.log'),log=fs.openSync(logPath,'w');
const server=spawn('java',['-Xmx512m','-jar',path.resolve(jar),'--server.address=127.0.0.1','--server.port=0',
  '--spring.profiles.active=desenvolvimento','--spring.datasource.url=jdbc:h2:file:'+base.replaceAll('\\','/')+'/banco;WRITE_DELAY=0',
  '--spring.flyway.enabled=true','--spring.h2.console.enabled=false','--rotabrasil.seguranca.ativa=false',
  '--rotabrasil.email.modo=SIMULADO','--rotabrasil.notificacoes.modo=SIMULADO','--rotabrasil.teste-email.modo=SIMULADO',
  '--rotabrasil.arquivos.viagens='+base+'/acervo','--rotabrasil.exportacao.raiz='+base+'/exportacao'],{stdio:['ignore',log,log]});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
let browser,page,context,liberarPendente;const checks=[],errors=[],observacoes=[];
let identidade;
async function observar(etapa){
  const estado=await page.locator('#destinatario').evaluate(async el=>{
    const {state,podeOperar}=await import('/painel-base.js');
    const box=el.getBoundingClientRect(),top=document.elementFromPoint(box.x+box.width/2,box.y+box.height/2);
    const ancestrais=[];for(let a=el.parentElement;a;a=a.parentElement)if(a.inert||a.matches('fieldset:disabled')||getComputedStyle(a).pointerEvents==='none')ancestrais.push({tag:a.tagName,id:a.id,inert:a.inert,disabled:a.matches(':disabled'),pointerEvents:getComputedStyle(a).pointerEvents});
    return {viagemSelecionada:Boolean(state.viagem),documentosSelecionados:state.selecionados.size+state.anexosSelecionados.size,envioReal:state.envioReal===true,podeOperar:podeOperar(),carregando:state.carregando,previaOcupada:state.previaOcupada,
      disabled:el.disabled,disabledEfetivo:el.matches(':disabled'),readOnly:el.readOnly,valor:el.value,placeholder:el.placeholder,
      foco:document.activeElement===el,ancestrais,interceptador:top?.id||top?.tagName,atingeInput:top===el,
      pointerEvents:getComputedStyle(el).pointerEvents,zIndex:getComputedStyle(el).zIndex,
      aberturaOculta:document.querySelector('#abertura').hidden,carregandoVisual:document.body.classList.contains('is-loading'),
      inputContinuaNoDom:el.isConnected};
  });observacoes.push({etapa,...estado});return estado;
}
async function mouseNoInput(){const box=await page.locator('#destinatario').boundingBox();await page.mouse.click(box.x+box.width/2,box.y+box.height/2);}
async function focarPorTab(){for(let i=0;i<90;i++){await page.keyboard.press('Tab');if(await page.locator('#destinatario').evaluate(el=>el===document.activeElement))return;}throw Error('Tab não alcançou o destinatário');}
async function escrever(texto){await page.keyboard.press('Control+A');await page.keyboard.press('Backspace');await page.keyboard.type(texto);await expect(page.locator('#destinatario')).toHaveValue(texto);}
async function colar(texto){
  // Escreve somente texto sintético na área de transferência do contexto de teste;
  // o valor do input só muda pelo comando nativo de colagem.
  await page.evaluate(t=>navigator.clipboard.writeText(t),texto);
  await page.keyboard.press('Control+A');await page.keyboard.press('Control+V');await expect(page.locator('#destinatario')).toHaveValue(texto);
}
async function atrasar(rota){
  const gate=Promise.withResolvers(),recebida=Promise.withResolvers();liberarPendente=gate.resolve;
  const handler=async r=>{recebida.resolve();await gate.promise;await r.continue();};
  await page.route(rota,handler,{times:1});
  return {recebida:recebida.promise,liberar:()=>{gate.resolve();liberarPendente=null;}};
}
async function main(){
  let url;
  for(let i=0;i<900;i++){
    const ports=[...fs.readFileSync(logPath,'utf8').matchAll(/Tomcat started on port (\d+)/g)];
    if(ports.length){url='http://127.0.0.1:'+ports.at(-1)[1];try{if((await fetch(url+'/api/sessao')).ok)break;}catch{}}
    if(server.exitCode!==null)throw Error('Servidor encerrou');await sleep(100);
  }
  browser=await chromium.launch({channel:'chrome',headless:true});
  context=await browser.newContext({viewport:{width:1440,height:1000},locale:'pt-BR'});
  await context.grantPermissions(['clipboard-read','clipboard-write'],{origin:url});
  page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{window.eventosDestinatario=[];for(const tipo of ['focusin','keydown','beforeinput','input','paste'])document.addEventListener(tipo,e=>{if(e.target.id==='destinatario')window.eventosDestinatario.push({tipo,confiavel:e.isTrusted,inputType:e.inputType||null,cancelado:e.defaultPrevented});},true);});
  const buffer=pacoteSintetico(),arquivo={name:'digitacao-sintetica.zip',mimeType:'application/zip',buffer};
  const i=await page.request.post(url+'/api/pacotes/inspecao',{multipart:{arquivo}});assert.equal(i.status(),200);
  const r=await page.request.post(url+'/api/pacotes/confirmacao',{multipart:{arquivo,configuracao:{name:'configuracao.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(configuracao(await i.json())))}}});assert.equal(r.status(),200);
  const trip=String((await r.json()).documentos[0].viagemId);
  const tripData=await(await page.request.get(url+'/api/viagens/'+trip)).json();
  const outro=await page.request.post(url+'/api/viagens',{data:{numero:'66214',clienteId:tripData.clienteId}});assert.equal(outro.status(),201);
  const outraViagem=String((await outro.json()).id);
  identidade={jar:path.resolve(jar),sha256:crypto.createHash('sha256').update(fs.readFileSync(jar)).digest('hex'),versao:await(await page.request.get(url+'/api/versao')).json()};
  await page.goto(url+'/painel',{waitUntil:'networkidle'});await page.locator('#abertura').waitFor({state:'hidden'});
  async function documentos(){await page.locator('.sidebar nav a[href="#documentos"],.sidebar nav a[href="#viagens"]').first().click();await expect(page.locator('#viagem-selecionada')).toBeVisible();}
  await documentos();await page.locator('#viagem-selecionada').selectOption(trip);
  async function selecionar(){await page.locator('#lista-documentos input').check();for(const box of await page.locator('#lista-anexos input').all())await box.check();await page.getByRole('link',{name:'Conferir seleção para envio',exact:true}).click();}
  await selecionar();await page.locator('#destinatario').scrollIntoViewIfNeeded();
  const inicial=await observar('seleção válida, antes do clique');
  assert.equal(inicial.valor,'');assert.equal(inicial.placeholder,'destinatario@example.invalid');
  assert.equal(inicial.aberturaOculta,true);assert.equal(inicial.carregandoVisual,false);assert.deepEqual(inicial.ancestrais,[]);assert.equal(inicial.atingeInput,true);
  await mouseNoInput();await page.keyboard.type('clique@example.invalid');
  const clicado=await observar('clique e teclado');
  if(modo==='--diagnosticar'&&inicial.disabled){
    assert.equal(inicial.viagemSelecionada,true);assert.equal(inicial.documentosSelecionados,3);assert.equal(inicial.podeOperar,true);assert.equal(inicial.envioReal,false);assert.equal(inicial.carregando,false);assert.equal(inicial.previaOcupada,false);
    assert.equal(clicado.valor,'');assert.equal(clicado.foco,false);await page.screenshot({path:path.join(out,'input-bloqueado.png'),fullPage:true});checks.push('Clique real não dá foco e teclas não alteram valor: disabled no input, sem overlay/inert/readonly');return;
  }
  await expect(page.locator('#destinatario')).toBeFocused();await expect(page.locator('#destinatario')).toHaveValue('clique@example.invalid');
  const inputOriginal=await page.locator('#destinatario').elementHandle();
  async function cicloEdicao(nome){
    await mouseNoInput();await expect(page.locator('#destinatario')).toBeFocused();
    await escrever('digitado@example.invalid');await page.keyboard.press('Home');await page.keyboard.type('editado-');await expect(page.locator('#destinatario')).toHaveValue('editado-digitado@example.invalid');
    await page.keyboard.press('Control+A');await page.keyboard.press('Backspace');await expect(page.locator('#destinatario')).toHaveValue('');
    await colar('colado@example.invalid');
    // Sai do campo e volta usando só teclas, sem focus() nem atribuição de value.
    await page.keyboard.press('Tab');await focarPorTab();await expect(page.locator('#destinatario')).toBeFocused();
    await escrever('teclado@example.invalid');await colar('teclado-colado@example.invalid');await observar(nome);
  }
  await cicloEdicao('desktop sem SMTP');await page.screenshot({path:path.join(out,'desktop-destinatario-focado.png'),fullPage:true});checks.push('Clique, digitação, edição, apagar, Tab e Ctrl+V funcionam sem SMTP');
  for(const falha of [false,true,false]){
    if(falha)await page.route('**/api/envio-documental',route=>route.fulfill({status:503,json:{}}));
    const valor=await page.locator('#destinatario').inputValue();await page.locator('#recarregar-envio').click();await expect(page.locator('#recarregar-envio')).toBeEnabled();
    await expect(page.locator('#destinatario')).toHaveValue(valor);
    if(falha)await expect(page.locator('#modo-email')).toContainText('Não foi possível consultar');
    await cicloEdicao(falha?'desktop falha de configuração':'desktop consulta/recuperação');
    if(falha)await page.unroute('**/api/envio-documental');
  }
  assert(await inputOriginal.evaluate(el=>el===document.getElementById('destinatario')));checks.push('Consulta, falha 503 e recuperação preservam texto e o mesmo nó do input');
  // Regressão adicional da v2: atualização genérica tirava foco e impedia editar.
  const atualizacao=await atrasar('**/api/consulta/resumo');await page.locator('#atualizar').click();await atualizacao.recebida;
  await page.locator('#destinatario').scrollIntoViewIfNeeded();const ocupado=await observar('durante Atualizar dados');
  if(modo==='--diagnosticar'&&ocupado.disabled){
    await mouseNoInput();await page.keyboard.type('nao-entra@example.invalid');await observar('teclas durante atualização bloqueada');
    await page.screenshot({path:path.join(out,'atualizacao-bloqueia-input.png'),fullPage:true});atualizacao.liberar();checks.push('v2 ainda aplica disabled por state.carregando durante atualização genérica');return;
  }
  await expect(page.locator('#destinatario')).toBeEnabled();await mouseNoInput();await escrever('durante-consulta@example.invalid');atualizacao.liberar();
  await expect(page.locator('#atualizar')).toBeEnabled();await expect(page.locator('#destinatario')).toHaveValue('durante-consulta@example.invalid');
  assert(await inputOriginal.evaluate(el=>el===document.getElementById('destinatario')));
  await documentos();await selecionar();checks.push('Atualizar dados não bloqueia edição nem apaga destinatário');
  const previa=await atrasar('**/conferencias-email');await page.locator('#gerar-previa').click();await previa.recebida;
  await mouseNoInput();await escrever('novo-durante-previa@example.invalid');await observar('edição enquanto prévia aguarda servidor');previa.liberar();
  await expect(page.locator('#gerar-previa')).toBeEnabled();await expect(page.locator('#resultado-previa')).toBeHidden();
  await page.locator('#gerar-previa').click();await expect(page.locator('#resultado-previa')).toContainText('novo-durante-previa@example.invalid');
  await expect(page.locator('#simular-envio')).toBeDisabled();await page.screenshot({path:path.join(out,'desktop-digitacao-conferencia.png'),fullPage:true});
  await mouseNoInput();await escrever('apos-conferencia@example.invalid');await expect(page.locator('#resultado-previa')).toBeHidden();
  checks.push('Edição durante prévia invalida resposta antiga; nova conferência reflete o destinatário e não libera envio real');
  await page.locator('#gerar-previa').click();await expect(page.locator('#resultado-previa')).toBeVisible();
  await documentos();await page.locator('#viagem-selecionada').selectOption(outraViagem);
  await page.locator('.sidebar nav a[href="#email"]').click();await expect(page.locator('#resultado-previa')).toBeHidden();await expect(page.locator('#gerar-previa')).toBeDisabled();
  await expect(page.locator('#motivo-previa')).toContainText('Selecione pelo menos');
  await expect(page.locator('#destinatario')).toHaveValue('apos-conferencia@example.invalid');checks.push('Trocar viagem não reutiliza conferência; destinatário preservado como rascunho');
  await documentos();await page.locator('#viagem-selecionada').selectOption(trip);await selecionar();
  await page.setViewportSize({width:390,height:844});await page.waitForTimeout(400);
  for(const falha of [false,true,false]){
    if(falha)await page.route('**/api/envio-documental',route=>route.fulfill({status:503,json:{}}));
    await page.locator('#recarregar-envio').click();await expect(page.locator('#recarregar-envio')).toBeEnabled();
    await page.locator('#destinatario').scrollIntoViewIfNeeded();await cicloEdicao(falha?'celular falha configuração':'celular sem SMTP/recuperação');
    if(falha)await page.unroute('**/api/envio-documental');
  }
  await page.locator('#gerar-previa').click();await expect(page.locator('#resultado-previa')).toContainText('teclado-colado@example.invalid');await expect(page.locator('#simular-envio')).toBeDisabled();
  await page.screenshot({path:path.join(out,'mobile-digitacao-conferencia.png'),fullPage:true});
  await mouseNoInput();await expect(page.locator('#destinatario')).toBeFocused();await page.screenshot({path:path.join(out,'mobile-destinatario-focado.png'),fullPage:true});
  assert(await inputOriginal.evaluate(el=>el===document.getElementById('destinatario')));
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  assert.equal((await(await page.request.get(url+`/api/viagens/${trip}/tentativas-email`)).json()).length,0);
  checks.push('Celular: clique/Tab/teclas/colagem sob falha e recuperação; conferência sem tentativa de envio');
  const eventos=await page.evaluate(()=>window.eventosDestinatario);assert(eventos.some(e=>e.tipo==='paste'&&e.confiavel));assert(eventos.some(e=>e.tipo==='input'&&e.confiavel));
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'eventos.json'),JSON.stringify(eventos,null,2));
}
main().then(()=>{fs.writeFileSync(path.join(out,'resultado.json'),JSON.stringify({identidade,checks,observacoes,errors,envioExterno:false},null,2));console.log(JSON.stringify({checks,observacoes,errors},null,2));}).catch(async e=>{
  console.error(e);if(page){await observar('falha').catch(()=>{});await page.screenshot({path:path.join(out,'falha.png'),fullPage:true}).catch(()=>{});}
  fs.writeFileSync(path.join(out,'resultado-falha.json'),JSON.stringify({identidade,checks,observacoes,errors,erro:e.message,envioExterno:false},null,2));process.exitCode=1;
}).finally(async()=>{liberarPendente?.();if(browser)await browser.close();server.kill();await Promise.race([new Promise(r=>server.once('exit',r)),sleep(15000)]);fs.closeSync(log);fs.rmSync(base,{recursive:true,force:true,maxRetries:10,retryDelay:500});});
