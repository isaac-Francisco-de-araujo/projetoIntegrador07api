# Etapa 2 — Identidade dos documentos

Atualização: a [Etapa 4](etapa4-banco-migracoes.md) substitui o padrão
`ddl-auto=update` por validação e acrescenta migrações e unicidade no banco.
As instruções abaixo registram a entrega histórica desta etapa; para iniciar
ou migrar a versão atual, siga a Etapa 4.


Este documento registra a entrega da Etapa 2. O armazenamento e a recuperação
foram posteriormente ampliados na [Etapa 3](etapa3-armazenamento-seguro.md), que
substitui os limites de escrita direta e ausência de compensação descritos aqui.

## Comportamento

O número `ide/nCT` continua identificando a viagem. A chave de acesso identifica
o documento fiscal, e SHA-256 compara os bytes recebidos. O hash não determina
equivalência fiscal entre XMLs diferentes.

A extração, o cálculo do hash e a escrita utilizam os mesmos bytes, com leitura
limitada a 20 MB. A busca de duplicatas ocorre antes de criar cliente, viagem ou
arquivo. Para documentos novos, `Documento.chaveAcesso` recebe os 44 dígitos e
`Documento.sha256` recebe o resumo hexadecimal com 64 caracteres.

| Situação | Resultado |
| --- | --- |
| Mesma chave e mesmos bytes, inclusive com outro nome | HTTP 200 com os mesmos IDs; nenhum novo registro ou arquivo |
| Mesma chave e bytes diferentes | HTTP 409; conteúdo existente preservado |
| Chaves diferentes com o mesmo nome original | Ambos são preservados em caminhos distintos |
| Arquivo registrado ausente, alterado ou inacessível | HTTP 409; exige reconciliação |
| Arquivo de destino existente sem registro | HTTP 409, mesmo com bytes idênticos; não adota nem sobrescreve o arquivo |
| Múltiplos registros para a mesma identidade | HTTP 409; não escolhe um arbitrariamente |
| Número de viagem associado a outro cliente ou com múltiplas viagens | HTTP 409; não associa o novo documento |

Novos arquivos usam `arquivos/viagens/{nCT}/{chaveAcesso}.xml`. O nome original é
preservado como metadado. A operação `CREATE_NEW` impede substituir um arquivo
já existente. Não são gerados PDFs, pacotes ZIP ou documentos fiscais novos.

## Compatibilidade com registros antigos

Os novos campos são opcionais no esquema para permitir registros legados sem
chave e sem hash. Não há preenchimento retroativo automático, mudança de nomes,
remoção de duplicatas nem movimentação de arquivos antigos.

Em cada importação, os XMLs sem chave são lidos para procurar a identidade fiscal,
independentemente da viagem à qual foram associados no passado. Um legado com
mesma identidade e mesmos bytes pode ser reutilizado no caminho antigo, desde que
seus relacionamentos sejam compatíveis. Seus novos campos continuam nulos.
Um registro com chave, mas sem hash, também pode ser conferido pelo arquivo sem
preenchimento automático do campo.

Essa verificação é conservadora: um único XML legado ilegível, inválido, sem chave
válida ou sem arquivo impede descartar duplicidades e bloqueia novas importações
com HTTP 409, inclusive de outras viagens. Caminhos antigos de outra máquina,
como caminhos absolutos do Windows, exigem reconciliação. Não se tenta substituir
ou reconstruir o documento ausente. A varredura custa leituras proporcionais ao
acervo legado; o inventário e o preenchimento controlado da Etapa 4 deverão
substituir essa dependência antes do uso operacional em escala.

## Banco operacional e implantação

O teste de compatibilidade cria o esquema anterior e dados sintéticos em H2 em
memória; então verifica que `ddl-auto=update` adiciona as colunas opcionais sem
perder o registro antigo. Isso não constitui migração do banco operacional.
`database/database.sql` permanece como referência do esquema anterior; não é o
script de implantação desta etapa.

A configuração padrão ainda usa `ddl-auto=update`. Iniciar a nova versão com
a configuração padrão pode alterar o esquema do banco em arquivo. Durante o
desenvolvimento, informe explicitamente uma URL H2 isolada e um diretório de
arquivos temporário. O banco e os XMLs operacionais não foram abertos ou alterados
nos testes desta etapa.

Antes da implantação operacional, é necessário inventariar dados e arquivos,
obter backup consistente, conferir chaves, hashes e duplicações e preparar uma
migração versionada revisada. Não adicione restrições únicas sobre dados legados
sem esse saneamento. A Etapa 2 não cria restrição de unicidade fiscal no banco.

## Limites na entrega da Etapa 2

- A consulta antecipada não garante unicidade concorrente no banco. A gravação
  com `CREATE_NEW` protege o destino contra sobrescrita, mas uma importação
  concorrente pode receber 409 em vez de reutilizar o documento. Restrições e
  tratamento transacional de concorrência ficam para a Etapa 4.
- Banco e disco não formam uma transação única. Falha de escrita ou falha no
  banco após a criação do arquivo pode deixar arquivo parcial ou sem registro.
  Ele será preservado e uma nova tentativa exigirá reconciliação. Arquivos
  temporários, publicação e recuperação de falhas ficam para a Etapa 3.
- Os testes desta etapa não comprovam recuperação de falhas, concorrência ou
  persistência após reinicialização. O esquema operacional não foi migrado.
- Os endpoints de consulta ainda retornam entidades JPA; DTOs, exposição de
  caminhos e serialização dos relacionamentos continuam pendentes da Etapa 6.
- SMTP permanece desativado.

## Validação

Execute com Java 25 e Maven:

```sh
mvn test
mvn -DskipTests package
```

No ambiente de nuvem preparado, use `/workspace/tools/rotabrasil-mvn` no lugar
de `mvn`. A suíte tem 49 testes: 21 do extrator, 20 de integração multipart/JPA,
4 de conteúdo/hash, 2 de organização, 1 de processamento com mocks e 1 de
compatibilidade do esquema legado. Os testes de integração usam H2 em memória,
diretórios temporários e fixtures sintéticas; não usam o banco operacional.
