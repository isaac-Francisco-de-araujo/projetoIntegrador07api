package br.com.rotabrasil.dto;

import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.UniqueElements;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/** Seleção explícita apenas para conferência; não autoriza nem agenda envio. */
public record PreviaEmailRequest(
        @NotBlank @Email @Size(max=255) String destinatario,
        @UniqueElements List<@NotNull @Positive Long> documentoIds,
        @UniqueElements List<@NotNull @Positive Long> anexoIds) {
    public PreviaEmailRequest {documentoIds=documentoIds==null?List.of():documentoIds;anexoIds=anexoIds==null?List.of():anexoIds;}
    public PreviaEmailRequest(String destinatario,List<Long> documentoIds){this(destinatario,documentoIds,List.of());}
    @AssertTrue(message="Selecione ao menos um documento ou PDF") @JsonIgnore
    public boolean isSelecaoValida(){return !documentoIds.isEmpty() || !anexoIds.isEmpty();}
}
