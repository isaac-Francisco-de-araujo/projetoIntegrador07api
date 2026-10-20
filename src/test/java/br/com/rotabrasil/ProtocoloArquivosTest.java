package br.com.rotabrasil;

import br.com.rotabrasil.exception.ArmazenamentoException;
import br.com.rotabrasil.exception.ConflitoDocumentoException;
import br.com.rotabrasil.service.OrganizadorArquivosService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocoloArquivosTest {
    @TempDir Path raiz;

    private byte[] conteudo() { return CteFixture.xml().getBytes(StandardCharsets.UTF_8); }
    private OrganizadorArquivosService arquivos() { return new OrganizadorArquivosService(raiz.toString()); }

    private void semOperacoesPendentes() throws IOException {
        try (var entradas = Files.list(raiz.resolve(".pendentes"))) {
            assertThat(entradas.toList()).isEmpty();
        }
    }

    @Test
    void preparaBytesCompletosAntesDePublicarONomeDefinitivo() throws Exception {
        var arquivos = arquivos();
        try (var operacao = arquivos.prepararXml(conteudo(), "66213", CteFixture.CHAVE)) {
            assertThat(operacao.destino()).doesNotExist();
            assertThat(Files.readAllBytes(operacao.temporario())).isEqualTo(conteudo());
            assertThat(operacao.diretorio().resolve("operacao.properties")).exists();
            arquivos.publicar(operacao);
            assertThat(Files.isSameFile(operacao.temporario(), operacao.destino())).isTrue();
            assertThat(Files.readAllBytes(operacao.destino())).isEqualTo(conteudo());
            arquivos.confirmar(operacao);
            assertThat(operacao.destino()).exists();
        }
        semOperacoesPendentes();
    }

    @Test
    void rollbackRemoveSomenteAPublicacaoPropria() throws Exception {
        var arquivos = arquivos();
        var operacao = arquivos.prepararXml(conteudo(), "66213", CteFixture.CHAVE);
        arquivos.publicar(operacao);
        arquivos.desfazer(operacao);
        assertThat(operacao.destino()).doesNotExist();
        semOperacoesPendentes();
    }

    @Test
    void duasPreparacoesNaoSobrescrevemNemCompensamOArquivoDaOutra() throws Exception {
        var arquivos = arquivos();
        try (var primeira = arquivos.prepararXml(conteudo(), "66213", CteFixture.CHAVE);
             var segunda = arquivos.prepararXml(conteudo(), "66213", CteFixture.CHAVE)) {
            arquivos.publicar(primeira);
            assertThatThrownBy(() -> arquivos.publicar(segunda)).isInstanceOf(ConflitoDocumentoException.class);
            arquivos.desfazer(segunda);
            assertThat(Files.readAllBytes(primeira.destino())).isEqualTo(conteudo());
            arquivos.confirmar(primeira);
        }
        semOperacoesPendentes();
    }

    @Test
    void escritaParcialNoTemporarioNaoPublicaNemDeixaResiduos() throws Exception {
        var arquivos = new OrganizadorArquivosService(raiz.toString()) {
            @Override
            protected void gravarForcado(Path caminho, byte[] dados) throws IOException {
                if (caminho.getFileName().toString().equals("conteudo.tmp")) {
                    Files.write(caminho, new byte[] {1, 2, 3});
                    throw new IOException("Falha de escrita simulada");
                }
                super.gravarForcado(caminho, dados);
            }
        };
        assertThatThrownBy(() -> arquivos.prepararXml(conteudo(), "66213", CteFixture.CHAVE))
                .isInstanceOf(ArmazenamentoException.class);
        assertThat(raiz.resolve("66213").resolve(CteFixture.CHAVE + ".xml")).doesNotExist();
        semOperacoesPendentes();
    }

    @Test
    void rollbackPreservaDestinoSubstituidoPorTerceiro() throws Exception {
        var arquivos = arquivos();
        try (var operacao = arquivos.prepararXml(conteudo(), "66213", CteFixture.CHAVE)) {
            arquivos.publicar(operacao);
            Files.delete(operacao.destino());
            Files.writeString(operacao.destino(), "arquivo de terceiro");
            assertThatThrownBy(() -> arquivos.desfazer(operacao)).isInstanceOf(ArmazenamentoException.class);
            assertThat(Files.readString(operacao.destino())).isEqualTo("arquivo de terceiro");
            assertThat(operacao.temporario()).exists();
            assertThat(operacao.diretorio().resolve("operacao.properties")).exists();
        }
    }

    @Test
    void rollbackPreservaConteudoModificadoNoMesmoInode() throws Exception {
        var arquivos = arquivos();
        try (var operacao = arquivos.prepararXml(conteudo(), "66213", CteFixture.CHAVE)) {
            arquivos.publicar(operacao);
            Files.writeString(operacao.destino(), "alterado externamente");
            assertThatThrownBy(() -> arquivos.desfazer(operacao)).isInstanceOf(ArmazenamentoException.class);
            assertThat(Files.readString(operacao.destino())).isEqualTo("alterado externamente");
            assertThat(operacao.diretorio().resolve("operacao.properties")).exists();
        }
    }

    @Test
    void temporarioAlteradoNuncaEhPublicado() throws Exception {
        var arquivos = arquivos();
        try (var operacao = arquivos.prepararXml(conteudo(), "66213", CteFixture.CHAVE)) {
            Files.writeString(operacao.temporario(), "alterado");
            assertThatThrownBy(() -> arquivos.publicar(operacao)).isInstanceOf(ArmazenamentoException.class);
            assertThat(operacao.destino()).doesNotExist();
            arquivos.desfazer(operacao);
        }
        semOperacoesPendentes();
    }

    @Test
    void recusaAreaTemporariaComoLinkSimbolico(@TempDir Path fora) throws Exception {
        Files.createSymbolicLink(raiz.resolve(".pendentes"), fora);
        assertThatThrownBy(() -> arquivos().prepararXml(conteudo(), "66213", CteFixture.CHAVE))
                .isInstanceOf(ArmazenamentoException.class);
        try (var entradas = Files.list(fora)) {
            assertThat(entradas.toList()).isEmpty();
        }
    }
}
