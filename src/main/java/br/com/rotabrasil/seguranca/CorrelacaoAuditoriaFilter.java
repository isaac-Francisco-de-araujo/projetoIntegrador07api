package br.com.rotabrasil.seguranca;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import java.io.IOException;
import java.util.*;

@Component @Order(Ordered.HIGHEST_PRECEDENCE+20)
public class CorrelacaoAuditoriaFilter extends OncePerRequestFilter {
    private static final Logger LOG=LoggerFactory.getLogger(CorrelacaoAuditoriaFilter.class);
    private final JdbcTemplate jdbc;
    public CorrelacaoAuditoriaFilter(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        long inicio=System.nanoTime();String id=UUID.randomUUID().toString();MDC.put("operacaoId",id);res.setHeader("X-Request-Id",id);
        try{chain.doFilter(req,res);}finally{
            try{
                Object rota=req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                if(rota!=null&&req.getRequestURI().startsWith(req.getContextPath()+"/api/"))LOG.info("requisicao correlacao={} metodo={} rota={} status={} duracaoMs={}",id,req.getMethod(),rota,res.getStatus(),(System.nanoTime()-inicio)/1_000_000);
                if(rota!=null&&req.getRequestURI().startsWith(req.getContextPath()+"/api/")&&!List.of("GET","HEAD","OPTIONS").contains(req.getMethod())){
                    Object referencia=req.getAttribute("auditoriaReferencia");if(referencia==null)referencia=req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                    String ref=null;
                    if(referencia instanceof Map<?,?> valores){var seguros=new TreeMap<String,String>();valores.forEach((k,v)->seguros.put(k.toString(),v.toString().matches("[0-9a-f-]{1,36}")?v.toString():"INVALIDO"));ref=seguros.toString();}
                    if(ref!=null&&ref.length()>255)ref=ref.substring(0,255);
                    jdbc.update("INSERT INTO auditoria(ator,acao,recurso,referencia,status_http,correlacao,criada_em) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                            Objects.toString(req.getAttribute("atorAuditoria"),"NAO_AUTENTICADO"),req.getMethod(),rota.toString(),ref,res.getStatus(),id);
                }
            }catch(RuntimeException ex){LOG.warn("Auditoria indisponível; correlacao={}",id);}finally{MDC.remove("operacaoId");}
        }
    }
}
