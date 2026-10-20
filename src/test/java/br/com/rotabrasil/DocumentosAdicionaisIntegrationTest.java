package br.com.rotabrasil;

import br.com.rotabrasil.manutencao.*;
import br.com.rotabrasil.model.Documento;
import br.com.rotabrasil.repository.*;
import br.com.rotabrasil.service.DocumentoService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Documentos não XML são fixtures preexistentes; não representam um fluxo de upload/associação. */
class DocumentosAdicionaisIntegrationTest {
    @TempDir Path base;
    private ConfigurableApplicationContext contexto;
    @BeforeEach void iniciar() { contexto = abrir(true); }
    @AfterEach void encerrar() { if (contexto != null) contexto.close(); }
    private ConfigurableApplicationContext abrir(boolean migrar) {
        return new SpringApplicationBuilder(RotaBrasilApplication.class).web(WebApplicationType.SERVLET).run(
                "--server.port=0", "--spring.datasource.url=jdbc:h2:file:"+base.resolve("banco"),
                "--spring.flyway.enabled="+migrar, "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.open-in-view=false",
                "--spring.h2.console.enabled=false", "--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                "--rotabrasil.arquivos.viagens="+raiz());
    }
    private Path raiz() { return base.resolve("viagens"); }
    private <T> T bean(Class<T> tipo) { return contexto.getBean(tipo); }
    private JdbcTemplate jdbc() { return bean(JdbcTemplate.class); }
    private TransactionTemplate transacao() { return new TransactionTemplate(bean(PlatformTransactionManager.class)); }
    private TestRestTemplate http() {
        int porta = ((ServletWebServerApplicationContext) contexto).getWebServer().getPort();
        return new TestRestTemplate(new RestTemplateBuilder().rootUri("http://127.0.0.1:"+porta));
    }
    private JsonNode get(String rota) {
        var resposta = http().getForEntity(rota,JsonNode.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);return resposta.getBody();
    }
    private JsonNode importar(String xml) {
        var resposta = FluxoImportacaoHttpTest.enviar(http(),"sintetico.xml",xml);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);return resposta.getBody();
    }
    private JsonNode post(String rota, Object body) {
        var resposta=http().postForEntity(rota,body,JsonNode.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);return resposta.getBody();
    }
    private long viagemVazia(String numero) {
        long clienteId;
        var clientes=get("/api/clientes");
        if(clientes.isEmpty()) clienteId=post("/api/clientes",Map.of("nome","Cliente Sintético","cnpj","00000000000000")).get("id").asLong();
        else clienteId=clientes.get(0).get("id").asLong();
        return post("/api/viagens",Map.of("numero",numero,"clienteId",clienteId)).get("id").asLong();
    }
    private record Adicional(long id, Path caminho, byte[] bytes) {}
    private Adicional adicionarFixture(long viagemId,String tipo,String nome,String pasta) throws Exception {
        // Inserção exclusiva da fixture para simular acervo já cadastrado, sem inferir associação por nome.
        byte[] bytes=(tipo.equals("PDF")?"%PDF-1.4\n% fixture sintética "+pasta+"\n%%EOF\n":"Fixture sintética "+pasta).getBytes(StandardCharsets.UTF_8);
        Path caminho=raiz().resolve(pasta).resolve(nome);Files.createDirectories(caminho.getParent());Files.write(caminho,bytes,StandardOpenOption.CREATE_NEW);
        long id=transacao().execute(status -> {
            var viagem=bean(ViagemRepository.class).findById(viagemId).orElseThrow();
            return bean(DocumentoRepository.class).saveAndFlush(Documento.builder().tipo(tipo).nomeArquivo(nome)
                    .caminho(caminho.toString()).viagem(viagem).build()).getId();
        });
        return new Adicional(id,caminho,bytes);
    }
    private Map<Long,JsonNode> porId(JsonNode documentos) {
        var resultado=new TreeMap<Long,JsonNode>();documentos.forEach(d->resultado.put(d.get("id").asLong(),d));return resultado;
    }
    private void preservado(Adicional adicional) throws Exception {
        assertThat(Files.readAllBytes(adicional.caminho())).isEqualTo(adicional.bytes());
        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM documentos WHERE id=?",Integer.class,adicional.id())).isEqualTo(1);
    }
    private void conflito(HttpMethod metodo,String rota,Object body) {
        var resposta=http().exchange(rota,metodo,new HttpEntity<>(body),JsonNode.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test void consultaMultiplosXmlPdfEOutroTipoSemLimitarQuantidadeOuExporCaminho() throws Exception {
        var primeiro=importar(CteFixture.xml());long viagemId=primeiro.get("viagemId").asLong();
        importar(CteFixture.xml().replace(CteFixture.CHAVE,CteFixture.outraChave()));
        var pdf1=adicionarFixture(viagemId,"PDF","comprovante.pdf","pdf-1");
        var pdf2=adicionarFixture(viagemId,"PDF","comprovante.pdf","pdf-2");
        var outro=adicionarFixture(viagemId,"TXT","observacoes.txt","outro");
        var documentos=get("/api/viagens/"+viagemId+"/documentos");assertThat(documentos.size()).isEqualTo(5);
        assertThat(porId(get("/api/documentos"))).isEqualTo(porId(documentos));
        var tipos=new ArrayList<String>();
        documentos.forEach(d->{
            tipos.add(d.get("tipo").asText());assertThat(d.get("viagemId").asLong()).isEqualTo(viagemId);
            assertThat(d.has("caminho")).isFalse();assertThat(d.has("viagem")).isFalse();
            if(!d.get("tipo").asText().equals("XML")) {
                assertThat(d.get("chaveAcesso").isNull()).isTrue();assertThat(d.get("sha256").isNull()).isTrue();
            }
        });
        assertThat(tipos).containsExactlyInAnyOrder("XML","XML","PDF","PDF","TXT");
        assertThat(documentos.toString()).doesNotContain(base.toString());preservado(pdf1);preservado(pdf2);preservado(outro);
        transacao().executeWithoutResult(status -> assertThat(bean(ViagemRepository.class).findById(viagemId).orElseThrow().getDocumentos()).hasSize(5));
    }
    @Test void reimportarXmlNaoAdotaNemModificaDocumentosAdicionais() throws Exception {
        var primeiro=importar(CteFixture.xml());long viagemId=primeiro.get("viagemId").asLong();
        var pdf=adicionarFixture(viagemId,"PDF","comprovante.pdf","pdf");
        var antes=porId(get("/api/documentos"));
        assertThat(importar(CteFixture.xml()).get("documentoId")).isEqualTo(primeiro.get("documentoId"));
        assertThat(porId(get("/api/documentos"))).isEqualTo(antes);preservado(pdf);
    }
    @Test void limparColecaoInversaNaoExcluiDocumentosOuArquivos() throws Exception {
        long viagemId=importar(CteFixture.xml()).get("viagemId").asLong();
        var pdf=adicionarFixture(viagemId,"PDF","comprovante.pdf","pdf");var antes=porId(get("/api/documentos"));
        transacao().executeWithoutResult(status -> {
            var repository=bean(ViagemRepository.class);var viagem=repository.findById(viagemId).orElseThrow();
            assertThat(viagem.getDocumentos()).hasSize(2);viagem.getDocumentos().clear();repository.flush();
        });
        assertThat(porId(get("/api/documentos"))).isEqualTo(antes);preservado(pdf);
        assertThat(Files.readString(raiz().resolve("66213").resolve(CteFixture.CHAVE+".xml"))).isEqualTo(CteFixture.xml());
    }
    @Test void exclusaoDiretaDeViagemComDocumentosFalhaSemCascataDestrutiva() throws Exception {
        long viagemId=importar(CteFixture.xml()).get("viagemId").asLong();
        var pdf=adicionarFixture(viagemId,"PDF","comprovante.pdf","pdf");var antes=porId(get("/api/documentos"));
        assertThatThrownBy(()->transacao().executeWithoutResult(status -> {
            var repository=bean(ViagemRepository.class);repository.delete(repository.findById(viagemId).orElseThrow());repository.flush();
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(porId(get("/api/documentos"))).isEqualTo(antes);preservado(pdf);
        assertThat(get("/api/viagens/"+viagemId).get("id").asLong()).isEqualTo(viagemId);
        assertThat(Files.readString(raiz().resolve("66213").resolve(CteFixture.CHAVE+".xml"))).isEqualTo(CteFixture.xml());
    }
    @Test void pdfSemXmlTambemProtegeIdentidadeDaViagemECliente() throws Exception {
        long viagemId=viagemVazia("1");var pdf=adicionarFixture(viagemId,"PDF","comprovante.pdf","pdf");
        long clienteId=get("/api/viagens/"+viagemId).get("clienteId").asLong();
        long outroCliente=post("/api/clientes",Map.of("nome","Outro Sintético","cnpj","11111111111111")).get("id").asLong();
        conflito(HttpMethod.PUT,"/api/viagens/"+viagemId,Map.of("numero","2","clienteId",clienteId));
        conflito(HttpMethod.PUT,"/api/viagens/"+viagemId,Map.of("numero","1","clienteId",outroCliente));
        conflito(HttpMethod.DELETE,"/api/clientes/"+clienteId,null);
        assertThat(get("/api/viagens/"+viagemId).get("numero").asText()).isEqualTo("1");preservado(pdf);
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"PDF","TXT",""})
    void cadastroXmlNaoSeTornaCadastroGenericoDeAnexos(String tipo) {
        long viagemId=viagemVazia("1");
        var documento=Documento.builder().nomeArquivo("fixture").tipo(tipo).caminho(base.resolve("nao-criado").toString())
                .chaveAcesso(CteFixture.CHAVE).sha256("0".repeat(64)).viagem(bean(ViagemRepository.class).findById(viagemId).orElseThrow()).build();
        assertThatThrownBy(()->bean(DocumentoService.class).registrarDocumento(documento)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("somente documentos XML");
        assertThat(bean(DocumentoRepository.class).count()).isZero();assertThat(base.resolve("nao-criado")).doesNotExist();
    }
    @Test void documentosNaoXmlNaoEnfraquecemRestricaoDeIdentidadeXml() throws Exception {
        long viagemId=viagemVazia("1");var pdf=adicionarFixture(viagemId,"PDF","comprovante.pdf","pdf");
        assertThatThrownBy(()->transacao().executeWithoutResult(status -> {
            var viagem=bean(ViagemRepository.class).findById(viagemId).orElseThrow();
            bean(DocumentoRepository.class).saveAndFlush(Documento.builder().tipo("XML").nomeArquivo("invalido.xml").caminho(base.resolve("nao-criado").toString()).viagem(viagem).build());
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(bean(DocumentoRepository.class).count()).isEqualTo(1);preservado(pdf);
    }
    @Test void consultasSeparamDocumentosDeViagensDistintasMesmoComMesmoNome() throws Exception {
        long primeira=viagemVazia("1"),segunda=viagemVazia("2");
        var a=adicionarFixture(primeira,"PDF","mesmo.pdf","primeira");var b=adicionarFixture(segunda,"PDF","mesmo.pdf","segunda");
        assertThat(porId(get("/api/viagens/"+primeira+"/documentos")).keySet()).containsExactly(a.id());
        assertThat(porId(get("/api/viagens/"+segunda+"/documentos")).keySet()).containsExactly(b.id());
        assertThat(get("/api/documentos").size()).isEqualTo(2);preservado(a);preservado(b);
    }
    @Test void reinicioPreservaColecaoMistaESuaIdentidadeSemMigrarOuReassociar() throws Exception {
        var primeiro=importar(CteFixture.xml());long viagemId=primeiro.get("viagemId").asLong();
        var pdf=adicionarFixture(viagemId,"PDF","comprovante.pdf","pdf");var texto=adicionarFixture(viagemId,"TXT","notas.txt","texto");
        var antes=porId(get("/api/documentos"));contexto.close();contexto=abrir(false);
        assertThat(porId(get("/api/viagens/"+viagemId+"/documentos"))).isEqualTo(antes);
        assertThat(importar(CteFixture.xml()).get("documentoId")).isEqualTo(primeiro.get("documentoId"));
        assertThat(porId(get("/api/documentos"))).isEqualTo(antes);preservado(pdf);preservado(texto);
    }
    @Test void inventarioEBackupIncluemOutrosTiposSemInterpretarComoXml() throws Exception {
        long viagemId=importar(CteFixture.xml()).get("viagemId").asLong();
        var pdf=adicionarFixture(viagemId,"PDF","comprovante.pdf","pdf");var texto=adicionarFixture(viagemId,"TXT","notas.txt","texto");
        // Sem requisições em curso; trava exclusiva durante o backup da fixture.
        try(var conexao=bean(DataSource.class).getConnection();var comando=conexao.createStatement()) {
            comando.execute("SET EXCLUSIVE 1");
            try {
                var inventario=new InventarioBanco(raiz()).analisar(conexao);
                assertThat(inventario.aprovado()).isTrue();assertThat(inventario.identidades()).hasSize(1);assertThat(inventario.arquivos()).hasSize(3);
                BackupBanco.criar(conexao,raiz(),base.resolve("backup"));
                assertThat(BackupBanco.verificar(base.resolve("backup")).arquivos()).hasSize(3);
            } finally {comando.execute("SET EXCLUSIVE 0");}
        }
        assertThat(Files.readAllBytes(base.resolve("backup/arquivos/pdf/comprovante.pdf"))).isEqualTo(pdf.bytes());
        assertThat(Files.readAllBytes(base.resolve("backup/arquivos/texto/notas.txt"))).isEqualTo(texto.bytes());preservado(pdf);preservado(texto);
    }
}
