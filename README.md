# RotaBrasil

**Gestão de documentos de viagens: do recebimento do arquivo à conferência e ao envio manual.**

O RotaBrasil é uma aplicação web para receber **CT-es XML e documentos PDF**, organizar
os arquivos por viagem, associar faturas, conferir informações e acompanhar tentativas
de envio de e-mail. O código do frontend fica em **`front/`** e o backend Java,
configurações e testes em **`back/`**. O Maven reúne os dois na mesma aplicação
Spring Boot, mantendo a comunicação com a API, login e sessão na mesma origem HTTP.

**Estado documentado:** novembro de 2026 · Java 25 · Spring Boot 3.5.6 · código requer esquema de banco **V15**.
O modo padrão de e-mail é **SIMULADO**. O adaptador SMTP real existe no código e depende
de ativação/configuração explícitas e homologação operacional.

**Uso diário compartilhado:** login individual, proteção de edição concorrente,
fila com responsáveis, busca paginada no servidor e painel de saúde já integram a
mesma aplicação. Consulte [o guia multioperador](docs/uso-compartilhado.md) para
permissões, teste com dois navegadores e implantação centralizada com HTTPS.

**Etapa E-mail (31/10/2026):** implementada a confirmação manual de recebimento ao
motorista, com conferência persistida, histórico idempotente e SMTP OAuth2 condicional.
O padrão continua **SIMULADO**. O uso da conta Outlook da empresa foi informado;
plataforma Microsoft, tenant e políticas SMTP ainda precisam ser confirmados.
Ativação REAL e homologação dependem da autorização e da configuração externa da
conta. O login protegido já está disponível; isso não ativa
SMTP real. Nenhum e-mail externo foi enviado.
Veja [o contrato da confirmação ao motorista](docs/confirmacao-motorista.md).
As correções anteriores foram preservadas: [relatório da estabilização](docs/estabilizacao.md).

**Regra comercial atual:** documentos e fatura são enviados **ao cliente**, com
seleção e destinatário explícitos. Não há e-mail de motorista informado; confirmação
ao motorista é opcional e não bloqueia importação. Download no Outlook e upload do
ZIP são manuais, e o CT-e de um PDF é identificado abrindo o documento.

### Documento geral do projeto

A [visão geral do sistema](docs/visao-geral-do-sistema.md), também disponível em
[PDF](docs/visao-geral-do-sistema.pdf) e [Word](docs/visao-geral-do-sistema.docx),
preserva o levantamento de **21/10/2026** e seus diagramas. Para o estado atual V15,
consulte este README e os contratos de uso compartilhado e da Etapa E-mail.

## Sumário

- [Atualizações da versão atual](#atualizações-da-versão-atual)
- [Visão geral](#visão-geral)
- [Funcionalidades atuais](#funcionalidades-atuais)
- [Tecnologias utilizadas](#tecnologias-utilizadas)
- [Arquitetura e organização](#arquitetura-e-organização)
- [Mapa dos módulos do backend e frontend](#mapa-dos-módulos-do-backend-e-frontend)
- [Frontend e navegação](#frontend-e-navegação)
- [Uso diário por vários operadores](#uso-diário-por-vários-operadores)
- [Como utilizar o sistema](#como-utilizar-o-sistema)
- [Fluxos de documentos](#fluxos-de-documentos)
- [Diagnóstico, Central de Pendências e recuperação](#diagnóstico-central-de-pendências-e-recuperação)
- [Banco de dados e armazenamento](#banco-de-dados-e-armazenamento)
- [Segurança e perfis de execução](#segurança-e-perfis-de-execução)
- [Como executar localmente](#como-executar-localmente)
- [Configurações principais](#configurações-principais)
- [Configurações da operação, notificações e exportação](#configurações-da-operação-notificações-e-exportação)
- [Confirmação de recebimento ao motorista](#confirmação-de-recebimento-ao-motorista)
- [Envio de e-mail](#envio-de-e-mail)
- [API REST](#api-rest)
- [Testes e verificações](#testes-e-verificações)
- [Limitações e próximos passos](#limitações-e-próximos-passos)
- [Documentação complementar](#documentação-complementar)

## Atualizações da versão atual

### Central de envios — preparação independente do SMTP — 13/11/2026

**Rastreamento de disabled e Configurações:** identificadas todas as atribuições ao
destinatário e seus chamadores; no JAR antigo a condição ativa é `!state.envioReal`.
Configurações agora explica os bloqueios de seus fieldsets e mantém recuperação
consistente após falha de consulta. [Rastreamento, estados observados, testes e JAR atual](docs/disabled-destinatario-configuracoes.md).

**Edição do destinatário:** comprovados o disabled por modo no JAR antigo da porta
8080 e o bloqueio adicional durante Atualizar dados na v2. Edição agora independe
de consultas/prévias pendentes, com permissão preservada. Teste de navegador com
clique, Tab, digitação, apagar e Ctrl+V passou em desktop/celular, inclusive sob falha
e recuperação. [Causas exatas, antes/depois e JAR de digitação validado](docs/destinatario-editavel.md).

**Continuação — instância efetiva:** a porta 8080 ainda executava o JAR anterior à
correção. A Central v2 agora identifica compilação/recursos, separa os requisitos Gmail
documentais dos do motorista e inclui iniciador com três arquivos sintéticos em outra
porta. **418 JUnit, 417 aprovados, 1 ignorado, zero falhas/erros.** Veja
[JAR identificado, hashes, início isolado e propriedades externas exatas](docs/iniciar-central-corrigida-gmail.md).

Corrigido o bloqueio de **Gerar prévia** pelo modo documental SIMULADO. A conferência
persistida valida destinatário, vínculos e integridade dos arquivos sem chamar transporte.
A confirmação real permanece separada, explícita e dependente da configuração documental,
sessão/permissão e CSRF. Ausência de configuração e falha de consulta têm diagnósticos
distintos, com recuperação e ações conforme o papel. Confirmação opcional ao motorista e
conta administrativa Gmail continuam independentes.

Validação: **413 testes JUnit, 412 aprovados, 1 ignorado, zero falhas/erros**, incluindo
navegador protegido com transporte double; preparação no JAR, desktop/celular, temas e
teclado verificados. Nenhum envio externo nem acesso ao banco operacional.
Veja [causa reproduzida, contratos, comandos e capturas](docs/central-envios.md).

### Revisão visual e de navegação — 13/11/2026

Frontend servido pelo mesmo JAR revisado com tema claro padrão, tema escuro
preservado, sidebar grafite, tipografia legível e componentes consistentes.
Importação ZIP e XML/PDF têm rótulos próprios, conferência por etapas e seleção
acessível. Navegação, estados vazios, tabelas e formulários foram reorganizados.
Veja [implementação, testes atuais e capturas antes/depois](docs/revisao-visual-20261113.md).

**Segunda revisão:** composição mais compacta, títulos sem repetição, seleção ZIP e
Envios mais diretos, Saúde com superfícies neutras e rolagem centralizada.
Veja [mudanças, testes e capturas comparáveis nos dois temas](docs/segunda-revisao-visual-20261113.md).

**Apresentação e marca oficial:** landing escura responsiva, logo fornecida e arte
local de caminhão/mapa, login alinhado e abertura vinculada à verificação real de
versão/sessão. [Implementação, limites e capturas](docs/apresentacao-marca-carregamento.md).

**Abertura animada e favicon oficial:** caminhão SVG com rodas e pista em movimento,
inicialização real paralela e apresentação mínima de aproximadamente **10 segundos**,
com opção de pular apenas a espera visual. Este requisito substitui a orientação
anterior de ausência de tempo mínimo. Ícones oficiais PNG/ICO na landing, login e
painel, inclusive em `/rota`. [Regras atuais, testes, vídeo e capturas](docs/abertura-animada-favicon.md).

### Preparação compartilhada e Gmail — 11/11/2026

Preservada a organização `front/` e `back/`. Conferidas a `main` remota e as
evoluções locais V15. O teste administrativo agora exige nova conferência vinculada
à tentativa anterior para repetir; a versão externa da credencial Gmail invalida
prévias e verificações antigas. Diagnósticos Google distinguem expiração, renovação
e reconexão, sem receber segredos no painel ou confundir Gmail com Microsoft.

**Validação desta revisão:** 407 testes JUnit contabilizados, **406 aprovados**,
zero falhas/erros e 1 ignorado; Python 2 aprovados. Playwright **1.58.2**: entrada
8 grupos, uso compartilhado 12, Configurações/exportação 7 e uso diário 16, sem
erros JavaScript. HTTPS operacional e contexto `/rota` aprovados. Não houve envio
externo, acesso ao banco operacional ou implantação.

Veja [diagnóstico, alterações, comandos e bloqueios externos](docs/preparacao-compartilhada-gmail-20261111.md).

### Revisão final para equipe e Gmail — 09/11/2026

A implementação local de conta administrativa de teste foi integrada e corrigida:
Gmail configurável por ADMIN, credenciais externas, teste sintético sem anexos fiscais
e confirmação específica. O envio documental ao cliente e o canal Microsoft continuam
independentes. Salvar endereço não autentica a conta; o padrão é sem envio.

Corrigidos o erro 500 na última verificação, a identificação da conta no histórico
e a recuperação do formulário após falha de consulta. Conferências documentais novas
preservam remetente, assunto e mensagem efetivamente usados; faturas compartilhadas
exigem conferência explícita na interface. Não houve envio externo.

Consulte o [acompanhamento e aceite desta revisão](docs/revisao-equipe-v15.md),
o [contrato Gmail seguro](docs/gmail-configuravel.md) e o
[procedimento centralizado](docs/uso-compartilhado.md#implantação-centralizada).
Resultados anteriores abaixo conservam sua data/versão; não representam o aceite V15.

**Aceite V15:** 402 testes JUnit contabilizados, **401 aprovados**, zero falhas/erros,
1 ignorado por privilégio de symlink no Windows. Navegador: entrada 8 grupos,
uso diário 16, uso compartilhado 10, arquivos/PDFs 10 e zoom real 100/150/200%,
sem erros JavaScript. Capturas e resultados externos estão no acompanhamento.

### Apresentação pública antes do login — 09/11/2026

O acesso inicial agora segue **apresentação (`/`) → Entrar → login (`/login`) →
painel (`/painel`)**. A apresentação é leve e não consulta dados operacionais.
Sessão válida entra diretamente no painel; `/index.html` e favoritos `/#aba`
permanecem compatíveis. Login, operadores, papéis e CSRF reutilizam os mecanismos
existentes. [Rotas, reprodução HTTPS protegida, testes e capturas](docs/acesso-apresentacao-login-painel.md).

### Revisão de pendências e preparação operacional — 09/11/2026

Comparados o local `1b90b50` e `origin/main` `5dcc237`, preservando o commit local
ainda não publicado. Reproduzido o bloqueio do bootstrap antigo por falha de módulo
Saúde; o fluxo ZIP atual passou sob falhas de Saúde, Busca e Administração, sem
ignorar sessão/contrato ou associações obrigatórias. O episódio com um pacote
operacional específico continua sem causa determinada por falta de evidência própria.

Corrigido o resumo de Configurações para separar envio **documental ao cliente**
da confirmação opcional. Regras comerciais, OAuth2 externo, instalação central,
encerramento e evidências distintas de backup/restauração foram alinhados ao código.
Validação desta revisão: **389 testes JUnit (388 aprovados, 1 ignorado)**, uso diário
14 grupos, uso compartilhado 10, arquivos/páginas 10 e zoom real 100/150/200%.
Veja [reprodução, capturas, testes e pendências reais](docs/revisao-operacional-20261109.md).

### Revisão integrada da interface — 09/11/2026

Revisadas as 12 abas e preservada a simplificação local existente. Corrigida a
navegação por teclado ao voltar à seleção ZIP e a permanência de diagnóstico antigo
após uma ação bem-sucedida em Configurações. O histórico de envios mantém data,
destinatário e resultado à vista, com referências/anexos em **Ver detalhes**;
falha e resultado desconhecido conservam orientação explícita.

Erros do ZIP mostram arquivo, problema e próxima ação, com diagnóstico técnico
recolhido. Na fila, Assumir tem destaque e UUIDs ficam em Histórico e referências.
Veja o [complemento e suas capturas finais](docs/uso-diario-simplificado.md#complemento-erros-do-zip-e-títulos-da-fila--09112026).

Validação nova: **387 testes JUnit (386 aprovados, 1 ignorado)** e cinco roteiros
de navegador concluídos, com capturas das 12 abas, permissões/concorrência e zoom
real 100–200%. [Resultados, capturas e limitações](docs/uso-diario-simplificado.md#revalidação-integrada--09112026).

### Todas as abas simplificadas — 07/11/2026

As 12 telas existentes receberam título e resumo curto. Filtros adicionais, referências,
históricos e ferramentas administrativas ficam sob demanda. Operadores usa formulários
em diálogos; Clientes concentra exclusão nos detalhes; Viagens não exibe tabelas
documentais vazias. ZIP e Configurações preservam a simplificação local anterior.
Veja o [mapa das ferramentas, correções e validação atual](docs/uso-diario-simplificado.md#revisão-de-todas-as-abas--07112026).

### Uso diário simplificado — 03/11/2026

Importação ZIP em **Selecionar → Conferir → Importar**, com orientação para vínculos
pendentes, prévia de PDFs e importações anteriores em tela separada. Sessão e contrato
da API continuam obrigatórios; falhas de módulos secundários são isoladas e a abertura
não tem espera mínima artificial. Login operacional, configurações resumidas, menu por
grupos e histórico identificado como **Teste sem envio** preservam os serviços existentes.
O envio cotidiano fica indisponível quando não há transporte real configurado.

Veja [investigação, regras, validação e capturas](docs/uso-diario-simplificado.md) e
o [diagrama da navegação atual](docs/diagramas/navegacao-uso-diario.mmd).
Esquema permanece **V14**; nenhuma migração operacional ou ativação SMTP foi realizada.

### Uso diário compartilhado — evolução atual

Login individual e gestão de operadores voltam a integrar o frontend existente.
Consulte [contratos, permissões e implantação centralizada](docs/uso-compartilhado.md).
A autorização atual substitui a orientação histórica de não acrescentar login.
Fila compartilhada, busca paginada no servidor e saúde estão integradas ao mesmo
frontend/backend. Novas migrações V12–V14; banco operacional não alterado.
Validação atual: **387 testes JUnit, 386 aprovados, 1 ignorado, zero falhas/erros**.
Navegador: uso compartilhado 9, envio manual 14, confirmação ao motorista 7,
Configurações 7 e recuperação 7 verificações, em execuções independentes.
Resultados detalhados e artefatos em [verificação da entrega](docs/uso-compartilhado.md#verificação-da-entrega).

### Histórico: Etapa E-mail — confirmação ao motorista

- Destinatário explícito no upload e possibilidade de correção antes do envio,
  sem extrair endereço do ZIP nem usar automaticamente o cliente do CT-e.
- Prévia persistida de remetente, destinatário, assunto, texto, recebimento/pacote,
  modo e situação conhecida. Alterações relevantes exigem nova conferência.
- Confirmação manual em SIMULADO ou REAL conforme configuração externa; eventos
  internos não disparam mensagens reais automaticamente. Sem anexos fiscais.
- Reutilização do adaptador SMTP com STARTTLS, verificação do servidor, timeouts
  e suporte XOAUTH2 por token externo. Sem fallback para autenticação básica na
  confirmação Microsoft, sem ativar envio documental REAL ao cliente.
- Tentativa persistida antes do transporte, proteção contra clique duplo,
  concorrência/resposta perdida, reenvio deliberado vinculado e histórico na Central.
- Configurações mostra pré-requisitos seguros, teste simulado e conferência de
  teste real sintético quando disponível. Salvar endereço não autentica a conta.
- Segurança mantida por decisão do responsável: REAL exige sessão protegida
  OPERADOR/ADMIN e CSRF; teste real de configuração exige ADMIN. Sem criar login.
- Migração nova V11; banco operacional não alterado. Homologação externa pendente.

**Validação da Etapa E-mail V11:** suíte completa com **378 testes contabilizados, 377 aprovados,
zero falhas/erros e 1 ignorado** (symlink no Windows). Navegador: confirmação ao
motorista 7 verificações, Configurações 7, recuperação 7, estabilização 5 e envio
documental local 13 cenários, em execuções separadas. Não houve SMTP externo.
Detalhes, configuração segura e recuperação em [Confirmação ao motorista](docs/confirmacao-motorista.md).

### Histórico preservado: estabilização antes da Etapa E-mail — 31/10/2026

| Área | Atualização |
| --- | --- |
| Configurações e Processamentos | Reproduzida a causa dos erros **“Recurso não encontrado”**: JAR antigo executado com arquivos estáticos atuais sobrepostos. No JAR atualizado, `GET /api/configuracoes` e `GET /api/pendencias` retornaram **200**. |
| Versão e empacotamento | O painel verifica a compatibilidade do backend ao iniciar. `GET /api/versao` informa versão, instante de build, contrato e hashes dos recursos estáticos. HTML, JavaScript e CSS usam `Cache-Control: no-store`. |
| Menu lateral | Um único contador de itens que precisam de ação, limitado a **99+**. Falha de consulta mostra um indicador acessível de indisponibilidade; a divisão entre falhas, atenção e andamento fica dentro da tela. |
| Layout da sidebar | Corrigida a regra que tratava o nome da aba como ícone. Verificados menu aberto, recolhido e celular, incluindo larguras de 800 e 1024 px, com medição do texto renderizado para impedir sobreposição. |
| Mensagens e recuperação | Falhas de consulta aparecem em vermelho, com mensagem específica e **Tentar novamente**. Recusa conhecida, pendência de processamento e resultado incerto de alteração recebem tratamentos distintos. Referências e informações técnicas ficam em **Ver detalhes**. |
| Cancelamento | A interface informa que a espera foi interrompida no navegador, sem afirmar que a operação foi cancelada no backend. |
| Pasta adicional | A raiz de exportação deve ficar **fora do checkout e separada do acervo**. Configurações sobrepostas ao checkout detectado são recusadas explicitamente, sem trocar o destino silenciosamente. A pasta pertence ao computador do backend. |
| Integridade e retomada | Validadas cópia automática/manual, fatura compartilhada, SHA-256, conflitos, indisponibilidade da pasta e retomada somente da exportação, preservando importação, originais e histórico. |

**Validação desta revisão:** suíte JUnit completa com **360 testes contabilizados:
359 aprovados, zero falhas/erros e 1 ignorado** por falta de privilégio de symlink
no Windows. No navegador, foram aprovadas verificações de estabilização,
Configurações, diagnóstico/recuperação, modernização e os **13 cenários do fluxo
manual local**, incluindo simulação, resposta perdida e persistência após reinício.
Os resultados de cada execução estão em [Testes e verificações](#testes-e-verificações);
os números não representam soma de recortes históricos.

**Limitação separada:** a continuação de navegador com segurança ativa permanece
bloqueada por `GET /login` retornando **404**. Autenticação, permissões e CSRF
foram preservados. Esse cenário não amplia o escopo local para criar um login.

O fluxo manual local validado está apto a servir de base para iniciar a **Etapa
E-mail**. O envio real de confirmação de recebimento ao motorista ainda é posterior;
**nenhum SMTP real foi habilitado e nenhuma mensagem real foi enviada** nesta tarefa.
A implantação operacional depende da verificação separada do banco V10 e do acesso
à pasta externa. Não houve alteração de banco operacional, documentos reais ou credenciais.

Para experimentar, siga [Como executar localmente](#como-executar-localmente),
utilizando o frontend do mesmo JAR. O roteiro completo de falhas e recuperação,
as URLs reproduzidas e as evidências estão em [Estabilização](docs/estabilizacao.md).

## Visão geral

O sistema centraliza documentos produzidos por sistemas externos e mantém o vínculo
entre **cliente, viagem, CT-e, PDFs e faturas**. O fluxo principal é assistido: o usuário
confere os arquivos, escolhe as associações e confirma as operações.

### Qual problema o sistema resolve?

Na rotina de gestão documental de transporte, os arquivos de uma viagem podem chegar
separados ou misturados em um ZIP. O RotaBrasil reúne esse material em um acervo
consultável, permite indicar a qual viagem cada PDF pertence e prepara o envio dos
documentos selecionados para um destinatário informado pelo usuário.

O trabalho atual acontece em três frentes:

- **Receber e organizar:** importar CT-es, conferir ZIPs e relacionar PDFs e faturas
  às viagens corretas, com diagnóstico dos arquivos recusados.
- **Consultar e conferir:** localizar clientes e viagens, abrir documentos, revisar
  visualmente cópias de PDFs e consultar processamentos anteriores.
- **Preparar e acompanhar envios:** selecionar arquivos, conferir destinatário e
  anexos, confirmar manualmente e consultar o resultado de cada tentativa.

### Termos usados no projeto

| Termo | Significado no sistema |
| --- | --- |
| CT-e | Conhecimento de Transporte Eletrônico recebido em XML; o sistema lê seus dados para organizar o acervo |
| Viagem | Registro do domínio identificado pelo número do CT-e (`nCT`), associado a um cliente |
| Cliente | Cadastro associado aos dados do destinatário (`dest`) lidos do XML |
| Documento | Registro documental vinculado à viagem, incluindo o XML importado |
| Anexo | PDF recebido por pacote, com finalidade e vínculo explícitos |
| Fatura | Finalidade atribuída a um PDF pelo usuário; pode reunir uma ou várias viagens |
| Pacote | Identidade e metadados de um ZIP processado, com referências aos documentos e PDFs |
| Conferência | Etapa de revisão anterior à confirmação de uma operação |
| SHA-256 | Resumo criptográfico usado para identificar conteúdo e conferir integridade |
| Idempotência | Repetir uma confirmação já reconhecida recupera seu resultado, evitando duplicação da operação |

```text
ZIP com XMLs e PDFs
        ↓
Conferência e diagnóstico por arquivo
        ↓
Associação explícita dos PDFs às viagens
        ↓
Confirmação da importação
        ↓
Consulta de viagens, documentos e faturas
        ↓
Seleção de destinatário e anexos → conferência → confirmação de envio manual
```

O RotaBrasil organiza documentos recebidos. Não emite CT-e, nota fiscal, boleto ou
fatura fiscal. A revisão visual de um PDF cria uma cópia e não modifica o XML fiscal.

## Funcionalidades atuais

| Área | O que está implementado |
| --- | --- |
| Importação XML | Recebimento individual de CT-e 4.00 e identificação da viagem e do destinatário |
| Importação ZIP | Pacotes com várias viagens, XMLs, PDFs e faturas; inspeção antes da confirmação |
| PDF individual | Seleção de uma viagem e de seu CT-e ativo para preparar uma conferência pelo fluxo ZIP existente |
| Associação de PDFs | Finalidade de documento de uma viagem ou fatura de uma ou várias viagens |
| Diagnóstico | Origem no ZIP, etapa, código, situação, orientação e histórico persistido de processamento |
| Visualização PDF | Prévia dentro do painel, páginas, zoom e download do original durante a conferência |
| Revisão PDF | Correção visual em cópia, download da cópia e geração de novo ZIP para reconferência |
| Viagens | Busca, filtros por cliente/data/status, ordenação, paginação, detalhes e documentos associados |
| Clientes | Pesquisa por nome/CNPJ, detalhes e acesso às viagens do cliente |
| Exclusão de cliente | Conferência e exclusão conjunta de viagens, documentos e PDFs exclusivos, preservando PDFs de outros clientes |
| Cadastros via API | Criação, consulta, atualização e exclusão protegida de clientes e viagens |
| Dashboard | Indicadores reais, últimas viagens por data e ações rápidas |
| Downloads | Leitura controlada de XMLs/PDFs com conferência de SHA-256 e da raiz permitida |
| Exclusão de seleção | Remoção local do ZIP, XML ou PDF escolhido, antes da importação |
| Exclusão de importados | Arquivamento reversível de XML/PDF e arquivamento transacional dos arquivos de um ZIP |
| Restauração | Recuperação individual dos mesmos registros, preservando identidade e bytes |
| E-mail manual | Conferência persistida, confirmação explícita, simulação, snapshots e histórico por UUID |
| Reenvio | Nova conferência e referência explícita à tentativa anterior, sem repetição automática |
| SMTP | Adaptador real implementado com autenticação, STARTTLS obrigatório e timeouts; padrão simulado |
| Controle de acesso | Sessões, BCrypt, CSRF e papéis CONSULTA, OPERADOR e ADMIN quando a segurança está ativa |
| Operadores | Login individual, logout, cadastro administrativo, papéis, ativação/desativação e redefinição de senha com revogação de sessões |
| Concorrência | Versões de configuração/operadores, ETag no CRUD de clientes/viagens e recusa de alterações desatualizadas |
| Fila compartilhada | Históricos existentes com situação técnica, trabalho, responsável, atribuição atômica, transferência auditada e paginação |
| Busca integrada | Clientes, CT-es, XMLs, PDFs e pacotes com filtros e ordenação no servidor, sem duplicar faturas compartilhadas |
| Saúde | Backend, banco, pastas, espaço disponível, configuração de e-mail e evidências de backup com permissões e estados verificáveis |
| Auditoria | Ator, correlação de requisições, referências de recursos e resultados HTTP |
| Manutenção | Inventário, backup, migrações controladas e recuperação conservadora de armazenamento |
| Configurações | Oitava tela; remetente da operação, destino de exportação, ativação da cópia automática e testes persistentes |
| Confirmação de recebimento | Motorista explícito, prévia persistida, confirmação manual, SIMULADO padrão e REAL condicionado a OAuth2/segurança; histórico, idempotência e reenvio vinculado, sem anexos fiscais |
| Exportação adicional | Cópia manual ou automática de arquivos ativos para subpasta autorizada do backend, com SHA-256 e retomada de pendências |
| Diagnóstico e recuperação | Cartões persistentes, erros em vermelho, causas comprovadas, correlação, impacto, ações específicas e evidência de resolução |
| Central de Pendências | Processamentos ampliada; consulta paginada dos históricos existentes, filtros e contadores separados de falha/atenção/andamento |

A interface de operadores foi reintegrada, restrita a ADMIN, com login individual,
logout e redefinição de acesso. O frontend não oferece edição manual de cliente e
viagem; as APIs exigem conferência de versão para essas alterações.
Novas telas: `/#fila`, `/#busca`, `/#saude` e `/#administracao` (ADMIN).

## Tecnologias utilizadas

### Backend

| Tecnologia | Uso no projeto |
| --- | --- |
| **Java 25** | Linguagem principal e versão de compilação |
| **Spring Boot 3.5.6** | Inicialização, configuração e distribuição da aplicação |
| **Spring Web / Spring MVC** | API REST, multipart e entrega do frontend estático |
| **Tomcat embarcado** | Servidor HTTP da aplicação Spring Boot |
| **Spring Data JPA / Hibernate** | Persistência e mapeamento das entidades |
| **Spring JDBC / JdbcTemplate** | Consultas operacionais, histórico, auditoria e coordenação de operações |
| **H2 Database** | Banco relacional em arquivo; bases em memória nos testes |
| **Flyway** | Evolução versionada do banco, atualmente até V15 |
| **Spring Security** | Autenticação, autorização por papel, sessões e CSRF |
| **BCrypt** | Hash das senhas de operadores |
| **Jakarta Validation** | Validação dos contratos de entrada |
| **Jackson** | JSON, DTOs e snapshots persistidos |
| **Spring Mail / Jakarta Mail** | Construção de mensagens e adaptador SMTP |
| **Lombok** | Redução de código repetitivo em entidades e componentes |
| **Java NIO, ZIP e SHA-256** | Arquivos, travas, leitura limitada de pacotes e identidade de conteúdo |
| **Maven** | Dependências, compilação, testes e empacotamento JAR |
| **Spring Boot DevTools** | Apoio ao desenvolvimento, com recursos de reinicialização automática |

As versões de Hibernate, H2, Flyway e demais dependências gerenciadas seguem o
gerenciamento de versões do Spring Boot definido em [`pom.xml`](pom.xml).

### Frontend

| Tecnologia | Uso no projeto |
| --- | --- |
| **HTML5** | Estrutura das telas, formulários, tabelas e dialogs |
| **CSS3** | Temas, componentes, responsividade, foco e animações |
| **JavaScript com ES Modules** | Organização dos módulos e comportamento das telas |
| **Fetch API / FormData** | Integração REST e envio multipart para a mesma origem |
| **History por hash** | Telas operacionais e compartilhadas com links diretos e navegação voltar/avançar |
| **Canvas e SVG** | Renderização de PDFs, ícones e ilustrações locais |
| **PDF.js 6.4.299** | Leitura e renderização das páginas dos PDFs |
| **pdf-lib 1.17.1** | Geração das cópias com revisões visuais |
| **fflate 0.8.3** | Preparação e leitura de ZIPs no navegador |
| **WebJars** | Distribuição local das bibliotecas PDF/ZIP pelo Spring Boot |

O frontend real está em `front/` e é servido pelo mesmo JAR.
Não exige instalar Node.js para utilizar o site. As bibliotecas de PDF e ZIP são
servidas localmente; documentos não são enviados a CDN ou serviço externo de edição.

A interface utiliza **JavaScript puro**, com módulos ES nativos do navegador e
manipulação do DOM. O CSS é próprio do projeto. O Maven inclui os arquivos estáticos
e as bibliotecas WebJars no empacotamento; a aplicação é entregue como um único
JAR executável com servidor embarcado.

### Testes, automação e auxiliares

- **JUnit 5, Spring Boot Test, MockMvc e TestRestTemplate:** testes de serviços e HTTP.
- **AssertJ, Mockito e Spring Security Test:** asserções, simulações e segurança.
- **Playwright 1.58.2 com Node.js:** verificação funcional em navegador real.
- **Chromium / Google Chrome:** navegadores usados nas ferramentas de teste.
- **Python:** auxiliares opcionais de XML, cópia de arquivos e testes legados de navegador.
- **GitHub Actions:** workflow de verificação Java/Python em Linux e Windows e job de navegador.
- **PlantUML:** diagrama de casos de uso em `docs/casos-de-uso.puml`.

Node.js 22 e Python 3.12 são utilizados pelo workflow atual. Eles são ferramentas
auxiliares, não servidores adicionais necessários ao funcionamento do frontend.

## Arquitetura e organização

```text
Navegador: HTML + CSS + JavaScript
             │ Fetch / JSON / multipart
             ▼
Controllers REST → Services → Repositories / JdbcTemplate → H2
                       │
                       ├── Armazenamento transacional de XML/PDF
                       ├── Leitura e diagnóstico de ZIP
                       ├── Conferência, snapshots e auditoria
                       └── Transporte de e-mail: simulado ou SMTP configurado
```

O backend concentra regras de negócio, validações, integridade e persistência.
O frontend apresenta os dados e coleta escolhas explícitas. Os scripts Python
não substituem o processamento oficial em Java.

### Estrutura do repositório

```text
back/src/main/java/br/com/rotabrasil/
├── config/          # Perfis, verificações de banco/armazenamento e SMTP
├── controller/      # Endpoints REST
├── dto/             # Contratos de entrada e saída
├── exception/       # Exceções e respostas de erro
├── manutencao/      # Inventário, backup, manutenção e CLIs auxiliares
├── migracao/        # Migração Java de identidade documental (V3)
├── model/           # Entidades do domínio
├── repository/      # Repositórios JPA
├── seguranca/       # Operadores, sessões, permissões e auditoria
└── service/         # Regras de negócio, arquivos, ZIP e serviços de e-mail

back/src/main/resources/
├── application*.properties
├── db/migration/    # Migrações SQL
└── email/           # CT-e sintético para demonstração/testes

back/src/test/       # Testes Java e fixtures sintéticas
back/pom.xml         # Build Spring Boot e dependências do backend
front/               # Frontend real: HTML, CSS, JavaScript e SVG
scripts/             # Testes de navegador e dependências dessas ferramentas
python/              # Auxiliares opcionais e testes Python
docs/                # Contratos, fluxos, manutenção e resultados históricos
.github/workflows/   # Automação de verificações
pom.xml              # Agregador Maven: permite compilar pela raiz
AGENTS.md            # Instruções de trabalho no projeto
```

### Manter o checkout enxuto

Use `rotabrasil.build.directory` apontando para uma pasta temporária externa nos
comandos Maven, conforme [Testes e verificações](#testes-e-verificações). `target/`
contém saídas regeneráveis, não código-fonte; relatórios antigos que precisem ser
preservados devem ficar fora do checkout. Atalhos pessoais e metadados do sistema
operacional também ficam fora do versionamento.

Na limpeza de 09/11/2026, o `target/` antigo e o atalho pessoal do Chrome foram
retirados do checkout e preservados em diretório temporário externo. Os contratos,
documentos históricos, scripts de teste e auxiliares Python continuam no projeto.

### Principais relações de dados

- **Cliente → Viagens → Documentos:** cliente destinatário, viagem identificada por nCT e seus documentos.
- **Pacote ZIP ↔ Documentos:** um XML já existente pode ser referenciado por mais de um pacote.
- **Pacote ZIP → Anexos PDF ↔ Viagens:** um PDF pode ter várias viagens vinculadas.
- **Conferências e tentativas de e-mail:** preservam seleção, destinatário, anexos e resultados.
- **Envios legados:** continuam separados das tentativas manuais novas.
- **Usuários, auditoria e processamentos:** controle de acesso e rastreabilidade persistentes.

## Mapa dos módulos do backend e frontend

### Backend: onde ficam as regras

Os controllers recebem as requisições, os DTOs definem os dados aceitos e retornados,
os services executam as regras e os repositories/JdbcTemplate acessam o banco.
Os bytes de XML/PDF ficam no armazenamento de arquivos, com metadados e vínculos no H2.

| Módulo ou componente | Responsabilidade |
| --- | --- |
| `ClienteService`, `ViagemService` e `CadastroConsultaService` | Cadastros, detalhes, consultas e indicadores operacionais |
| `ProcessamentoService` e `ExtratorDadosXml` | Recebimento do XML, extração dos dados de CT-e e organização do processamento |
| `ValidacaoDocumental` e `LeituraSeguraArquivos` | Validações documentais e leitura controlada dos arquivos |
| `LeitorPacoteZip`, `PacoteService` e `ConfirmacaoPacoteService` | Inspeção do ZIP, diagnóstico, associações e confirmação da importação |
| `PreviaPdfPacoteService` | Leitura do PDF solicitado no pacote para a prévia do navegador |
| `DocumentoService` e `AnexoService` | Consulta, acesso, arquivamento e restauração de documentos e PDFs |
| `ArquivamentoPacoteService` | Conferência de impacto e arquivamento transacional dos arquivos do ZIP |
| `ExclusaoClienteService` e `LimpezaArquivosClienteService` | Exclusão conjunta do cliente e limpeza dos arquivos exclusivos após o commit, com journal de recuperação |
| `CoordenacaoImportacaoService` e `ArmazenamentoTransacionalService` | Coordenação entre persistência e publicação dos arquivos |
| `ReconciliacaoArmazenamentoService` | Recuperação conservadora de operações incompletas de armazenamento |
| `HistoricoProcessamentoService` | Histórico e diagnóstico persistente das operações |
| `DiagnosticoOperacaoService` e `CentralPendenciasService` | Contrato público seguro e projeção paginada dos históricos existentes, com resolução comprovada e sem tabela paralela de pendências |
| `ConfiguracaoOperacaoService` e `DestinoExportacaoService` | Configuração persistida, testes e seleção limitada à raiz de exportação autorizada |
| `FluxoPacoteOperacionalService` e `NotificacaoRecebimentoService` | Eventos internos simulados e confirmação manual ao motorista com conferência, snapshot e tentativa persistida no histórico existente |
| `ConfiguracaoNotificacoes`, `TokenSmtpExterno` e `TransporteSmtp` | Pré-requisitos seguros, token OAuth2 externo e mesmo adaptador SMTP, sem habilitar envio documental automaticamente |
| `ExportacaoPacoteService` e `PublicadorExportacao` | Planos persistentes, cópia sem sobrescrita, integridade e retomada dos itens pendentes |
| `service/email/` | Preparação de anexos, conferências, tentativas e transportes de e-mail |
| `seguranca/` | Operadores, autorização, sessão, auditoria e funções administrativas |
| `manutencao/` e `config/` | Ferramentas de manutenção e verificações de configuração, banco e armazenamento |

### Frontend: onde fica cada parte da interface

Todos os arquivos abaixo ficam em `front/`.

| Arquivo | Responsabilidade |
| --- | --- |
| `index.html` | Telas operacionais, busca, fila, saúde, operadores e diálogos |
| `painel.js` | Entrada da aplicação: inicializa sessão, navegação e módulos do painel |
| `painel-base.js` | Estado compartilhado, cliente HTTP, CSRF, permissões, avisos e formatação |
| `painel-navegacao.js` | Rotas hash, títulos, aliases e histórico de navegação |
| `painel-interface.js` e `painel-comandos.js` | Tema, menu responsivo, drag-and-drop e central de atalhos |
| `painel-abertura.js` e `painel-abertura.css` | Apresentação inicial e progresso de carregamento |
| `painel-consultas.js` | Busca, filtros, ordenação, paginação e detalhes de clientes/viagens |
| `painel-documentos.js` | Atualização dos dados, documentos da viagem e seleção de anexos para envio |
| `painel-importacao.js` | Upload individual de XML e preparação local de PDF com o CT-e selecionado |
| `painel-pacotes.js` | Conferência do ZIP, vínculos dos PDFs e confirmação de importação |
| `painel-pdf.js` e `painel-pdf.css` | Visualizador e revisão visual de cópias de PDFs |
| `painel-arquivos.js` | ZIPs importados, conferência de exclusão e restauração de arquivos |
| `painel-confirmacao.js` | Diálogo reutilizável de confirmação de ações |
| `painel-email.js` | Conferência, confirmação e histórico de tentativas de envio manual |
| `painel-historico.js` | Consulta de processamentos, diagnósticos e acesso à correção com novo ZIP |
| `painel-configuracoes.js` e `painel-configuracoes.css` | Oitava tela, alterações não salvas, testes e configuração do remetente/destino |
| `painel-exportacoes.js` | Ações manuais, resultado de cópia, retomada e consulta de avisos |
| `painel-confirmacao-recebimento.js` | Conferência da mensagem, destinatário corrigível, confirmação explícita, recuperação e histórico ao motorista, separados do envio documental |
| `painel-diagnostico.js`, `painel-recuperacao.js` e `painel-diagnostico.css` | Cartões acessíveis, lista de problemas, suporte e ações reais de recuperação |
| `painel-emergencia.js` | Tratamento independente de erros globais de JavaScript, com mensagem segura e referência local |
| `painel.css`, `painel-operacao.css` e `painel-visual.css` | Estilos-base, componentes operacionais, identidade visual e responsividade |
| `rota-visual.svg` | Ilustração local utilizada na identidade visual |

Os módulos `painel-admin.js`, `painel-fila.js`, `painel-busca.js` e `painel-saude.js`
são inicializados pelo painel existente. Administração exige ADMIN; os demais
módulos respeitam as permissões de leitura e alteração validadas no backend.

### Como o frontend conversa com o backend

1. O navegador carrega `index.html`, estilos e módulos JavaScript do Spring Boot.
2. O painel consulta `/api/sessao` para obter usuário, papéis, modo de e-mail e CSRF.
3. As telas consultam a API usando Fetch e recebem JSON; uploads utilizam FormData.
4. Operações de alteração incluem o token CSRF quando fornecido pela sessão.
5. Downloads e prévias recebem os bytes pelos endpoints controlados da aplicação.
6. O painel apresenta o resultado ou o erro retornado, incluindo a referência de
   requisição quando disponível. Em timeout, orienta consultar o histórico antes
   de repetir uma confirmação.

## Frontend e navegação

O menu preserva as oito telas operacionais e acrescenta fila, busca, saúde e
operadores (ADMIN), mantendo a identidade verde/preto e verde-lima:

| Tela | Endereço | Conteúdo |
| --- | --- | --- |
| Visão geral | `/#inicio` | Indicadores, últimas viagens e ações rápidas |
| Importar ZIP | `/#pacotes` | Upload, diagnóstico, prévia PDF, vínculos e gerenciamento de ZIPs importados |
| Importar XML / PDF | `/#importacao` | Importação individual e preparação de PDF para conferência |
| Viagens e documentos | `/#viagens` | Pesquisa, filtros, ordenação, detalhes e arquivos da viagem |
| Clientes | `/#clientes` | Pesquisa por nome/CNPJ, contatos informados e viagens associadas |
| Central de envios | `/#email` | Seleção conferida, destinatário, confirmação e histórico |
| Processamentos e pendências | `/#processamentos` | Central paginada de falhas, atenção, andamento, resoluções e ações de recuperação |
| Configurações | `/#configuracoes` | E-mail da operação, pasta do backend, cópia automática e resultados de testes |
| Fila compartilhada | `/#fila` | Todos, meus itens, sem responsável, filtros, atribuição e histórico de responsabilidade |
| Busca integrada | `/#busca` | Resultados por entidade, filtros, paginação e acesso aos detalhes |
| Saúde do sistema | `/#saude` | Verificações locais, configuração e evidências; detalhes administrativos para ADMIN |
| Operadores | `/#administracao` | Gestão de contas e redefinição de acesso, restritas a ADMIN |

Os aliases `/#documentos`, `/#pacotes-importados` e `/#ultimas-viagens` continuam funcionando.

Recursos de interface:

- Tema escuro inicial, tema claro opcional e preferência salva.
- Sidebar fixa/recolhível no desktop e drawer com controle de foco no celular.
- Abertura acompanha somente a verificação essencial de versão e sessão, sem espera mínima.
- Módulos secundários carregam sob demanda e apresentam recuperação local.
- Respeito a `prefers-reduced-motion`, navegação por teclado e foco visível.
- Central de atalhos com **Ctrl+K / Cmd+K**.
- Ajuda contextual pelo botão **?** junto ao título: fechada por padrão, com passos
  curtos conforme a permissão da sessão. [Textos finais, testes e capturas](docs/ajuda-contextual.md).
- Drag-and-drop, nome/tamanho do upload e feedback da operação.
- Cards clicáveis e estados de carregamento, erro e ausência de resultados.
- Filtros e seleções preservados entre telas; mudanças de dados invalidam as prévias necessárias.

Viagens e clientes usam busca, filtros, ordenação e páginas de **20 registros no
servidor**. A lista de ZIPs também usa paginação de servidor. Seletores usam consulta
limitada com pesquisa explícita; a busca integrada localiza arquivos e pacotes.
As últimas viagens são ordenadas pela **data da viagem**,
não por uma data de processamento presumida.

## Uso diário por vários operadores

Uma aplicação central atende os navegadores de todos os PCs. Somente o backend
acessa o H2 e o acervo; não compartilhe o arquivo do banco entre computadores.

1. O administrador prepara a aplicação central com perfil `operacao`, HTTPS e
   banco V15, seguindo a manutenção autorizada para bases existentes.
2. O primeiro ADMIN utiliza o bootstrap seguro já existente. As demais contas são
   cadastradas em **Operadores**, com papel CONSULTA, OPERADOR ou ADMIN.
3. Cada pessoa entra em `/login` com sua conta e acompanha seus itens na **Fila
   compartilhada**. Assumir, liberar e transferir mantêm histórico no servidor.
4. Em conflito de edição, revise a versão atual antes de tentar novamente. A
   configuração não reaplica o rascunho antigo automaticamente.
5. Use **Busca integrada** para localizar registros e **Saúde do sistema** para
   consultar condições comprovadas, pendências e verificações ainda não realizadas.

Para testar dois operadores no mesmo computador, use perfis de navegador
independentes: abas do mesmo perfil compartilham a sessão. O roteiro completo e
os requisitos de rede estão em [Uso compartilhado](docs/uso-compartilhado.md).
A implantação não abre portas automaticamente nem publica a aplicação na internet.

## Como utilizar o sistema

Este guia descreve o uso pelo painel web. Para preparar o ambiente e iniciar o
servidor, siga primeiro [Como executar localmente](#como-executar-localmente).

### 1. Acessar o painel

1. Com a aplicação em execução local, abra **http://localhost:8080** no navegador.
   Em uma implantação configurada, utilize o endereço fornecido pelo responsável.
2. Na apresentação, clique em **Entrar**. Em operação, autentique-se com sua conta;
   em desenvolvimento isolado, use **Abrir painel local**. No painel, o carregamento
   interno verifica sessão e versão sem espera mínima artificial.
3. Na **Visão geral**, consulte os indicadores, as últimas viagens e os atalhos.
4. Use o menu lateral para trocar de tela. No celular, abra o botão de menu.
   A central de atalhos também pode ser aberta com **Ctrl+K / Cmd+K**.

No ambiente de desenvolvimento isolado, o acesso é local e não exige login.
No perfil de operação, as ações dependem do usuário autenticado e de suas
permissões. Entre com a conta individual em `/login`, conforme
[Segurança e perfis de execução](#segurança-e-perfis-de-execução).

### 2. Importar documentos de um ZIP

Este é o caminho principal para receber vários XMLs e PDFs de uma vez.

1. Abra **Importar ZIP** e selecione ou arraste o pacote para a área de upload.
2. Clique em **Conferir ZIP** e leia o diagnóstico dos arquivos.
3. Para cada PDF, escolha sua finalidade:
   - **Documento de uma viagem:** marque exatamente uma viagem.
   - **Fatura:** marque uma ou mais viagens às quais a fatura pertence.
4. Quando precisar conferir o conteúdo, use **Visualizar e corrigir PDF**.
5. Resolva os erros e as associações pendentes antes de confirmar.
6. Clique em **Confirmar importação** e aguarde o resultado.
7. Abra **Viagens e documentos** para consultar os arquivos importados.

Apenas conferir o ZIP não conclui a importação. As associações dos PDFs precisam
ser escolhidas pelo usuário; o sistema não as deduz pelos nomes dos arquivos.

### 3. Importar um XML individual

1. Abra **Importar XML / PDF**.
2. Selecione ou arraste o XML de CT-e e utilize a ação de importação do formulário.
3. Aguarde o resultado do processamento.
4. Consulte a viagem e o cliente identificados a partir do XML nas respectivas telas.

O sistema pode criar ou reutilizar os registros conforme os dados e a identidade
documental existentes. Para uma primeira demonstração em ambiente isolado, utilize
`back/src/main/resources/email/cte-sintetico.xml`, que não possui valor fiscal.

### 4. Adicionar um PDF a uma viagem existente

1. Em **Importar XML / PDF**, escolha o PDF.
2. Selecione a viagem e um CT-e ativo dessa viagem.
3. Use a ação do formulário para preparar o pacote de conferência.
4. O painel encaminha o material ao fluxo de **Importar ZIP**.
5. Confira o PDF, escolha sua finalidade e seus vínculos e confirme a importação.

O PDF individual depende de um XML ativo e íntegro da viagem. Se ele ainda não
estiver disponível, importe o CT-e primeiro ou utilize o ZIP original com os arquivos.

### 5. Localizar clientes, viagens e documentos

- Em **Clientes**, pesquise pelo nome ou CNPJ, abra os detalhes e acesse as viagens
  vinculadas ao cliente.
- Em **Viagens e documentos**, pesquise pelo número do CT-e ou nome do cliente.
  Combine filtros de cliente, período e status, ordene a tabela e navegue pelas páginas.
- Abra os detalhes da viagem para consultar suas informações e escolha a viagem
  na área documental para listar os XMLs e PDFs associados.
- Utilize as ações disponíveis para abrir ou baixar os arquivos originais.
- Para preparar um envio, selecione explicitamente os documentos desejados e
  prossiga para a **Central de envios**.

### 6. Conferir e revisar visualmente um PDF

1. Durante a conferência do ZIP, clique em **Visualizar e corrigir PDF**.
2. Navegue pelas páginas e ajuste o zoom para ler o documento.
3. Se necessário, escolha um trecho ou uma posição para acrescentar uma correção.
4. Adicione as correções ao rascunho, aplique-as à cópia e baixe o PDF revisado.
5. Para importar a versão revisada, gere o ZIP revisado pelo painel, confira esse
   novo pacote, selecione novamente seus vínculos e confirme a importação.

A revisão acrescenta elementos visuais em uma cópia. O original e o XML são
preservados; cobrir um texto com fundo branco não remove definitivamente o conteúdo
anterior. As revisões ficam na memória da aba até serem baixadas ou utilizadas na
geração do novo ZIP.

### 7. Preparar e confirmar um envio de e-mail

1. Selecione a viagem e os XMLs/PDFs que deseja enviar.
2. Abra a **Central de envios** e informe ou confira o destinatário.
3. Solicite a conferência e revise a relação de anexos e os dados apresentados.
4. Confirme explicitamente o envio somente depois dessa revisão.
5. Consulte o histórico para verificar o resultado da tentativa.

No modo padrão **SIMULADO**, o fluxo registra a tentativa sem enviar uma mensagem
real. O modo real depende da configuração SMTP e de sua ativação explícita.
`ACEITO_SMTP` indica aceite pelo servidor, não confirmação de entrega ou leitura.

Se houver timeout, perda de conexão ou resultado `DESCONHECIDO`, consulte o histórico
antes de iniciar outra tentativa. Um reenvio deliberado exige nova conferência,
referenciando a tentativa anterior. A conferência expira em 30 minutos.

### 8. Remover seleções, arquivar e restaurar arquivos

- **Antes da importação:** use **Remover ZIP da seleção** ou
  **Remover XML/PDF da seleção** para limpar o formulário.
- **XML/PDF importado:** em **Viagens e documentos**, use **Excluir XML** ou
  **Excluir PDF** e confira o diálogo. A ação arquiva o registro de forma reversível.
- **ZIP importado:** em **Importar ZIP → ZIPs importados**, use **Excluir ZIP**,
  confira os arquivos afetados e os vínculos compartilhados e confirme a operação.
- **Restaurar um arquivo:** consulte os documentos arquivados da viagem ou os ZIPs
  excluídos da lista ativa e utilize a restauração do item desejado.
- **Excluir cliente e seus documentos:** em **Clientes**, clique em **Excluir**,
  confira as quantidades e use **Excluir cliente e documentos**. A ação remove
  definitivamente o cliente, suas viagens, documentos, PDFs exclusivos e históricos
  de e-mail relacionados. PDFs compartilhados permanecem para os outros clientes.
  Envios com resultado desconhecido impedem a exclusão até revisão.

Arquivar um arquivo compartilhado afeta todas as viagens ou pacotes que o
referenciam. A restauração é individual e recupera o mesmo registro. Excluir um
ZIP pelo painel não apaga o ZIP do computador do usuário.

### 9. Acompanhar problemas de processamento

1. Abra **Processamentos** e atualize o histórico quando necessário.
2. Utilize o filtro de situação e abra **Consultar diagnóstico** na operação desejada.
3. Leia a etapa, o arquivo, o código do problema e a orientação apresentada.
4. Para corrigir o material, utilize **Selecionar ZIP corrigido**, quando disponível,
   e faça uma nova conferência antes de confirmar.

| Situação encontrada | Como proceder |
| --- | --- |
| ZIP com erros ou PDFs sem vínculo | Corrija os arquivos indicados e escolha todas as associações antes da confirmação |
| PDF abre com erro no visualizador | Confira o arquivo de origem; a inspeção inicial verifica apenas seu cabeçalho |
| PDF individual sem CT-e disponível | Importe o XML da viagem primeiro ou use o pacote original |
| Documento já arquivado | Consulte os arquivados e restaure explicitamente o registro |
| Conferência expirada ou conflito 409 | Atualize os dados e faça uma nova conferência da operação |
| Falha de conexão durante confirmação | Consulte o histórico e o estado atual antes de repetir a ação |
| Ação indisponível por permissão | Confira o papel da sessão; operações de alteração exigem OPERADOR ou ADMIN |

## Fluxos de documentos

### 1. Importação de ZIP — fluxo principal

1. Escolha o ZIP com XMLs e PDFs e clique em **Conferir ZIP**.
2. Confira os CT-es e o diagnóstico de cada arquivo.
3. Para cada PDF, selecione a finalidade e marque as viagens correspondentes:
   - **Documento de uma viagem:** exatamente uma viagem.
   - **Fatura:** uma ou várias viagens.
4. Use **Visualizar e corrigir PDF** para conferir o conteúdo quando necessário.
5. Clique em **Confirmar importação** depois de resolver os erros e os vínculos.

O sistema não deduz vínculos por nome, posição ou quantidade de PDFs. Arquivos
inválidos e associações pendentes bloqueiam a confirmação integral. A inspeção
registra diagnóstico/histórico, mas não importa os documentos no acervo definitivo.

### 2. Importação individual de XML

O upload de CT-e identifica `ide/nCT` e os dados de `dest`. O processamento pode criar
ou reutilizar o cliente, a viagem e o documento conforme a identidade existente.
Contatos ausentes não são inventados.

O extrator aceita CT-e 4.00 (`CTe` ou `cteProc`) no namespace fiscal e verifica chave
de 44 dígitos, modelo 57, dígito verificador, correspondência do número e, quando
presente, da chave do protocolo. XML sem protocolo não é tratado como autorizado.
Essas verificações não substituem XSD, assinatura digital ou consulta à SEFAZ.

A mesma chave com os mesmos bytes pode reutilizar o registro; conteúdo diferente
retorna conflito. Arquivo arquivado exige restauração explícita para voltar a ser usado.

### 3. Importação individual de PDF

O formulário aceita PDF sem exigir que o usuário monte um ZIP manualmente:

1. Selecione o PDF, uma viagem existente e um CT-e ativo dessa viagem.
2. O frontend lê o XML original e confere seu SHA-256.
3. Prepara um ZIP local com o XML inalterado e o PDF, usando metadados determinísticos.
4. Encaminha o pacote ao fluxo ZIP existente para conferência e confirmação.

Não há endpoint genérico de upload direto de PDF. Essa solução reutiliza os contratos
existentes e exige um XML ativo com integridade disponível. Repetir a mesma seleção
mantém a identidade do pacote e a reutilização idempotente.

### 4. Visualização e revisão de PDFs

Na conferência do ZIP, **Visualizar e corrigir PDF** abre páginas e zoom dentro do
site. O usuário pode escolher um trecho ou posicionar texto manualmente, adicionar
correções ao rascunho, aplicar à cópia e baixar o PDF revisado.

A revisão é **visual e feita em uma cópia**: acrescenta texto, opcionalmente com
fundo branco. O texto anterior pode continuar recuperável na estrutura do arquivo.
Não é remoção definitiva de dados, OCR ou reconstrução da diagramação original.
O XML e o documento original não são modificados.

Depois de aplicar revisões, a confirmação do ZIP original fica bloqueada. É preciso
gerar o ZIP revisado, conferir novamente e escolher seus vínculos antes de importar.
Bytes e revisões ficam na memória da aba; downloads permitem guardar as cópias.

### 5. Exclusão, arquivamento e restauração

| Ação | Comportamento |
| --- | --- |
| Remover ZIP/XML/PDF da seleção | Limpeza local, sem excluir registros importados |
| Remover ZIP com revisões pendentes | Confirmação antes de descartar as cópias que estão apenas na aba |
| Excluir XML ou PDF importado | Arquivamento reversível, retirando da lista ativa e da seleção de envio |
| Excluir ZIP importado | Conferência de impacto e arquivamento transacional dos arquivos associados |
| Restaurar | Recupera o mesmo registro individualmente; mantém IDs, bytes e vínculos |
| Excluir cliente | Exclusão definitiva confirmada de cliente, viagens, documentos e PDFs exclusivos; PDFs de outros clientes são preservados |
| Excluir viagem individualmente | Exclusão do cadastro somente quando não há vínculos impeditivos |

O ZIP original não é armazenado como arquivo único pelo servidor. Excluir um ZIP
no painel arquiva seus XMLs/PDFs, preservando a identidade do pacote e seu histórico.

A conferência mostra arquivos compartilhados com outras viagens/pacotes. Se os
metadados, estados ou vínculos mudarem antes da confirmação, a operação é recusada
com 409 e exige nova conferência. Falhas no lote desfazem todas as alterações.

Reimportar o mesmo ZIP não restaura arquivos silenciosamente. A restauração é
individual para respeitar documentos que já estavam arquivados anteriormente.

### Limites atuais

| Recurso | Limite padrão |
| --- | --- |
| Upload de XML/PDF/ZIP | 20 MiB por arquivo |
| Requisição multipart | 21 MiB |
| Entradas no ZIP | 100, incluindo diretórios |
| Arquivo descomprimido | 20 MiB |
| Total descomprimido | 100 MiB |
| Proporção de compressão | Até 100:1 para entradas acima de 1 MiB |
| Revisões visuais | 100 correções por PDF; 500 caracteres por correção |
| Cópia PDF revisada | 20 MiB; até 100 MiB de cópias na sessão |
| Conferência de e-mail | Validade de 30 minutos |
| Anexos de e-mail | 20 MiB por arquivo e 100 MiB somados, antes do overhead MIME |

ZIPs aninhados/protegidos, tipos não permitidos, nomes ambíguos, caminhos inseguros
e CRC divergente são recusados. O reconhecimento de PDF na inspeção verifica apenas
o cabeçalho `%PDF-`; um PDF pode passar por essa etapa e falhar no visualizador.

## Diagnóstico, Central de Pendências e recuperação

A tela **Processamentos** reúne os registros reais de processamento, exportação,
tentativas documentais e avisos incertos. Os filtros **Precisam de ação**,
**Em processamento** e **Resolvidos** usam paginação no backend, com 20 operações
por página na interface. O menu usa **um contador** de itens que precisam de ação
(falhas + atenção), limitado visualmente a `99+`; a divisão completa fica na tela.
Falha de consulta mostra um único `!` acessível, sem presumir zero pendências.

Cada diagnóstico mostra etapa, código, operação, requisição, data, pacote/arquivo
quando identificados, causa comprovada, impacto e orientação. A interface usa
**vermelho para falha/bloqueio**, **âmbar para pendência/incerteza** e **verde para
conclusão comprovada**, sempre com texto/ícone, contraste nos dois temas e teclado.
Os cartões persistem; fechar detalhes ou copiar informações não resolve problemas.

### Recuperar sem repetir trabalho

- **Selecionar ZIP corrigido:** inicia nova conferência vinculada e invalida a
  seleção/confirmação anterior. A correção preserva documentos originais e versões.
- **Revisar associações:** abre a seleção de PDFs da operação atual. Se o ZIP
  não estiver na aba, orienta selecioná-lo novamente para conferir os vínculos.
- **Abrir configurações da pasta:** direciona à configuração existente do destino.
- **Tentar somente a exportação:** retoma a cópia no destino histórico, preservando
  importação, arquivos anteriores e SHA-256, sem reimportar ou disparar avisos.
- **Consultar tentativa de envio:** faz leitura do histórico existente, sem confirmar
  envio ou reenvio. Resultado desconhecido continua exigindo revisão.
- **Ver detalhes / Copiar informações para suporte:** mostra ou copia somente
  referências mínimas, data, etapa, código, versão e nomes/IDs adequados de arquivos.

Uma nova conferência válida pode resolver o erro de conteúdo anterior, mas as
associações pendentes da nova seleção continuam abertas até evidência de correção.
A resolução mantém o diagnóstico original e sua referência. A exportação registra
as tentativas e seus problemas no próprio histórico, mesmo depois de concluída.
Não foi criada tabela paralela de pendências nem encerramento manual sem evidência.

Falhas de consulta GET aparecem em vermelho, com mensagem específica e **Tentar
novamente** em Configurações e Processamentos. UUID, código, rota e status ficam
em **Ver detalhes**. A Central indisponível permite tentar a consulta novamente.

Se os documentos foram importados e a pasta falhar, o resultado é
**“Documentos importados — exportação pendente”**. Corrija o destino e retome apenas
a cópia. Se a resposta de uma alteração não puder ser confirmada, o sistema informa resultado não confirmado,
sem presumir falha ou perda de documentos. Não há porcentagem fictícia por arquivo.

Para experimentar com segurança, monte um ZIP sintético com o CT-e de demonstração
e um texto chamado `cobrancas/fatura.pdf`. A conferência identifica o erro em vermelho
e bloqueia a importação. Use **Selecionar ZIP corrigido**, forneça um PDF sintético
válido, confira, associe e confirme. Consulte a evidência em **Resolvidos**.

Contrato, localização, recuperação de pasta, testes e limitações em
[Diagnóstico e Recuperação — V10](docs/diagnostico-recuperacao.md).

## Banco de dados e armazenamento

### Esquema exigido pelo código atual: V15

| Migração | Finalidade |
| --- | --- |
| V1 | Clientes, viagens, documentos e envios |
| V2 | Identidade documental e coordenação |
| V3, em Java | Conferência/preenchimento controlado da identidade legada |
| V4 | Restrições de integridade e unicidade |
| V5 | Arquivamento de documentos |
| V6 | Pacotes, PDFs e vínculos compartilhados |
| V7 | Conferências e tentativas manuais de e-mail |
| V8 | Usuários, auditoria e histórico persistente de processamento |
| V9 | Configuração da operação, testes, recebimentos, notificações, exportações e seus arquivos |
| V10 | Correlação, resolução de processamentos e histórico de tentativas/diagnósticos de recuperação da exportação |
| V11, em Java | Conferências da confirmação ao motorista, revisão da seleção e ampliação do histórico de notificações para REAL, snapshot e reenvio vinculado |
| V12 | Versão otimista de operadores |
| V13 | Responsabilidade e eventos auditados da fila, sem duplicar históricos técnicos |
| V14 | Índices de paginação e consultas compartilhadas |
| V15 | Preferências administrativas da conta de teste de e-mail, sem senhas ou tokens |

A aplicação valida o esquema com `spring.jpa.hibernate.ddl-auto=validate` e exige
histórico Flyway até V15. As exclusões de ZIP reutilizam colunas existentes de
arquivamento e não acrescentaram migração.

Migrações automáticas ficam desativadas por padrão. Bancos existentes precisam
de manutenção autorizada, com inventário e backup verificado, conforme
[o procedimento de banco e migrações](docs/etapa4-banco-migracoes.md). A versão
exigida pelo código não comprova que uma base operacional específica foi migrada.

### Arquivos e integridade

- A raiz do acervo é configurada por `rotabrasil.arquivos.viagens`.
- XMLs novos são organizados como `{nCT}/{chaveAcesso}.xml`.
- PDFs usam nomes gerados pelo armazenamento e vínculos registrados no banco.
- Operações incompletas mantêm journal/temporários sob `.pendentes/op-{UUID}`.
- Exclusões definitivas de cliente usam `.exclusoes-clientes/{UUID}.json` para
  concluir a limpeza dos arquivos após o commit, inclusive depois de reiniciar.
- Chave, SHA-256 e restrições únicas protegem identidade e duplicidade.
- Publicação sem sobrescrita, coordenação transacional e compensação de gravações
  próprias protegem o acervo em falhas e importações concorrentes.
- Downloads e preparação de envio conferem a raiz permitida e a integridade.
- A raiz precisa suportar hard links e travas de arquivo.

`arquivos/entrada` é uma convenção local; não existe recebimento automático dessa
pasta nesta versão. O recebimento atual ocorre pelo painel ou pelos endpoints de upload.
Banco, documentos reais, credenciais e artefatos gerados não devem ser versionados.

## Segurança e perfis de execução

| Perfil | Comportamento |
| --- | --- |
| `desenvolvimento` | Padrão; loopback `127.0.0.1`, sem autenticação, para banco/acervo isolados |
| `operacao` | Autenticação ativa, permissões, cookie seguro, H2 Console desligado e migrações automáticas desativadas |

O perfil de desenvolvimento recusa a URL operacional padrão do projeto. É necessário
informar explicitamente uma base e uma raiz isoladas. O perfil de operação exige
Hibernate em `validate` e recusa configurações incompatíveis com suas proteções.

Com segurança ativa:

- **CONSULTA:** leitura dos recursos permitidos.
- **OPERADOR:** importações, conferências, tentativas manuais e arquivamento/restauração.
- **ADMIN:** operações de operador, APIs administrativas e exclusão protegida de clientes/viagens.
- Senhas usam BCrypt; requisições de alteração exigem CSRF.
- Sessões são revalidadas após mudança de papel ou desativação do usuário.
- A auditoria registra ator, recurso, resultado HTTP e correlação de requisição.

O primeiro administrador, quando necessário, depende de `ROTABRASIL_ADMIN_USUARIO`
e `ROTABRASIL_ADMIN_HASH`. A CLI `br.com.rotabrasil.manutencao.GerarHashSenha` lê
a senha em terminal interativo, sem recebê-la como argumento. O perfil de operação
usa cookie `Secure` e requer configuração de HTTPS compatível com a implantação.

`GET /login` entrega formulário próprio integrado ao Spring Security. O 404 do
[registro histórico da modernização](docs/modernizacao-frontend.md) foi superado
no código atual. Veja [uso compartilhado](docs/uso-compartilhado.md) para contratos,
permissões, limitações e roteiro com dois operadores.

`GET /` é público e não inicializa o painel. O login válido redireciona para
`/painel`, protegido no perfil operacional. Consulte
[apresentação, acesso e sessão](docs/acesso-apresentacao-login-painel.md) para
compatibilidade dos favoritos, logout, expiração e teste HTTPS isolado com context path.

## Como executar localmente

### Requisitos

1. JDK **25** disponível no terminal.
2. Maven compatível, utilizando esse JDK.
3. Porta HTTP livre e diretório separado para banco/acervo de desenvolvimento.

Confira `java -version` e `mvn -version`. A aplicação usa a porta 8080 por padrão.
Os comandos a seguir compilam fora do repositório e iniciam uma base nova isolada.
Use o frontend **de dentro desse mesmo JAR**; não acrescente
`spring.web.resources.static-locations=file:.../front/` a um JAR
anterior. Essa combinação reproduziu os dois erros 404. O painel agora verifica
`GET /api/versao` antes de inicializar e identifica backend incompatível.

Os comandos Maven abaixo são executados na **raiz do repositório**. O `pom.xml`
da raiz compila o módulo `back`; também é possível usar `mvn -f back/pom.xml`.
Sem diretório externo, o JAR fica em `back/target/rota-brasil-0.0.1-SNAPSHOT.jar`.
Veja [a organização e a comunicação entre front e back](docs/front-back.md).

### Windows / PowerShell

```powershell
# Compilar a aplicação, preservando o target/ histórico do projeto.
$Build = Join-Path $env:TEMP 'opencode/rotabrasil-build'
mvn -B "-Drotabrasil.build.directory=$Build" -DskipTests clean package

# Criar uma área nova para esta demonstração.
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Select-Object ProcessId, CommandLine
if (Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue) {
  throw 'Porta 8080 ocupada. Identifique a instância antes de iniciar outra.'
}
$Demo = Join-Path $env:TEMP ('opencode/rotabrasil-demo-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $Demo -Force | Out-Null
$Db = (Join-Path $Demo 'banco') -replace '\\', '/'
$Files = Join-Path $Demo 'viagens'
$Exports = Join-Path $Demo 'exportacoes'
New-Item -ItemType Directory -Path $Exports | Out-Null

$RunArgs = @(
  '-jar', "$Build/rota-brasil-0.0.1-SNAPSHOT.jar",
  '--spring.profiles.active=desenvolvimento',
  '--server.address=127.0.0.1', '--server.port=8080',
  "--spring.datasource.url=jdbc:h2:file:$Db;WRITE_DELAY=0",
  '--spring.flyway.enabled=true', '--spring.h2.console.enabled=false',
  "--rotabrasil.arquivos.viagens=$Files",
  "--rotabrasil.exportacao.raiz=$Exports",
  '--rotabrasil.email.modo=SIMULADO'
)
java @RunArgs
```

### Linux / Bash

```bash
BUILD_DIR="${TMPDIR:-/tmp}/rotabrasil-build"
mvn -B "-Drotabrasil.build.directory=$BUILD_DIR" -DskipTests clean package
DEMO_DIR="$(mktemp -d "${TMPDIR:-/tmp}/rotabrasil-demo.XXXXXX")"
mkdir "$DEMO_DIR/exportacoes"

java -jar "$BUILD_DIR/rota-brasil-0.0.1-SNAPSHOT.jar" \
  --spring.profiles.active=desenvolvimento \
  --server.address=127.0.0.1 --server.port=8080 \
  "--spring.datasource.url=jdbc:h2:file:$DEMO_DIR/banco;WRITE_DELAY=0" \
  --spring.flyway.enabled=true --spring.h2.console.enabled=false \
  "--rotabrasil.arquivos.viagens=$DEMO_DIR/viagens" \
  "--rotabrasil.exportacao.raiz=$DEMO_DIR/exportacoes" \
  --rotabrasil.email.modo=SIMULADO
```

Abra **[http://localhost:8080](http://localhost:8080)**. A migração automática habilitada
nesses exemplos destina-se apenas à base nova criada para a demonstração.
Os comandos de empacotamento usam `-DskipTests`; as verificações estão na seção de testes.
Em outro terminal, verifique com limite de tempo:
`curl --max-time 5 http://127.0.0.1:8080/api/versao` e
`curl --max-time 5 http://127.0.0.1:8080/api/configuracoes`.
No Linux, confira antes `ss -ltnp` e os processos Java. Se a aplicação não iniciar,
consulte seu log; não encerre processos desconhecidos. `java -jar` permanece aberto
por ser o servidor; não espere seu encerramento como evidência de prontidão.

### Usar, reiniciar e limpar o ambiente local

- Para testar XML, use `back/src/main/resources/email/cte-sintetico.xml`, sem valor fiscal.
- Para testar ZIP/PDF, as ferramentas de navegador geram pacotes sintéticos válidos
  em seus diretórios externos de saída.
- `Ctrl+C` encerra a execução no terminal; `Ctrl+F5` atualiza os recursos no navegador.
- Para preservar os dados ao reiniciar, reutilize o mesmo caminho de banco/acervo.
  Em base já inicializada, mantenha `spring.flyway.enabled=false` e prefira
  `IFEXISTS=TRUE` na URL para evitar criar outra base por engano.
- Reexecutar o exemplo completo gera outra pasta de demonstração. Isso inicia um
  ambiente vazio e preserva a base anterior, como no reset local usado no projeto.
- Para operação sobre banco existente, siga o procedimento de manutenção e o perfil
  `operacao`; não use `ddl-auto=update`, Flyway `clean` ou `repair` como atalho.

## Configurações principais

As configurações-base ficam em `back/src/main/resources/application.properties`, com
ajustes por perfil em `application-desenvolvimento.properties` e
`application-operacao.properties`. Parâmetros de linha de comando e configuração
externa do Spring Boot permitem definir os valores para cada ambiente.

| Propriedade | Função / estado atual |
| --- | --- |
| `spring.profiles.active` | Escolhe `desenvolvimento` ou `operacao`; o perfil padrão é desenvolvimento |
| `server.address` / `server.port` | Endereço e porta HTTP; o perfil de desenvolvimento restringe o acesso a loopback |
| `spring.datasource.url` | Caminho/URL do banco H2; para desenvolvimento deve apontar para uma base isolada |
| `spring.jpa.hibernate.ddl-auto` | `validate`: confere a compatibilidade do esquema existente |
| `spring.flyway.enabled` | `false` por padrão; habilitação nos exemplos locais somente para a base nova isolada |
| `spring.flyway.clean-disabled` | `true`: mantém a limpeza destrutiva do Flyway desabilitada |
| `spring.h2.console.enabled` | `false` na configuração-base e obrigatório desligado em operação |
| `spring.servlet.multipart.max-file-size` / `.max-request-size` | Limites de upload: 20 MB e 21 MB na configuração do Spring |
| `rotabrasil.arquivos.viagens` | Raiz do acervo de XMLs e PDFs |
| `rotabrasil.exportacao.raiz` | Raiz externa autorizada para cópias; aceita `ROTABRASIL_EXPORTACAO_RAIZ`; vazia por padrão |
| `rotabrasil.email.modo` | `SIMULADO` por padrão; seleção explícita de `REAL` para o adaptador SMTP |
| `rotabrasil.notificacoes.modo` | Confirmação ao motorista: `SIMULADO` por padrão; independente do modo documental |
| `rotabrasil.notificacoes.tipo-conta` | `NAO_IDENTIFICADA` por padrão; REAL requer identificação explícita como `OUTLOOK_PESSOAL` ou `MICROSOFT_365` |
| `rotabrasil.email.limite-arquivo-bytes` / `.limite-total-bytes` | Limites dos anexos: 20 MiB individual e 100 MiB somados |
| `rotabrasil.seguranca.ativa` | Ativa o controle de acesso; exigido no perfil operacional e no SMTP real |

A persistência usa `spring.jpa.open-in-view=false`: os DTOs são montados na camada
de serviço. A configuração JSON recusa propriedades desconhecidas e números
fracionários em campos inteiros, para manter os contratos de entrada explícitos.

## Configurações da operação, notificações e exportação

### Como usar a nova aba

1. Prepare um banco **V15** e abra **Configurações** (`/painel#configuracoes`). A migração
   do banco em uso não foi executada nesta entrega e exige autorização específica.
2. Informe o **remetente da operação**, correspondente à conta Outlook que recebe
   os ZIPs. Salvar o endereço não configura senha nem autenticação SMTP.
3. Selecione uma subpasta da raiz autorizada do backend ou use **Criar subpasta**.
4. Escolha se deseja **Copiar automaticamente após nova importação concluída** e
    clique em **Salvar alterações**. O sucesso só aparece após resposta do servidor.
5. Use **Verificar pasta** quando necessário. Testes administrativos e seus históricos
   ficam recolhidos; **Verificar configuração sem envio** não autentica a conta.

Alterações não salvas são identificadas e desabilitam os testes até serem salvas.
Com segurança ativa, a configuração exige ADMIN e CSRF. Em desenvolvimento isolado,
o acesso continua em loopback, sem autenticação; esse perfil não serve para uso
compartilhado em rede. O perfil operacional usa login individual.

### Endereços e eventos separados

- **Remetente da operação:** conta Outlook configurada na aba.
- **Destinatário da confirmação opcional:** só é informado explicitamente quando
  houver endereço disponível e decisão de usar esse recurso. A operação atual não
  possui e-mail de motorista e não precisa dele para importar.
- **Destinatário dos documentos do cliente:** permanece no fluxo da Central de
  envios, sem substituição pelo remetente ou pelo destinatário da confirmação.

Não há destinatário interno fixo. O ZIP sozinho não informa de quem veio o e-mail.
O recebimento automático pelo Outlook permanece uma integração futura.

A seção **Confirmação de recebimento ao motorista** prepara e confirma uma mensagem
manual por recebimento. Os eventos abaixo são contexto, não vários e-mails reais.
Tentativas manuais usam `CONFIRMACAO_MOTORISTA` no histórico de notificações existente.

Os contratos legados de avisos simulados e seus registros foram preservados. A
interface cotidiana consulta esse histórico recolhido como **Eventos internos —
testes sem envio**; ferramentas de teste ficam na administração. Esses eventos
não enviam mensagens reais, mesmo com transporte documental REAL. Credenciais
continuam externas, sem campo ou armazenamento de senha pela interface.

Os eventos são registrados separadamente: `ARQUIVO_RECEBIDO`,
`CONFERENCIA_CONCLUIDA`, `IMPORTACAO_CONCLUIDA`, `EXPORTACAO_CONCLUIDA` e
`PROCESSAMENTO_COM_PENDENCIAS`. Recebimento não significa documentos lidos ou
importados. Os avisos contêm metadados mínimos e referência da operação, sem
anexos fiscais, caminhos físicos ou conteúdo de documentos. Cada evento é
idempotente dentro do recebimento reconhecido pelo SHA-256 do ZIP.

### Em qual computador os arquivos são exportados?

A cópia desta versão fica **no computador onde o backend executa**. Se o navegador
acessa um servidor remoto, os arquivos ficam naquele servidor, não no computador
do navegador. O painel não aceita caminho arbitrário nem oferece acesso livre ao disco.

O responsável deve criar uma pasta **fora do checkout e separada do acervo interno** e configurar:

```powershell
$env:ROTABRASIL_EXPORTACAO_RAIZ = 'C:/RotaBrasil/Exportacoes'
```

Ou fornecer `--rotabrasil.exportacao.raiz=/pasta/autorizada` na inicialização.
A raiz precisa existir e permitir gravação/publicação por hard link. O teste da
aba verifica essas capacidades; não há promessa de acesso a pastas de rede.
Uma raiz que sobreponha o checkout detectado no diretório de execução ou na origem
do JAR/classes impede a inicialização com **Configuração incompatível**. O sistema
não troca esse destino silenciosamente. Corrija a propriedade externa explicitamente.

Exemplo da estrutura controlada pelo sistema:

```text
Raiz autorizada / subpasta escolhida /
  pacote-42/
    viagens/
      66213/documento-101.xml
      66213/documento-viagem-201.pdf
      66214/documento-102.xml
    faturas/fatura-202.pdf
    manifesto.json
```

O manifesto identifica origens, viagens, localizações lógicas e SHA-256. Uma fatura
compartilhada é copiada uma única vez. Nomes e caminhos internos do ZIP não são
usados como destinos confiáveis. São copiados apenas os documentos/PDFs ativos
elegíveis do pacote importado, mantendo o acervo original e os caminhos do banco.

### Exportar, tratar falhas e retomar

- Após uma nova importação, a cópia ocorre se a opção automática estava salva.
- Em **Importar ZIP → ZIPs importados**, use **Exportar arquivos** para ação manual
  ou **Ver exportações** para consultar os resultados e os arquivos de cada cópia.
- Alterar a pasta não copia retroativamente o acervo. Exportar novamente para um
  novo destino exige ação manual; repetir o destino reconhecido reutiliza o registro.
- Em falha parcial, o painel mostra **Importado; exportação pendente**, com arquivo,
  localização lógica, etapa, código e orientação. Corrija a causa e use
  **Tentar apenas a exportação novamente**. Não é necessário reimportar nem reenviar.
- Arquivos preexistentes não são sobrescritos, mesmo com bytes iguais. Escolha
  outra subpasta salva e use a ação manual para nova exportação, ou revise o conflito.

Históricos guardam a configuração efetiva e não são reescritos por alterações
posteriores. Um staging próprio permite reconhecer publicações interrompidas;
pendências só são retomadas por ação explícita. A validação mantém SHA-256, limite
de 20 MiB por arquivo, 100 arquivos documentais e 100 MiB de cópia incluindo manifesto.

Contrato, recuperação, testes e instruções completas em
[Configurações, exportação e notificações](docs/configuracoes-exportacao-notificacoes.md).

## Confirmação de recebimento ao motorista

Recurso **opcional**, independente do envio documental. Não há endereço de motorista
na operação informada, portanto importe normalmente sem preenchê-lo. O recurso e
seus históricos continuam acessíveis na seção recolhida do recebimento e por
**Ver avisos**. Caso futuramente haja destinatário e decisão de usar o canal:

1. Revise o endereço explícito; o backend não o extrai do ZIP nem usa o cliente do CT-e.
2. Confira remetente, destinatário, assunto, corpo, pacote e modo. Preparar não envia.
3. Só confirme um envio disponível com autorização específica. Sem transporte real
   configurado, o fluxo cotidiano fica indisponível; não há envio real automático.
4. Consulte o resultado. `ACEITO_SMTP` não comprova entrega/leitura; falha não desfaz
   importação/exportação. Reenvio exige nova conferência vinculada à tentativa anterior.

O padrão é `rotabrasil.notificacoes.modo=SIMULADO`. O modo REAL requer ativação
externa, segurança ativa, sessão OPERADOR/ADMIN, CSRF, tipo de conta identificado,
remetente compatível e OAuth2/XOAUTH2. O token é fornecido por arquivo externo protegido,
renovado por ferramenta de identidade externa; não é recebido no painel ou em argumentos
de comandos. Uso empresarial foi informado, mas plataforma/tenant e autorização
ainda não foram homologados; nenhum envio externo foi executado. Não existe
fallback silencioso para simulação ou autenticação básica.

Em Configurações, **Conferir teste REAL** só fica disponível com pré-requisitos
válidos. ADMIN revisa uma mensagem sintética e confirma aquele teste explicitamente.
Salvar o endereço não autentica a conta Microsoft. O acesso ao RotaBrasil já usa
o formulário `/login`, com sessão e CSRF; a autorização OAuth2 do SMTP continua externa.

Contrato, autenticação Microsoft, parâmetros externos, teste controlado, histórico e
recuperação em [Etapa E-mail — confirmação ao motorista](docs/confirmacao-motorista.md).

## Envio de e-mail

Esta seção trata do **envio documental ao cliente**, independente da confirmação
ao motorista descrita acima. Ativar a confirmação REAL não ativa este modo REAL.
Sem transporte real configurado, a Central mostra **Envio não configurado** e não
habilita uma ação de envio que apenas simularia. O destinatário e os anexos são
escolhidos explicitamente; fatura compartilhada não define quem deve recebê-la.
Consulte [preparação Microsoft e homologação futura](docs/envio-manual.md#preparação-microsoft-para-homologação-futura).

O painel utiliza a conferência persistida, com leitura dos arquivos, validação de
integridade e snapshot de destinatário/anexos. A confirmação recebe o ID da
conferência e `confirmado: true`; não aceita substituir a seleção nesse momento.

Uma confirmação repetida recupera a mesma tentativa. Reenvio deliberado exige nova
conferência vinculada à tentativa anterior. Queda ou timeout não autoriza repetição
automática: a tentativa pode permanecer em estado desconhecido para revisão humana.

| Estado | Significado |
| --- | --- |
| `SIMULADO` | Fluxo concluído em simulação, sem envio de e-mail real |
| `ACEITO_SMTP` | Servidor SMTP aceitou a mensagem; não comprova entrega ou leitura |
| `FALHA` | Recusa informada pelo transporte, sem aceite confirmado |
| `DESCONHECIDO` | Resultado incerto/interrompido; exige revisão antes de reenviar |

### Configuração SMTP implementada

O modo real possui adaptador próprio, separado da CLI Gmail, e depende de:

| Propriedade | Finalidade |
| --- | --- |
| `rotabrasil.email.modo=REAL` | Seleção explícita do transporte real |
| `rotabrasil.email.smtp.ativado=true` | Ativação adicional do SMTP |
| `rotabrasil.seguranca.ativa=true` | Controle de acesso obrigatório para SMTP real |
| `rotabrasil.email.smtp.host` / `.porta` | Servidor e porta; padrão 587 |
| `rotabrasil.email.smtp.usuario` / `.senha` | Credenciais externas de autenticação |
| `rotabrasil.email.smtp.remetente` | Endereço único do remetente |
| `rotabrasil.email.smtp.timeout-ms` | Timeout de 1.000 a 30.000 ms; padrão 10.000 |
| `rotabrasil.email.smtp.autenticacao` | `BASIC` legado ou `XOAUTH2`; confirmação Microsoft REAL exige `XOAUTH2`, sem fallback |
| `rotabrasil.email.smtp.oauth2.token-arquivo` | Arquivo externo protegido de token/expiração, lido a cada uso; nenhuma credencial na API |

O adaptador exige STARTTLS, verificação de identidade do servidor e, para H2 em
arquivo, `WRITE_DELAY=0`. Credenciais devem ser fornecidas externamente, sem commit.
O modo padrão continua **SIMULADO**; nenhum e-mail real foi enviado nas verificações
documentadas. A configuração e a homologação no ambiente SMTP real permanecem pendentes.

O endpoint legado de prévia confere metadados e não envia e-mail; ele é distinto
da conferência persistida usada pelo painel. A [CLI Gmail](docs/teste-gmail.md) é
uma ferramenta manual independente, limitada a teste com anexo sintético.

## API REST

As respostas de domínio utilizam DTOs, sem caminhos físicos internos ou entidades
JPA aninhadas. Viagem recebe `clienteId`; campos fora do contrato são recusados.
Confira os contratos detalhados antes de integrar outro consumidor.

### Cadastros e consultas

| Método e rota | Uso |
| --- | --- |
| `GET /api/clientes` / `GET /api/clientes/{id}` | Listar e consultar clientes |
| `POST /api/clientes` / `PUT /api/clientes/{id}` | Criar e atualizar cliente |
| `GET /api/clientes/{id}/conferencia-exclusao` | Conferir o impacto da exclusão conjunta do cliente |
| `DELETE /api/clientes/{id}` | Excluir cliente e seus vínculos com `confirmado=true` e `conferenciaSha256`; cliente vazio também aceita DELETE simples |
| `GET /api/viagens` / `GET /api/viagens/{id}` | Listar e consultar viagens |
| `POST /api/viagens` / `PUT /api/viagens/{id}` | Criar e atualizar viagem |
| `DELETE /api/viagens/{id}` | Excluir viagem sem documentos ou histórico impeditivo |
| `GET /api/consulta/resumo` | Indicadores e pendências reais |
| `GET /api/consulta/viagens` / `GET /api/consulta/clientes` | Consultas paginadas disponíveis no backend |
| `GET /api/sessao` | Usuário, papéis, modo de e-mail e informações de CSRF |
| `GET /api/versao` | Versão Maven, instante de build, contrato e SHA-256 de recursos estáticos do JAR; segue a autenticação das consultas no perfil protegido |

### Documentos, pacotes e processamento

| Método e rota | Uso |
| --- | --- |
| `POST /api/processamento/xml` | Upload multipart `arquivo` de CT-e |
| `GET /api/documentos` | Metadados dos documentos ativos |
| `GET /api/viagens/{id}/documentos` | Documentos ativos da viagem |
| `GET /api/viagens/{id}/documentos/arquivados` | Documentos arquivados da viagem |
| `GET /api/documentos/{id}/arquivo` | Download controlado do documento |
| `DELETE /api/documentos/{id}` / `POST /api/documentos/{id}/restaurar` | Arquivar e restaurar documento |
| `POST /api/pacotes/inspecao` | Conferência de ZIP multipart |
| `POST /api/pacotes/confirmacao` | Confirmação com o mesmo ZIP e a parte JSON `configuracao` |
| `POST /api/pacotes/previa-pdf` | Bytes de um PDF do mesmo ZIP conferido, com hash e ID da entrada |
| `GET /api/pacotes` | ZIPs importados, paginação e filtro de arquivos arquivados |
| `GET /api/pacotes/{id}/conferencia-exclusao` | Conferência dos arquivos e vínculos para arquivamento do ZIP |
| `DELETE /api/pacotes/{id}` | Arquivamento com `confirmado=true` e `conferenciaSha256` |
| `GET /api/viagens/{id}/anexos` | PDFs da viagem; aceita `arquivados=true` |
| `GET /api/anexos/{id}/arquivo` | Download controlado do PDF |
| `DELETE /api/anexos/{id}` / `POST /api/anexos/{id}/restaurar` | Arquivar e restaurar PDF |
| `GET /api/processamentos` / `GET /api/processamentos/{id}` | Histórico persistente e diagnóstico da operação |
| `GET /api/diagnosticos/{origem}/{id}` | Contrato seguro para PROCESSAMENTO, EXPORTACAO, ENVIO ou NOTIFICACAO |
| `GET /api/pendencias` | Central com filtro de estado, paginação SQL e resolução baseada em evidência |
| `GET /api/pendencias/resumo` | Contadores distintos de falhas, atenção/incerteza, andamento e resolvidos |
| `GET/PUT /api/configuracoes` | Consultar/salvar remetente, destino e cópia automática com controle de versão |
| `POST /api/configuracoes/subpastas` | Criar uma subpasta de nome controlado na raiz autorizada |
| `GET /api/configuracoes/testes` | Resultados persistentes de testes |
| `POST /api/configuracoes/teste-pasta` / `POST /api/configuracoes/teste-email` | Testar gravação ou simular e-mail sem rede |
| `GET/POST /api/pacotes/{id}/exportacoes` | Histórico e ação manual confirmada de exportação |
| `GET /api/exportacoes/{id}` / `POST /api/exportacoes/{id}/retomar` | Consultar ou retomar itens pendentes no destino original |
| `GET /api/recebimentos?sha256=...` / `GET /api/recebimentos/{id}` | Consultar recebimento e avisos separados dos envios documentais |
| `POST /api/recebimentos/{id}/notificacoes/{notificacaoId}/simular` | Confirmar simulação idempotente de um aviso |
| `POST /api/recebimentos/{id}/conferencias-confirmacao` | Conferir mensagem ao motorista com destinatário explícito e `anteriorId` em reenvio |
| `POST /api/recebimentos/{id}/confirmacoes` | Confirmar `{conferenciaId, confirmado:true}` sem duplicar tentativa |
| `POST /api/configuracoes/conferencias-email-real` / `POST /api/configuracoes/confirmacoes-email-real` | ADMIN confere e confirma teste sintético REAL com pré-requisitos válidos |
| `GET /api/configuracoes/conferencias-email-real/{id}` | Consultar snapshot do teste, sem envio |

### Conferência e envio manual

| Método e rota | Uso |
| --- | --- |
| `POST /api/viagens/{id}/previa-email` | Prévia legada de metadados, sem envio |
| `GET /api/viagens/{id}/envios` | Histórico legado |
| `POST /api/viagens/{id}/conferencias-email` | Conferir bytes, vínculos e snapshot persistido |
| `POST /api/viagens/{id}/tentativas-email` | Confirmar a conferência de forma idempotente |
| `GET /api/viagens/{id}/tentativas-email` | Histórico das tentativas novas |
| `GET /api/viagens/{id}/tentativas-email/{tentativaId}` | Consultar uma tentativa e seu resultado |

### Administração disponível na API

`GET/POST /api/admin/usuarios`, `PUT /api/admin/usuarios/{id}`,
`GET /api/admin/auditoria`, `GET /api/admin/saude` e `GET/POST /api/admin/retencao`.
O backend mantém gestão de operadores, saúde/capacidade, auditoria e retenção
controlada de metadados elegíveis, embora o painel administrativo tenha sido removido.
Com segurança ativa, essas rotas exigem ADMIN. Autenticação e logout são tratados
pelo Spring Security; a pendência do formulário `/login` está registrada acima.

## Testes e verificações

Os testes usam **fixtures sintéticas, H2 e diretórios isolados**, sem abrir banco
operacional, utilizar documentos fiscais reais ou enviar e-mails reais.

### Backend e auxiliares Python

```powershell
$Build = Join-Path $env:TEMP 'opencode/rotabrasil-build'
mvn -B "-Drotabrasil.build.directory=$Build" clean test

# Recorte direcionado usado na entrega de exclusões e páginas:
mvn -B "-Drotabrasil.build.directory=$Build" `
  "-Dtest=ArquivamentoPacoteHttpTest,PacoteAssistidoHttpTest,SegurancaHttpTest" test

# Auxiliar Python, sem publicar arquivos no acervo Java:
python -m unittest discover -s python -p 'test_*.py'
```

O build externo preserva `target/` histórico. JUnit gera relatórios em
`<diretorio-do-build>/surefire-reports/`. Os testes cobrem contratos, integridade,
concorrência, arquivos, migrações, arquivamento, segurança e envio simulado.

### Navegador

Depois de empacotar o JAR atualizado:

```powershell
$Build = Join-Path $env:TEMP 'opencode/rotabrasil-build'
mvn -B "-Drotabrasil.build.directory=$Build" -DskipTests package
npm ci --prefix scripts
npx --prefix scripts playwright install chromium
$env:ROTABRASIL_BROWSER_CHANNEL = 'chromium'
$Ui = Join-Path $env:TEMP ('opencode/rotabrasil-ui-' + [guid]::NewGuid().ToString('N'))

node scripts/testar-exclusoes-paginas.cjs "$Build/rota-brasil-0.0.1-SNAPSHOT.jar" "$Ui/exclusoes"
node scripts/testar-previa-pdf.cjs "$Build/rota-brasil-0.0.1-SNAPSHOT.jar" "$Ui/pdf"
node scripts/testar-modernizacao.cjs "$Build/rota-brasil-0.0.1-SNAPSHOT.jar" "$Ui/modernizacao"
node scripts/testar-configuracoes.cjs "$Build/rota-brasil-0.0.1-SNAPSHOT.jar" "$Ui/configuracoes"
node scripts/testar-diagnostico-recuperacao.cjs "$Build/rota-brasil-0.0.1-SNAPSHOT.jar" "$Ui/recuperacao"
node scripts/testar-estabilizacao.cjs "$Build/rota-brasil-0.0.1-SNAPSHOT.jar" "$Ui/estabilizacao"
node scripts/testar-confirmacao-motorista.cjs "$Build/rota-brasil-0.0.1-SNAPSHOT.jar" "$Ui/motorista"

# Fluxo local combinado, sem exigir formulário de login:
node scripts/testar-envio-manual.cjs "$Build/rota-brasil-0.0.1-SNAPSHOT.jar" "$Ui/envio-local" --somente-local

# Fluxo ampliado; sua etapa autenticada possui a pendência /login documentada:
node scripts/testar-envio-manual.cjs "$Build/rota-brasil-0.0.1-SNAPSHOT.jar" "$Ui/envio-manual"
```

As ferramentas iniciam seus próprios servidores e bases. As saídas devem ficar fora
do repositório. Em Linux, a instalação do Chromium pode exigir `--with-deps`, como
no workflow. Os scripts Python `testar-painel.py` e `testar-pacotes.py` conservam
verificações de etapas anteriores; a navegação atual é exercitada pelos scripts Node.

### Resultados históricos — uso compartilhado V14

Execução completa daquela etapa: **387 testes contabilizados, 386 aprovados, zero
falhas/erros e 1 ignorado** (symlink no Windows). Inclui login, CSRF, permissões,
revogação de sessões, último ADMIN em concorrência, conflito de edição, atribuição
atômica, filtros/paginação e verificações independentes de saúde.

| Navegador — execuções independentes | Resultado |
| --- | --- |
| Uso compartilhado, dois operadores e celular | 9 verificações aprovadas |
| Envio manual, incluindo login/logout protegido | 14 verificações aprovadas |
| Confirmação ao motorista | 7 verificações aprovadas |
| Configurações e exportação | 7 verificações aprovadas |
| Diagnóstico e recuperação | 7 verificações aprovadas |

Nenhum erro JavaScript nas execuções aprovadas e nenhum envio externo. Build,
relatórios e capturas ficam fora do checkout. Comandos e evidências em
[Verificação da entrega](docs/uso-compartilhado.md#verificação-da-entrega).
Os resultados históricos a seguir não devem ser somados aos atuais.

### Resultados históricos — Etapa E-mail V11

- Suíte JUnit **completa**, concluída em 31/10/2026 às 01:56:57 -03:00:
  **378 contabilizados, 377 aprovados, zero falhas/erros, 1 ignorado** por privilégio
  de symlink no Windows. Build externo em `rotabrasil-email-motorista-build`.
- Navegador em execuções separadas: confirmação ao motorista **7**, Configurações
  **7**, recuperação **7**, estabilização **5** e envio documental local **13**
  verificações/cenários aprovados, sem erros JavaScript inesperados.
- REAL foi exercitado com transporte controlado nos testes HTTP protegidos; nenhuma
  conexão SMTP externa foi feita. Não há homologação real da conta.
- Foi preservada a limitação do acesso protegido no navegador (`/login` 404),
  separada da simulação local e da validação de sessão/papéis/CSRF nos testes HTTP.
- Comando completo usado: `mvn -B "-Drotabrasil.build.directory=$Build" -DargLine=-Xmx768m clean test`.
  O limite de heap controla o ambiente de teste, sem alterar regras de domínio.
- Evidências, testes e pendências: [Confirmação ao motorista](docs/confirmacao-motorista.md).

Os totais acima pertencem a esta revisão; não são soma dos registros abaixo.

### Resultados históricos — estabilização V10

- Suíte JUnit completa: **360 testes, 359 aprovados, zero falhas/erros, 1 ignorado**
  (symlink sem privilégio no Windows). Execução final concluída em 31/10/2026;
  não é soma de recortes. Build externo em `rotabrasil-estabilizacao-build`.
- Navegador: Configurações **7**, diagnóstico/recuperação **7**, estabilização **5**,
  modernização **10** verificações aprovadas, em execuções separadas com bases isoladas.
- Fluxo ampliado de envio: **13 cenários locais concluídos**; a continuação protegida
  bloqueia em `GET /login` 404. O resultado local é salvo separadamente e não transforma
  o cenário protegido em aprovado. Não houve SMTP real.
- O teste de Configurações conserva `fixture-sintetica.zip` na saída externa para
  experimentar o roteiro manual com dois CT-es e fatura compartilhada.
- Evidências, falhas intermediárias investigadas e limites: [estabilização](docs/estabilizacao.md).

### Resultados históricos de entregas anteriores

| Verificação | Resultado documentado |
| --- | --- |
| V10: build limpo direcionado de diagnóstico/recuperação e regressões | **128 aprovados**, sem falhas, erros ou ignorados; recorte detalhado no contrato V10 |
| V10: recorte final de diagnóstico/recuperação | **12 aprovados**, com testes em comum com o recorte anterior |
| V10: navegador — recuperação e regressão de Configurações | **7 + 7 verificações aprovadas**, sem erros JavaScript inesperados |
| V9: configurações/exportações/notificações e regressões de ZIP/envio/segurança/backup | **81 aprovados** no build limpo direcionado; ver recorte e cobertura no contrato V9 |
| V9: recorte funcional final de configuração/exportação | **19 aprovados**, incluindo destino indisponível e origem arquivada na retomada; possui testes em comum com o recorte anterior |
| V9: navegador com reinício, falha de destino e retomada | **7 verificações aprovadas**, sem erro JavaScript |
| Backend direcionado de exclusões/ZIP/segurança | **38 aprovados**: 8 + 25 + 5 |
| Navegador: exclusões e páginas | **9 verificações aprovadas** |
| Navegador: editor PDF | **11 verificações aprovadas** |
| Navegador: modernização | **10 verificações aprovadas** |
| Console das três execuções acima | Nenhum erro JavaScript observado |
| Empacotamento Maven externo | Aprovado |
| Fluxo ampliado de envio manual | 13 cenários concluídos na execução registrada; etapa de login bloqueada por 404 |
| Suíte JUnit completa na entrega V9/V10 anterior | Não executada naquela entrega; os resultados anteriores são de recortes direcionados |

Esses números são de recortes e execuções registradas, não o total de testes do
repositório. Resultados históricos de outras versões permanecem em `docs/` e não
devem ser somados como se fossem uma única execução. O cenário autenticado de
navegador foi concluído na evolução V14; resultados atuais em
[uso compartilhado](docs/uso-compartilhado.md#verificação-da-entrega).

## Limitações e próximos passos

1. **Homologação documental Microsoft:** confirmar plataforma/tenant/políticas da caixa Outlook da empresa, autorizar OAuth2 restrito a envio, fornecer/renovar token externo e aprovar remetente/destinatário/conteúdo/anexos de um teste específico. Confirmação ao motorista é opcional; não é requisito comercial.
2. **Implantação no ambiente de uso:** verificar separadamente o esquema V15 do banco e o acesso à pasta externa; migração operacional exige autorização e backup verificado.
3. **Documentos recentes globais:** dependem de consulta unificada com tipo, viagem, data de processamento e estado; esses metadados não estão completos nos DTOs atuais.
4. **Escala das consultas:** paginação/filtros/ordenação estão no servidor; busca de substring ainda pode exigir varredura SQL. Seletores são limitados e permitem pesquisa explícita.
5. **Evidência de backup:** manifesto não comprova por si só verificação ou restauração testada; o painel mantém estados não verificados quando falta evidência confiável.

As decisões atuais de destinatário, vínculos, confirmação e reenvio são manuais.
Baixar o ZIP manualmente permanece o escopo combinado; leitura automática do Outlook
não é pré-requisito desta entrega nem da estabilização.
Não há inferência de destinatários, repetição automática de envio ou promessa de
entrega/leitura de e-mail. PDFs e XMLs continuam sujeitos aos limites de conferência
descritos neste README e nos contratos específicos.

## Documentação complementar

| Documento | Assunto |
| --- | --- |
| [Revisão operacional — 09/11/2026](docs/revisao-operacional-20261109.md) | Comparação Git, reprodução ZIP, correção do resumo documental, capturas, testes, instalação e pendências reais |
| [Uso diário compartilhado](docs/uso-compartilhado.md) | Login, operadores, concorrência, fila, busca, saúde, contratos e implantação centralizada V15 |
| [Revisão para equipe V15](docs/revisao-equipe-v15.md) | Acompanhamento, causas comprovadas, testes e pendências externas |
| [Gmail configurável](docs/gmail-configuravel.md) | Conta administrativa de teste, autenticação externa e confirmação sem anexos fiscais |
| [Visão geral do sistema — 21/10/2026](docs/visao-geral-do-sistema.md) | Levantamento histórico e diagramas, também em PDF e Word |
| [Etapa E-mail — confirmação ao motorista](docs/confirmacao-motorista.md) | Mensagem manual, Microsoft OAuth2, configuração segura, conferência, idempotência, reenvio, V11, testes e homologação pendente |
| [Estabilização atual](docs/estabilizacao.md) | Causa reproduzida dos 404, versões, sidebar, pasta externa, suíte completa e roteiro de recuperação |
| [Diagnóstico e Recuperação — V10](docs/diagnostico-recuperacao.md) | Central de Pendências, erros vermelhos, causas, impacto, suporte, idempotência e resolução comprovada |
| [Configurações, exportação e notificações](docs/configuracoes-exportacao-notificacoes.md) | Oitava tela, remetente Outlook, destinatário manual, raiz autorizada e retomada das cópias |
| [Exclusão de cliente e documentos](docs/exclusao-cliente-com-documentos.md) | Exclusão conjunta, PDFs compartilhados, confirmação e recuperação da limpeza |
| [Exclusões e páginas](docs/exclusao-arquivos-e-paginas.md) | Navegação atual, exclusão de seleção, ZIP/XML/PDF e restauração |
| [Modernização do frontend](docs/modernizacao-frontend.md) | Dashboard, temas, consultas e PDF individual |
| [Prévia e revisão de PDF](docs/previa-e-revisao-pdf.md) | Visualizador, cópias, limites e nova conferência |
| [Pacotes assistidos](docs/pacotes-assistidos.md) | ZIP, associações manuais e faturas compartilhadas |
| [Diagnóstico de processamento](docs/diagnostico-processamento.md) | Erros por arquivo e recuperação manual |
| [Envio manual](docs/envio-manual.md) | Conferência, estados, idempotência, snapshots e reenvio |
| [Contrato da API](docs/etapa6-contrato-api.md) | DTOs, validações, erros e proteções de cadastro |
| [Identidade documental](docs/etapa2-identidade-documental.md) | Chave fiscal, hashes e compatibilidade legada |
| [Armazenamento seguro](docs/etapa3-armazenamento-seguro.md) | Publicação, journal e recuperação conservadora |
| [Banco e migrações](docs/etapa4-banco-migracoes.md) | Inventário, backup, manutenção e migração autorizada |
| [Arquivamento de documentos](docs/exclusoes-arquivamento.md) | Regras e histórico do arquivamento reversível |
| [Documentos adicionais](docs/etapa7-documentos-adicionais.md) | Coleções e documentos de tipos diferentes |
| [Prévia legada de e-mail](docs/etapa8-previa-email.md) | Contrato anterior e compatibilidade |
| [Ferramentas de frontend](docs/frontend-testes.md) | Registros de testes e evolução da interface |
| [Teste Gmail](docs/teste-gmail.md) | CLI manual independente com documento sintético |
| [Visão geral histórica](docs/visao-geral-do-sistema.md) | Registro da evolução anterior do sistema |
| [Visão geral histórica em PDF](docs/visao-geral-do-sistema.pdf) / [Word](docs/visao-geral-do-sistema.docx) | Documentação e diagrama publicados em 21/10/2026, preservados como referência histórica |
| [Visão geral do envio manual V7](docs/visao-geral-envio-manual-v7.md) | Registro local da entrega de simulação, conferência e reenvio manual |
| [Casos de uso](docs/casos-de-uso.puml) | Diagrama PlantUML |

Os documentos das etapas anteriores conservam seu contexto histórico. Para o estado
consolidado atual, use este README junto dos contratos específicos mais recentes.
