/* Abre somente base NOVA sintética em loopback; SMTP sempre SIMULADO. Ctrl+C encerra. */
'use strict';
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),crypto=require('node:crypto');
const {spawn}=require('node:child_process');
const {pacoteSintetico,configuracao}=require('./central-fixture.cjs');
const [jarArg,portArg='8092',flag]=process.argv.slice(2);
if(!jarArg||!/^\d+$/.test(portArg)||Number(portArg)<1024||Number(portArg)>65535)throw Error('Uso: node scripts/iniciar-central-isolada.cjs JAR [PORTA=8092] [--verificar]');
const jar=path.resolve(jarArg),hash=crypto.createHash('sha256').update(fs.readFileSync(jar)).digest('hex');
const base=fs.mkdtempSync(path.join(os.tmpdir(),'rotabrasil-central-demo-'));
const logfile=path.join(base,'servidor-sintetico.log'),log=fs.openSync(logfile,'w'),url='http://127.0.0.1:'+portArg;
const child=spawn('java',['-Xmx512m','-jar',jar,
  '--spring.config.location=classpath:/application.properties','--spring.config.additional-location=',
  '--spring.profiles.active=desenvolvimento','--server.address=127.0.0.1','--server.port='+portArg,
  '--spring.datasource.url=jdbc:h2:file:'+base.replaceAll('\\','/')+'/banco;WRITE_DELAY=0',
  '--spring.datasource.username=sa','--spring.datasource.password=',
  '--spring.jpa.hibernate.ddl-auto=validate','--spring.flyway.enabled=true','--spring.h2.console.enabled=false',
  '--rotabrasil.seguranca.ativa=false','--rotabrasil.email.modo=SIMULADO','--rotabrasil.email.smtp.ativado=false',
  '--rotabrasil.notificacoes.modo=SIMULADO','--rotabrasil.teste-email.modo=SIMULADO',
  '--rotabrasil.arquivos.viagens='+base+'/acervo','--rotabrasil.exportacao.raiz='+base+'/exportacao'],{cwd:base,stdio:['ignore',log,log]});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
let encerrando=false;
async function encerrar(){if(encerrando)return;encerrando=true;if(child.exitCode===null){child.kill();await Promise.race([new Promise(r=>child.once('exit',r)),sleep(15000)]);}fs.closeSync(log);}
process.on('SIGINT',()=>encerrar());process.on('SIGTERM',()=>encerrar());
async function main(){
  let pronto=false;
  for(let i=0;i<900;i++){
    if(child.exitCode!==null)throw Error('A instância isolada não iniciou. Consulte o log sintético: '+logfile);
    // Somente consultar a porta depois de o PRÓPRIO processo anunciar que a abriu.
    if(fs.readFileSync(logfile,'utf8').includes('Tomcat started on port '+portArg)){pronto=true;break;}
    await sleep(100);
  }
  if(!pronto)throw Error('Tempo de inicialização excedido: '+logfile);
  const versao=await(await fetch(url+'/api/versao')).json();
  if(versao.centralEnviosContrato!=='documental-conferencia-v2')throw Error('Este não é o JAR corrigido da Central documental v2. Não use essa URL para validação.');
  for(const nome of ['painel.js','painel-base.js','painel-documentos.js','painel-email.js','painel-versao.js']){
    const bytes=Buffer.from(await(await fetch(url+'/'+nome,{cache:'no-store'})).arrayBuffer());
    if(crypto.createHash('sha256').update(bytes).digest('hex')!==versao.estaticosSha256[nome])throw Error('Recursos incompatíveis com o backend');
  }
  const buffer=pacoteSintetico();
  async function upload(rota,c){const form=new FormData();form.set('arquivo',new Blob([buffer],{type:'application/zip'}),'central-sintetica.zip');if(c)form.set('configuracao',new Blob([JSON.stringify(c)],{type:'application/json'}),'configuracao.json');const r=await fetch(url+rota,{method:'POST',body:form});if(!r.ok)throw Error('Fixture sintética recusada: HTTP '+r.status);return r.json();}
  const inspecao=await upload('/api/pacotes/inspecao');const resultado=await upload('/api/pacotes/confirmacao',configuracao(inspecao));
  const evidencia={jar,sha256:hash,url:url+'/painel#email',versao,documentosSinteticos:resultado.documentos.length+resultado.anexos.length,envioExterno:false};
  fs.writeFileSync(path.join(base,'identidade.json'),JSON.stringify(evidencia,null,2));
  console.log(JSON.stringify(evidencia,null,2));
  console.log('Escolha a viagem sintética em Viagens e documentos, selecione os três arquivos e abra Envios. Informe um destinatário para conferir. Confirmação REAL permanece bloqueada.');
  console.log('Base exclusivamente sintética: '+base+' · Ctrl+C encerra esta instância. A porta 8080 não foi alterada.');
  if(flag==='--verificar')await encerrar();else await new Promise(r=>child.once('exit',r));
}
main().catch(async e=>{console.error(e.message);process.exitCode=1;await encerrar();});
