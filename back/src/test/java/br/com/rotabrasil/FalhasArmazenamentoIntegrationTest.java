package br.com.rotabrasil;

import br.com.rotabrasil.exception.ArmazenamentoException;
import br.com.rotabrasil.model.Documento;
import br.com.rotabrasil.repository.ClienteRepository;
import br.com.rotabrasil.repository.DocumentoRepository;
import br.com.rotabrasil.repository.ViagemRepository;
import br.com.rotabrasil.service.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static br.com.rotabrasil.service.ReconciliacaoArmazenamentoService.Estado.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:falhas-etapa3;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true", "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FalhasArmazenamentoIntegrationTest {
    private static final Path RAIZ = temporario();
    @Autowired MockMvc mvc;
    @Autowired ClienteRepository clientes;
    @Autowired ViagemRepository viagens;
    @Autowired ProcessamentoService processamento;
    @Autowired ArmazenamentoTransacionalService armazenamento;
    @Autowired ReconciliacaoArmazenamentoService reconciliacao;
    @Autowired PlatformTransactionManager transacoes;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean OrganizadorArquivosService arquivos;
    @MockitoSpyBean DocumentoRepository documentos;

    private static Path temporario() {
        try { return Files.createTempDirectory("rotabrasil-falhas-"); }
        catch (IOException ex) { throw new IllegalStateException(ex); }
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("rotabrasil.arquivos.viagens", RAIZ::toString);
    }

    @BeforeEach
    void limpar() throws IOException {
        reset(arquivos, documentos);
        documentos.deleteAll();
        viagens.deleteAll();
        clientes.deleteAll();
        FileSystemUtils.deleteRecursively(RAIZ);
        Files.createDirectories(RAIZ);
    }

    @AfterAll
    static void removerTemporarios() throws IOException {
        FileSystemUtils.deleteRecursively(RAIZ);
    }

    private byte[] conteudo() { return CteFixture.xml().getBytes(StandardCharsets.UTF_8); }
    private MockMultipartFile upload() { return new MockMultipartFile("arquivo", "teste.xml", "application/xml", conteudo()); }
    private Path destino() { return RAIZ.resolve("66213").resolve(CteFixture.CHAVE + ".xml"); }

    private void enviar(int codigo) throws Exception {
        mvc.perform(multipart("/api/processamento/xml").file(upload())).andExpect(status().is(codigo));
    }

    private void semRegistros() {
        assertThat(documentos.count()).isZero();
        assertThat(viagens.count()).isZero();
        assertThat(clientes.count()).isZero();
    }

    private void semPendencias() {
        assertThat(reconciliacao.inspecionar()).isEmpty();
    }

    @Test
    void semTransacaoNaoCriaArquivo() {
        assertThatThrownBy(() -> armazenamento.preparar(conteudo(), "66213", CteFixture.CHAVE))
                .isInstanceOf(IllegalStateException.class);
        assertThat(destino()).doesNotExist();
        semRegistros();
        semPendencias();
    }

    @Test
    void falhaDePreparacaoRetorna500EPreservaArtefatoPreexistente() throws Exception {
        Files.writeString(RAIZ.resolve(".pendentes"), "preexistente");
        enviar(500);
        semRegistros();
        assertThat(destino()).doesNotExist();
        assertThat(Files.readString(RAIZ.resolve(".pendentes"))).isEqualTo("preexistente");
    }

    @Test
    void falhaNoBancoAntesDaPublicacaoRemoveTemporario() throws Exception {
        doThrow(new DataIntegrityViolationException("Falha simulada no banco"))
                .when(documentos).saveAndFlush(any(Documento.class));
        enviar(409);
        semRegistros();
        assertThat(destino()).doesNotExist();
        semPendencias();
        verify(arquivos, never()).publicar(any());
    }

    @Test
    void publicacaoIndisponivelNaoUsaCopiaInseguraENaoPersisteDocumento() throws Exception {
        doThrow(new ArmazenamentoException("Hard link indisponível")).when(arquivos).publicar(any());
        enviar(500);
        semRegistros();
        assertThat(destino()).doesNotExist();
        semPendencias();
    }

    @Test
    void falhaDepoisDePublicarCompensaOArquivoEOBanco() throws Exception {
        doAnswer(invocacao -> {
            invocacao.callRealMethod();
            assertThat(destino()).exists();
            throw new ArmazenamentoException("Falha simulada após publicar");
        }).when(arquivos).publicar(any());
        enviar(500);
        semRegistros();
        assertThat(destino()).doesNotExist();
        semPendencias();
    }

    @Test
    void violacaoRealNoBancoAposPublicarReverteRegistrosEArquivo() {
        var publicado = new AtomicBoolean();
        assertThatThrownBy(() -> new TransactionTemplate(transacoes).execute(status -> {
            processamento.processarArquivoXml(upload());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    assertThat(destino()).exists();
                    publicado.set(true);
                    // Viola a restrição real de CNPJ depois da publicação e antes do commit.
                    jdbc.update("INSERT INTO clientes (nome, cnpj) VALUES (?, ?)", "Duplicado", "00000000000000");
                }
            });
            return null;
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(publicado).isTrue();
        semRegistros();
        assertThat(destino()).doesNotExist();
        semPendencias();
    }

    @Test
    void rollbackExplicitoAntesDoCommitNaoPublicaArquivo() {
        new TransactionTemplate(transacoes).executeWithoutResult(status -> {
            processamento.processarArquivoXml(upload());
            assertThat(destino()).doesNotExist();
            status.setRollbackOnly();
        });
        semRegistros();
        assertThat(destino()).doesNotExist();
        semPendencias();
    }

    @Test
    void falhaDeLimpezaDepoisDoCommitNaoRetornaFalsoErroEPermiteRecuperacao() throws Exception {
        doThrow(new ArmazenamentoException("Limpeza indisponível")).when(arquivos).confirmar(any());
        enviar(200);
        assertThat(documentos.count()).isEqualTo(1);
        Long id = documentos.findAll().getFirst().getId();
        assertThat(Files.readAllBytes(destino())).isEqualTo(conteudo());
        assertThat(reconciliacao.inspecionar()).extracting(ReconciliacaoArmazenamentoService.Pendencia::estado)
                .containsExactly(COMMIT_CONFIRMADO);
        doCallRealMethod().when(arquivos).confirmar(any());
        assertThat(reconciliacao.recuperarConfirmadas()).extracting(ReconciliacaoArmazenamentoService.Pendencia::estado)
                .containsExactly(RECUPERADA);
        semPendencias();
        enviar(200);
        assertThat(documentos.count()).isEqualTo(1);
        assertThat(documentos.findAll().getFirst().getId()).isEqualTo(id);
    }

    @Test
    void rollbackComFalhaDeLimpezaDeixaPendenciaSemAdotarDocumento() throws Exception {
        doThrow(new DataIntegrityViolationException("Falha no banco")).when(documentos).saveAndFlush(any(Documento.class));
        doThrow(new ArmazenamentoException("Falha na limpeza")).when(arquivos).desfazer(any());
        enviar(409);
        semRegistros();
        assertThat(destino()).doesNotExist();
        assertThat(reconciliacao.recuperarConfirmadas()).extracting(ReconciliacaoArmazenamentoService.Pendencia::estado)
                .containsExactly(PREPARADO_SEM_REGISTRO);
    }

    @Test
    void reconciliacaoIgnoraOperacaoComTravaAtiva() throws Exception {
        try (var operacao = arquivos.prepararXml(conteudo(), "66213", CteFixture.CHAVE)) {
            assertThat(reconciliacao.recuperarConfirmadas()).extracting(ReconciliacaoArmazenamentoService.Pendencia::estado)
                    .containsExactly(EM_ANDAMENTO);
            assertThat(operacao.temporario()).exists();
            arquivos.desfazer(operacao);
        }
        semPendencias();
    }

    @Test
    void resultadoTransacionalDesconhecidoPreservaPublicacaoETemporario() {
        // Exercita o callback UNKNOWN sem alegar que houve commit ou rollback.
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            armazenamento.preparar(conteudo(), "66213", CteFixture.CHAVE);
            var callbacks = TransactionSynchronizationManager.getSynchronizations();
            assertThat(callbacks).hasSize(1);
            callbacks.getFirst().beforeCommit(false);
            callbacks.getFirst().afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        semRegistros();
        assertThat(destino()).exists();
        assertThat(reconciliacao.inspecionar()).extracting(ReconciliacaoArmazenamentoService.Pendencia::estado)
                .containsExactly(PUBLICADO_SEM_REGISTRO);
    }
}
