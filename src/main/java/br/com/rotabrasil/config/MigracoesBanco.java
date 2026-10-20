package br.com.rotabrasil.config;

import br.com.rotabrasil.manutencao.InventarioBanco;
import br.com.rotabrasil.migracao.V3__ConferirEPreencherIdentidade;
import org.flywaydb.core.api.migration.JavaMigration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.nio.file.Path;

@Configuration
public class MigracoesBanco {
    @Bean
    JavaMigration identidadeDocumental(@Value("${rotabrasil.arquivos.viagens:arquivos/viagens}") String raiz) {
        return new V3__ConferirEPreencherIdentidade(new InventarioBanco(Path.of(raiz)), Path.of(raiz), null);
    }
}
