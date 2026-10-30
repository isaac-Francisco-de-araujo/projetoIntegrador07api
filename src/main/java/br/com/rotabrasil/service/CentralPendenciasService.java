package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.DiagnosticoOperacao;
import br.com.rotabrasil.exception.ArquivoInvalidoException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Projeção paginada dos históricos existentes. GET não cria nem resolve pendências. */
@Service @RequiredArgsConstructor
public class CentralPendenciasService {
    private final JdbcTemplate jdbc;private final DiagnosticoOperacaoService diagnosticos;
    public record Resumo(long falhas,long atencao,long emProcessamento,long resolvidos){}
    public record Pagina(int pagina,int tamanho,long total,List<DiagnosticoOperacao> itens,Resumo resumo){}
    private static final String BASE="""
        WITH processos AS (
          SELECT 'PROCESSAMENTO' AS origem,id,criado_em AS data,
            CASE WHEN resolvido_em IS NOT NULL OR situacao IN ('IMPORTADO','REUTILIZADO','CONFERIDA') THEN 'RESOLVIDO'
                 WHEN situacao='EM_PROCESSAMENTO' AND criado_em>DATEADD('MINUTE',-5,CURRENT_TIMESTAMP) THEN 'EM_PROCESSAMENTO'
                 ELSE 'PRECISA_ACAO' END AS estado,
            CASE WHEN situacao LIKE '%DESCONHECIDO%' OR situacao='EM_PROCESSAMENTO' THEN 'ATENCAO'
                 WHEN diagnostico LIKE '%"gravidade":"ERRO"%' OR situacao='INTERROMPIDA' THEN 'ERRO' ELSE 'ATENCAO' END AS gravidade,
            ROW_NUMBER() OVER(PARTITION BY COALESCE(chave_pendencia,CASE WHEN sha256 IS NOT NULL THEN sha256||fase||situacao ELSE id END) ORDER BY criado_em DESC,id DESC) AS ordem
          FROM processamentos
        ), operacoes AS (
          SELECT origem,id,data,estado,gravidade FROM processos WHERE ordem=1
          UNION ALL
          SELECT 'EXPORTACAO',e.id,e.criada_em,
            CASE WHEN e.situacao='CONCLUIDA' THEN 'RESOLVIDO'
                 WHEN e.situacao='EM_PROCESSAMENTO' AND e.execucao_em>DATEADD('MINUTE',-5,CURRENT_TIMESTAMP) THEN 'EM_PROCESSAMENTO'
                 ELSE 'PRECISA_ACAO' END,
            CASE WHEN e.situacao='EM_PROCESSAMENTO' THEN 'ATENCAO'
                 WHEN EXISTS(SELECT 1 FROM exportacao_arquivos a WHERE a.exportacao_id=e.id AND a.codigo IS NOT NULL AND a.codigo<>'ARQUIVOS_PENDENTES' AND a.situacao<>'CONCLUIDO') THEN 'ERRO' ELSE 'ATENCAO' END
          FROM exportacoes e
          UNION ALL
          SELECT 'ENVIO',t.id,t.criada_em,
            CASE WHEN t.situacao IN ('SIMULADO','ACEITO_SMTP') OR (t.situacao='FALHA' AND EXISTS(SELECT 1 FROM tentativas_email s WHERE s.anterior_id=t.id AND s.situacao IN ('SIMULADO','ACEITO_SMTP'))) THEN 'RESOLVIDO' ELSE 'PRECISA_ACAO' END,
            CASE WHEN t.situacao='FALHA' THEN 'ERRO' ELSE 'ATENCAO' END FROM tentativas_email t
          UNION ALL
          SELECT 'NOTIFICACAO',id,criada_em,'PRECISA_ACAO',CASE WHEN situacao='FALHA' THEN 'ERRO' ELSE 'ATENCAO' END
          FROM notificacoes_recebimento WHERE situacao IN ('DESCONHECIDO','FALHA')
        )
        """;
    public Resumo resumo(){
        var totais=jdbc.queryForList(BASE+"SELECT estado,gravidade,COUNT(*) AS total FROM operacoes GROUP BY estado,gravidade");
        long falhas=0,atencao=0,andamento=0,resolvidos=0;
        for(var t:totais){long n=((Number)t.get("TOTAL")).longValue();switch((String)t.get("ESTADO")){
            case "RESOLVIDO" -> resolvidos+=n;case "EM_PROCESSAMENTO" -> andamento+=n;default -> {if("ERRO".equals(t.get("GRAVIDADE")))falhas+=n;else atencao+=n;}
        }}return new Resumo(falhas,atencao,andamento,resolvidos);
    }
    public Pagina listar(String filtro,int pagina,int tamanho){
        if(!Set.of("PRECISA_ACAO","EM_PROCESSAMENTO","RESOLVIDO","TODAS").contains(filtro)||pagina<0||tamanho<1||tamanho>50)
            throw new ArquivoInvalidoException("Filtro ou paginação da Central de Pendências inválidos");
        String where=filtro.equals("TODAS")?"":" WHERE estado=?";
        var args=new ArrayList<Object>();if(!filtro.equals("TODAS"))args.add(filtro);
        long total=jdbc.queryForObject(BASE+"SELECT COUNT(*) FROM operacoes"+where,Long.class,args.toArray());
        args.add(tamanho);args.add((long)pagina*tamanho);
        var referencias=jdbc.queryForList(BASE+"SELECT origem,id FROM operacoes"+where+" ORDER BY data DESC,id DESC LIMIT ? OFFSET ?",args.toArray());
        var itens=referencias.stream().map(r->diagnosticos.consultar((String)r.get("ORIGEM"),(String)r.get("ID"))).toList();
        return new Pagina(pagina,tamanho,total,itens,resumo());
    }
}
