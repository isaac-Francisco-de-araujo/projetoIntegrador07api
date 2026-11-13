import { $,api } from './painel-base.js';
import { renderizarDiagnostico,apresentarErro } from './painel-diagnostico.js';
let pagina=0,versao=0;
function contadores(r){
  const total=r.falhas+r.atencao,contador=$('contador-pendencias');
  contador.textContent=total>99?'99+':String(total);contador.hidden=total===0;contador.classList.remove('unavailable');
  contador.setAttribute('aria-label',`${total} operação(ões) precisam de ação`);contador.title=contador.getAttribute('aria-label');
  $('resumo-central').textContent=`${r.falhas} falha(s) · ${r.atencao} pendência(s)/resultado(s) incerto(s) · ${r.emProcessamento} em processamento · ${r.resolvidos} resolvido(s).`;
}
async function carregar(){
  const atual=++versao,alvo=$('lista-processamentos');$('atualizar-processamentos').disabled=true;alvo.setAttribute('aria-busy','true');
  try{
    const params=new URLSearchParams({pagina:String(pagina),tamanho:'20',filtro:$('filtro-processamentos').value});
    const r=await api('/api/pendencias?'+params);if(atual!==versao)return;
    contadores(r.resumo);alvo.replaceChildren();for(const d of r.itens)renderizarDiagnostico(alvo,d,{substituir:false,compacto:true});
    if(!r.itens.length)alvo.textContent='Nenhuma operação neste filtro. Uma consulta sem resultados não é erro.';
    $('pagina-processamentos').textContent=`Página ${pagina+1} · ${r.total} operação(ões)`;
    $('processamentos-anterior').disabled=pagina===0;$('processamentos-proxima').disabled=(pagina+1)*r.tamanho>=r.total;
  }catch(error){if(atual===versao){apresentarErro(alvo,error,{tentarNovamente:carregar});$('resumo-central').textContent='Central indisponível. Nenhum dado antigo é apresentado como atualizado.';const contador=$('contador-pendencias');contador.hidden=false;contador.textContent='!';contador.classList.add('unavailable');contador.setAttribute('aria-label','Consulta de pendências indisponível');contador.title='Consulta de pendências indisponível';$('pagina-processamentos').textContent='Consulta indisponível';$('processamentos-anterior').disabled=true;$('processamentos-proxima').disabled=true;}}
  finally{if(atual===versao){$('atualizar-processamentos').disabled=false;alvo.setAttribute('aria-busy','false');}}
}
export function iniciarHistorico(){
  $('atualizar-processamentos').addEventListener('click',carregar);
  $('filtro-processamentos').addEventListener('change',()=>{pagina=0;carregar();});
  $('processamentos-anterior').addEventListener('click',()=>{if(pagina>0){pagina--;carregar();}});
  $('processamentos-proxima').addEventListener('click',()=>{pagina++;carregar();});
  for(const nome of ['processamento-atualizado','pendencias-atualizadas'])document.addEventListener(nome,()=>{pagina=0;carregar();});return carregar();
}
