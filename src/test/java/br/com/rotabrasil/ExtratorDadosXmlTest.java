package br.com.rotabrasil;

import br.com.rotabrasil.service.ExtratorDadosXml;
import br.com.rotabrasil.exception.ArquivoInvalidoException;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtratorDadosXmlTest {
    private final ExtratorDadosXml extrator = new ExtratorDadosXml();

    @Test
    void extraiDadosDoCteReal400() throws Exception {
        Path path = Paths.get("src/test/resources/35250467391920000159570020000662131001747710-cte.xml");
        try (InputStream is = Files.newInputStream(path)) {
            var dados = extrator.extrair(is);
            
            assertThat(dados.numero()).isEqualTo("66213");
            assertThat(dados.data().toString()).isEqualTo("2025-04-04");
            assertThat(dados.status()).isEqualTo("Autorizado o uso do CT-e.");
            
            // Destinatário no XML real
            assertThat(dados.nomeCliente()).isEqualTo("DSM PRODUTOS NUTRICIONAIS BRASIL S.A.");
            assertThat(dados.cnpjCliente()).isEqualTo("56992951001978");
            assertThat(dados.emailCliente()).isEqualTo("nao@informado.com"); // Campo <email> ausente no <dest> do XML real
            assertThat(dados.telefoneCliente()).isEqualTo("1147188467");
        }
    }

    @Test
    void rejeitaXmlInvalido() {
        assertThatThrownBy(() -> extrator.extrair(new java.io.ByteArrayInputStream("<invalid></invalid>".getBytes())))
                .isInstanceOf(ArquivoInvalidoException.class);
    }
}
