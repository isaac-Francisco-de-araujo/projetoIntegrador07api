package br.com.rotabrasil.repository;
import br.com.rotabrasil.model.Anexo;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface AnexoRepository extends JpaRepository<Anexo,Long> {
    List<Anexo> findByPacoteId(Long pacoteId);
    List<Anexo> findByViagensIdAndArquivadoEmIsNull(Long viagemId);
    List<Anexo> findByViagensIdAndArquivadoEmIsNotNull(Long viagemId);
    boolean existsByViagensId(Long viagemId);
    Optional<Anexo> findByToken(String token);
    @Lock(LockModeType.PESSIMISTIC_READ) @Query("select a from Anexo a where a.token=:token")
    Optional<Anexo> buscarParaReconciliacao(@org.springframework.data.repository.query.Param("token") String token);
}
