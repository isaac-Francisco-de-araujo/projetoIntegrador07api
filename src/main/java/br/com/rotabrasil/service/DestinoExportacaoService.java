package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.ArquivoInvalidoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** A API recebe apenas o nome de uma subpasta, nunca um caminho físico. */
@Service
public class DestinoExportacaoService {
    private final Path raiz,acervo;
    public DestinoExportacaoService(@Value("${rotabrasil.exportacao.raiz:}") String raiz,
            @Value("${rotabrasil.arquivos.viagens:arquivos/viagens}") String acervo) {
        this.raiz=raiz.isBlank()?null:Path.of(raiz).toAbsolutePath().normalize();
        this.acervo=Path.of(acervo).toAbsolutePath().normalize();
        if(this.raiz!=null&&(this.raiz.startsWith(this.acervo)||this.acervo.startsWith(this.raiz)))
            throw new IllegalStateException("Raiz de exportação deve ser separada do acervo interno");
    }
    public boolean autorizada(){return raiz!=null;}
    public void validarNome(String nome){
        if(nome==null||(!nome.isEmpty()&&(!nome.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,59}")||nome.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])"))))
            throw new ArquivoInvalidoException("Escolha uma subpasta permitida; não informe caminhos, pontos ou nomes reservados");
    }
    public Path resolver(String subpasta)throws IOException{
        validarNome(subpasta);
        if(raiz==null)throw new IOException("RAIZ_NAO_CONFIGURADA");
        // Conferir também os pais da raiz, mesmo quando a escolha é a própria raiz.
        LeituraSeguraArquivos.resolver(raiz,"validacao-raiz");
        if(!Files.isDirectory(raiz,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException("raiz");
        Path pasta=subpasta.isEmpty()?raiz:LeituraSeguraArquivos.resolver(raiz,subpasta);
        if(!Files.isDirectory(pasta,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException("subpasta");
        return pasta;
    }
    public String chave(String subpasta){
        validarNome(subpasta);
        return ConteudoXml.sha256((Objects.toString(raiz,"SEM_RAIZ")+"/"+subpasta).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public List<String> listar(){
        if(raiz==null)return List.of();
        try{
            resolver("");var pastas=new ArrayList<String>();pastas.add("");
            try(var lista=Files.newDirectoryStream(raiz)){
                for(Path p:lista){
                    if(pastas.size()>=101)break;
                    try{String nome=p.getFileName().toString();validarNome(nome);resolver(nome);pastas.add(nome);}catch(IOException|ArquivoInvalidoException ignorada){}
                }
            }
            return pastas.stream().sorted().toList();
        }catch(IOException ex){return List.of();}
    }
    public void criar(String nome)throws IOException{
        validarNome(nome);if(nome.isEmpty())throw new ArquivoInvalidoException("Informe o nome da nova subpasta");
        Path base=resolver("");Files.createDirectory(LeituraSeguraArquivos.resolver(base,nome));
    }
    public String testar(String subpasta){
        Path temporario=null,link=null;
        try{
            Path pasta=resolver(subpasta);
            temporario=Files.createTempFile(pasta,".rotabrasil-teste-",".tmp");
            Files.writeString(temporario,"Teste sintético de exportação RotaBrasil");
            link=temporario.resolveSibling(temporario.getFileName()+".link");Files.createLink(link,temporario);
            if(!Files.isSameFile(link,temporario))throw new IOException("PUBLICACAO_INDISPONIVEL");
            return null;
        }catch(IOException|UnsupportedOperationException|SecurityException ex){return codigo(ex);}
        finally{try{if(link!=null)Files.deleteIfExists(link);if(temporario!=null)Files.deleteIfExists(temporario);}catch(IOException ignorada){}}
    }
    public static String codigo(Exception ex){
        if(ex instanceof AccessDeniedException||ex instanceof SecurityException)return "SEM_PERMISSAO";
        if(ex instanceof NoSuchFileException)return "PASTA_INEXISTENTE";
        if(ex instanceof FileAlreadyExistsException)return "ARQUIVO_EXISTENTE";
        if(ex instanceof FileSystemException f){String motivo=Objects.toString(f.getReason(),"").toLowerCase(Locale.ROOT);if(motivo.contains("space")||motivo.contains("espaço")||motivo.contains("disk full"))return "SEM_ESPACO";}
        return "DESTINO_INDISPONIVEL";
    }
}
