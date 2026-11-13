# Segunda revisão visual — simplificação, 13/11/2026

Continuação da [primeira revisão](revisao-visual-20261113.md), preservando a
reorganização local `front/` / `back/`, os documentos anteriores e o target histórico.

## Diagnóstico e mudanças concretas

O Maven inclui `front/` em `static/` no JAR do backend. Os navegadores desta revisão
carregaram esses recursos empacotados, sem sobrepor arquivos do checkout ao JAR.
As capturas anteriores foram feitas sobre o JAR do início desta sessão, antes
destas mudanças, com o mesmo roteiro, resoluções e fixtures sintéticas.

- Removidos os títulos internos repetidos de ZIP, Clientes e Viagens e o cabeçalho
  interno de Configurações. A importação individual começa diretamente pela seleção.
- ZIP, Saúde, Busca e Fila usam seções abertas: não há cartão externo envolvendo
  os cartões de resultados. Os agrupamentos de formulário e tabelas permanecem.
- Cabeçalho, cards, etapas e intervalos compactados sem reduzir a fonte dos campos
  de 16 px. Rodapé mais discreto. Tabelas vazias usam divisor e texto contextual,
  sem retângulo preenchido dentro de outro cartão.
- ZIP tem seleção e conferência lado a lado no desktop; no celular, fluxo vertical.
  Sem arquivo, Conferir fica desabilitado e Remover não ocupa espaço. Após escolher,
  nome/tamanho e remoção ficam disponíveis. Associação manual, prévia/revisão de PDF,
  reconferência e confirmação permanecem no fluxo original.
- Importação individual mantém teclado, drag-and-drop, nome/tipo/tamanho, ação XML/PDF
  contextual e remoção secundária.
- Configurações mantém Conta de envio, Pasta externa, Cópia automática e Salvar.
  O estado salvo/rascunho divide uma barra compacta com Recarregar configuração.
  Atualizar dados global fica recolhido nesta aba: não recarregava a configuração.
  Confirmação de descarte e rascunho entre abas foram preservados. Autenticação,
  conta de teste e históricos continuam administrativos e sob demanda.
- Envios vazio tem uma orientação e uma ação para selecionar viagem/documentos.
  Destinatário e prévia aparecem após seleção, com controles desabilitados quando
  envio real ou permissão não estão disponíveis. A seleção aparece antes do
  destinatário; a conferência e confirmação explícita existentes seguem depois.
  Históricos, recuperação da mesma tentativa e distinção de resultados preservados.
- Saúde usa superfícies neutras nos dois temas; cor fica na borda e no rótulo com
  símbolo/texto. Indisponíveis e não configurados vêm primeiro. Próxima orientação
  é visível; detalhes técnicos e horários individuais ficam recolhidos.
- Clientes, Viagens, Busca, Fila e Operadores diferenciam ausência de registros e
  filtros sem resultados. Paginação sem navegação útil oculta botões e preserva
  resumo quando existente. Filtros, seleção e ações continuam disponíveis.
- Tema claro continua padrão apenas para preferência nova; preferência salva é
  respeitada. Tokens compartilhados de campos, superfícies, texto e estados;
  removidos `!important` das superfícies coloridas de diagnóstico.

### Rolagem e foco

A inspeção do DOM/CSS confirmou que o documento (`window`/HTML) rola o conteúdo;
`main` não é um contêiner de rolagem independente. A sidebar fixa tem `overflow-y:auto`;
dialogs, tabelas e visualizador PDF mantêm suas rolagens específicas.

O roteador anterior atribuía `location.hash` e depois posicionava manualmente,
deixando também o posicionamento nativo de âncora participar da navegação. No menu
móvel havia outro foco agendado para um título interno. Agora a navegação interna
usa `pushState`, um único posicionamento e foco no título principal com
`preventScroll`. O foco móvel agendado redundante foi removido. `popstate` restaura
posição registrada; hashes diretos e aliases continuam tratados por `hashchange`.
Voltar dos documentos para a lista mantém o mecanismo de restauração e filtros.
A sidebar não propaga a rolagem ao documento ao atingir suas extremidades.

Não foram adicionados timeouts de rolagem ou múltiplos `scrollTo` para o mesmo evento.
As capturas neutralizam transições finitas pelo recurso `animations:'disabled'`
do Playwright, evitando fotografar o drawer no meio da transição de viewport;
o foco visível continua validado separadamente.

### Medição comparável — 1366×768, claro, sem dados

| Ação | Antes: topo / fim | Depois: topo / fim |
| --- | --- | --- |
| Conferir arquivos ZIP | 519 / 564 px | 329 / 374 px |
| Salvar alterações | 733 / 778 px | 560 / 605 px |

Salvar estava parcialmente fora do viewport; agora aparece integralmente sem rolar.
Os números e a classificação dos contêineres estão nos `resultado.json` externos.

## Arquivos principais

- `front/index.html`: composição, títulos, seções e estado inicial de Envios.
- `front/painel-visual.css`, `front/painel-diagnostico.css`: densidade, tokens,
  superfícies neutras, estados vazios e paginação.
- `front/painel-navegacao.js`, `front/painel-interface.js`: rolagem, histórico e foco.
- `front/painel-documentos.js`, `front/painel-pacotes.js`: pré-requisitos visuais e seleção.
- `front/painel-saude.js`: prioridade dos problemas e orientação visível.
- `front/painel-consultas.js`, `front/painel-busca.js`, `front/painel-fila.js`,
  `front/painel-admin.js`: estados vazios contextuais.
- `scripts/testar-simplificacao-visual.cjs`: novo roteiro comparável antes/depois.
- `scripts/testar-zoom-uso-diario.cjs`: zoom real ampliado a dez telas nos dois temas.

## Verificações executadas agora

Java 25 e Maven, saída externa:

```text
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-refino-20261113-build -DargLine=-Xmx768m clean test
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-refino-20261113-build -DargLine=-Xmx768m test package
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-refino-20261113-build -DskipTests package
```

A primeira execução limpa foi interrompida pelo limite de tempo da ferramenta.
A execução seguinte completou toda a suíte: **407 contabilizados, 406 aprovados,
zero falhas/erros e 1 ignorado** por symlink no Windows. Build e empacotamento
aprovados; refinamentos finais exclusivamente frontend foram reempacotados antes
dos roteiros finais de navegador.

Playwright **1.58.2**, Chrome, JAR real, bases H2 e diretórios sintéticos externos:

| Roteiro | Resultado atual |
| --- | --- |
| `testar-simplificacao-visual.cjs` | 9 grupos; antes/depois nos dois temas e três resoluções; dados vazios/preenchidos, ZIP XML/PDF real, associação/confirmar, rascunho, drop individual, menu no fim da página, histórico, foco e scroll próprio da sidebar |
| `testar-entrada.cjs` | 9 grupos; 12 abas × 3 resoluções × 2 temas, HTTPS operacional na raiz e `/rota`, login/CSRF/permissões, XML sintético, preferência persistida, detalhes/filtros e foco móvel |
| `testar-uso-diario.cjs` | 16 grupos; ZIP 2 XMLs/2 PDFs, associação, revisão de erros, resposta perdida/idempotência, cópia/retomada/hash, Gmail sem envio, rascunho/conflitos, históricos e segurança operacional |
| `testar-previa-pdf.cjs` | 11 grupos; PDF multipágina, revisão em cópia, download/novo ZIP/hash, nova associação, celular/zoom, cancelamento, PDF estruturalmente inválido |
| `testar-zoom-uso-diario.cjs` | Zoom real Chrome 100/150/200%, dez telas × dois temas; sem overflow de página ou erros JS |

Resoluções: **1366×768, 1920×1080 e 390×844**. Zoom real mediu larguras CSS
**1350/900/675 px**, DPR **1/1,5/2**. Todos os roteiros finais acima concluíram sem
erros JavaScript. Contraste medido nos elementos de Configurações pelo roteiro
diário: mínimo **6,34:1 no claro** e **7,09:1 no escuro**. Não é certificação global.

A primeira rodada diária, concorrente com três outros navegadores, atingiu o limite
de espera de 5 s ao reconferir ZIP. Foi executada novamente, sem alterar as
asserções ou o timeout, e passou integralmente. A falha intermediária foi preservada
em diretório separado e não representa o aceite final.

## Capturas limpas e evidências

Raiz externa: `C:/Users/florb/AppData/Local/Temp/opencode/`.

- **Antes:** `rotabrasil-refino-20261113-antes-final/`.
- **Depois:** `rotabrasil-refino-20261113-depois-final/`.
- Mesmos nomes e dimensões para comparação, screenshots apenas do viewport:
  - `vazio-claro-pacotes-1366.png`, `vazio-escuro-pacotes-1366.png`;
  - `vazio-{claro|escuro}-configuracoes-1366.png`;
  - `vazio-{claro|escuro}-email-1366.png`;
  - `vazio-{claro|escuro}-saude-1366.png`;
  - `vazio-{claro|escuro}-clientes-1366.png`;
  - versões `-1920.png` e **móveis `-390.png`** dessas telas;
  - `selecionado-{tema}-pacotes-{largura}.png`;
  - `associacao-{tema}-pacotes-{largura}.png`;
  - `preenchido-{tema}-{email|clientes|viagens|busca|fila|saude|configuracoes}-{largura}.png`.
- Cada diretório comparativo contém **108 capturas** e `resultado.json`.
- **Entrada protegida:** `rotabrasil-refino-20261113-entrada-final/`.
- **Uso diário/estados históricos:** `rotabrasil-refino-20261113-diario-final/`.
- **PDF:** `rotabrasil-refino-20261113-pdf/`.
- **Zoom real:** `rotabrasil-refino-20261113-zoom/`.
- **Maven:** `rotabrasil-refino-20261113-maven.log` e
  `rotabrasil-refino-20261113-package.log`.

As capturas não incluem desktop, terminal, credenciais ou Gravador de Passos.
Os endereços e documentos apresentados são sintéticos, sem valor fiscal.

## Instância local e limites

O processo de desenvolvimento iniciado nesta conversa foi identificado e reiniciado
com o novo JAR em **http://localhost:8080**, preservando seu banco/acervo temporários,
com `IFEXISTS=TRUE`, migrações desativadas e e-mail SIMULADO. API e frontend revisado
responderam HTTP 200. Nenhuma instância operacional foi reiniciada.

Nenhum banco operacional foi aberto/migrado; nenhum documento real, envio externo,
publicação de serviço ou alteração de regra/contrato/permissão foi realizado.
Acervo indisponível, exportação não configurada/não verificada, e-mail sem configuração
e backup não verificado continuam estados reais da instância de teste; não foram
convertidos em sucesso. A importação sintética passou a tornar a pasta do acervo
legível, mas Saúde ainda informa gravação/publicação não testada nesta consulta.

Pendências: avaliação humana com a equipe, leitor de tela e outros navegadores
(Firefox/Safari) não foram exercitados. SMTP, OAuth2 externo e recebimento de e-mail
não foram homologados. Não foi identificado bloqueio nos roteiros finais executados.
