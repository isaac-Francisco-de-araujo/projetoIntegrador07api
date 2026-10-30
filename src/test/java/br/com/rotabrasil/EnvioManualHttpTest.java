package br.com.rotabrasil;

import br.com.rotabrasil.service.email.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.FileSystemUtils;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.datasource.url=jdbc:h2:mem:envio-manual;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate","spring.h2.console.enabled=false"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class EnvioManualHttpTest {
    static final Path RAIZ=temporario();
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TransporteEmailSimulado transporte;
    long viagem, documento;
    static Path temporario(){try{return Files.createTempDirectory("rotabrasil-envio-manual-");}catch(Exception ex){throw new IllegalStateException(ex);}}
    @DynamicPropertySource static void propriedades(DynamicPropertyRegistry r){r.add("rotabrasil.arquivos.viagens",RAIZ::toString);}
    @BeforeEach void preparar() throws Exception {
        // FK circular lógica: apagar as conferências somente após soltar a referência dos reenvios.
        jdbc.update("UPDATE conferencias_email SET anterior_id=NULL");
        jdbc.update("DELETE FROM tentativas_email WHERE anterior_id IS NOT NULL");
        jdbc.update("DELETE FROM tentativas_email");
        for(String t:List.of("conferencias_email","anexo_viagens","anexos","pacote_documentos","pacotes","envios","documentos","viagens","clientes")) jdbc.update("DELETE FROM "+t);
        FileSystemUtils.deleteRecursively(RAIZ); Files.createDirectories(RAIZ);
        reset(transporte); when(transporte.enviar(any())).thenAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tentativas_email",Integer.class)).isPositive();
            return TransporteEmail.Resultado.ACEITO;
        });
        var d=FluxoImportacaoHttpTest.enviar(http,"sintetico.xml",CteFixture.xml()).getBody();
        viagem=d.get("viagemId").asLong(); documento=d.get("documentoId").asLong();
    }
    @AfterAll static void limpar() throws Exception {FileSystemUtils.deleteRecursively(RAIZ);}
    Map<String,Object> selecao(){return Map.of("destinatario","operador@example.invalid","documentoIds",List.of(documento));}
    String rota(String sufixo){return "/api/viagens/"+viagem+"/"+sufixo;}
    ResponseEntity<JsonNode> post(String rota,Object body){return http.postForEntity(rota,body,JsonNode.class);}
    JsonNode conferir(String anterior){
        var p=new HashMap<String,Object>();p.put("selecao",selecao());if(anterior!=null)p.put("anteriorId",anterior);
        var r=post(rota("conferencias-email"),p);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();
    }
    ResponseEntity<JsonNode> confirmar(String id){return post(rota("tentativas-email"),Map.of("conferenciaId",id,"confirmado",true));}
    JsonNode enviar(){var c=conferir(null);var r=confirmar(c.get("id").asText());assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();}
    Path arquivo(){return Path.of(jdbc.queryForObject("SELECT caminho FROM documentos WHERE id=?",String.class,documento));}
    @Test void simulacaoAuditavelPreservaHistoricoMesmoComMudancasPosteriores() throws Exception {
        jdbc.update("INSERT INTO envios(viagem_id,cliente_id,status,mensagem) SELECT id,cliente_id,'ENVIADO','privado' FROM viagens WHERE id=?",viagem);
        var c=conferir(null);verifyNoInteractions(transporte);
        var r=confirmar(c.get("id").asText());assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        var tentativa=r.getBody();assertThat(tentativa.get("situacao").asText()).isEqualTo("SIMULADO");
        assertThat(tentativa.get("modo").asText()).isEqualTo("SIMULADO");assertThat(tentativa.get("motivo").asText()).contains("nenhum e-mail real");
        jdbc.update("UPDATE documentos SET nome_arquivo='corrigido.xml',arquivado_em=CURRENT_TIMESTAMP WHERE id=?",documento);
        jdbc.update("UPDATE clientes SET email='novo@example.invalid'");Files.delete(arquivo());
        var repetida=confirmar(c.get("id").asText());assertThat(repetida.getBody()).isEqualTo(tentativa);
        var consulta=http.getForEntity(rota("tentativas-email/")+tentativa.get("id").asText(),JsonNode.class);
        assertThat(consulta.getBody()).isEqualTo(tentativa);assertThat(tentativa.toString()).doesNotContain("corrigido.xml","novo@example.invalid","caminho","conteudo","privado");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM envios WHERE status='ENVIADO'",Integer.class)).isEqualTo(1);
        verify(transporte,times(1)).enviar(any());
    }
    @Test void falhaComprovadaEDesconhecidoSaoDistintosSemExporExcecao() {
        doReturn(TransporteEmail.Resultado.RECUSADO).when(transporte).enviar(any());
        var primeira=enviar();assertThat(primeira.get("situacao").asText()).isEqualTo("FALHA");
        doThrow(new IllegalStateException("senha=segredo resposta privada")).when(transporte).enviar(any());
        var c=conferir(primeira.get("id").asText());var segunda=confirmar(c.get("id").asText()).getBody();
        assertThat(segunda.get("situacao").asText()).isEqualTo("DESCONHECIDO");assertThat(segunda.toString()).doesNotContain("senha","segredo","privada");
        confirmar(c.get("id").asText());verify(transporte,times(2)).enviar(any());
    }
    @Test void transportePodeInformarResultadoDesconhecido() {
        doReturn(TransporteEmail.Resultado.DESCONHECIDO).when(transporte).enviar(any());
        assertThat(enviar().get("situacao").asText()).isEqualTo("DESCONHECIDO");verify(transporte,times(1)).enviar(any());
    }
    @Test void falhaAoGravarResultadoPreservaDesconhecidoSemRepetirTransporte() {
        String id=conferir(null).get("id").asText();
        jdbc.execute("ALTER TABLE tentativas_email ADD CONSTRAINT teste_falha_resultado CHECK(situacao <> 'SIMULADO')");
        try {
            assertThat(confirmar(id).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            var repetida=confirmar(id);assertThat(repetida.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(repetida.getBody().get("situacao").asText()).isEqualTo("DESCONHECIDO");
            assertThat(repetida.getBody().get("concluidaEm").isNull()).isTrue();
            verify(transporte,times(1)).enviar(any());
        } finally {jdbc.execute("ALTER TABLE tentativas_email DROP CONSTRAINT teste_falha_resultado");}
    }
    @Test void reenvioExigeAcaoSeparadaNovaConferenciaEReferenciaAnterior() {
        var primeira=enviar();String id=primeira.get("id").asText();
        assertThat(post(rota("conferencias-email"),Map.of("selecao",selecao())).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var c=conferir(id);var segunda=confirmar(c.get("id").asText()).getBody();
        assertThat(segunda.get("anteriorId").asText()).isEqualTo(id);assertThat(segunda.get("id").asText()).isNotEqualTo(id);
        assertThat(http.getForEntity(rota("tentativas-email"),JsonNode.class).getBody().size()).isEqualTo(2);verify(transporte,times(2)).enviar(any());
    }
    @Test void cliqueDuploConcorrenteReutilizaTentativaMesmoDuranteTransporte() throws Exception {
        var c=conferir(null);String id=c.get("id").asText();
        var entrou=new CountDownLatch(1);var liberar=new CountDownLatch(1);
        doAnswer(inv->{entrou.countDown();assertThat(liberar.await(10,TimeUnit.SECONDS)).isTrue();return TransporteEmail.Resultado.ACEITO;}).when(transporte).enviar(any());
        try(var pool=Executors.newFixedThreadPool(6)) {
            var primeira=pool.submit(()->confirmar(id));assertThat(entrou.await(10,TimeUnit.SECONDS)).isTrue();
            var repetidas=new ArrayList<Future<ResponseEntity<JsonNode>>>();for(int i=0;i<5;i++) repetidas.add(pool.submit(()->confirmar(id)));
            var ids=new HashSet<String>();for(var f:repetidas){var r=f.get(10,TimeUnit.SECONDS);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);ids.add(r.getBody().get("id").asText());assertThat(r.getBody().get("situacao").asText()).isEqualTo("DESCONHECIDO");}
            liberar.countDown();ids.add(primeira.get(10,TimeUnit.SECONDS).getBody().get("id").asText());assertThat(ids).hasSize(1);
        } finally {liberar.countDown();}
        verify(transporte,times(1)).enviar(any());assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tentativas_email",Integer.class)).isEqualTo(1);
    }
    @Test void conferenciasDiferentesNaoContornamProtecaoContraReenvioAcidental() throws Exception {
        String a=conferir(null).get("id").asText(),b=conferir(null).get("id").asText();
        try(var pool=Executors.newFixedThreadPool(2)){
            var x=pool.submit(()->confirmar(a));var y=pool.submit(()->confirmar(b));
            assertThat(List.of(x.get().getStatusCode(),y.get().getStatusCode())).containsExactlyInAnyOrder(HttpStatus.OK,HttpStatus.CONFLICT);
        } verify(transporte,times(1)).enviar(any());
    }
    @Test void destinatarioInvalidoOuConfirmacaoAusenteNaoCriaTentativa() {
        assertThat(post(rota("conferencias-email"),Map.of("selecao",Map.of("destinatario","invalido","documentoIds",List.of(documento)))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String id=conferir(null).get("id").asText();
        assertThat(post(rota("tentativas-email"),Map.of("conferenciaId",id,"confirmado",false)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(transporte);
    }
    @Test void alteracaoDeDestinatarioOuAnexosNaoPodeSerAcrescentadaAConfirmacao() {
        String id=conferir(null).get("id").asText();
        for(String campo:List.of("destinatario","documentoIds","anexoIds")){
            var p=new HashMap<String,Object>();p.put("conferenciaId",id);p.put("confirmado",true);p.put(campo,"alteracao");
            assertThat(post(rota("tentativas-email"),p).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        } verifyNoInteractions(transporte);
    }
    @Test void revalidaArquivadoAusenteAlteradoVinculoEMetadadosDepoisDaPrevia() throws Exception {
        String id=conferir(null).get("id").asText();byte[] original=Files.readAllBytes(arquivo());
        jdbc.update("UPDATE documentos SET arquivado_em=CURRENT_TIMESTAMP WHERE id=?",documento);
        assertThat(confirmar(id).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        jdbc.update("UPDATE documentos SET arquivado_em=NULL WHERE id=?",documento);
        Files.delete(arquivo());assertThat(confirmar(id).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Files.writeString(arquivo(),"adulterado");assertThat(confirmar(id).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Files.write(arquivo(),original);
        long cliente=jdbc.queryForObject("SELECT cliente_id FROM viagens WHERE id=?",Long.class,viagem);
        jdbc.update("INSERT INTO viagens(numero,cliente_id) VALUES('999',?)",cliente);
        long outra=jdbc.queryForObject("SELECT id FROM viagens WHERE numero='999'",Long.class);
        jdbc.update("UPDATE documentos SET viagem_id=? WHERE id=?",outra,documento);assertThat(confirmar(id).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        jdbc.update("UPDATE documentos SET viagem_id=?,nome_arquivo='novo.xml' WHERE id=?",viagem,documento);assertThat(confirmar(id).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(transporte);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tentativas_email",Integer.class)).isZero();
    }
    @Test void conferenciaExpiradaNaoAutorizaNovaTentativa() {
        String id=conferir(null).get("id").asText();jdbc.update("UPDATE conferencias_email SET expira_em=DATEADD('HOUR',-1,CURRENT_TIMESTAMP)");
        assertThat(confirmar(id).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);verifyNoInteractions(transporte);
    }
    @Test void faturaCompartilhadaFicaUmaVezSemTentativasParaOutrasViagens() throws Exception {
        long cliente=jdbc.queryForObject("SELECT cliente_id FROM viagens WHERE id=?",Long.class,viagem);
        jdbc.update("INSERT INTO viagens(numero,cliente_id) VALUES('999',?)",cliente);long outra=jdbc.queryForObject("SELECT id FROM viagens WHERE numero='999'",Long.class);
        jdbc.update("INSERT INTO pacotes(nome_arquivo,sha256,configuracao_sha256,criado_em) VALUES('sintetico.zip',?,?,CURRENT_TIMESTAMP)","a".repeat(64),"b".repeat(64));
        long pacote=jdbc.queryForObject("SELECT id FROM pacotes",Long.class);
        Path pdf=RAIZ.resolve("fatura.pdf");Files.writeString(pdf,"%PDF-sintetico");
        jdbc.update("INSERT INTO anexos(pacote_id,nome_arquivo,caminho,sha256,finalidade,token,numero_armazenamento) VALUES(?,?,?,?, 'FATURA',?,'66213')",pacote,"fatura.pdf",pdf.toString(),br.com.rotabrasil.service.ConteudoXml.sha256(Files.readAllBytes(pdf)),UUID.randomUUID().toString());
        long anexo=jdbc.queryForObject("SELECT id FROM anexos",Long.class);for(long v:List.of(viagem,outra))jdbc.update("INSERT INTO anexo_viagens(anexo_id,viagem_id) VALUES(?,?)",anexo,v);
        var c=post(rota("conferencias-email"),Map.of("selecao",Map.of("destinatario","escolhido@example.invalid","anexoIds",List.of(anexo))));
        assertThat(c.getStatusCode()).isEqualTo(HttpStatus.OK);var t=confirmar(c.getBody().get("id").asText()).getBody();
        assertThat(t.get("snapshot").get("arquivos").size()).isEqualTo(1);assertThat(t.get("snapshot").get("arquivos").get(0).get("viagemIds").size()).isEqualTo(2);
        assertThat(http.getForEntity("/api/viagens/"+outra+"/tentativas-email",JsonNode.class).getBody().isEmpty()).isTrue();verify(transporte,times(1)).enviar(any());
    }
}
