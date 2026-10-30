package br.com.rotabrasil.config;
import br.com.rotabrasil.seguranca.AtorAtual;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.*;
@Configuration
public class IdentificacaoAtorWeb implements WebMvcConfigurer {
    @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(new HandlerInterceptor(){
        @Override public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler){req.setAttribute("atorAuditoria",AtorAtual.nome());return true;}
    });}
}
