package br.com.rotabrasil.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;

@Configuration
public class SegurancaConfig {
    private record OperadorSessao(String usuario,String hash,String papel,boolean ativo) implements UserDetails {
        public String getUsername(){return usuario;}public String getPassword(){return hash;}public boolean isEnabled(){return ativo;}
        public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities(){return java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+papel));}
        @Override public String toString(){return "OperadorSessao[credenciais omitidas]";}
    }
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
    @Bean UserDetailsService usuarios(JdbcTemplate jdbc){return login->{
        var encontrados=jdbc.query("SELECT usuario,senha_hash,papel,ativo FROM usuarios WHERE usuario=?",(rs,n)->
                new OperadorSessao(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBoolean(4)),login);
        return encontrados.stream().findFirst().orElseThrow(()->new UsernameNotFoundException("Credenciais inválidas"));
    };}
    @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type=org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain acesso(HttpSecurity http,UserDetailsService usuarios,@Value("${rotabrasil.seguranca.ativa:false}") boolean ativa) throws Exception {
        if(!ativa)return http.authorizeHttpRequests(a->a.anyRequest().permitAll()).csrf(c->c.disable()).build();
        var api=PathPatternRequestMatcher.withDefaults().matcher("/api/**");
        return http.addFilterBefore(new RevalidarSessaoFilter(usuarios),org.springframework.security.web.access.intercept.AuthorizationFilter.class).authorizeHttpRequests(a->a
                .requestMatchers(HttpMethod.GET,"/","/acesso.css","/acesso-logo.svg","/acesso.js").permitAll()
                .requestMatchers("/login","/error").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/configuracoes/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/api/clientes/**","/api/viagens/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/**").authenticated()
                .anyRequest().hasAnyRole("OPERADOR","ADMIN"))
                .csrf(c->c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
                .formLogin(f->f.loginPage("/login").successHandler((req,res,auth)->{
                    req.getSession().setAttribute("rotabrasil.credencial",((UserDetails)auth.getPrincipal()).getPassword());
                    res.sendRedirect(req.getContextPath()+"/painel");
                }).permitAll())
                .logout(l->l.logoutSuccessUrl("/login?logout"))
                // Uma entry point global impede o formulário padrão do Spring de ser gerado.
                // Selecionar por rota mantém a entrada existente e o contrato JSON da API.
                .exceptionHandling(e->e.defaultAuthenticationEntryPointFor((req,res,ex)->{
                    res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"mensagem\":\"Sessão encerrada; entre novamente\"}");
                },api).defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),new NegatedRequestMatcher(api)))
                .build();
    }
}
