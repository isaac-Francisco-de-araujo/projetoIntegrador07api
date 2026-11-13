# Etapa E-mail — confirmação manual de recebimento ao motorista (V11)

## Regra comercial atual — 09/11/2026

A conta remetente é o Outlook da empresa. O trabalho principal é enviar os documentos
e a fatura **ao cliente**, com destinatário e anexos explicitamente conferidos. Não há
e-mail de motorista informado. **A confirmação ao motorista é opcional**, não é
requisito comercial nem etapa obrigatória de seleção, conferência ou importação.
Não há disparo real automático. Download no Outlook e upload do ZIP são manuais;
o CT-e de um PDF é identificado abrindo o documento, sem associação inferida.

O código atual exige **V15**, e login/permissões já existem e foram exercitados em
ambiente isolado. O 404 de `/login`, esquema V11 e roteiros de simulação abaixo são
registros da entrega histórica V11, não impedimentos atuais comprovados. Na interface
cotidiana, envio sem transporte real configurado fica indisponível; testes ficam na
área administrativa. Históricos não são apagados nem têm seus estados alterados.

Para o envio documental ao cliente, consulte o [contrato atualizado](envio-manual.md#regra-atual-envio-documental-ao-cliente--09112026)
e a [preparação Microsoft](envio-manual.md#preparação-microsoft-para-homologação-futura).
"Outlook da empresa" ainda não identifica plataforma, tenant, modalidade da caixa
e políticas de SMTP. Não se presume credencial básica nem uma conta Microsoft 365.

**Decisão atual de testes:** Gmail administrativo tem [configuração e autenticação
próprias](gmail-configuravel.md). Não usa OAuth2 Microsoft, não ativa este canal
opcional e não é requisito para importar. [Aceite isolado de 11/11/2026](preparacao-compartilhada-gmail-20261111.md).

## Entrega e decisões

Quando o recurso opcional for utilizado com um endereço disponível e informado
explicitamente, o operador confere remetente, destinatário, assunto, corpo,
recebimento/pacote, modo e situação conhecida antes de confirmar. A confirmação é
independente do envio documental ao cliente e não leva anexos fiscais.

Foi reutilizado `NotificacaoRecebimentoService`, `notificacoes_recebimento`,
`MensagemNotificacao`, `TransporteEmail` e `TransporteSmtp`, além da configuração,
coordenação transacional, diagnóstico e Central existentes. Não há segundo mecanismo
de envio, leitura de caixa, contatos, download automático de ZIP ou login novo.

Registro histórico da execução V11 (31/10/2026):

- O tipo da conta remetente **ainda não foi identificado pelo responsável**.
- Foi escolhida a opção **manter a proteção existente**: REAL exige segurança ativa,
  sessão OPERADOR/ADMIN e CSRF. O teste real de configuração exige ADMIN.
- O padrão continua `SIMULADO`. Nenhum envio externo foi autorizado ou executado.
- O acesso de navegador do ambiente protegido tem o `/login` 404 documentado na
  estabilização. A exigência de autenticação não foi removida para contorná-lo.

O código permite SMTP real com OAuth2 quando os pré-requisitos estiverem resolvidos.
Isso **não é homologação da conta, do provedor, da entrega ou da leitura**.

## Unidade da confirmação e eventos

Um recebimento é reconhecido pelo SHA-256 dos bytes do ZIP. A unidade manual é
**uma mensagem de confirmação desse recebimento para um destinatário explícito**.
Ela não é um envio por XML, PDF, viagem ou atualização interna.

Os eventos V9 continuam preservados como contexto/histórico simulado:
`ARQUIVO_RECEBIDO`, `CONFERENCIA_CONCLUIDA`, `IMPORTACAO_CONCLUIDA`,
`EXPORTACAO_CONCLUIDA` e `PROCESSAMENTO_COM_PENDENCIAS`. Não acionam SMTP real.
O evento `CONFIRMACAO_MOTORISTA` identifica as tentativas manuais novas no mesmo histórico.

A seleção inicial do upload continua alimentando os avisos internos. Na seção de
confirmação é possível corrigir o destinatário antes de enviar. Preparar uma nova
conferência com outro endereço altera a seleção atual do recebimento e incrementa
sua revisão; não reescreve eventos/tentativas já registrados. Após uma tentativa,
uma nova mensagem exige reenvio deliberado vinculado à última tentativa.

## Conteúdo e conferência

A conferência é persistida por 30 minutos. O snapshot guarda remetente, destinatário,
assunto, texto, modo, versão da configuração, identificação do recebimento/pacote,
situação conhecida, revisão da seleção e impressão do contexto/transporte sem segredos.
Não aceita assunto, corpo, servidor ou credencial escolhidos pelo navegador.

O texto é produzido a partir de fatos persistidos:

| Fato conhecido | Comunicação |
| --- | --- |
| Bytes recebidos | Recebimento confirmado; conferência/processamento ainda não concluídos |
| Conferência concluída | Conferência terminou; importação depende da confirmação da operação |
| Processamento com pendências | Há pendências em conferência pela operação |
| Pacote importado | Importação no sistema concluída, sem afirmar entrega de e-mail |
| Importação concluída e exportação pendente | Importação concluída; operação acompanha pendências internas, sem expor pasta ou detalhes de disco ao motorista |

Exemplo para arquivo inválido/pendência:

> Confirmamos o recebimento do pacote documentos.zip.<br>
> O processamento identificou pendências que estão em conferência pela operação.

Nome de pacote é limitado e tratado como texto, removendo componentes de caminho e
controles. Não são incluídos XML/PDF, dados bancários, conteúdo fiscal completo,
paths físicos, resposta privada SMTP ou detalhes técnicos internos. O corpo exibido
é o corpo confirmado; alterações posteriores não reconstroem mensagens históricas.

Alterar destinatário na interface limpa a prévia. Preparar outro destinatário,
salvar configuração, mudar modo/contexto do processamento ou expirar a conferência
impede usar uma conferência antiga para iniciar transporte. O operador também precisa
ser o mesmo da conferência. Repetir uma conferência que já gerou tentativa consulta
seu resultado original, sem revalidar para executar outra mensagem.

## Autenticação Microsoft e pré-requisitos

Fontes oficiais consultadas em 31/10/2026:

- [Configurações Outlook.com](https://support.microsoft.com/en-us/office/pop-imap-and-smtp-settings-for-outlook-com-d088b986-291d-42b8-9564-9c414e2aa040): SMTP `smtp-mail.outlook.com`, porta 587, STARTTLS e OAuth2/Modern Auth.
- [OAuth para SMTP](https://learn.microsoft.com/en-us/exchange/client-developer/legacy-protocols/how-to-authenticate-an-imap-pop-smtp-application-by-using-oauth): SMTP com SASL XOAUTH2 para Outlook.com e Microsoft 365; escopo delegado de envio `https://outlook.office.com/SMTP.Send`.
- [SMTP AUTH no Exchange Online](https://learn.microsoft.com/en-us/exchange/clients-and-mobile-in-exchange-online/authenticated-client-smtp-submission): a disponibilidade depende das políticas da organização e da caixa. Não se presume SMTP AUTH habilitado nem autenticação básica disponível.

| Conta identificada externamente | `rotabrasil.notificacoes.tipo-conta` | Host SMTP |
| --- | --- | --- |
| Outlook.com / Hotmail / Live pessoal | `OUTLOOK_PESSOAL` | `smtp-mail.outlook.com` |
| Microsoft 365 corporativa | `MICROSOFT_365` | `smtp.office365.com` |
| Ainda não identificada | `NAO_IDENTIFICADA` (padrão) | REAL bloqueado |

Nesta confirmação Microsoft, a autenticação aceita é **XOAUTH2**, sem fallback para
LOGIN/PLAIN. A capacidade BASIC do transporte documental anterior foi preservada
para seus contratos existentes; não é presumida compatível com esta conta Microsoft.

A opção de menor escopo é OAuth2 delegado com **SMTP.Send**, sem IMAP, POP,
`Mail.Read` ou sincronização de contatos. O registro da aplicação e o consentimento
precisam permitir o tipo de conta escolhido. No Microsoft 365, o administrador precisa
verificar as políticas vigentes. Não desative políticas de segurança da organização
como atalho para liberar este sistema. Se SMTP não for permitido, o bloqueio deve ser
resolvido com o responsável antes de decidir outro transporte; não há troca para Graph
ou outro provedor automaticamente.

### Fornecimento seguro e renovação

O token é obtido/renovado por ferramenta de identidade externa aprovada pelo
responsável, usando o fluxo OAuth2 adequado à conta. Não foi implementada nesta
entrega uma tela de autorização Microsoft ou um renovador de refresh token.
Não existe campo de segredo no painel, endpoint de token ou aquisição automática
de credenciais de Outlook, navegador ou sistema operacional.

A ferramenta externa publica um arquivo restrito à conta do processo do backend,
fora do checkout e dos documentos, contendo:

```json
{
  "access_token": "<token OAuth2 fornecido externamente; não copiar para o chat>",
  "expires_at": "<instante UTC ISO-8601 de expiração informado pela ferramenta>"
}
```

Esse exemplo é um contrato, não uma credencial válida. Proteja o arquivo com ACL
ou permissão restrita do sistema operacional; não o versione nem o inclua em logs
ou saídas de suporte. Publique a renovação de forma atômica. O backend lê o token
a cada uso, limita o arquivo a 32 KiB e bloqueia credencial ausente/inválida ou com
menos de 60 segundos de validade declarada. Não exibe conteúdo ou caminho físico
na API. A autenticação real e os escopos efetivos ainda são validados pelo provedor.

### Configuração externa

Prepare um arquivo de propriedades protegido, fora do checkout. Exemplo de
parâmetros **sem segredos**, para conta pessoal já identificada:

```properties
rotabrasil.notificacoes.modo=REAL
rotabrasil.notificacoes.tipo-conta=OUTLOOK_PESSOAL
rotabrasil.email.modo=SIMULADO
rotabrasil.seguranca.ativa=true
rotabrasil.email.smtp.ativado=true
rotabrasil.email.smtp.host=smtp-mail.outlook.com
rotabrasil.email.smtp.porta=587
rotabrasil.email.smtp.usuario=<conta autorizada>
rotabrasil.email.smtp.remetente=<mesma conta remetente salva no painel>
rotabrasil.email.smtp.autenticacao=XOAUTH2
rotabrasil.email.smtp.oauth2.token-arquivo=C:/RotaBrasil/Segredos/smtp-token.json
rotabrasil.email.smtp.timeout-ms=10000
```

Para Microsoft 365, escolha explicitamente `MICROSOFT_365` e `smtp.office365.com`.
Não aplique o exemplo pessoal a uma conta corporativa por suposição. A configuração
de operação já existente continua exigindo HTTPS/cookie seguro, H2 Console desligado,
Hibernate `validate`, Flyway desligado e H2 em arquivo com `WRITE_DELAY=0`.

Carregue a configuração pelo mecanismo externo do Spring Boot. O argumento pode
identificar o **arquivo**, mas nunca deve conter token, senha ou outro segredo:

```powershell
java -jar '<JAR atualizado>' '--spring.config.additional-location=file:C:/RotaBrasil/Config/operacao.properties'
```

Preserve os parâmetros de banco/perfil autorizados do ambiente. A simulação
documental permanece independente: ativar confirmação REAL **não ativa** envio
documental REAL. Uma requisição do navegador não pode escolher hosts ou modo REAL.

O painel mostra a conta salva, modo, tipo ainda não identificado ou escolhido,
autenticação, pré-requisitos ausentes e disponibilidade para **teste**, sem afirmar
que salvar o endereço autenticou a conta. Configuração REAL incompleta bloqueia
novas conferências/envios; não troca silenciosamente para simulação.

## Uso, teste e recuperação

### Sem envio externo

1. Use base/acervo isolados e esquema V11, conforme README. Deixe ambos os modos
   em `SIMULADO` e SMTP desativado.
2. Salve remetente sintético em Configurações. O teste simulado existente permanece
   disponível e não verifica autenticação Microsoft.
3. Selecione ZIP sintético e informe `motorista@example.invalid` no upload.
4. Após a inspeção, na seção **Confirmação de recebimento ao motorista**, revise
   o endereço e clique em **Conferir confirmação ao motorista**.
5. Confira todos os campos, marque a autorização de simulação e confirme.
6. Consulte o histórico e a Central. SIMULADO significa que nenhum e-mail real saiu.

### Teste real controlado, ainda não executado

Somente após resolver tipo de conta, autorização OAuth2, políticas SMTP e acesso
autenticado da aplicação:

1. Configure o ambiente externamente e salve o remetente correspondente.
2. Em Configurações, ADMIN informa o destinatário controlado do teste e usa
   **Conferir teste REAL**. A mensagem é sintética e não leva documentos reais.
3. Apresente/revise remetente, destinatário, assunto e corpo. Preparar a conferência
   não envia. Obtenha autorização explícita para **aquele teste**, marque a caixa e
   clique em **Enviar confirmação REAL**.
4. Consulte o resultado em testes da configuração. **Ver mensagem do teste** é
   somente leitura do snapshot, sem envio. Aceite SMTP não comprova entrega/leitura.

Nesta execução não há conta identificada, token real disponibilizado ou autorização
de destinatário/mensagem para teste externo. Nenhuma tentativa externa foi feita.

### Estados e idempotência

| Estado | Apresentação e significado |
| --- | --- |
| `SIMULADO` | Verde: fluxo de simulação concluído; nenhum e-mail real enviado |
| `ACEITO_SMTP` | Verde: servidor SMTP aceitou; entrega e leitura não comprovadas |
| `FALHA` | Vermelho: recusa conhecida de autenticação/provedor, sem aceite confirmado |
| `DESCONHECIDO` | Âmbar: resultado não comprovado; não repetir automaticamente |

Commit curto registra tentativa como DESCONHECIDO **antes** da comunicação. O
transporte não participa da transação de importação. O resultado é atualizado em
outro commit. A conferência possui unicidade no histórico; o lock existente serializa
a reserva. Repetir o mesmo ID, inclusive concorrentemente ou após perder resposta,
retorna a tentativa existente e não chama novamente o transporte.

Não há promessa de exatamente uma vez entre banco e SMTP. Queda após o commit,
timeout, erro genérico de rede ou perda de resposta pode deixar resultado desconhecido,
inclusive se o servidor tiver aceitado ou se nenhuma conexão tiver sido iniciada.
O frontend guarda apenas IDs em `sessionStorage`, sem destinatário/conteúdo/token,
para **Consultar/recuperar a mesma confirmação**. Não inicia recuperação automaticamente.

Reenvio deliberado exige nova conferência, autorização explícita e `anteriorId`
correspondente à última tentativa. Destinatário e mensagem anteriores permanecem
intactos. Reenvio bem-sucedido pode comprovar resolução de uma falha conhecida;
não resolve o resultado de uma tentativa anterior DESCONHECIDA. Fechar detalhes
ou navegar não resolve nenhuma pendência.

Falha de notificação não desfaz importação ou exportação. Corrigir pasta e retomar
somente a exportação não dispara uma confirmação real. As proteções de pasta externa,
publicação sem sobrescrita, SHA-256, sidebar e falhas de GET foram preservadas.

## Contrato HTTP

| Método/rota | Uso |
| --- | --- |
| `GET /api/configuracoes` | Acrescenta `confirmacaoMotorista` com modo, tipo, autenticação, situação, disponibilidade para teste e pré-requisitos seguros |
| `GET /api/recebimentos/{id}` | Mantém eventos e acrescenta tentativas manuais, snapshots e estado atual seguro do canal |
| `POST /api/recebimentos/{id}/conferencias-confirmacao` | `{destinatario, anteriorId?}`; prepara snapshot, não envia |
| `POST /api/recebimentos/{id}/confirmacoes` | `{conferenciaId, confirmado:true}`; confirma uma única identidade |
| `POST /api/configuracoes/conferencias-email-real` | ADMIN prepara teste sintético com `{destinatario}` |
| `POST /api/configuracoes/confirmacoes-email-real` | ADMIN confirma `{conferenciaId, confirmado:true}` de teste |
| `GET /api/configuracoes/conferencias-email-real/{id}` | Consulta a mensagem persistida do teste, sem enviar |
| `GET /api/diagnosticos/NOTIFICACAO/{id}` | Estado seguro e acesso ao histórico do recebimento |
| `GET /api/pendencias` | Inclui confirmações manuais, falhas, incertezas e resoluções comprovadas |

As rotas de confirmação seguem OPERADOR/ADMIN e CSRF quando a segurança está ativa.
Configuração/teste real seguem ADMIN e CSRF. As proteções do transporte também
verificam sessão autenticada para REAL; desenvolvimento local não libera SMTP.
Validação de endereço único ocorre no navegador e no backend. Campos desconhecidos,
incluindo servidor, modo, credenciais ou mensagem arbitrária no pedido, são recusados.

## Persistência, dados e implantação

Migração **V11**, em Java (`db.migration.V11__ConfirmacaoMotorista`):

- Nova tabela de **conferências**, não de tentativas paralelas: `conferencias_notificacao`.
- Novos campos em `notificacoes_recebimento`: conferência única, tentativa anterior,
  motivo tratado, ator e snapshot. Reutiliza correlação, criação e conclusão existentes.
- Nome seguro do arquivo e revisão da seleção atual em `recebimentos`.
- Evolução do CHECK de modo para SIMULADO/REAL. O nome do CHECK V9 era gerado pelo
  H2; a migração identifica somente esse CHECK e recusa esquema divergente.

O inventário/fingerprint inclui as conferências. Endereços, assunto e corpo de
metadados confirmados são dados persistentes do histórico e dos backups do banco,
com as permissões de leitura existentes. Não são copiados para logs técnicos ou
suporte. Tokens/credenciais não integram snapshots, API ou histórico.

Nenhuma migração publicada foi editada. Só bases sintéticas foram migradas. O banco
operacional não foi aberto/migrado e sua versão não foi presumida. Código atual
exige V11; migração operacional requer autorização e backup verificado, conforme
[manutenção](etapa4-banco-migracoes.md). Flyway automático continua desligado por padrão.

## Verificações desta entrega

Execução completa final em 31/10/2026, concluída às 01:56:57 -03:00:
**378 testes JUnit contabilizados: 377 aprovados, zero falhas/erros, 1 ignorado**
(symlink sem privilégio no Windows). Não é soma de recortes.

```powershell
$Build = Join-Path $env:TEMP 'opencode/rotabrasil-email-motorista-build'
mvn -B "-Drotabrasil.build.directory=$Build" -DargLine=-Xmx768m clean test
mvn -B "-Drotabrasil.build.directory=$Build" -DskipTests package
$Ui = Join-Path $env:TEMP ('opencode/rotabrasil-email-ui-' + [guid]::NewGuid().ToString('N'))
node scripts/testar-confirmacao-motorista.cjs "$Build/rota-brasil-0.0.1-SNAPSHOT.jar" "$Ui/motorista"
```

O limite de heap mantém os testes isolados previsíveis nesta máquina; não altera
regras de domínio. Testes novos: 10 HTTP de confirmação, 4 HTTP protegidos com double
de transporte REAL, 3 casos adicionais do adaptador SMTP e 1 de migração V10→V11.
Cobrem simulação, bloqueios, ausência de anexos, seleção alterada, snapshot, recusa,
timeout/incerteza, concorrência, repetição, reenvio, logs sem segredo sintético,
preservação de importação/exportação/bytes e preservação dos históricos na migração.

Navegador, em execuções distintas desta revisão:

- Confirmação ao motorista: **7 verificações aprovadas**, com destinatário corrigido,
  pendência real de PDF, prévia, confirmação explícita, clique duplo/resposta perdida,
  recuperação idempotente, reenvio, Central, recarga, reinício e REAL incompleto bloqueado.
- Configurações/exportação: **7 aprovadas**.
- Diagnóstico/recuperação: **7 aprovadas**, incluindo contraste, pasta e falhas de GET.
- Estabilização: **5 aprovadas**, incluindo versões/cache, sidebar e recusa/incerteza.
- Envio documental local anterior: **13 cenários aprovados**.

Nenhum erro JavaScript inesperado nas execuções aprovadas. REAL foi validado somente
com mocks/doubles e testes locais controlados do adaptador; **não houve SMTP externo**.
A etapa protegida da interface não é declarada homologada: os testes protegidos
HTTP usam a segurança existente e o teste de navegador local não equivale a login real.

Relatórios externos em `%TEMP%/opencode/rotabrasil-email-motorista-build/surefire-reports/`.
Resultados/capturas em `rotabrasil-email-motorista-ui/`, `rotabrasil-email-motorista-config-ui/`,
`rotabrasil-email-motorista-recuperacao-ui/`, `rotabrasil-email-motorista-estabilizacao-final-ui/`
e `rotabrasil-email-motorista-documental-final-ui/`, sob a mesma raiz temporária.
O identificador de compatibilidade do JAR/painel é `email-motorista-V11-20261031`.

Falhas intermediárias investigadas: um texto novo de pré-requisito colidiu com
asserção antiga que proíbe a palavra de segredo na API (o texto foi ajustado, sem
relaxar o teste); o fixture protegido inicialmente não provisionava o administrador
exigido pelo bootstrap (corrigido com hash sintético); uma execução Maven ultrapassou
o prazo externo; uma regressão de navegador encontrou `ERR_NETWORK_IO_SUSPENDED`.
As reexecuções também expuseram duas fixtures não determinísticas: um teste antigo
regenerava o ZIP entre conferência e confirmação (timestamp mudava o SHA-256 e
produzia 409 correto), e uma fixture nova usava mapa sem ordem para entradas cujos
IDs o teste conhecia por posição (associação recusada com 400). As fixtures agora
reutilizam os mesmos bytes e ordem explícita, respectivamente. Nenhuma regra de
hash ou associação foi relaxada. A classificação SMTP também foi revisada para
manter desconhecido um erro posterior ao aceite, por exemplo no comando QUIT.
As execuções finais concluídas acima não incluem esses recortes interrompidos.

### Pendências para homologação externa

Identificar a conta; registrar/autorizar a aplicação de envio com o escopo adequado;
disponibilizar e renovar credencial OAuth2 segura; verificar políticas do tenant se
corporativa; disponibilizar acesso autenticado utilizável; preparar o banco V11 com
autorização; e aprovar remetente, destinatário e mensagem de **um teste específico**.
Nenhuma dessas pendências foi contornada por autenticação básica presumida, TLS
desabilitado, leitura de credenciais ou liberação do perfil local.
