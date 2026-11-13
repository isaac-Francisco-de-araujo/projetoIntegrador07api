package br.com.rotabrasil;
import br.com.rotabrasil.config.LimiteProcessamentosFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class LimiteProcessamentosTest {
    @Test void recusaExcessoSemExecutarEDevolveCapacidadeAposFalha()throws Exception{
        var filtro=new LimiteProcessamentosFilter(1);var entrou=new CountDownLatch(1);var sair=new CountDownLatch(1);
        try(var pool=Executors.newSingleThreadExecutor()){
            var primeiro=pool.submit(()->{try{filtro.doFilter(new MockHttpServletRequest("POST","/api/pacotes/inspecao"),new MockHttpServletResponse(),(r,s)->{entrou.countDown();try{sair.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});}catch(Exception ex){throw new RuntimeException(ex);}});
            assertThat(entrou.await(5,TimeUnit.SECONDS)).isTrue();var resposta=new MockHttpServletResponse();
            filtro.doFilter(new MockHttpServletRequest("POST","/api/pacotes/inspecao"),resposta,(r,s)->{throw new AssertionError("Não deveria admitir");});assertThat(resposta.getStatus()).isEqualTo(429);
            sair.countDown();primeiro.get(5,TimeUnit.SECONDS);var aceita=new MockHttpServletResponse();filtro.doFilter(new MockHttpServletRequest("POST","/api/pacotes/inspecao"),aceita,(r,s)->s.getWriter().write("ok"));assertThat(aceita.getContentAsString()).isEqualTo("ok");
        }finally{sair.countDown();}
    }
}
