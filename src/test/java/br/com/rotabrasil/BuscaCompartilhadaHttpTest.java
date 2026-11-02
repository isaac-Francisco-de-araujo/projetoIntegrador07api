package br.com.rotabrasil;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:busca-equipe;DB_CLOSE_DELAY=-1","spring.flyway.enabled=true","rotabrasil.arquivos.viagens=${java.io.tmpdir}/rotabrasil-busca-nao-usado"})
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class BuscaCompartilhadaHttpTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;
    @Test void paginacaoEstavelFiltrosCombinadosEArquivoCompartilhadoSemDuplicacao()throws Exception{
        jdbc.update("INSERT INTO clientes(id,nome,cnpj) VALUES(1,'Cliente Sintetico','12345678901234')");
        for(int i=1;i<=4;i++)jdbc.update("INSERT INTO viagens(id,numero,cliente_id,data,status) VALUES(?,?,1,'2026-10-01','Recebida')",i,String.valueOf(i));
        jdbc.update("INSERT INTO pacotes VALUES(1,'pacote-sintetico.zip',?,?,CURRENT_TIMESTAMP)","a".repeat(64),"b".repeat(64));
        jdbc.update("INSERT INTO anexos(id,pacote_id,nome_arquivo,finalidade,caminho,sha256,token,numero_armazenamento) VALUES(1,1,'fatura-sintetica.pdf','FATURA','caminho-interno-nao-publico',?,'00000000-0000-0000-0000-000000000001','1')","c".repeat(64));
        jdbc.update("INSERT INTO anexo_viagens VALUES(1,1),(1,2)");
        mvc.perform(get("/api/busca").param("entidade","VIAGEM").param("tamanho","2").param("inicio","2026-10-01").param("fim","2026-10-01").param("cliente","1").param("situacao","Recebida")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4)).andExpect(jsonPath("$.itens[0].id").value(4));
        mvc.perform(get("/api/busca").param("entidade","VIAGEM").param("tamanho","2").param("pagina","1")).andExpect(jsonPath("$.itens[0].id").value(2));
        mvc.perform(get("/api/busca").param("entidade","ARQUIVO").param("busca","Cliente Sintetico").param("cliente","1").param("pacote","1").param("tipo","PDF").param("estado","ATIVO")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("caminho-interno"))));
        mvc.perform(get("/api/busca").param("entidade","PACOTE").param("estado","ATIVO")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/busca").param("entidade","CLIENTE").param("busca","12345678901234")).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/busca").param("busca","inexistente")).andExpect(jsonPath("$.total").value(0));
        for(String parametro:new String[]{"tamanho","pagina"})mvc.perform(get("/api/busca").param(parametro,"-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/busca").param("ordem","data; DELETE FROM clientes")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/busca").param("inicio","2026-11-02").param("fim","2026-01-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/busca").param("busca","%' OR 1=1 --")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }
}
