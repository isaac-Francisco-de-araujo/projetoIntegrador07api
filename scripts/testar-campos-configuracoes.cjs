/* H2 novo, ADMIN local sintético; falhas HTTP controladas, nenhum envio. */
'use strict';
const {chromium,expect}=require('playwright/test');
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const [jar,out,modo]=process.argv.slice(2);
if(!jar||!out)throw Error('Uso: node scripts/testar-campos-configuracoes.cjs JAR SAIDA [--diagnosticar]');
fs.mkdirSync(out,{recursive:true});const base=fs.mkdtempSync(path.join(os.tmpdir(),'rotabrasil-campos-config-'));
const logfile=path.join(out,'servidor.log'),log=fs.openSync(logfile,'w');
const server=spawn('java',['-Xmx512m','-jar',path.resolve(jar),'--server.address=127.0.0.1','--server.port=0','--spring.profiles.active=desenvolvimento',
  '--spring.datasource.url=jdbc:h2:file:'+base.replaceAll('\\','/')+'/banco;WRITE_DELAY=0','--spring.flyway.enabled=true',
  '--spring.h2.console.enabled=false','--rotabrasil.seguranca.ativa=false','--rotabrasil.email.modo=SIMULADO',
  '--rotabrasil.notificacoes.modo=SIMULADO','--rotabrasil.teste-email.modo=SIMULADO','--rotabrasil.arquivos.viagens='+base+'/acervo',
  '--rotabrasil.exportacao.raiz='+base+'/exportacao'],{stdio:['ignore',log,log]});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));let browser,page,liberar;const estados=[],errors=[],checks=[];let identidade;
async function observar(etapa){
  const e=await page.evaluate(()=>{const f=document.getElementById('campos-configuracoes'),i=document.getElementById('config-remetente');return {
    fieldsetDisabled:f.disabled,inputDisabled:i.disabled,inputDisabledEfetivo:i.matches(':disabled'),
    bloqueio:f.dataset.bloqueio||null,busy:f.getAttribute('aria-busy'),mensagem:document.getElementById('estado-configuracoes').textContent,
    motivo:document.getElementById('motivo-configuracoes')?.textContent||null,
    contaTesteDisabled:document.getElementById('campos-conta-teste').disabled,
    admin:document.documentElement.dataset.administrador,aberturaOculta:document.getElementById('abertura').hidden};});estados.push({etapa,...e});return e;
}
async function falharProximaConsulta(){const gate=Promise.withResolvers(),recebeu=Promise.withResolvers();liberar=gate.resolve;
  await page.route('**/api/configuracoes',async r=>{recebeu.resolve();await gate.promise;await r.fulfill({status:503,json:{}});},{times:1});
  return {recebeu:recebeu.promise,liberar:()=>{gate.resolve();liberar=null;}};
}
async function main(){
  let url;for(let i=0;i<900;i++){const ports=[...fs.readFileSync(logfile,'utf8').matchAll(/Tomcat started on port (\d+)/g)];if(ports.length){url='http://127.0.0.1:'+ports.at(-1)[1];try{if((await fetch(url+'/api/sessao')).ok)break;}catch{}}if(server.exitCode!==null)throw Error('Servidor encerrou');await sleep(100);}
  browser=await chromium.launch({channel:'chrome',headless:true});page=await browser.newPage({viewport:{width:1440,height:1000},locale:'pt-BR'});page.on('pageerror',e=>errors.push(e.message));
  identidade={jar:path.resolve(jar),sha256:crypto.createHash('sha256').update(fs.readFileSync(jar)).digest('hex'),versao:await(await page.request.get(url+'/api/versao')).json()};
  await page.goto(url+'/painel',{waitUntil:'networkidle'});await page.locator('#abertura').waitFor({state:'hidden'});
  assert.equal((await observar('HTML inicial, antes de abrir módulo')).fieldsetDisabled,true);
  const primeira=await falharProximaConsulta();await page.locator('.sidebar nav a[href="#configuracoes"]').click();await primeira.recebeu;
  await observar('primeira consulta pendente');await expect(page.locator('#config-remetente')).toBeDisabled();primeira.liberar();
  await expect(page.locator('#estado-configuracoes')).toContainText('Não foi possível');await expect(page.locator('#recarregar-configuracoes')).toBeEnabled();
  await observar('primeira consulta falhou, nenhum snapshot carregado');await expect(page.locator('#campos-configuracoes')).toHaveJSProperty('disabled',true);await expect(page.locator('#config-remetente')).toBeDisabled();
  await page.screenshot({path:path.join(out,'primeira-consulta-falhou.png'),fullPage:true});
  await page.locator('#recarregar-configuracoes').click();await expect(page.locator('#config-remetente')).toBeEnabled({timeout:20000});await expect(page.locator('#campos-configuracoes')).toHaveJSProperty('disabled',false);
  await observar('recuperação 200, SMTP ausente');checks.push('HTML inicial/carregamento/primeira falha mantêm fieldset bloqueado; recuperação 200 habilita edição sem SMTP');
  const segunda=await falharProximaConsulta();await page.locator('#recarregar-configuracoes').click();await segunda.recebeu;
  await observar('reconsulta pendente após sucesso');await expect(page.locator('#campos-configuracoes')).toHaveJSProperty('disabled',true);await expect(page.locator('#config-remetente')).toBeDisabled();
  if(modo!=='--diagnosticar')await expect(page.locator('#estado-configuracoes')).toContainText('Carregando configuração');
  segunda.liberar();await expect(page.locator('#estado-configuracoes')).toContainText('Não foi possível');await expect(page.locator('#recarregar-configuracoes')).toBeEnabled();
  await observar('reconsulta falhou após snapshot anterior');await page.screenshot({path:path.join(out,'reconsulta-falhou.png'),fullPage:true});
  if(modo==='--diagnosticar'){checks.push('Estado anterior observado sem presumir causa: ver estados de cada consulta');return;}
  await expect(page.locator('#campos-configuracoes')).toHaveJSProperty('disabled',true);await expect(page.locator('#motivo-configuracoes')).toContainText('consulta válida');
  await page.locator('#config-conta-teste > summary').click();await page.locator('#recarregar-conta-teste').click();await expect(page.locator('#conta-teste-provedor')).toBeEnabled();
  await expect(page.locator('#campos-configuracoes')).toHaveJSProperty('disabled',true);await observar('conta-teste carregada não reabilita configuração desconhecida');
  await page.locator('#recarregar-configuracoes').click();await expect(page.locator('#config-remetente')).toBeEnabled({timeout:20000});
  await page.locator('#config-remetente').click();await page.keyboard.type('rascunho@example.invalid');await expect(page.locator('#config-remetente')).toHaveValue('rascunho@example.invalid');
  await observar('recuperação final e digitação real');await page.screenshot({path:path.join(out,'campos-recuperados.png'),fullPage:true});
  checks.push('Reconsulta falha invalida prontidão da edição sem apagar rascunho; evento da conta independente não libera snapshot desatualizado; nova consulta recupera edição');
  await page.route('**/api/configuracoes/conta-teste',r=>r.fulfill({status:503,json:{}}),{times:1});
  await page.locator('#recarregar-conta-teste').click();await expect(page.locator('#conta-teste-estado')).toContainText('Não foi possível');
  await expect(page.locator('#campos-conta-teste')).toHaveJSProperty('disabled',true);await expect(page.locator('#config-remetente')).toBeEnabled();await expect(page.locator('#config-remetente')).toHaveValue('rascunho@example.invalid');
  await observar('falha só na conta de teste, edição da operação permanece disponível');
  await page.locator('#recarregar-conta-teste').click();await expect(page.locator('#conta-teste-provedor')).toBeEnabled();
  const gate=Promise.withResolvers(),recebeu=Promise.withResolvers();liberar=gate.resolve;
  await page.route('**/api/configuracoes',async r=>{assert.equal(r.request().method(),'PUT');recebeu.resolve();await gate.promise;await r.continue();},{times:1});
  await page.locator('#salvar-configuracoes').click();await recebeu.promise;
  await expect(page.locator('#campos-configuracoes')).toHaveJSProperty('disabled',true);await expect(page.locator('#motivo-configuracoes')).toContainText('Processamento');
  await observar('gravação sintética pendente, bloqueio legítimo');gate.resolve();liberar=null;
  await expect(page.locator('#config-remetente')).toBeEnabled();await expect(page.locator('#config-situacao-email')).toContainText('não comprovada');
  await observar('gravação concluída, edição recuperada sem autenticar SMTP');
  checks.push('Falha da conta de teste bloqueia apenas seu fieldset; gravação da operação mantém bloqueio legítimo e recuperação; salvar endereço não autentica SMTP');
  assert.deepEqual(errors,[]);
}
main().then(()=>{fs.writeFileSync(path.join(out,'resultado.json'),JSON.stringify({identidade,checks,estados,errors,envioExterno:false},null,2));console.log(JSON.stringify({checks,estados,errors},null,2));}).catch(async e=>{console.error(e);if(page){await observar('falha do teste').catch(()=>{});await page.screenshot({path:path.join(out,'falha.png'),fullPage:true}).catch(()=>{});}fs.writeFileSync(path.join(out,'resultado-falha.json'),JSON.stringify({identidade,estados,errors,erro:e.message},null,2));process.exitCode=1;}).finally(async()=>{liberar?.();if(browser)await browser.close();server.kill();await Promise.race([new Promise(r=>server.once('exit',r)),sleep(15000)]);fs.closeSync(log);fs.rmSync(base,{recursive:true,force:true,maxRetries:10,retryDelay:500});});
