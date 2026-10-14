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
    void processaXmlOrganizaArquivoECadastraDocumento(@TempDir Path temp) {
        var clientes = mock(ClienteRepository.class);
        var viagens = mock(ViagemRepository.class);
        var documentos = mock(DocumentoRepository.class);
        var cliente = Cliente.builder().id(1L).nome("Cliente Teste").cnpj("00000000000000").build();
        when(clientes.findByCnpj("00000000000000")).thenReturn(Optional.of(cliente));
        when(viagens.findByNumero("66213")).thenReturn(Optional.empty());
        when(viagens.save(any())).thenAnswer(invocation -> { Viagem v = invocation.getArgument(0); v.setId(2L); return v; });
        when(documentos.save(any())).thenAnswer(invocation -> { Documento d = invocation.getArgument(0); d.setId(3L); return d; });
        var service = new ProcessamentoService(new ExtratorDadosXml(), new OrganizadorArquivosService(temp.toString()),
                new ViagemService(viagens), new DocumentoService(documentos), new ClienteService(clientes));
        String xmlCte = "<cteProc xmlns=\"http://www.portalfiscal.inf.br/cte\"><CTe><infCte Id=\"CTe123\"><ide><nCT>66213</nCT><dhEmi>2025-04-04T08:14:10-03:00</dhEmi></ide><dest><CNPJ>00000000000000</CNPJ><xNome>Cliente Teste</xNome></dest></infCte></CTe></cteProc>";
        var arquivo = new MockMultipartFile("arquivo", "viagem-teste.xml", "application/xml", xmlCte.getBytes());

        var resposta = service.processarArquivoXml(arquivo);

        assertThat(resposta.sucesso()).isTrue();
        assertThat(Files.exists(temp.resolve("66213/viagem-teste.xml"))).isTrue();
        verify(documentos).save(argThat(d -> "XML".equals(d.getTipo()) && d.getViagem().getId().equals(2L)));
    }
}
