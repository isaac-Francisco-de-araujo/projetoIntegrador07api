package br.com.rotabrasil.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletResponse;

/** Entrada do mesmo servidor, usando o processamento de login e CSRF do Spring Security. */
@Controller
public class LoginController {
    @Value("${rotabrasil.seguranca.ativa:false}") private boolean segurancaAtiva;
    @GetMapping(value="/login", produces=MediaType.TEXT_HTML_VALUE)
    @ResponseBody public String login(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        response.setHeader("Cache-Control", "no-store");
        String contexto=HtmlUtils.htmlEscape(request.getContextPath());
        if (segurancaAtiva && request.getUserPrincipal()!=null) {
            response.sendRedirect(request.getContextPath()+"/painel");
            return "";
        }
        if (!segurancaAtiva) return """
            <!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Acesso local · RotaBrasil</title>
            <link rel="stylesheet" href="%s/acesso.css"><body><main><h1>RotaBrasil</h1><p>Ambiente de desenvolvimento local, sem login individual, para dados isolados.</p><p>O uso compartilhado exige o ambiente operacional com autenticação.</p><a class="botao" href="%s/painel">Abrir painel local</a></main></body></html>
            """.formatted(contexto,contexto);
        var token=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
        String csrf=token==null?"":"<input type=\"hidden\" name=\""+HtmlUtils.htmlEscape(token.getParameterName())+"\" value=\""+HtmlUtils.htmlEscape(token.getToken())+"\">";
        String aviso=request.getParameterMap().containsKey("error")?"Credenciais inválidas. Verifique seu acesso.":request.getParameterMap().containsKey("logout")?"Sessão encerrada.":request.getParameterMap().containsKey("expirada")?"Sua sessão expirou. Entre novamente para continuar.":"Use sua conta individual. Procure o administrador se precisar redefinir o acesso.";
        return """
            <!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Entrar · RotaBrasil</title>
            <link rel="stylesheet" href="%s/acesso.css"><script src="%s/acesso.js" defer></script></head><body><main aria-labelledby="titulo"><div class="marca"><img src="%s/acesso-logo.svg" width="48" height="48" alt=""><span>RotaBrasil</span></div><h1 id="titulo">Entrar na operação</h1><p role="status">%s</p><form data-login action="%s/login" method="post">%s<label>Usuário<input name="username" autocomplete="username" required maxlength="100" autofocus></label><label>Senha<input name="password" type="password" autocomplete="current-password" required></label><button type="submit">Entrar</button></form><p class="rodape"><a href="%s/">Voltar à apresentação</a></p></main></body></html>
            """.formatted(contexto,contexto,contexto,aviso,contexto,csrf,contexto);
    }
}
