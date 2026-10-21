package br.com.rotabrasil.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
@DependsOnDatabaseInitialization
@ConditionalOnProperty(name = "rotabrasil.banco.exigir-restricoes", havingValue = "true", matchIfMissing = true)
public class VerificacaoEsquemaBanco implements InitializingBean {
    private final JdbcTemplate jdbc;
    @Override public void afterPropertiesSet() {
        for (String nome : List.of("UK_CLIENTES_CNPJ", "UK_VIAGENS_NUMERO", "UK_DOCUMENTOS_CHAVE", "CK_DOCUMENTOS_IDENTIDADE_XML")) {
            String tipo = nome.startsWith("UK_") ? "UNIQUE" : "CHECK";
            Integer quantidade = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='PUBLIC' AND constraint_name=? AND constraint_type=?", Integer.class, nome, tipo);
            if (quantidade == null || quantidade != 1) throw new IllegalStateException("Migrações de integridade incompletas; use a manutenção offline");
            if (tipo.equals("UNIQUE")) {
                String coluna = nome.equals("UK_CLIENTES_CNPJ") ? "CNPJ" : nome.equals("UK_VIAGENS_NUMERO") ? "NUMERO" : "CHAVE_ACESSO";
                var colunas = jdbc.queryForList("SELECT column_name FROM information_schema.key_column_usage WHERE constraint_schema='PUBLIC' AND constraint_name=? ORDER BY ordinal_position", String.class, nome);
                if (!colunas.equals(List.of(coluna))) throw new IllegalStateException("Restrição de unicidade divergente");
            }
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\"='6' AND \"success\"=TRUE", Integer.class) != 1) {
            throw new IllegalStateException("Histórico de migrações incompleto");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM controle_importacao WHERE id=1", Integer.class) != 1) {
            throw new IllegalStateException("Coordenação de importação não inicializada");
        }
    }
}
