# Importação assistida de ZIP

**Exclusão de cliente:** veja o [contrato de exclusão conjunta](exclusao-cliente-com-documentos.md).
Pacotes permanecem como histórico, mas não reutilizam um resultado parcial nem
restauram automaticamente registros removidos pela exclusão do cliente.

**Diagnóstico por arquivo:** inspeção/erros de confirmação agora incluem
[relatório estruturado e recuperação manual](diagnostico-processamento.md),
com origem no ZIP, etapa, situação, orientação, filtros e bloqueio integral.
Um erro de conteúdo permite conferir as outras entradas quando seguro, sem
importá-las parcialmente. HTTP 400 inclui o relatório em `diagnostico`.

**Atualização V7:** após a importação, o painel permite [conferir e confirmar
tentativas manuais em SIMULAÇÃO](envio-manual.md), consultar snapshots e confirmar
reenvio. SMTP operacional continua desativado. As referências à prévia de
metadados abaixo descrevem o endpoint anterior, mantido para compatibilidade.

O painel, na rota `/`, recebe ZIPs com CT-es XML e PDFs produzidos pelo sistema
externo. O envio operacional de e-mail continua desativado enquanto as regras e
a configuração não forem definidas. A prévia não agenda envio nem registra uma
entrega. Não há integração com contatos ou caixa de entrada do Outlook nesta etapa.

## Uso no painel

**Interface atual (03/11/2026):** [uso diário simplificado](uso-diario-simplificado.md).
A entrada tem três passos; inspeção continua separada da confirmação transacional.
`/#pacotes-importados` agora abre uma tela independente. A confirmação ao motorista
é opcional e não bloqueia importação sem e-mail. Os capítulos anteriores abaixo
preservam seus contratos e evidências históricas.

**Regra atual (09/11/2026):** download no Outlook da empresa e upload do ZIP são
manuais; abra os PDFs para identificar o CT-e e associe explicitamente. Documentos
e fatura destinam-se ao cliente escolhido na Central de envios. Não há e-mail de
motorista disponível nem destinatário inferido por fatura. Veja a
[reprodução comparativa e os resultados atuais](revisao-operacional-20261109.md).

### Verificação local de 02/11/2026

A conferência não grava os documentos: cada PDF exige finalidade e viagens
selecionadas pelo operador, seguidas de **Confirmar importação**. A tela agora
exibe o número de PDFs ainda sem associação e um atalho para a primeira escolha
pendente. As associações e a confirmação aparecem antes do diagnóstico detalhado.
O PDF avulso informa quando é necessário importar primeiro o XML da viagem.

Verificação com o JAR atualizado, H2 e documentos sintéticos em diretórios externos:
`testar-exclusoes-paginas.cjs` (10 verificações), `testar-previa-pdf.cjs` (11) e
`testar-configuracoes.cjs` (7), sem erros JavaScript. Inclui XML individual
idempotente, ZIP, PDF avulso, visualização/revisão, downloads com SHA-256,
arquivamento/restauração, exportação, recuperação da pasta e persistência após
reinício. O teste de gerenciamento usa seletores específicos de exclusão e
restauração, distinguindo-os das ações de exportação existentes na mesma linha.

Os PDFs agora têm [prévia no painel e revisão visual em cópia](previa-e-revisao-pdf.md),
com páginas, zoom e geração de ZIP revisado para nova conferência. Os vínculos
continuam manuais e o XML não é alterado pelas revisões do PDF.

1. Em **Importar ZIP**, escolha o arquivo e clique em **Conferir arquivos**. Esta
   operação identifica as viagens pelo XML e mostra nomes, clientes e quantidades,
   sem gravar clientes, viagens, documentos ou arquivos definitivos.
2. Para cada PDF, escolha a finalidade e marque os CT-es correspondentes:
   **Documento de uma viagem** exige exatamente uma viagem; **Fatura** permite
   uma ou várias. Nenhuma associação é deduzida de nomes, posição ou número fixo
   de arquivos. Todos os PDFs precisam de uma escolha explícita.
3. **Confirmar importação** revalida os mesmos bytes e grava todos os XMLs/PDFs
   em uma transação. Falhas desfazem os registros e as publicações próprias.
4. Escolha uma viagem, selecione os XMLs/PDFs ativos e informe o destinatário
   para gerar a conferência documental quando o transporte estiver disponível.
   Sem envio real configurado, a ação cotidiana fica indisponível. Uma fatura
   compartilhada aparece em todas as viagens vinculadas e não define destinatário.
   Seleções são limpas ao trocar de viagem ou atualizar os dados.
5. **Arquivar** um PDF preserva registro, vínculos e bytes, retirando-o da lista
   ativa e da prévia em todas as viagens vinculadas. O filtro de arquivados permite
   restaurar. Não existe exclusão física do arquivo.

Não é preciso esperar a definição final da origem dos contatos para usar esse
fluxo: o destinatário é escolhido explicitamente na prévia. Use dados sintéticos
em testes; o painel opera no banco configurado na aplicação.

## API

- `POST /api/pacotes/previa-pdf?sha256=<hash>&arquivoId=a1`: multipart `arquivo`
  com o mesmo ZIP conferido; retorna os bytes daquele PDF, sem importação.
  Exige papel de operador/admin quando a segurança está ativa e proteção CSRF.

- `POST /api/pacotes/inspecao`: multipart `arquivo` com o ZIP; retorna `sha256`
  do ZIP e `arquivos` (`id`, nome, tipo, tamanho, SHA-256 e metadados do CT-e).
  IDs como `a0` são locais à conferência, não IDs do banco. `nomeArquivo` é nome
  simples; `caminhoInterno` conserva o caminho dentro do ZIP. O relatório também
  inclui UUID, diagnósticos, contagens e indicação de interrupção.
- `POST /api/pacotes/confirmacao`: multipart com o mesmo `arquivo` e parte
  `configuracao` de tipo `application/json`:

```json
{
  "sha256": "<hash recebido na inspeção>",
  "vinculos": [
    {"arquivoId": "a2", "finalidade": "FATURA", "xmlIds": ["a0", "a1"]}
  ]
}
```

  Retorna `pacoteId`, `reutilizado`, `documentos` XML e `anexos` PDF, sem caminhos
  físicos. O exemplo corresponde a dois XMLs e um PDF, não a uma quantidade exigida.
  ZIP contendo apenas XML aceita `vinculos: []`. É necessário ao menos um CT-e.
- `GET /api/viagens/{id}/anexos`: PDFs ativos; `?arquivados=true` lista arquivados.
- `DELETE /api/anexos/{id}`: arquiva, HTTP 204; repetição não troca a data original.
- `POST /api/anexos/{id}/restaurar`: restaura o mesmo registro e vínculos.
- `POST /api/viagens/{id}/previa-email`: mantém `destinatario` e `documentoIds`;
  acrescenta `anexoIds`. As listas podem ser omitidas individualmente, mas é
  necessário selecionar ao menos um XML/PDF. IDs repetidos, inválidos, PDFs
  arquivados ou sem vínculo com a viagem são recusados. Retorna `documentos`,
  `anexos` e `enviado: false`. Esta prévia confere metadados e vínculos, sem
  verificar disponibilidade/bytes dos anexos ou efetuar transporte SMTP.

## Integridade e repetição

A confirmação do ZIP com os mesmos bytes e associações reutiliza o pacote,
mesmo que o nome de upload mude. A ordem das escolhas não muda sua identidade.
Associações diferentes para o mesmo ZIP retornam HTTP 409 e preservam o original.
A reutilização não restaura arquivos arquivados nem muda seus vínculos.

XML com chave e conteúdo idênticos pode reutilizar um documento existente; a
mesma chave com conteúdo diferente exige revisão, sem sobrescrita. No fluxo ZIP,
mais de um CT-e para o mesmo nCT é recusado, inclusive diante de outro CT-e já
registrado para aquela viagem. Registros históricos divergentes precisam de
reconciliação; a entrada individual XML anterior mantém seu contrato.

Um novo ZIP pode trazer PDFs corrigidos: a confirmação explícita cria novos
registros/arquivos, preservando os anteriores, que podem ser arquivados. Não há
reenvio automático. ZIP diferente com PDF idêntico não sofre deduplicação global;
a identidade de um pacote é o hash do ZIP e sua associação confirmada.

A fatura tem uma linha em `anexos` e vários vínculos em `anexo_viagens`, sem
copiar o arquivo por viagem. O PDF fica fisicamente no diretório de uma das
viagens selecionadas, com nome gerado `anexo-<UUID>.pdf`. Essa localização não
define os vínculos comerciais. O journal de armazenamento usa versão 2 para
PDFs e continua aceitando a versão 1 dos XMLs. A recuperação opt-in só publica
pendências compatíveis com registros confirmados; não adota arquivos órfãos.
O inventário e backup incluem pacotes, PDFs e tabelas de associação no fingerprint.

## Limites da entrada

ZIP de até 20 MiB; requisição multipart de até 21 MiB (margem para metadados).
Até 100 entradas, 20 MiB por arquivo e 100 MiB descomprimidos no total. Arquivos
acima de 1 MiB com proporção maior que 100:1 são recusados. Somente XML/PDF,
sem ZIPs aninhados ou senha, nomes ambíguos, CRC divergente ou caminhos inseguros.
Nomes de entrada nunca são usados para extrair arquivos em caminhos recebidos.
O ZIP é reaberto na confirmação e seu hash deve coincidir com a conferência.
Não há uma pasta temporária de importações aguardando associação no servidor.

XML passa pelo extrator CT-e 4.00 existente; PDF exige cabeçalho `%PDF-`.
Isso não valida o conteúdo interno do PDF, não executa antivírus e não substitui
XSD, assinatura fiscal ou consulta à SEFAZ. A rotina não gera XML, PDF, fatura ou
boleto; conserva os documentos fornecidos pelo sistema externo.

## Banco e execução

A importação ZIP exige **V6**, que cria as tabelas de pacotes, PDFs e seus vínculos.
A aplicação atual exige também **V7** para conferências/tentativas de e-mail.
Bancos em V4 também precisam da V5 de arquivamento. O banco operacional não foi
migrado nesta implementação. A configuração padrão continua Flyway desativado e
Hibernate `validate`; não usar `ddl-auto=update`, `clean` ou `repair` como atalho.
A migração operacional exige autorização, escritores parados e inventário/backup
atual verificado pelo procedimento de [manutenção](etapa4-banco-migracoes.md).
Backups de outros ambientes não devem ser presumidos disponíveis ou atuais.

Para experimentar sem alterar o banco existente, compile com Java 25/Maven:

```bash
mvn clean test
mvn -DskipTests package
ROTA_TESTE_DIR=$(mktemp -d)
java -jar target/rota-brasil-0.0.1-SNAPSHOT.jar \
  "--spring.datasource.url=jdbc:h2:file:$ROTA_TESTE_DIR/banco" \
  --spring.flyway.enabled=true --spring.jpa.hibernate.ddl-auto=validate \
  --spring.h2.console.enabled=false \
  "--rotabrasil.arquivos.viagens=$ROTA_TESTE_DIR/viagens"
```

A API e o painel são servidos pela mesma aplicação. Abra a rota `/` do servidor.
Os testes JUnit e scripts de navegador usam bancos e arquivos sintéticos isolados.
`scripts/testar-pacotes.py --jar CAMINHO --java JAVA_25 --chromium CHROMIUM --saida PASTA`
exercita a conferência, associação, fatura compartilhada, prévia, arquivamento,
repetição e layout móvel. `scripts/testar-painel.py` mantém os cenários anteriores.

Ficam pendentes: integração com Outlook, recebimento automático do ZIP, formato
real anonimizado para validar o mapeamento comercial e envio SMTP operacional.
A auditoria manual de tentativas, falhas e reenvio foi implementada em modo simulado.
