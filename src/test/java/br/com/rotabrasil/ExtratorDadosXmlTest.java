package br.com.rotabrasil;
import br.com.rotabrasil.exception.ArquivoInvalidoException;
import br.com.rotabrasil.service.*;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class ExtratorDadosXmlTest {
 private DadosViagemXml extrair(String xml) {
  return new ExtratorDadosXml().extrair(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
 }

    @Test
    void extraiIdentidadeSemInventarEmail() {
  var d = extrair(CteFixture.xml());
  assertThat(d.chaveAcesso()).isEqualTo(CteFixture.CHAVE);
  assertThat(d.numero()).isEqualTo("66213");
  assertThat(d.data().toString()).isEqualTo("2025-04-04");
  assertThat(d.nomeCliente()).isEqualTo("Cliente Teste");
  assertThat(d.cnpjCliente()).isEqualTo("00000000000000");
  assertThat(d.emailCliente()).isNull();
  assertThat(d.telefoneCliente()).isEqualTo("1100000000");
  assertThat(d.status()).isEqualTo("Autorizado");
 }

    @Test
    void aceitaSemPrefixoESemContatos() {
  var d = extrair(CteFixture.xml().replace("Id=\"CTe", "Id=\"")
    .replace("<fone>1100000000</fone>", "<fone> </fone>")
    .replace("</xNome>", "</xNome><email> </email>"));
  assertThat(d.emailCliente()).isNull(); assertThat(d.telefoneCliente()).isNull();
 }

    @Test
    void aceitaCteSemProtocolo() {
  String xml = CteFixture.xml();
  String cte = xml.substring(xml.indexOf("<CTe>"), xml.indexOf("</CTe>")+6)
    .replace("<CTe>", "<CTe xmlns=\"http://www.portalfiscal.inf.br/cte\">");
  assertThat(extrair(cte).status()).isEqualTo("Sem protocolo de autorização");
 }

    @Test
    void chaveCurta() {
        assertThatThrownBy(() -> extrair(CteFixture.xml().replace("CTe" + CteFixture.CHAVE, "CTe123"))).isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("44");
    }

    @Test
    void digitoInvalido() {
        assertThatThrownBy(() -> extrair(CteFixture.xml().replace("CTe" + CteFixture.CHAVE, "CTe" + CteFixture.CHAVE.substring(0, 43) + "8"))).isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("verificador");
    }

    @Test
    void modeloInvalido() {
        assertThatThrownBy(() -> extrair(CteFixture.xml().replace("CTe" + CteFixture.CHAVE, "CTe" + CteFixture.CHAVE.substring(0, 20) + "55" + CteFixture.CHAVE.substring(22)))).isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("modelo");
    }

    @Test
    void protocoloDivergente() {
        assertThatThrownBy(() -> extrair(CteFixture.xml().replace("<chCTe>" + CteFixture.CHAVE, "<chCTe>" + "1".repeat(44)))).isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("protocolo");
    }

    @Test
    void versaoInvalida() {
        assertThatThrownBy(() -> extrair(CteFixture.xml().replace("4.00", "3.00"))).isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("Vers\u00e3o");
    }

    @Test
    void numeroDivergente() {
        assertThatThrownBy(() -> extrair(CteFixture.xml().replace("<nCT>66213</nCT>", "<nCT>66214</nCT>"))).isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("nCT");
    }

    @Test
    void duplicado() {
        assertThatThrownBy(() -> extrair(CteFixture.xml().replace("</nCT>", "</nCT><nCT>66213</nCT>"))).isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("duplicado");
    }

    @Test
    void ausentedest() {
        assertThatThrownBy(() -> extrair(CteFixture.xml().replaceAll("(?s)<dest>.*?</dest>", ""))).isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("dest");
    }

    @Test
    void ausenteide() {
        assertThatThrownBy(() -> extrair(CteFixture.xml().replaceAll("(?s)<ide>.*?</ide>", ""))).isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("ide");
    }

    @Test
    void ausentenCT() {
        assertThatThrownBy(() -> extrair(CteFixture.xml().replaceAll("(?s)<nCT>.*?</nCT>", ""))).isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("nCT");
    }

    @Test
    void ausentedhEmi() {
        assertThatThrownBy(() -> extrair(CteFixture.xml().replaceAll("(?s)<dhEmi>.*?</dhEmi>", ""))).isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("dhEmi");
    }

    @Test
    void ausentexNome() {
        assertThatThrownBy(() -> extrair(CteFixture.xml().replaceAll("(?s)<xNome>.*?</xNome>", ""))).isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("xNome");
    }

    @Test
    void ausenteCNPJ() {
        assertThatThrownBy(() -> extrair(CteFixture.xml().replaceAll("(?s)<CNPJ>.*?</CNPJ>", ""))).isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("CNPJ");
    }

    @Test
    void ausentechCTe() {
        assertThatThrownBy(() -> extrair(CteFixture.xml().replaceAll("(?s)<chCTe>.*?</chCTe>", ""))).isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("chCTe");
    }

    @Test
    void ausenteinfProt() {
        assertThatThrownBy(() -> extrair(CteFixture.xml().replaceAll("(?s)<infProt>.*?</infProt>", ""))).isInstanceOf(ArquivoInvalidoException.class).hasMessageContaining("infProt");
    }

    @Test
    void malformado() {
        assertThatThrownBy(() -> extrair("<CTe")).isInstanceOf(ArquivoInvalidoException.class);
    }

    @Test
    void estrutura() {
        assertThatThrownBy(() -> extrair("<invalid/>")).isInstanceOf(ArquivoInvalidoException.class);
    }

    @Test
    void xxe() {
        assertThatThrownBy(() -> extrair("<!DOCTYPE CTe [<!ENTITY xxe SYSTEM \"file:///nonexistent\">]><CTe>&xxe;</CTe>")).isInstanceOf(ArquivoInvalidoException.class);
    }
}
