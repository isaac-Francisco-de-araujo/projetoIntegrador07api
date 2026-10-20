package br.com.rotabrasil.migracao;

import br.com.rotabrasil.manutencao.InventarioBanco;
import br.com.rotabrasil.manutencao.BackupBanco;
import java.nio.file.Path;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public final class V3__ConferirEPreencherIdentidade extends BaseJavaMigration {
    private final InventarioBanco inventario;
    private final Path raiz;
    private final Path backup;
    public V3__ConferirEPreencherIdentidade(InventarioBanco inventario, Path raiz, Path backup) {
        this.inventario = inventario; this.raiz = raiz; this.backup = backup;
    }
    @Override public Integer getChecksum() { return 1; }
    @Override public void migrate(Context contexto) throws Exception {
        var relatorio = inventario.analisar(contexto.getConnection());
        if (!relatorio.aprovado()) throw new IllegalStateException("Inventário reprovado; saneamento explícito necessário antes das restrições");
        if (relatorio.totais().values().stream().anyMatch(total -> total > 0)) {
            if (backup == null) throw new IllegalStateException("Banco existente exige backup verificado pela manutenção offline");
            BackupBanco.conferirOrigem(contexto.getConnection(), raiz, backup);
        }
        try (var comando = contexto.getConnection().prepareStatement("UPDATE documentos SET chave_acesso=?, sha256=? WHERE id=?")) {
            for (var identidade : relatorio.identidades()) {
                comando.setString(1, identidade.chave()); comando.setString(2, identidade.sha256());
                comando.setLong(3, identidade.documentoId()); comando.addBatch();
            }
            comando.executeBatch();
        }
    }
}
