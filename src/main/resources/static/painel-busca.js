import { $,api,element,state } from './painel-base.js';
import { navegarPara } from './painel-navegacao.js';
import { urlAplicacao } from './painel-rotas.js';
let pagina=0,sequencia=0,timer;
const campos=['entidade','busca','numero','cliente','pacote','tipo','estado','inicio','fim','ordem','direcao'];
async function carregar(){
  const seq=++sequencia,params=new URLSearchParams({pagina,tamanho:20});for(const c of campos){const v=$('busca-'+c).value;if(v)params.set(c,v);}
  const alvo=$('busca-resultados');alvo.setAttribute('aria-busy','true');$('busca-resumo').textContent='Consultando…';
  try{const r=await api('/api/busca?'+params);if(seq!==sequencia)return;alvo.replaceChildren();
    const tipos={VIAGEM:'Viagem',CLIENTE:'Cliente',ARQUIVO:'Documento',PACOTE:'Pacote'};
    for(const item of r.itens){const row=element('article',null,'card');row.append(element('strong',`${tipos[r.entidade]} · ${item.nome}`),element('p',[item.clienteNome,item.cnpj,item.tipo,item.status,item.estado,item.arquivadoEm?'Arquivado':null].filter(Boolean).join(' · ')));
      const detalhes=element('button','Ver detalhes');detalhes.type='button';detalhes.addEventListener('click',async()=>{
        const dialog=$('busca-detalhe');$('busca-detalhe-conteudo').textContent='Carregando detalhes…';dialog.showModal();
        try{let d=item;if(r.entidade==='CLIENTE')d=await api(`/api/clientes/${item.id}`);if(r.entidade==='VIAGEM')d=await api(`/api/viagens/${item.id}`);if(r.entidade==='PACOTE')d=await api(`/api/pacotes/${item.id}/conferencia-exclusao`);
          const lista=element('dl',null,'record-fields');for(const [k,v] of Object.entries(d)){if(v===null||typeof v==='object')continue;lista.append(element('dt',k),element('dd',String(v)));}$('busca-detalhe-conteudo').replaceChildren(lista);
          if(r.entidade==='ARQUIVO'){const a=element('a','Baixar original');a.href=urlAplicacao(`/api/${item.origem==='ANEXO'?'anexos':'documentos'}/${item.id}/arquivo`);a.download='';$('busca-detalhe-conteudo').append(a);}
          const viagemId=r.entidade==='VIAGEM'?item.id:item.viagemId;if(viagemId){const abrir=element('button','Abrir documentos da viagem vinculada');abrir.type='button';abrir.addEventListener('click',async()=>{const id=String(viagemId);if(!state.viagens.some(v=>String(v.id)===id)){const v=await api(`/api/viagens/${id}`);state.viagens.push(v);$('viagem-selecionada').add(new Option(`CT-e ${v.numero}`,id));}$('viagem-selecionada').value=id;$('viagem-selecionada').dispatchEvent(new Event('change'));dialog.close();navegarPara('documentos');});$('busca-detalhe-conteudo').append(abrir);}
        }catch(e){$('busca-detalhe-conteudo').textContent=e.message;}
      });row.append(detalhes);alvo.append(row);
    }
    if(!r.itens.length)alvo.textContent='Nenhum resultado para estes filtros.';$('busca-resumo').textContent=`${r.total} resultado(s) · Página ${pagina+1} · Data: ${r.dataUtilizada}`;$('busca-anterior').disabled=pagina===0;$('busca-proxima').disabled=(pagina+1)*r.tamanho>=r.total;
  }catch(e){if(seq===sequencia){alvo.replaceChildren();$('busca-resumo').textContent=`Falha de consulta: ${e.message} Revise os filtros ou tente novamente.`;$('busca-anterior').disabled=true;$('busca-proxima').disabled=true;}}finally{if(seq===sequencia)alvo.setAttribute('aria-busy','false');}
}
export function iniciarBusca(){
  for(const c of campos)$('busca-'+c).addEventListener(['busca','numero','cliente','pacote'].includes(c)?'input':'change',()=>{pagina=0;++sequencia;clearTimeout(timer);timer=setTimeout(carregar,300);});
  $('busca-atualizar').addEventListener('click',carregar);$('busca-anterior').addEventListener('click',()=>{pagina--;carregar();});$('busca-proxima').addEventListener('click',()=>{pagina++;carregar();});$('busca-detalhe-fechar').addEventListener('click',()=>$('busca-detalhe').close());return carregar();
}
