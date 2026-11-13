package br.com.rotabrasil.config;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
/** Limite de admissão por instância, sem fila ou repetição automática. */
@Component
public class LimiteProcessamentosFilter extends OncePerRequestFilter {
    private final Semaphore vagas;
    private final int limite;private final java.util.concurrent.atomic.LongAdder recusas=new java.util.concurrent.atomic.LongAdder();
    public LimiteProcessamentosFilter(@Value("${rotabrasil.processamentos.concorrentes:2}") int limite){if(limite<1||limite>8)throw new IllegalStateException("Limite de processamentos deve estar entre 1 e 8");this.limite=limite;vagas=new Semaphore(limite,true);}
    public java.util.Map<String,Object> metricas(){var heap=java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();return java.util.Map.of("limite",limite,"emUso",limite-vagas.availablePermits(),"recusas",recusas.sum(),"heapUsadoBytes",heap.getUsed(),"heapMaximoBytes",heap.getMax());}
    @Override protected boolean shouldNotFilter(HttpServletRequest r){return !((r.getMethod().equals("GET")&&r.getRequestURI().endsWith("/arquivo"))||(r.getMethod().equals("POST")&&(r.getRequestURI().startsWith("/api/pacotes/")||r.getRequestURI().equals("/api/processamento/xml")||r.getRequestURI().endsWith("/conferencias-email")||r.getRequestURI().endsWith("/tentativas-email"))));}
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain c)throws IOException,ServletException {
        if(!vagas.tryAcquire()){recusas.increment();s.setStatus(429);s.setHeader("Retry-After","2");s.setContentType("application/json;charset=UTF-8");s.getWriter().write("{\"mensagem\":\"Capacidade de processamento ocupada; aguarde e repita manualmente a mesma operação\"}");return;}
        try{c.doFilter(r,s);}finally{vagas.release();}
    }
}
