# Uso diário compartilhado

## 1. Login e operadores

Reutilizados usuários V8, BCrypt, sessão, CSRF, revalidação de papel e auditoria HTTP.
A correção local preexistente da seleção de entry point já fazia `/login` responder
200 no teste inicial (8 testes aprovados). O 404 documentado é evidência histórica;
não foi reproduzido neste código. Agora `/login` tem formulário próprio no mesmo
Spring Boot, sem depender da geração implícita do Spring Security.

O painel identifica a sessão, permite logout POST/CSRF, sinaliza 401 preservando
rascunhos na aba e explica 403. ADMIN encontra **Operadores** no menu: cadastro,
papel, ativação e redefinição de senha. Não há cadastro público nem conta padrão.
Redefinição invalida sessões anteriores na próxima requisição. O administrador
entrega a nova senha por canal seguro; não há envio automático de credenciais.
Primeiro ADMIN: mecanismo existente `ROTABRASIL_ADMIN_USUARIO` e
`ROTABRASIL_ADMIN_HASH`, gerado pela CLI interativa `GerarHashSenha`.

| Papel | Permissões |
|---|---|
| CONSULTA | Leitura operacional, detalhes e documentos |
| OPERADOR | Leitura, importação, conferência, arquivamento/restauração, exportação e confirmação manual |
| ADMIN | Operador, configurações, usuários, auditoria e exclusões administrativas |

As APIs validam os papéis e CSRF. A coordenação transacional existente serializa
alterações de usuários e impede remover o último ADMIN ativo, inclusive em disputa.
Auditoria obtém o ator da sessão no servidor; nomes enviados pelo navegador não
definem identidade. Históricos legados permanecem com suas informações originais.

## 2. Alterações simultâneas

Configurações já usavam versão e UPDATE condicional. O rascunho é preservado em
409 e **Recarregar configuração** agora pede confirmação para descartá-lo e revisar
a versão atual. Não há reaplicação automática. Usuários ganham `versao` na nova
migração V12; salvar permissões/redefinir senha compara a versão sob coordenação.
Em conflito, as escolhas ficam na tela até **Recarregar operadores**.

GET `/api/clientes/{id}` e `/api/viagens/{id}` retornam ETag. PUT nesses recursos
e DELETE de viagem exigem `If-Match` igual ao GET revisado; falta ou divergência
retorna 409. A comparação acontece sob a mesma coordenação/transação da alteração.
O frontend atual não oferece edição manual desses cadastros. Exclusão de cliente
e ZIP conserva a conferência SHA-256 do impacto existente. Conferências de envio e
importação mantêm snapshots, idempotência e invalidação preexistentes. Nenhuma trava
de banco fica aberta enquanto o usuário revisa uma página.

## 3. Fila compartilhada

`/#fila` e GET `/api/fila` projetam a mesma Central de Pendências, com dados das
fontes originais. V13 armazena somente responsabilidade/versionamento e eventos de
atribuição, não outro histórico técnico. Itens antigos atribuídos continuam visíveis
mesmo quando uma conferência mais recente substitui o diagnóstico agrupado.

Filtros: `escopo=TODOS|MEUS|SEM_RESPONSAVEL`, `situacao=TODAS|A_CONFERIR|COM_PENDENCIAS|EM_TRABALHO|ETAPA_CONCLUIDA|AGUARDANDO_PROCESSAMENTO`,
`responsavel` (login exato), `inicio/fim` (entrada UTC), `ordem=ENTRADA|ATUALIZACAO`
(atualização da responsabilidade), `pagina` zero-based e `tamanho` 1–50.
Desempate por origem e ID. A consulta usa joins limitados, sem consultar diagnósticos
individualmente para montar cada linha. Detalhes e recuperação usam a API existente.

POST `/api/fila/{origem}/{id}/responsabilidade` recebe
`{acao:ASSUMIR|LIBERAR|TRANSFERIR,versao,destino,confirmado}`. Assumir é atômico;
liberar exige ser responsável; transferir exige ADMIN e confirmação, inclusive para
retirar responsabilidade de inativo. O backend valida operador ativo de destino.
Cada mudança registra anterior, novo, ator efetivo e data. GET `.../historico` lê
até 50 mudanças. Não há expiração de responsabilidade, presença ou abandono por aba.
Desativação ou retirada do papel operacional sinaliza responsável indisponível.
A retenção administrativa existente preserva processamentos com histórico de
responsabilidade, para não apagar a fonte de tarefas atribuídas/liberadas.
Resultado técnico em andamento/incerto preserva a responsabilidade até diagnóstico,
inclusive quando não é possível distinguir transporte ativo de resposta perdida.

Atualização a cada 45 s somente com a fila visível, sem ação/foco em uma linha.
Filtros/página são preservados; falha limpa a lista e informa indisponibilidade.
Etapa concluída é somente o fato técnico comprovado, nunca “pronto para enviar”.
Conferência válida sem importação comprovada permanece A_CONFERIR/EM_TRABALHO;
o sucesso técnico da inspeção não conclui o trabalho de confirmar a importação.

## 4. Busca e paginação

GET `/api/busca` recebe `entidade=CLIENTE|VIAGEM|ARQUIVO|PACOTE`, `busca` (até 255),
`numero` (CT-e exato), `cliente` (ID), `pacote` (ID), `tipo=TODOS|XML|PDF`,
`estado=TODOS|ATIVO|ARQUIVADO`, `situacao` (status exato de viagem), `inicio/fim`,
`ordem=nome|numero|cliente|data|status`, `direcao=ASC|DESC`, `pagina` (zero-based) e
`tamanho` (1–100). Retorna entidade, paginação, total, itens seguros e `dataUtilizada`.
Filtros cliente/CT-e valem para viagens/arquivos; pacote para pacotes/arquivos;
tipo e arquivamento para arquivos. A interface identifica esses escopos.

Data de viagem corresponde ao cadastro (emissão extraída quando importado). Arquivos
usam a primeira importação de pacote conhecida; não se inventa data de XML avulso.
Clientes não têm data registrada e recusam intervalo. `LIKE` tem escape literal e
parâmetros; ordenação é allowlist com ID de desempate e origem para arquivos.
Faturas usam EXISTS, sem duplicação por vínculos. V14 indexa data/ID de viagens e
pacotes, nome/ID de clientes, referência reversa pacote/documento e estado de XMLs.
Busca de substring ainda pode exigir varredura SQL; não foi criado índice full-text.

`/#busca` oferece entidades paginadas, detalhes e acesso aos arquivos/viagens.
Clientes e viagens das telas existentes agora pesquisam no servidor, com debounce
300 ms e descarte por sequência de respostas antigas. Filtros permanecem ao abrir
detalhes e voltar. O painel não carrega mais arrays completos do acervo: seletores
iniciais usam até 100 opções e há busca explícita de viagens para localizar outras.
Para localizar cliente fora das opções iniciais, a tabela paginada **Clientes → Ver
viagens** adiciona o cliente selecionado ao filtro. Fila concentra situação e
responsável do trabalho, com link na busca.

## 5. Saúde

`/#saude` e GET `/api/saude` reúnem verificações independentes de backend, SELECT 1,
metadados do acervo/destino, configuração de e-mail e evidência de backup.
Falha de uma sondagem não impede as demais. Todos os resultados têm hora e estado:
FUNCIONANDO, ATENCAO, INDISPONIVEL, NAO_CONFIGURADO ou NAO_VERIFICADO.
O resumo é autenticado; detalhes de capacidade de disco, autorização e testes de
e-mail são ADMIN. Não há caminhos físicos nas respostas.

Consulta de pasta apenas mede metadados e espaço disponível no volume efetivo
(ADMIN), sem percorrer arquivos. Existir/ser legível não comprova publicação:
permanece atenção. O teste explícito da exportação reutiliza Configurações, agora
também registrando falha ao limpar seu próprio artefato como LIMPEZA_TESTE_PENDENTE.
E-mail distingue endereço salvo, autenticação configurada, autorização externa,
modo documental/motorista e os últimos testes com data. Nenhum transporte é chamado
pelo painel; simulação não comprova autorização/conectividade/entrega.

O backup existente gera manifesto, mas não persiste recibo confiável com data da
verificação/restauração. Logo o painel informa **Sem informação de backup verificado**.
ADMIN pode configurar externamente `rotabrasil.saude.backup-manifesto` para o
`manifesto.json` produzido pela manutenção: leitura limitada a 256 KiB, mostrando
apenas manifesto localizado e sua modificação (não data comprovada da captura).
Não lê banco.zip nem documentos para conferir hashes automaticamente e não promove
esse manifesto a integridade/restauração verificadas. Manifestos maiores/ilegíveis
ficam não verificados. Não há agendamento, criação ou restauração automática.
Revalidação de sessão depende do banco; uma indisponibilidade que impeça autenticar
pode impedir a consulta inteira. A interface então mostra backend/consulta indisponível,
sem afirmar qual componente foi diagnosticado e sem reutilizar dados antigos como atuais.

## Roteiro com dois operadores

1. Em ambiente isolado, iniciar uma aplicação central de teste e criar o primeiro
   ADMIN pelo bootstrap seguro. Cadastrar Ana e Carlos como OPERADOR e uma conta CONSULTA.
2. Abrir dois perfis de navegador independentes (ou PCs da rede autorizada); entrar
   com contas distintas e conferir a identificação. IMPORTANTE: abas do mesmo perfil
   compartilham cookie, portanto não representam operadores diferentes.
3. Com dois ADMIN de teste, abrir Configurações em ambos. Salvar na primeira sessão;
   tentar salvar a versão antiga na outra. Esperar 409, rascunho preservado e revisão
   por Recarregar configuração. Não há reaplicação automática.
4. Importar/conferir somente ZIP sintético; abrir Fila nos dois navegadores e clicar
   Assumir no mesmo item. Apenas um assume; o outro atualiza para revisar. Conferir
   Meus itens, liberar e transferir com ADMIN; consultar o histórico. Desativar um
   responsável e conferir o aviso; nenhuma responsabilidade expira por fechar aba.
5. Pesquisar cliente/CT-e, nome do arquivo e pacote; combinar datas/tipo/arquivamento,
   navegar páginas, abrir detalhes e voltar. Filtros e paginação devem permanecer.
6. Abrir Saúde como operador e ADMIN: verificar resumo/detalhes, ausência de informação
   de backup verificado e configuração de e-mail sem gerar mensagens externas.
7. Sair, tentar API diretamente, testar conta CONSULTA e desativação/mudança de papel.
   Alteração exige CSRF; sessão expirada permite novo login sem reaplicar rascunhos.

## Implantação centralizada

Um processo/backend central serve todos os navegadores; apenas esse backend acessa
o H2. Não compartilhar o arquivo H2 entre PCs nem ativar H2 TCP/Console. Banco e
acervo ficam fora do checkout. A exportação usa pasta do servidor ou rede autorizada,
separada do acervo; permissões pertencem à conta que executa o Java.

Usar perfil `operacao`, HTTPS com certificado confiável e cookies Secure/HttpOnly.
O responsável deve configurar nome DNS/IP privado, bind da aplicação, proxy HTTPS
e encaminhamento confiável de headers conforme sua infraestrutura. Restringir
acesso aos PCs/rede autorizados. Nenhuma porta ou publicação externa é aberta por
esta implementação. `desenvolvimento` é somente loopback com dados isolados.
Migrações automáticas operacionais continuam desligadas: inventário, backup com
verificação e autorização específica precedem qualquer migração offline.
Etapa E-mail permanece independente: nenhum SMTP real é ativado por login.

### Preparação do servidor (procedimento, sem implantação automática)

1. Selecionar uma máquina central e conta de serviço; provisionar Java 25, disco,
   diretórios privados do H2/acervo e raiz de exportação externa e separada.
2. Empacotar com Maven em diretório externo. Não sobrepor estáticos novos a JAR antigo.
3. Conferir versão de **cada** banco. O código requer V14. Uma base V11 não pode ser
   iniciada com este código antes da migração offline autorizada, seguindo inventário,
   backup verificado e manutenção descritos em `etapa4-banco-migracoes.md`.
4. Configurar externamente URL JDBC privada com IFEXISTS/WRITE_DELAY=0, caminhos,
   primeiro ADMIN e variáveis sensíveis. Não transportar credenciais em argumentos
   nem arquivos versionados. Iniciar o JAR com `--spring.profiles.active=operacao`;
   manter `spring.flyway.enabled=false`, `ddl-auto=validate` e H2 Console desligado.
5. Configurar HTTPS no servidor/proxy da rede autorizada. O padrão operacional
   permanece `server.address=127.0.0.1`; um proxy na mesma máquina pode terminar TLS
   e encaminhar ao loopback. Se necessário outro bind privado, a equipe responsável
   configura-o deliberadamente junto das regras de rede e certificado. Somente
   confiar headers encaminhados de proxy controlado, que substitua os recebidos.
6. Fornecer aos PCs a URL HTTPS central e contas individuais. Validar cookies
   Secure/HttpOnly/SameSite, logout, permissões, pasta do backend e o roteiro acima.
   Nenhum PC cliente recebe acesso direto ao H2, ao acervo ou às credenciais SMTP.

## Verificação da entrega

Build e artefatos em `C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-multi-build/`.
Somente H2 isolado, fixtures sintéticas e transportes controlados. Comando da suíte:

```sh
mvn -B -Drotabrasil.build.directory=C:/Users/florb/AppData/Local/Temp/opencode/rotabrasil-multi-build -Dlogging.level.root=OFF -Dspring.main.banner-mode=off clean test
```

Suíte completa: **387 contabilizados, 386 aprovados, zero falhas/erros e 1 ignorado**
(symlink no Windows). Inclui 11 testes de segurança, edição antiga recusada, disputa
de responsabilidade, paginação/filtros combinados, fatura sem duplicação e falha
isolada de saúde. Os testes de regressão cobrem importação, exportação, diagnóstico,
backup/restauração sintéticos e envios simulados/controlados.

Execuções de navegador independentes (não somar com JUnit nem recortes históricos):

| Script | Resultado | Artefatos externos sob `.../opencode/` |
|---|---|---|
| `testar-uso-compartilhado.cjs` | 9 verificações; login/CSRF, usuários, rascunho em conflito, dois operadores, busca fora de ordem, saúde, celular, logout/CONSULTA e sessão expirada | `rotabrasil-multi-ui-entrega-final/` |
| `testar-envio-manual.cjs` | 14 verificações; incluindo cenário protegido que antes parava em `/login` | `rotabrasil-multi-envio-ui-final/` |
| `testar-confirmacao-motorista.cjs` | 7 verificações; simulação, invalidação, resposta perdida, reenvio e reinício | `rotabrasil-multi-confirmacao-ui/` |
| `testar-configuracoes.cjs` | 7 verificações; pasta externa sintética, SHA-256, retomada e persistência | `rotabrasil-multi-config-ui/` |
| `testar-diagnostico-recuperacao.cjs` | 7 verificações; pendências, recuperação, consulta indisponível e celular | `rotabrasil-multi-diagnostico-ui/` |

Nenhum erro JavaScript nas execuções aprovadas; nenhum SMTP externo. O teste novo
aguarda a conclusão real da abertura após navegar do login (esperar elemento
ausente como “hidden” antes de terminar a navegação não comprovava prontidão).
Capturas desktop/celular e resultados ficam fora do checkout. Empacotamento externo
aprovado; banco operacional e `target` histórico preservados.
