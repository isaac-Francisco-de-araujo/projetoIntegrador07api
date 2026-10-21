# Etapa 7 — Preparação para documentos adicionais

Atualização: o [fluxo ZIP assistido](pacotes-assistidos.md) acrescenta PDFs/faturas
compartilhados, seleção de PDFs na prévia e migração V6. Consulte esse contrato
para o comportamento atual; o envio operacional continua desativado.

Uma viagem mantém uma coleção de documentos, sem quantidade fixa nem campos
separados como XML1/PDF1. Cada documento tem seu próprio ID e aponta para uma
viagem por `viagem_id`. O esquema V4 já comporta essa relação; não foi necessária
nova migração ou alteração no banco operacional.

## Modelo e preservação

`Viagem.documentos` conserva as cascatas PERSIST e MERGE, mas não REMOVE nem
`orphanRemoval`. Limpar ou substituir a coleção inversa deixa de excluir
registros do acervo. A associação persistida pertence a `Documento.viagem`;
modificar apenas a coleção inversa não reassocia os documentos. Consultar a
viagem novamente recupera sua coleção persistida.

Excluir diretamente uma viagem com documentos falha por chave estrangeira,
sem apagar documentos em cascata. A API continua sem endpoint de exclusão de
viagens ou documentos; exclusão de cliente vinculado permanece bloqueada.
Arquivos não são apagados pela edição dessas coleções.

O modelo preserva `tipo` como texto, sem impor uma taxonomia ainda não definida.
Nome original não é identificador único: documentos distintos podem ter o mesmo
nome, com registros e caminhos próprios. Não se infere viagem a partir de nome,
extensão ou posição na coleção.

## Identidade fiscal e outros tipos

Chave fiscal de CT-e e SHA-256 continuam obrigatórios para XML conforme V4.
Documentos de outros tipos já existentes podem ter esses campos nulos; não se
fabrica chave fiscal para PDF ou outro anexo. As restrições existentes não foram
relaxadas nem editadas retroativamente.

`DocumentoService.registrarDocumento` é o cadastro usado pelo importador CT-e e
agora recusa explicitamente tipos diferentes de XML, mesmo com chave/hash
preenchidos. Ele não se torna uma entrada genérica de anexos. Importação e
reimportação de XML preservam os outros documentos da mesma viagem.

A consulta usa os DTOs da Etapa 6:

- `GET /api/documentos`: metadados de todos os tipos cadastrados.
- `GET /api/viagens/{id}/documentos`: todos os documentos daquela viagem.

As respostas mantêm `id`, `nomeArquivo`, `tipo`, `chaveAcesso`, `sha256` e
`viagemId`, sem caminho físico, conteúdo ou entidades aninhadas. Uma viagem que
possui apenas documentos não XML também conserva as proteções de identidade
(número e cliente) da Etapa 6.

## Limite operacional

Esta etapa prepara e verifica o modelo e a consulta de acervos mistos. Não cria
um fluxo de upload, cadastro ou associação de PDF/outros anexos. A possibilidade
de associá-los depende das regras operacionais confirmadas pelo responsável,
como previsto no documento mestre. Não há processamento ZIP, geração de PDF,
OCR, associação por nome, substituição de arquivo ou envio de e-mail.

Antes de implementar a associação, é necessário definir a indicação explícita
da viagem, tipos aceitos, limites, critério de duplicidade e comportamento de
conflitos/correções. O protocolo de integridade entre banco e arquivo também
precisa ser aplicado ao fluxo aprovado. Não existe ainda uma garantia genérica
de deduplicação ou validação de conteúdo para documentos não XML.

## Verificação

`DocumentosAdicionaisIntegrationTest` usa H2 em arquivo migrado, servidores HTTP
em portas aleatórias, diretórios temporários e fixtures sintéticas. Documentos
não XML são inseridos diretamente pelo teste para representar registros
preexistentes. Essa montagem de fixture não é uma implementação de associação.
Os bytes com cabeçalho PDF servem para verificar preservação, não validação do
formato ou renderização de PDF.

Os 13 casos verificam:

- Dois XMLs, dois PDFs com mesmo nome e um TXT na mesma viagem, sem recursão ou
  exposição de caminhos, e consultas isoladas entre viagens.
- Reimportação de XML sem adotar ou alterar documentos adicionais.
- Coleção inversa esvaziada sem exclusão; tentativa de excluir viagem com
  documentos rejeitada no banco, preservando registros e arquivos.
- Proteções da API para uma viagem com somente PDF.
- Recusa de tipos não XML pelo cadastro CT-e e manutenção da restrição fiscal XML.
- Reinicialização com Flyway desativado, IDs e metadados preservados.
- Inventário e backup existentes cobrindo todos os arquivos e interpretando
  identidade fiscal somente dos XMLs. O arquivo ZIP desse teste é o backup
  nativo H2 já existente, não uma entrada documental do produto.

Resultado da suíte completa: **158 testes, zero falhas, erros ou ignorados**
(145 anteriores e 13 novos). Na nuvem:

```sh
cd /workspace/projetoIntegrador07api
/workspace/tools/rotabrasil-mvn -B clean test
/workspace/tools/rotabrasil-mvn -B -DskipTests package
```

Relatórios atuais em `/workspace/rotabrasil-dev/etapa7/relatorios`, com resumo
`resultado-testes.json` e log `testes.log` na mesma área. O JAR também é iniciado
separadamente em base sintética para conferir importação XML, consulta de DTOs,
vínculos e preservação dos bytes. Os testes de coleção mista acima exercitam o
modelo e a API; o smoke do JAR exercita o fluxo XML.

Hashes do banco operacional e dos arquivos reais são comparados antes/depois.
Nenhum teste abre o banco operacional; não há cadastro de documentos reais ou
migração nesta entrega. Builds usam `clean` para evitar artefatos históricos de
`target/`; resultados atuais são preservados fora do checkout.

## Compatibilidade e próximo passo

O contrato HTTP da Etapa 6 permanece igual. Código interno que dependesse de
exclusão por limpar a coleção ou por cascata de viagem passa a preservar os
documentos e, no caso da exclusão da viagem, receber violação de integridade.
Reconciliação ou exclusão autorizada exigem um fluxo explícito, não alterações
incidentais na coleção.

Etapa 8: envio de e-mails, somente após autorização e definição da origem do
destinatário, SMTP, seleção de documentos, momento de envio, histórico, falhas e
política de reenvio. SMTP continua desativado.
