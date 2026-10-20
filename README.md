# RotaBrasil

Backend inicial para automatizar o recebimento, organização e histórico de documentos de viagens de caminhão.

## Tecnologias

- Java 25
- Maven
- Spring Boot 3.5
- Spring Web
- Spring Data JPA
- H2 Database
- Lombok
- Flyway (migrações versionadas)

## Arquitetura

`Controller -> Service -> Repository -> H2`.

O Java concentra o processamento. Os scripts Python são auxiliares opcionais para validação estrutural e cópia segura de arquivos.

## XML e mapeamento

A entrada principal é CT-e 4.00 (`CTe` ou `cteProc`) no namespace fiscal.
`ide/nCT` identifica a viagem e `dest` identifica o cliente. O extrator exige
nome e CNPJ do destinatário; e-mail e telefone ausentes permanecem nulos.

A chave de `infCte/@Id`, com prefixo `CTe` opcional, deve ter 44 dígitos,
modelo 57 e dígito verificador módulo 11 válido. O número do CT-e deve
corresponder ao número na chave. Havendo protocolo, sua chave deve coincidir.
Essas verificações não substituem validação XSD, assinatura digital ou consulta
à SEFAZ. XML sem protocolo não é tratado como autorizado.

A chave e o SHA-256 são persistidos para documentos novos. Reimportar a mesma
chave com os mesmos bytes reutiliza o registro, mesmo com outro nome; conteúdo
diferente retorna HTTP 409. A comparação ocorre antes da escrita. Arquivos
novos usam `{nCT}/{chaveAcesso}.xml`, sem substituir destinos existentes.

Registros antigos só recebem os novos campos em migração explícita, com
inventário e backup verificados. Na execução normal, são conferidos pelos
arquivos; inconsistências exigem reconciliação.
A Etapa 3 prepara o XML em área temporária, publica sem sobrescrita na confirmação
da transação e compensa gravações próprias em rollback. Operações incompletas
mantêm um journal para inspeção e recuperação conservadora. A Etapa 4 acrescenta
restrições únicas e coordenação transacional no banco para reutilizar documentos
também nas importações concorrentes.

Veja [identidade e compatibilidade da Etapa 2](docs/etapa2-identidade-documental.md),
[protocolo e recuperação da Etapa 3](docs/etapa3-armazenamento-seguro.md)
e [inventário, backup e migrações da Etapa 4](docs/etapa4-banco-migracoes.md).

Os testes utilizam dados sintéticos e diretórios temporários, sem abrir o banco
operacional. Não utilize documentos fiscais reais em novos testes.

## Execução

Requisitos: Java 25 e Maven compatível.

O banco conhecido é H2 em arquivo. A aplicação agora valida o esquema com
`ddl-auto=validate`; migrações automáticas ficam desativadas e a URL padrão
exige banco existente. Um banco legado precisa passar pela manutenção offline
antes de iniciar esta versão. Não use `ddl-auto=update` para contornar isso.

Para desenvolvimento em banco novo e isolado:

```sh
mvn clean spring-boot:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:rotabrasil_dev --spring.flyway.enabled=true --rotabrasil.arquivos.viagens=/tmp/rotabrasil-dev/viagens --spring.h2.console.enabled=false"
```

Use uma raiz vazia nova para esse banco novo. A raiz precisa suportar hard links
e travas de arquivo. Consulte a [Etapa 4](docs/etapa4-banco-migracoes.md) para
inventário, backup e implantação em bancos existentes. O H2 Console padrão
continua em `/h2-console`, com usuário `sa` e senha vazia, se habilitado.

## Endpoints

- `GET /api/clientes`
- `GET /api/clientes/{id}`
- `POST /api/clientes`
- `PUT /api/clientes/{id}`
- `DELETE /api/clientes/{id}`
- `GET /api/viagens`
- `GET /api/viagens/{id}`
- `POST /api/viagens`
- `PUT /api/viagens/{id}`
- `GET /api/documentos`
- `GET /api/viagens/{id}/documentos`
- `POST /api/processamento/xml` com multipart `arquivo`
- `POST /api/viagens/{id}/previa-email` (conferência de metadados, sem envio)
- `GET /api/viagens/{id}/envios` (histórico existente)

Os contratos de entrada/saída, validações, erros e proteções de exclusão estão
na [Etapa 6](docs/etapa6-contrato-api.md). As respostas usam DTOs sem entidades
aninhadas ou caminhos internos. Cadastro de viagem recebe `clienteId`; campos
fora do contrato retornam 400. Consumidores do CRUD devem adaptar o JSON.

## Arquivos

- `arquivos/entrada`: área destinada aos arquivos recebidos.
- `arquivos/viagens/{nCT}/{chaveAcesso}.xml`: destino de novos XMLs após identificação e conferência de duplicidade. Arquivos legados conservam seus caminhos.
- `arquivos/viagens/.pendentes/op-{UUID}`: temporário, metadados e trava das operações ainda não concluídas. Não apague essa área indiscriminadamente.

A entidade `Viagem` mantém `List<Documento>`, permitindo múltiplos documentos
por viagem. A [Etapa 7](docs/etapa7-documentos-adicionais.md) protege a coleção
contra exclusão automática e verifica consultas de tipos mistos já cadastrados.
Upload e associação de PDFs/outros anexos dependem de regra operacional confirmada.

## E-mail

A [preparação da Etapa 8](docs/etapa8-previa-email.md) permite consultar o
histórico e conferir uma seleção explícita de documentos e destinatário. A
prévia sempre informa `enviado=false`, não valida os bytes dos anexos nem cria
histórico. Envio real e SMTP continuam indisponíveis; regras operacionais e
configuração ainda precisam ser definidas. Há também uma [CLI de teste Gmail](docs/teste-gmail.md)
com anexo sintético, para execução manual após configurar senha de app e
conectividade. Nenhum e-mail real foi enviado nesta preparação.

## Testes

```text
mvn clean test
```

A suíte tem 190 testes aprovados: os 108 casos das Etapas 1–5, incluindo
[HTTP real, falhas, segurança XML e reinicialização](docs/etapa5-testes-integracao.md),
37 casos de [contrato e integridade da API](docs/etapa6-contrato-api.md) e
13 de [preparação para documentos adicionais](docs/etapa7-documentos-adicionais.md)
19 de [prévia e histórico de e-mail](docs/etapa8-previa-email.md) e
13 de [teste manual Gmail](docs/teste-gmail.md).

## Próximo passo

O vínculo legado foi corrigido com autorização, e o banco fornecido foi migrado
até V4. Inventário, preservação dos dados e inicialização com `validate` foram
conferidos; veja a [Etapa 4](docs/etapa4-banco-migracoes.md).

A Etapa 7 prepara e verifica múltiplos documentos por viagem, preservando o
acervo. Associação de PDFs/outros anexos depende de regra operacional confirmada.
A Etapa 8 tem prévia e consulta de histórico preparadas. Para implementar envio
real, faltam autorização e definição do destinatário, SMTP, documentos, momento
de envio e políticas de histórico/falhas/reenvio. Nenhum envio real está ativo.
