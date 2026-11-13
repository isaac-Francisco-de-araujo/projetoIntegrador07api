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
   await expect(p.locator('#abertura')).toBeHidden({timeout:10000});
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
   for(const [largura,altura] of [[1366,768],[1920,1080],[390,844]]){
    await p.setViewportSize({width:largura,height:altura});
    assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),nome+' sem overflow');
    await p.screenshot({path:path.join(out,nome+'-'+largura+'.png'),fullPage:true});
  }
  await p.setViewportSize({width:1366,height:768});
}
async function design(p){
  await expect(p.locator('html')).toHaveAttribute('data-tema','claro');
  await expect(p.locator('#resumo-pendencias')).toBeEmpty();
  await expect(p.locator('#lista-ultimas-viagens')).toContainText('Importar primeiro ZIP');
  async function nav(tela){
    await p.evaluate(t=>{location.hash=t;},tela);
    await expect(p.locator('main')).toHaveAttribute('data-pagina-atual',tela);
    await p.waitForFunction(()=>scrollY===0);
  }
  await nav('importacao');
  await expect(p.locator('#importar')).toHaveText('Selecione um XML ou PDF');
  await expect(p.locator('#importar')).toBeDisabled();
  const xml=fs.readFileSync(path.join(root,'back/src/main/resources/email/cte-sintetico.xml'));
  await p.locator('#arquivo').setInputFiles({name:'cte-sintetico.xml',mimeType:'application/xml',buffer:xml});
  await expect(p.locator('#arquivo-info')).toContainText('XML de CT-e');
  await expect(p.locator('#importar')).toHaveText('Importar XML');
  await p.locator('#importar').click();
  await expect(p.locator('#resultado-importacao')).not.toHaveClass(/error/);
  await expect(p.locator('#total-viagens')).toHaveText('1');
  await p.locator('#remover-arquivo-selecao').click();
  await expect(p.locator('#importar')).toBeDisabled();
  await p.locator('#arquivo').setInputFiles({name:'conferencia.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.7\nSINTETICO')});
  await expect(p.locator('#importar')).toHaveText('Conferir PDF');
  await expect(p.locator('#arquivo-info')).toContainText('PDF');
  await p.locator('#remover-arquivo-selecao').click();
  await nav('viagens');await p.locator('#buscar-viagem').fill('66213');
  await expect(p.locator('#lista-viagens')).toContainText('66213');
  await p.locator('#lista-viagens').getByRole('button',{name:/Detalhes/}).click();
  await expect(p.locator('#detalhes-cadastro')).toBeVisible();
  await p.keyboard.press('Escape');await expect(p.locator('#detalhes-cadastro')).toBeHidden();
  await expect(p.locator('#buscar-viagem')).toHaveValue('66213');
  await p.evaluate(()=>scrollTo(0,100));
  const posicaoLista=await p.evaluate(()=>scrollY);
  await p.locator('#lista-viagens').getByRole('button',{name:'Ver documentos da viagem 66213',exact:true}).click();
  await expect(p.locator('#documentos-conteudo')).toBeVisible();
  await p.locator('#voltar-lista-viagens').click();
  await expect(p.locator('#buscar-viagem')).toBeFocused();
  await expect(p.locator('#buscar-viagem')).toHaveValue('66213');
  await p.waitForFunction(y=>Math.abs(scrollY-Math.min(y,Math.max(0,document.documentElement.scrollHeight-innerHeight)))<=1,posicaoLista);
  const telas=['inicio','pacotes','importacao','viagens','clientes','fila','busca','email','processamentos','configuracoes','administracao','saude'];
  for(const tema of ['claro','escuro']){
    await p.evaluate(t=>{localStorage.setItem('rotabrasil-tema',t);document.documentElement.dataset.tema=t;},tema);
    for(const [width,height] of [[1366,768],[1920,1080],[390,844]]){
      await p.setViewportSize({width,height});
      for(const tela of telas){
        await nav(tela);
        await p.waitForLoadState('networkidle');
        assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),`${tema} ${tela} ${width} sem overflow`);
        await p.screenshot({path:path.join(out,`design-${tema}-${tela}-${width}.png`),fullPage:true});
        await p.evaluate(()=>scrollTo(0,document.documentElement.scrollHeight));
      }
    }
  }
  await p.setViewportSize({width:390,height:844});await nav('pacotes');
  await p.locator('#menu-abrir').click();await expect(p.locator('#menu-abrir')).toHaveAttribute('aria-expanded','true');
  await p.locator('#menu-lateral nav a').last().focus();await p.keyboard.press('Tab');
  await expect(p.locator('#menu-lateral .brand')).toBeFocused();
  await p.keyboard.press('Escape');await expect(p.locator('#menu-abrir')).toBeFocused();
  await p.setViewportSize({width:1366,height:768});
  await p.evaluate(()=>localStorage.setItem('rotabrasil-tema','escuro'));
  await p.goto(url+'/painel');await pronta(p);await expect(p.locator('html')).toHaveAttribute('data-tema','escuro');
  await p.locator('#alternar-tema').click();await expect(p.locator('html')).toHaveAttribute('data-tema','claro');
  assert.equal((await p.request.get(url+'/painel-visual.css')).status(),200);
  checks.push('Design: 12 abas × 3 resoluções × 2 temas em HTTPS; novas abas no topo, importação XML real sintética, rótulo PDF, filtros/detalhes, preferência persistida e foco do menu móvel');
}
async function verificar(contexto){
  await start(true,contexto);const p=await nova(),prefixo=contexto?'contexto-':'';
  const inicio=pedidos.length;await p.goto(url+'/');await p.waitForLoadState('networkidle');
   await expect(p.getByRole('heading',{level:1})).toHaveText('Seus documentos no caminho certo.');
   assert.ok(await p.getByRole('link',{name:'Entrar no sistema',exact:true}).evaluate(e=>e.getBoundingClientRect().bottom<=innerHeight),'Entrar visível sem rolar no notebook');
  const recursos=pedidos.slice(inicio).map(r=>new URL(r.url).pathname);
   const icones=[contexto+'/favicon.ico',contexto+'/assets/marca/favicon-16.png',contexto+'/assets/marca/favicon-32.png'];
   assert.deepEqual(recursos.filter(r=>!icones.includes(r)).sort(),[contexto+'/',contexto+'/acesso.css',contexto+'/assets/marca/logo-rotabrasil.webp',contexto+'/assets/marca/landing-rota.webp',contexto+'/acesso.js'].sort());
  assert.ok(await p.locator('.marca img').evaluate(i=>i.complete&&i.naturalWidth>0));
  await capturar(p,prefixo+'apresentacao');
   await p.keyboard.press('Tab');await expect(p.getByRole('link',{name:'Entrar no sistema',exact:true})).toBeFocused();
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
   checks.push(prefixo+'Apresentação só carrega os recursos públicos locais esperados e favicon; teclado, celular, CSRF, erro genérico, painel/downloads protegidos e JSON 401');
  await p.goto(url+'/login?continue=https://externo.invalid');await login(p,'admin-sintetico');await expect(p).toHaveURL(url+'/painel');
  await capturar(p,prefixo+'painel');
  if(!contexto)await design(p);
  const cookies=await p.context().cookies();assert.ok(cookies.some(c=>c.name==='JSESSIONID'&&c.secure&&c.httpOnly&&c.sameSite==='Strict'));
  await p.goto(url+'/login');await expect(p).toHaveURL(url+'/painel');await pronta(p);
   await p.goto(url+'/');await p.getByRole('link',{name:'Entrar no sistema',exact:true}).click();await expect(p).toHaveURL(url+'/painel');await pronta(p);
  await p.goto(url+'/index.html#documentos');await pronta(p);await expect(p.locator('main')).toHaveAttribute('data-pagina-atual','viagens');
  await p.goto(url+'/#operadores');await pronta(p);await expect(p).toHaveURL(url+'/painel#operadores');await expect(p.locator('main')).toHaveAttribute('data-pagina-atual','administracao');
  checks.push(prefixo+'Login válido e sessão existente levam ao painel sem loops/destino externo; index.html, favorito antigo e alias preservados');
  // Cadastro e alteração pelo frontend existente em perfil operacional, nunca por cadastro público.
  const novo=contexto?'contexto-operador':'novo-operador';
  await p.locator('#adicionar-operador').click();await p.locator('#novo-usuario').fill(novo);await p.locator('#nova-senha').fill(senha);await p.locator('#novo-papel').selectOption('CONSULTA');
  const cadastro=p.waitForResponse(r=>r.url()===url+'/api/admin/usuarios'&&r.request().method()==='POST',{timeout:30000});
  await p.locator('#form-usuario button[type=submit]').click();assert.equal((await cadastro).status(),200,'Cadastro administrativo deve ser aceito pelo backend');await expect(p.locator('#lista-usuarios')).toContainText(novo,{timeout:30000});
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
