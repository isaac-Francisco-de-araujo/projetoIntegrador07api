'use strict';

const $ = id => document.getElementById(id);
const state = { clientes: [], viagens: [], documentos: [], daViagem: [], viagem: '', selecionados: new Set(), carregando: false, previaOcupada: false };
let consulta = 0;
let atualizacao = 0;
let previa = 0;

function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined && text !== null) node.textContent = String(text);
  if (className) node.className = className;
  return node;
}

function avisar(message, error = false) {
  const aviso = $('aviso');
  aviso.textContent = message;
  aviso.classList.toggle('error', error);
  aviso.hidden = false;
}

async function api(path, options = {}) {
  let response;
  try { response = await fetch(path, { credentials: 'same-origin', ...options }); }
  catch { throw new Error('Não foi possível conectar à aplicação. Confira se o servidor está disponível e tente novamente.'); }
  let body = null;
  try { body = await response.json(); } catch { /* Respostas sem JSON não devem expor HTML ou detalhes internos. */ }
  if (!response.ok) {
    const fields = Array.isArray(body?.campos) ? body.campos.map(item => `${item.campo}: ${item.mensagem}`).join('; ') : '';
    throw new Error([body?.mensagem || `A solicitação falhou (HTTP ${response.status}).`, fields].filter(Boolean).join(' '));
  }
  if (body === null) throw new Error('A aplicação retornou uma resposta inesperada. Atualize os dados para conferir o resultado.');
  return body;
}

function vazio(tbody, cols, text) {
  const row = element('tr');
  const cell = element('td', text, 'empty'); cell.colSpan = cols;
  row.append(cell); tbody.replaceChildren(row);
}

function data(value) {
  if (!value) return '—';
  const date = new Date(value.length === 10 ? `${value}T12:00:00` : value);
  return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('pt-BR').format(date);
}

function clientes() {
  const list = $('lista-clientes'); list.replaceChildren();
  if (!state.clientes.length) { list.append(element('li', 'Os clientes aparecerão após a primeira importação.', 'empty')); return; }
  for (const cliente of state.clientes) {
    const item = element('li');
    item.append(element('span', (cliente.nome || '?').slice(0, 1).toUpperCase(), 'client-avatar'));
    const info = element('div');
    info.append(element('strong', cliente.nome || 'Sem nome'), element('small', `CNPJ ${cliente.cnpj || 'não informado'}`));
    if (cliente.email) info.append(element('small', cliente.email));
    item.append(info); list.append(item);
  }
}

function viagens() {
  const tbody = $('lista-viagens');
  const filter = $('buscar-viagem').value.trim().toLocaleLowerCase('pt-BR');
  const names = new Map(state.clientes.map(cliente => [cliente.id, cliente.nome]));
  const items = state.viagens.filter(viagem => `${viagem.numero} ${names.get(viagem.clienteId) || ''}`.toLocaleLowerCase('pt-BR').includes(filter));
  if (!items.length) { vazio(tbody, 5, state.viagens.length ? 'Nenhuma viagem corresponde à busca.' : 'Nenhuma viagem por aqui. Importe um XML para começar.'); return; }
  tbody.replaceChildren();
  for (const viagem of items) {
    const row = element('tr'); const number = element('td'); number.append(element('strong', viagem.numero));
    const status = element('td'); status.append(element('span', viagem.status || 'Sem status informado', 'status-pill'));
    const action = element('td'); const button = element('button', 'Ver documentos →', 'row-action');
    button.type = 'button'; button.setAttribute('aria-label', `Ver documentos da viagem ${viagem.numero}`);
    button.addEventListener('click', () => { $('viagem-selecionada').value = String(viagem.id); carregarDocumentos(String(viagem.id)); $('documentos').scrollIntoView({ behavior: 'smooth' }); });
    action.append(button);
    row.append(number, element('td', names.get(viagem.clienteId) || `Cliente #${viagem.clienteId}`), element('td', data(viagem.data)), status, action);
    tbody.append(row);
  }
}

function invalidarPrevia() { previa++; $('resultado-previa').hidden = true; $('resultado-previa').replaceChildren(); }

function atualizarSelecao() {
  $('gerar-previa').disabled = state.previaOcupada || state.carregando || !state.viagem || !state.selecionados.size;
  $('selecao-info').textContent = state.selecionados.size ? `${state.selecionados.size} documento(s) selecionado(s) para conferência.` : 'Escolha uma viagem e selecione documentos.';
}

function documentos() {
  const tbody = $('lista-documentos');
  if (!state.daViagem.length) { vazio(tbody, 4, 'Esta viagem ainda não possui documentos.'); return; }
  tbody.replaceChildren();
  for (const doc of state.daViagem) {
    const row = element('tr'); const choice = element('td'); const checkbox = element('input');
    checkbox.type = 'checkbox'; checkbox.value = String(doc.id); checkbox.setAttribute('aria-label', `Selecionar ${doc.nomeArquivo}`);
    checkbox.addEventListener('change', () => {
      if (checkbox.checked) state.selecionados.add(doc.id); else state.selecionados.delete(doc.id);
      invalidarPrevia(); atualizarSelecao();
    });
    choice.append(checkbox);
    const name = element('td', doc.nomeArquivo, 'filename');
    name.append(element('small', ` · #${doc.id}`));
    row.append(choice, name, element('td', doc.tipo), element('td', doc.chaveAcesso || 'Não se aplica / não informada', 'fiscal-key'));
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
  state.viagem = id; state.selecionados.clear(); state.daViagem = []; invalidarPrevia();
  state.carregando = Boolean(id); atualizarSelecao();
  if (!id) { vazio($('lista-documentos'), 4, 'Escolha uma viagem para consultar os documentos.'); $('historico').textContent = 'Escolha uma viagem para consultar.'; return; }
  vazio($('lista-documentos'), 4, 'Carregando documentos…'); $('historico').textContent = 'Carregando histórico…';
  try {
    const [docs, history] = await Promise.all([api(`/api/viagens/${encodeURIComponent(id)}/documentos`), api(`/api/viagens/${encodeURIComponent(id)}/envios`)]);
    if (version !== consulta) return;
    state.daViagem = docs; documentos(); historico(history);
  } catch (error) {
    if (version !== consulta) return;
    vazio($('lista-documentos'), 4, 'Não foi possível carregar os documentos. Use Atualizar dados para tentar novamente.');
    $('historico').textContent = 'Histórico indisponível.'; avisar(error.message, true);
  } finally {
    if (version === consulta) { state.carregando = false; atualizarSelecao(); }
  }
}

async function atualizar(viagemId = null) {
  const version = ++atualizacao;
  $('atualizar').disabled = true;
  try {
    const [people, trips, docs] = await Promise.all([api('/api/clientes'), api('/api/viagens'), api('/api/documentos')]);
    if (version !== atualizacao) return;
    state.clientes = people; state.viagens = trips; state.documentos = docs;
    $('total-clientes').textContent = String(people.length); $('total-viagens').textContent = String(trips.length); $('total-documentos').textContent = String(docs.length);
    clientes(); viagens();
    const chosen = viagemId === null ? state.viagem : String(viagemId);
    const select = $('viagem-selecionada'); select.replaceChildren(new Option('Escolha uma viagem', ''));
    for (const trip of trips) select.add(new Option(`CT-e ${trip.numero} · #${trip.id}`, String(trip.id)));
    select.value = trips.some(trip => String(trip.id) === chosen) ? chosen : '';
    await carregarDocumentos(select.value);
  } finally { if (version === atualizacao) $('atualizar').disabled = false; }
}

$('buscar-viagem').addEventListener('input', viagens);
$('viagem-selecionada').addEventListener('change', event => carregarDocumentos(event.target.value));
$('destinatario').addEventListener('input', invalidarPrevia);
$('atualizar').addEventListener('click', () => { $('aviso').hidden = true; atualizar().catch(error => avisar(error.message, true)); });
$('arquivo').addEventListener('change', () => {
  const file = $('arquivo').files[0];
  $('arquivo-info').textContent = file ? `${file.name} · ${(file.size / 1024).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} KB` : 'Nenhum arquivo selecionado';
});

$('form-importacao').addEventListener('submit', async event => {
  event.preventDefault();
  const file = $('arquivo').files[0];
  if (!file || !file.name.toLowerCase().endsWith('.xml')) { avisar('Escolha um arquivo com extensão .xml.', true); return; }
  if (!file.size || file.size > 20 * 1024 * 1024) { avisar('O XML deve ter conteúdo e no máximo 20 MB.', true); return; }
  $('importar').disabled = true; $('importar').textContent = 'Validando e importando…';
  const form = new FormData(); form.append('arquivo', file);
  try {
    const result = await api('/api/processamento/xml', { method: 'POST', body: form });
    avisar(`${result.mensagem} · Documento #${result.documentoId} · Viagem #${result.viagemId}.`);
    try { await atualizar(result.viagemId); }
    catch { avisar('A importação foi concluída, mas a atualização dos dados falhou. Use Atualizar dados para conferir.', true); }
  } catch (error) { avisar(error.message, true); }
  finally { $('importar').disabled = false; $('importar').textContent = 'Importar XML →'; }
});

$('form-email').addEventListener('submit', async event => {
  event.preventDefault();
  if (state.previaOcupada || state.carregando || !state.viagem || !state.selecionados.size) return;
  const version = ++previa;
  const trip = state.viagem;
  const ids = [...state.selecionados];
  state.previaOcupada = true; atualizarSelecao(); $('gerar-previa').textContent = 'Gerando prévia…';
  $('resultado-previa').hidden = true;
  try {
    const result = await api(`/api/viagens/${encodeURIComponent(trip)}/previa-email`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ destinatario: $('destinatario').value.trim(), documentoIds: ids })
    });
    if (version !== previa || trip !== state.viagem) return;
    const target = $('resultado-previa');
    const list = element('ul'); for (const doc of result.documentos) list.append(element('li', `${doc.nomeArquivo} · ${doc.tipo} · #${doc.id}`));
    target.replaceChildren(element('strong', 'Prévia gerada. Nenhum e-mail enviado.'), element('p', `Destinatário: ${result.destinatario}`), list, element('p', 'Somente metadados: os anexos não foram verificados.'));
    target.hidden = false;
  } catch (error) { if (version === previa) avisar(error.message, true); }
  finally { state.previaOcupada = false; $('gerar-previa').textContent = 'Gerar prévia →'; atualizarSelecao(); }
});

atualizar().catch(error => {
  avisar(error.message, true);
  vazio($('lista-viagens'), 5, 'Dados indisponíveis. Use Atualizar dados para tentar novamente.');
  $('lista-clientes').replaceChildren(element('li', 'Não foi possível carregar os clientes.', 'empty'));
});
