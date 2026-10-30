# Prévia e revisão visual dos PDFs do ZIP

Na conferência de um ZIP, cada PDF reconhecido tem **Visualizar e corrigir PDF**.
O visualizador mostra o documento dentro do painel, com páginas e zoom. A
finalidade e os vínculos com CT-es continuam sendo escolhas explícitas no cartão
do arquivo; não são inferidos pelo texto do PDF.

## Uso

1. Selecione o ZIP e clique em **Conferir ZIP**.
2. Escolha **Visualizar e corrigir PDF** no arquivo que deseja conferir.
3. Navegue pelas páginas ou use o zoom. O caminho interno aparece no título,
   distinguindo arquivos de mesmo nome em pastas diferentes.
4. Para revisar um texto, selecione o trecho na lista ou clique na página.
   Informe o texto corrigido, tamanho e largura; adicione a correção ao rascunho.
   PDFs sem texto selecionável permitem posicionar texto manualmente, sem OCR.
5. Confira as correções da cópia. É possível remover uma correção. Fechar sem
   aplicar descarta as alterações feitas nessa abertura do editor.
6. **Aplicar revisões à cópia** prepara um novo PDF em memória. A partir desse
   momento, a confirmação do ZIP original fica bloqueada. **Baixar cópia revisada**
   permite guardar esse PDF separadamente.
7. Clique em **Gerar ZIP revisado e conferir**. Um novo ZIP é preparado na aba e
   submetido à inspeção existente. O arquivo ZIP selecionado no computador não é
   sobrescrito. O novo pacote referencia a operação anterior no histórico.
8. Confira novamente os documentos e escolha os vínculos. Uma alteração de hash
   não reaproveita automaticamente as associações anteriores. Só **Confirmar
   importação** grava o novo pacote no acervo.

## O que a revisão faz

É uma **correção visual em uma cópia**: acrescenta texto, opcionalmente cobrindo
a região escolhida com fundo branco. O texto anterior pode continuar recuperável
na estrutura do PDF. Não é uma ferramenta de remoção definitiva/redação de dados
nem um editor que reconstrói parágrafos, tabelas ou a diagramação original.

O XML do CT-e não é alterado. Revisar a aparência de um PDF não corrige dados
fiscais, não emite um novo documento fiscal e não comprova assinatura ou validade
fiscal do original ou da cópia. Dados fiscais incorretos exigem o documento correto
no sistema de origem.

Os PDFs originais do ZIP selecionado e documentos anteriormente importados são
preservados. O pacote revisado mantém os bytes dos XMLs e dos PDFs não editados.
O SHA-256 do ZIP e do PDF alterado muda; a confirmação segue as regras existentes
de duplicidade e publicação sem sobrescrita.

## Dados, limites e recuperação

- Prévia e rascunhos não importam documentos, não criam tentativas de envio e
  não enviam e-mail. Bytes e revisões ficam na memória da aba, sem `localStorage`
  ou `sessionStorage`. Trocar o ZIP limpa o visualizador e as revisões.
- Rascunhos de vínculos continuam seguindo o mecanismo anterior, por SHA-256.
- Bibliotecas PDF.js 6.4.299, pdf-lib 1.17.1 e fflate 0.8.3 são distribuídas
  como WebJars pelo próprio Spring Boot; não há CDN ou serviço externo.
- PDF.js renderiza em canvas, sem camada de links, formulários ativos ou execução
  de JavaScript do documento. A avaliação dinâmica está desativada.
- A inspeção continua conferindo apenas o cabeçalho `%PDF-`; não ganhou validação
  estrutural ou fiscal. Um PDF aceito pelo cabeçalho pode falhar no visualizador.
- PDFs inválidos ou protegidos por senha exibem erro e não permitem gerar revisão.
  O original continua disponível para download quando sua leitura foi concluída.
- Até 100 correções por PDF, texto de até 500 caracteres e fonte Helvetica para
  caracteres latinos. Cada cópia tem limite de 20 MiB; o total de cópias em memória
  é limitado a 100 MiB. O raster de uma página é limitado a 16 milhões de pixels.
- O novo ZIP usa entradas sem compressão para evitar exceder a proporção de
  compressão permitida. Se passar de 20 MiB, o painel mantém as revisões para
  download individual e orienta dividir o pacote na origem.
- Conteúdo inválido ou interrupção da conferência impede gerar um ZIP revisado;
  os erros devem ser resolvidos na origem. É possível visualizar um PDF válido
  mesmo quando outro arquivo tem um erro local de conteúdo.

## API

`POST /api/pacotes/previa-pdf?sha256=<hash-do-zip>&arquivoId=a1`, multipart
`arquivo` com o mesmo ZIP conferido. A rota usa as permissões de operador/admin,
CSRF e o limite de processamentos existente. O servidor reabre o ZIP com o leitor
limitado atual, confere o hash, recusa interrupção de segurança e devolve somente
a entrada PDF solicitada. Não recebe caminhos de disco.

A resposta é `application/pdf`, `Cache-Control: no-store`, `nosniff` e
`Content-Disposition: attachment`, sem caminho interno. Os bytes são usados pelo
canvas, não incorporados como página HTML ou iframe. O cliente confere novamente
tamanho e SHA-256 antes de abrir o documento. Não há cache persistente, migração
de banco ou novo armazenamento de uploads para essa função.

## Verificação

Testes de backend direcionados: `PreviaPdfPacoteHttpTest`,
`PacoteAssistidoHttpTest` e `SegurancaHttpTest`. A prévia é exercitada com H2,
diretórios isolados e conteúdo sintético: bytes exatos, ausência de importação,
identidade divergente, seleção não PDF, pacote inseguro, autenticação, papel e CSRF.

O script `scripts/testar-previa-pdf.cjs JAR SAIDA_EXTERNA` gera um ZIP e PDFs válidos
em memória, incluindo duas páginas e rotação. Exercita visualização, correções,
novo ZIP, hashes preservados, download, confirmação explícita, mobile/tema escuro,
zoom, fechamento sem aplicar, troca de pacote e PDF de estrutura inválida. Requer
Playwright/Chrome, conforme as ferramentas existentes em `scripts/package.json`.
Nenhum documento real ou e-mail é usado.

Resultado desta entrega: **35 testes de backend direcionados aprovados** (25 de
pacotes, cinco de segurança e cinco da prévia), **11 verificações de navegador
aprovadas**, sem erros JavaScript ou requisições externas; empacotamento aprovado.
Os primeiros 30 testes passaram na execução conjunta; após ajustar a configuração
do administrador sintético do teste novo, seus cinco casos também passaram.
Não se declara execução da suíte completa nesta entrega.

Relatórios JUnit em `C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-pdf-tests/`;
capturas, resultado e ZIP exclusivamente sintético para demonstração em
`C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-pdf-ui-validado/`.
