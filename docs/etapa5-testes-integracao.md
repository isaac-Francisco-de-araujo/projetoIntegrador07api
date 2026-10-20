# Etapa 5 — Testes de integração

O fluxo `HTTP → Controller → Service → Repository → H2 → Arquivo` é exercitado
com Tomcat em porta aleatória, multipart real, migrações Flyway até V4 e
Hibernate em `validate`. Não há mocks nos dois novos conjuntos. Fixtures,
bancos e diretórios são exclusivamente sintéticos e isolados.

## Cobertura

| Cenário | Evidência |
| --- | --- |
| XML válido e relacionamentos | `FluxoImportacaoHttpTest`: resposta 200, IDs ligados por joins, nCT, destinatário, data, chave, hash e bytes do arquivo |
| XML inválido | Mesmo conjunto: seis entradas vazias, malformadas ou semanticamente inválidas retornam 400 sem registros ou arquivos |
| Reimportação idêntica, mesmo CT-e com outro nome | IDs preservados, nome original mantido, bytes e data de modificação do arquivo inalterados |
| Documentos distintos com mesmo nome | Dois documentos e arquivos independentes, uma viagem e um cliente |
| Conflito de conteúdo | 409 e preservação do primeiro arquivo |
| Requisições simultâneas | `ConcorrenciaImportacaoHttpTest`: oito requisições por cenário; `ConcorrenciaDuasInstanciasTest`: coordenação entre contextos independentes |
| Falha de disco por HTTP | Obstáculo real na área de preparação causa 500, rollback do banco e preservação do obstáculo; nova tentativa funciona após removê-lo |
| Falha de banco por HTTP | CHECK temporário no H2 rejeita a inserção de documento: 409, rollback de cliente/viagem e remoção dos temporários; nova tentativa funciona após retirar a restrição de teste |
| Falhas antes/depois da publicação | `FalhasArmazenamentoIntegrationTest`: compensação, falha de limpeza e resultado transacional desconhecido |
| Reinicialização por HTTP | `ReinicioImportacaoHttpTest`: servidor e datasource encerrados; nova instância abre H2 em arquivo com Flyway desativado, valida relações e reutiliza os IDs sem reescrever o XML |
| Segurança de XML | Entidade externa de arquivo rejeitada sem expor marcador sintético; DTD remota rejeitada sem requisição ao servidor local de controle, previamente verificado |
| Respostas HTTP | 200/400/409/500, estrutura de sucesso e erro, mensagem e timestamp, ausência de campos de stack trace |

O teste de reinicialização encerra e reabre contextos e conexões no mesmo
processo JVM. Não representa queda de energia, encerramento abrupto do sistema
operacional ou validação em outro sistema de arquivos. A falha de disco é de
preparação, não uma simulação de disco fisicamente cheio.

## Execução e resultados

Na nuvem, a partir de `/workspace/projetoIntegrador07api`:

```sh
/workspace/tools/rotabrasil-mvn -B clean test
```

Resultado: **108 testes, 0 falhas, 0 erros e 0 ignorados**. São 92 casos anteriores
mais 15 em `FluxoImportacaoHttpTest` e 1 em `ReinicioImportacaoHttpTest`.
O log inclui erros esperados dos XMLs rejeitados e da restrição de teste; o
resultado do Surefire confirma a aprovação dos casos.

Relatórios atuais preservados fora do checkout em
`/workspace/rotabrasil-dev/etapa5/relatorios`; log em `testes.log` e resumo em
`resultado-testes.json`, sob a mesma pasta. `target/` contém artefatos históricos
versionados: use sempre `clean` e não confunda esses arquivos com a execução atual.

Nenhuma dependência, versão ou regra de negócio foi alterada. O banco operacional
e os arquivos do acervo tiveram seus hashes conferidos antes/depois, sem alteração.
Não houve importação de documento real, envio de e-mail ou configuração SMTP.

## Próximo passo

Etapa 6: DTOs, validações e respostas da API, exposição de entidades e políticas
de exclusão. Esta entrega verifica o endpoint de importação e não declara todos
os endpoints CRUD revisados. A evolução da API deve conservar os cenários da suíte.
