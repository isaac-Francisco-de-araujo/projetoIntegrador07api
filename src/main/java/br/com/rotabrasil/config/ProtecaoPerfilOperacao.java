package br.com.rotabrasil.config;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.core.env.Environment;
/** Executado antes de inicializar Flyway, JPA ou qualquer escritor. */
@Configuration @Profile("operacao")
public class ProtecaoPerfilOperacao {
    @Bean static BeanFactoryPostProcessor validarOperacao(Environment e){
        if(!e.getProperty("rotabrasil.seguranca.ativa",Boolean.class,false)||e.getProperty("spring.h2.console.enabled",Boolean.class,false)||e.getProperty("spring.flyway.enabled",Boolean.class,false)||!e.getProperty("spring.jpa.hibernate.ddl-auto","validate").equals("validate"))
            throw new IllegalStateException("Perfil operacao exige autenticação, H2 Console desligado, Hibernate validate e migrações automáticas desativadas");
        return factory->{};
    }
}
