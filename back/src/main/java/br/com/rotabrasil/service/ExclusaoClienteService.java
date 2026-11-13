package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.ExclusaoClienteConferencia;
import br.com.rotabrasil.exception.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Exclusão explícita do agregado; referências de outros clientes não são removidas. */
@Service @RequiredArgsConstructor
public class ExclusaoClienteService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CoordenacaoImportacaoService coordenacao;
    private final LimpezaArquivosClienteService arquivos;
    private static final String VIAGENS = "SELECT id FROM viagens WHERE cliente_id=?";
    private static final String ANEXOS = "SELECT anexo_id FROM anexo_viagens WHERE viagem_id IN ("+VIAGENS+")";

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ExclusaoClienteConferencia conferir(Long id) {
        coordenacao.bloquear();
        return plano(id).conferencia();
    }

    private record Plano(ExclusaoClienteConferencia conferencia, List<Map<String,Object>> documentos,
            List<Map<String,Object>> exclusivos) {}

    private Plano plano(Long id) {
        var clientes=jdbc.queryForList("SELECT * FROM clientes WHERE id=?",id);
        if(clientes.isEmpty())throw new RecursoNaoEncontradoException("Cliente não encontrado: "+id);
        var viagens=jdbc.queryForList("SELECT * FROM viagens WHERE cliente_id=? ORDER BY id",id);
        var docs=jdbc.queryForList("SELECT * FROM documentos WHERE viagem_id IN ("+VIAGENS+") ORDER BY id",id);
        var anexos=jdbc.queryForList("SELECT * FROM anexos WHERE id IN ("+ANEXOS+") ORDER BY id",id);
        var vinculos=jdbc.queryForList("SELECT * FROM anexo_viagens WHERE anexo_id IN ("+ANEXOS+") ORDER BY anexo_id,viagem_id",id);
        var pacotes=jdbc.queryForList("SELECT * FROM pacote_documentos WHERE documento_id IN (SELECT id FROM documentos WHERE viagem_id IN ("+VIAGENS+")) ORDER BY pacote_id,documento_id",id);
        var envios=jdbc.queryForList("SELECT * FROM envios WHERE cliente_id=? OR viagem_id IN ("+VIAGENS+") ORDER BY id",id,id);
        var conferencias=jdbc.queryForList("SELECT * FROM conferencias_email WHERE viagem_id IN ("+VIAGENS+") ORDER BY id",id);
        var tentativas=jdbc.queryForList("SELECT * FROM tentativas_email WHERE viagem_id IN ("+VIAGENS+") ORDER BY id",id);
        var idsViagens=new HashSet<>(viagens.stream().map(v->v.get("ID")).toList());
        var compartilhados=new HashSet<>(vinculos.stream().filter(v->!idsViagens.contains(v.get("VIAGEM_ID"))).map(v->v.get("ANEXO_ID")).toList());
        var exclusivos=anexos.stream().filter(a->!compartilhados.contains(a.get("ID"))).toList();
        int desconhecidas=(int)tentativas.stream().filter(t->"DESCONHECIDO".equals(t.get("SITUACAO"))).count();
        try {
            String hash=ConteudoXml.sha256(json.writeValueAsBytes(List.of(clientes,viagens,docs,anexos,vinculos,pacotes,envios,conferencias,tentativas)));
            return new Plano(new ExclusaoClienteConferencia(id,(String)clientes.getFirst().get("NOME"),viagens.size(),docs.size(),
                    exclusivos.size(),compartilhados.size(),envios.size(),conferencias.size(),tentativas.size(),desconhecidas,hash),docs,exclusivos);
        }catch(JsonProcessingException ex){throw new IllegalStateException("Não foi possível conferir a exclusão do cliente");}
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void excluir(Long id, boolean confirmado, String hash) {
        coordenacao.bloquear();
        var plano=plano(id); var c=plano.conferencia();
        boolean vinculado=c.viagens()>0||c.envios()>0;
        if(vinculado&&(!confirmado||!Objects.equals(hash,c.conferenciaSha256())))
            throw new ConflitoRecursoException("Confira e confirme a exclusão do cliente com suas viagens e documentos; se os vínculos mudaram, faça nova conferência");
        if(c.tentativasDesconhecidas()>0)
            throw new ConflitoRecursoException("Cliente possui tentativa de envio com resultado desconhecido; revise o envio antes de excluir para preservar seu histórico");
        // Não desligar a integridade referencial nem apagar referências externas de reenvio.
        Integer externas=jdbc.queryForObject("SELECT COUNT(*) FROM tentativas_email WHERE viagem_id NOT IN ("+VIAGENS+") AND anterior_id IN (SELECT id FROM tentativas_email WHERE viagem_id IN ("+VIAGENS+"))",Integer.class,id,id);
        Integer conferenciasExternas=jdbc.queryForObject("SELECT COUNT(*) FROM conferencias_email WHERE viagem_id NOT IN ("+VIAGENS+") AND anterior_id IN (SELECT id FROM tentativas_email WHERE viagem_id IN ("+VIAGENS+"))",Integer.class,id,id);
        if(externas>0||conferenciasExternas>0)throw new ConflitoRecursoException("Histórico de envio referenciado por outra viagem; revisão necessária antes de excluir");
        var removidos=new ArrayList<>(plano.documentos()); removidos.addAll(plano.exclusivos());
        arquivos.agendar(removidos);
        jdbc.update("UPDATE conferencias_email SET anterior_id=NULL WHERE viagem_id IN ("+VIAGENS+")",id);
        jdbc.update("UPDATE tentativas_email SET anterior_id=NULL WHERE viagem_id IN ("+VIAGENS+")",id);
        jdbc.update("DELETE FROM tentativas_email WHERE viagem_id IN ("+VIAGENS+")",id);
        jdbc.update("DELETE FROM conferencias_email WHERE viagem_id IN ("+VIAGENS+")",id);
        jdbc.update("DELETE FROM envios WHERE cliente_id=? OR viagem_id IN ("+VIAGENS+")",id,id);
        jdbc.update("DELETE FROM pacote_documentos WHERE documento_id IN (SELECT id FROM documentos WHERE viagem_id IN ("+VIAGENS+"))",id);
        jdbc.update("DELETE FROM anexo_viagens WHERE viagem_id IN ("+VIAGENS+")",id);
        for(var anexo:plano.exclusivos())jdbc.update("DELETE FROM anexos WHERE id=?",anexo.get("ID"));
        jdbc.update("DELETE FROM documentos WHERE viagem_id IN ("+VIAGENS+")",id);
        jdbc.update("DELETE FROM viagens WHERE cliente_id=?",id);
        jdbc.update("DELETE FROM clientes WHERE id=?",id);
        // Pacotes/processamentos/auditoria continuam como histórico; o ZIP não restaura o agregado.
    }
}
