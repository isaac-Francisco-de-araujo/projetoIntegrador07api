# Configurações da operação, exportação e notificações — V9

**Interface atual (03/11/2026):** [uso diário simplificado](uso-diario-simplificado.md).
Configurações resume conta, pasta e cópia; requisitos/testes ficam recolhidos e
restritos. Confirmação ao motorista é opcional, sem exigir endereço para importar.
Os contratos e resultados históricos abaixo são preservados.

**Evolução V10:** [Diagnóstico e Recuperação](diagnostico-recuperacao.md) amplia a
exibição dos resultados, centraliza pendências e preserva cada tentativa de cópia.
A recuperação da exportação apenas registra eventos; não executa seu transporte.
O código atual exige V11, com migração operacional separadamente autorizada.

**Etapa E-mail V11:** [Confirmação manual ao motorista](confirmacao-motorista.md)
implementa conferência, correção de destinatário, tentativa idempotente e reenvio no
histórico existente. REAL é condicionado a OAuth2, segurança ativa e opt-in externo;
o padrão continua SIMULADO. Os capítulos V9 abaixo descrevem os avisos internos legados.

**Estabilização atual:** [evidências e roteiro](estabilizacao.md). Não executar JAR
antigo com estáticos atuais sobrepostos. `GET /api/versao` identifica contrato,
build e hashes do empacotamento; a inicialização detecta backend incompatível.

## Decisão confirmada

A conta Outlook da operação recebe os ZIPs e será o **remetente** da confirmação
ao remetente original da mensagem. Não é um destinatário interno fixo. Nesta
entrega o recebimento continua manual: o operador informa o destinatário do aviso
no upload, porque os bytes do ZIP não identificam o remetente de um e-mail.

O destinatário dos documentos do cliente, selecionado na Central de envios,
continua independente. Não é preenchido nem substituído pelo endereço da operação.
Recebimento automático Outlook não integra o escopo. A confirmação manual REAL
está implementada na V11, mas sua homologação depende da identificação/autorização
da conta e do acesso protegido. O transporte documental e suas proteções foram preservados.

## Usar a aba Configurações

A oitava tela fica em `/#configuracoes`, no menu e na central de atalhos.

1. Informe o remetente da operação.
2. Escolha uma subpasta existente da raiz autorizada ou use **Criar subpasta**.
3. Ative/desative **Copiar automaticamente após nova importação concluída**.
4. Clique em **Salvar configurações** e aguarde a confirmação do backend.
5. Teste a pasta e faça um teste simulado de e-mail, informando um destinatário de teste.

Configuração e resultados de testes persistem no banco. Alterações ainda não
salvas são sinalizadas; testes são bloqueados enquanto houver essas alterações.
Uma falha de gravação não mostra sucesso. Controle de versão impede sobrescrever
silenciosamente uma configuração alterada por outra requisição.

Não foi criada tela de login ou gestão de usuários. O painel local é acessado
sem login no perfil `desenvolvimento`, mantendo loopback e banco/acervo isolados.
O backend já contém segurança para o perfil `operacao`; ela permanece ativa nesse
perfil. Quando habilitada, as configurações exigem ADMIN/CSRF e exportações e
simulações exigem OPERADOR/ADMIN. A ausência de formulário de login utilizável
na interface atual não autoriza remover essas restrições ou publicar o modo local
sem autenticação em uma rede compartilhada.

## Raiz autorizada: pasta do backend

O destino implementado é no **computador que executa o Java/Spring Boot**.
Se o navegador acessa um servidor remoto, a pasta está naquele servidor.
Não há seletor de disco local do navegador nem campo HTTP de caminho arbitrário.
Downloads individuais existentes continuam disponíveis quando o operador precisa
guardar um arquivo no computador do navegador.

O responsável cria uma raiz fora do checkout e separada do acervo e a fornece externamente:

```powershell
$env:ROTABRASIL_EXPORTACAO_RAIZ = 'C:/RotaBrasil/Exportacoes'
```

Alternativa: `--rotabrasil.exportacao.raiz=/pasta/autorizada`.
Sem essa configuração, a tela informa que o destino está incompleto e não permite
ativar a cópia automática. A raiz deve existir; ela não é criada implicitamente.

Somente nomes de subpastas de até 60 caracteres, com letras ASCII, números,
hífen e sublinhado, são aceitos. Caminhos absolutos, separadores, `..`, links
simbólicos e nomes reservados do Windows são recusados. A API lista até 100
subpastas elegíveis, além da própria raiz. A raiz e o acervo não podem conter um ao outro.
Na revisão atual a raiz também não pode sobrepor o checkout encontrado nos ancestrais
do diretório de execução ou da origem do JAR/classes. Configuração incompatível
interrompe a inicialização com orientação explícita; não há mudança automática do destino.

**Testar acesso e gravação** cria um arquivo sintético temporário e um hard link
na pasta salva, confere a publicação e remove os temporários. A exportação usa
esse recurso para publicar sem sobrescrever arquivos preexistentes. Acesso a uma
pasta de rede não é presumido: depende da conta do processo, permissões e suporte
real do sistema de arquivos. Uma falha fica registrada para consulta.

Mudar a raiz externa exige reiniciar; mudar a subpasta pela interface não move
nem copia retroativamente o acervo. O histórico mantém uma impressão da raiz
efetiva, a subpasta e o snapshot de configuração. Se a raiz externa mudar,
retomar um histórico antigo é recusado; uma nova exportação exige ação manual.
Nenhum caminho físico da raiz ou da origem é retornado pela API de operação.

## Estrutura e integridade

```text
Raiz autorizada / subpasta /
  pacote-<id>/
    viagens/
      <nCT>/documento-<id>.xml
      <nCT>/documento-viagem-<id>.pdf
    faturas/fatura-<id>.pdf
    manifesto.json
```

O plano seleciona apenas documentos e anexos **ativos e elegíveis** do pacote já
importado. Não realiza extração irrestrita do ZIP. Os IDs e `nCT` controlados pelo
domínio compõem os destinos; nomes/caminhos fornecidos no ZIP não são confiados.
Uma fatura compartilhada é copiada uma vez. Suas viagens ficam registradas no
item persistido e no manifesto, junto de origem/ID, localização lógica e SHA-256.
O manifesto não contém caminhos físicos, contatos ou conteúdo de documentos.

A origem é lida pelo leitor seguro existente, com SHA-256 e limite de 20 MiB por
arquivo. A cópia admite até 100 arquivos documentais; o total de bytes, incluindo
o manifesto, não excede 100 MiB. O destino publicado também é conferido pelo hash.
O acervo interno não é movido, substituído ou remapeado. XMLs e PDFs existentes
mantêm seus bytes e os caminhos registrados no banco.
Antes de copiar um item pendente, a retomada também revalida que a origem continua
ativa, vinculada ao pacote e com os metadados do plano. Arquivamento ou remoção
posterior impede publicar novos bytes daquela origem; cópias anteriores são preservadas.

## Automação, ação manual e idempotência

- Cópia automática: somente após o commit de uma **nova** importação e com a
  opção salva. Reutilizar tecnicamente um ZIP não dispara outra exportação automática.
- Ação manual: **Exportar arquivos**, no pacote importado ou no resultado da
  importação, usa a configuração salva naquele momento.
- Histórico: **Ver exportações**, na lista de ZIPs, exibe estados e arquivos.
- A chave de reutilização é pacote + impressão da raiz/subpasta. Repetir o mesmo
  destino não cria diretórios extras nem outro registro de exportação.
- Um destino diferente só cria outra cópia por ação explícita. O snapshot de uma
  exportação anterior não é alterado para acompanhar o novo destino.

O plano e os itens são persistidos antes da cópia. A publicação usa staging próprio
em `.rotabrasil-exportacoes/<exportacaoId>/<itemId>.tmp` e `createLink`, sem opção
de substituição. Um arquivo preexistente, mesmo de bytes iguais, é conflito,
salvo quando reconhecido pelo histórico ou pela testemunha de publicação própria.
Depois do commit dos resultados, os links temporários dos itens concluídos são limpos.

Uma interrupção após publicar, mas antes de gravar o resultado, mantém a testemunha
do mesmo arquivo físico. Na retomada explícita, o sistema confere identidade e hash
para reconhecer a publicação. Staging parcial próprio pode ser refeito; arquivos
finais ou temporários alheios não são tratados como sucesso por igualdade de conteúdo.

### Falha parcial e recuperação

Os estados de exportação são `PENDENTE`, `PARCIAL` e `CONCLUIDA`. Só há conclusão
quando todos os itens, incluindo manifesto, foram confirmados. O manifesto aguarda
os documentos; uma cópia parcial não publica um manifesto como se estivesse completa.

Falhas preservam a importação e produzem **Importado; exportação pendente**.
Cada item informa localização lógica, estado, etapa, código e orientação. Entre
os casos tratados estão pasta inexistente/indisponível, falta de permissão, falta
de espaço, conflito, falha de cópia, origem ausente e SHA-256 divergente.
Mensagens privadas de exceções de disco não são devolvidas ao navegador.

Corrija a causa e use **Tentar apenas a exportação novamente**. A retomada usa
o destino histórico; arquivos anteriormente concluídos são conferidos e mantidos,
sem recópia. Se houver conflito que exija outra pasta, salve outro destino nas
Configurações e use **Exportar arquivos** para nova exportação deliberada.
Não há nova importação, envio de documentos, repetição de notificações ou retomada
automática no reinício.

## Avisos internos por evento — histórico V9 preservado

Antes de **Conferir ZIP**, informe quem deve receber a confirmação e, se desejar,
marque **Simular os avisos desta operação**. O remetente deve estar salvo.
Sem remetente/destinatário, os avisos ficam com `CONFIGURACAO_INCOMPLETA`, sem envio.
Configurações posteriores não reescrevem a seleção histórica daquele recebimento:
o endereço e a autorização devem ser escolhidos antes da primeira conferência.

| Evento | O que comunica |
| --- | --- |
| `ARQUIVO_RECEBIDO` | Bytes recebidos pelo upload; não confirma leitura/conferência/importação |
| `CONFERENCIA_CONCLUIDA` | Leitura/conferência terminou, com contagens e necessidade de associações/confirmação |
| `IMPORTACAO_CONCLUIDA` | Commit interno confirmado; não afirma exportação ou entrega ao cliente |
| `EXPORTACAO_CONCLUIDA` | Cópia elegível confirmada no destino |
| `PROCESSAMENTO_COM_PENDENCIAS` | Diagnóstico ou exportação precisa de revisão; remete à referência da operação |

O SHA-256 do ZIP reconhece um recebimento. Cada evento lógico possui chave única
nesse recebimento; clique duplo, retransmissão e conferência técnica não duplicam
avisos. Uma tentativa existente é retornada sem novo transporte. Resultado
`DESCONHECIDO` é preservado e não autoriza repetição automática.

Os avisos não anexam XML/PDF. Usam remetente e destinatário explícitos, evento,
contagens mínimas e referência. **Ver avisos**, no pacote importado, permite
consultar o histórico após recarga/reinício e simular individualmente um aviso
`PENDENTE`. Os testes de e-mail da configuração têm histórico próprio, separado.

Os avisos internos por evento são **sempre SIMULADOS**, inclusive se o transporte documental
estiver REAL. A confirmação manual `CONFIRMACAO_MOTORISTA` da V11 é separada desses
eventos, no mesmo histórico. O contrato de transporte foi reutilizado com mensagem de metadados
separada de `MensagemPreparada` e sem anexos. O teste não verifica autenticação
ou conectividade do Outlook e não abre SMTP. Não existe campo de senha na tela;
TLS, verificação de identidade e configuração externa do SMTP documental permanecem.

Para ativar a confirmação REAL, siga o [contrato V11](confirmacao-motorista.md):
identificar a conta, configurar OAuth2 externo, manter segurança e conferir/confirmar
explicitamente cada mensagem. Salvar endereço ou marcar simulação não autoriza REAL.
Não foi executado envio externo nem homologação da conta nesta entrega.

## API

| Método e rota | Contrato |
| --- | --- |
| `GET /api/configuracoes` | Configuração salva, finalidade, modos, estado do endereço e subpastas autorizadas |
| `PUT /api/configuracoes` | `{versao, remetente, exportacaoAutomatica, subpasta}`; versão desatualizada retorna 409 |
| `POST /api/configuracoes/subpastas` | `{nome}`; um nome, nunca caminho físico |
| `GET /api/configuracoes/testes` | Até 20 resultados recentes persistidos |
| `POST /api/configuracoes/teste-pasta` | Testa somente o destino salvo |
| `POST /api/configuracoes/teste-email` | `{id: UUID, destinatario}`; chave idempotente e transporte simulado |
| `POST /api/pacotes/inspecao` | Mantém multipart; acrescenta `destinatarioConfirmacao` opcional e `simularConfirmacao` booleano |
| `GET /api/recebimentos?sha256=...` | Consulta por SHA-256 do ZIP |
| `GET /api/recebimentos/{id}` | Seleção efetiva e avisos do recebimento |
| `POST /api/recebimentos/{id}/notificacoes/{notificacaoId}/simular` | `{confirmado:true}`; nunca envia real |
| `GET /api/pacotes/{id}/exportacoes` | Histórico e itens de cada exportação |
| `POST /api/pacotes/{id}/exportacoes` | `{confirmado:true}`; destino da configuração atual |
| `GET /api/exportacoes/{id}` | Resultado e itens, sem caminho físico da origem |
| `POST /api/exportacoes/{id}/retomar` | `{confirmado:true}`; destino histórico, sem reimportação |

A inspeção retorna `X-Recebimento-Id`, inclusive em diagnóstico com pendências.
Os DTOs anteriores de inspeção/importação continuam compatíveis. Arquivos inválidos
continuam bloqueando a confirmação integral pelo fluxo existente.

## Banco, manutenção e implantação

`V9__configuracoes_notificacoes_exportacoes.sql` acrescenta:
`configuracao_operacao`, `testes_configuracao`, `recebimentos`,
`notificacoes_recebimento`, `exportacoes` e `exportacao_arquivos`.
Configuração, snapshots de avisos e exportações são separados das tentativas de
envio documental. Não há senha nesses registros. Inventário/fingerprint abrangem
as novas tabelas; o backup do H2 preserva seus dados. Os arquivos da pasta de
exportação são cópias externas e não integram o backup do acervo interno.

A etapa descrita exigia V9; o código atual exige V11. A versão do banco em uso deve ser verificada separadamente;
nenhum banco operacional foi migrado nesta tarefa. Para banco existente, mantenha
Flyway desligado e siga o procedimento de autorização, inventário e backup verificado
em [Banco e migrações](etapa4-banco-migracoes.md). Não use `clean`, `repair`, alteração
de migração publicada ou `ddl-auto=update`. Habilitar Flyway nos testes/exemplos
é destinado exclusivamente a uma base nova e isolada.

## Testes e resultados

Fixtures de CT-e/PDF sintéticas, H2 e pastas temporárias externas; sem mensagens reais.

```powershell
$Build = Join-Path $env:TEMP 'opencode/rotabrasil-configuracoes-build'
mvn -B "-Drotabrasil.build.directory=$Build" `
  '-Dtest=ConfiguracaoExportacaoHttpTest,PacoteAssistidoHttpTest,EnvioManualHttpTest,SegurancaHttpTest,ArquivamentoPacoteHttpTest,ExclusaoClienteHttpTest,MigracaoEBackupTest#migraV4ParaV8ComBackupSemAlterarDadosOuArquivos+inventarioBackupERestauracaoPreservamRetratoDasTentativasV7+migraV7ParaV8SemReescreverSnapshotsNemInventarOperadoresLegados' clean test
mvn -B "-Drotabrasil.build.directory=$Build" -DskipTests package
npm ci --prefix scripts
$Ui = Join-Path $env:TEMP ('opencode/rotabrasil-configuracoes-ui-' + [guid]::NewGuid().ToString('N'))
node scripts/testar-configuracoes.cjs "$Build/rota-brasil-0.0.1-SNAPSHOT.jar" "$Ui"
```

O script de navegador usa banco em arquivo isolado, reinicia a aplicação com
Flyway desligado e verifica persistência de configuração/testes/histórico, além
de mensagens de falha, ausência de falso sucesso, cópia automática pendente,
retomada manual, fatura compartilhada, hashes e visualização móvel.
Falta de permissão e disco cheio são injetados nos testes de I/O do backend,
permitindo exercitar os mesmos ramos de tratamento no Windows sem mexer nas
permissões de diretórios operacionais.

### Resultados registrados

- Build limpo direcionado: **81 testes aprovados**, zero falhas/erros/ignorados:
  17 de configuração/exportação, 26 de ZIP, 12 de envio manual, 7 de segurança,
  8 de arquivamento de ZIP, 8 de exclusão de cliente e 3 de migração/backup.
- Recorte funcional final, após acrescentar desativação com destino indisponível
  e revalidação de origem arquivada na retomada:
  **19 testes de configuração/exportação aprovados**, com JAR empacotado.
- Navegador real: **7 verificações aprovadas**, com encerramento/reinício e
  capturas desktop/móvel, sem erro JavaScript observado.
- Os recortes possuem testes em comum e não devem ser somados como uma única execução.
  A suíte completa do repositório não foi executada nesta entrega.

Relatórios/JAR em `C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-configuracoes-build/`;
resultados e capturas em `rotabrasil-configuracoes-ui/`, na mesma área externa.
O Playwright utilizado nesta máquina foi o já instalado na área temporária externa;
a dependência reproduzível das ferramentas do repositório permanece em `scripts/`.

### Decisões pendentes para operação

1. Autorizar e executar separadamente a migração exigida pelo código atual (V11) do banco existente, com backup
   verificado; nenhuma base operacional foi atualizada pela implementação/testes.
2. Definir e conceder acesso à raiz externa de exportação no ambiente de uso e
   executar o teste de gravação nesse ambiente.
3. Homologar SMTP/autenticação da conta Outlook para futuras notificações reais.
   Não houve envio real, armazenamento de senha pela tela ou relaxamento de TLS.
O fluxo combinado é baixar/importar o ZIP e informar seu destinatário manualmente.
A Etapa E-mail posterior enviará confirmação real ao motorista pela conta configurada;
leitura automática de mensagens e criação de login não são requisitos desta entrega.
