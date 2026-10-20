
package br.com.rotabrasil.repository;

import br.com.rotabrasil.model.Documento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    List<Documento> findByViagemId(Long viagemId);

    List<Documento> findByTipoIgnoreCase(String tipo);

    Optional<Documento> findByViagemIdAndTipoIgnoreCaseAndNomeArquivoIgnoreCase(
            Long viagemId,
            String tipo,
            String nomeArquivo
    );
}
