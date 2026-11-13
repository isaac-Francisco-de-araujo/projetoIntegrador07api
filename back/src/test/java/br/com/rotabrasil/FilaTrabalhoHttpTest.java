package br.com.rotabrasil;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:fila-equipe;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000","spring.flyway.enabled=true","rotabrasil.seguranca.ativa=true","rotabrasil.arquivos.viagens=${java.io.tmpdir}/rotabrasil-fila-nao-usado"})
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class FilaTrabalhoHttpTest {
    @org.springframework.test.context.DynamicPropertySource static void props(org.springframework.test.context.DynamicPropertyRegistry r){r.add("rotabrasil.seguranca.admin-usuario",()->"admin");r.add("rotabrasil.seguranca.admin-hash",()->new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("SenhaTesteSintetica12!"));}
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;
    String id;
    @BeforeEach void preparar(){
        jdbc.update("DELETE FROM eventos_responsabilidade");jdbc.update("DELETE FROM responsabilidades");jdbc.update("DELETE FROM processamentos");jdbc.update("DELETE FROM usuarios");
        for(String nome:List.of("ana","carlos","admin","leitor"))jdbc.update("INSERT INTO usuarios(usuario,senha_hash,papel,criado_em) VALUES(?,'hash-sintetico',?,CURRENT_TIMESTAMP)",nome,nome.equals("admin")?"ADMIN":nome.equals("leitor")?"CONSULTA":"OPERADOR");
        id=UUID.randomUUID().toString();inserir(id);
    }
    void inserir(String id){jdbc.update("INSERT INTO processamentos(id,pacote,fase,situacao,ator,criado_em,diagnostico) VALUES(?,'pacote-sintetico.zip','CONFERENCIA','INTERROMPIDA','LEGADO',CURRENT_TIMESTAMP,'{}')",id);}
    int atribuir(String ator,String acao,long versao,Long destino)throws Exception{return mvc.perform(post("/api/fila/PROCESSAMENTO/"+id+"/responsabilidade").with(user(ator)).with(csrf()).contentType("application/json").content("{\"acao\":\""+acao+"\",\"versao\":"+versao+",\"destino\":"+destino+",\"confirmado\":true}")).andReturn().getResponse().getStatus();}
    @Test void disputaAtomicaLiberacaoTransferenciaEHistorico()throws Exception{
        var barreira=new CyclicBarrier(2);try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{barreira.await();return atribuir("ana","ASSUMIR",0,null);});var b=pool.submit(()->{barreira.await();return atribuir("carlos","ASSUMIR",0,null);});
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        String dono=jdbc.queryForObject("SELECT u.usuario FROM responsabilidades r JOIN usuarios u ON u.id=r.usuario_id",String.class);
        assertThat(atribuir(dono,"LIBERAR",1,null)).isEqualTo(200);
        long destino=jdbc.queryForObject("SELECT id FROM usuarios WHERE usuario='carlos'",Long.class);
        assertThat(atribuir("ana","TRANSFERIR",2,destino)).isEqualTo(403);
        assertThat(atribuir("admin","TRANSFERIR",2,destino)).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eventos_responsabilidade",Integer.class)).isEqualTo(3);
        mvc.perform(get("/api/fila").with(user("carlos")).param("escopo","MEUS")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.itens[0].responsavel").value("carlos"));
        jdbc.update("UPDATE usuarios SET ativo=FALSE WHERE usuario='carlos'");
        mvc.perform(get("/api/fila").with(user("ana"))).andExpect(jsonPath("$.itens[0].responsavelAtivo").value(false));
        jdbc.update("UPDATE usuarios SET ativo=TRUE,papel='CONSULTA' WHERE usuario='carlos'");
        mvc.perform(get("/api/fila").with(user("ana"))).andExpect(jsonPath("$.itens[0].responsavelAtivo").value(false));
        jdbc.update("UPDATE processamentos SET criado_em=DATEADD('DAY',-400,CURRENT_TIMESTAMP) WHERE id=?",id);
        mvc.perform(post("/api/admin/retencao").with(user("admin")).with(csrf()).contentType("application/json").content("{\"diasProcessamentos\":30,\"confirmado\":true}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processamentos WHERE id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void paginacaoFiltrosPermissoesECritica()throws Exception{
        for(int i=0;i<4;i++)inserir(UUID.randomUUID().toString());
        var primeira=mvc.perform(get("/api/fila").with(user("ana")).param("tamanho","2")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5)).andReturn().getResponse().getContentAsString();
        assertThat(mvc.perform(get("/api/fila").with(user("ana")).param("tamanho","2")).andReturn().getResponse().getContentAsString().replaceAll("\"verificadoEm\":\"[^\"]+\"","" )).isEqualTo(primeira.replaceAll("\"verificadoEm\":\"[^\"]+\"",""));
        mvc.perform(get("/api/fila").with(user("leitor")).param("tamanho","51")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/fila").with(user("ana")).param("inicio","2026-10-02").param("fim","2026-01-01")).andExpect(status().isBadRequest());
        assertThat(atribuir("leitor","ASSUMIR",0,null)).isEqualTo(403);
        jdbc.update("UPDATE processamentos SET situacao='EM_PROCESSAMENTO' WHERE id=?",id);assertThat(atribuir("ana","ASSUMIR",0,null)).isEqualTo(409);
    }
}
