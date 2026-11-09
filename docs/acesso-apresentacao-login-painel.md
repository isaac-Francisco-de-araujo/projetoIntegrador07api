# Acesso: apresentação → login → painel

## Rotas e comportamento

| Rota | Uso |
|---|---|
| `/` | Apresentação pública com marca, frase curta e **Entrar** |
| `/login` | Formulário existente do Spring Security; sessão válida segue para `/painel` |
| `/painel` | Painel operacional existente, protegido em `operacao` |
| `/index.html` | Acesso compatível ao mesmo painel, igualmente protegido |
| `/#aba` | Favorito antigo: script leve encaminha para `/painel#aba` |
| `/logout` | POST com CSRF; encerra a sessão e leva a `/login?logout` |

Antes: **login → carregamento → painel**. Agora: **apresentação → Entrar → login →
carregamento essencial → painel**. Com sessão válida, Entrar passa por `/login` e
chega ao painel sem pedir a senha novamente. Abrir `/` continua mostrando a apresentação.

A apresentação carrega apenas seu HTML, `acesso.css`, `acesso-logo.svg` e `acesso.js`.
Não consulta APIs, sessão, indicadores, documentos ou módulos operacionais. O script
somente mantém os favoritos antigos e o fragmento local ao atravessar o formulário.
Sem JavaScript, a apresentação e o formulário funcionam; favoritos `/#aba` exigem
JavaScript, assim como o próprio painel.

O HTML do painel permanece em `static/index.html`; `EntradaController` o entrega em
`/painel` e `/index.html`, aplicando o context path aos recursos. APIs, downloads e
bibliotecas PDF/ZIP usam o contexto derivado do próprio módulo `painel-rotas.js`.
As abas e aliases continuam os mesmos: por exemplo, `/painel#documentos` abre Viagens.
Não existe parâmetro de destino externo após login; o destino do servidor é fixo.

## Segurança e sessão

- Públicos em operação: GET `/`, CSS/logo/script de acesso, `/login` e a rota técnica
  `/error`. Estáticos do painel e WebJars continuam exigindo autenticação.
- Navegação HTML protegida sem sessão redireciona para `/login`. APIs sem sessão
  respondem JSON 401, inclusive escrita sem sessão; permissão/CSRF recusados com sessão
  respondem 403. Uma resposta HTML de login não é tratada como sucesso de API.
- BCrypt, CSRF, sessão, cookies Secure/HttpOnly/SameSite e revalidação de conta/papel
  são os mecanismos existentes. Não há cadastro público.
- Primeiro ADMIN: bootstrap seguro já existente; demais contas: **Operadores**, por
  ADMIN. Versão concorrente, auditoria, redefinição de acesso e proteção do último
  ADMIN ativo permanecem nos serviços existentes.
- HTML e recursos usam `no-store`. Ao sair/voltar ao histórico, a página antiga
  revalida a sessão antes de reapresentar os dados. Em 401, oculta os dados e menus,
  fecha diálogos, bloqueia ações e oferece **Entrar novamente**. O rascunho permanece
  apenas na memória daquela aba; recarregar não reaplica importações nem envios.
- Retornar a uma aba visível também revalida a sessão. Outro logout ou expiração
  é detectado nessa revalidação ou na próxima requisição, sem promessa de notificação
  instantânea entre navegadores.

`painel-abertura.js` continua sendo carregamento interno: contrato e sessão antes de
liberar ações, sem espera mínima. Telas secundárias continuam sob demanda e suas
falhas são isoladas. A apresentação não inicializa esses módulos.

## Experimentar com segurança

### Verificação automatizada reproduzível

Requisitos: Java 25, Maven, Node.js, Playwright conforme `scripts/package.json`,
Chrome disponível (ou `ROTABRASIL_BROWSER_CHANNEL` ajustado). Use diretórios externos
novos. Exemplo PowerShell:

```powershell
$Build = Join-Path $env:TEMP 'opencode/rotabrasil-entrada-build'
$Saida = Join-Path $env:TEMP ('opencode/rotabrasil-entrada-' + [guid]::NewGuid().ToString('N'))
mvn -B "-Drotabrasil.build.directory=$Build" clean test
mvn -B "-Drotabrasil.build.directory=$Build" -DskipTests package
node scripts/testar-entrada.cjs "$Build/rota-brasil-0.0.1-SNAPSHOT.jar" "$Saida"
```

Se Playwright estiver instalado fora do projeto, configure `NODE_PATH` para aquela
pasta `node_modules`, como nos demais roteiros. O script prepara uma base nova e uma
conta sintética em desenvolvimento loopback, encerra esse processo e faz todo o
aceite em **operacao com HTTPS**, migrações desligadas e cookies Secure. Repete com
context path `/rota`. A senha aleatória e o certificado descartável ficam somente
no ambiente temporário; o script encerra seus processos e remove a base ao concluir.
O navegador aceita somente o certificado sintético desse teste via contexto isolado;
as proteções operacionais do servidor não são desativadas.

### Experiência manual em ambiente protegido

1. Prepare uma **base nova isolada**, conforme os comandos de desenvolvimento do
   README, usando banco, acervo e exportação temporários. Inicialize o esquema
   exigido pelo JAR atual e encerre normalmente. Não reutilize a base da empresa.
2. Gere o hash do primeiro administrador pela CLI existente em terminal interativo:

   ```sh
   java -Dloader.main=br.com.rotabrasil.manutencao.GerarHashSenha -cp CAMINHO_DO_JAR org.springframework.boot.loader.launch.PropertiesLauncher
   ```

   Provisione `ROTABRASIL_ADMIN_USUARIO` e `ROTABRASIL_ADMIN_HASH` por configuração
   externa privada. Não informe senha em argumento, URL ou arquivo versionado.
3. Configure certificado de teste confiável para localhost e armazenamento TLS
   externo. Inicie o mesmo JAR com `--spring.profiles.active=operacao`, banco isolado
   com `IFEXISTS=TRUE;WRITE_DELAY=0`, acervo isolado, `spring.flyway.enabled=false`,
   Hibernate `validate`, H2 Console desligado, `server.ssl.enabled=true` e chave TLS
   provisionada externamente. Preserve cookies Secure e ambos os modos de e-mail
   SIMULADO, com `rotabrasil.email.smtp.ativado=false`.
4. Abra a URL HTTPS: confira apresentação, Entrar e login; use o ADMIN que você
   configurou. Cadastre contas sintéticas OPERADOR e CONSULTA pela aba Operadores.
   Teste em perfis independentes, não somente em abas da mesma sessão.
5. Teste acesso direto a `/painel`, `/login` com sessão, logout, histórico do navegador
   e expiração. Confirme que nenhuma alteração é reaplicada automaticamente.

Em `desenvolvimento`, `/login` explica o acesso local e oferece **Abrir painel local**;
não há autenticação nesse perfil. Isso não serve como validação do fluxo protegido.
Instalação real segue [uso compartilhado](uso-compartilhado.md), inclusive autorização
e backup verificado antes de qualquer migração de uma base existente.

## Evidências desta tarefa

O roteiro de entrada registra apresentação, login, painel autenticado e expiração em
1366×768 e 390×844, também com `/rota`. Valida teclado/foco, ausência de overflow,
CSRF, credenciais inválidas, redirecionamentos, downloads protegidos, os três papéis,
cadastro e alteração por ADMIN, negação de cadastro sem permissão e auditoria.
Os resultados são de dados sintéticos; não homologam infraestrutura da empresa.

Artefatos externos em `C:/Users/florb/AppData/Local/Temp/opencode/`:

- `rotabrasil-entrada-20261109-build/`: build e relatórios JUnit;
- `rotabrasil-entrada-20261109-ui-v2/`: primeira validação completa do fluxo protegido,
  antes das alterações concorrentes V15; `resultado.json`, oito grupos aprovados;
- `rotabrasil-entrada-20261109-diario-final/`: regressão diária inicial, 14 grupos
  aprovados, incluindo ZIP autenticado e isolamento de módulos;
- execuções integradas posteriores e seus resultados são registrados na entrega final.

Revisão integrada V15: [resultados atuais e capturas](revisao-equipe-v15.md).
`rotabrasil-revisao-equipe-20261109-entrada/` passou os oito grupos em HTTPS
operacional, raiz e `/rota`; uso diário final também validou os três papéis,
logout/expiração, CSRF e importação autenticada. Nenhum envio externo.

As capturas se chamam `apresentacao-1366.png`, `login-1366.png`, `painel-1366.png`
e suas variantes `-390.png`; o prefixo `contexto-` identifica `/rota`. Capturas,
bases, certificados e logs permanecem fora do Git.
