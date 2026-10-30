# RotaBrasil

**Gestão de documentos de viagens: do recebimento do arquivo à conferência e ao envio manual.**

O RotaBrasil é uma aplicação web para receber **CT-es XML e documentos PDF**, organizar
os arquivos por viagem, associar faturas, conferir informações e acompanhar tentativas
de envio de e-mail. Backend, API REST e frontend são distribuídos na mesma aplicação
Spring Boot.

**Estado documentado:** outubro de 2026 · Java 25 · Spring Boot 3.5.6 · código requer esquema de banco **V10**.
O modo padrão de e-mail é **SIMULADO**. O adaptador SMTP real existe no código e depende
de ativação/configuração explícitas e homologação operacional.

## Sumário

- [Visão geral](#visão-geral)
- [Funcionalidades atuais](#funcionalidades-atuais)
- [Tecnologias utilizadas](#tecnologias-utilizadas)
- [Arquitetura e organização](#arquitetura-e-organização)
- [Mapa dos módulos do backend e frontend](#mapa-dos-módulos-do-backend-e-frontend)
- [Frontend e navegação](#frontend-e-navegação)
- [Como utilizar o sistema](#como-utilizar-o-sistema)
- [Fluxos de documentos](#fluxos-de-documentos)
- [Diagnóstico, Central de Pendências e recuperação](#diagnóstico-central-de-pendências-e-recuperação)
- [Banco de dados e armazenamento](#banco-de-dados-e-armazenamento)
- [Segurança e perfis de execução](#segurança-e-perfis-de-execução)
- [Como executar localmente](#como-executar-localmente)
- [Configurações principais](#configurações-principais)
- [Configurações da operação, notificações e exportação](#configurações-da-operação-notificações-e-exportação)
- [Envio de e-mail](#envio-de-e-mail)
- [API REST](#api-rest)
- [Testes e verificações](#testes-e-verificações)
- [Limitações e próximos passos](#limitações-e-próximos-passos)
- [Documentação complementar](#documentação-complementar)

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
| Auditoria | Ator, correlação de requisições, referências de recursos e resultados HTTP |
| Manutenção | Inventário, backup, migrações controladas e recuperação conservadora de armazenamento |
| Configurações | Oitava tela; remetente da operação, destino de exportação, ativação da cópia automática e testes persistentes |
| Confirmação de recebimento | Destinatário informado no upload, avisos por evento e simulação idempotente, sem anexos fiscais |
| Exportação adicional | Cópia manual ou automática de arquivos ativos para subpasta autorizada do backend, com SHA-256 e retomada de pendências |
| Diagnóstico e recuperação | Cartões persistentes, erros em vermelho, causas comprovadas, correlação, impacto, ações específicas e evidência de resolução |
| Central de Pendências | Processamentos ampliada; consulta paginada dos históricos existentes, filtros e contadores separados de falha/atenção/andamento |

A interface administrativa de operadores foi retirada do painel. As APIs administrativas
e as regras de permissão continuam no backend. O frontend atual não possui formulários
de cadastro/edição manual de cliente e viagem; esses contratos existem na API.
O painel local atual também não apresenta formulário de login. Configurações não
adiciona autenticação ou gestão de usuários; preserva as proteções já existentes.

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
| **Flyway** | Evolução versionada do banco, atualmente até V10 |
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
| **History por hash** | Oito telas com links diretos e navegação voltar/avançar |
| **Canvas e SVG** | Renderização de PDFs, ícones e ilustrações locais |
| **PDF.js 6.4.299** | Leitura e renderização das páginas dos PDFs |
| **pdf-lib 1.17.1** | Geração das cópias com revisões visuais |
| **fflate 0.8.3** | Preparação e leitura de ZIPs no navegador |
| **WebJars** | Distribuição local das bibliotecas PDF/ZIP pelo Spring Boot |

O frontend real está em `src/main/resources/static/` e é servido pelo mesmo JAR.
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
src/main/java/br/com/rotabrasil/
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

src/main/resources/
├── application*.properties
├── db/migration/    # Migrações SQL
├── email/           # CT-e sintético para demonstração/testes
└── static/          # Frontend real

src/test/            # Testes Java e fixtures sintéticas
scripts/             # Testes de navegador e dependências dessas ferramentas
python/              # Auxiliares opcionais e testes Python
docs/                # Contratos, fluxos, manutenção e resultados históricos
.github/workflows/   # Automação de verificações
pom.xml              # Build e dependências
AGENTS.md            # Instruções de trabalho no projeto
```

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
| `FluxoPacoteOperacionalService` e `NotificacaoRecebimentoService` | Eventos de recebimento/conferência/importação e notificações somente simuladas |
| `ExportacaoPacoteService` e `PublicadorExportacao` | Planos persistentes, cópia sem sobrescrita, integridade e retomada dos itens pendentes |
| `service/email/` | Preparação de anexos, conferências, tentativas e transportes de e-mail |
| `seguranca/` | Operadores, autorização, sessão, auditoria e funções administrativas |
| `manutencao/` e `config/` | Ferramentas de manutenção e verificações de configuração, banco e armazenamento |

### Frontend: onde fica cada parte da interface

Todos os arquivos abaixo ficam em `src/main/resources/static/`.

| Arquivo | Responsabilidade |
| --- | --- |
| `index.html` | Estrutura das oito telas, formulários, tabelas e diálogos |
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
| `painel-diagnostico.js`, `painel-recuperacao.js` e `painel-diagnostico.css` | Cartões acessíveis, lista de problemas, suporte e ações reais de recuperação |
| `painel-emergencia.js` | Tratamento independente de erros globais de JavaScript, com mensagem segura e referência local |
| `painel.css`, `painel-operacao.css` e `painel-visual.css` | Estilos-base, componentes operacionais, identidade visual e responsividade |
| `rota-visual.svg` | Ilustração local utilizada na identidade visual |

O arquivo `painel-admin.js` permanece no repositório como módulo anterior, mas não
é importado pela inicialização atual do painel.

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

O menu abre **oito telas separadas**, preservando a identidade verde/preto e verde-lima:

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

Os aliases `/#documentos`, `/#pacotes-importados` e `/#ultimas-viagens` continuam funcionando.

Recursos de interface:

- Tema escuro inicial, tema claro opcional e preferência salva.
- Sidebar fixa/recolhível no desktop e drawer com controle de foco no celular.
- Abertura animada com mínimo de **cinco segundos**, aguardando os dados necessários.
- Botões para pausar animações ou pular somente o tempo de apresentação.
- Respeito a `prefers-reduced-motion`, navegação por teclado e foco visível.
- Central de atalhos com **Ctrl+K / Cmd+K**.
- Drag-and-drop, nome/tamanho do upload e feedback da operação.
- Cards clicáveis e estados de carregamento, erro e ausência de resultados.
- Filtros e seleções preservados entre telas; mudanças de dados invalidam as prévias necessárias.

Viagens e clientes utilizam os arrays completos dos endpoints de metadados para
filtro e ordenação no navegador, com páginas de **20 registros**. A lista de ZIPs
usa paginação de servidor. As últimas viagens são ordenadas pela **data da viagem**,
não por uma data de processamento presumida.

## Como utilizar o sistema

Este guia descreve o uso pelo painel web. Para preparar o ambiente e iniciar o
servidor, siga primeiro [Como executar localmente](#como-executar-localmente).

### 1. Acessar o painel

1. Com a aplicação em execução local, abra **http://localhost:8080** no navegador.
   Em uma implantação configurada, utilize o endereço fornecido pelo responsável.
2. Aguarde o carregamento da sessão e dos dados. **Pular abertura** reduz apenas
   o tempo da apresentação, sem interromper as consultas necessárias.
3. Na **Visão geral**, consulte os indicadores, as últimas viagens e os atalhos.
4. Use o menu lateral para trocar de tela. No celular, abra o botão de menu.
   A central de atalhos também pode ser aberta com **Ctrl+K / Cmd+K**.

No ambiente de desenvolvimento isolado, o acesso é local e não exige login.
No perfil de operação, as ações dependem do usuário autenticado e de suas
permissões. A pendência atual de acesso pelo formulário `/login` está descrita em
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
`src/main/resources/email/cte-sintetico.xml`, que não possui valor fiscal.

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
por página na interface. O menu distingue falhas, atenção/incerteza e andamento.

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

Se os documentos foram importados e a pasta falhar, o resultado é
**“Documentos importados — exportação pendente”**. Corrija o destino e retome apenas
a cópia. Se a resposta HTTP demorar, o sistema informa resultado não confirmado,
sem presumir falha ou perda de documentos. Não há porcentagem fictícia por arquivo.

Para experimentar com segurança, monte um ZIP sintético com o CT-e de demonstração
e um texto chamado `cobrancas/fatura.pdf`. A conferência identifica o erro em vermelho
e bloqueia a importação. Use **Selecionar ZIP corrigido**, forneça um PDF sintético
válido, confira, associe e confirme. Consulte a evidência em **Resolvidos**.

Contrato, localização, recuperação de pasta, testes e limitações em
[Diagnóstico e Recuperação — V10](docs/diagnostico-recuperacao.md).

## Banco de dados e armazenamento

### Esquema exigido pelo código atual: V10

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

A aplicação valida o esquema com `spring.jpa.hibernate.ddl-auto=validate` e exige
histórico Flyway até V10. As exclusões de ZIP reutilizam colunas existentes de
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

**Pendência conhecida:** o teste de navegador do fluxo protegido encontrou **404 em
`GET /login`**, sem formulário. Autenticação/permissões da API possuem testes, mas
o acesso autenticado completo pela interface depende de resolver essa entrega da
tela de login. Veja [o registro da modernização](docs/modernizacao-frontend.md).

## Como executar localmente

### Requisitos

1. JDK **25** disponível no terminal.
2. Maven compatível, utilizando esse JDK.
3. Porta HTTP livre e diretório separado para banco/acervo de desenvolvimento.

Confira `java -version` e `mvn -version`. A aplicação usa a porta 8080 por padrão.
Os comandos a seguir compilam fora do repositório e iniciam uma base nova isolada.

### Windows / PowerShell

```powershell
# Compilar a aplicação, preservando o target/ histórico do projeto.
$Build = Join-Path $env:TEMP 'opencode/rotabrasil-build'
mvn -B "-Drotabrasil.build.directory=$Build" -DskipTests clean package

# Criar uma área nova para esta demonstração.
$Demo = Join-Path $env:TEMP ('opencode/rotabrasil-demo-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $Demo -Force | Out-Null
$Db = (Join-Path $Demo 'banco') -replace '\\', '/'
$Files = Join-Path $Demo 'viagens'

$RunArgs = @(
  '-jar', "$Build/rota-brasil-0.0.1-SNAPSHOT.jar",
  '--spring.profiles.active=desenvolvimento',
  '--server.address=127.0.0.1', '--server.port=8080',
  "--spring.datasource.url=jdbc:h2:file:$Db;WRITE_DELAY=0",
  '--spring.flyway.enabled=true', '--spring.h2.console.enabled=false',
  "--rotabrasil.arquivos.viagens=$Files",
  '--rotabrasil.email.modo=SIMULADO'
)
java @RunArgs
```

### Linux / Bash

```bash
BUILD_DIR="${TMPDIR:-/tmp}/rotabrasil-build"
mvn -B "-Drotabrasil.build.directory=$BUILD_DIR" -DskipTests clean package
DEMO_DIR="$(mktemp -d "${TMPDIR:-/tmp}/rotabrasil-demo.XXXXXX")"

java -jar "$BUILD_DIR/rota-brasil-0.0.1-SNAPSHOT.jar" \
  --spring.profiles.active=desenvolvimento \
  --server.address=127.0.0.1 --server.port=8080 \
  "--spring.datasource.url=jdbc:h2:file:$DEMO_DIR/banco;WRITE_DELAY=0" \
  --spring.flyway.enabled=true --spring.h2.console.enabled=false \
  "--rotabrasil.arquivos.viagens=$DEMO_DIR/viagens" \
  --rotabrasil.email.modo=SIMULADO
```

Abra **[http://localhost:8080](http://localhost:8080)**. A migração automática habilitada
nesses exemplos destina-se apenas à base nova criada para a demonstração.
Os comandos de empacotamento usam `-DskipTests`; as verificações estão na seção de testes.

### Usar, reiniciar e limpar o ambiente local

- Para testar XML, use `src/main/resources/email/cte-sintetico.xml`, sem valor fiscal.
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

As configurações-base ficam em `src/main/resources/application.properties`, com
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
| `rotabrasil.email.limite-arquivo-bytes` / `.limite-total-bytes` | Limites dos anexos: 20 MiB individual e 100 MiB somados |
| `rotabrasil.seguranca.ativa` | Ativa o controle de acesso; exigido no perfil operacional e no SMTP real |

A persistência usa `spring.jpa.open-in-view=false`: os DTOs são montados na camada
de serviço. A configuração JSON recusa propriedades desconhecidas e números
fracionários em campos inteiros, para manter os contratos de entrada explícitos.

## Configurações da operação, notificações e exportação

### Como usar a nova aba

1. Prepare um banco **V10** e abra **Configurações** (`/#configuracoes`). A migração
   do banco em uso não foi executada nesta entrega e exige autorização específica.
2. Informe o **remetente da operação**, correspondente à conta Outlook que recebe
   os ZIPs. Salvar o endereço não configura senha nem autenticação SMTP.
3. Selecione uma subpasta da raiz autorizada do backend ou use **Criar subpasta**.
4. Escolha se deseja **Copiar automaticamente após nova importação concluída** e
   clique em **Salvar configurações**. O sucesso só aparece após resposta do servidor.
5. Use **Testar acesso e gravação** e **Testar em modo simulado**. O destinatário do
   teste é separado e vale apenas para a simulação. Os resultados ficam persistidos.

Alterações não salvas são identificadas e desabilitam os testes até serem salvas.
Com segurança ativa, a configuração exige ADMIN e CSRF; em desenvolvimento isolado
o acesso continua local, sem novo login ou gestão de usuários.

### Endereços e eventos separados

- **Remetente da operação:** conta Outlook configurada na aba.
- **Destinatário da confirmação:** remetente original da mensagem, informado pelo
  operador em **Importar ZIP → Quem receberá a confirmação deste upload?**.
- **Destinatário dos documentos do cliente:** permanece no fluxo da Central de
  envios, sem substituição pelo remetente ou pelo destinatário da confirmação.

Não há destinatário interno fixo. O ZIP sozinho não informa de quem veio o e-mail.
O recebimento automático pelo Outlook permanece uma integração futura.

O painel permite autorizar a **simulação dos avisos desta operação** ou simular
individualmente um aviso pendente. **Ver avisos**, no pacote importado, consulta
o histórico. Nenhuma dessas ações envia e-mail real, mesmo quando o transporte
documental está configurado como REAL. Credenciais continuam externas, sem campo
de senha e sem armazenamento de senha pela interface.

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

O responsável deve criar uma pasta separada do acervo interno e configurar:

```powershell
$env:ROTABRASIL_EXPORTACAO_RAIZ = 'C:/RotaBrasil/Exportacoes'
```

Ou fornecer `--rotabrasil.exportacao.raiz=/pasta/autorizada` na inicialização.
A raiz precisa existir e permitir gravação/publicação por hard link. O teste da
aba verifica essas capacidades; não há promessa de acesso a pastas de rede.

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

## Envio de e-mail

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

# Fluxo ampliado; sua etapa autenticada possui a pendência /login documentada:
node scripts/testar-envio-manual.cjs "$Build/rota-brasil-0.0.1-SNAPSHOT.jar" "$Ui/envio-manual"
```

As ferramentas iniciam seus próprios servidores e bases. As saídas devem ficar fora
do repositório. Em Linux, a instalação do Chromium pode exigir `--with-deps`, como
no workflow. Os scripts Python `testar-painel.py` e `testar-pacotes.py` conservam
verificações de etapas anteriores; a navegação atual é exercitada pelos scripts Node.

### Últimos resultados registrados

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
| Suíte JUnit completa da revisão atual | Não executada nesta entrega; os resultados V9 são de recortes direcionados |

Esses números são de recortes e execuções registradas, não o total de testes do
repositório. Resultados históricos de outras versões permanecem em `docs/` e não
devem ser somados como se fossem uma única execução. O workflow existente também
precisa da resolução do login para concluir seu fluxo autenticado de navegador.

## Limitações e próximos passos

1. **Login no navegador:** corrigir a entrega do formulário `/login` no fluxo protegido.
2. **SMTP real:** configurar e homologar remetente, credenciais e conectividade no ambiente de uso.
3. **Documentos recentes globais:** dependem de consulta unificada com tipo, viagem, data de processamento e estado; esses metadados não estão completos nos DTOs atuais.
4. **Escala das consultas:** filtros/ordenação globais de viagens/clientes com paginação de servidor exigem ampliar os contratos atuais para grandes bases.
5. **Integrações de entrada:** Outlook/caixa de entrada e recebimento automático de ZIP permanecem pendentes.

As decisões atuais de destinatário, vínculos, confirmação e reenvio são manuais.
Não há inferência de destinatários, repetição automática de envio ou promessa de
entrega/leitura de e-mail. PDFs e XMLs continuam sujeitos aos limites de conferência
descritos neste README e nos contratos específicos.

## Documentação complementar

| Documento | Assunto |
| --- | --- |
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
