import { $, state, element, avisar, api, vazio, data, podeOperar } from './painel-base.js';
import { criarConsultas } from './painel-consultas.js';
import { confirmarAcao } from './painel-confirmacao.js';
import { navegarPara } from './painel-navegacao.js';
let consulta = 0;
let atualizacao = 0;
let consultas;
import { invalidarPrevia, renderizarTentativas, atualizarRecuperacao, limparEstadoEmail } from './painel-email.js';

export function atualizarSelecao() {
  $('gerar-previa').disabled = !state.envioReal || !podeOperar() || state.previaOcupada || state.carregando || !state.viagem || !(state.selecionados.size + state.anexosSelecionados.size);
  $('selecao-info').textContent = (state.selecionados.size + state.anexosSelecionados.size) ? `${state.selecionados.size + state.anexosSelecionados.size} documento(s) selecionado(s) para conferência.` : 'Escolha uma viagem e selecione documentos.';
}

function documentos() {
  const tbody = $('lista-documentos');
  const arquivados = $('mostrar-arquivados').checked;
  if (!state.daViagem.length) { vazio(tbody, 5, arquivados ? 'Nenhum documento arquivado nesta viagem.' : 'Esta viagem ainda não possui documentos ativos.'); return; }
  tbody.replaceChildren();
  for (const doc of state.daViagem) {
    const row = element('tr'); const choice = element('td');
    if (arquivados) choice.textContent = 'Arquivado';
    else {
      const checkbox = element('input');
      checkbox.type = 'checkbox'; checkbox.value = String(doc.id); checkbox.setAttribute('aria-label', `Selecionar ${doc.nomeArquivo}`);
      checkbox.addEventListener('change', () => {
        if (checkbox.checked) state.selecionados.add(doc.id); else state.selecionados.delete(doc.id);
        invalidarPrevia(); atualizarSelecao();
      });
      choice.append(checkbox);
    }
    const name = element('td', doc.nomeArquivo, 'filename');
    const referencia=element('details');referencia.append(element('summary','Referência'),element('small',`Documento #${doc.id}`));name.append(referencia);
    const action = element('td'); const button = element('button', arquivados ? 'Restaurar' : `Excluir ${doc.tipo}`, arquivados ? 'row-action' : 'delete-action');
    button.type = 'button'; button.setAttribute('aria-label', `${arquivados ? 'Restaurar' : 'Excluir'} ${doc.tipo} ${doc.nomeArquivo}`);
    if (arquivados) button.addEventListener('click', () => restaurarDocumento(doc, button));
    else button.addEventListener('click', () => confirmarExclusao('documento', doc.id, doc.nomeArquivo, button));
    button.hidden=!podeOperar();const baixar=element('a','Baixar original','row-action');baixar.href=`/api/documentos/${doc.id}/arquivo`;baixar.setAttribute('download','');
    action.append(baixar,button);
    const type = element('td'); type.append(element('span', doc.tipo, 'document-type'));
    row.append(choice, name, type, element('td', doc.chaveAcesso || 'Não se aplica / não informada', 'fiscal-key'), action);
    tbody.append(row);
  }
}

function historico(items) {
  const target = $('historico'); target.replaceChildren();
  if (!items.length) { target.textContent = 'Nenhum registro de envio para esta viagem.'; return; }
  for (const item of items) target.append(element('p', `#${item.id} · ${item.status || 'Sem status'} · ${item.dataEnvio ? data(item.dataEnvio) : 'Sem data informada'}`));
}

async function carregarDocumentos(id) {
  const version = ++consulta;
  limparEstadoEmail();
  $('tentativas').textContent = id ? 'Carregando tentativas…' : 'Escolha uma viagem para consultar.';
  atualizarRecuperacao(id);
  state.viagem = id; state.selecionados.clear(); state.anexosSelecionados.clear(); state.anexos = []; renderizarAnexos(); state.daViagem = []; invalidarPrevia();
  $('documentos-conteudo').hidden=!id;
  const viagem = state.viagens.find(item => String(item.id) === id);
  const cliente = viagem && state.clientes.find(item => item.id === viagem.clienteId);
  const context = viagem ? `Viagem ${viagem.numero} · ${cliente?.nome || `Cliente #${viagem.clienteId}`} · ${data(viagem.data)}` : 'Escolha uma viagem para conferir os documentos e anexos.';
  $('contexto-documentos').textContent = context; $('contexto-email').textContent = viagem ? context : 'Selecione uma viagem e os documentos para preparar a conferência.';
  state.carregando = Boolean(id); atualizarSelecao();
  if (!id) { vazio($('lista-documentos'), 5, 'Escolha uma viagem para consultar os documentos.'); $('historico').textContent = 'Escolha uma viagem para consultar.'; return; }
  vazio($('lista-documentos'), 5, 'Carregando documentos…'); $('historico').textContent = 'Carregando histórico…';
  try {
    const [docs, anexos] = await Promise.all([api(`/api/viagens/${encodeURIComponent(id)}/documentos${$('mostrar-arquivados').checked ? '/arquivados' : ''}`), api(`/api/viagens/${encodeURIComponent(id)}/anexos?arquivados=${$('mostrar-arquivados').checked}`)]);
    if (version !== consulta) return;
    state.daViagem = docs; state.anexos = anexos; documentos(); renderizarAnexos();
    carregarHistoricos(id,version);
  } catch (error) {
    if (version !== consulta) return;
    vazio($('lista-documentos'), 5, 'Não foi possível carregar os documentos. Use Atualizar dados para tentar novamente.');
    $('historico').textContent = 'Histórico indisponível.';
  } finally {
    if (version === consulta) { state.carregando = false; atualizarSelecao(); }
  }
}

export async function atualizar(viagemId = null) {
  const version = ++atualizacao;
  $('atualizar').disabled = true;
  $('indicadores').setAttribute('aria-busy','true'); $('indicadores').classList.remove('has-error');
  $('estado-indicadores').textContent = 'Carregando indicadores e consultas…';
  for (const id of ['total-clientes','total-viagens','total-documentos','total-pendencias']) $(id).textContent = '—';
  consultas.carregar(); state.carregando = true; invalidarPrevia(); atualizarSelecao();
  try {
    const [peoplePage, tripsPage, resumo] = await Promise.all([api('/api/busca?entidade=CLIENTE&tamanho=100&ordem=nome&direcao=ASC'), api('/api/busca?entidade=VIAGEM&tamanho=100&ordem=data&direcao=DESC'), api('/api/consulta/resumo')]);
    const people=peoplePage.itens,trips=tripsPage.itens;
    if (version !== atualizacao) return;
    state.clientes = people; state.viagens = trips; state.documentos = [];
    $('total-clientes').textContent = String(resumo.clientes); $('total-viagens').textContent = String(resumo.viagens); $('total-documentos').textContent = String(resumo.documentos);
    $('total-pendencias').textContent = String(resumo.processamentosPendentes);
    $('indicador-pendencias').classList.toggle('has-pending', resumo.processamentosPendentes > 0);
    $('resumo-pendencias').textContent=`Operações que precisam de ação: ${resumo.processamentosPendentes} · Tentativas com resultado desconhecido: ${resumo.tentativasDesconhecidas} · Em processamento: ${resumo.operacoesEmProcessamento??'não informado'}`;
    consultas.atualizar(); $('estado-indicadores').textContent = 'Indicadores atualizados com o banco conectado.';
    document.dispatchEvent(new Event('dados-atualizados'));
    const chosen = viagemId === null ? state.viagem : String(viagemId);
    if(chosen&&!trips.some(t=>String(t.id)===chosen))try{trips.push(await api(`/api/viagens/${encodeURIComponent(chosen)}`));}catch{}
    const select = $('viagem-selecionada'); select.replaceChildren(new Option('Escolha uma viagem', ''));
    for (const trip of trips) select.add(new Option(`CT-e ${trip.numero} · ${trip.clienteNome||'Cliente não informado'}`, String(trip.id)));
    select.value = trips.some(trip => String(trip.id) === chosen) ? chosen : '';
    await carregarDocumentos(select.value);
  } catch(error) {
    if(version === atualizacao){
      state.clientes=[];state.viagens=[];state.documentos=[];consultas.falhar();
      $('viagem-selecionada').replaceChildren(new Option('Dados indisponíveis',''));await carregarDocumentos('');
      $('indicadores').classList.add('has-error');$('estado-indicadores').textContent='Indicadores indisponíveis. Use Atualizar dados para tentar novamente.';
      $('resumo-pendencias').textContent='A consulta de pendências está indisponível.';
      for(const id of ['total-clientes','total-viagens','total-documentos','total-pendencias'])$(id).textContent='—';
      document.dispatchEvent(new Event('dados-atualizados'));
    }
    throw error;
  } finally { if (version === atualizacao) { $('atualizar').disabled = false; $('indicadores').setAttribute('aria-busy','false'); } }
}

async function confirmarExclusao(tipo, id, nome, origem, viagensAfetadas = []) {
  let conferencia,etag;
  if(tipo==='viagem')try{await api(`/api/viagens/${id}`,{aoResponder:r=>{etag=r.headers.get('ETag');}});}catch(e){avisar(e,true);return;}
  if(tipo==='cliente') {
    origem.disabled=true;
    try {
      conferencia=await api(`/api/clientes/${encodeURIComponent(id)}/conferencia-exclusao`);
      if(conferencia.tentativasDesconhecidas>0)throw new Error('Este cliente possui envio com resultado desconhecido. Revise a tentativa antes de excluir para preservar o histórico.');
    }catch(error){avisar(error.message,true);return;}
    finally{origem.disabled=false;}
  }
  const arquivo = tipo === 'documento' || tipo === 'anexo';
  const descricao = conferencia
    ? `Excluir o cliente “${conferencia.nome}” e seus dados? Serão removidos ${conferencia.viagens} viagem(ns), ${conferencia.documentos} documento(s), ${conferencia.pdfsExclusivos} PDF(s) exclusivo(s), ${conferencia.envios} envio(s) legado(s), ${conferencia.conferenciasEmail} conferência(s) e ${conferencia.tentativasEmail} tentativa(s) de e-mail.`
    : arquivo ? `Excluir “${nome}” da lista ativa? O arquivo será arquivado e poderá ser restaurado.${tipo === 'anexo' ? ` A exclusão afeta todas as viagens vinculadas${viagensAfetadas.length ? ': '+viagensAfetadas.map(v=>'#'+v).join(', ') : ''}.` : ''}` : `Excluir ${tipo} “${nome}”? Essa ação remove o cadastro sem vínculos.`;
  confirmarAcao({ titulo:arquivo?'Excluir documento?':`Excluir ${tipo}?`, descricao, origem,
    textoConfirmar:conferencia?'Excluir cliente e documentos':arquivo?'Excluir e arquivar':'Excluir cadastro',
    regra:conferencia?`Exclusão definitiva, sem restauração pelo painel. ${conferencia.pdfsCompartilhados} PDF(s) compartilhado(s) serão mantidos nas viagens de outros clientes. Registros de pacotes, processamentos e auditoria são preservados.`:arquivo?'Os bytes, vínculos e históricos são preservados. O documento sai da lista ativa e da seleção de envio.':'Viagens com documentos ou histórico não podem ser excluídas.',
    acao:async()=>{
      if(arquivo||conferencia)invalidarPrevia();
      const rota={cliente:'clientes',viagem:'viagens',documento:'documentos',anexo:'anexos'}[tipo];
      const parametros=conferencia?'?'+new URLSearchParams({confirmado:'true',conferenciaSha256:conferencia.conferenciaSha256}):'';
      await api(`/api/${rota}/${encodeURIComponent(id)}${parametros}`,{method:'DELETE',headers:etag?{'If-Match':etag}:{}});
      avisar(conferencia?'Cliente, viagens e documentos exclusivos excluídos com sucesso.':arquivo?'Documento excluído da lista ativa por arquivamento. É possível restaurá-lo.':'Viagem excluída com sucesso.');
      try{await atualizar();}catch{avisar('Exclusão concluída, mas a atualização falhou. Use Atualizar dados antes de continuar.',true);}
    }
  });
}

async function restaurarDocumento(doc, button) {
  button.disabled = true;
  try {
    await api(`/api/documentos/${encodeURIComponent(doc.id)}/restaurar`, { method: 'POST' });
    avisar('Documento restaurado. Consulte a lista de documentos ativos.');
    try { await atualizar(); }
    catch { avisar('Restauração concluída, mas a atualização falhou. Use Atualizar dados para conferir.', true); }
  } catch (error) { avisar(error.message, true); }
  finally { button.disabled = false; }
}

$('mostrar-arquivados').addEventListener('change', () => carregarDocumentos(state.viagem));
function carregarHistoricos(id,version=consulta){
  // Históricos não bloqueiam a consulta documental e falham independentemente.
  if(!id)return;
  api(`/api/viagens/${encodeURIComponent(id)}/tentativas-email`).then(items=>{if(version===consulta)renderizarTentativas(items);}).catch(()=>{if(version===consulta)$('tentativas').textContent='⛔ Histórico indisponível. Use Atualizar dados para tentar novamente.';});
  if($('historico').closest('details').open)api(`/api/viagens/${encodeURIComponent(id)}/envios`).then(items=>{if(version===consulta)historico(items);}).catch(()=>{if(version===consulta)$('historico').textContent='⛔ Histórico indisponível. Use Atualizar dados para tentar novamente.';});
}
$('historico').closest('details').addEventListener('toggle',()=>{if($('historico').closest('details').open)carregarHistoricos(state.viagem);});
$('voltar-lista-viagens').addEventListener('click',()=>{$('viagem-selecionada').value='';carregarDocumentos('');navegarPara('viagens',{foco:'buscar-viagem'});});
$('viagem-selecionada').addEventListener('change', event => carregarDocumentos(event.target.value));
$('destinatario').addEventListener('input', invalidarPrevia);
$('atualizar').addEventListener('click', () => { $('aviso').hidden = true; atualizar().catch(error => avisar(error.message, true)); });

function renderizarAnexos() {
  const list = $('lista-anexos'); list.replaceChildren();
  if (!state.anexos.length) { list.append(element('li', state.viagem ? 'Nenhum PDF nesta consulta.' : 'Escolha uma viagem.', 'empty')); return; }
  for (const anexo of state.anexos) {
    const item=element('li'); const label=element('label');
    if (!anexo.arquivadoEm) {
      const box=element('input');box.type='checkbox';box.setAttribute('aria-label',`Selecionar PDF ${anexo.nomeArquivo}`);
      box.addEventListener('change',()=>{if(box.checked)state.anexosSelecionados.add(anexo.id);else state.anexosSelecionados.delete(anexo.id);invalidarPrevia();atualizarSelecao();});label.append(box);
    }
    label.append(element('span', `${anexo.nomeArquivo} · ${anexo.finalidade === 'FATURA' ? 'Fatura' : 'Viagem'} · ${anexo.viagemIds.length} viagem(ns)`));
    const button=element('button',anexo.arquivadoEm?'Restaurar PDF':'Excluir PDF',anexo.arquivadoEm?'row-action':'delete-action');button.type='button';
    button.hidden=!podeOperar();
    button.setAttribute('aria-label',`${anexo.arquivadoEm?'Restaurar':'Excluir'} PDF ${anexo.nomeArquivo}`);
    button.addEventListener('click',async()=>{
      if(!anexo.arquivadoEm){confirmarExclusao('anexo',anexo.id,anexo.nomeArquivo,button,anexo.viagemIds);return;}
      button.disabled=true;try{await api(`/api/anexos/${anexo.id}/restaurar`,{method:'POST'});await atualizar();avisar('PDF restaurado em todas as viagens vinculadas.');}catch(error){avisar(error.message,true);}finally{button.disabled=false;}
    });const baixar=element('a','Baixar original','row-action');baixar.href=`/api/anexos/${anexo.id}/arquivo`;baixar.setAttribute('download','');item.append(label,baixar,button);list.append(item);
  }
}

export function iniciarDocumentos(){
  let timerSeletor,seqSeletor=0;
  $('buscar-seletor-viagem').addEventListener('input',()=>{const seq=++seqSeletor;clearTimeout(timerSeletor);timerSeletor=setTimeout(async()=>{try{const r=await api('/api/busca?'+new URLSearchParams({entidade:'VIAGEM',busca:$('buscar-seletor-viagem').value,tamanho:100}));if(seq!==seqSeletor)return;const atual=state.viagens.find(v=>String(v.id)===state.viagem);state.viagens=r.itens;if(atual&&!state.viagens.some(v=>v.id===atual.id))state.viagens.push(atual);const select=$('viagem-selecionada');select.replaceChildren(new Option('Escolha uma viagem',''));for(const v of state.viagens)select.add(new Option(`CT-e ${v.numero} · ${v.clienteNome||'#'+v.clienteId}`,String(v.id)));select.value=state.viagem;document.dispatchEvent(new Event('dados-atualizados'));$('estado-seletor-viagem').textContent=`${r.total} resultados · até 100 opções; refine a pesquisa se necessário.`;}catch(e){if(seq===seqSeletor)$('estado-seletor-viagem').textContent=`Seletores indisponíveis: ${e.message}`;}},300);});
  consultas=criarConsultas({
    abrirViagem:async id=>{if(!state.viagens.some(v=>String(v.id)===id)){const v=await api(`/api/viagens/${id}`);state.viagens.push(v);$('viagem-selecionada').add(new Option(`CT-e ${v.numero} · #${v.id}`,id));} $('viagem-selecionada').value=id;carregarDocumentos(id);navegarPara('documentos',{foco:'viagem-selecionada'}); },
    excluirCliente:(cliente,origem)=>confirmarExclusao('cliente',cliente.id,cliente.nome,origem),
    excluirViagem:(viagem,origem)=>confirmarExclusao('viagem',viagem.id,viagem.numero,origem)
  });
  return atualizar();
}
