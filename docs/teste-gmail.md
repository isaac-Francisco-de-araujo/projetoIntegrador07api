# Teste manual de Gmail com XML sintético

O teste real foi autorizado pelo usuário para um remetente e um destinatário
de teste definidos nas configurações do ambiente. Os endereços não são gravados
no código nem em cadastros operacionais. O teste não usa documentos reais.

Foi adicionada `spring-boot-starter-mail`, na versão gerenciada pelo Spring Boot
existente. O teste é uma CLI separada: não inicia Spring, JPA, servidor HTTP ou
agendador e não consulta banco, contatos ou acervo. A aplicação normal continua
sem disparo operacional de e-mail, e a prévia permanece somente de leitura.

## Configuração segura

Na conta remetente, habilite a verificação em duas etapas e gere uma
[senha de app do Google](https://myaccount.google.com/apppasswords).
Use os 16 caracteres sem os espaços de apresentação; não use a senha normal da
conta e não envie a senha no chat ou no Git. Se a conta não disponibiliza senhas
de app, é necessário avaliar autenticação OAuth2 antes de continuar.

Configure estas variáveis no ambiente de execução:

- `ROTABRASIL_GMAIL_REMETENTE`: endereço Gmail completo autorizado como remetente.
- `ROTABRASIL_GMAIL_DESTINATARIO`: endereço autorizado para receber o teste.
- `ROTABRASIL_GMAIL_APP_PASSWORD`: senha de app, fornecida como valor protegido
  nas configurações seguras do ambiente. Não incluí-la em scripts, histórico do
  shell ou argumentos de linha de comando.

O programa lê os valores diretamente do processo. Um placeholder de segredo
substituído apenas em requisições HTTPS por proxy não serve como senha SMTP;
a variável precisa estar disponível de forma segura no processo que executa o
teste. As instruções salvas declaram nomes, nunca valores de credenciais.

A conexão requer resolução de `smtp.gmail.com` e saída TCP na porta 587. A
liberação do domínio no rascunho de rede não comprova que SMTP direto está
suportado ou liberado no ambiente em execução. Não contorne a falha usando IP
fixo, TLS desativado ou certificado sem verificação.

## Executar uma tentativa

Após `mvn clean test` e `mvn -DskipTests package`, copie o JAR para uma área de
artefatos externa ao checkout. Na nuvem, ele está em
`/workspace/rotabrasil-dev/gmail-teste/app.jar`.

Conferência local, sem conexão SMTP ou autenticação:

```sh
/workspace/tools/jdk-25.0.5/bin/java \
  -Dloader.main=br.com.rotabrasil.manutencao.TesteGmail \
  -cp /workspace/rotabrasil-dev/gmail-teste/app.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher --verificar
```

Uma tentativa de envio real, somente depois da configuração e com os endereços
autorizados:

```sh
/workspace/tools/jdk-25.0.5/bin/java \
  -Dloader.main=br.com.rotabrasil.manutencao.TesteGmail \
  -cp /workspace/rotabrasil-dev/gmail-teste/app.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher --enviar
```

Sem a opção `--enviar`, não há tentativa de envio. Configuração ausente/inválida
retorna código 2; falha de envio retorna 1; sucesso no aceite SMTP retorna 0.
`--verificar` também retorna 0 quando as variáveis/endereço estão válidos, mas
**não** comprova conectividade, autenticação ou entrega. Nenhum valor de senha ou
resposta privada de exceção SMTP é impresso pela CLI.

O transporte usa `smtp.gmail.com:587`, autenticação, STARTTLS obrigatório,
verificação da identidade do servidor e timeouts de 10 segundos por operação.
Não há repetição automática. Em erro ou resultado desconhecido, confira a caixa
de destino antes de repetir para evitar duplicar a mensagem.

A mensagem tem assunto de TESTE e exatamente um anexo fixo
`email/cte-sintetico.xml`, marcado sem valor fiscal. O programa não aceita
caminhos de anexos, IDs de documentos operacionais, destinatários adicionais ou
uma seleção de acervo. O aceite pelo servidor SMTP não comprova entrega: é
necessário conferir a caixa de entrada/spam da conta destinatária.

## Verificação desta implementação

Suíte completa: **190 testes**, zero falhas, erros ou ignorados, incluindo 13
novos casos em `TesteGmailTest`. Transporte mock verifica composição MIME,
remetente, destinatário único, anexo sintético e ausência de repetição em falha.
Um servidor SMTP local sem STARTTLS verifica que o cliente recusa a conexão
antes de enviar AUTH, MAIL FROM ou DATA. Nenhum teste automatizado envia e-mail
real ou usa credenciais reais.

O JAR é empacotado e a CLI é exercitada em `--verificar` com configuração
sintética, sem rede. Resultados ficam em `/workspace/rotabrasil-dev/gmail-teste`.
Banco operacional e XMLs têm hashes conferidos antes/depois.

No ambiente desta entrega, a senha de app estava ausente e a tentativa de
resolver/conectar a `smtp.gmail.com` falhou na resolução DNS. **Nenhum e-mail
real foi enviado, e autenticação/entrega no Gmail não foram verificadas.**
Esses pré-requisitos permanecem necessários para executar o teste autorizado.

## Limite do teste

Esta autorização cobre o teste sintético para os endereços informados. Não
habilita envio operacional, reenvio automático ou uso de documentos fiscais
reais. Não altera `envios` nem registra a mensagem sintética como entrega
operacional. Seleção de documentos reais, histórico auditável e política de
falhas/reenvio da Etapa 8 continuam pendentes.
