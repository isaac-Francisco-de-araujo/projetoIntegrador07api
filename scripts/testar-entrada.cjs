/* Apresentação → login → painel: JAR real, HTTPS operacional e dados sintéticos externos. */
'use strict';
const {chromium,expect}=require('playwright/test');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {spawn,execFileSync}=require('node:child_process'),{randomBytes}=require('node:crypto');
const [jarArg,outArg]=process.argv.slice(2),root=path.resolve(__dirname,'..');
if(!jarArg||!outArg)throw Error('Uso: node scripts/testar-entrada.cjs JAR SAIDA_EXTERNA');
const out=path.resolve(outArg);
if(out===root||out.startsWith(root+path.sep))throw Error('Use uma saída externa ao checkout');
fs.mkdirSync(out,{recursive:true});
const base=fs.mkdtempSync(path.join(out,'.isolado-')),senha=randomBytes(24).toString('base64url');
const env={...process.env,ROTABRASIL_TESTE_SENHA:senha};
const checks=[],errors=[],pedidos=[];let server,browser,url,logs='',paginaAtual;
const wait=ms=>new Promise(r=>setTimeout(r,ms));
async function start(protegido,contexto=''){
  let log='';
  const args=['-Xmx512m','-jar',path.resolve(jarArg),'--server.address=127.0.0.1','--server.port=0',
    '--spring.profiles.active='+(protegido?'operacao':'desenvolvimento'),
    '--spring.datasource.url=jdbc:h2:file:'+path.join(base,'banco').replaceAll('\\','/')+';WRITE_DELAY=0'+(protegido?';IFEXISTS=TRUE':''),
    '--spring.flyway.enabled='+!protegido,'--spring.h2.console.enabled=false',
    '--rotabrasil.email.modo=SIMULADO','--rotabrasil.notificacoes.modo=SIMULADO','--rotabrasil.email.smtp.ativado=false',
    '--rotabrasil.arquivos.viagens='+path.join(base,'acervo'),'--server.servlet.context-path='+contexto];
  if(protegido)args.push('--server.ssl.enabled=true','--server.ssl.key-store='+path.join(base,'tls.p12'));
  server=spawn(process.env.JAVA||'java',args,{cwd:base,env:protegido?{...env,SERVER_SSL_KEY_STORE_PASSWORD:senha}:env});
  for(const stream of [server.stdout,server.stderr])stream.on('data',b=>{log+=b;logs+=b;});
  for(let i=0;i<1200;i++){
    const port=log.match(/Tomcat started on port (\d+)/)?.[1];
    if(port){url=(protegido?'https':'http')+'://127.0.0.1:'+port+contexto;return;}
    if(server.exitCode!==null)throw Error('Servidor sintético terminou antes de iniciar');
    await wait(100);
  }
  throw Error('Servidor não iniciou');
}
async function stop(){
  if(!server||server.exitCode!==null||server.signalCode!==null)return;
  const fim=new Promise(r=>server.once('exit',r));server.kill();
  await Promise.race([fim,wait(15000)]);
  assert.ok(server.exitCode!==null||server.signalCode!==null,'Servidor de teste deve encerrar antes de reutilizar a base');
}
async function nova(){
  const context=await browser.newContext({ignoreHTTPSErrors:true,viewport:{width:1366,height:768},locale:'pt-BR'});
  const p=await context.newPage();paginaAtual=p;
  p.on('pageerror',e=>errors.push(e.message));
  p.on('request',r=>pedidos.push({metodo:r.method(),url:r.url()}));
  return p;
}
async function pronta(p){
  await expect(p.locator('#sessao-usuario')).not.toHaveText('Carregando sessão…',{timeout:30000});
  await expect(p.locator('#abertura')).toBeHidden();
  await expect(p.locator('body')).not.toHaveClass(/is-loading/);
}
async function login(p,usuario){
  await p.locator('[name=username]').fill(usuario);
  await p.locator('[name=password]').fill(senha);
  await p.getByRole('button',{name:'Entrar',exact:true}).click();await pronta(p);
}
async function api(p,rota,corpo,method='POST'){
  return p.evaluate(async({rota,corpo,method,base})=>{
    const s=await(await fetch(base+'/api/sessao')).json();
    const r=await fetch(base+rota,{method,headers:{'Content-Type':'application/json',[s.csrfHeader]:s.csrf},body:JSON.stringify(corpo)});
    return {status:r.status,body:await r.json()};
  },{rota,corpo,method,base:new URL(url).pathname.replace(/\/$/,'')});
}
async function capturar(p,nome){
  for(const [largura,altura] of [[1366,768],[390,844]]){
    await p.setViewportSize({width:largura,height:altura});
    assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),nome+' sem overflow');
    await p.screenshot({path:path.join(out,nome+'-'+largura+'.png'),fullPage:true});
  }
  await p.setViewportSize({width:1366,height:768});
}
async function verificar(contexto){
  await start(true,contexto);const p=await nova(),prefixo=contexto?'contexto-':'';
  const inicio=pedidos.length;await p.goto(url+'/');await p.waitForLoadState('networkidle');
  await expect(p.getByRole('heading',{level:1})).toHaveText('Organize documentos de viagens e acompanhe sua operação.');
  const recursos=pedidos.slice(inicio).map(r=>new URL(r.url).pathname);
  assert.deepEqual(recursos.sort(),[contexto+'/',contexto+'/acesso.css',contexto+'/acesso-logo.svg',contexto+'/acesso.js'].sort());
  assert.ok(await p.locator('.marca img').evaluate(i=>i.complete&&i.naturalWidth>0));
  await capturar(p,prefixo+'apresentacao');
  await p.keyboard.press('Tab');await expect(p.getByRole('link',{name:'Entrar',exact:true})).toBeFocused();
  await p.keyboard.press('Enter');await expect(p).toHaveURL(url+'/login');
  await expect(p.locator('[name=username]')).toBeFocused();
  await expect(p.locator('[name=_csrf]')).toHaveAttribute('value',/.+/);
  await p.keyboard.press('Tab');await expect(p.locator('[name=password]')).toBeFocused();
  await p.keyboard.press('Tab');await expect(p.getByRole('button',{name:'Entrar',exact:true})).toBeFocused();
  await capturar(p,prefixo+'login');
  await p.locator('[name=username]').fill('inexistente-sintetico');await p.locator('[name=password]').fill('incorreta');
  await p.getByRole('button',{name:'Entrar',exact:true}).click();await expect(p.locator('[role=status]')).toContainText('Credenciais inválidas. Verifique seu acesso.');
  await expect(p.locator('[name=password]')).toHaveValue('');
  assert.equal((await p.request.post(url+'/login',{form:{username:'admin-sintetico',password:senha}})).status(),403);
  for(const recurso of ['/api/sessao','/api/viagens','/api/admin/usuarios','/api/configuracoes','/api/documentos/1/arquivo','/api/anexos/1/arquivo']){
    const r=await p.request.get(url+recurso);assert.equal(r.status(),401,recurso);assert.match(r.headers()['content-type'],/application\/json/);
  }
  const anonimoPost=await p.request.post(url+'/api/pacotes/inspecao');assert.equal(anonimoPost.status(),401);assert.match(anonimoPost.headers()['content-type'],/application\/json/);
  for(const rota of ['/painel','/index.html','/painel-admin.js']){
    const r=await p.request.get(url+rota,{maxRedirects:0});assert.equal(r.status(),302);assert.ok(r.headers().location.endsWith(contexto+'/login'));
  }
  checks.push(prefixo+'Apresentação só carrega 4 recursos públicos; teclado, celular, CSRF, erro genérico, painel/downloads protegidos e JSON 401');
  await p.goto(url+'/login?continue=https://externo.invalid');await login(p,'admin-sintetico');await expect(p).toHaveURL(url+'/painel');
  await capturar(p,prefixo+'painel');
  const cookies=await p.context().cookies();assert.ok(cookies.some(c=>c.name==='JSESSIONID'&&c.secure&&c.httpOnly&&c.sameSite==='Strict'));
  await p.goto(url+'/login');await expect(p).toHaveURL(url+'/painel');await pronta(p);
  await p.goto(url+'/');await p.getByRole('link',{name:'Entrar',exact:true}).click();await expect(p).toHaveURL(url+'/painel');await pronta(p);
  await p.goto(url+'/index.html#documentos');await pronta(p);await expect(p.locator('main')).toHaveAttribute('data-pagina-atual','viagens');
  await p.goto(url+'/#operadores');await pronta(p);await expect(p).toHaveURL(url+'/painel#operadores');await expect(p.locator('main')).toHaveAttribute('data-pagina-atual','administracao');
  checks.push(prefixo+'Login válido e sessão existente levam ao painel sem loops/destino externo; index.html, favorito antigo e alias preservados');
  // Cadastro e alteração pelo frontend existente em perfil operacional, nunca por cadastro público.
  const novo=contexto?'contexto-operador':'novo-operador';
  await p.locator('#adicionar-operador').click();await p.locator('#novo-usuario').fill(novo);await p.locator('#nova-senha').fill(senha);await p.locator('#novo-papel').selectOption('CONSULTA');
  await p.locator('#form-usuario button[type=submit]').click();await expect(p.locator('#lista-usuarios')).toContainText(novo);
  const row=p.locator('.operator-row').filter({hasText:novo});await row.getByRole('button',{name:'Gerenciar',exact:true}).click();
  await p.locator('#editar-operador select').selectOption('OPERADOR');await p.getByRole('button',{name:'Salvar permissões',exact:true}).click();await expect(row).toContainText('OPERADOR');
  const usuarios=await(await p.request.get(url+'/api/admin/usuarios')).json();assert.ok(!JSON.stringify(usuarios).includes('$2'));
  const admin=usuarios.find(u=>u.USUARIO==='admin-sintetico');
  assert.equal((await api(p,'/api/admin/usuarios/'+admin.ID,{papel:'CONSULTA',ativo:false,versao:admin.VERSAO},'PUT')).status,409);
  const auditoria=await(await p.request.get(url+'/api/admin/auditoria')).json();assert.ok(JSON.stringify(auditoria).includes('/api/admin/usuarios'));
  for(const [usuario,papel] of [[contexto?'contexto-consulta':'consulta-sintetica','CONSULTA']])assert.equal((await api(p,'/api/admin/usuarios',{usuario,senha,papel})).status,200);
  for(const [usuario,papel] of [[novo,'OPERADOR'],[contexto?'contexto-consulta':'consulta-sintetica','CONSULTA']]){
    const q=await nova();await q.goto(url+'/login');await login(q,usuario);await expect(q.locator('#sessao-usuario')).toContainText(papel);
    assert.equal((await q.request.get(url+'/api/admin/usuarios')).status(),403);
    assert.equal((await api(q,'/api/admin/usuarios',{usuario:'negado',senha,papel:'ADMIN'})).status,403);
    await q.goto(url+'/painel#pacotes');await pronta(q);assert.equal(await q.locator('#arquivo-zip').isDisabled(),papel==='CONSULTA');
    if(papel==='CONSULTA')assert.equal((await api(q,'/api/pacotes/confirmacao',{})).status,403);
    await q.context().close();
  }
  checks.push(prefixo+'ADMIN cadastra e altera operador pela aba existente; três papéis, último ADMIN, auditoria e negação de cadastro preservados');
  // Outra aba antiga da mesma sessão deve perder acesso após logout.
  const antiga=await p.context().newPage();await antiga.goto(url+'/painel#pacotes');await pronta(antiga);
  await p.locator('#sair').click();await expect(p).toHaveURL(url+'/login?logout');await expect(p.locator('[role=status]')).toContainText('Sessão encerrada.');
  assert.equal((await antiga.request.get(url+'/api/sessao')).status(),401);
  await antiga.evaluate(async base=>{const {api}=await import(base+'/painel-base.js');try{await api('/api/viagens');}catch{}},contexto);
  await expect(antiga.locator('#sessao-expirada')).toBeVisible();await expect(antiga.locator('main')).toBeHidden();
  await p.goBack();if(new URL(p.url()).pathname===contexto+'/painel')await expect(p.locator('#sessao-expirada')).toBeVisible();else await expect(p).toHaveURL(/\/login/);
  await p.goto(url+'/#documentos');await expect(p).toHaveURL(/\/login#documentos$/);await login(p,'admin-sintetico');
  await expect(p).toHaveURL(url+'/painel#documentos');await expect(p.locator('main')).toHaveAttribute('data-pagina-atual','viagens');
  await p.goto(url+'/painel#pacotes');await pronta(p);await p.context().clearCookies();
  const antes=pedidos.length;await p.locator('#atualizar').click();await expect(p.locator('#sessao-expirada')).toBeVisible();await expect(p.locator('main')).toBeHidden();
  await expect(p.getByRole('link',{name:'Entrar novamente'})).toHaveAttribute('href',contexto+'/login?expirada');
  assert.ok(!pedidos.slice(antes).some(r=>r.metodo==='POST'),'Expiração não reaplica ações');
  await capturar(p,prefixo+'sessao-expirada');
  checks.push(prefixo+'Logout confirmado, aba antiga e histórico sem dados acessíveis; favorito anônimo mantém hash e expiração oferece login sem repetir ações');
  await p.context().close();await stop();
}
async function run(){
  execFileSync('keytool',['-genkeypair','-alias','sintetico','-keyalg','RSA','-storetype','PKCS12','-keystore',path.join(base,'tls.p12'),'-storepass:env','ROTABRASIL_TESTE_SENHA','-keypass:env','ROTABRASIL_TESTE_SENHA','-dname','CN=localhost','-validity','1','-ext','SAN=ip:127.0.0.1,dns:localhost'],{env,stdio:'ignore'});
  await start(false);
  // Somente preparação da base nova e da conta sintética; todo o aceite ocorre em operacao/HTTPS.
  const r=await fetch(url+'/api/admin/usuarios',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({usuario:'admin-sintetico',senha,papel:'ADMIN'})});assert.equal(r.status,200);
  await stop();browser=await chromium.launch({channel:process.env.ROTABRASIL_BROWSER_CHANNEL||'chrome',headless:true});
  await verificar('');await verificar('/rota');assert.deepEqual(errors,[]);
  assert.ok(!pedidos.some(r=>r.metodo==='POST'&&/tentativas-email|confirmacoes-email-real|\/confirmacoes$/.test(r.url)));
  const resultado={checks,errors,perfilValidado:'operacao',https:true,contextPaths:['','/rota'],envioExternoExecutado:false};
  fs.writeFileSync(path.join(out,'resultado.json'),JSON.stringify(resultado,null,2));console.log(JSON.stringify(resultado,null,2));
}
run().catch(async e=>{console.error(e);fs.writeFileSync(path.join(out,'falha.log'),String(e));fs.writeFileSync(path.join(out,'servidor.log'),logs);if(paginaAtual&&!paginaAtual.isClosed())await paginaAtual.screenshot({path:path.join(out,'falha.png'),fullPage:true}).catch(()=>{});process.exitCode=1;})
  .finally(async()=>{await browser?.close();await stop();fs.rmSync(base,{recursive:true,force:true,maxRetries:5,retryDelay:500});});
