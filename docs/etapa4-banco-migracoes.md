# Etapa 4 — Inventário, backup, migrações e concorrência

## Estado da aplicação

Java 25, Spring Boot 3.5.6 e H2 foram preservados. Flyway 11.7.2 é a versão
gerenciada pelo Spring Boot; não há mudança de banco nem ativação de SMTP.

O padrão agora é `spring.jpa.hibernate.ddl-auto=validate`,
`spring.flyway.enabled=false` e `IFEXISTS=TRUE` na URL do banco conhecido. A
inicialização normal valida o esquema sem atualizá-lo nem criar outro banco
por engano. Restrições e histórico são conferidos antes de a aplicação ficar
pronta. Esquema incompleto exige manutenção offline; desativar a verificação
não é uma alternativa à migração.

Para um banco **novo e isolado**, informe outra URL e raiz e habilite Flyway:

```sh
java -jar app.jar \
  --spring.datasource.url=jdbc:h2:file:/tmp/rotabrasil-novo/banco \
  --spring.flyway.enabled=true \
  --spring.jpa.hibernate.ddl-auto=validate \
  --spring.h2.console.enabled=false \
  --rotabrasil.arquivos.viagens=/tmp/rotabrasil-novo/viagens
```

Não use essa opção para converter silenciosamente um banco existente. O
baseline automático está desativado. A migração Java também exige backup
verificado se houver registros, mesmo que alguém force um baseline externo.

## Migrações

| Versão | Função |
| --- | --- |
| V1 | Esquema inicial para banco vazio, com relacionamentos e índices |
| V2 | Colunas opcionais de chave/hash e linha de coordenação das importações |
| V3 | Inventário completo; preenche chave e SHA-256 apenas com dados recuperáveis e sem impedimentos |
| V4 | Unicidade de CNPJ, número operacional e chave fiscal; formato e obrigatoriedade da identidade para XML |

O baseline em V1 de um banco legado só é criado pela ferramenta após conferir
inventário e backup. V2 é compatível com colunas de identidade já existentes.
V3 verifica todos os XMLs, relacionamentos, arquivos sem documento, documentos
sem arquivo, duplicações e pendências de armazenamento antes de preencher
qualquer registro. Nomes e caminhos antigos são preservados. Documentos não XML
não recebem uma identidade fiscal inventada.

A chave continua identificando o documento; `nCT` continua identificando a
viagem. A restrição no número operacional não substitui essa regra por outra
combinação. Colisões existentes exigem decisão humana; não há fusão ou exclusão
automática de registros.

Mantenha migrações aplicadas imutáveis. Alterações posteriores devem usar nova
versão. O checksum da migração Java é explícito; não edite a implementação de
V3 depois de publicada. `database/database.sql` é referência histórica, não é
o script para implantar esta versão.

## Inventário offline

A CLI não inicia JPA, servidor HTTP nem serviços de envio. Os comandos
`inventario` e `backup` abrem a origem H2 em modo de dados somente de leitura.
`IFEXISTS` é obrigatório e a ferramenta recusa criar um banco ausente.

Exemplo usando o JAR empacotado; substitua os caminhos por um ambiente escolhido
explicitamente. `--banco` é a base **sem** a extensão `.mv.db`.

```sh
java -Dloader.main=br.com.rotabrasil.manutencao.ManutencaoBanco \
  -cp app.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  inventario --banco /caminho/banco \
  --arquivos /caminho/viagens --saida /caminho/privado/inventario.json
```

O relatório deve ser novo e ficar fora da raiz dos documentos. Em POSIX, é
criado com permissão 600. Contém IDs, identidade recuperada, hashes e caminhos
relativos necessários à revisão local; não deve ser publicado. O console mostra
somente a quantidade de impedimentos. Senhas não são argumentos de comando;
configurações H2 diferentes das existentes podem usar `ROTABRASIL_DB_USER` e
`ROTABRASIL_DB_PASSWORD` no ambiente seguro, nunca valores em chat ou scripts.

O inventário não segue links simbólicos, não remapeia caminhos do Windows,
não remove arquivos órfãos e não escolhe uma duplicata arbitrariamente.

## Backup e restauração

Pare os escritores e outras instâncias antes da manutenção. O backup mantém
modo exclusivo durante a captura e usa `BACKUP TO` do H2, em vez de copiar um
`.mv.db` aberto para escrita. Preserva os arquivos disponíveis da raiz
informada e gera hashes, inventário e manifesto em diretório novo e privado.

```sh
java -Dloader.main=br.com.rotabrasil.manutencao.ManutencaoBanco \
  -cp app.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  backup --banco /caminho/banco --arquivos /caminho/viagens \
  --saida /caminho/privado/backup-antes
```

O diretório de backup precisa ter pai existente, não pode existir previamente
e deve ficar fora do acervo. O comando confere o ZIP, os arquivos copiados e a
impressão do estado antes/depois. Pendências ativas, artefatos não regulares ou
mudanças detectadas impedem declarar a captura consistente.

Um snapshot pode preservar um estado **inconsistente** para permitir revisão
segura: por exemplo, referências quebradas e arquivos órfãos continuam no
inventário. Arquivos fora da raiz informada não são copiados nem tratados como
recuperados. A captura do estado conhecido não equivale a aprovar a integridade
ou a cobertura de arquivos externos; um inventário reprovado continua impedindo
a migração.

O backup contém `banco.zip`, `arquivos/`, `inventario.json` e `manifesto.json`.
Os testes restauram o `.mv.db` do ZIP em outro diretório, abrem esse banco e
conferem registros e bytes dos documentos. Na restauração operacional, mantenha
as origens paradas e restaure em destino vazio. Os caminhos absolutos registrados
no banco são preservados: restauração para outro caminho exige um plano explícito
de remapeamento, não uma substituição automática. Não sobrescreva o banco atual
como primeira tentativa de restauração.

## Aplicar a migração a um banco existente

1. Com escritores parados, faça inventário e backup verificado do estado inicial.
2. Revise cada impedimento. Qualquer correção de dados ou vínculos exige plano,
   autorização e comparação dos dados esperados; esta CLI não faz saneamento
   destrutivo nem remapeamento automático.
3. Execute novo inventário até não haver impedimentos. Se houve correção, faça
   **outro backup** do estado saneado. O snapshot anterior continua preservado.
4. Aplique as migrações com esse backup. A impressão deve corresponder exatamente
   ao estado atual do banco e dos arquivos; backup desatualizado é recusado.
5. Valide histórico, restrições, contagens e hashes antes de iniciar a aplicação.

```sh
java -Dloader.main=br.com.rotabrasil.manutencao.ManutencaoBanco \
  -cp app.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  migrar --banco /caminho/banco --arquivos /caminho/viagens \
  --backup /caminho/privado/backup-saneado
```

A CLI exige acesso exclusivo e não executa `clean`, `repair`, remoção de
duplicatas ou exclusão de dados. Repetir uma migração já concluída valida o
histórico e não preenche registros novamente. DDL no H2 pode fazer commit por
instrução: uma falha parcial de V4 exige análise do histórico e do backup, sem
reparação ou downgrade automático. A aplicação recusa um conjunto incompleto de
restrições. Um backup antigo não deve ser editado para fazer a verificação passar.

## Concorrência

O importador obtém `SELECT ... FOR UPDATE` na linha sentinela de
`controle_importacao` antes de procurar ou criar os registros. A transação usa
`READ_COMMITTED` e mantém a trava até commit/rollback, inclusive a publicação do
arquivo da Etapa 3. A coordenação é do banco e funciona entre instâncias; não
há mutex em memória como garantia de unicidade.

As importações são serializadas de forma conservadora. Isso reduz paralelismo
de persistência, mas evita corridas também na criação do cliente e da viagem.
Restrições únicas permanecem a defesa final, inclusive para inserções que não
passam pelo importador. Violações de integridade retornam 409; indisponibilidade
por trava pode retornar 503 para tentativa posterior. Não se continua uma
transação que já falhou.

Requisições idênticas simultâneas reutilizam o mesmo documento após aguardar o
commit vencedor. Mesma chave com bytes diferentes continua em 409, sem substituir
conteúdo. Arquivos sem registro e demais ambiguidades da Etapa 3 permanecem
preservados para reconciliação.

## Verificação desta entrega

- `mvn clean test`: 92 testes aprovados, zero falhas/erros/ignorados.
- Inclui três cenários HTTP com oito requisições concorrentes cada; coordenação
  entre duas instâncias independentes; persistência após reinício; unicidade e
  obrigatoriedade de identidade verificadas diretamente no banco.
- Inclui migração de esquema legado, baseline automático recusado, backup e
  restauração, origem aberta somente para leitura com checksum preservado,
  backup inválido/desatualizado e inventário impeditivo.
- As regressões históricas com dados inválidos usam explicitamente o estágio
  anterior às restrições para verificar diagnóstico. A aplicação normal exige
  V4; os cenários históricos não desativam as restrições dos testes atuais.

O repositório ainda versiona artefatos históricos em `target/`. Inicie builds
com `clean` para não reutilizar classes ou configurações antigas; não inclua
esses artefatos na revisão das fontes.

Na nuvem, use `/workspace/tools/rotabrasil-mvn` no lugar de `mvn`. Relatórios e
artefatos ficam fora do checkout em `/workspace/rotabrasil-dev/etapa4`.

## Situação do banco fornecido

Após autorização do usuário, foi corrigido exclusivamente o caminho do documento
1 para o candidato único previamente conferido. O inventário saneado apresentou
zero impedimentos. Foi criado e verificado novo backup desse estado antes da
migração; o backup anterior à correção também foi preservado.

As migrações foram aplicadas até V4, com preenchimento da chave e do SHA-256 a
partir do XML existente, restrições de integridade e coordenação de importação.
Foram preservados todos os demais valores dos registros existentes: 1 cliente,
1 viagem, 1 documento e 0 envios. O XML permaneceu no mesmo local, com os mesmos
bytes. Nenhum arquivo foi substituído ou excluído.

A conferência operacional somente de leitura confirmou inventário sem
impedimentos, restrições e histórico V4. A aplicação iniciou normalmente com
Flyway desativado e Hibernate em `validate`; nova leitura após o encerramento
confirmou a persistência dos resultados. Não foram executados testes automatizados
contra esse banco real. Os 92 testes acima correspondem à validação anterior em
bases sintéticas.

Os relatórios, snapshots e logs desta manutenção estão na área privada
`/workspace/rotabrasil-dev/etapa4/aplicacao-autorizada`. Os backups estão em
`backup-operacional-antes` e `backup-operacional-saneado`, sob a mesma área da
Etapa 4. Contêm dados operacionais e não devem ser publicados no Git. O checksum
histórico do banco anterior à correção não representa o banco já migrado.
