package br.com.rotabrasil.repository;
import br.com.rotabrasil.model.Pacote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
public interface PacoteRepository extends JpaRepository<Pacote,Long> {
    Optional<Pacote> findBySha256(String sha256);
    List<Pacote> findByDocumentos_Id(Long documentoId);
}
