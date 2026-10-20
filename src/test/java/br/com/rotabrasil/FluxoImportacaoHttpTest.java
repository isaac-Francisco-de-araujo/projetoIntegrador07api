package br.com.rotabrasil;

import br.com.rotabrasil.service.ConteudoXml;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.util.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Servidor HTTP real, serviços/repositórios reais, H2 migrado e diretório exclusivo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:fluxo-http-etapa5;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FluxoImportacaoHttpTest {
    private static final Path RAIZ = temporario();
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    private static Path temporario() {
        try { return Files.createTempDirectory("rotabrasil-http-etapa5-"); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    @DynamicPropertySource static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("rotabrasil.arquivos.viagens", RAIZ::toString);
    }
    @BeforeEach void limpar() throws Exception {
        jdbc.execute("ALTER TABLE documentos DROP CONSTRAINT IF EXISTS teste_falha_banco");
        for (String tabela : new String[]{"envios", "documentos", "viagens", "clientes"}) jdbc.update("DELETE FROM " + tabela);
        FileSystemUtils.deleteRecursively(RAIZ); Files.createDirectories(RAIZ);
    }
    @AfterAll static void remover() throws Exception { FileSystemUtils.deleteRecursively(RAIZ); }

    static ResponseEntity<JsonNode> enviar(TestRestTemplate http, String nome, String xml) {
        var arquivo = new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return nome; }
        };
        var partes = new LinkedMultiValueMap<String, Object>(); partes.add("arquivo", arquivo);
        var headers = new HttpHeaders(); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.postForEntity("/api/processamento/xml", new HttpEntity<>(partes, headers), JsonNode.class);
    }
    private ResponseEntity<JsonNode> enviar(String nome, String xml) { return enviar(http, nome, xml); }
    private void totais(int clientes, int viagens, int documentos) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clientes", Integer.class)).isEqualTo(clientes);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM viagens", Integer.class)).isEqualTo(viagens);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documentos", Integer.class)).isEqualTo(documentos);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM envios", Integer.class)).isZero();
    }
    private void arquivos(int quantidade) throws Exception {
        try (var paths = Files.walk(RAIZ)) { assertThat(paths.filter(Files::isRegularFile).toList()).hasSize(quantidade); }
        if (Files.isDirectory(RAIZ.resolve(".pendentes"))) {
            try (var paths = Files.list(RAIZ.resolve(".pendentes"))) { assertThat(paths.toList()).isEmpty(); }
        }
    }
    private JsonNode sucesso(ResponseEntity<JsonNode> resposta) {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        var body = resposta.getBody(); assertThat(body).isNotNull();
        assertThat(body.get("sucesso").asBoolean()).isTrue();
        assertThat(body.get("documentoId").asLong()).isPositive();
        assertThat(body.get("viagemId").asLong()).isPositive();
        return body;
    }
    private void erro(ResponseEntity<JsonNode> resposta, HttpStatus status) {
        assertThat(resposta.getStatusCode()).isEqualTo(status);
        var body = resposta.getBody(); assertThat(body).isNotNull();
        assertThat(body.get("status").asInt()).isEqualTo(status.value());
        assertThat(body.get("mensagem").asText()).isNotBlank();
        assertThat(body.hasNonNull("timestamp")).isTrue();
        assertThat(body.has("trace")).isFalse(); assertThat(body.has("exception")).isFalse();
    }
    @Test void xmlValidoPersisteRelacionamentosEBytesExatos() throws Exception {
        var body = sucesso(enviar("original.xml", CteFixture.xml()));
        var linha = jdbc.queryForMap("SELECT d.nome_arquivo,d.chave_acesso,d.sha256,d.caminho,v.id viagem_id,v.numero,v.data,c.nome,c.cnpj FROM documentos d JOIN viagens v ON v.id=d.viagem_id JOIN clientes c ON c.id=v.cliente_id WHERE d.id=?", body.get("documentoId").asLong());
        assertThat(linha.get("VIAGEM_ID")).isEqualTo(body.get("viagemId").asLong());
        assertThat(linha.get("NUMERO")).isEqualTo("66213");
        assertThat(linha.get("CNPJ")).isEqualTo("00000000000000");
        assertThat(linha.get("NOME")).isEqualTo("Cliente Teste");
        assertThat(linha.get("DATA").toString()).isEqualTo("2025-04-04");
        assertThat(linha.get("NOME_ARQUIVO")).isEqualTo("original.xml");
        assertThat(linha.get("CHAVE_ACESSO")).isEqualTo(CteFixture.CHAVE);
        assertThat(linha.get("SHA256")).isEqualTo(ConteudoXml.sha256(CteFixture.xml().getBytes(StandardCharsets.UTF_8)));
        Path destino = RAIZ.resolve("66213").resolve(CteFixture.CHAVE + ".xml");
        assertThat(Path.of((String) linha.get("CAMINHO"))).isEqualTo(destino);
        assertThat(Files.readString(destino)).isEqualTo(CteFixture.xml());
        totais(1,1,1); arquivos(1);
    }
    @Test void reimportarComMesmoNomeOuOutroNomeReutilizaIdsSemAlterarArquivo() throws Exception {
        var original = sucesso(enviar("original.xml", CteFixture.xml()));
        Path destino = RAIZ.resolve("66213").resolve(CteFixture.CHAVE + ".xml");
        var modificacao = Files.getLastModifiedTime(destino);
        for (String nome : new String[]{"original.xml", "renomeado.xml"}) {
            var repetido = sucesso(enviar(nome, CteFixture.xml()));
            assertThat(repetido.get("documentoId")).isEqualTo(original.get("documentoId"));
            assertThat(repetido.get("viagemId")).isEqualTo(original.get("viagemId"));
        }
        assertThat(Files.getLastModifiedTime(destino)).isEqualTo(modificacao);
        assertThat(Files.readString(destino)).isEqualTo(CteFixture.xml());
        assertThat(jdbc.queryForObject("SELECT nome_arquivo FROM documentos", String.class)).isEqualTo("original.xml");
        totais(1,1,1); arquivos(1);
    }
    @Test void documentosDistintosComMesmoNomeNaoColidem() throws Exception {
        String outro = CteFixture.xml().replace(CteFixture.CHAVE, CteFixture.outraChave());
        var primeiro = sucesso(enviar("igual.xml", CteFixture.xml()));
        var segundo = sucesso(enviar("igual.xml", outro));
        assertThat(segundo.get("documentoId")).isNotEqualTo(primeiro.get("documentoId"));
        assertThat(segundo.get("viagemId")).isEqualTo(primeiro.get("viagemId"));
        assertThat(Files.readString(RAIZ.resolve("66213").resolve(CteFixture.CHAVE + ".xml"))).isEqualTo(CteFixture.xml());
        assertThat(Files.readString(RAIZ.resolve("66213").resolve(CteFixture.outraChave() + ".xml"))).isEqualTo(outro);
        totais(1,1,2); arquivos(2);
    }
    @ParameterizedTest @ValueSource(strings = {"", "<CTe>", "<outro/>", "sem-namespace", "versao-invalida", "numero-divergente"})
    void entradaInvalidaRetorna400SemEfeitos(String entrada) throws Exception {
        String xml = switch (entrada) {
            case "sem-namespace" -> CteFixture.xml().replace("xmlns=\"http://www.portalfiscal.inf.br/cte\"", "");
            case "versao-invalida" -> CteFixture.xml().replace("4.00", "3.00");
            case "numero-divergente" -> CteFixture.xml().replace("<nCT>66213</nCT>", "<nCT>1</nCT>");
            default -> entrada;
        };
        erro(enviar("invalido.xml", xml), HttpStatus.BAD_REQUEST); totais(0,0,0); arquivos(0);
    }
    @Test void extensaoNaoXmlRetorna400() throws Exception {
        erro(enviar("arquivo.zip", CteFixture.xml()), HttpStatus.BAD_REQUEST); totais(0,0,0); arquivos(0);
    }
    @Test void conflitoDeConteudoRetorna409EPreservaPrimeiroArquivo() throws Exception {
        sucesso(enviar("primeiro.xml", CteFixture.xml()));
        erro(enviar("segundo.xml", CteFixture.xml()+"\n"), HttpStatus.CONFLICT);
        assertThat(Files.readString(RAIZ.resolve("66213").resolve(CteFixture.CHAVE+".xml"))).isEqualTo(CteFixture.xml());
        totais(1,1,1); arquivos(1);
    }
    @Test void falhaRealDeDiscoRetorna500ReverteBancoEPreservaObstaculo() throws Exception {
        Files.writeString(RAIZ.resolve(".pendentes"), "obstaculo preexistente");
        erro(enviar("disco.xml", CteFixture.xml()), HttpStatus.INTERNAL_SERVER_ERROR);
        totais(0,0,0); arquivos(1);
        assertThat(Files.readString(RAIZ.resolve(".pendentes"))).isEqualTo("obstaculo preexistente");
        Files.delete(RAIZ.resolve(".pendentes"));
        sucesso(enviar("disco.xml", CteFixture.xml())); totais(1,1,1); arquivos(1);
    }
    @Test void violacaoRealNoH2Retorna409ReverteClienteViagemETemporarios() throws Exception {
        jdbc.execute("ALTER TABLE documentos ADD CONSTRAINT teste_falha_banco CHECK (nome_arquivo <> 'falha-banco.xml')");
        try {
            erro(enviar("falha-banco.xml", CteFixture.xml()), HttpStatus.CONFLICT);
            totais(0,0,0); arquivos(0);
        } finally { jdbc.execute("ALTER TABLE documentos DROP CONSTRAINT teste_falha_banco"); }
        sucesso(enviar("recuperado.xml", CteFixture.xml())); totais(1,1,1); arquivos(1);
    }
    @Test void entidadeExternaDeArquivoNaoExfiltraConteudo() throws Exception {
        Path segredo = Files.createTempFile("rotabrasil-segredo-sintetico-", ".txt");
        try {
            Files.writeString(segredo, "MARCADOR_PRIVADO_SINTETICO");
            String xml = "<!DOCTYPE cteProc [<!ENTITY segredo SYSTEM '"+segredo.toUri()+"'>]>" + CteFixture.xml().replace("Cliente Teste", "&segredo;");
            var resposta = enviar("xxe.xml", xml); erro(resposta, HttpStatus.BAD_REQUEST);
            assertThat(resposta.getBody().toString()).doesNotContain("MARCADOR_PRIVADO_SINTETICO");
            assertThat(Files.readString(segredo)).isEqualTo("MARCADOR_PRIVADO_SINTETICO");
            totais(0,0,0); arquivos(0);
        } finally { Files.delete(segredo); }
    }
    @Test void dtdRemotaNaoProvocaRequisicaoExterna() throws Exception {
        var acessos = new AtomicInteger();
        var servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/externo.dtd", exchange -> {
            acessos.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        servidor.start();
        try {
            // Confirma que a armadilha está acessível antes da tentativa de XXE.
            String url = "http://127.0.0.1:"+servidor.getAddress().getPort()+"/externo.dtd";
            try (var entrada = new java.net.URI(url).toURL().openStream()) { entrada.readAllBytes(); }
            assertThat(acessos.get()).isEqualTo(1); acessos.set(0);
            erro(enviar("dtd.xml", "<!DOCTYPE cteProc SYSTEM '"+url+"'>"+CteFixture.xml()), HttpStatus.BAD_REQUEST);
            assertThat(acessos.get()).isZero(); totais(0,0,0); arquivos(0);
        } finally { servidor.stop(0); }
    }
}
