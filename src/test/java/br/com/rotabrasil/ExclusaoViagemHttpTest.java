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
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:exclusao-viagens;DB_CLOSE_DELAY=-1","spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate","spring.h2.console.enabled=false"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ExclusaoViagemHttpTest {
    private static final Path RAIZ=temporario();
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    private static Path temporario(){try{return Files.createTempDirectory("rotabrasil-exclusao-");}catch(Exception ex){throw new IllegalStateException(ex);}}
    @DynamicPropertySource static void configurar(DynamicPropertyRegistry r){r.add("rotabrasil.arquivos.viagens",RAIZ::toString);}
    @BeforeEach void limpar()throws Exception{for(String tabela:new String[]{"envios","documentos","viagens","clientes"})jdbc.update("DELETE FROM "+tabela);FileSystemUtils.deleteRecursively(RAIZ);Files.createDirectories(RAIZ);}
    @AfterAll static void remover()throws Exception{FileSystemUtils.deleteRecursively(RAIZ);}
    private long vazia(){
        var cliente=http.postForEntity("/api/clientes",Map.of("nome","Cliente Sintético","cnpj","00000000000000"),JsonNode.class);
        assertThat(cliente.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var viagem=http.postForEntity("/api/viagens",Map.of("numero","1","clienteId",cliente.getBody().get("id").asLong()),JsonNode.class);
        assertThat(viagem.getStatusCode()).isEqualTo(HttpStatus.CREATED);return viagem.getBody().get("id").asLong();
    }
    private ResponseEntity<JsonNode> excluir(String id){return http.exchange("/api/viagens/"+id,HttpMethod.DELETE,HttpEntity.EMPTY,JsonNode.class);}
    @Test void excluiSomenteViagemSemVinculosEPreservaCliente(){
        long id=vazia();var resposta=excluir(String.valueOf(id));assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);assertThat(resposta.getBody()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM viagens",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clientes",Integer.class)).isEqualTo(1);
        assertThat(excluir(String.valueOf(id)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","abc"}) void rejeitaIdentificadoresInvalidos(String id){assertThat(excluir(id).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);}
    @Test void viagemComXmlRetorna409EPreservaRegistrosEArquivo()throws Exception{
        var importado=FluxoImportacaoHttpTest.enviar(http,"sintetico.xml",CteFixture.xml());assertThat(importado.getStatusCode()).isEqualTo(HttpStatus.OK);
        long id=importado.getBody().get("viagemId").asLong();Path arquivo=Path.of(jdbc.queryForObject("SELECT caminho FROM documentos",String.class));byte[] antes=Files.readAllBytes(arquivo);
        var resposta=excluir(String.valueOf(id));assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(resposta.getBody().get("mensagem").asText()).contains("documentos");
        assertThat(Files.readAllBytes(arquivo)).isEqualTo(antes);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM viagens",Integer.class)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documentos",Integer.class)).isEqualTo(1);
    }
    @Test void viagemComPdfTambemFicaProtegida(){
        long id=vazia();jdbc.update("INSERT INTO documentos (tipo,nome_arquivo,caminho,viagem_id) VALUES ('PDF','fixture.pdf','fixture-inexistente',?)",id);
        assertThat(excluir(String.valueOf(id)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documentos",Integer.class)).isEqualTo(1);
    }
    @Test void historicoImpedeExclusaoMesmoSemDocumento(){
        long id=vazia();long cliente=jdbc.queryForObject("SELECT cliente_id FROM viagens WHERE id=?",Long.class,id);
        jdbc.update("INSERT INTO envios (cliente_id,viagem_id,status) VALUES (?,?,'PENDENTE')",cliente,id);
        assertThat(excluir(String.valueOf(id)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM envios",Integer.class)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM viagens",Integer.class)).isEqualTo(1);
    }
}
