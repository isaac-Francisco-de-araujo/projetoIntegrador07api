import { $,api,element,podeAdministrar } from './painel-base.js';
let sequencia=0;
async function carregar(){
  const seq=++sequencia;$('saude-estado').textContent='Verificando…';$('saude-itens').setAttribute('aria-busy','true');
  try{const r=await api('/api/saude');if(seq!==sequencia)return;$('saude-itens').replaceChildren();
    const estados={FUNCIONANDO:'Disponível',INDISPONIVEL:'Indisponível',NAO_CONFIGURADO:'Não configurado',NAO_VERIFICADO:'Não verificado',ATENCAO:'Requer atenção'};
    const prioridade={INDISPONIVEL:0,NAO_CONFIGURADO:1,ATENCAO:2,NAO_VERIFICADO:3,FUNCIONANDO:4};
    for(const v of [...r.verificacoes].sort((a,b)=>(prioridade[a.estado]??3)-(prioridade[b.estado]??3))){const estilo=v.estado==='FUNCIONANDO'?'sucesso':v.estado==='INDISPONIVEL'?'erro':'atencao',icone=estilo==='sucesso'?'✓':estilo==='erro'?'⛔':'⚠';const card=element('article',null,`diag-card diag-${estilo}`);card.append(element('h3',v.componente),element('span',`${icone} ${estados[v.estado]||'Requer atenção'}`,'health-status'),element('p',v.mensagem));
      if(v.estado!=='FUNCIONANDO')card.append(element('p',v.orientacao,'health-next'));
      const detalhes=element('dl',null,'record-fields');for(const [nome,valor] of Object.entries(v.detalhes)){detalhes.append(element('dt',nome),element('dd',typeof valor==='object'?JSON.stringify(valor):String(valor)));}const recolhido=element('details');recolhido.append(element('summary','Ver detalhes'),...(v.estado==='FUNCIONANDO'?[element('p',v.orientacao)]:[]),element('p',`Verificado em ${new Date(v.verificadoEm).toLocaleString('pt-BR')}`),detalhes);card.append(recolhido);$('saude-itens').append(card);
    }
    $('saude-estado').textContent=`Última verificação: ${new Date(r.verificadoEm).toLocaleString('pt-BR')}. Consulta local, sem envio ou testes externos.`;
  }catch(e){if(seq===sequencia){$('saude-itens').replaceChildren();$('saude-estado').textContent=`⛔ Backend indisponível para esta consulta. ${e.message} Nenhum resultado antigo é apresentado como atual.`;}}finally{if(seq===sequencia)$('saude-itens').setAttribute('aria-busy','false');}
}
export function iniciarSaude(){$('saude-configuracoes').hidden=!podeAdministrar();$('saude-atualizar').addEventListener('click',carregar);window.setInterval(()=>{if(!document.hidden&&location.hash==='#saude')carregar();},60000);return carregar();}
