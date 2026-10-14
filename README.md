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

## Arquitetura

`Controller -> Service -> Repository -> H2`.

O Java concentra o processamento. Os scripts Python são auxiliares opcionais para validação estrutural e cópia segura de arquivos.

## XML e mapeamento

O XML é a entrada principal. O XML real da empresa ainda não foi fornecido, portanto o projeto não inventa nomes de elementos, CNPJ, e-mail, número ou identificador de viagem.

A classe `ExtratorDadosXml` valida o XML com parser DOM seguro e é o ponto específico onde os caminhos reais deverão ser configurados. O `ProcessamentoService` interrompe o fluxo de negócio enquanto não houver identificador mapeado. Assim, o XML somente é salvo depois de existir um identificador válido.

## Teste com XML fictício

O arquivo `arquivos/entrada/viagem-teste.xml` existe exclusivamente para testar o fluxo do sistema. Seu formato é fictício e não representa o XML real da empresa. O mapeamento temporário está implementado em `ExtratorDadosXml.java` e deverá ser substituído ou adaptado quando o XML real estiver disponível.

O teste processa o XML, identifica `TESTE-001`, cria ou reutiliza o cliente fictício, cria ou reutiliza a viagem, registra o documento e organiza o arquivo em `arquivos/viagens/TESTE-001/`. Nenhum envio real de e-mail é realizado.

## Execução

Requisitos: Java 25 e Maven compatível.

```text
mvn spring-boot:run
```

O H2 é criado em memória com `jdbc:h2:mem:rotabrasil`. O console fica em `/h2-console`, usando JDBC URL `jdbc:h2:mem:rotabrasil`, usuário `sa` e senha vazia.

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

## Arquivos

- `arquivos/entrada`: área destinada aos arquivos recebidos.
- `arquivos/viagens/{identificador}`: destino do XML somente após identificação válida.

A entidade `Viagem` mantém `List<Documento>`, permitindo qualquer quantidade de XMLs, PDFs e outros documentos.

## E-mail

`EmailService` está preparado como ponto de integração, mas não envia e-mails reais. SMTP não foi configurado porque servidor, credenciais e política de envio ainda não foram fornecidos.

## Testes

```text
mvn test
```

## Próximo passo

Fornecer um XML real anonimizado ou a documentação oficial do formato. Então devem ser configurados exclusivamente os caminhos em `ExtratorDadosXml`, além da regra confirmada de identificação de viagem e cliente.
