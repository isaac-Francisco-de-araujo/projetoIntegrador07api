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
sem conexão SMTP. O fluxo cotidiano de documentos mostra **Envio não configurado**
até haver transporte documental REAL. Selecionar Gmail de teste não ativa esse
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
| `rotabrasil.teste-email.modo` | `SIMULADO` por padrão; `REAL` somente em preparação autorizada |
| `rotabrasil.teste-email.gmail.ativado` | `false` por padrão; opt-in externo independente |

Alternativas de ambiente já suportadas: `ROTABRASIL_GMAIL_REMETENTE` e
`ROTABRASIL_GMAIL_APP_PASSWORD`. Não confundir essas variáveis com autorização de
envio. A conta externa deve corresponder à conta salva. Alterar usuário/credencial
de ambiente exige reinício do processo; arquivo OAuth2 é relido por uso.

### Autenticação Google

Documentação oficial consultada em 09/11/2026:

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

Mudança de conta/configuração ou conferência expirada exige nova conferência.
A última verificação pertence à assinatura/versão da configuração atual; registros
de outras contas continuam no histórico, sem serem promovidos a autenticação atual.
Não há repetição automática, leitura de caixa postal ou envio de documentos reais.

## API e compatibilidade

- `GET /api/configuracoes/conta-teste`: preferências públicas e pré-requisitos seguros.
- `PUT /api/configuracoes/conta-teste`: `{versao,provedor,remetente,autenticacao}`;
  provedores `GMAIL|MICROSOFT_EXISTENTE`, autenticação `APP_PASSWORD|XOAUTH2`.
- `GET /api/configuracoes/conta-teste/ultima-verificacao`: resultado correspondente
  à conta/configuração atual ou `NAO_VERIFICADO`, nunca uma credencial.
- `POST /api/configuracoes/teste-email`: teste sem envio com UUID idempotente.
- Conferência/consulta/confirmação REAL reutilizam as rotas
  `conferencias-email-real` e `confirmacoes-email-real` de Configurações.

Esquema V15, contrato `equipe-V15-20261109`; migração V15 preexistente preservada.
A tabela V9 `testes_configuracao` guarda o retrato em `configuracao`, não em
`snapshot`. Microsoft continua usando sua assinatura e requisitos próprios.

O [teste Gmail por CLI](teste-gmail.md) é histórico e distinto: tem um anexo XML
sintético fixo. Sua autorização histórica não autoriza envio nesta tarefa nem
substitui o teste sem anexos do painel ou a homologação documental ao cliente.
