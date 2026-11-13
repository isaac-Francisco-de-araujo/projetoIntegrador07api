package br.com.rotabrasil.dto;

import br.com.rotabrasil.model.Documento;

/** Metadados públicos: o caminho físico pertence ao armazenamento interno. */
public record DocumentoResponse(Long id, String nomeArquivo, String tipo, String chaveAcesso, String sha256, Long viagemId) {
    public static DocumentoResponse de(Documento documento) {
        return new DocumentoResponse(documento.getId(), documento.getNomeArquivo(), documento.getTipo(),
                documento.getChaveAcesso(), documento.getSha256(), documento.getViagem().getId());
    }
}
