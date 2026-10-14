package br.com.rotabrasil;

import br.com.rotabrasil.exception.ArquivoInvalidoException;
import br.com.rotabrasil.service.OrganizadorArquivosService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Files;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizadorArquivosServiceTest {
    @Test
    void exigeIdentificadorAntesDeSalvar() throws Exception {
        var raiz = Files.createTempDirectory("rotabrasil-test");
        var service = new OrganizadorArquivosService(raiz.toString());
        var arquivo = new MockMultipartFile("arquivo", "entrada.xml", "application/xml", "<r/>".getBytes());
        assertThatThrownBy(() -> service.organizarXml(arquivo, ""))
                .isInstanceOf(ArquivoInvalidoException.class);
    }
}
