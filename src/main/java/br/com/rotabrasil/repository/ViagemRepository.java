package br.com.rotabrasil.repository;

import br.com.rotabrasil.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ViagemRepository extends JpaRepository<Viagem, Long> {
    Optional<Viagem> findByNumero(String numero);
    List<Viagem> findByClienteId(Long clienteId);
}
