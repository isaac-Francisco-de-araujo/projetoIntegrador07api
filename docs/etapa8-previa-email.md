# Etapa 8 — Preparação de e-mail sem envio real

Esta entrega prepara uma prévia de metadados e a consulta de histórico. **Não
implementa entrega de e-mail.** Destinatário operacional, SMTP, seleção de
anexos, momento de disparo e políticas de falha/reenvio continuam pendentes de
definição. Nenhuma dessas regras é inferida do cadastro ou do XML.

## Prévia

`POST /api/viagens/{viagemId}/previa-email` recebe destinatário e documentos
explicitamente escolhidos:

```json
{
  "destinatario": "conferencia@example.invalid",
  "documentoIds": [1]
}
```

Viagem e documentos precisam existir. Todos os documentos devem pertencer à
viagem indicada. Lista deve conter IDs positivos, sem nulos ou repetições;
identificadores decimais são rejeitados. Destinatário é obrigatório, com formato
de e-mail e até 255 caracteres. Campos fora do contrato retornam 400.

Resposta 200:

```json
{
  "viagemId": 1,
  "destinatario": "conferencia@example.invalid",
  "enviado": false,
  "documentos": [
    {
      "id": 1,
      "nomeArquivo": "comprovante.pdf",
      "tipo": "PDF",
      "chaveAcesso": null,
      "sha256": null,
      "viagemId": 1
    }
  ]
}
```

Os metadados refletem os registros existentes; XMLs no esquema V4 têm chave e
hash preenchidos. O exemplo representa um PDF previamente cadastrado, sem
pressupor um fluxo de associação de anexos. A prévia não usa automaticamente
o e-mail do cliente e não seleciona todos os documentos da viagem. A ordem dos
documentos segue a seleção do pedido.

**É uma conferência de metadados, não uma garantia de disponibilidade dos
anexos ou autorização de envio.** Não lê bytes, não valida disponibilidade,
conteúdo ou hash dos arquivos e não monta mensagem MIME. Arquivo indisponível
pode continuar representado na prévia pelos metadados cadastrados. Um fluxo real
futuro terá de verificar novamente relações, arquivos e autorização no momento
da tentativa de envio.

A operação é somente de leitura: não cria `PENDENTE`, `ENVIADO` ou `ERRO`, não
altera data de envio e não agenda trabalho. Repetir a prévia não cria histórico,
nem implica garantia de idempotência de um futuro transporte.

## Histórico existente

`GET /api/viagens/{viagemId}/envios` retorna registros já existentes, em ordem
crescente de ID. Viagem existente sem histórico retorna `[]`; inexistente retorna
404. A ordem indica os IDs dos registros, sem inferir cronologia de entrega.

Campos do DTO: `id`, `viagemId`, `clienteId`, `dataEnvio` e `status`. Campos
nulos e estados existentes são preservados. Não são expostas entidades JPA nem
mensagens internas, que podem conter diagnósticos privados. O esquema atual não
registra destinatário e seleção exata de documentos; portanto esta consulta não
reconstrói essas informações nem comprova entrega ao destinatário.

Não há `POST /envios` nem endpoint de envio/reenvio. A chamada interna histórica
`EmailService.enviarDocumentos` continua lançando exceção antes de qualquer
transporte. O fluxo operacional não ganhou flag de ativação, agendador ou transporte.
Posteriormente foi preparada uma [CLI isolada de teste Gmail](teste-gmail.md),
com dependência de e-mail e anexo sintético, mediante autorização específica.
Ela não habilita envio de documentos operacionais.

## Erros e compatibilidade

Formato padronizado da Etapa 6, sem caminhos internos ou valores rejeitados:

- 400: entrada inválida, IDs repetidos ou parâmetros inválidos.
- 404: viagem ou documento inexistente.
- 409: documento pertence a outra viagem; não ocorre reassociação.
- 405: tentativa de cadastrar envio pelo endpoint de consulta.

Endpoints e resposta de importação anteriores foram preservados. Não há migração
ou alteração das tabelas, registros operacionais ou arquivos reais.

## Verificação

`PreviaEmailHttpTest`: 19 casos em HTTP real, H2 isolado migrado e raiz temporária.
Verifica seleção explícita, destinatário independente do cadastro, repetição sem
histórico, preservação dos bytes, validações, documentos de outra viagem,
consultas de histórico, ausência de mensagens internas e bloqueio do transporte.
Registros ENVIADO/PENDENTE/ERRO usados nos testes são fixtures sintéticas
inseridas diretamente, sem tentativa de e-mail.

Suíte completa: **177 testes, zero falhas, erros ou ignorados** (158 anteriores e
19 novos). Na nuvem:

```sh
cd /workspace/projetoIntegrador07api
/workspace/tools/rotabrasil-mvn -B clean test
/workspace/tools/rotabrasil-mvn -B -DskipTests package
```

Resultados em `/workspace/rotabrasil-dev/etapa8`: `testes.log`, `relatorios`,
`resultado-testes.json` e JAR. O smoke do JAR usa somente dados sintéticos.
Hashes do banco operacional e arquivos reais são comparados antes/depois para
conferir preservação. Nenhum teste usa o banco operacional ou envia e-mail.

## Necessário antes da entrega real

Definir e autorizar:

1. Origem e confirmação do destinatário, remetente e permissão de envio.
2. Documentos selecionáveis, limites de anexos e tratamento de indisponibilidade.
3. Disparo manual ou automático e condições operacionais.
4. Host, porta, TLS e autenticação SMTP; credenciais devem ser cadastradas de
   forma segura nas configurações, nunca no chat ou no Git.
5. Histórico auditável com destinatário e documentos exatos da tentativa.
6. Falhas, repetição, prevenção de duplicidades e resultado desconhecido. Aceite
   pelo servidor SMTP não deve ser confundido com entrega ao destinatário.

Essas definições e a implementação/verificação do transporte faltam para
concluir a Etapa 8 de envio real. Não iniciar disparos apenas porque a prévia
retorna 200. A finalização geral da Etapa 9 deve refletir esse limite.
