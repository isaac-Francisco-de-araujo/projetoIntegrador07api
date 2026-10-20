package br.com.rotabrasil;

import br.com.rotabrasil.model.*;
import br.com.rotabrasil.repository.*;
import br.com.rotabrasil.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProcessamentoServiceTest {
    @Test
    void encaminhaConteudoEIdentidadeParaArmazenamentoERegistro(@TempDir Path temp) {
        var clientes = mock(ClienteRepository.class);
        var viagens = mock(ViagemRepository.class);
        var documentos = mock(DocumentoRepository.class);
        var cliente = Cliente.builder().id(1L).nome("Cliente Teste").cnpj("00000000000000").build();
        when(clientes.findByCnpj("00000000000000")).thenReturn(Optional.of(cliente));
        when(viagens.save(any())).thenAnswer(invocation -> { Viagem v = invocation.getArgument(0); v.setId(2L); return v; });
        when(documentos.saveAndFlush(any())).thenAnswer(invocation -> { Documento d = invocation.getArgument(0); d.setId(3L); return d; });
        var armazenamento = mock(ArmazenamentoTransacionalService.class);
        Path destino = temp.resolve("66213/" + CteFixture.CHAVE + ".xml");
        when(armazenamento.preparar(any(), eq("66213"), eq(CteFixture.CHAVE))).thenReturn(destino);
        var service = new ProcessamentoService(new ExtratorDadosXml(), armazenamento,
                new ViagemService(viagens), new DocumentoService(documentos, new ExtratorDadosXml()), new ClienteService(clientes), mock(CoordenacaoImportacaoService.class));
        String xmlCte = CteFixture.xml();
        var arquivo = new MockMultipartFile("arquivo", "viagem-teste.xml", "application/xml", xmlCte.getBytes());

        var resposta = service.processarArquivoXml(arquivo);

        assertThat(resposta.sucesso()).isTrue();
        verify(armazenamento).preparar(eq(xmlCte.getBytes(java.nio.charset.StandardCharsets.UTF_8)), eq("66213"), eq(CteFixture.CHAVE));
        verify(documentos).saveAndFlush(argThat(d -> "XML".equals(d.getTipo()) && d.getViagem().getId().equals(2L)
                && destino.toString().equals(d.getCaminho())
                && CteFixture.CHAVE.equals(d.getChaveAcesso())
                && ConteudoXml.sha256(xmlCte.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(d.getSha256())));
    }
}
