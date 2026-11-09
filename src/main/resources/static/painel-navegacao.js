const paginas = {
  saude: { titulo:'Saúde do sistema', descricao:'Confira se os recursos necessários à operação estão disponíveis.', contexto:'ADMINISTRAÇÃO' },
  busca: { titulo:'Busca integrada', descricao:'Localize clientes, viagens, pacotes e documentos.', contexto:'TRABALHO' },
  fila: { titulo:'Fila compartilhada', descricao:'Assuma uma tarefa e acompanhe o trabalho da equipe.', contexto:'TRABALHO' },
  administracao: { titulo:'Operadores', descricao:'Gerencie os acessos e as permissões da equipe.', contexto:'ADMINISTRAÇÃO' },
  inicio: { titulo:'Visão geral', descricao:'Acompanhe o trabalho do dia e o que precisa de atenção.', contexto:'TRABALHO' },
  pacotes: { titulo:'Importar ZIP', descricao:'Organize os documentos de uma ou várias viagens.', contexto:'TRABALHO' },
  importacao: { titulo:'Importar XML ou PDF', descricao:'Adicione um CT-e ou um PDF a uma viagem.', contexto:'TRABALHO' },
  viagens: { titulo:'Viagens e documentos', descricao:'Encontre viagens e consulte seus documentos.', contexto:'TRABALHO' },
  clientes: { titulo:'Clientes', descricao:'Consulte clientes, contatos e viagens vinculadas.', contexto:'TRABALHO' },
  email: { titulo:'Central de envios', descricao:'Selecione os documentos e confira o destinatário antes de enviar.', contexto:'ACOMPANHAMENTO' },
  processamentos: { titulo:'Processamentos e pendências', descricao:'Veja o que precisa de correção e continue de onde parou.', contexto:'ACOMPANHAMENTO' },
  configuracoes: { titulo:'Configurações', descricao:'Defina a conta de envio e onde guardar as cópias dos documentos.', contexto:'ADMINISTRAÇÃO' }
};
paginas['pacotes-importados'] = { titulo:'Importações', descricao:'Pacotes importados e seus históricos.', contexto:'ACOMPANHAMENTO' };
const aliases = { documentos:'viagens', 'ultimas-viagens':'inicio', pendencias:'processamentos', envios:'email', operadores:'administracao', importar:'pacotes' };
let pronta = false, paginaAtual = 'inicio';

function destinoAtual() {
  try { return decodeURIComponent(location.hash.slice(1)) || 'inicio'; } catch { return 'inicio'; }
}
function resolver(destino) { return paginas[destino] ? destino : aliases[destino] || 'inicio'; }
function mostrar(destino) {
  const pagina = resolver(destino), info = paginas[pagina];
  if (pagina !== paginaAtual) {
    document.getElementById('aviso').hidden = true;
    document.getElementById('diagnostico-global').replaceChildren();
    document.getElementById('anuncios-diagnostico').textContent='';
  }
  paginaAtual = pagina;
  document.querySelectorAll('main > [data-pagina]').forEach(node => { node.hidden = node.dataset.pagina !== pagina; });
  const heading = document.querySelector('.page-heading h1');
  const eyebrow = document.querySelector('.page-heading .eyebrow');
  heading.textContent = info.titulo; eyebrow.textContent = info.contexto;
  document.querySelector('.page-heading p:not(.eyebrow)').textContent = info.descricao;
  document.getElementById('pagina-atual').textContent = info.titulo;
  document.querySelector('main').dataset.paginaAtual = pagina;
  document.title = `${info.titulo} · RotaBrasil`;
  document.dispatchEvent(new CustomEvent('pagina-alterada',{detail:{pagina}}));
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
  pronta = true;
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
