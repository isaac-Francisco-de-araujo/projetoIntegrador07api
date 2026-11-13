import { iniciarSessao,avisar,api } from './painel-base.js';
import { iniciarDocumentos,atualizar,atualizarSelecao } from './painel-documentos.js';
import { configurarEmail } from './painel-email.js';
import { configurarPacotes } from './painel-pacotes.js';
import { iniciarImportacao } from './painel-importacao.js';
import { iniciarInterface } from './painel-interface.js';
import { iniciarAbertura } from './painel-abertura.js';
import { iniciarNavegacao } from './painel-navegacao.js';
import { iniciarRecuperacao } from './painel-recuperacao.js';
import { iniciarAjuda } from './painel-ajuda.js';
import { verificarVersao } from './painel-versao.js';
const abertura = iniciarAbertura();
const telas = {
  administracao: () => import('./painel-admin.js').then(m => m.iniciarAdministracao()),
  fila: () => import('./painel-fila.js').then(m => m.iniciarFila()),
  busca: () => import('./painel-busca.js').then(m => m.iniciarBusca()),
  saude: () => import('./painel-saude.js').then(m => m.iniciarSaude()),
  configuracoes: () => import('./painel-configuracoes.js').then(m => m.iniciarConfiguracoes())
};
const iniciadas = new Set();
async function abrirTela(pagina) {
  if (!telas[pagina] || iniciadas.has(pagina)) return;
  const alvo = document.getElementById(pagina);
  alvo.querySelector(':scope > [data-falha-modulo]')?.remove();
  iniciadas.add(pagina);
  try { await telas[pagina](); }
  catch (error) {
    iniciadas.delete(pagina);
    try { await api('/api/sessao'); } catch (sessao) { if(sessao.status===401)return; }
    const aviso = document.createElement('div'); aviso.dataset.falhaModulo=''; aviso.className = 'notice error'; aviso.setAttribute('role','alert');
    aviso.textContent = '⛔ Não foi possível abrir esta tela. Tente novamente.';
    const repetir = document.createElement('button'); repetir.type = 'button'; repetir.textContent = 'Tentar novamente';
    repetir.addEventListener('click', () => { aviso.remove(); abrirTela(pagina); }); aviso.append(repetir); alvo.prepend(aviso);
  }
}
try {
  iniciarNavegacao();
  iniciarInterface();
  abertura.etapa('Verificando a versão da aplicação…');
  try{
    await verificarVersao();
  }catch(error){if(error.status===404)error.message='Backend desatualizado. Inicie o JAR atual, sem sobrepor arquivos estáticos do checkout, e recarregue o painel.';throw error;}
   abertura.etapa('Verificando sua sessão…');
   await iniciarSessao();
   iniciarAjuda();
   configurarEmail({atualizarSelecao});configurarPacotes({atualizar});
   iniciarImportacao({atualizar});
   document.addEventListener('pagina-alterada', e => abrirTela(e.detail.pagina));
   iniciarRecuperacao();
   // Registrar eventos antes das consultas independentes; nenhuma delas libera sessão/contrato.
   iniciarDocumentos().catch(()=>{document.getElementById('estado-indicadores').classList.add('error');});
   telas['pacotes-importados']=() => import('./painel-arquivos.js').then(m => m.iniciarGerenciamentoArquivos({atualizar}));
   import('./painel-historico.js').then(m=>m.iniciarHistorico()).catch(error=>{document.getElementById('contador-pendencias').textContent='!';});
   await abertura.concluir();
   abrirTela(document.querySelector('main').dataset.paginaAtual);
}catch(error){const seguro=error.status||error.codigo?error:Object.assign(new Error('Falha ao inicializar a interface. Consulte a referência e recarregue sem repetir operações de negócio.'),{incerto:false,codigo:'FALHA_INTERFACE'});avisar(seguro,true);abertura.falhar(seguro);}
