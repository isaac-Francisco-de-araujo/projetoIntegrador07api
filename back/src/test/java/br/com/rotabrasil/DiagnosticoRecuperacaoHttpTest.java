package br.com.rotabrasil;

import br.com.rotabrasil.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.util.*;
import org.springframework.web.bind.annotation.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.datasource.url=jdbc:h2:mem:diagnostico-recuperacao;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000",
        "spring.flyway.enabled=true","spring.h2.console.enabled=false","rotabrasil.email.modo=SIMULADO"})
@Import(DiagnosticoRecuperacaoHttpTest.FalhaControlada.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class DiagnosticoRecuperacaoHttpTest {
    static final Path TMP=temporario(),ACERVO=TMP.resolve("acervo"),SAIDA=TMP.resolve("saida");
    static Path temporario(){try{return Files.createTempDirectory("rotabrasil-recuperacao-http-");}catch(Exception ex){throw new IllegalStateException(ex);}}
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("rotabrasil.arquivos.viagens",ACERVO::toString);r.add("rotabrasil.exportacao.raiz",SAIDA::toString);}
    @Autowired TestRestTemplate http;@Autowired JdbcTemplate jdbc;
    @TestConfiguration @RestController static class FalhaControlada {
        @GetMapping("/api/fixture/falha") public void falhar(){throw new IllegalStateException("SENHA_PRIVADA jdbc:SQL SELECT segredo fiscal completo");}
    }
    @BeforeEach void preparar()throws Exception{
        jdbc.update("UPDATE conferencias_email SET anterior_id=NULL");jdbc.update("UPDATE tentativas_email SET anterior_id=NULL");jdbc.update("UPDATE processamentos SET anterior_id=NULL");
        for(String t:List.of("exportacao_arquivos","exportacoes","notificacoes_recebimento","recebimentos","testes_configuracao","tentativas_email","conferencias_email","anexo_viagens","anexos","pacote_documentos","envios","documentos","pacotes","viagens","clientes","processamentos"))jdbc.update("DELETE FROM "+t);
        jdbc.update("UPDATE configuracao_operacao SET versao=0,remetente=NULL,exportacao_automatica=FALSE,subpasta='' WHERE id=1");
        FileSystemUtils.deleteRecursively(ACERVO);FileSystemUtils.deleteRecursively(SAIDA);Files.createDirectories(ACERVO);Files.createDirectories(SAIDA);
    }
    @AfterAll static void limpar()throws Exception{FileSystemUtils.deleteRecursively(TMP);}
    byte[] zip(boolean valido)throws Exception{var a=DiagnosticoPacoteTest.arquivos();a.put("cobrancas/fatura.pdf",DiagnosticoPacoteTest.texto(valido?"%PDF-1.4\nSintético\n%%EOF":"invalido"));return DiagnosticoPacoteTest.zip(a);}
    ResponseEntity<JsonNode> upload(String rota,byte[] bytes,Object config){
        var partes=new LinkedMultiValueMap<String,Object>();partes.add("arquivo",new ByteArrayResource(bytes){@Override public String getFilename(){return "documentos.zip";}});
        if(config!=null){var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);partes.add("configuracao",new HttpEntity<>(config,h));}return http.postForEntity(rota,partes,JsonNode.class);
    }
    JsonNode get(String rota){var r=http.getForEntity(rota,JsonNode.class);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();}
    ResponseEntity<JsonNode> post(String rota,Object corpo){return http.postForEntity(rota,corpo,JsonNode.class);}
    JsonNode central(String filtro){return get("/api/pendencias?filtro="+filtro);}
    JsonNode diagnostico(String tipo,String id){return get("/api/diagnosticos/"+tipo+"/"+id);}
    JsonNode importar(byte[] bytes)throws Exception{
        var i=upload("/api/pacotes/inspecao",bytes,null);assertThat(i.getStatusCode()).isEqualTo(HttpStatus.OK);var inspecao=i.getBody();
        var r=upload("/api/pacotes/confirmacao?anteriorOperacaoId="+inspecao.get("operacaoId").asText(),bytes,Map.of("sha256",inspecao.get("sha256").asText(),"vinculos",List.of(Map.of("arquivoId","a1","finalidade","FATURA","xmlIds",List.of("a0")))));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();
    }
    int total(String tabela){return jdbc.queryForObject("SELECT COUNT(*) FROM "+tabela,Integer.class);}
    void semPublicacao(){assertThat(total("documentos")).isZero();assertThat(total("viagens")).isZero();assertThat(total("anexos")).isZero();}
    @Test void contratoPorArquivoCorrelacionaRequisicaoSemInferirSenhaOuExporConteudo()throws Exception{
        var arquivos=DiagnosticoPacoteTest.arquivos();arquivos.put("cobrancas/fatura.pdf",DiagnosticoPacoteTest.texto("nao-pdf"));arquivos.put("outros/fatura.pdf",DiagnosticoPacoteTest.texto("%PDF-sintetico"));arquivos.put("ctes/quebrado.xml",DiagnosticoPacoteTest.texto("<!DOCTYPE CTe SYSTEM 'file:///SEGREDO_FISCAL'><CTe>"));
        var r=upload("/api/pacotes/inspecao",DiagnosticoPacoteTest.zip(arquivos),null);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var d=r.getBody().get("diagnosticoOperacao");assertThat(d.get("correlacaoId").asText()).isEqualTo(r.getHeaders().getFirst("X-Request-Id"));
        assertThat(d.get("gravidade").asText()).isEqualTo("ERRO");assertThat(d.get("dataHora").asText()).isNotBlank();
        assertThat(d.toString()).contains("cobrancas/fatura.pdf","outros/fatura.pdf","quebrado.xml","VALIDACAO_XML","VALIDACAO_PDF");
        assertThat(d.toString()).doesNotContain("SEGREDO_FISCAL","SAXParseException",ACERVO.toString(),"arquivo protegido por senha");
        assertThat(d.get("impacto").asText()).contains("Nenhum documento");semPublicacao();
    }
    @Test void repeticaoTecnicaNaoDuplicaPendenciasEMantemHistoricosOriginais()throws Exception{
        byte[] bytes=zip(false);var primeira=upload("/api/pacotes/inspecao",bytes,null).getBody().get("diagnostico");String id=primeira.get("operacaoId").asText();
        String original=jdbc.queryForObject("SELECT diagnostico FROM processamentos WHERE id=?",String.class,id);
        upload("/api/pacotes/inspecao",bytes,null);assertThat(total("processamentos")).isEqualTo(2);assertThat(central("PRECISA_ACAO").get("total").asInt()).isEqualTo(1);
        get("/api/pendencias/resumo");central("PRECISA_ACAO");assertThat(total("processamentos")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT diagnostico FROM processamentos WHERE id=?",String.class,id)).isEqualTo(original);
    }
    @Test void conferenciaCorrigidaResolveErroMasAssociacaoSoResolveComImportacao()throws Exception{
        var anterior=upload("/api/pacotes/inspecao",zip(false),null).getBody().get("diagnostico");String id=anterior.get("operacaoId").asText();
        // Conferência e confirmação precisam dos mesmos bytes, inclusive timestamp ZIP.
        byte[] corrigido=zip(true);
        var corrigida=upload("/api/pacotes/inspecao?anteriorOperacaoId="+id,corrigido,null).getBody();
        var antigo=diagnostico("PROCESSAMENTO",id);assertThat(antigo.get("estado").asText()).isEqualTo("RESOLVIDO");assertThat(antigo.get("resolucao").get("referencia").asText()).isEqualTo(corrigida.get("operacaoId").asText());
        assertThat(antigo.toString()).contains("PDF_NAO_RECONHECIDO");assertThat(central("PRECISA_ACAO").get("total").asInt()).isEqualTo(1);
        String novoId=corrigida.get("operacaoId").asText();
        var r=upload("/api/pacotes/confirmacao?anteriorOperacaoId="+novoId,corrigido,Map.of("sha256",corrigida.get("sha256").asText(),"vinculos",List.of(Map.of("arquivoId","a1","finalidade","FATURA","xmlIds",List.of("a0")))));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(diagnostico("PROCESSAMENTO",novoId).get("estado").asText()).isEqualTo("RESOLVIDO");assertThat(central("PRECISA_ACAO").get("total").asInt()).isZero();
    }
    @Test void novaOperacaoSemVinculoNaoEncerraPendenciaAnterior()throws Exception{
        String id=upload("/api/pacotes/inspecao",zip(false),null).getBody().get("diagnostico").get("operacaoId").asText();
        upload("/api/pacotes/inspecao",DiagnosticoPacoteTest.zip(DiagnosticoPacoteTest.arquivos()),null);
        assertThat(diagnostico("PROCESSAMENTO",id).get("estado").asText()).isEqualTo("PRECISA_ACAO");
    }
    @Test void segurancaInterrompeSemValidarNaoExaminadosESemImportacaoParcial()throws Exception{
        var a=DiagnosticoPacoteTest.arquivos();a.put("../inseguro.pdf",DiagnosticoPacoteTest.texto("%PDF-falso"));a.put("depois.pdf",DiagnosticoPacoteTest.texto("%PDF-falso"));
        byte[] bytes=DiagnosticoPacoteTest.zip(a);var r=upload("/api/pacotes/inspecao",bytes,null);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var d=r.getBody().get("diagnostico");assertThat(d.get("interrompida").asBoolean()).isTrue();
        assertThat(r.getBody().get("diagnosticoOperacao").get("impacto").asText()).contains("não examinadas não são válidas");
        var confirmacao=upload("/api/pacotes/confirmacao",bytes,Map.of("sha256",ConteudoXml.sha256(bytes),"vinculos",List.of()));assertThat(confirmacao.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);semPublicacao();
    }
    @Test void exportacaoPendentePreservaImportacaoHistoricoDaFalhaERetomadaIdempotente()throws Exception{
        long pacoteId=importar(zip(true)).get("pacoteId").asLong();Files.delete(SAIDA);
        var e=post("/api/pacotes/"+pacoteId+"/exportacoes",Map.of("confirmado",true)).getBody();String id=e.get("id").asText();
        var falha=diagnostico("EXPORTACAO",id);assertThat(falha.get("impacto").asText()).contains("Não é necessário importar novamente");assertThat(falha.toString()).contains("PASTA_INEXISTENTE");
        assertThat(total("documentos")).isEqualTo(1);assertThat(central("PRECISA_ACAO").get("total").asInt()).isEqualTo(1);
        Files.createDirectories(SAIDA);String chave=UUID.randomUUID().toString();var corpo=Map.of("confirmado",true,"requisicaoId",chave);
        try(var executor=Executors.newFixedThreadPool(2)){var a=executor.submit(()->post("/api/exportacoes/"+id+"/retomar",corpo));var b=executor.submit(()->post("/api/exportacoes/"+id+"/retomar",corpo));assertThat(a.get().getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(b.get().getStatusCode()).isEqualTo(HttpStatus.OK);}
        var resolvida=diagnostico("EXPORTACAO",id);assertThat(resolvida.get("estado").asText()).isEqualTo("RESOLVIDO");assertThat(resolvida.get("recuperacoes").size()).isEqualTo(2);assertThat(resolvida.toString()).contains("PASTA_INEXISTENTE");
        assertThat(central("PRECISA_ACAO").get("total").asInt()).isZero();assertThat(total("documentos")).isEqualTo(1);assertThat(total("tentativas_email")).isZero();
    }
    @Test void origemAusenteEHashDivergenteSaoIdentificadosSemCaminhoPrivado()throws Exception{
        long pacoteId=importar(zip(true)).get("pacoteId").asLong();String fonte=jdbc.queryForObject("SELECT caminho FROM documentos",String.class);Path origem=LeituraSeguraArquivos.resolver(ACERVO,fonte);byte[] original=Files.readAllBytes(origem);Files.delete(origem);
        String id=post("/api/pacotes/"+pacoteId+"/exportacoes",Map.of("confirmado",true)).getBody().get("id").asText();assertThat(diagnostico("EXPORTACAO",id).toString()).contains("ORIGEM_AUSENTE").doesNotContain(origem.toString());
        Files.writeString(origem,"Alteração sintética");post("/api/exportacoes/"+id+"/retomar",Map.of("confirmado",true));assertThat(diagnostico("EXPORTACAO",id).toString()).contains("HASH_DIVERGENTE");
        Files.write(origem,original);post("/api/exportacoes/"+id+"/retomar",Map.of("confirmado",true));assertThat(diagnostico("EXPORTACAO",id).get("estado").asText()).isEqualTo("RESOLVIDO");
    }
    @Test void falhaInesperadaTemReferenciaSemMensagemPrivadaOuArquivoInventado(){
        var r=http.getForEntity("/api/fixture/falha",JsonNode.class);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);var d=r.getBody().get("diagnosticoOperacao");
        assertThat(d.get("correlacaoId").asText()).isEqualTo(r.getHeaders().getFirst("X-Request-Id"));assertThat(d.get("pacote").isNull()).isTrue();assertThat(d.get("problemas")).isEmpty();
        assertThat(d.get("causaConhecida").asText()).isEqualTo(DiagnosticoOperacaoService.CAUSA_DESCONHECIDA);assertThat(r.getBody().toString()).doesNotContain("SENHA_PRIVADA","jdbc:","SELECT segredo","stackTrace");
    }
    void processo(String id,String situacao,String hash,String data){
        jdbc.update("INSERT INTO processamentos(id,pacote,sha256,fase,situacao,ator,criado_em,diagnostico) VALUES(?,'fixture.zip',?,'CONFIRMACAO',?,'SINTETICO',"+data+",?)",id,hash,situacao,"{\"pacote\":\"fixture.zip\",\"diagnosticos\":[],\"temporario\":\"REMOVIDO\"}");
    }
    @Test void centralPaginaNoBackendDiferenciaAndamentoIncertezaEResolvidos(){
        for(int n=0;n<25;n++)processo(UUID.randomUUID().toString(),"IMPORTACAO_REVERTIDA",String.format("%064x",n),"CURRENT_TIMESTAMP");
        var pagina=get("/api/pendencias?filtro=PRECISA_ACAO&tamanho=5&pagina=1");assertThat(pagina.get("total").asInt()).isEqualTo(25);assertThat(pagina.get("itens").size()).isEqualTo(5);
        processo(UUID.randomUUID().toString(),"EM_PROCESSAMENTO","a".repeat(64),"CURRENT_TIMESTAMP");processo(UUID.randomUUID().toString(),"EM_PROCESSAMENTO","b".repeat(64),"DATEADD('MINUTE',-10,CURRENT_TIMESTAMP)");
        assertThat(central("EM_PROCESSAMENTO").get("total").asInt()).isEqualTo(1);assertThat(central("PRECISA_ACAO").get("total").asInt()).isEqualTo(26);
        assertThat(http.getForEntity("/api/pendencias?tamanho=500",JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test void resultadoDeEnvioDesconhecidoPermaneceAbertoSemAcionarTransporte()throws Exception{
        long trip=importar(zip(true)).get("documentos").get(0).get("viagemId").asLong();String conferencia=UUID.randomUUID().toString(),id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO conferencias_email(id,viagem_id,selecao,snapshot,criada_em,expira_em) VALUES(?,?,'{}','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",conferencia,trip);
        jdbc.update("INSERT INTO tentativas_email(id,conferencia_id,viagem_id,snapshot,modo,situacao,motivo,criada_em) VALUES(?,?,?,'{\"destinatario\":\"PRIVADO@example.invalid\"}','SIMULADO','DESCONHECIDO','Fixture',CURRENT_TIMESTAMP)",id,conferencia,trip);
        var d=diagnostico("ENVIO",id);assertThat(d.get("gravidade").asText()).isEqualTo("ATENCAO");assertThat(d.get("mensagem").asText()).contains("Consulte a tentativa");assertThat(d.toString()).doesNotContain("PRIVADO@example.invalid");
        central("PRECISA_ACAO");assertThat(total("tentativas_email")).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT situacao FROM tentativas_email",String.class)).isEqualTo("DESCONHECIDO");
    }
    @Test void migraV9ParaV10SemReescreverDiagnosticoAnterior()throws Exception{
        String url="jdbc:h2:mem:preservacao-v9-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        var m=new br.com.rotabrasil.migracao.V3__ConferirEPreencherIdentidade(new br.com.rotabrasil.manutencao.InventarioBanco(ACERVO),ACERVO,null);
        var f=org.flywaydb.core.Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").javaMigrations(m).target("9").load();f.migrate();
        try(var c=java.sql.DriverManager.getConnection(url,"sa","");var p=c.prepareStatement("INSERT INTO processamentos(id,pacote,fase,situacao,ator,criado_em,diagnostico) VALUES('legado','fixture.zip','CONFERENCIA','INTERROMPIDA','SINTETICO',CURRENT_TIMESTAMP,?)")){
            p.setString(1,"{\"diagnosticoLegado\":\"preservar sem alteração\"}");p.executeUpdate();
            org.flywaydb.core.Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").javaMigrations(m).load().migrate();
            try(var r=c.createStatement().executeQuery("SELECT diagnostico,correlacao,resolvido_em FROM processamentos WHERE id='legado'")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("{\"diagnosticoLegado\":\"preservar sem alteração\"}");assertThat(r.getString(2)).isNull();assertThat(r.getObject(3)).isNull();}
        }
    }
    @Test void falhaDeNotificacaoTemVermelhoConsistenteComContadorSemNovoDisparo()throws Exception{
        upload("/api/pacotes/inspecao",zip(true),null);String id=jdbc.queryForList("SELECT id FROM notificacoes_recebimento ORDER BY criada_em,id",String.class).getFirst();
        jdbc.update("UPDATE notificacoes_recebimento SET situacao='FALHA' WHERE id=?",id);
        assertThat(diagnostico("NOTIFICACAO",id).get("gravidade").asText()).isEqualTo("ERRO");
        assertThat(get("/api/pendencias/resumo").get("falhas").asLong()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT situacao FROM notificacoes_recebimento WHERE id=?",String.class,id)).isEqualTo("FALHA");
    }
}
