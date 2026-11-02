package br.com.rotabrasil;

import br.com.rotabrasil.service.DestinoExportacaoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class DestinoExportacaoTest {
    @TempDir Path tmp;
    @Test void recusaCheckoutSemCriarOuMudarDestino(){
        assertThatThrownBy(()->new DestinoExportacaoService(Path.of("copias").toAbsolutePath().toString(),tmp.resolve("acervo").toString()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("fora do checkout");
    }
    @Test void recusaSobreposicaoComAcervoEPermitePastaExterna(){
        assertThatThrownBy(()->new DestinoExportacaoService(tmp.toString(),tmp.resolve("acervo").toString()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("separada do acervo");
        assertThat(new DestinoExportacaoService(tmp.resolve("copias").toString(),tmp.resolve("acervo").toString()).autorizada()).isTrue();
    }
}
