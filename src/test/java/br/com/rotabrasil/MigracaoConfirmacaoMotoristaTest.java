package br.com.rotabrasil;

import br.com.rotabrasil.manutencao.InventarioBanco;
import br.com.rotabrasil.migracao.V3__ConferirEPreencherIdentidade;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class MigracaoConfirmacaoMotoristaTest {
    @TempDir Path tmp;
    @Test void v10ParaV11PreservaRecebimentoEAvisoHistoricoSemInventarTentativas()throws Exception{
        Path acervo=Files.createDirectory(tmp.resolve("acervo"));String url="jdbc:h2:mem:motorista-migracao-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        var fonte=new DriverManagerDataSource(url,"sa","");
        var v3=new V3__ConferirEPreencherIdentidade(new InventarioBanco(acervo),acervo,null);
        Flyway.configure().dataSource(fonte).javaMigrations(v3).target("10").cleanDisabled(true).load().migrate();var jdbc=new JdbcTemplate(fonte);
        String id=UUID.randomUUID().toString(),aviso=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO recebimentos VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",id,"a".repeat(64),"motorista@example.invalid","operacao@example.invalid",false,"{\"legado\":true}");
        jdbc.update("INSERT INTO notificacoes_recebimento(id,recebimento_id,evento,chave_evento,referencia,remetente,destinatario,configuracao,assunto,mensagem,modo,situacao,criada_em) VALUES(?,?,'ARQUIVO_RECEBIDO','recebido',?,?,?,'{}','Assunto sintético','Mensagem histórica','SIMULADO','SIMULADO',CURRENT_TIMESTAMP)",aviso,id,id,"operacao@example.invalid","motorista@example.invalid");
        var antes=jdbc.queryForMap("SELECT remetente,destinatario,assunto,mensagem,modo,situacao,criada_em FROM notificacoes_recebimento WHERE id=?",aviso);
        Flyway.configure().dataSource(fonte).javaMigrations(v3).cleanDisabled(true).load().migrate();
        assertThat(jdbc.queryForMap("SELECT remetente,destinatario,assunto,mensagem,modo,situacao,criada_em FROM notificacoes_recebimento WHERE id=?",aviso)).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conferencias_notificacao",Long.class)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notificacoes_recebimento WHERE conferencia_id IS NOT NULL",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT configuracao FROM recebimentos WHERE id=?",String.class,id)).isEqualTo("{\"legado\":true}");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\"='11' AND \"success\"=TRUE",Long.class)).isEqualTo(1);
        // A nova restrição admite REAL, mas a migração não ativa nem executa transporte.
        jdbc.update("UPDATE notificacoes_recebimento SET modo='REAL' WHERE id=?",aviso);assertThat(jdbc.queryForObject("SELECT modo FROM notificacoes_recebimento WHERE id=?",String.class,aviso)).isEqualTo("REAL");
    }
}
