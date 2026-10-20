# Etapa 6 — Contrato e integridade da API

Os endpoints existentes recebem e retornam DTOs. Entidades JPA, coleções
bidirecionais, proxies e caminhos físicos de arquivos não fazem parte do JSON.
Os serviços materializam as respostas dentro de transações de leitura;
`spring.jpa.open-in-view=false` dispensa sessões JPA abertas durante a resposta.

## Entradas

`POST /api/clientes` cria um cliente; `PUT /api/clientes/{id}` substitui seus
campos editáveis usando o mesmo contrato:

```json
{
  "nome": "Cliente Sintético",
  "cnpj": "00000000000000",
  "email": "contato@example.invalid",
  "telefone": "1100000000"
}
```

Nome não vazio e CNPJ com 14 dígitos são obrigatórios. Não foi introduzida
validação fiscal de dígito verificador do CNPJ. E-mail é opcional e deve ter
formato válido quando preenchido. Nome, e-mail e telefone respeitam os 255
caracteres das colunas atuais. Ausências de e-mail/telefone permanecem nulas;
não se inventam contatos. A validação do cadastro HTTP não amplia a validação
fiscal do importador XML.

`POST /api/viagens` e `PUT /api/viagens/{id}` recebem:

```json
{
  "numero": "66213",
  "data": "2025-04-04",
  "status": "Recebida",
  "clienteId": 1
}
```

Número é texto numérico com 1 a 9 dígitos e valor maior que zero, conforme o
`nCT`; clienteId deve ser um inteiro positivo e apontar para um cliente existente.
Valores decimais são rejeitados, sem truncamento para outro identificador. Data
ISO (`AAAA-MM-DD`) e status permanecem opcionais; status é texto de até 255
caracteres, sem novo enum ou fluxo operacional. PUT é substituição completa dos
campos editáveis, não atualização parcial: campos opcionais omitidos tornam-se
nulos. Identificadores nas rotas devem ser inteiros positivos.

Campos desconhecidos são rejeitados com 400. Não envie `id`, `cliente`,
`viagens`, `documentos`, `caminho` ou outros campos fora dos DTOs. A associação
de viagem usa somente `clienteId`, resolvido no serviço. Nenhum endpoint de
cadastro manual de documento foi adicionado; o XML continua no multipart
`arquivo` em `POST /api/processamento/xml`.

## Saídas

| DTO | Campos |
| --- | --- |
| ClienteResponse | id, nome, cnpj, email, telefone |
| ViagemResponse | id, numero, data, status, clienteId |
| DocumentoResponse | id, nomeArquivo, tipo, chaveAcesso, sha256, viagemId |
| ProcessamentoXmlResponse | sucesso, mensagem, viagemId, documentoId (preservado) |

Listagens continuam sendo arrays. Para consultar documentos de uma viagem use
`GET /api/viagens/{id}/documentos`. Viagem existente sem documentos retorna
`[]`; viagem inexistente retorna 404. `GET /api/documentos` retorna metadados,
sem caminho físico ou conteúdo do arquivo. Campos de identidade dos registros
legados preservam seu valor, inclusive nulo quando aplicável ao tipo documental.

## Respostas e erros

| Situação | Status |
| --- | --- |
| Consulta, atualização ou processamento XML | 200 |
| Cadastro de cliente ou viagem | 201 |
| Exclusão de cliente sem vínculos | 204, sem corpo |
| Bean Validation, JSON inválido, campos desconhecidos, ID inválido ou multipart sem arquivo | 400 |
| Recurso ou cliente referenciado inexistente | 404 |
| Método não permitido | 405, preserva header Allow |
| Formato de resposta não suportado | 406 |
| Unicidade, conflito documental ou proteção de vínculos | 409 |
| Limite multipart excedido | 413 |
| Tipo de conteúdo não suportado | 415 |
| Falha interna de banco ou armazenamento | 500 |
| Indisponibilidade por trava | 503 |

Erros tratados usam o mesmo formato:

```json
{
  "timestamp": "2026-10-20T12:00:00",
  "status": 400,
  "mensagem": "Dados de entrada inválidos",
  "campos": [{"campo": "cnpj", "mensagem": "deve conter 14 dígitos"}]
}
```

`campos` fica vazio quando o erro não é de validação de campo. Os valores
rejeitados, SQL e stack traces não são devolvidos. O tratamento conserva o
status e os headers das exceções HTTP do Spring. Limites multipart continuam
em 20 MB na configuração normal; o teste usa limites menores para exercitar 413.
Uma negociação Accept incompatível pode impedir o envio do corpo JSON do erro.

## Proteção de vínculos e exclusão

Mutações do cadastro HTTP usam a mesma linha de coordenação transacional do
importador, em `READ_COMMITTED`. A verificação dos vínculos e a alteração ocorrem
na mesma transação. As restrições V4 e as chaves estrangeiras continuam como
proteção no banco.

- Cliente com viagem ou histórico de envio não é excluído (409). Não há
  exclusão em cascata pela API; cliente sem vínculos pode ser excluído.
- CNPJ de cliente vinculado a viagem ou envio não é alterado pela API (409).
  Nome, e-mail e telefone continuam editáveis.
- Número e cliente de viagem com documentos ou histórico de envio não são
  alterados pela API (409). Data e status continuam editáveis.
- Viagem sem documentos/envios pode ser reassociada a um cliente existente.
- Não foram adicionadas exclusões de viagens, documentos, arquivos ou histórico,
  nem reconciliação automática de identidade fiscal.

Essas proteções mantêm os vínculos conhecidos; mudanças de identidade em acervos
existentes exigem reconciliação operacional separada. Não houve migração do
esquema ou alteração de dados operacionais nesta etapa.

## Compatibilidade

Rotas, verbos, arrays de listagem, códigos de sucesso e resposta da importação
foram preservados. **Há mudança no contrato JSON do CRUD**: cliente aninhado
passa a clienteId, coleções de entidades e caminho são removidos das respostas,
campos desconhecidos são recusados e entradas antes aceitas sem validação podem
retornar 400. Consumidores devem se adaptar a este documento; não há frontend
integrado validado nesta entrega.

## Verificação

`ContratoApiHttpTest` executa 37 casos em servidor HTTP real, banco H2 migrado,
raiz temporária e sem Open Session in View. Cobre todos os endpoints existentes,
ciclos CRUD permitidos, JSON inválido, validações, duplicidades com rollback,
serialização sem caminhos/recursão, ausência de recursos, vínculos de documentos
e envios, multipart, negociação de métodos e concorrência com importação.
Nenhum envio real é feito: os históricos usados nos testes são sintéticos.

A suíte completa inclui as regressões das Etapas 1–5 (108 casos) e os 37 novos,
totalizando **145 testes**, sem falhas, erros ou ignorados. Execute na nuvem:

```sh
cd /workspace/projetoIntegrador07api
/workspace/tools/rotabrasil-mvn -B clean test
/workspace/tools/rotabrasil-mvn -B -DskipTests package
```

Relatórios e artefatos atuais ficam fora do checkout em
`/workspace/rotabrasil-dev/etapa6`. Não use `target/` histórico como evidência.
A verificação do JAR usa banco e XML sintéticos. Hashes do banco operacional e
dos arquivos existentes são comparados antes/depois para conferir preservação.

Próxima etapa: documentos adicionais (Etapa 7), mantendo múltiplos documentos
por viagem. Associação automática de PDFs depende de regra operacional
confirmada; não foi implementada nesta etapa.
