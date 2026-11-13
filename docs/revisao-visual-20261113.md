# Revisão visual e de navegação — 13/11/2026

**Continuação:** a [segunda revisão de simplificação](segunda-revisao-visual-20261113.md)
registra a composição mais compacta, navegação revisada e novas capturas comparáveis
nos dois temas. Os resultados abaixo permanecem como histórico da primeira revisão.

## Escopo e diagnóstico

O frontend servido foi confirmado em `back/pom.xml`: os arquivos de `front/` são
copiados para `BOOT-INF/classes/static/` no mesmo JAR da API. A reorganização local
`front/` / `back/` e as demais alterações preexistentes foram preservadas.

Problemas reproduzidos: tema escuro padrão mesmo sem preferência; paleta verde em
quase todas as superfícies; fontes auxiliares muito pequenas; indicador de viagens
com contraste insuficiente no escuro; rótulo genérico do ZIP e rótulo XML sem arquivo
selecionado. A navegação fazia posicionamento no clique e novamente no `hashchange`.
As animações de entrada atrasavam a aparição dos indicadores mesmo com dados prontos.

## Implementação

- Paleta clara padrão: fundo cinza suave, superfícies brancas, texto escuro, verde
  de marca nas ações. Tema escuro grafite com superfícies distintas e preferência
  `rotabrasil-tema` preservada.
- `painel-visual.css` refeito como linguagem visual compartilhada, com tokens para
  as duas paletas. A antiga camada `painel-diario.css` foi incorporada e retirada
  da cadeia de estilos, reduzindo sobreposições. Layouts específicos continuam nos
  arquivos de componentes existentes.
- Apresentação e login compartilham `acesso.css`, com texto legível e ação Entrar
  visível em notebook. Processamento de login e CSRF permanecem os existentes.
- Sidebar grafite compacta, grupos e SVGs consistentes; menu móvel com foco contido,
  Escape e retorno ao acionador. Atalhos e menu recolhível preservados.
- Indicadores compactos, sem destaque de alerta quando zerados; resumo mostra
  somente situações existentes. Estado vazio orienta a primeira importação ZIP.
- ZIP com etapas Selecionar → Conferir e associar → Confirmar importação, upload
  por teclado, conferência e associação manual de PDFs preservadas.
- Importação individual com nome, tipo, tamanho, remoção e substituição. Antes da
  seleção, botão desabilitado e neutro; depois, Importar XML ou Conferir PDF.
- Tabelas e ações legíveis, filtros principais visíveis e avançados sob demanda;
  cabeçalhos de tabelas vazias e paginação sem próxima/anterior útil recolhidos.
- Conta de envio, Pasta externa e Cópia automática com hierarquia compartilhada;
  autenticação, testes e histórico continuam sob demanda. Não há estado inventado
  de conexão Gmail ou comprovação de entrega.
- Navegação de abas inicia no topo sem reposicionamento duplicado. Fechar um
  diálogo preserva foco/rolagem; voltar dos documentos à lista conserva filtros e
  a posição disponível da lista. Favoritos, aliases e histórico continuam operantes.
- Editor PDF com textos auxiliares e controles maiores. Sem animações de entrada
  no trabalho diário; `prefers-reduced-motion` continua respeitado.

## Verificação executada nesta revisão

Todos os bancos/acervos usados foram sintéticos e isolados, fora do checkout.
Nenhum banco operacional foi aberto ou migrado, nenhum e-mail externo foi enviado,
nenhum serviço foi publicado. Nenhum contrato HTTP, permissão ou regra de negócio
foi alterado.

### Maven / Java 25

```text
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-design-20261113-build -DargLine=-Xmx768m clean test
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-design-20261113-build -DskipTests package
```

407 testes contabilizados, 406 aprovados, zero falhas/erros, 1 ignorado por condição
de symlink do Windows. Empacotamento aprovado, 40 recursos web copiados para o JAR.
Refinamentos posteriores foram exclusivamente frontend; o JAR foi reempacotado e
o aceite de entrada e o editor PDF foram executados novamente sobre ele.

### Navegador / Playwright 1.58.2 / Chrome

Os roteiros recebem o JAR acima e uma saída externa. A dependência já disponível
foi utilizada via `NODE_PATH`, sem instalar dependências na aplicação.

| Roteiro | Resultado desta execução |
| --- | --- |
| `scripts/testar-entrada.cjs` | 9 grupos aprovados; HTTPS `operacao` na raiz e `/rota`, login, CSRF, cookies, 3 papéis, logout e expiração; nova matriz de 12 abas × 3 resoluções × 2 temas; importação XML sintética, rótulos PDF, filtros/detalhes, volta à lista, foco móvel e persistência de tema |
| `scripts/testar-uso-diario.cjs` | 16 grupos aprovados; ZIP com 2 XMLs/2 PDFs, associações obrigatórias, confirmações, erros locais, resposta perdida/idempotência, cópia/retomada, histórico sem envio, filtros e permissões |
| `scripts/testar-previa-pdf.cjs` | 11 grupos aprovados; PDF multipágina/rotacionado, revisão em cópia, download, novo ZIP/hash, nova associação explícita, zoom, celular, cancelamento e estrutura inválida |
| `scripts/testar-zoom-uso-diario.cjs` | Zoom real 100/150/200%, sem overflow; viewport CSS medido 1350/900/675 px e DPR 1/1,5/2 |

O roteiro de PDF estava desatualizado: iniciava na apresentação e sobrepunha os
estáticos do checkout ao JAR. Foi atualizado para `/painel`, recursos empacotados e
as etapas ZIP atuais, mantendo as verificações de integridade e confirmação.

Resoluções: **1366×768, 1920×1080 e 390×844**. A matriz protegida não apresentou
overflow horizontal nem erros JavaScript. Foram verificados foco visível, seletor
de arquivo por teclado, Escape/retorno de diálogos, ciclo de foco do menu móvel e
troca de abas depois de rolar. As capturas finais da matriz aguardam consultas de
rede, evitando registrar telas ainda carregando.

Contraste medido nos controles, mensagens e auxiliares de Configurações: mínimo
**6,34:1 no claro** e **7,09:1 no escuro**, acima de AA (4,5:1). Isso é uma medição
dos elementos registrados no roteiro, não uma certificação de acessibilidade do
produto inteiro. Não houve avaliação com leitor de tela ou Safari/Firefox.
O atalho de zoom headless não alterou o viewport; apenas o roteiro separado de
preferência real do Chrome fundamenta o resultado de zoom.

## Capturas e evidências externas

Raiz: `C:/Users/florb/AppData/Local/Temp/opencode/`.

- **Antes**, capturado nesta sessão sobre JAR anterior às alterações:
  `rotabrasil-design-20261113-antes/` (apresentação, login e painel).
- **Depois / aceite final:** `rotabrasil-design-20261113-aceite/`.
  - `apresentacao-1366.png`, `login-1366.png`, `painel-1366.png` (visão vazia).
  - `design-claro-{aba}-{largura}.png`, `design-escuro-{aba}-{largura}.png`.
  - `resultado.json` registra segurança e os grupos aprovados.
- **ZIP e erros contextualizados:** `rotabrasil-design-20261113-diario-final/`,
  incluindo `zip-associacoes-1366.png`, telas das 12 abas e medições de contraste.
- **PDF:** `rotabrasil-design-20261113-pdf-final/`, incluindo editor desktop,
  celular escuro, zoom e `resultado.json`.
- **Zoom real:** `rotabrasil-design-20261113-zoom/`, com capturas 100/150/200% e
  `resultado.json`.

As primeiras rodadas encontraram overflow no seletor de viagem em 390 px e
animações legadas na entrada. Ambos foram corrigidos e revalidados. Diretórios
sem sufixo final/aceite podem conter essas evidências intermediárias; não representam
o aceite. Resultados de novembro anteriores a esta revisão permanecem históricos.

## Limitações e pendências

- Validação humana com a equipe em uso diário e leitor de tela continua útil;
  não há pendência bloqueante identificada nos roteiros executados.
- SMTP real, autenticação externa Gmail/Microsoft e entrega não foram exercitados,
  conforme o escopo. Seus estados não foram promovidos na interface.
- O JAR foi construído e iniciado somente para testes isolados. A instalação
  operacional existente não foi reiniciada nem substituída automaticamente.
