package br.com.rotabrasil.repository;
import br.com.rotabrasil.model.Pacote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PacoteRepository extends JpaRepository<Pacote,Long> { Optional<Pacote> findBySha256(String sha256); }
