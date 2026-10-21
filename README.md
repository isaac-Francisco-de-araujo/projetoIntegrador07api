# RotaBrasil

Backend inicial para automatizar o recebimento, organização e histórico de documentos de viagens de caminhão.

O painel já oferece [importação assistida de ZIP](docs/pacotes-assistidos.md):
conferência dos CT-es, associação manual de PDFs/faturas a uma ou várias viagens,
arquivamento/restauração e prévia com destinatário e anexos explícitos. O envio
operacional de e-mail continua desativado. Esta versão exige migrações até **V6**;
para testar, use banco novo separado conforme a documentação.

## Documento geral do projeto

Consulte a [visão geral do sistema](docs/visao-geral-do-sistema.md) para entender
o que já funciona, o objetivo da entrega, as decisões pendentes e os próximos
passos. Também disponível em [PDF](docs/visao-geral-do-sistema.pdf) e
[Word](docs/visao-geral-do-sistema.docx), com referência em 21/10/2026.

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

## Painel de testes

A rota `/` apresenta um painel web para importar XML, consultar clientes,
viagens e documentos e gerar prévia de e-mail. Ele é servido pelo próprio
Spring Boot, sem instalar um projeto frontend separado. Não há disparo de e-mail
pela tela. Use uma base isolada e documentos sintéticos; veja as
[instruções do painel e teste de navegador](docs/frontend-testes.md).

Veja também [exclusões e arquivamento reversível](docs/exclusoes-arquivamento.md).
**Banco existente precisa das migrações até V6 antes de iniciar esta versão.**

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
- `DELETE /api/viagens/{id}` (sem documentos, anexos ou envios)
- `GET /api/documentos` (ativos)
- `DELETE /api/documentos/{id}` (arquivamento)
- `POST /api/documentos/{id}/restaurar`
- `GET /api/viagens/{id}/documentos/arquivados`
- `GET /api/viagens/{id}/documentos`
- `POST /api/processamento/xml` com multipart `arquivo`
- `POST /api/pacotes/inspecao` com multipart `arquivo`
- `POST /api/pacotes/confirmacao` com multipart `arquivo` e JSON `configuracao`
- `GET /api/viagens/{id}/anexos` (ativos; `?arquivados=true` para arquivados)
- `DELETE /api/anexos/{id}` (arquivamento do PDF em todas as viagens vinculadas)
- `POST /api/anexos/{id}/restaurar`
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
PDFs podem ser importados pelo ZIP com associação explícita a uma ou várias
viagens; veja o [fluxo assistido](docs/pacotes-assistidos.md). Outros tipos de
anexo não são aceitos nessa entrada.

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

A última execução da suíte teve **234 testes aprovados**, sem falhas, erros ou
casos ignorados. Inclui os 208 cenários anteriores e 26 novos casos do fluxo ZIP,
associações, concorrência, rollback, reinicialização, recuperação e backup.
Há também **25 verificações de navegador**: 17 do painel e 8 do fluxo ZIP.
Os testes utilizam exclusivamente documentos sintéticos e bancos isolados.

## Próximo passo

O vínculo legado foi corrigido com autorização, e o banco fornecido foi migrado
até V4. Inventário, preservação dos dados e inicialização com `validate` foram
conferidos; veja a [Etapa 4](docs/etapa4-banco-migracoes.md).

A importação assistida de ZIP e a associação manual de PDFs/faturas já estão
implementadas. A prévia permite conferir destinatário, XMLs e PDFs, sem envio.
Os próximos passos são validar amostras anonimizadas e regras comerciais,
preparar o banco existente com migrações até V6 quando autorizado e implementar
envio com auditoria, falhas e confirmação de reenvio. A integração Outlook e o
recebimento automático do ZIP ainda dependem de definição e implementação.
Veja a [visão geral e o plano de continuidade](docs/visao-geral-do-sistema.md).
