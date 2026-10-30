# Exclusão de ZIP/XML/PDF e navegação por páginas

**Navegação ampliada na V9:** a oitava tela é
[Configurações](configuracoes-exportacao-notificacoes.md), com remetente da operação,
destino autorizado e testes. O registro abaixo documenta a entrega das sete telas
anteriores; exclusão/arquivamento e restauração continuam disponíveis.

## Navegação

O painel passou a apresentar sete telas separadas, com a mesma identidade visual:

| Tela | Rota |
| --- | --- |
| Visão geral | `/#inicio` |
| Importar ZIP e gerenciar ZIPs importados | `/#pacotes` |
| Importar XML ou PDF individual | `/#importacao` |
| Viagens e documentos | `/#viagens` |
| Clientes | `/#clientes` |
| Central de envios | `/#email` |
| Processamentos | `/#processamentos` |

As telas são rotas hash do frontend estático, sem novo framework ou rotas de
servidor para páginas. O menu muda o conteúdo visível e o título da tela, em vez
de apresentar todos os formulários em uma página longa. Voltar/avançar, recarga,
atalhos e links diretos são suportados. Os endereços anteriores `/#documentos`,
`/#pacotes-importados` e `/#ultimas-viagens` abrem a tela correspondente.

Os componentes permanecem no DOM: trocar de tela preserva filtros, paginação,
arquivo selecionado, vínculos em conferência e seleção de documentos. Atualizar
os dados ou trocar a viagem mantém as invalidações de prévia existentes.

## Arquivos selecionados antes da importação

- **Importar ZIP → Remover ZIP da seleção** limpa o upload, a conferência local e
  os rascunhos daquele ZIP. Não exclui pacotes já importados ou seus históricos.
- Se houver cópias de PDFs revisadas somente na memória da aba, uma confirmação
  identifica o descarte dessas cópias. Cancelar mantém a seleção e as revisões.
- **Importar XML/PDF → Remover XML/PDF da seleção** limpa o arquivo individual e
  sua escolha de viagem/CT-e, sem requisições de exclusão ao servidor.
- Controles de limpeza ficam bloqueados enquanto a operação correspondente está
  em processamento; não cancelam uma importação já enviada silenciosamente.

## XMLs e PDFs já importados

Na tela **Viagens e documentos**, escolha a viagem e use **Excluir XML** ou
**Excluir PDF**. O diálogo confirma o nome e informa que a operação é
arquivamento reversível. Arquivos saem da lista ativa e da seleção de envio;
bytes, IDs, hashes, vínculos e histórico são preservados.

PDF compartilhado é arquivado em todas as suas viagens vinculadas, informadas no
diálogo. A opção **Consultar documentos arquivados** permite restaurar o mesmo
registro. Os endpoints existentes de arquivamento/restauração foram preservados.

## ZIPs já importados

Na tela **Importar ZIP**, a seção **ZIPs importados** apresenta nome, data real de
importação e quantidades de documentos/anexos ativos. **Excluir ZIP** abre uma
conferência com todos os arquivos do pacote e seus estados atuais.

O servidor não armazena o ZIP original como um arquivo único após a importação.
Por isso, excluir o ZIP no painel significa arquivar os XMLs e PDFs associados,
não apagar o arquivo ZIP no computador nem o registro de identidade do pacote.

### Vínculos compartilhados e confirmação

- A conferência identifica PDFs com várias viagens e XMLs referenciados por outros
  ZIPs. Arquivar um registro compartilhado afeta todas essas referências.
- É necessário marcar a confirmação explícita dos arquivos e dos vínculos.
- Um SHA-256 da conferência identifica metadados, estados e referências atuais.
  Ele não substitui o SHA-256 de conteúdo de cada arquivo nem confere seus bytes.
- Antes de modificar dados, o servidor adquire a mesma coordenação transacional
  usada por importação/arquivamento e refaz a conferência. Alteração de estado,
  identificação ou vínculo exige nova conferência (409), sem arquivar parcialmente.
- O lote é uma única transação: uma falha desfaz todas as alterações do lote.
  Registros já arquivados mantêm sua data de arquivamento anterior.
- Repetir a mesma operação já concluída retorna o estado arquivado sem trocar as
  datas. Não há repetição automática pelo painel.

### Consulta e restauração

**Consultar ZIPs excluídos da lista ativa** mostra os pacotes cujos documentos e
PDFs estão todos arquivados. Essa condição é calculada dos arquivos vinculados;
não existe um marcador independente de exclusão no pacote. Arquivar arquivos
individualmente também pode fazer um pacote aparecer nessa consulta.

**Ver arquivos / restaurar** permite restaurar os itens individualmente. Não há
restauração automática do lote, pois algum item pode ter sido arquivado antes da
exclusão do ZIP. Restaurar um XML compartilhado também o torna ativo nos outros
pacotes que já o referenciam.

Reimportar o mesmo ZIP segue reutilizando sua identidade anterior e não restaura
documentos silenciosamente. Para voltar a usar um arquivo arquivado, restaure-o
explicitamente. Arquivar não remove nem reconstrói tentativas e snapshots de envio.

## API adicionada com a autorização para os ZIPs importados

- `GET /api/pacotes?pagina=0&tamanho=20&arquivados=false`: metadados paginados.
- `GET /api/pacotes/{id}/conferencia-exclusao`: arquivos, estados, viagens,
  referências compartilhadas e `conferenciaSha256`, sem caminhos internos.
- `DELETE /api/pacotes/{id}?confirmado=true&conferenciaSha256=<hash>`: arquivamento
  transacional dos arquivos ativos conferidos; retorna o resumo atualizado.

Os novos endpoints usam as permissões existentes: consultas autenticadas quando a
segurança está ativa; arquivamento para OPERADOR/ADMIN, com CSRF. IDs e paginação
são validados. A auditoria registra a referência ao pacote, sem bytes ou caminhos.

Não houve alteração de migração publicada, nova tabela ou novo campo. O esquema
continua **V8**. A operação reutiliza `documentos.arquivado_em` e `anexos.arquivado_em`,
sem exclusão física de arquivos, cadastros de pacote ou associações. O banco
operacional não foi aberto ou migrado para implementar ou testar esta função.

## Organização

- `painel-navegacao.js`: páginas, títulos, aliases, links e histórico do navegador.
- `painel-confirmacao.js`: diálogo reutilizável, bloqueio durante ação e foco.
- `painel-arquivos.js`: consulta de ZIPs, conferência, exclusão e restauração por item.
- `painel-documentos.js`, `painel-importacao.js` e `painel-pacotes.js`: ações de
  exclusão/limpeza e integração com a navegação.
- `ArquivamentoPacoteService`, `GerenciamentoPacoteController` e
  `ArquivamentoPacoteDtos`: transação e contratos novos, mantendo os anteriores.

## Verificação

`ArquivamentoPacoteHttpTest` cobre conferência, compartilhamento, confirmação,
estado alterado, rollback induzido, idempotência, concorrência, restauração seletiva,
validação e preservação dos bytes/históricos. `SegurancaHttpTest` verifica papéis e
CSRF nas rotas novas; `PacoteAssistidoHttpTest` mantém a regressão da importação.

`scripts/testar-exclusoes-paginas.cjs JAR SAIDA_EXTERNA` usa backend real, H2 novo,
diretório isolado e PDFs/CT-es sintéticos. Cobre as sete telas, limpeza local sem
mutações, importação, exclusão/cancelamento, conferência vencida, compartilhamento,
restauração, hashes, revisões pendentes, filtros, links diretos e responsividade.
As ferramentas de modernização e editor PDF também foram adaptadas à navegação.

Resultados desta entrega:

- **38 testes de backend direcionados aprovados**: oito de arquivamento de ZIP,
  25 de regressão do ZIP assistido e cinco de segurança, incluindo as rotas novas.
- **30 verificações de navegador aprovadas**: nove de exclusões/páginas, 11 do
  editor PDF e dez de modernização, sem erros JavaScript.
- Empacotamento externo aprovado. Nenhum teste utilizou documento real, envio
  real ou o banco do localhost em uso. Não se declara execução da suíte completa.

Relatórios JUnit em `C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-exclusoes-build/`.
Capturas e resultados em `rotabrasil-exclusoes-paginas-final/`,
`rotabrasil-exclusoes-pdf-final/` e `rotabrasil-exclusoes-modernizacao-final/`,
na mesma área temporária externa.
