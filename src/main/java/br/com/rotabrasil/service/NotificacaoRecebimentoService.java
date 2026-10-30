package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.OperacaoDtos.*;
import br.com.rotabrasil.exception.*;
import br.com.rotabrasil.service.email.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class NotificacaoRecebimentoService {
    private final JdbcTemplate jdbc;private final ConfiguracaoOperacaoService config;
    private final CoordenacaoImportacaoService coordenacao;private final TransactionTemplate tx;
    public NotificacaoRecebimentoService(JdbcTemplate jdbc,ConfiguracaoOperacaoService config,CoordenacaoImportacaoService coordenacao,PlatformTransactionManager manager){
        this.jdbc=jdbc;this.config=config;this.coordenacao=coordenacao;tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public String receber(MultipartFile arquivo,String destinatario,boolean simular){
        String email=destinatario==null||destinatario.isBlank()?null:destinatario.trim();
        if(email!=null)ConfiguracaoOperacaoService.validarEmail(email);
        String hash;
        try(var entrada=arquivo.getInputStream()){hash=ConteudoXml.sha256(ConteudoXml.ler(entrada));}
        catch(IOException ex){throw new ArquivoInvalidoException("Não foi possível receber o arquivo");}
        String id=tx.execute(status->{
            coordenacao.bloquear();var c=config.consultar();
            var existentes=jdbc.queryForList("SELECT * FROM recebimentos WHERE sha256=?",hash);
            if(!existentes.isEmpty()){
                var anterior=existentes.getFirst();
                if(email!=null&&!Objects.equals(email,anterior.get("DESTINATARIO")))
                    throw new ConflitoRecursoException("ZIP já recebido com outra seleção de destinatário; o recebimento anterior foi preservado");
                return (String)anterior.get("ID");
            }
            if(simular&&(email==null||c.remetente()==null))throw new ArquivoInvalidoException("Para simular a confirmação, salve o remetente e informe o destinatário deste upload");
            String novo=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO recebimentos VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",novo,hash,email,c.remetente(),simular,config.escrever(c));return novo;
        });
        evento(id,"ARQUIVO_RECEBIDO","recebido",id,"Arquivo recebido pelo upload manual. Este aviso não confirma leitura, conferência ou importação dos documentos.");
        return id;
    }
    public void evento(String recebimentoId,String evento,String chave,String referencia,String texto){
        evento(recebimentoId,evento,chave,referencia,texto,true);
    }
    private void evento(String recebimentoId,String evento,String chave,String referencia,String texto,boolean simularAutorizado){
        String id=tx.execute(status->{
            coordenacao.bloquear();var r=registro(recebimentoId);
            var existentes=jdbc.queryForList("SELECT id FROM notificacoes_recebimento WHERE recebimento_id=? AND chave_evento=?",String.class,recebimentoId,chave);
            if(!existentes.isEmpty())return existentes.getFirst();
            String novo=UUID.randomUUID().toString();
            String mensagem=texto+" Referência da operação: "+referencia+". Sem anexos fiscais. Nenhum e-mail real enviado.";
            jdbc.update("INSERT INTO notificacoes_recebimento(id,recebimento_id,evento,chave_evento,referencia,remetente,destinatario,configuracao,assunto,mensagem,modo,situacao,criada_em,correlacao) VALUES(?,?,?,?,?,?,?,?,?,?,'SIMULADO',?,CURRENT_TIMESTAMP,?)",
                    novo,recebimentoId,evento,chave,referencia,r.get("REMETENTE"),r.get("DESTINATARIO"),r.get("CONFIGURACAO"),"RotaBrasil · "+evento.replace('_',' '),mensagem,
                    r.get("REMETENTE")==null||r.get("DESTINATARIO")==null?"CONFIGURACAO_INCOMPLETA":"PENDENTE",org.slf4j.MDC.get("operacaoId"));
            return novo;
        });
        boolean autorizado=tx.execute(status->Boolean.TRUE.equals(registro(recebimentoId).get("SIMULAR")));
        if(autorizado&&simularAutorizado)simular(recebimentoId,id);
    }
    public Notificacao simular(String recebimentoId,String id){
        var mensagem=tx.execute(status->{
            coordenacao.bloquear();registro(recebimentoId);
            var linhas=jdbc.queryForList("SELECT * FROM notificacoes_recebimento WHERE id=? AND recebimento_id=?",id,recebimentoId);
            if(linhas.isEmpty())throw new RecursoNaoEncontradoException("Notificação não encontrada");
            var r=linhas.getFirst();
            if(!"PENDENTE".equals(r.get("SITUACAO"))){
                if("CONFIGURACAO_INCOMPLETA".equals(r.get("SITUACAO")))throw new ConflitoRecursoException("Recebimento sem remetente ou destinatário explícito; este histórico não será reescrito");
                return null;
            }
            jdbc.update("UPDATE notificacoes_recebimento SET situacao='DESCONHECIDO' WHERE id=?",id);
            return new MensagemNotificacao((String)r.get("REMETENTE"),(String)r.get("DESTINATARIO"),(String)r.get("ASSUNTO"),(String)r.get("MENSAGEM"));
        });
        if(mensagem!=null){
            // Este caminho é sempre simulado, inclusive se o transporte documental estiver REAL.
            var resultado=new TransporteEmailSimulado().enviarNotificacao(mensagem);
            tx.executeWithoutResult(status->jdbc.update("UPDATE notificacoes_recebimento SET situacao=?,concluida_em=CURRENT_TIMESTAMP WHERE id=?",
                    resultado==TransporteEmail.Resultado.ACEITO?"SIMULADO":"DESCONHECIDO",id));
        }
        return consultar(recebimentoId).notificacoes().stream().filter(n->n.id().equals(id)).findFirst().orElseThrow();
    }
    private Map<String,Object> registro(String id){
        var r=jdbc.queryForList("SELECT * FROM recebimentos WHERE id=?",id);
        if(r.isEmpty())throw new RecursoNaoEncontradoException("Recebimento não encontrado");return r.getFirst();
    }
    public Recebimento porHash(String hash){
        var ids=jdbc.queryForList("SELECT id FROM recebimentos WHERE sha256=?",String.class,hash);
        if(ids.isEmpty())throw new RecursoNaoEncontradoException("Recebimento não encontrado");return consultar(ids.getFirst());
    }
    public Recebimento consultar(String id){
        var r=registro(id);
        var avisos=jdbc.query("SELECT * FROM notificacoes_recebimento WHERE recebimento_id=? ORDER BY criada_em,id",(rs,n)->
                new Notificacao(rs.getString("id"),rs.getString("evento"),rs.getString("referencia"),rs.getString("remetente"),rs.getString("destinatario"),rs.getString("assunto"),rs.getString("mensagem"),rs.getString("modo"),rs.getString("situacao"),rs.getObject("criada_em",OffsetDateTime.class)),id);
        return new Recebimento(id,(String)r.get("SHA256"),(String)r.get("DESTINATARIO"),(String)r.get("REMETENTE"),Boolean.TRUE.equals(r.get("SIMULAR")),avisos);
    }
    public void eventoPacote(Long pacoteId,String evento,String chave,String referencia,String texto){
        eventoPacote(pacoteId,evento,chave,referencia,texto,true);
    }
    public void eventoPacote(Long pacoteId,String evento,String chave,String referencia,String texto,boolean simularAutorizado){
        var ids=jdbc.queryForList("SELECT r.id FROM recebimentos r JOIN pacotes p ON p.sha256=r.sha256 WHERE p.id=?",String.class,pacoteId);
        if(!ids.isEmpty())evento(ids.getFirst(),evento,chave,referencia,texto,simularAutorizado);
    }
}
