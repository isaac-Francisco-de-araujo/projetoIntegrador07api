const paginas = {
  saude: { titulo:'Saúde do sistema', descricao:'Verificações locais e evidências, sem testes externos automáticos.', contexto:'OPERAÇÃO / DISPONIBILIDADE' },
  busca: { titulo:'Busca integrada', descricao:'Clientes, viagens, documentos e pacotes com paginação no servidor.', contexto:'ACERVO / CONSULTA' },
  fila: { titulo:'Fila compartilhada', descricao:'Situação técnica, trabalho e responsabilidade dos históricos existentes.', contexto:'OPERAÇÃO / EQUIPE' },
  administracao: { titulo:'Operadores', descricao:'Contas individuais, permissões e redefinição de acesso.', contexto:'ADMINISTRAÇÃO / ACESSO' },
  inicio: { titulo:'Visão geral', descricao:'Bem-vindo ao seu workspace. Vamos colocar tudo em movimento.', contexto:'SUA OPERAÇÃO. UMA NOVA PERSPECTIVA.' },
  pacotes: { titulo:'Importar ZIP', descricao:'Confira pacotes, visualize os PDFs e gerencie os arquivos já importados.', contexto:'ENTRADA ASSISTIDA / ZIP' },
  importacao: { titulo:'Importar XML ou PDF', descricao:'Um CT-e individual ou um PDF vinculado a uma viagem existente.', contexto:'ENTRADA INDIVIDUAL / DOCUMENTOS' },
  viagens: { titulo:'Viagens e documentos', descricao:'Pesquise viagens, confira seus arquivos e gerencie documentos ativos ou arquivados.', contexto:'ACOMPANHAMENTO / VIAGENS' },
  clientes: { titulo:'Clientes', descricao:'Consulte cadastros, contatos informados e as viagens de cada cliente.', contexto:'CADASTROS / CLIENTES' },
  email: { titulo:'Central de envios', descricao:'Selecione documentos, confira o destinatário e acompanhe as tentativas de envio.', contexto:'CONFERÊNCIA / ENVIO MANUAL' },
  processamentos: { titulo:'Processamentos e pendências', descricao:'Diagnóstico, impacto e ações seguras sobre os históricos reais da operação.', contexto:'DIAGNÓSTICO / RECUPERAÇÃO' },
  configuracoes: { titulo:'Configurações', descricao:'E-mail da operação, pasta de exportação e resultados de testes.', contexto:'OPERAÇÃO / CONFIGURAÇÕES' }
};
const aliases = { documentos:'viagens', 'pacotes-importados':'pacotes', 'ultimas-viagens':'inicio' };
let pronta = false, paginaAtual = 'inicio', tituloOriginal, contextoOriginal;

function destinoAtual() {
  try { return decodeURIComponent(location.hash.slice(1)) || 'inicio'; } catch { return 'inicio'; }
}
function resolver(destino) { return paginas[destino] ? destino : aliases[destino] || 'inicio'; }
function mostrar(destino) {
  const pagina = resolver(destino), info = paginas[pagina]; paginaAtual = pagina;
  document.querySelectorAll('main > [data-pagina]').forEach(node => { node.hidden = node.dataset.pagina !== pagina; });
  const heading = document.querySelector('.page-heading h1');
  const eyebrow = document.querySelector('.page-heading .eyebrow');
  if (pagina === 'inicio') {
    heading.replaceChildren(...[...tituloOriginal.childNodes].map(node => node.cloneNode(true)));
    eyebrow.replaceChildren(...[...contextoOriginal.childNodes].map(node => node.cloneNode(true)));
  } else { heading.textContent = info.titulo; eyebrow.textContent = info.contexto; }
  document.querySelector('.page-heading p:not(.eyebrow)').textContent = info.descricao;
  document.getElementById('pagina-atual').textContent = info.titulo;
  document.querySelector('main').dataset.paginaAtual = pagina;
  document.title = `${info.titulo} · RotaBrasil`;
  for (const link of document.querySelectorAll('.sidebar nav a')) {
    const active = link.getAttribute('href') === `#${pagina}`;
    link.classList.toggle('is-active',active);
    if(active)link.setAttribute('aria-current','page');else link.removeAttribute('aria-current');
  }
}
function posicionar(destino) {
  const target = document.getElementById(destino);
  if (!target || paginas[destino]) window.scrollTo({top:0,behavior:'instant'});
  else target.scrollIntoView({behavior:'instant',block:'start'});
}
export function navegarPara(destino, { foco } = {}) {
  if (!pronta) return;
  mostrar(destino);
  if (location.hash !== `#${destino}`) location.hash = destino;
  posicionar(destino);
  const requested = foco ? document.getElementById(foco) : null;
  const heading = document.querySelector('.page-heading h1');
  const target = requested && !requested.disabled && requested.getClientRects().length ? requested : heading;
  if (target === heading) target.tabIndex = -1;
  target.focus({preventScroll:true});
}
export function iniciarNavegacao() {
  if (pronta) return;
  const heading = document.querySelector('.page-heading h1'); heading.id = 'titulo-pagina';
  document.querySelector('main').setAttribute('aria-labelledby','titulo-pagina');
  tituloOriginal = heading.cloneNode(true); contextoOriginal = document.querySelector('.page-heading .eyebrow').cloneNode(true); pronta = true;
  mostrar(destinoAtual());
  document.body.classList.add('paginas-prontas');
  window.addEventListener('hashchange', () => {
    const destino = destinoAtual(); mostrar(destino); posicionar(destino);
    const focus = document.activeElement;
    if (!document.body.classList.contains('is-loading') && !document.querySelector('dialog[open]') &&
        (focus === document.body || focus?.classList.contains('skip-link') || !focus?.getClientRects().length)) {
      const title = document.querySelector('.page-heading h1'); title.tabIndex = -1; title.focus({preventScroll:true});
    }
  });
  document.addEventListener('click', event => {
    const link = event.target.closest?.('a[href^="#"]');
    if (!link || event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    const destino = link.classList.contains('skip-link') ? paginaAtual : link.getAttribute('href').slice(1);
    if (!paginas[destino] && !aliases[destino]) return;
    event.preventDefault(); navegarPara(destino);
  });
}
