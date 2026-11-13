package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.PacoteDtos;
import br.com.rotabrasil.exception.*;
import br.com.rotabrasil.seguranca.AtorAtual;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

/** Metadados persistentes separados do histórico de e-mail. Não retém ZIP ou bytes. */
@Service @RequiredArgsConstructor
public class HistoricoProcessamentoService {
    private final PacoteService pacotes;private final ConfirmacaoPacoteService confirmacao;
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    private void anterior(String id){if(id!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM processamentos WHERE id=?",Integer.class,id)!=1)throw new RecursoNaoEncontradoException("Processamento anterior não encontrado");}
    public PacoteDtos.Inspecao inspecionar(MultipartFile arquivo,String anterior){
        anterior(anterior);
        try{var r=pacotes.inspecionar(arquivo);registrar(r,anterior,"CONFERENCIA",r.situacao(),null);if(r.conteudoValido()&&!r.interrompida())resolverAnteriores(anterior,r.operacaoId(),false,r.pendentes()==0);return r;}
        catch(DiagnosticoPacoteException ex){registrar(ex.diagnostico(),anterior,"CONFERENCIA",ex.diagnostico().situacao(),null);throw ex;}
    }
    public PacoteDtos.Resultado confirmar(MultipartFile arquivo,PacoteDtos.Confirmacao pedido,String anterior){
        anterior(anterior);PacoteDtos.Inspecao previa;
        try{previa=pacotes.inspecionar(arquivo);}catch(DiagnosticoPacoteException ex){registrar(ex.diagnostico(),anterior,"CONFIRMACAO",ex.diagnostico().situacao(),null);throw ex;}
        registrar(previa,anterior,"CONFIRMACAO","EM_PROCESSAMENTO",null);
        try{var resultado=confirmacao.confirmar(arquivo,pedido);
            jdbc.update("UPDATE processamentos SET situacao=?,resultado=? WHERE id=?",resultado.reutilizado()?"REUTILIZADO":"IMPORTADO",escrever(resultado),previa.operacaoId());resolverAnteriores(anterior,previa.operacaoId(),true,true);return new PacoteDtos.Resultado(resultado.pacoteId(),resultado.reutilizado(),resultado.documentos(),resultado.anexos(),previa.operacaoId());}
        catch(DiagnosticoPacoteException ex){
            var d=ex.diagnostico();var identificado=new PacoteDtos.Inspecao(previa.operacaoId(),d.pacote(),d.sha256(),d.arquivos(),d.diagnosticos(),d.situacao(),d.conteudoValido(),d.interrompida(),d.validos(),d.invalidos(),d.pendentes(),d.naoExaminados(),d.localizacao(),d.temporario());
            jdbc.update("UPDATE processamentos SET situacao=?,diagnostico=?,chave_pendencia=? WHERE id=?",d.situacao(),escrever(identificado),chave(identificado,"CONFIRMACAO"),previa.operacaoId());throw new DiagnosticoPacoteException(identificado,ex.status());
        }
    }
    private void registrar(PacoteDtos.Inspecao r,String anterior,String fase,String situacao,Object resultado){
        String diagnostico=escrever(r),saida=resultado==null?null:escrever(resultado);
        if(diagnostico.length()>1024*1024||(saida!=null&&saida.length()>1024*1024))throw new IllegalStateException("Metadados excedem o limite de processamento");
        jdbc.update("INSERT INTO processamentos(id,anterior_id,pacote,sha256,fase,situacao,ator,criado_em,diagnostico,resultado,correlacao,chave_pendencia) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?,?,?,?)",
                r.operacaoId(),anterior,Objects.toString(r.pacote(),"(pacote não identificado)"),r.sha256(),fase,situacao,AtorAtual.nome(),diagnostico,saida,org.slf4j.MDC.get("operacaoId"),chave(r,fase));
    }
    private String chave(PacoteDtos.Inspecao r,String fase){
        String componentes=r.diagnosticos().stream().filter(d->!d.gravidade().equals("INFO")).map(d->d.codigo()+":"+Objects.toString(d.caminhoInterno(),"PACOTE")).sorted().collect(java.util.stream.Collectors.joining("|"));
        return ConteudoXml.sha256((fase+":"+Objects.toString(r.sha256(),r.operacaoId())+":"+componentes).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    /** Só a cadeia explicitamente vinculada fornece evidência. Desconhecido nunca é encerrado por outra operação. */
    private void resolverAnteriores(String anterior,String evidencia,boolean importado,boolean semPendencias){
        var visitados=new HashSet<String>();String atual=anterior;
        while(atual!=null&&visitados.size()<100&&visitados.add(atual)){
            var linhas=jdbc.queryForList("SELECT * FROM processamentos WHERE id=?",atual);if(linhas.isEmpty())break;var r=linhas.getFirst();
            String situacao=(String)r.get("SITUACAO");JsonNode d=ler((String)r.get("DIAGNOSTICO"));
            boolean incerteza=situacao.contains("DESCONHECIDO")||situacao.equals("EM_PROCESSAMENTO");
            boolean erroConteudo=d.path("invalidos").asInt()>0||d.path("interrompida").asBoolean();
            boolean resolve=!incerteza&&(importado||("CONFERENCIA".equals(r.get("FASE"))&&(semPendencias||erroConteudo)));
            if(resolve){
                String criterio=importado?"Importação confirmada em operação vinculada.":"Nova conferência vinculada válida; pendências de associação da nova seleção continuam independentes.";
                jdbc.update("UPDATE processamentos SET resolvido_por=?,resolvido_em=CURRENT_TIMESTAMP,criterio_resolucao=? WHERE resolvido_em IS NULL AND (id=? OR (chave_pendencia IS NOT NULL AND chave_pendencia=?)) AND situacao NOT LIKE '%DESCONHECIDO%' AND situacao<>'EM_PROCESSAMENTO'",evidencia,criterio,atual,r.get("CHAVE_PENDENCIA"));
            }
            atual=(String)r.get("ANTERIOR_ID");
        }
    }
    public List<Map<String,Object>> listar(int pagina,String situacao){
        String base="SELECT id,anterior_id,pacote,fase,situacao,ator,criado_em FROM processamentos";
        return situacao==null||situacao.isBlank()?jdbc.queryForList(base+" ORDER BY criado_em DESC,id DESC LIMIT 50 OFFSET ?",(long)pagina*50):
                jdbc.queryForList(base+" WHERE situacao=? ORDER BY criado_em DESC,id DESC LIMIT 50 OFFSET ?",situacao,(long)pagina*50);
    }
    public Map<String,Object> buscar(String id){
        var lista=jdbc.queryForList("SELECT * FROM processamentos WHERE id=?",id);
        if(lista.isEmpty())throw new RecursoNaoEncontradoException("Processamento não encontrado");
        var r=new LinkedHashMap<>(lista.getFirst());r.put("DIAGNOSTICO",ler((String)r.get("DIAGNOSTICO")));
        r.put("RESULTADO",r.get("RESULTADO")==null?null:ler((String)r.get("RESULTADO")));return r;
    }
    private String escrever(Object obj){try{return json.writeValueAsString(obj);}catch(Exception ex){throw new IllegalStateException("Falha ao registrar metadados do processamento");}}
    private JsonNode ler(String valor){try{return json.readTree(valor);}catch(Exception ex){throw new IllegalStateException("Diagnóstico histórico indisponível");}}
}
