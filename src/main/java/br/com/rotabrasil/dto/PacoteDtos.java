package br.com.rotabrasil.dto;

import br.com.rotabrasil.model.FinalidadeAnexo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.UniqueElements;
import java.util.List;

public final class PacoteDtos {
    private PacoteDtos(){}
    public record Arquivo(String id,String nomeArquivo,String tipo,long tamanho,String sha256,String numero,String chaveAcesso,String cliente,String cnpj){}
    public record Inspecao(String sha256,List<Arquivo> arquivos){}
    public record Vinculo(@NotBlank String arquivoId,@NotNull FinalidadeAnexo finalidade,@NotEmpty @Size(max=100) @UniqueElements List<@NotBlank String> xmlIds){}
    public record Confirmacao(@NotBlank @Pattern(regexp="[0-9a-f]{64}") String sha256,@NotNull @Size(max=100) List<@Valid Vinculo> vinculos){}
    public record Resultado(Long pacoteId,boolean reutilizado,List<DocumentoResponse> documentos,List<AnexoResponse> anexos){}
}
