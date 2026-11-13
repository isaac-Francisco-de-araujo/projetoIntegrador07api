package br.com.rotabrasil.manutencao;
import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
/** CLI sem banco/rede; lê senha pelo console sem eco. Nunca aceita senha como argumento. */
public final class GerarHashSenha {
    public static void main(String[] args){
        var console=System.console();if(args.length!=0||console==null)throw new IllegalStateException("Execute em terminal interativo, sem argumentos");
        char[] senha=console.readPassword("Nova senha (12 a 72 bytes UTF-8): ");
        try{String texto=new String(senha);int bytes=texto.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if(texto.length()<12||bytes>72)throw new IllegalArgumentException("Tamanho inválido");
            console.printf("Hash BCrypt para configuração protegida:%n%s%n",new BCryptPasswordEncoder(12).encode(texto));
        }finally{Arrays.fill(senha,'\0');}
    }
}
