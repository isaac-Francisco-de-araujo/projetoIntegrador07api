import { $,api,element,avisar,podeAdministrar } from './painel-base.js';
import { apresentarErro } from './painel-diagnostico.js';
let salvo=null,ocupado=false,alterado=false,testeId=null;
const campos=['config-remetente','config-subpasta','config-auto-exportacao'];
function estado(texto,erro=false){$('estado-configuracoes').textContent=texto;$('estado-configuracoes').classList.toggle('error',erro);}
function controles(){
  const habilitado=podeAdministrar()&&salvo&&!ocupado;
  $('campos-configuracoes').disabled=!habilitado;$('salvar-configuracoes').disabled=!habilitado||!alterado;
  for(const id of ['testar-config-email','testar-config-pasta','criar-config-subpasta'])$(id).disabled=!habilitado||alterado;
  $('recarregar-configuracoes').disabled=ocupado;
}
function pastas(lista,selecionada){const opcoes=[...new Set(['',...lista,selecionada])];$('config-subpasta').replaceChildren(...opcoes.map(p=>new Option(p||'Raiz autorizada (backend; teste o acesso)',p)));$('config-subpasta').value=selecionada;}
function preencher(c){
  $('config-diagnostico').replaceChildren();
  salvo=c;alterado=false;testeId=null;$('config-remetente').value=c.remetente||'';pastas(c.subpastas,c.subpasta);$('config-auto-exportacao').checked=c.exportacaoAutomatica;
  $('config-local-pasta').textContent=c.localDaPasta;
  $('config-modo-email').textContent=`Modo documental: ${c.modoEnvioDocumental} · Notificações: ${c.modoNotificacoes}`;
  const situacao={REMETENTE_NAO_INFORMADO:'Configuração incompleta: informe o remetente.',REMETENTE_DIVERGE_SMTP:'O endereço salvo difere do remetente SMTP externo; a configuração precisa ser conferida.',ENDERECO_SALVO_SMTP_EXTERNO:'Endereço salvo. Autenticação e conectividade SMTP dependem da configuração externa.'};
  $('config-situacao-email').textContent=`${situacao[c.situacaoEmail]||c.situacaoEmail} Endereço salvo: ${c.remetente||'não informado'}.`;
  estado(`Configuração salva · versão ${c.versao}. ${c.raizAutorizada?'Destino limitado à raiz autorizada do backend.':'Pasta incompleta: falta configurar a raiz autorizada externamente.'}`);controles();
}
async function resultados(){
  const testes=await api('/api/configuracoes/testes');const alvo=$('config-resultados-testes');alvo.replaceChildren();
  for(const t of testes){const artigo=element('article');artigo.append(element('strong',`${t.tipo} · ${t.situacao}`),element('p',t.mensagem),element('small',`${new Date(t.criadoEm).toLocaleString('pt-BR')} · Referência ${t.id}`));alvo.append(artigo);}
  if(!testes.length)alvo.append(element('p','Nenhum teste registrado.'));
}
async function carregar(){
  if(!podeAdministrar()){estado('Configurações restritas ao papel ADMIN quando a segurança está ativa.');controles();return;}
  ocupado=true;controles();
  try{preencher(await api('/api/configuracoes'));await resultados();}
  catch(error){estado(`Configuração indisponível: ${error.message} A versão V10 do banco precisa estar preparada pelo responsável.`,true);apresentarErro($('config-diagnostico'),error);}
  finally{ocupado=false;controles();}
}
async function executar(acao){ocupado=true;controles();try{await acao();}catch(error){if(error.incerto===undefined&&!error.diagnosticoOperacao){error.incerto=false;error.codigo='CONFIGURACAO_INCOMPLETA';}estado(error.message,true);apresentarErro($('config-diagnostico'),error);}finally{ocupado=false;controles();}}
export function iniciarConfiguracoes(){
  for(const id of campos)$(id).addEventListener('input',()=>{alterado=true;testeId=null;estado('Alterações ainda não salvas. Salve para usar ou testar esta configuração.');controles();});
  $('recarregar-configuracoes').addEventListener('click',()=>{if(alterado){estado('Há alterações não salvas. Salve-as antes de recarregar para evitar perdê-las.');return;}carregar();});
  $('form-configuracoes').addEventListener('submit',event=>{event.preventDefault();if(ocupado||!salvo)return;executar(async()=>{
    const resposta=await api('/api/configuracoes',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({versao:salvo.versao,remetente:$('config-remetente').value||null,exportacaoAutomatica:$('config-auto-exportacao').checked,subpasta:$('config-subpasta').value})});
    preencher(resposta);avisar('Configurações salvas pelo servidor. Nenhum acervo anterior foi exportado.');
  });});
  $('testar-config-email').addEventListener('click',()=>executar(async()=>{
    const input=$('config-email-teste');if(!input.value||!input.reportValidity())throw new Error('Informe um destinatário válido para este teste.');
    testeId||=crypto.randomUUID();const t=await api('/api/configuracoes/teste-email',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({id:testeId,destinatario:input.value})});estado(t.mensagem);await resultados();
  }));
  $('config-email-teste').addEventListener('input',()=>{testeId=null;});
  $('testar-config-pasta').addEventListener('click',()=>executar(async()=>{const t=await api('/api/configuracoes/teste-pasta',{method:'POST'});estado(t.mensagem,t.situacao!=='GRAVAVEL');await resultados();}));
  $('criar-config-subpasta').addEventListener('click',()=>executar(async()=>{
    const input=$('config-nova-subpasta');if(!input.value||!input.reportValidity())throw new Error('Informe um nome de subpasta válido.');
    const lista=await api('/api/configuracoes/subpastas',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({nome:input.value})});pastas(lista,input.value);alterado=true;estado('Subpasta criada no backend. Salve a configuração para utilizá-la como destino.');
  }));
  window.addEventListener('beforeunload',event=>{if(alterado){event.preventDefault();event.returnValue='';}});
  return carregar();
}
