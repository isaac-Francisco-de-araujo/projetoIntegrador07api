# Revisão operacional — 09/11/2026

## 1. Estado examinado e limites

- Branch `main`; HEAD inicial `1b90b50`, sem alterações soltas na abertura.
- `git fetch origin` não destrutivo confirmou `origin/main` em `5dcc237`.
- Um commit local à frente, com 38 arquivos da simplificação/limpeza, ainda não
  publicado. O envio anterior falhou por autenticação; não é tratado como publicado.
- Esta revisão preserva esse commit, acrescentando alterações no working tree.
  Não houve reset, merge, descarte, nova migração, push ou implantação externa.
- Código exige V14. Só bancos H2 e arquivos sintéticos isolados foram usados;
  versão da base operacional não foi consultada nem presumida.

## 2. ZIP: causa reproduzida e resultado atual

O relato genérico de que um ZIP operacional não importa **não foi atribuído a uma
causa específica sem seus registros**. No baseline local desta revisão, o ZIP
sintético com dois CT-es e dois PDFs chegou à confirmação com HTTP 200 e documentos
persistidos, sem e-mail de motorista.

A comparação opcional do roteiro de navegador carrega **somente `painel.js` de
`5dcc237`** sobre o servidor atual isolado e injeta 503 em `painel-saude.js`. O
console registra o 503 e nenhuma chamada `/api/` ocorre: o import estático rejeita
o grafo antes de executar a inicialização. Isso reproduz o defeito do bootstrap
anterior; não é uma execução integral da distribuição antiga nem prova da causa
do episódio operacional original.

No bootstrap local atual, a correção já presente em `1b90b50` foi preservada:
sessão e contrato são verificados; eventos essenciais são configurados; Saúde,
Busca, Administração e Configurações carregam sob demanda. Falhas controladas
nos módulos Saúde, Busca e Administração geram erro local e a inspeção ZIP continua
respondendo 200. Falha de sessão ou contrato bloqueia a operação corretamente.

Na execução final (`rotabrasil-pendencias-20261109-ui-final`):

| Etapa | Evidência |
|---|---|
| Versão/sessão | GET 200; contrato `multioperador-V14-20261102`; estáticos do mesmo JAR empacotado |
| Esquema sintético | Log de criação/migração isolada até V14; reinício operacional com Flyway desligado |
| Inspeção | POST `/api/pacotes/inspecao` 200; correlação `6fbc5ccb-75ff-4f39-a5b4-e20b1d7a9efa` |
| Antes da confirmação | Zero documentos no acervo; 2 associações pendentes bloqueiam Continuar com orientação junto ao botão |
| Confirmação | POST `/api/pacotes/confirmacao` 200; correlação `3b81e6d1-fb86-4c14-b868-870e32e41f2a`; 2 XMLs persistidos |
| Inválidos | PDF/XML recusados com 400, arquivo/problema/orientação visíveis; sem importação parcial |
| Resposta perdida | Commit 200 no servidor; navegador mantém resultado incerto; reconferência e repetição deliberadas reutilizam o pacote |
| Exportação | Falha da pasta preserva importação; retomada somente da cópia conclui com SHA-256 |

As duas correlações foram conferidas em `resultado.json` **e** `servidor.log` da
mesma execução. O 503 injetado na inspeção não chegou ao backend; não se inventa
uma correlação de servidor para ele. Console e erros esperados ficam separados
dos erros JavaScript não tratados (zero nos roteiros aprovados).

Se o ZIP da operação continuar falhando, obter a etapa, o status, o diagnóstico
seguro, a referência de correlação e a identificação da distribuição em execução.
Não solicitar documentos reais, senha/token ou supor problema de banco/cache.

## 3. Alterações desta revisão

As 12 abas, resumos, passos ZIP, vínculos explícitos, escolha de PDF, rascunho,
filtros recolhidos, diálogos e histórico separado **já existiam no commit local**.
Foram verificados, sem recriação. [Mapa das ferramentas](uso-diario-simplificado.md#onde-estão-as-ferramentas).

Corrigida uma inconsistência reproduzida em Configurações: o resumo do envio ao
cliente usava `confirmacaoMotorista.realDisponivel`. A fixture com modo documental
SIMULADO e canal opcional pronto mostrava verificação da conta em vez de Envio não
configurado. O resumo agora usa `modoEnvioDocumental`/`situacaoEmail`: envio não
configurado, divergência de remetente (vermelho) ou configuração externa ainda não
verificada (âmbar). Endereço salvo não é apresentado como autenticação.

Arquivos de produção alterados: `painel-configuracoes.js` e `painel-diario.css`.
Não foi necessário alterar serviço, transporte, DTO, migração ou regras do backend.
Os acréscimos Java são testes nas suítes existentes de backup e saúde.

## 4. Regra comercial e e-mail

Outlook da empresa é o remetente; cliente recebe documentos/fatura explicitamente
selecionados. Download/upload de ZIP e identificação visual do CT-e no PDF são
manuais. Confirmação ao motorista é opcional, sem endereço obrigatório, transporte
automático ou inferência de destinatário de fatura compartilhada.

O [contrato documental](envio-manual.md#regra-atual-envio-documental-ao-cliente--09112026)
agora separa a regra atual dos capítulos V7 e documenta XOAUTH2/SMTP.Send,
configuração externa, aquisição/renovação externas e homologação futura. O
adaptador real já existe; não foi habilitado. A API e o histórico de confirmação
opcional permanecem preservados. O utilitário de aquisição/renovação OAuth2 não
integra o repositório: ainda precisa ser aprovado/provisionado externamente.

## 5. Login, dois operadores e instalação central

Login, sessão válida, logout, expiração, CONSULTA/OPERADOR/ADMIN, API anônima,
CSRF e importação protegida foram exercitados no roteiro diário em HTTPS local
descartável. O roteiro multioperador usa quatro contextos independentes: disputa
atômica de responsável, liberação/transferência com histórico e edição antiga
recusada com rascunho preservado. Nada disso exige remover segurança ou criar
contas padrão conhecidas.

A [preparação centralizada](uso-compartilhado.md#inicialização-aceite-e-encerramento-controlados)
inclui serviço único, Java 25/JAR fixo, banco/acervo privados, exportação separada,
HTTPS/proxy confiável, cookies, contas individuais, verificação offline V14,
encerramento normal, reinício e roteiro de aceite nos PCs autorizados.
Não foram abertas portas, implantados serviços ou migrados dados da empresa.

## 6. Backup e restauração

Reutilizadas as ferramentas existentes. O novo teste de V14 executa a CLI de
backup offline e chama a verificação novamente. Depois retira os originais do
caminho usado pelo banco, restaura os arquivos do snapshot, abre outro H2 em
somente leitura e compara inventário completo, bytes XML/PDF, vínculo da fatura,
V14 e tentativa com resultado DESCONHECIDO. Não usa originais para mascarar falta
de recuperação. [Evidências e limites](etapa4-banco-migracoes.md#evidências-separadas-e-teste-v14--09112026).

Outro teste comprova que um manifesto localizado mantém Saúde em Não verificado,
sem promover integridade/restauração a sucesso. Resultados JUnit são evidências
**sintéticas**; não são comprovantes de backup/restauração da empresa.

## 7. Testes desta revisão (sem soma histórica)

| Execução | Resultado final |
|---|---|
| Build limpo externo `clean test` | 389 contabilizados, 388 aprovados, zero falhas/erros, 1 ignorado (symlink Windows) |
| Recorte backup/saúde antes da suíte completa | 19 testes aprovados; não somados ao total acima |
| Empacotamento externo | Aprovado; JAR com estáticos atuais |
| `testar-uso-diario.cjs ... 5dcc237` | 14 grupos aprovados e comparação de bootstrap anterior; zero erros JS não tratados |
| `testar-uso-compartilhado.cjs` | 10 grupos aprovados |
| `testar-exclusoes-paginas.cjs` | 10 grupos aprovados; XML/PDF/ZIP, fatura compartilhada, SHA-256, arquivamento/restauração, filtros e navegação |
| `testar-zoom-uso-diario.cjs` | 100%, 150%, 200%; larguras CSS 1350/900/675, sem overflow nas telas exercitadas |

O teste novo do resumo documental falhou **antes** da correção, comprovando a
inconsistência. A execução final passou sem afrouxar as expectativas. Build
exibe avisos preexistentes de depreciação e de metadados de repositório opcional;
os testes concluíram com BUILD SUCCESS. As exceções esperadas de esquema inválido
na suíte são verificações negativas, não falhas de execução.

Capturas das 12 abas em 1366×768, 1920×1080 e 390×844; menu móvel/recolhido,
teclado/foco e diálogos exercitados. Amostra de Configurações: 15 px, contraste
mínimo 7,06:1 claro / 9,36:1 escuro. Não é auditoria completa de acessibilidade.
O atalho de zoom em headless não mudou o zoom; a evidência válida é o roteiro
separado de zoom real do Chrome com perfil temporário, sem CSS zoom/transform.

## 8. Capturas e reprodução

Artefatos externos sob `C:/Users/florb/AppData/Local/Temp/opencode/`:

- `rotabrasil-pendencias-20261109-build/`: JAR e `surefire-reports/`, incluindo os
  métodos novos de `MigracaoEBackupTest` e `SaudeCompartilhadaTest`.
- `rotabrasil-pendencias-20261109-baseline/`: execução do estado inicial atual.
- `rotabrasil-pendencias-20261109-reproducao/`: falha esperada do resumo antes da correção.
- `rotabrasil-pendencias-20261109-ui-final/`: resultado JSON, log sintético e capturas finais.
- `rotabrasil-pendencias-20261109-multi/`: dois operadores e permissões.
- `rotabrasil-pendencias-20261109-arquivos/`: integridade e navegação.
- `rotabrasil-pendencias-20261109-zoom/`: medições/capturas de zoom real.

Roteiro visual: `zip-selecao-1366.png` → `zip-associacoes-1366.png` →
`pacotes-1366.png` (resultado persistido). Falhas em `zip-pdfInvalido-1366.png`
e `zip-xmlInvalido-1366.png`; configuração em `configuracoes-1366.png` e
`configuracoes-390.png`. `inicializacao-anterior-modulo-503.png` é evidência do
bootstrap anterior, não a aparência entregue. Artefatos não são versionados.

```sh
mvn -B -Drotabrasil.build.directory=BUILD_EXTERNO clean test
mvn -B -Drotabrasil.build.directory=BUILD_EXTERNO -DskipTests package
node scripts/testar-uso-diario.cjs BUILD_EXTERNO/rota-brasil-0.0.1-SNAPSHOT.jar SAIDA_EXTERNA 5dcc237
node scripts/testar-uso-compartilhado.cjs BUILD_EXTERNO/rota-brasil-0.0.1-SNAPSHOT.jar SAIDA_MULTI
node scripts/testar-exclusoes-paginas.cjs BUILD_EXTERNO/rota-brasil-0.0.1-SNAPSHOT.jar SAIDA_ARQUIVOS
node scripts/testar-zoom-uso-diario.cjs BUILD_EXTERNO/rota-brasil-0.0.1-SNAPSHOT.jar SAIDA_ZOOM
```

Usar Playwright do `scripts/package.json` ou a instalação externa conforme o guia
de testes. A comparação é opcional, exige o commit disponível no Git e nunca altera
o checkout. Não usar esse roteiro de falhas contra uma instância operacional.

## 9. Respostas e autorizações ainda necessárias

1. A caixa Outlook da empresa é Microsoft 365/Exchange Online? Quem confirma tenant,
   modalidade da caixa, SMTP AUTH e autoriza a aplicação OAuth2? Não enviar segredos.
2. Qual máquina central, URL HTTPS e equipe/conta de serviço serão responsáveis pela
   instalação e pelo aceite real nos PCs? Nenhuma implantação externa está autorizada.
3. Quais destino, frequência, responsável, retenção e prazo/janela de recuperação
   do backup? Nenhuma política foi inventada ou exclusão automática ativada.
4. Se persistir o relato do ZIP: qual etapa e diagnóstico seguro da execução real?
   Sem essa evidência, o sucesso sintético não encerra diagnóstico de um pacote específico.
