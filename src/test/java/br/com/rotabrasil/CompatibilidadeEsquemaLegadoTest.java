package br.com.rotabrasil;

import br.com.rotabrasil.repository.DocumentoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:esquema-legado-etapa2;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=update",
        "rotabrasil.banco.exigir-restricoes=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-legado-etapa2.sql",
        "spring.h2.console.enabled=false",
        "rotabrasil.arquivos.viagens=${java.io.tmpdir}/rotabrasil-esquema-nao-utilizado"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CompatibilidadeEsquemaLegadoTest {
    @Autowired DocumentoRepository documentos;

    @Test
    void adicionaColunasOpcionaisSemAlterarRegistroLegado() {
        var documento = documentos.findById(103L).orElseThrow();
        assertThat(documento.getNomeArquivo()).isEqualTo("legado.xml");
        assertThat(documento.getCaminho()).isEqualTo("caminho-legado-preservado.xml");
        assertThat(documento.getViagem().getId()).isEqualTo(102L);
        assertThat(documento.getChaveAcesso()).isNull();
        assertThat(documento.getSha256()).isNull();
        assertThat(documentos.count()).isEqualTo(1);
    }
}
