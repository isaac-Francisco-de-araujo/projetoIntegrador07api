package br.com.rotabrasil;

import br.com.rotabrasil.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SaudeCompartilhadaTest {
    @TempDir Path pasta;
    @Test void falhaIsoladaBancoNaoImpedeDemaisVerificacoesNemInventaBackupOuTesteEmail()throws Exception{
        var jdbc=mock(JdbcTemplate.class);var destinos=mock(DestinoExportacaoService.class);var canal=mock(ConfiguracaoNotificacoes.class);
        when(jdbc.queryForObject("SELECT 1",Integer.class)).thenThrow(new IllegalStateException("SQL PRIVADO"));
        when(canal.consultar(null)).thenReturn(new ConfiguracaoNotificacoes.Estado("SIMULADO","NAO_IDENTIFICADA","BASIC",false,"BLOQUEADO",List.of(),false,"AUSENTE","NAO_INFORMADA",null,"ADMIN",false,false));
        var service=new SaudeCompartilhadaService(jdbc,destinos,canal,new ObjectMapper(),pasta.toString(),"","SIMULADO");
        var r=service.consultar(true);assertThat(r.verificacoes()).hasSize(6);
        assertThat(r.verificacoes()).filteredOn(v->v.componente().equals("Banco")).allMatch(v->v.estado().equals("INDISPONIVEL"));
        assertThat(r.verificacoes()).filteredOn(v->v.componente().equals("Backup")).allMatch(v->v.estado().equals("NAO_VERIFICADO")&&v.mensagem().contains("Sem informação"));
        assertThat(r.toString()).doesNotContain("SQL PRIVADO",pasta.toString());verify(canal,never()).enviar(any());
        var operador=service.consultar(false);assertThat(operador.toString()).doesNotContain("espacoDisponivelBytes","ultimosTestes");
    }
}
