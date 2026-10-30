import { iniciarSessao,avisar } from './painel-base.js';
import { iniciarDocumentos,atualizar,atualizarSelecao } from './painel-documentos.js';
import { configurarEmail } from './painel-email.js';
import { configurarPacotes } from './painel-pacotes.js';
import { iniciarHistorico } from './painel-historico.js';
import { iniciarImportacao } from './painel-importacao.js';
import { iniciarGerenciamentoArquivos } from './painel-arquivos.js';
import { iniciarInterface } from './painel-interface.js';
import { iniciarAbertura } from './painel-abertura.js';
import { iniciarNavegacao } from './painel-navegacao.js';
import { iniciarConfiguracoes } from './painel-configuracoes.js';
import { iniciarRecuperacao } from './painel-recuperacao.js';
const abertura = iniciarAbertura();
try {
  iniciarNavegacao();
  iniciarInterface();
  abertura.etapa(1, 'Conectando à sua sessão…');
  await iniciarSessao();
  iniciarRecuperacao();
  await iniciarConfiguracoes();
  configurarEmail({atualizarSelecao});configurarPacotes({atualizar});
  iniciarImportacao({atualizar});
  abertura.etapa(2, 'Consultando viagens e documentos…');
  await iniciarDocumentos();
  abertura.etapa(3, 'Organizando seu workspace…');
  await iniciarHistorico();
  await iniciarGerenciamentoArquivos({atualizar});
  await abertura.concluir();
}catch(error){const seguro=error.status||error.codigo?error:Object.assign(new Error('Falha ao inicializar a interface. Consulte a referência e recarregue sem repetir operações de negócio.'),{incerto:false,codigo:'FALHA_INTERFACE'});avisar(seguro,true);abertura.falhar(seguro);}
