const $ = id => document.getElementById(id);

export function iniciarAbertura() {
  const screen = $('abertura');
  const targets = [...document.querySelectorAll('body > main, body > aside, body > .skip-link')];
  let closed = false, failed = false;
  let resolverLiberacao;
  const liberacao = new Promise(resolve => { resolverLiberacao=resolve; });
  for (const target of targets) target.inert = true;

  function etapa(texto) {
    if (!screen.classList.contains('has-error') && $('abertura-status').textContent !== texto) $('abertura-status').textContent = texto;
  }
  function fechar() {
    if (closed) return;
    closed = true;
    const focusInScreen = screen.contains(document.activeElement);
    screen.hidden = true;
    for (const target of targets) target.inert = false;
    document.body.classList.remove('is-loading');
    document.documentElement.classList.remove('painel-inicializando');
    document.body.classList.add('workspace-entered');
    document.dispatchEvent(new Event('workspace-pronto'));
    resolverLiberacao();
    // Não tomar o foco na entrada normal; só retirar foco de um elemento que vai sumir.
    if (focusInScreen) {
      const focus = failed || screen.classList.contains('has-error') ? $('sessao-expirada') || $('aviso') : document.querySelector('main h1');
      focus.tabIndex = -1; focus.focus({preventScroll:true});
    }
  }
  $('abertura-ver-painel').addEventListener('click', fechar);
  $('abertura-ver-painel').hidden = false;
  document.addEventListener('abertura-liberada', fechar, {once:true});
  return {
    etapa,
    concluir() {
      if (screen.classList.contains('has-error')) return;
      document.dispatchEvent(new Event('abertura-essencial-pronta'));
      return liberacao;
    },
    falhar(error) {
      failed = true;
      document.dispatchEvent(new CustomEvent('inicializacao-falhou', {detail:{mensagem:error.message || 'Não foi possível abrir o painel. Recarregue a página para tentar novamente.', semSessao:error.status === 401}}));
    }
  };
}
