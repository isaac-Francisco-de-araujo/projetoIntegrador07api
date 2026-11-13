package br.com.rotabrasil;

import br.com.rotabrasil.exception.ConflitoDocumentoException;
import br.com.rotabrasil.model.Cliente;
import br.com.rotabrasil.model.Documento;
import br.com.rotabrasil.model.Viagem;
import br.com.rotabrasil.repository.ClienteRepository;
import br.com.rotabrasil.repository.DocumentoRepository;
import br.com.rotabrasil.repository.ViagemRepository;
import br.com.rotabrasil.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static br.com.rotabrasil.service.ReconciliacaoArmazenamentoService.Estado.*;
import static org.assertj.core.api.Assertions.*;

/** Reinicia contextos com H2 em arquivo e resíduos equivalentes a uma interrupção. */
class RecuperacaoReinicioTest {
    @TempDir Path base;

    private ConfigurableApplicationContext iniciar() {
        return new SpringApplicationBuilder(RotaBrasilApplication.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=jdbc:h2:file:" + base.resolve("banco-teste"),
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.flyway.enabled=true",
                "--spring.h2.console.enabled=false",
                "--spring.devtools.restart.enabled=false",
                "--spring.main.banner-mode=off",
                "--logging.level.root=ERROR",
                "--rotabrasil.arquivos.viagens=" + base.resolve("viagens"));
    }

    private byte[] conteudo() { return CteFixture.xml().getBytes(StandardCharsets.UTF_8); }
    private MockMultipartFile upload() { return new MockMultipartFile("arquivo", "renomeado.xml", "application/xml", conteudo()); }
    private record Residuo(Path destino, Path temporario, Path diretorio, Long documentoId) {}

    private Residuo criarResiduo(boolean publicar, boolean confirmarNoBanco) throws Exception {
        try (var contexto = iniciar()) {
            var arquivos = contexto.getBean(OrganizadorArquivosService.class);
            try (var operacao = arquivos.prepararXml(conteudo(), "66213", CteFixture.CHAVE)) {
                if (publicar) arquivos.publicar(operacao);
                Long documentoId = null;
                if (confirmarNoBanco) {
                    var cliente = contexto.getBean(ClienteRepository.class).save(
                            Cliente.builder().nome("Cliente Teste").cnpj("00000000000000").build());
                    var viagem = contexto.getBean(ViagemRepository.class).save(
                            Viagem.builder().numero("66213").cliente(cliente).build());
                    documentoId = contexto.getBean(DocumentoRepository.class).saveAndFlush(Documento.builder()
                            .tipo("XML").nomeArquivo("original.xml").viagem(viagem)
                            .chaveAcesso(CteFixture.CHAVE).sha256(ConteudoXml.sha256(conteudo()))
                            .caminho(operacao.destino().toString()).build()).getId();
                }
                // Libera a trava sem executar compensação/limpeza, simulando o resíduo após interrupção.
                return new Residuo(operacao.destino(), operacao.temporario(), operacao.diretorio(), documentoId);
            }
        }
    }

    @Test
    void aposReinicioConfereCommitELimpaSoOsArtefatosTemporarios() throws Exception {
        var residuo = criarResiduo(true, true);
        try (var contexto = iniciar()) {
            var reconciliacao = contexto.getBean(ReconciliacaoArmazenamentoService.class);
            assertThat(reconciliacao.inspecionar()).extracting(ReconciliacaoArmazenamentoService.Pendencia::estado)
                    .containsExactly(COMMIT_CONFIRMADO);
            assertThat(residuo.temporario()).exists();
            assertThat(reconciliacao.recuperarConfirmadas()).extracting(ReconciliacaoArmazenamentoService.Pendencia::estado)
                    .containsExactly(RECUPERADA);
            assertThat(residuo.diretorio()).doesNotExist();
            assertThat(Files.readAllBytes(residuo.destino())).isEqualTo(conteudo());
            var resposta = contexto.getBean(ProcessamentoService.class).processarArquivoXml(upload());
            assertThat(resposta.documentoId()).isEqualTo(residuo.documentoId());
            assertThat(contexto.getBean(DocumentoRepository.class).count()).isEqualTo(1);
            assertThat(reconciliacao.recuperarConfirmadas()).isEmpty();
        }
    }

    @Test
    void restauraDestinoAusenteSomenteComRegistroConfirmadoEBytesVerificados() throws Exception {
        var residuo = criarResiduo(true, true);
        Files.delete(residuo.destino());
        try (var contexto = iniciar()) {
            var reconciliacao = contexto.getBean(ReconciliacaoArmazenamentoService.class);
            assertThat(reconciliacao.inspecionar()).extracting(ReconciliacaoArmazenamentoService.Pendencia::estado)
                    .containsExactly(REGISTRO_SEM_ARQUIVO);
            assertThat(residuo.destino()).doesNotExist(); // Inspeção não modifica arquivos.
            assertThat(reconciliacao.recuperarConfirmadas()).extracting(ReconciliacaoArmazenamentoService.Pendencia::estado)
                    .containsExactly(RECUPERADA);
            assertThat(Files.readAllBytes(residuo.destino())).isEqualTo(conteudo());
            assertThat(residuo.diretorio()).doesNotExist();
            assertThat(contexto.getBean(DocumentoRepository.class).findById(residuo.documentoId())).isPresent();
        }
    }

    @Test
    void publicacaoSemCommitPermanecePreservadaSemAdocaoOuExclusao() throws Exception {
        var residuo = criarResiduo(true, false);
        try (var contexto = iniciar()) {
            var reconciliacao = contexto.getBean(ReconciliacaoArmazenamentoService.class);
            assertThat(reconciliacao.recuperarConfirmadas()).extracting(ReconciliacaoArmazenamentoService.Pendencia::estado)
                    .containsExactly(PUBLICADO_SEM_REGISTRO);
            assertThat(Files.readAllBytes(residuo.destino())).isEqualTo(conteudo());
            assertThat(residuo.temporario()).exists();
            assertThatThrownBy(() -> contexto.getBean(ProcessamentoService.class).processarArquivoXml(upload()))
                    .isInstanceOf(ConflitoDocumentoException.class);
            assertThat(contexto.getBean(DocumentoRepository.class).count()).isZero();
            assertThat(contexto.getBean(ClienteRepository.class).count()).isZero();
            assertThat(residuo.diretorio()).exists();
        }
    }

    @Test
    void preparacaoSemCommitPermiteNovaTentativaSemPerderOResiduoAntigo() throws Exception {
        var residuo = criarResiduo(false, false);
        try (var contexto = iniciar()) {
            var reconciliacao = contexto.getBean(ReconciliacaoArmazenamentoService.class);
            assertThat(reconciliacao.recuperarConfirmadas()).extracting(ReconciliacaoArmazenamentoService.Pendencia::estado)
                    .containsExactly(PREPARADO_SEM_REGISTRO);
            assertThat(residuo.temporario()).exists();
            assertThat(residuo.destino()).doesNotExist();
            contexto.getBean(ProcessamentoService.class).processarArquivoXml(upload());
            assertThat(reconciliacao.recuperarConfirmadas()).extracting(ReconciliacaoArmazenamentoService.Pendencia::estado)
                    .containsExactly(RECUPERADA);
            assertThat(Files.readAllBytes(residuo.destino())).isEqualTo(conteudo());
            assertThat(contexto.getBean(DocumentoRepository.class).count()).isEqualTo(1);
            assertThat(residuo.diretorio()).doesNotExist();
        }
    }

    @Test
    void destinoDivergenteNuncaEhSobrescritoPelaRecuperacao() throws Exception {
        var residuo = criarResiduo(true, true);
        Files.delete(residuo.destino());
        Files.writeString(residuo.destino(), "conteúdo externo que deve ser preservado");
        try (var contexto = iniciar()) {
            var reconciliacao = contexto.getBean(ReconciliacaoArmazenamentoService.class);
            assertThat(reconciliacao.recuperarConfirmadas()).extracting(ReconciliacaoArmazenamentoService.Pendencia::estado)
                    .containsExactly(REVISAO_NECESSARIA);
            assertThat(Files.readString(residuo.destino())).isEqualTo("conteúdo externo que deve ser preservado");
            assertThat(residuo.temporario()).exists();
        }
    }

    @Test
    void metadadosComTraversalSaoRejeitadosSemTocarNoDestinoExterno() throws Exception {
        var residuo = criarResiduo(false, false);
        Path externo = base.resolve("preservar.xml");
        Files.writeString(externo, "preservar");
        var metadados = new java.util.Properties();
        try (var entrada = Files.newInputStream(residuo.diretorio().resolve("operacao.properties"))) {
            metadados.load(entrada);
        }
        metadados.setProperty("numero", "../../");
        try (var saida = Files.newOutputStream(residuo.diretorio().resolve("operacao.properties"))) {
            metadados.store(saida, "Metadados inválidos simulados");
        }
        try (var contexto = iniciar()) {
            assertThat(contexto.getBean(ReconciliacaoArmazenamentoService.class).recuperarConfirmadas())
                    .extracting(ReconciliacaoArmazenamentoService.Pendencia::estado).containsExactly(REVISAO_NECESSARIA);
            assertThat(Files.readString(externo)).isEqualTo("preservar");
            assertThat(residuo.temporario()).exists();
        }
    }
}
