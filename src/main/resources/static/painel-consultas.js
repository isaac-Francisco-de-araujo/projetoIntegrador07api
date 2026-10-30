import { $, state, element, api, vazio, data, podeAdministrar } from './painel-base.js';
import { navegarPara } from './painel-navegacao.js';

const normalizar = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('pt-BR');
const TAMANHO = 20;
const cnpj = value => value ? String(value).replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5') : 'Não informado';
function acao(text, handler, className = 'row-action') {
  const button = element('button', text, className); button.type = 'button'; button.addEventListener('click', handler); return button;
}

export function criarConsultas({ abrirViagem, excluirCliente, excluirViagem }) {
  let paginaViagens = 0, paginaClientes = 0, coluna = 'data', direcao = -1, busy = true;
  let nomesClientes = new Map();
  const dialog = $('detalhes-cadastro');
  let detailVersion = 0, detailRequest, origin, detailAction;
  const nomeCliente = viagem => nomesClientes.get(viagem.clienteId) || `Cliente #${viagem.clienteId}`;

  function paginar(items, pagina, prefix) {
    const pages = Math.max(1, Math.ceil(items.length / TAMANHO));
    const current = Math.min(pagina, pages - 1);
    $(`${prefix}-anterior`).disabled = busy || current === 0;
    $(`${prefix}-proxima`).disabled = busy || current + 1 >= pages;
    $(`pagina-${prefix}`).textContent = `Página ${current + 1} de ${pages} · ${items.length} resultado(s)`;
    return { pagina: current, items: items.slice(current * TAMANHO, (current + 1) * TAMANHO) };
  }
  function ordenar(a, b) {
    const value = viagem => coluna === 'cliente' ? nomeCliente(viagem) : viagem[coluna];
    const av = value(a), bv = value(b);
    if (av == null || av === '') return bv == null || bv === '' ? b.id - a.id : 1;
    if (bv == null || bv === '') return -1;
    return String(av).localeCompare(String(bv), 'pt-BR', { numeric:true, sensitivity:'base' }) * direcao || b.id - a.id;
  }
  function linhaViagem(viagem, recent = false) {
    const row = element('tr'); row.dataset.viagemId = String(viagem.id);
    const number = element('td'); number.append(element('strong', viagem.numero));
    const status = element('td'); status.append(element('span', viagem.status || 'Não informado', 'status-pill'));
    const actions = element('td'); actions.className = 'row-actions';
    const abrir = acao('Ver documentos →', () => abrirViagem(String(viagem.id)));
    abrir.setAttribute('aria-label', `Ver documentos da viagem ${viagem.numero}`);
    actions.append(abrir);
    if (!recent) {
      actions.append(acao('Detalhes', () => detalhes('viagem', viagem.id)));
      if (podeAdministrar()) {
        const excluir = acao('Excluir', () => excluirViagem(viagem, excluir), 'delete-action');
        excluir.dataset.excluirViagem = String(viagem.id); excluir.setAttribute('aria-label', `Excluir viagem ${viagem.numero}`); actions.append(excluir);
      }
    }
    row.append(number, element('td', nomeCliente(viagem)), element('td', data(viagem.data)), status, actions); return row;
  }
  function viagens() {
    const query = normalizar($('buscar-viagem').value.trim()), cliente = $('filtro-cliente').value;
    const inicio = $('filtro-inicio').value, fim = $('filtro-fim').value, situacao = $('filtro-situacao').value.trim();
    if (inicio && fim && inicio > fim) {
      vazio($('lista-viagens'), 5, 'A data inicial deve ser anterior ou igual à data final.');
      $('resultado-consulta-viagens').textContent = 'Revise o intervalo de datas.';
      paginar([], 0, 'viagens'); return;
    }
    const items = state.viagens.filter(viagem =>
      (!query || normalizar(`${viagem.numero} ${nomeCliente(viagem)}`).includes(query)) &&
      (!cliente || String(viagem.clienteId) === cliente) && (!inicio || (viagem.data && viagem.data >= inicio)) &&
      (!fim || (viagem.data && viagem.data <= fim)) && (!situacao || viagem.status === situacao)).sort(ordenar);
    const page = paginar(items, paginaViagens, 'viagens'); paginaViagens = page.pagina;
    $('resultado-consulta-viagens').textContent = `${items.length} de ${state.viagens.length} viagem(ns) · ${TAMANHO} por página`;
    if (page.items.length) $('lista-viagens').replaceChildren(...page.items.map(item => linhaViagem(item)));
    else vazio($('lista-viagens'), 5, state.viagens.length ? 'Nenhuma viagem corresponde aos filtros. Use Limpar filtros para ampliar a consulta.' : 'Nenhuma viagem por aqui. Importe um XML ou ZIP para começar.');
    document.querySelectorAll('[data-ordenar]').forEach(button => {
      const active = button.dataset.ordenar === coluna;
      button.closest('th').setAttribute('aria-sort', active ? direcao === 1 ? 'ascending' : 'descending' : 'none');
      button.querySelector('span').textContent = active ? direcao === 1 ? '↑' : '↓' : '↕';
    });
  }
  function clientes() {
    const query = normalizar($('buscar-cliente').value.trim()), digits = query.replace(/\D/g, '');
    const items = state.clientes.filter(cliente => !query || normalizar(`${cliente.nome} ${cliente.cnpj || ''}`).includes(query) || (digits && /^[\d\s./-]+$/.test(query) && (cliente.cnpj || '').includes(digits)))
      .sort((a,b) => a.nome.localeCompare(b.nome, 'pt-BR', {sensitivity:'base'}));
    const page = paginar(items, paginaClientes, 'clientes'); paginaClientes = page.pagina;
    $('resultado-consulta-clientes').textContent = `${items.length} cliente(s) encontrado(s)`;
    const rows = page.items.map(cliente => {
      const row = element('tr'); const info = element('td'); const identity = element('div', null, 'customer-identity');
      const avatar = element('span', (cliente.nome || '?').slice(0,1).toUpperCase(), 'client-avatar');
      const copy = element('div'); copy.append(element('strong', cliente.nome), element('small', `CNPJ ${cnpj(cliente.cnpj)}`)); identity.append(avatar, copy); info.append(identity);
      const actions = element('td'); actions.className = 'row-actions';
      actions.append(acao('Detalhes', () => detalhes('cliente', cliente.id)), acao('Ver viagens →', () => filtrarCliente(cliente.id)));
      if (podeAdministrar()) {
        const excluir = acao('Excluir', () => excluirCliente(cliente, excluir), 'delete-action');
        excluir.dataset.excluirCliente = String(cliente.id); excluir.setAttribute('aria-label', `Excluir cliente ${cliente.nome}`); actions.append(excluir);
      }
      row.append(info, actions); return row;
    });
    if (rows.length) $('lista-clientes').replaceChildren(...rows);
    else vazio($('lista-clientes'), 2, query ? 'Nenhum cliente corresponde ao nome ou CNPJ pesquisado.' : 'Os clientes aparecerão após a primeira importação.');
  }
  function filtrarCliente(id) {
    for (const field of ['buscar-viagem','filtro-inicio','filtro-fim','filtro-situacao']) $(field).value = '';
    $('filtro-cliente').value = String(id); paginaViagens = 0; viagens();
    navegarPara('viagens',{foco:'filtro-cliente'});
  }
  function campo(list, label, value) { list.append(element('dt', label), element('dd', value || 'Não informado')); }
  async function detalhes(tipo, id) {
    if (document.querySelector('dialog[open]')) return;
    origin = document.activeElement; const version = ++detailVersion;
    detailRequest = new AbortController(); detailAction = null;
    $('detalhes-tipo').textContent = tipo === 'cliente' ? 'CLIENTE / CADASTRO' : 'VIAGEM / DETALHES';
    $('detalhes-titulo').textContent = 'Carregando…'; $('detalhes-conteudo').textContent = 'Consultando os dados cadastrados…';
    $('detalhes-conteudo').setAttribute('aria-busy', 'true'); $('acao-detalhes').hidden = true; dialog.showModal();
    try {
      const item = await api(`/api/${tipo === 'cliente' ? 'clientes' : 'viagens'}/${id}`, {signal:detailRequest.signal});
      if (version !== detailVersion || !dialog.open) return;
      const list = element('dl', null, 'record-fields');
      $('detalhes-titulo').textContent = tipo === 'cliente' ? item.nome : `Viagem ${item.numero}`;
      if (tipo === 'cliente') {
        campo(list,'CNPJ',cnpj(item.cnpj)); campo(list,'E-mail',item.email); campo(list,'Telefone',item.telefone);
        campo(list,'Viagens nesta consulta',String(state.viagens.filter(viagem => viagem.clienteId === item.id).length));
        detailAction = () => filtrarCliente(item.id); $('acao-detalhes').textContent = 'Ver viagens deste cliente →';
      } else {
        campo(list,'Cliente',nomeCliente(item)); campo(list,'Data da viagem',data(item.data)); campo(list,'Status',item.status);
        campo(list,'Identificador',`#${item.id}`); detailAction = () => abrirViagem(String(item.id)); $('acao-detalhes').textContent = 'Abrir documentos da viagem →';
      }
      $('detalhes-conteudo').replaceChildren(list); $('acao-detalhes').hidden = false;
    } catch (error) { if (version === detailVersion && dialog.open) { $('detalhes-titulo').textContent = 'Dados indisponíveis'; $('detalhes-conteudo').textContent = error.message; } }
    finally { if (version === detailVersion) $('detalhes-conteudo').setAttribute('aria-busy','false'); }
  }
  $('fechar-detalhes').addEventListener('click', () => dialog.close());
  dialog.addEventListener('close', () => { detailVersion++; detailRequest?.abort(); if (origin?.isConnected) origin.focus({preventScroll:true}); });
  $('acao-detalhes').addEventListener('click', () => { const action = detailAction; origin = null; dialog.close(); action?.(); });

  function renderizar() { if (!busy) { viagens(); clientes(); } }
  for (const id of ['buscar-viagem','filtro-situacao']) $(id).addEventListener('input', () => { paginaViagens = 0; renderizar(); });
  for (const id of ['filtro-cliente','filtro-inicio','filtro-fim']) $(id).addEventListener('change', () => { paginaViagens = 0; renderizar(); });
  $('buscar-cliente').addEventListener('input', () => { paginaClientes = 0; renderizar(); });
  $('limpar-filtros').addEventListener('click', () => {
    for (const id of ['buscar-viagem','filtro-cliente','filtro-inicio','filtro-fim','filtro-situacao']) $(id).value = '';
    paginaViagens = 0; renderizar(); $('buscar-viagem').focus();
  });
  document.querySelectorAll('[data-ordenar]').forEach(button => button.addEventListener('click', () => {
    direcao = coluna === button.dataset.ordenar ? -direcao : 1; coluna = button.dataset.ordenar; paginaViagens = 0; renderizar();
  }));
  for (const [id, delta] of [['viagens-anterior',-1],['viagens-proxima',1],['clientes-anterior',-1],['clientes-proxima',1]]) $(id).addEventListener('click', () => {
    if (busy) return;
    if (id.startsWith('viagens')) paginaViagens = Math.max(0,paginaViagens + delta); else paginaClientes = Math.max(0,paginaClientes + delta);
    renderizar();
  });
  return {
    carregar() {
      busy = true;
      for (const [id, cols] of [['lista-viagens',5],['lista-clientes',2],['lista-ultimas-viagens',5]]) { $(id).setAttribute('aria-busy','true'); vazio($(id),cols,'Carregando dados…'); }
      for (const id of ['viagens-anterior','viagens-proxima','clientes-anterior','clientes-proxima']) $(id).disabled = true;
    },
    atualizar() {
      busy = false;
      nomesClientes = new Map(state.clientes.map(cliente => [cliente.id,cliente.nome]));
      const selected = $('filtro-cliente').value; $('filtro-cliente').replaceChildren(new Option('Todos os clientes',''));
      for (const cliente of [...state.clientes].sort((a,b) => a.nome.localeCompare(b.nome,'pt-BR'))) $('filtro-cliente').add(new Option(cliente.nome,String(cliente.id)));
      $('filtro-cliente').value = state.clientes.some(cliente => String(cliente.id) === selected) ? selected : '';
      renderizar();
      const recent = [...state.viagens].sort((a,b) => a.data && b.data ? b.data.localeCompare(a.data) || b.id-a.id : a.data ? -1 : b.data ? 1 : b.id-a.id).slice(0,5);
      if (recent.length) $('lista-ultimas-viagens').replaceChildren(...recent.map(item => linhaViagem(item,true)));
      else vazio($('lista-ultimas-viagens'),5,'As viagens aparecerão aqui após a primeira importação.');
      for (const id of ['lista-viagens','lista-clientes','lista-ultimas-viagens']) $(id).setAttribute('aria-busy','false');
    },
    falhar() {
      busy = true;
      for (const [id,cols] of [['lista-viagens',5],['lista-clientes',2],['lista-ultimas-viagens',5]]) { vazio($(id),cols,'Não foi possível carregar os dados. Use Atualizar dados para tentar novamente.'); $(id).setAttribute('aria-busy','false'); }
      $('resultado-consulta-viagens').textContent = 'Consulta indisponível.'; $('resultado-consulta-clientes').textContent = 'Consulta indisponível.';
      for (const id of ['viagens-anterior','viagens-proxima','clientes-anterior','clientes-proxima']) $(id).disabled = true;
    }
  };
}
