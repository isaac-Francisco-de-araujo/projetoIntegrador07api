package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.ArmazenamentoException;
import br.com.rotabrasil.exception.ArquivoInvalidoException;
import br.com.rotabrasil.exception.ConflitoDocumentoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

@Service
public class OrganizadorArquivosService {
    private final Path raizViagens;

    public OrganizadorArquivosService(@Value("${rotabrasil.arquivos.viagens:arquivos/viagens}") String raiz) {
        raizViagens = Paths.get(raiz).toAbsolutePath().normalize();
    }

    public OperacaoArquivo prepararXml(byte[] conteudo, String numero, String chave) {
        if (chave == null || !chave.matches("[0-9]{44}")) {
            throw new ArquivoInvalidoException("Chave fiscal inválida para armazenamento");
        }
        return preparar(conteudo, numero, chave, chave + ".xml");
    }

    private OperacaoArquivo preparar(byte[] conteudo, String numero, String chave, String nome) {
        validarNumero(numero);
        OperacaoArquivo operacao = null;
        try {
            Path destino = destino(numero, nome);
            Files.createDirectories(destino.getParent());
            validarPasta(destino.getParent());
            Path pendentes = pendentes();
            Files.createDirectories(pendentes);
            validarPasta(pendentes);
            Path diretorio = Files.createDirectory(pendentes.resolve("op-" + UUID.randomUUID()));
            operacao = new OperacaoArquivo(diretorio, destino, numero, chave, ConteudoXml.sha256(conteudo));
            var canal = FileChannel.open(diretorio.resolve("operacao.lock"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            operacao.travar(canal, null);
            operacao.travar(canal, canal.lock());
            Properties metadados = new Properties();
            metadados.setProperty("versao", "1");
            metadados.setProperty("numero", numero);
            metadados.setProperty("chave", chave == null ? "" : chave);
            metadados.setProperty("nome", nome);
            metadados.setProperty("sha256", operacao.hash);
            var dados = new ByteArrayOutputStream();
            metadados.store(dados, "Operacao de armazenamento RotaBrasil");
            gravarForcado(diretorio.resolve("operacao.properties"), dados.toByteArray());
            gravarForcado(operacao.temporario, conteudo);
            return operacao;
        } catch (IOException | RuntimeException ex) {
            if (operacao != null) {
                try {
                    limpar(operacao);
                } catch (IOException falhaLimpeza) {
                    ex.addSuppressed(falhaLimpeza);
                }
            }
            if (ex instanceof ArquivoInvalidoException invalido) throw invalido;
            throw new ArmazenamentoException("Não foi possível preparar o XML; nenhuma publicação foi concluída", ex);
        }
    }

    /** Escreve e força os bytes antes de permitir a publicação do nome definitivo. */
    protected void gravarForcado(Path caminho, byte[] conteudo) throws IOException {
        try (var canal = FileChannel.open(caminho, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var buffer = ByteBuffer.wrap(conteudo);
            while (buffer.hasRemaining()) canal.write(buffer);
            canal.force(true);
        }
    }

    public void publicar(OperacaoArquivo operacao) {
        try {
            if (!confere(operacao.temporario, operacao.hash)) {
                throw new ArmazenamentoException("Conteúdo temporário inválido; publicação interrompida");
            }
            Files.createDirectories(operacao.destino.getParent());
            validarPasta(operacao.destino.getParent());
            // Hard link publica o arquivo completo atomicamente e falha se o destino existe.
            // ATOMIC_MOVE sem REPLACE_EXISTING não garante a preservação do destino em todos os provedores.
            Files.createLink(operacao.destino, operacao.temporario);
            operacao.publicado = true;
        } catch (FileAlreadyExistsException ex) {
            throw new ConflitoDocumentoException("Arquivo de destino já existe e foi preservado; reconciliação necessária");
        } catch (IOException | UnsupportedOperationException ex) {
            throw new ArmazenamentoException("Não foi possível publicar o XML sem sobrescrita", ex);
        }
    }

    /** Só remove a referência definitiva criada por esta operação e com os bytes esperados. */
    public void desfazer(OperacaoArquivo operacao) {
        try {
            if (Files.exists(operacao.destino, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isRegularFile(operacao.destino, LinkOption.NOFOLLOW_LINKS) &&
                        Files.isSameFile(operacao.destino, operacao.temporario)) {
                    if (!confere(operacao.destino, operacao.hash)) {
                        throw new ArmazenamentoException("Arquivo da operação foi alterado; pendência preservada");
                    }
                    Files.delete(operacao.destino);
                } else if (operacao.publicado) {
                    throw new ArmazenamentoException("Destino mudou após publicação; pendência preservada");
                }
            }
            limpar(operacao);
        } catch (IOException ex) {
            throw new ArmazenamentoException("Não foi possível compensar a gravação; pendência preservada", ex);
        } finally {
            fechar(operacao);
        }
    }

    /** Após commit confirmado, remove somente os artefatos temporários. */
    public void confirmar(OperacaoArquivo operacao) {
        try {
            if (!confere(operacao.destino, operacao.hash) ||
                    (Files.exists(operacao.temporario, LinkOption.NOFOLLOW_LINKS) && !confere(operacao.temporario, operacao.hash))) {
                throw new ArmazenamentoException("Publicação divergente; pendência preservada para reconciliação");
            }
            limpar(operacao);
        } catch (IOException ex) {
            throw new ArmazenamentoException("Não foi possível limpar a operação confirmada; pendência preservada", ex);
        } finally {
            fechar(operacao);
        }
    }

    private void limpar(OperacaoArquivo operacao) throws IOException {
        try {
            Files.deleteIfExists(operacao.temporario);
            Files.deleteIfExists(operacao.diretorio.resolve("operacao.properties"));
        } finally {
            operacao.close();
        }
        Files.deleteIfExists(operacao.diretorio.resolve("operacao.lock"));
        Files.deleteIfExists(operacao.diretorio);
    }

    public void fechar(OperacaoArquivo operacao) {
        try {
            operacao.close();
        } catch (IOException ex) {
            throw new ArmazenamentoException("Não foi possível liberar a operação", ex);
        }
    }

    boolean confere(Path caminho, String hash) throws IOException {
        if (!Files.isRegularFile(caminho, LinkOption.NOFOLLOW_LINKS)) return false;
        try (var entrada = Files.newInputStream(caminho)) {
            return hash.equals(ConteudoXml.sha256(ConteudoXml.ler(entrada)));
        } catch (ArquivoInvalidoException ex) {
            return false;
        }
    }

    List<Path> listarPendentes() throws IOException {
        Path pendentes = pendentes();
        if (!Files.exists(pendentes, LinkOption.NOFOLLOW_LINKS)) return List.of();
        validarPasta(pendentes);
        try (var entradas = Files.list(pendentes)) {
            return entradas.sorted().toList();
        }
    }

    Optional<OperacaoArquivo> reabrir(Path diretorio) throws IOException {
        if (!diretorio.getFileName().toString().matches("op-[0-9a-f-]{36}") ||
                !Files.isDirectory(diretorio, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Diretório de operação inválido");
        }
        var canal = FileChannel.open(diretorio.resolve("operacao.lock"), StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        boolean transferida = false;
        try {
            java.nio.channels.FileLock trava;
            try {
                trava = canal.tryLock();
            } catch (OverlappingFileLockException ex) {
                return Optional.empty();
            }
            if (trava == null) return Optional.empty();
            Properties metadados = new Properties();
            try (var entrada = Files.newInputStream(diretorio.resolve("operacao.properties"), LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = entrada.readNBytes(8193);
                if (bytes.length > 8192) throw new IOException("Metadados excedem o limite");
                metadados.load(new ByteArrayInputStream(bytes));
            }
            String numero = metadados.getProperty("numero", "");
            String chave = metadados.getProperty("chave", "");
            String hash = metadados.getProperty("sha256", "");
            if (!"1".equals(metadados.getProperty("versao")) || !numero.matches("[0-9]{1,9}") ||
                    !chave.matches("[0-9]{44}") || !hash.matches("[0-9a-f]{64}") ||
                    !(chave + ".xml").equals(metadados.getProperty("nome"))) {
                throw new IOException("Metadados inválidos");
            }
            var operacao = new OperacaoArquivo(diretorio, destino(numero, chave + ".xml"), numero, chave, hash);
            operacao.travar(canal, trava);
            transferida = true;
            return Optional.of(operacao);
        } finally {
            if (!transferida) canal.close();
        }
    }

    private Path pendentes() { return raizViagens.resolve(".pendentes"); }

    private Path destino(String numero, String nome) throws IOException {
        validarNumero(numero);
        Path pasta = raizViagens.resolve(numero);
        Path destino = pasta.resolve(nome).normalize();
        if (!destino.getParent().equals(pasta)) throw new ArquivoInvalidoException("Nome de arquivo inválido");
        if (Files.exists(pasta, LinkOption.NOFOLLOW_LINKS)) validarPasta(pasta);
        return destino;
    }

    private void validarNumero(String numero) {
        if (numero == null || !numero.matches("[A-Za-z0-9_-]+")) {
            throw new ArquivoInvalidoException("Identificador válido da viagem é obrigatório antes de salvar o XML");
        }
    }

    private void validarPasta(Path pasta) throws IOException {
        if (!Files.isDirectory(pasta, LinkOption.NOFOLLOW_LINKS) ||
                !pasta.toRealPath().startsWith(raizViagens.toRealPath())) {
            throw new IOException("Diretório fora da raiz ou link simbólico");
        }
    }

    /** Uso sem banco, mantido para chamadas antigas. O importador usa o coordenador transacional. */
    public Path organizarXml(byte[] conteudo, String numero, String chave) {
        var operacao = prepararXml(conteudo, numero, chave);
        return publicarSemBanco(operacao);
    }

    public Path organizarXml(MultipartFile arquivo, String numero) {
        validarNumero(numero);
        String nome = Path.of(arquivo.getOriginalFilename() == null ? "documento.xml" : arquivo.getOriginalFilename()).getFileName().toString();
        if (!nome.toLowerCase(Locale.ROOT).endsWith(".xml")) throw new ArquivoInvalidoException("A entrada principal deve ser um arquivo XML");
        try (var entrada = arquivo.getInputStream()) {
            return publicarSemBanco(preparar(ConteudoXml.ler(entrada), numero, null, nome));
        } catch (IOException ex) {
            throw new ArmazenamentoException("Não foi possível ler o XML", ex);
        }
    }

    private Path publicarSemBanco(OperacaoArquivo operacao) {
        try {
            publicar(operacao);
            confirmar(operacao);
            return operacao.destino;
        } catch (RuntimeException ex) {
            // Após publicar, uma falha de limpeza preserva o arquivo e seu journal.
            if (!operacao.publicado) {
                try { desfazer(operacao); } catch (RuntimeException limpeza) { ex.addSuppressed(limpeza); }
            } else {
                fechar(operacao);
            }
            throw ex;
        }
    }
}
