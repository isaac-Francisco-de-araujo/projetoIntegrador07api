package br.com.rotabrasil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class ReinicioImportacaoHttpTest {
    @TempDir Path base;
    private ConfigurableApplicationContext iniciar(boolean migrar) {
        return new SpringApplicationBuilder(RotaBrasilApplication.class).web(WebApplicationType.SERVLET).run(
                "--server.port=0", "--spring.datasource.url=jdbc:h2:file:"+base.resolve("banco"),
                "--spring.flyway.enabled="+migrar, "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.h2.console.enabled=false", "--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                "--rotabrasil.arquivos.viagens="+base.resolve("viagens"));
    }
    private TestRestTemplate http(ConfigurableApplicationContext contexto) {
        int porta = ((ServletWebServerApplicationContext) contexto).getWebServer().getPort();
        return new TestRestTemplate(new org.springframework.boot.web.client.RestTemplateBuilder().rootUri("http://127.0.0.1:"+porta));
    }
    @Test void reinicioDoServidorPreservaIdsRelacionamentosEArquivoSemRemigrar() throws Exception {
        long documentoId, viagemId; Path caminho;
        try (var primeira = iniciar(true)) {
            var resposta = FluxoImportacaoHttpTest.enviar(http(primeira), "antes.xml", CteFixture.xml());
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            documentoId = resposta.getBody().get("documentoId").asLong();
            viagemId = resposta.getBody().get("viagemId").asLong();
            caminho = Path.of(primeira.getBean(JdbcTemplate.class).queryForObject("SELECT caminho FROM documentos WHERE id=?", String.class, documentoId));
            assertThat(Files.readString(caminho)).isEqualTo(CteFixture.xml());
        }
        assertThat(base.resolve("banco.mv.db")).exists();
        var modificacao = Files.getLastModifiedTime(caminho);
        try (var segunda = iniciar(false)) {
            var jdbc = segunda.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documentos d JOIN viagens v ON v.id=d.viagem_id JOIN clientes c ON c.id=v.cliente_id WHERE d.id=? AND v.id=? AND v.numero='66213' AND c.cnpj='00000000000000'", Integer.class, documentoId, viagemId)).isEqualTo(1);
            var resposta = FluxoImportacaoHttpTest.enviar(http(segunda), "apos-reinicio.xml", CteFixture.xml());
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody().get("documentoId").asLong()).isEqualTo(documentoId);
            assertThat(resposta.getBody().get("viagemId").asLong()).isEqualTo(viagemId);
            for (String tabela : new String[]{"clientes", "viagens", "documentos"}) {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+tabela, Integer.class)).isEqualTo(1);
            }
            assertThat(jdbc.queryForObject("SELECT nome_arquivo FROM documentos", String.class)).isEqualTo("antes.xml");
            assertThat(Files.readString(caminho)).isEqualTo(CteFixture.xml());
            assertThat(Files.getLastModifiedTime(caminho)).isEqualTo(modificacao);
            try (var paths = Files.walk(base.resolve("viagens"))) { assertThat(paths.filter(Files::isRegularFile).toList()).containsExactly(caminho); }
            try (var paths = Files.list(base.resolve("viagens/.pendentes"))) { assertThat(paths.toList()).isEmpty(); }
        }
    }
}
