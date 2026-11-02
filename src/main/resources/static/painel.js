import { iniciarSessao,avisar,api } from './painel-base.js';
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
import { iniciarAdministracao } from './painel-admin.js';
import { iniciarFila } from './painel-fila.js';
import { iniciarBusca } from './painel-busca.js';
import { iniciarSaude } from './painel-saude.js';
const abertura = iniciarAbertura();
try {
  iniciarNavegacao();
  iniciarInterface();
  abertura.etapa(1, 'Conectando à sua sessão…');
  try{
    const versao=await api('/api/versao');
    if(versao.contrato!=='multioperador-V14-20261102')throw Object.assign(new Error('Versões incompatíveis. Inicie o JAR atual e recarregue o painel.'),{codigo:'VERSOES_INCOMPATIVEIS',incerto:false});
  }catch(error){if(error.status===404)error.message='Backend desatualizado. Inicie o JAR atual, sem sobrepor arquivos estáticos do checkout, e recarregue o painel.';throw error;}
  await iniciarSessao();
  await iniciarAdministracao();
  const linkFila=document.createElement('a');linkFila.href='#fila';linkFila.textContent='Fila compartilhada';document.querySelector('.sidebar nav').append(linkFila);
  await iniciarFila();
  await iniciarBusca();
  await iniciarSaude();
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
