package br.com.rotabrasil;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:edicao-compartilhada;DB_CLOSE_DELAY=-1","spring.flyway.enabled=true","rotabrasil.arquivos.viagens=${java.io.tmpdir}/rotabrasil-edicao-nao-usado"})
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class EdicaoCompartilhadaHttpTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;
    @Test void edicaoAntigaNaoSobrescreveNemExcluiVersaoNova()throws Exception{
        jdbc.update("INSERT INTO clientes(nome,cnpj) VALUES('Sintetico','12345678901234')");
        long id=jdbc.queryForObject("SELECT id FROM clientes",Long.class);String rota="/api/clientes/"+id;
        String etag=mvc.perform(get(rota)).andReturn().getResponse().getHeader("ETag");
        String novo="{\"nome\":\"Ana\",\"cnpj\":\"12345678901234\"}";
        mvc.perform(put(rota).header("If-Match",etag).contentType("application/json").content(novo)).andExpect(status().isOk());
        mvc.perform(put(rota).header("If-Match",etag).contentType("application/json").content(novo.replace("Ana","Carlos"))).andExpect(status().isConflict());
        mvc.perform(put(rota).contentType("application/json").content(novo)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT nome FROM clientes WHERE id=?",String.class,id)).isEqualTo("Ana");
    }
}
