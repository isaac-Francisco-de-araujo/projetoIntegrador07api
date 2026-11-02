package br.com.rotabrasil;

import br.com.rotabrasil.service.*;
import br.com.rotabrasil.service.email.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.util.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=jdbc:h2:mem:confirmacao-motorista;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000","spring.flyway.enabled=true","rotabrasil.email.modo=SIMULADO"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class ConfirmacaoMotoristaHttpTest {
    static final Path TMP=temporario();static Path temporario(){try{return Files.createTempDirectory("motorista-http-");}catch(Exception ex){throw new IllegalStateException(ex);}}
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("rotabrasil.arquivos.viagens",()->TMP.resolve("acervo").toString());r.add("rotabrasil.exportacao.raiz",()->TMP.resolve("copias").toString());}
    @Autowired TestRestTemplate http;@Autowired JdbcTemplate jdbc;@Autowired NotificacaoRecebimentoService notificacoes;
    @SpyBean ConfiguracaoNotificacoes canal;
    String recebimento;byte[] zip;
    @BeforeEach void preparar()throws Exception{
        reset(canal);Files.createDirectories(TMP.resolve("copias"));
        jdbc.update("UPDATE configuracao_operacao SET remetente='operacao@example.invalid',versao=versao+1,exportacao_automatica=FALSE,subpasta='' WHERE id=1");
        var arquivos=new LinkedHashMap<String,byte[]>();
        arquivos.put("cte.xml",CteFixture.xml().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        arquivos.put("fatura.pdf",("%PDF-1.4\nSINTETICO "+UUID.randomUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip=ConfiguracaoExportacaoHttpTest.zip(arquivos);
        recebimento=notificacoes.receber(new MockMultipartFile("arquivo","documentos.zip","application/zip",zip),"motorista@example.invalid",false);
    }
    @AfterAll static void limpar()throws Exception{FileSystemUtils.deleteRecursively(TMP);}
    ResponseEntity<JsonNode> post(String rota,Object corpo){return http.postForEntity(rota,corpo,JsonNode.class);}
    JsonNode get(String rota){var r=http.getForEntity(rota,JsonNode.class);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();}
    JsonNode conferir(String email,String anterior){var p=new HashMap<String,Object>();p.put("destinatario",email);p.put("anteriorId",anterior);var r=post("/api/recebimentos/"+recebimento+"/conferencias-confirmacao",p);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();}
    ResponseEntity<JsonNode> confirmar(JsonNode c){return post("/api/recebimentos/"+recebimento+"/confirmacoes",Map.of("conferenciaId",c.get("id").asText(),"confirmado",true));}
    int tentativas(){return jdbc.queryForObject("SELECT COUNT(*) FROM notificacoes_recebimento WHERE recebimento_id=? AND evento='CONFIRMACAO_MOTORISTA'",Integer.class,recebimento);}
    ResponseEntity<JsonNode> upload(String rota,Object config){var form=new LinkedMultiValueMap<String,Object>();form.add("arquivo",new ByteArrayResource(zip){public String getFilename(){return "documentos.zip";}});if(config!=null){var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);form.add("configuracao",new HttpEntity<>(config,h));}return http.postForEntity(rota,form,JsonNode.class);}
    JsonNode importar(){var i=upload("/api/pacotes/inspecao",null);assertThat(i.getStatusCode()).isEqualTo(HttpStatus.OK);var r=upload("/api/pacotes/confirmacao",Map.of("sha256",i.getBody().get("sha256").asText(),"vinculos",List.of(Map.of("arquivoId","a1","finalidade","FATURA","xmlIds",List.of("a0")))));assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();}

    @Test void simuladoPadraoConferenciaNaoEnviaEConfirmacaoIdempotentePreservaRetrato(){
        var c=conferir("motorista@example.invalid",null);assertThat(c.get("snapshot").get("modo").asText()).isEqualTo("SIMULADO");assertThat(tentativas()).isZero();
        var r=confirmar(c);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(r.getBody().get("situacao").asText()).isEqualTo("SIMULADO");assertThat(r.getBody().get("snapshot")).isEqualTo(c.get("snapshot"));
        jdbc.update("UPDATE configuracao_operacao SET remetente='outra@example.invalid',versao=versao+1 WHERE id=1");
        assertThat(confirmar(c).getBody()).isEqualTo(r.getBody());assertThat(tentativas()).isEqualTo(1);verify(canal,times(1)).enviar(any());
    }
    @Test void testeRealDesativadoEPrerequisitosSemCredenciaisNaApi(){
        var c=get("/api/configuracoes");assertThat(c.get("confirmacaoMotorista").get("realDisponivel").asBoolean()).isFalse();assertThat(c.toString()).doesNotContain("access_token",TMP.toString());
        assertThat(post("/api/configuracoes/conferencias-email-real",Map.of("destinatario","teste@example.invalid")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);verify(canal,never()).enviar(any());
    }
    @Test void remetenteIncompletoEDestinatarioInvalidoNaoCriamTentativa(){
        assertThat(post("/api/recebimentos/"+recebimento+"/conferencias-confirmacao",Map.of("destinatario","errado")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        jdbc.update("UPDATE configuracao_operacao SET remetente=NULL WHERE id=1");assertThat(post("/api/recebimentos/"+recebimento+"/conferencias-confirmacao",Map.of("destinatario","motorista@example.invalid")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);assertThat(tentativas()).isZero();
    }
    @Test void alteracaoDestinatarioOuConfiguracaoInvalidaConferenciaAnterior(){
        var c=conferir("um@example.invalid",null);var novo=conferir("dois@example.invalid",null);assertThat(confirmar(c).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        jdbc.update("UPDATE configuracao_operacao SET versao=versao+1 WHERE id=1");assertThat(confirmar(novo).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(tentativas()).isZero();
    }
    @Test void confirmacaoExplicitaExpiracaoEAtorSaoObrigatorios(){
        var c=conferir("motorista@example.invalid",null);String rota="/api/recebimentos/"+recebimento+"/confirmacoes";
        assertThat(post(rota,Map.of("conferenciaId",c.get("id").asText(),"confirmado",false)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        jdbc.update("UPDATE conferencias_notificacao SET expira_em=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP) WHERE id=?",c.get("id").asText());assertThat(confirmar(c).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var outro=conferir("motorista@example.invalid",null);jdbc.update("UPDATE conferencias_notificacao SET ator='outro' WHERE id=?",outro.get("id").asText());assertThat(confirmar(outro).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(tentativas()).isZero();
    }
    @Test void mensagemRecebidaNaoAfirmaProcessamentoEProblemasNaoVazamDetalhes(){
        var antes=conferir("motorista@example.invalid",null);assertThat(antes.get("snapshot").get("mensagem").asText()).contains("recebimento do pacote documentos.zip","ainda não foram concluídos");
        notificacoes.evento(recebimento,"PROCESSAMENTO_COM_PENDENCIAS","fixture-erro","fixture","DETALHE PRIVADO SINTETICO");assertThat(confirmar(antes).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var c=conferir("motorista@example.invalid",null);String corpo=c.get("snapshot").get("mensagem").asText();assertThat(corpo).contains("pendências que estão em conferência").doesNotContain("DETALHE PRIVADO",TMP.toString(),"todos os documentos",CteFixture.CHAVE);assertThat(c.get("snapshot").has("anexos")).isFalse();
    }
    @Test void cliqueDuploConcorrenteTransportaUmaVezForaDaTransacao()throws Exception{
        var c=conferir("motorista@example.invalid",null);CountDownLatch iniciou=new CountDownLatch(1),liberar=new CountDownLatch(1);
        doAnswer(inv->{assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();iniciou.countDown();assertThat(liberar.await(10,TimeUnit.SECONDS)).isTrue();return TransporteEmail.Resultado.ACEITO;}).when(canal).enviar(any());
        try(var pool=Executors.newFixedThreadPool(2)){
            var primeira=pool.submit(()->confirmar(c));assertThat(iniciou.await(10,TimeUnit.SECONDS)).isTrue();
            var segunda=pool.submit(()->confirmar(c)).get(10,TimeUnit.SECONDS);assertThat(segunda.getBody().get("situacao").asText()).isEqualTo("DESCONHECIDO");liberar.countDown();
            var concluida=primeira.get(10,TimeUnit.SECONDS);assertThat(concluida.getBody().get("id")).isEqualTo(segunda.getBody().get("id"));assertThat(tentativas()).isEqualTo(1);verify(canal,times(1)).enviar(any());
        }finally{liberar.countDown();}
    }
    @Test void recusaReenvioDeliberadoPreservaHistoricoEResolveComEvidencia(){
        doReturn(TransporteEmail.Resultado.RECUSADO).when(canal).enviar(any());var a=confirmar(conferir("um@example.invalid",null)).getBody();assertThat(a.get("situacao").asText()).isEqualTo("FALHA");String id=a.get("id").asText();assertThat(get("/api/diagnosticos/NOTIFICACAO/"+id).get("gravidade").asText()).isEqualTo("ERRO");
        assertThat(post("/api/recebimentos/"+recebimento+"/conferencias-confirmacao",Map.of("destinatario","dois@example.invalid")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        doReturn(TransporteEmail.Resultado.ACEITO).when(canal).enviar(any());var novo=confirmar(conferir("dois@example.invalid",id)).getBody();assertThat(novo.get("anteriorId").asText()).isEqualTo(id);assertThat(a.get("destinatario").asText()).isEqualTo("um@example.invalid");assertThat(tentativas()).isEqualTo(2);assertThat(get("/api/diagnosticos/NOTIFICACAO/"+id).get("resolucao").get("referencia").asText()).isEqualTo(novo.get("id").asText());
    }
    @Test void timeoutOuExcecaoNaoRepeteEVermelhoNaoEncobreDesconhecido(org.springframework.boot.test.system.CapturedOutput saida){
        doThrow(new IllegalStateException("SEGREDO_SINTETICO_NAO_LOGAR")).when(canal).enviar(any());var c=conferir("motorista@example.invalid",null);var r=confirmar(c);assertThat(r.getBody().get("situacao").asText()).isEqualTo("DESCONHECIDO");assertThat(r.getBody().toString()).doesNotContain("SEGREDO_SINTETICO");assertThat(confirmar(c).getBody()).isEqualTo(r.getBody());verify(canal,times(1)).enviar(any());
        String id=r.getBody().get("id").asText();assertThat(get("/api/diagnosticos/NOTIFICACAO/"+id).get("gravidade").asText()).isEqualTo("ATENCAO");
        doReturn(TransporteEmail.Resultado.ACEITO).when(canal).enviar(any());confirmar(conferir("motorista@example.invalid",id));assertThat(get("/api/diagnosticos/NOTIFICACAO/"+id).get("estado").asText()).isEqualTo("PRECISA_ACAO");
        assertThat(saida.getAll()).doesNotContain("SEGREDO_SINTETICO_NAO_LOGAR");
    }
    @Test void falhaNotificacaoPreservaImportacaoExportacaoEBytes()throws Exception{
        var p=importar();long id=p.get("pacoteId").asLong();var e=post("/api/pacotes/"+id+"/exportacoes",Map.of("confirmado",true)).getBody();assertThat(e.get("situacao").asText()).isEqualTo("CONCLUIDA");
        var antes=jdbc.queryForList("SELECT caminho,sha256 FROM documentos ORDER BY id");
        doReturn(TransporteEmail.Resultado.RECUSADO).when(canal).enviar(any());var c=conferir("motorista@example.invalid",null);assertThat(c.get("snapshot").get("estadoProcessamento").asText()).isEqualTo("IMPORTACAO_CONCLUIDA");confirmar(c);
        assertThat(jdbc.queryForList("SELECT caminho,sha256 FROM documentos ORDER BY id")).isEqualTo(antes);for(var d:antes)assertThat(ConteudoXml.sha256(Files.readAllBytes(Path.of((String)d.get("CAMINHO"))))).isEqualTo(d.get("SHA256"));assertThat(get("/api/exportacoes/"+e.get("id").asText()).get("situacao").asText()).isEqualTo("CONCLUIDA");
    }
}
