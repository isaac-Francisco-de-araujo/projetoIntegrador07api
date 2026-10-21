package br.com.rotabrasil;

import br.com.rotabrasil.dto.*;
import br.com.rotabrasil.model.*;
import br.com.rotabrasil.repository.*;
import br.com.rotabrasil.service.*;
import br.com.rotabrasil.manutencao.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.zip.*;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;
import static br.com.rotabrasil.service.ReconciliacaoArmazenamentoService.Estado.*;

class PacoteReinicioTest {
    @TempDir Path base;
    private static final byte[] PDF="%PDF-1.4\n% sintético\n%%EOF".getBytes(StandardCharsets.UTF_8);
    private ConfigurableApplicationContext iniciar(boolean migrar) {
        return new SpringApplicationBuilder(RotaBrasilApplication.class).web(WebApplicationType.NONE).run(
            "--spring.datasource.url=jdbc:h2:file:"+base.resolve("banco"),"--spring.flyway.enabled="+migrar,
            "--spring.jpa.hibernate.ddl-auto=validate","--spring.h2.console.enabled=false","--spring.main.banner-mode=off",
            "--logging.level.root=ERROR","--rotabrasil.arquivos.viagens="+base.resolve("viagens"));
    }
    private MockMultipartFile upload()throws Exception{
        var output=new java.io.ByteArrayOutputStream();try(var zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("cte.xml"));zip.write(CteFixture.xml().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("fatura.pdf"));zip.write(PDF);zip.closeEntry();
        }return new MockMultipartFile("arquivo","viagem.zip","application/zip",output.toByteArray());
    }
    @Test void reinicioPreservaPacoteIdempotenciaArquivamentoEBackupContemPdfEVinculos()throws Exception{
        long id,pacoteId,viagem;Path path;PacoteDtos.Confirmacao config;var arquivo=upload();
        try(var contexto=iniciar(true)){
            var service=contexto.getBean(PacoteService.class);var i=service.inspecionar(arquivo);
            config=new PacoteDtos.Confirmacao(i.sha256(),List.of(new PacoteDtos.Vinculo("a1",FinalidadeAnexo.FATURA,List.of("a0"))));
            var r=service.confirmar(arquivo,config);id=r.anexos().getFirst().id();pacoteId=r.pacoteId();viagem=r.documentos().getFirst().viagemId();
            path=Path.of(contexto.getBean(JdbcTemplate.class).queryForObject("SELECT caminho FROM anexos WHERE id=?",String.class,id));
            contexto.getBean(AnexoService.class).arquivar(id);
        }
        var mtime=Files.getLastModifiedTime(path);
        try(var contexto=iniciar(false)){
            var r=contexto.getBean(PacoteService.class).confirmar(arquivo,config);assertThat(r.reutilizado()).isTrue();assertThat(r.pacoteId()).isEqualTo(pacoteId);
            var anexos=contexto.getBean(AnexoService.class);assertThat(anexos.listar(viagem,false)).isEmpty();assertThat(anexos.listar(viagem,true)).hasSize(1);
            anexos.restaurar(id);assertThat(anexos.listar(viagem,false)).hasSize(1);assertThat(Files.getLastModifiedTime(path)).isEqualTo(mtime);
            assertThat(Files.readAllBytes(path)).isEqualTo(PDF);
            try(var c=contexto.getBean(DataSource.class).getConnection()){
                c.createStatement().execute("SET EXCLUSIVE 2");
                try{BackupBanco.criar(c,base.resolve("viagens"),base.resolve("backup"));assertThat(BackupBanco.verificar(base.resolve("backup")).arquivos()).hasSize(2);BackupBanco.conferirOrigem(c,base.resolve("viagens"),base.resolve("backup"));}
                finally{c.createStatement().execute("SET EXCLUSIVE 0");}
            }
        }
        Path restaurado=base.resolve("restaurado");Files.createDirectory(restaurado);
        try(var zip=new ZipFile(base.resolve("backup/banco.zip").toFile())){var entries=zip.entries();while(entries.hasMoreElements()){var e=entries.nextElement();Path target=restaurado.resolve(e.getName()).normalize();assertThat(target.startsWith(restaurado)).isTrue();Files.createDirectories(target.getParent());try(var input=zip.getInputStream(e)){Files.copy(input,target);}}}
        try(var paths=Files.walk(restaurado)){
            Path banco=paths.filter(p->p.toString().endsWith(".mv.db")).findFirst().orElseThrow();String bancoBase=banco.toString().replaceFirst("\\.mv\\.db$","");
            try(var c=java.sql.DriverManager.getConnection("jdbc:h2:file:"+bancoBase+";IFEXISTS=TRUE","sa","");var rs=c.createStatement().executeQuery("SELECT COUNT(*) FROM anexo_viagens")){rs.next();assertThat(rs.getInt(1)).isEqualTo(1);}
        }
    }
    @Test void recuperaPdfConfirmadoAposReinicioSemAdotarPdfSemRegistro()throws Exception{
        Path destino,pendente;String token=UUID.randomUUID().toString();
        try(var contexto=iniciar(true)){
            var cliente=contexto.getBean(ClienteRepository.class).save(Cliente.builder().nome("Sintético").cnpj("00000000000000").build());
            var viagem=contexto.getBean(ViagemRepository.class).save(Viagem.builder().numero("66213").cliente(cliente).build());
            var pacote=contexto.getBean(PacoteRepository.class).save(Pacote.builder().nomeArquivo("teste.zip").sha256("a".repeat(64)).configuracaoSha256("b".repeat(64)).criadoEm(OffsetDateTime.now()).build());
            var arquivos=contexto.getBean(OrganizadorArquivosService.class);
            try(var op=arquivos.prepararPdf(PDF,"66213",token)){
                destino=op.destino();pendente=op.diretorio();
                contexto.getBean(AnexoRepository.class).saveAndFlush(Anexo.builder().nomeArquivo("fatura.pdf").pacote(pacote).finalidade(FinalidadeAnexo.FATURA).token(token)
                    .sha256(ConteudoXml.sha256(PDF)).numeroArmazenamento("66213").caminho(destino.toString()).viagens(new LinkedHashSet<>(List.of(viagem))).build());
            }
        }
        try(var contexto=iniciar(false)){
            var service=contexto.getBean(ReconciliacaoArmazenamentoService.class);
            assertThat(service.inspecionar()).extracting(ReconciliacaoArmazenamentoService.Pendencia::estado).containsExactly(REGISTRO_SEM_ARQUIVO);
            assertThat(destino).doesNotExist();assertThat(service.recuperarConfirmadas()).extracting(ReconciliacaoArmazenamentoService.Pendencia::estado).containsExactly(RECUPERADA);
            assertThat(Files.readAllBytes(destino)).isEqualTo(PDF);assertThat(pendente).doesNotExist();
            try(var op=contexto.getBean(OrganizadorArquivosService.class).prepararPdf(PDF,"66213",UUID.randomUUID().toString())){
                contexto.getBean(OrganizadorArquivosService.class).publicar(op);
            }
            assertThat(service.recuperarConfirmadas()).extracting(ReconciliacaoArmazenamentoService.Pendencia::estado).containsExactly(PUBLICADO_SEM_REGISTRO);
            assertThat(contexto.getBean(AnexoRepository.class).count()).isEqualTo(1);
        }
    }
}
