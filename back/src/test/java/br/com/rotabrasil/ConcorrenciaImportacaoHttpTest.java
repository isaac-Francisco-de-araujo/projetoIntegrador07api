package br.com.rotabrasil;

import br.com.rotabrasil.repository.*;
import br.com.rotabrasil.service.ConteudoXml;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:concorrencia-etapa4;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConcorrenciaImportacaoHttpTest {
    private static final Path RAIZ = temporario();
    @Autowired TestRestTemplate http;
    @Autowired DocumentoRepository documentos;
    @Autowired ViagemRepository viagens;
    @Autowired ClienteRepository clientes;
    private static Path temporario() {
        try { return Files.createTempDirectory("rotabrasil-concorrencia-"); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    @DynamicPropertySource static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("rotabrasil.arquivos.viagens", RAIZ::toString);
    }
    @BeforeEach void limpar() throws Exception {
        documentos.deleteAll(); viagens.deleteAll(); clientes.deleteAll();
        FileSystemUtils.deleteRecursively(RAIZ); Files.createDirectories(RAIZ);
    }
    @AfterAll static void remover() throws Exception { FileSystemUtils.deleteRecursively(RAIZ); }
    private record Resultado(String xml, ResponseEntity<JsonNode> resposta) {}
    private List<Resultado> simultaneas(List<String> xmls) throws Exception {
        var inicio = new CyclicBarrier(xmls.size());
        var executor = Executors.newFixedThreadPool(xmls.size());
        try {
            var tarefas = new ArrayList<Future<Resultado>>();
            for (int i = 0; i < xmls.size(); i++) {
                String xml = xmls.get(i); String nome = "documento-" + i + ".xml";
                tarefas.add(executor.submit(() -> {
                    var arquivo = new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)) {
                        @Override public String getFilename() { return nome; }
                    };
                    var partes = new LinkedMultiValueMap<String, Object>(); partes.add("arquivo", arquivo);
                    var headers = new HttpHeaders(); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                    inicio.await(10, TimeUnit.SECONDS);
                    return new Resultado(xml, http.postForEntity("/api/processamento/xml", new HttpEntity<>(partes, headers), JsonNode.class));
                }));
            }
            var resultados = new ArrayList<Resultado>();
            for (var tarefa : tarefas) resultados.add(tarefa.get(30, TimeUnit.SECONDS));
            return resultados;
        } finally { executor.shutdownNow(); }
    }
    private void semResiduos() throws Exception {
        try (var operacoes = Files.list(RAIZ.resolve(".pendentes"))) { assertThat(operacoes.toList()).isEmpty(); }
    }
    @Test void oitoImportacoesIdenticasReutilizamUmDocumentoUmaViagemEUmCliente() throws Exception {
        var resultados = simultaneas(Collections.nCopies(8, CteFixture.xml()));
        assertThat(resultados).allSatisfy(r -> assertThat(r.resposta().getStatusCode()).isEqualTo(HttpStatus.OK));
        assertThat(resultados.stream().map(r -> r.resposta().getBody().get("documentoId").asLong()).distinct().toList()).hasSize(1);
        assertThat(documentos.count()).isEqualTo(1); assertThat(viagens.count()).isEqualTo(1); assertThat(clientes.count()).isEqualTo(1);
        assertThat(Files.readString(Path.of(documentos.findAll().getFirst().getCaminho()))).isEqualTo(CteFixture.xml());
        semResiduos();
    }
    @Test void chavesDistintasConcorrentesCompartilhamViagemSemDuplicarCliente() throws Exception {
        String outro = CteFixture.xml().replace(CteFixture.CHAVE, CteFixture.outraChave());
        var resultados = simultaneas(List.of(CteFixture.xml(), outro, CteFixture.xml(), outro, CteFixture.xml(), outro, CteFixture.xml(), outro));
        assertThat(resultados).allSatisfy(r -> assertThat(r.resposta().getStatusCode()).isEqualTo(HttpStatus.OK));
        assertThat(documentos.count()).isEqualTo(2); assertThat(viagens.count()).isEqualTo(1); assertThat(clientes.count()).isEqualTo(1);
        for (var documento : documentos.findAll()) {
            assertThat(Files.readString(Path.of(documento.getCaminho()))).isIn(CteFixture.xml(), outro);
        }
        semResiduos();
    }
    @Test void mesmaChaveComBytesDiferentesProduzConflitoSemSobrescrita() throws Exception {
        var resultados = simultaneas(List.of(CteFixture.xml(), CteFixture.xml()+"\n", CteFixture.xml(), CteFixture.xml()+"\n",
                CteFixture.xml(), CteFixture.xml()+"\n", CteFixture.xml(), CteFixture.xml()+"\n"));
        assertThat(resultados).extracting(r -> r.resposta().getStatusCode()).containsOnly(HttpStatus.OK, HttpStatus.CONFLICT)
                .contains(HttpStatus.OK, HttpStatus.CONFLICT);
        assertThat(documentos.count()).isEqualTo(1);
        var documento = documentos.findAll().getFirst();
        String vencedor = Files.readString(Path.of(documento.getCaminho()));
        for (var resultado : resultados) {
            assertThat(resultado.resposta().getStatusCode()).isEqualTo(resultado.xml().equals(vencedor) ? HttpStatus.OK : HttpStatus.CONFLICT);
        }
        assertThat(documento.getSha256()).isEqualTo(ConteudoXml.sha256(vencedor.getBytes(StandardCharsets.UTF_8)));
        assertThat(viagens.count()).isEqualTo(1); assertThat(clientes.count()).isEqualTo(1);
        semResiduos();
    }
}
