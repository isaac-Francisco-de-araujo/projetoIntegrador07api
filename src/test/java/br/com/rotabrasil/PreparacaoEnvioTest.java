package br.com.rotabrasil;

import br.com.rotabrasil.dto.PreviaEmailRequest;
import br.com.rotabrasil.exception.ConflitoRecursoException;
import br.com.rotabrasil.model.*;
import br.com.rotabrasil.repository.*;
import br.com.rotabrasil.service.*;
import br.com.rotabrasil.service.email.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PreparacaoEnvioTest {
    @TempDir Path raiz;
    ValidatorFactory factory;
    PreparacaoEnvioService preparacao;
    Documento documento;
    Anexo anexo;
    Path xml;

    @BeforeEach void preparar() throws Exception {
        factory = Validation.buildDefaultValidatorFactory();
        var viagens = mock(ViagemService.class);
        var coordenacao = mock(CoordenacaoImportacaoService.class);
        var repositorioDocumentos = mock(DocumentoRepository.class);
        var repositorioAnexos = mock(AnexoRepository.class);
        var documentos = new DocumentoService(repositorioDocumentos, mock(ExtratorDadosXml.class));
        var anexos = new AnexoService(repositorioAnexos, viagens, coordenacao);
        var email = new EmailService(viagens, documentos, mock(EnvioRepository.class), anexos);
        preparacao = new PreparacaoEnvioService(raiz.toString(), email, documentos, anexos, coordenacao, factory.getValidator());
        var viagem = Viagem.builder().id(1L).build();
        xml = raiz.resolve("sintetico.xml");
        Files.writeString(xml, "<sintetico/>");
        documento = Documento.builder().id(1L).viagem(viagem).tipo("XML").nomeArquivo("sintetico.xml")
                .caminho(xml.toString()).sha256(ConteudoXml.sha256(Files.readAllBytes(xml))).build();
        Path pdf = raiz.resolve("fatura.pdf");
        Files.writeString(pdf, "%PDF-sintetico");
        anexo = Anexo.builder().id(2L).nomeArquivo("fatura.pdf").caminho(pdf.toString())
                .sha256(ConteudoXml.sha256(Files.readAllBytes(pdf))).finalidade(FinalidadeAnexo.FATURA)
                .viagens(new LinkedHashSet<>(List.of(viagem, Viagem.builder().id(2L).build()))).build();
        when(repositorioDocumentos.findById(1L)).thenReturn(Optional.of(documento));
        when(repositorioAnexos.findById(2L)).thenReturn(Optional.of(anexo));
    }

    @AfterEach void fechar() { factory.close(); }
    PreviaEmailRequest pedido() { return new PreviaEmailRequest("operador@example.invalid", List.of(1L), List.of(2L)); }

    @Test void snapshotMantemSelecaoEBytesMesmoAposAlteracao() throws Exception {
        var mensagem = preparacao.preparar(1L, pedido());
        byte[] original = mensagem.arquivos().getFirst().conteudo();
        Files.writeString(xml, "alterado");
        documento.setNomeArquivo("novo.xml");
        byte[] copia = mensagem.arquivos().getFirst().conteudo(); copia[0] = 0;
        assertArrayEquals(original, mensagem.arquivos().getFirst().conteudo());
        assertEquals("sintetico.xml", mensagem.arquivos().getFirst().nome());
        assertEquals(2, mensagem.arquivos().size()); // Uma fatura, apesar de dois vínculos.
        assertThrows(UnsupportedOperationException.class, () -> mensagem.arquivos().clear());
    }

    @Test void transporteSimuladoRecebeSnapshotSemRede() {
        TransporteEmail transporte = mock(TransporteEmail.class);
        var mensagem = preparacao.preparar(1L, pedido());
        when(transporte.enviar(mensagem)).thenReturn(TransporteEmail.Resultado.DESCONHECIDO);
        assertEquals(TransporteEmail.Resultado.DESCONHECIDO, transporte.enviar(mensagem));
        verify(transporte, times(1)).enviar(mensagem);
    }

    @Test void transportePadraoBloqueiaEnvio() {
        assertThrows(IllegalStateException.class, () -> new TransporteEmailDesativado().enviar(preparacao.preparar(1L, pedido())));
    }
    @Test void hashDivergenteImpedePreparacao() throws Exception {
        Files.writeString(xml, "adulterado");
        assertThrows(ConflitoRecursoException.class, () -> preparacao.preparar(1L, pedido()));
    }
    @Test void arquivoAusenteImpedePreparacao() throws Exception {
        Files.delete(xml);
        assertThrows(ConflitoRecursoException.class, () -> preparacao.preparar(1L, pedido()));
    }
    @Test void documentoArquivadoImpedePreparacao() {
        documento.setArquivadoEm(OffsetDateTime.now());
        assertThrows(ConflitoRecursoException.class, () -> preparacao.preparar(1L, pedido()));
    }
    @Test void faturaArquivadaImpedePreparacao() {
        anexo.setArquivadoEm(OffsetDateTime.now());
        assertThrows(ConflitoRecursoException.class, () -> preparacao.preparar(1L, pedido()));
    }
    @Test void documentoDeOutraViagemImpedePreparacao() {
        documento.setViagem(Viagem.builder().id(3L).build());
        assertThrows(ConflitoRecursoException.class, () -> preparacao.preparar(1L, pedido()));
    }
    @Test void faturaSemVinculoImpedePreparacao() {
        anexo.getViagens().clear();
        assertThrows(ConflitoRecursoException.class, () -> preparacao.preparar(1L, pedido()));
    }
    @Test void selecaoRepetidaEContatoInvalidoSaoRecusados() {
        assertThrows(IllegalArgumentException.class, () -> preparacao.preparar(1L, new PreviaEmailRequest("x@example.invalid", List.of(1L, 1L))));
        assertThrows(IllegalArgumentException.class, () -> preparacao.preparar(1L, new PreviaEmailRequest("invalido", List.of(1L))));
    }
    @Test void caminhoExternoENomeInseguroSaoRecusados() {
        documento.setCaminho(raiz.resolve("../externo.xml").toString());
        assertThrows(ConflitoRecursoException.class, () -> preparacao.preparar(1L, pedido()));
        documento.setCaminho(xml.toString()); documento.setNomeArquivo("anexo\r\nBcc: outro");
        assertThrows(ConflitoRecursoException.class, () -> preparacao.preparar(1L, pedido()));
    }
    @Test void limitesConfiguradosPorArquivoETotalSaoRevalidados() {
        org.springframework.test.util.ReflectionTestUtils.setField(preparacao, "limiteArquivo", 4);
        assertThrows(ConflitoRecursoException.class, () -> preparacao.preparar(1L, pedido()));
        org.springframework.test.util.ReflectionTestUtils.setField(preparacao, "limiteArquivo", 1024);
        org.springframework.test.util.ReflectionTestUtils.setField(preparacao, "limiteTotal", 15);
        assertThrows(ConflitoRecursoException.class, () -> preparacao.preparar(1L, pedido()));
    }
    @Test void nomeLegadoComSubdiretorioEConferidoComoNomeSimplesSemAlterarCadastro(){
        documento.setNomeArquivo("origem/sintetico.xml");
        var mensagem=preparacao.preparar(1L,pedido());assertEquals("sintetico.xml",mensagem.arquivos().getFirst().nome());
        assertEquals("origem/sintetico.xml",documento.getNomeArquivo());
    }
}
