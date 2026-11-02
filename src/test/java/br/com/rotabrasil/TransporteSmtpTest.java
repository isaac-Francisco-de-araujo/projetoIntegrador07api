package br.com.rotabrasil;
import br.com.rotabrasil.config.ConfiguracaoSmtp;
import br.com.rotabrasil.service.email.*;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.*;
import org.springframework.mail.javamail.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class TransporteSmtpTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp;
    @Test void notificacaoUsaMesmoAdaptadorSemAnexosNemTrocaRemetente()throws Exception{
        var s=sender();var t=new TransporteSmtp(s,"operacao@example.invalid");
        assertThat(t.enviarNotificacao(new MensagemNotificacao("operacao@example.invalid","motorista@example.invalid","Recebimento sintético","Há pendências em conferência."))).isEqualTo(TransporteEmail.Resultado.ACEITO);
        var captor=org.mockito.ArgumentCaptor.forClass(MimeMessage.class);verify(s).send(captor.capture());var m=captor.getValue();assertThat(m.getAllRecipients()).hasSize(1);assertThat(m.getContent()).isEqualTo("Há pendências em conferência.");assertThat(m.isMimeType("multipart/*")).isFalse();
        assertThat(t.enviarNotificacao(new MensagemNotificacao("outra@example.invalid","motorista@example.invalid","Teste","Teste"))).isEqualTo(TransporteEmail.Resultado.RECUSADO);verify(s,times(1)).send(any(MimeMessage.class));
    }
    @Test void oauth2UsaTokenExternoAtualizadoSemFallbackBasic()throws Exception{
        var arquivo=tmp.resolve("token.json");java.nio.file.Files.writeString(arquivo,"{\"access_token\":\"SINTETICO_UM\",\"expires_at\":\""+java.time.Instant.now().plusSeconds(3600)+"\"}");
        configuracao().withPropertyValues("rotabrasil.email.smtp.ativado=true","rotabrasil.email.smtp.senha=","rotabrasil.email.smtp.autenticacao=XOAUTH2","rotabrasil.email.smtp.oauth2.token-arquivo="+arquivo).run(c->{assertThat(c).hasNotFailed();var s=c.getBean(JavaMailSenderImpl.class);assertThat(s.getPassword()).isEqualTo("SINTETICO_UM");assertThat(s.getJavaMailProperties().getProperty("mail.smtp.auth.mechanisms")).isEqualTo("XOAUTH2");assertThat(s.getJavaMailProperties().getProperty("mail.smtp.auth.plain.disable")).isEqualTo("true");});
        java.nio.file.Files.writeString(arquivo,"{\"access_token\":\"SINTETICO_DOIS\",\"expires_at\":\"2000-01-01T00:00:00Z\"}");assertThatThrownBy(()->TokenSmtpExterno.ler(arquivo.toString())).hasMessage("Credencial OAuth2 externa ausente, inválida ou expirada");
    }
    @Test void recusaSmtpExplicitaEConhecidaETimeoutPermaneceDesconhecido()throws Exception{
        var s=sender();var rejeicao=new org.eclipse.angus.mail.smtp.SMTPSendFailedException("DATA",550,"RESPOSTA_PRIVADA",null,null,null,new jakarta.mail.Address[]{new jakarta.mail.internet.InternetAddress("teste@example.invalid")});
        doThrow(new MailSendException(Map.of(new Object(),rejeicao))).when(s).send(any(MimeMessage.class));var t=new TransporteSmtp(s,"operacao@example.invalid");
        assertThat(t.enviarNotificacao(new MensagemNotificacao("operacao@example.invalid","motorista@example.invalid","Teste","Sintético"))).isEqualTo(TransporteEmail.Resultado.RECUSADO);
        var posterior=new org.eclipse.angus.mail.smtp.SMTPSendFailedException("QUIT",550,"RESPOSTA_PRIVADA",null,null,null,null);doThrow(new MailSendException(Map.of(new Object(),posterior))).when(s).send(any(MimeMessage.class));assertThat(t.enviarNotificacao(new MensagemNotificacao("operacao@example.invalid","motorista@example.invalid","Teste","Sintético"))).isEqualTo(TransporteEmail.Resultado.DESCONHECIDO);
        doThrow(new MailSendException("privado",new java.net.SocketTimeoutException("TOKEN_PRIVADO"))).when(s).send(any(MimeMessage.class));assertThat(t.enviarNotificacao(new MensagemNotificacao("operacao@example.invalid","motorista@example.invalid","Teste","Sintético"))).isEqualTo(TransporteEmail.Resultado.DESCONHECIDO);
    }
    MensagemPreparada mensagem(){return new MensagemPreparada(1L,"teste@example.invalid",List.of(new MensagemPreparada.Arquivo("DOCUMENTO",1L,"sintetico.xml","a".repeat(64),"<sintetico/>".getBytes(java.nio.charset.StandardCharsets.UTF_8))));}
    JavaMailSender sender(){var s=mock(JavaMailSender.class);when(s.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));return s;}
    @Test void compoeMensagemExplicitaSemUsarCliGmailOuRede()throws Exception{
        var s=sender();assertThat(new TransporteSmtp(s,"remetente@example.invalid").enviar(mensagem())).isEqualTo(TransporteEmail.Resultado.ACEITO);
        var captor=org.mockito.ArgumentCaptor.forClass(MimeMessage.class);verify(s,times(1)).send(captor.capture());var m=captor.getValue();
        assertThat(m.getAllRecipients()).hasSize(1);assertThat(m.getAllRecipients()[0].toString()).isEqualTo("teste@example.invalid");
        assertThat(m.getSubject()).contains("referência #1");var partes=(jakarta.mail.Multipart)m.getContent();assertThat(partes.getCount()).isPositive();
    }
    @Test void falhaAutenticacaoEConhecidaMasFalhaDeEnvioGenericaEDesconhecida(){
        var s=sender();doThrow(new MailAuthenticationException("segredo privado")).when(s).send(any(MimeMessage.class));
        assertThat(new TransporteSmtp(s,"remetente@example.invalid").enviar(mensagem())).isEqualTo(TransporteEmail.Resultado.RECUSADO);verify(s,times(1)).send(any(MimeMessage.class));
        s=sender();doThrow(new MailSendException("resposta SMTP privada")).when(s).send(any(MimeMessage.class));
        assertThat(new TransporteSmtp(s,"remetente@example.invalid").enviar(mensagem())).isEqualTo(TransporteEmail.Resultado.DESCONHECIDO);verify(s,times(1)).send(any(MimeMessage.class));
    }
    ApplicationContextRunner configuracao(){return new ApplicationContextRunner().withUserConfiguration(ConfiguracaoSmtp.class).withPropertyValues("rotabrasil.email.modo=REAL","rotabrasil.seguranca.ativa=true","rotabrasil.email.smtp.host=smtp.example.invalid","rotabrasil.email.smtp.usuario=sintetico","rotabrasil.email.smtp.senha=segredo-sintetico","rotabrasil.email.smtp.remetente=teste@example.invalid");}
    @Test void smtpNaoInicializaSemSegundoOptIn(){configuracao().run(c->assertThat(c).hasFailed());}
    @Test void configuraTlsObrigatorioIdentidadeTimeoutSemConectar(){
        configuracao().withPropertyValues("rotabrasil.email.smtp.ativado=true").run(c->{assertThat(c).hasNotFailed();var s=c.getBean(JavaMailSenderImpl.class);var p=s.getJavaMailProperties();
            assertThat(p.getProperty("mail.smtp.starttls.required")).isEqualTo("true");assertThat(p.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");assertThat(p.getProperty("mail.smtp.timeout")).isEqualTo("10000");assertThat(p.getProperty("mail.debug")).isEqualTo("false");
        });
    }
    @Test void modoRealSemSegurancaAtivaFalhaFechado(){configuracao().withPropertyValues("rotabrasil.email.smtp.ativado=true","rotabrasil.seguranca.ativa=false").run(c->assertThat(c).hasFailed());}
    @Test void h2EmArquivoExigeCommitSincronoAntesDeHabilitarSmtp(){configuracao().withPropertyValues("rotabrasil.email.smtp.ativado=true","spring.datasource.url=jdbc:h2:file:isolado").run(c->assertThat(c).hasFailed());}
    @Test void servidorLocalSemStarttlsNaoRecebeAutenticacaoOuMensagem()throws Exception {
        var comandos=java.util.Collections.synchronizedList(new ArrayList<String>());
        try(var server=new java.net.ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress());var pool=java.util.concurrent.Executors.newSingleThreadExecutor()){
            server.setSoTimeout(5000);
            var futuro=pool.submit(()->{try(var socket=server.accept()){
                socket.setSoTimeout(3000);var out=new java.io.PrintWriter(socket.getOutputStream(),true,java.nio.charset.StandardCharsets.US_ASCII);var in=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(),java.nio.charset.StandardCharsets.US_ASCII));out.print("220 teste-local\r\n");out.flush();
                for(int n=0;n<10;n++){String linha=in.readLine();if(linha==null)break;comandos.add(linha);if(linha.startsWith("EHLO")){out.print("250-teste-local\r\n250 OK\r\n");out.flush();}else if(linha.equals("QUIT")){out.print("221 fim\r\n");out.flush();break;}else{out.print("500 nao suportado\r\n");out.flush();}}
            }catch(java.io.IOException ex){/* timeout/conexão encerrada pelo cliente que exige TLS */}});
            configuracao().withPropertyValues("rotabrasil.email.smtp.ativado=true","rotabrasil.email.smtp.host="+server.getInetAddress().getHostAddress(),"rotabrasil.email.smtp.porta="+server.getLocalPort(),"rotabrasil.email.smtp.timeout-ms=1000").run(c->{
                var transporte=new TransporteSmtp(c.getBean(JavaMailSenderImpl.class),"sintetico@example.invalid");assertThat(transporte.enviar(mensagem())).isEqualTo(TransporteEmail.Resultado.DESCONHECIDO);
            });
            futuro.get(6,java.util.concurrent.TimeUnit.SECONDS);assertThat(comandos).anyMatch(s->s.startsWith("EHLO"));assertThat(comandos).noneMatch(s->s.startsWith("AUTH")||s.startsWith("MAIL FROM")||s.startsWith("DATA"));
        }
    }
}
