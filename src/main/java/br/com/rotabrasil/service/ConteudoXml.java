package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.ArquivoInvalidoException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Os mesmos bytes são usados na extração, no hash e no armazenamento. */
public final class ConteudoXml {
    public static final int LIMITE_BYTES = 20 * 1024 * 1024;

    private ConteudoXml() {}

    public static byte[] ler(InputStream entrada) throws IOException {
        byte[] conteudo = entrada.readNBytes(LIMITE_BYTES + 1);
        if (conteudo.length == 0 || conteudo.length > LIMITE_BYTES) {
            throw new ArquivoInvalidoException("XML vazio ou excede o limite de 20 MB");
        }
        return conteudo;
    }

    public static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", ex);
        }
    }
}
