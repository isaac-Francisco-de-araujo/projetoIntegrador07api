package br.com.rotabrasil;

import br.com.rotabrasil.service.ConteudoXml;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:arquivamento-zip;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
    "spring.flyway.enabled=true","spring.h2.console.enabled=false","rotabrasil.seguranca.ativa=false"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ArquivamentoPacoteHttpTest {
    static final Path RAIZ=temporario();
    static final byte[] PDF="%PDF-1.4\n% fixture sintetica\n%%EOF".getBytes(StandardCharsets.UTF_8);
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @DynamicPropertySource static void configurar(DynamicPropertyRegistry r){r.add("rotabrasil.arquivos.viagens",RAIZ::toString);}
    static Path temporario(){try{return Files.createTempDirectory("rotabrasil-arquivar-zip-");}catch(IOException e){throw new UncheckedIOException(e);}}
    @BeforeEach void limpar()throws IOException{
        jdbc.execute("ALTER TABLE anexos DROP CONSTRAINT IF EXISTS teste_arquivamento_atomico");
        for(var table:List.of("tentativas_email","conferencias_email","anexo_viagens","anexos","pacote_documentos","pacotes","envios","documentos","viagens","clientes","processamentos"))jdbc.update("DELETE FROM "+table);
        FileSystemUtils.deleteRecursively(RAIZ);Files.createDirectories(RAIZ);
    }
    @AfterAll static void remover()throws IOException{FileSystemUtils.deleteRecursively(RAIZ);}
    static byte[] texto(String value){return value.getBytes(StandardCharsets.UTF_8);}
    static String segundoXml(){
        String base=CteFixture.CHAVE.substring(0,25)+"000066214"+CteFixture.CHAVE.substring(34,43);int soma=0,peso=2;
        for(int i=42;i>=0;i--){soma+=(base.charAt(i)-'0')*peso;peso=peso==9?2:peso+1;}
        int dv=11-soma%11;if(dv>=10)dv=0;
        return CteFixture.xml().replace(CteFixture.CHAVE,base+dv).replace("<nCT>66213</nCT>","<nCT>66214</nCT>");
    }
    byte[] pacote(boolean adicional)throws IOException{
        var entries=new LinkedHashMap<String,byte[]>();entries.put("cte.xml",texto(CteFixture.xml()));
        if(!adicional)entries.put("outro.xml",texto(segundoXml()));
        entries.put(adicional?"adicional.pdf":"fatura.pdf",PDF);if(!adicional)entries.put("viagem.pdf",PDF);
        var out=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(out)){for(var entry:entries.entrySet()){var zipEntry=new ZipEntry(entry.getKey());zipEntry.setTime(946684800000L);zip.putNextEntry(zipEntry);zip.write(entry.getValue());zip.closeEntry();}}return out.toByteArray();
    }
    JsonNode importar(boolean adicional)throws IOException{
        byte[] zip=pacote(adicional);var form=new LinkedMultiValueMap<String,Object>();
        form.add("arquivo",new ByteArrayResource(zip){@Override public String getFilename(){return adicional?"adicional.zip":"original.zip";}});
        var config=new LinkedHashMap<String,Object>();config.put("sha256",ConteudoXml.sha256(zip));
        config.put("vinculos",adicional?List.of(Map.of("arquivoId","a1","finalidade","VIAGEM","xmlIds",List.of("a0"))):
                List.of(Map.of("arquivoId","a2","finalidade","FATURA","xmlIds",List.of("a0","a1")),Map.of("arquivoId","a3","finalidade","VIAGEM","xmlIds",List.of("a0"))));
        var partHeaders=new HttpHeaders();partHeaders.setContentType(MediaType.APPLICATION_JSON);form.add("configuracao",new HttpEntity<>(config,partHeaders));
        var headers=new HttpHeaders();headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        var response=http.postForEntity("/api/pacotes/confirmacao",new HttpEntity<>(form,headers),JsonNode.class);assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);return response.getBody();
    }
    JsonNode conferir(long id){var response=http.getForEntity("/api/pacotes/"+id+"/conferencia-exclusao",JsonNode.class);assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);return response.getBody();}
    ResponseEntity<JsonNode> arquivar(long id,String hash,boolean confirmado){return http.exchange("/api/pacotes/"+id+"?conferenciaSha256="+hash+"&confirmado="+confirmado,HttpMethod.DELETE,HttpEntity.EMPTY,JsonNode.class);}
    int contar(String sql){return jdbc.queryForObject(sql,Integer.class);}
    Map<String,String> hashes()throws IOException{
        var result=new TreeMap<String,String>();try(var paths=Files.walk(RAIZ)){for(var file:paths.filter(Files::isRegularFile).toList())result.put(RAIZ.relativize(file).toString(),ConteudoXml.sha256(Files.readAllBytes(file)));}return result;
    }
    @Test void confereImpactoCompartilhadoSemArquivarOuExporCaminhos()throws Exception{
        var first=importar(false);var second=importar(true);long id=first.get("pacoteId").asLong();var before=hashes();var snapshot=conferir(id);
        assertThat(snapshot.get("documentos").size()).isEqualTo(2);assertThat(snapshot.get("anexos").size()).isEqualTo(2);
        assertThat(snapshot.get("documentos").get(0).get("outrosPacoteIds").get(0).asLong()).isEqualTo(second.get("pacoteId").asLong());
        assertThat(snapshot.get("anexos").get(0).get("viagens").size()).isEqualTo(2);
        assertThat(snapshot.toString()).doesNotContain(RAIZ.toString(),"caminho","senha");
        assertThat(contar("SELECT COUNT(*) FROM documentos WHERE arquivado_em IS NOT NULL")).isZero();assertThat(hashes()).isEqualTo(before);
    }
    @Test void exigeConfirmacaoEConferenciaAtualAntesDeModificar()throws Exception{
        long id=importar(false).get("pacoteId").asLong();String hash=conferir(id).get("conferenciaSha256").asText();
        assertThat(arquivar(id,hash,false).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(arquivar(id,"0".repeat(64),true).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(arquivar(id,"invalido",true).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        importar(true);assertThat(arquivar(id,hash,true).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(contar("SELECT COUNT(*) FROM documentos WHERE arquivado_em IS NOT NULL")).isZero();assertThat(contar("SELECT COUNT(*) FROM anexos WHERE arquivado_em IS NOT NULL")).isZero();
    }
    @Test void arquivaJuntoMantendoBytesIdentidadesHistoricosEArquivadosAnteriores()throws Exception{
        var first=importar(false);long id=first.get("pacoteId").asLong();var before=hashes();
        long oldPdf=first.get("anexos").get(1).get("id").asLong();assertThat(http.exchange("/api/anexos/"+oldPdf,HttpMethod.DELETE,HttpEntity.EMPTY,String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Object oldDate=jdbc.queryForObject("SELECT arquivado_em FROM anexos WHERE id=?",Object.class,oldPdf);
        long trip=first.get("documentos").get(0).get("viagemId").asLong();
        jdbc.update("INSERT INTO envios(viagem_id,cliente_id,status,mensagem) SELECT id,cliente_id,'SIMULADO','fixture' FROM viagens WHERE id=?",trip);
        int history=contar("SELECT COUNT(*) FROM processamentos");String hash=conferir(id).get("conferenciaSha256").asText();
        var response=arquivar(id,hash,true);assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(response.getBody().get("arquivado").asBoolean()).isTrue();
        assertThat(contar("SELECT COUNT(*) FROM documentos WHERE arquivado_em IS NOT NULL")).isEqualTo(2);assertThat(contar("SELECT COUNT(*) FROM anexos WHERE arquivado_em IS NOT NULL")).isEqualTo(2);
        assertThat(contar("SELECT COUNT(*) FROM pacotes")).isEqualTo(1);assertThat(contar("SELECT COUNT(*) FROM envios")).isEqualTo(1);assertThat(contar("SELECT COUNT(*) FROM processamentos")).isEqualTo(history);
        assertThat(jdbc.queryForObject("SELECT arquivado_em FROM anexos WHERE id=?",Object.class,oldPdf)).isEqualTo(oldDate);assertThat(hashes()).isEqualTo(before);
        assertThat(http.getForObject("/api/pacotes",JsonNode.class).size()).isZero();assertThat(http.getForObject("/api/pacotes?arquivados=true",JsonNode.class).size()).isEqualTo(1);
        Object archived=jdbc.queryForObject("SELECT arquivado_em FROM documentos ORDER BY id LIMIT 1",Object.class);
        assertThat(arquivar(id,hash,true).getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(jdbc.queryForObject("SELECT arquivado_em FROM documentos ORDER BY id LIMIT 1",Object.class)).isEqualTo(archived);
        assertThat(importar(false).get("reutilizado").asBoolean()).isTrue();assertThat(contar("SELECT COUNT(*) FROM documentos WHERE arquivado_em IS NULL")).isZero();
    }
    @Test void estadoAlteradoDepoisDaPreviaExigeNovaConferencia()throws Exception{
        long id=importar(false).get("pacoteId").asLong();var snapshot=conferir(id);long pdf=snapshot.get("anexos").get(0).get("id").asLong();
        http.exchange("/api/anexos/"+pdf,HttpMethod.DELETE,HttpEntity.EMPTY,String.class);
        assertThat(arquivar(id,snapshot.get("conferenciaSha256").asText(),true).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(contar("SELECT COUNT(*) FROM documentos WHERE arquivado_em IS NOT NULL")).isZero();assertThat(contar("SELECT COUNT(*) FROM anexos WHERE arquivado_em IS NULL")).isEqualTo(1);
    }
    @Test void falhaNoBancoReverteTodosOsArquivamentosSemAlterarArquivos()throws Exception{
        long id=importar(false).get("pacoteId").asLong();var before=hashes();String hash=conferir(id).get("conferenciaSha256").asText();
        jdbc.execute("ALTER TABLE anexos ADD CONSTRAINT teste_arquivamento_atomico CHECK (arquivado_em IS NULL)");
        assertThat(arquivar(id,hash,true).getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(contar("SELECT COUNT(*) FROM documentos WHERE arquivado_em IS NOT NULL")).isZero();assertThat(contar("SELECT COUNT(*) FROM anexos WHERE arquivado_em IS NOT NULL")).isZero();assertThat(hashes()).isEqualTo(before);
    }
    @Test void restauracaoIndividualNaoRestauraAutomaticamenteOutrosArquivos()throws Exception{
        var original=importar(false);long id=original.get("pacoteId").asLong();var second=importar(true);String hash=conferir(id).get("conferenciaSha256").asText();
        assertThat(arquivar(id,hash,true).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForObject("/api/pacotes",JsonNode.class).get(0).get("id").asLong()).isEqualTo(second.get("pacoteId").asLong());
        long doc=original.get("documentos").get(0).get("id").asLong();assertThat(http.postForEntity("/api/documentos/"+doc+"/restaurar",HttpEntity.EMPTY,JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contar("SELECT COUNT(*) FROM documentos WHERE arquivado_em IS NULL")).isEqualTo(1);assertThat(contar("SELECT COUNT(*) FROM anexos WHERE arquivado_em IS NULL")).isEqualTo(1);
        assertThat(http.getForObject("/api/pacotes",JsonNode.class).size()).isEqualTo(2);
    }
    @Test void exclusoesConcorrentesDaMesmaConferenciaSaoIdempotentes()throws Exception{
        long id=importar(false).get("pacoteId").asLong();String hash=conferir(id).get("conferenciaSha256").asText();
        try(var executor=Executors.newFixedThreadPool(2)){
            var one=executor.submit(()->arquivar(id,hash,true));var two=executor.submit(()->arquivar(id,hash,true));
            assertThat(one.get(20,TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(two.get(20,TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(contar("SELECT COUNT(*) FROM documentos WHERE arquivado_em IS NOT NULL")).isEqualTo(2);assertThat(contar("SELECT COUNT(*) FROM anexos WHERE arquivado_em IS NOT NULL")).isEqualTo(2);
    }
    @Test void validaIdsEPaginacaoSemCriarEstado() {
        assertThat(http.getForEntity("/api/pacotes/999/conferencia-exclusao",JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.getForEntity("/api/pacotes/-1/conferencia-exclusao",JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.getForEntity("/api/pacotes?pagina=-1",JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.getForEntity("/api/pacotes?tamanho=101",JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
