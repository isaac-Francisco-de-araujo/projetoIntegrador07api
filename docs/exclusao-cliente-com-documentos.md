# Exclusão de cliente com viagens e documentos

## Uso pelo painel

1. Acesse **Clientes** e localize o cliente.
2. Clique em **Excluir**. O painel consulta a conferência atual no servidor.
3. Confira as quantidades de viagens, documentos, PDFs exclusivos e históricos de
   e-mail que serão removidos. O diálogo também mostra os PDFs compartilhados que
   permanecerão disponíveis para outros clientes.
4. Use **Cancelar** para manter os dados ou **Excluir cliente e documentos** para
   confirmar a exclusão definitiva.

O painel atualiza clientes, viagens, documentos, indicadores e seleções após a ação.
No perfil autenticado, a exclusão continua restrita a ADMIN e exige CSRF.

## Alcance

- Remove o cliente e todas as suas viagens.
- Remove documentos das viagens, inclusive os já arquivados, e suas referências
  em pacotes ZIP.
- Remove PDFs vinculados exclusivamente às viagens desse cliente.
- PDFs compartilhados com viagens de outros clientes permanecem com os mesmos
  IDs e bytes; somente os vínculos com as viagens excluídas são removidos.
- Remove envios legados vinculados ao cliente ou às suas viagens, conferências e
  tentativas de e-mail das viagens excluídas.
- Preserva os registros de pacotes, processamentos e auditoria como histórico.
- Não oferece restauração pelo painel para o agregado excluído. O arquivamento
  individual de XML/PDF e o arquivamento de ZIP continuam reversíveis.

Tentativas com situação `DESCONHECIDO` impedem a exclusão, inclusive enquanto o
transporte de e-mail está em andamento. Seu resultado não é alterado para liberar
a operação. Referências de reenvio vindas de outras viagens também são protegidas.

## Contrato HTTP

`GET /api/clientes/{id}/conferencia-exclusao` retorna:

```json
{
  "clienteId": 1,
  "nome": "Cliente Sintético",
  "viagens": 2,
  "documentos": 2,
  "pdfsExclusivos": 1,
  "pdfsCompartilhados": 1,
  "envios": 0,
  "conferenciasEmail": 0,
  "tentativasEmail": 0,
  "tentativasDesconhecidas": 0,
  "conferenciaSha256": "<SHA-256 da conferência>"
}
```

Para excluir o cliente com seus vínculos:

```text
DELETE /api/clientes/{id}?confirmado=true&conferenciaSha256=<hash>
```

- **204:** exclusão dos registros confirmada.
- **409:** ausência de confirmação/conferência válida, alteração posterior dos
  dados, tentativa desconhecida ou referência externa impeditiva.
- **404:** cliente inexistente, inclusive em uma repetição após exclusão concluída.
- **500:** falha de preparação de armazenamento/integridade; os registros não são
  parcialmente excluídos.

Cliente sem viagens nem envios mantém compatibilidade com o DELETE sem parâmetros.
O frontend sempre consulta a conferência e envia a confirmação. O hash inclui os
dados e vínculos envolvidos, não é o hash de conteúdo de um único arquivo.

## Transação, armazenamento e recuperação

As mutações usam `READ_COMMITTED` e a mesma coordenação de importações. As chaves
estrangeiras permanecem ativas. Uma falha na transação desfaz a remoção dos registros.

Antes de excluir registros, o serviço verifica a raiz permitida e a integridade
dos arquivos existentes e grava um journal em `.exclusoes-clientes/{UUID}.json`.
Arquivos ausentes não impedem remover um cadastro que já estava inconsistente;
arquivos existentes com hash divergente ou caminho inseguro impedem a operação.

Os bytes só são removidos **depois do commit**. A limpeza adquire nova coordenação,
confere se algum documento/anexo ainda referencia o caminho e verifica o SHA-256
antes da remoção. Falhas de limpeza mantêm o journal e são registradas no log.
A inicialização seguinte retoma as pendências. Uma reversão da transação mantém
os bytes; após queda, referências ainda existentes também impedem apagá-los.
Um arquivo alterado após a exclusão permanece para revisão, sem remoção cega.

Repetir um ZIP histórico com registros removidos retorna conflito em vez de
informar sucesso com resultado parcial ou recriar silenciosamente o cliente.
Para uma nova importação deliberada, prepare um novo pacote e confira os vínculos.

O esquema continua V8; esta implementação não altera migrações publicadas.

## Verificação isolada

`ExclusaoClienteHttpTest` cobre conferência, confirmação, mudança de vínculos,
exclusão de registros/bytes, PDF compartilhado, rollback, resultado desconhecido,
integridade, caminho externo, recuperação de journal e cadeia de reenvios.
`PacoteAssistidoHttpTest` cobre a recusa de reutilização de ZIP após exclusão.
`SegurancaHttpTest` confere ADMIN e CSRF nas exclusões de cliente.

```powershell
$Build = Join-Path $env:TEMP 'opencode/rotabrasil-exclusao-cliente-build'
mvn -B "-Drotabrasil.build.directory=$Build" `
  '-Dtest=ExclusaoClienteHttpTest,ContratoApiHttpTest,SegurancaHttpTest,PacoteAssistidoHttpTest' clean test
mvn -B "-Drotabrasil.build.directory=$Build" -DskipTests package
$Ui = Join-Path $env:TEMP ('opencode/rotabrasil-cliente-ui-' + [guid]::NewGuid().ToString('N'))
node scripts/testar-exclusao-cliente.cjs "$Build/rota-brasil-0.0.1-SNAPSHOT.jar" "$Ui"
```

O teste de navegador inicia seu próprio servidor, usa H2 em memória e gera CT-es e
PDFs exclusivamente sintéticos. Nenhuma exclusão de dados do ambiente em uso é
necessária para testar a funcionalidade.

### Resultados registrados nesta entrega

- Recorte inicial de backend: **75 testes aprovados**, sem falhas, erros ou
  ignorados (8 de exclusão de cliente, 37 de contratos, 25 de ZIP e 5 de segurança).
- Verificação adicional após ampliar a cobertura: **7 testes aprovados**, sendo
  o novo cenário de ZIP após exclusão e os 6 testes de segurança atualizados.
- Navegador real: **5 verificações aprovadas**, incluindo cancelamento, conferência
  desatualizada, exclusão conjunta, compartilhamento, recarga e largura móvel.
- Nenhum erro JavaScript observado na execução final. JAR empacotado com sucesso.
- As execuções usaram somente H2/arquivos sintéticos isolados e modo SIMULADO.

Relatórios e JAR em `rotabrasil-exclusao-cliente-build/`; resultados/captura de
navegador em `rotabrasil-exclusao-cliente-ui/`, ambos sob a área temporária externa
`C:/Users/florb/AppData/Local/Temp/opencode/`. Os recortes têm testes em comum;
seus totais não representam uma única execução nem a suíte completa do projeto.
