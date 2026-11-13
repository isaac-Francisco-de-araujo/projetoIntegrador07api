# Central documental v2 — instância identificada e início seguro

**Atualização de digitação:** o artefato abaixo preserva a validação anterior. Para
o JAR mais recente, que mantém o destinatário editável também durante consultas e
prévia, use [a entrega de destinatário editável](destinatario-editavel.md).

## Causa efetiva da reincidência — 13/11/2026

Foi identificado o processo Java **PID 13676**, ouvindo em **127.0.0.1:8080**, que
corresponde à URL informada `http://localhost:8080/painel#email`.
Ele executa:

```text
C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-marca-20261113-build/rota-brasil-0.0.1-SNAPSHOT.jar
```

Compilação desse artefato: **2026-11-13T07:15:23Z**.
SHA-256: `adae85306e18308097cd9e20a2dbd7fdfbf69e0168943713be06a19ee317ea6f`.

O arquivo foi examinado como ZIP de código, sem iniciar a instância operacional,
ler argumentos completos de processo, consultar sessão ou acessar banco/documentos.
Ele **ainda contém**:

- `painel-base.js`: aviso genérico “Envio não configurado” para modo SIMULADO;
- `painel-documentos.js`: `!state.envioReal` como condição que desabilita Gerar prévia;
- nenhum endpoint de diagnóstico `EnvioDocumentalController` da correção anterior.

Executei **esse mesmo JAR** em outra porta, com H2 novo e três arquivos sintéticos
(um XML e dois PDFs). Seleção=3 e sessão local ADMIN válidas: campo e prévia ficam
desabilitados pelo modo, enquanto a API de conferência persistida responde 200.
O exemplo do destinatário é placeholder, não valor escolhido. Isso reproduz o
bloqueio sem usar documentos da captura nem o banco da porta 8080.

**A correção anterior existia em outro artefato; não estava no processo da porta 8080.**
Uma recarga do navegador nessa mesma porta não muda o JAR em execução.
Também há outro processo na porta 8081; ele não deve ser confundido com a URL informada.
Nenhum dos dois processos foi parado, reiniciado ou substituído nesta tarefa.

## O que mudou nesta continuação

- `/api/versao` identifica `centralEnviosContrato=documental-conferencia-v2` e inclui
  o hash de `painel-documentos.js`, que controla a prévia, além do verificador novo.
- A inicialização confere hashes dos módulos da Central com o backend desta origem
  e mostra incompatibilidade em vez de liberar silenciosamente uma composição mista.
- A Central mostra compilação e identidade v2 visíveis.
- O diagnóstico lista host/usuário/credencial documentais ausentes pelos nomes
  exatos das propriedades, sem expor valores sensíveis. Informa Gmail quando o
  host documental é `smtp.gmail.com`. Presença local não prova autenticação.
- Configurações tem seção **Envio documental ao cliente — SMTP / Gmail** própria.
  O link que ainda levava aos requisitos do canal opcional ao motorista foi corrigido;
  os detalhes Microsoft agora têm título explícito de canal separado.
- Salvar endereço atualiza o diagnóstico, mas nunca autentica nem ativa REAL.
- Preparação continua independente do SMTP. Confirmação revalida prontidão,
  autorização, seleção e integridade, preservando histórico e idempotência.
- Acrescentados auditor de JAR, fixture de três documentos e iniciador isolado.

## JAR corrigido validado

```text
C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-central-v2-jar/rota-brasil-0.0.1-SNAPSHOT.jar
```

- Compilado em: **2026-11-13T19:18:08Z**.
- SHA-256: `528e1b358c78440c3b29d56048dd440eeb4efac1668c7880a225c7c414775785`.
- Contrato da Central: **documental-conferencia-v2**; esquema continua **V15**.
- O JAR foi realmente iniciado com H2/acervo novos, não apenas compilado.
- Os hashes dos módulos servidos foram comparados com a API e com `front/`.

Evidências externas em `C:/Users/florb/AppData/Local/Temp/opencode/`:

| Evidência | Arquivo/pasta |
|---|---|
| JAR antigo com três arquivos, bloqueado | `rotabrasil-central-v2-antigo/resultado.json`, `antes-bloqueio.png` |
| Identidade exata do JAR corrigido e requisições | `rotabrasil-central-v2-ui/resultado.json` |
| Preparação com três documentos | `rotabrasil-central-v2-ui/preparacao-desktop.png` |
| Conferência persistida e celular | `rotabrasil-central-v2-ui/conferencia-desktop.png`, `conferencia-mobile.png` |
| Falha de configuração e recuperação | `rotabrasil-central-v2-ui/falha-configuracao.png` |
| Frontend/backend incompatíveis | `rotabrasil-central-v2-ui/versao-incompativel.png` |
| Gmail documental separado na configuração | `rotabrasil-central-v2-double/configuracao-gmail-documental.png` |
| Confirmação, resposta perdida e desconhecido com double | `rotabrasil-central-v2-double/resultado-real-double.json` e capturas da mesma pasta |
| Suíte integrada | `rotabrasil-central-v2-test.log` |
| Auditoria dos dois artefatos | `rotabrasil-central-v2-artefatos.json` |

**418 testes JUnit: 417 aprovados, zero falhas/erros e 1 ignorado** por symlink Windows,
incluindo o navegador protegido com transporte double. Preparação validada no JAR,
desktop/celular, temas, teclado, três arquivos, destinatário inválido/válido, falha de
consulta, resposta obsoleta, divergência de hashes e recuperação. Confirmação protegida
valida papéis, CSRF, divergência de remetente, clique duplo, resposta perdida depois do
processamento, recuperação idempotente e reenvio vinculado com resultado desconhecido.
Nenhuma chamada ao JavaMailSender no teste protegido; dois acionamentos do double.

## Iniciar a versão corrigida agora, sem substituir a porta 8080

Abra PowerShell na raiz do repositório, com Java 25 e Node disponíveis:

```powershell
$Jar = 'C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-central-v2-jar/rota-brasil-0.0.1-SNAPSHOT.jar'
(Get-FileHash -Algorithm SHA256 -LiteralPath $Jar).Hash
node scripts/iniciar-central-isolada.cjs $Jar 8092
```

O hash deve corresponder ao acima (maiúsculas/minúsculas não importam).
Abra **http://127.0.0.1:8092/painel#email** — não a porta 8080.

O iniciador cria banco/acervo temporários novos, importa somente os três arquivos
sintéticos e verifica o contrato e hashes antes de informar que a instância está pronta.
Força loopback, perfil desenvolvimento isolado e todos os modos de e-mail SIMULADO;
não carrega configuração externa do diretório operacional nem ativa SMTP real.
Se a porta estiver ocupada, falha antes de usar a instância que já a ocupa.

1. Abra **Viagens e documentos**, escolha a viagem sintética e marque os três arquivos.
2. Abra **Envios**. Deve aparecer “Central documental v2”, a compilação acima e os nomes.
3. Informe manualmente um destinatário sintético, por exemplo `conferencia@example.invalid`.
4. Gere a prévia e revise os três arquivos. Nenhum transporte é chamado.
5. A confirmação real fica bloqueada com os requisitos concretos da configuração isolada.
6. Ctrl+C encerra somente essa instância. O diretório sintético informado fica separado
   para conferência; não contém dados operacionais. A porta 8080 permanece intocada.

Para conferir também a resposta da API, sem dados operacionais:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8092/api/versao' |
  Select-Object versao, compiladoEm, centralEnviosContrato
```

O modo não interativo `node scripts/iniciar-central-isolada.cjs $Jar 8092 --verificar`
também foi executado: iniciou, verificou os recursos, importou três sintéticos e encerrou.

Para recompilar outro artefato, utilize diretório externo NOVO, execute testes e
package. Um novo build muda horário/hash; confira o novo hash e a API juntos.
Não copie recursos web avulsos para um JAR antigo.

## Configuração externa Gmail documental — nomes exatos

Esses nomes foram conferidos em `ConfiguracaoSmtp`, `TransporteSmtp`,
`ProntidaoEnvioDocumental` e nas propriedades Spring. O mesmo `TransporteSmtp`
compõe mensagem e anexos; Gmail não exige host ou tipo de conta Microsoft.

| Propriedade canônica | Uso |
|---|---|
| `rotabrasil.email.modo` | `SIMULADO` por padrão; `REAL` só em ativação futura autorizada |
| `rotabrasil.email.smtp.ativado` | Segundo opt-in externo, `true` somente nessa ativação |
| `rotabrasil.email.smtp.host` | `smtp.gmail.com` para Gmail/Workspace |
| `rotabrasil.email.smtp.porta` | `587` |
| `rotabrasil.email.smtp.usuario` | Conta autorizada no Google |
| `rotabrasil.email.smtp.remetente` | Remetente autorizado, correspondente ao salvo em Configurações |
| `rotabrasil.email.smtp.autenticacao` | `BASIC` para senha de aplicativo ou `XOAUTH2` |
| `rotabrasil.email.smtp.senha` | Apenas a senha de aplicativo Google para BASIC; nunca senha normal |
| `rotabrasil.email.smtp.oauth2.token-arquivo` | Arquivo externo do token quando XOAUTH2; sem fallback para senha |
| `rotabrasil.email.smtp.timeout-ms` | Padrão `10000`, permitido `1000`–`30000` |
| `rotabrasil.seguranca.ativa` | `true` para envio REAL, com sessão OPERADOR/ADMIN e CSRF |

`rotabrasil.teste-email.gmail.*`, `ROTABRASIL_GMAIL_REMETENTE` e
`ROTABRASIL_GMAIL_APP_PASSWORD` pertencem à conta administrativa de teste; **não são
configuração do transporte documental**. Não presumir que defini-las autentique a Central.
`rotabrasil.notificacoes.*` pertence ao canal opcional ao motorista e não configura Gmail documental.

### Como os valores chegam ao Java, sem segredo no comando ou no checkout

No computador que executa o backend, o administrador deve criar um arquivo externo,
por exemplo `C:/RotaBrasilSeguro/documental.properties`, editado localmente, com ACL
restrita à conta de serviço/administrador. Nunca enviar o conteúdo ao chat ou versioná-lo.
Pode partir deste esqueleto **sem segredos**:

```properties
# Preparação apenas; estes opt-ins não autorizam envio nesta tarefa.
rotabrasil.email.modo=SIMULADO
rotabrasil.email.smtp.ativado=false
rotabrasil.email.smtp.host=smtp.gmail.com
rotabrasil.email.smtp.porta=587
rotabrasil.email.smtp.timeout-ms=10000
# Preencher localmente com a conta autorizada, sem aspas:
rotabrasil.email.smtp.usuario=
rotabrasil.email.smtp.remetente=
rotabrasil.email.smtp.autenticacao=BASIC
# Preencher SOMENTE neste arquivo protegido com senha de aplicativo autorizada:
rotabrasil.email.smtp.senha=
```

Para OAuth2, usar `rotabrasil.email.smtp.autenticacao=XOAUTH2` e
`rotabrasil.email.smtp.oauth2.token-arquivo=C:/RotaBrasilSeguro/google-smtp.json`.
O utilitário externo autorizado deve escrever `access_token` e `expires_at` nesse
arquivo protegido, com validade superior a 60 segundos e autorização Google para SMTP.
Obtenção/renovação/consentimento são externos. Não copiar tokens para a interface.

O Spring Boot carrega esse arquivo com o argumento **não secreto**:

```powershell
java -jar $Jar '--spring.config.additional-location=file:C:/RotaBrasilSeguro/documental.properties'
```

**Não executar essa última linha isoladamente nesta pasta:** o processo também precisa
de configuração explícita de perfil `operacao`, HTTPS/cookie seguro, banco/acervo
autorizados e V15, Flyway desligado e `ddl-auto=validate`. Para um teste futuro protegido,
use somente base sintética V15 previamente preparada, em outra porta, nunca o banco
operacional por omissão. Um comando completo deve apontar ao arquivo protegido de
runtime que define esses itens, por exemplo:

```powershell
java -jar $Jar '--spring.profiles.active=operacao' '--server.port=8443' '--spring.config.additional-location=file:C:/RotaBrasilSeguro/runtime-isolado.properties,file:C:/RotaBrasilSeguro/documental.properties'
```

`runtime-isolado.properties` deve definir `spring.datasource.url` absoluto com
`IFEXISTS=TRUE;WRITE_DELAY=0` na base sintética, `rotabrasil.arquivos.viagens` absoluto,
`spring.flyway.enabled=false`, `spring.jpa.hibernate.ddl-auto=validate`,
`server.ssl.enabled=true`, `server.ssl.key-store`, `server.ssl.key-store-password` e
o ADMIN inicial por `rotabrasil.seguranca.admin-usuario` / `rotabrasil.seguranca.admin-hash`.
Segredos ficam somente nesses arquivos protegidos. O hash é gerado pela CLI interativa
existente `GerarHashSenha`, que não acessa banco ou rede:

```powershell
java '-Dloader.main=br.com.rotabrasil.manutencao.GerarHashSenha' -cp $Jar org.springframework.boot.loader.launch.PropertiesLauncher
```

Definir `server.address=127.0.0.1` para o teste local e abrir a URL **HTTPS** da porta
escolhida. Não desabilitar cookie seguro, CSRF ou TLS para contornar configuração.
Se o arquivo não existir ou a conta Java não puder lê-lo, o processo/diagnóstico não
terá a credencial; editar outro terminal, frontend ou proxy não fornece segredo ao Java.
Alterar propriedades de ambiente/arquivo exige reiniciar o processo correto; o arquivo
OAuth2 é relido pelo adaptador. Na ativação futura, substituir os opt-ins explicitamente
no arquivo protegido e gerar nova conferência. Não foi feita essa ativação nesta tarefa.

### O que ainda falta comprovar

Não foram lidos os segredos nem a configuração da instalação operacional. Para Gmail
REAL faltam provisionamento/verificação autorizados da conta, credencial/consentimento,
rede, remetente e sessão protegida, além de teste externo futuro com destinatário
controlado e recebimento comprovado. Salvar endereço não comprova nada disso.
Prontidão local e ACEITO_SMTP do double não homologam Gmail. A preparação isolada está
funcional; na porta 8080 continua o artefato antigo até substituição deliberada fora
desta tarefa. Nenhum e-mail externo foi enviado e nenhum banco operacional foi acessado.
