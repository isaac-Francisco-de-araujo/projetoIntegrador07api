# Revisão para uso diário por equipe — V15, 09/11/2026

## Ponto de partida e escopo

Conferidos `AGENTS.md`, README, acesso, ZIP, envio manual, Configurações,
diagnóstico/recuperação, uso compartilhado e manutenção. Comparados código,
migrações, testes HTTP e roteiros de navegador antes de acrescentar mudanças.

- Branch inicial `main`, HEAD `a5fa3b15c3b736080ea4cdd7aa111f1f499cab74`.
- `origin/main` confirmado com fetch e `ls-remote`:
  `5fdd7241c92463da2d7ce4573a3bf01e4b9a2ef9`.
- Commit local de apresentação/acesso ainda não publicado foi preservado.
- Alterações locais preexistentes de Gmail, V15, snapshots e roteiros foram
  examinadas e integradas. Nenhuma migração existente foi reescrita.
- Banco operacional, documentos reais e credenciais não foram utilizados.
  A versão da base operacional continua **não verificada nesta revisão**.
  Bases sintéticas novas foram inicializadas em V15; reinícios usam Flyway desligado.
- A instância local da tarefa anterior foi identificada em loopback:8080;
  testes usam portas dinâmicas e diretórios externos próprios, sem duplicá-la.

## Acompanhamento final

| Situação | Item e evidência |
|---|---|
| Concluído e testado | Apresentação pública → Entrar → login → painel; sem APIs antes do login; acesso direto com sessão; logout/expiração; HTTPS, cookies, CSRF e três papéis; contexto `/rota` |
| Concluído e testado | Bootstrap ADMIN por hash externo, cadastro/redefinição/ativação, última conta ADMIN protegida, versão e ator auditado no servidor |
| Concluído e testado | ZIP Selecionar → Conferir/associar → Importar, PDF visual, múltiplas viagens, fatura compartilhada, arquivos inválidos e resposta perdida; nenhuma importação parcial |
| Concluído e testado | SHA-256, originais, limites ZIP/caminhos, arquivamento/restauração, exclusão conferida, persistência/reinício e concorrência na suíte HTTP |
| Concluído e testado | Falhas de Saúde/Busca/Administração não bloqueiam ZIP; sessão/contrato incompatíveis bloqueiam alterações; API/estáticos no mesmo JAR |
| Concluído e testado | Doze abas existentes com título/resumo, menu agrupado, filtros e detalhes sob demanda, histórico separado; teclado, celular, temas e zoom real |
| Concluído e testado | Exportação externa manual/automática, fatura uma vez, conflitos sem sobrescrita e retomada só da cópia, sem reimportar ou enviar e-mail |
| Concluído e testado | Fila atômica, dois operadores disputando item, liberar/transferir, versão/rascunho e histórico; busca SQL paginada, desempate, debounce e descarte de resposta antiga |
| Concluído e testado | Backup sintético criado/verificado/restaurado em diretório vazio; comparação de banco/bytes sem ler originais; Saúde não usa manifesto como prova de restauração |
| Corrigido | GET da última verificação usava coluna inexistente; leitura V9 correta e seleção do histórico pela configuração da conta |
| Corrigido | Recarregar conta ficava indisponível após falha inicial; botão fora do fieldset, estado antigo não libera teste real após falha; formulário Gmail sem borda extra, campos de largura responsiva e texto legível no celular |
| Corrigido | Canal Gmail é reservado junto da tentativa; seleção de provedor posterior não redireciona uma tentativa para outro transporte; gravação da conta usa coordenação curta/versionamento |
| Corrigido | Conferências documentais novas incluem assunto/texto/remetente efetivos e os exibem na prévia/histórico; snapshots antigos preservados |
| Corrigido | Documentação de execução atual alinhada a V15; roteiro de arquivos acessa `/painel` em vez da apresentação pública |
| Depende de configuração | Servidor central, HTTPS confiável, conta de serviço, diretórios privados, raiz de exportação e contas individuais na empresa |
| Depende de configuração | Credencial Gmail externa, política da conta, conectividade SMTP e homologação de mensagem sintética específica; envio real não executado |
| Depende de decisão | Responsáveis por instalação/backup, destino, frequência, retenção e recuperação; regra comercial de faturas envolvendo clientes diferentes |
| Não implementado | Aquisição/renovação Google OAuth2 dentro do aplicativo; contrato de token externo já suportado. Não impede uso de senha de app permitida ou utilitário OAuth autorizado |
| Não implementado | Agendamento/descarte automático de backup e instalação automática de serviço; dependem da política e infraestrutura da empresa |

Leitura de caixas, download automático de ZIP, WhatsApp e reconstrução do sistema
não integram esta entrega. Confirmação ao motorista continua opcional; sem endereço
confirmado não bloqueia importação nem define destinatário documental.

## Causas comprovadas e limites

1. `testes_configuracao` (V9) tem coluna `configuracao`; a implementação local nova
   consultava `snapshot`, causando `BadSqlGrammarException`/HTTP 500. Corrigido sem
   migração; teste passa a exigir também status 200 e resultado correto.
2. O controle de recarga estava dentro de fieldset desabilitado quando a primeira
   consulta falhava. Separado do fieldset e exercitado com HTTP 503 sintético no
   navegador, seguido de recuperação real sem repetir qualquer envio.
3. A assinatura Gmail e a Microsoft têm contratos distintos. A última verificação
   agora respeita o canal correspondente; troca de conta/configuração não herda
   resultado de outra. Transporte continua fora da transação de importação.
4. O roteiro de arquivos abria `/`, que passou a ser apresentação. A revisão usa
   `/painel`, conservando todas as verificações de importação/exclusão/restauração.

Não foi reproduzida falha de ZIP válido no código atual sob falhas secundárias.
Erros de XML/PDF, falta de associação, sessão e contrato foram reproduzidos como
recusas esperadas. Nenhuma causa foi atribuída a um ZIP operacional não fornecido.

Busca usa duas consultas SQL paginadas (contagem + página), com joins/EXISTS,
sem uma consulta HTTP/diagnóstico por linha. V14 já contém índices pertinentes;
nenhum índice/migração extra foi criado sem necessidade. Busca por substring pode
exigir varredura SQL: não é alegação de benchmark do acervo real da empresa.

## Validação e capturas

Build externo: `C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-revisao-equipe-20261109-build/`.
Somente fixtures sintéticas, H2 isolado e doubles de transporte. Testes de SMTP
usam mocks/servidor local; nenhuma mensagem externa foi enviada.

| Bloco | Resultado |
|---|---|
| Segurança, conta e transporte (primeiro recorte) | 45 testes, zero falhas/erros |
| Conta, confirmação opcional, documento e transporte (recorte integrado) | 45 testes, zero falhas/erros; inclui conta alterada, assinatura Microsoft, OAuth expirado, recusa/desconhecido e idempotência |
| `testar-entrada.cjs` | 8 grupos, zero erros JavaScript, perfil operacao/HTTPS, contextos raiz e `/rota` |
| `testar-uso-diario.cjs` | 16 grupos, zero erros JavaScript, ZIP/configuração Gmail/recuperação/12 abas/três papéis |
| `testar-uso-compartilhado.cjs` | 10 grupos, zero erros JavaScript; dois operadores, conflitos, busca/paginação e saúde |
| `testar-exclusoes-paginas.cjs` | 10 grupos, zero erros JavaScript; XML/PDF/ZIP, cópia visual, exclusão/restauração e celular |
| `testar-zoom-uso-diario.cjs` | Zoom real Chrome 100/150/200%, DPR 1/1,5/2, sem overflow ou erros JavaScript |
| Suíte completa final (`clean test`) | **402 contabilizados, 401 aprovados, zero falhas/erros, 1 ignorado** por privilégio de symlink no Windows |
| Empacotamento final (`-DskipTests package`) | BUILD SUCCESS; inclui o frontend do mesmo código validado |
| Revalidação diária do JAR final | 16 grupos aprovados, zero erros JavaScript, incluindo recuperação da conta sem reutilizar prontidão antiga |

Recortes não são somados como total da suíte. Contraste medido em textos/controles
de Configurações: fontes de 15 px, mínimo de 7,06:1 claro e 9,36:1 escuro nas
amostras verificadas. Não é certificação global de acessibilidade de todo conteúdo.

Artefatos externos sob `C:/Users/florb/AppData/Local/Temp/opencode/`:

- `rotabrasil-revisao-equipe-20261109-entrada/`: apresentação, login e painel em
  1366/390 px, incluindo contexto `/rota`; `resultado.json`.
- `rotabrasil-revisao-equipe-20261109-diario/`: capturas das 12 abas em desktop e
  celular, Gmail, erros ZIP, tela clara e dados sintéticos; `resultado.json`.
- `rotabrasil-revisao-equipe-20261109-diario-final/`: revalidação do JAR final e
  capturas atualizadas dos mesmos cenários, incluindo formulário Gmail aberto em
  390 px; sem tempo mínimo artificial e sem generalizar como benchmark de produção.
- `rotabrasil-revisao-equipe-20261109-multi/`: fila/busca/saúde/operadores em celular.
- `rotabrasil-revisao-equipe-20261109-arquivos/`: exclusão conferida e navegação.
- `rotabrasil-revisao-equipe-20261109-zoom/`: 9 capturas, três telas × três zooms.

Logs, relatórios e imagens ficam fora do Git. Para reproduzir no PowerShell:

```powershell
$Build = Join-Path $env:TEMP 'opencode/rotabrasil-revisao-equipe-20261109-build'
mvn -B "-Drotabrasil.build.directory=$Build" -Dlogging.level.root=OFF -Dspring.main.banner-mode=off clean test
mvn -B "-Drotabrasil.build.directory=$Build" -DskipTests package
# Instale Playwright conforme scripts/package.json em área externa; NODE_PATH se necessário.
$Saida = Join-Path $env:TEMP ('opencode/rotabrasil-aceite-' + [guid]::NewGuid().ToString('N'))
node scripts/testar-entrada.cjs "$Build/rota-brasil-0.0.1-SNAPSHOT.jar" "$Saida"
```

Os roteiros já criam suas bases/portas próprias e encerram os processos. Para
outros blocos, usar o nome do roteiro e outra saída externa nova.

## Execução, dois operadores e instalação

1. Desenvolvimento local isolado: comandos do [README](../README.md#como-executar-localmente).
   Uma aplicação serve back/front; não há servidor Node separado. Não usar perfil
   sem autenticação para equipe em PCs diferentes.
2. Teste protegido com dois operadores: [roteiro e requisitos](uso-compartilhado.md#roteiro-com-dois-operadores),
   conta ADMIN inicial por `GerarHashSenha` interativo e hash externo; cadastrar
   OPERADOR/CONSULTA no painel. Usar dois perfis distintos, não duas abas do mesmo.
3. Instalação central: [procedimento](uso-compartilhado.md#implantação-centralizada),
   uma máquina/serviço Java 25, HTTPS e usuários individuais. Apenas backend abre
   H2; bancos/acervo privados e exportação separada fora do checkout.
4. Atualização: parar escritores, inventariar cada base offline, criar e verificar
   backup. Revisar impedimentos e autorizar separadamente migração necessária até
   V15. Substituir o JAR inteiro; não sobrepor estáticos. Iniciar `operacao` com
   Flyway desligado, validate e IFEXISTS/WRITE_DELAY=0. Conferir API/versão, login,
   saúde e ZIP sintético antes de liberar acesso dos PCs.
5. Backup/restauração: [CLI e evidências separadas](etapa4-banco-migracoes.md#backup-e-restauração).
   Backup inclui banco e acervo, não substitui política externa nem prova de
   restauração. Testar em destino vazio, sem substituir o banco em uso; conferir
   caminhos persistidos, vínculos/históricos e bytes. Encerrar pelo serviço ou
   Ctrl+C e aguardar processo antes de manutenção. Fechar aba não cancela efeitos.
6. Gmail: [configuração e autenticação seguras](gmail-configuravel.md). Envio real
   exige configuração externa, conta permitida e confirmação específica; salvar
   o endereço ou ter teste local aprovado não homologa SMTP/entrega.

## Pendências indispensáveis e Git

O aceite local não equivale à implantação na empresa. Restam provisionamento
central/HTTPS, conta Gmail e teste externo especificamente confirmado, política
de backup e decisão sobre faturas entre clientes. Não há autorização para migrar
a base operacional ou abrir portas nesta revisão.

Commits e situação remota são informados na entrega final, após revisão do diff.
O commit local inicial e o histórico publicado devem permanecer intactos. Esta
tarefa pede commits; não há pedido explícito de push, portanto publicação remota
não deve ser presumida.
