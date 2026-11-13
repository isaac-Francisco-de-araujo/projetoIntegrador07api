import { $, avisar } from './painel-base.js';

let pending = null, busy = false;
const dialog = $('confirmar-exclusao');
function fechar() {
  if (busy) return;
  const origin = pending?.origem; pending = null; dialog.close();
  if (origin?.isConnected) origin.focus({preventScroll:true}); else $('atualizar').focus({preventScroll:true});
}
export function confirmarAcao({ titulo, descricao, regra, textoConfirmar, acao, origem = document.activeElement }) {
  if (busy || document.querySelector('dialog[open]')) return false;
  pending = { acao, origem };
  $('titulo-exclusao').textContent = titulo; $('descricao-exclusao').textContent = descricao; $('regra-exclusao').textContent = regra;
  $('executar-exclusao').textContent = textoConfirmar; dialog.showModal(); return true;
}
$('cancelar-exclusao').addEventListener('click', fechar);
dialog.addEventListener('cancel', event => { event.preventDefault(); fechar(); });
$('form-exclusao').addEventListener('submit', async event => {
  event.preventDefault(); if (busy || !pending) return;
  const request = pending; busy = true; $('executar-exclusao').disabled = true; $('cancelar-exclusao').disabled = true;
  $('executar-exclusao').textContent = 'Processando…';
  try { await request.acao(); } catch (error) { avisar(error.message,true); }
  finally { busy = false; $('executar-exclusao').disabled = false; $('cancelar-exclusao').disabled = false; fechar(); }
});
