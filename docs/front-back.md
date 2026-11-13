# Organização do frontend e backend

**Continuação de 11/11/2026:** a falha anterior de espera do cadastro em `/rota`
foi investigada; o roteiro agora aguarda a resposta do POST e a atualização da lista.
Entrada HTTPS na raiz e em `/rota` passou com Playwright 1.58.2 (8 grupos).
Veja [o aceite atual](preparacao-compartilhada-gmail-20261111.md); os resultados
da primeira reorganização abaixo permanecem como registro daquela execução.

```text
front/                  HTML, CSS, JavaScript e imagens da interface
back/
  pom.xml               Dependências e build Spring Boot
  src/main/java/        API, segurança e regras de negócio
  src/main/resources/   Configurações, migrações e fixture sintética
  src/test/             Testes Java e fixtures
pom.xml                 Agregador Maven para comandos na raiz
```

## Comunicação

O Maven copia os recursos web de `front/` para `static/` no classpath do backend.
O Spring Boot serve a interface e a API no mesmo endereço, normalmente
`http://localhost:8080`. O JavaScript continua usando as rotas `/api/...`, com
o suporte existente a caminho de contexto, sessão, permissões e CSRF.
As bibliotecas PDF e ZIP continuam disponíveis nos WebJars do backend.

Não é necessário iniciar um segundo servidor para o frontend. Abra a URL da
aplicação; abrir `front/index.html` diretamente como arquivo não fornece a API.
Após editar `front/`, recompile o JAR para distribuir os recursos atualizados.

## Build e execução

Na raiz do repositório, com Java 25 e Maven:

```powershell
$Build = Join-Path $env:TEMP 'opencode/rotabrasil-front-back-build'
mvn -B "-Drotabrasil.build.directory=$Build" clean test
mvn -B "-Drotabrasil.build.directory=$Build" -DskipTests package
```

O resultado é `$Build/rota-brasil-0.0.1-SNAPSHOT.jar`, contendo frontend e backend.
O módulo também pode ser compilado com `mvn -f back/pom.xml`, usando os mesmos
argumentos. Consulte [Como executar localmente](../README.md#como-executar-localmente)
para iniciar com banco e acervo isolados. Execute o JAR a partir da raiz do
repositório se utilizar configurações de caminhos relativos existentes; iniciar
dentro de `back/` muda a resolução desses caminhos. Na implantação, prefira
caminhos absolutos para banco, acervo e exportação.

`database/` e `arquivos/` permanecem em seus locais existentes. Esta reorganização
não exige mudança de esquema nem migração do banco.

Os documentos de etapas anteriores conservam os caminhos históricos `src/...`;
os caminhos atuais são `back/src/...` e, para a interface, `front/`.

## Verificação da reorganização — 11/11/2026

- Build limpo externo pela raiz: 402 testes JUnit, 401 aprovados, zero falhas/erros
  e 1 ignorado (symlink no Windows). Usado `-DargLine=-Xmx768m`.
- Empacotamento externo aprovado; 41 recursos de `front/` copiados para `static/`.
- Roteiro `scripts/testar-entrada.cjs`: fluxo operacional HTTPS na raiz concluído,
  incluindo apresentação, login, painel, gestão de operadores, permissões e logout.
  No caminho de contexto `/rota`, apresentação, login e painel carregaram, mas
  o roteiro interrompeu ao aguardar o operador `contexto-operador` aparecer na lista.
  O roteiro completo de navegador, portanto, não é declarado aprovado.
- Logs e capturas externos: diretórios/arquivos com prefixo
  `C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-separacao-20261111-`.
