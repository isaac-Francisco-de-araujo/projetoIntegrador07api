// Comportamentos de apresentação, independentes das operações da API.
import { iniciarComandos } from './painel-comandos.js';
import { avisar } from './painel-base.js';

export function iniciarInterface() {
  const themeButton = document.getElementById('alternar-tema');
  function aplicarTema(dark) {
    document.documentElement.dataset.tema = dark ? 'escuro' : 'claro';
    themeButton.setAttribute('aria-pressed', String(dark));
    themeButton.setAttribute('aria-label', dark ? 'Ativar tema claro' : 'Ativar tema escuro');
    themeButton.title = themeButton.getAttribute('aria-label');
    document.querySelector('meta[name="theme-color"]').content = dark ? '#0e181c' : '#f5f6f3';
  }
  let saved;
  try { saved = localStorage.getItem('rotabrasil-tema'); } catch {}
  aplicarTema(saved === 'escuro');
  function alternarTema() {
    const dark = document.documentElement.dataset.tema !== 'escuro';
    aplicarTema(dark);
    try { localStorage.setItem('rotabrasil-tema', dark ? 'escuro' : 'claro'); } catch {}
  }
  themeButton.addEventListener('click', alternarTema);
  iniciarComandos({ alternarTema });

  const mobile = matchMedia('(max-width: 760px)');
  const sidebar = document.getElementById('menu-lateral');
  let menuOpen = false;
  function sincronizarMenu() {
    const loading = document.body.classList.contains('is-loading');
    if (!mobile.matches) menuOpen = false;
    document.body.classList.toggle('menu-aberto', mobile.matches && menuOpen);
    document.getElementById('menu-overlay').hidden = !mobile.matches || !menuOpen;
    document.getElementById('menu-abrir').setAttribute('aria-expanded', String(mobile.matches && menuOpen));
    sidebar.inert = loading || (mobile.matches && !menuOpen);
    document.querySelector('main').inert = loading || (mobile.matches && menuOpen);
    document.querySelector('.skip-link').inert = loading || (mobile.matches && menuOpen);
  }
  function fecharMenu(restore = true) {
    menuOpen = false; sincronizarMenu();
    if (restore && mobile.matches) document.getElementById('menu-abrir').focus();
  }
  document.getElementById('menu-abrir').addEventListener('click', () => {
    menuOpen = !menuOpen; sincronizarMenu();
    if (menuOpen) sidebar.querySelector('nav a').focus();
  });
  for (const id of ['menu-fechar','menu-overlay']) document.getElementById(id).addEventListener('click', () => fecharMenu());
  document.addEventListener('keydown', event => { if (event.key === 'Escape' && menuOpen) { event.preventDefault(); fecharMenu(); } });
  sidebar.addEventListener('keydown', event => {
    if (!mobile.matches || !menuOpen || event.key !== 'Tab') return;
    const items = [...sidebar.querySelectorAll('a,button')].filter(el => !el.disabled && el.getClientRects().length);
    const first = items[0], last = items.at(-1);
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  });
  document.querySelectorAll('.sidebar nav a,.sidebar-journey a,.sidebar .brand').forEach(link => {
    link.title = link.textContent.trim();
    link.addEventListener('click', () => {
      if (!mobile.matches || !menuOpen) return;
      fecharMenu(false);
    });
  });
  function aplicarMenuCompacto(compact) {
    document.body.classList.toggle('menu-compacto', compact);
    const button = document.getElementById('menu-compactar');
    button.setAttribute('aria-expanded', String(!compact));
    button.setAttribute('aria-label', compact ? 'Expandir menu lateral' : 'Recolher menu lateral');
    button.title = button.getAttribute('aria-label');
  }
  let compact = false;
  try { compact = localStorage.getItem('rotabrasil-menu-compacto') === 'true'; } catch {}
  aplicarMenuCompacto(compact);
  document.getElementById('menu-compactar').addEventListener('click', () => {
    compact = !compact; aplicarMenuCompacto(compact);
    try { localStorage.setItem('rotabrasil-menu-compacto', String(compact)); } catch {}
  });
  mobile.addEventListener('change', sincronizarMenu);
  document.addEventListener('workspace-pronto', sincronizarMenu);
  sincronizarMenu();
  document.querySelectorAll('.stats .stat-bottom a').forEach(link => link.classList.add('stat-card-link'));

  for (const [selector, inputId, extensions] of [['.zip-upload','arquivo-zip',['.zip']],['.file-area','arquivo',['.xml','.pdf']]]) {
    const area = document.querySelector(selector);
    const input = document.getElementById(inputId);
    let depth = 0;
    area.addEventListener('dragenter', event => { event.preventDefault(); if (!input.disabled) { depth++; area.classList.add('is-dragging'); } });
    area.addEventListener('dragover', event => { event.preventDefault(); if (event.dataTransfer) event.dataTransfer.dropEffect = input.disabled ? 'none' : 'copy'; });
    area.addEventListener('dragleave', () => { depth = Math.max(0, depth - 1); if (!depth) area.classList.remove('is-dragging'); });
    area.addEventListener('drop', event => {
      event.preventDefault(); depth = 0; area.classList.remove('is-dragging');
      if (input.disabled) return;
      const files = event.dataTransfer?.files;
      if (!files || files.length !== 1 || !extensions.some(extension => files[0].name.toLowerCase().endsWith(extension))) {
        avisar(`Solte apenas um arquivo ${extensions.join(' ou ').toUpperCase()}. A seleção anterior foi preservada.`, true); return;
      }
      const transfer = new DataTransfer(); transfer.items.add(files[0]); input.files = transfer.files;
      input.dispatchEvent(new Event('change', { bubbles: true }));
    });
  }
  const date = document.getElementById('data-painel');
  date.textContent = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date());

}
