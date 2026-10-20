package br.com.rotabrasil.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class CoordenacaoImportacaoService {
    private final JdbcTemplate jdbc;
    public void bloquear() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Coordenação exige transação ativa");
        }
        Integer id = jdbc.queryForObject("SELECT id FROM controle_importacao WHERE id=1 FOR UPDATE", Integer.class);
        if (id == null || id != 1) throw new IllegalStateException("Banco não preparado para importação");
    }
}
