package br.com.rotabrasil;
import br.com.rotabrasil.config.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
class ProtecaoPerfisTest {
    @Test void operacaoRecusaMigracaoAutomaticaAntesDeInicializarEscritores(){
        var iniciou=new AtomicBoolean();new ApplicationContextRunner().withUserConfiguration(ProtecaoPerfilOperacao.class)
                .withPropertyValues("spring.profiles.active=operacao","rotabrasil.seguranca.ativa=true","spring.flyway.enabled=true")
                .withBean("escritor",Object.class,()->{iniciou.set(true);return new Object();})
                .run(c->{assertThat(c).hasFailed();assertThat(iniciou).isFalse();});
    }
    @Test void operacaoRecusaSegurancaDesativada(){new ApplicationContextRunner().withUserConfiguration(ProtecaoPerfilOperacao.class).withPropertyValues("spring.profiles.active=operacao","rotabrasil.seguranca.ativa=false").run(c->assertThat(c).hasFailed());}
    @Test void operacaoRecusaDdlAutoUpdate(){new ApplicationContextRunner().withUserConfiguration(ProtecaoPerfilOperacao.class).withPropertyValues("spring.profiles.active=operacao","rotabrasil.seguranca.ativa=true","spring.jpa.hibernate.ddl-auto=update").run(c->assertThat(c).hasFailed());}
    @Test void desenvolvimentoNaoAbreUrlOperacionalPadrao(){new ApplicationContextRunner().withUserConfiguration(ProtecaoPerfilDesenvolvimento.class).withPropertyValues("spring.profiles.active=desenvolvimento","spring.datasource.url=jdbc:h2:file:./database/rotabrasil;IFEXISTS=TRUE").run(c->assertThat(c).hasFailed());}
    @Test void desenvolvimentoAceitaBaseIsolada(){new ApplicationContextRunner().withUserConfiguration(ProtecaoPerfilDesenvolvimento.class).withPropertyValues("spring.profiles.active=desenvolvimento","spring.datasource.url=jdbc:h2:mem:isolado").run(c->assertThat(c).hasNotFailed());}
}
