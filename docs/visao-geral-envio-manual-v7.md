# RotaBrasil — visão geral histórica da etapa V7

Este documento preserva o registro local da entrega V7 de envio manual.
Para o estado consolidado atual, consulte o [README](../README.md).

O sistema organiza CT-es XML e PDFs de viagens/faturas produzidos externamente.
Não gera documento fiscal, fatura ou boleto.

## Estado confirmado no código

| Funcionalidade | Estado |
| --- | --- |
| Importação individual de XML CT-e | Presente, com chave fiscal e SHA-256 |
| ZIP selecionado manualmente com várias viagens | Presente; CT-e identificado por nCT |
| Diagnóstico por arquivo e recuperação manual | Presente na sessão: origem no ZIP, etapa, situação, orientação, filtros e nova conferência |
| Associação manual de PDFs | Presente; sem inferência por nomes, ordem ou quantidade |
| Uma fatura para várias viagens | Presente; único arquivo com múltiplos vínculos |
| Duplicidade e proteção contra sobrescrita | Presente, banco e protocolo de armazenamento |
| Arquivamento e restauração | Presente para XMLs e PDFs, preservando bytes |
| Prévia explícita | Evoluiu: painel confere bytes e persiste conferência vinculada à confirmação |
| Consulta de histórico | Legado preservado; novas tentativas com snapshots e resultados separados |
| Inventário, backup e recuperação | Presentes; inventário/backup agora incluem conferências e tentativas |
| Painel web e API | Presentes, mesmo Spring Boot, sem frontend adicional |
| Confirmação e reenvio manuais | Implementados em SIMULAÇÃO, com idempotência no banco |
| Outlook, recebimento/disparo automático, SMTP operacional | Pendentes |

## Componentes

- Backend Java 25/Spring Boot, JPA/JDBC, H2 e Flyway, migrações até V7.
- Frontend em `src/main/resources/static/{index.html,painel.js,painel.css}`.
- Documentos organizados sob a raiz configurada, preservando o protocolo de
  publicação sem sobrescrita, journal e recuperação conservadora.
- Preparação/validação em `PreparacaoEnvioService`; conferência, reserva
  idempotente e auditoria em `EnvioManualService`; comunicação separada pela
  interface `TransporteEmail`. O único bean operacional é simulado e sem rede.
- CLI Gmail independente, limitada a teste sintético explicitamente configurado.

## Fluxo manual

Importar ZIP → conferir CT-es → associar PDFs → confirmar importação → escolher
viagem, destinatário e anexos → gerar conferência → confirmar **Simular envio** →
consultar tentativa e resultado. Reenvio é ação explícita com referência anterior.
Nenhuma fatura compartilhada cria destinatários/tentativas adicionais.

Consulte [o contrato completo de envio manual](envio-manual.md),
[o diagnóstico de processamento e recuperação manual](diagnostico-processamento.md),
[os pacotes assistidos](pacotes-assistidos.md) e
[o diagrama de casos de uso](casos-de-uso.puml).

O banco operacional não foi alterado nesta etapa. A implantação em base existente
requer migração autorizada com inventário e backup verificado.
