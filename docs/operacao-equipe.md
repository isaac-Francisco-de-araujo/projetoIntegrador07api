# Operação compartilhada — revisão de 09/11/2026

## Acompanhamento

| Bloco | Situação |
|---|---|
| ZIP, exportação, fila, busca, diagnóstico e backup | Implementados; regressão integrada desta revisão pendente |
| Apresentação pública → login → painel | Concluído; 16 testes de segurança/compatibilidade aprovados |
| Gmail administrativo sem segredos no banco | Em implementação |
| HTTPS, diretórios e credencial reais | Dependem de configuração externa |
| Frequência, destino, retenção e responsável por backup | Dependem de decisão |
| Fatura com viagens de clientes diferentes | Exige conferência; regra comercial depende de decisão |

## Base preservada

Branch `main`, HEAD inicial e remoto `5fdd7241c92463da2d7ce4573a3bf01e4b9a2ef9`,
conferidos por `git ls-remote origin refs/heads/main`. As alterações locais de acesso,
context path, sessão e frontend foram examinadas e preservadas, sem reconstrução.
Não há autorização para migrar a base operacional ou enviar mensagens externas.

## Acesso

- `/` apresenta somente marca, orientação curta e **Entrar**, sem consultas operacionais.
- `/login` usa Spring Security, BCrypt e CSRF. Login válido vai a `/painel`.
- `/painel`, estáticos operacionais, downloads e APIs exigem sessão em `operacao`.
- Sessão válida acessa o painel diretamente; logout exige novo login.
- Favoritos `/#aba` continuam encaminhando ao painel; fragmentos não são destinos externos.
- Revalidação de sessão ao retornar à página bloqueia alterações quando o acesso expirou,
  preservando o rascunho e sem repetir operações.
- Context path é aplicado às APIs, downloads, bibliotecas PDF/ZIP e URLs de acesso.

Teste inicial: `SegurancaHttpTest` (15) e `CompatibilidadeEsquemaLegadoTest` (1),
sem falhas/erros. Build e saída em diretório temporário externo; sem banco real.
