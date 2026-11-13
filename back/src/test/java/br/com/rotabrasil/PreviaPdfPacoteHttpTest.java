package br.com.rotabrasil;

import br.com.rotabrasil.service.ConteudoXml;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:previa-pdf;DB_CLOSE_DELAY=-1","spring.flyway.enabled=true","rotabrasil.seguranca.ativa=true"})
@AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PreviaPdfPacoteHttpTest {
    static final Path RAIZ=temporario();
    static final byte[] PDF="%PDF-1.4\n% PDF sintético, somente cabeçalho\n%%EOF".getBytes(StandardCharsets.UTF_8);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void usuarios(){
        for(var nome:List.of("operador","leitor")){
            jdbc.update("DELETE FROM usuarios WHERE usuario=?",nome);
            jdbc.update("INSERT INTO usuarios(usuario,senha_hash,papel,criado_em) VALUES(?,?,?,CURRENT_TIMESTAMP)",nome,"hash-sintetico-nao-utilizado",nome.equals("operador")?"OPERADOR":"CONSULTA");
        }
    }
    @DynamicPropertySource static void propriedades(DynamicPropertyRegistry r){
        r.add("rotabrasil.arquivos.viagens",RAIZ::toString);
        r.add("rotabrasil.seguranca.admin-usuario",()->"admin-previa");
        r.add("rotabrasil.seguranca.admin-hash",()->new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("SenhaSinteticaDaPrevia12!"));
    }
    static Path temporario(){try{return Files.createTempDirectory("rotabrasil-previa-pdf-");}catch(IOException e){throw new UncheckedIOException(e);}}
    @AfterAll static void remover()throws IOException{FileSystemUtils.deleteRecursively(RAIZ);}
    byte[] zip(String xml,String caminho)throws IOException{
        var bytes=new ByteArrayOutputStream();
        try(var z=new ZipOutputStream(bytes)){
            z.putNextEntry(new ZipEntry("cte.xml"));z.write(xml.getBytes(StandardCharsets.UTF_8));z.closeEntry();
            z.putNextEntry(new ZipEntry(caminho));z.write(PDF);z.closeEntry();
        }
        return bytes.toByteArray();
    }
    MockMultipartFile arquivo(byte[] bytes){return new MockMultipartFile("arquivo","sintetico.zip","application/zip",bytes);}
    void semImportacao()throws IOException{
        for(var table:List.of("clientes","viagens","documentos","pacotes","anexos","conferencias_email","tentativas_email","processamentos"))
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).as(table).isZero();
        try(var files=Files.walk(RAIZ)){assertThat(files.filter(Files::isRegularFile).count()).isZero();}
    }
    @Test void operadorObtemBytesExatosSemImportarOuExporCaminhos()throws Exception{
        var bytes=zip(CteFixture.xml(),"faturas/sintético.pdf");
        mvc.perform(multipart("/api/pacotes/previa-pdf").file(arquivo(bytes)).param("arquivoId","a1").param("sha256",ConteudoXml.sha256(bytes)).with(user("operador").roles("OPERADOR")).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(content().bytes(PDF))
                .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(header().string("Content-Security-Policy","sandbox; default-src 'none'"))
                .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("faturas/"))));
        semImportacao();
    }
    @Test void permiteConferirPdfValidoQuandoOutroArquivoTemErroDeConteudo()throws Exception{
        var bytes=zip("<xml-invalido/>","fatura.pdf");
        mvc.perform(multipart("/api/pacotes/previa-pdf").file(arquivo(bytes)).param("arquivoId","a1").param("sha256",ConteudoXml.sha256(bytes)).with(user("operador").roles("OPERADOR")).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));semImportacao();
    }
    @Test void recusaZipTrocadoEntradaNaoPdfEIdInvalido()throws Exception{
        var bytes=zip(CteFixture.xml(),"fatura.pdf");
        mvc.perform(multipart("/api/pacotes/previa-pdf").file(arquivo(bytes)).param("arquivoId","a1").param("sha256","0".repeat(64)).with(user("operador").roles("OPERADOR")).with(csrf())).andExpect(status().isConflict());
        mvc.perform(multipart("/api/pacotes/previa-pdf").file(arquivo(bytes)).param("arquivoId","a0").param("sha256",ConteudoXml.sha256(bytes)).with(user("operador").roles("OPERADOR")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/pacotes/previa-pdf").file(arquivo(bytes)).param("arquivoId","../fatura.pdf").param("sha256",ConteudoXml.sha256(bytes)).with(user("operador").roles("OPERADOR")).with(csrf())).andExpect(status().isBadRequest());semImportacao();
    }
    @Test void recusaPacoteComInterrupcaoDeSegurancaMesmoSePdfAnteriorFoiLido()throws Exception{
        var bytes=zip(CteFixture.xml(),"../fatura.pdf");
        mvc.perform(multipart("/api/pacotes/previa-pdf").file(arquivo(bytes)).param("arquivoId","a1").param("sha256",ConteudoXml.sha256(bytes)).with(user("operador").roles("OPERADOR")).with(csrf())).andExpect(status().isBadRequest());semImportacao();
    }
    @Test void preservaAutenticacaoPapelECsrfNaRotaDePrevia()throws Exception{
        var bytes=zip(CteFixture.xml(),"fatura.pdf");
        mvc.perform(multipart("/api/pacotes/previa-pdf").file(arquivo(bytes)).param("arquivoId","a1").param("sha256",ConteudoXml.sha256(bytes)).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/pacotes/previa-pdf").file(arquivo(bytes)).param("arquivoId","a1").param("sha256",ConteudoXml.sha256(bytes)).with(user("leitor").roles("CONSULTA")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/pacotes/previa-pdf").file(arquivo(bytes)).param("arquivoId","a1").param("sha256",ConteudoXml.sha256(bytes)).with(user("operador").roles("OPERADOR"))).andExpect(status().isForbidden());semImportacao();
    }
}
