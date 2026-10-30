package br.com.rotabrasil;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:seguranca-v8;DB_CLOSE_DELAY=-1","spring.flyway.enabled=true","rotabrasil.seguranca.ativa=true","rotabrasil.seguranca.admin-usuario=administrador"})
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class SegurancaHttpTest {
    static final String SENHA="SenhaSintetica12!";
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("rotabrasil.seguranca.admin-hash",()->new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode(SENHA));r.add("rotabrasil.arquivos.viagens",()->System.getProperty("java.io.tmpdir")+"/rotabrasil-seguranca-nao-usado");}
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;
    @BeforeEach void preparar(){
        jdbc.update("UPDATE usuarios SET ativo=TRUE,papel='ADMIN' WHERE usuario='administrador'");jdbc.update("DELETE FROM usuarios WHERE usuario<>'administrador'");
        for(String nome:new String[]{"leitor","operador"})jdbc.update("INSERT INTO usuarios(usuario,senha_hash,papel,criado_em) VALUES(?,?,?,CURRENT_TIMESTAMP)",nome,encoder.encode(SENHA),nome.equals("leitor")?"CONSULTA":"OPERADOR");
    }
    MockHttpSession login(String nome)throws Exception{return (MockHttpSession)mvc.perform(formLogin().user(nome).password(SENHA)).andExpect(status().is3xxRedirection()).andReturn().getRequest().getSession(false);}
    @Test void apiExigeLoginEPaginaRedirecionaAoFormulario()throws Exception{
        mvc.perform(get("/api/viagens")).andExpect(status().isUnauthorized()).andExpect(header().exists("X-Request-Id"));
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
    }
    @Test void leitorConsultaMasNaoImportaNemAdministra()throws Exception{
        var s=login("leitor");mvc.perform(get("/api/viagens").session(s)).andExpect(status().isOk());
        mvc.perform(post("/api/pacotes/inspecao").session(s).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/usuarios").session(s)).andExpect(status().isForbidden());
        mvc.perform(get("/api/configuracoes").session(s)).andExpect(status().isForbidden());
        mvc.perform(post("/api/pacotes/1/exportacoes").session(s).with(csrf()).contentType("application/json").content("{\"confirmado\":true}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/pacotes").session(s)).andExpect(status().isOk());
        mvc.perform(delete("/api/pacotes/1").session(s).param("confirmado","true").param("conferenciaSha256","0".repeat(64)).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void csrfObrigatorioEOperadorNaoExcluiCadastro()throws Exception{
        var s=login("operador");mvc.perform(post("/api/viagens/1/conferencias-email").session(s).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/clientes/1").session(s).param("confirmado","true").param("conferenciaSha256","0".repeat(64)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/viagens/1").session(s).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/pacotes/1").session(s).param("confirmado","true").param("conferenciaSha256","0".repeat(64))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/pacotes/999").session(s).param("confirmado","true").param("conferenciaSha256","0".repeat(64)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/viagens/1/conferencias-email").session(s).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }
    @Test void exclusaoDeClienteExigeAdminECsrfMesmoComConfirmacao()throws Exception{
        var admin=login("administrador");
        mvc.perform(delete("/api/clientes/999").session(admin).param("confirmado","true").param("conferenciaSha256","0".repeat(64))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/clientes/999").session(admin).param("confirmado","true").param("conferenciaSha256","0".repeat(64)).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void configuracoesPreservamAdminECsrfSemNovasTelasDeLogin()throws Exception{
        var operador=login("operador");mvc.perform(get("/api/configuracoes").session(operador)).andExpect(status().isForbidden());
        var admin=login("administrador");mvc.perform(get("/api/configuracoes").session(admin)).andExpect(status().isOk());
        mvc.perform(post("/api/configuracoes/teste-pasta").session(admin)).andExpect(status().isForbidden());
        mvc.perform(post("/api/configuracoes/subpastas").session(admin).with(csrf()).contentType("application/json").content("{\"nome\":\"../fora\"}")).andExpect(status().isBadRequest());
    }
    @Test void administradorCriaUsuarioSemExporSenhaOuHashEAuditaAtor()throws Exception{
        var s=login("administrador");String corpo="{\"usuario\":\"novo\",\"senha\":\"SenhaSinteticaNova12!\",\"papel\":\"CONSULTA\"}";
        mvc.perform(post("/api/admin/usuarios").session(s).with(csrf()).contentType("application/json").content(corpo)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SenhaSintetica"))));
        String resposta=mvc.perform(get("/api/admin/usuarios").session(s)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertThat(resposta).doesNotContain("SENHA_HASH","$2a$");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE ator='administrador' AND recurso='/api/admin/usuarios' AND status_http=200",Integer.class)).isPositive();
    }
    @Test void desativacaoRevogaSessaoExistenteEProtegeUltimoAdmin()throws Exception{
        var s=login("operador");jdbc.update("UPDATE usuarios SET ativo=FALSE WHERE usuario='operador'");mvc.perform(get("/api/viagens").session(s)).andExpect(status().isUnauthorized());
        var admin=login("administrador");long id=jdbc.queryForObject("SELECT id FROM usuarios WHERE usuario='administrador'",Long.class);
        mvc.perform(put("/api/admin/usuarios/"+id).session(admin).with(csrf()).contentType("application/json").content("{\"papel\":\"CONSULTA\",\"ativo\":false}")).andExpect(status().isConflict());
    }
}
