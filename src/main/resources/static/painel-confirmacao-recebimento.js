import {api,element,podeOperar} from './painel-base.js';
import {apresentarErro} from './painel-diagnostico.js';
export const pedidoConfirmacao=body=>({method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body),timeoutMs:45000});
const chave=id=>'rotabrasil-confirmacao-motorista-'+id;
function guardar(id,conferencia){try{if(conferencia)sessionStorage.setItem(chave(id),conferencia);else sessionStorage.removeItem(chave(id));}catch{}}
function pendente(id){try{return sessionStorage.getItem(chave(id));}catch{return null;}}
document.addEventListener('configuracoes-atualizadas',()=>{for(const bloco of document.querySelectorAll('.confirmacao-motorista'))bloco.dispatchEvent(new Event('invalidar-conferencia-motorista'));});
export function mostrarConferenciaMensagem(alvo,c,confirmar){
  const s=c.snapshot,bloco=element('section',null,'diag-card diag-atencao');bloco.dataset.conferenciaConfirmacao=c.id;
  bloco.append(element('h3',`${c.anteriorId?'Reenvio deliberado · ':''}Conferência da mensagem · ${s.modo}`),element('p',`Remetente: ${s.remetente}`),element('p',`Destinatário: ${s.destinatario}`),element('p',`Assunto: ${s.assunto}`),element('p',`Recebimento: ${s.recebimentoId||'teste sintético'} · Pacote: ${s.pacote} · Situação conhecida: ${s.estadoProcessamento}`),element('pre',s.mensagem,'mensagem-confirmacao'));
  bloco.append(element('p',`Conferência válida até ${new Date(c.expiraEm).toLocaleString('pt-BR')}. Sem anexos fiscais. ${c.anteriorId?'Tentativa anterior: '+c.anteriorId+'. Reenvio não comprova o resultado da tentativa anterior.':''}`));
  const label=element('label'),check=element('input');check.type='checkbox';label.append(check,element('span',s.modo==='REAL'?'Conferi remetente, destinatário e mensagem. Autorizo este envio REAL.':'Conferi os dados e autorizo esta SIMULAÇÃO, sem enviar e-mail real.'));
  const botao=element('button',s.modo==='REAL'?'Enviar confirmação REAL':'Confirmar simulação','button primary');botao.type='button';botao.disabled=true;
  check.addEventListener('change',()=>{botao.disabled=!check.checked;});botao.addEventListener('click',async()=>{if(botao.disabled)return;botao.disabled=true;check.disabled=true;try{await confirmar(c);}catch(error){apresentarErro(bloco,error);}});
  bloco.append(label,botao);alvo.replaceChildren(bloco);return bloco;
}
export function montarConfirmacaoMotorista(r,alvo,atualizar){
  const bloco=element('section',null,'confirmacao-motorista'),modo=r.canalConfirmacao.modo;
  const tentativas=r.notificacoes.filter(n=>n.evento==='CONFIRMACAO_MOTORISTA'),ultima=tentativas.at(-1);
  bloco.append(element('h3','Confirmação de recebimento ao motorista'),element('p',`Conta remetente atual: ${r.remetenteAtual||'não configurada'} · Modo: ${modo}`),element('p','Envio manual de uma confirmação por recebimento reconhecido pelo SHA-256 do ZIP. Eventos internos não geram e-mails reais automáticos. A confirmação é separada do envio de documentos ao cliente.'));
  bloco.append(element('p',`Permissão desta sessão: ${r.canalConfirmacao.permissaoSessao}. ${modo==='REAL'&&!r.canalConfirmacao.sessaoPodeEnviarReal?'Envio real indisponível para esta sessão.':''}`));
  if(modo==='REAL'&&!r.canalConfirmacao.realDisponivel){const aviso=element('div',null,'diag-card diag-erro');aviso.append(element('h3','⛔ Envio real bloqueado'));for(const p of r.canalConfirmacao.preRequisitos)aviso.append(element('p',p));bloco.append(aviso);}
  for(const n of tentativas){
    const ok=['SIMULADO','ACEITO_SMTP'].includes(n.situacao),artigo=element('article',null,`diag-card diag-${ok?'sucesso':n.situacao==='FALHA'?'erro':'atencao'}`);artigo.dataset.tentativaMotorista=n.id;
    artigo.append(element('h4',`${ok?'✓':n.situacao==='FALHA'?'⛔':'⚠'} ${n.situacao==='ACEITO_SMTP'?'Aceita pelo servidor SMTP — entrega/leitura não comprovadas':n.situacao==='SIMULADO'?'SIMULAÇÃO concluída — nenhum e-mail real enviado':n.situacao==='FALHA'?'Falha na confirmação':'Resultado desconhecido — não repetir automaticamente'}`),element('p',`De ${n.remetente} para ${n.destinatario} · ${new Date(n.criadaEm).toLocaleString('pt-BR')}`),element('p',n.motivo));
    const d=element('details');d.append(element('summary','Ver mensagem e detalhes da tentativa'),element('p',`Tentativa: ${n.id} · Modo: ${n.modo} · Correlação: ${n.correlacao||'não disponível'} · Anterior: ${n.anteriorId||'primeira tentativa'}`),element('p',`Assunto: ${n.assunto}`),element('pre',n.mensagem,'mensagem-confirmacao'));artigo.append(d);bloco.append(artigo);
  }
  const form=element('form'),label=element('label','E-mail do motorista para confirmação'),input=element('input');input.type='email';input.required=true;input.maxLength=255;input.autocomplete='off';input.value=r.destinatario||'';label.append(input);
  const preparar=element('button',ultima?'Conferir reenvio ao motorista':'Conferir confirmação ao motorista','button secondary');preparar.type='submit';
  const previa=element('div');let anterior=null,versao=0;const permitido=podeOperar()&&Boolean(r.remetenteAtual)&&(modo==='SIMULADO'||r.canalConfirmacao.realDisponivel&&r.canalConfirmacao.sessaoPodeEnviarReal);
  input.disabled=!permitido||Boolean(ultima);preparar.disabled=input.disabled;
  if(ultima){const reenviar=element('button','Preparar reenvio deliberado ao motorista','button secondary');reenviar.type='button';reenviar.disabled=!permitido;reenviar.addEventListener('click',()=>{anterior=ultima.id;input.disabled=false;preparar.disabled=false;previa.replaceChildren(element('p',ultima.situacao==='DESCONHECIDO'?'A tentativa anterior pode ter sido aceita. Revise seu resultado antes de autorizar outra mensagem.':'Nova conferência e confirmação serão exigidas; a tentativa anterior será preservada.'));input.focus();});bloco.append(reenviar);}
  input.addEventListener('input',()=>{++versao;previa.replaceChildren(element('p','Destinatário alterado. Confira novamente antes de confirmar.'));});
  bloco.addEventListener('invalidar-conferencia-motorista',()=>{++versao;previa.replaceChildren(element('p','Configuração alterada. Confira a mensagem novamente.'));});
  form.addEventListener('submit',async e=>{e.preventDefault();if(preparar.disabled||!input.reportValidity())return;const v=++versao;preparar.disabled=true;input.disabled=true;
    try{const c=await api(`/api/recebimentos/${r.id}/conferencias-confirmacao`,pedidoConfirmacao({destinatario:input.value,anteriorId:anterior}));if(v!==versao||!alvo.contains(form))return;
      mostrarConferenciaMensagem(previa,c,async conf=>{guardar(r.id,conf.id);try{await api(`/api/recebimentos/${r.id}/confirmacoes`,pedidoConfirmacao({conferenciaId:conf.id,confirmado:true}));guardar(r.id,null);await atualizar();document.dispatchEvent(new Event('pendencias-atualizadas'));}catch(error){apresentarErro(previa,error);recuperar.hidden=false;}});
    }catch(error){apresentarErro(previa,error);}finally{preparar.disabled=!permitido;input.disabled=!permitido;}
  });
  const recuperar=element('button','Consultar/recuperar a mesma confirmação ao motorista','button secondary');recuperar.type='button';recuperar.hidden=!pendente(r.id);recuperar.disabled=!podeOperar();
  recuperar.addEventListener('click',async()=>{const id=pendente(r.id);if(!id)return;recuperar.disabled=true;try{await api(`/api/recebimentos/${r.id}/confirmacoes`,pedidoConfirmacao({conferenciaId:id,confirmado:true}));guardar(r.id,null);await atualizar();document.dispatchEvent(new Event('pendencias-atualizadas'));}catch(error){apresentarErro(previa,error);}finally{recuperar.disabled=false;}});
  form.append(label,preparar);bloco.append(form,previa,recuperar);alvo.append(bloco);
}
