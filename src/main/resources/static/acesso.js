'use strict';
// Compatibilidade dos favoritos /#aba. Nenhuma API ou módulo do painel é carregado aqui.
if (document.body.dataset.entrada === 'apresentacao' && location.hash) {
  location.replace(new URL('painel', location.href).pathname + location.hash);
}
// O fragmento permanece local ao navegador; nunca é um destino de redirecionamento externo.
const formulario = document.querySelector('form[data-login]');
if (formulario && location.hash) formulario.action += location.hash;
