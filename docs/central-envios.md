# Central de envios — correção e validação de 13/11/2026

**Continuação:** identificada a instância antiga na porta 8080. Consulte
[o diagnóstico do JAR efetivamente em execução, a Central v2 e os passos exatos de início](iniciar-central-corrigida-gmail.md).
Os resultados abaixo preservam a primeira correção e sua validação.

## Causa comprovada e limites do diagnóstico

Recompilado o código local preservando a reorganização em `front/` e `back/`.
Executado o JAR exclusivamente em loopback, com H2 em diretório temporário,
migrações da base sintética até V15 e XML explicitamente sintético versionado.
Não foi consultado processo, configuração externa, documento ou banco operacional.

Reprodução anterior à correção (`scripts/testar-central-envios.cjs ... --antes`):

- Viagem e documento selecionados em Viagens permanecem selecionados em Envios.
- `/api/sessao` informa `modoEmail=SIMULADO`. O frontend usa essa única informação
  como `state.envioReal` para desabilitar **tanto destinatário quanto Gerar prévia**.
- `destinatario@example.invalid` é **placeholder**, não `value`: o campo está vazio.
  Não há endereço fictício preenchido como escolha do usuário.
- O mesmo backend responde **200** a `POST /api/viagens/{id}/conferencias-email`
  com seleção e destinatário sintéticos válidos, persiste a conferência e não envia.
  Esse endpoint chama a preparação com leitura segura e SHA-256, sem depender do SMTP.
- O endpoint legado `previa-email` fornece metadados e **não foi usado como substituto**.
- Não houve erro JavaScript na reprodução. O bloqueio ocorre no JAR recém-compilado:
  desatualização de artefato não é necessária para explicar o defeito reproduzido.
  Não se conclui qual JAR está rodando na instalação originalmente observada.

Logo, há um **bug de acoplamento entre preparação e modo de envio na interface**.
O aviso genérico ainda esconde a diferença entre modo, remetente, permissão e falha
de consulta. A ausência de SMTP no ambiente sintético é configuração deliberadamente
ausente, não defeito que deva ser corrigido ativando REAL ou simulando silenciosamente.
Nada nesta investigação comprova a configuração SMTP da instalação do usuário.

## Contrato atual

1. Selecionar viagem e documentos ativos. Nomes e contexto aparecem na Central.
2. Informar destinatário explicitamente: vazio/inválido bloqueia Gerar prévia com razão
   próxima ao botão; o exemplo continua somente no placeholder.
3. Gerar **conferência persistida**. Validação de vínculo, elegibilidade, disponibilidade,
   limites e SHA-256 continua no backend; erro aparece junto da ação, com recuperação.
4. Revisar remetente SMTP efetivo (inclusive sua ausência), destinatário, assunto,
   mensagem e arquivos. Preparação SIMULADO é identificada como **PREPARAÇÃO SEM ENVIO**:
   não executa transporte nem cria uma tentativa simulada automaticamente.
5. Confirmar somente com modo REAL, prontidão documental consultada, permissão e
   checkbox explícito. Se o modo mudar, exige nova conferência. Sem configuração,
   a conferência funciona e a confirmação real continua bloqueada com a causa.

`GET /api/envio-documental` é consulta autenticada disponível aos três papéis, sem
segredos, caminhos privados ou conexão SMTP. Informa modo documental, prontidão local
da confirmação e bloqueios comprovados. Não usa `ConfiguracaoNotificacoes` (Microsoft)
nem `ContaTesteEmailService` (Gmail administrativo). A criação de SMTP REAL já exige
configuração externa completa na inicialização; em execução a consulta também verifica
remetente salvo/SMTP, opt-in, segurança e disponibilidade local do token quando XOAUTH2.
Prontidão local **não comprova autenticação, aceite, recebimento ou leitura**.

Falha de consulta torna a prontidão desconhecida, não ausente: oferece tentar novamente,
mantém a preparação independente e bloqueia a confirmação. Respostas antigas da consulta
e de conferências são descartadas por geração. Trocar documentos ou destinatário invalida
a conferência exibida; trocar documentos não apaga o destinatário.

ADMIN recebe link de Configurações e orientação sobre provisionamento externo.
OPERADOR/CONSULTA recebem orientação para procurar ADMIN, sem ação administrativa proibida.
CONSULTA não cria conferência. Expiração de sessão invalida a conferência em tela e mantém
o aviso de recuperação do acesso. As regras de autorização e CSRF não foram relaxadas.

No backend, uma nova confirmação REAL revalida a prontidão **antes de criar tentativa**.
Recuperar a mesma confirmação já registrada continua possível após perda da prontidão:
retorna o resultado persistido, sem transportar novamente. Preservados actor/modo,
expiração, retrato dos dados, confirmação explícita, reserva transacional antes do
transporte, concorrência, idempotência e vínculo com última tentativa para reenvio.
`SIMULADO`, `ACEITO_SMTP`, `FALHA` e `DESCONHECIDO` continuam distintos no histórico.
Resultado desconhecido não dispara repetição automática.

## Arquivos desta correção

- `front/index.html`: diagnóstico, recuperação e razão próxima da ação.
- `front/painel-documentos.js`: preparação independente do modo, validação por input,
  contexto/nome dos selecionados, invalidação em expiração de sessão.
- `front/painel-email.js`: consulta documental, controle de respostas obsoletas,
  conferência sem envio e confirmação condicionada à prontidão conhecida.
- `front/painel-base.js`: elimina inferência de configuração ausente pelo modo da sessão.
- `front/painel-ajuda-conteudo.js`: ajuda contextual alinhada ao fluxo atual.
- `back/src/main/java/br/com/rotabrasil/service/email/ProntidaoEnvioDocumental.java`:
  diagnóstico local e guarda reutilizada na confirmação real.
- `back/src/main/java/br/com/rotabrasil/controller/EnvioDocumentalController.java`:
  contrato de consulta sem privilégios de administração.
- `back/src/main/java/br/com/rotabrasil/service/email/EnvioManualService.java`:
  guarda de prontidão, preservando recuperação idempotente antes dela.
- `back/src/test/java/br/com/rotabrasil/{EnvioManualHttpTest,CentralEnviosRealHttpTest,MelhoriasHttpTest}.java`:
  regressões, integração real com double e adaptação do construtor no teste existente.
- `scripts/testar-central-envios{,-real}.cjs`: reprodução e navegação automatizadas.
- Este documento, README e atualização do contrato Gmail.

Nenhuma migração, esquema publicado ou transporte foi substituído em produção.

## Validação executada

Java 25, Maven 3.10.0, Playwright/Chrome headless. Todos os dados são sintéticos.
Artefatos externos em `C:/Users/florb/AppData/Local/Temp/opencode/`.

```powershell
$env:NODE_PATH = 'C:/Users/florb/AppData/Local/Temp/opencode/node_modules'
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-central-build -DargLine=-Xmx768m -Drotabrasil.browser=true -Drotabrasil.browser.output=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-central-real-double clean test
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-central-jar -DskipTests package
node scripts/testar-central-envios.cjs C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-central-jar/rota-brasil-0.0.1-SNAPSHOT.jar C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-central-depois
```

- Execução final integrada: **413 JUnit, 412 aprovados, zero falhas/erros, 1 ignorado**
  (symlink Windows). O teste de navegador protegido é opt-in; sem `rotabrasil.browser=true`
  ele é ignorado para não exigir Node/Chrome na suíte Java usual.
- Conferência/confirmação com double: tentativa já persistida antes do transporte,
  fora de transação; aceite não equivale a entrega. Testes existentes verificam
  falha, desconhecido, integridade adulterada, arquivamento, desaparecimento,
  expiração, concorrência e múltiplas conferências contra reenvio acidental.
- Regressões novas: prévia sem SMTP/remetente, persistência sem tentativa,
  destinatário vazio/inválido, ausência de arquivos, divergência de remetente,
  guarda no backend, recuperação mesmo após perda de prontidão, sessão/papéis/CSRF
  e independência do canal Microsoft não identificado.
- Navegador com JAR: seleção Viagens → Envios, input vazio/inválido/válido,
  nomes selecionados, invalidação por edição, preservação de destinatário,
  falha 503 na consulta e recuperação, resposta de conferência atrasada descartada,
  temas claro/escuro, Tab, desktop 1440×1000 e celular 390×844 sem overflow.
  Compara os módulos efetivamente servidos com as fontes atuais de `front/`.
- Navegador protegido: login real ADMIN/OPERADOR/CONSULTA, CSRF, divergência e correção,
  checkbox explícito, clique duplo, resposta perdida **depois** do processamento,
  recuperação idempotente (uma chamada ao double), reenvio vinculado, desconhecido
  sem repetição, logout/expiração e orientação por papel. Duas chamadas ao double
  e duas tentativas; **zero chamadas ao JavaMailSender**.
- Os dois roteiros de navegador terminaram sem erros JavaScript.
- Após o ajuste final para manter o contexto da viagem mesmo sem documentos
  selecionados, reempacotamento e roteiro do JAR aprovados; os **4 testes** de
  `CentralEnviosRealHttpTest` (incluindo navegador protegido) foram reexecutados
  e aprovados, sem falhas/erros/ignorados.

Uma execução intermediária detectou erro no roteiro ao aguardar `/login` sem aceitar
`?logout`. O seletor de URL foi corrigido e a suíte integrada foi reexecutada com sucesso.
Logs finais: `rotabrasil-central-test-final.log`, `rotabrasil-central-package.log`,
JSONs dos dois roteiros e `rotabrasil-central-real-double/playwright.log`.

### Capturas externas

| Evidência | Caminho relativo ao diretório temporário acima |
|---|---|
| Defeito reproduzido | `rotabrasil-central-antes/antes-bloqueio.png` |
| Preparação sem SMTP | `rotabrasil-central-depois/preparacao-desktop.png` |
| Conferência persistida sem envio | `rotabrasil-central-depois/conferencia-desktop.png` |
| Conferência no celular | `rotabrasil-central-depois/conferencia-mobile.png` |
| Tema escuro | `rotabrasil-central-depois/conferencia-escuro.png` |
| Falha de consulta | `rotabrasil-central-depois/falha-configuracao.png` |
| Destinatário vazio no celular | `rotabrasil-central-depois/bloqueio-mobile.png` |
| Divergência / ADMIN | `rotabrasil-central-real-double/remetente-divergente-admin.png` |
| Divergência / OPERADOR | `rotabrasil-central-real-double/remetente-divergente-operador.png` |
| Conferência REAL com double | `rotabrasil-central-real-double/conferencia-real-double-desktop.png` |
| Conferência com double no celular | `rotabrasil-central-real-double/conferencia-real-double-mobile.png` |
| Resposta recuperada / double | `rotabrasil-central-real-double/aceite-double-recuperado.png` |
| Resultado desconhecido / double | `rotabrasil-central-real-double/resultado-desconhecido-mobile.png` |
| Sessão expirada | `rotabrasil-central-real-double/sessao-expirada.png` |
| Permissão CONSULTA | `rotabrasil-central-real-double/permissao-consulta.png` |

Capturas que mostram REAL/ACEITO_SMTP neste teste representam **apenas o double**,
nunca aceite do Gmail. Não foram adicionados artefatos gerados ao repositório.

## Pendências externas para Gmail documental

O adaptador SMTP existente permanece o transporte documental. Em futura tarefa
autorizada, o responsável deverá provisionar externamente o modo e opt-in documentais,
`smtp.gmail.com:587`, usuário autorizado, remetente SMTP correspondente ao salvo em
Configurações e autenticação Google permitida (senha de aplicativo no mecanismo
existente BASIC, nunca senha normal; ou XOAUTH2 com token externo válido).
STARTTLS, verificação do servidor e timeouts do adaptador permanecem obrigatórios.
O processo precisa de sessão protegida/CSRF e armazenamento durável conforme o contrato.

As propriedades documentais são `rotabrasil.email.modo` e `rotabrasil.email.smtp.*`.
Salvar endereço e configurar `rotabrasil.teste-email.gmail.*` **não autentica nem
ativa o transporte documental**. Os requisitos Microsoft da confirmação opcional
ao motorista não se aplicam ao envio documental Gmail. Não é necessário preencher
tipo de conta Microsoft para preparar/enviar documentos nesse canal independente.

Não foram inspecionadas credenciais existentes; portanto não se afirma quais itens
estão ausentes na instalação. Homologação Gmail, autenticação e recebimento efetivo
continuam pendentes de tarefa futura autorizada. Nenhum e-mail externo foi enviado,
nenhum banco operacional foi acessado, nenhum serviço foi exposto ou implantado.
