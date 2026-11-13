package br.com.rotabrasil.seguranca;
import org.springframework.security.core.context.SecurityContextHolder;
public final class AtorAtual {
    private AtorAtual(){}
    public static String nome(){var a=SecurityContextHolder.getContext().getAuthentication();return a==null||!a.isAuthenticated()||a.getName().equals("anonymousUser")?"DESENVOLVIMENTO_LOCAL":a.getName();}
}
