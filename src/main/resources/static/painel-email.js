import { $, state, element, avisar, api, podeOperar } from './painel-base.js';
import { apresentarErro,renderizarDiagnostico } from './painel-diagnostico.js';
let previa=0, conferenciaEmail=null, anteriorEmail=null, tentativaOcupada=false, tentativasEmail=[];
let atualizarSelecao=()=>{};
export function configurarEmail(config){atualizarSelecao=config.atualizarSelecao;}
export function invalidarPrevia(){conferenciaEmail=null;previa++;$('resultado-previa').hidden=true;$('resultado-previa').replaceChildren();}
export function limparEstadoEmail(){anteriorEmail=null;tentativasEmail=[];$('referencia-reenvio').textContent='';$('resultado-tentativa').textContent='';$('consulta-tentativa').replaceChildren();}
function listaSnapshot(snapshot){const list=element('ul');for(const a of snapshot.arquivos)list.append(element('li',`${a.nome} · ${a.tipo} · ${a.tamanho} bytes · ${a.origem} #${a.id}${a.finalidade==='FATURA'&&a.viagemIds.length>1?` · FATURA COMPARTILHADA (${a.viagemIds.length} viagens)`:''}`));return list;}
export function mostrarConsultaTentativa(tentativa,diagnostico){
  const card=renderizarDiagnostico($('consulta-tentativa'),diagnostico),snapshot=tentativa.snapshot;
  card.append(element('p',`Tentativa consultada: ${tentativa.id} · ${tentativa.modo} · ${tentativa.situacao}`),element('p',`CT-e: ${snapshot?.numero||'não informado no registro'}`),element('p',`Destinatário: ${snapshot?.destinatario||'não informado no registro'}`));
  if(Array.isArray(snapshot?.arquivos))card.append(listaSnapshot(snapshot));else card.append(element('p','Metadados de anexos indisponíveis neste registro.'));
  card.append(element('p','Consulta somente de leitura. Nenhuma confirmação ou reenvio foi executado.'));
}
$('form-email').addEventListener('submit',async event=>{
  event.preventDefault();if(!state.envioReal||!podeOperar()||state.previaOcupada||state.carregando||!state.viagem||!(state.selecionados.size+state.anexosSelecionados.size))return;
  const version=++previa,trip=state.viagem,ids=[...state.selecionados];
  if(tentativasEmail.length&&!anteriorEmail){avisar('Esta viagem já tem tentativa. Consulte o histórico e escolha Preparar reenvio confirmado.',true);return;}
  state.previaOcupada=true;atualizarSelecao();$('gerar-previa').textContent='Gerando prévia…';$('resultado-previa').hidden=true;
  try{
    const result=await api(`/api/viagens/${encodeURIComponent(trip)}/conferencias-email`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({selecao:{destinatario:$('destinatario').value.trim(),documentoIds:ids,anexoIds:[...state.anexosSelecionados]},anteriorId:anteriorEmail}),timeoutMs:30000});
    if(version!==previa||trip!==state.viagem)return;conferenciaEmail=result;
    const real=result.modo==='REAL',target=$('resultado-previa'),label=element('label'),check=element('input');check.type='checkbox';check.id='conferi-envio';
    label.append(check,element('span',`${result.anteriorId?'Conferi e confirmo deliberadamente este REENVIO':'Conferi viagem, destinatário e anexos'} · ${real?'ENVIO REAL':'SIMULAÇÃO'}.`));
    const button=element('button',real?(result.anteriorId?'Confirmar reenvio':'Confirmar envio'):'Envio não configurado','button primary');button.type='button';button.id='simular-envio';button.disabled=true;
    check.addEventListener('change',()=>{button.disabled=!real||!check.checked||tentativaOcupada;});
    button.addEventListener('click',()=>{if(check.checked&&conferenciaEmail?.id===result.id)executarConfirmacao(trip,result.id);});
    target.replaceChildren(element('strong',`${real?'ENVIO REAL':'SIMULAÇÃO'} · arquivos conferidos no servidor`),element('p','Nenhum e-mail enviado nesta prévia.'),element('p',`CT-e ${result.snapshot.numero} · Viagem #${result.snapshot.viagemId}`),element('p',`Destinatário: ${result.snapshot.destinatario}`),listaSnapshot(result.snapshot),element('p','Fatura compartilhada será incluída uma vez nesta tentativa. Não haverá tentativas automáticas para outras viagens ou destinatários.'),label,button);target.hidden=false;
  }catch(error){if(version===previa)avisar(error.message,true);}finally{state.previaOcupada=false;$('gerar-previa').textContent='Gerar prévia →';atualizarSelecao();}
});
export function renderizarTentativas(items){
  tentativasEmail=items;const target=$('tentativas');target.replaceChildren();if(!items.length){target.textContent='Nenhuma tentativa manual registrada.';return;}
  for(const item of items){
    const section=element('section',null,`diag-card diag-${item.situacao==='FALHA'?'erro':item.situacao==='DESCONHECIDO'?'atencao':item.situacao==='ACEITO_SMTP'?'sucesso':'atencao'}`);
    section.append(element('strong'),element('p',`${new Date(item.criadaEm).toLocaleString('pt-BR')} · CT-e ${item.snapshot.numero} · ${item.snapshot.destinatario}`));
    if(item.situacao==='FALHA'||item.situacao==='DESCONHECIDO')section.append(element('p',item.motivo),element('p',item.situacao==='DESCONHECIDO'?'Confira o resultado antes de preparar um reenvio. Não há repetição automática.':'Revise a causa e confira novamente antes de preparar um reenvio.'));
    const detalhes=element('details');detalhes.append(element('summary','Ver detalhes'),element('p',`Tentativa ${item.id} · ${item.modo} · ${item.situacao}`),listaSnapshot(item.snapshot));
    if(item.situacao!=='FALHA'&&item.situacao!=='DESCONHECIDO')detalhes.append(element('p',item.motivo));
    if(item.anteriorId)detalhes.append(element('p',`Reenvio da tentativa ${item.anteriorId}`));
    section.append(detalhes);
    const titulo=section.querySelector('strong');titulo.textContent=item.situacao==='FALHA'?`⛔ Falha${item.modo==='SIMULADO'?' no teste sem envio':''}`:item.situacao==='DESCONHECIDO'?`⚠ Resultado desconhecido${item.modo==='SIMULADO'?' do teste sem envio':''}`:item.modo==='SIMULADO'?'Teste sem envio':item.situacao==='ACEITO_SMTP'?'Aceito pelo provedor — entrega não comprovada':'⚠ Em processamento';
    if(item.modo==='SIMULADO'&&!['FALHA','DESCONHECIDO'].includes(item.situacao))section.className='diag-card';
    if(state.envioReal&&podeOperar()&&item.id===items.at(-1).id){const button=element('button','Preparar reenvio confirmado','button secondary');button.type='button';button.addEventListener('click',()=>{if(tentativaOcupada)return;anteriorEmail=item.id;invalidarPrevia();$('referencia-reenvio').textContent=`Reenvio deliberado. ${item.situacao==='DESCONHECIDO'?'Resultado anterior desconhecido: revise antes de prosseguir. ':''}Escolha destinatário e anexos e gere nova prévia.`;$('destinatario').focus();});section.append(button);}target.append(section);
  }
}
function pendencias(){try{return JSON.parse(sessionStorage.getItem('rotabrasil-confirmacoes')||'{}');}catch{return {};}}
function salvarPendencia(viagem,id){try{const mapa=pendencias();if(id)mapa[String(viagem)]=id;else delete mapa[String(viagem)];sessionStorage.setItem('rotabrasil-confirmacoes',JSON.stringify(mapa));}catch{}}
export function atualizarRecuperacao(viagem){$('recuperar-confirmacao').hidden=!pendencias()[String(viagem)]||!podeOperar();}
async function executarConfirmacao(viagem,id){
  if(tentativaOcupada)return;tentativaOcupada=true;salvarPendencia(viagem,id);
  if($('simular-envio'))$('simular-envio').disabled=true;$('recuperar-confirmacao').disabled=true;$('resultado-tentativa').textContent='Processando tentativa… Consulte o histórico em caso de interrupção.';
  try{
    const item=await api(`/api/viagens/${encodeURIComponent(viagem)}/tentativas-email`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({conferenciaId:id,confirmado:true}),timeoutMs:30000});
    if(String(state.viagem)===String(viagem)){$('resultado-tentativa').textContent=`${item.modo==='SIMULADO'?'SIMULAÇÃO':'REAL'} · ${item.situacao} · ${item.motivo} · Tentativa ${item.id}`;invalidarPrevia();anteriorEmail=null;$('referencia-reenvio').textContent='';const items=await api(`/api/viagens/${encodeURIComponent(viagem)}/tentativas-email`);if(String(state.viagem)===String(viagem))renderizarTentativas(items);}
    salvarPendencia(viagem,null);
    const d=await api(`/api/diagnosticos/ENVIO/${item.id}`);if(String(state.viagem)===String(viagem))renderizarDiagnostico($('consulta-tentativa'),d);
  }catch(error){if(String(state.viagem)===String(viagem)){$('resultado-tentativa').textContent=`${error.message} Consulte o histórico ou repita a MESMA confirmação para recuperar o resultado; não prepare um reenvio por causa de timeout.`;apresentarErro($('consulta-tentativa'),error);}}
  finally{tentativaOcupada=false;$('recuperar-confirmacao').disabled=false;atualizarRecuperacao(state.viagem);}
}
$('recuperar-confirmacao').addEventListener('click',()=>{const id=pendencias()[state.viagem];if(id)executarConfirmacao(state.viagem,id);});
