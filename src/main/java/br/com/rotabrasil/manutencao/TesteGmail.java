package br.com.rotabrasil.manutencao;

import br.com.rotabrasil.service.TesteGmailService;
import jakarta.mail.MessagingException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.io.PrintStream;
import java.util.Map;
import java.util.Properties;

/** CLI sem Spring/JPA: só --enviar realiza uma tentativa manual. Não habilita envio operacional. */
public final class TesteGmail {
    private TesteGmail() {}
    public static void main(String[] args) {
        System.exit(executar(args,System.getenv(),System.out,System.err));
    }
    public static int executar(String[] args,Map<String,String> ambiente,PrintStream saida,PrintStream erro) {
        if(args.length!=1 || !(args[0].equals("--verificar") || args[0].equals("--enviar"))) {
            erro.println("Uso: TesteGmail --verificar | --enviar (uma tentativa, XML sintético)");return 2;
        }
        try {
            var transporte=configurar(ambiente);
            if(args[0].equals("--verificar")) {
                saida.println("Variáveis presentes e endereços válidos. SMTP, autenticação e entrega ainda não foram testados.");return 0;
            }
            new TesteGmailService(transporte).enviar(transporte.getUsername(),ambiente.get("ROTABRASIL_GMAIL_DESTINATARIO"));
            saida.println("Servidor SMTP aceitou a mensagem sintética. Confirme o recebimento; aceite não comprova entrega.");return 0;
        } catch(IllegalArgumentException ex) {
            erro.println("Configuração incompleta ou inválida: confira as três variáveis ROTABRASIL_GMAIL_REMETENTE, ROTABRASIL_GMAIL_DESTINATARIO e ROTABRASIL_GMAIL_APP_PASSWORD nas configurações seguras.");return 2;
        } catch(MailException | MessagingException ex) {
            // A exceção SMTP pode conter credenciais, destinatários ou respostas privadas do servidor.
            erro.println("Envio não confirmado. Confira conectividade, autenticação e a caixa de destino antes de repetir; não houve repetição automática.");return 1;
        }
    }
    public static JavaMailSenderImpl configurar(Map<String,String> ambiente) {
        String remetente=obrigatorio(ambiente,"ROTABRASIL_GMAIL_REMETENTE");
        String destinatario=obrigatorio(ambiente,"ROTABRASIL_GMAIL_DESTINATARIO");
        String senha=obrigatorio(ambiente,"ROTABRASIL_GMAIL_APP_PASSWORD");
        try {
            TesteGmailService.validarEndereco(remetente);TesteGmailService.validarEndereco(destinatario);
            if(!remetente.toLowerCase(java.util.Locale.ROOT).endsWith("@gmail.com"))throw new IllegalArgumentException();
        } catch(MessagingException ex) { throw new IllegalArgumentException("Endereços inválidos"); }
        var transporte=new JavaMailSenderImpl();
        transporte.setHost("smtp.gmail.com");transporte.setPort(587);transporte.setProtocol("smtp");
        transporte.setUsername(remetente);transporte.setPassword(senha);transporte.setDefaultEncoding("UTF-8");
        var props=new Properties();
        props.setProperty("mail.smtp.auth","true");props.setProperty("mail.smtp.starttls.enable","true");
        props.setProperty("mail.smtp.starttls.required","true");props.setProperty("mail.smtp.ssl.checkserveridentity","true");
        props.setProperty("mail.smtp.connectiontimeout","10000");props.setProperty("mail.smtp.timeout","10000");
        props.setProperty("mail.smtp.writetimeout","10000");props.setProperty("mail.debug","false");
        transporte.setJavaMailProperties(props);return transporte;
    }
    private static String obrigatorio(Map<String,String> ambiente,String nome) {
        String valor=ambiente.get(nome);if(valor==null || valor.isBlank())throw new IllegalArgumentException("Variável ausente");return valor;
    }
}
