package br.com.rotabrasil;

import br.com.rotabrasil.manutencao.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.nio.file.*;
import java.sql.*;
import java.util.zip.ZipInputStream;
import static org.assertj.core.api.Assertions.*;

class MigracaoEBackupTest {
    @TempDir Path base;
    private Path raiz() { return base.resolve("viagens"); }
    private Path backup() { return base.resolve("backup"); }
    private Connection legado() throws Exception {
        Files.createDirectories(raiz());
        Files.writeString(raiz().resolve("legado.xml"), CteFixture.xml());
        var conexao = DriverManager.getConnection("jdbc:h2:file:" + base.resolve("banco"), "sa", "");
        ScriptUtils.executeSqlScript(conexao, new ClassPathResource("schema-legado-etapa2.sql"));
        try (var comando = conexao.prepareStatement("UPDATE documentos SET caminho=? WHERE id=103")) {
            comando.setString(1, raiz().resolve("legado.xml").toString()); comando.executeUpdate();
        }
        return conexao;
    }
    private void criarBackup(Connection conexao) throws Exception {
        try (var comando = conexao.createStatement()) { comando.execute("SET EXCLUSIVE 1"); }
        BackupBanco.criar(conexao, raiz(), backup());
    }
    private boolean colunaIdentidade(Connection conexao) throws Exception {
        try (var rs = conexao.getMetaData().getColumns(null, "PUBLIC", "DOCUMENTOS", "CHAVE_ACESSO")) { return rs.next(); }
    }
    private String escalar(Connection conexao, String sql) throws Exception {
        try (var comando = conexao.createStatement(); var rs = comando.executeQuery(sql)) { rs.next(); return rs.getString(1); }
    }

    @Test void inventarioIdentificaLegadoSemAlterarEsquemaOuConteudo() throws Exception {
        try (var conexao = legado()) {
            var relatorio = new InventarioBanco(raiz()).analisar(conexao);
            assertThat(relatorio.aprovado()).isTrue();
            assertThat(relatorio.identidades()).hasSize(1);
            assertThat(relatorio.identidades().getFirst().chave()).isEqualTo(CteFixture.CHAVE);
            assertThat(colunaIdentidade(conexao)).isFalse();
            assertThat(Files.readString(raiz().resolve("legado.xml"))).isEqualTo(CteFixture.xml());
        }
    }

    @Test void backupVerificadoPermiteRestaurarBancoEDocumentos() throws Exception {
        try (var conexao = legado()) { criarBackup(conexao); }
        assertThat(BackupBanco.verificar(backup()).arquivos()).hasSize(1);
        Path restaurado = base.resolve("restaurado.mv.db");
        int bancos = 0;
        try (var zip = new ZipInputStream(Files.newInputStream(backup().resolve("banco.zip")))) {
            for (var entrada = zip.getNextEntry(); entrada != null; entrada = zip.getNextEntry()) {
                if (entrada.getName().endsWith(".mv.db")) { Files.copy(zip, restaurado); bancos++; }
            }
        }
        assertThat(bancos).isEqualTo(1);
        try (var conexao = DriverManager.getConnection("jdbc:h2:file:" + base.resolve("restaurado") + ";IFEXISTS=TRUE", "sa", "")) {
            assertThat(escalar(conexao, "SELECT nome_arquivo FROM documentos WHERE id=103")).isEqualTo("legado.xml");
            assertThat(escalar(conexao, "SELECT COUNT(*) FROM documentos")).isEqualTo("1");
            assertThat(escalar(conexao, "SELECT caminho FROM documentos WHERE id=103")).isEqualTo(raiz().resolve("legado.xml").toString());
        }
        assertThat(Files.readString(backup().resolve("arquivos/legado.xml"))).isEqualTo(CteFixture.xml());
    }

    @Test void migracaoPreencheIdentidadePreservaCaminhoEImpedeDuplicatas() throws Exception {
        try (var conexao = legado()) {
            criarBackup(conexao);
            ManutencaoBanco.migrar(conexao, raiz(), backup());
            // Repetir a ferramenta valida o histórico, sem preencher novamente nem alterar arquivos.
            ManutencaoBanco.migrar(conexao, raiz(), backup());
            assertThat(escalar(conexao, "SELECT chave_acesso FROM documentos WHERE id=103")).isEqualTo(CteFixture.CHAVE);
            assertThat(escalar(conexao, "SELECT LENGTH(sha256) FROM documentos WHERE id=103")).isEqualTo("64");
            assertThat(escalar(conexao, "SELECT caminho FROM documentos WHERE id=103")).isEqualTo(raiz().resolve("legado.xml").toString());
            try (var comando = conexao.createStatement()) {
                assertThatThrownBy(() -> comando.executeUpdate("INSERT INTO viagens(numero,cliente_id) VALUES ('66213',101)"))
                        .isInstanceOf(SQLException.class).extracting(e -> ((SQLException)e).getSQLState()).isEqualTo("23505");
                assertThatThrownBy(() -> comando.executeUpdate("INSERT INTO documentos(nome_arquivo,tipo,caminho,viagem_id,chave_acesso,sha256) SELECT 'outro.xml',tipo,caminho,viagem_id,chave_acesso,sha256 FROM documentos WHERE id=103"))
                        .isInstanceOf(SQLException.class).extracting(e -> ((SQLException)e).getSQLState()).isEqualTo("23505");
                assertThatThrownBy(() -> comando.executeUpdate("INSERT INTO documentos(nome_arquivo,tipo,caminho,viagem_id) VALUES ('sem.xml','XML','sem.xml',102)"))
                        .isInstanceOf(SQLException.class).extracting(e -> ((SQLException)e).getSQLState()).isEqualTo("23513");
            }
            assertThat(escalar(conexao, "SELECT COUNT(*) FROM documentos")).isEqualTo("1");
            assertThat(Files.readString(raiz().resolve("legado.xml"))).isEqualTo(CteFixture.xml());
        }
    }

    @Test void duplicataFiscalBloqueiaAntesDeBaselineOuPreenchimento() throws Exception {
        try (var conexao = legado()) {
            Files.writeString(raiz().resolve("duplicado.xml"), CteFixture.xml());
            try (var comando = conexao.prepareStatement("INSERT INTO documentos(id,nome_arquivo,tipo,caminho,viagem_id) VALUES (104,'duplicado.xml','XML',?,102)")) {
                comando.setString(1, raiz().resolve("duplicado.xml").toString()); comando.executeUpdate();
            }
            assertThat(new InventarioBanco(raiz()).analisar(conexao).achados()).extracting(InventarioBanco.Achado::codigo)
                    .contains("CHAVE_FISCAL_DUPLICADA");
            criarBackup(conexao);
            assertThatThrownBy(() -> ManutencaoBanco.migrar(conexao, raiz(), backup())).isInstanceOf(IllegalStateException.class);
            assertThat(colunaIdentidade(conexao)).isFalse();
            assertThat(escalar(conexao, "SELECT COUNT(*) FROM documentos")).isEqualTo("2");
        }
    }

    @Test void arquivosAusentesEOrfaosImpedemMigracao() throws Exception {
        try (var conexao = legado()) {
            Files.delete(raiz().resolve("legado.xml"));
            Files.writeString(raiz().resolve("orfao.xml"), CteFixture.xml());
            assertThat(new InventarioBanco(raiz()).analisar(conexao).achados()).extracting(InventarioBanco.Achado::codigo)
                    .contains("DOCUMENTO_SEM_ARQUIVO", "ARQUIVO_SEM_DOCUMENTO");
            criarBackup(conexao);
            assertThatThrownBy(() -> ManutencaoBanco.migrar(conexao, raiz(), backup())).isInstanceOf(IllegalStateException.class);
            assertThat(colunaIdentidade(conexao)).isFalse();
        }
    }

    @Test void backupDesatualizadoBloqueiaAntesDeQualquerMudanca() throws Exception {
        try (var conexao = legado()) {
            criarBackup(conexao);
            Files.writeString(raiz().resolve("legado.xml"), CteFixture.xml() + "\n");
            assertThatThrownBy(() -> ManutencaoBanco.migrar(conexao, raiz(), backup())).isInstanceOf(IllegalStateException.class);
            assertThat(colunaIdentidade(conexao)).isFalse();
        }
    }

    @Test void copiaCorrompidaNaoEhAceitaComoBackup() throws Exception {
        try (var conexao = legado()) { criarBackup(conexao); }
        Files.writeString(backup().resolve("arquivos/legado.xml"), "corrompido");
        assertThatThrownBy(() -> BackupBanco.verificar(backup())).isInstanceOf(IllegalStateException.class);
    }

    @Test void pendenciaDeArmazenamentoImpedeDeclararBackupConsistente() throws Exception {
        try (var conexao = legado()) {
            Files.createDirectories(raiz().resolve(".pendentes/op-em-andamento"));
            assertThatThrownBy(() -> criarBackup(conexao)).isInstanceOf(IllegalStateException.class);
            assertThat(backup()).doesNotExist();
        }
    }

    @Test void ferramentaNaoCriaBancoAusentePorEngano() {
        assertThatThrownBy(() -> ManutencaoBanco.executar("inventario", "--banco", base.resolve("inexistente").toString(),
                "--arquivos", raiz().toString(), "--saida", base.resolve("inventario.json").toString()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(base.resolve("inexistente.mv.db")).doesNotExist();
    }

    @Test void ferramentaGeraInventarioSomenteDeLeituraForaDoAcervo() throws Exception {
        try (var conexao = legado()) { assertThat(colunaIdentidade(conexao)).isFalse(); }
        Path saida = base.resolve("relatorio.json");
        ManutencaoBanco.executar("inventario", "--banco", base.resolve("banco").toString(),
                "--arquivos", raiz().toString(), "--saida", saida.toString());
        var relatorio = new com.fasterxml.jackson.databind.ObjectMapper().readTree(saida.toFile());
        assertThat(relatorio.get("achados")).isEmpty();
        try (var conexao = DriverManager.getConnection("jdbc:h2:file:"+base.resolve("banco"), "sa", "")) {
            assertThat(colunaIdentidade(conexao)).isFalse();
        }
        assertThat(Files.getPosixFilePermissions(saida)).isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
    }

    @Test void bancoLegadoNaoRecebeBaselineAutomatico() throws Exception {
        try (var conexao = legado()) {
            var fonte = new org.springframework.jdbc.datasource.SingleConnectionDataSource(conexao, true);
            var flyway = org.flywaydb.core.Flyway.configure().dataSource(fonte).baselineOnMigrate(false).load();
            assertThatThrownBy(flyway::migrate).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
            assertThat(colunaIdentidade(conexao)).isFalse();
        }
    }

    @Test void backupOfflineAbreOrigemSomenteParaLeituraEPreservaBytesDoBanco() throws Exception {
        try (var conexao = legado()) { assertThat(colunaIdentidade(conexao)).isFalse(); }
        String antes=InventarioBanco.hashArquivo(base.resolve("banco.mv.db"));
        ManutencaoBanco.executar("backup", "--banco", base.resolve("banco").toString(),
                "--arquivos", raiz().toString(), "--saida", backup().toString());
        assertThat(InventarioBanco.hashArquivo(base.resolve("banco.mv.db"))).isEqualTo(antes);
        assertThat(BackupBanco.verificar(backup()).arquivos()).hasSize(1);
    }

    @Test void backupPreservaEstadoInconsistenteMasNaoAutorizaMigracao() throws Exception {
        try(var conexao=legado()) {
            try(var comando=conexao.createStatement()) { comando.executeUpdate("UPDATE documentos SET caminho='C:\\legado\\documento.xml' WHERE id=103"); }
            criarBackup(conexao);
            assertThat(BackupBanco.verificar(backup()).arquivos()).hasSize(1);
            assertThatThrownBy(() -> ManutencaoBanco.migrar(conexao, raiz(), backup())).isInstanceOf(IllegalStateException.class);
            assertThat(colunaIdentidade(conexao)).isFalse();
        }
    }

    @Test void migraV4ParaV5ComBackupSemAlterarDadosOuArquivos() throws Exception {
        try (var conexao = legado()) {
            criarBackup(conexao);
            var flyway = org.flywaydb.core.Flyway.configure()
                    .dataSource(new org.springframework.jdbc.datasource.SingleConnectionDataSource(conexao, true))
                    .baselineVersion("1").baselineOnMigrate(false).cleanDisabled(true).target("4")
                    .javaMigrations(new br.com.rotabrasil.migracao.V3__ConferirEPreencherIdentidade(new InventarioBanco(raiz()), raiz(), backup())).load();
            flyway.baseline(); flyway.migrate();
            var antes = new java.util.TreeMap<String,String>();
            try (var comando = conexao.createStatement(); var rs = comando.executeQuery("SELECT * FROM documentos WHERE id=103")) {
                rs.next();for(int campo=1;campo<=rs.getMetaData().getColumnCount();campo++)antes.put(rs.getMetaData().getColumnName(campo),rs.getString(campo));
            }
            Path novoBackup=base.resolve("backup-v4");BackupBanco.criar(conexao,raiz(),novoBackup);
            ManutencaoBanco.migrar(conexao,raiz(),novoBackup);
            for(var campo:antes.entrySet())assertThat(escalar(conexao,"SELECT "+campo.getKey()+" FROM documentos WHERE id=103")).isEqualTo(campo.getValue());
            assertThat(escalar(conexao,"SELECT arquivado_em FROM documentos WHERE id=103")).isNull();
            assertThat(escalar(conexao,"SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\"='5' AND \"success\"=TRUE")).isEqualTo("1");
            assertThat(Files.readString(raiz().resolve("legado.xml"))).isEqualTo(CteFixture.xml());
        }
    }
}
