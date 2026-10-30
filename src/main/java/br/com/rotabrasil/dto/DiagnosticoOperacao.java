package br.com.rotabrasil.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Contrato público: somente fatos e metadados seguros, nunca paths físicos ou conteúdo fiscal. */
public record DiagnosticoOperacao(String operacaoId,String correlacaoId,OffsetDateTime dataHora,String versao,
        String origem,Long pacoteId,String pacote,String etapa,String codigo,String gravidade,String mensagem,
        String causaConhecida,String orientacao,String situacao,String estado,String impacto,
        List<Problema> problemas,List<Acao> acoes,Resolucao resolucao,List<Recuperacao> recuperacoes) {
    public record Problema(String arquivoId,String arquivo,String localizacao,String tipoLocalizacao,
            String etapa,String codigo,String gravidade,String mensagem,String causaConhecida,String orientacao) {}
    public record Acao(String codigo,String rotulo,String referencia) {}
    public record Resolucao(String referencia,OffsetDateTime dataHora,String criterio) {}
    public record Recuperacao(String id,String correlacaoId,OffsetDateTime inicio,OffsetDateTime fim,
            String situacao,int concluidos,int pendentes,List<Problema> problemas) {
        public Recuperacao(String id,String correlacaoId,OffsetDateTime inicio,OffsetDateTime fim,String situacao,int concluidos,int pendentes){this(id,correlacaoId,inicio,fim,situacao,concluidos,pendentes,List.of());}
    }
}
