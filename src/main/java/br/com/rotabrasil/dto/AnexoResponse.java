package br.com.rotabrasil.dto;
import br.com.rotabrasil.model.*;
import java.time.OffsetDateTime;
import java.util.List;
public record AnexoResponse(Long id,String nomeArquivo,String tipo,FinalidadeAnexo finalidade,String sha256,List<Long> viagemIds,OffsetDateTime arquivadoEm) {
    public static AnexoResponse de(Anexo a){return new AnexoResponse(a.getId(),a.getNomeArquivo(),"PDF",a.getFinalidade(),a.getSha256(),a.getViagens().stream().map(Viagem::getId).sorted().toList(),a.getArquivadoEm());}
}
