package br.com.rotabrasil;

import br.com.rotabrasil.manutencao.TesteGmail;
import br.com.rotabrasil.service.TesteGmailService;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TesteGmailTest {
    private Map<String,String> ambiente(){return Map.of("ROTABRASIL_GMAIL_REMETENTE","teste@gmail.com","ROTABRASIL_GMAIL_DESTINATARIO","destino@example.invalid","ROTABRASIL_GMAIL_APP_PASSWORD","senha-sintetica");}
    @Test void configuraAutenticacaoTlsObrigatorioVerificacaoDeCertificadoETimeouts(){
        var transporte=TesteGmail.configurar(ambiente());
        assertThat(transporte.getHost()).isEqualTo("smtp.gmail.com");assertThat(transporte.getPort()).isEqualTo(587);
        assertThat(transporte.getUsername()).isEqualTo("teste@gmail.com");
        var props=transporte.getJavaMailProperties();
        for(String chave:List.of("mail.smtp.auth","mail.smtp.starttls.enable","mail.smtp.starttls.required","mail.smtp.ssl.checkserveridentity"))assertThat(props.getProperty(chave)).isEqualTo("true");
        assertThat(props.getProperty("mail.debug")).isEqualTo("false");assertThat(props.containsKey("mail.smtp.ssl.trust")).isFalse();
        for(String chave:List.of("connectiontimeout","timeout","writetimeout"))assertThat(props.getProperty("mail.smtp."+chave)).isEqualTo("10000");
    }
    @Test void mensagemTemUmDestinatarioEUmAnexoFixoSinteticoSemAcervo()throws Exception{
        var transporte=mock(JavaMailSender.class);when(transporte.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        new TesteGmailService(transporte).enviar("teste@gmail.com","destino@example.invalid");
        var captor=ArgumentCaptor.forClass(MimeMessage.class);verify(transporte,times(1)).send(captor.capture());var mensagem=captor.getValue();mensagem.saveChanges();
        assertThat(mensagem.getFrom()[0].toString()).isEqualTo("teste@gmail.com");assertThat(mensagem.getAllRecipients()).hasSize(1);
        assertThat(mensagem.getAllRecipients()[0].toString()).isEqualTo("destino@example.invalid");assertThat(mensagem.getSubject()).contains("TESTE","sintético");
        var mistura=(Multipart)mensagem.getContent();assertThat(mistura.getCount()).isEqualTo(2);
        var anexo=mistura.getBodyPart(1);assertThat(anexo.getFileName()).isEqualTo("cte-sintetico.xml");
        String xml=new String(anexo.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        assertThat(xml).contains("SEM VALOR FISCAL","00000000000000","Documento sintético");
    }
    @Test void falhaDeTransporteNaoRepeteNemFingeSucesso()throws Exception{
        var transporte=mock(JavaMailSender.class);when(transporte.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("falha sintética")).when(transporte).send(any(MimeMessage.class));
        assertThatThrownBy(()->new TesteGmailService(transporte).enviar("teste@gmail.com","destino@example.invalid")).isInstanceOf(MailSendException.class);
        verify(transporte,times(1)).send(any(MimeMessage.class));
    }
    @Test void servidorSemStarttlsNaoRecebeAutenticacaoOuMensagem()throws Exception{
        var comandos=new java.util.concurrent.CopyOnWriteArrayList<String>();
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try(var servidor=new java.net.ServerSocket()){
            servidor.bind(new java.net.InetSocketAddress("127.0.0.1",0));servidor.setSoTimeout(5000);
            var tarefa=executor.submit(()->{
                try(var socket=servidor.accept()){
                    socket.setSoTimeout(3000);
                    var entrada=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
                    var saida=new PrintWriter(socket.getOutputStream(),true,StandardCharsets.US_ASCII);
                    saida.print("220 localhost teste\r\n");saida.flush();
                    String linha;
                    while((linha=entrada.readLine())!=null){
                        comandos.add(linha);
                        if(linha.startsWith("EHLO")){saida.print("250-localhost\r\n250 SIZE 100000\r\n");saida.flush();}
                        else if(linha.equals("QUIT")){saida.print("221 fim\r\n");saida.flush();break;}
                        else {saida.print("500 nao permitido\r\n");saida.flush();}
                    }
                }catch(java.net.SocketTimeoutException ex){throw new IllegalStateException("Teste SMTP excedeu prazo",ex);}
                catch(IOException ex){throw new IllegalStateException(ex);}
            });
            var transporte=TesteGmail.configurar(ambiente());transporte.setHost("127.0.0.1");transporte.setPort(servidor.getLocalPort());
            assertThatThrownBy(()->new TesteGmailService(transporte).enviar("teste@gmail.com","destino@example.invalid")).isInstanceOf(MailSendException.class);
            tarefa.get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(comandos).anyMatch(c->c.startsWith("EHLO"));
            assertThat(comandos).noneMatch(c->c.startsWith("AUTH")||c.startsWith("MAIL FROM")||c.equals("DATA"));
        }finally{executor.shutdownNow();}
    }
    @ParameterizedTest @ValueSource(strings={"ROTABRASIL_GMAIL_REMETENTE","ROTABRASIL_GMAIL_DESTINATARIO","ROTABRASIL_GMAIL_APP_PASSWORD"})
    void faltaDeConfiguracaoImpedeTentativa(String chave){
        var env=new HashMap<>(ambiente());env.remove(chave);var buffer=new ByteArrayOutputStream();var log=new PrintStream(buffer);
        assertThat(TesteGmail.executar(new String[]{"--enviar"},env,log,log)).isEqualTo(2);assertThat(buffer.toString()).doesNotContain("senha-sintetica");
    }
    @Test void verificarNaoConectaNemEnviaEDeixaLimiteExplicito(){
        var buffer=new ByteArrayOutputStream();var log=new PrintStream(buffer);
        assertThat(TesteGmail.executar(new String[]{"--verificar"},ambiente(),log,log)).isZero();
        assertThat(buffer.toString()).contains("ainda não foram testados").doesNotContain("senha-sintetica");
        assertThat(TesteGmail.executar(new String[]{},ambiente(),log,log)).isEqualTo(2);
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"", "invalido", "a@example.invalid,b@example.invalid","a@example.invalid\r\nBcc: outro@example.invalid"})
    void destinatarioInvalidoNemCriaMensagem(String destinatario){
        var transporte=mock(JavaMailSender.class);
        assertThatThrownBy(()->new TesteGmailService(transporte).enviar("teste@gmail.com",destinatario)).isInstanceOf(MessagingException.class);verifyNoInteractions(transporte);
    }
}
