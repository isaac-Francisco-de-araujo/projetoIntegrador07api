package br.com.rotabrasil.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class BootstrapOperador implements ApplicationRunner {
    private final JdbcTemplate jdbc;private final boolean ativa;private final String usuario,hash;
    public BootstrapOperador(JdbcTemplate jdbc,@Value("${rotabrasil.seguranca.ativa:false}") boolean ativa,
            @Value("${rotabrasil.seguranca.admin-usuario:admin}") String usuario,@Value("${rotabrasil.seguranca.admin-hash:}") String hash){this.jdbc=jdbc;this.ativa=ativa;this.usuario=usuario;this.hash=hash;}
    public void run(ApplicationArguments args){
        if(!ativa)return;
        if(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE ativo=TRUE AND papel='ADMIN'",Integer.class)>0)return;
        if(!usuario.matches("[A-Za-z0-9._-]{3,100}")||!hash.matches("\\$2[aby]\\$(0[4-9]|1[0-6])\\$[./A-Za-z0-9]{53}"))
            throw new IllegalStateException("Primeiro acesso exige administrador e hash BCrypt configurados externamente; use a CLI GerarHashSenha");
        jdbc.update("INSERT INTO usuarios(usuario,senha_hash,papel,criado_em) VALUES(?,?,'ADMIN',CURRENT_TIMESTAMP)",usuario,hash);
    }
}
