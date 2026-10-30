import { $, element, api, avisar, vazio, podeOperar } from './painel-base.js';
import { invalidarPrevia } from './painel-email.js';
import { botaoExportar,mostrarExportacoes,mostrarRecebimento } from './painel-exportacoes.js';

export async function iniciarGerenciamentoArquivos({ atualizar }) {
  let page = 0, version = 0, detailVersion = 0, loading = false, busy = false, stale = false;
  let snapshot = null, selectedId, origin, detailRequest;
  const dialog = $('excluir-pacote');
  function controles() {
    $('fechar-excluir-pacote').disabled = busy;
    $('reconferir-excluir-pacote').disabled = busy || loading;
    $('confirmar-excluir-pacote').disabled = busy || loading || stale;
    const canDelete = podeOperar() && snapshot && !snapshot.pacote.arquivado;
    $('label-confirmar-excluir-pacote').hidden = !canDelete;
    $('executar-excluir-pacote').hidden = !canDelete;
    $('executar-excluir-pacote').disabled = !canDelete || busy || loading || stale || !$('confirmar-excluir-pacote').checked;
    dialog.querySelectorAll('[data-restaurar-arquivo]').forEach(button => { button.disabled = busy || loading || stale; });
  }
  async function carregar() {
    const token = ++version; const target = $('lista-pacotes-importados');
    target.setAttribute('aria-busy','true'); vazio(target,4,'Carregando ZIPs importados…'); $('atualizar-pacotes').disabled = true;
    try {
      const params = new URLSearchParams({pagina:String(page),tamanho:'20',arquivados:String($('mostrar-pacotes-arquivados').checked)});
      const items = await api('/api/pacotes?'+params);
      if (token !== version) return;
      const rows = items.map(pacote => {
        const row = element('tr'); row.dataset.pacoteId = String(pacote.id);
        const name = element('td'); name.append(element('strong',pacote.nomeArquivo),element('small',`ZIP #${pacote.id}`));
        const date = new Intl.DateTimeFormat('pt-BR',{dateStyle:'short',timeStyle:'short'}).format(new Date(pacote.criadoEm));
        const action = element('td'); const button = element('button',pacote.arquivado || !podeOperar()?'Ver arquivos / restaurar':'Excluir ZIP',pacote.arquivado || !podeOperar()?'row-action':'delete-action');
        button.type='button';button.setAttribute('aria-label',`${pacote.arquivado || !podeOperar()?'Ver arquivos do':'Excluir'} ZIP ${pacote.nomeArquivo}`);
         button.addEventListener('click',()=>abrir(pacote.id,button));action.append(button);
         const resultado=element('div',null,'settings-results export-result');
         const historico=element('button','Ver exportações','button secondary');historico.type='button';historico.addEventListener('click',()=>mostrarExportacoes(pacote.id,resultado));
         const avisos=element('button','Ver avisos','button secondary');avisos.type='button';avisos.addEventListener('click',async()=>{try{const r=await api('/api/recebimentos?'+new URLSearchParams({sha256:pacote.sha256}));await mostrarRecebimento(r.id,resultado);}catch(error){resultado.textContent=error.message;}});
         action.append(botaoExportar(pacote.id,resultado),historico,avisos,resultado);
        row.append(name,element('td',date),element('td',`Documentos: ${pacote.documentosAtivos}/${pacote.documentos} · PDFs: ${pacote.anexosAtivos}/${pacote.anexos}`),action);return row;
      });
      if(rows.length)target.replaceChildren(...rows);else vazio(target,4,$('mostrar-pacotes-arquivados').checked?'Nenhum ZIP com todos os arquivos arquivados nesta consulta.':'Nenhum ZIP com arquivos ativos nesta consulta.');
      $('estado-pacotes-importados').textContent='A lista considera o estado atual dos arquivos vinculados. O histórico das importações permanece disponível.';
      $('pagina-pacotes-importados').textContent=`Página ${page+1}`;$('pacotes-anterior').disabled=page===0;$('pacotes-proxima').disabled=items.length<20;
    } catch(error) {
      if(token!==version)return;
      vazio(target,4,'Não foi possível consultar os ZIPs. Use Atualizar ZIPs para tentar novamente.');$('estado-pacotes-importados').textContent=error.message;
      $('pacotes-anterior').disabled=true;$('pacotes-proxima').disabled=true;
    } finally { if(token===version){target.setAttribute('aria-busy','false');$('atualizar-pacotes').disabled=false;} }
  }
  async function restaurar(tipo,id) {
    if(busy||stale||!podeOperar())return;
    busy=true;controles();$('estado-excluir-pacote').textContent='Restaurando o arquivo selecionado…';
    try {
      await api(`/api/${tipo==='documento'?'documentos':'anexos'}/${id}/restaurar`,{method:'POST'});
      avisar('Arquivo restaurado com a mesma identidade e os mesmos bytes. Os vínculos compartilhados continuam preservados.');
      try{await atualizar();}catch{avisar('Restauração concluída. Atualize os dados para conferir as consultas.',true);}
      await conferir();
    } catch(error) {stale=true;$('estado-excluir-pacote').textContent=error.message+' Atualize a conferência antes de qualquer nova ação.';}
    finally{busy=false;controles();}
  }
  function itemArquivo(item,tipo) {
    const article=element('article',null,'package-impact-item');
    article.append(element('strong',`${tipo==='documento'?item.tipo:'PDF'} · ${item.nomeArquivo}`),element('span',item.arquivadoEm?'Já arquivado':'Ativo','status-pill'));
    const trips=tipo==='documento'?[item.viagem]:item.viagens;
    article.append(element('p',trips.map(viagem=>`Viagem ${viagem.numero} · ${viagem.cliente}`).join(' / ')));
    if(tipo==='documento'&&item.outrosPacoteIds.length)article.append(element('p',`Compartilhado também com os ZIPs ${item.outrosPacoteIds.map(id=>'#'+id).join(', ')}. Arquivar este registro também afeta esses pacotes.`,'shared-file-impact'));
    if(tipo==='anexo'&&item.viagens.length>1)article.append(element('p',`PDF compartilhado: a exclusão afeta as ${item.viagens.length} viagens listadas.`,'shared-file-impact'));
    if(item.arquivadoEm&&podeOperar()){
      const button=element('button',`Restaurar ${tipo==='documento'?item.tipo:'PDF'}`,'button secondary');button.type='button';button.dataset.restaurarArquivo=`${tipo}-${item.id}`;
      button.addEventListener('click',()=>restaurar(tipo,item.id));article.append(button);
    }
    return article;
  }
  async function conferir() {
    const token=++detailVersion;detailRequest?.abort();detailRequest=new AbortController();loading=true;stale=false;snapshot=null;
    $('confirmar-excluir-pacote').checked=false;$('impacto-excluir-pacote').replaceChildren();$('estado-excluir-pacote').textContent='Consultando os arquivos e vínculos atuais do ZIP…';controles();
    try{
      const result=await api(`/api/pacotes/${selectedId}/conferencia-exclusao`,{signal:detailRequest.signal});
      if(token!==detailVersion||!dialog.open)return;
      snapshot=result;$('excluir-pacote-titulo').textContent=result.pacote.nomeArquivo;
      $('impacto-excluir-pacote').replaceChildren(...result.documentos.map(item=>itemArquivo(item,'documento')),...result.anexos.map(item=>itemArquivo(item,'anexo')));
      $('estado-excluir-pacote').textContent=result.pacote.arquivado?'Todos os arquivos deste ZIP estão arquivados. Restaure somente os arquivos desejados.':`${result.pacote.documentosAtivos} documento(s) e ${result.pacote.anexosAtivos} PDF(s) ativos serão arquivados juntos. Os já arquivados permanecem como estão.`;
    }catch(error){if(token===detailVersion&&dialog.open){stale=true;$('estado-excluir-pacote').textContent=error.message;}}
    finally{if(token===detailVersion){loading=false;controles();}}
  }
  async function abrir(id,button){
    if(busy||document.querySelector('dialog[open]'))return;
    selectedId=id;origin=button;$('excluir-pacote-titulo').textContent='Arquivos do ZIP';dialog.showModal();await conferir();
  }
  $('fechar-excluir-pacote').addEventListener('click',()=>{if(!busy)dialog.close();});
  dialog.addEventListener('cancel',event=>{if(busy)event.preventDefault();});
  dialog.addEventListener('close',()=>{detailVersion++;detailRequest?.abort();snapshot=null;loading=false;stale=false;if(origin?.isConnected)origin.focus({preventScroll:true});else $('atualizar-pacotes').focus({preventScroll:true});});
  $('confirmar-excluir-pacote').addEventListener('change',controles);
  $('reconferir-excluir-pacote').addEventListener('click',()=>{if(!busy)conferir();});
  $('executar-excluir-pacote').addEventListener('click',async()=>{
    if(!snapshot||busy||loading||stale||!podeOperar()||!$('confirmar-excluir-pacote').checked)return;
    busy=true;controles();$('estado-excluir-pacote').textContent='Arquivando os arquivos conferidos em uma única operação…';
    invalidarPrevia();
    try{
      const params=new URLSearchParams({conferenciaSha256:snapshot.conferenciaSha256,confirmado:'true'});
      await api(`/api/pacotes/${selectedId}?${params}`,{method:'DELETE'});
      dialog.close();avisar('ZIP excluído da lista ativa por arquivamento dos arquivos associados. É possível restaurar cada XML/PDF na consulta de excluídos.');
      try{await atualizar();}catch{avisar('Arquivamento concluído. Atualize as consultas para conferir o resultado.',true);}
    }catch(error){stale=true;$('estado-excluir-pacote').textContent=error.message+' Consulte os dados e atualize a conferência antes de repetir.';}
    finally{busy=false;controles();}
  });
  $('atualizar-pacotes').addEventListener('click',carregar);
  $('mostrar-pacotes-arquivados').addEventListener('change',()=>{page=0;carregar();});
  $('pacotes-anterior').addEventListener('click',()=>{if(page>0){page--;carregar();}});
  $('pacotes-proxima').addEventListener('click',()=>{page++;carregar();});
  document.addEventListener('dados-atualizados',carregar);
  await carregar();
}
