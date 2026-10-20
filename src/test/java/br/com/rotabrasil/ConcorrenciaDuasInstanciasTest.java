package br.com.rotabrasil;

import br.com.rotabrasil.repository.*;
import br.com.rotabrasil.service.ProcessamentoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class ConcorrenciaDuasInstanciasTest {
    @TempDir Path base;
    private ConfigurableApplicationContext iniciar(boolean migrar) {
        return new SpringApplicationBuilder(RotaBrasilApplication.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=jdbc:h2:file:"+base.resolve("banco")+";LOCK_TIMEOUT=15000",
                "--spring.flyway.enabled="+migrar, "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.h2.console.enabled=false", "--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                "--rotabrasil.arquivos.viagens="+base.resolve("viagens"));
    }
    private MockMultipartFile upload() { return new MockMultipartFile("arquivo", "sintetico.xml", "application/xml", CteFixture.xml().getBytes(StandardCharsets.UTF_8)); }
    @Test void coordenaInstanciasIndependentesEPreservaDocumentoAposReinicio() throws Exception {
        long id;
        try (var primeira=iniciar(true); var segunda=iniciar(true)) {
            var servicos=List.of(primeira.getBean(ProcessamentoService.class), segunda.getBean(ProcessamentoService.class));
            var inicio=new CyclicBarrier(6); var executor=Executors.newFixedThreadPool(6);
            try {
                var tarefas=new ArrayList<Future<Long>>();
                for(int i=0;i<6;i++) {
                    var servico=servicos.get(i%2);
                    tarefas.add(executor.submit(() -> { inicio.await(10,TimeUnit.SECONDS); return servico.processarArquivoXml(upload()).documentoId(); }));
                }
                var ids=new ArrayList<Long>(); for(var tarefa:tarefas) ids.add(tarefa.get(30,TimeUnit.SECONDS));
                assertThat(ids.stream().distinct().toList()).hasSize(1); id=ids.getFirst();
                assertThat(primeira.getBean(DocumentoRepository.class).count()).isEqualTo(1);
                assertThat(segunda.getBean(ClienteRepository.class).count()).isEqualTo(1);
                assertThat(segunda.getBean(ViagemRepository.class).count()).isEqualTo(1);
            } finally { executor.shutdownNow(); }
        }
        try(var reiniciada=iniciar(false)) {
            assertThat(reiniciada.getBean(ProcessamentoService.class).processarArquivoXml(upload()).documentoId()).isEqualTo(id);
            var documento=reiniciada.getBean(DocumentoRepository.class).findById(id).orElseThrow();
            assertThat(Files.readString(Path.of(documento.getCaminho()))).isEqualTo(CteFixture.xml());
            assertThat(reiniciada.getBean(DocumentoRepository.class).count()).isEqualTo(1);
        }
    }

    @Test void esquemaSemRestricaoFiscalNaoInicializaAplicacao() throws Exception {
        try(var contexto=iniciar(true)) {
            contexto.getBean(org.springframework.jdbc.core.JdbcTemplate.class)
                    .execute("ALTER TABLE documentos DROP CONSTRAINT uk_documentos_chave");
        }
        assertThatThrownBy(() -> { try(var contexto=iniciar(false)) { /* Deve falhar antes de ficar pronto. */ } })
                .isInstanceOf(Exception.class).hasStackTraceContaining("Migrações de integridade incompletas");
        assertThat(base.resolve("banco.mv.db")).exists();
    }
}
