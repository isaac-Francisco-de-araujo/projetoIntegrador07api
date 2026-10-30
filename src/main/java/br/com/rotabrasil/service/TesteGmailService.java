package br.com.rotabrasil.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import java.nio.charset.StandardCharsets;

/** Teste manual isolado: anexo fixo sintético, sem acesso a banco/acervo ou reenvio automático. */
@RequiredArgsConstructor
public final class TesteGmailService {
    private final JavaMailSender transporte;

    public void enviar(String remetente, String destinatario) throws MessagingException {
        validarEndereco(remetente);validarEndereco(destinatario);
        var mensagem=transporte.createMimeMessage();
        var helper=new MimeMessageHelper(mensagem,true,StandardCharsets.UTF_8.name());
        helper.setFrom(remetente);helper.setTo(destinatario);
        helper.setSubject("RotaBrasil - TESTE DE E-MAIL - documento sintético");
        helper.setText("Mensagem de teste do RotaBrasil. O XML anexo é sintético e não possui valor fiscal. " +
                "Este teste não envia documentos reais nem confirma entrega de documentos operacionais.",false);
        helper.addAttachment("cte-sintetico.xml",new ClassPathResource("email/cte-sintetico.xml"),"application/xml");
        transporte.send(mensagem);
    }

    public static void validarEndereco(String endereco) throws MessagingException {
        ValidacaoDocumental.validarEnderecoUnico(endereco);
    }
}
