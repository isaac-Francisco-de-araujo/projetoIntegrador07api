# Exclusões no painel e arquivamento de documentos

Atualização: o [fluxo ZIP assistido](pacotes-assistidos.md) acrescenta PDFs/faturas
compartilhados, seleção de PDFs na prévia e migração V6. Consulte esse contrato
para o comportamento atual; o envio operacional continua desativado.

Decisão operacional confirmada: o painel deve permitir excluir clientes e
viagens, e documentos devem ser arquivados com possibilidade de restauração.
Os arquivos XML/PDF são preservados.

## Comportamento

| Registro | Operação | Proteção |
| --- | --- | --- |
| Cliente | Excluir cadastro sem vínculos | Viagens ou histórico de envio impedem a exclusão (409) |
| Viagem | Excluir cadastro vazio, preservando o cliente | Documentos, inclusive arquivados, ou histórico de envio impedem a exclusão (409) |
| Documento | Arquivar e retirar da lista ativa | Cadastro, chave, hash, relacionamento e arquivo são preservados |
| Documento arquivado | Restaurar | Retorna à lista ativa com o mesmo ID e os mesmos arquivos |

Toda exclusão/arquivamento no painel exige confirmação em diálogo identificado
com o nome do registro. Cancelar ou pressionar Escape não envia a requisição.
Enquanto a operação está em andamento, o diálogo não permite novas confirmações
ou cancelamento. Os botões são reabilitados ao terminar.

Documentos arquivados são consultados pela opção **Consultar documentos
arquivados**, na seção da viagem. Essa lista oferece **Restaurar**, sem permitir
selecionar arquivos para a prévia de e-mail. Os totais do painel contam os
documentos ativos. Uma viagem com apenas documentos arquivados continua
protegida para manter a possibilidade de restauração.

Cliente/viagem vazios são excluídos definitivamente do cadastro, sem restaurar
por esta interface. Arquivamento de documentos é lógico, reversível e não
remove, move ou sobrescreve arquivos. Não foi criado mecanismo de exclusão
definitiva de XML/PDF ou histórico.

## API

- `DELETE /api/clientes/{id}`: 204 para cliente sem vínculos (já existente).
- `DELETE /api/viagens/{id}`: 204 para viagem sem documentos/envios.
- `DELETE /api/documentos/{id}`: arquiva; 204, sem corpo.
- `POST /api/documentos/{id}/restaurar`: restaura; 200 com DocumentoResponse.
- `GET /api/viagens/{id}/documentos/arquivados`: arquivados da viagem, com os
  campos usuais e `arquivadoEm` com fuso explícito, gravado em UTC.

As consultas `/api/documentos` e `/api/viagens/{id}/documentos` retornam somente
ativos, preservando a forma do DTO anterior. Arquivar novamente é idempotente e
não altera a data original. Restaurar um documento já ativo também é idempotente.
ID inválido retorna 400; registro inexistente, 404; vínculo impeditivo, 409.

A prévia recusa documentos arquivados, inclusive quando o ID é informado
manualmente. Reimportar XML arquivado retorna 409 e orienta restaurar; não cria
novo registro e não restaura silenciosamente. Após restaurar, as verificações
normais de existência/hash do importador continuam valendo.

Arquivar ou restaurar não valida bytes/disponibilidade dos arquivos, não envia
e-mail e não modifica histórico. A possibilidade de restaurar o cadastro não
substitui backup dos arquivos. Alterações usam a trava transacional compartilhada
com o importador, em READ_COMMITTED, para coordenar requisições concorrentes.

## Migração V5 obrigatória em bancos existentes

`V5__arquivamento_documentos.sql` acrescenta `documentos.arquivado_em` opcional,
com timestamp e fuso, e um índice. Registros anteriores ficam ativos (coluna
nula), sem alterar seus outros campos. A aplicação atual exige V5 e mantém
Hibernate em `validate`; não usar `ddl-auto=update` para contornar a manutenção.

Em base nova de testes, `spring.flyway.enabled=true` aplica as migrações. Em
banco existente, parar escritores, fazer inventário, obter backup verificado da
origem correspondente e executar a CLI de manutenção offline `migrar --backup`,
conforme a Etapa 4. Não editar migrações antigas nem executar clean/repair.

Preparação feita no ambiente desta entrega: inventário operacional somente de
leitura sem impedimentos e backup verificado em
`/workspace/rotabrasil-dev/exclusoes/backup-operacional-v4-antes-v5`. O inventário
privado está em `inventario-operacional-v4.json`, sob a mesma área. O checksum
do banco permaneceu igual durante a preparação. Esses artefatos contêm dados
operacionais e não fazem parte do Git.

**A migração V5 ainda não foi aplicada ao banco operacional.** A autorização
anterior cobriu o saneamento e migração até V4. O documento mestre determina
“Não altere o banco operacional sem autorização”; a aplicação dessa nova migração
precisa de autorização específica. Nenhum registro operacional foi excluído,
arquivado ou restaurado para testar a interface.

## Validação

- 208 testes JUnit aprovados, zero falhas, erros ou ignorados: 190 anteriores e
  18 adicionais/estendidos para exclusão, arquivamento e manutenção.
- Exclusão de viagem vazia, preservação do cliente, vínculos XML/PDF/envios,
  IDs inválidos e 405 conservando o header Allow para método sem suporte.
- Ciclo de arquivar/restaurar, mesma identidade e bytes, idempotência, bloqueios
  de prévia/reimportação e concorrência com importação.
- Migração V4→V5 de base sintética com backup, preservando todas as colunas
  anteriores e arquivos. Arquivamento/ID persistem após reiniciar e permitem
  restauração sem remigrar.
- 17 verificações Playwright aprovadas, incluindo as 10 anteriores e 7 de
  confirmação/cancelamento, 204, bloqueio por vínculos, arquivamento e restauração.

O teste histórico de identidade que usa Flyway target=2 adiciona, em configuração
exclusiva de teste, apenas a coluna opcional de arquivamento. Continua sem V4
para exercitar dados históricos inválidos. A aplicação normal não desabilita
restrições nem validação do esquema.

Comandos: `rotabrasil-mvn -B clean test`, `rotabrasil-mvn -B -DskipTests package`
e `python3 scripts/testar-painel.py` com JAR atual, Java 25, Chromium e saída
externa. Relatórios, JAR e capturas ficam em `/workspace/rotabrasil-dev/exclusoes`.
Nenhum teste usa banco operacional, documento real ou transporte de e-mail.
