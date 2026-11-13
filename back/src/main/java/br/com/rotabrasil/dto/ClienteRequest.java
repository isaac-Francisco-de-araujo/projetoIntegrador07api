package br.com.rotabrasil.dto;

import jakarta.validation.constraints.*;

public record ClienteRequest(
        @NotBlank @Size(max = 255) String nome,
        @NotBlank @Pattern(regexp = "[0-9]{14}", message = "deve conter 14 dígitos") String cnpj,
        @Email @Size(max = 255) String email,
        @Size(max = 255) String telefone) {}
