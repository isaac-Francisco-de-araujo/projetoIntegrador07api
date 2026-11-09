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
    @Override public Resultado enviarNotificacao(MensagemNotificacao mensagem){
        if(!remetente.equalsIgnoreCase(mensagem.remetente()))return Resultado.RECUSADO;
        jakarta.mail.internet.MimeMessage mime;
        try{
            br.com.rotabrasil.service.ValidacaoDocumental.validarEnderecoUnico(mensagem.destinatario());
            mime=sender.createMimeMessage();var helper=new MimeMessageHelper(mime,false,"UTF-8");
            helper.setFrom(remetente);helper.setTo(mensagem.destinatario());helper.setSubject(mensagem.assunto());helper.setText(mensagem.texto(),false);
        }catch(MessagingException|MailParseException ex){return Resultado.RECUSADO;}
        return publicar(mime);
    }
    @Override public Resultado enviar(MensagemPreparada mensagem){
        jakarta.mail.internet.MimeMessage mime;
        try{
            mime=sender.createMimeMessage();var helper=new MimeMessageHelper(mime,true,"UTF-8");
            helper.setFrom(remetente);helper.setTo(mensagem.destinatario());helper.setSubject(mensagem.assunto());
            helper.setText(mensagem.texto(),false);
            for(var a:mensagem.arquivos())helper.addAttachment(a.nome(),new ByteArrayResource(a.conteudo()),a.nome().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")?"application/pdf":a.nome().toLowerCase(java.util.Locale.ROOT).endsWith(".xml")?"application/xml":"application/octet-stream");
        }catch(MessagingException|MailParseException ex){return Resultado.RECUSADO;}
        return publicar(mime);
    }
    private Resultado publicar(jakarta.mail.internet.MimeMessage mime){
        try{sender.send(mime);return Resultado.ACEITO;}
        catch(MailAuthenticationException ex){return Resultado.RECUSADO;}
        catch(MailException ex){return recusaComprovada(ex)?Resultado.RECUSADO:Resultado.DESCONHECIDO;}
    }
    private static boolean recusaComprovada(Throwable ex){
        if(ex instanceof MailSendException envio&&!envio.getFailedMessages().isEmpty())return envio.getFailedMessages().values().stream().allMatch(TransporteSmtp::recusaComprovada);
        if(ex instanceof jakarta.mail.SendFailedException envio&&envio.getValidSentAddresses()!=null&&envio.getValidSentAddresses().length>0)return false;
        if(ex instanceof org.eclipse.angus.mail.smtp.SMTPSendFailedException smtp){
            String cmd=java.util.Objects.toString(smtp.getCommand(),"").toUpperCase(java.util.Locale.ROOT);
            // Recusa de envelope/DATA é conhecida. Falha posterior (por exemplo QUIT)
            // não permite concluir que uma mensagem já aceita deixou de ser enviada.
            return (cmd.startsWith("MAIL FROM")||cmd.startsWith("RCPT TO")||cmd.equals("DATA")||cmd.equals("."))&&smtp.getReturnCode()>=400&&smtp.getReturnCode()<600;
        }
        if(ex instanceof org.eclipse.angus.mail.smtp.SMTPAddressFailedException smtp)return smtp.getReturnCode()>=400&&smtp.getReturnCode()<600;
        return ex.getCause()!=null&&ex.getCause()!=ex&&recusaComprovada(ex.getCause());
    }
}
