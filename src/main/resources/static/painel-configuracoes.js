import { $,api,element,avisar,podeAdministrar } from './painel-base.js';
import { apresentarErro } from './painel-diagnostico.js';
import { mostrarConferenciaMensagem,pedidoConfirmacao } from './painel-confirmacao-recebimento.js';
let salvo=null,ocupado=false,alterado=false,testeId=null;
const campos=['config-remetente','config-subpasta','config-auto-exportacao'];
function estado(texto,erro=false){$('estado-configuracoes').textContent=texto;$('estado-configuracoes').classList.toggle('error',erro);}
function controles(){
  const habilitado=podeAdministrar()&&salvo&&!ocupado;
  $('campos-configuracoes').disabled=!habilitado;$('salvar-configuracoes').disabled=!habilitado||!alterado;
  for(const id of ['testar-config-email','testar-config-pasta','criar-config-subpasta'])$(id).disabled=!habilitado||alterado;
  $('recarregar-configuracoes').disabled=ocupado;
  $('config-email-teste').disabled=ocupado||!podeAdministrar();
  $('preparar-teste-real').disabled=!habilitado||alterado||!salvo.confirmacaoMotorista.prontoParaTesteReal;
}
function pastas(lista,selecionada){const opcoes=[...new Set(['',...lista,selecionada])];$('config-subpasta').replaceChildren(...opcoes.map(p=>new Option(p||'Raiz autorizada (backend; teste o acesso)',p)));$('config-subpasta').value=selecionada;}
function preencher(c){
  $('config-diagnostico').replaceChildren();
  salvo=c;alterado=false;testeId=null;$('config-remetente').value=c.remetente||'';pastas(c.subpastas,c.subpasta);$('config-auto-exportacao').checked=c.exportacaoAutomatica;
  $('resultado-teste-real').replaceChildren();
  $('config-local-pasta').textContent=c.localDaPasta;
  $('config-modo-email').textContent=`Modo documental: ${c.modoEnvioDocumental} · Notificações: ${c.modoNotificacoes}`;
  const situacao={REMETENTE_NAO_INFORMADO:'Configuração incompleta: informe o remetente.',REMETENTE_DIVERGE_SMTP:'O endereço salvo difere do remetente SMTP externo; a configuração precisa ser conferida.',ENDERECO_SALVO_SMTP_EXTERNO:'Endereço salvo. Autenticação e conectividade SMTP dependem da configuração externa.'};
  $('config-situacao-email').textContent=`${situacao[c.situacaoEmail]||c.situacaoEmail} Endereço salvo: ${c.remetente||'não informado'}.`;
  const canal=c.confirmacaoMotorista;$('config-prerequisitos-email').textContent=`Confirmação ao motorista: ${canal.modo} · Conta: ${canal.tipoConta} · Autenticação: ${canal.autenticacao}. ${canal.realDisponivel?'Configuração local pronta para teste real explícito; autenticação e entrega ainda não homologadas.':canal.preRequisitos.join('. ')+'.'}`;
  const autorizacao={REGISTRADA_EXTERNAMENTE:'Autorização OAuth2 registrada pelo utilitário externo; SMTP ainda depende de teste.',NAO_VERIFICADA:'Credencial externa disponível; autorização e autenticação no provedor ainda não verificadas.',EXPIRADA:'Credencial expirada; execute a renovação externa. Reconecte se for exigida nova autorização.',RECONEXAO_NECESSARIA:'É necessário reconectar a conta Microsoft pelo utilitário externo.',AUSENTE:'Endereço salvo; autorização da conta pendente.',AUTORIZACAO_PENDENTE:'Endereço salvo; autorização da conta pendente.',INVALIDA:'Credencial externa inválida; verifique a autorização.',INDISPONIVEL:'Renovação indisponível; confira o utilitário externo e a conexão.'};
  const linhas=[['Endereço remetente',c.remetente?'Salvo; não significa conta autenticada.':'Não informado.'],['Autenticação configurada',canal.autenticacaoConfigurada?'SMTP OAuth2 configurado externamente.':'Configuração externa incompleta.'],['Autorização da conta',autorizacao[canal.autorizacao]||'Autorização não verificada.'],['Renovação',canal.renovacao==='EXTERNA_GERENCIADA'?'Gerenciada pelo utilitário externo; mantenha o agendamento ativo.':'Gerenciamento não informado; um token copiado manualmente expira e não é renovado pelo backend.'],['Modo',canal.modo],['Permissão desta sessão',`${canal.permissaoSessao}. ${canal.sessaoPodeEnviarReal?'Pode confirmar recebimento quando a configuração estiver pronta.':'Envio real indisponível para esta sessão.'}`],['Prontidão para teste',canal.prontoParaTesteReal?'Configuração pronta para teste confirmado; nenhum envio externo autorizado por este estado.':'Teste real indisponível; resolva os requisitos e a permissão ADMIN.']];
  $('config-conta-estado').replaceChildren(...linhas.flatMap(([nome,texto])=>[element('dt',nome),element('dd',texto)]));
  estado(`Configuração salva · versão ${c.versao}. ${c.raizAutorizada?'Destino limitado à raiz autorizada do backend.':'Pasta incompleta: falta configurar a raiz autorizada externamente.'}`);controles();
}
async function resultados(){
  const testes=await api('/api/configuracoes/testes');const alvo=$('config-resultados-testes');alvo.replaceChildren();
  for(const t of testes){const artigo=element('article',null,t.tipo==='EMAIL_REAL'?`diag-card diag-${t.situacao==='FALHA'?'erro':t.situacao==='DESCONHECIDO'?'atencao':'sucesso'}`:'');artigo.append(element('strong',`${t.tipo} · ${t.situacao}`),element('p',t.mensagem),element('small',`${new Date(t.criadoEm).toLocaleString('pt-BR')} · Referência ${t.id}`));if(t.tipo==='EMAIL_REAL'){const b=element('button','Ver mensagem do teste','button secondary');b.type='button';b.addEventListener('click',async()=>{b.disabled=true;try{const c=await api('/api/configuracoes/conferencias-email-real/'+encodeURIComponent(t.id));const d=element('details');d.open=true;d.append(element('summary','Retrato confirmado — somente consulta'),element('p',`De ${c.snapshot.remetente} para ${c.snapshot.destinatario} · ${c.snapshot.modo}`),element('p',c.snapshot.assunto),element('pre',c.snapshot.mensagem,'mensagem-confirmacao'));artigo.append(d);}catch(error){apresentarErro(artigo,error);}finally{b.disabled=false;}});artigo.append(b);}alvo.append(artigo);}
  if(!testes.length)alvo.append(element('p','Nenhum teste registrado.'));
}
async function carregar(){
  if(!podeAdministrar()){estado('Configurações restritas ao papel ADMIN quando a segurança está ativa.');controles();return;}
  ocupado=true;controles();
  try{preencher(await api('/api/configuracoes'));await resultados();}
  catch(error){estado(error.message,true);apresentarErro($('config-diagnostico'),error,{tentarNovamente:carregar});}
  finally{ocupado=false;controles();}
}
async function executar(acao){ocupado=true;controles();try{await acao();}catch(error){if(error.incerto===undefined&&!error.diagnosticoOperacao){error.incerto=false;error.codigo='CONFIGURACAO_INCOMPLETA';}estado(error.message,true);apresentarErro($('config-diagnostico'),error);}finally{ocupado=false;controles();}}
export function iniciarConfiguracoes(){
  for(const id of campos)$(id).addEventListener('input',()=>{alterado=true;testeId=null;$('resultado-teste-real').replaceChildren();estado('Alterações ainda não salvas. Salve para usar ou testar esta configuração.');controles();});
  $('recarregar-configuracoes').addEventListener('click',()=>{if(alterado&&!confirm('O rascunho ainda não foi salvo. Descartá-lo e carregar a configuração atual do servidor para revisar?'))return;carregar();});
  $('form-configuracoes').addEventListener('submit',event=>{event.preventDefault();if(ocupado||!salvo)return;executar(async()=>{
    const resposta=await api('/api/configuracoes',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({versao:salvo.versao,remetente:$('config-remetente').value||null,exportacaoAutomatica:$('config-auto-exportacao').checked,subpasta:$('config-subpasta').value})});
    preencher(resposta);document.dispatchEvent(new Event('configuracoes-atualizadas'));avisar('Configurações salvas pelo servidor. Nenhum acervo anterior foi exportado.');
  });});
  $('testar-config-email').addEventListener('click',()=>executar(async()=>{
    const input=$('config-email-teste');if(!input.value||!input.reportValidity())throw new Error('Informe um destinatário válido para este teste.');
    testeId||=crypto.randomUUID();const t=await api('/api/configuracoes/teste-email',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({id:testeId,destinatario:input.value})});estado(t.mensagem);await resultados();
  }));
  $('config-email-teste').addEventListener('input',()=>{testeId=null;$('resultado-teste-real').replaceChildren();});
  const exibirTeste=c=>mostrarConferenciaMensagem($('resultado-teste-real'),c,async conf=>{try{sessionStorage.setItem('rotabrasil-teste-real',conf.id);}catch{}await executar(async()=>{const t=await api('/api/configuracoes/confirmacoes-email-real',pedidoConfirmacao({conferenciaId:conf.id,confirmado:true}));try{sessionStorage.removeItem('rotabrasil-teste-real');}catch{}estado(t.mensagem,t.situacao==='FALHA');$('resultado-teste-real').replaceChildren(element('p',`${t.situacao} · ${t.mensagem}`));await resultados();});try{$('recuperar-teste-real').hidden=!sessionStorage.getItem('rotabrasil-teste-real');}catch{}});
  $('preparar-teste-real').addEventListener('click',()=>executar(async()=>{const input=$('config-email-teste');if(!input.value||!input.reportValidity())throw new Error('Informe o destinatário explícito do teste.');const c=await api('/api/configuracoes/conferencias-email-real',pedidoConfirmacao({destinatario:input.value,anteriorId:null}));exibirTeste(c);}));
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
