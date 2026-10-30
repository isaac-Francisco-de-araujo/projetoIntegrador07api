package br.com.rotabrasil.controller;
import br.com.rotabrasil.seguranca.AtorAtual;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class SessaoController {
    @org.springframework.beans.factory.annotation.Value("${rotabrasil.email.modo:SIMULADO}") private String modoEmail;
    @GetMapping("/api/sessao") public Map<String,Object> sessao(HttpServletRequest request){
        var a=SecurityContextHolder.getContext().getAuthentication();var token=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
        return Map.of("usuario",AtorAtual.nome(),"papeis",a==null||AtorAtual.nome().equals("DESENVOLVIMENTO_LOCAL")?List.of("ROLE_ADMIN"):a.getAuthorities().stream().map(Object::toString).toList(),
                "csrf",token==null?"":token.getToken(),"csrfHeader",token==null?"X-CSRF-TOKEN":token.getHeaderName(),"modoEmail",modoEmail);
    }
}
