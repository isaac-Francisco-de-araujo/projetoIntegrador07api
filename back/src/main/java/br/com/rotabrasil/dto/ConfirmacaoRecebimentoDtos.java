package br.com.rotabrasil.dto;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class ConfirmacaoRecebimentoDtos {
    private ConfirmacaoRecebimentoDtos(){}
    public record Conferir(@NotBlank @Email @Size(max=255) String destinatario,UUID anteriorId){}
    public record Confirmar(@NotNull UUID conferenciaId,@AssertTrue boolean confirmado){}
    public record Snapshot(String remetente,String destinatario,String assunto,String mensagem,String modo,long versaoConfiguracao,
            String assinaturaTransporte,String recebimentoId,String pacote,Long pacoteId,String estadoProcessamento,long revisaoDestinatario,String contexto,boolean teste,String testeAnteriorId){}
    public record Conferencia(String id,String anteriorId,OffsetDateTime expiraEm,Snapshot snapshot){}
}
