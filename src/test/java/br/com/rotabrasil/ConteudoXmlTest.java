package br.com.rotabrasil;

import br.com.rotabrasil.exception.ArquivoInvalidoException;
import br.com.rotabrasil.service.ConteudoXml;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConteudoXmlTest {
    @Test
    void calculaSha256Conhecido() {
        assertThat(ConteudoXml.sha256("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void rejeitaConteudoVazio() {
        assertThatThrownBy(() -> ConteudoXml.ler(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(ArquivoInvalidoException.class);
    }

    @Test
    void rejeitaTamanhoRealExcessivo() {
        assertThatThrownBy(() -> ConteudoXml.ler(new ByteArrayInputStream(new byte[ConteudoXml.LIMITE_BYTES + 1])))
                .isInstanceOf(ArquivoInvalidoException.class);
    }

    @Test
    void aceitaLimiteExato() throws Exception {
        byte[] conteudo = new byte[ConteudoXml.LIMITE_BYTES];
        conteudo[conteudo.length - 1] = 1;
        assertThat(ConteudoXml.ler(new ByteArrayInputStream(conteudo))).isEqualTo(conteudo);
    }
}
