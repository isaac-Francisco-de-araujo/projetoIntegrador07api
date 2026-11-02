import { $,api,element,podeOperar,podeAdministrar,carregarSessao } from './painel-base.js';
import { renderizarDiagnostico } from './painel-diagnostico.js';
let pagina=0,sequencia=0,ocupado=false,usuario='',responsaveis=[];
const filtros=['fila-escopo','fila-situacao','fila-responsavel','fila-inicio','fila-fim','fila-ordem'];
async function carregar(){
  const seq=++sequencia,alvo=$('fila-itens');alvo.setAttribute('aria-busy','true');$('fila-estado').textContent='Consultando fila…';
  const params=new URLSearchParams({pagina,tamanho:20,escopo:$('fila-escopo').value,situacao:$('fila-situacao').value,responsavel:$('fila-responsavel').value,inicio:$('fila-inicio').value,fim:$('fila-fim').value,ordem:$('fila-ordem').value});
  if(!params.get('inicio'))params.delete('inicio');if(!params.get('fim'))params.delete('fim');
  try{const r=await api('/api/fila?'+params);if(seq!==sequencia)return;alvo.replaceChildren();
    for(const item of r.itens){
      const row=element('article',null,'card');row.append(element('h3',`${item.nome} · ${item.origem}`),element('p',`Trabalho: ${item.trabalho} · Situação técnica: ${item.tecnico} · ${item.gravidade}`),element('p',`Responsável: ${item.responsavel||'Sem responsável'}${item.responsavelAtivo===false?' — INDISPONÍVEL (inativo ou sem papel de operação): intervenção administrativa necessária':''}`));
      const minutos=Math.max(0,Math.floor((Date.now()-Date.parse(item.atualizacao))/60000));row.append(element('p',`Entrada: ${new Date(item.entrada).toLocaleString('pt-BR')} · Última atualização: ${new Date(item.atualizacao).toLocaleString('pt-BR')} (há ${minutos} min)`));
      const detalhe=element('div'),botao=element('button','Detalhe e recuperação');botao.type='button';botao.addEventListener('click',async()=>{botao.disabled=true;try{const d=await api(`/api/diagnosticos/${item.origem}/${item.id}`);renderizarDiagnostico(detalhe,d);}catch(e){detalhe.textContent=e.message;}finally{botao.disabled=false;}});row.append(botao,detalhe);
      const acao=(rotulo,tipo,destino)=>{const b=element('button',rotulo);b.type='button';b.addEventListener('click',async()=>{if(ocupado)return;let novo=null;if(tipo==='TRANSFERIR'){novo=destino.value?Number(destino.value):null;if(!confirm(`Transferir ${item.nome} para ${destino.selectedOptions[0].textContent}?`))return;}ocupado=true;b.disabled=true;try{await api(`/api/fila/${item.origem}/${item.id}/responsabilidade`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({acao:tipo,versao:item.versao,destino:novo,confirmado:tipo==='TRANSFERIR'})});await carregar();}catch(e){$('fila-estado').textContent=e.status===409?`${e.message} Atualize a fila para revisar.`:e.message;}finally{ocupado=false;b.disabled=false;}});return b;};
      if(podeOperar()&&!item.responsavelId)row.append(acao('Assumir','ASSUMIR'));
      if(podeOperar()&&item.responsavel===usuario)row.append(acao('Liberar minha responsabilidade','LIBERAR'));
      if(podeAdministrar()){const select=element('select');select.setAttribute('aria-label',`Transferir ${item.nome} para`);select.add(new Option('Sem responsável',''));for(const u of responsaveis.filter(u=>u.ATIVO))select.add(new Option(u.USUARIO,u.ID));row.append(select,acao('Transferir (ADMIN)','TRANSFERIR',select));}
      const historico=element('button','Histórico de responsabilidade');historico.type='button';historico.addEventListener('click',async()=>{try{const h=await api(`/api/fila/${item.origem}/${item.id}/historico`);detalhe.replaceChildren(...h.map(e=>element('p',`${new Date(e.CRIADO_EM).toLocaleString('pt-BR')} · ${e.ATOR} · ${e.ACAO}: ${e.ANTERIOR||'sem responsável'} → ${e.NOVO||'sem responsável'}`)));if(!h.length)detalhe.textContent='Sem mudanças de responsabilidade registradas.';}catch(e){detalhe.textContent=e.message;}});row.append(historico);alvo.append(row);
    }
    if(!r.itens.length)alvo.textContent='Nenhum item para estes filtros.';
    $('fila-estado').textContent=`Página ${pagina+1} · ${r.total} itens · Verificado em ${new Date(r.verificadoEm).toLocaleString('pt-BR')}`;$('fila-anterior').disabled=pagina===0;$('fila-proxima').disabled=(pagina+1)*r.tamanho>=r.total;
  }catch(e){if(seq===sequencia){alvo.replaceChildren();$('fila-estado').textContent=`Fila indisponível: ${e.message} Atualize a consulta.`;$('fila-anterior').disabled=true;$('fila-proxima').disabled=true;}}
  finally{if(seq===sequencia)alvo.setAttribute('aria-busy','false');}
}
export async function iniciarFila(){
  usuario=(await carregarSessao()).usuario;
  try{responsaveis=await api('/api/fila/responsaveis');for(const u of responsaveis)$('fila-responsavel').add(new Option(`${u.USUARIO}${u.ATIVO?'':' (inativo)'}`,u.USUARIO));}catch(e){$('fila-estado').textContent=e.message;}
  filtros.forEach(id=>$(id).addEventListener('change',()=>{pagina=0;carregar();}));$('fila-atualizar').addEventListener('click',carregar);$('fila-anterior').addEventListener('click',()=>{pagina--;carregar();});$('fila-proxima').addEventListener('click',()=>{pagina++;carregar();});
  window.setInterval(()=>{if(!document.hidden&&location.hash==='#fila'&&!ocupado&&!$('fila-itens').contains(document.activeElement))carregar();},45000);
  return carregar();
}
