package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.*;
import br.com.rotabrasil.seguranca.AtorAtual;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class FilaTrabalhoService {
    private final JdbcTemplate jdbc;private final CoordenacaoImportacaoService coordenacao;
    private static final String BASE=CentralPendenciasService.BASE.replace("FROM processos WHERE ordem=1","FROM processos p0 WHERE ordem=1 OR EXISTS(SELECT 1 FROM responsabilidades r0 WHERE r0.origem='PROCESSAMENTO' AND r0.referencia=p0.id)")+"""
        , fila AS (
          SELECT o.origem,o.id,o.data,o.estado AS tecnico,o.gravidade,
            COALESCE(p.pacote,pa.nome_arquivo,o.origem||' '||o.id) AS nome,
            r.usuario_id,u.usuario,CASE WHEN u.id IS NULL THEN NULL WHEN u.papel NOT IN ('OPERADOR','ADMIN') THEN FALSE ELSE u.ativo END AS ativo,
            r.versao,COALESCE(r.atualizada_em,o.data) AS atualizada_em,
            CASE WHEN p.situacao='CONFERIDA' AND NOT EXISTS(SELECT 1 FROM processamentos pi WHERE pi.sha256=p.sha256 AND pi.situacao IN ('IMPORTADO','REUTILIZADO'))
                   THEN CASE WHEN r.usuario_id IS NOT NULL THEN 'EM_TRABALHO' ELSE 'A_CONFERIR' END
                 WHEN o.estado='RESOLVIDO' THEN 'ETAPA_CONCLUIDA'
                 WHEN r.usuario_id IS NOT NULL THEN 'EM_TRABALHO'
                 WHEN o.estado='EM_PROCESSAMENTO' THEN 'AGUARDANDO_PROCESSAMENTO'
                 ELSE 'A_CONFERIR' END AS trabalho
          FROM operacoes o
          LEFT JOIN responsabilidades r ON r.origem=o.origem AND r.referencia=o.id
          LEFT JOIN usuarios u ON u.id=r.usuario_id
          LEFT JOIN processamentos p ON o.origem='PROCESSAMENTO' AND p.id=o.id
          LEFT JOIN exportacoes e ON o.origem='EXPORTACAO' AND e.id=o.id
          LEFT JOIN pacotes pa ON pa.id=e.pacote_id
        )
        """;
    public record Item(String origem,String id,OffsetDateTime entrada,OffsetDateTime atualizacao,String nome,String tecnico,String trabalho,String gravidade,Long responsavelId,String responsavel,Boolean responsavelAtivo,long versao){}
    public record Pagina(int pagina,int tamanho,long total,List<Item> itens,OffsetDateTime verificadoEm){}
    public Pagina listar(String escopo,String situacao,String responsavel,LocalDate inicio,LocalDate fim,String ordem,int pagina,int tamanho){
        if(!Set.of("TODOS","MEUS","SEM_RESPONSAVEL").contains(escopo)||!Set.of("TODAS","A_CONFERIR","EM_TRABALHO","ETAPA_CONCLUIDA","AGUARDANDO_PROCESSAMENTO","COM_PENDENCIAS").contains(situacao)||!Set.of("ENTRADA","ATUALIZACAO").contains(ordem)||pagina<0||pagina>1000000||tamanho<1||tamanho>50||responsavel.length()>100||inicio!=null&&fim!=null&&inicio.isAfter(fim))throw new ArquivoInvalidoException("Filtros da fila inválidos");
        String where=" WHERE 1=1";var args=new ArrayList<Object>();
        if(escopo.equals("MEUS")){where+=" AND usuario=?";args.add(AtorAtual.nome());}
        if(escopo.equals("SEM_RESPONSAVEL"))where+=" AND usuario_id IS NULL";
        if(!responsavel.isBlank()){where+=" AND usuario=?";args.add(responsavel);}
        if(situacao.equals("COM_PENDENCIAS"))where+=" AND tecnico='PRECISA_ACAO'";
        else if(!situacao.equals("TODAS")){where+=" AND trabalho=?";args.add(situacao);}
        if(inicio!=null){where+=" AND data>=?";args.add(inicio.atStartOfDay().atOffset(ZoneOffset.UTC));}
        if(fim!=null){where+=" AND data<?";args.add(fim.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC));}
        long total=jdbc.queryForObject(BASE+"SELECT COUNT(*) FROM fila"+where,Long.class,args.toArray());
        args.add(tamanho);args.add((long)pagina*tamanho);
        var itens=jdbc.query(BASE+"SELECT * FROM fila"+where+" ORDER BY "+(ordem.equals("ENTRADA")?"data":"atualizada_em")+" DESC,origem,id DESC LIMIT ? OFFSET ?",(r,n)->new Item(r.getString("origem"),r.getString("id"),r.getObject("data",OffsetDateTime.class),r.getObject("atualizada_em",OffsetDateTime.class),r.getString("nome"),r.getString("tecnico"),r.getString("trabalho"),r.getString("gravidade"),r.getObject("usuario_id",Long.class),r.getString("usuario"),r.getObject("ativo",Boolean.class),r.getLong("versao")),args.toArray());
        return new Pagina(pagina,tamanho,total,itens,OffsetDateTime.now(ZoneOffset.UTC));
    }
    public List<Map<String,Object>> responsaveis(){return jdbc.queryForList("SELECT id,usuario,ativo FROM usuarios WHERE papel IN ('OPERADOR','ADMIN') ORDER BY usuario,id");}
    public List<Map<String,Object>> historico(String origem,String id){validarReferencia(origem,id);return jdbc.queryForList("SELECT e.acao,e.ator,e.criado_em,a.usuario AS anterior,n.usuario AS novo FROM eventos_responsabilidade e LEFT JOIN usuarios a ON a.id=e.anterior_usuario_id LEFT JOIN usuarios n ON n.id=e.novo_usuario_id WHERE e.origem=? AND e.referencia=? ORDER BY e.id DESC LIMIT 50",origem,id);}
    private void validarReferencia(String origem,String id){if(!Set.of("PROCESSAMENTO","EXPORTACAO","ENVIO","NOTIFICACAO").contains(origem)||!id.matches("[0-9a-f-]{36}"))throw new ArquivoInvalidoException("Referência inválida");}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void atribuir(String origem,String id,String acao,long versao,Long destino,boolean confirmado){
        validarReferencia(origem,id);coordenacao.bloquear();
        var fonte=jdbc.queryForList(BASE+"SELECT tecnico AS estado FROM fila WHERE origem=? AND id=?",origem,id);
        if(fonte.isEmpty())throw new RecursoNaoEncontradoException("Item não encontrado na fila atual; recarregue");
        if("EM_PROCESSAMENTO".equals(fonte.getFirst().get("ESTADO")))throw new ConflitoRecursoException("Ação técnica em andamento; aguarde e consulte o diagnóstico antes de mudar a responsabilidade");
        // Resultados incertos não autorizam liberação automática nem repetição de efeito externo.
        String tabela=switch(origem){case "PROCESSAMENTO"->"processamentos";case "EXPORTACAO"->"exportacoes";case "ENVIO"->"tentativas_email";default->"notificacoes_recebimento";};
        String tecnico=jdbc.queryForObject("SELECT situacao FROM "+tabela+" WHERE id=?",String.class,id);
        if(Set.of("EM_PROCESSAMENTO","EM_ENVIO","ENVIANDO","DESCONHECIDO","RESULTADO_DESCONHECIDO").contains(tecnico))throw new ConflitoRecursoException("Ação crítica em andamento ou resultado incerto; responsabilidade preservada até diagnóstico");
        var rows=jdbc.queryForList("SELECT usuario_id,versao FROM responsabilidades WHERE origem=? AND referencia=?",origem,id);
        Long anterior=rows.isEmpty()?null:(Long)rows.getFirst().get("USUARIO_ID");
        long atual=rows.isEmpty()?0:((Number)rows.getFirst().get("VERSAO")).longValue();
        if(atual!=versao)throw new ConflitoRecursoException("Responsabilidade alterada por outro operador; recarregue a fila");
        var ator=jdbc.queryForList("SELECT id FROM usuarios WHERE usuario=? AND ativo=TRUE AND papel IN ('OPERADOR','ADMIN')",AtorAtual.nome());
        if(ator.isEmpty())throw new org.springframework.security.access.AccessDeniedException("Exige operador identificado");
        long atorId=((Number)ator.getFirst().get("ID")).longValue();Long novo;
        switch(acao){
            case "ASSUMIR" -> {if(anterior!=null)throw new ConflitoRecursoException("Item já possui responsável");novo=atorId;}
            case "LIBERAR" -> {if(!Objects.equals(anterior,atorId))throw new org.springframework.security.access.AccessDeniedException("Somente o responsável pode liberar");novo=null;}
            case "TRANSFERIR" -> {
                var auth=SecurityContextHolder.getContext().getAuthentication();
                if(auth==null||auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))throw new org.springframework.security.access.AccessDeniedException("Transferência exige ADMIN");
                if(!confirmado)throw new ArquivoInvalidoException("Confirme a transferência administrativa");
                novo=destino;if(novo!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE id=? AND ativo=TRUE AND papel IN ('OPERADOR','ADMIN')",Integer.class,novo)!=1)throw new ArquivoInvalidoException("Destino deve ser operador ativo");
            }
            default -> throw new ArquivoInvalidoException("Ação de responsabilidade inválida");
        }
        if(Objects.equals(anterior,novo))throw new ConflitoRecursoException("Responsabilidade não mudou");
        if(rows.isEmpty())jdbc.update("INSERT INTO responsabilidades VALUES(?,?,?,1,CURRENT_TIMESTAMP)",origem,id,novo);
        else jdbc.update("UPDATE responsabilidades SET usuario_id=?,versao=versao+1,atualizada_em=CURRENT_TIMESTAMP WHERE origem=? AND referencia=?",novo,origem,id);
        jdbc.update("INSERT INTO eventos_responsabilidade(origem,referencia,anterior_usuario_id,novo_usuario_id,ator,acao,criado_em) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",origem,id,anterior,novo,AtorAtual.nome(),acao);
    }
}
