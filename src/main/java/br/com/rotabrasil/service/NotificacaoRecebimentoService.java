package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.OperacaoDtos.*;
import br.com.rotabrasil.dto.ConfirmacaoRecebimentoDtos.*;
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
    private final CoordenacaoImportacaoService coordenacao;private final TransactionTemplate tx;private final ConfiguracaoNotificacoes canal;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    public NotificacaoRecebimentoService(JdbcTemplate jdbc,ConfiguracaoOperacaoService config,CoordenacaoImportacaoService coordenacao,PlatformTransactionManager manager,ConfiguracaoNotificacoes canal,com.fasterxml.jackson.databind.ObjectMapper json){
        this.jdbc=jdbc;this.config=config;this.coordenacao=coordenacao;this.canal=canal;this.json=json;tx=new TransactionTemplate(manager);
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
            jdbc.update("INSERT INTO recebimentos(id,sha256,destinatario,remetente,simular,configuracao,criado_em,nome_arquivo) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP,?)",novo,hash,email,c.remetente(),simular,config.escrever(c),nomeSeguro(arquivo.getOriginalFilename()));return novo;
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
                new Notificacao(rs.getString("id"),rs.getString("evento"),rs.getString("referencia"),rs.getString("remetente"),rs.getString("destinatario"),rs.getString("assunto"),rs.getString("mensagem"),rs.getString("modo"),rs.getString("situacao"),rs.getObject("criada_em",OffsetDateTime.class),rs.getObject("concluida_em",OffsetDateTime.class),rs.getString("motivo"),rs.getString("anterior_id"),rs.getString("conferencia_id"),rs.getString("correlacao"),rs.getString("ator"),rs.getString("snapshot")==null?null:lerSnapshot(rs.getString("snapshot"))),id);
        var atual=config.consultar();return new Recebimento(id,(String)r.get("SHA256"),(String)r.get("DESTINATARIO"),(String)r.get("REMETENTE"),Boolean.TRUE.equals(r.get("SIMULAR")),avisos,atual.remetente(),atual.confirmacaoMotorista());
    }
    public void eventoPacote(Long pacoteId,String evento,String chave,String referencia,String texto){
        eventoPacote(pacoteId,evento,chave,referencia,texto,true);
    }
    public void eventoPacote(Long pacoteId,String evento,String chave,String referencia,String texto,boolean simularAutorizado){
        var ids=jdbc.queryForList("SELECT r.id FROM recebimentos r JOIN pacotes p ON p.sha256=r.sha256 WHERE p.id=?",String.class,pacoteId);
        if(!ids.isEmpty())evento(ids.getFirst(),evento,chave,referencia,texto,simularAutorizado);
    }

    private static String nomeSeguro(String nome){
        String n=Objects.toString(nome,"pacote.zip").replace('\\','/');n=n.substring(n.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","");
        return n.isBlank()?"pacote.zip":n.substring(0,Math.min(n.length(),120));
    }
    private Snapshot lerSnapshot(String valor){try{return json.readValue(valor,Snapshot.class);}catch(Exception ex){throw new IllegalStateException("Retrato da confirmação indisponível");}}
    private void conferirAnterior(String recebimento,String anterior){
        var ids=jdbc.queryForList("SELECT id FROM notificacoes_recebimento WHERE recebimento_id=? AND evento='CONFIRMACAO_MOTORISTA' ORDER BY criada_em DESC,id DESC LIMIT 1",String.class,recebimento);
        if(ids.isEmpty()?anterior!=null:!Objects.equals(ids.getFirst(),anterior))throw new ConflitoRecursoException("Já há uma tentativa; consulte o histórico e prepare reenvio explicitamente vinculado à última tentativa");
    }
    private Snapshot snapshot(String id,String destinatario,boolean teste){
        ConfiguracaoOperacaoService.validarEmail(destinatario);var c=config.consultar();
        if(c.remetente()==null)throw new ArquivoInvalidoException("Salve a conta remetente antes de conferir a confirmação");
        String pacote="TESTE SINTÉTICO",estado="TESTE_SEM_DOCUMENTOS",contexto="TESTE";Long pacoteId=null;long revisao=0;
        if(!teste){
            var r=registro(id);revisao=((Number)r.get("REVISAO_DESTINATARIO")).longValue();pacote=nomeSeguro((String)r.get("NOME_ARQUIVO"));
            var ps=jdbc.queryForList("SELECT id FROM pacotes WHERE sha256=?",r.get("SHA256"));
            if(!ps.isEmpty()){
                pacoteId=((Number)ps.getFirst().get("ID")).longValue();
                long pendentes=jdbc.queryForObject("SELECT COUNT(*) FROM exportacoes WHERE pacote_id=? AND situacao<>'CONCLUIDA'",Long.class,pacoteId);
                estado=pendentes>0?"IMPORTACAO_CONCLUIDA_EXPORTACAO_PENDENTE":"IMPORTACAO_CONCLUIDA";
            }else{
                var eventos=jdbc.queryForList("SELECT evento FROM notificacoes_recebimento WHERE recebimento_id=? AND evento<>'CONFIRMACAO_MOTORISTA'",String.class,id);
                estado=eventos.contains("PROCESSAMENTO_COM_PENDENCIAS")?"PROCESSAMENTO_COM_PROBLEMAS":eventos.contains("CONFERENCIA_CONCLUIDA")?"CONFERENCIA_CONCLUIDA":"ARQUIVO_RECEBIDO";
            }
            contexto=config.escrever(jdbc.queryForList("SELECT id,evento,referencia FROM notificacoes_recebimento WHERE recebimento_id=? AND evento<>'CONFIRMACAO_MOTORISTA' ORDER BY criada_em,id",id));
        }
        String assunto=teste?"RotaBrasil · teste sintético de confirmação":"RotaBrasil · confirmação de recebimento";
        String mensagem=teste?"Mensagem sintética para testar a confirmação de recebimento do RotaBrasil. Nenhum arquivo ou documento real foi recebido por este teste.":"Confirmamos o recebimento do pacote "+pacote+".\n"+switch(estado){
            case "IMPORTACAO_CONCLUIDA" -> "A importação dos documentos no sistema foi concluída.";
            case "IMPORTACAO_CONCLUIDA_EXPORTACAO_PENDENTE" -> "A importação dos documentos no sistema foi concluída. A operação ainda acompanha pendências internas.";
            case "CONFERENCIA_CONCLUIDA" -> "A conferência dos arquivos foi concluída. A importação ainda depende da confirmação da operação.";
            case "PROCESSAMENTO_COM_PROBLEMAS" -> "O processamento identificou pendências que estão em conferência pela operação.";
            default -> "Os arquivos foram recebidos. A conferência e o processamento ainda não foram concluídos.";
        };
        mensagem+="\nEsta confirmação não comprova entrega ou leitura de e-mail. Sem documentos fiscais anexados.";
        return new Snapshot(c.remetente(),destinatario,assunto,mensagem,canal.modo(),c.versao(),canal.assinatura(),id,pacote,pacoteId,estado,revisao,ConteudoXml.sha256(contexto.getBytes(java.nio.charset.StandardCharsets.UTF_8)),teste);
    }
    public Conferencia conferir(String id,Conferir pedido){
        return tx.execute(status->{
            coordenacao.bloquear();String anterior=pedido.anteriorId()==null?null:pedido.anteriorId().toString();conferirAnterior(id,anterior);
            ConfiguracaoOperacaoService.validarEmail(pedido.destinatario());var r=registro(id);
            // Só a seleção atual muda. Os eventos e tentativas já registrados são imutáveis.
            if(!Objects.equals(pedido.destinatario(),r.get("DESTINATARIO")))jdbc.update("UPDATE recebimentos SET destinatario=?,revisao_destinatario=revisao_destinatario+1 WHERE id=?",pedido.destinatario(),id);
            var s=snapshot(id,pedido.destinatario(),false);canal.validar(s.remetente());return gravarConferencia(id,anterior,s);
        });
    }
    public Conferencia conferirTeste(Conferir pedido){
        if(!canal.modo().equals("REAL"))throw new ConflitoRecursoException("Teste real desativado; use o teste simulado ou configure explicitamente REAL");
        if(pedido.anteriorId()!=null)throw new ArquivoInvalidoException("Teste de configuração não é reenvio de recebimento");
        return tx.execute(status->{coordenacao.bloquear();var s=snapshot(null,pedido.destinatario(),true);canal.validar(s.remetente());return gravarConferencia(null,null,s);});
    }
    private Conferencia gravarConferencia(String id,String anterior,Snapshot s){
        String uuid=UUID.randomUUID().toString();var expira=OffsetDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(30);
        jdbc.update("INSERT INTO conferencias_notificacao VALUES(?,?,?,?,?,CURRENT_TIMESTAMP,?)",uuid,id,anterior,config.escrever(s),br.com.rotabrasil.seguranca.AtorAtual.nome(),expira);
        return new Conferencia(uuid,anterior,expira,s);
    }
    public Conferencia consultarConferencia(String id){
        var r=jdbc.queryForList("SELECT * FROM conferencias_notificacao WHERE id=?",id);if(r.isEmpty())throw new RecursoNaoEncontradoException("Conferência não encontrada");var c=r.getFirst();
        return new Conferencia(id,(String)c.get("ANTERIOR_ID"),(OffsetDateTime)c.get("EXPIRA_EM"),lerSnapshot((String)c.get("SNAPSHOT")));
    }
    private record Reserva(String id,Snapshot snapshot){}
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NEVER)
    public Notificacao confirmar(String recebimento,br.com.rotabrasil.dto.ConfirmacaoRecebimentoDtos.Confirmar pedido){
        String id=executar(recebimento,pedido,false);
        return consultar(recebimento).notificacoes().stream().filter(n->n.id().equals(id)).findFirst().orElseThrow();
    }
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NEVER)
    public Teste confirmarTeste(br.com.rotabrasil.dto.ConfirmacaoRecebimentoDtos.Confirmar pedido){
        String id=executar(null,pedido,true);return config.buscarTeste(id);
    }
    private String executar(String recebimento,br.com.rotabrasil.dto.ConfirmacaoRecebimentoDtos.Confirmar pedido,boolean teste){
        if(!pedido.confirmado())throw new ConflitoRecursoException("Confirme explicitamente a mensagem conferida");
        String conferencia=pedido.conferenciaId().toString();
        Reserva reserva=tx.execute(status->{
            coordenacao.bloquear();var conf=consultarConferencia(conferencia);var s=conf.snapshot();
            if(!Objects.equals(recebimento,s.recebimentoId())||teste!=s.teste())throw new ConflitoRecursoException("Conferência pertence a outra operação");
            var c=jdbc.queryForMap("SELECT ator FROM conferencias_notificacao WHERE id=?",conferencia);
            if(!Objects.equals(c.get("ATOR"),br.com.rotabrasil.seguranca.AtorAtual.nome()))throw new ConflitoRecursoException("Operador mudou; gere nova conferência");
            var existentes=teste?jdbc.queryForList("SELECT id FROM testes_configuracao WHERE id=?",String.class,conferencia):jdbc.queryForList("SELECT id FROM notificacoes_recebimento WHERE conferencia_id=?",String.class,conferencia);
            if(!existentes.isEmpty())return new Reserva(existentes.getFirst(),null);
            if(conf.expiraEm().isBefore(OffsetDateTime.now()))throw new ConflitoRecursoException("Conferência expirada; confira novamente");
            if(!teste){conferirAnterior(recebimento,conf.anteriorId());if(!Objects.equals(s.destinatario(),registro(recebimento).get("DESTINATARIO")))throw new ConflitoRecursoException("Destinatário alterado; confira novamente");}
            if(!s.equals(snapshot(recebimento,s.destinatario(),teste)))throw new ConflitoRecursoException("Configuração, seleção ou processamento mudou; confira novamente a mensagem");
            canal.validar(s.remetente());String id=teste?conferencia:UUID.randomUUID().toString();
            if(teste)jdbc.update("INSERT INTO testes_configuracao VALUES(?,'EMAIL_REAL',?,'DESCONHECIDO',?,CURRENT_TIMESTAMP)",id,config.escrever(s),"Resultado ainda não confirmado; não repetir automaticamente");
            else jdbc.update("INSERT INTO notificacoes_recebimento(id,recebimento_id,evento,chave_evento,referencia,remetente,destinatario,configuracao,assunto,mensagem,modo,situacao,criada_em,correlacao,conferencia_id,anterior_id,motivo,ator,snapshot) VALUES(?,?,'CONFIRMACAO_MOTORISTA',?,?,?,?,?,?,?,?, 'DESCONHECIDO',CURRENT_TIMESTAMP,?,?,?,?,?,?)",
                    id,recebimento,"confirmacao:"+conferencia,recebimento,s.remetente(),s.destinatario(),config.escrever(s),s.assunto(),s.mensagem(),s.modo(),org.slf4j.MDC.get("operacaoId"),conferencia,conf.anteriorId(),"Resultado ainda não confirmado; não repetir automaticamente",br.com.rotabrasil.seguranca.AtorAtual.nome(),config.escrever(s));
            return new Reserva(id,s);
        });
        if(reserva.snapshot()==null)return reserva.id();
        // O commit conservador já terminou. Nenhuma transação de importação acompanha SMTP.
        var s=reserva.snapshot();TransporteEmail.Resultado resultado;
        try{resultado=canal.enviar(new MensagemNotificacao(s.remetente(),s.destinatario(),s.assunto(),s.mensagem()));}catch(RuntimeException ex){resultado=TransporteEmail.Resultado.DESCONHECIDO;}
        String estado=resultado==TransporteEmail.Resultado.ACEITO?(s.modo().equals("REAL")?"ACEITO_SMTP":"SIMULADO"):resultado==TransporteEmail.Resultado.RECUSADO?"FALHA":"DESCONHECIDO";
        String motivo=switch(estado){case "ACEITO_SMTP"->"Servidor SMTP aceitou a mensagem; não comprova entrega ou leitura";case "SIMULADO"->"SIMULAÇÃO concluída; nenhum e-mail real enviado";case "FALHA"->"Transporte recusou a tentativa; nenhum aceite confirmado";default->"Resultado desconhecido; consulte o histórico antes de qualquer reenvio deliberado";};
        tx.executeWithoutResult(status->{if(teste)jdbc.update("UPDATE testes_configuracao SET situacao=?,mensagem=? WHERE id=?",estado,motivo,reserva.id());else jdbc.update("UPDATE notificacoes_recebimento SET situacao=?,motivo=?,concluida_em=CURRENT_TIMESTAMP WHERE id=?",estado,motivo,reserva.id());});
        return reserva.id();
    }
}
