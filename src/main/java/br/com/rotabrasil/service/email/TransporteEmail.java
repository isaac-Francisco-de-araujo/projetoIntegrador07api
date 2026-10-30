package br.com.rotabrasil.service.email;

/** Executar somente depois do commit da tentativa; não deve repetir automaticamente. */
public interface TransporteEmail {
    Resultado enviar(MensagemPreparada mensagem);
    default Resultado enviarNotificacao(MensagemNotificacao mensagem) {
        throw new UnsupportedOperationException("Notificações não habilitadas neste transporte");
    }

    /** ACEITO não comprova entrega ou leitura. DESCONHECIDO exige avaliação humana. */
    enum Resultado { ACEITO, RECUSADO, DESCONHECIDO }
}
