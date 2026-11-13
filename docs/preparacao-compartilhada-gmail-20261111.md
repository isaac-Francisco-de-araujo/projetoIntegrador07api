# Preparação compartilhada e Gmail — 11/11/2026

## Partida e diagnóstico

- Branch `main`, HEAD `eb59f5de957acd25fac0fd9be094669d9fc3e963`.
- `git fetch origin main` executado; `origin/main` permanece em
  `5fdd7241c92463da2d7ce4573a3bf01e4b9a2ef9`, três commits atrás do local.
  Não havia atualização remota para mesclar. Sem reset, commit, push ou reescrita.
- Preservadas as alterações locais da separação `front/` e `back/`, POM agregador,
  documentação e caminhos dos scripts. O estado anterior já continha apresentação
  pública, login próprio Spring Security, Gmail administrativo e esquema V15.
- Java Temurin 25.0.4.1, Maven 3.10.0, Node 24.18.0, Chrome local e Playwright
  **1.58.2**, conforme `scripts/package.json`. Python inicialmente indisponível;
  instalado CPython 3.13.14 com uv em diretório externo próprio.
- Lidos os contratos atuais de uso compartilhado, ZIP, configuração/exportação,
  envio manual, confirmação ao motorista, recuperação e banco/backup.

## Código concluído nesta continuação

| Arquivo | Alteração |
|---|---|
| `back/src/main/java/br/com/rotabrasil/service/ContaTesteEmailService.java` | Assinatura Gmail inclui versão externa não secreta e ativação; última verificação exige assinatura compatível; diagnóstico Google distingue expiração/reconexão/renovação |
| `back/src/main/java/br/com/rotabrasil/service/NotificacaoRecebimentoService.java` | Nova tentativa administrativa exige conferência vinculada à última tentativa; revalida vínculo sob coordenação antes de reservar; conferências concorrentes antigas recusadas |
| `back/src/main/java/br/com/rotabrasil/dto/ConfirmacaoRecebimentoDtos.java` | `snapshot.testeAnteriorId` no JSON existente; preserva domínio/FK do reenvio ao motorista |
| `back/src/main/java/br/com/rotabrasil/service/ConfiguracaoOperacaoService.java` | Simulação registra assinatura; histórico mantém última tentativa real além dos 20 recentes quando necessário |
| `front/painel-configuracoes.js` | Nova preparação consulta a última tentativa e exige decisão explícita de preparar outra mensagem vinculada |
| `front/painel-conta-teste.js` | Aceite de autenticação identificado somente na verificação correspondente, distinto de recebimento; resposta antiga não atualiza conta/rascunho posterior |
| `back/src/test/java/br/com/rotabrasil/ContaTesteEmailHttpTest.java` | Cinco regressões novas: vínculo/concorrência incerta, rotação externa, estados OAuth Google, expiração/adulteração/outro ADMIN, histórico longo |
| `scripts/testar-entrada.cjs` | Aguarda resposta real de cadastro e atualização da lista no contexto `/rota` |
| `scripts/testar-configuracoes.cjs` | Asserção da interface corrigida mantendo conferência da localização no backend; usa `/painel`, prontidão do módulo e detalhes/histórico sob demanda; conserva exportação, vínculos, hashes e reinício |
| `scripts/testar-uso-compartilhado.cjs` | Aceite em `operacao` com HTTPS, quatro contextos independentes, cookies, CSRF, importação auditada, desativação/reativação e papel atualizado |
| `scripts/testar-uso-diario.cjs` | Senha do keystore sintético em ambiente do processo, não em argumentos; reinício protegido com IFEXISTS |

Documentação atualizada: README, AGENTS, `gmail-configuravel.md`,
`uso-compartilhado.md`, `configuracoes-exportacao-notificacoes.md`,
`confirmacao-motorista.md`, `etapa4-banco-migracoes.md`, `front-back.md` e este relatório.
Os demais arquivos movidos e ajustes de caminhos pertencem à separação local anterior.

Nenhuma migração nova necessária: esquema **V15**. Verificados os 234 arquivos de
fontes/testes/estáticos rastreados nas novas pastas; 15 migrações publicadas
preservadas, comparando texto com normalização de fim de linha. Dados, vínculos,
tentativas e originais não foram convertidos para concluir esta tarefa.

## Resultados executados agora

| Verificação | Resultado |
|---|---|
| Build limpo externo Java | **407 contabilizados, 406 aprovados, zero falhas/erros, 1 ignorado** por privilégio de symlink no Windows |
| `ContaTesteEmailHttpTest` | 14 aprovados; transporte substituído por double controlado, inclusive casos chamados REAL |
| `MigracaoEBackupTest` | 17 aprovados; backup, corrupção recusada e restauração V15 sintética com inventário/vínculos/bytes comparados sem ler os originais |
| Python unittest | **2 aprovados**, sem sobrescrita e sem travessia de caminho |
| `testar-entrada.cjs` | **8 grupos**, `operacao`, HTTPS, raiz e `/rota`, três papéis, logout e expiração |
| `testar-uso-compartilhado.cjs` | **12 grupos**, sessões independentes, conflitos, responsabilidades, autoria, importação protegida e revalidação de contas |
| `testar-configuracoes.cjs` | **7 grupos**, publicação/hard link, fatura compartilhada, SHA-256, indisponibilidade/retomada e reinício |
| `testar-uso-diario.cjs` | **16 grupos**, Gmail simulado, ZIP com duas viagens/PDFs, módulos secundários falhando, resposta perdida, sessão/contrato, interface e recuperação |
| Empacotamento | BUILD SUCCESS; frontend e API no mesmo JAR |
| `git diff --check` | Sem erros de whitespace |

Os quatro roteiros finais usam Playwright **1.58.2**, com zero erros JavaScript.
Não somar recortes JUnit como testes adicionais à suíte completa. O zoom por atalho
do roteiro diário não foi aplicado pelo Chrome nesta execução (`aplicado:false`);
isso não é aprovação nova de zoom real. Capturas/medidas de viewport e contraste
foram executadas; resultados históricos de zoom têm suas próprias evidências.

### Falhas investigadas durante o trabalho

- Teste de Configurações abria a apresentação e tratava ausência do loader como
  prontidão do painel; passou a abrir `/painel` e aguardar o módulo. A indicação
  resumida da pasta mudou: agora confere o texto atual **e** `localDaPasta` da API.
- Controles opcionais legados estão ocultos e o histórico é carregado sob demanda.
  O teste preserva a cobertura do upload com destinatário explícito pela API e
  abre os detalhes pelos controles existentes, sem reexpor campos ocultos.
- Na primeira implementação do vínculo administrativo, a FK `anterior_id` apontava
  para notificações, não testes. Os testes reproduziram a recusa; o vínculo agora
  fica em `snapshot.testeAnteriorId`, sem editar esquema/migração publicada.
- Cadastro em `/rota` excedia a espera padrão de cinco segundos em execução anterior.
  Agora o roteiro exige resposta HTTP 200 e aguarda a lista; raiz/contexto passaram.
- Uma execução concorrente de dois roteiros excedeu o limite de inicialização
  do servidor. Não foi comprovada uma causa de código para esse atraso; o aceite
  final foi sequencial e passou. Nenhuma proteção operacional foi desativada.
- Playwright externo preexistente era 1.64.1. Instalado 1.58.2 em área própria e
  repetidos os quatro roteiros contra o JAR final, todos aprovados.

Não restaram falhas nos blocos finais executados. SMTP Google, rede corporativa,
instalação central e o ZIP operacional específico **não foram homologados**.

## Reprodução e ferramentas

Artefatos desta revisão ficam sob
`C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-continuacao-20261111-`:

- `build/`: JAR, classes e relatórios Surefire;
- `junit-final.log`, `package-final.log`: logs finais;
- `entrada-1582/`, `compartilhado-1582/`, `configuracoes-1582/`, `diario-1582/`:
  `resultado.json` e capturas aprovadas;
- `tools/`: dependência Playwright isolada;
- Python em `C:/Users/florb/AppData/Local/Temp/opencode/python-managed/`.

Na raiz do repositório, PowerShell com Java 25/Maven/Node, Chrome e uv disponíveis:

```powershell
$Build = Join-Path $env:TEMP 'opencode/rotabrasil-continuacao-build'
mvn -B "-Drotabrasil.build.directory=$Build" -DargLine=-Xmx768m clean test
mvn -B "-Drotabrasil.build.directory=$Build" -DskipTests package

# Ferramentas externas próprias; sem substituir instalações/configurações alheias.
$Tools = Join-Path $env:TEMP ('opencode/rotabrasil-tools-' + [guid]::NewGuid().ToString('N'))
npm install --prefix "$Tools" --no-audit --no-fund playwright@1.58.2
$env:NODE_PATH = Join-Path $Tools 'node_modules'
$Jar = Join-Path $Build 'rota-brasil-0.0.1-SNAPSHOT.jar'
foreach ($Roteiro in @('entrada','uso-compartilhado','configuracoes','uso-diario')) {
  $Saida = Join-Path $env:TEMP ('opencode/rotabrasil-' + $Roteiro + '-' + [guid]::NewGuid().ToString('N'))
  node "scripts/testar-$Roteiro.cjs" "$Jar" "$Saida"
  if ($LASTEXITCODE -ne 0) { throw "Roteiro falhou: $Roteiro; consultar saída externa" }
}

# Se Python não estiver disponível, instalação própria via uv.
$env:UV_PYTHON_INSTALL_DIR = Join-Path $env:TEMP 'opencode/rotabrasil-python'
uv python install 3.13.14
$Python = Join-Path $env:UV_PYTHON_INSTALL_DIR 'cpython-3.13.14-windows-x86_64-none/python.exe'
& $Python -B -m unittest discover -s python -p 'test_*.py' -v
```

Os roteiros usam loopback, porta dinâmica, bases novas e diretórios descartáveis.
Somente a preparação sintética usa Flyway; o aceite protegido/reinício mantém
`operacao`, Flyway desligado, validate, IFEXISTS e WRITE_DELAY=0. Certificado e
senha são descartáveis. A confiança no certificado autossinado é limitada ao
contexto de teste do navegador; não há `trust-all` no SMTP ou desativação de TLS.
Nenhum serviço é publicado ou instalado na máquina empresarial.

Prontidão: aguardar Tomcat, conferir `/login` e apresentação, entrar como ADMIN e
consultar `/api/versao`, `/api/sessao` e Saúde. Sem sessão, API deve retornar 401;
receber 401 não comprova falha do servidor. Encerrar normalmente os processos
próprios antes de reutilizar a base. Não encerrar instâncias desconhecidas.

## Configuração pendente e homologação futura

**Nomes de configuração externa, sem valores:**

- `rotabrasil.teste-email.modo`, `rotabrasil.teste-email.gmail.ativado`;
- `rotabrasil.teste-email.gmail.usuario`, `rotabrasil.teste-email.gmail.credencial-versao`;
- `rotabrasil.teste-email.gmail.senha-aplicativo` **ou**
  `rotabrasil.teste-email.gmail.token-arquivo`;
- alternativas existentes `ROTABRASIL_GMAIL_REMETENTE`, `ROTABRASIL_GMAIL_APP_PASSWORD`;
- contrato OAuth externo: `access_token`, `expires_at`, `authorization_state`, `renewal_managed`;
- operação: `ROTABRASIL_ADMIN_USUARIO`, `ROTABRASIL_ADMIN_HASH`,
  `spring.datasource.url`, `rotabrasil.arquivos.viagens`, `ROTABRASIL_EXPORTACAO_RAIZ`,
  `server.ssl.key-store`, `SERVER_SSL_KEY_STORE_PASSWORD` ou proxy HTTPS autorizado.

Gmail permite senha de aplicativo somente se as políticas da conta/2FA permitirem;
caso contrário, utilitário OAuth2 externo autorizado para SMTP Google e seu
agendamento são necessários. OAuth Microsoft não é credencial Google. O backend
consome o contrato e diagnostica seu estado; não adquire nem renova tokens.

**Decisões da empresa:** conta Gmail/Workspace autorizada, mecanismo e responsável
pela autorização/renovação; destinatário controlado; servidor central, conta de
serviço, HTTPS/rede e diretórios; responsável/destino/frequência/retenção de backup;
regra de divulgação integral de fatura com viagens/clientes diferentes. Não há
destinatário inferido, envio múltiplo automático, retenção ou exclusão automática.

**Futura validação real, sob autorização específica ainda não dada:**

1. Provisionar ambiente protegido isolado V15 e credencial fora de Git, documentos,
   logs e argumentos, restrita à conta do serviço. Conferir política Google e rede.
2. ADMIN salva provedor/remetente/autenticação; isso não autentica nem muda o canal
   documental/Microsoft. Só preparar modo REAL/opt-in quando autorizado.
3. Informar destinatário controlado e gerar prévia: conferir remetente, destinatário,
   assunto, texto sintético e ausência de anexos no teste administrativo.
4. Obter **confirmação específica dessa mensagem antes do envio**. Nenhuma permissão
   desta tarefa autoriza executar essa etapa agora.
5. Registrar separadamente resultado do transporte/aceite SMTP e recebimento observado
   pelo destinatário. Desconhecido exige consulta humana; repetir somente com nova
   conferência explicitamente vinculada. Não há repetição automática no reinício.
6. Homologar revogação/renovação e acesso compartilhado. Teste administrativo sem
   anexos não homologa a entrega documental; esta exige outro aceite sintético com
   seleção/anexos explicitamente autorizados. Motorista continua opcional.

**ZIP operacional não reproduzido:** faltam versão/contrato da instância do episódio,
horário, referência/correlação, etapa/HTTP, relatório seguro de entradas/quantidades/
tamanhos e códigos, associações tentadas e indicação de falha de sessão/contrato.
Uma reprodução sintética minimizada é preferível. Não foram procurados documentos
privados ou presumidos arquivos do ambiente antigo; a causa específica segue desconhecida.

**Confirmação:** nenhum e-mail externo enviado; nenhum banco operacional aberto,
alterado ou migrado; nenhuma exposição à internet ou implantação realizada.
