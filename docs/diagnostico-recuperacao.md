# Etapa de Diagnóstico e Recuperação — V10

**Evolução V11:** a [confirmação ao motorista](confirmacao-motorista.md) reutiliza
o diagnóstico `NOTIFICACAO` e a Central. Falha conhecida fica em vermelho;
DESCONHECIDO em âmbar; SIMULADO/ACEITO_SMTP em verde com identificação explícita
do que foi comprovado, sem afirmar entrega/leitura. Consultar avisos abre a
mensagem e suas tentativas; não dispara transporte. Reenvio exige conferência e
confirmação vinculadas. Sucesso posterior pode resolver falha conhecida, mas não
resolve uma tentativa anterior desconhecida. Importação/exportação são independentes.
Código atual exige V11; não houve migração operacional.

**Revisão atual (31/10/2026):** veja [Estabilização](estabilizacao.md). O menu tem um
contador compacto (falhas + atenção, `99+`), com divisão na tela e indisponibilidade
acessível única. GET com falha é erro de consulta, em vermelho, com nova tentativa;
não é resultado incerto de alteração. Os resultados V10 abaixo são históricos.

## Visão operacional

A tela existente **Processamentos** passou a ser a **Central de Pendências**.
Ela consulta processamentos, exportações, tentativas documentais e avisos incertos
nos seus históricos originais. Não existe tabela paralela de pendências, cópia
dos históricos nem um botão genérico para declarar problemas resolvidos.

Toda falha relevante apresenta o ocorrido, etapa, referência, localização lógica,
causa comprovada ou desconhecida, impacto no que foi salvo e orientação segura.

- **Vermelho:** falha ou bloqueio, com texto “Erro”/“Falha” e ícone.
- **Âmbar:** pendência, processamento ou resultado ainda incerto.
- **Verde:** resultado comprovado ou resolução com evidência.
- Consulta sem resultados permanece neutra.

Os cartões persistem na tela enquanto exigirem ação, sem depender de toast.
Detalhes usam `details/summary`, botões nativos e foco visível; novos erros são
anunciados em região acessível. Há contraste próprio nos temas claro/escuro,
sem piscadas ou animações de erro. O aviso geral também deixou de ser flutuante.

## Contrato consistente

`DiagnosticoOperacao` é o contrato público comum. Exemplo abreviado:

```json
{
  "operacaoId": "<UUID>",
  "correlacaoId": "<X-Request-Id da requisição original>",
  "dataHora": "<data ISO com fuso>",
  "versao": "0.0.1-SNAPSHOT / diagnostico-V10",
  "origem": "PROCESSAMENTO",
  "pacoteId": null,
  "pacote": "documentos.zip",
  "etapa": "VALIDACAO_PDF",
  "codigo": "PDF_NAO_RECONHECIDO",
  "gravidade": "ERRO",
  "mensagem": "Importação bloqueada — arquivo ou pacote precisa de correção.",
  "causaConhecida": "O cabeçalho %PDF- não foi reconhecido. Não foi feita validação estrutural ou detecção de senha do PDF.",
  "orientacao": "Corrija o material e faça nova conferência vinculada; não substitua os originais.",
  "situacao": "CONFERENCIA_COM_PENDENCIAS",
  "estado": "PRECISA_ACAO",
  "impacto": "Nenhum documento desta conferência foi gravado no acervo...",
  "problemas": [],
  "acoes": [],
  "resolucao": null,
  "recuperacoes": []
}
```

Cada problema contém ID/nome do arquivo quando conhecido, localização, tipo de
localização, etapa, código, gravidade, mensagem, causa e orientação. Problemas de
requisição, conexão ou consulta não recebem um arquivo culpado inventado.

O envelope HTTP mantém `timestamp`, `status`, `mensagem`, `campos` e, quando
aplicável, o `diagnostico` ZIP anterior. Acrescenta `diagnosticoOperacao`, preservando
os consumidores do contrato anterior. As consultas novas são:

| Rota | Uso |
| --- | --- |
| `GET /api/diagnosticos/PROCESSAMENTO/{uuid}` | Resultado e evidência de resolução do processamento |
| `GET /api/diagnosticos/EXPORTACAO/{uuid}` | Itens pendentes e histórico de tentativas de recuperação |
| `GET /api/diagnosticos/ENVIO/{uuid}` | Resultado seguro da tentativa, sem repetir o transporte |
| `GET /api/diagnosticos/NOTIFICACAO/{uuid}` | Consulta do aviso incerto, sem disparo |
| `GET /api/pendencias?filtro=PRECISA_ACAO&pagina=0&tamanho=20` | Central com filtro e paginação SQL |
| `GET /api/pendencias/resumo` | Contadores distintos de falhas, atenção, andamento e resolvidos |

Filtros aceitos: `PRECISA_ACAO`, `EM_PROCESSAMENTO`, `RESOLVIDO` e `TODAS`.
O tamanho da página vai de 1 a 50. A resposta contém `pagina`, `tamanho`, `total`,
`itens` e `resumo`. A interface usa páginas de 20, sem carregar toda a base para
filtrar no navegador. Menu e indicadores usam os contadores dos mesmos registros.

As causas conhecidas vêm de códigos observados. Corrupção/proteção de ZIP não
distinguida, falha genérica de I/O e bug inesperado não recebem uma hipótese como
causa confirmada. Nesses casos, a mensagem é:

> Não foi possível identificar a causa. Use a referência da operação para suporte.

PDF continua tendo apenas conferência de cabeçalho no backend. Não há detecção
de senha de PDF, validação fiscal/estrutural nem inferência de vínculos.

## Localização e resultado sem ambiguidades

- **Dentro do ZIP:** `documentos.zip > cobrancas/fatura.pdf` identifica a entrada
  original; nomes iguais em diretórios diferentes continuam distinguíveis.
- **Acervo:** IDs de pacote/viagem e resultados comprovados, sem caminho físico.
- **Exportação:** raiz autorizada do **computador do backend**, subpasta e caminho
  relativo controlado pelo sistema. Detalhes do plano continuam consultáveis.
- **Temporário:** informa se foi removido, não criado, se a limpeza ficou pendente
  ou desconhecida. A origem no ZIP nunca é apresentada como temporário recuperável.

Falhas locais de conteúdo permitem examinar outras entradas quando seguro.
Corrupção, caminho inseguro e limites podem interromper a leitura. Entradas não
examinadas não são válidas. Isso não permite importação parcial: a confirmação
integral continua bloqueada e o processamento transacional existente foi mantido.

Uma importação confirmada não significa que a exportação adicional terminou.
O diagnóstico de cópia mostra **Documentos importados — exportação pendente** e
explica que os documentos estão preservados e não precisam ser importados novamente.

## Recuperação e ciclo de vida

### Corrigir conteúdo ou associações

1. Na Central ou no resultado, consulte o problema e seus detalhes.
2. Use **Selecionar ZIP corrigido** para iniciar uma nova conferência vinculada.
   A seleção antiga, confirmações e diagnóstico na aba são invalidados.
3. Para o ZIP já selecionado, **Revisar associações** posiciona o foco no seletor
   dos PDFs. Se o arquivo não está na aba, orienta selecioná-lo novamente; o servidor
   não retém o ZIP original para editar seu conteúdo.
4. Confira o novo arquivo e confirme suas associações e a importação normalmente.

Nova conferência válida vinculada pode resolver o erro de conteúdo anterior.
Se o novo material ainda precisa de associações, elas continuam pendentes em sua
própria operação. Pendência de associação só é resolvida com evidência adequada,
como importação confirmada. Apenas selecionar vínculos no navegador não encerra
a pendência persistida.

`processamentos` guarda `anterior_id` e, agora, `resolvido_por`, `resolvido_em` e
`criterio_resolucao`. O JSON original do diagnóstico é preservado. A Central agrupa
repetições técnicas pela fase, SHA-256 e problemas identificados, mantendo as linhas
originais para consulta. Conferência sem vínculo não encerra uma falha anterior.

### Corrigir destino e retomar a exportação

1. Abra **Configurações da pasta** pela ação do diagnóstico.
2. Corrija acesso, espaço ou disponibilidade e use o teste de gravação salvo.
3. Volte à operação e use **Tentar somente a exportação**.

Não é enviado um novo ZIP e não é repetida a importação. A retomada usa o destino
histórico e preserva publicações próprias reconhecidas. Conflito com arquivo alheio
não permite sobrescrita; se precisar de outro destino, salve a configuração e use
a exportação explícita do pacote para criar outra cópia deliberada.

`exportacoes.recuperacoes` registra, na própria exportação, ID da tentativa,
correlação, início/fim, situação, contagens e snapshot dos problemas daquela
tentativa. Resolver a cópia não apaga a causa anterior. A resolução só é comprovada
quando todos os itens e o manifesto concluem a cópia com SHA-256 conferido.

As ações POST de exportação aceitam, além de `confirmado:true`, `requisicaoId`
UUID opcional. Repetir esse ID recupera a tentativa reconhecida sem executar outra
cópia. A interface guarda apenas IDs para recuperar uma resposta perdida; uma
nova tentativa deliberada após resultado conhecido usa outro ID. As proteções
de coordenação, staging/testemunha e publicação sem sobrescrita permanecem.

Retomar a exportação registra o evento correspondente, mas **não executa o
transporte de notificação**, nem mesmo a simulação automática. A Etapa E-mail
continua separada e nenhuma recuperação autoriza envio real.

### Consultar envio incerto

**Consultar tentativa de envio** lê o endpoint já existente e apresenta seu
diagnóstico na Central de envios. Não confirma envio ou reenvio. `DESCONHECIDO`
continua aberto para revisão, inclusive depois de outra tentativa: sucesso de um
reenvio não prova o resultado da mensagem anterior incerta. Falha conhecida pode
ter resolução vinculada a reenvio explícito com resultado conhecido, preservando
o registro original. Aceite SMTP nunca é descrito como entrega ou leitura.

Fechar detalhes, navegar ou copiar informações **não resolve** pendências.
Não foi acrescentado encerramento manual sem evidência.

## Demora, interrupção e falha da interface

A tela só mostra fatos: requisição solicitada, diagnóstico recebido, confirmação
iniciada no backend e exportação registrada como em processamento. Não há
porcentagem nem progresso fictício por XML/PDF; a inspeção síncrona só fornece
seu diagnóstico por entrada ao terminar.

Registros recentes `EM_PROCESSAMENTO` aparecem separados. Após cinco minutos sem
resultado final, a Central os apresenta como **precisam de ação / incertos**, sem
transformar isso em falha comprovada ou repetir efeitos automaticamente. O estado
durável original não é reescrito por uma consulta. Esse intervalo não é timeout
do transporte, cancelamento ou prova de que o processo terminou.

No timeout do navegador:

> A resposta demorou além do esperado. O resultado ainda não foi confirmado.
> Consulte o histórico antes de repetir.

Respostas inválidas e perda de conexão durante **alterações** também ficam incertas.
GET sem resposta, com timeout ou corpo inválido apresenta falha ao carregar a
consulta, sem afirmar incerteza transacional. Cancelamento explícito informa espera
interrompida no navegador; não promete cancelar o trabalho no backend. Confirmações com
dados incompletos continuam bloqueadas. Um diagnóstico anterior da mesma seleção
fica explicitamente rotulado como anterior; trocar de operação limpa os resultados
locais e impede que uma resposta atrasada habilite outra seleção.

Uma consulta bem-sucedida da mesma rota pode remover seu erro transitório local,
sem criar uma pendência permanente. O script de emergência independente anuncia
falhas JavaScript globais com mensagem segura e referência local, sem capturar a
mensagem privada/stack do erro e sem ciclos de alertas. Não promete corrigir bugs.

## Logs e suporte

**Copiar informações para suporte** usa uma lista fechada: operação, requisição,
data/hora, código, etapa, versão e IDs/nomes de arquivos quando adequados. Não copia
mensagens completas, documentos, bytes, destinatários, tokens, credenciais ou paths.
Se o clipboard não estiver disponível, apresenta texto seguro selecionável, sem
declarar sucesso de cópia automática.

A correlação original é persistida e se relaciona com `X-Request-Id` e o MDC dos
logs. Falhas inesperadas registram tipo e pilha técnica sem a mensagem privada
da exceção. A leitura de um problema histórico não grava novamente o erro a cada
atualização. O acesso HTTP normal continua auditado pelos recursos existentes.

## Persistência e implantação

`V10__diagnostico_e_recuperacao.sql` amplia as tabelas existentes; não cria outra
tabela de histórico/pendências. Registros anteriores permanecem intactos, com
correlação/resolução nulas quando desconhecidas. Inventário/fingerprint já leem
essas tabelas e incluem as novas colunas.

O código exige V10. **Nenhum banco operacional foi migrado nesta entrega.** Para
usar no banco existente, siga autorização, inventário e backup verificado do
[procedimento de manutenção](etapa4-banco-migracoes.md). Migrações automáticas
continuam desligadas por padrão. Não há login novo, credenciais novas, leitura
do Outlook ou ativação de envio real.

## Provocar um erro e recuperar com segurança

Use somente o ambiente isolado descrito no README:

1. Monte um ZIP com `src/main/resources/email/cte-sintetico.xml` e um texto chamado
   `cobrancas/fatura.pdf` contendo `invalido`.
2. Confira o ZIP. O cartão vermelho identifica o PDF, a etapa e o bloqueio; nada
   é importado. Consulte a mesma pendência em **Processamentos**.
3. Use **Selecionar ZIP corrigido**, troque o texto por PDF válido sintético e
   confira o novo pacote. Revise as associações e confirme a importação.
4. Consulte **Resolvidos**: a falha anterior tem evidência e diagnóstico original.
5. Para testar exportação pendente, configure uma raiz temporária e habilite a
   cópia automática. Torne somente essa pasta de teste indisponível antes de
   confirmar a importação. Os documentos ficam preservados no acervo isolado.
6. Restabeleça a pasta, faça o teste de gravação e use **Tentar somente a exportação**.
   Consulte a confirmação e os diagnósticos históricos das tentativas em detalhes.

## Verificações

```powershell
$Build = Join-Path $env:TEMP 'opencode/rotabrasil-recuperacao-build'
mvn -B "-Drotabrasil.build.directory=$Build" `
  '-Dtest=DiagnosticoRecuperacaoHttpTest,DiagnosticoPacoteTest,ConfiguracaoExportacaoHttpTest,PacoteAssistidoHttpTest,EnvioManualHttpTest,SegurancaHttpTest,ContratoApiHttpTest' clean test
mvn -B "-Drotabrasil.build.directory=$Build" -DskipTests package
$Ui = Join-Path $env:TEMP ('opencode/rotabrasil-recuperacao-ui-' + [guid]::NewGuid().ToString('N'))
node scripts/testar-diagnostico-recuperacao.cjs "$Build/rota-brasil-0.0.1-SNAPSHOT.jar" "$Ui"
```

Os testes usam H2, arquivos/diretórios sintéticos isolados e transporte simulado.
O teste de navegador inicia uma única instância de teste, encerra antes do reinício
e a remove ao terminar. Artefatos ficam fora do repositório.

### Resultados desta entrega

- Build limpo direcionado: **128 testes aprovados**, sem falhas, erros ou ignorados:
  11 de diagnóstico/recuperação, 16 do leitor/diagnóstico ZIP, 19 de configuração/
  exportação, 26 de ZIP assistido, 12 de envio manual, 7 de segurança e 37 de contratos.
- Recorte final de diagnóstico/recuperação: **12 aprovados**, após acrescentar a
  consistência entre a gravidade de falha de notificação e o contador da Central.
  Esse recorte possui testes em comum com o anterior; os totais não são somados.
- Navegador: **7 verificações de diagnóstico/recuperação aprovadas**, incluindo
  contraste mínimo de 4,5:1, teclado, suporte seguro, correção vinculada, exportação
  pendente/retomada, consulta real da tentativa sem reenvio, timeout, resposta inválida,
  erros globais seguros, persistência após reinício e largura móvel.
- Regressão no navegador: **7 verificações de Configurações aprovadas** com o JAR V10.
- Nenhum erro JavaScript inesperado observado nas execuções finais; empacotamento aprovado.
- A suíte completa do repositório não foi executada nesta entrega.

JAR e relatórios em `C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-recuperacao-build/`.
Capturas/resultados em `rotabrasil-recuperacao-ui/` e
`rotabrasil-recuperacao-config-ui/`, sob a mesma área temporária externa.
As execuções locais usaram o Playwright já instalado nessa área; a dependência
reproduzível das ferramentas continua declarada em `scripts/`.

### Limitações conhecidas

- Causa não identificada depende de suporte usando as referências; não há reparo universal.
- A Central não reconcilia automaticamente transações ou mensagens com resultado incerto.
- Não há porcentagem por arquivo em tempo real, fila de retentativas ou repetição automática.
- Diagnósticos legados sem correlação não recebem uma referência histórica inventada.
- Exportações mostram até dez históricos recentes no contexto do pacote; a Central
  oferece consulta paginada crescente. O endpoint legado por pacote mantém seu contrato.
- PDF continua limitado ao cabeçalho no backend; senha e estrutura não são inferidas.
- Implantação no banco em uso depende de migração V10 autorizada e backup verificado.
