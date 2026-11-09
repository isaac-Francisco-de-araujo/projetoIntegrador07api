import {api,element,podeOperar} from './painel-base.js';
import { renderizarDiagnostico,apresentarErro,referenciaLocal } from './painel-diagnostico.js';
import { montarConfirmacaoMotorista } from './painel-confirmacao-recebimento.js';
const json=body=>({method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body),timeoutMs:120000});
const consultas=new WeakMap();
export function detalhesPlanoExportacao(e){
  const bloco=element('div');bloco.append(element('p',`Destino: raiz autorizada do backend/${e.subpasta?e.subpasta+'/':''}pacote-${e.pacoteId}`));
  const detalhes=element('details');detalhes.append(element('summary','Arquivos do plano de exportação'));
  const lista=element('ul');for(const item of e.arquivos||[])lista.append(element('li',`${item.localizacao} · ${item.situacao} · ${item.tamanho} bytes`));detalhes.append(lista);bloco.append(detalhes);return bloco;
}
async function renderizar(exportacoes,alvo,pacoteId,token){
  alvo.replaceChildren();
  for(const e of exportacoes.slice(0,10)){
    const d=await api(`/api/diagnosticos/EXPORTACAO/${e.id}`);if(consultas.get(alvo)!==token)return;
    const destino=element('div');alvo.append(destino);const artigo=renderizarDiagnostico(destino,d);artigo.querySelector('details').append(element('p',e.mensagem),detalhesPlanoExportacao(e));
  }
  if(exportacoes.length>10)alvo.append(element('p','Mostrando as dez exportações mais recentes deste pacote. Consulte a Central de Pendências para navegação paginada.'));
  if(!exportacoes.length)alvo.append(element('p','Importação concluída; nenhuma exportação registrada. A cópia automática pode estar desativada. Para copiar este pacote, use Exportar arquivos.'));
}
export async function mostrarExportacoes(pacoteId,alvo){
  const token=Symbol();consultas.set(alvo,token);
  alvo.textContent='Consultando o resultado da exportação…';
  try{const lista=await api(`/api/pacotes/${pacoteId}/exportacoes`);if(consultas.get(alvo)===token)await renderizar(lista,alvo,pacoteId,token);}
  catch(error){if(consultas.get(alvo)===token)apresentarErro(alvo,error);}
}
export function limparResultadoExportacao(alvo){consultas.set(alvo,Symbol());alvo.replaceChildren();}
export function botaoExportar(pacoteId,alvo){
  const button=element('button','Exportar arquivos','button secondary');button.type='button';button.disabled=!podeOperar();
  button.addEventListener('click',async()=>{
    button.disabled=true;const token=Symbol();consultas.set(alvo,token);alvo.textContent='Exportação solicitada para o destino salvo no backend; aguardando o resultado confirmado…';
    try{await api(`/api/pacotes/${pacoteId}/exportacoes`,json({confirmado:true,requisicaoId:referenciaLocal()}));if(consultas.get(alvo)===token)await mostrarExportacoes(pacoteId,alvo);document.dispatchEvent(new Event('pendencias-atualizadas'));}
    catch(error){if(consultas.get(alvo)===token)apresentarErro(alvo,error);}
    finally{button.disabled=!podeOperar();}
  });return button;
}
export async function mostrarRecebimento(id,alvo){
  if(alvo.id==='avisos-recebimento')document.getElementById('recebimento-opcional').hidden=!id;
  const token=Symbol();consultas.set(alvo,token);alvo.replaceChildren();if(!id)return;
  try{
     const r=await api(`/api/recebimentos/${id}`);if(consultas.get(alvo)!==token)return;alvo.append(element('h3','Recebimento e histórico da operação'));
     alvo.append(element('p',`Remetente inicial da operação: ${r.remetente||'não informado'} · Destinatário atual deste recebimento: ${r.destinatario||'não informado'}`));
     montarConfirmacaoMotorista(r,alvo,()=>mostrarRecebimento(id,alvo));
     const eventos=element('details');eventos.append(element('summary','Eventos internos — testes sem envio'));alvo.append(eventos);
     for(const n of r.notificacoes.filter(n=>n.evento!=='CONFIRMACAO_MOTORISTA')){
       const artigo=element('article');artigo.append(element('strong',`${n.evento} · ${n.situacao}`),element('p',`Snapshot do evento: ${n.remetente||'remetente não informado'} → ${n.destinatario||'destinatário não informado'} · ${n.modo}`),element('p',n.mensagem));
      eventos.append(artigo);
    }
    return r;
  }catch(error){if(consultas.get(alvo)===token)apresentarErro(alvo,error);}
}
