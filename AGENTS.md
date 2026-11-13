# RotaBrasil — instruções atuais

- Java 25, Maven e Spring Boot; backend em `back/`, frontend real em `front/`. O build de `back/pom.xml` empacota `front/` em `static/` no mesmo JAR, preservando a comunicação na mesma origem.
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
- Gmail administrativo é independente: credenciais externas e `rotabrasil.teste-email.gmail.credencial-versao` não secreta, alterada ao substituir credencial/autorização. Repetir teste real exige nova conferência vinculada à última tentativa. Não promover simulação ou aceite SMTP a recebimento.
- Não versionar banco, documentos reais, credenciais ou artefatos gerados. Não reescrever histórico Git.
