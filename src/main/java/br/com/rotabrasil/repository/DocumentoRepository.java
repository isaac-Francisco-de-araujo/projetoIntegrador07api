
package br.com.rotabrasil.repository;

import br.com.rotabrasil.model.Documento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    List<Documento> findByViagemId(Long viagemId);

    List<Documento> findByTipoIgnoreCase(String tipo);

    List<Documento> findAllByChaveAcesso(String chaveAcesso);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select d from Documento d where d.chaveAcesso = :chave")
    List<Documento> buscarParaReconciliacao(@Param("chave") String chave);

    List<Documento> findByTipoIgnoreCaseAndChaveAcessoIsNull(String tipo);
    boolean existsByViagemId(Long viagemId);
}
