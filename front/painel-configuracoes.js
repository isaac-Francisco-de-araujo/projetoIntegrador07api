import { $,api,element,podeAdministrar } from './painel-base.js';
import { apresentarErro } from './painel-diagnostico.js';
import { mostrarConferenciaMensagem,pedidoConfirmacao } from './painel-confirmacao-recebimento.js';
import { iniciarContaTeste,atualizarUltimaVerificacao } from './painel-conta-teste.js';
let salvo=null,ocupado=false,alterado=false,testeId=null;
let faseConsulta='INICIAL';
let contaTestePronta=false,contaTesteAlterada=false;
let consultaDocumental=0;
async function carregarDocumental(){
  const seq=++consultaDocumental;$('recarregar-documental').disabled=true;
  $('config-documental-estado').textContent='Consultando configuração documental, sem conectar a SMTP…';$('config-documental-bloqueios').replaceChildren();
  try{const d=await api('/api/envio-documental');if(seq!==consultaDocumental)return;
    $('config-documental-estado').textContent=`Canal documental: ${d.provedor} · modo ${d.modo} · autenticação ${d.autenticacao}. ${d.confirmacaoRealDisponivel?'Prontidão local; autenticação no provedor e entrega não comprovadas.':'Confirmação real bloqueada; preparação sem envio disponível.'}`;
    $('config-documental-bloqueios').replaceChildren(...d.bloqueios.map(m=>element('li',m)));
  }catch(error){if(seq===consultaDocumental)$('config-documental-estado').textContent=error.status===401||error.status===403?error.message:'Não foi possível consultar a configuração documental. Reconsulte; o estado do transporte é desconhecido.';}
  finally{if(seq===consultaDocumental)$('recarregar-documental').disabled=false;}
}
const campos=['config-remetente','config-subpasta','config-auto-exportacao'];
function estado(texto,erro=false,incerto=false){$('estado-configuracoes').textContent=texto;$('estado-configuracoes').classList.toggle('error',erro&&!incerto);$('estado-configuracoes').classList.toggle('attention',incerto);}
function detalhesFalha(error){const detalhes=element('details');detalhes.append(element('summary','Ver detalhes da falha'));apresentarErro(detalhes,error);$('config-diagnostico').replaceChildren(detalhes);}
function controles(){
  const bloqueio=!podeAdministrar()?(document.body.dataset.sessaoEncerrada==='true'?'SESSAO_ENCERRADA':'PERMISSAO'):ocupado?(faseConsulta==='CARREGANDO'?'CARREGANDO':'PROCESSAMENTO'):faseConsulta!=='DISPONIVEL'||!salvo?(faseConsulta==='FALHA'?'CONSULTA_FALHOU':'INICIAL'):'';
  const motivos={INICIAL:'Campos bloqueados até consultar a configuração salva. Use Recarregar configuração.',CARREGANDO:'Carregando configuração. Aguarde a consulta para editar.',PROCESSAMENTO:'Processamento das configurações em andamento. Aguarde a conclusão.',CONSULTA_FALHOU:'Edição bloqueada até uma consulta válida. Use Recarregar configuração; os valores em tela não foram confirmados.',PERMISSAO:'Configurações restritas ao papel ADMIN. Solicite a alteração a um administrador.',SESSAO_ENCERRADA:'Sessão encerrada. Entre novamente e recarregue antes de editar as configurações.'};
  const habilitado=!bloqueio;
  $('campos-configuracoes').dataset.bloqueio=bloqueio||'NENHUM';
  $('campos-configuracoes').setAttribute('aria-busy',String(ocupado));
  $('motivo-configuracoes').textContent=motivos[bloqueio]||'';$('motivo-configuracoes').hidden=!bloqueio;
  $('campos-configuracoes').disabled=!habilitado;$('salvar-configuracoes').disabled=!habilitado||!alterado;
   for(const id of ['testar-config-email','testar-config-pasta','criar-config-subpasta'])$(id).disabled=!habilitado||alterado||(id==='testar-config-email'&&contaTesteAlterada);
  $('recarregar-configuracoes').disabled=ocupado||!podeAdministrar();
  $('config-email-teste').disabled=ocupado||!podeAdministrar();
  $('preparar-teste-real').disabled=!habilitado||alterado||!contaTestePronta;
}
function pastas(lista,selecionada){const opcoes=[...new Set(['',...lista,selecionada])];$('config-subpasta').replaceChildren(...opcoes.map(p=>new Option(p||'Pasta principal autorizada',p)));$('config-subpasta').value=selecionada;}
function preencher(c){
  $('config-diagnostico').replaceChildren();
  salvo=c;alterado=false;testeId=null;$('config-remetente').value=c.remetente||'';pastas(c.subpastas,c.subpasta);$('config-auto-exportacao').checked=c.exportacaoAutomatica;
  $('resultado-teste-real').replaceChildren();
  $('config-local-pasta').textContent=c.raizAutorizada?'Destino: pasta autorizada'+(c.subpasta?' / '+c.subpasta:''):'Pasta ainda não configurada';
  $('config-modo-email').textContent=`Modo documental: ${c.modoEnvioDocumental} · Notificações: ${c.modoNotificacoes}`;
  const documental=c.modoEnvioDocumental==='REAL',divergente=documental&&c.situacaoEmail==='REMETENTE_DIVERGE_SMTP';
   $('config-situacao-email').textContent=!c.remetente?'Remetente não informado. Salve o endereço autorizado; isso não autentica a conta.':divergente?'⛔ O remetente salvo difere do SMTP externo. Confira os requisitos documentais abaixo.':'Endereço salvo; autenticação não comprovada. Consulte o diagnóstico documental abaixo.';
  $('config-situacao-email').classList.toggle('error',divergente);$('config-situacao-email').classList.toggle('attention',!divergente);
  const canal=c.confirmacaoMotorista;$('config-prerequisitos-email').textContent=`Confirmação opcional ao motorista (independente do envio ao cliente): ${canal.modo} · Conta: ${canal.tipoConta} · Autenticação: ${canal.autenticacao}. ${canal.realDisponivel?'Configuração local pronta para teste real explícito; autenticação e entrega ainda não homologadas.':canal.preRequisitos.join('. ')+'.'}`;
  const autorizacao={REGISTRADA_EXTERNAMENTE:'Autorização OAuth2 registrada pelo utilitário externo; SMTP ainda depende de teste.',NAO_VERIFICADA:'Credencial externa disponível; autorização e autenticação no provedor ainda não verificadas.',EXPIRADA:'Credencial expirada; execute a renovação externa. Reconecte se for exigida nova autorização.',RECONEXAO_NECESSARIA:'É necessário reconectar a conta Microsoft pelo utilitário externo.',AUSENTE:'Endereço salvo; autorização da conta pendente.',AUTORIZACAO_PENDENTE:'Endereço salvo; autorização da conta pendente.',INVALIDA:'Credencial externa inválida; verifique a autorização.',INDISPONIVEL:'Renovação indisponível; confira o utilitário externo e a conexão.'};
  const linhas=[['Endereço remetente',c.remetente?'Salvo; não significa conta autenticada.':'Não informado.'],['Autenticação configurada',canal.autenticacaoConfigurada?'SMTP OAuth2 configurado externamente.':'Configuração externa incompleta.'],['Autorização da conta',autorizacao[canal.autorizacao]||'Autorização não verificada.'],['Renovação',canal.renovacao==='EXTERNA_GERENCIADA'?'Gerenciada pelo utilitário externo; mantenha o agendamento ativo.':'Gerenciamento não informado; um token copiado manualmente expira e não é renovado pelo backend.'],['Modo',canal.modo],['Permissão desta sessão',`${canal.permissaoSessao}. ${canal.sessaoPodeEnviarReal?'Pode confirmar recebimento quando a configuração estiver pronta.':'Envio real indisponível para esta sessão.'}`],['Prontidão para teste',canal.prontoParaTesteReal?'Configuração pronta para teste confirmado; nenhum envio externo autorizado por este estado.':'Teste real indisponível; resolva os requisitos e a permissão ADMIN.']];
  $('config-conta-estado').replaceChildren(...linhas.flatMap(([nome,texto])=>[element('dt',nome),element('dd',texto)]));
  faseConsulta='DISPONIVEL';estado(`Configuração salva.${c.raizAutorizada?'':' Pasta não configurada; procure o administrador do servidor.'}`);controles();
}
async function resultados(){
  atualizarUltimaVerificacao();
  const testes=await api('/api/configuracoes/testes');const alvo=$('config-resultados-testes');alvo.replaceChildren();
  for(const t of testes){
    const titulo=t.situacao==='SIMULADO'?'Teste sem envio':t.situacao==='ACEITO_SMTP'?'Aceito pelo provedor — entrega não comprovada':t.situacao==='FALHA'?'⛔ Falha':t.situacao==='DESCONHECIDO'?'⚠ Resultado desconhecido':t.situacao==='GRAVAVEL'?'✓ Pasta verificada':'Verificação requer atenção';
    const artigo=element('article');artigo.append(element('strong',titulo),element('p',new Date(t.criadoEm).toLocaleString('pt-BR')));
    const detalhes=element('details');detalhes.append(element('summary','Ver detalhes'),element('p',t.mensagem),element('small',`${t.tipo} · ${t.situacao} · ${t.id}`));artigo.append(detalhes);alvo.append(artigo);
    if(t.tipo==='EMAIL_REAL'){
      const mensagem=element('button','Ver mensagem do teste','button secondary');mensagem.type='button';
      mensagem.addEventListener('click',async()=>{mensagem.disabled=true;try{const c=await api('/api/configuracoes/conferencias-email-real/'+encodeURIComponent(t.id));detalhes.append(element('p',`De ${c.snapshot.remetente} para ${c.snapshot.destinatario}`),element('p',c.snapshot.assunto),element('pre',c.snapshot.mensagem,'mensagem-confirmacao'));}catch(error){apresentarErro(detalhes,error);}finally{mensagem.disabled=false;}});detalhes.append(mensagem);
    }
  }
  if(!testes.length)alvo.append(element('p','Nenhum teste registrado.'));
}
async function carregar(){
  if(ocupado)return;
  if(!podeAdministrar()){estado('Configurações restritas ao papel ADMIN quando a segurança está ativa.');controles();return;}
  faseConsulta='CARREGANDO';ocupado=true;estado('Carregando configuração salva…');controles();
    try{preencher(await api('/api/configuracoes'));carregarDocumental();}
  catch(error){faseConsulta='FALHA';estado('⛔ '+error.message,true);detalhesFalha(error);}
  finally{ocupado=false;controles();}
}
async function executar(acao){if(ocupado||!podeAdministrar()||faseConsulta!=='DISPONIVEL'||!salvo)return;ocupado=true;controles();try{await acao();$('config-diagnostico').replaceChildren();}catch(error){if(error.incerto===undefined&&!error.diagnosticoOperacao){error.incerto=false;error.codigo='CONFIGURACAO_INCOMPLETA';}estado((error.incerto?'⚠ ':'⛔ ')+error.message,true,Boolean(error.incerto));detalhesFalha(error);}finally{ocupado=false;controles();}}
export function iniciarConfiguracoes(){
  document.addEventListener('sessao-expirada',controles);
  document.addEventListener('conta-teste-estado',event=>{contaTestePronta=event.detail.pronto;contaTesteAlterada=event.detail.alterado;testeId=null;controles();});
  iniciarContaTeste();
   $('config-verificar-conta').addEventListener('click',event=>{event.preventDefault();$('config-documental').tabIndex=-1;$('config-documental').focus();carregarDocumental();});
   $('recarregar-documental').addEventListener('click',carregarDocumental);
  $('config-historico-testes').addEventListener('toggle',()=>{if($('config-historico-testes').open)resultados().catch(error=>apresentarErro($('config-resultados-testes'),error));});
  for(const id of campos)$(id).addEventListener('input',()=>{alterado=true;testeId=null;$('resultado-teste-real').replaceChildren();estado('Alterações ainda não salvas. Salve para usar ou testar esta configuração.');controles();});
  $('recarregar-configuracoes').addEventListener('click',()=>{if(alterado&&!confirm('O rascunho ainda não foi salvo. Descartá-lo e carregar a configuração atual do servidor para revisar?'))return;carregar();});
  $('form-configuracoes').addEventListener('submit',event=>{event.preventDefault();if(ocupado||!salvo)return;executar(async()=>{
    const resposta=await api('/api/configuracoes',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({versao:salvo.versao,remetente:$('config-remetente').value||null,exportacaoAutomatica:$('config-auto-exportacao').checked,subpasta:$('config-subpasta').value})});
     preencher(resposta);carregarDocumental();document.dispatchEvent(new Event('configuracoes-atualizadas'));
  });});
  $('testar-config-email').addEventListener('click',()=>executar(async()=>{
    const input=$('config-email-teste');if(!input.value||!input.reportValidity())throw new Error('Informe um destinatário válido para este teste.');
    testeId||=crypto.randomUUID();await api('/api/configuracoes/teste-email',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({id:testeId,destinatario:input.value})});estado('Verificação registrada sem envio. Consulte o histórico de testes.');await resultados();
  }));
  $('config-email-teste').addEventListener('input',()=>{testeId=null;$('resultado-teste-real').replaceChildren();});
  const exibirTeste=c=>mostrarConferenciaMensagem($('resultado-teste-real'),c,async conf=>{try{sessionStorage.setItem('rotabrasil-teste-real',conf.id);}catch{}await executar(async()=>{const t=await api('/api/configuracoes/confirmacoes-email-real',pedidoConfirmacao({conferenciaId:conf.id,confirmado:true}));try{sessionStorage.removeItem('rotabrasil-teste-real');}catch{}estado(`${t.situacao==='FALHA'?'⛔ ':t.situacao==='DESCONHECIDO'?'⚠ ':'✓ '}${t.mensagem}`,t.situacao==='FALHA',t.situacao==='DESCONHECIDO');$('resultado-teste-real').replaceChildren(element('p',`${t.situacao} · ${t.mensagem}`));await resultados();});try{$('recuperar-teste-real').hidden=!sessionStorage.getItem('rotabrasil-teste-real');}catch{}});
  $('preparar-teste-real').addEventListener('click',()=>executar(async()=>{const input=$('config-email-teste');if(!input.value||!input.reportValidity())throw new Error('Informe o destinatário explícito do teste.');const anterior=(await api('/api/configuracoes/testes')).find(t=>t.tipo==='EMAIL_REAL');
    if(anterior&&!confirm(`Preparar novo teste vinculado à tentativa ${anterior.id} (${anterior.situacao})? Consulte o resultado anterior e verifique o recebimento antes de decidir repetir. A próxima tela exige nova conferência e confirmação.`))return;
    const c=await api('/api/configuracoes/conferencias-email-real',pedidoConfirmacao({destinatario:input.value,anteriorId:anterior?.id||null}));exibirTeste(c);}));
  const recuperar=$('recuperar-teste-real');try{recuperar.hidden=!sessionStorage.getItem('rotabrasil-teste-real');}catch{}
  recuperar.addEventListener('click',()=>executar(async()=>{const id=sessionStorage.getItem('rotabrasil-teste-real');if(id)exibirTeste(await api('/api/configuracoes/conferencias-email-real/'+encodeURIComponent(id)));}));
  $('testar-config-pasta').addEventListener('click',()=>executar(async()=>{const t=await api('/api/configuracoes/teste-pasta',{method:'POST'});estado(t.mensagem,t.situacao!=='GRAVAVEL');await resultados();}));
  $('criar-config-subpasta').addEventListener('click',()=>executar(async()=>{
    const input=$('config-nova-subpasta');if(!input.value||!input.reportValidity())throw new Error('Informe um nome de subpasta válido.');
    const lista=await api('/api/configuracoes/subpastas',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({nome:input.value})});pastas(lista,input.value);alterado=true;estado('Subpasta criada no backend. Salve a configuração para utilizá-la como destino.');
  }));
  window.addEventListener('beforeunload',event=>{if(alterado){event.preventDefault();event.returnValue='';}});
  return carregar();
}
