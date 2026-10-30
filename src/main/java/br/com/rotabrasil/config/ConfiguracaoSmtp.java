package br.com.rotabrasil.config;
import br.com.rotabrasil.service.TesteGmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.util.Properties;

@Configuration @ConditionalOnProperty(name="rotabrasil.email.modo",havingValue="REAL")
public class ConfiguracaoSmtp {
    @Bean("rotaSmtpSender") JavaMailSenderImpl smtp(@Value("${rotabrasil.email.smtp.ativado:false}") boolean ativado,
            @Value("${rotabrasil.seguranca.ativa:false}") boolean seguranca,@Value("${rotabrasil.email.smtp.host:}") String host,
            @Value("${rotabrasil.email.smtp.porta:587}") int porta,@Value("${rotabrasil.email.smtp.usuario:}") String usuario,
            @Value("${rotabrasil.email.smtp.senha:}") String senha,@Value("${rotabrasil.email.smtp.remetente:}") String remetente,
            @Value("${spring.datasource.url:}") String banco,
            @Value("${rotabrasil.email.smtp.timeout-ms:10000}") int timeout){
        if(!ativado||!seguranca||host.isBlank()||usuario.isBlank()||senha.isBlank()||porta<1||porta>65535||timeout<1000||timeout>30000)
            throw new IllegalStateException("SMTP real exige ativação explícita, segurança ativa, configuração externa completa e timeout válido");
        if(banco.startsWith("jdbc:h2:file:")&&!java.util.regex.Pattern.compile("(?i)(^|;)WRITE_DELAY\\s*=\\s*0(;|$)").matcher(banco).find())throw new IllegalStateException("SMTP real com H2 em arquivo exige WRITE_DELAY=0 na URL configurada");
        try{br.com.rotabrasil.service.ValidacaoDocumental.validarEnderecoUnico(remetente);}catch(Exception ex){throw new IllegalStateException("Remetente SMTP inválido");}
        var sender=new JavaMailSenderImpl();sender.setHost(host);sender.setPort(porta);sender.setUsername(usuario);sender.setPassword(senha);sender.setDefaultEncoding("UTF-8");
        var p=new Properties();p.setProperty("mail.smtp.auth","true");p.setProperty("mail.smtp.starttls.enable","true");p.setProperty("mail.smtp.starttls.required","true");
        p.setProperty("mail.smtp.ssl.checkserveridentity","true");p.setProperty("mail.smtp.connectiontimeout",String.valueOf(timeout));p.setProperty("mail.smtp.timeout",String.valueOf(timeout));p.setProperty("mail.smtp.writetimeout",String.valueOf(timeout));p.setProperty("mail.debug","false");
        sender.setJavaMailProperties(p);return sender;
    }
}
