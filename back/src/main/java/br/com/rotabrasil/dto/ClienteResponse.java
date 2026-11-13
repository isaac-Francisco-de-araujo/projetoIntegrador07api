package br.com.rotabrasil.dto;

import br.com.rotabrasil.model.Cliente;

public record ClienteResponse(Long id, String nome, String cnpj, String email, String telefone) {
    public static ClienteResponse de(Cliente cliente) {
        return new ClienteResponse(cliente.getId(), cliente.getNome(), cliente.getCnpj(), cliente.getEmail(), cliente.getTelefone());
    }
}
