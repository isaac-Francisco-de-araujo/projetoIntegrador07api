# Diagnóstico por arquivo e recuperação manual de ZIP

**Etapa atual:** veja [Diagnóstico e Recuperação — V10](diagnostico-recuperacao.md).
A V8 já persistia os processamentos; a V10 reúne os históricos existentes em uma
Central paginada, acrescenta correlação, recuperação e resolução comprovada.
As seções de retenção e os resultados V7 abaixo conservam seu contexto histórico.

## O que está disponível

A conferência do ZIP mostra uma operação identificada por UUID, nome do pacote,
contagens, localização de origem e diagnóstico individual de cada arquivo examinado.
Erros locais de conteúdo (XML inválido, PDF sem cabeçalho reconhecido, arquivo vazio
ou nCT duplicado) não impedem a inspeção segura das outras entradas. Eles continuam
bloqueando **toda** a importação. Não existe importação parcial implícita.

O painel mantém o resultado em uma seção própria, com texto e ícones, filtros
**Todos os arquivos / Erros / Pendências**, revisão das associações e botão
**Conferir novamente o pacote selecionado**. O diagnóstico anterior permanece
visível enquanto a nova requisição está em andamento. Trocar o arquivo selecionado
descarta imediatamente os resultados e vínculos antigos. Respostas de requisições
concluídas não habilitam a importação de outro arquivo.

O caminho completo no computador do operador não é conhecido pelo navegador.
O painel não oferece abertura de caminhos do servidor nem editor de arquivos.

## Localizações e estados

- `pacote` é o nome simples do ZIP enviado, sem diretório do computador de origem.
- `arquivo`/`nomeArquivo` são nomes simples; `caminhoInterno` distingue, por exemplo,
  `cobrancas/fatura.pdf` de `outra-viagem/fatura.pdf`. Os IDs locais são diferentes.
  Nenhum vínculo é deduzido desses nomes.
- O painel apresenta `documentos.zip > cobrancas/fatura.pdf`, explicitamente
  rotulado como localização **no ZIP**. Isso não é um caminho publicado no servidor.
- Antes de importar, informa **nenhum documento publicado no armazenamento definitivo**.
  O temporário de leitura é removido ao terminar a requisição; quando não chegou
  a ser criado, isso é informado. Se a remoção falhar, a resposta indica limpeza
  pendente, sem expor o caminho; o fallback existente é remoção no encerramento
  do processo. Falha persistente exige revisão administrativa, não retenção como
  documento de negócio ou pasta de importações aguardando associação.
- Após confirmação integral, o painel mostra localização lógica: ID do pacote,
  viagens e quantidades de documentos/anexos. Nenhum caminho físico é exposto.
- Em rollback, os arquivos são `NAO_IMPORTADO` e o arquivo identificado na falha
  é `ERRO`. O relatório preserva a origem no ZIP mesmo após remover o temporário.
  O protocolo existente compensa as publicações próprias. Como a compensação pode
  deixar resíduos em falha de limpeza, o diagnóstico não promete que todo journal
  foi apagado: orienta a reconciliação e não oferece destino definitivo inexistente.
- Resultado transacional não confirmado é `DESCONHECIDO`, com revisão obrigatória
  e `novaTentativa=false`; não é apresentado como falha comprovada. Consultar o
  acervo e a reconciliação precede uma decisão de nova confirmação.

Estados da inspeção: `CONFERIDA`, `ASSOCIACAO_PENDENTE`,
`CONFERENCIA_COM_PENDENCIAS` ou `INTERROMPIDA`. Arquivos XML conferidos são
`VALIDO`; PDFs com cabeçalho reconhecido ficam `PENDENTE` até a escolha manual.
No painel, uma associação completa vira `ASSOCIADO`, mas só é revalidada e
persistida na confirmação. Contagens são disjuntas: válidos, inválidos e pendentes.
Problemas do pacote inteiro também são listados, sem inventar um arquivo culpado.

## Limites de segurança e PDF

São preservados: ZIP até 20 MiB, 100 entradas (inclusive diretórios), 20 MiB por
arquivo, 100 MiB descomprimidos no total, limite de compressão 100:1 acima de
1 MiB, CRC, recusa de caminhos inseguros/ambíguos e de tipos fora de XML/PDF.
Corrupção, proteção do ZIP, erro na leitura da entrada ou limite/segurança
interrompem o pacote. Os arquivos examinados antes da interrupção continuam
identificados, mas não autorizam importação. As entradas restantes não são
marcadas como válidas. `naoExaminados` conta entradas restantes, inclusive
diretórios; `-1` significa que não foi possível determinar a quantidade.

**PDF: somente o cabeçalho `%PDF-` é verificado.** Não há biblioteca de leitura
estrutural, interpretação comercial/fiscal, detecção de senha do PDF, leitura
completa ou execução de conteúdo incorporado. Um PDF com cabeçalho reconhecido
mas estrutura danificada ou proteção interna pode passar por esta verificação.
O painel declara essa limitação e pede revisão do original. O ZIP com senha é
recusado quando não pode ser aberto/lido pelo leitor; não há tentativa de senha.

O XML usa o extrator CT-e 4.00 existente, com proteções contra entidades externas
e validações de identidade. Não equivale a XSD, assinatura ou consulta à SEFAZ.
Erros públicos são mensagens fixas, sem texto bruto do parser. O parser também
não imprime seus diagnósticos privados no stderr durante essas validações.

## API e diagnóstico estruturado

`POST /api/pacotes/inspecao` continua recebendo multipart `arquivo`.
Sucesso HTTP 200 retorna `sha256` e `arquivos` como antes, acrescentando os campos
abaixo. Em conteúdo inválido ou interrupção, HTTP 400 mantém o envelope de erro
e acrescenta `diagnostico`, que contém o mesmo formato de relatório. Clientes
devem ler esse campo também em respostas não-2xx; o painel já faz isso.

```json
{
  "operacaoId": "<UUID da operação>",
  "pacote": "documentos.zip",
  "sha256": "<SHA-256 do ZIP, se lido>",
  "arquivos": [],
  "situacao": "CONFERENCIA_COM_PENDENCIAS",
  "conteudoValido": false,
  "interrompida": false,
  "validos": 2,
  "invalidos": 1,
  "pendentes": 2,
  "naoExaminados": 0,
  "localizacao": "Nenhum documento publicado no armazenamento definitivo. Temporário de leitura removido após a conferência.",
  "diagnosticos": [{
    "arquivoId": "a2",
    "arquivo": "fatura.pdf",
    "caminhoInterno": "cobrancas/fatura.pdf",
    "etapa": "VALIDACAO_PDF",
    "codigo": "PDF_NAO_RECONHECIDO",
    "mensagem": "Arquivo não reconhecido como PDF pelo cabeçalho %PDF-.",
    "gravidade": "ERRO",
    "situacao": "INVALIDO",
    "novaTentativa": true,
    "acaoSugerida": "Abra o arquivo original, obtenha uma cópia válida e refaça a conferência com o pacote corrigido."
  }]
}
```

O exemplo abrevia as listas: em uma resposta real, `arquivos` contém os metadados
dos arquivos reconhecidos e `diagnosticos` contém também os demais examinados.
`conteudoValido=true` não dispensa as associações nem significa importação.
No contrato ampliado, `arquivos[].nomeArquivo` é nome simples e
`arquivos[].caminhoInterno` é o caminho original no ZIP. Isso permite guardar
nomes seguros para a preparação de e-mail também em pacotes com subdiretórios.
Cadastros já existentes não são renomeados ou migrados por esta alteração.

`POST /api/pacotes/confirmacao` mantém multipart e o contrato de seleção anterior.
Relê o ZIP e aplica os mesmos bloqueios, mesmo se o consumidor ignorar a tela.
Falta de associação retorna HTTP 400 com arquivo/etapa diagnosticados. Conflitos
retornam 409; banco ocupado, 503; falhas internas/armazenamento, 500, com mensagem
tratada. Erros de contrato HTTP anteriores à leitura (parte ausente, JSON inválido,
requisição acima do limite multipart) continuam no envelope padrão quando não há
um arquivo identificável. O painel mantém a conferência anterior e bloqueia a
confirmação se não conseguir obter um novo diagnóstico.

Principais códigos estáveis:

| Grupo | Códigos |
| --- | --- |
| Upload/leitura | `ZIP_INVALIDO`, `FALHA_LEITURA`, `ZIP_CORROMPIDO_OU_PROTEGIDO`, `CRC_DIVERGENTE` |
| Segurança/limites | `CAMINHO_INSEGURO`, `NOME_AMBIGUO`, `TIPO_NAO_PERMITIDO`, `LIMITE_ZIP`, `LIMITE_ENTRADAS`, `LIMITE_ARQUIVO`, `LIMITE_DESCOMPRESSAO`, `TAXA_COMPRESSAO_EXCEDIDA` |
| Conteúdo | `ARQUIVO_VAZIO`, `XML_INVALIDO`, `PDF_NAO_RECONHECIDO`, `NCT_DUPLICADO`, `CTE_VALIDO_AUSENTE` |
| Revisão | `ASSOCIACAO_PENDENTE`, `ASSOCIACAO_INVALIDA` |
| Confirmação | `CONFLITO_VERSAO_OU_SELECAO`, `CONFLITO_PERSISTENCIA`, `CONFLITO_ARMAZENAMENTO`, `FALHA_ARMAZENAMENTO`, `FALHA_CONFIRMACAO`, `BANCO_OCUPADO`, `RESULTADO_IMPORTACAO_DESCONHECIDO`, `IMPORTACAO_NAO_CONFIRMADA` |

Não se tenta distinguir corrupção de senha quando a biblioteca não fornece
evidência confiável: nesses casos o código é explicitamente combinado.
Quando não é possível identificar o arquivo, o relatório usa item de pacote,
sem atribuir uma falha arbitrariamente a uma entrada.

## Recuperação manual e preservação

1. Gere um ZIP sintético com `src/main/resources/email/cte-sintetico.xml` e um
   arquivo de texto chamado `cobrancas/fatura.pdf`, cujo conteúdo seja `invalido`.
2. Selecione esse ZIP em **Importar ZIP** e clique em **Conferir ZIP**.
3. Consulte a linha `PDF_NAO_RECONHECIDO`, seu caminho e a orientação. Use o filtro
   **Erros**; **Confirmar importação** permanece bloqueado. Nada é importado.
4. Abra a cópia original no computador, obtenha o PDF correto no sistema de
   origem e gere um **ZIP corrigido**. Não basta renomear um conteúdo inválido.
5. Selecione o ZIP corrigido: o resultado e os vínculos anteriores são descartados.
   Confira novamente, selecione finalidade/viagens de **cada** PDF e confirme.
6. Consulte os documentos/viagens. Uma confirmação bem-sucedida inclui o pacote
   inteiro; se falhar, consulte o relatório e não suponha que a parte válida entrou.

Não foi implementada substituição individual nem edição no servidor. Corrigir
metadados/vínculos de um pacote já importado continua sujeito às proteções de
identidade: a mesma chave com bytes divergentes não substitui XML; o mesmo ZIP
com associações diferentes é recusado. PDFs corrigidos em novo pacote preservam
os anteriores, que o operador pode arquivar/restaurar conforme as regras atuais.
Não há disparo ou repetição de e-mail ao corrigir uma importação.

## Retenção e histórico

**Este diagnóstico não é histórico permanente.** Fica na resposta HTTP e na
memória da página enquanto o arquivo está selecionado. Filtros não o apagam;
nova conferência substitui o resultado, e trocar pacote/recarregar/fechar a página
o descarta. UUIDs de operações não possuem endpoint de consulta posterior.
Mensagens e metadados são limitados: até 100 entradas, nomes/caminhos até 255
caracteres e textos públicos fixos; conteúdo dos arquivos e stack traces não
fazem parte do diagnóstico. Não são armazenadas cópias do ZIP à espera da correção.

Não há nova migração: permanece o esquema V7. Histórico de envio em `envios` e
`tentativas_email` é separado e não recebe falhas de importação. Persistência
de diagnósticos, encadeamento durável de correções e consulta entre sessões ficam
para outra etapa, com migração e política de retenção próprias.

## Verificação

Os testes usam apenas dados sintéticos, bancos e diretórios temporários. Veja
os comandos e os resultados desta entrega no README. A ferramenta
`scripts/testar-envio-manual.cjs` foi ampliada para cobrir o diagnóstico, filtros,
reconferência sem perder a identificação e troca por pacote corrigido, além
das verificações anteriores de envio simulado.

Execução final: **276 testes JUnit, 274 aprovados, zero falhas de asserção,
dois erros de ambiente e nenhum ignorado**. Continuam os dois erros preexistentes
neste Windows: `MigracaoEBackupTest.ferramentaGeraInventarioSomenteDeLeituraForaDoAcervo`
(permissões POSIX indisponíveis) e `ProtocoloArquivosTest.recusaAreaTemporariaComoLinkSimbolico`
(processo sem privilégio para criar symlink). Não se declara suíte completa verde.

Os **16 casos de `DiagnosticoPacoteTest` e 25 de `PacoteAssistidoHttpTest`** passaram.
Cobrem nome/caminho/etapa/situação, continuidade segura, CRC/proteção, XXE sem
expor conteúdo, arquivo inválido, PDF limitado a cabeçalho, nomes iguais em
diretórios diferentes, limite total de 100 MiB e limites individuais/taxa,
associação obrigatória, bloqueio da API, pacote corrigido, rollback em persistência
e publicação preservando original e distinção do resultado desconhecido.

**10 verificações Playwright/Chrome aprovadas**, incluindo diagnóstico visível,
filtros, reconferência preservando o resultado durante a requisição, descarte do
resultado ao trocar ZIP, associação manual, importação integral e regressão do
envio simulado com timeout/reenvio/reinício. Sem erro JavaScript; desktop e celular
sem overflow. Empacotamento e `node --check` aprovados.

Comandos executados (build externo para preservar o `target/` histórico):

```bash
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-diagnostico-build clean test
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-diagnostico-build -DskipTests package
NODE_PATH="C:/Users/florb/AppData/Local/Temp/opencode/node_modules" node scripts/testar-envio-manual.cjs "C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-diagnostico-build/rota-brasil-0.0.1-SNAPSHOT.jar" "C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-diagnostico-ui"
```

Playwright deve estar instalado na área de ferramentas; é dependência apenas
do teste de navegador. JAR e relatórios JUnit ficam em `rotabrasil-diagnostico-build`;
JSON de resultados, logs sintéticos e capturas `erro-pdf.png`, `desktop.png` e
`mobile.png` ficam em `rotabrasil-diagnostico-ui`, ambos sob a área temporária
externa indicada acima. Os servidores de teste são encerrados ao finalizar.
Nenhum banco/documento operacional foi alterado e nenhum e-mail real foi enviado.
