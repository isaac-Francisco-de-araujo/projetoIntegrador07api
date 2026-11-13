import { $, state, element, avisar, api, podeOperar, podeAdministrar } from './painel-base.js';
import { apresentarErro,renderizarDiagnostico } from './painel-diagnostico.js';
let previa=0, conferenciaEmail=null, anteriorEmail=null, tentativaOcupada=false, tentativasEmail=[];
let atualizarSelecao=()=>{};
let configuracaoEnvio=null, consultaConfig=0;
export function configurarEmail(config){atualizarSelecao=config.atualizarSelecao;consultarConfiguracaoEnvio();}
function atualizarConfirmacao(){const b=$('simular-envio');if(b)b.disabled=conferenciaEmail?.modo!=='REAL'||!configuracaoEnvio?.confirmacaoRealDisponivel||!podeOperar()||!$('conferi-envio')?.checked||tentativaOcupada;}
async function consultarConfiguracaoEnvio(){
  const version=++consultaConfig;configuracaoEnvio=null;atualizarConfirmacao();
  $('modo-email').textContent='Consultando configuração documental… A preparação não envia e-mail.';
  $('bloqueios-envio').replaceChildren();$('recarregar-envio').disabled=true;
  $('configurar-envio').hidden=true;$('orientacao-envio').textContent='';
  try{
    const c=await api('/api/envio-documental');if(version!==consultaConfig)return;
    if(!['REAL','SIMULADO'].includes(c.modo)||!Array.isArray(c.bloqueios)||typeof c.confirmacaoRealDisponivel!=='boolean')throw Error('Contrato de configuração incompatível. Recompile e reinicie o JAR correspondente ao frontend.');
    configuracaoEnvio=c;
    $('modo-email').textContent=(c.provedor==='GMAIL'?'Gmail documental · ':'')+(c.confirmacaoRealDisponivel?'Transporte REAL disponível por configuração local; autenticação, entrega e leitura não comprovadas.':'Conferência disponível sem envio. Confirmação real bloqueada:');
    $('bloqueios-envio').replaceChildren(...c.bloqueios.map(m=>element('li',m)));
    $('configurar-envio').hidden=!podeAdministrar()||!c.bloqueios.length;
    $('orientacao-envio').textContent=c.bloqueios.length?(podeAdministrar()?'Credenciais, autorização e modo REAL são provisionados externamente.':'Solicite a configuração a um ADMIN. Seu perfil não pode alterá-la.'):'';
  }catch(error){if(version!==consultaConfig)return;$('modo-email').textContent=error.status===401||error.status===403?error.message:'Não foi possível consultar a configuração. Tente novamente. A preparação continua sem envio; a confirmação permanece bloqueada.';}
  finally{if(version===consultaConfig){$('recarregar-envio').disabled=false;atualizarConfirmacao();atualizarSelecao();}}
}
$('recarregar-envio').addEventListener('click',consultarConfiguracaoEnvio);
window.addEventListener('hashchange',()=>{if(location.hash==='#email')consultarConfiguracaoEnvio();});
document.addEventListener('configuracoes-atualizadas',consultarConfiguracaoEnvio);
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
  event.preventDefault();if(!podeOperar()||state.previaOcupada||state.carregando||!state.viagem||!(state.selecionados.size+state.anexosSelecionados.size)||!$('destinatario').value.trim()||!$('destinatario').validity.valid)return;
  const version=++previa,trip=state.viagem,ids=[...state.selecionados];
  if(tentativasEmail.length&&!anteriorEmail){avisar('Esta viagem já tem tentativa. Consulte o histórico e escolha Preparar reenvio confirmado.',true);return;}
  state.previaOcupada=true;atualizarSelecao();$('gerar-previa').textContent='Gerando prévia…';$('resultado-previa').hidden=true;
  try{
    const result=await api(`/api/viagens/${encodeURIComponent(trip)}/conferencias-email`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({selecao:{destinatario:$('destinatario').value.trim(),documentoIds:ids,anexoIds:[...state.anexosSelecionados]},anteriorId:anteriorEmail}),timeoutMs:30000});
    if(version!==previa||trip!==state.viagem)return;conferenciaEmail=result;
    const real=result.modo==='REAL',target=$('resultado-previa'),label=element('label'),check=element('input');check.type='checkbox';check.id='conferi-envio';
    const compartilhada=result.snapshot.arquivos.some(a=>a.finalidade==='FATURA'&&a.viagemIds.length>1);
    label.append(check,element('span',`${result.anteriorId?'Conferi e confirmo deliberadamente este REENVIO':'Conferi viagem, destinatário, mensagem e anexos'} · ${real?'ENVIO REAL':'Preparação sem envio'}.${compartilhada?' Conferi que as faturas compartilhadas podem ser enviadas integralmente a este destinatário.':''}`));
    const button=element('button',real?(result.anteriorId?'Confirmar reenvio':'Confirmar envio'):'Confirmar envio real indisponível','button primary');button.type='button';button.id='simular-envio';button.disabled=true;
    button.setAttribute('aria-describedby','modo-email bloqueios-envio motivo-confirmacao');
    check.addEventListener('change',atualizarConfirmacao);
    button.addEventListener('click',()=>{if(check.checked&&conferenciaEmail?.id===result.id)executarConfirmacao(trip,result.id);});
    const motivo=element('p',real?'Revise os dados e marque a confirmação explícita. O envio também depende da configuração documental verificada acima.':'Conferência de preparação em modo SIMULADO. Não autoriza envio real nem executa simulação. Configurar REAL exige nova conferência.');motivo.id='motivo-confirmacao';
    target.replaceChildren(element('strong',`${real?'ENVIO REAL':'PREPARAÇÃO SEM ENVIO'} · arquivos conferidos no servidor`),element('p','Nenhum e-mail enviado nesta prévia.'),element('p',`CT-e ${result.snapshot.numero}`),element('p',`Remetente SMTP: ${result.snapshot.remetente||'não configurado — será necessário configurar e conferir novamente antes do envio real'}`),element('p',`Destinatário: ${result.snapshot.destinatario}`),element('p',`Assunto: ${result.snapshot.assunto}`),element('pre',result.snapshot.mensagem,'mensagem-confirmacao'),listaSnapshot(result.snapshot),...(compartilhada?[element('p','⚠ Fatura compartilhada: pode conter informações de outros clientes. Confira o documento integral e a autorização antes de enviar; os vínculos não autorizam envio a todos.')]:[]),label,button,motivo);target.hidden=false;
  }catch(error){if(version===previa){$('motivo-previa').textContent=error.message;state.erroConferencia=error.message;}}finally{state.previaOcupada=false;$('gerar-previa').textContent='Gerar prévia →';atualizarSelecao();if(state.erroConferencia){$('motivo-previa').textContent=state.erroConferencia+' Revise a seleção e gere nova conferência.';state.erroConferencia=null;}}
});
export function renderizarTentativas(items){
  tentativasEmail=items;const target=$('tentativas');target.replaceChildren();if(!items.length){target.textContent='Nenhuma tentativa manual registrada.';return;}
  for(const item of items){
    const section=element('section',null,`diag-card diag-${item.situacao==='FALHA'?'erro':item.situacao==='DESCONHECIDO'?'atencao':item.situacao==='ACEITO_SMTP'?'sucesso':'atencao'}`);
    section.append(element('strong'),element('p',`${new Date(item.criadaEm).toLocaleString('pt-BR')} · CT-e ${item.snapshot.numero} · ${item.snapshot.destinatario}`));
    if(item.situacao==='FALHA'||item.situacao==='DESCONHECIDO')section.append(element('p',item.motivo),element('p',item.situacao==='DESCONHECIDO'?'Confira o resultado antes de preparar um reenvio. Não há repetição automática.':'Revise a causa e confira novamente antes de preparar um reenvio.'));
    const detalhes=element('details');detalhes.append(element('summary','Ver detalhes'),element('p',`Tentativa ${item.id} · ${item.modo} · ${item.situacao}`),listaSnapshot(item.snapshot));
    detalhes.append(element('p',`Remetente: ${item.snapshot.remetente||'não registrado no histórico'}`),element('p',`Assunto: ${item.snapshot.assunto||'não registrado no histórico'}`),element('pre',item.snapshot.mensagem||'Texto não registrado neste histórico antigo.','mensagem-confirmacao'));
    if(item.situacao!=='FALHA'&&item.situacao!=='DESCONHECIDO')detalhes.append(element('p',item.motivo));
    if(item.anteriorId)detalhes.append(element('p',`Reenvio da tentativa ${item.anteriorId}`));
    section.append(detalhes);
    const titulo=section.querySelector('strong');titulo.textContent=item.situacao==='FALHA'?`⛔ Falha${item.modo==='SIMULADO'?' no teste sem envio':''}`:item.situacao==='DESCONHECIDO'?`⚠ Resultado desconhecido${item.modo==='SIMULADO'?' do teste sem envio':''}`:item.modo==='SIMULADO'?'Teste sem envio':item.situacao==='ACEITO_SMTP'?'Aceito pelo provedor — entrega não comprovada':'⚠ Em processamento';
    if(item.modo==='SIMULADO'&&!['FALHA','DESCONHECIDO'].includes(item.situacao))section.className='diag-card';
    if(podeOperar()&&item.id===items.at(-1).id){const button=element('button','Preparar reenvio confirmado','button secondary');button.type='button';button.addEventListener('click',()=>{if(tentativaOcupada)return;anteriorEmail=item.id;invalidarPrevia();$('referencia-reenvio').textContent=`Reenvio deliberado. ${item.situacao==='DESCONHECIDO'?'Resultado anterior desconhecido: revise antes de prosseguir. ':''}Escolha destinatário e anexos e gere nova prévia.`;$('destinatario').focus();});section.append(button);}target.append(section);
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
