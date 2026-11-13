package br.com.rotabrasil;

import br.com.rotabrasil.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileSystemUtils;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.datasource.url=jdbc:h2:mem:exclusao-cliente;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
        "spring.flyway.enabled=true","spring.h2.console.enabled=false","rotabrasil.email.modo=SIMULADO"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ExclusaoClienteHttpTest {
    static final Path RAIZ=temporario();
    static Path temporario(){try{return Files.createTempDirectory("rotabrasil-exclusao-cliente-");}catch(Exception ex){throw new IllegalStateException(ex);}}
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("rotabrasil.arquivos.viagens",RAIZ::toString);}
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ExclusaoClienteService exclusao;
    @Autowired LimpezaArquivosClienteService limpeza;
    @Autowired PlatformTransactionManager manager;

    @BeforeEach void preparar()throws Exception{
        jdbc.update("UPDATE conferencias_email SET anterior_id=NULL");jdbc.update("UPDATE tentativas_email SET anterior_id=NULL");
        for(String tabela:List.of("tentativas_email","conferencias_email","envios","anexo_viagens","anexos","pacote_documentos","documentos","pacotes","viagens","clientes"))jdbc.update("DELETE FROM "+tabela);
        FileSystemUtils.deleteRecursively(RAIZ);Files.createDirectories(RAIZ);
        jdbc.update("INSERT INTO clientes(id,nome,cnpj) VALUES(101,'Cliente Sintético A','00000000000000'),(102,'Cliente Sintético B','11111111111111')");
        jdbc.update("INSERT INTO viagens(id,numero,cliente_id) VALUES(201,'101',101),(202,'102',102)");
        jdbc.update("INSERT INTO pacotes(id,nome_arquivo,sha256,configuracao_sha256,criado_em) VALUES(301,'sintetico.zip',?,?,CURRENT_TIMESTAMP)","a".repeat(64),"b".repeat(64));
        documento(401,201,"101/cte.xml","1".repeat(44));documento(402,202,"102/cte.xml","2".repeat(44));
        jdbc.update("INSERT INTO pacote_documentos VALUES(301,401),(301,402)");
        anexo(501,"101/exclusivo.pdf");anexo(502,"102/compartilhado.pdf");
        jdbc.update("INSERT INTO anexo_viagens VALUES(501,201),(502,201),(502,202)");
    }
    void documento(long id,long viagem,String caminho,String chave)throws Exception{
        byte[] bytes=CteFixture.xml().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.createDirectories(RAIZ.resolve(caminho).getParent());Files.write(RAIZ.resolve(caminho),bytes);
        jdbc.update("INSERT INTO documentos(id,nome_arquivo,tipo,caminho,viagem_id,chave_acesso,sha256) VALUES(?,'cte.xml','XML',?,?,?,?)",id,caminho,viagem,chave,ConteudoXml.sha256(bytes));
    }
    void anexo(long id,String caminho)throws Exception{
        byte[] bytes="%PDF-1.4\nPDF exclusivamente sintético\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.createDirectories(RAIZ.resolve(caminho).getParent());Files.write(RAIZ.resolve(caminho),bytes);
        jdbc.update("INSERT INTO anexos(id,pacote_id,nome_arquivo,finalidade,caminho,sha256,token,numero_armazenamento) VALUES(?,301,'sintetico.pdf','FATURA',?,?,?,'101')",id,caminho,ConteudoXml.sha256(bytes),UUID.randomUUID().toString());
    }
    @AfterAll static void remover()throws Exception{FileSystemUtils.deleteRecursively(RAIZ);}
    JsonNode conferir(){var r=http.getForEntity("/api/clientes/101/conferencia-exclusao",JsonNode.class);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();}
    String rota(JsonNode c){return "/api/clientes/101?confirmado=true&conferenciaSha256="+c.get("conferenciaSha256").asText();}
    ResponseEntity<JsonNode> apagar(String rota){return http.exchange(rota,HttpMethod.DELETE,HttpEntity.EMPTY,JsonNode.class);}
    int total(String tabela){return jdbc.queryForObject("SELECT COUNT(*) FROM "+tabela,Integer.class);}
    void preservado()throws Exception{
        assertThat(total("clientes")).isEqualTo(2);assertThat(total("viagens")).isEqualTo(2);assertThat(total("documentos")).isEqualTo(2);assertThat(total("anexos")).isEqualTo(2);
        assertThat(Files.readString(RAIZ.resolve("101/cte.xml"))).isEqualTo(CteFixture.xml());assertThat(RAIZ.resolve("101/exclusivo.pdf")).exists();
    }
    void historico(String situacao){
        jdbc.update("INSERT INTO conferencias_email(id,viagem_id,selecao,snapshot,criada_em,expira_em) VALUES('c1',201,'{}','{}',CURRENT_TIMESTAMP,DATEADD('MINUTE',30,CURRENT_TIMESTAMP))");
        jdbc.update("INSERT INTO tentativas_email(id,conferencia_id,viagem_id,snapshot,modo,situacao,motivo,criada_em) VALUES('t1','c1',201,'{}','SIMULADO',?,'Fixture sintética',CURRENT_TIMESTAMP)",situacao);
    }

    @Test void conferenciaMostraImpactoSemExporCaminhosNemAlterarDados()throws Exception{
        var c=conferir();assertThat(c.get("viagens").asInt()).isEqualTo(1);assertThat(c.get("documentos").asInt()).isEqualTo(1);
        assertThat(c.get("pdfsExclusivos").asInt()).isEqualTo(1);assertThat(c.get("pdfsCompartilhados").asInt()).isEqualTo(1);
        assertThat(c.toString()).doesNotContain("caminho",RAIZ.toString());preservado();
    }
    @Test void exigeConfirmacaoEConferenciaAtualSemExcluirParcialmente()throws Exception{
        assertThat(apagar("/api/clientes/101").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(apagar("/api/clientes/101?confirmado=true&conferenciaSha256="+"0".repeat(64)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var c=conferir();jdbc.update("INSERT INTO viagens(numero,cliente_id) VALUES('103',101)");
        assertThat(apagar(rota(c)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(total("clientes")).isEqualTo(2);assertThat(RAIZ.resolve("101/cte.xml")).exists();
    }
    @Test void exclusaoRemoveAgregadoEBytesExclusivosMasMantemOutroClienteEPdfCompartilhado()throws Exception{
        historico("SIMULADO");
        jdbc.update("INSERT INTO envios(viagem_id,cliente_id,status) VALUES(201,101,'ENVIADO')");
        byte[] compartilhado=Files.readAllBytes(RAIZ.resolve("102/compartilhado.pdf"));
        var c=conferir();assertThat(apagar(rota(c)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(total("clientes")).isEqualTo(1);assertThat(total("viagens")).isEqualTo(1);assertThat(total("documentos")).isEqualTo(1);
        assertThat(total("anexos")).isEqualTo(1);assertThat(total("anexo_viagens")).isEqualTo(1);assertThat(total("pacote_documentos")).isEqualTo(1);
        assertThat(total("envios")).isZero();assertThat(total("tentativas_email")).isZero();assertThat(total("conferencias_email")).isZero();
        assertThat(RAIZ.resolve("101/cte.xml")).doesNotExist();assertThat(RAIZ.resolve("101/exclusivo.pdf")).doesNotExist();
        assertThat(Files.readAllBytes(RAIZ.resolve("102/compartilhado.pdf"))).isEqualTo(compartilhado);assertThat(RAIZ.resolve("102/cte.xml")).exists();
        assertThat(http.getForEntity("/api/anexos/502/arquivo",byte[].class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/api/clientes/101",JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(apagar(rota(c)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
    @Test void resultadoDesconhecidoNaoEApagadoNemPermiteExcluirCliente()throws Exception{
        historico("DESCONHECIDO");var c=conferir();assertThat(c.get("tentativasDesconhecidas").asInt()).isEqualTo(1);
        assertThat(apagar(rota(c)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);preservado();assertThat(total("tentativas_email")).isEqualTo(1);
    }
    @Test void rollbackPreservaBancoArquivosEHistoricos()throws Exception{
        historico("SIMULADO");var c=conferir();
        assertThatThrownBy(()->new TransactionTemplate(manager).executeWithoutResult(status->{exclusao.excluir(101L,true,c.get("conferenciaSha256").asText());throw new IllegalStateException("Falha sintética antes do commit");})).isInstanceOf(IllegalStateException.class);
        preservado();assertThat(total("tentativas_email")).isEqualTo(1);
        try(var lista=Files.list(RAIZ.resolve(".exclusoes-clientes"))){assertThat(lista.toList()).isEmpty();}
    }
    @Test void arquivoAlteradoOuCaminhoForaDaRaizBloqueiaSemExcluirDados()throws Exception{
        Files.writeString(RAIZ.resolve("101/cte.xml"),"Alteração sintética");
        assertThat(apagar(rota(conferir())).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(total("clientes")).isEqualTo(2);
        Path fora=Files.createTempFile("rotabrasil-fora-acervo-",".xml");
        try{
            Files.writeString(fora,"Preservar fixture externa");jdbc.update("UPDATE documentos SET caminho=? WHERE id=401",fora.toString());
            assertThat(apagar(rota(conferir())).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(Files.readString(fora)).isEqualTo("Preservar fixture externa");assertThat(total("clientes")).isEqualTo(2);
        }finally{Files.deleteIfExists(fora);}
    }
    @Test void recuperacaoDeJournalRespeitaReferenciasExistentesEConcluiLimpezaPendente()throws Exception{
        Path pasta=Files.createDirectories(RAIZ.resolve(".exclusoes-clientes"));
        Path orfao=RAIZ.resolve("101/orfao.pdf");Files.writeString(orfao,"PDF sintético órfão");
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        json.writeValue(pasta.resolve(UUID.randomUUID()+".json").toFile(),List.of(
                new LimpezaArquivosClienteService.Arquivo("101/orfao.pdf",ConteudoXml.sha256(Files.readAllBytes(orfao))),
                new LimpezaArquivosClienteService.Arquivo("101/cte.xml",ConteudoXml.sha256(Files.readAllBytes(RAIZ.resolve("101/cte.xml"))))));
        limpeza.recuperarPendentes();assertThat(orfao).doesNotExist();preservado();
        try(var lista=Files.list(pasta)){assertThat(lista.toList()).isEmpty();}
    }
    @Test void encadeamentoDeReenviosConhecidosEConferenciasExpiradasPodeSerExcluido(){
        historico("SIMULADO");
        jdbc.update("INSERT INTO conferencias_email(id,viagem_id,selecao,snapshot,anterior_id,criada_em,expira_em) VALUES('c2',201,'{}','{}','t1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO tentativas_email(id,conferencia_id,viagem_id,snapshot,anterior_id,modo,situacao,motivo,criada_em) VALUES('t2','c2',201,'{}','t1','SIMULADO','SIMULADO','Fixture',CURRENT_TIMESTAMP)");
        assertThat(apagar(rota(conferir())).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);assertThat(total("tentativas_email")).isZero();
    }
}
