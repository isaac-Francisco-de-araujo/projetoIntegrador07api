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
        String favicons="""
            <link rel="icon" type="image/x-icon" href="%1$s/favicon.ico?v=rb-20261113-1"><link rel="icon" type="image/png" sizes="16x16" href="%1$s/assets/marca/favicon-16.png?v=rb-20261113-1"><link rel="icon" type="image/png" sizes="32x32" href="%1$s/assets/marca/favicon-32.png?v=rb-20261113-1">
            """.formatted(contexto);
        if (segurancaAtiva && request.getUserPrincipal()!=null) {
            response.sendRedirect(request.getContextPath()+"/painel");
            return "";
        }
        if (!segurancaAtiva) return """
            <!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#0b1410"><title>Acesso local · RotaBrasil</title>%6$s
            <style>html,body{margin:0;background:#0b1410;color:#f3f6f2}</style><link rel="stylesheet" href="%s/acesso.css"><script src="%s/acesso.js" defer></script></head><body data-entrada="local"><main aria-labelledby="titulo"><div class="marca"><img src="%s/assets/marca/logo-rotabrasil.webp" width="384" height="351" alt=""><span>Rota<strong>Brasil</strong></span></div><h1 id="titulo">Acesso local</h1><p>Ambiente de desenvolvimento local, sem login individual, para dados isolados.</p><p>O uso compartilhado exige o ambiente operacional com autenticação.</p><a class="botao" data-acesso-local href="%s/painel">Abrir painel local</a><p class="rodape"><a href="%s/">Voltar à apresentação</a></p></main></body></html>
            """.formatted(contexto,contexto,contexto,contexto,contexto,favicons);
        var token=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
        String csrf=token==null?"":"<input type=\"hidden\" name=\""+HtmlUtils.htmlEscape(token.getParameterName())+"\" value=\""+HtmlUtils.htmlEscape(token.getToken())+"\">";
        String aviso=request.getParameterMap().containsKey("error")?"Credenciais inválidas. Verifique seu acesso.":request.getParameterMap().containsKey("logout")?"Sessão encerrada.":request.getParameterMap().containsKey("expirada")?"Sua sessão expirou. Entre novamente para continuar.":"Use sua conta individual. Procure o administrador se precisar redefinir o acesso.";
        return """
            <!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#0b1410"><title>Entrar · RotaBrasil</title>%8$s
            <style>html,body{margin:0;background:#0b1410;color:#f3f6f2}</style><link rel="stylesheet" href="%s/acesso.css"><script src="%s/acesso.js" defer></script></head><body data-entrada="login"><main aria-labelledby="titulo"><div class="marca"><img src="%s/assets/marca/logo-rotabrasil.webp" width="384" height="351" alt=""><span>Rota<strong>Brasil</strong></span></div><h1 id="titulo">Entrar na operação</h1><p role="status">%s</p><form data-login action="%s/login" method="post">%s<label>Usuário<input name="username" autocomplete="username" required maxlength="100" autofocus></label><label>Senha<input name="password" type="password" autocomplete="current-password" required></label><button type="submit">Entrar</button></form><p class="rodape"><a href="%s/">Voltar à apresentação</a></p></main></body></html>
            """.formatted(contexto,contexto,contexto,aviso,contexto,csrf,contexto,favicons);
    }
}
