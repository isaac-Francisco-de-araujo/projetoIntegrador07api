# Painel web para testes da API

**Navegação atual em sete telas:** [exclusão de arquivos e páginas](exclusao-arquivos-e-paginas.md).
Inclui remoção de seleções, exclusão por arquivamento de ZIP/XML/PDF e restauração.

**Versão atual:** veja [modernização operacional do frontend](modernizacao-frontend.md)
para loading de cinco segundos, menu responsivo/recolhível, remoções solicitadas,
consultas completas, detalhes de clientes/viagens e importação individual XML/PDF.
Os registros a seguir mantêm as características e os resultados das versões anteriores.

**Prévia dos PDFs do ZIP:** o painel agora permite visualizar páginas, usar zoom
e preparar correções visuais em uma cópia. O novo ZIP exige nova conferência e
associação explícita, preservando XMLs e originais. Consulte
[o fluxo, os limites e os resultados dos testes](previa-e-revisao-pdf.md).

## Redesign do workspace — outubro de 2026

O painel recebeu identidade visual azul-profundo/verde-lima, navegação lateral
com seção ativa, banner com ilustração SVG local de rota/caminhão, indicadores
com atalhos e uma trilha de importação, conferência e preparação de envio.
Formulários, tabelas, históricos, administração e confirmação mantêm seus IDs
e os contratos existentes. Os totais continuam vindo do banco conectado.

- `painel-visual.css`: camada visual responsiva sobre os estilos dos estados existentes.
- `painel-interface.js`: data local e acompanhamento da seção na navegação.
- `rota-visual.svg`: ilustração decorativa original, sem recurso externo/CDN.
- Acessibilidade: link para pular navegação, foco visível, ícones decorativos
  identificados e respeito a `prefers-reduced-motion`.

Verificação deste redesign, em Chrome/Playwright com H2 em memória, acervo
temporário e CT-e sintético: **11 verificações aprovadas**, sem erro JavaScript.
Incluem inicialização/permissões locais, importação XML, seleção de documentos,
conferência simulada, busca/estado vazio, navegação ativa, movimento reduzido e
ausência de overflow horizontal nas larguras **360, 390, 768, 1024, 1440 e 1920 px**.
Empacotamento externo Maven e sintaxe dos módulos JavaScript aprovados.

Capturas e resultado: `C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-front-visual/`.
O teste de envio manual existente também avançou pelos fluxos de ZIP, diagnóstico,
simulação, reenvio e persistência após reinício; sua execução completa não foi
aprovada porque o helper `start()` esgotou a espera de disponibilidade no reinício
com autenticação (o log registra Tomcat iniciado). Não se declara validação do
login por essa execução. Nenhum e-mail real foi enviado.

### Abertura animada e transição para o workspace

A entrada agora apresenta uma tela editorial animada com mapa de conexões,
cartões flutuantes, marca e progresso das etapas efetivamente concluídas.
`painel-abertura.{js,css}` concentra essa apresentação. `painel.js` informa
interface, sessão, documentos e conclusão; falhas da consulta inicial são
propagadas após renderizar o diagnóstico, para não concluir a abertura com
dados indisponíveis.

- A primeira abertura tem uma apresentação mínima de 1,3 s; na mesma aba,
  as seguintes usam 250 ms. O carregamento dos dados nunca é ignorado.
- É possível pular o tempo de apresentação ou pausar as animações.
- `prefers-reduced-motion` remove animações e a espera de apresentação.
- O painel fica `inert` durante a abertura; a navegação por teclado só é liberada
  ao terminar. O foco é restaurado quando necessário.
- Falhas oferecem recarregamento ou acesso ao painel com diagnóstico. O progresso
  não é concluído nesse caso. A abertura começa apenas quando o módulo é executado;
  JavaScript desativado tem orientação própria, sem overlay permanente.
- A entrada dos elementos do dashboard, os atalhos e os botões receberam
  transições adicionais, respeitando movimento reduzido.

Validação Chrome/Playwright com backend real, H2 em memória e CT-e sintético:
**17 verificações aprovadas, sem erros JavaScript**. Incluem os fluxos anteriores,
progresso durante resposta atrasada, pausa, pular sem ignorar requisição pendente,
liberação do painel, falha sintética HTTP 503, recuperação e navegação por teclado.
A abertura foi conferida de 360 a 1920 px; o dashboard, nas seis larguras anteriores.
Capturas e resultado em
`C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-abertura-visual/`.

### Workspace premium: temas e central de atalhos

O dashboard ganhou composição editorial ampliada, quatro indicadores reais
(clientes, viagens, XMLs ativos e processamentos pendentes), faixa de formatos
no banner, hierarquia revisada e tema escuro completo. A preferência claro/escuro
fica em `localStorage`, com fallback quando o armazenamento não está disponível.
O botão de tema identifica a próxima ação e seu estado para leitores de tela.

A central de atalhos (`painel-comandos.js`) abre pelo botão da barra superior
ou `Ctrl+K` / `Cmd+K`. Permite pesquisar as áreas do painel sem distinção de acentos,
navegar pelas setas e abrir com Enter. Escape fecha e restaura o foco; navegar
para uma área posiciona o foco no destino. Não abre sobre outro dialog ou durante
a tela de carregamento. As ações disponíveis são navegação e alteração de tema.

As áreas de upload aceitam arrastar um único XML ou ZIP. O drop somente seleciona
o arquivo, respeita o estado desabilitado e dispara a invalidação de conferência
já existente. Tipo incorreto ou múltiplos arquivos preservam a seleção anterior
e exibem aviso. Importação e conferência continuam sendo ações explícitas.

Verificação Chrome/Playwright com backend real, H2 isolado e arquivos sintéticos:
**23 verificações aprovadas, nenhum erro JavaScript**. Além das anteriores,
incluem atalhos, foco, busca vazia, persistência de tema, indicador conectado à
API e drag-and-drop. Os dois temas foram conferidos nas larguras 360, 390, 768,
1024, 1440 e 1920 px, sem overflow horizontal da página. Capturas e resultado em
`C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-workspace-premium/`.

Os registros abaixo descrevem as versões anteriores do painel.

**Diagnóstico por arquivo:** o painel agora mantém o relatório da conferência,
filtra erros/pendências e orienta a [correção por ZIP novo](diagnostico-processamento.md).
O relatório fica na memória da página, não é histórico permanente de processamento.

**Atualização V7:** o painel existente foi ampliado com [conferência validada,
confirmação de SIMULAÇÃO, tentativas auditáveis e reenvio explícito](envio-manual.md).
Os detalhes e resultados abaixo registram a versão anterior. O novo teste
`scripts/testar-envio-manual.cjs` usa Node/Playwright com Chrome e inclui perda de
resposta, recuperação idempotente, alteração de seleção e persistência após reinício.

Atualização: o [fluxo ZIP assistido](pacotes-assistidos.md) acrescenta PDFs/faturas
compartilhados, seleção de PDFs na prévia e migração V6. Consulte esse contrato
para o comportamento atual; o envio operacional continua desativado.

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
não oferece exclusão definitiva de documentos ou associação de PDFs e não possui botão de envio real.
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
Permite excluir clientes/viagens sem vínculos e arquivar/restaurar documentos,
conforme [as regras de exclusão e migração V5](exclusoes-arquivamento.md).

## Verificação

Comando do backend: `/workspace/tools/rotabrasil-mvn -B clean test` — **208 testes
aprovados, zero falhas, erros ou ignorados**. Empacotamento também aprovado.

O teste de navegador está em `scripts/testar-painel.py`. É uma ferramenta
opcional de desenvolvimento; precisa de Python com Playwright e Chromium, mas
o painel não depende deles para funcionar. Na nuvem, com ferramentas instaladas:

```sh
python3 scripts/testar-painel.py \
  --jar /workspace/rotabrasil-dev/app.jar \
  --java /workspace/tools/jdk-25.0.5/bin/java \
  --chromium /usr/bin/chromium \
  --saida /workspace/rotabrasil-dev/exclusoes
```

O script inicia seu próprio servidor em porta aleatória, H2 e diretório
exclusivamente temporários, e os encerra ao terminar. A opção `--saida` recebe
apenas relatórios e capturas. A variável `--jar` deve apontar para um pacote
recém-compilado; não reutilize artefatos históricos de `target/`.

**17 verificações de navegador aprovadas:** estado vazio, importação completa,
prévia sem envio/histórico, duplicidade com outro nome, busca, XML inválido,
recarregamento, exibição segura de HTML malicioso como texto, recuperação após
falha de consulta e layout móvel sem rolagem horizontal da página. Não houve
erro JavaScript. São verificações adicionais, não estão incluídas nos 190 casos
JUnit. O fluxo também cobre confirmação/cancelamento, exclusões protegidas,
arquivamento com arquivo preservado e restauração.

Capturas `desktop.png` e `mobile.png`, `resultado-ui.json`, relatórios JUnit e
logs ficam fora do checkout em `/workspace/rotabrasil-dev/exclusoes`.
Banco operacional e XMLs reais têm hashes comparados antes/depois. Nenhum teste
abre o banco operacional, usa documento real ou envia e-mail. O processo usado
para a verificação é encerrado ao final; é necessário iniciar a aplicação para
usar o painel depois.
