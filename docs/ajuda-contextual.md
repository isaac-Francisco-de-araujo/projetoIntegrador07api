# Ajuda contextual das abas — 13/11/2026

## Implementação e validação

Um botão `?` acompanha o título principal das **13 telas**, incluindo **Importações**
(acesso por Importações anteriores). Nomes do título e do menu foram preservados:
Fila compartilhada / Fila de trabalho, Busca integrada / Buscar documentos,
Central de envios / Envios.

O componente único usa Popover nativo não modal, sem biblioteca ou animação nova.
Abre por clique, toque, Enter ou Espaço. Fecha pelo próprio acionador, Fechar,
Escape ou clique fora; a navegação e a expiração da sessão também fecham a ajuda.
Não bloqueia o conteúdo nem altera a altura das telas. O foco entra no painel
identificado por título e resumo; Fechar/Escape devolvem o foco ao acionador,
sem reposicionar a página. Navegar mantém o foco definido pelo roteador.

O painel é posicionado junto ao botão e limitado ao viewport. Quando necessário
no zoom, tem rolagem própria e sempre reabre no início. Conteúdo e variantes por
permissão ficam somente em `front/painel-ajuda-conteudo.js`; as instruções são
calculadas com o papel e o modo de envio efetivos da sessão. Não há consultas ou
alterações HTTP para abrir a ajuda. As descrições anteriores das páginas permanecem.

Arquivos desta implementação:

- `front/painel-ajuda-conteudo.js`: textos e variantes.
- `front/painel-ajuda.js`: marcação, abertura, fechamento, foco e posicionamento.
- `front/painel-ajuda.css`: estilos compartilhados, tokens existentes e adaptação móvel.
- `front/painel.js` e `front/index.html`: inicialização após verificar sessão e estilo.
- `scripts/testar-ajuda-contextual.cjs`: aceitação de interação, permissões e zoom.
- Este documento e o link no README.

### Testes executados nesta entrega

Build externo limpo, Java 25:

```text
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-ajuda-20261113-build -DargLine=-Xmx768m clean test package
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-ajuda-20261113-build -DskipTests package
```

- **407 testes contabilizados, 406 aprovados, 1 ignorado**, zero falhas/erros.
  Ignorado: condição de symlink do Windows. Build/empacotamento aprovados.
- Refinamentos finais de interação da ajuda foram reempacotados e validados no
  navegador sobre o mesmo JAR, sem sobreposição dos estáticos do checkout.
- `testar-ajuda-contextual.cjs`: **6 grupos aprovados**. As 13 telas em claro/escuro,
  **1366×768 e 390×844**; clique, alternância pelo acionador, toque, Enter, Espaço,
  Fechar, Escape, clique fora, nome/atributos ARIA, foco, ausência de deslocamento,
  fechamento ao navegar/expirar, rascunho e filtros preservados. ADMIN, OPERADOR
  e CONSULTA autenticados em HTTPS local, bases H2 novas e isoladas.
- Zoom real de Chrome **200%** nas 13 telas e ambos os temas: largura CSS **675 px**,
  altura **308 px**, DPR **2**. Painel dentro do viewport, sem overflow horizontal,
  conteúdo vertical acessível por rolagem, botão Fechar acessível e reabertura no topo.
- `testar-entrada.cjs`: **9 grupos aprovados**, incluindo matriz visual de 12 abas,
  três resoluções e dois temas, login, CSRF, três papéis, HTTPS raiz e `/rota`.
- `testar-uso-diario.cjs`: **16 grupos aprovados**, incluindo ZIP XML/PDF sintético,
  associação, confirmação, erros, rascunhos/conflitos, recuperação, histórico e permissões.
- Zero erros JavaScript nos roteiros concluídos. Nenhuma alteração HTTP ao usar
  a ajuda e nenhum envio externo. Não houve acesso a banco operacional.

As rodadas intermediárias identificaram uma disputa entre light-dismiss e novo
clique no acionador, além da posição interna preservada ao reabrir após rolar.
Corrigidos pelo vínculo nativo `popovertarget` e reinício da rolagem do painel.
O teste aguarda o estado carregado de Configurações antes de medir layout,
para não atribuir à ajuda a chegada assíncrona da última verificação.

### Capturas externas

Raiz: `C:/Users/florb/AppData/Local/Temp/opencode/`.

- **Aceite final:** `rotabrasil-ajuda-20261113-aceite-final/` — 78 capturas e
  `resultado.json` com resultados, medição do zoom e textos efetivamente exibidos.
  - Desktop: `claro-pacotes-1366.png`, `escuro-saude-1366.png`.
  - Celular: `claro-processamentos-390.png`, `escuro-configuracoes-390.png`.
  - Demais telas: `{claro|escuro}-{rota}-{1366|390}.png`.
  - Zoom: `zoom-200-{claro|escuro}-{rota}.png`.
- **Regressão de entrada:** `rotabrasil-ajuda-20261113-entrada/`.
- **Regressão diária:** `rotabrasil-ajuda-20261113-diario/`.
- Maven: `rotabrasil-ajuda-20261113-maven.log` e `rotabrasil-ajuda-20261113-package.log`.

As capturas contêm apenas a aplicação no navegador. As de zoom usam a captura
da superfície do Chrome sem recorte CSS, evitando cortar a imagem quando o DPR
muda pelo zoom real. Pastas intermediárias sem `aceite-final` não são o aceite.

A instância de desenvolvimento iniciada nesta conversa foi atualizada em
`http://localhost:8080`, preservando seu banco/acervo temporários. Migrações
permanecem desligadas nessa base existente e e-mail permanece SIMULADO. Backend
e módulo de ajuda empacotado responderam HTTP 200.

Limitações: não houve teste humano com leitor de tela, Firefox/Safari ou dispositivos
físicos; toque foi validado pelo navegador com suporte a eventos de toque. O componente
usa a API Popover dos navegadores atuais. SMTP/OAuth2 externos não foram exercitados.

## Textos finais para revisão

Os textos abaixo correspondem à variante de operador/administrador. As diferenças
de CONSULTA, telas administrativas e envio indisponível estão ao final.

### 1. Visão geral — “Por onde começar?”

Veja os indicadores do acervo e o que precisa de atenção.

1. Confira os totais e as últimas viagens.
2. Abra uma viagem, a fila ou as pendências para consultar os detalhes.

### 2. Fila compartilhada — “Como acompanhar o trabalho?”

Acompanhe os itens da equipe e quem está responsável por eles.

1. Filtre por situação ou responsável.
2. Use Assumir em um item sem responsável antes de trabalhar.
3. Abra Detalhe e recuperação para conferir o próximo passo.

**Observação:** Concluir uma etapa não comprova entrega de e-mail.

### 3. Importar ZIP — “Como importar um ZIP?”

Organize documentos de uma ou várias viagens a partir de um ZIP com XMLs de CT-e e PDFs.

1. Selecione o ZIP e clique em Conferir arquivos.
2. Abra os PDFs e associe manualmente cada um às viagens corretas.
3. Continue para importar, confira o resumo e confirme.

**Observação:** Se a cópia externa falhar, a importação fica preservada; retome somente
a cópia. A confirmação ao motorista é opcional.

### 4. Importar XML ou PDF — “Como adicionar um arquivo?”

Adicione um XML de CT-e ou prepare um PDF para uma viagem existente.

1. Escolha ou arraste um XML ou PDF.
2. Para XML, use Importar XML. Para PDF, escolha a viagem e seu CT-e e use Conferir PDF.
3. No caso de PDF, confira o pacote, associe o arquivo e confirme a importação.

**Observação:** O PDF precisa de um CT-e ativo da viagem. A conferência visual não
é validação fiscal.

### 5. Viagens e documentos — “Como consultar uma viagem?”

Encontre viagens e consulte seus XMLs, PDFs e faturas.

1. Pesquise pelo número do CT-e ou cliente e ajuste os filtros.
2. Use Ver documentos para abrir os arquivos da viagem.
3. Selecione os arquivos e abra Conferir seleção para envio, se necessário.

**Observação:** Voltar à lista de viagens preserva os filtros e a posição da lista.

### 6. Clientes — “Como consultar um cliente?”

Localize clientes, seus contatos e as viagens vinculadas.

1. Pesquise pelo nome ou CNPJ.
2. Abra Detalhes ou Ver viagens para continuar a consulta.

**Observação:** Os clientes são identificados na importação dos CT-es; contatos
ausentes não são preenchidos automaticamente.

### 7. Busca integrada — “Como localizar um documento?”

Pesquise clientes, viagens, arquivos e pacotes no acervo.

1. Escolha a categoria e informe o que procura.
2. Use Mais filtros para refinar a pesquisa.
3. Abra Ver detalhes para consultar o resultado e as ações disponíveis.

### 8. Central de envios — “Como acompanhar os envios?”

Confira a seleção de documentos e acompanhe as tentativas de envio da viagem.

**Com acesso de operação e envio disponível:**

1. Selecione a viagem e os documentos em Viagens e documentos.
2. Informe o destinatário e gere a prévia.
3. Confira mensagem e anexos antes de confirmar o envio.

**Observação:** Aceite pelo provedor não comprova recebimento ou leitura. Se o
resultado for desconhecido, confira o histórico antes de repetir.

**Com envio indisponível ou acesso de consulta:**

1. Escolha uma viagem em Viagens e documentos.
2. Abra os históricos na Central de envios para consultar as tentativas.

**Observação para operador sem envio:** O envio está indisponível nesta sessão;
procure o administrador. Aceite pelo provedor não comprova recebimento. Confira
resultados desconhecidos antes de repetir.

**Observação para CONSULTA:** Seu acesso permite consulta, sem confirmar envios.
Aceite pelo provedor não comprova recebimento. Confira resultados desconhecidos
antes de repetir.

### 9. Processamentos e pendências — “Como revisar uma pendência?”

Consulte importações, cópias e tentativas que precisam de atenção.

1. Filtre pela situação e localize o item.
2. Abra os detalhes e leia a causa e a orientação.
3. Use a ação de recuperação disponível após conferir o resultado.

**Observação:** Fechar os detalhes não resolve a pendência. Resultado desconhecido
exige conferência antes de repetir.

### 10. Configurações — “Como ajustar a operação?”

Defina o remetente e o destino autorizado das cópias externas.

1. Revise Conta de envio e Pasta externa.
2. Escolha se deseja copiar após importar e salve as alterações.
3. Abra os requisitos da conta ou as ferramentas de teste somente quando precisar.

**Observação:** Salvar o remetente não autentica a conta. A pasta é do servidor da
operação; copiar não move nem apaga os originais.

### 11. Operadores — “Como gerenciar os acessos?”

Cadastre operadores e mantenha as permissões da equipe.

1. Pesquise o operador ou use Adicionar operador.
2. Em Gerenciar, revise o papel e se o acesso está ativo.
3. Para trocar a senha, abra Redefinir acesso.

**Observação:** Entregue senhas por um canal seguro. Redefinir o acesso encerra as
sessões anteriores.

### 12. Saúde do sistema — “O que esta verificação informa?”

Veja verificações específicas do banco, das pastas, do e-mail e das informações de backup.

1. Atualize a verificação e leia os itens que precisam de atenção.
2. Abra Ver detalhes para entender o que foi verificado.
3. Siga a orientação ou revise Configurações quando indicado.

**Observação:** Banco disponível não comprova integridade completa. Não verificado
não é sucesso; um manifesto não comprova backup nem restauração.

### 13. Importações — “Como consultar as importações?”

Consulte os ZIPs recebidos, seus arquivos e as cópias externas.

1. Localize o ZIP na lista ou consulte os arquivados.
2. Use Ver exportações ou Ver avisos para acompanhar o histórico.
3. Para excluir, confira os arquivos e vínculos antes de confirmar o arquivamento.

**Observação:** Arquivar preserva arquivos e histórico; arquivos compartilhados
afetam todas as viagens vinculadas.

### Variantes de permissão

- **CONSULTA / Fila:** dois passos: “Filtre por situação ou responsável.” e
  “Abra Detalhe e recuperação para consultar o item e seu histórico.” A observação permanece.
- **CONSULTA / ZIP e XML/PDF:** título “Como consultar documentos recebidos?”;
  resumo “A importação de arquivos exige acesso de operador ou administrador.”;
  passos “Abra Viagens e documentos ou Buscar documentos.” e “Localize a viagem
  e consulte seus arquivos.”; observação “Para importar novos arquivos, peça a
  um operador ou administrador.”
- **CONSULTA / Viagens:** terceiro passo substituído por “Consulte os detalhes ou
  baixe os arquivos disponíveis.”
- **CONSULTA / Pendências:** terceiro passo substituído por “Informe ao operador
  responsável o problema encontrado.”
- **CONSULTA / Importações:** terceiro passo substituído por “Abra os arquivos
  para consultar os vínculos disponíveis.”
- **Não ADMIN / Saúde:** terceiro passo substituído por “Informe os problemas ao
  administrador.”
- **Não ADMIN / Configurações e Operadores**, em acesso direto: título “Quem pode
  usar esta tela?”; resumo “Esta tela é reservada à administração.”; passos “Use as
  telas de consulta disponíveis no seu menu.” e “Peça ao administrador para revisar
  as configurações ou os acessos da equipe.” Não libera a tela nem altera permissões.
