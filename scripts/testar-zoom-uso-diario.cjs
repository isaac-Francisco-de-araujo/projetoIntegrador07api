/* Zoom REAL do Chrome, configurado pela preferência do navegador; sem CSS zoom/transform. */
'use strict';
const {chromium,expect}=require('playwright/test'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{spawn}=require('node:child_process');
const [jar,outArg]=process.argv.slice(2),root=path.resolve(__dirname,'..'),out=path.resolve(outArg||'');
if(!jar||!outArg||out===root||out.startsWith(root+path.sep))throw Error('Uso: node scripts/testar-zoom-uso-diario.cjs JAR SAIDA_EXTERNA');
fs.mkdirSync(out,{recursive:true});const base=fs.mkdtempSync(path.join(out,'.isolado-')),wait=ms=>new Promise(r=>setTimeout(r,ms));let logs='',context;
const server=spawn(process.env.JAVA||'java',['-Xmx512m','-jar',path.resolve(jar),'--server.port=0','--server.address=127.0.0.1','--spring.profiles.active=desenvolvimento','--spring.datasource.url=jdbc:h2:mem:zoom_sintetico','--spring.flyway.enabled=true','--spring.h2.console.enabled=false','--rotabrasil.email.smtp.ativado=false','--rotabrasil.email.modo=SIMULADO','--rotabrasil.arquivos.viagens='+path.join(base,'acervo')],{cwd:root});for(const s of [server.stdout,server.stderr])s.on('data',b=>logs+=b);
(async()=>{
 let port;for(let i=0;i<1200;i++){port=logs.match(/Tomcat started on port (\d+)/)?.[1];if(port)break;if(server.exitCode!==null)throw Error('Servidor terminou');await wait(100);}assert.ok(port);
 context=await chromium.launchPersistentContext(path.join(base,'chrome'),{channel:process.env.ROTABRASIL_BROWSER_CHANNEL||'chrome',headless:false,viewport:null,args:['--window-size=1366,768']});
 const settings=await context.newPage();await settings.goto('chrome://settings/appearance');await expect(settings.locator('#zoomLevel')).toBeVisible();
 const page=await context.newPage(),medicoes=[],errors=[];page.on('pageerror',e=>errors.push(e.message));
 for(const zoom of ['1','1.5','2']){
  await settings.locator('#zoomLevel').selectOption(zoom);
  await page.goto(`http://127.0.0.1:${port}/#pacotes`);await expect(page.locator('body')).not.toHaveClass(/is-loading/);await expect(page.locator('#sessao-usuario')).toContainText('Sessão local');
  const m=await page.evaluate(()=>({largura:innerWidth,altura:innerHeight,dpr:devicePixelRatio,overflow:document.documentElement.scrollWidth>innerWidth+1}));medicoes.push({zoom:Number(zoom),...m});assert.equal(m.overflow,false);
  for(const tema of ['claro','escuro']){await page.evaluate(t=>document.documentElement.dataset.tema=t,tema);for(const tela of ['pacotes','importacao','configuracoes','email','saude','clientes','viagens','fila','busca','administracao']){await page.evaluate(h=>location.hash=h,tela);await expect(page.locator('main')).toHaveAttribute('data-pagina-atual',tela);if(tela==='configuracoes')await expect(page.locator('#config-remetente')).toBeEnabled();await page.waitForLoadState('networkidle');assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),`${tela} ${tema} ${zoom}`);await page.screenshot({path:path.join(out,`${tema}-${tela}-zoom-${Number(zoom)*100}.png`),fullPage:true});}}
 }
 assert.ok(medicoes[1].largura<medicoes[0].largura);assert.ok(medicoes[2].largura<medicoes[1].largura);assert.ok(medicoes[2].dpr>medicoes[0].dpr);assert.deepEqual(errors,[]);
 fs.writeFileSync(path.join(out,'resultado.json'),JSON.stringify({medicoes,errors,envioExternoExecutado:false},null,2));console.log(JSON.stringify({medicoes,errors},null,2));
})().catch(e=>{console.error(e);fs.writeFileSync(path.join(out,'falha.log'),String(e)+'\n'+logs);process.exitCode=1;}).finally(async()=>{await context?.close();server.kill();await Promise.race([new Promise(r=>server.once('exit',r)),wait(10000)]);fs.rmSync(base,{recursive:true,force:true,maxRetries:5,retryDelay:500});});
