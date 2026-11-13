import { element, podeOperar, podeAdministrar, state } from './painel-base.js';
import { conteudoAjuda } from './painel-ajuda-conteudo.js';

export function iniciarAjuda() {
  const tituloPagina = document.querySelector('.page-heading h1');
  const linha = element('div', null, 'page-title-line');
  tituloPagina.before(linha); linha.append(tituloPagina);
  const botao = element('button', null, 'context-help-trigger');
  botao.id = 'abrir-ajuda'; botao.type = 'button';
  botao.setAttribute('aria-haspopup', 'dialog');
  botao.setAttribute('aria-expanded', 'false');
  botao.setAttribute('aria-controls', 'ajuda-contextual');
  const icone = element('span', '?'); icone.setAttribute('aria-hidden', 'true');
  botao.append(icone); linha.append(botao);

  // Popover nativo: não modal, sem overlay bloqueante, Escape/clique fora e uma instância.
  const painel = element('section', null, 'context-help');
  painel.id = 'ajuda-contextual'; painel.popover = 'auto'; painel.tabIndex = -1;
  botao.setAttribute('popovertarget', painel.id);
  painel.setAttribute('role', 'dialog'); painel.setAttribute('aria-modal', 'false');
  painel.setAttribute('aria-labelledby', 'ajuda-titulo');
  painel.setAttribute('aria-describedby', 'ajuda-resumo');
  const titulo = element('h2'); titulo.id = 'ajuda-titulo';
  const resumo = element('p'); resumo.id = 'ajuda-resumo';
  const passos = element('ol'); passos.id = 'ajuda-passos';
  const observacao = element('p', null, 'context-help-note'); observacao.id = 'ajuda-observacao';
  const fechar = element('button', 'Fechar', 'button secondary context-help-close'); fechar.type = 'button';
  painel.append(titulo, resumo, passos, observacao, fechar);
  document.body.append(painel);
  const aberta = () => painel.matches(':popover-open');
  function fecharAjuda(restaurar = false) {
    if (!aberta()) return;
    painel.hidePopover(); botao.setAttribute('aria-expanded', 'false');
    if (restaurar) botao.focus({preventScroll:true});
  }
  function posicionar() {
    if (!aberta()) return;
    const ancora = botao.getBoundingClientRect(), largura = document.documentElement.clientWidth, altura = innerHeight;
    if (ancora.bottom <= 0 || ancora.top >= altura) { fecharAjuda(); return; }
    const caixa = painel.getBoundingClientRect(), margem = 12, intervalo = 8;
    const esquerda = Math.max(margem, Math.min(ancora.left, largura - caixa.width - margem));
    const abaixo = ancora.bottom + intervalo;
    const topo = abaixo + caixa.height <= altura - margem ? abaixo
      : Math.max(margem, Math.min(ancora.top - intervalo - caixa.height, altura - caixa.height - margem));
    painel.style.left = `${esquerda}px`; painel.style.top = `${topo}px`;
  }
  function atualizar() {
    fecharAjuda();
    botao.setAttribute('aria-label', `Ajuda sobre ${tituloPagina.textContent.trim()}`);
  }
  botao.addEventListener('click', event => {
    // Vincular o acionador evita que o light-dismiss feche antes deste mesmo clique.
    event.preventDefault();
    if (aberta()) { fecharAjuda(true); return; }
    const conteudo = conteudoAjuda(document.querySelector('main').dataset.paginaAtual,
      {operar:podeOperar(), administrar:podeAdministrar(), envioReal:state.envioReal});
    if (!conteudo) return;
    titulo.textContent = conteudo.titulo; resumo.textContent = conteudo.resumo;
    passos.replaceChildren(...conteudo.passos.map(texto => element('li', texto)));
    observacao.textContent = conteudo.observacao || ''; observacao.hidden = !conteudo.observacao;
    painel.showPopover({source:botao}); botao.setAttribute('aria-expanded', 'true');
    painel.scrollTop = 0;
    posicionar(); painel.focus({preventScroll:true});
  });
  fechar.addEventListener('click', () => fecharAjuda(true));
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && aberta()) {
      event.preventDefault(); event.stopPropagation(); fecharAjuda(true);
    }
  }, true);
  painel.addEventListener('toggle', () => {
    botao.setAttribute('aria-expanded', String(aberta()));
    if (!aberta() && painel.contains(document.activeElement)) botao.focus({preventScroll:true});
  });
  document.addEventListener('pagina-alterada', atualizar);
  document.addEventListener('sessao-expirada', () => fecharAjuda());
  window.addEventListener('resize', posicionar);
  window.addEventListener('scroll', posicionar, true);
  atualizar();
}
