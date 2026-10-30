package br.com.rotabrasil.service.email;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.*;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Component;

/** Adaptador operacional independente da CLI Gmail. Não repete chamadas. */
@Component @ConditionalOnProperty(name="rotabrasil.email.modo",havingValue="REAL")
public class TransporteSmtp implements TransporteEmail {
    private final JavaMailSender sender;private final String remetente;
    public TransporteSmtp(@Qualifier("rotaSmtpSender") JavaMailSender sender,@Value("${rotabrasil.email.smtp.remetente}") String remetente){this.sender=sender;this.remetente=remetente;}
    @Override public Resultado enviar(MensagemPreparada mensagem){
        jakarta.mail.internet.MimeMessage mime;
        try{
            mime=sender.createMimeMessage();var helper=new MimeMessageHelper(mime,true,"UTF-8");
            helper.setFrom(remetente);helper.setTo(mensagem.destinatario());helper.setSubject("Documentos de viagem - referência #"+mensagem.viagemId());
            helper.setText("Documentos selecionados e conferidos manualmente para a viagem de referência #"+mensagem.viagemId()+".",false);
            for(var a:mensagem.arquivos())helper.addAttachment(a.nome(),new ByteArrayResource(a.conteudo()),a.nome().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")?"application/pdf":a.nome().toLowerCase(java.util.Locale.ROOT).endsWith(".xml")?"application/xml":"application/octet-stream");
        }catch(MessagingException|MailParseException ex){return Resultado.RECUSADO;}
        try{sender.send(mime);return Resultado.ACEITO;}
        catch(MailAuthenticationException ex){return Resultado.RECUSADO;}
        catch(MailException ex){return Resultado.DESCONHECIDO;}
    }
}
