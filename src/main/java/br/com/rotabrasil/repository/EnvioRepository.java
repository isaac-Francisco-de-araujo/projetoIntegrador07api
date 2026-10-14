package br.com.rotabrasil.repository;

import br.com.rotabrasil.model.Envio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EnvioRepository extends JpaRepository<Envio, Long> {
    List<Envio> findByViagemId(Long viagemId);
    List<Envio> findByClienteId(Long clienteId);
    List<Envio> findByStatus(Envio.Status status);
}
