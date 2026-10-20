package br.com.rotabrasil.dto;

import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.UniqueElements;
import java.util.List;

/** Seleção explícita apenas para conferência; não autoriza nem agenda envio. */
public record PreviaEmailRequest(
        @NotBlank @Email @Size(max = 255) String destinatario,
        @NotEmpty @UniqueElements(message = "não deve conter documentos repetidos")
        List<@NotNull @Positive Long> documentoIds) {}
