/* Chamado SOMENTE pelo teste Java CentralEnviosRealHttpTest com SMTP substituído por Mockito. */
'use strict';
const {chromium,expect}=require('playwright/test');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const [url,out,trip]=process.argv.slice(2);const errors=[],checks=[];let browser,page;
async function main(){
  if(!url?.startsWith('http://127.0.0.1:')||!trip)throw Error('Requer fixture Java local com transporte double');
  fs.mkdirSync(out,{recursive:true});browser=await chromium.launch({channel:'chrome',headless:true});
  page=await browser.newPage({viewport:{width:1440,height:1000},locale:'pt-BR'});page.on('pageerror',e=>errors.push(e.message));
  const screenshot=async name=>page.screenshot({path:path.join(out,name+'.png'),fullPage:true});
  async function navegar(hash){await page.evaluate(h=>{location.hash=h;},hash);await expect(page.locator('main')).toHaveAttribute('data-pagina-atual',hash==='documentos'?'viagens':hash);}
  async function login(nome){await page.goto(url+'/login');await page.locator('[name=username]').fill(nome);await page.locator('[name=password]').fill('SenhaSomenteFixture12!');await page.locator('button[type=submit]').click();await page.locator('#abertura').waitFor({state:'hidden'});}
  async function selecionar(){await navegar('documentos');await page.locator('#viagem-selecionada').selectOption(trip);await page.locator('#lista-documentos input').check();await navegar('email');}
  async function salvarRemetente(remetente){
    const s=await(await page.request.get(url+'/api/sessao')).json(),c=await(await page.request.get(url+'/api/configuracoes')).json();
    const r=await page.request.put(url+'/api/configuracoes',{headers:{[s.csrfHeader]:s.csrf},data:{versao:c.versao,remetente,exportacaoAutomatica:false,subpasta:''}});assert.equal(r.status(),200);
  }
  await login('admin');await navegar('configuracoes');
  await expect(page.locator('#config-documental-estado')).toContainText('GMAIL',{timeout:20000});
  await expect(page.locator('#config-documental-estado')).toContainText('não comprovadas');
  await page.locator('#config-verificar-conta').click();assert.equal(await page.locator('#config-conta-detalhes').evaluate(n=>n.open),false);
  await screenshot('configuracao-gmail-documental');checks.push('Configurações identifica Gmail documental sem promover endereço salvo a autenticação; link não abre requisitos Microsoft');
  await selecionar();await page.locator('#destinatario').fill('cliente@example.invalid');
  await page.locator('#gerar-previa').click();await expect(page.locator('#resultado-previa')).toContainText('ENVIO REAL');
  await expect(page.locator('#simular-envio')).toBeDisabled();await page.locator('#conferi-envio').check();await expect(page.locator('#simular-envio')).toBeEnabled();
  await salvarRemetente('divergente@example.invalid');await page.locator('#recarregar-envio').click();
  await expect(page.locator('#bloqueios-envio')).toContainText('difere');await expect(page.locator('#simular-envio')).toBeDisabled();
  await expect(page.locator('#configurar-envio')).toBeVisible();await screenshot('remetente-divergente-admin');
  await salvarRemetente('remetente@example.invalid');await page.locator('#recarregar-envio').click();await expect(page.locator('#simular-envio')).toBeEnabled();
  checks.push('Login ADMIN, CSRF e remetente divergente comprovados; recuperação revalida prontidão');
  await screenshot('conferencia-real-double-desktop');
  let perdeu=false,posts=0;
  await page.route('**/tentativas-email',async route=>{
    if(route.request().method()==='POST'){posts++;if(!perdeu){perdeu=true;await route.fetch();await route.abort();return;}}
    await route.continue();
  });
  await page.locator('#simular-envio').dblclick({timeout:1500}).catch(()=>{});
  await expect(page.locator('#recuperar-confirmacao')).toBeVisible();assert.equal(posts,1);
  await page.locator('#recuperar-confirmacao').click();await expect(page.locator('#resultado-tentativa')).toContainText('ACEITO_SMTP');
  let attempts=await(await page.request.get(url+`/api/viagens/${trip}/tentativas-email`)).json();assert.equal(attempts.length,1);
  await page.locator('#email-historico-tentativas').evaluate(n=>{n.open=true;});await screenshot('aceite-double-recuperado');
  checks.push('Clique duplo e resposta perdida: recuperação da mesma confirmação, uma tentativa, sem segundo transporte');
  await page.getByRole('button',{name:'Preparar reenvio confirmado',exact:true}).click();await page.locator('#gerar-previa').click();
  await page.locator('#conferi-envio').check();await page.setViewportSize({width:390,height:844});await page.waitForTimeout(400);await screenshot('conferencia-real-double-mobile');
  await page.locator('#simular-envio').click();await expect(page.locator('#resultado-tentativa')).toContainText('DESCONHECIDO');
  attempts=await(await page.request.get(url+`/api/viagens/${trip}/tentativas-email`)).json();assert.equal(attempts.length,2);assert.equal(attempts[1].anteriorId,attempts[0].id);
  await page.waitForTimeout(600);assert.equal(posts,3);await screenshot('resultado-desconhecido-mobile');
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  checks.push('Reenvio deliberado vinculado; resultado DESCONHECIDO sem repetição automática; celular sem overflow');
  await page.setViewportSize({width:1440,height:1000});
  await salvarRemetente('divergente@example.invalid');
  const s=await(await page.request.get(url+'/api/sessao')).json();assert.equal((await page.request.post(url+'/logout',{headers:{[s.csrfHeader]:s.csrf}})).status(),200);
  await page.locator('#recarregar-envio').click();await expect(page.locator('#sessao-expirada')).toBeVisible();await screenshot('sessao-expirada');
  await expect(page.locator('#campos-configuracoes')).toHaveJSProperty('disabled',true);await expect(page.locator('#motivo-configuracoes')).toContainText('Sessão encerrada');
  await login('leitor');await selecionar();await expect(page.locator('#destinatario')).toBeDisabled();await expect(page.locator('#motivo-previa')).toContainText('Permissão de consulta');await expect(page.locator('#configurar-envio')).toBeHidden();await screenshot('permissao-consulta');
  const sessao=await(await page.request.get(url+'/api/sessao')).json();
  assert.equal((await page.request.post(url+`/api/viagens/${trip}/conferencias-email`,{headers:{[sessao.csrfHeader]:sessao.csrf},data:{}})).status(),403);
  checks.push('Expiração real de sessão e CONSULTA no navegador: conferência bloqueada, sem ação administrativa proibida; API retorna 403');
  await navegar('configuracoes');await expect(page.locator('#motivo-configuracoes')).toContainText('ADMIN');await expect(page.locator('#campos-configuracoes')).toHaveJSProperty('disabled',true);await expect(page.locator('#config-remetente')).toBeDisabled();
  assert.equal((await page.request.get(url+'/api/configuracoes')).status(),403);await screenshot('configuracoes-consulta-bloqueada');
  checks.push('Configurações: CONSULTA mantém fieldset bloqueado por permissão, motivo ADMIN visível e API 403');
  await page.locator('#sair').click();await page.waitForURL(/\/login(?:\?.*)?$/);await login('operador');await selecionar();
  await expect(page.locator('#bloqueios-envio')).toContainText('difere');await expect(page.locator('#configurar-envio')).toBeHidden();
  await expect(page.locator('#orientacao-envio')).toContainText('Solicite');await expect(page.locator('#destinatario')).toBeEnabled();await screenshot('remetente-divergente-operador');
  checks.push('OPERADOR pode preparar e recebe diagnóstico de divergência com orientação ao ADMIN, sem link de alteração proibida');
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'resultado-real-double.json'),JSON.stringify({checks,errors,transporte:'DOUBLE Mockito; nenhum SMTP',envioExterno:false},null,2));console.log(JSON.stringify({checks,errors},null,2));
}
main().catch(async e=>{console.error(e);if(page)await page.screenshot({path:path.join(out,'falha-real.png'),fullPage:true}).catch(()=>{});process.exitCode=1;}).finally(async()=>{if(browser)await browser.close();});
