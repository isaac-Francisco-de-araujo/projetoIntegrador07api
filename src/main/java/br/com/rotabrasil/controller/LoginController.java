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
        if (segurancaAtiva && request.getUserPrincipal()!=null) {
            response.sendRedirect(request.getContextPath()+"/");
            return "";
        }
        if (!segurancaAtiva) return """
            <!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Acesso local · RotaBrasil</title>
            <body style="font:16px system-ui;max-width:34rem;margin:10vh auto;padding:24px"><h1>RotaBrasil</h1><p>Ambiente de desenvolvimento local, sem login individual, para dados isolados.</p><p>O uso compartilhado exige o ambiente operacional com autenticação.</p><a href="/">Abrir painel local</a></body></html>
            """;
        var token=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
        String csrf=token==null?"":"<input type=\"hidden\" name=\""+HtmlUtils.htmlEscape(token.getParameterName())+"\" value=\""+HtmlUtils.htmlEscape(token.getToken())+"\">";
        String aviso=request.getParameterMap().containsKey("error")?"Credenciais inválidas. Verifique seu acesso.":request.getParameterMap().containsKey("logout")?"Sessão encerrada.":request.getParameterMap().containsKey("expirada")?"Sua sessão expirou. Entre novamente para continuar.":"Use sua conta individual. Procure o administrador se precisar redefinir o acesso.";
        return """
            <!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Entrar · RotaBrasil</title>
            <style>body{font:1rem system-ui;background:#111b18;color:#edf7ed;margin:0;min-height:100dvh;display:grid;place-items:center}main{box-sizing:border-box;width:min(94vw,28rem);padding:2rem;border:1px solid #718676;border-radius:1rem}label{display:block;margin:1rem 0}input,button{box-sizing:border-box;width:100%;font:inherit;padding:.8rem;border-radius:.4rem}button{background:#c9f66b;color:#112016;font-weight:700;cursor:pointer}a{color:#c9f66b}:focus-visible{outline:3px solid #c9f66b;outline-offset:3px}</style></head><body><main><h1>RotaBrasil</h1><h2>Entrar na operação</h2><p role="status">%s</p><form action="%s/login" method="post">%s<label>Usuário<input name="username" autocomplete="username" required maxlength="100" autofocus></label><label>Senha<input name="password" type="password" autocomplete="current-password" required></label><button type="submit">Entrar</button></form></main></body></html>
            """.replace("%s</p>",aviso+"</p>").replace("%s/login",HtmlUtils.htmlEscape(request.getContextPath())+"/login").replace("%s<label>",csrf+"<label>");
    }
}
