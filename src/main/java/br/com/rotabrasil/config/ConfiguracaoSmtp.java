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
            @Value("${rotabrasil.email.smtp.timeout-ms:10000}") int timeout,
            @Value("${rotabrasil.email.smtp.autenticacao:BASIC}") String autenticacao,
            @Value("${rotabrasil.email.smtp.oauth2.token-arquivo:}") String tokenArquivo){
        return criarSender(ativado,seguranca,host,porta,usuario,senha,remetente,banco,timeout,autenticacao,tokenArquivo);
    }
    public static JavaMailSenderImpl criarSender(boolean ativado,boolean seguranca,String host,int porta,String usuario,String senha,String remetente,String banco,int timeout,String autenticacao,String tokenArquivo){
        boolean oauth="XOAUTH2".equals(autenticacao);
        if(!ativado||!seguranca||host.isBlank()||usuario.isBlank()||(!oauth&&senha.isBlank())||!java.util.Set.of("BASIC","XOAUTH2").contains(autenticacao)||porta<1||porta>65535||timeout<1000||timeout>30000)
            throw new IllegalStateException("SMTP real exige ativação explícita, segurança ativa, configuração externa completa e timeout válido");
        if(banco.startsWith("jdbc:h2:file:")&&!java.util.regex.Pattern.compile("(?i)(^|;)WRITE_DELAY\\s*=\\s*0(;|$)").matcher(banco).find())throw new IllegalStateException("SMTP real com H2 em arquivo exige WRITE_DELAY=0 na URL configurada");
        try{br.com.rotabrasil.service.ValidacaoDocumental.validarEnderecoUnico(remetente);}catch(Exception ex){throw new IllegalStateException("Remetente SMTP inválido");}
        if(oauth)br.com.rotabrasil.service.email.TokenSmtpExterno.ler(tokenArquivo);
        JavaMailSenderImpl sender=oauth?new JavaMailSenderImpl(){@Override public String getPassword(){return br.com.rotabrasil.service.email.TokenSmtpExterno.ler(tokenArquivo);}}:new JavaMailSenderImpl();
        sender.setHost(host);sender.setPort(porta);sender.setUsername(usuario);if(!oauth)sender.setPassword(senha);sender.setDefaultEncoding("UTF-8");
        var p=new Properties();p.setProperty("mail.smtp.auth","true");p.setProperty("mail.smtp.starttls.enable","true");p.setProperty("mail.smtp.starttls.required","true");
        p.setProperty("mail.smtp.ssl.checkserveridentity","true");p.setProperty("mail.smtp.connectiontimeout",String.valueOf(timeout));p.setProperty("mail.smtp.timeout",String.valueOf(timeout));p.setProperty("mail.smtp.writetimeout",String.valueOf(timeout));p.setProperty("mail.debug","false");
        if(oauth){p.setProperty("mail.smtp.auth.mechanisms","XOAUTH2");p.setProperty("mail.smtp.auth.login.disable","true");p.setProperty("mail.smtp.auth.plain.disable","true");p.setProperty("mail.smtp.auth.xoauth2.disable","false");}
        sender.setJavaMailProperties(p);return sender;
    }
}
