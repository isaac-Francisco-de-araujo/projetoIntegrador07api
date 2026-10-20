package br.com.rotabrasil;

import br.com.rotabrasil.service.EmailService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.util.FileSystemUtils;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.datasource.url=jdbc:h2:mem:previa-email-etapa8;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate","spring.h2.console.enabled=false",
        "spring.jpa.open-in-view=false"
})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PreviaEmailHttpTest {
    private static final Path RAIZ=temporario();
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmailService emails;
    private static Path temporario(){try{return Files.createTempDirectory("rotabrasil-previa-etapa8-");}catch(Exception ex){throw new IllegalStateException(ex);}}
    @DynamicPropertySource static void propriedades(DynamicPropertyRegistry registry){registry.add("rotabrasil.arquivos.viagens",RAIZ::toString);}
    @BeforeEach void limpar()throws Exception{
        for(String tabela:new String[]{"envios","documentos","viagens","clientes"})jdbc.update("DELETE FROM "+tabela);
        FileSystemUtils.deleteRecursively(RAIZ);Files.createDirectories(RAIZ);
    }
    @AfterAll static void remover()throws Exception{FileSystemUtils.deleteRecursively(RAIZ);}
    private JsonNode importar(String xml){
        var resposta=FluxoImportacaoHttpTest.enviar(http,"sintetico.xml",xml);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);return resposta.getBody();
    }
    private ResponseEntity<JsonNode> previa(long viagemId,Object pedido){
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity("/api/viagens/"+viagemId+"/previa-email",new HttpEntity<>(pedido,headers),JsonNode.class);
    }
    private void erro(ResponseEntity<JsonNode> resposta,HttpStatus status){
        assertThat(resposta.getStatusCode()).isEqualTo(status);var body=resposta.getBody();
        assertThat(body.get("status").asInt()).isEqualTo(status.value());assertThat(body.get("mensagem").asText()).isNotBlank();
        assertThat(body.get("campos").isArray()).isTrue();assertThat(body.toString()).doesNotContain(RAIZ.toString(),"trace","jdbc:h2");
        semEnvios();
    }
    private void semEnvios(){assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM envios",Integer.class)).isZero();}
    private Map<String,Object> pedido(long documentoId){return Map.of("destinatario","conferencia@example.invalid","documentoIds",List.of(documentoId));}
    @Test void previaExplicitaSelecionaSomenteDocumentoPedidoSemEnviarPersistirOuAlterarArquivos()throws Exception{
        var primeiro=importar(CteFixture.xml());var segundo=importar(CteFixture.xml().replace(CteFixture.CHAVE,CteFixture.outraChave()));
        long viagemId=primeiro.get("viagemId").asLong(),id=segundo.get("documentoId").asLong();
        Path arquivo=Path.of(jdbc.queryForObject("SELECT caminho FROM documentos WHERE id=?",String.class,id));
        byte[] bytes=Files.readAllBytes(arquivo);var data=Files.getLastModifiedTime(arquivo);
        // O contato cadastrado não é escolhido como destinatário da prévia.
        jdbc.update("UPDATE clientes SET email='cadastrado@example.invalid'");
        for(int i=0;i<2;i++){
            var resposta=previa(viagemId,pedido(id));assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            var body=resposta.getBody();assertThat(body.get("enviado").asBoolean()).isFalse();
            assertThat(body.get("viagemId").asLong()).isEqualTo(viagemId);assertThat(body.get("destinatario").asText()).isEqualTo("conferencia@example.invalid");
            assertThat(body.get("documentos").size()).isEqualTo(1);assertThat(body.get("documentos").get(0).get("id").asLong()).isEqualTo(id);
            assertThat(body.toString()).doesNotContain("caminho",RAIZ.toString(),"cadastrado@example.invalid");semEnvios();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documentos",Integer.class)).isEqualTo(2);
        assertThat(Files.readAllBytes(arquivo)).isEqualTo(bytes);assertThat(Files.getLastModifiedTime(arquivo)).isEqualTo(data);
    }
    @ParameterizedTest @ValueSource(strings={"{}","null","{", "{\"destinatario\":\"email-invalido\",\"documentoIds\":[1]}",
        "{\"destinatario\":\"ok@example.invalid\",\"documentoIds\":[]}","{\"destinatario\":\"ok@example.invalid\",\"documentoIds\":[null]}",
        "{\"destinatario\":\"ok@example.invalid\",\"documentoIds\":[0]}","{\"destinatario\":\"ok@example.invalid\",\"documentoIds\":[-1]}",
        "{\"destinatario\":\"ok@example.invalid\",\"documentoIds\":[1.5]}","{\"destinatario\":\"ok@example.invalid\",\"documentoIds\":[1,1]}",
        "{\"destinatario\":\"ok@example.invalid\",\"documentoIds\":[1],\"enviado\":true}"})
    void entradaInvalidaNaoAgendaNemCriaHistorico(String pedido){erro(previa(1,pedido),HttpStatus.BAD_REQUEST);}
    @Test void viagemOuDocumentoInexistenteRetornam404(){
        erro(previa(999999,pedido(999999)),HttpStatus.NOT_FOUND);
        long id=importar(CteFixture.xml()).get("viagemId").asLong();erro(previa(id,pedido(999999)),HttpStatus.NOT_FOUND);
    }
    @Test void idsInvalidosNaRotaRetornam400(){
        for(String id:new String[]{"0","-1","abc"}){
            erro(http.getForEntity("/api/viagens/"+id+"/envios",JsonNode.class),HttpStatus.BAD_REQUEST);
            erro(http.postForEntity("/api/viagens/"+id+"/previa-email",pedido(1),JsonNode.class),HttpStatus.BAD_REQUEST);
        }
    }
    @Test void documentoDeOutraViagemNaoEReassociado(){
        var documento=importar(CteFixture.xml());long dono=documento.get("viagemId").asLong();
        long clienteId=jdbc.queryForObject("SELECT cliente_id FROM viagens WHERE id=?",Long.class,dono);
        var viagem=http.postForEntity("/api/viagens",Map.of("numero","1","clienteId",clienteId),JsonNode.class);
        assertThat(viagem.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        erro(previa(viagem.getBody().get("id").asLong(),pedido(documento.get("documentoId").asLong())),HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT viagem_id FROM documentos",Long.class)).isEqualTo(dono);
    }
    @Test void consultarHistoricoVazioNaoCriaRegistro(){
        long id=importar(CteFixture.xml()).get("viagemId").asLong();
        var resposta=http.getForEntity("/api/viagens/"+id+"/envios",JsonNode.class);assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(resposta.getBody().isEmpty()).isTrue();
        erro(http.getForEntity("/api/viagens/999999/envios",JsonNode.class),HttpStatus.NOT_FOUND);semEnvios();
    }
    @Test void historicoRefleteDadosExistentesSemExporMensagensInternasNemMisturarViagens(){
        var documento=importar(CteFixture.xml());long viagemId=documento.get("viagemId").asLong();long clienteId=jdbc.queryForObject("SELECT cliente_id FROM viagens",Long.class);
        var outra=http.postForEntity("/api/viagens",Map.of("numero","1","clienteId",clienteId),JsonNode.class).getBody();
        // Histórico totalmente sintético; não houve tentativa de transporte.
        for(String status:new String[]{"PENDENTE","ENVIADO","ERRO"})jdbc.update("INSERT INTO envios (viagem_id,cliente_id,status,mensagem) VALUES (?,?,?,?)",viagemId,clienteId,status,"diagnóstico interno privado");
        jdbc.update("INSERT INTO envios (viagem_id,cliente_id,status) VALUES (?,?,'PENDENTE')",outra.get("id").asLong(),clienteId);
        var resposta=http.getForEntity("/api/viagens/"+viagemId+"/envios",JsonNode.class);assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body=resposta.getBody();assertThat(body.size()).isEqualTo(3);var statuses=new ArrayList<String>();long anterior=0;
        for(var linha:body){assertThat(linha.get("id").asLong()).isGreaterThan(anterior);anterior=linha.get("id").asLong();statuses.add(linha.get("status").asText());
            assertThat(linha.get("viagemId").asLong()).isEqualTo(viagemId);assertThat(linha.get("clienteId").asLong()).isEqualTo(clienteId);assertThat(linha.get("dataEnvio").isNull()).isTrue();}
        assertThat(statuses).containsExactly("PENDENTE","ENVIADO","ERRO");assertThat(body.toString()).doesNotContain("mensagem","diagnóstico","viagem\"", "cliente\"");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM envios",Integer.class)).isEqualTo(4);
    }
    @Test void previaNaoRepresentaValidacaoDeDisponibilidadeDeAnexos()throws Exception{
        var doc=importar(CteFixture.xml());long id=doc.get("documentoId").asLong();
        Files.delete(Path.of(jdbc.queryForObject("SELECT caminho FROM documentos WHERE id=?",String.class,id)));
        var resposta=previa(doc.get("viagemId").asLong(),pedido(id));assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().get("enviado").asBoolean()).isFalse();semEnvios();
    }
    @Test void cadastroDeEnvioNaoEExpostoETransporteInternoPermaneceBloqueado(){
        long id=importar(CteFixture.xml()).get("viagemId").asLong();
        erro(http.postForEntity("/api/viagens/"+id+"/envios",pedido(1),JsonNode.class),HttpStatus.METHOD_NOT_ALLOWED);
        assertThatThrownBy(()->emails.enviarDocumentos(null)).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("Envio real");semEnvios();
    }
}
