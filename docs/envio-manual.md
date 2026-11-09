# Envio manual auditável em SIMULAÇÃO

**Interface atual (03/11/2026):** [uso diário simplificado](uso-diario-simplificado.md).
Sem transporte real configurado, o fluxo cotidiano mostra **Envio não configurado**
e mantém a ação indisponível. Históricos identificam testes sem envio. Os contratos
backend e as evidências históricas V7 descritos abaixo foram preservados.

**Evolução atual:** o [README](../README.md#envio-de-e-mail) descreve o adaptador SMTP
documental real já implementado, com ativação/configuração externas. A
[V9](configuracoes-exportacao-notificacoes.md) acrescenta remetente da operação e
notificações somente simuladas, separadas do destinatário e dos anexos do cliente.
Os resultados e limitações de implementação V7 abaixo conservam seu contexto histórico.

## Fluxo atual (V7)

O painel existente em `/` permite importar ZIP, associar PDFs manualmente,
selecionar uma viagem, destinatário e anexos, conferir e **Simular envio**.
Não há geração de documentos fiscais, faturas ou boletos. Outlook e recebimento
automático de ZIP permanecem fora desta etapa. A CLI Gmail continua separada,
limitada ao seu anexo sintético; não é o transporte deste fluxo.

1. Escolha o ZIP e confira os CT-es, identificados por `nCT`.
2. Associe cada PDF explicitamente. Fatura pode ter vários vínculos, sem quantidade
   fixa de PDFs ou inferência pelo nome/posição.
3. Selecione uma viagem, os documentos ativos e informe o destinatário manualmente.
4. Gere a prévia: o servidor verifica arquivos e persiste a conferência por 30 minutos.
5. Confira CT-e/viagem, destinatário, nomes, tipos, tamanhos e faturas compartilhadas.
   Marque a confirmação e clique em **Simular envio**.
6. Consulte a tentativa, resultado tratado e snapshot em **Tentativas manuais**.
   O histórico legado continua disponível em seção própria, sem reconstruir dados ausentes.

Alterar destinatário, viagem ou anexos invalida a confirmação no painel. A API
de confirmação recebe somente o ID da conferência e `confirmado: true`; não
aceita destinatário ou seleção substitutos. Antes de registrar a tentativa,
revalida seleção, vínculos, estado ativo, arquivos, SHA-256 e metadados. Mudanças
desde a conferência exigem nova prévia. Uma falha de validação não cria tentativa
de transporte. Os bytes usados são cópias privadas em memória dos bytes revalidados.

Limites configuráveis em bytes: `rotabrasil.email.limite-arquivo-bytes` (20 MiB)
e `rotabrasil.email.limite-total-bytes` (100 MiB). Nomes inseguros, caminhos fora
da raiz, links simbólicos, arquivo ausente/vazio, hash ausente/divergente são
recusados pela preparação existente. Esses limites não incluem overhead MIME.

### API nova

Todas as rotas abaixo ficam sob `/api/viagens/{viagemId}`:

- `POST /conferencias-email` com
  `{"selecao":{"destinatario":"operador@example.invalid","documentoIds":[1],"anexoIds":[2]},"anteriorId":null}`.
  Retorna `id`, `modo`, `expiraEm`, `anteriorId`, `snapshot`.
- `POST /tentativas-email` com `{"conferenciaId":"<id retornado>","confirmado":true}`.
  Retorna a tentativa criada ou a tentativa já existente dessa mesma conferência.
- `GET /tentativas-email` lista o histórico novo da viagem.
- `GET /tentativas-email/{id}` consulta uma tentativa e seu snapshot.

O snapshot preserva viagem/`nCT`, destinatário efetivo, origem e IDs dos anexos,
nomes, tipos/finalidades, viagens vinculadas, SHA-256 e tamanhos. Não inclui
caminhos físicos, bytes ou credenciais. A tentativa acrescenta UUID, modo,
situação, motivo tratado, timestamps UTC e referência à tentativa anterior.
Mudanças posteriores no cadastro não reescrevem esse retrato.

### Idempotência, concorrência e reenvio

A conferência é também a chave idempotente persistida. A coordenação usa a mesma
linha de banco `SELECT ... FOR UPDATE` de importação/arquivamento, com transação
READ_COMMITTED e unicidade de `tentativas_email.conferencia_id`. Repetições
simultâneas retornam a tentativa existente, inclusive enquanto o transporte está
em execução. Essa consulta acontece antes de revalidar arquivos ou validade da
conferência, permitindo recuperar a resposta após timeout ou alterações posteriores.

Outra conferência não contorna a proteção: depois da primeira tentativa da
viagem, toda tentativa seguinte exige `anteriorId` igual ao da tentativa mais
recente e nova conferência. Isso também cobre correções de documentos/destinatário.
No painel, **Preparar reenvio confirmado** inicia essa ação separada; é necessário
conferir e confirmar novamente. Conferências concorrentes da mesma referência
não criam dois reenvios. Não há repetição automática, fila ou agendador.

Uma fatura compartilhada aparece uma vez na seleção. Uma confirmação representa
uma tentativa para uma viagem de referência e um destinatário explícitos. Não
cria tentativas para as outras viagens. O operador pode iniciar uma ação manual
em outra viagem; não há deduplicação comercial global por PDF/destinatário.

Após timeout/perda de conexão, use **Consultar/repetir a mesma confirmação**,
ou consulte o histórico. O painel guarda em `sessionStorage` apenas IDs de
conferência/viagem para recuperação após recarga, sem contatos ou nomes de arquivos.
O timeout HTTP do painel é 30 segundos e não cancela uma tentativa já registrada.

### Estados e interrupções

| Situação | Significado |
| --- | --- |
| `SIMULADO` | Simulação concluída, sem comunicação SMTP e sem e-mail real enviado |
| `FALHA` | Transporte informou recusa comprovada; não há aceite confirmado |
| `DESCONHECIDO` | Resultado ainda não confirmado ou interrompido; exige revisão humana |
| `ACEITO_SMTP` | Reservado no esquema para transporte futuro; não é produzido hoje e não comprova entrega/leitura |

`modo` é sempre `SIMULADO` nesta implementação. A tela identifica **SIMULAÇÃO**
também em falha/resultado desconhecido. O transporte padrão não abre rede.
`rotabrasil.email.modo=REAL` (ou qualquer valor diferente de `SIMULADO`) impede
a inicialização: não existe opção que habilite SMTP operacional nesta entrega.

A tentativa é gravada inicialmente como `DESCONHECIDO`, com `concluidaEm=null`,
e commit **antes** do transporte. A chamada não admite transação externa aberta.
Resultado é atualizado em outra transação. Queda entre commit, chamada e gravação
do resultado mantém o estado conservador; nenhuma retomada automática ocorre.
Mesmo após reiniciar, repetir a mesma confirmação não chama o transporte novamente.
Exceção genérica do transporte resulta em desconhecido, nunca em falha presumida.
Não são salvos/logados texto, causa ou resposta privada da exceção.

Não existe promessa de “exatamente uma vez” entre banco e SMTP. Inclusive uma
queda antes da chamada pode deixar uma tentativa desconhecida que nunca comunicou
com o transporte; é a escolha conservadora para evitar duplicação após incerteza.

### Banco, implantação e transporte real

`V7__tentativas_email_manuais.sql` adiciona conferências e tentativas, preservando
`envios` e todas as migrações anteriores. Inventário/fingerprint/backup incluem
as tabelas novas; vínculos impedem excluir viagens referenciadas. Nenhuma migração
operacional foi executada. Banco existente exige autorização específica, parada
de escritores, inventário e backup atual verificado, conforme a Etapa 4.
Flyway permanece desativado por padrão e Hibernate usa `validate`.

Para envio real ainda faltam implementação do adaptador operacional, remetente,
configuração segura externa, TLS obrigatório e validação da identidade do servidor,
timeouts, ativação explícita e controle de acesso/permissões dos operadores.
O adaptador deverá preservar os resultados recusado/desconhecido e distinguir
aceite SMTP de entrega/leitura. Não reutilizar a CLI Gmail como atalho.

### Experimentar com banco isolado

Em PowerShell, Java 25/Maven, a partir do repositório:

```powershell
$build = Join-Path $env:TEMP 'opencode/rotabrasil-envio-build'
mvn -B "-Drotabrasil.build.directory=$build" clean test
mvn -B "-Drotabrasil.build.directory=$build" -DskipTests package
$demo = Join-Path $env:TEMP ('rotabrasil-demo-' + [guid]::NewGuid())
java -jar "$build/rota-brasil-0.0.1-SNAPSHOT.jar" --server.port=18083 "--spring.datasource.url=jdbc:h2:file:$demo/banco" --spring.flyway.enabled=true --spring.h2.console.enabled=false "--rotabrasil.arquivos.viagens=$demo/viagens" --rotabrasil.email.modo=SIMULADO
```

Antes de iniciar, confira os processos e a disponibilidade da porta 18083;
inicie apenas uma instância para essa demonstração. Abra `http://localhost:18083/`.
Pode importar `src/main/resources/email/cte-sintetico.xml`. A base e os documentos
da demonstração ficam na pasta temporária escolhida. Nenhum segredo é necessário.

### Verificação desta etapa

Build limpo final: **259 testes executados, 257 aprovados, zero falhas de asserção,
dois erros de ambiente e nenhum ignorado**. Os dois erros preexistentes neste
Windows continuam sendo:

- `MigracaoEBackupTest.ferramentaGeraInventarioSomenteDeLeituraForaDoAcervo`:
  `getPosixFilePermissions` não é suportado pelo sistema de arquivos Windows.
- `ProtocoloArquivosTest.recusaAreaTemporariaComoLinkSimbolico`: o processo não
  tem privilégio para criar o link simbólico usado pelo teste.

Não se declara a suíte completa verde neste ambiente. Os **12 testes HTTP de
envio manual**, **12 de preparação** e os casos novos/atualizados de migração V7,
fingerprint, backup e restauração passaram. Há cobertura de simulação, recusa,
resultado desconhecido explícito e por exceção, falha ao persistir o resultado,
destinatário inválido, arquivamento, arquivo ausente/alterado, vínculo inválido,
limites configurados, metadados alterados, confirmação modificada/expirada,
concorrência da mesma e de diferentes conferências, reenvio, histórico legado
e fatura compartilhada sem tentativas adicionais.

Empacotamento: `mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-envio-build -DskipTests package` aprovado.
`node --check` nos scripts JavaScript também aprovado.

Navegador Playwright/Chrome: **7 verificações aprovadas**, sem erro JavaScript:
ZIP/associação manual, invalidação por destinatário, invalidação por anexos,
resposta perdida recuperada idempotentemente, reenvio vinculado/recarga,
persistência após encerramento/reinício do servidor e layout desktop/móvel.
Artefatos sintéticos externos em
`C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-envio-ui-final` e relatórios
JUnit sob `rotabrasil-envio-build/surefire-reports`, na mesma área temporária.
Os servidores do teste foram encerrados. Nenhum e-mail real foi enviado e
nenhum banco/documento operacional foi usado nos testes.

O script
`scripts/testar-envio-manual.cjs` usa Node com Playwright e Chrome, cria ZIP
sintético, banco/arquivos isolados e porta aleatória, encerra o servidor e verifica
reinicialização. Uso: `node scripts/testar-envio-manual.cjs CAMINHO_JAR PASTA_EXTERNA`.
Playwright é dependência apenas da ferramenta de testes, não do painel.

---

## Registro histórico: preparação interna anterior à V7

O texto abaixo conserva o diagnóstico e os resultados do bloco anterior. As
pendências de confirmação, auditoria e painel ali descritas são substituídas
pelo fluxo V7 acima; os números de testes correspondem à execução anterior.

## Estado conferido

O checkout examinado está em `9812f9cf5a84d5c8b672e9d58a6841ae48c58873`.
Não contém `docs/visao-geral-do-sistema.md` nem o diagrama mencionado na referência
`a1d9275`. Essa referência precisa ser conciliada antes de atualizar seus arquivos.

## Bloco 1: preparação interna e fronteira de transporte

`PreparacaoEnvioService` é uma base interna, sem endpoint ou botão de envio.
Recebe viagem, destinatário e IDs explicitamente selecionados, usando as mesmas
validações da prévia. Obtém a coordenação do banco em READ_COMMITTED durante a
conferência, compartilhada com importação e arquivamento. Não chama transporte.

Além de conferir existência, estado ativo e vínculos, lê cada arquivo e compara
SHA-256 com o cadastro. Arquivo ausente, hash não cadastrado/divergente, caminho
fora da raiz, link simbólico ou nome inseguro impedem a preparação. A leitura é
limitada a 20 MiB por arquivo e 100 MiB por seleção; limites são da preparação,
não garantia de aceitação de uma mensagem MIME por um provedor futuro.

O resultado é um snapshot privado **em memória**, com destinatário, viagem,
origem/ID, nome, hash e cópias defensivas dos bytes. Alterações posteriores no
arquivo ou cadastro não alteram esse objeto. Uma fatura compartilhada selecionada
aparece uma vez, independentemente da quantidade de vínculos. IDs repetidos são
recusados. Isso não evita enviar a mesma fatura em duas tentativas distintas.

`TransporteEmail` separa o transporte da preparação. Seus resultados distinguem
ACEITO, RECUSADO e DESCONHECIDO. Aceite não comprova entrega/leitura. O único bean
de produção é `TransporteEmailDesativado`, que bloqueia a chamada. Não há opção
de configuração para habilitar SMTP. Testes usam transporte simulado, sem rede.

O snapshot não é auditoria persistente e não sobrevive ao encerramento do
processo. Não deve ser exposto em JSON, logs ou usado como autorização de envio.
A prévia pública continua consultando apenas metadados, sem preparar bytes.
Banco continua exigindo V6; este bloco não acrescenta migrações.

## Próximos blocos independentes

1. Criar migração nova para tentativas e snapshots persistentes de destinatário,
   anexos, vínculos, hashes, datas e resultado tratado. Incluir essas tabelas no
   inventário/fingerprint/backup antes de autorizar migração operacional.
2. Registrar tentativa antes do transporte; usar chave idempotente e coordenação
   no banco. Commit antes da chamada externa. Persistir resultado em outra
   transação. Interrupção após iniciar transporte deve exigir revisão, sem
   repetição automática. Preservar o conjunto efetivamente enviado.
3. Definir confirmação de reenvio vinculada à tentativa anterior, inclusive
   duplicidade de fatura compartilhada entre viagens e seleção alterada.
4. Adicionar API e painel para confirmação e consulta da nova auditoria,
   preservando o histórico legado sem inventar seus destinatários/anexos.
5. Só habilitar transporte operacional após confirmar remetente, acesso dos
   operadores e configuração segura; validar TLS e identidade do servidor.

Ainda precisam de resposta operacional: unidade de envio; destinatário de fatura
compartilhada e possibilidade de clientes diferentes; fonte dos contatos;
remetente; permissões de confirmação/correção/reenvio e destinatários dos avisos.
Recebimento automático e Outlook não são necessários para testar esses blocos.

## Verificação deste bloco

Java 25 e Maven, build limpo externo para preservar o `target/` histórico:

```sh
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-envio-build clean test
```

Resultado atual: **245 executados, 243 aprovados, zero falhas de asserção e
dois erros**, sem ignorados. Os 11 testes novos de `PreparacaoEnvioTest` passaram.
Os erros existentes foram `MigracaoEBackupTest.ferramentaGeraInventarioSomenteDeLeituraForaDoAcervo`
(permissões POSIX indisponíveis no Windows) e
`ProtocoloArquivosTest.recusaAreaTemporariaComoLinkSimbolico`
(Windows sem privilégio para criar symlink). A suíte completa não ficou verde
neste ambiente; essas verificações precisam de ambiente compatível.

Não foram executadas verificações de navegador, pois não houve mudança de tela.
Persistência de tentativas, falha de transporte orquestrada, idempotência e
reenvio confirmado ainda não estão cobertos por este bloco. Os testes usam dados
sintéticos e os bancos isolados da suíte. Nenhum envio real foi executado.
