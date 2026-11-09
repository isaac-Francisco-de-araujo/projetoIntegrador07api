import { $,element,avisar,api,podeOperar,tamanhoArquivo } from './painel-base.js';
import { criarEditorPdf,gerarZipRevisado,baixar } from './painel-pdf.js';
import { confirmarAcao } from './painel-confirmacao.js';
import { navegarPara } from './painel-navegacao.js';
import { botaoExportar,mostrarExportacoes,mostrarRecebimento,limparResultadoExportacao } from './painel-exportacoes.js';
import { renderizarDiagnostico,apresentarErro } from './painel-diagnostico.js';
let pacoteConferido=null,pacoteOcupado=false,diagnosticoPacote=null,controle=null,geracao=0,anteriorExplicito=null;
const revisoesPdf=new Map();
let editorPdf;
let recebimentoId=null;
let atualizar=async()=>{};
let passo=1;
function mostrarPasso(numero,{foco=false}={}){
  passo=numero;
  $('zip-selecionar').hidden=numero!==1;
  $('conferencia-zip').hidden=numero===1;
  $('zip-conferir').hidden=numero!==2;
  $('zip-importar').hidden=numero!==3;
  $('orientacao-confirmar-zip').hidden=numero!==3||Boolean(diagnosticoPacote?.importacao);
  $('resumo-zip').hidden=numero===3;
  $('passos-zip').querySelectorAll('li').forEach((li,i)=>{if(i+1===numero)li.setAttribute('aria-current','step');else li.removeAttribute('aria-current');});
  if(foco){const alvo=numero===1?$('arquivo-zip'):numero===2?$('resumo-zip'):$('zip-resumo-final');if(numero!==1)alvo.tabIndex=-1;alvo.focus();}
}
export function configurarPacotes(config){
  atualizar=config.atualizar;
  editorPdf=criarEditorPdf({aoSalvar:async(pdf,revisao)=>{
    const total=[...revisoesPdf].reduce((s,[id,r])=>s+(id===pdf.id?0:r.bytes.length),0)+(revisao?.bytes.length||0);
    if(total>100*1024*1024)throw new Error('As cópias revisadas ultrapassam 100 MiB nesta sessão.');
    if(revisao)revisoesPdf.set(pdf.id,revisao);else revisoesPdf.delete(pdf.id);
    habilitar();
    avisar(revisao?'Cópia revisada preparada nesta aba. Gere o ZIP revisado e confira novamente antes de importar.':'Revisão removida; o PDF original será usado.');
  }});
  habilitar();
}
function habilitar(){
  $('form-pacote').setAttribute('aria-busy',String(pacoteOcupado));
  const file=$('arquivo-zip').files[0];
  $('arquivo-zip-info').textContent=(pacoteOcupado?'Processando pacote… ':'')+(file?`${file.name} · ${tamanhoArquivo(file.size)}`:'Nenhum ZIP selecionado.');
  $('arquivo-zip').disabled=pacoteOcupado||!podeOperar();$('inspecionar-zip').disabled=pacoteOcupado||!podeOperar();$('reconferir-zip').disabled=pacoteOcupado||!podeOperar();
  if(revisoesPdf.size){$('inspecionar-zip').disabled=true;$('reconferir-zip').disabled=true;}
  $('confirmar-zip').disabled=pacoteOcupado||!podeOperar()||vinculosPacote()===null||revisoesPdf.size>0;
  const pdfs=pacoteConferido?.arquivos.filter(a=>a.tipo==='PDF')||[];
  const pendentes=pdfs.filter(pdf=>!escolhaPdf(pdf.id));
  if(pacoteConferido?.conteudoValido&&!diagnosticoPacote?.importacao)$('resumo-zip').textContent=`${pacoteConferido.arquivos.filter(a=>a.tipo==='XML').length} viagens · ${pdfs.length} PDFs${pendentes.length?` · ⚠ ${pendentes.length} associações pendentes`:''}`;
  const concluido=Boolean(diagnosticoPacote?.importacao);
  $('orientacao-confirmar-zip').hidden=passo!==3||concluido;
  const orientacao=pacoteOcupado?'Aguarde o resultado da operação.':concluido?'Importação concluída.':!podeOperar()?'Seu acesso não permite importar. Verifique a sessão e sua permissão.':!pacoteConferido?.file?'Selecione e confira um ZIP para continuar.':!pacoteConferido.conteudoValido?'⛔ Corrija os arquivos com erro e confira o ZIP novamente.':revisoesPdf.size?'Gere o ZIP revisado e confira novamente.':pendentes.length?`Associe os ${pendentes.length} PDFs pendentes para continuar.`:'Associações conferidas. Continue para importar.';
  $('orientacao-confirmar-zip').textContent=orientacao;
  $('orientacao-avancar-zip').textContent=orientacao;
  $('zip-avancar').disabled=$('confirmar-zip').disabled||concluido;
  $('confirmar-zip').hidden=concluido;
  $('zip-ver-documentos').hidden=!concluido;
  $('zip-novo').hidden=!concluido;
  $('zip-voltar-conferencia').hidden=false;
  $('zip-voltar-conferencia').textContent=concluido?'Rever conferência':'Voltar à conferência';
  $('zip-retomar').hidden=!pacoteConferido?.file;
  $('zip-retomar').disabled=pacoteOcupado;
  $('zip-voltar-conferencia').disabled=pacoteOcupado;
  $('zip-voltar-selecao').disabled=pacoteOcupado;
  $('zip-resumo-final').textContent=concluido?`${diagnosticoPacote.importacao.documentos.length} XMLs e ${diagnosticoPacote.importacao.anexos.length} PDFs ${diagnosticoPacote.importacao.reutilizado?'já registrados, sem duplicação':'importados'}.`:`${file?.name||'Pacote'} · ${pacoteConferido?.arquivos.filter(a=>a.tipo==='XML').length||0} viagens · ${pdfs.length} PDFs. A confirmação grava todos os arquivos juntos.`;
  $('revisar-vinculos-zip').hidden=pacoteOcupado||concluido||!pacoteConferido?.conteudoValido||!pendentes.length;
  document.querySelectorAll('#pdfs-zip fieldset').forEach(n=>{n.disabled=pacoteOcupado||!pacoteConferido?.file||!podeOperar();});
  $('revisoes-pdf').hidden=!revisoesPdf.size;
  $('revisoes-pdf-resumo').textContent=`${revisoesPdf.size} PDF(s) com cópia revisada pronta para nova conferência`;
  $('gerar-zip-revisado').disabled=pacoteOcupado||!podeOperar()||!pacoteConferido?.conteudoValido||!revisoesPdf.size;
  $('descartar-revisoes-pdf').disabled=pacoteOcupado;
  $('remover-zip-selecao').disabled=pacoteOcupado||!$('arquivo-zip').files.length;
  $('destinatario-confirmacao').disabled=pacoteOcupado||!podeOperar()||Boolean(recebimentoId);$('simular-confirmacao').disabled=pacoteOcupado||!podeOperar()||Boolean(recebimentoId);
  document.querySelectorAll('[data-revisao-pdf]').forEach(node=>{const revised=revisoesPdf.has(node.dataset.revisaoPdf);node.textContent=revised?'Revisão pendente de conferência':escolhaPdf(node.dataset.revisaoPdf)?'✓ Associado':'⚠ Associação pendente';node.classList.toggle('is-revised',revised);});
  document.querySelectorAll('[data-baixar-revisao-pdf]').forEach(node=>{node.hidden=!revisoesPdf.has(node.dataset.baixarRevisaoPdf);});
}
function limparPacote(){
  mostrarPasso(1);$('zip-erro').replaceChildren();$('zip-problemas').replaceChildren();$('zip-problemas').hidden=true;$('zip-detalhes').open=false;
  recebimentoId=null;$('destinatario-confirmacao').value='';$('simular-confirmacao').checked=false;
  mostrarRecebimento(null,$('avisos-recebimento'));limparResultadoExportacao($('resultado-exportacao'));$('resultado-operacional-zip').replaceChildren();$('diagnostico-global').replaceChildren();
  editorPdf?.cancelar();revisoesPdf.clear();
  ++geracao;controle?.abort();pacoteOcupado=false;pacoteConferido=null;diagnosticoPacote=null;$('conferencia-zip').hidden=true;$('aviso').hidden=true;
  for(const id of ['resumo-zip','localizacao-zip','estado-conferencia-zip'])$(id).textContent='';
  for(const id of ['diagnosticos-zip','xmls-zip','pdfs-zip'])$(id).replaceChildren();$('filtro-diagnostico').value='todos';habilitar();
  const file=$('arquivo-zip').files[0];$('arquivo-zip-info').textContent=file?`${file.name} · ${tamanhoArquivo(file.size)}`:'Nenhum ZIP selecionado.';
}
export function receberPacoteIndividual(file){
  if(!podeOperar())throw new Error('Seu perfil não permite preparar importações.');
  if(pacoteOcupado||revisoesPdf.size)throw new Error('Finalize a operação ou as revisões pendentes do ZIP antes de preparar outro PDF.');
  const transfer=new DataTransfer();transfer.items.add(file);$('arquivo-zip').files=transfer.files;
  $('arquivo-zip').dispatchEvent(new Event('change',{bubbles:true}));anteriorExplicito=null;$('correcao-processamento').textContent='';
  navegarPara('pacotes');
  $('form-pacote').requestSubmit();
}
function escolhaPdf(id){const row=$('vinculo-'+id);if(!row)return null;const finalidade=row.querySelector('select').value,xmlIds=[...row.querySelectorAll('input:checked')].map(b=>b.value);return !finalidade||!xmlIds.length||(finalidade==='VIAGEM'&&xmlIds.length!==1)?null:{arquivoId:id,finalidade,xmlIds};}
function vinculosPacote(){if(!pacoteConferido?.conteudoValido||!pacoteConferido.file)return null;const lista=[];for(const p of pacoteConferido.arquivos.filter(a=>a.tipo==='PDF')){const v=escolhaPdf(p.id);if(!v)return null;lista.push(v);}return lista;}
function rascunhos(){try{return JSON.parse(sessionStorage.getItem('rotabrasil-vinculos')||'{}');}catch{return {};}}
function salvarRascunho(){
  if(!pacoteConferido?.sha256||!pacoteConferido.file)return;
  try{const m=rascunhos();m[pacoteConferido.sha256]=pacoteConferido.arquivos.filter(a=>a.tipo==='PDF').map(p=>escolhaPdf(p.id)).filter(Boolean);const chaves=Object.keys(m);while(chaves.length>10)delete m[chaves.shift()];sessionStorage.setItem('rotabrasil-vinculos',JSON.stringify(m));}catch{}
}
function renderizarDiagnosticoPacote(){
  const d=diagnosticoPacote;if(!d)return;const concluido=Boolean(d.importacao);
  const items=d.diagnosticos.map(item=>conclidoItem(item,concluido,d));
  const pendentes=items.filter(i=>i.arquivoId&&i.gravidade==='PENDENCIA').length,validos=items.filter(i=>i.arquivoId&&i.gravidade==='INFO').length,invalidos=items.filter(i=>i.arquivoId&&i.gravidade==='ERRO').length;
  const bloqueios=items.some(i=>i.gravidade==='ERRO')||d.interrompida||!d.conteudoValido,xmls=d.arquivos.filter(a=>a.tipo==='XML'),pdfs=d.arquivos.filter(a=>a.tipo==='PDF');
  $('resumo-zip').textContent=`${xmls.length} viagens · ${pdfs.length} PDFs${invalidos?` · ⛔ ${invalidos} arquivos com erro`:''}${d.interrompida?' · Conferência interrompida':''}${concluido?' · Importação concluída':''}`;
  $('localizacao-zip').textContent=concluido?`Localização lógica: pacote #${d.importacao.pacoteId}; viagens ${d.importacao.documentos.map(a=>'#'+a.viagemId).join(', ')}; ${d.importacao.documentos.length} XML(s) e ${d.importacao.anexos.length} PDF(s). Nenhum e-mail enviado.`:d.localizacao;
  const problemas=$('zip-problemas');problemas.replaceChildren();
  for(const i of items.filter(i=>i.gravidade==='ERRO')){
    const row=element('li');row.append(element('strong',`⛔ ${i.arquivo||'Pacote'}`),element('p',i.mensagem),element('p',i.acaoSugerida));problemas.append(row);
  }
  problemas.hidden=!problemas.children.length;
  const filtro=$('filtro-diagnostico').value,list=$('diagnosticos-zip');list.replaceChildren();
  for(const i of items.filter(i=>filtro==='todos'||(filtro==='erros'?i.gravidade==='ERRO':i.gravidade==='PENDENCIA'))){
    const row=element('li',null,'file-diagnostic');row.dataset.gravidade=i.gravidade;
    const detalhes=element('details');detalhes.append(element('summary',`${i.gravidade==='ERRO'?'⛔ Erro':i.gravidade==='PENDENCIA'?'⚠ Pendente':'✓ Conferido'} · ${i.arquivo||'Pacote'}`),element('p',i.mensagem),element('p',i.acaoSugerida),element('small',`${i.etapa} · ${i.codigo} · ${i.situacao}`));row.append(detalhes);list.append(row);
  }
  if(!list.children.length)list.append(element('li','Nenhum item neste filtro.'));habilitar();
}
function conclidoItem(item,concluido,d){
  if(concluido&&item.arquivoId)return {...item,situacao:d.importacao.reutilizado?'REUTILIZADO':'IMPORTADO',etapa:'CONFIRMACAO_CONCLUIDA',gravidade:'INFO',codigo:'IMPORTACAO_CONFIRMADA',mensagem:'Arquivo incluído na confirmação integral do pacote.',acaoSugerida:'Consulte a viagem e os documentos. Nenhum e-mail foi disparado.'};
  if(item.codigo==='ASSOCIACAO_PENDENTE'&&pacoteConferido?.file&&escolhaPdf(item.arquivoId))return {...item,situacao:'ASSOCIADO',gravidade:'INFO',codigo:'ASSOCIACAO_ESCOLHIDA',acaoSugerida:'Vínculo escolhido pelo operador; será revalidado no servidor na confirmação integral.'};return item;
}
function mostrarConferenciaZip(result,file){
  editorPdf?.cancelar();revisoesPdf.clear();
  diagnosticoPacote=result;pacoteConferido={...result,file};const xmls=result.arquivos.filter(a=>a.tipo==='XML'),pdfs=result.arquivos.filter(a=>a.tipo==='PDF');
  $('xmls-zip').replaceChildren(...xmls.map(x=>element('li',`✓ XML · CT-e ${x.numero} · ${x.cliente} · ${x.caminhoInterno}`)));$('pdfs-zip').replaceChildren();
  for(const pdf of pdfs){
    const row=element('fieldset');row.id='vinculo-'+pdf.id;row.append(element('legend',pdf.caminhoInterno));
    const header=element('div',null,'pdf-file-actions');
    const badge=element('span','Original do ZIP','pdf-source-badge');badge.dataset.revisaoPdf=pdf.id;
    const preview=element('button','Visualizar e corrigir PDF ↗','button secondary pdf-open-button');preview.type='button';preview.dataset.previaPdf=pdf.id;
    preview.setAttribute('aria-label',`Visualizar e corrigir ${pdf.caminhoInterno}`);
    preview.addEventListener('click',()=>{
      if(pacoteOcupado||!pacoteConferido?.file||!podeOperar())return;
      const conferido=pacoteConferido,version=geracao;
      editorPdf.abrir({file:conferido.file,pacote:conferido,pdf,revisao:revisoesPdf.get(pdf.id),valido:()=>geracao===version&&pacoteConferido===conferido});
    });
    const download=element('button','Baixar cópia revisada','button secondary');download.type='button';download.dataset.baixarRevisaoPdf=pdf.id;download.hidden=true;
    download.addEventListener('click',()=>{const revisao=revisoesPdf.get(pdf.id);if(revisao)baixar(revisao.bytes,pdf.nomeArquivo.replace(/\.pdf$/i,'')+'-revisado.pdf','application/pdf');});
    header.append(badge,preview,download);row.append(header);
    const label=element('label','Finalidade '),select=element('select');select.setAttribute('aria-label',`Finalidade de ${pdf.caminhoInterno}`);
    select.add(new Option('Escolha…',''));select.add(new Option('Documento de uma viagem','VIAGEM'));select.add(new Option('Fatura de uma ou mais viagens','FATURA'));label.append(select);row.append(label);
    for(const xml of xmls){const l=element('label'),b=element('input');b.type='checkbox';b.value=xml.id;l.append(b,element('span',`CT-e ${xml.numero} · ${xml.cliente}`));row.append(l);}
    row.addEventListener('change',()=>{salvarRascunho();renderizarDiagnosticoPacote();});$('pdfs-zip').append(row);
  }
  if(file&&result.sha256){for(const v of rascunhos()[result.sha256]||[]){const row=$('vinculo-'+v.arquivoId);if(!row||!['VIAGEM','FATURA'].includes(v.finalidade)||!Array.isArray(v.xmlIds)||!v.xmlIds.every(id=>xmls.some(x=>x.id===id)))continue;row.querySelector('select').value=v.finalidade;row.querySelectorAll('input').forEach(b=>{b.checked=v.xmlIds.includes(b.value);});}}
  $('estado-conferencia-zip').textContent=file?'A confirmação registra os documentos no acervo.':'Consulta histórica; selecione explicitamente um ZIP para iniciar uma correção.';
  $('conferencia-zip').hidden=false;renderizarDiagnosticoPacote();
  mostrarPasso(result.importacao?3:2);
  $('zip-detalhes').open=false;
}
export function consultarProcessamento(item){limparPacote();mostrarConferenciaZip({...item.DIAGNOSTICO,importacao:item.RESULTADO||null},null);navegarPara('pacotes');}
export function corrigirProcessamento(id){$('arquivo-zip').value='';limparPacote();anteriorExplicito=id;$('correcao-processamento').textContent=`Nova conferência vinculada ao processamento ${id}. Selecione o ZIP corrigido; os originais serão preservados.`;navegarPara('pacotes',{foco:'arquivo-zip'});}
export function revisarAssociacoes(id){
  if(pacoteConferido?.file&&diagnosticoPacote?.operacaoId===id){navegarPara('pacotes');mostrarPasso(2);$('pdfs-zip').querySelector('select')?.focus();return;}
  corrigirProcessamento(id);$('correcao-processamento').textContent=`Revisão vinculada à operação ${id}. Selecione novamente o ZIP para conferir e revisar associações; o servidor não retém o ZIP original.`;
}
async function consultarResumo(id,version){
  try{const d=await api(`/api/diagnosticos/PROCESSAMENTO/${id}`);if(version===geracao&&diagnosticoPacote?.operacaoId===id)renderizarDiagnostico($('resultado-operacional-zip'),d);}
  catch(error){if(version===geracao)apresentarErro($('resultado-operacional-zip'),error);}
}
function rotaComAnterior(rota,id){return id&&/^[a-f0-9-]{36}$/.test(id)?`${rota}?anteriorOperacaoId=${encodeURIComponent(id)}`:rota;}
function notificar(){document.dispatchEvent(new Event('processamento-atualizado'));}
function falhaLocal(file,codigo,mensagem){mostrarConferenciaZip({operacaoId:'cliente-'+Date.now(),pacote:file?.name||'(nenhum pacote)',sha256:null,arquivos:[],conteudoValido:false,interrompida:true,naoExaminados:-1,localizacao:'Upload não conferido. Localização física do computador desconhecida; nenhuma publicação confirmada.',diagnosticos:[{arquivoId:null,arquivo:null,caminhoInterno:null,etapa:'SOLICITACAO_CONFERENCIA',codigo,mensagem,gravidade:'ERRO',situacao:'NAO_CONFERIDO',novaTentativa:true,acaoSugerida:'Revise o pacote, os limites e a conexão e confira novamente.'}]},file);pacoteConferido=null;habilitar();}
$('arquivo-zip').addEventListener('change',limparPacote);
$('zip-avancar').addEventListener('click',()=>{if(!vinculosPacote()||pacoteOcupado||revisoesPdf.size)return;mostrarPasso(3,{foco:true});});
$('zip-voltar-conferencia').addEventListener('click',()=>{if(!pacoteOcupado)mostrarPasso(2,{foco:true});});
$('zip-voltar-selecao').addEventListener('click',()=>{if(!pacoteOcupado)mostrarPasso(1,{foco:true});});
$('zip-retomar').addEventListener('click',()=>{if(!pacoteOcupado&&pacoteConferido?.file)mostrarPasso(2,{foco:true});});
$('zip-novo').addEventListener('click',()=>{if(pacoteOcupado)return;$('arquivo-zip').value='';limparPacote();$('arquivo-zip').focus();});
const drop=$('form-pacote').querySelector('.zip-upload');
drop.addEventListener('dragover',e=>{e.preventDefault();});
drop.addEventListener('drop',e=>{e.preventDefault();if(pacoteOcupado||!podeOperar())return;const files=e.dataTransfer.files;if(files.length!==1)return;const data=new DataTransfer();data.items.add(files[0]);$('arquivo-zip').files=data.files;$('arquivo-zip').dispatchEvent(new Event('change'));});
$('revisar-vinculos-zip').addEventListener('click',()=>{
  const pdf=pacoteConferido?.arquivos.find(a=>a.tipo==='PDF'&&!escolhaPdf(a.id));
  const row=pdf&&$('vinculo-'+pdf.id);
  if(!row)return;
  row.scrollIntoView({block:'center'});
  const select=row.querySelector('select');
  (select.value?row.querySelector('input'):select).focus({preventScroll:true});
});
$('remover-zip-selecao').addEventListener('click',()=>{
  if(pacoteOcupado||!$('arquivo-zip').files.length)return;
  const version=geracao;
  const remover=()=>{
    if(version!==geracao)throw new Error('A seleção mudou. Confira o ZIP atual antes de removê-lo.');
    if(pacoteConferido?.sha256)try{const drafts=rascunhos();delete drafts[pacoteConferido.sha256];sessionStorage.setItem('rotabrasil-vinculos',JSON.stringify(drafts));}catch{}
    $('arquivo-zip').value='';$('arquivo-zip').dispatchEvent(new Event('change',{bubbles:true}));anteriorExplicito=null;$('correcao-processamento').textContent='';
    avisar('ZIP removido da seleção. Os documentos já importados e o histórico foram preservados.');
  };
  if(revisoesPdf.size)confirmarAcao({titulo:'Remover ZIP da seleção?',descricao:'As cópias revisadas que ainda estão nesta aba serão descartadas.',regra:'Baixe as cópias revisadas antes de remover a seleção, se quiser guardá-las. Nenhum registro importado será excluído.',textoConfirmar:'Remover seleção e revisões',acao:remover});
  else remover();
});
$('descartar-revisoes-pdf').addEventListener('click',()=>{if(pacoteOcupado)return;revisoesPdf.clear();habilitar();avisar('Revisões descartadas. Os PDFs originais do ZIP foram mantidos.');});
$('gerar-zip-revisado').addEventListener('click',async()=>{
  if(pacoteOcupado||!podeOperar()||!pacoteConferido?.file||!revisoesPdf.size)return;
  const conferido=pacoteConferido,version=geracao;pacoteOcupado=true;habilitar();
  try{
    const novo=await gerarZipRevisado(conferido.file,conferido,revisoesPdf);
    if(version!==geracao)return;
    const transfer=new DataTransfer();transfer.items.add(novo);$('arquivo-zip').files=transfer.files;
    $('arquivo-zip').dispatchEvent(new Event('change',{bubbles:true}));
    anteriorExplicito=conferido.operacaoId;
    $('form-pacote').requestSubmit();
  }catch(error){if(version===geracao)avisar(error.message||'Não foi possível criar o ZIP revisado. O original permanece preservado.',true);}
  finally{if(version===geracao){pacoteOcupado=false;habilitar();}}
});
$('filtro-diagnostico').addEventListener('change',renderizarDiagnosticoPacote);
$('reconferir-zip').addEventListener('click',()=>{if(!pacoteOcupado)$('form-pacote').requestSubmit();});
$('cancelar-conferencia').addEventListener('click',()=>controle?.abort());
$('form-pacote').addEventListener('submit',async event=>{
  event.preventDefault();if(pacoteOcupado||!podeOperar()||revisoesPdf.size)return;const file=$('arquivo-zip').files[0],anterior=anteriorExplicito||diagnosticoPacote?.operacaoId;pacoteConferido=null;$('aviso').hidden=true;
  if(!file||!file.name.toLowerCase().endsWith('.zip')||!file.size||file.size>20*1024*1024){falhaLocal(file,'UPLOAD_ZIP_INVALIDO','Selecione um ZIP não vazio de até 20 MiB.');return;}
  $('zip-erro').replaceChildren();$('zip-problemas').replaceChildren();$('zip-problemas').hidden=true;const version=++geracao;pacoteOcupado=true;controle=new AbortController();$('cancelar-conferencia').hidden=false;habilitar();$('estado-conferencia-zip').textContent='Conferindo arquivos…';
  try{const form=new FormData();form.append('arquivo',file);
    const destinatario=$('destinatario-confirmacao');if(destinatario.value&&!destinatario.reportValidity())throw new Error('Informe um destinatário válido para a confirmação deste upload.');
    if(destinatario.value)form.append('destinatarioConfirmacao',destinatario.value);form.append('simularConfirmacao',String($('simular-confirmacao').checked));
    const result=await api(rotaComAnterior('/api/pacotes/inspecao',anterior),{method:'POST',body:form,signal:controle.signal,timeoutMs:30000,aoResponder:response=>{recebimentoId=response.headers.get('X-Recebimento-Id')||recebimentoId;}});if(version!==geracao)return;mostrarConferenciaZip(result,file);anteriorExplicito=null;notificar();await consultarResumo(result.operacaoId,version);}
  catch(error){if(version!==geracao)return;if(error.diagnostico){mostrarConferenciaZip(error.diagnostico,file);anteriorExplicito=null;notificar();}else{pacoteConferido=null;diagnosticoPacote=null;for(const id of ['xmls-zip','pdfs-zip','diagnosticos-zip'])$(id).replaceChildren();$('resumo-zip').textContent='Conferência não concluída. Volte à seleção e confira novamente.';mostrarPasso(2);}$('zip-erro').className=error.incerto?'notice attention':'notice error';$('zip-erro').textContent=!$('zip-problemas').hidden?'':`${error.incerto?'⚠':'⛔'} ${error.message}`;apresentarErro($('resultado-operacional-zip'),error);}
  finally{if(version===geracao){pacoteOcupado=false;controle=null;$('cancelar-conferencia').hidden=true;habilitar();const recebido=await mostrarRecebimento(recebimentoId,$('avisos-recebimento'));if(version===geracao&&recebido){$('destinatario-confirmacao').value=recebido.destinatario||'';$('simular-confirmacao').checked=recebido.simulacaoAutorizada;habilitar();}}}
});
$('confirmar-zip').addEventListener('click',async()=>{
  const vinculos=vinculosPacote();if(pacoteOcupado||vinculos===null||!podeOperar()||revisoesPdf.size)return;
  const conferido=pacoteConferido;pacoteOcupado=true;salvarRascunho();habilitar();
  try{
    const form=new FormData();form.append('arquivo',conferido.file);form.append('configuracao',new Blob([JSON.stringify({sha256:conferido.sha256,vinculos})],{type:'application/json'}));
    const result=await api(rotaComAnterior('/api/pacotes/confirmacao',conferido.operacaoId),{method:'POST',body:form,timeoutMs:30000});
    $('zip-erro').replaceChildren();
    diagnosticoPacote={...diagnosticoPacote,operacaoId:result.operacaoId||diagnosticoPacote.operacaoId,importacao:result};renderizarDiagnosticoPacote();mostrarPasso(3);$('estado-conferencia-zip').textContent='✓ Documentos registrados no acervo.';notificar();
    await consultarResumo(result.operacaoId,geracao);
    try{await atualizar(result.documentos[0]?.viagemId);}catch{avisar('Importação concluída. Use Atualizar dados para conferir.',true);}
    await mostrarExportacoes(result.pacoteId,$('resultado-exportacao'));
    $('resultado-exportacao').prepend(botaoExportar(result.pacoteId,$('resultado-exportacao')));
    await mostrarRecebimento(recebimentoId,$('avisos-recebimento'));
  }catch(error){pacoteConferido=null;if(error.diagnostico){diagnosticoPacote=error.diagnostico;renderizarDiagnosticoPacote();}$('estado-conferencia-zip').textContent='';$('zip-erro').className=error.incerto?'notice attention':'notice error';$('zip-erro').textContent=`${error.incerto?'⚠ Resultado não confirmado':'⛔ Confirmação recusada'}: ${error.message} Consulte as pendências antes de conferir novamente.`;apresentarErro($('resultado-operacional-zip'),error);notificar();}
  finally{pacoteOcupado=false;habilitar();}
});
