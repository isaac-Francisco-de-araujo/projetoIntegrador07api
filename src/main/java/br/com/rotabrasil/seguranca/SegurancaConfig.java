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

@Configuration
public class SegurancaConfig {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
    @Bean UserDetailsService usuarios(JdbcTemplate jdbc){return login->{
        var encontrados=jdbc.query("SELECT usuario,senha_hash,papel,ativo FROM usuarios WHERE usuario=?",(rs,n)->
                User.withUsername(rs.getString(1)).password(rs.getString(2)).roles(rs.getString(3)).disabled(!rs.getBoolean(4)).build(),login);
        return encontrados.stream().findFirst().orElseThrow(()->new UsernameNotFoundException("Credenciais inválidas"));
    };}
    @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type=org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain acesso(HttpSecurity http,UserDetailsService usuarios,@Value("${rotabrasil.seguranca.ativa:false}") boolean ativa) throws Exception {
        if(!ativa)return http.authorizeHttpRequests(a->a.anyRequest().permitAll()).csrf(c->c.disable()).build();
        return http.addFilterBefore(new RevalidarSessaoFilter(usuarios),org.springframework.security.web.access.intercept.AuthorizationFilter.class).authorizeHttpRequests(a->a
                .requestMatchers("/login","/error").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/configuracoes/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/api/clientes/**","/api/viagens/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/**").authenticated()
                .anyRequest().hasAnyRole("OPERADOR","ADMIN"))
                .csrf(c->c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
                .formLogin(f->f.defaultSuccessUrl("/",true).permitAll())
                .logout(l->l.logoutSuccessUrl("/login?logout"))
                .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{
                    if(req.getRequestURI().startsWith(req.getContextPath()+"/api/")){res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"mensagem\":\"Sessão encerrada; entre novamente\"}");}
                    else new LoginUrlAuthenticationEntryPoint("/login").commence(req,res,ex);
                }))
                .build();
    }
}
