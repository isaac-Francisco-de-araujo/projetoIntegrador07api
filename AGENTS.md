# RotaBrasil — instruções atuais

- Java 25, Maven e Spring Boot; frontend real em `src/main/resources/static/`.
- Leia README e docs atuais antes de alterar fluxos. ZIP assistido faz parte do sistema.
- Preserve alterações locais, documentos anteriores e o banco operacional.
- Testes somente com fixtures sintéticas, H2 e diretórios isolados. Não enviar e-mails reais.
- Build limpo externo: `mvn -B -Drotabrasil.build.directory=<diretorio-temporario> clean test`.
- `desenvolvimento`: loopback, sem autenticação, apenas para dados isolados.
- `operacao`: autenticação/permissões, H2 Console desligado; manter migrações automáticas desativadas.
- Código atual exige esquema V15 (conta administrativa de teste sem segredos; V12 versão de operadores, V13 responsabilidade de trabalho, V14 índices de busca; preserva V11 da confirmação ao motorista). Verifique separadamente a versão de cada banco. Mudanças usam migração nova; não editar migrações publicadas, clean, repair ou ddl-auto=update.
- Não migrar banco operacional sem autorização específica e backup verificado.
- PDFs têm conferência de cabeçalho, não validação estrutural/fiscal. Não inferir vínculos ou destinatários.
- Preservar idempotência, SHA-256, confirmação explícita e estado desconhecido; não repetir automaticamente.
- Confirmação REAL ao motorista: manter sessão OPERADOR/ADMIN e CSRF, SMTP OAuth2 externo e opt-in; modo documental independente. Tipo da conta ainda não identificado; não presumir credencial básica nem contornar o acesso protegido. Sem envio externo nesta tarefa.
- Não versionar banco, documentos reais, credenciais ou artefatos gerados. Não reescrever histórico Git.
