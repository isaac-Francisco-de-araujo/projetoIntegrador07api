# Modernização operacional do frontend RotaBrasil

## Escopo e identidade

Evolução do frontend estático existente, sem frameworks adicionais e sem alterações
no backend, nas migrações ou nos contratos REST. Mantidos marca, ilustração do
caminhão, cores verde/preto, verde-lima, temas, importação ZIP, editor de revisão de
PDFs e confirmações de importação/envio.

O tema inicial é escuro; uma preferência claro/escuro já salva é respeitada.
Os blocos de administração de operadores, ambiente de testes e seletor fictício
de workspace foram retirados da interface. O módulo de administração anterior
não é mais importado; autenticação e permissões continuam sendo as do servidor.

## Loading e navegação

- Abertura com duração mínima de **5.000 ms em toda visita**, aguardando também
  os dados necessários. O progresso continua vinculado às etapas reais.
- **Pular abertura** pula somente o tempo de apresentação; não ignora requisições
  necessárias. Movimento reduzido desativa animações, mantendo os cinco segundos.
- Sidebar fixa no desktop, opção de recolher e preferência persistida.
- No celular, botão de menu abre um drawer. A navegação escondida fica `inert`;
  enquanto aberta, o conteúdo de fundo fica `inert`. Escape, botão e overlay fecham
  o menu. A escolha de uma seção fecha o drawer e posiciona o foco no destino.
- Rotas âncora originais preservadas, com estado ativo, hover e foco visíveis.
- Central `Ctrl+K` / `Cmd+K` preservada e atualização do atalho XML/PDF.

## Dashboard

- Banner reduzido aproximadamente 25%, mantendo caminhão e ambos os botões.
- Indicadores inteiros clicáveis com links nativos, hierarquia numérica,
  carregamento com `aria-busy` e estado de indisponibilidade.
- Valores provenientes de `/api/consulta/resumo`; uma falha limpa os números
  anteriores, sem substituí-los por zeros ou exemplos.
- Ações rápidas para ZIP, XML/PDF, viagens, clientes e central de envios.
- **Últimas viagens**: até cinco registros reais, por data da viagem decrescente,
  com datas ausentes por último. Não representa data de importação/processamento.

### Dependência documentada: documentos recentes

Não foi criada uma lista que invente data de processamento ou situação documental.
O DTO de `/api/documentos` não fornece essas informações, e os PDFs modernos são
consultados por viagem em `/api/viagens/{id}/anexos`. Uma seção global de documentos
recentes com tipo, viagem, data e estado depende de uma consulta unificada com esses
metadados. Essa evolução de backend não faz parte da autorização desta entrega.

## Viagens, documentos e clientes

- Busca de viagens por nCT/nome do cliente; filtros por cliente, período e status
  exato, com limpeza de filtros e validação de intervalo de datas.
- Ordenação de número, cliente, data e status, com `aria-sort`.
- Paginação de 20 registros, contagem de resultados, carregamento e erros.
- Últimas viagens e tabela principal reutilizam a mesma construção de linhas.
- Detalhes da viagem pelo endpoint existente e ação para abrir seus documentos.
- Clientes em tabela com nome, CNPJ, pesquisa por nome/CNPJ, detalhes, paginação
  e atalho para todas as viagens daquele cliente.
- Detalhes consultam `/api/clientes/{id}` ou `/api/viagens/{id}`; campos opcionais
  ausentes são identificados como não informados. Não são criados contatos.
- Documentos recebem identificação visual do tipo. A viagem e o cliente atuais
  aparecem tanto na área documental quanto na central de envios.
- Seleção explícita, snapshots, estados reais de envio, idempotência e confirmação
  de ações destrutivas permanecem nos módulos existentes.

### Estratégia de consulta sem alterar a API

As consultas usam os arrays completos de metadados de `/api/clientes` e
`/api/viagens`, fazendo filtro, ordenação e paginação no navegador. Assim o filtro
por cliente considera todo o conjunto retornado, não somente uma página de servidor.
Os endpoints `/api/consulta/viagens` e `/api/consulta/clientes` permanecem intactos.

Para bases muito grandes, uma paginação de servidor combinada com todos esses
filtros e ordenações exigirá ampliar o contrato existente. Não foi introduzido
um limite silencioso que esconda registros. Uma falha de leitura esvazia as
consultas e seleções dependentes e mantém a atualização manual disponível.

## Importação individual de XML e PDF

O mesmo formulário agora aceita XML e PDF, inclusive por drag-and-drop, com nome,
tamanho e resultado da operação. O XML usa exatamente `/api/processamento/xml`.

Como não existe endpoint direto para PDF avulso, o frontend reutiliza o ZIP:

1. O usuário escolhe um PDF, uma viagem cadastrada e um CT-e ativo dessa viagem.
2. O XML é lido por `/api/documentos/{id}/arquivo` e seu SHA-256 é comparado ao DTO.
3. Um ZIP é preparado localmente com o XML original e o PDF, sem modificar bytes.
   A data interna do ZIP é fixa para que a repetição da mesma seleção gere os
   mesmos bytes e mantenha a reutilização idempotente existente.
4. O pacote vai para **Conferir ZIP**, preservando leitura, limites e diagnóstico
   do servidor. Nenhuma importação é concluída por essa preparação.
5. O usuário escolhe a finalidade, marca o CT-e e confirma a importação.

Sem XML ativo com integridade disponível, a interface orienta importar o CT-e ou
usar o ZIP original. Não é criado XML fictício para contornar essa dependência.
PDF e XML juntos devem respeitar os 20 MiB do ZIP. Uma operação ZIP ou revisão em
andamento impede substituir o pacote por outro PDF. O formato e as validações de
PDF continuam os existentes: cabeçalho, sem promessa de validação estrutural/fiscal.

## Organização dos arquivos

| Arquivo | Responsabilidade |
| --- | --- |
| `index.html` | Estrutura, remoções solicitadas, filtros, atalhos e dialogs |
| `painel-operacao.css` | Componentes de navegação, consultas, banner compacto e responsividade |
| `painel-interface.js` | Temas, drawer, menu compacto, atalhos e drag-and-drop |
| `painel-comandos.js` | Central de atalhos com entrada XML/PDF |
| `painel-consultas.js` | Tabelas, filtros, ordenação, paginação e detalhes |
| `painel-documentos.js` | Atualização real dos dados, documentos, arquivamento e seleção de envio |
| `painel-importacao.js` | Formulário XML/PDF e preparação determinística do pacote individual |
| `painel-pacotes.js` | Fluxo ZIP existente e entrada explícita do pacote preparado |
| `painel-abertura.js` | Duração mínima, progresso real e liberação acessível |
| `painel-base.js` | API, permissões, avisos e formatação compartilhada de tamanho |
| `painel-historico.js` | Carregamento inicial aguardado e erro local do histórico |
| `painel.js` | Inicialização dos módulos, sem administração de operadores |

## Verificação

`scripts/testar-modernizacao.cjs JAR SAIDA_EXTERNA` exercita o frontend empacotado
com backend real, H2 em memória e acervo isolado. Gera clientes e 28 viagens por
APIs reais e arquivos exclusivamente sintéticos. Cobre duração da abertura,
remoções, indicadores, filtros/ordenação/paginação, detalhes, PDF avulso, repetição
idempotente, simulação confirmada, falha/recuperação da API e navegação responsiva.

`scripts/testar-previa-pdf.cjs` mantém a regressão do ZIP com editor PDF.
`scripts/testar-envio-manual.cjs` mantém ZIP, confirmação/reenvio, reinício e
autenticação; foi ajustado para a remoção do painel administrativo e sua espera de
inicialização usa `/api/sessao` (200 ou 401), atendendo também ao servidor protegido.

Todos os testes usam perfis e bancos isolados, sem e-mail real, documentos reais
ou abertura do banco operacional. Artefatos e capturas ficam fora do repositório.

### Resultados desta entrega

- **10 verificações da modernização aprovadas**, com 28 viagens criadas pelas APIs
  no H2 de teste: loading repetido, remoções, banner, indicadores, consultas,
  detalhes, PDF avulso idempotente, envio simulado confirmado, falha/recuperação,
  menu, teclado e larguras 360, 390, 768, 1024, 1440 e 1920 px.
- **11 verificações do editor PDF aprovadas**: ZIP, revisão, cópia, hashes,
  reconferência, mobile, zoom, cancelamento e PDFs de estrutura inválida.
- O teste ampliado de envio manual passou por **13 cenários**, incluindo ZIP
  assistido, fatura compartilhada, resposta perdida, reenvio e persistência após
  reinício. A etapa autenticada ficou bloqueada porque **GET `/login` retorna 404**
  sem fornecer o formulário. O resultado parcial e a captura registram a falha;
  não se declara a suíte ampliada inteira aprovada. A geração/entrega da tela de
  login depende do fluxo de segurança do servidor, que não foi alterado nesta
  modernização exclusivamente frontend.
- Nenhum erro JavaScript observado nas três execuções. O teste novo também
  confirma ausência de chamadas à administração retirada da interface.
- Empacotamento Maven externo aprovado; sintaxe dos módulos JavaScript conferida.
  Não houve execução de nova suíte JUnit, pois nenhum código Java foi alterado.

Artefatos externos sob `C:/Users/florb/AppData/Local/Temp/opencode/`:
`rotabrasil-modernizacao-final/`, `rotabrasil-modernizacao-pdf/` e
`rotabrasil-modernizacao-envios-final/`.
