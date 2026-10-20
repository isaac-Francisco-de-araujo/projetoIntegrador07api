# Etapa 3 — Preparação, publicação e recuperação de arquivos

Atualização: a [Etapa 4](etapa4-banco-migracoes.md) substitui o padrão
`ddl-auto=update` por validação e acrescenta migrações e unicidade no banco.
As instruções abaixo registram a entrega histórica desta etapa; para iniciar
ou migrar a versão atual, siga a Etapa 4.


## Protocolo de importação

A identidade fiscal, o SHA-256, as regras de viagem e a verificação de legados da
[Etapa 2](etapa2-identidade-documental.md) permanecem em vigor. Não há nova
migração de esquema nesta etapa.

Após conferir duplicidade e relacionamentos, o importador executa:

1. Cria uma operação exclusiva em `{raiz}/.pendentes/op-{UUID}/` e mantém uma
   trava de arquivo durante a transação. O identificador serve para diagnóstico.
2. Grava `operacao.properties` com versão do protocolo, número da viagem, chave,
   nome definitivo e hash esperado. Grava os mesmos bytes validados em
   `conteudo.tmp`. Ambos são fechados após `FileChannel.force(true)`.
3. Registra os callbacks de compensação. Salva o documento e executa `flush`
   para detectar erros de persistência antes da publicação.
4. No `beforeCommit`, confere o temporário e cria um **hard link** no destino
   `{raiz}/{nCT}/{chave}.xml`. O nome passa a apontar para o arquivo completo em
   uma única operação. Destino existente gera HTTP 409; não há sobrescrita.
5. Após commit confirmado, remove somente o temporário, os metadados e a trava
   da operação. O arquivo definitivo e o documento permanecem.
6. Em rollback confirmado, remove o destino somente se ele ainda aponta para o
   mesmo arquivo temporário desta operação e mantém o hash esperado. Depois
   remove os artefatos temporários. Arquivos preexistentes, substituídos ou
   alterados externamente são preservados.

O importador exige uma transação ativa; chamadas fora de uma transação não
iniciam gravação. As sobrecargas antigas de `OrganizadorArquivosService` para
uso sem banco continuam disponíveis, mas não fazem a coordenação com JPA.

O destino pode existir por um curto intervalo antes do commit. Enquanto a
operação está ativa, a reconciliação não a modifica. Uma importação simultânea
pode receber 409; isso não substitui a unicidade fiscal no banco prevista para
a Etapa 4.

## Falhas e respostas

| Falha | Comportamento |
| --- | --- |
| Escrita temporária parcial | Não publica o nome definitivo; tenta remover apenas os artefatos desta operação; transação revertida |
| Erro no banco antes de publicar | Rollback e remoção dos temporários |
| Falha de publicação | HTTP 500, ou 409 se o destino existe; rollback sem substituir arquivo existente |
| Erro transacional após publicar | Compensação do destino próprio e rollback dos registros |
| Falha de limpeza depois do commit | A resposta continua sendo sucesso; registra aviso com o UUID e preserva a pendência |
| Resultado transacional desconhecido | Preserva temporário, metadados e eventual publicação; não presume commit ou rollback |
| Interrupção do processo | As travas são liberadas pelo sistema operacional; os resíduos são conferidos na reconciliação |

Falhas de armazenamento são erros HTTP 500, não XML inválido. Os avisos de
limpeza não incluem documentos, chaves fiscais, contatos ou caminhos absolutos.
Se a compensação não puder terminar, a operação continua na área `.pendentes`
para investigação. Não há limpeza por idade nem exclusão indiscriminada.

## Inspeção e recuperação

`ReconciliacaoArmazenamentoService.inspecionar()` não altera arquivos ou dados.
`recuperarConfirmadas()` pode recuperar somente operações cujo registro está
confirmado no banco com a mesma chave, hash, viagem e caminho. Mantém uma trava
no registro durante a recuperação e uma trava exclusiva no diretório da operação.
Não modifica registros JPA e não preenche dados legados.

| Estado | Ação da recuperação |
| --- | --- |
| `EM_ANDAMENTO` | Ignora; outra execução mantém a trava |
| `PREPARADO_SEM_REGISTRO` | Preserva para revisão; não adota nem apaga |
| `PUBLICADO_SEM_REGISTRO` | Preserva XML e temporário; não inventa um registro nem apaga o destino |
| `COMMIT_CONFIRMADO` | Confere o arquivo definitivo e remove apenas artefatos temporários |
| `REGISTRO_SEM_ARQUIVO` | Restaura pelo temporário íntegro, sem substituir destinos, e conclui a limpeza |
| `RECUPERADA` | Recuperação da operação concluída; nova execução não encontra essa pendência |
| `REVISAO_NECESSARIA` | Preserva os arquivos; metadados inválidos, duplicidade ou conteúdo divergente impedem ação automática |

As operações sem commit não são adotadas automaticamente, mesmo com XML válido.
Se havia apenas preparação, uma nova tentativa pode concluir a importação e a
recuperação poderá então limpar o temporário anterior após conferir o registro
confirmado. Se já havia uma publicação sem registro, a importação permanece em
409 até decisão explícita de reconciliação.

A inspeção rejeita metadados com caminhos inválidos, links simbólicos na área de
operação e conteúdo acima dos limites. Somente o nome e número validados são
resolvidos sob a raiz configurada; os metadados não fornecem caminhos absolutos.

## Executar manutenção opcional

A manutenção está desativada por padrão e não cria endpoint HTTP adicional.
O runner `VerificacaoArmazenamento` é ativado na inicialização pelas propriedades
abaixo. Primeiro execute a inspeção. Para manutenção, pare as demais instâncias
que escrevem no mesmo acervo e utilize backup consistente. Informe sempre as
configurações externas corretas: o exemplo é destinado exclusivamente a um
ambiente isolado já inicializado, não ao banco operacional.

```sh
java -jar app.jar \
  --spring.main.web-application-type=none \
  --spring.datasource.url=jdbc:h2:file:/tmp/rotabrasil-isolado/banco \
  --spring.jpa.hibernate.ddl-auto=validate \
  --spring.h2.console.enabled=false \
  --rotabrasil.arquivos.viagens=/tmp/rotabrasil-isolado/viagens \
  --rotabrasil.armazenamento.verificar-pendencias=true
```

Para recuperar **somente as operações confirmadas**, acrescente:

```text
--rotabrasil.armazenamento.recuperar-confirmadas=true
```

O log apresenta UUID, estado e ID do documento. Sem `verificar-pendencias=true`,
o runner não é ativado. Não execute manutenção apontando para a URL padrão por
engano: `ddl-auto=update` continua sendo o padrão do projeto e pode alterar o
esquema operacional. A configuração operacional não foi usada nesta entrega.

## Requisitos e limites

- O sistema de arquivos precisa suportar hard links entre `.pendentes` e o
  destino, no mesmo sistema de arquivos, além de travas de arquivo. Não existe
  fallback para copiar conteúdo parcial ou para mover com substituição. A
  implementação foi validada no Linux deste ambiente.
- O protocolo trata falhas de aplicação e permite conferir resíduos após uma
  interrupção do processo. Não promete uma transação distribuída, durabilidade
  contra qualquer falta de energia, falha física do disco ou semântica de NFS.
  `force(true)` não substitui garantias de persistência de todas as entradas de
  diretório do sistema de arquivos.
- Preserve o controle de escrita sobre os diretórios: as travas coordenam o
  importador e o reconciliador, não ferramentas externas que ignorem o protocolo.
- A reconciliação cobre as operações com journal deste protocolo. Inventário
  completo de arquivos antigos sem journal, backup, saneamento e restrições
  únicas no banco permanecem na Etapa 4.
- Os registros legados e seus caminhos continuam inalterados. SMTP não é ativado
  e nenhum XML ou PDF é gerado.

## Verificação executada

```sh
mvn test
mvn -DskipTests package
```

Na nuvem, use `/workspace/tools/rotabrasil-mvn` no lugar de `mvn`.
A suíte tem 74 testes, sem falhas ou testes ignorados. Além das verificações
anteriores, inclui 8 testes do protocolo em disco, 11 de integração de falhas e
6 de reinicialização com H2 em arquivo e resíduos de interrupção simulados.

As verificações cobrem escrita parcial, publicação indisponível, rollback,
violação real de unicidade no H2 após publicação, falha de limpeza, estado
transacional desconhecido, travas, conteúdo externo preservado, inspeção sem
alterações e recuperação repetível. Os testes de reinicialização encerram e
reabrem contextos Spring com arquivos preservados; não simulam falta de energia.
Todos usam fixtures sintéticas e bancos/diretórios isolados.
