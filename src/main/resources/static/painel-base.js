'use strict';
import { apresentarErro,referenciaLocal } from './painel-diagnostico.js';
export const $ = id => document.getElementById(id);
export const state = { clientes: [], viagens: [], documentos: [], daViagem: [], viagem: '', selecionados: new Set(), anexosSelecionados: new Set(), anexos: [], carregando: false, previaOcupada: false, papeis: [] };
export const podeOperar = () => state.papeis.some(p => ['ROLE_ADMIN','ROLE_OPERADOR'].includes(p));
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
  const rota=path.split('?')[0],metodo=pedido.method||'GET';
  if(!['GET','HEAD','OPTIONS'].includes(pedido.method||'GET')){const s=await carregarSessao();if(s.csrf)headers.set(s.csrfHeader,s.csrf);}
  let response;
  try{response=await fetch(path,{credentials:'same-origin',...pedido,headers,signal:pedido.signal?AbortSignal.any([pedido.signal,AbortSignal.timeout(timeoutMs)]):AbortSignal.timeout(timeoutMs)});}
  catch(error){const timeout=['AbortError','TimeoutError'].includes(error.name);const falha=new Error(timeout?'A resposta demorou além do esperado. O resultado ainda não foi confirmado. Consulte o histórico antes de repetir.':'Não foi possível conectar à aplicação. O resultado ainda não foi confirmado. Consulte o histórico antes de repetir.');Object.assign(falha,{incerto:true,codigo:timeout?'RESPOSTA_NAO_CONFIRMADA':'CONEXAO_INDISPONIVEL',referencia:referenciaLocal(),rota,metodo});throw falha;}
  aoResponder?.(response);
  if(response.status===204)return null;
  if(response.ok&&resposta==='bytes')return new Uint8Array(await response.arrayBuffer());
  let body=null;try{body=await response.json();}catch{}
  if(!response.ok){
    const fields=Array.isArray(body?.campos)?body.campos.map(i=>`${i.campo}: ${i.mensagem}`).join('; '):'';
    const error=new Error([body?.mensagem||`A solicitação falhou (HTTP ${response.status}).`,fields,response.headers.get('X-Request-Id')?`Referência: ${response.headers.get('X-Request-Id')}`:''].filter(Boolean).join(' '));
    error.diagnostico=body?.diagnostico;error.diagnosticoOperacao=body?.diagnosticoOperacao;error.correlacaoId=response.headers.get('X-Request-Id');error.referencia=error.correlacaoId||referenciaLocal();error.incerto=body?.diagnosticoOperacao?.gravidade==='ATENCAO';error.status=response.status;throw error;
  }
  if(body===null||typeof body!=='object'){const erro=new Error('Resposta inválida do servidor. O resultado não foi confirmado; consulte o histórico antes de repetir.');Object.assign(erro,{incerto:true,codigo:'RESPOSTA_INVALIDA',correlacaoId:response.headers.get('X-Request-Id'),rota,metodo});erro.referencia=erro.correlacaoId||referenciaLocal();throw erro;}
  if(metodo==='GET')document.dispatchEvent(new CustomEvent('consulta-confirmada',{detail:{rota}}));
  return body;
}
export async function iniciarSessao(){
  const s=await carregarSessao();$('sessao-usuario').textContent=`${s.usuario==='DESENVOLVIMENTO_LOCAL'?'Sessão local':s.usuario} · ${s.papeis.map(p=>p.replace('ROLE_','')).join(', ')}`;
  document.documentElement.dataset.operador=String(podeOperar());document.documentElement.dataset.administrador=String(podeAdministrar());
  $('sessao-usuario').title=`${s.usuario} · ${s.papeis.map(p=>p.replace('ROLE_','')).join(', ')}`;
  $('modo-email').textContent=s.modoEmail==='REAL'?'04 / ENVIO REAL MANUAL':'04 / SIMULAÇÃO MANUAL';
  $('sair').hidden=s.usuario==='DESENVOLVIMENTO_LOCAL';
  $('sair').addEventListener('click',async()=>{
    const headers=new Headers();if(s.csrf)headers.set(s.csrfHeader,s.csrf);
    await fetch('/logout',{method:'POST',credentials:'same-origin',headers,signal:AbortSignal.timeout(10000)});location.assign('/login');
  });
  if(!podeOperar())document.querySelectorAll('#form-pacote input,#form-pacote button,#form-importacao input,#form-importacao select,#form-importacao button,#form-email input').forEach(n=>{n.disabled=true;});
}
