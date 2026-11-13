package br.com.rotabrasil.service;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;

/** Mantém a trava da operação enquanto o importador ou reconciliador a utiliza. */
public final class OperacaoArquivo implements AutoCloseable {
    final Path diretorio;
    final Path temporario;
    final Path destino;
    final String numero;
    final String chave;
    final String hash;
    private FileChannel canal;
    private FileLock trava;
    boolean publicado;

    OperacaoArquivo(Path diretorio, Path destino, String numero, String chave, String hash) {
        this.diretorio = diretorio;
        this.temporario = diretorio.resolve("conteudo.tmp");
        this.destino = destino;
        this.numero = numero;
        this.chave = chave;
        this.hash = hash;
    }

    void travar(FileChannel canal, FileLock trava) {
        this.canal = canal;
        this.trava = trava;
    }

    public String id() { return diretorio.getFileName().toString(); }
    public Path destino() { return destino; }
    public Path temporario() { return temporario; }
    public Path diretorio() { return diretorio; }

    @Override
    public void close() throws IOException {
        try {
            if (trava != null && trava.isValid()) trava.release();
        } finally {
            if (canal != null) canal.close();
        }
    }
}
