package br.com.rotabrasil.manutencao;

import br.com.rotabrasil.migracao.V3__ConferirEPreencherIdentidade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** CLI offline: não inicializa JPA, servidor HTTP ou SMTP. */
public final class ManutencaoBanco {
    private ManutencaoBanco() {}
    public static void main(String[] args) {
        try { executar(args); }
        catch (Exception ex) {
            // Mensagens de drivers podem conter registros fiscais ou credenciais.
            System.err.println("Manutenção não concluída. Confira argumentos, inventário, backup e disponibilidade exclusiva do banco.");
            System.exit(1);
        }
    }

    public static void executar(String... args) throws Exception {
        if (args.length < 1 || (args.length - 1) % 2 != 0) throw new IllegalArgumentException("Argumentos inválidos");
        var opcoes = new HashMap<String, String>();
        for (int i = 1; i < args.length; i += 2) {
            if (!List.of("--banco", "--arquivos", "--saida", "--backup").contains(args[i]) || opcoes.put(args[i], args[i + 1]) != null) {
                throw new IllegalArgumentException("Opção inválida");
            }
        }
        Path banco = Path.of(Objects.requireNonNull(opcoes.get("--banco"))).toAbsolutePath().normalize();
        Path raiz = Path.of(Objects.requireNonNull(opcoes.get("--arquivos"))).toAbsolutePath().normalize();
        if (banco.toString().contains(";") || !Files.isRegularFile(Path.of(banco + ".mv.db"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Informe a base de um banco existente, sem .mv.db");
        }
        String acao = args[0];
        if (!List.of("inventario", "backup", "migrar").contains(acao)) throw new IllegalArgumentException("Ação inválida");
        String url = "jdbc:h2:file:" + banco + ";IFEXISTS=TRUE" + (!acao.equals("migrar") ? ";ACCESS_MODE_DATA=r" : "");
        try (var conexao = DriverManager.getConnection(url, System.getenv().getOrDefault("ROTABRASIL_DB_USER", "sa"),
                System.getenv().getOrDefault("ROTABRASIL_DB_PASSWORD", ""))) {
            if (acao.equals("inventario")) {
                var relatorio = new InventarioBanco(raiz).analisar(conexao);
                Path saida = Path.of(Objects.requireNonNull(opcoes.get("--saida")));
                if (saida.toAbsolutePath().normalize().startsWith(raiz)) throw new IllegalArgumentException("Relatório deve ficar fora do acervo");
                java.nio.file.attribute.FileAttribute<?>[] atributos = Files.getFileStore(saida.toAbsolutePath().getParent()).supportsFileAttributeView("posix")
                        ? new java.nio.file.attribute.FileAttribute<?>[] {java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))}
                        : new java.nio.file.attribute.FileAttribute<?>[0];
                try (var canal = java.nio.channels.FileChannel.open(saida, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), atributos);
                     var stream = java.nio.channels.Channels.newOutputStream(canal)) {
                    new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(stream, relatorio);
                }
                System.out.println("Inventário concluído: " + relatorio.achados().size() + " impedimentos; relatório local gerado.");
                return;
            }
            try (var comando = conexao.createStatement()) { comando.execute("SET EXCLUSIVE 1"); }
            try {
                if (acao.equals("backup")) {
                    BackupBanco.criar(conexao, raiz, Path.of(Objects.requireNonNull(opcoes.get("--saida"))));
                    System.out.println("Backup do banco e dos arquivos da raiz criado e verificado; consulte inventario.json para limitações de cobertura.");
                } else {
                    migrar(conexao, raiz, Path.of(Objects.requireNonNull(opcoes.get("--backup"))));
                    System.out.println("Migrações concluídas; arquivos e registros existentes preservados.");
                }
            } finally {
                try (var comando = conexao.createStatement()) { comando.execute("SET EXCLUSIVE 0"); }
            }
        }
    }

    public static void migrar(Connection conexao, Path raiz, Path backup) throws Exception {
        var fonte = new SingleConnectionDataSource(conexao, true);
        var flyway = Flyway.configure().dataSource(fonte).cleanDisabled(true).baselineOnMigrate(false)
                .baselineVersion("1").javaMigrations(new V3__ConferirEPreencherIdentidade(new InventarioBanco(raiz), raiz, backup)).load();
        BackupBanco.verificar(backup);
        boolean possuiHistorico;
        try (var consulta = conexao.getMetaData().getTables(null, "PUBLIC", "flyway_schema_history", null)) {
            possuiHistorico = consulta.next();
        }
        if (possuiHistorico && flyway.info().pending().length == 0) { flyway.validate(); return; }
        BackupBanco.conferirOrigem(conexao, raiz, backup);
        if (!new InventarioBanco(raiz).analisar(conexao).aprovado()) throw new IllegalStateException("Inventário reprovado");
        if (!possuiHistorico) flyway.baseline();
        flyway.migrate();
    }
}
