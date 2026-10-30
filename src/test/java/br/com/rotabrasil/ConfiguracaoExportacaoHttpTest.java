package br.com.rotabrasil;

import br.com.rotabrasil.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.datasource.url=jdbc:h2:mem:config-export-v9;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000",
        "spring.flyway.enabled=true","spring.h2.console.enabled=false","rotabrasil.email.modo=SIMULADO"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ConfiguracaoExportacaoHttpTest {
    static final Path TMP=temporario(),ACERVO=TMP.resolve("acervo"),SAIDA=TMP.resolve("exportacoes");
    static Path temporario(){try{return Files.createTempDirectory("rotabrasil-config-export-");}catch(Exception ex){throw new IllegalStateException(ex);}}
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("rotabrasil.arquivos.viagens",ACERVO::toString);r.add("rotabrasil.exportacao.raiz",SAIDA::toString);}
    @Autowired TestRestTemplate http;@Autowired JdbcTemplate jdbc;
    @SpyBean PublicadorExportacao publicador;
    @SpyBean DestinoExportacaoService destinos;
    byte[] pacote;
    @BeforeEach void preparar()throws Exception{
        reset(publicador,destinos);
        for(String t:List.of("exportacao_arquivos","exportacoes","notificacoes_recebimento","recebimentos","testes_configuracao","anexo_viagens","anexos","pacote_documentos","documentos","pacotes","envios","viagens","clientes","processamentos")){
            if(t.equals("processamentos"))jdbc.update("UPDATE processamentos SET anterior_id=NULL");jdbc.update("DELETE FROM "+t);
        }
        jdbc.update("UPDATE configuracao_operacao SET versao=0,remetente=NULL,subpasta='',exportacao_automatica=FALSE WHERE id=1");
        FileSystemUtils.deleteRecursively(ACERVO);FileSystemUtils.deleteRecursively(SAIDA);Files.createDirectories(ACERVO);Files.createDirectories(SAIDA);
        var arquivos=new LinkedHashMap<String,byte[]>();arquivos.put("cte.xml",bytes(CteFixture.xml()));arquivos.put("outro.xml",outroXml());arquivos.put("fatura.pdf",bytes("%PDF-1.4\nFixture sintética sem valor fiscal\n%%EOF"));pacote=zip(arquivos);
    }
    @AfterAll static void limpar()throws Exception{FileSystemUtils.deleteRecursively(TMP);}
    static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    static byte[] outroXml(){String base=CteFixture.CHAVE.substring(0,25)+"000066214"+CteFixture.CHAVE.substring(34,43);int soma=0;for(int i=42,p=2;i>=0;i--,p=p==9?2:p+1)soma+=(base.charAt(i)-'0')*p;int dv=11-soma%11;return bytes(CteFixture.xml().replace(CteFixture.CHAVE,base+(dv>=10?0:dv)).replace("<nCT>66213</nCT>","<nCT>66214</nCT>"));}
    static byte[] zip(Map<String,byte[]> arquivos)throws Exception{var out=new ByteArrayOutputStream();try(var z=new ZipOutputStream(out)){for(var a:arquivos.entrySet()){z.putNextEntry(new ZipEntry(a.getKey()));z.write(a.getValue());z.closeEntry();}}return out.toByteArray();}
    JsonNode get(String rota){var r=http.getForEntity(rota,JsonNode.class);assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();}
    ResponseEntity<JsonNode> json(HttpMethod metodo,String rota,Object corpo){return http.exchange(rota,metodo,new HttpEntity<>(corpo),JsonNode.class);}
    JsonNode salvar(boolean automatico,String pasta){long versao=get("/api/configuracoes").get("versao").asLong();var r=json(HttpMethod.PUT,"/api/configuracoes",Map.of("versao",versao,"remetente","operacao@example.invalid","exportacaoAutomatica",automatico,"subpasta",pasta));assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();}
    ResponseEntity<JsonNode> upload(String rota,byte[] conteudo,Object config){var form=new LinkedMultiValueMap<String,Object>();form.add("arquivo",new ByteArrayResource(conteudo){@Override public String getFilename(){return "fixture.zip";}});if(config!=null){var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);form.add("configuracao",new HttpEntity<>(config,h));}return http.postForEntity(rota,form,JsonNode.class);}
    JsonNode importar(){var i=upload("/api/pacotes/inspecao",pacote,null);assertThat(i.getStatusCode()).isEqualTo(HttpStatus.OK);var r=confirmar(i.getBody());assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();}
    ResponseEntity<JsonNode> confirmar(JsonNode i){return upload("/api/pacotes/confirmacao",pacote,Map.of("sha256",i.get("sha256").asText(),"vinculos",List.of(Map.of("arquivoId","a2","finalidade","FATURA","xmlIds",List.of("a0","a1")))));}
    JsonNode exportar(long id){var r=json(HttpMethod.POST,"/api/pacotes/"+id+"/exportacoes",Map.of("confirmado",true));assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();}
    JsonNode retomar(JsonNode e){var r=json(HttpMethod.POST,"/api/exportacoes/"+e.get("id").asText()+"/retomar",Map.of("confirmado",true));assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);return r.getBody();}
    int total(String tabela){return jdbc.queryForObject("SELECT COUNT(*) FROM "+tabela,Integer.class);}

    @Test void salvaConsultaRecusaEmailInvalidoSenhaECaminhoArbitrario(){
        assertThat(salvar(false,"").get("remetente").asText()).isEqualTo("operacao@example.invalid");
        assertThat(get("/api/configuracoes").toString()).doesNotContain("senha",SAIDA.toString(),ACERVO.toString());
        for(String email:List.of("invalido","a@example.invalid\r\nBcc:x@example.invalid"))assertThat(json(HttpMethod.PUT,"/api/configuracoes",Map.of("versao",1,"remetente",email,"subpasta","","exportacaoAutomatica",false)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        for(String pasta:List.of("../fora","C:\\fora","CON","pasta/outra"))assertThat(json(HttpMethod.PUT,"/api/configuracoes",Map.of("versao",1,"remetente","a@example.invalid","subpasta",pasta,"exportacaoAutomatica",false)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json(HttpMethod.PUT,"/api/configuracoes",Map.of("versao",1,"senha","segredo-nao-aceito","subpasta","","exportacaoAutomatica",false)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json(HttpMethod.PUT,"/api/configuracoes",Map.of("versao",0,"subpasta","","exportacaoAutomatica",false)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    @Test void testeSimuladoPersisteResultadoIdempotenteESnapshotSemAlterarCliente(){
        salvar(false,"");String id=UUID.randomUUID().toString();var pedido=Map.of("id",id,"destinatario","teste@example.invalid");
        assertThat(json(HttpMethod.POST,"/api/configuracoes/teste-email",pedido).getBody().get("situacao").asText()).isEqualTo("SIMULADO");
        json(HttpMethod.POST,"/api/configuracoes/teste-email",pedido);assertThat(total("testes_configuracao")).isEqualTo(1);
        assertThat(get("/api/configuracoes/testes").get(0).get("mensagem").asText()).contains("Nenhuma conexão SMTP");
        assertThat(total("envios")).isZero();
    }
    @Test void testePastaValidaESemPermissaoTemResultadoPersistente(){
        assertThat(json(HttpMethod.POST,"/api/configuracoes/teste-pasta",null).getBody().get("situacao").asText()).isEqualTo("GRAVAVEL");
        doReturn("SEM_PERMISSAO").when(destinos).testar("");
        assertThat(json(HttpMethod.POST,"/api/configuracoes/teste-pasta",null).getBody().get("situacao").asText()).isEqualTo("SEM_PERMISSAO");assertThat(total("testes_configuracao")).isEqualTo(2);
    }
    @Test void recebimentoNaoAfirmaImportacaoENaoDuplicaAvisosNemTrocaDestinatarioDoCliente(){
        salvar(false,"");String rota="/api/pacotes/inspecao?destinatarioConfirmacao=origem@example.invalid&simularConfirmacao=true";
        var r=upload(rota,pacote,null);String id=r.getHeaders().getFirst("X-Recebimento-Id");assertThat(id).isNotBlank();
        upload(rota,pacote,null);var recebimento=get("/api/recebimentos/"+id);
        assertThat(total("recebimentos")).isEqualTo(1);
        var recebido=recebimento.get("notificacoes").findValues("mensagem").stream().map(JsonNode::asText).filter(s->s.contains("Arquivo recebido pelo upload manual")).findFirst().orElseThrow();
        assertThat(recebido).contains("não confirma leitura, conferência ou importação");assertThat(recebimento.get("destinatario").asText()).isEqualTo("origem@example.invalid");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notificacoes_recebimento WHERE evento='ARQUIVO_RECEBIDO'",Integer.class)).isEqualTo(1);
        assertThat(recebimento.toString()).doesNotContain(ACERVO.toString(),"\"conteudo\":","\"anexos\":");
        assertThat(confirmar(r.getBody()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT email FROM clientes",String.class)).isNull();
        assertThat(upload("/api/pacotes/inspecao?destinatarioConfirmacao=outro@example.invalid",pacote,null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    @Test void pdfInvalidoEXmlRecusadoRegistramPendenciasSemImportacao()throws Exception{
        salvar(false,"");var invalidos=zip(Map.of("cte.xml",bytes("<invalido/>"),"invalido.pdf",bytes("nao-pdf")));
        var r=upload("/api/pacotes/inspecao?destinatarioConfirmacao=origem@example.invalid&simularConfirmacao=true",invalidos,null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);var avisos=get("/api/recebimentos/"+r.getHeaders().getFirst("X-Recebimento-Id"));
        assertThat(avisos.toString()).contains("ARQUIVO_RECEBIDO","PROCESSAMENTO_COM_PENDENCIAS","não se confirma importação integral");assertThat(total("documentos")).isZero();
    }
    @Test void copiaManualPreservaBytesShaFaturaCompartilhadaERepeticaoIdempotente()throws Exception{
        var pacote=importar();long id=pacote.get("pacoteId").asLong();assertThat(total("exportacoes")).isZero();
        var e=exportar(id);assertThat(e.get("situacao").asText()).isEqualTo("CONCLUIDA");assertThat(e.get("arquivos").size()).isEqualTo(4);
        for(JsonNode a:e.get("arquivos")){byte[] bytes=Files.readAllBytes(SAIDA.resolve(a.get("localizacao").asText()));assertThat(ConteudoXml.sha256(bytes)).isEqualTo(a.get("sha256").asText());}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exportacao_arquivos WHERE origem='ANEXO'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT viagens FROM exportacao_arquivos WHERE origem='ANEXO'",String.class)).contains("66213","66214");
        assertThat(exportar(id).get("id")).isEqualTo(e.get("id"));assertThat(total("exportacoes")).isEqualTo(1);assertThat(total("documentos")).isEqualTo(2);
        assertThat(e.toString()).doesNotContain("caminho_fonte",ACERVO.toString(),SAIDA.toString());
    }
    @Test void conflitoMesmoComBytesIguaisNaoSobrescreveEOutroDestinoExigeAcaoExplicita()throws Exception{
        long id=importar().get("pacoteId").asLong();var doc=jdbc.queryForList("SELECT d.id,d.caminho,v.numero FROM documentos d JOIN viagens v ON v.id=d.viagem_id ORDER BY d.id").getFirst();
        Path alvo=SAIDA.resolve("pacote-"+id+"/viagens/"+doc.get("NUMERO")+"/documento-"+doc.get("ID")+".xml");Files.createDirectories(alvo.getParent());byte[] original=Files.readAllBytes(Path.of((String)doc.get("CAMINHO")));Files.write(alvo,original);
        var e=exportar(id);assertThat(e.toString()).contains("ARQUIVO_EXISTENTE");assertThat(e.get("situacao").asText()).isEqualTo("PARCIAL");assertThat(Files.readAllBytes(alvo)).isEqualTo(original);
        Files.createDirectory(SAIDA.resolve("outro"));salvar(false,"outro");assertThat(total("exportacoes")).isEqualTo(1);
        var novo=exportar(id);assertThat(novo.get("id")).isNotEqualTo(e.get("id"));assertThat(novo.get("situacao").asText()).isEqualTo("CONCLUIDA");assertThat(get("/api/exportacoes/"+e.get("id").asText()).get("subpasta").asText()).isEmpty();
    }
    @Test void falhaParcialERetomadaMantemArquivosAnterioresSemReimportar()throws Exception{
        long id=importar().get("pacoteId").asLong();
        doAnswer(inv->{Path alvo=inv.getArgument(0);if(alvo.toString().endsWith(".pdf"))throw new AccessDeniedException("fixture");return inv.callRealMethod();}).when(publicador).publicar(any(),any(),any());
        var e=exportar(id);assertThat(e.get("situacao").asText()).isEqualTo("PARCIAL");assertThat(e.toString()).contains("SEM_PERMISSAO");
        Path xml=SAIDA.resolve(e.get("arquivos").findValues("localizacao").stream().map(JsonNode::asText).filter(s->s.endsWith(".xml")).findFirst().orElseThrow());var tempo=Files.getLastModifiedTime(xml);
        reset(publicador);assertThat(retomar(e).get("situacao").asText()).isEqualTo("CONCLUIDA");assertThat(Files.getLastModifiedTime(xml)).isEqualTo(tempo);assertThat(total("documentos")).isEqualTo(2);assertThat(total("envios")).isZero();
    }
    @Test void publicacaoAntesDeFalhaReconhecePropriosBytesPorTestemunhaSemDuplicar()throws Exception{
        long id=importar().get("pacoteId").asLong();
        doAnswer(inv->{Object result=inv.callRealMethod();throw new IOException("Falha sintética após publicação");}).when(publicador).publicar(any(),any(),any());
        var e=exportar(id);assertThat(e.get("situacao").asText()).isNotEqualTo("CONCLUIDA");
        reset(publicador);assertThat(retomar(e).get("situacao").asText()).isEqualTo("CONCLUIDA");assertThat(total("exportacoes")).isEqualTo(1);
    }
    @Test void origemAusenteOuHashDivergenteNaoModificaAcervoNemConcluiExportacao()throws Exception{
        long id=importar().get("pacoteId").asLong();Path origem=Path.of(jdbc.queryForList("SELECT caminho FROM documentos ORDER BY id",String.class).getFirst());byte[] original=Files.readAllBytes(origem);
        Files.writeString(origem,"Alteração sintética");var e=exportar(id);assertThat(e.toString()).contains("HASH_DIVERGENTE");assertThat(e.get("situacao").asText()).isNotEqualTo("CONCLUIDA");
        Files.delete(origem);assertThat(retomar(e).toString()).contains("ORIGEM_AUSENTE");Files.write(origem,original);assertThat(retomar(e).get("situacao").asText()).isEqualTo("CONCLUIDA");
    }
    @Test void importacaoPreservadaSeDestinoAutomaticoIndisponivelENaoRepetePorReprocessamento()throws Exception{
        salvar(true,"");Files.delete(SAIDA);var r=importar();assertThat(total("documentos")).isEqualTo(2);
        var e=get("/api/pacotes/"+r.get("pacoteId").asLong()+"/exportacoes").get(0);assertThat(e.get("situacao").asText()).isEqualTo("PENDENTE");assertThat(e.toString()).contains("PASTA_INEXISTENTE","Importado; exportação pendente");
        Files.createDirectories(SAIDA);importar();assertThat(get("/api/exportacoes/"+e.get("id").asText()).get("situacao").asText()).isEqualTo("PENDENTE");
        assertThat(retomar(e).get("situacao").asText()).isEqualTo("CONCLUIDA");assertThat(total("documentos")).isEqualTo(2);
    }
    @Test void semEspacoETratadoComoPendencia()throws Exception{
        long id=importar().get("pacoteId").asLong();doThrow(new FileSystemException("fixture",null,"No space left on device")).when(publicador).publicar(any(),any(),any());
        assertThat(exportar(id).toString()).contains("SEM_ESPACO");assertThat(total("documentos")).isEqualTo(2);
    }
    @Test void exportacoesConcorrentesReutilizamMesmaIdentidade()throws Exception{
        long id=importar().get("pacoteId").asLong();try(var executor=Executors.newFixedThreadPool(2)){
            var a=executor.submit(()->exportar(id));var b=executor.submit(()->exportar(id));assertThat(a.get().get("id")).isEqualTo(b.get().get("id"));assertThat(total("exportacoes")).isEqualTo(1);
        }
    }
    @Test void novaConfiguracaoNaoReescreveSnapshotDeRecebimento(){
        salvar(false,"");var recebido=upload("/api/pacotes/inspecao?destinatarioConfirmacao=origem@example.invalid",pacote,null);String id=recebido.getHeaders().getFirst("X-Recebimento-Id");
        var c=get("/api/configuracoes");json(HttpMethod.PUT,"/api/configuracoes",Map.of("versao",c.get("versao").asLong(),"remetente","novo@example.invalid","subpasta","","exportacaoAutomatica",false));
        var r=get("/api/recebimentos/"+id);assertThat(r.get("remetente").asText()).isEqualTo("operacao@example.invalid");
        var n=r.get("notificacoes").get(0);var endpoint="/api/recebimentos/"+id+"/notificacoes/"+n.get("id").asText()+"/simular";
        assertThat(json(HttpMethod.POST,endpoint,Map.of("confirmado",true)).getBody().get("situacao").asText()).isEqualTo("SIMULADO");
        assertThat(json(HttpMethod.POST,endpoint,Map.of("confirmado",true)).getBody().get("id")).isEqualTo(n.get("id"));
    }
    @Test void destinoForaDaRaizOuSemRaizNaoPermiteAcessoLivreAoDisco()throws Exception{
        var semRaiz=new DestinoExportacaoService("",ACERVO.toString());assertThat(semRaiz.autorizada()).isFalse();assertThat(semRaiz.listar()).isEmpty();assertThat(semRaiz.testar("")).isEqualTo("DESTINO_INDISPONIVEL");
        assertThatThrownBy(()->destinos.resolver("../fora")).isInstanceOf(br.com.rotabrasil.exception.ArquivoInvalidoException.class);
        assertThatThrownBy(()->new DestinoExportacaoService(ACERVO.resolve("saida").toString(),ACERVO.toString())).isInstanceOf(IllegalStateException.class);
        assertThat(json(HttpMethod.POST,"/api/configuracoes/subpastas",Map.of("nome","../fora")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test void permiteDesativarAutomacaoMesmoQuandoSubpastaSalvaFicaIndisponivel()throws Exception{
        Files.createDirectory(SAIDA.resolve("operacao"));salvar(true,"operacao");Files.delete(SAIDA.resolve("operacao"));
        assertThat(salvar(false,"operacao").get("exportacaoAutomatica").asBoolean()).isFalse();assertThat(total("exportacoes")).isZero();
    }
    @Test void retomadaNaoCopiaOrigemArquivadaDepoisDoPlano()throws Exception{
        long id=importar().get("pacoteId").asLong();Files.delete(SAIDA);var e=exportar(id);Files.createDirectories(SAIDA);
        jdbc.update("UPDATE documentos SET arquivado_em=CURRENT_TIMESTAMP");
        var retomada=retomar(e);assertThat(retomada.toString()).contains("ORIGEM_INELEGIVEL");assertThat(retomada.get("situacao").asText()).isNotEqualTo("CONCLUIDA");
        try(var paths=Files.walk(SAIDA)){assertThat(paths.filter(p->p.toString().endsWith(".xml")).toList()).isEmpty();}
    }
    @Test void notificacaoDesconhecidaNaoRepeteTransporteNemApagaIncerteza(){
        salvar(false,"");var resposta=upload("/api/pacotes/inspecao?destinatarioConfirmacao=origem@example.invalid",pacote,null);String id=resposta.getHeaders().getFirst("X-Recebimento-Id");
        var n=get("/api/recebimentos/"+id).get("notificacoes").get(0);String aviso=n.get("id").asText();jdbc.update("UPDATE notificacoes_recebimento SET situacao='DESCONHECIDO' WHERE id=?",aviso);
        var r=json(HttpMethod.POST,"/api/recebimentos/"+id+"/notificacoes/"+aviso+"/simular",Map.of("confirmado",true));assertThat(r.getBody().get("situacao").asText()).isEqualTo("DESCONHECIDO");
    }
    @Test void inventarioIncluiConfiguracaoNotificacoesEExportacoesNoFingerprint()throws Exception{
        salvar(false,"");long id=importar().get("pacoteId").asLong();exportar(id);
        try(var c=jdbc.getDataSource().getConnection()){
            var inventario=new br.com.rotabrasil.manutencao.InventarioBanco(ACERVO).analisar(c);
            assertThat(inventario.totais()).containsKeys("configuracao_operacao","testes_configuracao","recebimentos","notificacoes_recebimento","exportacoes","exportacao_arquivos");
            assertThat(inventario.totais().get("exportacoes")).isEqualTo(1L);
            String anterior=inventario.impressao();salvar(false,"");assertThat(new br.com.rotabrasil.manutencao.InventarioBanco(ACERVO).analisar(c).impressao()).isNotEqualTo(anterior);
        }
    }
}
