/* Teste integrado com PDFs válidos gerados em memória, H2 e acervo isolados. */
'use strict';
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');
const [jarArg, outputArg] = process.argv.slice(2);
if (!jarArg || !outputArg) throw Error('Uso: node scripts/testar-previa-pdf.cjs JAR SAIDA_EXTERNA');
const root = path.resolve(__dirname, '..'), output = path.resolve(outputArg);
if (output === root || output.startsWith(root + path.sep)) throw Error('A saída deve ficar fora do repositório');
fs.mkdirSync(output, {recursive:true});
const base = fs.mkdtempSync(path.join(output, '.acervo-isolado-'));
const checks = [], errors = [], external = [];
let logs = '', browser;
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
const server = spawn(process.env.JAVA || 'java', ['-jar', path.resolve(jarArg), '--server.port=0', '--server.address=127.0.0.1',
  '--spring.profiles.active=desenvolvimento', '--rotabrasil.seguranca.ativa=false', '--spring.datasource.url=jdbc:h2:mem:pdf_ui',
  '--spring.flyway.enabled=true', '--spring.h2.console.enabled=false', '--rotabrasil.email.modo=SIMULADO',
  '--rotabrasil.email.smtp.ativado=false', '--rotabrasil.arquivos.viagens='+base+'/viagens'], {cwd:root});
server.stdout.on('data', data => logs += data); server.stderr.on('data', data => logs += data);

function zip(entries) {
  const locals = [], centrals = []; let offset = 0;
  for (const [name, value] of entries) {
    const data = Buffer.from(value), filename = Buffer.from(name); let crc = 0xffffffff;
    for (const b of data) { crc ^= b; for (let i=0;i<8;i++) crc=(crc>>>1)^((crc&1)?0xedb88320:0); } crc=(crc^0xffffffff)>>>0;
    const local=Buffer.alloc(30);local.writeUInt32LE(0x04034b50);local.writeUInt16LE(20,4);local.writeUInt16LE(0x800,6);
    local.writeUInt32LE(crc,14);local.writeUInt32LE(data.length,18);local.writeUInt32LE(data.length,22);local.writeUInt16LE(filename.length,26);
    const central=Buffer.alloc(46);central.writeUInt32LE(0x02014b50);central.writeUInt16LE(20,4);central.writeUInt16LE(20,6);central.writeUInt16LE(0x800,8);
    central.writeUInt32LE(crc,16);central.writeUInt32LE(data.length,20);central.writeUInt32LE(data.length,24);central.writeUInt16LE(filename.length,28);central.writeUInt32LE(offset,42);
    locals.push(local,filename,data);centrals.push(central,filename);offset+=local.length+filename.length+data.length;
  }
  const central=Buffer.concat(centrals),end=Buffer.alloc(22);end.writeUInt32LE(0x06054b50);end.writeUInt16LE(entries.length,8);end.writeUInt16LE(entries.length,10);end.writeUInt32LE(central.length,12);end.writeUInt32LE(offset,16);
  return Buffer.concat([...locals,central,end]);
}

async function run() {
  let port;
  for(let i=0;i<600;i++){port=logs.match(/Tomcat started on port (\d+)/)?.[1];if(port)break;if(server.exitCode!==null)throw Error('Servidor terminou');await wait(100);}
  if(!port)throw Error('Servidor não iniciou');
  const url='http://127.0.0.1:'+port;
  browser=await chromium.launch({channel:process.env.ROTABRASIL_BROWSER_CHANNEL || 'chrome',headless:true});
  const page=await browser.newPage({viewport:{width:1440,height:1000},locale:'pt-BR',reducedMotion:'reduce'});
  page.on('pageerror',error=>errors.push(error.message));
  page.on('request',request=>{if(!request.url().startsWith(url)&&!request.url().startsWith('blob:')&&!request.url().startsWith('data:'))external.push(request.url());});
  await page.goto(url+'/painel',{waitUntil:'networkidle'});await page.locator('#abertura').waitFor({state:'hidden'});
  await page.locator('.sidebar nav a[href="#pacotes"]').click();
  const pdfs=await page.evaluate(async()=>{
    const {PDFDocument,StandardFonts,rgb,degrees}=await import('/webjars/pdf-lib/1.17.1/dist/pdf-lib.esm.min.js');
    const result=[];
    for(const name of ['FATURA SINTETICA','COMPROVANTE SINTETICO']){
      const doc=await PDFDocument.create(),font=await doc.embedFont(StandardFonts.Helvetica);
      for(let n=0;n<2;n++){
        const p=doc.addPage([595,842]);p.drawRectangle({x:0,y:750,width:595,height:92,color:rgb(.09,.2,.18)});
        p.drawText(name+' - SEM VALOR FISCAL',{x:40,y:790,size:18,font,color:rgb(.85,.95,.7)});
        p.drawText('CLIENTE SINTETICO ORIGINAL',{x:40,y:700,size:12,font});
        p.drawText('Data: 01/01/2026',{x:40,y:665,size:12,font});
        p.drawText('Pagina '+(n+1)+' - Documento exclusivamente de teste.',{x:40,y:620,size:12,font});
        if(n===1)p.setRotation(degrees(90));
      }
      result.push(Array.from(await doc.save()));
    }
    return result;
  });
  const xml=fs.readFileSync(path.join(root,'back/src/main/resources/email/cte-sintetico.xml'));
  const original=zip([['cte.xml',xml],['faturas/documento.pdf',pdfs[0]],['viagem/documento.pdf',pdfs[1]]]);
  fs.writeFileSync(path.join(output,'pacote-sintetico.zip'),original);
  await page.locator('#arquivo-zip').setInputFiles({name:'pacote-sintetico.zip',mimeType:'application/zip',buffer:original});
  const firstResponse=page.waitForResponse(r=>r.url().includes('/api/pacotes/inspecao')&&r.request().method()==='POST');
  await page.locator('#inspecionar-zip').click();const first=await (await firstResponse).json();
  assert.equal(await page.locator('[data-previa-pdf]').count(),2);
  for(const id of ['a1','a2']){
    await page.locator('#vinculo-'+id+' select').selectOption(id==='a1'?'FATURA':'VIAGEM');
    await page.locator('#vinculo-'+id+' input[type=checkbox]').check();
  }
  assert.equal(await page.locator('#zip-avancar').isEnabled(),true);
  await page.locator('[data-previa-pdf="a1"]').click();
  await page.waitForFunction(()=>document.getElementById('pdf-paginacao').textContent==='Página 1 de 2');
  assert.equal(await page.locator('#pdf-canvas').evaluate(c=>c.width>0&&c.height>0),true);
  assert.equal((await (await page.request.get(url+'/api/consulta/resumo')).json()).viagens,0);
  checks.push('Prévia renderiza PDF válido de duas páginas, distingue caminhos com mesmo nome e não importa registros');

  const trecho=await page.locator('#pdf-trecho option').evaluateAll(items=>items.find(i=>i.textContent==='CLIENTE SINTETICO ORIGINAL').value);
  await page.locator('#pdf-trecho').selectOption(trecho);
  await page.locator('#pdf-texto').fill('CLIENTE SINTETICO CORRIGIDO');await page.locator('#pdf-largura').fill('400');
  await page.locator('#pdf-adicionar').click();assert.equal(await page.locator('.pdf-mark.is-applied').count(),1);
  await page.locator('#pdf-proxima').click();await page.waitForFunction(()=>document.getElementById('pdf-paginacao').textContent==='Página 2 de 2');
  const date=await page.locator('#pdf-trecho option').evaluateAll(items=>items.find(i=>i.textContent==='Data: 01/01/2026').value);
  await page.locator('#pdf-trecho').selectOption(date);await page.locator('#pdf-texto').fill('Data: 02/02/2026');await page.locator('#pdf-largura').fill('250');
  await page.locator('#pdf-adicionar').click();assert.equal(await page.locator('#pdf-lista-correcoes li').count(),2);
  await page.screenshot({path:path.join(output,'editor-desktop.png')});
  await page.locator('#pdf-salvar').click();await page.locator('#editor-pdf').waitFor({state:'hidden'});
  assert.equal(await page.locator('#confirmar-zip').isDisabled(),true);assert.equal(await page.locator('[data-revisao-pdf="a1"]').textContent(),'Revisão pendente de conferência');
  checks.push('Correções de cliente e data em páginas diferentes geram cópia e bloqueiam importação do original');
  const downloadPromise=page.waitForEvent('download');await page.locator('[data-baixar-revisao-pdf="a1"]').click();
  const download=await downloadPromise;assert.equal(download.suggestedFilename(),'documento-revisado.pdf');
  await download.saveAs(path.join(output,'documento-revisado.pdf'));
  checks.push('Cópia revisada pode ser baixada separadamente sem modificar o original');

  const revisedResponse=page.waitForResponse(r=>r.url().includes('/api/pacotes/inspecao')&&r.request().method()==='POST');
  await page.locator('#gerar-zip-revisado').click();const revised=await (await revisedResponse).json();
  assert.notEqual(first.sha256,revised.sha256);
  for(const name of ['cte.xml','viagem/documento.pdf'])assert.equal(first.arquivos.find(f=>f.caminhoInterno===name).sha256,revised.arquivos.find(f=>f.caminhoInterno===name).sha256);
  assert.notEqual(first.arquivos.find(f=>f.caminhoInterno==='faturas/documento.pdf').sha256,revised.arquivos.find(f=>f.caminhoInterno==='faturas/documento.pdf').sha256);
  assert.equal(await page.locator('#pdfs-zip input:checked').count(),0);assert.equal(await page.locator('#confirmar-zip').isDisabled(),true);
  const contents=await page.evaluate(async()=>{
    const {unzipSync}=await import('/webjars/fflate/0.8.3/esm/browser.js');
    const entries=unzipSync(new Uint8Array(await document.getElementById('arquivo-zip').files[0].arrayBuffer()));
    const pdfjs=await import('/webjars/pdfjs-dist/6.4.299/build/pdf.mjs');
    const task=pdfjs.getDocument({data:entries['faturas/documento.pdf'],isEvalSupported:false});
    const doc=await task.promise;
    const texts=[];for(let n=1;n<=doc.numPages;n++)texts.push((await (await doc.getPage(n)).getTextContent()).items.map(i=>i.str||'').join(' '));
    await task.destroy();return texts;
  });
  assert.match(contents[0],/CLIENTE SINTETICO CORRIGIDO/);assert.match(contents[1],/02\/02\/2026/);
  checks.push('Novo ZIP possui novo hash, mantém XML/PDF não editado idênticos e contém as correções sem reaproveitar vínculos automaticamente');

  for(const id of ['a1','a2']){await page.locator('#vinculo-'+id+' select').selectOption(id==='a1'?'FATURA':'VIAGEM');await page.locator('#vinculo-'+id+' input[type=checkbox]').check();}
  await page.locator('#zip-avancar').click();await page.locator('#confirmar-zip').click();await page.waitForFunction(()=>document.getElementById('zip-resumo-final').textContent.includes('importados'));
  assert.equal((await (await page.request.get(url+'/api/consulta/resumo')).json()).viagens,1);
  assert.equal((await (await page.request.get(url+'/api/viagens/1/anexos')).json()).length,2);
  checks.push('Pacote revisado exige nova associação e só é importado por confirmação explícita');
  await page.locator('#zip-voltar-conferencia').click();

  if(await page.locator('html').getAttribute('data-tema')!=='escuro')await page.locator('#alternar-tema').click();
  await page.setViewportSize({width:390,height:844});
  await page.locator('[data-previa-pdf="a1"]').click();await page.waitForFunction(()=>document.getElementById('pdf-paginacao').textContent==='Página 1 de 2');
  assert.ok(await page.locator('#editor-pdf').evaluate(d=>d.scrollWidth<=d.clientWidth+1));
  await page.screenshot({path:path.join(output,'editor-mobile-escuro.png')});
  const fitWidth=await page.locator('#pdf-canvas').evaluate(c=>c.width);
  await page.locator('#pdf-zoom').selectOption('1.5');
  await page.waitForFunction(width=>document.getElementById('pdf-canvas').width>width&&!document.getElementById('pdf-zoom').disabled,fitWidth);
  assert.ok(await page.locator('#editor-pdf').evaluate(d=>d.scrollWidth<=d.clientWidth+1));
  await page.screenshot({path:path.join(output,'editor-mobile-zoom.png')});await page.locator('#pdf-fechar').click();
  checks.push('Editor responsivo e tema escuro no celular sem overflow do dialog');
  checks.push('Zoom permite ler detalhes no celular com rolagem restrita ao documento');

  await page.locator('[data-previa-pdf="a1"]').click();await page.waitForFunction(()=>document.getElementById('pdf-paginacao').textContent==='Página 1 de 2');
  const correctedName=await page.locator('#pdf-trecho option').evaluateAll(items=>items.find(i=>i.textContent==='CLIENTE SINTETICO CORRIGIDO').value);
  await page.locator('#pdf-trecho').selectOption(correctedName);await page.locator('#pdf-texto').fill('RASCUNHO QUE NAO SERA APLICADO');await page.locator('#pdf-largura').fill('450');await page.locator('#pdf-adicionar').click();
  await page.locator('#pdf-fechar').click();await page.locator('[data-previa-pdf="a1"]').click();
  await page.waitForFunction(()=>document.getElementById('pdf-paginacao').textContent==='Página 1 de 2');
  assert.equal(await page.locator('#pdf-lista-correcoes').textContent(),'Nenhuma correção adicionada.');await page.locator('#pdf-fechar').click();
  checks.push('Fechar sem aplicar descarta o rascunho sem alterar a cópia do pacote');

  let release;const gate=new Promise(resolve=>release=resolve);
  await page.route('**/api/pacotes/previa-pdf?**',async route=>{await gate;try{await route.continue();}catch{}},{times:1});
  await page.locator('[data-previa-pdf="a1"]').click();await page.locator('#editor-pdf').waitFor({state:'visible'});
  await page.locator('#arquivo-zip').setInputFiles({name:'outro-pacote.zip',mimeType:'application/zip',buffer:original});
  await page.locator('#editor-pdf').waitFor({state:'hidden'});release();
  assert.equal(await page.locator('#pdfs-zip fieldset').count(),0);assert.equal(await page.locator('#revisoes-pdf').isHidden(),true);
  checks.push('Trocar o ZIP cancela a prévia pendente e limpa vínculos e revisões da sessão anterior');

  const invalid=zip([['cte.xml',xml],['invalido.pdf',Buffer.from('%PDF-1.4\ninvalido')]]);
  await page.locator('#arquivo-zip').setInputFiles({name:'pdf-invalido.zip',mimeType:'application/zip',buffer:invalid});
  await page.locator('#inspecionar-zip').click();await page.locator('[data-previa-pdf]').waitFor();
  await page.locator('[data-previa-pdf]').click();await page.waitForFunction(()=>document.getElementById('pdf-status').classList.contains('error'));
  assert.equal(await page.locator('#pdf-salvar').isDisabled(),true);await page.locator('#pdf-fechar').click();
  checks.push('Cabeçalho reconhecido com estrutura inválida mostra erro sem aplicar revisão');
  assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
  checks.push('Nenhum erro JavaScript e nenhuma requisição a CDN ou serviço externo');
  fs.writeFileSync(path.join(output,'resultado.json'),JSON.stringify({checks,errors,external},null,2));
  console.log(JSON.stringify({checks,errors,external},null,2));
}
run().catch(error=>{console.error(error);fs.writeFileSync(path.join(output,'servidor.log'),logs);process.exitCode=1;}).finally(async()=>{
  await browser?.close();server.kill();await Promise.race([new Promise(resolve=>server.once('exit',resolve)),wait(10000)]);
  fs.rmSync(base,{recursive:true,force:true,maxRetries:5,retryDelay:500});
});
