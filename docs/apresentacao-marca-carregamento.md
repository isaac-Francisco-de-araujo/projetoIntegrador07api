# Apresentação, marca oficial e abertura real — 13/11/2026

**Atualização posterior:** o requisito passou a exigir abertura animada mínima de
aproximadamente 5 segundos, com opção de pular a espera visual e favicon oficial.
Veja [implementação e testes atuais](abertura-animada-favicon.md). As referências
abaixo à ausência de tempo mínimo descrevem o aceite anterior.

## Referências fornecidas e assets utilizados

As duas imagens foram localizadas e visualizadas em `back/src/test/imagem/`:

1. **`Logo R Futurista com Estrada Neon.png`**: é a logo oficial. A versão utilizada
   mantém a imagem completa, proporção e canal de transparência. Não há R redesenhado
   como substituto. A logo é exibida na apresentação, login, acesso local, abertura e
   sidebar, acompanhada pelo nome em texto.
2. **`Gestão de Viagens em Neon Verde.png`**: referência de composição e atmosfera.
   Foi usado seu lado direito, x=824/y=0, 848×941 px: caminhão, mapa do Brasil,
   rotas e estrada. O recorte exclui a chamada, descrição, benefícios e botão já
   desenhados no lado esquerdo. A marca no próprio caminhão permanece parte da arte.

Os originais não foram sobrescritos. Assets de produção em `front/assets/marca/`:

| Asset | Dimensões | Tamanho |
| --- | --- | --- |
| `logo-rotabrasil.webp` | 384×351 | 58.892 bytes |
| `landing-rota.webp` | 848×941 | 96.514 bytes |

Total aproximado: **152 KiB**. A logo usa `object-fit:contain` e altura proporcional;
o teste confere o carregamento e a proporção. O recorte decorativo recebe máscaras
suaves nas bordas; não é usado como página inteira nem como botão. O processamento
local é reproduzível por `scripts/preparar-assets-marca.cjs`, sem serviço externo.

`back/pom.xml` agora inclui `assets/**/*.webp` na mesma pasta `static/` do JAR.
Somente os dois caminhos exatos de imagens foram acrescentados aos GETs públicos
da apresentação. Não foi aberta uma pasta genérica, API ou rota de trabalho.
O teste compara SHA-256 das respostas com os assets do checkout na raiz e em `/rota`.

## Apresentação e acesso

- Landing grafite/preto com verde profundo, destaque verde vibrante e texto branco.
  Duas colunas no desktop, arte à direita, chamada e ação em HTML à esquerda.
- Texto: **“Seus documentos no caminho certo.”**; descrição sobre conferência,
  organização por viagem e trabalho da equipe. Benefícios: importar CT-e/PDF,
  conferir/organizar por viagem e preparar envio ao cliente.
- Botão **Entrar no sistema**, seguido discretamente por **Importe. Confira. Organize.**
- Em 1366×768, logo, chamada, descrição e botão ficam visíveis sem rolar. No celular,
  texto e botão vêm antes da arte reduzida. Em 1920×1080 há limite de largura.
- A arte tem uma entrada suave, finita, por transform/opacity. Movimento reduzido
  remove a animação. Não há fontes, fotos, bibliotecas ou serviços externos.
- Login e acesso local compartilham a identidade escura, mantendo formulário,
  avisos de conta individual/ambiente isolado e ação local explícita.
- Apresentação → login → painel permanece. Sessão existente atravessa `/login`
  sem pedir senha novamente. Favoritos por hash, raiz e `/rota` foram mantidos.
- A identidade escura pública não lê nem altera a preferência de tema do painel.
  Nenhum efeito neon foi acrescentado às superfícies de trabalho.

## Abertura ligada à inicialização

O HTML já chega com fundo crítico escuro, logo, mensagem **“Preparando seu espaço de
trabalho”**, rota decorativa e estado **“Carregando a interface…”**. Não precisa
esperar a execução do módulo principal para existir. As áreas operacionais chegam
temporariamente inertes e são liberadas pelo mesmo controlador de abertura.

Estados reais, sem percentual ou etapas numéricas artificiais:

1. **Carregando a interface…** enquanto o grafo inicial de módulos é carregado.
2. **Verificando a versão da aplicação…** durante a consulta/verificação do contrato.
3. **Verificando sua sessão…** durante a verificação de sessão/permissões.
4. Conclusão essencial: abertura é retirada imediatamente. Consultas e módulos
   secundários continuam independentes; não são pré-requisito para liberar o painel.

Foi removido o antigo progresso de quatro etapas, a abertura editorial duplicada,
os comandos de pular/pausar que não representavam o estado atual e as regras visuais
que ocultavam o desenho antigo. O acabamento preexistente das páginas foi preservado.
As 13 ajudas contextuais continuam funcionando.

### Falhas, foco e recuperação

- `painel-emergencia.js` é um canal clássico independente dos módulos. O controlador
  de abertura envia suas falhas ao mesmo apresentador, que também atende módulo
  essencial ausente. Mensagem e ação de recuperação aparecem sem depender da
  inicialização bem-sucedida do painel.
- Em falha, o título muda para **“Não foi possível abrir o painel”**, a animação
  para e a causa é anunciada em região de alerta. Há Recarregar página e Ver
  diagnóstico quando o controlador foi carregado; módulo principal ausente não
  apresenta um botão de diagnóstico sem funcionamento. Sessão encerrada oferece
  Entrar novamente, com contexto correto, e foco no aviso ao abrir o diagnóstico.
- Há um **limite máximo de recuperação de 45 segundos** para downloads essenciais
  presos, iniciado antes de `DOMContentLoaded` (esse evento também pode ficar
  esperando um módulo). Não é espera mínima: sucesso remove a abertura sem aguardar
  esse relógio e cancela o prazo. Se o prazo vencer, não há repetição automática.
- O link Recarregar página existe desde o HTML. Com JavaScript desativado, a
  mensagem de carregamento é substituída por orientação para ativá-lo e links locais.
- A entrada normal não toma o foco do usuário. Se ele focou um controle da abertura,
  ao removê-la o foco sai desse elemento oculto para título/diagnóstico, sem scroll.
- Nenhuma animação é apresentada como carregamento do servidor antes da entrega
  do HTML. Falha de servidor antes de entregar a página depende do navegador/servidor.

## Arquivos principais

- `front/apresentacao.html`, `front/acesso.css`, `front/acesso.js`.
- `front/assets/marca/` e `scripts/preparar-assets-marca.cjs`.
- `front/index.html`, `front/painel-abertura.css`, `front/painel-abertura.js`,
  `front/painel-emergencia.js`, `front/painel.js`, `front/painel-visual.css`.
- `back/pom.xml`, `LoginController.java` (apenas HTML de acesso) e `SegurancaConfig.java`
  (somente os dois assets públicos exatos).
- `SegurancaHttpTest.java`, `scripts/testar-entrada.cjs`, `scripts/testar-uso-diario.cjs`
  e novo `scripts/testar-apresentacao-carregamento.cjs`.

## Verificações executadas nesta entrega

Java 25, build externo, target histórico preservado:

```text
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-marca-20261113-build -DargLine=-Xmx768m clean test package
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-marca-20261113-build -DskipTests package
```

**407 testes contabilizados: 406 aprovados, zero falhas/erros, 1 ignorado** por
condição de symlink do Windows. Empacotamento aprovado; refinamentos finais de
frontend foram reempacotados antes do aceite de carregamento.

Chrome / Playwright **1.58.2**, recursos do JAR, sem sobreposição de estáticos:

| Roteiro | Resultado atual |
| --- | --- |
| `testar-apresentacao-carregamento.cjs` | **16 grupos**: acesso local, público, protegido, sessão existente, raiz e `/rota`, assets íntegros, três resoluções, rede lenta por gates de versão/sessão, relógio congelado, erros essenciais, módulo ausente/preso, importação confirmada sob falha de Saúde, reload, tema, sem JS, movimento reduzido e zoom real |
| `testar-entrada.cjs` | **9 grupos**: HTTPS raiz/`/rota`, login, CSRF, três papéis, logout/expiração, matriz 12 abas × 3 resoluções × 2 temas, XML sintético, filtros/detalhes e foco móvel |
| `testar-uso-diario.cjs` | **16 grupos**: ZIP/XML/PDF, vínculos manuais, confirmação, resposta perdida/idempotência, exportação/retomada/hash, configurações/rascunhos, histórico e permissões |
| `testar-ajuda-contextual.cjs` | **6 grupos**: 13 ajudas × 2 temas × 2 resoluções, três papéis, teclado/toque/foco, filtros/rascunho, expiração e zoom real de 200% |

Resoluções **1366×768, 1920×1080, 390×844**; zoom real **200%**, medido em
**675×308 CSS px, DPR 2**. Sem overflow horizontal, erros JavaScript ou requisições
externas nos cenários concluídos. Os erros HTTP deliberados de testes são falhas
controladas, não ocultados por alteração de asserções.

### Por que isto valida carregamento real

- Requisições de versão e sessão foram retidas separadamente e liberadas pelo teste,
  sem sleeps para prolongar a animação. O painel permaneceu inerte até ambas passarem.
- A conclusão passou com o relógio do navegador congelado: não depende de timer,
  duração mínima ou animação. Avançar 46 s depois não reabriu uma falha antiga.
- Download do módulo principal retido: o HTML mostrou a abertura sem executar o
  painel; avanço do relógio de teste até 45 s produziu recuperação explícita.
- Módulo principal recusado: recuperação pelo canal independente. Contrato inválido
  e sessão recusada: operações bloqueadas; recarga recuperou sem repetir alterações.
- Saúde recusada: o painel abriu e um ZIP sintético foi importado por confirmação.
- A aparência inicial foi capturada com o módulo principal retido, e o HTML crítico
  foi conferido antes dos links de estilo. Não se trata apenas de fotografar animação.

Uma tentativa adicional de fotografar a superfície enquanto uma folha CSS
render-blocking estava retida ficou bloqueada pelo mecanismo de captura/estilos do
Chrome. Esse cenário não é declarado aprovado; o aceite final usa HTML crítico e
gates reais de módulos/APIs, com capturas bem-sucedidas. As pastas intermediárias
não representam o aceite final.

## Capturas e evidências externas

Raiz: `C:/Users/florb/AppData/Local/Temp/opencode/`.

**Apresentação/carregamento final:** `rotabrasil-marca-20261113-entrega/`:

- `landing-1366.png`, `landing-1920.png`, `landing-390.png`.
- `login-1366.png`, `login-1920.png`, `login-390.png`, `acesso-local.png`.
- `abertura-versao-{1366|1920|390}.png`, `abertura-sessao-390.png`.
- `primeira-aparencia-html.png`: módulo principal ainda retido.
- `falha-versao.png`, `falha-sessao.png`, `falha-modulo-essencial.png`,
  `falha-prazo-modulo.png` e `sem-javascript.png`.
- Versões com prefixo `contexto-` registram `/rota`.
- `zoom-200-landing.png`, `zoom-200-login.png`, `zoom-200-abertura.png`.
- `resultado.json` e `servidor.log`.

Outras evidências: `rotabrasil-marca-20261113-entrada/`,
`rotabrasil-marca-20261113-diario/`, `rotabrasil-marca-20261113-ajudas/`;
logs `rotabrasil-marca-20261113-maven.log` e `rotabrasil-marca-20261113-package.log`.

As capturas contêm apenas a aplicação, sem desktop/terminal/credenciais. As capturas
de documento ainda carregando e zoom usam a superfície nativa do Chrome para não
ficarem esperando `document.fonts.ready` ou sofrerem recorte incorreto de DPR.

## Limites e segurança

Sem e-mail externo, documentos reais, banco operacional, exposição de rede ou
alteração de regra de negócio, contrato HTTP, autenticação, CSRF ou papéis. Testes
usaram somente H2, fixtures sintéticas e pastas externas. Os originais da marca,
alterações locais e revisões anteriores foram preservados.

A instância local desta conversa foi identificada e atualizada em
**http://localhost:8080**, preservando sua base/acervo temporários. Migrações
permanecem desligadas na base já existente e e-mail continua SIMULADO. A nova
landing e a logo oficial responderam HTTP 200.

Não houve avaliação humana com leitor de tela, Firefox/Safari nem celular físico.
O recorte é raster, não um caminhão/mapa vetorial separado; sua apresentação é
decorativa, responsiva e independente dos textos HTML. Os pequenos detalhes da
logo não podem permanecer tão legíveis quanto a imagem grande; o nome em texto
acompanha a imagem, sem cortar ou redesenhar o símbolo.
