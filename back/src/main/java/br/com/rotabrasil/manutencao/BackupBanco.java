package br.com.rotabrasil.manutencao;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.util.List;
import java.util.zip.ZipFile;

public final class BackupBanco {
    private static final ObjectMapper JSON = new ObjectMapper();
    private BackupBanco() {}
    public record Manifesto(int versao, String impressao, String bancoSha256, String raizOriginal,
                            List<InventarioBanco.Arquivo> arquivos) {}

    /** Executar com os escritores parados e conexão em modo EXCLUSIVE. Não copia um .mv.db aberto. */
    public static void criar(Connection conexao, Path raiz, Path destino) throws Exception {
        raiz = raiz.toAbsolutePath().normalize(); destino = destino.toAbsolutePath().normalize();
        if (destino.startsWith(raiz) || Files.exists(destino)) throw new IllegalArgumentException("Backup deve ser novo e fora da raiz dos documentos");
        var inventario = new InventarioBanco(raiz);
        var antes = inventario.analisar(conexao);
        if (antes.achados().stream().anyMatch(a -> List.of("OPERACOES_PENDENTES", "ARTEFATO_NAO_REGULAR",
                "ARQUIVO_MUDOU_DURANTE_LEITURA").contains(a.codigo()))) {
            throw new IllegalStateException("Resolva pendências de armazenamento antes do backup consistente");
        }
        Files.createDirectory(destino);
        if (Files.getFileStore(destino).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(destino, PosixFilePermissions.fromString("rwx------"));
        }
        Path banco = destino.resolve("banco.zip");
        try (var comando = conexao.createStatement()) {
            comando.execute("BACKUP TO '" + banco.toString().replace("'", "''") + "'");
        }
        for (var arquivo : antes.arquivos()) {
            Path copia = destino.resolve("arquivos").resolve(arquivo.relativo());
            Files.createDirectories(copia.getParent());
            Files.copy(raiz.resolve(arquivo.relativo()), copia);
            if (!arquivo.sha256().equals(InventarioBanco.hashArquivo(copia))) throw new IllegalStateException("Cópia de arquivo não confere");
        }
        if (!antes.impressao().equals(inventario.analisar(conexao).impressao())) {
            throw new IllegalStateException("Acervo mudou durante o backup; backup não aprovado");
        }
        var manifesto = new Manifesto(1, antes.impressao(), InventarioBanco.hashArquivo(banco), raiz.toString(), antes.arquivos());
        JSON.writerWithDefaultPrettyPrinter().writeValue(destino.resolve("inventario.json").toFile(), antes);
        JSON.writerWithDefaultPrettyPrinter().writeValue(destino.resolve("manifesto.json").toFile(), manifesto);
        verificar(destino);
    }

    public static Manifesto verificar(Path backup) throws Exception {
        var manifesto = JSON.readValue(backup.resolve("manifesto.json").toFile(), Manifesto.class);
        if (manifesto.versao() != 1 || !manifesto.bancoSha256().equals(InventarioBanco.hashArquivo(backup.resolve("banco.zip")))) {
            throw new IllegalStateException("Backup de banco inválido");
        }
        try (var zip = new ZipFile(backup.resolve("banco.zip").toFile())) {
            if (zip.size() == 0) throw new IllegalStateException("Backup vazio");
            var entradas = zip.entries();
            while (entradas.hasMoreElements()) {
                try (var entrada = zip.getInputStream(entradas.nextElement())) { entrada.transferTo(java.io.OutputStream.nullOutputStream()); }
            }
        }
        Path raizBackup = backup.resolve("arquivos").toAbsolutePath().normalize();
        for (var arquivo : manifesto.arquivos()) {
            Path caminho = raizBackup.resolve(arquivo.relativo()).normalize();
            if (!caminho.startsWith(raizBackup) || !Files.isRegularFile(caminho, LinkOption.NOFOLLOW_LINKS) ||
                    !arquivo.sha256().equals(InventarioBanco.hashArquivo(caminho))) {
                throw new IllegalStateException("Backup de documentos inválido");
            }
        }
        return manifesto;
    }

    public static void conferirOrigem(Connection conexao, Path raiz, Path backup) throws Exception {
        var manifesto = verificar(backup);
        if (!manifesto.raizOriginal().equals(raiz.toAbsolutePath().normalize().toString()) ||
                !manifesto.impressao().equals(new InventarioBanco(raiz).analisar(conexao).impressao())) {
            throw new IllegalStateException("Backup não corresponde ao estado atual do banco e dos arquivos");
        }
    }
}
