package br.com.rotabrasil.service.email;

import br.com.rotabrasil.dto.EnvioManualDtos.*;
import br.com.rotabrasil.dto.PreviaEmailRequest;
import br.com.rotabrasil.exception.*;
import br.com.rotabrasil.service.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Commits curtos no banco; nunca mantém a trava/transação durante o transporte. */
@Service
public class EnvioManualService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PreparacaoEnvioService preparacao;
    private final EmailService email;
    private final ViagemService viagens;
    private final CoordenacaoImportacaoService coordenacao;
    private final TransporteEmail transporte;
    private final TransactionTemplate tx;
    private final String modo;
    @Value("${rotabrasil.email.smtp.remetente:}") private String remetente="";

    public EnvioManualService(JdbcTemplate jdbc, ObjectMapper json, PreparacaoEnvioService preparacao,
            EmailService email, ViagemService viagens, CoordenacaoImportacaoService coordenacao,
            TransporteEmail transporte, PlatformTransactionManager manager,
            @Value("${rotabrasil.email.modo:SIMULADO}") String modo) {
        if (!Set.of("SIMULADO","REAL").contains(modo))throw new IllegalStateException("Modo de transporte inválido");
        this.modo=modo;
        this.jdbc=jdbc; this.json=json; this.preparacao=preparacao; this.email=email;
        this.viagens=viagens; this.coordenacao=coordenacao; this.transporte=transporte;
        tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public Conferencia conferir(Long viagemId, Conferir pedido) {
        return tx.execute(status -> {
            coordenacao.bloquear();
            conferirAnterior(viagemId, pedido.anteriorId());
            var mensagem=preparacao.preparar(viagemId,pedido.selecao());
            var snapshot=snapshot(mensagem,pedido.selecao());
            String id=UUID.randomUUID().toString();
            var agora=OffsetDateTime.now(ZoneOffset.UTC); var expira=agora.plusMinutes(30);
            jdbc.update("INSERT INTO conferencias_email(id,viagem_id,selecao,snapshot,anterior_id,criada_em,expira_em,ator,modo) VALUES(?,?,?,?,?,?,?,?,?)",
                    id,viagemId,escrever(pedido.selecao()),escrever(snapshot),pedido.anteriorId(),agora,expira,br.com.rotabrasil.seguranca.AtorAtual.nome(),modo);
            return new Conferencia(id,modo,expira,pedido.anteriorId(),snapshot);
        });
    }

    private void conferirAnterior(Long viagemId, String anteriorId) {
        var ultimas=jdbc.queryForList("SELECT id FROM tentativas_email WHERE viagem_id=? ORDER BY criada_em DESC, id DESC LIMIT 1",String.class,viagemId);
        if (ultimas.isEmpty() ? anteriorId!=null : !Objects.equals(ultimas.getFirst(),anteriorId))
            throw new ConflitoRecursoException("Há tentativa anterior ou referência de reenvio inválida; consulte o histórico e confirme o reenvio");
    }

    private Snapshot snapshot(MensagemPreparada mensagem, PreviaEmailRequest pedido) {
        var previa=email.prepararPrevia(mensagem.viagemId(),pedido);
        var arquivos=mensagem.arquivos().stream().map(a -> {
            if (a.origem().equals("DOCUMENTO")) {
                var d=previa.documentos().stream().filter(v->v.id().equals(a.id())).findFirst().orElseThrow();
                return new Arquivo(a.origem(),a.id(),a.nome(),d.tipo(),null,List.of(mensagem.viagemId()),a.sha256(),a.tamanho());
            }
            var d=previa.anexos().stream().filter(v->v.id().equals(a.id())).findFirst().orElseThrow();
            return new Arquivo(a.origem(),a.id(),a.nome(),d.tipo(),d.finalidade().name(),d.viagemIds(),a.sha256(),a.tamanho());
        }).toList();
        return new Snapshot(2,mensagem.viagemId(),viagens.buscarViagem(mensagem.viagemId()).getNumero(),mensagem.destinatario(),arquivos,remetente.isBlank()?null:remetente,mensagem.assunto(),mensagem.texto());
    }

    private record Reserva(Tentativa tentativa, MensagemPreparada mensagem) {}

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NEVER)
    public Tentativa confirmar(Long viagemId, Confirmar pedido) {
        if (!pedido.confirmado()) throw new ConflitoRecursoException("Confirme a seleção conferida");
        Reserva reserva=tx.execute(status -> {
            coordenacao.bloquear();
            var existentes=jdbc.query("SELECT * FROM tentativas_email WHERE conferencia_id=? AND viagem_id=?",this::linha,pedido.conferenciaId(),viagemId);
            // Repetição técnica retorna o retrato original, mesmo se arquivos/cadastros mudaram.
            if (!existentes.isEmpty()) return new Reserva(existentes.getFirst(),null);
            var registros=jdbc.queryForList("SELECT * FROM conferencias_email WHERE id=? AND viagem_id=?",pedido.conferenciaId(),viagemId);
            if (registros.isEmpty()) throw new RecursoNaoEncontradoException("Conferência não encontrada");
            var registro=registros.getFirst();
            if(!Objects.equals(registro.get("ATOR"),br.com.rotabrasil.seguranca.AtorAtual.nome())||!Objects.equals(registro.get("MODO"),modo))throw new ConflitoRecursoException("Operador ou modo mudou; gere nova conferência");
            var expira=(OffsetDateTime)registro.get("EXPIRA_EM");
            if (expira.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) throw new ConflitoRecursoException("Conferência expirada; confira novamente");
            String anterior=(String)registro.get("ANTERIOR_ID");
            conferirAnterior(viagemId,anterior);
            var selecao=ler((String)registro.get("SELECAO"),PreviaEmailRequest.class);
            var mensagem=preparacao.preparar(viagemId,selecao);
            String atual=escrever(snapshot(mensagem,selecao));
            if (!atual.equals(registro.get("SNAPSHOT"))) throw new ConflitoRecursoException("Seleção ou metadados alterados; gere nova conferência");
            String id=UUID.randomUUID().toString(); var agora=OffsetDateTime.now(ZoneOffset.UTC);
            // Conservador desde o commit: queda do processo não pode autorizar repetição automática.
            jdbc.update("INSERT INTO tentativas_email(id,conferencia_id,viagem_id,snapshot,anterior_id,modo,situacao,motivo,criada_em,ator,correlacao) VALUES(?,?,?,?,?,?,'DESCONHECIDO',?,?,?,?)",
                    id,pedido.conferenciaId(),viagemId,atual,anterior,modo,"Resultado ainda não confirmado; não repetir automaticamente",agora,br.com.rotabrasil.seguranca.AtorAtual.nome(),org.slf4j.MDC.get("operacaoId"));
            return new Reserva(buscar(viagemId,id),mensagem);
        });
        if (reserva.mensagem()==null) return reserva.tentativa();
        String situacao; String motivo;
        try {
            var resultado=transporte.enviar(reserva.mensagem());
            if (resultado==TransporteEmail.Resultado.ACEITO) {
                situacao=modo.equals("SIMULADO")?"SIMULADO":"ACEITO_SMTP"; motivo=modo.equals("SIMULADO")?"SIMULAÇÃO concluída; nenhum e-mail real enviado":"Servidor SMTP aceitou a mensagem; isso não comprova entrega ou leitura";
            } else if (resultado==TransporteEmail.Resultado.RECUSADO) {
                situacao="FALHA"; motivo="Transporte recusou a tentativa; nenhum aceite confirmado";
            } else {
                situacao="DESCONHECIDO"; motivo="Resultado desconhecido; revisão manual necessária, sem repetição automática";
            }
        } catch (RuntimeException ex) {
            // Nunca persistir/logar mensagem, causa ou credenciais de uma exceção de transporte.
            situacao="DESCONHECIDO"; motivo="Transporte interrompido; resultado desconhecido, revise antes de reenviar";
        }
        final String estado=situacao, resultadoTratado=motivo;
        return tx.execute(status -> {
            jdbc.update("UPDATE tentativas_email SET situacao=?,motivo=?,concluida_em=? WHERE id=?",
                    estado,resultadoTratado,OffsetDateTime.now(ZoneOffset.UTC),reserva.tentativa().id());
            return buscar(viagemId,reserva.tentativa().id());
        });
    }

    public List<Tentativa> listar(Long viagemId) {
        viagens.buscarViagem(viagemId);
        return jdbc.query("SELECT * FROM tentativas_email WHERE viagem_id=? ORDER BY criada_em,id",this::linha,viagemId);
    }
    public Tentativa buscar(Long viagemId,String id) {
        return jdbc.query("SELECT * FROM tentativas_email WHERE viagem_id=? AND id=?",this::linha,viagemId,id)
                .stream().findFirst().orElseThrow(()->new RecursoNaoEncontradoException("Tentativa não encontrada"));
    }
    private Tentativa linha(ResultSet rs,int n) throws SQLException {
        return new Tentativa(rs.getString("id"),rs.getLong("viagem_id"),rs.getString("modo"),rs.getString("situacao"),
                rs.getString("motivo"),rs.getObject("criada_em",OffsetDateTime.class),rs.getObject("concluida_em",OffsetDateTime.class),
                rs.getString("anterior_id"),ler(rs.getString("snapshot"),Snapshot.class),rs.getString("ator"));
    }
    private String escrever(Object valor) {
        try { return json.writeValueAsString(valor); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Não foi possível preparar o retrato da tentativa"); }
    }
    private <T> T ler(String valor,Class<T> tipo) {
        try { return tipo==Snapshot.class?json.readerFor(tipo).without(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(valor):json.readValue(valor,tipo); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Retrato da tentativa indisponível"); }
    }
}
