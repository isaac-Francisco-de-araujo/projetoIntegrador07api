package br.com.rotabrasil.config;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.core.env.Environment;
/** O perfil sem autenticação não pode abrir por engano a URL operacional padrão. */
@Configuration @Profile("desenvolvimento")
public class ProtecaoPerfilDesenvolvimento {
    @Bean static BeanFactoryPostProcessor validarBaseIsolada(Environment e){
        if(e.getProperty("spring.datasource.url","").startsWith("jdbc:h2:file:./database/rotabrasil"))
            throw new IllegalStateException("Desenvolvimento exige URL de banco e raiz de documentos isoladas configuradas explicitamente; para dados operacionais use o perfil operacao");
        return factory->{};
    }
}
