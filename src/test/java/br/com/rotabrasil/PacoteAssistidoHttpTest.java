package br.com.rotabrasil;

import com.fasterxml.jackson.databind.JsonNode;
import br.com.rotabrasil.manutencao.*;
import br.com.rotabrasil.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.util.*;
import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:pacotes-http;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
    "spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate","spring.h2.console.enabled=false"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PacoteAssistidoHttpTest {
    private static final Path RAIZ=temporario();
    private static final byte[] PDF="%PDF-1.4\n% PDF sintético, sem valor fiscal\n%%EOF".getBytes(StandardCharsets.UTF_8);
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource datasource;
    private static Path temporario(){try{return Files.createTempDirectory("rotabrasil-pacotes-");}catch(Exception ex){throw new IllegalStateException(ex);}}
    @DynamicPropertySource static void configurar(DynamicPropertyRegistry r){r.add("rotabrasil.arquivos.viagens",RAIZ::toString);}
    @BeforeEach void limpar()throws Exception {
        jdbc.execute("DROP TRIGGER IF EXISTS falha_pdf");
        jdbc.execute("ALTER TABLE anexos DROP CONSTRAINT IF EXISTS teste_falha_pdf");
        for(String tabela:List.of("anexo_viagens","anexos","pacote_documentos","pacotes","envios","documentos","viagens","clientes"))jdbc.update("DELETE FROM "+tabela);
        FileSystemUtils.deleteRecursively(RAIZ);Files.createDirectories(RAIZ);
    }
    @AfterAll static void remover()throws Exception{FileSystemUtils.deleteRecursively(RAIZ);}
    private static byte[] texto(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private static byte[] outroXml(){
        String base=CteFixture.CHAVE.substring(0,25)+"000066214"+CteFixture.CHAVE.substring(34,43);int soma=0,peso=2;
        for(int i=42;i>=0;i--){soma+=(base.charAt(i)-'0')*peso;peso=peso==9?2:peso+1;}
        int dv=11-soma%11;if(dv>=10)dv=0;
        return texto(CteFixture.xml().replace(CteFixture.CHAVE,base+dv).replace("<nCT>66213</nCT>","<nCT>66214</nCT>"));
    }
    private static byte[] zip(Map<String,byte[]> arquivos)throws Exception {
        var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){for(var e:arquivos.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}}return bytes.toByteArray();
    }
    private static byte[] pacote()throws Exception{
        var arquivos=new LinkedHashMap<String,byte[]>();arquivos.put("cte-1.xml",texto(CteFixture.xml()));arquivos.put("cte-2.xml",outroXml());arquivos.put("fatura.pdf",PDF);return zip(arquivos);
    }
    private ResponseEntity<JsonNode> enviar(String rota,byte[] bytes,Object config){
        var form=new LinkedMultiValueMap<String,Object>();form.add("arquivo",new ByteArrayResource(bytes){@Override public String getFilename(){return "viagens.zip";}});
        if(config!=null){var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);form.add("configuracao",new HttpEntity<>(config,headers));}
        var headers=new HttpHeaders();headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.postForEntity(rota,new HttpEntity<>(form,headers),JsonNode.class);
    }
    private JsonNode inspecao(byte[] bytes){var r=enviar("/api/pacotes/inspecao",bytes,null);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();}
    private Map<String,Object> config(JsonNode i,List<String> ids,String finalidade){return Map.of("sha256",i.get("sha256").asText(),"vinculos",List.of(Map.of("arquivoId","a2","finalidade",finalidade,"xmlIds",ids)));}
    private ResponseEntity<JsonNode> confirmar(byte[] bytes,Object config){return enviar("/api/pacotes/confirmacao",bytes,config);}
    private JsonNode importar(byte[] bytes){var r=confirmar(bytes,config(inspecao(bytes),List.of("a0","a1"),"FATURA"));assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();}
    private int contar(String tabela){return jdbc.queryForObject("SELECT COUNT(*) FROM "+tabela,Integer.class);}
    private void vazio()throws Exception {
        for(String tabela:List.of("clientes","viagens","documentos","pacotes","anexos","envios"))assertThat(contar(tabela)).as(tabela).isZero();
        try(var arquivos=Files.walk(RAIZ)){assertThat(arquivos.filter(Files::isRegularFile).count()).isZero();}
    }
    @Test void confereSemGravarEFaturaCompartilhadaPossuiUmArquivoEDoisVinculos()throws Exception{
        byte[] bytes=pacote();var i=inspecao(bytes);assertThat(i.get("arquivos").size()).isEqualTo(3);vazio();
        var resultado=importar(bytes);assertThat(resultado.get("documentos").size()).isEqualTo(2);assertThat(resultado.get("anexos").size()).isEqualTo(1);
        var anexo=resultado.get("anexos").get(0);assertThat(anexo.get("viagemIds").size()).isEqualTo(2);assertThat(anexo.has("caminho")).isFalse();
        assertThat(contar("anexo_viagens")).isEqualTo(2);assertThat(contar("anexos")).isEqualTo(1);assertThat(contar("envios")).isZero();
        Path caminho=Path.of(jdbc.queryForObject("SELECT caminho FROM anexos",String.class));assertThat(Files.readAllBytes(caminho)).isEqualTo(PDF);
        try(var conexao=datasource.getConnection()){var inventario=new InventarioBanco(RAIZ).analisar(conexao);assertThat(inventario.aprovado()).isTrue();assertThat(inventario.arquivos()).hasSize(3);assertThat(inventario.totais().get("anexo_viagens")).isEqualTo(2);}
        for(var id:anexo.get("viagemIds")){var lista=http.getForObject("/api/viagens/"+id.asLong()+"/anexos",JsonNode.class);assertThat(lista.get(0).get("id")).isEqualTo(anexo.get("id"));}
    }
    @Test void repetirMesmoZipReutilizaMasTrocarVinculoNaoAlteraOriginal()throws Exception{
        byte[] bytes=pacote();var primeiro=importar(bytes);var segundo=importar(bytes);
        assertThat(segundo.get("reutilizado").asBoolean()).isTrue();assertThat(segundo.get("pacoteId")).isEqualTo(primeiro.get("pacoteId"));
        assertThat(confirmar(bytes,config(inspecao(bytes),List.of("a0"),"VIAGEM")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(contar("documentos")).isEqualTo(2);assertThat(contar("anexos")).isEqualTo(1);assertThat(contar("anexo_viagens")).isEqualTo(2);
    }
    @Test void hashDiferenteImpedeConfirmacao()throws Exception{byte[] bytes=pacote();assertThat(confirmar(bytes,Map.of("sha256","0".repeat(64),"vinculos",List.of())).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);vazio();}
    @ParameterizedTest @ValueSource(strings={"VIAGEM","INEXISTENTE",""})
    void finalidadeInvalidaOuViagemComDoisCtesNaoGrava(String finalidade)throws Exception{byte[] bytes=pacote();assertThat(confirmar(bytes,config(inspecao(bytes),List.of("a0","a1"),finalidade)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);vazio();}
    @Test void faltaDeAssociacaoOuXmlAlheioOuDuplicadoNaoGrava()throws Exception{
        byte[] bytes=pacote();var i=inspecao(bytes);
        for(var config:List.of(Map.of("sha256",i.get("sha256").asText(),"vinculos",List.of()),config(i,List.of("a99"),"FATURA"),config(i,List.of("a0","a0"),"FATURA")))
            assertThat(confirmar(bytes,config).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        vazio();
    }
    @Test void falhaNoPdfReverteClientesViagensXmlsPacoteEVinculos()throws Exception{
        jdbc.execute("ALTER TABLE anexos ADD CONSTRAINT teste_falha_pdf CHECK (nome_arquivo <> 'fatura.pdf')");
        byte[] bytes=pacote();var response=confirmar(bytes,config(inspecao(bytes),List.of("a0","a1"),"FATURA"));
        assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();vazio();
    }
    @Test void falhaAoPublicarPdfDesfazXmlsJaPublicadosSemSobrescrever()throws Exception{
        byte[] bytes=pacote();var i=inspecao(bytes);
        Files.createDirectory(RAIZ.resolve("66213")); // Simular outro processo ocupando o destino antes da publicação.
        jdbc.execute("CREATE TRIGGER falha_pdf BEFORE INSERT ON anexos FOR EACH ROW CALL 'br.com.rotabrasil.PacoteAssistidoHttpTest$DestinoConflitante'");
        assertThat(confirmar(bytes,config(i,List.of("a0","a1"),"FATURA")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(contar("documentos")).isZero();assertThat(contar("pacotes")).isZero();
        try(var arquivos=Files.walk(RAIZ)){var restantes=arquivos.filter(Files::isRegularFile).toList();assertThat(restantes).hasSize(1);assertThat(Files.readString(restantes.getFirst())).isEqualTo("original preservado");}
    }
    public static class DestinoConflitante implements org.h2.api.Trigger {
        public void fire(java.sql.Connection c,Object[] oldRow,Object[] newRow)throws java.sql.SQLException{
            try{ // Localizar a coluna por metadados evita depender da ordem Hibernate/Flyway.
                var rs=c.createStatement().executeQuery("SELECT * FROM anexos WHERE 1=0");int indice=0;for(int n=1;n<=rs.getMetaData().getColumnCount();n++)if(rs.getMetaData().getColumnName(n).equalsIgnoreCase("caminho"))indice=n-1;
                Files.writeString(Path.of(newRow[indice].toString()),"original preservado",StandardOpenOption.CREATE_NEW);
            }catch(Exception ex){throw new java.sql.SQLException(ex);}
        }
    }
    @Test void previaExigeVinculoAtivoEArquivoPreservadoAoArquivarRestaurar()throws Exception{
        var resultado=importar(pacote());var anexo=resultado.get("anexos").get(0);long id=anexo.get("id").asLong(),viagem=anexo.get("viagemIds").get(0).asLong();
        Path path=Path.of(jdbc.queryForObject("SELECT caminho FROM anexos WHERE id=?",String.class,id));var mtime=Files.getLastModifiedTime(path);
        var pedido=Map.of("destinatario","teste@example.invalid","anexoIds",List.of(id));
        var previa=http.postForEntity("/api/viagens/"+viagem+"/previa-email",pedido,JsonNode.class);
        assertThat(previa.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(previa.getBody().get("enviado").asBoolean()).isFalse();assertThat(previa.getBody().get("anexos").size()).isEqualTo(1);
        assertThat(http.exchange("/api/anexos/"+id,HttpMethod.DELETE,HttpEntity.EMPTY,JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(http.postForEntity("/api/viagens/"+viagem+"/previa-email",pedido,JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        for(var v:anexo.get("viagemIds")){assertThat(http.getForObject("/api/viagens/"+v.asLong()+"/anexos",JsonNode.class).isEmpty()).isTrue();assertThat(http.getForObject("/api/viagens/"+v.asLong()+"/anexos?arquivados=true",JsonNode.class).size()).isEqualTo(1);}
        assertThat(http.postForEntity("/api/anexos/"+id+"/restaurar",HttpEntity.EMPTY,JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Files.readAllBytes(path)).isEqualTo(PDF);assertThat(Files.getLastModifiedTime(path)).isEqualTo(mtime);assertThat(contar("envios")).isZero();
    }
    @Test void pdfDeOutraViagemNaoEntraNaPrevia()throws Exception{
        byte[] bytes=pacote();var r=confirmar(bytes,config(inspecao(bytes),List.of("a0"),"VIAGEM"));assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        long id=r.getBody().get("anexos").get(0).get("id").asLong();long outra=r.getBody().get("documentos").get(1).get("viagemId").asLong();
        assertThat(http.postForEntity("/api/viagens/"+outra+"/previa-email",Map.of("destinatario","teste@example.invalid","anexoIds",List.of(id)),JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    @ParameterizedTest @ValueSource(strings={"../escape.pdf","/escape.pdf","pasta/../escape.pdf","C:/escape.pdf","pasta\\escape.pdf","programa.exe"})
    void entradasInsegurasOuTipoNaoPermitidoSaoRejeitados(String nome)throws Exception{
        byte[] bytes=zip(Map.of("cte.xml",texto(CteFixture.xml()),nome,PDF));assertThat(enviar("/api/pacotes/inspecao",bytes,null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);vazio();
    }
    @Test void pdfFalsoXmlInvalidoZipCorrompidoENctDuplicadoSaoRejeitados()throws Exception{
        for(byte[] bytes:List.of(zip(Map.of("cte.xml",texto(CteFixture.xml()),"falso.pdf",texto("nao e pdf"))),zip(Map.of("cte.xml",texto("<CTe>"))),zip(Map.of("a.xml",texto(CteFixture.xml()),"b.xml",texto(CteFixture.xml()))),texto("nao e zip")))
            assertThat(enviar("/api/pacotes/inspecao",bytes,null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        vazio();
    }
    @Test void zipBombEEntradasDemaisSaoRejeitados()throws Exception{
        byte[] enorme=new byte[2*1024*1024];System.arraycopy(PDF,0,enorme,0,PDF.length);
        assertThat(enviar("/api/pacotes/inspecao",zip(Map.of("cte.xml",texto(CteFixture.xml()),"grande.pdf",enorme)),null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var muitas=new LinkedHashMap<String,byte[]>();muitas.put("cte.xml",texto(CteFixture.xml()));for(int n=0;n<100;n++)muitas.put(n+".pdf",PDF);
        assertThat(enviar("/api/pacotes/inspecao",zip(muitas),null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);vazio();
    }
    @Test void cteDiferenteParaViagemExistenteExigeRevisao()throws Exception{
        FluxoImportacaoHttpTest.enviar(http,"primeiro.xml",CteFixture.xml().replace(CteFixture.CHAVE,CteFixture.outraChave()));
        byte[] bytes=pacote();assertThat(confirmar(bytes,config(inspecao(bytes),List.of("a0","a1"),"FATURA")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(contar("documentos")).isEqualTo(1);assertThat(contar("pacotes")).isZero();
    }
    @Test void inventarioDetectaAlteracaoDeVinculoEConteudoDoPdf()throws Exception{
        importar(pacote());try(var c=datasource.getConnection()){
            var inventario=new InventarioBanco(RAIZ);var antes=inventario.analisar(c);assertThat(antes.aprovado()).isTrue();
            jdbc.update("DELETE FROM anexo_viagens WHERE viagem_id=(SELECT MAX(viagem_id) FROM anexo_viagens)");assertThat(inventario.analisar(c).impressao()).isNotEqualTo(antes.impressao());
            Files.writeString(Path.of(jdbc.queryForObject("SELECT caminho FROM anexos",String.class)),"alterado");
            assertThat(inventario.analisar(c).achados()).extracting(InventarioBanco.Achado::codigo).contains("ANEXO_CONTEUDO_DIVERGENTE");
        }
    }
    @Test void confirmacoesConcorrentesReutilizamMesmoPacote()throws Exception{
        byte[] bytes=pacote();var config=config(inspecao(bytes),List.of("a0","a1"),"FATURA");
        var barreira=new java.util.concurrent.CyclicBarrier(2);var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var a=executor.submit(()->{barreira.await();return confirmar(bytes,config);});var b=executor.submit(()->{barreira.await();return confirmar(bytes,config);});
            var primeiro=a.get(20,java.util.concurrent.TimeUnit.SECONDS);var segundo=b.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(primeiro.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(segundo.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(primeiro.getBody().get("pacoteId")).isEqualTo(segundo.getBody().get("pacoteId"));assertThat(contar("pacotes")).isEqualTo(1);assertThat(contar("anexos")).isEqualTo(1);
        }finally{executor.shutdownNow();}
    }
    @Test void zipSemPdfPermiteImportarApenasCte()throws Exception{
        byte[] bytes=zip(Map.of("cte.xml",texto(CteFixture.xml())));var i=inspecao(bytes);
        assertThat(confirmar(bytes,Map.of("sha256",i.get("sha256").asText(),"vinculos",List.of())).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contar("documentos")).isEqualTo(1);assertThat(contar("anexos")).isZero();
    }
    @Test void nomesAmbiguosECrcAdulteradoSaoRejeitados()throws Exception{
        byte[] ambiguo=zip(Map.of("CTE.xml",texto(CteFixture.xml()),"cte.xml",texto(CteFixture.xml())));
        assertThat(enviar("/api/pacotes/inspecao",ambiguo,null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        byte[] corrompido=pacote();boolean adulterado=false;
        for(int i=0;i<corrompido.length-20;i++)if(corrompido[i]==0x50&&corrompido[i+1]==0x4b&&corrompido[i+2]==1&&corrompido[i+3]==2){corrompido[i+16]^=1;adulterado=true;break;}
        assertThat(adulterado).isTrue();assertThat(enviar("/api/pacotes/inspecao",corrompido,null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);vazio();
    }

}
