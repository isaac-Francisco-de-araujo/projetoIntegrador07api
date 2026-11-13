# Abertura animada de 5 segundos e favicon oficial — 13/11/2026

**Atualização de duração:** a pedido do usuário, a duração mínima atual passou a
**10 segundos**. As medições de 5 segundos abaixo registram o aceite anterior.

## Requisito atual

Esta entrega **substitui a orientação anterior de não ter duração mínima**.
A landing e o formulário de acesso continuam leves e independentes da abertura.
A apresentação é exclusiva da inicialização de cada documento do painel.

- Caminhão desenhado em SVG, com rodas girando, suspensão sutil e faixas da
  estrada se movendo continuamente. A imagem da landing não é animada como caminhão.
- Logo oficial localizada e conferida em `back/src/test/imagem/Logo R Futurista com
  Estrada Neon.png`, reutilizando `assets/marca/logo-rotabrasil.webp`.
- Fundo escuro crítico já no HTML, composição centralizada e responsiva, texto
  “Preparando seu espaço de trabalho”, sem porcentagem inventada.
- Inicialização real acontece em paralelo. São obrigatórios contrato da versão,
  sessão/permissões e configuração dos fluxos essenciais. Consultas independentes e
  módulos secundários continuam com isolamento de falhas.
- A liberação exige requisitos essenciais **e** aproximadamente 5 segundos desde
  a entrada. Se o sistema ainda não estiver pronto, o estado real permanece visível.
- “Pular animação” remove somente a espera visual e interrompe o movimento.
  Mantém foco acessível enquanto aguarda os requisitos reais; ao liberar, o foco
  vai ao título do painel sem rolar. A entrada normal não toma o foco.
- `prefers-reduced-motion` produz composição estática, indicador discreto e opção
  de pular. A região de status anuncia apenas mudanças reais, sem contagem periódica.
- Painel inerte durante a abertura. Trocas de abas, ajuda, atualização de dados e
  revalidação de sessão não reiniciam a apresentação.
- Falha essencial interrompe imediatamente a espera e mostra diagnóstico e recarga;
  sessão encerrada oferece entrar novamente. Há limite de 45 segundos para
  inicialização presa, sem repetição automática de operações.

O único agendamento da apresentação fica em `painel-emergencia.js`: prazo máximo
enquanto inicializa, substituído pelo restante visual quando os essenciais passam.
Conclusão/falha cancelam esse agendamento. Não há bloqueio da thread ou polling.
O canal clássico funciona mesmo quando o módulo principal não carrega.

## Favicons

Derivados da logo oficial, com recorte das margens transparentes/halo tênue,
proporção preservada e base grafite arredondada para contraste nos dois temas:

- `front/assets/marca/favicon-16.png`: 16×16.
- `front/assets/marca/favicon-32.png`: 32×32.
- `front/favicon.ico`: duas imagens DIB de 32 bits, 16×16 e 32×32.
- Geração reproduzível: `node scripts/preparar-favicons.cjs` com Playwright de testes.

Landing, login protegido, acesso local e painel usam os três recursos locais
com `?v=rb-20261113-1`. Seus títulos específicos foram preservados, inclusive os
títulos dinâmicos por aba do painel. O Maven empacota PNG/ICO em `static/` no mesmo
JAR e o acesso público limita-se aos caminhos exatos dos ícones. O contexto é
aplicado pelo controlador de entrada e pelo HTML de login, inclusive em `/rota`.

**Conferir atualização de cache:** em DevTools → Network, confira as URLs com
`v=rb-20261113-1` e resposta 200. Abra o PNG de 32 px diretamente com essa query.
Se a aba ainda mostrar o ícone anterior, feche e reabra a aba; teste também num
perfil/janela privada. Na próxima alteração da arte, atualize a versão nos dois
HTMLs e no `LoginController` conjuntamente.

## Verificações executadas

Build Java 25 externo, sem tocar no target histórico:

```text
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-abertura-5s-20261113-build -DargLine=-Xmx768m clean test package
```

**BUILD SUCCESS: 407 testes, 406 aprovados, zero falhas/erros, 1 ignorado**
(condição de symlink no Windows). Log externo `rotabrasil-abertura-5s-20261113-maven.log`.

Chrome com Playwright **1.58.2**, carregando o frontend do JAR sem sobrepor estáticos:

```text
node scripts/testar-apresentacao-carregamento.cjs C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-abertura-5s-20261113-build/rota-brasil-0.0.1-SNAPSHOT.jar C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-abertura-5s-20261113-evidencias
```

O ambiente usou `NODE_PATH` no diretório externo
`rotabrasil-continuacao-20261111-tools/node_modules` e `PLAYWRIGHT_BROWSERS_PATH`
em `rotabrasil-continuacao-20261111-tools/browsers` para o FFmpeg de gravação.

**20 grupos aprovados** na raiz e em `/rota`:

1. Entrada rápida: **5.003 ms** medidos; essenciais prontos em **390 ms**.
   Relógio controlado confirma abertura visível em 4.900 ms e liberação em 5.000 ms.
2. Rede retida além de 7 segundos: painel permanece inerte até os requisitos reais.
3. Contrato incompatível, sessão 401, módulo ausente e download preso: falha visível,
   ações de recuperação e limite máximo; sem animação infinita.
4. Abas do painel, ajuda, atualização e evento de visibilidade: abertura não repete.
5. Pular com rede retida continua aguardando; com essenciais prontos libera antes
   dos 5 segundos. Teclado e transferência de foco conferidos.
6. Desktop 1366×768 e 1920×1080, celular 390×844, movimento reduzido e zoom real 200%
   (675×308 CSS px, DPR 2), sem overflow horizontal. Sem JS há orientação útil.
7. Favicons: URLs, versão, dimensões PNG, entradas ICO e bytes servidos iguais aos
   arquivos locais, em todas as entradas; prancha clara/escura conferida visualmente.
8. Sessão existente, login/CSRF e importação ZIP sintética sob falha secundária de
   Saúde preservados. Nenhum erro JavaScript, recurso 404 ou requisição externa.

Os relógios de teste são isolados/restaurados para não congelar as navegações
seguintes. A prontidão aguarda a presença do painel antes de sua abertura sumir,
evitando confundir a ausência do elemento no login com conclusão.

Não houve avaliação humana com leitor de tela, celular físico ou outros motores
de navegador. A prancha dos ícones demonstra contraste e tamanho nativo, não é
uma captura da barra de abas do Chrome.

## Evidências fora do checkout

Pasta: `C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-abertura-5s-20261113-evidencias/`.

- **`abertura-caminhao.webm`**: vídeo de aproximadamente 7 segundos, 1366×768,
  incluindo a animação real e a chegada ao painel.
- `caminhao-desktop.png`, `abertura-versao-390.png`, `caminhao-mobile-reduzido.png`.
- `painel-depois.png`, `favicons-claro-escuro.png`.
- `landing-{1366|1920|390}.png`, `login-{1366|1920|390}.png`, `acesso-local.png`.
- `falha-versao.png`, `falha-sessao.png`, `falha-modulo-essencial.png`,
  `falha-prazo-modulo.png`, `primeira-aparencia-html.png`.
- Prefixos `contexto-` para `/rota`, capturas de zoom, `resultado.json` e `servidor.log`.

## Arquivos da implementação

- `front/index.html`, `front/painel-abertura.css`, `front/painel-abertura.js`,
  `front/painel-emergencia.js`: cena, apresentação e integração ao bootstrap existente.
- `front/apresentacao.html`, `front/assets/marca/favicon-{16|32}.png`, `front/favicon.ico`.
- `back/pom.xml`, `LoginController.java`, `SegurancaConfig.java` e
  `SegurancaHttpTest.java`: empacotamento, entradas e disponibilidade dos ícones.
- `scripts/preparar-favicons.cjs`, `scripts/testar-apresentacao-carregamento.cjs`.

As alterações locais que já encaminhavam a cena, espera e ícones foram preservadas
e completadas com indicador estático, refinamento da rotação, foco ao pular,
verificações e evidências. Não houve envio de e-mail ou acesso ao banco operacional.
Todos os servidores usados nesta validação foram criados pelo roteiro com H2,
TLS, conta e documentos sintéticos em diretórios externos isolados e encerrados ao fim.
