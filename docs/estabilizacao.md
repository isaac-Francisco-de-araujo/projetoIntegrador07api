# Estabilização antes da Etapa E-mail — 31/10/2026

**Leitura atual (09/11/2026):** este documento conserva evidências históricas V10.
O código atual exige V14 e já possui login individual. A regra comercial atual é
envio de documentos/fatura ao cliente pela conta Outlook da empresa; confirmação
ao motorista é opcional, sem endereço obrigatório. Consulte a
[revisão operacional](revisao-operacional-20261109.md) para reprodução ZIP atual,
testes, preparação centralizada e limites de homologação.

## Resultado e escopo

O fluxo manual foi exercitado com backend e frontend reais: configuração,
conferência de ZIP sintético, associação explícita, importação, exportação,
recuperação, tentativa SIMULADA e persistência após reinício. Nenhum banco
operacional, documento real ou credencial foi alterado. O Git estava limpo no início.
Todos os builds e dados de teste ficaram fora do checkout; `target/` histórico foi preservado.

O operador baixa o ZIP manualmente e o importa. PDFs e fatura são associados por
conferência. A cópia adicional pertence ao computador do backend, fora do checkout
e separada do acervo. A **Etapa E-mail posterior** enviará confirmação real de
recebimento ao motorista usando a conta configurada. Salvar o endereço não ativa SMTP.
Leitura automática do Outlook e criação de login não são requisitos desta entrega.

## Investigação dos dois “Recurso não encontrado”

Na inspeção inicial não havia processo Java nem listener na porta 8080. Não é
possível atribuir retrospectivamente um PID, cache ou hash ao navegador que exibiu
o problema original. Foram encontrados inicializadores locais anteriores que
executavam JARs antigos e sobrepunham os recursos por:

```text
--spring.web.resources.static-locations=file:<checkout>/src/main/resources/static/
```

O inicializador anterior de atualização apontava para `rotabrasil-pdf-build`.
Esse artefato não contém `ConfiguracaoOperacaoController` nem
`DiagnosticoOperacaoController`. A reprodução usou esse JAR, banco **novo isolado**,
porta aleatória e os estáticos atuais. Requisições registradas nesta execução:

| URL exata da reprodução | Método | HTTP | Endpoint atual correspondente |
| --- | --- | --- | --- |
| `http://127.0.0.1:61952/api/configuracoes` | GET | 404 | `ConfiguracaoOperacaoController.consultar` |
| `http://127.0.0.1:61952/api/pendencias?pagina=0&tamanho=20&filtro=PRECISA_ACAO` | GET | 404 | `DiagnosticoOperacaoController.listar` |
| `http://127.0.0.1:61979/api/configuracoes` | GET | 200 | Mesmo contrato, JAR atual |
| `http://127.0.0.1:61979/api/configuracoes/testes` | GET | 200 | `ConfiguracaoOperacaoController.testes` |
| `http://127.0.0.1:61979/api/pendencias?pagina=0&tamanho=20&filtro=PRECISA_ACAO` | GET | 200 | Mesmo contrato, JAR atual |

As portas são desta reprodução, não portas fixas de implantação. O frontend usa
URLs absolutas da mesma origem (`/api/...`), sem outra URL-base ou servidor frontend.
Não foi encontrada divergência de rota entre fonte Java atual e JavaScript atual.
O problema reproduzido é **divergência de versões em execução**, não ausência de
implementação dos endpoints. Cache não foi comprovado como causa do episódio original.

### Identificação e correção da execução

- Executar o JAR novo com os estáticos **do próprio JAR**, sem sobreposição do checkout.
- `GET /api/versao` informa versão Maven, instante de build, contrato e hashes dos
  recursos estáticos empacotados. No perfil protegido, exige a autenticação das consultas.
- O painel verifica o contrato antes da inicialização e mostra **Backend desatualizado**
  para backend sem esse endpoint ou **Versões incompatíveis** para contrato divergente.
  Não converte erro em dados vazios e permite abrir o diagnóstico com rota/status.
- HTML/JS/CSS usam `Cache-Control: no-store`. Após substituir a aplicação, recarregar
  a aba já aberta; a execução de módulos de uma aba antiga não é atualizada em memória.
- O teste comparou os bytes HTTP aos hashes fornecidos pelo JAR.

Identificação da entrega testada:

```text
Maven:       0.0.1-SNAPSHOT
Spring Boot: 3.5.6
Contrato:    estabilizacao-V10-20261030
Build:       2026-10-31T03:32:38Z
JAR SHA-256: 647a43d93389649e87612d6028da1291ef1b3d892884ffd63ca9b6f5bf358b57
```

O marcador no HTML identifica a revisão; hashes permitem distinguir builds que
compartilham o mesmo `SNAPSHOT`. `versao.json` na saída externa contém os hashes
individuais dos estáticos efetivamente consultados. Recompilar altera o build/hash.

## Correções no frontend

- Sidebar: um contador de **falhas + atenção**, `99+` acima de 99. Andamento e
  resoluções ficam separados dentro da tela. Indisponibilidade é um único `!`
  com nome acessível, sem zero falso, `?` repetidos, cortes ou sobreposição.
  A regra antiga `nav a span` também atribuía largura de ícone e fonte maior ao
  novo rótulo textual; o rótulo agora tem largura automática e fonte herdada.
  A verificação final mede os caracteres renderizados com `Range`, incluindo
  800/1024px, em vez de considerar apenas a caixa CSS do rótulo.
- Configurações e Processamentos: GET malsucedido apresenta erro vermelho com
  título específico e **Tentar novamente**. A orientação não depende de abrir a
  própria Central quando ela está indisponível. Não se presume problema de esquema.
- Resultado não confirmado fica reservado para alterações com resposta perdida,
  timeout, corpo inválido ou falha de servidor potencialmente incerta. Recusas
  conhecidas e pendências de processamento mantêm seus diagnósticos específicos.
- Cancelamento explícito informa **espera interrompida no navegador**, sem afirmar
  que o backend interrompeu a operação. Uma falha de GET não recebe esse tratamento.
- Referência, código, rota, status e detalhes técnicos ficam em **Ver detalhes**.
  Diagnósticos por arquivo conservam nome, localização, etapa, causa, impacto e recuperação.
- Teste de envio ampliado atualizado para os cartões V10 existentes, em vez dos
  botões de histórico anteriores. O modo `--somente-local` verifica o escopo local;
  sem essa opção o cenário protegido continua sendo exigido e falha se não funcionar.

## Pasta externa e backend

Configure `ROTABRASIL_EXPORTACAO_RAIZ` ou `--rotabrasil.exportacao.raiz` com uma
pasta externa existente, por exemplo `C:/RotaBrasil/Exportacoes`. Não use
`<checkout>/exportacoes` nem uma raiz que contenha o acervo. Nos exemplos do README,
`$Demo/exportacoes` é externa e irmã de `$Demo/viagens`.

`DestinoExportacaoService` agora recusa sobreposição com checkout identificado nos
ancestrais do diretório de execução e da origem do JAR/classes. A inicialização
informa **Configuração incompatível**; não move uma configuração existente para
outro lugar. Execute a partir do repositório conforme o README durante desenvolvimento.
O teste unitário verifica a recusa sem criar a pasta e a aceitação de raiz externa.

A API continua aceitando somente nomes controlados de subpastas. Não foram alterados
os contratos de cópia sem sobrescrita, snapshots históricos, integridade SHA-256,
origens do acervo ou caminhos persistidos. Não houve migração nova ou alteração de
migração publicada. As bases novas dos testes foram migradas até V10; a versão do
banco operacional **não foi consultada nem presumida**.

## Roteiro para experimentar e recuperar

1. Siga **Como executar localmente** no README: build externo e base nova. Confira
   processos e porta antes de iniciar. Use uma instância e o frontend do mesmo JAR.
2. Verifique `/api/versao`, `/api/configuracoes` e `/api/pendencias` com timeout
   de cinco segundos. Se falhar, consulte o log; não encerre processos desconhecidos.
3. Abra Configurações; salve remetente sintético, crie/escolha subpasta e teste
   gravação. Recarregue e confira a versão salva. O destino é do backend.
4. Execute `testar-configuracoes.cjs` conforme README para gerar a fixture externa
   `fixture-sintetica.zip`. Ela contém dois CT-es e um PDF válido sintéticos.
5. Importe essa fixture, confira os documentos e associe a fatura às duas viagens.
   Confirme. Consulte cliente, viagens e documentos, a cópia e seu manifesto.
6. Abra Processamentos e o histórico de exportações. Use a Central de envios para
   selecionar documentos, informar `motorista@example.invalid`, conferir e confirmar
   em **SIMULADO**. Verifique snapshot e identificação explícita da simulação.
7. Pare a sua instância e reinicie com a **mesma** base/acervo/raiz, Flyway desligado
   e `IFEXISTS=TRUE`. Confira configurações, documentos, exportações e tentativas.

Para falhas, apenas na área sintética:

| Falha | Como reproduzir | Recuperação e garantia verificada |
| --- | --- | --- |
| PDF inválido | Texto `invalido` com extensão `.pdf` dentro do ZIP | Diagnóstico por entrada; importação integral bloqueada, sem publicação parcial; ZIP corrigido inicia nova conferência |
| XML inválido | XML sem CT-e válido | Erro identifica entrada/etapa; corrigir a origem e conferir novo ZIP |
| Associação pendente | Não escolher finalidade ou viagens de um PDF | Confirmação desabilitada; associação é explícita; não encerra pendência persistida só ao fechar aviso |
| ZIP inseguro/limites | Entrada `../fora.pdf`, excesso de entradas/tamanho/taxa | Leitura interrompida; entradas restantes não são declaradas válidas; nenhuma importação parcial |
| Pasta indisponível | Renomear somente a raiz sintética antes de confirmar cópia automática | Importação permanece; restaurar pasta, testar e tentar somente a exportação |
| Conflito de destino | Criar arquivo no caminho planejado, inclusive com bytes iguais | Não sobrescreve; revisar conflito ou salvar outra subpasta e exportar deliberadamente |
| Origem ausente/hash divergente | Remover/alterar apenas arquivo sintético do acervo | Cópia não conclui; restaurar bytes exatos e retomar; não reimportar nem remapear caminhos |
| Falha de conexão GET | Interceptação do navegador em teste | Erro de consulta vermelho, indicador indisponível; Tentar novamente carrega dados reais |
| Clique duplo/repetição | Confirmar/repetir mesma identidade e chamadas concorrentes | Mesmos registros reconhecidos; sem duplicar arquivos, tentativas ou avisos |
| Timeout/resposta perdida em alteração | Reter resposta depois do commit ou abortar a resposta de tentativa | Estado incerto; consultar histórico/mesma identidade antes de repetir; sem reenvio automático |

Resolver preserva diagnóstico e histórico das tentativas; fechar detalhes/navegar
não resolve pendência. PDFs têm somente conferência de cabeçalho no backend, não
validação estrutural/fiscal. Aceite SMTP não é prova de entrega ou leitura.

## Testes desta revisão

Comando da suíte completa, com Java 25:

```powershell
$Build = Join-Path $env:TEMP 'opencode/rotabrasil-estabilizacao-build'
mvn -B "-Drotabrasil.build.directory=$Build" clean test
mvn -B "-Drotabrasil.build.directory=$Build" -DskipTests package
```

**Execução completa final encerrada em 31/10/2026 00:07:48 -03:00:**
360 testes contabilizados, **359 aprovados**, zero falhas, zero erros, **1 ignorado**.
O ignorado é `ProtocoloArquivosTest.recusaAreaTemporariaComoLinkSimbolico`: Windows
sem privilégio de symlink; a exigência desse caso no job Linux foi preservada.
Após essa execução, os ajustes finais foram em JavaScript, ferramentas e documentação;
o mesmo backend foi reempacotado com os estáticos finais e validado no navegador.

| Execução de navegador | Resultado desta tarefa |
| --- | --- |
| `testar-estabilizacao.cjs` | 5 verificações aprovadas, incluindo reprodução do JAR antigo, detecção da incompatibilidade, hashes/cache, GET 404, conexão indisponível, sidebar 390/800/1024/1440px, PUT incerto após commit e recusa 409 conhecida em vermelho |
| `testar-configuracoes.cjs` | 7 aprovadas; pasta externa, simulação, fatura compartilhada, bytes/hashes, automática/manual, indisponibilidade, retomada e reinício |
| `testar-diagnostico-recuperacao.cjs` | 7 aprovadas; arquivo inválido, contraste ≥4,5:1 nos dois temas, correção vinculada, histórico preservado, consulta de tentativa, falhas GET e reinício |
| `testar-modernizacao.cjs` | 10 aprovadas; consultas, documentos, PDF individual, envio simulado e responsividade |
| `testar-envio-manual.cjs ... --somente-local` | 13 aprovadas; importação com três PDFs, correção, cancelamento, resposta perdida, tentativa idempotente, reenvio e reinício |
| Continuação protegida de `testar-envio-manual.cjs` | **Bloqueada**: API sem sessão respondeu 401 como esperado, mas `GET /login` retornou 404 sem formulário |

Não foram observados erros JavaScript inesperados nas execuções aprovadas. Os
números acima pertencem a execuções separadas desta tarefa, não a um total acumulado
de cenários únicos. A falha protegida não foi convertida em aprovação.

Cobertura JUnit relevante, dentro da suíte completa: `DiagnosticoPacoteTest`,
`DiagnosticoRecuperacaoHttpTest`, `ConfiguracaoExportacaoHttpTest`,
`PacoteAssistidoHttpTest`, `EnvioManualHttpTest`, `SegurancaHttpTest`,
`DestinoExportacaoTest`, testes de contratos, migrações, armazenamento e reinício.
Recusa/timeout SMTP são simulados com doubles; nenhum SMTP real foi habilitado.

### Evidências externas

Sob `C:/Users/florb/AppData/Local/Temp/opencode/`:

- `rotabrasil-estabilizacao-build/surefire-reports/`: suíte completa final.
- `rotabrasil-estabilizacao-entrega-ui/`: `resultado.json`, `versao.json`, sidebar móvel/recolhida.
- `rotabrasil-estabilizacao-config-entrega-ui/`: resultado, capturas e ZIP sintético reutilizável.
- `rotabrasil-estabilizacao-recuperacao-final-ui/`: resultado e capturas de recuperação/contraste.
- `rotabrasil-estabilizacao-modernizacao-ui/`: regressão das consultas e responsividade.
- `rotabrasil-estabilizacao-local-evidencia-ui/resultado-local.json`: 13 cenários locais finais.
- `rotabrasil-estabilizacao-envio-validado-ui/`: resultado local e bloqueio protegido registrados separadamente.

Falhas intermediárias foram investigadas antes de ampliar alterações: erro de API
Fetch no novo script (`status` é propriedade), verificação de encerramento por sinal
no Windows, seletores do teste legado incompatíveis com os cartões V10, descrição
incorreta de cancelamento como timeout, limite de tempo externo de uma execução Maven
e timeout do navegador antes da resposta de confirmação em execução intermediária.
Os logs dessa última não mostravam resposta do POST; não foram usados como evidência
de falha transacional. O teste passou a aguardar a resposta HTTP antes dos indicadores,
com prazo finito e registro de requisições, e a execução local final concluiu os 13
cenários. Nenhuma regra de importação ou segurança foi relaxada para fazê-lo passar.

## Pendências reais e prontidão

Não há bloqueio encontrado no **fluxo manual local isolado testado**. A base está
apta para iniciar o desenvolvimento da Etapa E-mail. Isso não declara homologação
de SMTP, implantação operacional ou de um ambiente ainda não inspecionado.

Para uso no ambiente operacional, ainda é necessário verificar sua versão de banco
(V10), backup e permissões da raiz externa. Migração exige autorização específica.
O acesso de navegador com segurança ativa continua bloqueado pelo `/login` 404,
registrado como cenário separado; autenticação, papéis e CSRF não foram removidos.
Nenhum e-mail real foi enviado ou apresentado como simulação concluída de envio real.
