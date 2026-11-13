// Texto de todas as telas em um único lugar. As variantes só orientam ações permitidas.
export function conteudoAjuda(pagina, { operar = false, administrar = false, envioReal = false } = {}) {
  const restrita = {
    titulo:'Quem pode usar esta tela?',
    resumo:'Esta tela é reservada à administração.',
    passos:['Use as telas de consulta disponíveis no seu menu.', 'Peça ao administrador para revisar as configurações ou os acessos da equipe.']
  };
  const consultasImportacao = {
    titulo:'Como consultar documentos recebidos?',
    resumo:'A importação de arquivos exige acesso de operador ou administrador.',
    passos:['Abra Viagens e documentos ou Buscar documentos.', 'Localize a viagem e consulte seus arquivos.'],
    observacao:'Para importar novos arquivos, peça a um operador ou administrador.'
  };
  const textos = {
    inicio: {
      titulo:'Por onde começar?',
      resumo:'Veja os indicadores do acervo e o que precisa de atenção.',
      passos:['Confira os totais e as últimas viagens.', 'Abra uma viagem, a fila ou as pendências para consultar os detalhes.']
    },
    fila: {
      titulo:'Como acompanhar o trabalho?',
      resumo:'Acompanhe os itens da equipe e quem está responsável por eles.',
      passos:operar
        ? ['Filtre por situação ou responsável.', 'Use Assumir em um item sem responsável antes de trabalhar.', 'Abra Detalhe e recuperação para conferir o próximo passo.']
        : ['Filtre por situação ou responsável.', 'Abra Detalhe e recuperação para consultar o item e seu histórico.'],
      observacao:'Concluir uma etapa não comprova entrega de e-mail.'
    },
    pacotes: operar ? {
      titulo:'Como importar um ZIP?',
      resumo:'Organize documentos de uma ou várias viagens a partir de um ZIP com XMLs de CT-e e PDFs.',
      passos:['Selecione o ZIP e clique em Conferir arquivos.', 'Abra os PDFs e associe manualmente cada um às viagens corretas.', 'Continue para importar, confira o resumo e confirme.'],
      observacao:'Se a cópia externa falhar, a importação fica preservada; retome somente a cópia. A confirmação ao motorista é opcional.'
    } : consultasImportacao,
    importacao: operar ? {
      titulo:'Como adicionar um arquivo?',
      resumo:'Adicione um XML de CT-e ou prepare um PDF para uma viagem existente.',
      passos:['Escolha ou arraste um XML ou PDF.', 'Para XML, use Importar XML. Para PDF, escolha a viagem e seu CT-e e use Conferir PDF.', 'No caso de PDF, confira o pacote, associe o arquivo e confirme a importação.'],
      observacao:'O PDF precisa de um CT-e ativo da viagem. A conferência visual não é validação fiscal.'
    } : consultasImportacao,
    viagens: {
      titulo:'Como consultar uma viagem?',
      resumo:'Encontre viagens e consulte seus XMLs, PDFs e faturas.',
      passos:['Pesquise pelo número do CT-e ou cliente e ajuste os filtros.', 'Use Ver documentos para abrir os arquivos da viagem.', operar ? 'Selecione os arquivos e abra Conferir seleção para envio, se necessário.' : 'Consulte os detalhes ou baixe os arquivos disponíveis.'],
      observacao:'Voltar à lista de viagens preserva os filtros e a posição da lista.'
    },
    clientes: {
      titulo:'Como consultar um cliente?',
      resumo:'Localize clientes, seus contatos e as viagens vinculadas.',
      passos:['Pesquise pelo nome ou CNPJ.', 'Abra Detalhes ou Ver viagens para continuar a consulta.'],
      observacao:'Os clientes são identificados na importação dos CT-es; contatos ausentes não são preenchidos automaticamente.'
    },
    busca: {
      titulo:'Como localizar um documento?',
      resumo:'Pesquise clientes, viagens, arquivos e pacotes no acervo.',
      passos:['Escolha a categoria e informe o que procura.', 'Use Mais filtros para refinar a pesquisa.', 'Abra Ver detalhes para consultar o resultado e as ações disponíveis.']
    },
    email: {
      titulo:'Como acompanhar os envios?',
      resumo:'Confira a seleção de documentos e acompanhe as tentativas de envio da viagem.',
      passos:operar
        ? ['Selecione a viagem e os documentos em Viagens e documentos.', 'Informe o destinatário e gere a prévia.', 'Confira mensagem e anexos antes de confirmar o envio.']
        : ['Escolha uma viagem em Viagens e documentos.', 'Abra os históricos na Central de envios para consultar as tentativas.'],
      observacao:operar
        ? 'A conferência persistida verifica a seleção sem enviar e funciona sem SMTP. A confirmação real depende da configuração documental indicada na tela e de nova conferência se o modo mudar. Aceite pelo provedor não comprova recebimento ou leitura. Resultado desconhecido exige consulta antes de repetir.'
        : `${!operar ? 'Seu acesso permite consulta, sem confirmar envios.' : 'O envio está indisponível nesta sessão; procure o administrador.'} Aceite pelo provedor não comprova recebimento. Confira resultados desconhecidos antes de repetir.`
    },
    processamentos: {
      titulo:'Como revisar uma pendência?',
      resumo:'Consulte importações, cópias e tentativas que precisam de atenção.',
      passos:['Filtre pela situação e localize o item.', 'Abra os detalhes e leia a causa e a orientação.', operar ? 'Use a ação de recuperação disponível após conferir o resultado.' : 'Informe ao operador responsável o problema encontrado.'],
      observacao:'Fechar os detalhes não resolve a pendência. Resultado desconhecido exige conferência antes de repetir.'
    },
    configuracoes: administrar ? {
      titulo:'Como ajustar a operação?',
      resumo:'Defina o remetente e o destino autorizado das cópias externas.',
      passos:['Revise Conta de envio e Pasta externa.', 'Escolha se deseja copiar após importar e salve as alterações.', 'Abra os requisitos da conta ou as ferramentas de teste somente quando precisar.'],
      observacao:'Salvar o remetente não autentica a conta. A pasta é do servidor da operação; copiar não move nem apaga os originais.'
    } : restrita,
    administracao: administrar ? {
      titulo:'Como gerenciar os acessos?',
      resumo:'Cadastre operadores e mantenha as permissões da equipe.',
      passos:['Pesquise o operador ou use Adicionar operador.', 'Em Gerenciar, revise o papel e se o acesso está ativo.', 'Para trocar a senha, abra Redefinir acesso.'],
      observacao:'Entregue senhas por um canal seguro. Redefinir o acesso encerra as sessões anteriores.'
    } : restrita,
    saude: {
      titulo:'O que esta verificação informa?',
      resumo:'Veja verificações específicas do banco, das pastas, do e-mail e das informações de backup.',
      passos:['Atualize a verificação e leia os itens que precisam de atenção.', 'Abra Ver detalhes para entender o que foi verificado.', administrar ? 'Siga a orientação ou revise Configurações quando indicado.' : 'Informe os problemas ao administrador.'],
      observacao:'Banco disponível não comprova integridade completa. Não verificado não é sucesso; um manifesto não comprova backup nem restauração.'
    },
    'pacotes-importados': {
      titulo:'Como consultar as importações?',
      resumo:'Consulte os ZIPs recebidos, seus arquivos e as cópias externas.',
      passos:['Localize o ZIP na lista ou consulte os arquivados.', 'Use Ver exportações ou Ver avisos para acompanhar o histórico.', operar ? 'Para excluir, confira os arquivos e vínculos antes de confirmar o arquivamento.' : 'Abra os arquivos para consultar os vínculos disponíveis.'],
      observacao:'Arquivar preserva arquivos e histórico; arquivos compartilhados afetam todas as viagens vinculadas.'
    }
  };
  return textos[pagina];
}
