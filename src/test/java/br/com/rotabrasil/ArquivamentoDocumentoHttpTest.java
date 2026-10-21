package br.com.rotabrasil;

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
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:arquivamento-http;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
    "spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate","spring.h2.console.enabled=false"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ArquivamentoDocumentoHttpTest {
    private static final Path RAIZ=temporario();
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    private static Path temporario(){try{return Files.createTempDirectory("rotabrasil-arquivar-");}catch(Exception ex){throw new IllegalStateException(ex);}}
    @DynamicPropertySource static void configurar(DynamicPropertyRegistry r){r.add("rotabrasil.arquivos.viagens",RAIZ::toString);}
    @BeforeEach void limpar()throws Exception{for(String tabela:new String[]{"envios","documentos","viagens","clientes"})jdbc.update("DELETE FROM "+tabela);FileSystemUtils.deleteRecursively(RAIZ);Files.createDirectories(RAIZ);}
    @AfterAll static void remover()throws Exception{FileSystemUtils.deleteRecursively(RAIZ);}
    private JsonNode importar(){var r=FluxoImportacaoHttpTest.enviar(http,"sintetico.xml",CteFixture.xml());assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();}
    private ResponseEntity<JsonNode> arquivar(long id){return http.exchange("/api/documentos/"+id,HttpMethod.DELETE,HttpEntity.EMPTY,JsonNode.class);}
    private ResponseEntity<JsonNode> restaurar(long id){return http.postForEntity("/api/documentos/"+id+"/restaurar",HttpEntity.EMPTY,JsonNode.class);}
    private JsonNode lista(long viagemId,boolean arquivados){var r=http.getForEntity("/api/viagens/"+viagemId+"/documentos"+(arquivados?"/arquivados":""),JsonNode.class);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();}
    private Path arquivo(long id){return Path.of(jdbc.queryForObject("SELECT caminho FROM documentos WHERE id=?",String.class,id));}
    @Test void cicloPreservaRegistroArquivoRelacionamentosEIdentidade()throws Exception{
        var importado=importar();long id=importado.get("documentoId").asLong(),viagem=importado.get("viagemId").asLong();
        Path path=arquivo(id);byte[] antes=Files.readAllBytes(path);var modificacao=Files.getLastModifiedTime(path);
        String chave=jdbc.queryForObject("SELECT chave_acesso FROM documentos WHERE id=?",String.class,id);
        assertThat(arquivar(id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(lista(viagem,false).isEmpty()).isTrue();var arquivados=lista(viagem,true);assertThat(arquivados.size()).isEqualTo(1);
        assertThat(arquivados.get(0).get("id").asLong()).isEqualTo(id);assertThat(arquivados.get(0).get("arquivadoEm").asText()).isNotBlank();assertThat(arquivados.get(0).has("caminho")).isFalse();
        assertThat(http.getForEntity("/api/documentos",JsonNode.class).getBody().isEmpty()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documentos",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT chave_acesso FROM documentos WHERE id=?",String.class,id)).isEqualTo(chave);
        var response=restaurar(id);assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(response.getBody().get("id").asLong()).isEqualTo(id);
        assertThat(lista(viagem,false).size()).isEqualTo(1);assertThat(lista(viagem,true).isEmpty()).isTrue();
        assertThat(Files.readAllBytes(path)).isEqualTo(antes);assertThat(Files.getLastModifiedTime(path)).isEqualTo(modificacao);
        assertThat(importar().get("documentoId").asLong()).isEqualTo(id);
    }
    @Test void arquivamentoERestauracaoRepetidosSaoIdempotentes(){
        var doc=importar();long id=doc.get("documentoId").asLong(),viagem=doc.get("viagemId").asLong();arquivar(id);
        String data=lista(viagem,true).get(0).get("arquivadoEm").asText();arquivar(id);assertThat(lista(viagem,true).get(0).get("arquivadoEm").asText()).isEqualTo(data);
        assertThat(restaurar(id).getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(restaurar(id).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documentos",Integer.class)).isEqualTo(1);
    }
    @Test void arquivadoNaoPodeEntrarNaPreviaOuSerReimportadoSemRestauracao(){
        var doc=importar();long id=doc.get("documentoId").asLong(),viagem=doc.get("viagemId").asLong();arquivar(id);
        var previa=http.postForEntity("/api/viagens/"+viagem+"/previa-email",Map.of("destinatario","teste@example.invalid","documentoIds",List.of(id)),JsonNode.class);
        assertThat(previa.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var reimportacao=FluxoImportacaoHttpTest.enviar(http,"outro.xml",CteFixture.xml());assertThat(reimportacao.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(reimportacao.getBody().get("mensagem").asText()).contains("restaure");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM envios",Integer.class)).isZero();
    }
    @Test void arquivarNaoPermiteApagarViagemOuClienteENaoAlteraHistorico(){
        var doc=importar();long id=doc.get("documentoId").asLong(),viagem=doc.get("viagemId").asLong();long cliente=jdbc.queryForObject("SELECT cliente_id FROM viagens WHERE id=?",Long.class,viagem);
        jdbc.update("INSERT INTO envios (cliente_id,viagem_id,status) VALUES (?,?,'PENDENTE')",cliente,viagem);arquivar(id);
        for(String rota:List.of("/api/viagens/"+viagem,"/api/clientes/"+cliente))assertThat(http.exchange(rota,HttpMethod.DELETE,HttpEntity.EMPTY,JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM envios",Integer.class)).isEqualTo(1);
    }
    @Test void documentoInexistenteOuViagemInexistenteRetorna404(){
        assertThat(arquivar(999999).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);assertThat(restaurar(999999).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.getForEntity("/api/viagens/999999/documentos/arquivados",JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","abc"}) void idsInvalidosRetornam400(String id){
        assertThat(http.exchange("/api/documentos/"+id,HttpMethod.DELETE,HttpEntity.EMPTY,JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.postForEntity("/api/documentos/"+id+"/restaurar",HttpEntity.EMPTY,JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test void arquivamentoConcorrenteComImportacaoNaoDuplicaArquivoOuRegistro()throws Exception{
        var doc=importar();long id=doc.get("documentoId").asLong();byte[] antes=Files.readAllBytes(arquivo(id));
        var barreira=new CyclicBarrier(2);var executor=Executors.newFixedThreadPool(2);
        try{
            var remover=executor.submit(()->{barreira.await(10,TimeUnit.SECONDS);return arquivar(id);});
            var importar=executor.submit(()->{barreira.await(10,TimeUnit.SECONDS);return FluxoImportacaoHttpTest.enviar(http,"paralelo.xml",CteFixture.xml());});
            assertThat(remover.get(30,TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(importar.get(30,TimeUnit.SECONDS).getStatusCode()).isIn(HttpStatus.OK,HttpStatus.CONFLICT);
        }finally{executor.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documentos",Integer.class)).isEqualTo(1);assertThat(Files.readAllBytes(arquivo(id))).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT arquivado_em IS NOT NULL FROM documentos WHERE id=?",Boolean.class,id)).isTrue();
    }
}
