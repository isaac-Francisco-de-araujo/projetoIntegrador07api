package br.com.rotabrasil;

import br.com.rotabrasil.exception.ArquivoInvalidoException;
import br.com.rotabrasil.exception.ConflitoDocumentoException;
import br.com.rotabrasil.service.OrganizadorArquivosService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizadorArquivosServiceTest {
    @Test
    void preservaArquivoExistenteComBytesDiferentes(@TempDir Path raiz) throws Exception {
        var service = new OrganizadorArquivosService(raiz.toString());
        Path destino = service.organizarXml("original".getBytes(StandardCharsets.UTF_8), "66213", CteFixture.CHAVE);
        assertThatThrownBy(() -> service.organizarXml("outro".getBytes(StandardCharsets.UTF_8), "66213", CteFixture.CHAVE))
                .isInstanceOf(ConflitoDocumentoException.class);
        assertThat(Files.readString(destino)).isEqualTo("original");
    }

    @Test
    void exigeIdentificadorAntesDeSalvar() throws Exception {
        var raiz = Files.createTempDirectory("rotabrasil-test");
        var service = new OrganizadorArquivosService(raiz.toString());
        var arquivo = new MockMultipartFile("arquivo", "entrada.xml", "application/xml", "<r/>".getBytes());
        assertThatThrownBy(() -> service.organizarXml(arquivo, ""))
                .isInstanceOf(ArquivoInvalidoException.class);
    }
}
