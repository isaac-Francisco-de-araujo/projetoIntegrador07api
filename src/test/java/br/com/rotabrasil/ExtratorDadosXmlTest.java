
package br.com.rotabrasil;

import br.com.rotabrasil.exception.ArquivoInvalidoException;
import br.com.rotabrasil.service.ExtratorDadosXml;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtratorDadosXmlTest {

    private final ExtratorDadosXml extrator = new ExtratorDadosXml();

    @Test
    void extraiDadosDoCteReal400() throws Exception {

        Path path = Paths.get(
            "src/test/resources/35250467391920000159570020000662131001747710-cte.xml"
        );

        try (InputStream is = Files.newInputStream(path)) {

            var dados = extrator.extrair(is);

            assertThat(dados.numero()).isEqualTo("66213");
            assertThat(dados.data().toString()).isEqualTo("2025-04-04");
            assertThat(dados.status())
                .isEqualTo("Autorizado o uso do CT-e.");

            assertThat(dados.nomeCliente())
                .isEqualTo("DSM PRODUTOS NUTRICIONAIS BRASIL S.A.");

            assertThat(dados.cnpjCliente())
                .isEqualTo("56992951001978");

            assertThat(dados.emailCliente())
                .isEqualTo("nao@informado.com");

            assertThat(dados.telefoneCliente())
                .isEqualTo("1147188467");
        }
    }

    @Test
    void rejeitaXmlInvalido() {

        String xml = "<invalid></invalid>";

        assertThatThrownBy(() ->
            extrator.extrair(
                new ByteArrayInputStream(
                    xml.getBytes(StandardCharsets.UTF_8)
                )
            )
        ).isInstanceOf(ArquivoInvalidoException.class);
    }

    @Test
    void naoPresumeAutorizacaoQuandoNaoExisteProtocolo() {

        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <CTe xmlns="http://www.portalfiscal.inf.br/cte">
                <infCte>
                    <ide>
                        <nCT>12345</nCT>
                        <dhEmi>2025-04-04T10:00:00-03:00</dhEmi>
                    </ide>
                    <dest>
                        <CNPJ>12345678000199</CNPJ>
                        <xNome>CLIENTE DE TESTE</xNome>
                    </dest>
                </infCte>
            </CTe>
            """;

        var dados = extrator.extrair(
            new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8)
            )
        );

        assertThat(dados.numero()).isEqualTo("12345");

        assertThat(dados.status())
            .isEqualTo("Sem protocolo de autorização");
    }
}
