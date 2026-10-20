# Painel web para testes da API

Frontend estático em HTML, CSS e JavaScript, servido pelo próprio Spring Boot
na rota `/`. Não exige React, Node, npm, CDN ou um segundo servidor para usar.
Os pedidos usam os endpoints existentes da mesma origem; não foi necessário
abrir CORS ou alterar os contratos da API.

## Funcionalidades

- Resumo com quantidades reais de clientes, viagens e documentos da API.
- Importação multipart de XML CT-e, com resposta de sucesso, duplicidade ou erro.
- Busca de viagens por nCT ou nome do cliente e consulta dos seus documentos.
- Consulta dos clientes e do histórico de envios da viagem selecionada.
- Seleção explícita de documentos e destinatário para a prévia de e-mail.
- Estados vazios, carregamento, erros e recuperação pela atualização dos dados.
- Layout adaptado para desktop e celular, controles rotulados e avisos acessíveis.

O painel não cria dados fictícios ao abrir, não cadastra contatos implicitamente,
não oferece exclusão ou associação de PDFs e não possui botão de envio real.
A prévia usa `POST /api/viagens/{id}/previa-email` e não verifica os anexos nem
cria histórico. A CLI de teste Gmail permanece um fluxo separado.

Dados da API são inseridos com `textContent` e elementos DOM, sem interpretar
nomes, arquivos ou mensagens como HTML. Alterar viagem, seleção ou destinatário
invalida a prévia anterior. Uma falha de consulta limpa a seleção e mantém o
botão de prévia bloqueado; respostas antigas de outra seleção são ignoradas.

## Executar em banco de testes

O painel usa o banco configurado no servidor. O rótulo de testes da tela **não**
substitui a configuração de uma base isolada. Importar XML grava dados e
arquivos. Para testar, crie uma base e raiz novas; não use o banco operacional.

Em Bash, a partir do checkout, com Java 25 e Maven:

```sh
ROTA_PAINEL_DIR=$(mktemp -d /tmp/rotabrasil-painel.XXXXXX)
mvn clean spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:file:$ROTA_PAINEL_DIR/banco --spring.flyway.enabled=true --rotabrasil.arquivos.viagens=$ROTA_PAINEL_DIR/viagens --spring.h2.console.enabled=false --server.port=18083"
```

Na nuvem, use `/workspace/tools/rotabrasil-mvn` no lugar de `mvn`.
Abra a rota `/` do servidor na porta configurada. O painel e a API são servidos
pelo mesmo processo; abrir o HTML diretamente do disco não funciona.

Pode usar `src/main/resources/email/cte-sintetico.xml` para uma importação
sintética, sem valor fiscal. Reimportá-lo com outro nome deve reutilizar os IDs.
Nenhum documento fiscal real é necessário para esse teste.

O painel é de apoio aos testes locais, sem autenticação e sem pretensão de ser
o frontend final. Não implementa cadastro/edição manual de cliente/viagem,
download de arquivo, associação de anexos ou envio operacional de e-mail.

## Verificação

Comando do backend: `/workspace/tools/rotabrasil-mvn -B clean test` — **190 testes
aprovados, zero falhas, erros ou ignorados**. Empacotamento também aprovado.

O teste de navegador está em `scripts/testar-painel.py`. É uma ferramenta
opcional de desenvolvimento; precisa de Python com Playwright e Chromium, mas
o painel não depende deles para funcionar. Na nuvem, com ferramentas instaladas:

```sh
python3 scripts/testar-painel.py \
  --jar /workspace/rotabrasil-dev/frontend/app.jar \
  --java /workspace/tools/jdk-25.0.5/bin/java \
  --chromium /usr/bin/chromium \
  --saida /workspace/rotabrasil-dev/frontend
```

O script inicia seu próprio servidor em porta aleatória, H2 e diretório
exclusivamente temporários, e os encerra ao terminar. A opção `--saida` recebe
apenas relatórios e capturas. A variável `--jar` deve apontar para um pacote
recém-compilado; não reutilize artefatos históricos de `target/`.

**10 verificações de navegador aprovadas:** estado vazio, importação completa,
prévia sem envio/histórico, duplicidade com outro nome, busca, XML inválido,
recarregamento, exibição segura de HTML malicioso como texto, recuperação após
falha de consulta e layout móvel sem rolagem horizontal da página. Não houve
erro JavaScript. São verificações adicionais, não estão incluídas nos 190 casos
JUnit.

Capturas `desktop.png` e `mobile.png`, `resultado-ui.json`, relatórios JUnit e
logs ficam fora do checkout em `/workspace/rotabrasil-dev/frontend`.
Banco operacional e XMLs reais têm hashes comparados antes/depois. Nenhum teste
abre o banco operacional, usa documento real ou envia e-mail. O processo usado
para a verificação é encerrado ao final; é necessário iniciar a aplicação para
usar o painel depois.
