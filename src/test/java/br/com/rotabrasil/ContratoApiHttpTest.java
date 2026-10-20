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
import org.springframework.util.*;
import org.springframework.core.io.ByteArrayResource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:contrato-api-etapa6;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
        "spring.h2.console.enabled=false", "spring.servlet.multipart.max-file-size=2KB",
        "spring.servlet.multipart.max-request-size=4KB"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ContratoApiHttpTest {
    private static final Path RAIZ = temporario();
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    private static Path temporario() {
        try { return Files.createTempDirectory("rotabrasil-api-etapa6-"); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    @DynamicPropertySource static void propriedades(DynamicPropertyRegistry registry) { registry.add("rotabrasil.arquivos.viagens", RAIZ::toString); }
    @BeforeEach void limpar() throws Exception {
        for (String tabela : new String[]{"envios", "documentos", "viagens", "clientes"}) jdbc.update("DELETE FROM " + tabela);
        FileSystemUtils.deleteRecursively(RAIZ); Files.createDirectories(RAIZ);
    }
    @AfterAll static void remover() throws Exception { FileSystemUtils.deleteRecursively(RAIZ); }
    private ResponseEntity<JsonNode> json(HttpMethod method, String path, Object body) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
    private JsonNode ok(ResponseEntity<JsonNode> resposta, HttpStatus status) {
        assertThat(resposta.getStatusCode()).isEqualTo(status);
        assertThat(resposta.getBody()).isNotNull(); return resposta.getBody();
    }
    private JsonNode erro(ResponseEntity<JsonNode> resposta, HttpStatus status) {
        var body = ok(resposta, status);
        assertThat(body.get("status").asInt()).isEqualTo(status.value());
        assertThat(body.get("mensagem").asText()).isNotBlank();
        assertThat(body.get("timestamp").asText()).isNotBlank();
        assertThat(body.get("campos").isArray()).isTrue();
        assertThat(body.has("trace")).isFalse(); assertThat(body.has("exception")).isFalse();
        assertThat(body.toString()).doesNotContain("jdbc:h2", "insert into", "select ", RAIZ.toString());
        return body;
    }
    private void campos(JsonNode body, String... esperados) {
        var nomes = new ArrayList<String>(); body.fieldNames().forEachRemaining(nomes::add);
        assertThat(nomes).containsExactlyInAnyOrder(esperados);
    }
    private Map<String,Object> cliente(String cnpj) { return Map.of("nome", "Cliente Sintético", "cnpj", cnpj); }
    private JsonNode cadastrarCliente(String cnpj) { return ok(json(HttpMethod.POST, "/api/clientes", cliente(cnpj)), HttpStatus.CREATED); }
    private Map<String,Object> viagem(String numero, long clienteId) {
        return Map.of("numero", numero, "clienteId", clienteId, "data", "2025-04-04", "status", "Recebida");
    }
    private JsonNode cadastrarViagem(String numero, long clienteId) { return ok(json(HttpMethod.POST, "/api/viagens", viagem(numero,clienteId)), HttpStatus.CREATED); }
    private JsonNode importar() { return ok(FluxoImportacaoHttpTest.enviar(http,"sintetico.xml",CteFixture.xml()), HttpStatus.OK); }
    private int total(String tabela) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+tabela, Integer.class); }
    private void bancoVazio() { for (String tabela : new String[]{"clientes","viagens","documentos","envios"}) assertThat(total(tabela)).isZero(); }
    private void semArquivos() throws Exception { try (var paths = Files.walk(RAIZ)) { assertThat(paths.filter(Files::isRegularFile).toList()).isEmpty(); } }

    @Test void cicloClienteComDTOsCadastroConsultaAtualizacaoEExclusao() {
        var criado = cadastrarCliente("00000000000000"); long id = criado.get("id").asLong();
        campos(criado,"id","nome","cnpj","email","telefone");
        assertThat(criado.get("email").isNull()).isTrue();
        assertThat(ok(http.getForEntity("/api/clientes/"+id,JsonNode.class),HttpStatus.OK)).isEqualTo(criado);
        var lista=ok(http.getForEntity("/api/clientes",JsonNode.class),HttpStatus.OK); assertThat(lista.size()).isEqualTo(1); assertThat(lista.get(0)).isEqualTo(criado);
        var atualizado=ok(json(HttpMethod.PUT,"/api/clientes/"+id,Map.of("nome","Nome Atualizado","cnpj","11111111111111","email","teste@example.invalid","telefone","1100000000")),HttpStatus.OK);
        assertThat(atualizado.get("id").asLong()).isEqualTo(id); assertThat(atualizado.get("nome").asText()).isEqualTo("Nome Atualizado");
        assertThat(atualizado.get("email").asText()).isEqualTo("teste@example.invalid");
        var exclusao=json(HttpMethod.DELETE,"/api/clientes/"+id,null); assertThat(exclusao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT); assertThat(exclusao.getBody()).isNull();
        bancoVazio(); erro(http.getForEntity("/api/clientes/"+id,JsonNode.class),HttpStatus.NOT_FOUND);
    }
    @Test void cicloViagemUsaClienteIdSemEntidadesAninhadas() {
        long clienteId=cadastrarCliente("00000000000000").get("id").asLong();
        long outroId=cadastrarCliente("11111111111111").get("id").asLong();
        var criada=cadastrarViagem("66213",clienteId);long id=criada.get("id").asLong();
        campos(criada,"id","numero","data","status","clienteId");
        assertThat(criada.get("clienteId").asLong()).isEqualTo(clienteId);
        assertThat(ok(http.getForEntity("/api/viagens/"+id,JsonNode.class),HttpStatus.OK)).isEqualTo(criada);
        assertThat(ok(http.getForEntity("/api/viagens",JsonNode.class),HttpStatus.OK).get(0)).isEqualTo(criada);
        var atualizada=ok(json(HttpMethod.PUT,"/api/viagens/"+id,viagem("66214",outroId)),HttpStatus.OK);
        assertThat(atualizada.get("clienteId").asLong()).isEqualTo(outroId); assertThat(atualizada.get("numero").asText()).isEqualTo("66214");
        assertThat(ok(http.getForEntity("/api/viagens/"+id+"/documentos",JsonNode.class),HttpStatus.OK).isEmpty()).isTrue();
    }
    @Test void consultasAposImportacaoNaoRecursamNemExpoemCaminhosOuProxies() {
        var importado=importar(); long viagemId=importado.get("viagemId").asLong();
        var documentos=ok(http.getForEntity("/api/documentos",JsonNode.class),HttpStatus.OK);assertThat(documentos.size()).isEqualTo(1);
        var doc=documentos.get(0);campos(doc,"id","nomeArquivo","tipo","chaveAcesso","sha256","viagemId");
        assertThat(doc.get("id")).isEqualTo(importado.get("documentoId"));assertThat(doc.get("viagemId").asLong()).isEqualTo(viagemId);
        assertThat(doc.get("chaveAcesso").asText()).isEqualTo(CteFixture.CHAVE);
        assertThat(doc.get("sha256").asText()).hasSize(64);
        assertThat(ok(http.getForEntity("/api/viagens/"+viagemId+"/documentos",JsonNode.class),HttpStatus.OK)).isEqualTo(documentos);
        var viagens=ok(http.getForEntity("/api/viagens",JsonNode.class),HttpStatus.OK);campos(viagens.get(0),"id","numero","data","status","clienteId");
        long clienteId=viagens.get(0).get("clienteId").asLong();
        var clientes=ok(http.getForEntity("/api/clientes",JsonNode.class),HttpStatus.OK);campos(clientes.get(0),"id","nome","cnpj","email","telefone");
        assertThat(clientes.get(0).get("id").asLong()).isEqualTo(clienteId);
        assertThat(ok(http.getForEntity("/api/clientes/"+clienteId,JsonNode.class),HttpStatus.OK)).isEqualTo(clientes.get(0));
        assertThat(ok(http.getForEntity("/api/viagens/"+viagemId,JsonNode.class),HttpStatus.OK)).isEqualTo(viagens.get(0));
        assertThat(documentos.toString()+viagens+clientes).doesNotContain("caminho",RAIZ.toString(),"hibernateLazyInitializer","handler");
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"nome\":\" \",\"cnpj\":\"00000000000000\"}","{\"nome\":\"Teste\",\"cnpj\":\"123\"}","{\"nome\":\"Teste\",\"cnpj\":\"00000000000000\",\"email\":\"email-invalido\"}"})
    void clienteInvalidoRetorna400ComCamposSemPersistir(String corpo) throws Exception {
        var resposta=erro(json(HttpMethod.POST,"/api/clientes",corpo),HttpStatus.BAD_REQUEST);
        assertThat(resposta.get("campos").isEmpty()).isFalse(); assertThat(resposta.toString()).doesNotContain("email-invalido");bancoVazio();semArquivos();
    }
    @Test void tamanhosAcimaDasColunasRetornam400AntesDoBanco() {
        erro(json(HttpMethod.POST,"/api/clientes",Map.of("nome","x".repeat(256),"cnpj","00000000000000","telefone","1".repeat(256))),HttpStatus.BAD_REQUEST);bancoVazio();
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"numero\":\"0\",\"clienteId\":1}","{\"numero\":\"../1\",\"clienteId\":1}","{\"numero\":\"1234567890\",\"clienteId\":1}","{\"numero\":\"1\",\"clienteId\":0}","{\"numero\":\"1\",\"clienteId\":-1}"})
    void viagemInvalidaRetorna400SemPersistir(String corpo) { erro(json(HttpMethod.POST,"/api/viagens",corpo),HttpStatus.BAD_REQUEST);bancoVazio(); }
    @ParameterizedTest @ValueSource(strings={"{", "null", "[]", "{\"id\":7,\"nome\":\"Teste\",\"cnpj\":\"00000000000000\"}","{\"nome\":\"Teste\",\"cnpj\":\"00000000000000\",\"viagens\":[]}"})
    void corpoInvalidoOuCamposForaDoContratoRetornam400(String corpo) { erro(json(HttpMethod.POST,"/api/clientes",corpo),HttpStatus.BAD_REQUEST);bancoVazio(); }
    @Test void objetoClienteAninhadoEDataInvalidaNaoSaoAceitos() {
        erro(json(HttpMethod.POST,"/api/viagens","{\"numero\":\"1\",\"cliente\":{\"id\":1}}"),HttpStatus.BAD_REQUEST);
        erro(json(HttpMethod.POST,"/api/viagens","{\"numero\":\"1\",\"clienteId\":1,\"data\":\"data-invalida\"}"),HttpStatus.BAD_REQUEST);bancoVazio();
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","abc","9999999999999999999999999999"})
    void idsInvalidosRetornam400(String id) {
        for(String rota:new String[]{"/api/clientes/","/api/viagens/"}) erro(http.getForEntity(rota+id,JsonNode.class),HttpStatus.BAD_REQUEST);
        erro(http.getForEntity("/api/viagens/"+id+"/documentos",JsonNode.class),HttpStatus.BAD_REQUEST);
    }
    @Test void recursosAusentesRetornam404IncluindoConsultaDeDocumentos() {
        for(String rota:new String[]{"/api/clientes/999999","/api/viagens/999999","/api/viagens/999999/documentos","/api/rota-inexistente"}) erro(http.getForEntity(rota,JsonNode.class),HttpStatus.NOT_FOUND);
        erro(json(HttpMethod.PUT,"/api/clientes/999999",cliente("00000000000000")),HttpStatus.NOT_FOUND);
        erro(json(HttpMethod.DELETE,"/api/clientes/999999",null),HttpStatus.NOT_FOUND);
        erro(json(HttpMethod.PUT,"/api/viagens/999999",viagem("1",999999L)),HttpStatus.NOT_FOUND);bancoVazio();
    }
    @Test void clienteIdDecimalNaoETruncadoParaUmClienteExistente() {
        long id=cadastrarCliente("00000000000000").get("id").asLong();
        erro(json(HttpMethod.POST,"/api/viagens","{\"numero\":\"1\",\"clienteId\":"+id+".5}"),HttpStatus.BAD_REQUEST);
        assertThat(total("clientes")).isEqualTo(1);assertThat(total("viagens")).isZero();
    }
    @Test void clienteReferenciadoInexistenteRetorna404SemCriarViagem() {
        erro(json(HttpMethod.POST,"/api/viagens",viagem("1",999999L)),HttpStatus.NOT_FOUND);bancoVazio();
        long id=cadastrarCliente("00000000000000").get("id").asLong();long viagemId=cadastrarViagem("1",id).get("id").asLong();
        erro(json(HttpMethod.PUT,"/api/viagens/"+viagemId,viagem("2",999999L)),HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT numero FROM viagens",String.class)).isEqualTo("1");
    }
    @Test void duplicidadeDeCnpjRetorna409InclusiveAtualizacaoSemAlteracaoParcial() {
        cadastrarCliente("00000000000000");
        erro(json(HttpMethod.POST,"/api/clientes",cliente("00000000000000")),HttpStatus.CONFLICT);
        long id=cadastrarCliente("11111111111111").get("id").asLong();
        erro(json(HttpMethod.PUT,"/api/clientes/"+id,Map.of("nome","Alterado","cnpj","00000000000000")),HttpStatus.CONFLICT);
        assertThat(total("clientes")).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT nome FROM clientes WHERE id=?",String.class,id)).isEqualTo("Cliente Sintético");
        assertThat(jdbc.queryForObject("SELECT cnpj FROM clientes WHERE id=?",String.class,id)).isEqualTo("11111111111111");
    }
    @Test void duplicidadeDeNumeroRetorna409InclusiveAtualizacaoSemAlteracaoParcial() {
        long clienteId=cadastrarCliente("00000000000000").get("id").asLong();cadastrarViagem("1",clienteId);
        erro(json(HttpMethod.POST,"/api/viagens",viagem("1",clienteId)),HttpStatus.CONFLICT);
        long id=cadastrarViagem("2",clienteId).get("id").asLong();
        erro(json(HttpMethod.PUT,"/api/viagens/"+id,viagem("1",clienteId)),HttpStatus.CONFLICT);
        assertThat(total("viagens")).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT numero FROM viagens WHERE id=?",String.class,id)).isEqualTo("2");
    }
    @Test void clienteComViagemNaoPodeSerExcluidoOuTerCnpjTrocado() {
        long id=cadastrarCliente("00000000000000").get("id").asLong();cadastrarViagem("1",id);
        erro(json(HttpMethod.DELETE,"/api/clientes/"+id,null),HttpStatus.CONFLICT);
        erro(json(HttpMethod.PUT,"/api/clientes/"+id,cliente("11111111111111")),HttpStatus.CONFLICT);
        assertThat(total("clientes")).isEqualTo(1);assertThat(total("viagens")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cnpj FROM clientes",String.class)).isEqualTo("00000000000000");
    }
    @Test void identidadeDeViagemComDocumentoFicaProtegidaMasMetadadosPodemMudar() throws Exception {
        var importado=importar();long viagemId=importado.get("viagemId").asLong();
        long clienteId=jdbc.queryForObject("SELECT cliente_id FROM viagens WHERE id=?",Long.class,viagemId);
        long outroId=cadastrarCliente("11111111111111").get("id").asLong();
        erro(json(HttpMethod.PUT,"/api/viagens/"+viagemId,viagem("66214",clienteId)),HttpStatus.CONFLICT);
        erro(json(HttpMethod.PUT,"/api/viagens/"+viagemId,viagem("66213",outroId)),HttpStatus.CONFLICT);
        erro(json(HttpMethod.DELETE,"/api/clientes/"+clienteId,null),HttpStatus.CONFLICT);
        erro(json(HttpMethod.PUT,"/api/clientes/"+clienteId,cliente("22222222222222")),HttpStatus.CONFLICT);
        ok(json(HttpMethod.PUT,"/api/clientes/"+clienteId,Map.of("nome","Nome Corrigido","cnpj","00000000000000","email","teste@example.invalid")),HttpStatus.OK);
        var metadados=new HashMap<String,Object>(viagem("66213",clienteId));metadados.put("status","Conferida");
        assertThat(ok(json(HttpMethod.PUT,"/api/viagens/"+viagemId,metadados),HttpStatus.OK).get("status").asText()).isEqualTo("Conferida");
        assertThat(importar().get("documentoId")).isEqualTo(importado.get("documentoId"));
        assertThat(Files.readString(RAIZ.resolve("66213").resolve(CteFixture.CHAVE+".xml"))).isEqualTo(CteFixture.xml());assertThat(total("documentos")).isEqualTo(1);
    }
    @Test void historicoDeEnvioProtegeClienteEViagemSemDocumentos() {
        long dono=cadastrarCliente("00000000000000").get("id").asLong();long destinatario=cadastrarCliente("11111111111111").get("id").asLong();
        long id=cadastrarViagem("1",dono).get("id").asLong();
        // Histórico sintético inserido diretamente; nenhum envio é executado.
        jdbc.update("INSERT INTO envios (viagem_id,cliente_id,status) VALUES (?,?,'PENDENTE')",id,destinatario);
        erro(json(HttpMethod.DELETE,"/api/clientes/"+destinatario,null),HttpStatus.CONFLICT);
        erro(json(HttpMethod.PUT,"/api/clientes/"+destinatario,cliente("22222222222222")),HttpStatus.CONFLICT);
        erro(json(HttpMethod.PUT,"/api/viagens/"+id,viagem("2",dono)),HttpStatus.CONFLICT);
        erro(json(HttpMethod.PUT,"/api/viagens/"+id,viagem("1",destinatario)),HttpStatus.CONFLICT);
        assertThat(total("envios")).isEqualTo(1);assertThat(total("clientes")).isEqualTo(2);assertThat(total("viagens")).isEqualTo(1);
    }
    @Test void metodoInvalidoPreserva405EHeaderAllow() {
        var resposta=json(HttpMethod.DELETE,"/api/viagens/1",null);erro(resposta,HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(resposta.getHeaders().getAllow()).contains(HttpMethod.GET,HttpMethod.PUT);bancoVazio();
    }
    @Test void contentTypeInvalidoRetorna415() {
        var headers=new HttpHeaders();headers.setContentType(MediaType.TEXT_PLAIN);
        erro(http.postForEntity("/api/clientes",new HttpEntity<>("texto",headers),JsonNode.class),HttpStatus.UNSUPPORTED_MEDIA_TYPE);bancoVazio();
    }
    @Test void multipartSemArquivoRetorna400() {
        var partes=new LinkedMultiValueMap<String,Object>();partes.add("outraParte","texto");
        var headers=new HttpHeaders();headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        erro(http.postForEntity("/api/processamento/xml",new HttpEntity<>(partes,headers),JsonNode.class),HttpStatus.BAD_REQUEST);bancoVazio();
    }
    @Test void multipartAcimaDoLimiteRetorna413SemEfeitos() throws Exception {
        erro(FluxoImportacaoHttpTest.enviar(http,"grande.xml",CteFixture.xml()+" ".repeat(8192)),HttpStatus.PAYLOAD_TOO_LARGE);bancoVazio();semArquivos();
    }
    @Test void importacaoConcorrenteComMudancaDeIdentidadeNaoCorrompeVinculos() throws Exception {
        var existente=importar();long id=existente.get("viagemId").asLong();long clienteId=jdbc.queryForObject("SELECT cliente_id FROM viagens WHERE id=?",Long.class,id);
        var inicio=new CyclicBarrier(2);var executor=Executors.newFixedThreadPool(2);
        try {
            var mudar=executor.submit(()->{inicio.await(10,TimeUnit.SECONDS);return json(HttpMethod.PUT,"/api/viagens/"+id,viagem("66214",clienteId));});
            var reimportar=executor.submit(()->{inicio.await(10,TimeUnit.SECONDS);return FluxoImportacaoHttpTest.enviar(http,"concorrente.xml",CteFixture.xml());});
            erro(mudar.get(30,TimeUnit.SECONDS),HttpStatus.CONFLICT);
            assertThat(ok(reimportar.get(30,TimeUnit.SECONDS),HttpStatus.OK).get("documentoId")).isEqualTo(existente.get("documentoId"));
        } finally {executor.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT numero FROM viagens",String.class)).isEqualTo("66213");
        assertThat(total("clientes")).isEqualTo(1);assertThat(total("viagens")).isEqualTo(1);assertThat(total("documentos")).isEqualTo(1);
    }
}
