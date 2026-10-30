import { navegarPara } from './painel-navegacao.js';
const normalizar = text => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('pt-BR');

export function iniciarComandos({ alternarTema }) {
  const dialog = document.getElementById('central-comandos');
  const input = document.getElementById('buscar-comando');
  const list = document.getElementById('lista-comandos');
  let origin;

  function navegar(id, focusId) {
    origin = null;
    dialog.close();
    navegarPara(id,{foco:focusId});
  }

  function comandos() {
    return [
      { titulo:'Visão geral', detalhe:'Uma visão clara da sua operação', icone:'▦', executar:()=>navegar('inicio') },
      { titulo:'Importar ZIP', detalhe:'Conferir CT-es, PDFs e faturas', icone:'↥', executar:()=>navegar('pacotes','arquivo-zip') },
      { titulo:'Importar XML ou PDF', detalhe:'CT-e individual ou PDF de uma viagem existente', icone:'↑', executar:()=>navegar('importacao','arquivo') },
      { titulo:'Buscar viagem', detalhe:'Encontrar pelo nCT ou pelo cliente', icone:'→', executar:()=>navegar('viagens','buscar-viagem') },
      { titulo:'Documentos da viagem', detalhe:'Selecionar, baixar e conferir documentos', icone:'▤', executar:()=>navegar('documentos','viagem-selecionada') },
      { titulo:'Clientes', detalhe:'Consultar destinatários cadastrados', icone:'◎', executar:()=>navegar('clientes') },
      { titulo:'Central de envios', detalhe:'Preparar uma seleção e conferir o envio', icone:'✉', executar:()=>navegar('email','destinatario') },
      { titulo:'Histórico de processamento', detalhe:'Consultar diagnósticos e pendências', icone:'↻', executar:()=>navegar('processamentos') },
      { titulo:'Configurações', detalhe:'E-mail da operação e pasta de exportação no backend', icone:'⚙', executar:()=>navegar('configuracoes') },
      { titulo:document.documentElement.dataset.tema === 'escuro' ? 'Ativar tema claro' : 'Ativar tema escuro', detalhe:'Seu workspace, do seu jeito', icone:'◐', executar:()=>{ dialog.close(); alternarTema(); } }
    ];
  }

  function renderizar() {
    const query = normalizar(input.value.trim());
    const results = comandos().filter(command => normalizar(`${command.titulo} ${command.detalhe}`).includes(query));
    list.replaceChildren();
    document.getElementById('comandos-contagem').textContent = `${results.length} ${results.length === 1 ? 'atalho' : 'atalhos'}`;
    for (const command of results) {
      const li = document.createElement('li');
      const button = document.createElement('button');
      button.type = 'button'; button.className = 'command-option';
      const icon = document.createElement('span'); icon.className = 'command-option-icon'; icon.textContent = command.icone; icon.setAttribute('aria-hidden','true');
      const copy = document.createElement('span'); copy.className = 'command-option-copy';
      const title = document.createElement('strong'); title.textContent = command.titulo;
      const detail = document.createElement('small'); detail.textContent = command.detalhe;
      copy.append(title, detail);
      const arrow = document.createElement('span'); arrow.textContent = '↗'; arrow.className = 'command-option-arrow'; arrow.setAttribute('aria-hidden','true');
      button.append(icon, copy, arrow); button.addEventListener('click', command.executar);
      li.append(button); list.append(li);
    }
    if (!results.length) {
      const li = document.createElement('li'); li.className = 'command-empty'; li.textContent = 'Nenhum atalho encontrado. Tente “viagem”, “XML” ou “tema”.'; list.append(li);
    }
  }

  function abrir() {
    if (document.body.classList.contains('is-loading') || document.querySelector('dialog[open]')) return;
    origin = document.activeElement;
    input.value = ''; renderizar(); dialog.showModal(); input.focus();
  }
  document.getElementById('abrir-comandos').addEventListener('click', abrir);
  document.getElementById('fechar-comandos').addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', event => {
    if (event.target !== dialog) return;
    const rect = dialog.getBoundingClientRect();
    if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close();
  });
  dialog.addEventListener('close', () => { if (origin?.isConnected) origin.focus({preventScroll:true}); });
  input.addEventListener('input', renderizar);
  dialog.addEventListener('keydown', event => {
    if (event.key === 'Escape') { event.preventDefault(); dialog.close(); return; }
    const buttons = [...list.querySelectorAll('button')];
    const index = buttons.indexOf(document.activeElement);
    if (event.key === 'ArrowDown') { event.preventDefault(); buttons[(index + 1) % buttons.length]?.focus(); }
    if (event.key === 'ArrowUp') { event.preventDefault(); if (index <= 0) input.focus(); else buttons[index - 1]?.focus(); }
    if (event.key === 'Enter' && document.activeElement === input) { event.preventDefault(); buttons[0]?.click(); }
  });
  document.addEventListener('keydown', event => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      if (dialog.open) dialog.close(); else abrir();
    }
  });
}
