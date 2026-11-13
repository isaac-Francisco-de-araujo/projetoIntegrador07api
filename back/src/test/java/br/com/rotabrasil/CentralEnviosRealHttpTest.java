package br.com.rotabrasil;

import br.com.rotabrasil.service.email.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;
import java.nio.file.*;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Transporte REAL substituído por double: nenhuma conexão externa é possível neste contexto. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.address=127.0.0.1","spring.datasource.url=jdbc:h2:mem:central-real;DB_CLOSE_DELAY=-1","spring.flyway.enabled=true","rotabrasil.seguranca.ativa=true","rotabrasil.email.modo=REAL","rotabrasil.email.smtp.ativado=true","rotabrasil.email.smtp.host=smtp.gmail.com","rotabrasil.email.smtp.usuario=remetente@example.invalid","rotabrasil.email.smtp.remetente=remetente@example.invalid","rotabrasil.notificacoes.modo=SIMULADO","rotabrasil.notificacoes.tipo-conta=NAO_IDENTIFICADA"})
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class CentralEnviosRealHttpTest {
    static final Path TMP=temporario();
    static Path temporario(){try{return Files.createTempDirectory("central-real-sintetica-");}catch(Exception ex){throw new IllegalStateException(ex);}}
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("rotabrasil.arquivos.viagens",TMP::toString);r.add("rotabrasil.email.smtp.senha",()->"SOMENTE_FIXTURE_NUNCA_USAR");r.add("rotabrasil.seguranca.admin-usuario",()->"admin");r.add("rotabrasil.seguranca.admin-hash",()->new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("SenhaSomenteFixture12!"));}
    @MockitoBean TransporteSmtp transporte;
    @MockitoBean(name="rotaSmtpSender") org.springframework.mail.javamail.JavaMailSenderImpl sender;
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @org.springframework.boot.test.web.server.LocalServerPort int porta;
    long viagem,documento;
    @BeforeEach void preparar()throws Exception{
        reset(transporte);
        jdbc.update("DELETE FROM tentativas_email");jdbc.update("DELETE FROM conferencias_email");
        jdbc.update("DELETE FROM usuarios");for(String nome:List.of("admin","operador","leitor"))jdbc.update("INSERT INTO usuarios(usuario,senha_hash,papel,criado_em) VALUES(?,?,?,CURRENT_TIMESTAMP)",nome,"hash-sintetico",nome.equals("leitor")?"CONSULTA":nome.equals("operador")?"OPERADOR":"ADMIN");
        jdbc.update("UPDATE configuracao_operacao SET remetente='remetente@example.invalid' WHERE id=1");
        var r=mvc.perform(multipart("/api/processamento/xml").file(new MockMultipartFile("arquivo","central.xml","application/xml",CteFixture.xml().getBytes(java.nio.charset.StandardCharsets.UTF_8))).with(user("operador").roles("OPERADOR")).with(csrf())).andExpect(status().isOk()).andReturn();
        var d=json.readTree(r.getResponse().getContentAsString());viagem=d.path("viagemId").asLong();documento=d.path("documentoId").asLong();
        when(transporte.enviar(any())).thenAnswer(inv->{assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tentativas_email WHERE situacao='DESCONHECIDO'",Integer.class)).isEqualTo(1);return TransporteEmail.Resultado.ACEITO;});
    }
    @AfterAll static void limpar()throws Exception{FileSystemUtils.deleteRecursively(TMP);}
    String rota(String sufixo){return "/api/viagens/"+viagem+"/"+sufixo;}
    String conferir()throws Exception{return json.readTree(mvc.perform(post(rota("conferencias-email")).with(user("operador").roles("OPERADOR")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("selecao",Map.of("destinatario","cliente@example.invalid","documentoIds",List.of(documento)))))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();}
    org.springframework.test.web.servlet.ResultActions confirmar(String id)throws Exception{return mvc.perform(post(rota("tentativas-email")).with(user("operador").roles("OPERADOR")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("conferenciaId",id,"confirmado",true))));}
    @Test void documentalRealIndependeDeMicrosoftEContaAdministrativa()throws Exception{
        mvc.perform(get("/api/envio-documental").with(user("operador").roles("OPERADOR"))).andExpect(status().isOk()).andExpect(jsonPath("$.confirmacaoRealDisponivel").value(true)).andExpect(jsonPath("$.provedor").value("GMAIL")).andExpect(jsonPath("$.autenticacaoVerificada").value(false));
        String id=conferir();verifyNoInteractions(transporte,sender);
        confirmar(id).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("ACEITO_SMTP")).andExpect(jsonPath("$.motivo").value(org.hamcrest.Matchers.containsString("não comprova entrega")));
        // Recuperação funciona mesmo que a configuração tenha sido alterada após a tentativa.
        jdbc.update("UPDATE configuracao_operacao SET remetente=NULL WHERE id=1");
        confirmar(id).andExpect(status().isOk());verify(transporte,times(1)).enviar(any());verifyNoInteractions(sender);
    }
    @Test void divergenciaBloqueiaSoConfirmacaoENaoCriaTentativa()throws Exception{
        jdbc.update("UPDATE configuracao_operacao SET remetente='outro@example.invalid' WHERE id=1");
        mvc.perform(get("/api/envio-documental").with(user("leitor").roles("CONSULTA"))).andExpect(status().isOk()).andExpect(jsonPath("$.confirmacaoRealDisponivel").value(false)).andExpect(jsonPath("$.bloqueios[0]").value(org.hamcrest.Matchers.containsString("difere")));
        confirmar(conferir()).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tentativas_email",Integer.class)).isZero();verifyNoInteractions(transporte,sender);
    }
    @Test void sessaoPapeisECsrf()throws Exception{
        mvc.perform(get("/api/envio-documental")).andExpect(status().isUnauthorized());
        mvc.perform(post(rota("conferencias-email")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post(rota("conferencias-email")).with(user("leitor").roles("CONSULTA")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(rota("conferencias-email")).with(user("operador").roles("OPERADOR")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/configuracoes").with(user("operador").roles("OPERADOR"))).andExpect(status().isForbidden());verifyNoInteractions(transporte,sender);
    }
    @Test void identificaContratoDaCentralEModuloQueControlaPrevia()throws Exception{
        mvc.perform(get("/api/versao").with(user("operador").roles("OPERADOR"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.centralEnviosContrato").value("documental-conferencia-v2"))
                .andExpect(jsonPath("$.estaticosSha256['painel-documentos.js']").isNotEmpty()).andExpect(jsonPath("$.estaticosSha256['painel-versao.js']").isNotEmpty());
    }
    @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="rotabrasil.browser",matches="true")
    void navegadorProtegidoComDoubleSemSmtp()throws Exception{
        String hash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("SenhaSomenteFixture12!");
        jdbc.update("UPDATE usuarios SET senha_hash=?",hash);
        var chamadas=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(inv->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tentativas_email WHERE situacao='DESCONHECIDO'",Integer.class)).isPositive();
            return chamadas.incrementAndGet()==1?TransporteEmail.Resultado.ACEITO:TransporteEmail.Resultado.DESCONHECIDO;
        }).when(transporte).enviar(any());
        Path script=Path.of("../scripts/testar-central-envios-real.cjs").toAbsolutePath().normalize();
        Path saida=Path.of(System.getProperty("rotabrasil.browser.output",System.getProperty("java.io.tmpdir")+"/central-envios-browser"));
        Files.createDirectories(saida);
        var p=new ProcessBuilder("node",script.toString(),"http://127.0.0.1:"+porta,saida.toString(),String.valueOf(viagem)).redirectErrorStream(true).redirectOutput(saida.resolve("playwright.log").toFile()).start();
        try {assertThat(p.waitFor(180,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(p.exitValue()).isZero();}
        finally {if(p.isAlive())p.destroyForcibly();}
        verify(transporte,times(2)).enviar(any());verifyNoInteractions(sender);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tentativas_email",Integer.class)).isEqualTo(2);
    }
}
