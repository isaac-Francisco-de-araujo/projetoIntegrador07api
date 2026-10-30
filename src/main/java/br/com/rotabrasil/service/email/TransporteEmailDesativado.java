package br.com.rotabrasil.service.email;

/** Não há propriedade para habilitar SMTP operacional nesta entrega. */
public class TransporteEmailDesativado implements TransporteEmail {
    @Override public Resultado enviar(MensagemPreparada mensagem) {
        throw new IllegalStateException("Transporte operacional de e-mail desativado");
    }
}
