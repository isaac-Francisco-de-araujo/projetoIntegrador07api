package br.com.rotabrasil;

import br.com.rotabrasil.service.*;
import br.com.rotabrasil.service.email.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** Gmail real somente com double de transporte, credenciais e diretórios sintéticos. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:conta-teste-v15;DB_CLOSE_DELAY=-1","spring.flyway.enabled=true","rotabrasil.seguranca.ativa=true","rotabrasil.email.modo=SIMULADO","rotabrasil.teste-email.modo=REAL","rotabrasil.teste-email.gmail.ativado=true","rotabrasil.teste-email.gmail.usuario=conta-sintetica@example.invalid","rotabrasil.teste-email.gmail.senha-aplicativo=abcdefghijklmnop"})
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ContaTesteEmailHttpTest {
    static final Path TMP=temporario();static Path temporario(){try{return Files.createTempDirectory("conta-teste-v15-");}catch(Exception e){throw new IllegalStateException(e);}}
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("rotabrasil.arquivos.viagens",()->TMP.resolve("acervo").toString());r.add("rotabrasil.teste-email.gmail.token-arquivo",()->TMP.resolve("token.json").toString());r.add("rotabrasil.seguranca.admin-usuario",()->"admin");r.add("rotabrasil.seguranca.admin-hash",()->new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("SenhaSomenteFixture12!"));}
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired ConfiguracaoNotificacoes microsoft;@Autowired CoordenacaoImportacaoService coordenacao;
    @SpyBean ContaTesteEmailService conta;
    @BeforeEach void preparar(){
        reset(conta);jdbc.update("DELETE FROM usuarios");
        for(String nome:List.of("admin","operador","leitor"))jdbc.update("INSERT INTO usuarios(usuario,senha_hash,papel,criado_em) VALUES(?,?,?,CURRENT_TIMESTAMP)",nome,"hash-sintetico",nome.equals("admin")?"ADMIN":nome.equals("operador")?"OPERADOR":"CONSULTA");
        jdbc.update("UPDATE conta_teste_email SET versao=0,provedor='GMAIL',remetente='conta-sintetica@example.invalid',autenticacao='APP_PASSWORD' WHERE id=1");
        jdbc.update("DELETE FROM testes_configuracao");jdbc.update("DELETE FROM conferencias_notificacao");
        doReturn(TransporteEmail.Resultado.ACEITO).when(conta).enviar(any());
    }
    @AfterAll static void limpar()throws Exception{FileSystemUtils.deleteRecursively(TMP);}
    JsonNode getConta()throws Exception{return json.readTree(mvc.perform(get("/api/configuracoes/conta-teste").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    JsonNode conferir()throws Exception{return json.readTree(mvc.perform(post("/api/configuracoes/conferencias-email-real").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"destinatario\":\"destino@example.invalid\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    String corpo(JsonNode c){return "{\"conferenciaId\":\""+c.path("id").asText()+"\",\"confirmado\":true}";}
    JsonNode confirmar(JsonNode c)throws Exception{return json.readTree(mvc.perform(post("/api/configuracoes/confirmacoes-email-real").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(corpo(c))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    @Test void contaAdminExigePapelCsrfValidacaoEVersaoSemSegredos()throws Exception{
        mvc.perform(get("/api/configuracoes/conta-teste")).andExpect(status().isUnauthorized());
        for(String papel:List.of("OPERADOR","CONSULTA"))mvc.perform(get("/api/configuracoes/conta-teste").with(user(papel.equals("OPERADOR")?"operador":"leitor").roles(papel))).andExpect(status().isForbidden());
        String c="{\"versao\":0,\"provedor\":\"GMAIL\",\"remetente\":\"outra@example.invalid\",\"autenticacao\":\"APP_PASSWORD\"}";
        mvc.perform(put("/api/configuracoes/conta-teste").with(user("admin").roles("ADMIN")).contentType("application/json").content(c)).andExpect(status().isForbidden());
        mvc.perform(put("/api/configuracoes/conta-teste").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(c)).andExpect(status().isOk()).andExpect(jsonPath("$.prontoParaTesteReal").value(false));
        mvc.perform(put("/api/configuracoes/conta-teste").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(c)).andExpect(status().isConflict());
        assertThat(getConta().toString()).doesNotContain("abcdefghijklmnop",TMP.toString(),"senha-aplicativo","access_token");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE ator='admin' AND recurso='/api/configuracoes/conta-teste' AND status_http=200",Integer.class)).isPositive();
        verify(conta,never()).enviar(any());
    }
    @Test void modoPadraoSemEnvioENaoAlteraMicrosoftOuDocumentos()throws Exception{
        var local=new ContaTesteEmailService(jdbc,new org.springframework.mock.env.MockEnvironment(),microsoft,coordenacao).consultar();
        assertThat(local.modo()).isEqualTo("SIMULADO");assertThat(local.prontoParaTesteReal()).isFalse();
        mvc.perform(get("/api/configuracoes").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$.modoEnvioDocumental").value("SIMULADO")).andExpect(jsonPath("$.confirmacaoMotorista.modo").value("SIMULADO"));
        mvc.perform(post("/api/configuracoes/teste-email").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"id\":\""+UUID.randomUUID()+"\",\"destinatario\":\"destino@example.invalid\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("SIMULADO"));
        verify(conta,never()).enviar(any());
    }
    @Test void previaMostraMensagemSemAnexosEExigeConfirmacaoAntesDoDouble()throws Exception{
        var c=conferir();assertThat(c.path("snapshot").path("remetente").asText()).isEqualTo("conta-sintetica@example.invalid");
        assertThat(c.path("snapshot").path("mensagem").asText()).contains("Mensagem sintética","Sem documentos fiscais");assertThat(c.path("snapshot").has("arquivos")).isFalse();verify(conta,never()).enviar(any());
        mvc.perform(post("/api/configuracoes/confirmacoes-email-real").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(corpo(c).replace("true","false"))).andExpect(status().isBadRequest());
        var t=confirmar(c);assertThat(t.path("situacao").asText()).isEqualTo("ACEITO_SMTP");assertThat(t.path("mensagem").asText()).contains("não comprova entrega ou leitura");assertThat(confirmar(c)).isEqualTo(t);verify(conta,times(1)).enviar(any());
    }
    @Test void configuracaoAlteradaInvalidaConferenciaSemTransportar()throws Exception{
        var c=conferir();jdbc.update("UPDATE conta_teste_email SET versao=versao+1 WHERE id=1");
        mvc.perform(post("/api/configuracoes/confirmacoes-email-real").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(corpo(c))).andExpect(status().isConflict());verify(conta,never()).enviar(any());
    }
    @Test void oauthExpiradoBloqueiaSemFallbackParaSenha()throws Exception{
        jdbc.update("UPDATE conta_teste_email SET autenticacao='XOAUTH2' WHERE id=1");
        Files.writeString(TMP.resolve("token.json"),"{\"access_token\":\"TOKEN_SINTETICO\",\"expires_at\":\"2000-01-01T00:00:00Z\"}");
        assertThat(getConta().path("prontoParaTesteReal").asBoolean()).isFalse();
        mvc.perform(post("/api/configuracoes/conferencias-email-real").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"destinatario\":\"destino@example.invalid\"}")).andExpect(status().isConflict());
        Files.writeString(TMP.resolve("token.json"),"{\"access_token\":\"TOKEN_SINTETICO\",\"expires_at\":\""+Instant.now().plusSeconds(3600)+"\"}");assertThat(getConta().path("prontoParaTesteReal").asBoolean()).isTrue();verify(conta,never()).enviar(any());
    }
    @Test void recusaEExcecaoConservamHistoricoSemRepeticao()throws Exception{
        doReturn(TransporteEmail.Resultado.RECUSADO).when(conta).enviar(any());assertThat(confirmar(conferir()).path("situacao").asText()).isEqualTo("FALHA");
        doThrow(new IllegalStateException("NAO_EXIBIR_SEGREDO_SINTETICO")).when(conta).enviar(any());var c=conferir();var t=confirmar(c);assertThat(t.path("situacao").asText()).isEqualTo("DESCONHECIDO");assertThat(t.toString()).doesNotContain("NAO_EXIBIR_SEGREDO");assertThat(confirmar(c)).isEqualTo(t);verify(conta,times(2)).enviar(any());
        mvc.perform(get("/api/configuracoes/conta-teste/ultima-verificacao").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("DESCONHECIDO"));
    }
    @Test void ultimaVerificacaoNaoAtribuiResultadoDeOutraConta()throws Exception{
        mvc.perform(get("/api/configuracoes/conta-teste/ultima-verificacao").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("NAO_VERIFICADO"));
        confirmar(conferir());
        mvc.perform(get("/api/configuracoes/conta-teste/ultima-verificacao").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("ACEITO_SMTP"));
        jdbc.update("UPDATE conta_teste_email SET versao=versao+1,remetente='outra@example.invalid' WHERE id=1");
        mvc.perform(get("/api/configuracoes/conta-teste/ultima-verificacao").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("NAO_VERIFICADO"));
    }
    @Test void ultimaVerificacaoMicrosoftPreservaCanalExistente()throws Exception{
        jdbc.update("UPDATE conta_teste_email SET provedor='MICROSOFT_EXISTENTE',autenticacao='XOAUTH2' WHERE id=1");
        jdbc.update("INSERT INTO testes_configuracao VALUES(?,'EMAIL_REAL',?,'FALHA','Recusa sintética',CURRENT_TIMESTAMP)",UUID.randomUUID().toString(),json.writeValueAsString(Map.of("assinaturaTransporte",microsoft.assinatura())));
        mvc.perform(get("/api/configuracoes/conta-teste/ultima-verificacao").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("FALHA"));
        jdbc.update("UPDATE conta_teste_email SET provedor='GMAIL',autenticacao='APP_PASSWORD' WHERE id=1");
        mvc.perform(get("/api/configuracoes/conta-teste/ultima-verificacao").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("NAO_VERIFICADO"));
        verify(conta,never()).enviar(any());
    }
    @Test void cliqueDuploConcorrenteTransportaUmaVezSemTransacaoAberta()throws Exception{
        var c=conferir();CountDownLatch iniciou=new CountDownLatch(1),liberar=new CountDownLatch(1);
        doAnswer(inv->{assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();iniciou.countDown();assertThat(liberar.await(10,TimeUnit.SECONDS)).isTrue();return TransporteEmail.Resultado.ACEITO;}).when(conta).enviar(any());
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->confirmar(c));assertThat(iniciou.await(10,TimeUnit.SECONDS)).isTrue();var b=pool.submit(()->confirmar(c)).get(10,TimeUnit.SECONDS);assertThat(b.path("situacao").asText()).isEqualTo("DESCONHECIDO");liberar.countDown();assertThat(a.get(10,TimeUnit.SECONDS).path("situacao").asText()).isEqualTo("ACEITO_SMTP");verify(conta,times(1)).enviar(any());
        }finally{liberar.countDown();}
    }
}
