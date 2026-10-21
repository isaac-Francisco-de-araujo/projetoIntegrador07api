# Importação assistida de ZIP

O painel, na rota `/`, recebe ZIPs com CT-es XML e PDFs produzidos pelo sistema
externo. O envio operacional de e-mail continua desativado enquanto as regras e
a configuração não forem definidas. A prévia não agenda envio nem registra uma
entrega. Não há integração com contatos ou caixa de entrada do Outlook nesta etapa.

## Uso no painel

1. Em **Importar ZIP**, escolha o arquivo e clique em **Conferir ZIP**. Esta
   operação identifica as viagens pelo XML e mostra nomes, clientes e quantidades,
   sem gravar clientes, viagens, documentos ou arquivos definitivos.
2. Para cada PDF, escolha a finalidade e marque os CT-es correspondentes:
   **Documento de uma viagem** exige exatamente uma viagem; **Fatura** permite
   uma ou várias. Nenhuma associação é deduzida de nomes, posição ou número fixo
   de arquivos. Todos os PDFs precisam de uma escolha explícita.
3. **Confirmar importação** revalida os mesmos bytes e grava todos os XMLs/PDFs
   em uma transação. Falhas desfazem os registros e as publicações próprias.
4. Escolha uma viagem, selecione os XMLs/PDFs ativos e informe o destinatário
   para gerar a prévia. Uma fatura compartilhada aparece em todas as viagens
   vinculadas. Seleções são limpas ao trocar de viagem ou atualizar os dados.
5. **Arquivar** um PDF preserva registro, vínculos e bytes, retirando-o da lista
   ativa e da prévia em todas as viagens vinculadas. O filtro de arquivados permite
   restaurar. Não existe exclusão física do arquivo.

Não é preciso esperar a definição final da origem dos contatos para usar esse
fluxo: o destinatário é escolhido explicitamente na prévia. Use dados sintéticos
em testes; o painel opera no banco configurado na aplicação.

## API

- `POST /api/pacotes/inspecao`: multipart `arquivo` com o ZIP; retorna `sha256`
  do ZIP e `arquivos` (`id`, nome, tipo, tamanho, SHA-256 e metadados do CT-e).
  IDs como `a0` são locais à conferência, não IDs do banco.
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

A nova versão exige **V6**, que cria as tabelas de pacotes, PDFs e seus vínculos.
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
real anonimizado para validar o mapeamento comercial, envio SMTP operacional,
auditoria de tentativas com destinatário/anexos e tratamento de falhas/reenvio.
