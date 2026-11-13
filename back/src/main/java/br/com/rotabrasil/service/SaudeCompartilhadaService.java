package br.com.rotabrasil.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** Sondagens limitadas de metadados. Nenhum transporte, backup ou leitura integral do acervo. */
@Service
public class SaudeCompartilhadaService {
    private final JdbcTemplate jdbc;private final DestinoExportacaoService destinos;private final ConfiguracaoNotificacoes canal;
    private final Path acervo;private final String manifesto,modo;private final ObjectMapper json;
    public SaudeCompartilhadaService(JdbcTemplate jdbc,DestinoExportacaoService destinos,ConfiguracaoNotificacoes canal,ObjectMapper json,
            @Value("${rotabrasil.arquivos.viagens}") String acervo,@Value("${rotabrasil.saude.backup-manifesto:}") String manifesto,@Value("${rotabrasil.email.modo:SIMULADO}") String modo){this.jdbc=jdbc;this.destinos=destinos;this.canal=canal;this.json=json;this.acervo=Path.of(acervo);this.manifesto=manifesto;this.modo=modo;}
    public record Verificacao(String componente,String estado,String mensagem,String orientacao,OffsetDateTime verificadoEm,Map<String,Object> detalhes){}
    public record Painel(OffsetDateTime verificadoEm,List<Verificacao> verificacoes){}
    private Verificacao v(String c,String estado,String msg,String orientacao,Map<String,Object> detalhes){return new Verificacao(c,estado,msg,orientacao,OffsetDateTime.now(ZoneOffset.UTC),detalhes);}
    private Verificacao verificar(String nome,Supplier<Verificacao> teste){try{return teste.get();}catch(Exception ex){return v(nome,"INDISPONIVEL","Verificação indisponível; as demais verificações são independentes.","Consultar diagnóstico e procurar o administrador.",Map.of());}}
    public Painel consultar(boolean admin){
        var lista=new ArrayList<Verificacao>();lista.add(v("Backend","FUNCIONANDO","Backend respondeu a esta consulta.","Atualizar a verificação se houver falha na operação.",Map.of()));
        lista.add(verificar("Banco",()->v("Banco",jdbc.queryForObject("SELECT 1",Integer.class)==1?"FUNCIONANDO":"INDISPONIVEL","Consulta limitada ao banco.","Consultar diagnóstico se a consulta falhar.",Map.of())));
        lista.add(verificar("Acervo",()->pasta("Acervo",acervo,admin)));
        lista.add(verificar("Exportação",()->{
            if(!destinos.autorizada())return v("Exportação","NAO_CONFIGURADO","Destino de exportação não configurado.","Revisar configuração com o administrador.",Map.of());
            String sub=jdbc.queryForObject("SELECT subpasta FROM configuracao_operacao WHERE id=1",String.class);
            try{var resultado=pasta("Exportação",destinos.resolver(sub),admin);if(!admin)return resultado;
                var detalhes=new LinkedHashMap<>(resultado.detalhes());detalhes.put("ultimoTesteHistorico",jdbc.queryForList("SELECT situacao,criado_em FROM testes_configuracao WHERE tipo='PASTA' ORDER BY criado_em DESC,id DESC LIMIT 1"));detalhes.put("observacaoTeste","Teste histórico não garante gravação atual; configuração ou permissões podem ter mudado.");
                return new Verificacao(resultado.componente(),resultado.estado(),resultado.mensagem(),resultado.orientacao(),resultado.verificadoEm(),detalhes);
            }catch(Exception ex){return v("Exportação","INDISPONIVEL","Pasta autorizada não acessível.","Verificar existência e permissões da pasta no backend.",Map.of());}
        }));
        lista.add(verificar("E-mail",()->email(admin)));
        lista.add(verificar("Backup",()->backup(admin)));
        return new Painel(OffsetDateTime.now(ZoneOffset.UTC),List.copyOf(lista));
    }
    private Verificacao pasta(String nome,Path pasta,boolean admin){
        if(!Files.isDirectory(pasta,LinkOption.NOFOLLOW_LINKS)||!Files.isReadable(pasta))return v(nome,"INDISPONIVEL","Pasta ausente ou sem leitura autorizada.","Verificar permissões da pasta com o administrador.",Map.of());
        Map<String,Object> detalhes=new LinkedHashMap<>();if(admin)try{detalhes.put("espacoDisponivelBytes",Files.getFileStore(pasta).getUsableSpace());}catch(Exception ex){detalhes.put("espaco","NAO_VERIFICADO");}
        detalhes.put("gravacao","NAO_VERIFICADA");
        return v(nome,Files.isWritable(pasta)?"ATENCAO":"INDISPONIVEL",Files.isWritable(pasta)?"Pasta existe e é legível. Gravação/publicação não testada nesta consulta.":"Pasta sem permissão de gravação detectada.","Verificar permissões; ADMIN pode executar o teste explícito da exportação em Configurações.",detalhes);
    }
    private Verificacao email(boolean admin){
        String remetente=jdbc.queryForObject("SELECT remetente FROM configuracao_operacao WHERE id=1",String.class);
        var c=canal.consultar(remetente);Map<String,Object> detalhes=new LinkedHashMap<>();
        detalhes.put("modoDocumental",modo);detalhes.put("modoConfirmacaoMotorista",c.modo());detalhes.put("enderecoSalvo",remetente!=null);detalhes.put("conectividade","NAO_VERIFICADA_NESTA_CONSULTA");
        if(admin){detalhes.put("autenticacaoConfigurada",c.autenticacaoConfigurada());detalhes.put("autorizacao",c.autorizacao());
            detalhes.put("ultimosTestes",jdbc.queryForList("SELECT tipo,situacao,criado_em FROM testes_configuracao WHERE tipo IN ('EMAIL','EMAIL_REAL') ORDER BY criado_em DESC,id DESC LIMIT 5"));
            detalhes.put("observacao","Teste EMAIL é somente simulação; aceite SMTP não comprova entrega ou leitura. Datas se referem ao teste histórico, não validam configuração posterior.");}
        return v("E-mail",remetente==null?"NAO_CONFIGURADO":"NAO_VERIFICADO",remetente==null?"Endereço remetente não salvo.":"Endereço salvo; conectividade e entrega não comprovadas por configuração.","Revisar configuração; nenhum teste externo é executado ao abrir este painel.",detalhes);
    }
    private Verificacao backup(boolean admin){
        if(!admin||manifesto.isBlank())return v("Backup","NAO_VERIFICADO","Sem informação de backup verificado.","Procurar o administrador para consultar evidências de cópia, integridade e restauração.",Map.of());
        Path p=Path.of(manifesto);
        try{
            if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>262144)return v("Backup","NAO_VERIFICADO","Evidência ausente ou não suportada. Sem informação de backup verificado.","Consultar relatório privado da manutenção.",Map.of());
            var documento=json.readTree(Files.readString(p));
            if(documento.path("versao").asInt()!=1||!documento.path("bancoSha256").asText().matches("[a-f0-9]{64}")||!documento.path("arquivos").isArray())throw new IllegalArgumentException();
            return v("Backup","NAO_VERIFICADO","Manifesto de cópia localizado; sem informação de backup verificado.","Conferir integridade offline e registrar teste de restauração conforme manutenção.",Map.of("copia","MANIFESTO_LOCALIZADO","manifestoModificadoEm",Files.getLastModifiedTime(p).toInstant().toString(),"integridade","NAO_VERIFICADA","restauracao","NAO_VERIFICADA","nota","Data é modificação do manifesto, não data comprovada da captura."));
        }catch(Exception ex){return v("Backup","NAO_VERIFICADO","Evidência não pôde ser lida. Sem informação de backup verificado.","Consultar relatório privado de manutenção.",Map.of());}
    }
}
