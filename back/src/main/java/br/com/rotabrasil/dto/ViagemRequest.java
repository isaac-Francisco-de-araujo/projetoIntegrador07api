package br.com.rotabrasil.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record ViagemRequest(
        @NotBlank @Pattern(regexp = "[0-9]{1,9}", message = "deve conter de 1 a 9 dígitos")
        @DecimalMin(value = "1", message = "deve ser maior que zero") String numero,
        LocalDate data,
        @Size(max = 255) String status,
        @NotNull @Positive Long clienteId) {}
