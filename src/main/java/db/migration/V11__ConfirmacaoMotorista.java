package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.util.ArrayList;

/** Evolui o histórico existente; o CHECK V9 recebeu nome gerado pelo H2. */
public class V11__ConfirmacaoMotorista extends BaseJavaMigration {
    @Override public Integer getChecksum(){return 1101;}
    @Override public void migrate(Context context)throws Exception{
        var c=context.getConnection();var nomes=new ArrayList<String>();
        try(var p=c.prepareStatement("SELECT t.constraint_name FROM information_schema.table_constraints t JOIN information_schema.check_constraints k ON t.constraint_schema=k.constraint_schema AND t.constraint_name=k.constraint_name WHERE t.table_schema='PUBLIC' AND t.table_name='NOTIFICACOES_RECEBIMENTO' AND t.constraint_type='CHECK' AND k.check_clause LIKE '%MODO%'" );var r=p.executeQuery()){
            while(r.next())nomes.add(r.getString(1));
        }
        if(nomes.size()!=1)throw new IllegalStateException("Restrição de modo das notificações divergente; migração interrompida");
        try(var s=c.createStatement()){
            s.execute("ALTER TABLE notificacoes_recebimento DROP CONSTRAINT \""+nomes.getFirst().replace("\"","\"\"")+"\"");
            s.execute("ALTER TABLE notificacoes_recebimento ADD CONSTRAINT ck_notificacao_modo CHECK(modo IN ('SIMULADO','REAL'))");
            s.execute("ALTER TABLE recebimentos ADD nome_arquivo VARCHAR(120)");
            s.execute("ALTER TABLE recebimentos ADD revisao_destinatario BIGINT NOT NULL DEFAULT 0");
            s.execute("CREATE TABLE conferencias_notificacao (id VARCHAR(36) PRIMARY KEY, recebimento_id VARCHAR(36) REFERENCES recebimentos(id), anterior_id VARCHAR(36) REFERENCES notificacoes_recebimento(id), snapshot TEXT NOT NULL, ator VARCHAR(100) NOT NULL, criada_em TIMESTAMP WITH TIME ZONE NOT NULL, expira_em TIMESTAMP WITH TIME ZONE NOT NULL)");
            s.execute("ALTER TABLE notificacoes_recebimento ADD conferencia_id VARCHAR(36) UNIQUE REFERENCES conferencias_notificacao(id)");
            s.execute("ALTER TABLE notificacoes_recebimento ADD anterior_id VARCHAR(36) REFERENCES notificacoes_recebimento(id)");
            s.execute("ALTER TABLE notificacoes_recebimento ADD motivo VARCHAR(500)");
            s.execute("ALTER TABLE notificacoes_recebimento ADD ator VARCHAR(100)");
            s.execute("ALTER TABLE notificacoes_recebimento ADD snapshot TEXT");
            s.execute("CREATE INDEX ix_notificacao_anterior ON notificacoes_recebimento(anterior_id)");
        }
    }
}
