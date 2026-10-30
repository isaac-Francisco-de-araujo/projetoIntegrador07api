const $ = id => document.getElementById(id);

export function iniciarAbertura() {
  const screen = $('abertura');
  const targets = [...document.querySelectorAll('body > main, body > aside, body > .skip-link')];
  const previousFocus = document.activeElement;
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const started = performance.now();
  let skip = false, closed = false, failed = false, releaseDelay;
  const minimum = 5000;

  screen.hidden = false;
  document.body.classList.add('is-loading');
  for (const target of targets) target.inert = true;

  function etapa(value, text) {
    $('abertura-progresso').value = value;
    $('abertura-status').textContent = text;
    $('abertura-etapa').textContent = `${String(Math.min(value + 1, 4)).padStart(2, '0')} / 04`;
    screen.querySelectorAll('[data-etapa]').forEach(node => {
      node.classList.toggle('is-complete', Number(node.dataset.etapa) <= value);
      node.classList.toggle('is-current', Number(node.dataset.etapa) === value + 1);
    });
  }

  async function fechar() {
    if (closed) return;
    closed = true;
    const focusInScreen = screen.contains(document.activeElement);
    screen.classList.add('is-leaving');
    if (!reduced.matches && !screen.classList.contains('motion-paused')) {
      await new Promise(resolve => setTimeout(resolve, 480));
    }
    screen.hidden = true;
    for (const target of targets) target.inert = false;
    document.body.classList.remove('is-loading');
    document.body.classList.add('workspace-entered');
    document.dispatchEvent(new Event('workspace-pronto'));
    if (focusInScreen) {
      const focus = failed ? $('aviso') : document.querySelector('main h1');
      focus.tabIndex = -1;
      focus.focus({ preventScroll: true });
    } else if (previousFocus && previousFocus !== document.body) previousFocus.focus({ preventScroll: true });
  }

  $('abertura-pular').addEventListener('click', () => {
    skip = true;
    releaseDelay?.();
    $('abertura-pular').textContent = 'Aguardando os dados…';
    $('abertura-pular').disabled = true;
  });
  $('abertura-movimento').addEventListener('click', event => {
    const paused = screen.classList.toggle('motion-paused');
    event.currentTarget.setAttribute('aria-pressed', String(paused));
    event.currentTarget.textContent = paused ? 'Retomar animação ▷' : 'Pausar animação Ⅱ';
  });
  $('abertura-ver-painel').addEventListener('click', fechar);
  etapa(0, 'Preparando seu espaço de trabalho…');

  return {
    etapa,
    async concluir() {
      etapa(4, 'Tudo conectado. Seu workspace está pronto.');
      const remaining = minimum - (performance.now() - started);
      if (!skip && remaining > 0) await new Promise(resolve => {
        const timer = setTimeout(resolve, remaining);
        releaseDelay = () => { clearTimeout(timer); resolve(); };
      });
      await fechar();
    },
    falhar(error) {
      failed = true;
      screen.classList.add('has-error', 'motion-paused');
      $('abertura-status').textContent = 'O workspace precisa da sua atenção.';
      $('abertura-erro-texto').textContent = error.message || 'Não foi possível concluir o carregamento. Confira sua conexão e tente novamente.';
      $('abertura-erro').hidden = false;
      $('abertura-pular').hidden = true;
      $('abertura-movimento').hidden = true;
    }
  };
}
