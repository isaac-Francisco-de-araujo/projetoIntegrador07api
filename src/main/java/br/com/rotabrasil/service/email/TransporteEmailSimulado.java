package br.com.rotabrasil.service.email;

import org.springframework.stereotype.Component;

/** Sem rede, credenciais ou efeitos externos. Não representa envio real. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="rotabrasil.email.modo",havingValue="SIMULADO",matchIfMissing=true)
public class TransporteEmailSimulado implements TransporteEmail {
    @Override public Resultado enviar(MensagemPreparada mensagem) { return Resultado.ACEITO; }
    @Override public Resultado enviarNotificacao(MensagemNotificacao mensagem) { return Resultado.ACEITO; }
}
