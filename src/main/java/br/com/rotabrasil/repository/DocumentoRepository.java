package br.com.rotabrasil.repository;

import br.com.rotabrasil.model.Documento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {
    List<Documento> findByViagemId(Long viagemId);
    List<Documento> findByTipoIgnoreCase(String tipo);
}
