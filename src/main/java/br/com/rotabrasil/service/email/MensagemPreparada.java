package br.com.rotabrasil.service.email;

import java.util.List;

/** Snapshot privado em memória. Nunca retornar os bytes em DTOs HTTP ou logs. */
public record MensagemPreparada(Long viagemId, String destinatario, List<Arquivo> arquivos) {
    public MensagemPreparada { arquivos = List.copyOf(arquivos); }
    public String assunto(){return "Documentos de viagem - referência #"+viagemId;}
    public String texto(){return "Documentos selecionados e conferidos manualmente para a viagem de referência #"+viagemId+".";}

    public record Arquivo(String origem, Long id, String nome, String sha256, byte[] conteudo) {
        public Arquivo { conteudo = conteudo.clone(); }
        @Override public byte[] conteudo() { return conteudo.clone(); }
        public int tamanho() { return conteudo.length; }
        @Override public String toString() { return "Arquivo[origem=" + origem + ", id=" + id + "]"; }
    }

    @Override public String toString() { return "MensagemPreparada[viagemId=" + viagemId + ", quantidade=" + arquivos.size() + "]"; }
}
