# Destinatário editável — reprodução por interação real — 13/11/2026

**Continuação:** [rastreamento de todas as atribuições de disabled, investigação dos
fieldsets de Configurações e artefato mais recente](disabled-destinatario-configuracoes.md).

## Causas comprovadas

### JAR que continua na porta 8080

Confirmado novamente PID **13676**, `127.0.0.1:8080`, executando o JAR
`C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-marca-20261113-build/rota-brasil-0.0.1-SNAPSHOT.jar`.
O SHA-256 permanece `adae85306e18308097cd9e20a2dbd7fdfbf69e0168943713be06a19ee317ea6f`.

Foi iniciado **o mesmo artefato**, em outra porta, com H2 e três documentos novos
sintéticos. No módulo empacotado `static/painel-documentos.js`, linha 18:

```js
$('destinatario').disabled = !selecionado || !state.envioReal || !podeOperar() || state.carregando || state.previaOcupada;
```

Com seleção válida, ADMIN e modo SIMULADO, `!state.envioReal` mantém o atributo
disabled. Um clique REAL no centro do campo não dá foco; teclas reais deixam o
valor vazio. Não é apenas uma mensagem de configuração ou falha visual.

Inspeção no navegador, depois de a abertura terminar:

| Verificação | Observação no JAR antigo |
|---|---|
| `disabled` / `:disabled` | `true` / `true` |
| `readOnly` | `false` |
| Fieldset desabilitado / ancestral inert | Nenhum |
| `pointer-events` do input | `auto` |
| `z-index` do input | `auto` |
| Elemento no centro do input | O próprio `#destinatario`, não um overlay |
| Abertura | `hidden=true`, `body.is-loading=false` |
| Foco depois do clique | Não recebe |
| Valor depois das teclas | Continua vazio |
| Endereço de exemplo | Placeholder; não há valor preenchido |
| Erros JavaScript | Nenhum |

Não foi aberta a sessão/banco da porta 8080. Identificamos o processo e auditamos
o artefato de código; toda reprodução usou instância e banco novos.

### Regressão adicional no JAR v2 da correção anterior

O JAR `rotabrasil-central-v2-jar`, SHA-256
`528e1b358c78440c3b29d56048dd440eeb4efac1668c7880a225c7c414775785`, permite
digitar em repouso, mas a linha 18 ainda era:

```js
$('destinatario').disabled = !state.viagem || !podeOperar() || state.carregando || state.previaOcupada;
```

O teste reteve a resposta de `/api/consulta/resumo`, clicou **Atualizar dados**
e tentou editar. `state.carregando` deixou o input disabled durante a requisição,
sem overlay, sem readonly e sem ancestral inert. O valor foi preservado, mas não
aceitava novas teclas. O teste normal de editabilidade falhou nesse ponto no JAR v2.
`state.previaOcupada` também vinculava desnecessariamente edição e preparação.

## Correção implementada

Em `front/painel-documentos.js`, `atualizarSelecao()` agora separa:

- **Edição:** bloqueada somente por sessão/permissão não verificada ou ausência de
  viagem. Não depende de SMTP, consultas de dados/configuração ou prévia em andamento.
- **Conferência:** continua bloqueada por consulta/preparação em andamento, seleção
  ausente e destinatário vazio/inválido.
- **Confirmação:** preserva seu contrato de prontidão real, checkbox, sessão,
  CSRF e conferência persistida. Não houve mudança no transporte ou no backend.

O estado de edição é calculado por `bloqueioEdicao`, com explicação própria junto
ao campo em `front/index.html`. O disabled inicial continua protegendo a inicialização;
não foi removido indiscriminadamente do HTML, nem foi alterado qualquer papel.

Cada evento `input` já invalida a conferência e sua geração assíncrona. Isso agora
também é exercitado enquanto a prévia aguarda o servidor: a resposta antiga não
reaparece e a próxima conferência contém o novo destinatário. Trocar viagem limpa
a conferência, mantendo o endereço apenas como rascunho para nova revisão.

Nenhuma consulta recria o input ou atribui valor a ele. A abertura e o CSS não foram
alterados: o hit-test comprovou que não eram a causa dos bloqueios reproduzidos.

## Teste de navegador e evidências

Novo roteiro: `scripts/testar-destinatario-editavel.cjs`.

Usa clique real via mouse, Tab, teclas (`keyboard.type`, Home, Backspace, Ctrl+A) e
Ctrl+V. A Clipboard API escreve somente texto sintético na área de transferência
do contexto; o input é preenchido pelo comando nativo de colagem. **Não usa
atribuição JavaScript de value, dispatch de input falso nem fill como prova de digitação.**
Eventos `input`/`paste` com `isTrusted=true`, foco e valores foram registrados.

Executado antes/depois:

1. JAR da porta 8080: diagnóstico reproduz disabled e ausência de foco/entrada.
2. JAR v2: diagnóstico reproduz bloqueio durante consulta de dados.
3. Mesmo teste normal contra v2: falha em `toBeEnabled()` durante atualização.
4. Mesmo teste contra o JAR corrigido: passa integralmente.

Cobertura: três arquivos selecionados, clique, digitar, editar, apagar, colar,
somente teclado, preservação após consulta de configuração, 503 e recuperação,
mesmo nó DOM após consultas/atualização, digitação durante carregamento/prévia,
descarte de resposta obsoleta, troca de viagem, desktop 1440×1000 e celular 390×844.
Conferência persistida com três arquivos; zero tentativas de envio nesse roteiro.

Suíte integrada com navegador protegido e double de transporte: **418 testes JUnit,
417 aprovados, 1 ignorado (symlink Windows), zero falhas/erros**. CONSULTA e sessão
expirada continuam bloqueadas, CSRF e idempotência preservados. Nenhum e-mail externo.

Evidências em `C:/Users/florb/AppData/Local/Temp/opencode/`:

| Evidência | Arquivo/pasta |
|---|---|
| Bloqueio de foco/teclas no artefato antigo | `rotabrasil-digitacao-antigo/resultado.json`, `input-bloqueado.png` |
| Disabled durante atualização na v2 | `rotabrasil-digitacao-v2/resultado.json`, `atualizacao-bloqueia-input.png` |
| Teste normal falhando antes | `rotabrasil-digitacao-regressao-antes/resultado-falha.json` |
| Teste passando depois / estados inspecionados | `rotabrasil-digitacao-depois/resultado.json` |
| Eventos reais de digitação/colagem | `rotabrasil-digitacao-depois/eventos.json` |
| Campo focado após digitar/colar | `rotabrasil-digitacao-depois/desktop-destinatario-focado.png` |
| Campo focado no celular | `rotabrasil-digitacao-depois/mobile-destinatario-focado.png` |
| Conferência desktop/celular | `rotabrasil-digitacao-depois/desktop-digitacao-conferencia.png`, `mobile-digitacao-conferencia.png` |
| Regras exatas dos três artefatos | `rotabrasil-digitacao-artefatos.json` |
| Suíte integrada / transporte controlado | `rotabrasil-digitacao-test.log`, `rotabrasil-digitacao-double/resultado-real-double.json` |

## Identificação e início isolado

JAR validado:

```text
C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-digitacao-jar/rota-brasil-0.0.1-SNAPSHOT.jar
```

Compilação **2026-11-13T20:01:57Z**; contrato `documental-conferencia-v2`, esquema V15.
SHA-256:

```text
7cd5ceec23669177b80a42a2af2c0ad7b02a7b110c9095f23937b11926df0354
```

Na raiz do repositório, PowerShell:

```powershell
$Jar = 'C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-digitacao-jar/rota-brasil-0.0.1-SNAPSHOT.jar'
(Get-FileHash -Algorithm SHA256 -LiteralPath $Jar).Hash
node scripts/iniciar-central-isolada.cjs $Jar 8092
```

Abra **http://127.0.0.1:8092/painel#email**. Escolha a viagem sintética em Viagens
e documentos, marque os três arquivos e abra Envios. O iniciador força banco/acervo
novos e modos SIMULADO. Ctrl+C encerra somente essa instância.
Para repetir o teste automatizado com Playwright disponível em NODE_PATH:

```powershell
node scripts/testar-destinatario-editavel.cjs $Jar "$env:TEMP/opencode/rotabrasil-digitacao-repeticao"
```

A porta 8080 ainda usa o artefato antigo: não foi substituída automaticamente.
Uma recarga nessa porta não aplica a correção. Nenhum banco operacional foi acessado.

## Pendências externas, distintas do bug de interface

Ausência de SMTP não bloqueia edição nem preparação. A confirmação REAL depende
de credencial/consentimento Google autorizados no processo Java, remetente,
configuração documental, TLS e sessão protegida. Nomes exatos e provisionamento
seguro continuam em [iniciar-central-corrigida-gmail.md](iniciar-central-corrigida-gmail.md#configuração-externa-gmail-documental--nomes-exatos).
Digitar/colar, gerar conferência e passar com double não homologam Gmail, autenticação,
recebimento ou leitura. Não foram enviados e-mails externos nem consultadas credenciais.
