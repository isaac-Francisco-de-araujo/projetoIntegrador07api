package br.com.rotabrasil.service.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;

/** Token renovado por ferramenta externa de identidade; nunca aparece em DTO, log ou argumento. */
public final class TokenSmtpExterno {
    private TokenSmtpExterno(){}
    public record Estado(String situacao,String autorizacao,String renovacao,Instant expiraEm){}
    private record Carga(JsonNode documento,Estado estado){@Override public String toString(){return "CargaCredencial[protegida]";}}
    public static Estado estado(String arquivo){return analisar(arquivo).estado();}
    public static String ler(String arquivo){
        var carga=analisar(arquivo);
        if(!carga.estado().situacao().equals("DISPONIVEL"))throw new IllegalStateException("Credencial OAuth2 externa ausente, inválida ou expirada");
        return carga.documento().path("access_token").asText();
    }
    private static Carga analisar(String arquivo){
        String renovacao="EXTERNA_NAO_INFORMADA";
        try{
            if(arquivo==null||arquivo.isBlank())return indisponivel("AUSENTE",renovacao,null);
            Path p=Path.of(arquivo);
            if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return indisponivel("AUSENTE",renovacao,null);
            if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException();
            byte[] bytes;try(var in=Files.newInputStream(p,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){bytes=in.readNBytes(32769);}
            if(bytes.length>32768)throw new IllegalArgumentException();
            var j=new ObjectMapper().readTree(bytes);
            boolean gerenciada=j.path("renewal_managed").asBoolean(false);if(gerenciada)renovacao="EXTERNA_GERENCIADA";
            String autorizacao=j.path("authorization_state").asText("");
            if(java.util.Set.of("RECONEXAO_NECESSARIA","AUTORIZACAO_PENDENTE","INDISPONIVEL").contains(autorizacao))return indisponivel(autorizacao,renovacao,null);
            if(!autorizacao.isEmpty()&&!autorizacao.equals("AUTORIZADA"))throw new IllegalArgumentException();
            String token=j.path("access_token").asText();
            if(token.isBlank()||token.length()>24576||token.chars().anyMatch(Character::isWhitespace))throw new IllegalArgumentException();
            Instant expira=Instant.parse(j.path("expires_at").asText());
            if(!expira.isAfter(Instant.now().plusSeconds(60)))return indisponivel("EXPIRADA",renovacao,expira);
            return new Carga(j,new Estado("DISPONIVEL",gerenciada&&autorizacao.equals("AUTORIZADA")?"REGISTRADA_EXTERNAMENTE":"NAO_VERIFICADA",renovacao,expira));
        }catch(Exception ex){return indisponivel("INVALIDA",renovacao,null);}
    }
    private static Carga indisponivel(String situacao,String renovacao,Instant expira){return new Carga(null,new Estado(situacao,situacao,renovacao,expira));}
}
