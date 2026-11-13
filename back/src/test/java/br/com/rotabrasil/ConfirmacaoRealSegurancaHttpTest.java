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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.util.FileSystemUtils;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** REAL exercitado só com double de transporte, nunca com conexão externa. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:confirmacao-real;DB_CLOSE_DELAY=-1","spring.flyway.enabled=true","rotabrasil.seguranca.ativa=true","rotabrasil.email.modo=SIMULADO","rotabrasil.notificacoes.modo=REAL","rotabrasil.notificacoes.tipo-conta=OUTLOOK_PESSOAL","rotabrasil.email.smtp.ativado=true","rotabrasil.email.smtp.host=smtp-mail.outlook.com","rotabrasil.email.smtp.usuario=operacao@example.invalid","rotabrasil.email.smtp.remetente=operacao@example.invalid","rotabrasil.email.smtp.autenticacao=XOAUTH2"})
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ConfirmacaoRealSegurancaHttpTest {
    static final Path TMP=temporario();static Path temporario(){try{return Files.createTempDirectory("motorista-real-");}catch(Exception ex){throw new IllegalStateException(ex);}}
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("rotabrasil.arquivos.viagens",()->TMP.resolve("acervo").toString());r.add("rotabrasil.email.smtp.oauth2.token-arquivo",()->TMP.resolve("credencial.json").toString());r.add("rotabrasil.seguranca.admin-usuario",()->"administrador");r.add("rotabrasil.seguranca.admin-hash",()->new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("SenhaSomenteFixture12!"));}
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired NotificacaoRecebimentoService notificacoes;
    @SpyBean ConfiguracaoNotificacoes canal;
    String recebimento;
    @BeforeEach void preparar()throws Exception{
        reset(canal);Files.writeString(TMP.resolve("credencial.json"),"{\"access_token\":\"TOKEN_SINTETICO_NUNCA_USAR\",\"expires_at\":\""+Instant.now().plusSeconds(3600)+"\"}");
        jdbc.update("DELETE FROM usuarios");for(String nome:List.of("administrador","operador","leitor"))jdbc.update("INSERT INTO usuarios(usuario,senha_hash,papel,criado_em) VALUES(?,?,?,CURRENT_TIMESTAMP)",nome,"hash-sintetico",nome.equals("leitor")?"CONSULTA":nome.equals("operador")?"OPERADOR":"ADMIN");
        jdbc.update("UPDATE configuracao_operacao SET remetente='operacao@example.invalid',versao=versao+1 WHERE id=1");
        recebimento=notificacoes.receber(new MockMultipartFile("arquivo","teste.zip","application/zip",UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),"motorista@example.invalid",false);
        doReturn(TransporteEmail.Resultado.ACEITO).when(canal).enviar(any());
    }
    @AfterAll static void limpar()throws Exception{FileSystemUtils.deleteRecursively(TMP);}
    JsonNode conferir(String usuario)throws Exception{return json.readTree(mvc.perform(post("/api/recebimentos/"+recebimento+"/conferencias-confirmacao").with(user(usuario).roles(usuario.equals("administrador")?"ADMIN":"OPERADOR")).with(csrf()).contentType("application/json").content("{\"destinatario\":\"motorista@example.invalid\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    JsonNode confirmar(JsonNode c)throws Exception{return json.readTree(mvc.perform(post("/api/recebimentos/"+recebimento+"/confirmacoes").with(user("operador").roles("OPERADOR")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("conferenciaId",c.get("id").asText(),"confirmado",true)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    @Test void segurancaPapeisECsrfContinuamObrigatorios()throws Exception{
        String rota="/api/recebimentos/"+recebimento+"/conferencias-confirmacao";
        mvc.perform(post(rota).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post(rota).with(user("leitor").roles("CONSULTA")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(rota).with(user("operador").roles("OPERADOR")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/configuracoes/conferencias-email-real").with(user("operador").roles("OPERADOR")).with(csrf()).contentType("application/json").content("{\"destinatario\":\"teste@example.invalid\"}")).andExpect(status().isForbidden());verify(canal,never()).enviar(any());
    }
    @Test void aceiteRealDoDoubleNaoComprovaEntregaENaoAtivaDocumentos()throws Exception{
        var c=conferir("operador");assertThat(c.get("snapshot").get("modo").asText()).isEqualTo("REAL");verify(canal,never()).enviar(any());var n=confirmar(c);assertThat(n.get("situacao").asText()).isEqualTo("ACEITO_SMTP");assertThat(n.get("motivo").asText()).contains("não comprova entrega ou leitura");assertThat(confirmar(c)).isEqualTo(n);verify(canal,times(1)).enviar(any());
        String config=mvc.perform(get("/api/configuracoes").with(user("administrador").roles("ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertThat(config).contains("\"modoEnvioDocumental\":\"SIMULADO\"").doesNotContain("TOKEN_SINTETICO_NUNCA_USAR",TMP.toString(),"access_token");
    }
    @Test void testeRealExigePreviaConfirmacaoExplicitaETransportaUmaVez()throws Exception{
        var c=json.readTree(mvc.perform(post("/api/configuracoes/conferencias-email-real").with(user("administrador").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"destinatario\":\"teste@example.invalid\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(c.get("snapshot").get("mensagem").asText()).contains("Mensagem sintética","Nenhum arquivo ou documento real");verify(canal,never()).enviar(any());
        String rota="/api/configuracoes/confirmacoes-email-real";mvc.perform(post(rota).with(user("administrador").roles("ADMIN")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("conferenciaId",c.get("id").asText(),"confirmado",false)))).andExpect(status().isBadRequest());
        String body=json.writeValueAsString(Map.of("conferenciaId",c.get("id").asText(),"confirmado",true));for(int i=0;i<2;i++)mvc.perform(post(rota).with(user("administrador").roles("ADMIN")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("ACEITO_SMTP"));verify(canal,times(1)).enviar(any());
    }
    @Test void tokenExpiradoBloqueiaSemChamarTransporteENaoVazaSegredo()throws Exception{
        Files.writeString(TMP.resolve("credencial.json"),"{\"access_token\":\"TOKEN_SINTETICO_NUNCA_USAR\",\"expires_at\":\"2000-01-01T00:00:00Z\"}");
        String erro=mvc.perform(post("/api/recebimentos/"+recebimento+"/conferencias-confirmacao").with(user("operador").roles("OPERADOR")).with(csrf()).contentType("application/json").content("{\"destinatario\":\"motorista@example.invalid\"}")).andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();assertThat(erro).contains("expirada").doesNotContain("TOKEN_SINTETICO",TMP.toString());verify(canal,never()).enviar(any());
    }
}
