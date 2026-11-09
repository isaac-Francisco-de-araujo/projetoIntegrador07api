import { $,api,element,podeAdministrar } from './painel-base.js';
import { apresentarErro } from './painel-diagnostico.js';
import { mostrarConferenciaMensagem,pedidoConfirmacao } from './painel-confirmacao-recebimento.js';
let salvo=null,ocupado=false,alterado=false,testeId=null;
const campos=['config-remetente','config-subpasta','config-auto-exportacao'];
function estado(texto,erro=false,incerto=false){$('estado-configuracoes').textContent=texto;$('estado-configuracoes').classList.toggle('error',erro&&!incerto);$('estado-configuracoes').classList.toggle('attention',incerto);}
function detalhesFalha(error){const detalhes=element('details');detalhes.append(element('summary','Ver detalhes da falha'));apresentarErro(detalhes,error);$('config-diagnostico').replaceChildren(detalhes);}
function controles(){
  const habilitado=podeAdministrar()&&salvo&&!ocupado;
  $('campos-configuracoes').disabled=!habilitado;$('salvar-configuracoes').disabled=!habilitado||!alterado;
  for(const id of ['testar-config-email','testar-config-pasta','criar-config-subpasta'])$(id).disabled=!habilitado||alterado;
  $('recarregar-configuracoes').disabled=ocupado;
  $('config-email-teste').disabled=ocupado||!podeAdministrar();
  $('preparar-teste-real').disabled=!habilitado||alterado||!salvo.confirmacaoMotorista.prontoParaTesteReal;
}
function pastas(lista,selecionada){const opcoes=[...new Set(['',...lista,selecionada])];$('config-subpasta').replaceChildren(...opcoes.map(p=>new Option(p||'Pasta principal autorizada',p)));$('config-subpasta').value=selecionada;}
function preencher(c){
  $('config-diagnostico').replaceChildren();
  salvo=c;alterado=false;testeId=null;$('config-remetente').value=c.remetente||'';pastas(c.subpastas,c.subpasta);$('config-auto-exportacao').checked=c.exportacaoAutomatica;
  $('resultado-teste-real').replaceChildren();
  $('config-local-pasta').textContent=c.raizAutorizada?'Destino: pasta autorizada'+(c.subpasta?' / '+c.subpasta:''):'Pasta ainda não configurada';
  $('config-modo-email').textContent=`Modo documental: ${c.modoEnvioDocumental} · Notificações: ${c.modoNotificacoes}`;
  const documental=c.modoEnvioDocumental==='REAL',divergente=documental&&c.situacaoEmail==='REMETENTE_DIVERGE_SMTP';
  $('config-situacao-email').textContent=!c.remetente||!documental?'⚠ Envio não configurado. Salvar o endereço não autentica a conta.':divergente?'⛔ O remetente salvo difere do SMTP externo. Peça ao administrador para conferir a conta.':'⚠ Envio documental configurado externamente; autenticação e entrega não verificadas.';
  $('config-situacao-email').classList.toggle('error',divergente);$('config-situacao-email').classList.toggle('attention',!divergente);
  const canal=c.confirmacaoMotorista;$('config-prerequisitos-email').textContent=`Confirmação opcional ao motorista (independente do envio ao cliente): ${canal.modo} · Conta: ${canal.tipoConta} · Autenticação: ${canal.autenticacao}. ${canal.realDisponivel?'Configuração local pronta para teste real explícito; autenticação e entrega ainda não homologadas.':canal.preRequisitos.join('. ')+'.'}`;
  const autorizacao={REGISTRADA_EXTERNAMENTE:'Autorização OAuth2 registrada pelo utilitário externo; SMTP ainda depende de teste.',NAO_VERIFICADA:'Credencial externa disponível; autorização e autenticação no provedor ainda não verificadas.',EXPIRADA:'Credencial expirada; execute a renovação externa. Reconecte se for exigida nova autorização.',RECONEXAO_NECESSARIA:'É necessário reconectar a conta Microsoft pelo utilitário externo.',AUSENTE:'Endereço salvo; autorização da conta pendente.',AUTORIZACAO_PENDENTE:'Endereço salvo; autorização da conta pendente.',INVALIDA:'Credencial externa inválida; verifique a autorização.',INDISPONIVEL:'Renovação indisponível; confira o utilitário externo e a conexão.'};
  const linhas=[['Endereço remetente',c.remetente?'Salvo; não significa conta autenticada.':'Não informado.'],['Autenticação configurada',canal.autenticacaoConfigurada?'SMTP OAuth2 configurado externamente.':'Configuração externa incompleta.'],['Autorização da conta',autorizacao[canal.autorizacao]||'Autorização não verificada.'],['Renovação',canal.renovacao==='EXTERNA_GERENCIADA'?'Gerenciada pelo utilitário externo; mantenha o agendamento ativo.':'Gerenciamento não informado; um token copiado manualmente expira e não é renovado pelo backend.'],['Modo',canal.modo],['Permissão desta sessão',`${canal.permissaoSessao}. ${canal.sessaoPodeEnviarReal?'Pode confirmar recebimento quando a configuração estiver pronta.':'Envio real indisponível para esta sessão.'}`],['Prontidão para teste',canal.prontoParaTesteReal?'Configuração pronta para teste confirmado; nenhum envio externo autorizado por este estado.':'Teste real indisponível; resolva os requisitos e a permissão ADMIN.']];
  $('config-conta-estado').replaceChildren(...linhas.flatMap(([nome,texto])=>[element('dt',nome),element('dd',texto)]));
  estado(`Configuração salva.${c.raizAutorizada?'':' Pasta não configurada; procure o administrador do servidor.'}`);controles();
}
async function resultados(){
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
  if(!podeAdministrar()){estado('Configurações restritas ao papel ADMIN quando a segurança está ativa.');controles();return;}
  ocupado=true;controles();
    try{preencher(await api('/api/configuracoes'));}
  catch(error){estado('⛔ '+error.message,true);detalhesFalha(error);}
  finally{ocupado=false;controles();}
}
async function executar(acao){ocupado=true;controles();try{await acao();$('config-diagnostico').replaceChildren();}catch(error){if(error.incerto===undefined&&!error.diagnosticoOperacao){error.incerto=false;error.codigo='CONFIGURACAO_INCOMPLETA';}estado((error.incerto?'⚠ ':'⛔ ')+error.message,true,Boolean(error.incerto));detalhesFalha(error);}finally{ocupado=false;controles();}}
export function iniciarConfiguracoes(){
  $('config-verificar-conta').addEventListener('click',event=>{event.preventDefault();$('config-conta-detalhes').open=true;$('config-conta-detalhes').querySelector('summary').focus();});
  $('config-historico-testes').addEventListener('toggle',()=>{if($('config-historico-testes').open)resultados().catch(error=>apresentarErro($('config-resultados-testes'),error));});
  for(const id of campos)$(id).addEventListener('input',()=>{alterado=true;testeId=null;$('resultado-teste-real').replaceChildren();estado('Alterações ainda não salvas. Salve para usar ou testar esta configuração.');controles();});
  $('recarregar-configuracoes').addEventListener('click',()=>{if(alterado&&!confirm('O rascunho ainda não foi salvo. Descartá-lo e carregar a configuração atual do servidor para revisar?'))return;carregar();});
  $('form-configuracoes').addEventListener('submit',event=>{event.preventDefault();if(ocupado||!salvo)return;executar(async()=>{
    const resposta=await api('/api/configuracoes',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({versao:salvo.versao,remetente:$('config-remetente').value||null,exportacaoAutomatica:$('config-auto-exportacao').checked,subpasta:$('config-subpasta').value})});
    preencher(resposta);document.dispatchEvent(new Event('configuracoes-atualizadas'));
  });});
  $('testar-config-email').addEventListener('click',()=>executar(async()=>{
    const input=$('config-email-teste');if(!input.value||!input.reportValidity())throw new Error('Informe um destinatário válido para este teste.');
    testeId||=crypto.randomUUID();await api('/api/configuracoes/teste-email',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({id:testeId,destinatario:input.value})});estado('Verificação registrada sem envio. Consulte o histórico de testes.');await resultados();
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
