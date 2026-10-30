package br.com.rotabrasil.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class ArquivamentoPacoteDtos {
    private ArquivamentoPacoteDtos() {}
    public record Resumo(Long id,String nomeArquivo,String sha256,OffsetDateTime criadoEm,
                         int documentos,int documentosAtivos,int anexos,int anexosAtivos,boolean arquivado) {}
    public record Viagem(Long id,String numero,String cliente) {}
    public record Documento(Long id,String nomeArquivo,String tipo,String sha256,OffsetDateTime arquivadoEm,
                            Viagem viagem,List<Long> outrosPacoteIds) {}
    public record Anexo(Long id,String nomeArquivo,String sha256,OffsetDateTime arquivadoEm,List<Viagem> viagens) {}
    public record Conferencia(Resumo pacote,List<Documento> documentos,List<Anexo> anexos,String conferenciaSha256) {}
}
