# Conta Gmail configurável para testes — V15

## Escopo e uso

Em **Configurações → Conta para testes de e-mail**, ADMIN escolhe Gmail e salva
o endereço autorizado e a autenticação. Não existe endereço fixo nem cadastro
público. A API exige ADMIN, sessão e CSRF em operação, com versão concorrente.
O banco guarda somente essas preferências, nunca senha, token ou refresh token.

O formulário distingue provedor, remetente, autenticação não verificada e modo.
Nas ferramentas administrativas ficam destinatário explícito, prévia e resultado.
Salvar endereço **não autentica**. Falha de consulta mantém **Recarregar conta**
disponível; conflito preserva o rascunho e exige recarga deliberada.

O padrão `rotabrasil.teste-email.modo=SIMULADO` registra **Teste sem envio**,
sem conexão SMTP. A Central de envios permite **preparar conferência sem envio**
sem transporte documental REAL e mostra os bloqueios específicos da confirmação.
Veja [o contrato atual da Central](central-envios.md). Selecionar Gmail de teste não ativa esse
transporte nem altera a confirmação opcional ao motorista ou o canal Microsoft.

## Configuração externa segura

O administrador da máquina central provisiona os valores em ambiente protegido
da conta de serviço ou arquivo de configuração externo com acesso restrito.
Não informar segredos no chat, argumentos de processo, URLs, scripts versionados
ou logs. O painel não recebe nem devolve credenciais.

| Propriedade | Finalidade |
|---|---|
| `rotabrasil.teste-email.gmail.usuario` | Mesmo endereço autorizado salvo no formulário |
| `rotabrasil.teste-email.gmail.senha-aplicativo` | Apenas senha de aplicativo, quando esse mecanismo foi escolhido |
| `rotabrasil.teste-email.gmail.token-arquivo` | Arquivo externo do token Google, quando escolhido XOAUTH2 |
| `rotabrasil.teste-email.gmail.credencial-versao` | Identificador não secreto obrigatório para prontidão REAL; mudar ao substituir credencial, conta autorizada ou consentimento |
| `rotabrasil.teste-email.modo` | `SIMULADO` por padrão; `REAL` somente em preparação autorizada |
| `rotabrasil.teste-email.gmail.ativado` | `false` por padrão; opt-in externo independente |

Alternativas de ambiente já suportadas: `ROTABRASIL_GMAIL_REMETENTE` e
`ROTABRASIL_GMAIL_APP_PASSWORD`. Não confundir essas variáveis com autorização de
envio. A conta externa deve corresponder à conta salva. Alterar usuário/credencial
de ambiente exige reinício do processo; arquivo OAuth2 é relido por uso.

Gmail é o **provedor**, não uma restrição de domínio: Google Workspace com domínio
próprio é aceito, desde que a conta esteja autorizada no Google e corresponda ao
usuário externo. A senha/token deve estar disponível **ao processo Java** pelo
contrato acima; um segredo substituído somente em requisições por proxy HTTPS não
é uma credencial SMTP acessível ao Java.

A versão não secreta integra a assinatura da conferência e da última verificação.
Não contém senha, token ou seu hash. A renovação rotineira de access token da mesma
autorização não exige nova versão; substituição de credencial/identidade/consentimento
exige alterá-la e reiniciar a configuração externa. Uma conferência anterior então
é recusada e um resultado antigo não é atribuído à nova configuração.

### Autenticação Google

Documentação oficial consultada em 09/11/2026 e reconferida em 11/11/2026:

- [Senhas de aplicativo](https://support.google.com/accounts/answer/185833?hl=pt-BR):
  exigem verificação em duas etapas, podem estar indisponíveis por políticas da
  organização, Proteção Avançada ou configuração da conta. Google recomenda OAuth
  quando possível. Nunca usar a senha normal nem desativar proteções da conta.
- [SMTP XOAUTH2](https://developers.google.com/workspace/gmail/imap/xoauth2-protocol):
  token OAuth2 para SMTP usa o escopo `https://mail.google.com/`; consentimento,
  políticas e eventual verificação do aplicativo precisam ser avaliados pelo
  administrador. Escopo `gmail.send` da Gmail API não substitui esse contrato SMTP.

OAuth2 reutiliza `TokenSmtpExterno`: JSON externo limitado a 32 KiB com
`access_token` e `expires_at` em ISO-8601. Token ausente, inválido ou com menos
de 60 segundos de validade bloqueia a tentativa, sem fallback para senha.
Aquisição, consentimento e renovação são externos; não há botão de login Google
nem renovador implementado no RotaBrasil. Publicar o arquivo atomicamente, com
ACL da conta de serviço, fora do checkout, acervo e backup documental.

O contrato também aceita `renewal_managed` e `authorization_state`, publicados pelo
utilitário externo. Estados `AUTORIZACAO_PENDENTE`, `RECONEXAO_NECESSARIA` e
`INDISPONIVEL` bloqueiam transporte mesmo se o arquivo conservar um token.
`DISPONIVEL` comprova somente formato/validade local declarada, nunca escopo ou
aceite do Google. A API retorna diagnóstico, renovação e expiração, sem token/path.
Aquisição/renovação e seu agendamento continuam dependências externas explícitas.

Senha de aplicativo deve conter os 16 caracteres sem espaços de apresentação.
Sua presença local não comprova validade no provedor. Se a conta não a permite,
use o processo OAuth2 autorizado; não recorra à senha normal.

O transporte reutilizado é `smtp.gmail.com:587`, STARTTLS obrigatório,
verificação de identidade do servidor, debug desligado e timeouts de 10 segundos.
Rede, DNS, consentimento e permissão do remetente precisam funcionar externamente.

## Teste externo específico (não executado nesta revisão)

1. Preparar ambiente **operacao**, HTTPS, sessão ADMIN e CSRF, com base isolada V15,
   Flyway desligado, Hibernate validate e H2 em arquivo com `WRITE_DELAY=0`.
2. Provisionar a credencial externamente e salvar Gmail/endereço/autenticação.
   Opt-in e modo REAL do teste são independentes do modo documental/motorista.
3. Informar o destinatário controlado nas ferramentas de teste e preparar a
   conferência. Conferir remetente, destinatário, assunto e texto sintético visíveis.
   Não há anexos fiscais nesse endpoint.
4. Confirmar **essa mensagem específica**. Prévia não chama SMTP; a tentativa
   fica persistida antes do transporte, que roda sem transação de importação.
5. Consultar resultado: `ACEITO_SMTP` é aceite, não entrega/leitura;
   `FALHA` é recusa conhecida; `DESCONHECIDO` exige consulta humana. Repetir a
   mesma confirmação recupera a tentativa e não transporta novamente.
6. Para uma nova tentativa, consultar o resultado anterior e aceitar deliberadamente
   preparar outro teste vinculado. A API exige `anteriorId` da última tentativa
   administrativa, inclusive após resultado desconhecido. A nova prévia exige outra
   confirmação; uma conferência concorrente antiga não dispara uma segunda mensagem.

Mudança de conta/configuração ou conferência expirada exige nova conferência.
A última verificação pertence à assinatura/versão da configuração atual; registros
de outras contas continuam no histórico, sem serem promovidos a autenticação atual.
Não há repetição automática, leitura de caixa postal ou envio de documentos reais.

A interface identifica autenticação aceita **naquela verificação** somente após
`ACEITO_SMTP` da configuração correspondente. Isso não afirma validade futura da
credencial nem recebimento. Resultados sem assinatura compatível não comprovam a
configuração atual; permanecem no histórico.

## API e compatibilidade

- `GET /api/configuracoes/conta-teste`: preferências públicas e pré-requisitos seguros.
- `PUT /api/configuracoes/conta-teste`: `{versao,provedor,remetente,autenticacao}`;
  provedores `GMAIL|MICROSOFT_EXISTENTE`, autenticação `APP_PASSWORD|XOAUTH2`.
- `GET /api/configuracoes/conta-teste/ultima-verificacao`: resultado correspondente
  à conta/configuração atual ou `NAO_VERIFICADO`, nunca uma credencial.
- `POST /api/configuracoes/teste-email`: teste sem envio com UUID idempotente.
- Conferência/consulta/confirmação REAL reutilizam as rotas
   `conferencias-email-real` e `confirmacoes-email-real` de Configurações.
- Em nova conferência administrativa, `anteriorId` é nulo somente sem tentativas
  anteriores; caso contrário deve apontar à última `EMAIL_REAL`. O vínculo fica
  em `snapshot.testeAnteriorId`, no JSON existente. A FK `anterior_id` das notificações
  ao motorista não é reutilizada para outra tabela. Esquema continua V15.
- O histórico expõe os 20 testes recentes **e** a última tentativa `EMAIL_REAL`,
  quando mais antiga, para não perder o vínculo após muitos testes de pasta.

Esquema V15, contrato `equipe-V15-20261109`; migração V15 preexistente preservada.
A tabela V9 `testes_configuracao` guarda o retrato em `configuracao`, não em
`snapshot`. Microsoft continua usando sua assinatura e requisitos próprios.

O [teste Gmail por CLI](teste-gmail.md) é histórico e distinto: tem um anexo XML
sintético fixo. Sua autorização histórica não autoriza envio nesta tarefa nem
substitui o teste sem anexos do painel ou a homologação documental ao cliente.
