package br.com.rotabrasil.dto;

import br.com.rotabrasil.model.Envio;
import java.time.LocalDateTime;

/** Histórico existente; mensagens internas e entidades não são expostas. */
public record EnvioResponse(Long id, Long viagemId, Long clienteId, LocalDateTime dataEnvio, Envio.Status status) {
    public static EnvioResponse de(Envio envio) {
        return new EnvioResponse(envio.getId(), envio.getViagem().getId(), envio.getCliente().getId(), envio.getDataEnvio(), envio.getStatus());
    }
}
