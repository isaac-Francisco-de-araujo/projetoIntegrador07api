package br.com.rotabrasil;

import br.com.rotabrasil.service.*;
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
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.datasource.url=jdbc:h2:mem:melhorias;DB_CLOSE_DELAY=-1","spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate","rotabrasil.arquivos.caminhos-relativos=true"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class MelhoriasHttpTest {
    static final Path RAIZ=temp();
    static Path temp(){try{return Files.createTempDirectory("rotabrasil-melhorias-");}catch(Exception ex){throw new IllegalStateException(ex);}}
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("rotabrasil.arquivos.viagens",RAIZ::toString);}
    @Autowired TestRestTemplate http;@Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.context.ApplicationContext contexto;
    @BeforeEach void limpar()throws Exception{
        jdbc.update("UPDATE processamentos SET anterior_id=NULL");
        jdbc.update("UPDATE conferencias_email SET anterior_id=NULL");jdbc.update("DELETE FROM tentativas_email");jdbc.update("DELETE FROM conferencias_email");
        for(String t:List.of("processamentos","anexo_viagens","anexos","pacote_documentos","pacotes","documentos","viagens","clientes"))jdbc.update("DELETE FROM "+t);
        FileSystemUtils.deleteRecursively(RAIZ);Files.createDirectories(RAIZ);
    }
    @AfterAll static void remover()throws Exception{FileSystemUtils.deleteRecursively(RAIZ);}
    ResponseEntity<JsonNode> pacote(String fase,byte[] bytes,Object config,String anterior){
        var form=new LinkedMultiValueMap<String,Object>();form.add("arquivo",new ByteArrayResource(bytes){public String getFilename(){return "sintetico.zip";}});
        if(config!=null){var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);form.add("configuracao",new HttpEntity<>(config,h));}
        var h=new HttpHeaders();h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.postForEntity("/api/pacotes/"+fase+(anterior==null?"":"?anteriorOperacaoId="+anterior),new HttpEntity<>(form,h),JsonNode.class);
    }
    @Test void validaCadastroNoXmlAntesDeCriarRegistros()throws Exception{
        for(String xml:List.of(CteFixture.xml().replace("00000000000000","ABC"),CteFixture.xml().replace("Cliente Teste","X".repeat(300)),CteFixture.xml().replace("<enderDest>","<email>sem-arroba</email><enderDest>"))){
            assertThatThrownBy(()->new ExtratorDadosXml().extrair(new java.io.ByteArrayInputStream(DiagnosticoPacoteTest.texto(xml)))).isInstanceOf(br.com.rotabrasil.exception.DadosXmlInvalidosException.class);
            var r=FluxoImportacaoHttpTest.enviar(http,"teste.xml",xml);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clientes",Integer.class)).isZero();
            var arquivos=DiagnosticoPacoteTest.arquivos();arquivos.put("ctes/viagem.xml",DiagnosticoPacoteTest.texto(xml));
            var d=pacote("inspecao",DiagnosticoPacoteTest.zip(arquivos),null,null).getBody().get("diagnostico");
            assertThat(d.get("conteudoValido").asBoolean()).isFalse();assertThat(d.get("diagnosticos").get(0).get("codigo").asText()).as(d.toString()).isIn("CNPJ_INVALIDO","CAMPO_CADASTRAL_INVALIDO","CONTATO_XML_INVALIDO");
        }
    }
    @Test void normalizaNomeEUsaCaminhoRelativoComDownloadIntegro()throws Exception{
        var r=FluxoImportacaoHttpTest.enviar(http,"pasta/seguro.xml",CteFixture.xml());assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);long id=r.getBody().get("documentoId").asLong();
        assertThat(jdbc.queryForObject("SELECT nome_arquivo FROM documentos",String.class)).isEqualTo("seguro.xml");
        String caminho=jdbc.queryForObject("SELECT caminho FROM documentos",String.class);assertThat(Path.of(caminho).isAbsolute()).isFalse();
        var download=http.getForEntity("/api/documentos/"+id+"/arquivo",byte[].class);assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(download.getBody()).isEqualTo(DiagnosticoPacoteTest.texto(CteFixture.xml()));
        assertThat(download.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment","seguro.xml");
        Files.writeString(RAIZ.resolve(caminho),"adulterado");var erro=http.getForEntity("/api/documentos/"+id+"/arquivo",JsonNode.class);
        assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(erro.getBody().toString()).doesNotContain(RAIZ.toString());
    }
    @Test void cadastroManualAplicaMesmasRegrasDeControleDosCamposImportados(){
        var resposta=http.postForEntity("/api/clientes",Map.of("nome","Cliente sintetico","cnpj","00000000000000","email","teste@example.invalid","telefone","123\r\n456"),JsonNode.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clientes",Integer.class)).isZero();
        assertThat(resposta.getBody().toString()).doesNotContain("123\\r\\n456","teste@example.invalid");
    }
    @Test void historicoPreservaFalhaEEncadeiaCorrecaoAteConfirmacao()throws Exception{
        var arquivos=DiagnosticoPacoteTest.arquivos();arquivos.put("fatura.pdf",DiagnosticoPacoteTest.texto("invalido"));
        var antiga=pacote("inspecao",DiagnosticoPacoteTest.zip(arquivos),null,null).getBody().get("diagnostico");String primeiro=antiga.get("operacaoId").asText();
        arquivos.put("fatura.pdf",DiagnosticoPacoteTest.texto("%PDF-sintetico"));byte[] corrigido=DiagnosticoPacoteTest.zip(arquivos);
        var nova=pacote("inspecao",corrigido,null,primeiro).getBody();String segundo=nova.get("operacaoId").asText();
        var config=Map.of("sha256",nova.get("sha256").asText(),"vinculos",List.of(Map.of("arquivoId","a1","finalidade","FATURA","xmlIds",List.of("a0"))));
        var confirmado=pacote("confirmacao",corrigido,config,segundo);assertThat(confirmado.getStatusCode()).isEqualTo(HttpStatus.OK);
        var historico=http.getForObject("/api/processamentos/"+confirmado.getBody().get("operacaoId").asText(),JsonNode.class);
        assertThat(historico.get("ANTERIOR_ID").asText()).isEqualTo(segundo);assertThat(historico.get("SITUACAO").asText()).isEqualTo("IMPORTADO");
        assertThat(http.getForObject("/api/processamentos/"+primeiro,JsonNode.class).get("DIAGNOSTICO")).isEqualTo(antiga);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM envios",Integer.class)).isZero();
    }
    @Test void filtrosPaginasETotaisNaoDependemDeCarregarAcervoTodo(){
        jdbc.update("INSERT INTO clientes(nome,cnpj) VALUES('Cliente sintetico','00000000000000')");long c=jdbc.queryForObject("SELECT id FROM clientes",Long.class);
        for(int n=1;n<=55;n++)jdbc.update("INSERT INTO viagens(numero,cliente_id,data,status) VALUES(?,?,DATE '2026-01-01','Pendente')",String.valueOf(n),c);
        var primeira=http.getForObject("/api/consulta/viagens",JsonNode.class);var segunda=http.getForObject("/api/consulta/viagens?pagina=1",JsonNode.class);
        assertThat(primeira.size()).isEqualTo(50);assertThat(segunda.size()).isEqualTo(5);
        assertThat(http.getForObject("/api/consulta/viagens?inicio=2026-02-01",JsonNode.class).isEmpty()).isTrue();
        assertThat(http.getForObject("/api/consulta/resumo",JsonNode.class).get("viagens").asInt()).isEqualTo(55);
        assertThat(http.getForEntity("/api/consulta/viagens?tamanho=10000",JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test void conferenciaExpiradaSemTentativaPodeSerRemovidaSemApagarAuditoriaDeEnvio(){
        FluxoImportacaoHttpTest.enviar(http,"sintetico.xml",CteFixture.xml());long v=jdbc.queryForObject("SELECT id FROM viagens",Long.class);
        jdbc.update("INSERT INTO conferencias_email(id,viagem_id,selecao,snapshot,criada_em,expira_em) VALUES(?,?,'{}','{}',CURRENT_TIMESTAMP,DATEADD('HOUR',-1,CURRENT_TIMESTAMP))",UUID.randomUUID().toString(),v);
        var r=http.postForEntity("/api/admin/retencao",Map.of("diasProcessamentos",365,"confirmado",true),JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(r.getBody().get("conferenciasRemovidas").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM viagens",Integer.class)).isEqualTo(1);
    }
    @Test void orquestracaoDeAceiteSmtpUsaModoRealSemConfundirComSimulacao(){
        var r=FluxoImportacaoHttpTest.enviar(http,"sintetico.xml",CteFixture.xml()).getBody();
        var transporte=org.mockito.Mockito.mock(br.com.rotabrasil.service.email.TransporteEmail.class);
        org.mockito.Mockito.when(transporte.enviar(org.mockito.ArgumentMatchers.any())).thenReturn(br.com.rotabrasil.service.email.TransporteEmail.Resultado.ACEITO);
        var real=new br.com.rotabrasil.service.email.EnvioManualService(jdbc,contexto.getBean(com.fasterxml.jackson.databind.ObjectMapper.class),
                contexto.getBean(br.com.rotabrasil.service.email.PreparacaoEnvioService.class),contexto.getBean(EmailService.class),contexto.getBean(ViagemService.class),contexto.getBean(CoordenacaoImportacaoService.class),transporte,contexto.getBean(org.springframework.transaction.PlatformTransactionManager.class),"REAL",org.mockito.Mockito.mock(br.com.rotabrasil.service.email.ProntidaoEnvioDocumental.class));
        long viagem=r.get("viagemId").asLong();
        var conf=real.conferir(viagem,new br.com.rotabrasil.dto.EnvioManualDtos.Conferir(new br.com.rotabrasil.dto.PreviaEmailRequest("sintetico@example.invalid",List.of(r.get("documentoId").asLong())),null));
        var tentativa=real.confirmar(viagem,new br.com.rotabrasil.dto.EnvioManualDtos.Confirmar(conf.id(),true));
        assertThat(tentativa.modo()).isEqualTo("REAL");assertThat(tentativa.situacao()).isEqualTo("ACEITO_SMTP");assertThat(tentativa.motivo()).contains("não comprova entrega ou leitura");assertThat(tentativa.snapshot().versao()).isEqualTo(2);
        assertThat(tentativa.snapshot().assunto()).isEqualTo("Documentos de viagem - referência #"+viagem);
        assertThat(tentativa.snapshot().mensagem()).contains("conferidos manualmente");
        real.confirmar(viagem,new br.com.rotabrasil.dto.EnvioManualDtos.Confirmar(conf.id(),true));org.mockito.Mockito.verify(transporte,org.mockito.Mockito.times(1)).enviar(org.mockito.ArgumentMatchers.any());
    }
}
