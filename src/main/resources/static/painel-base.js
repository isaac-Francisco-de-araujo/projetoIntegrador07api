'use strict';
import { apresentarErro,referenciaLocal } from './painel-diagnostico.js';
import { urlAplicacao } from './painel-rotas.js';
export const $ = id => document.getElementById(id);
export const state = { clientes: [], viagens: [], documentos: [], daViagem: [], viagem: '', selecionados: new Set(), anexosSelecionados: new Set(), anexos: [], carregando: false, previaOcupada: false, papeis: [] };
let operacoesLiberadas=false;
export const podeOperar = () => operacoesLiberadas&&state.papeis.some(p => ['ROLE_ADMIN','ROLE_OPERADOR'].includes(p));
export const podeAdministrar = () => state.papeis.includes('ROLE_ADMIN');
export function element(tag,text,className){const node=document.createElement(tag);if(text!==undefined&&text!==null)node.textContent=String(text);if(className)node.className=className;return node;}
export function avisar(message,error=false){const aviso=$('aviso'),obj=message instanceof Error?message:null,incerto=obj?.incerto===true;
  aviso.textContent=`${error?(incerto?'⚠ Atenção — ':'⛔ Erro — '):''}${obj?obj.message:message}`;aviso.classList.toggle('error',error&&!incerto);aviso.classList.toggle('attention',error&&incerto);aviso.setAttribute('role',error&&!incerto?'alert':'status');aviso.hidden=false;
  if(obj&&error)apresentarErro($('diagnostico-global'),obj);
}
export function vazio(tbody,cols,text){const row=element('tr'),cell=element('td',text,'empty');cell.colSpan=cols;row.append(cell);tbody.replaceChildren(row);}
export function data(value){if(!value)return '—';const date=new Date(value.length===10?`${value}T12:00:00`:value);return Number.isNaN(date.getTime())?'—':new Intl.DateTimeFormat('pt-BR').format(date);}
export function tamanhoArquivo(bytes){const unit=bytes>=1048576?'MiB':'KiB';return `${(bytes/(unit==='MiB'?1048576:1024)).toLocaleString('pt-BR',{maximumFractionDigits:1})} ${unit}`;}
let sessaoPromise;
export function carregarSessao(){
  if(!sessaoPromise)sessaoPromise=api('/api/sessao').then(s=>{state.papeis=s.papeis;return s;}).catch(error=>{sessaoPromise=null;throw error;});
  return sessaoPromise;
}
export async function api(path,options={}){
  const { timeoutMs=15000, resposta='json', aoResponder, ...pedido }=options;
  const headers=new Headers(pedido.headers);
  const rota=path.split('?')[0],metodo=(pedido.method||'GET').toUpperCase(),alteracao=!['GET','HEAD','OPTIONS'].includes(metodo);
  const consulta=rota==='/api/configuracoes'?'as configurações':rota.startsWith('/api/configuracoes/')?'os testes das configurações':rota.startsWith('/api/pendencias')?'os processamentos':'os dados solicitados';
  const mensagemConsulta=`Não foi possível carregar ${consulta}.`;
  if(alteracao){if(!operacoesLiberadas)throw Object.assign(new Error('Sessão ou versão não verificada. Entre novamente e recarregue o painel.'),{codigo:'ACESSO_NAO_VERIFICADO',incerto:false});const s=await carregarSessao();if(s.csrf)headers.set(s.csrfHeader,s.csrf);}
  let response;
  try{response=await fetch(urlAplicacao(path),{credentials:'same-origin',...pedido,headers,signal:pedido.signal?AbortSignal.any([pedido.signal,AbortSignal.timeout(timeoutMs)]):AbortSignal.timeout(timeoutMs)});}
  catch(error){const cancelado=pedido.signal?.aborted,timeout=['AbortError','TimeoutError'].includes(error.name);const falha=new Error(alteracao?(cancelado?'A espera foi interrompida no navegador. O resultado ainda não foi confirmado. Consulte o histórico antes de repetir.':timeout?'A resposta demorou além do esperado. O resultado ainda não foi confirmado. Consulte o histórico antes de repetir.':'Não foi possível conectar à aplicação. O resultado ainda não foi confirmado. Consulte o histórico antes de repetir.'):mensagemConsulta);Object.assign(falha,{incerto:alteracao,codigo:cancelado?'ESPERA_INTERROMPIDA':timeout?'TEMPO_LIMITE':'CONEXAO_INDISPONIVEL',referencia:referenciaLocal(),rota,metodo});throw falha;}
  aoResponder?.(response);
  if(response.status===204)return null;
  if(response.ok&&resposta==='bytes')return new Uint8Array(await response.arrayBuffer());
  let body=null;try{body=await response.json();}catch{}
  if(!response.ok){
    if(response.status===401){sessaoPromise=null;document.dispatchEvent(new CustomEvent('sessao-expirada'));}
    const fields=Array.isArray(body?.campos)?body.campos.map(i=>`${i.campo}: ${i.mensagem}`).join('; '):'';
    const error=new Error(response.status===401?'Sessão encerrada. Entre novamente para continuar.':response.status===403?'Acesso negado. Confira sua permissão; se a sessão expirou, entre novamente.':alteracao?(body?.mensagem||'Operação recusada pelo servidor.'):mensagemConsulta);
    Object.assign(error,{rota,metodo,detalhes:[body?.mensagem,fields,`HTTP ${response.status}`].filter(Boolean).join(' ')});
    error.diagnostico=body?.diagnostico;error.diagnosticoOperacao=alteracao?body?.diagnosticoOperacao:null;error.correlacaoId=response.headers.get('X-Request-Id');error.referencia=error.correlacaoId||referenciaLocal();error.incerto=alteracao&&response.status>=500;error.status=response.status;throw error;
  }
  if(body===null||typeof body!=='object'){const erro=new Error(alteracao?'Resposta inválida do servidor. O resultado não foi confirmado; consulte o histórico antes de repetir.':mensagemConsulta);Object.assign(erro,{incerto:alteracao,codigo:'RESPOSTA_INVALIDA',correlacaoId:response.headers.get('X-Request-Id'),rota,metodo});erro.referencia=erro.correlacaoId||referenciaLocal();throw erro;}
  if(metodo==='GET')document.dispatchEvent(new CustomEvent('consulta-confirmada',{detail:{rota}}));
  return body;
}
export async function iniciarSessao(){
  await carregarSessao();operacoesLiberadas=true;
  const s=await carregarSessao();$('sessao-usuario').textContent=`${s.usuario==='DESENVOLVIMENTO_LOCAL'?'Sessão local':s.usuario} · ${s.papeis.map(p=>p.replace('ROLE_','')).join(', ')}`;
  document.documentElement.dataset.operador=String(podeOperar());document.documentElement.dataset.administrador=String(podeAdministrar());
  $('sessao-usuario').title=`${s.usuario} · ${s.papeis.map(p=>p.replace('ROLE_','')).join(', ')}`;
  state.envioReal=s.modoEmail==='REAL';
  $('modo-email').textContent=state.envioReal?'Envio manual':'Envio não configurado';
  $('sair').hidden=s.usuario==='DESENVOLVIMENTO_LOCAL';
  if(s.usuario==='DESENVOLVIMENTO_LOCAL'){
    const acesso=element('a','Acesso local');acesso.href=urlAplicacao('/login');acesso.title='Desenvolvimento isolado, sem login individual';$('sair').after(acesso);
  }
  $('sair').addEventListener('click',async()=>{
    const headers=new Headers();if(s.csrf)headers.set(s.csrfHeader,s.csrf);
    try{const resposta=await fetch(urlAplicacao('/logout'),{method:'POST',credentials:'same-origin',headers,signal:AbortSignal.timeout(10000)});if(!resposta.ok)throw new Error('Não foi possível encerrar a sessão. Tente sair novamente.');location.assign(urlAplicacao('/login?logout'));}catch(error){avisar(error.message,true);}
  });
  if(!podeOperar())document.querySelectorAll('#form-pacote input,#form-pacote button,#form-importacao input,#form-importacao select,#form-importacao button,#form-email input').forEach(n=>{n.disabled=true;});
}
document.addEventListener('sessao-expirada',()=>{
  operacoesLiberadas=false;state.papeis=[];
  document.documentElement.dataset.operador='false';document.documentElement.dataset.administrador='false';
  document.body.dataset.sessaoEncerrada='true';
  document.querySelectorAll('dialog[open]').forEach(dialog=>dialog.close());
  document.querySelectorAll('main form input,main form select,main form button,#confirmar-zip').forEach(n=>{n.disabled=true;});
  if($('sessao-expirada'))return;
  const aviso=element('aside',null,'card');aviso.id='sessao-expirada';aviso.setAttribute('role','alert');
  aviso.append(element('p','Sessão encerrada. O rascunho desta aba foi preservado. Entre novamente em outra aba e recarregue os dados antes de confirmar ações.'));
  const link=element('a','Entrar novamente');link.href=urlAplicacao('/login?expirada');link.target='_blank';link.rel='noopener';aviso.append(link);document.body.prepend(aviso);
});
// Uma página antiga não recupera acesso por voltar/avançar ou por manter outra aba aberta.
window.addEventListener('pagehide',()=>{document.body.style.visibility='hidden';});
async function revalidarPagina(){
  try{await api('/api/sessao');}catch(error){if(error.status!==401)document.dispatchEvent(new Event('sessao-expirada'));}
  finally{document.body.style.visibility='';}
}
window.addEventListener('pageshow',event=>{if(event.persisted)revalidarPagina();});
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&operacoesLiberadas)revalidarPagina();});
