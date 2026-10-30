package br.com.rotabrasil.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import org.springframework.stereotype.Component;

/** Staging próprio como testemunha de publicação; createLink jamais substitui o destino. */
@Component
public class PublicadorExportacao {
    public void publicar(Path destino,Path temporario,byte[] bytes)throws IOException{
        Files.createDirectories(destino.getParent());Files.createDirectories(temporario.getParent());
        if(Files.exists(temporario,LinkOption.NOFOLLOW_LINKS)){
            if(!Files.isRegularFile(temporario,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(temporario))throw new IOException("Staging inválido");
            // Nome exclusivo persistido no plano. Uma cópia incompleta própria pode ser refeita.
            Files.delete(temporario);
        }
        try(var canal=FileChannel.open(temporario,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())canal.write(buffer);canal.force(true);
        }
        Files.createLink(destino,temporario);
    }
}
