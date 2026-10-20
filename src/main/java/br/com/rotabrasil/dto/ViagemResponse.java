package br.com.rotabrasil.dto;

import br.com.rotabrasil.model.Viagem;
import java.time.LocalDate;

public record ViagemResponse(Long id, String numero, LocalDate data, String status, Long clienteId) {
    public static ViagemResponse de(Viagem viagem) {
        return new ViagemResponse(viagem.getId(), viagem.getNumero(), viagem.getData(), viagem.getStatus(), viagem.getCliente().getId());
    }
}
