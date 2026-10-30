package br.com.rotabrasil.service.email;

/** Aviso de metadados: sem anexos e separado do envio documental ao cliente. */
public record MensagemNotificacao(String remetente,String destinatario,String assunto,String texto) {
    @Override public String toString(){return "MensagemNotificacao[metadados, sem anexos]";}
}
