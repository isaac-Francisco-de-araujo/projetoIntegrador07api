package br.com.rotabrasil.service;

import java.io.IOException;
import java.nio.file.*;

/** Leitura comum de caminhos legados absolutos e novos caminhos relativos à raiz. */
public final class LeituraSeguraArquivos {
    private LeituraSeguraArquivos() {}
    public static Path resolver(Path raiz,String registrado) throws IOException {
        raiz=raiz.toAbsolutePath().normalize();
        if(registrado==null||registrado.isBlank())throw new IOException("Caminho indisponível");
        Path recebido=Path.of(registrado);
        Path arquivo=(recebido.isAbsolute()?recebido:raiz.resolve(recebido)).toAbsolutePath().normalize();
        if(!arquivo.startsWith(raiz)||arquivo.equals(raiz))throw new IOException("Caminho não permitido");
        for(Path atual=arquivo;atual!=null;atual=atual.getParent())if(Files.isSymbolicLink(atual))throw new IOException("Link não permitido");
        if(Files.exists(raiz,LinkOption.NOFOLLOW_LINKS)){
            Path existente=arquivo;while(existente!=null&&!Files.exists(existente,LinkOption.NOFOLLOW_LINKS))existente=existente.getParent();
            if(existente==null||!existente.toRealPath().startsWith(raiz.toRealPath()))throw new IOException("Localização física não permitida");
        }
        return arquivo;
    }
    public static byte[] ler(Path raiz,String registrado,int limite) throws IOException {
        if(limite<1||limite>512*1024*1024)throw new IOException("Limite inválido");
        Path arquivo=resolver(raiz,registrado);
        if(!Files.isRegularFile(arquivo,LinkOption.NOFOLLOW_LINKS))throw new IOException("Arquivo indisponível");
        byte[] bytes;try(var in=Files.newInputStream(arquivo,LinkOption.NOFOLLOW_LINKS)){bytes=in.readNBytes(limite+1);}
        if(bytes.length==0||bytes.length>limite)throw new IOException("Tamanho inválido");
        return bytes;
    }
}
