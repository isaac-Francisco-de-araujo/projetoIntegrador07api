# Disabled do destinatário e fieldsets de Configurações — 13/11/2026

## Condição ativa comprovada

O processo identificado na porta 8080 continua sendo o PID 13676, com o artefato
`rotabrasil-marca-20261113-build/rota-brasil-0.0.1-SNAPSHOT.jar`.
Foi reproduzido **esse mesmo JAR**, em outra porta, usando banco H2 novo e três
arquivos sintéticos. Não foi consultado o banco ou a sessão operacional.

No `static/painel-documentos.js:18` desse JAR:

```js
$('destinatario').disabled = !selecionado || !state.envioReal || !podeOperar() || state.carregando || state.previaOcupada;
```

O teste leu o estado do módulo já carregado, sem modificá-lo:

| Condição | Valor observado |
|---|---|
| Viagem selecionada | `true` |
| Documentos selecionados | `3` |
| `podeOperar()` | `true` |
| `state.carregando` | `false` |
| `state.previaOcupada` | `false` |
| `state.envioReal` | **`false`** |
| `input.disabled` / `matches(':disabled')` | **`true` / `true`** |

Logo, o termo ativo é **`!state.envioReal`**, não permissão, seleção ou processamento.
O navegador recebe o input, sem overlay/inert/fieldset ancestral; porém o disabled
nativo impede foco e digitação. Readonly=false e pointer-events=auto foram confirmados.
O texto de exemplo é placeholder. A abertura estava encerrada e não houve erro JS.

## Todas as atribuições que atingem `#destinatario`

Rastreamento das fontes atuais em `front/`:

| Origem | Atribuição/condição | Chamador |
|---|---|---|
| `index.html:187` | Atributo `disabled` inicial do input | Parse do HTML; proteção até inicializar sessão/estado |
| `painel-base.js:62` | `n.disabled=true` para `#form-email input` quando `!podeOperar()` | `iniciarSessao()`, chamado por `painel.js` após verificar versão |
| `painel-base.js:69` | `n.disabled=true` para inputs de todos os formulários | Evento `sessao-expirada`, disparado por HTTP 401 e revalidação de sessão |
| `painel-documentos.js:21` | `disabled = Boolean(bloqueioEdicao)` | `atualizarSelecao()`; regra central de edição |

Não foram encontradas outras atribuições, `setAttribute('disabled')` ou
`toggleAttribute('disabled')` que atinjam esse input. O `destinatario-confirmacao`
é outro campo, do canal opcional ao motorista, e tem regras próprias.

Chamadores de `atualizarSelecao()`:

- Checkbox de documento (`painel-documentos.js:46`) e PDF (`:203`).
- Início/fim de `carregarDocumentos()` (`:81`, `:94`), chamado pelo seletor de viagem,
  abertura de viagem nas consultas, filtro de arquivados e atualização de dados.
- Início de `atualizar()` (`:106`), chamado no bootstrap, no botão Atualizar dados e
  após operações que precisam reler viagens/documentos.
- Evento `input` do destinatário e expiração de sessão (`:192–193`).
- `painel-email.js`: início/fim da conferência (`:40`, `:53`) e `finally` da consulta
  documental (`:22`). O callback é registrado por `configurarEmail({atualizarSelecao})`
  em `painel.js:46`.

Portanto não basta retirar o atributo do HTML/Console: o próximo desses eventos
reaplica a regra do módulo empacotado.

### Regra final presente no JAR corrigido

`bloqueioEdicao` considera somente sessão/permissão e ausência de viagem. SMTP,
consulta documental, carregamento de dados e prévia pendente não bloqueiam digitar.
Os bloqueios de processamento continuam em **Gerar prévia** e nas confirmações.
Cada edição invalida a conferência; resposta antiga não reaparece após modificar o
endereço. Confirmar envio REAL continua dependendo da conferência persistida,
prontidão documental, permissão, CSRF e aceite explícito.

## Por que Configurações tem fieldsets desabilitados

São ramos HTML distintos. `#campos-configuracoes` não é ancestral de `#destinatario`.
Em Configurações, o input pode ter `disabled=false` mas `matches(':disabled')=true`
porque **herda** o bloqueio do fieldset. Isso foi observado no navegador.

### Fieldset principal

- HTML inicial em `index.html:201`: `fieldset disabled`, pois a tela é inicializada
  sob demanda e ainda não existe consulta confirmada.
- Atribuição em `painel-configuracoes.js`, `controles()` (`:21–32`). Antes usava
  apenas `podeAdministrar() && salvo && !ocupado`.
- `controles()` é chamado por carregamento/finalização da consulta, preenchimento
  de GET/PUT bem-sucedido, ações e seus `finally`, edição do formulário e eventos
  da conta administrativa de teste. Agora também reage à expiração da sessão.

Reproduções antes da alteração, com ADMIN e SMTP ausente:

1. Antes de abrir o módulo: fieldset desabilitado, estado inicial legítimo.
2. GET pendente: fieldset desabilitado, carregamento legítimo.
3. Primeira consulta HTTP 503: sem snapshot salvo, permanece desabilitado.
4. Recuperação HTTP 200: habilita normalmente, mesmo sem SMTP.
5. Reconsulta pendente depois de sucesso: desabilita, **mas ainda mostrava
   “Configuração salva”** — diagnóstico incorreto durante o carregamento.
6. Reconsulta HTTP 503 depois de sucesso: voltava a habilitar com snapshot anterior,
   embora a consulta atual tivesse falhado — validade do estado não era explícita.

Corrigido com `faseConsulta` e motivo visível próximo dos campos:

- `INICIAL`, `CARREGANDO`, `PROCESSAMENTO`, `CONSULTA_FALHOU`, `PERMISSAO`,
  `SESSAO_ENCERRADA` ou `NENHUM` em `data-bloqueio`.
- `aria-busy` indica operação em andamento; `aria-describedby` relaciona o motivo.
- Uma reconsulta pendente mostra carregamento. Uma falha não é promovida a ausência
  de SMTP nem a configuração confirmada. A edição aguarda consulta válida e há
  botão de recuperação; o DOM não é apagado na falha.
- Eventos da conta de teste não reabilitam a configuração principal desconhecida.
- ADMIN sem SMTP pode editar após GET 200. CONSULTA/OPERADOR e sessão encerrada
  permanecem bloqueados por permissão, com explicação.

### Fieldset da conta de teste

`#campos-conta-teste` começa disabled (`index.html:212`). Em
`painel-conta-teste.js`, `controles()` usa `!salvo || ocupado || !podeAdministrar()`.
É independente do fieldset principal e não representa autenticação documental.
Foram acrescentados mensagem de carregamento/gravação, identificação do bloqueio,
aria-busy e atualização na expiração da sessão. Falha da conta de teste mantém
seu fieldset bloqueado, mas não bloqueia o principal se este tem consulta válida.

Só o atributo disabled da captura não distingue qual condição estava ativa em
Configurações daquela sessão. Não consultamos o banco operacional para inferi-la;
os casos acima foram reproduzidos separadamente e agora têm motivo explícito na UI.

## Validação e identidade do artefato

JAR efetivamente iniciado e validado:

```text
C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-disabled-jar/rota-brasil-0.0.1-SNAPSHOT.jar
```

Compilação: **2026-11-13T20:37:10Z**. Contrato: `documental-conferencia-v2`, esquema V15.
SHA-256: `09633018f3602d439b4bcd2763a3e6efee900f5b37d27511ac0156e213c4c323`.

`painel-versao.js` confere hashes dos módulos de edição **e também** de Configurações
e conta de teste com `/api/versao` da mesma origem. O relatório dos testes identifica
caminho, SHA do JAR, compilação e hashes dos recursos servidos.

- `scripts/testar-destinatario-editavel.cjs`: bloqueio antes, digitação real depois
  com SMTP ausente, clique/Tab/teclas/colar/apagar, desktop/celular, 503/recuperação,
  atualização de dados, prévia atrasada, troca de viagem, conferência e envio real bloqueado.
  O estado de cada condição é registrado sem alterar as variáveis do módulo.
- `scripts/testar-campos-configuracoes.cjs`: estados inicial/carregamento/falha,
  recuperação, dependências independentes, digitação e processamento legítimo de
  gravação de preferências sintéticas. Usa propriedade nativa do fieldset e
  `:disabled` do input, não presume o estado apenas pelo matcher do fieldset.
- `scripts/testar-configuracoes.cjs`: **7 grupos aprovados**, incluindo falha ao
  salvar, recuperação, simulação sem SMTP, exportação e reinício da base isolada.
- Suíte Java integrada: **418 testes, 417 aprovados, 1 ignorado (symlink Windows),
  zero falhas/erros**. Inclui navegador protegido, ADMIN/CONSULTA/OPERADOR, sessão
  expirada, CSRF e confirmação com transporte double sem chamada ao JavaMailSender.

Evidências em `C:/Users/florb/AppData/Local/Temp/opencode/`:

| Evidência | Arquivo/pasta |
|---|---|
| Estado ativo antes da correção | `rotabrasil-disabled-antes/resultado.json` |
| Digitação e estados depois | `rotabrasil-disabled-depois/resultado.json`, `eventos.json` |
| Capturas de foco desktop/celular | `rotabrasil-disabled-depois/*destinatario-focado.png` |
| Estados anteriores dos fieldsets | `rotabrasil-config-campos-antes/resultado.json` |
| Estados corrigidos e recuperação | `rotabrasil-config-campos-depois/resultado.json` |
| Falha com razão visível / recuperação | `rotabrasil-config-campos-depois/reconsulta-falhou.png`, `campos-recuperados.png` |
| Permissão bloqueada em sessão protegida | `rotabrasil-disabled-double/configuracoes-consulta-bloqueada.png` |
| Auditoria dos artefatos | `rotabrasil-disabled-artefatos.json` |
| Iniciador realmente executado | `rotabrasil-disabled-iniciador.log` |
| Suíte integrada | `rotabrasil-disabled-test.log` |

## Inicialização isolada

Na raiz do repositório, com Java 25 e Node disponíveis:

```powershell
$Jar = 'C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-disabled-jar/rota-brasil-0.0.1-SNAPSHOT.jar'
(Get-FileHash -Algorithm SHA256 -LiteralPath $Jar).Hash
node scripts/iniciar-central-isolada.cjs $Jar 8092
```

Abra **http://127.0.0.1:8092/painel#email**. O iniciador cria banco/acervo novos e
três arquivos sintéticos. Selecione-os em Viagens e documentos e volte a Envios.
Todos os modos ficam SIMULADO; Ctrl+C encerra somente essa instância.
O modo `--verificar` desse comando foi executado com o mesmo artefato.

A porta 8080 continua com seu processo antigo: não houve substituição automática,
reinício ou acesso ao banco operacional. Use a URL isolada acima para a versão entregue.
Nenhum e-mail externo foi enviado. Requisitos de segurança do backend não foram
alterados. Salvar endereço e passar com transporte controlado não homologam Gmail.
