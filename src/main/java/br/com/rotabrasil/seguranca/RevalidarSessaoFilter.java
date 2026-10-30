package br.com.rotabrasil.seguranca;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
/** Desativação e mudança de papel valem também para sessões já abertas. */
public class RevalidarSessaoFilter extends OncePerRequestFilter {
    private final UserDetailsService usuarios;
    public RevalidarSessaoFilter(UserDetailsService usuarios){this.usuarios=usuarios;}
    protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain c)throws IOException,ServletException {
        var a=SecurityContextHolder.getContext().getAuthentication();
        if(a!=null&&a.isAuthenticated()&&!a.getName().equals("anonymousUser"))try{
            var u=usuarios.loadUserByUsername(a.getName());if(!u.isEnabled())throw new UsernameNotFoundException("Sessão indisponível");
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,u.getAuthorities()));
        }catch(UsernameNotFoundException ex){SecurityContextHolder.clearContext();if(r.getSession(false)!=null)r.getSession(false).invalidate();if(!r.getRequestURI().startsWith(r.getContextPath()+"/api/")){s.sendRedirect(r.getContextPath()+"/login");return;}s.setStatus(401);s.setContentType("application/json;charset=UTF-8");s.getWriter().write("{\"mensagem\":\"Sessão encerrada; entre novamente\"}");return;}
        c.doFilter(r,s);
    }
}
