package br.com.rotabrasil.dto;

import br.com.rotabrasil.model.Documento;
import java.time.OffsetDateTime;

public record DocumentoArquivadoResponse(Long id, String nomeArquivo, String tipo, String chaveAcesso,
        String sha256, Long viagemId, OffsetDateTime arquivadoEm) {
    public static DocumentoArquivadoResponse de(Documento documento) {
        return new DocumentoArquivadoResponse(documento.getId(), documento.getNomeArquivo(), documento.getTipo(),
                documento.getChaveAcesso(), documento.getSha256(), documento.getViagem().getId(), documento.getArquivadoEm());
    }
}
