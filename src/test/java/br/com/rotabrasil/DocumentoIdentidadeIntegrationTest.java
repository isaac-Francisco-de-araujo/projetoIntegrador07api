package br.com.rotabrasil;

import br.com.rotabrasil.model.Cliente;
import br.com.rotabrasil.model.Documento;
import br.com.rotabrasil.model.Viagem;
import br.com.rotabrasil.repository.ClienteRepository;
import br.com.rotabrasil.repository.DocumentoRepository;
import br.com.rotabrasil.repository.ViagemRepository;
import br.com.rotabrasil.service.ConteudoXml;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identidade-etapa2;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true", "spring.flyway.target=2",
        "rotabrasil.banco.exigir-restricoes=false",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DocumentoIdentidadeIntegrationTest {
    private static final Path ARQUIVOS = criarDiretorioTemporario();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired DocumentoRepository documentos;
    @Autowired ViagemRepository viagens;
    @Autowired ClienteRepository clientes;

    private static Path criarDiretorioTemporario() {
        try {
            return Files.createTempDirectory("rotabrasil-identidade-");
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("rotabrasil.arquivos.viagens", ARQUIVOS::toString);
    }

    @BeforeEach
    void limparDadosIsolados() throws IOException {
        documentos.deleteAll();
        viagens.deleteAll();
        clientes.deleteAll();
        FileSystemUtils.deleteRecursively(ARQUIVOS);
        Files.createDirectories(ARQUIVOS);
    }

    @AfterAll
    static void removerDiretorioTemporario() throws IOException {
        FileSystemUtils.deleteRecursively(ARQUIVOS);
    }

    private JsonNode enviar(String nome, String xml, int statusEsperado) throws Exception {
        var arquivo = new MockMultipartFile("arquivo", nome, "application/xml", xml.getBytes(StandardCharsets.UTF_8));
        var resposta = mvc.perform(multipart("/api/processamento/xml").file(arquivo))
                .andExpect(status().is(statusEsperado)).andReturn().getResponse();
        return mapper.readTree(resposta.getContentAsByteArray());
    }

    private Path destino(String chave) {
        return ARQUIVOS.resolve("66213").resolve(chave + ".xml");
    }

    private Viagem criarViagem(String cnpj) {
        var cliente = clientes.save(Cliente.builder().nome("Cliente Teste").cnpj(cnpj).build());
        return viagens.save(Viagem.builder().numero("66213").cliente(cliente)
                .data(LocalDate.of(2025, 4, 4)).status("Teste").build());
    }

    private Documento criarLegado(String nome, String xml) throws IOException {
        Viagem viagem = viagens.findAll().stream().findFirst().orElseGet(() -> criarViagem("00000000000000"));
        Path caminho = ARQUIVOS.resolve(nome);
        Files.writeString(caminho, xml, StandardCharsets.UTF_8);
        return documentos.save(Documento.builder().nomeArquivo(nome).tipo("XML")
                .viagem(viagem).caminho(caminho.toString()).build());
    }

    @Test
    void persisteChaveHashENomeOriginalComBytesPreservados() throws Exception {
        var resposta = enviar("original.xml", CteFixture.xml(), 200);
        var documento = documentos.findById(resposta.get("documentoId").asLong()).orElseThrow();
        assertThat(documento.getChaveAcesso()).isEqualTo(CteFixture.CHAVE);
        assertThat(documento.getSha256()).isEqualTo(ConteudoXml.sha256(CteFixture.xml().getBytes(StandardCharsets.UTF_8)));
        assertThat(documento.getNomeArquivo()).isEqualTo("original.xml");
        assertThat(Path.of(documento.getCaminho())).isEqualTo(destino(CteFixture.CHAVE));
        assertThat(Files.readString(destino(CteFixture.CHAVE))).isEqualTo(CteFixture.xml());
        assertThat(clientes.findAll().getFirst().getEmail()).isNull();
        assertThat(documentos.count()).isEqualTo(1);
        assertThat(viagens.count()).isEqualTo(1);
    }

    @Test
    void reimportaMesmoNomeEOutroNomeSemNovosRegistrosOuArquivos() throws Exception {
        var primeira = enviar("original.xml", CteFixture.xml(), 200);
        var segunda = enviar("original.xml", CteFixture.xml(), 200);
        var renomeada = enviar("renomeado.xml", CteFixture.xml(), 200);
        assertThat(segunda.get("documentoId")).isEqualTo(primeira.get("documentoId"));
        assertThat(renomeada.get("documentoId")).isEqualTo(primeira.get("documentoId"));
        assertThat(documentos.findAll().getFirst().getNomeArquivo()).isEqualTo("original.xml");
        assertThat(documentos.count()).isEqualTo(1);
        assertThat(viagens.count()).isEqualTo(1);
        assertThat(clientes.count()).isEqualTo(1);
        try (var arquivos = Files.list(destino(CteFixture.CHAVE).getParent())) {
            assertThat(arquivos.toList()).containsExactly(destino(CteFixture.CHAVE));
        }
    }

    @Test
    void mesmaChaveComBytesDiferentesRetorna409SemSobrescrever() throws Exception {
        enviar("original.xml", CteFixture.xml(), 200);
        var resposta = enviar("outro.xml", CteFixture.xml() + "\n", 409);
        assertThat(resposta.get("mensagem").asText()).contains("conteúdo diferente");
        assertThat(Files.readString(destino(CteFixture.CHAVE))).isEqualTo(CteFixture.xml());
        assertThat(documentos.count()).isEqualTo(1);
    }

    @Test
    void chavesDiferentesComMesmoNomePreservamAmbos() throws Exception {
        String outraChave = CteFixture.outraChave();
        var primeira = enviar("original.xml", CteFixture.xml(), 200);
        var segunda = enviar("original.xml", CteFixture.xml().replace(CteFixture.CHAVE, outraChave), 200);
        assertThat(segunda.get("documentoId")).isNotEqualTo(primeira.get("documentoId"));
        assertThat(documentos.count()).isEqualTo(2);
        assertThat(viagens.count()).isEqualTo(1);
        assertThat(Files.readString(destino(CteFixture.CHAVE))).isEqualTo(CteFixture.xml());
        assertThat(Files.readString(destino(outraChave))).contains(outraChave);
    }

    @Test
    void reutilizaLegadoRenomeadoSemMigrarCamposNemMoverArquivo() throws Exception {
        Documento legado = criarLegado("antigo.xml", CteFixture.xml());
        var resposta = enviar("renomeado.xml", CteFixture.xml(), 200);
        assertThat(resposta.get("documentoId").asLong()).isEqualTo(legado.getId());
        Documento preservado = documentos.findById(legado.getId()).orElseThrow();
        assertThat(preservado.getChaveAcesso()).isNull();
        assertThat(preservado.getSha256()).isNull();
        assertThat(preservado.getCaminho()).isEqualTo(legado.getCaminho());
        assertThat(Files.readString(Path.of(legado.getCaminho()))).isEqualTo(CteFixture.xml());
        assertThat(destino(CteFixture.CHAVE)).doesNotExist();
        assertThat(documentos.count()).isEqualTo(1);
    }

    @Test
    void legadoComMesmaChaveEConteudoDiferenteRetornaConflito() throws Exception {
        Documento legado = criarLegado("antigo.xml", CteFixture.xml());
        enviar("novo.xml", CteFixture.xml() + "\n", 409);
        assertThat(Files.readString(Path.of(legado.getCaminho()))).isEqualTo(CteFixture.xml());
        assertThat(documentos.count()).isEqualTo(1);
        assertThat(destino(CteFixture.CHAVE)).doesNotExist();
    }

    @Test
    void permiteNovaChaveComNomeLegadoIgualSemAlterarOriginal() throws Exception {
        Documento legado = criarLegado("original.xml", CteFixture.xml());
        String outraChave = CteFixture.outraChave();
        enviar("original.xml", CteFixture.xml().replace(CteFixture.CHAVE, outraChave), 200);
        assertThat(documentos.count()).isEqualTo(2);
        assertThat(Files.readString(Path.of(legado.getCaminho()))).isEqualTo(CteFixture.xml());
        assertThat(destino(outraChave)).exists();
    }

    @Test
    void legadoSemArquivoExigeReconciliacao() throws Exception {
        Documento legado = criarLegado("antigo.xml", CteFixture.xml());
        Files.delete(Path.of(legado.getCaminho()));
        var resposta = enviar("novo.xml", CteFixture.xml(), 409);
        assertThat(resposta.get("mensagem").asText()).contains("reconciliação");
        assertThat(documentos.count()).isEqualTo(1);
        assertThat(destino(CteFixture.CHAVE)).doesNotExist();
    }

    @Test
    void legadoInvalidoNaoEhIgnorado() throws Exception {
        criarLegado("antigo.xml", "<invalido/>");
        enviar("novo.xml", CteFixture.xml(), 409);
        assertThat(documentos.count()).isEqualTo(1);
        assertThat(destino(CteFixture.CHAVE)).doesNotExist();
    }

    @Test
    void duplicatasLegadasExigemReconciliacao() throws Exception {
        criarLegado("antigo1.xml", CteFixture.xml());
        criarLegado("antigo2.xml", CteFixture.xml());
        var resposta = enviar("novo.xml", CteFixture.xml(), 409);
        assertThat(resposta.get("mensagem").asText()).contains("múltiplos");
        assertThat(documentos.count()).isEqualTo(2);
        assertThat(destino(CteFixture.CHAVE)).doesNotExist();
    }

    @Test
    void duplicatasIdentificadasNaoSaoEscolhidasArbitrariamente() throws Exception {
        enviar("original.xml", CteFixture.xml(), 200);
        Documento original = documentos.findAll().getFirst();
        documentos.save(Documento.builder().nomeArquivo("duplicado.xml").tipo("XML")
                .viagem(original.getViagem()).caminho(original.getCaminho())
                .chaveAcesso(original.getChaveAcesso()).sha256(original.getSha256()).build());
        var resposta = enviar("novo.xml", CteFixture.xml(), 409);
        assertThat(resposta.get("mensagem").asText()).contains("múltiplos");
        assertThat(documentos.count()).isEqualTo(2);
    }

    @Test
    void registroParcialmenteIdentificadoPodeSerConferidoSemPreencherHash() throws Exception {
        Documento legado = criarLegado("antigo.xml", CteFixture.xml());
        legado.setChaveAcesso(CteFixture.CHAVE);
        documentos.save(legado);
        var resposta = enviar("novo.xml", CteFixture.xml(), 200);
        assertThat(resposta.get("documentoId").asLong()).isEqualTo(legado.getId());
        assertThat(documentos.findById(legado.getId()).orElseThrow().getSha256()).isNull();
        assertThat(destino(CteFixture.CHAVE)).doesNotExist();
    }

    @Test
    void legadoAssociadoAViagemIncorretaEhEncontradoPelaIdentidadeFiscal() throws Exception {
        Documento legado = criarLegado("antigo.xml", CteFixture.xml());
        Viagem viagem = viagens.findAll().getFirst();
        viagem.setNumero("999");
        viagens.save(viagem);
        var resposta = enviar("novo.xml", CteFixture.xml(), 409);
        assertThat(resposta.get("mensagem").asText()).contains("incompatível");
        assertThat(documentos.count()).isEqualTo(1);
        assertThat(Files.readString(Path.of(legado.getCaminho()))).isEqualTo(CteFixture.xml());
        assertThat(destino(CteFixture.CHAVE)).doesNotExist();
    }

    @Test
    void legadoDeOutraViagemNaoIdentificavelTambemExigeReconciliacao() throws Exception {
        criarLegado("antigo.xml", "<invalido/>");
        Viagem viagem = viagens.findAll().getFirst();
        viagem.setNumero("999");
        viagens.save(viagem);
        enviar("novo.xml", CteFixture.xml(), 409);
        assertThat(viagens.count()).isEqualTo(1);
        assertThat(documentos.count()).isEqualTo(1);
        assertThat(destino(CteFixture.CHAVE)).doesNotExist();
    }

    @Test
    void arquivoSemRegistroEhPreservadoMesmoQuandoIdentico() throws Exception {
        Files.createDirectories(destino(CteFixture.CHAVE).getParent());
        Files.writeString(destino(CteFixture.CHAVE), CteFixture.xml());
        var resposta = enviar("novo.xml", CteFixture.xml(), 409);
        assertThat(resposta.get("mensagem").asText()).contains("destino já existe");
        assertThat(Files.readString(destino(CteFixture.CHAVE))).isEqualTo(CteFixture.xml());
        assertThat(documentos.count()).isZero();
        assertThat(clientes.count()).isZero();
        assertThat(viagens.count()).isZero();
    }

    @Test
    void detectaArquivoAlteradoDepoisDoCadastro() throws Exception {
        enviar("original.xml", CteFixture.xml(), 200);
        Files.writeString(destino(CteFixture.CHAVE), CteFixture.xml() + "\n");
        var resposta = enviar("novo.xml", CteFixture.xml(), 409);
        assertThat(resposta.get("mensagem").asText()).contains("hash registrado");
        assertThat(Files.readString(destino(CteFixture.CHAVE))).endsWith("\n\n");
        assertThat(documentos.count()).isEqualTo(1);
    }

    @Test
    void documentoIdentificadoSemArquivoNaoEhReutilizado() throws Exception {
        enviar("original.xml", CteFixture.xml(), 200);
        Files.delete(destino(CteFixture.CHAVE));
        enviar("novo.xml", CteFixture.xml(), 409);
        assertThat(documentos.count()).isEqualTo(1);
        assertThat(destino(CteFixture.CHAVE)).doesNotExist();
    }

    @Test
    void numeroDeViagemDeOutroClienteNaoAssociaDocumentoNemMantemClienteNovo() throws Exception {
        criarViagem("11111111111111");
        var resposta = enviar("novo.xml", CteFixture.xml(), 409);
        assertThat(resposta.get("mensagem").asText()).contains("outro cliente");
        assertThat(documentos.count()).isZero();
        assertThat(viagens.count()).isEqualTo(1);
        assertThat(clientes.count()).isEqualTo(1);
        assertThat(destino(CteFixture.CHAVE)).doesNotExist();
    }

    @Test
    void numeroDeViagemAmbiguoRetornaConflito() throws Exception {
        Viagem viagem = criarViagem("00000000000000");
        viagens.save(Viagem.builder().numero("66213").cliente(viagem.getCliente()).build());
        enviar("novo.xml", CteFixture.xml(), 409);
        assertThat(documentos.count()).isZero();
        assertThat(viagens.count()).isEqualTo(2);
        assertThat(destino(CteFixture.CHAVE)).doesNotExist();
    }

    @Test
    void rejeitaXmlInvalidoAntesDeCriarRegistros() throws Exception {
        enviar("novo.xml", "<invalido/>", 400);
        assertThat(documentos.count()).isZero();
        assertThat(viagens.count()).isZero();
        assertThat(clientes.count()).isZero();
        try (var arquivos = Files.list(ARQUIVOS)) {
            assertThat(arquivos.toList()).isEmpty();
        }
    }

    /** Fixtures históricas sem V4: adicionar somente a coluna opcional exigida pelo modelo atual. */
    @org.springframework.boot.test.context.TestConfiguration
    static class EsquemaHistorico {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy migracaoHistorica() {
            return flyway -> {
                flyway.migrate();
                try (var conexao = flyway.getConfiguration().getDataSource().getConnection(); var comando = conexao.createStatement()) {
                    comando.execute("ALTER TABLE documentos ADD COLUMN arquivado_em TIMESTAMP(6) WITH TIME ZONE");
                    org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(conexao,
                        new org.springframework.core.io.ClassPathResource("db/migration/V6__pacotes_e_anexos_compartilhados.sql"));
                } catch (java.sql.SQLException ex) { throw new IllegalStateException(ex); }
            };
        }
    }
}
