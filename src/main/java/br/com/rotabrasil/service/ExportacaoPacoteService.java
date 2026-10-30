package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.OperacaoDtos.*;
import br.com.rotabrasil.dto.DiagnosticoOperacao.Recuperacao;
import br.com.rotabrasil.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.IOException;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ExportacaoPacoteService {
    private final JdbcTemplate jdbc;private final CoordenacaoImportacaoService coordenacao;
    private final ConfiguracaoOperacaoService config;private final DestinoExportacaoService destinos;
    private final PublicadorExportacao publicador;private final NotificacaoRecebimentoService notificacoes;
    private final ObjectMapper json;private final Path acervo;private final TransactionTemplate tx;
    private static final long TOTAL=100L*1024*1024;
    private record Fonte(String origem,Long id,String caminho,String local,String viagens,String hash,String manifesto){}
    public ExportacaoPacoteService(JdbcTemplate jdbc,CoordenacaoImportacaoService coordenacao,ConfiguracaoOperacaoService config,
            DestinoExportacaoService destinos,PublicadorExportacao publicador,NotificacaoRecebimentoService notificacoes,
            ObjectMapper json,PlatformTransactionManager manager,@Value("${rotabrasil.arquivos.viagens:arquivos/viagens}") String acervo){
        this.jdbc=jdbc;this.coordenacao=coordenacao;this.config=config;this.destinos=destinos;this.publicador=publicador;
        this.notificacoes=notificacoes;this.json=json;this.acervo=Path.of(acervo);tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public Exportacao exportar(Long pacoteId){return exportar(pacoteId,null);}
    public Exportacao exportar(Long pacoteId,UUID requisicaoId){
        String id=tx.execute(status->{
            coordenacao.bloquear();
            if(jdbc.queryForObject("SELECT COUNT(*) FROM pacotes WHERE id=?",Integer.class,pacoteId)!=1)throw new RecursoNaoEncontradoException("Pacote não encontrado");
            var c=config.consultar();String chave=destinos.chave(c.subpasta());
            var existentes=jdbc.queryForList("SELECT id FROM exportacoes WHERE pacote_id=? AND destino_chave=?",String.class,pacoteId,chave);
            if(!existentes.isEmpty())return existentes.getFirst();
            String novo=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO exportacoes(id,pacote_id,destino_chave,subpasta,configuracao,situacao,mensagem,criada_em,atualizada_em,correlacao) VALUES(?,?,?,?,?,'PENDENTE','Exportação preparada; nenhum sucesso presumido',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)",novo,pacoteId,chave,c.subpasta(),config.escrever(c),org.slf4j.MDC.get("operacaoId"));
            var fontes=fontes(pacoteId);
            if(fontes.size()>100)throw new ArquivoInvalidoException("Exportação excede 100 arquivos elegíveis");
            for(var f:fontes)inserir(novo,f);
            if(!fontes.isEmpty()){
                var entradas=fontes.stream().map(f->Map.of("origem",f.origem(),"id",f.id(),"localizacao",f.local(),"viagens",f.viagens(),"sha256",Objects.toString(f.hash(),"INDISPONIVEL"))).toList();
                String manifesto=config.escrever(Map.of("pacoteId",pacoteId,"exportacaoId",novo,"arquivos",entradas));
                inserir(novo,new Fonte("MANIFESTO",null,null,"pacote-"+pacoteId+"/manifesto.json","[]",ConteudoXml.sha256(manifesto.getBytes(java.nio.charset.StandardCharsets.UTF_8)),manifesto));
            }
            return novo;
        });
        return retomar(id,requisicaoId);
    }
    public void automatico(Long pacoteId,boolean reutilizado){
        if(!reutilizado&&config.consultar().exportacaoAutomatica())exportar(pacoteId);
    }
    private void inserir(String id,Fonte f){
        jdbc.update("INSERT INTO exportacao_arquivos(id,exportacao_id,origem,origem_id,caminho_fonte,localizacao,viagens,sha256,manifesto,situacao,etapa,mensagem) VALUES(?,?,?,?,?,?,?,?,?,'PENDENTE','PREPARACAO','Aguardando cópia')",
                UUID.randomUUID().toString(),id,f.origem(),f.id(),f.caminho(),f.local(),f.viagens(),f.hash(),f.manifesto());
    }
    private List<Fonte> fontes(Long pacoteId){
        var fontes=new ArrayList<Fonte>();
        jdbc.query("SELECT d.*,v.numero FROM documentos d JOIN viagens v ON v.id=d.viagem_id JOIN pacote_documentos pd ON pd.documento_id=d.id WHERE pd.pacote_id=? AND d.arquivado_em IS NULL ORDER BY d.id",rs->{
            String numero=numero(rs.getString("numero"));Long id=rs.getLong("id");
            String extensao="XML".equalsIgnoreCase(rs.getString("tipo"))?"xml":"PDF".equalsIgnoreCase(rs.getString("tipo"))?"pdf":null;
            if(extensao==null)throw new ArquivoInvalidoException("Tipo documental não elegível para exportação");
            fontes.add(new Fonte("DOCUMENTO",id,rs.getString("caminho"),"pacote-"+pacoteId+"/viagens/"+numero+"/documento-"+id+"."+extensao,
                    config.escrever(List.of(Map.of("id",rs.getLong("viagem_id"),"numero",numero))),rs.getString("sha256"),null));
        },pacoteId);
        for(var a:jdbc.queryForList("SELECT * FROM anexos WHERE pacote_id=? AND arquivado_em IS NULL ORDER BY id",pacoteId)){
            Long id=((Number)a.get("ID")).longValue();
            var viagens=jdbc.queryForList("SELECT v.id,v.numero FROM viagens v JOIN anexo_viagens av ON av.viagem_id=v.id WHERE av.anexo_id=? ORDER BY v.id",id);
            if(viagens.isEmpty())throw new ConflitoRecursoException("PDF sem viagens; confira os vínculos antes de exportar");
            for(var v:viagens)numero((String)v.get("NUMERO"));
            String local="FATURA".equals(a.get("FINALIDADE"))?"faturas/fatura-"+id+".pdf":"viagens/"+numero((String)viagens.getFirst().get("NUMERO"))+"/documento-viagem-"+id+".pdf";
            fontes.add(new Fonte("ANEXO",id,(String)a.get("CAMINHO"),"pacote-"+pacoteId+"/"+local,config.escrever(viagens),(String)a.get("SHA256"),null));
        }
        return fontes;
    }
    private String numero(String numero){if(numero==null||!numero.matches("[0-9]{1,9}"))throw new ArquivoInvalidoException("Número de viagem inválido para exportação");return numero;}

    public Exportacao retomar(String id){return retomar(id,null);}
    public Exportacao retomar(String id,UUID requisicaoId){
        String tentativa=(requisicaoId==null?UUID.randomUUID():requisicaoId).toString();
        boolean iniciar=tx.execute(status->{
            coordenacao.bloquear();var e=registro(id);var historico=recuperacoes(e);
            if("CONCLUIDA".equals(e.get("SITUACAO"))||historico.stream().anyMatch(h->h.id().equals(tentativa)))return false;
            if(!Objects.equals(e.get("DESTINO_CHAVE"),destinos.chave((String)e.get("SUBPASTA"))))throw new ConflitoRecursoException("A raiz autorizada mudou; inicie uma exportação explícita usando a configuração atual");
            if("EM_PROCESSAMENTO".equals(e.get("SITUACAO"))&&e.get("EXECUCAO_EM") instanceof OffsetDateTime t&&t.isAfter(OffsetDateTime.now().minusMinutes(5)))return false;
            historico.add(new Recuperacao(tentativa,org.slf4j.MDC.get("operacaoId"),OffsetDateTime.now(java.time.ZoneOffset.UTC),null,"EM_PROCESSAMENTO",0,0));
            jdbc.update("UPDATE exportacoes SET situacao='EM_PROCESSAMENTO',execucao_em=CURRENT_TIMESTAMP,correlacao=?,recuperacoes=? WHERE id=?",org.slf4j.MDC.get("operacaoId"),config.escrever(historico),id);
            return true;
        });
        if(!iniciar)return consultar(id);
        Exportacao resultado=tx.execute(status->{
            coordenacao.bloquear();var e=registro(id);
            if("CONCLUIDA".equals(e.get("SITUACAO")))return consultar(id);
            String subpasta=(String)e.get("SUBPASTA");
            if(!Objects.equals(e.get("DESTINO_CHAVE"),destinos.chave(subpasta)))throw new ConflitoRecursoException("A raiz autorizada mudou; inicie uma exportação explícita usando a configuração atual");
            var itens=jdbc.queryForList("SELECT * FROM exportacao_arquivos WHERE exportacao_id=? ORDER BY CASE WHEN origem='MANIFESTO' THEN 1 ELSE 0 END,localizacao",id);
            long tamanho=0;boolean falha=false;
            for(var item:itens){
                String itemId=(String)item.get("ID");String etapa="VALIDACAO_DESTINO",codigo=null,mensagem="";
                try{
                    Path pasta=destinos.resolver(subpasta);
                    Path alvo=LeituraSeguraArquivos.resolver(pasta,(String)item.get("LOCALIZACAO"));
                    Path temporario=LeituraSeguraArquivos.resolver(pasta,".rotabrasil-exportacoes/"+id+"/"+itemId+".tmp");
                    etapa="CONFERENCIA_DESTINO";
                    if(Files.exists(alvo,LinkOption.NOFOLLOW_LINKS)){
                        boolean proprio="CONCLUIDO".equals(item.get("SITUACAO"))||
                                (Files.isRegularFile(temporario,LinkOption.NOFOLLOW_LINKS)&&Files.isSameFile(alvo,temporario));
                        if(!proprio)throw new FalhaExportacao("ARQUIVO_EXISTENTE","Arquivo preexistente preservado. Escolha outra subpasta ou revise o conflito antes de retomar.");
                        byte[] bytes=LeituraSeguraArquivos.ler(pasta,alvo.toString(),ConteudoXml.LIMITE_BYTES);
                        if(!ConteudoXml.sha256(bytes).equals(item.get("SHA256")))throw new FalhaExportacao("DESTINO_ALTERADO","Arquivo exportado foi alterado; não será sobrescrito.");
                        tamanho+=bytes.length;if(tamanho>TOTAL)throw new FalhaExportacao("LIMITE_TOTAL","Total exportado excede 100 MiB.");
                        concluido(itemId,bytes.length);continue;
                    }
                    if("CONCLUIDO".equals(item.get("SITUACAO")))throw new FalhaExportacao("DESTINO_AUSENTE","Arquivo anteriormente exportado está ausente. Confira o destino antes de criar nova exportação.");
                    if("MANIFESTO".equals(item.get("ORIGEM"))&&falha)throw new FalhaExportacao("ARQUIVOS_PENDENTES","Manifesto aguarda a conclusão dos documentos.");
                    etapa="LEITURA_ORIGEM";byte[] bytes;
                    if("MANIFESTO".equals(item.get("ORIGEM")))bytes=((String)item.get("MANIFESTO")).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    else{
                        String sql="DOCUMENTO".equals(item.get("ORIGEM"))
                                ?"SELECT COUNT(*) FROM documentos d JOIN pacote_documentos pd ON pd.documento_id=d.id WHERE pd.pacote_id=? AND d.id=? AND d.arquivado_em IS NULL AND d.caminho=? AND d.sha256=?"
                                :"SELECT COUNT(*) FROM anexos WHERE pacote_id=? AND id=? AND arquivado_em IS NULL AND caminho=? AND sha256=?";
                        if(jdbc.queryForObject(sql,Integer.class,e.get("PACOTE_ID"),item.get("ORIGEM_ID"),item.get("CAMINHO_FONTE"),item.get("SHA256"))!=1)
                            throw new FalhaExportacao("ORIGEM_INELEGIVEL","Origem arquivada, removida ou com metadados alterados desde o plano; revise o acervo antes de exportar.");
                        Path fonte=LeituraSeguraArquivos.resolver(acervo,(String)item.get("CAMINHO_FONTE"));
                        if(!Files.isRegularFile(fonte,LinkOption.NOFOLLOW_LINKS))throw new FalhaExportacao("ORIGEM_AUSENTE","Arquivo de origem ausente ou não regular; a importação foi preservada.");
                        bytes=LeituraSeguraArquivos.ler(acervo,fonte.toString(),ConteudoXml.LIMITE_BYTES);
                    }
                    etapa="INTEGRIDADE_ORIGEM";
                    if(item.get("SHA256")==null||!ConteudoXml.sha256(bytes).equals(item.get("SHA256")))throw new FalhaExportacao("HASH_DIVERGENTE","SHA-256 da origem ausente ou divergente; nenhum original será alterado.");
                    if(tamanho+bytes.length>TOTAL)throw new FalhaExportacao("LIMITE_TOTAL","Total da exportação excede 100 MiB.");
                    etapa="COPIA";publicador.publicar(alvo,temporario,bytes);
                    if(!ConteudoXml.sha256(LeituraSeguraArquivos.ler(pasta,alvo.toString(),ConteudoXml.LIMITE_BYTES)).equals(item.get("SHA256")))
                        throw new FalhaExportacao("DESTINO_ALTERADO","Cópia divergiu do SHA-256; arquivo preservado para revisão.");
                    tamanho+=bytes.length;concluido(itemId,bytes.length);
                }catch(FalhaExportacao ex){codigo=ex.codigo;mensagem=ex.getMessage();}
                catch(IOException|UnsupportedOperationException|SecurityException ex){codigo=DestinoExportacaoService.codigo(ex);mensagem="Não foi possível concluir este arquivo. Confira acesso, espaço e disponibilidade no backend; tente apenas a exportação novamente.";}
                if(codigo!=null){falha=true;jdbc.update("UPDATE exportacao_arquivos SET situacao='PENDENTE',etapa=?,codigo=?,mensagem=? WHERE id=?",etapa,codigo,mensagem,itemId);}
            }
            int concluidos=jdbc.queryForObject("SELECT COUNT(*) FROM exportacao_arquivos WHERE exportacao_id=? AND situacao='CONCLUIDO'",Integer.class,id);
            String situacao=itens.isEmpty()?"PENDENTE":!falha?"CONCLUIDA":concluidos>0?"PARCIAL":"PENDENTE";
            String mensagem="CONCLUIDA".equals(situacao)?"Exportação concluída na pasta autorizada do backend.":itens.isEmpty()?"Importado; exportação pendente: nenhum arquivo ativo elegível.":"Importado; exportação pendente. Arquivos concluídos foram preservados; retome apenas a exportação após corrigir o destino.";
            jdbc.update("UPDATE exportacoes SET situacao=?,mensagem=?,atualizada_em=CURRENT_TIMESTAMP WHERE id=?",situacao,mensagem,id);
            var historico=recuperacoes(registro(id));
            for(int n=0;n<historico.size();n++)if(historico.get(n).id().equals(tentativa)){
                var falhas=jdbc.query("SELECT id,localizacao,etapa,codigo,mensagem FROM exportacao_arquivos WHERE exportacao_id=? AND situacao<>'CONCLUIDO' ORDER BY localizacao",(rs,linha)->{
                    String local=rs.getString("localizacao"),codigo=rs.getString("codigo");
                    return new br.com.rotabrasil.dto.DiagnosticoOperacao.Problema(rs.getString("id"),local.substring(local.lastIndexOf('/')+1),"Raiz autorizada do backend/"+subpasta+(subpasta.isEmpty()?"":"/")+local,"DESTINO_EXPORTACAO",rs.getString("etapa"),codigo,"ARQUIVOS_PENDENTES".equals(codigo)?"ATENCAO":"ERRO",rs.getString("mensagem"),DiagnosticoOperacaoService.causa(codigo),"Corrija a condição indicada e tente somente a exportação.");
                },id);
                var h=historico.get(n);historico.set(n,new Recuperacao(h.id(),h.correlacaoId(),h.inicio(),OffsetDateTime.now(java.time.ZoneOffset.UTC),situacao,concluidos,itens.size()-concluidos,falhas));
            }
            jdbc.update("UPDATE exportacoes SET execucao_em=NULL,recuperacoes=? WHERE id=?",config.escrever(historico),id);
            return consultar(id);
        });
        // Notificação é um efeito separado: não altera nem repete a importação/cópia.
        try{
            boolean ok=resultado.situacao().equals("CONCLUIDA");
            notificacoes.eventoPacote(resultado.pacoteId(),ok?"EXPORTACAO_CONCLUIDA":"PROCESSAMENTO_COM_PENDENCIAS",(ok?"exportacao:":"pendencia-exportacao:")+id,id,
                    ok?"Cópia adicional dos arquivos elegíveis concluída. A importação interna permanece preservada.":"Importação preservada; exportação com pendências. Consulte os itens por referência; nem todos os arquivos foram exportados.",false);
        }catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Aviso de exportação pendente; referência {}",id);}
        limparStaging(resultado);
        return resultado;
    }
    private void limparStaging(Exportacao exportacao){
        try{Path pasta=destinos.resolver(exportacao.subpasta());for(var item:exportacao.arquivos())if("CONCLUIDO".equals(item.situacao()))Files.deleteIfExists(LeituraSeguraArquivos.resolver(pasta,".rotabrasil-exportacoes/"+exportacao.id()+"/"+item.id()+".tmp"));}
        catch(IOException|RuntimeException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Staging de exportação mantido para revisão; referência {}",exportacao.id());}
    }
    private void concluido(String id,int tamanho){jdbc.update("UPDATE exportacao_arquivos SET situacao='CONCLUIDO',etapa='CONCLUSAO',codigo=NULL,mensagem='Bytes copiados e SHA-256 conferido',tamanho=? WHERE id=?",tamanho,id);}
    private ArrayList<Recuperacao> recuperacoes(Map<String,Object> exportacao){
        try{return json.readValue(Objects.toString(exportacao.get("RECUPERACOES"),"[]"),json.getTypeFactory().constructCollectionType(ArrayList.class,Recuperacao.class));}
        catch(Exception ex){throw new IllegalStateException("Histórico de recuperação indisponível; não repetir a cópia sem revisão");}
    }
    private static class FalhaExportacao extends IOException{final String codigo;FalhaExportacao(String codigo,String mensagem){super(mensagem);this.codigo=codigo;}}
    private Map<String,Object> registro(String id){var lista=jdbc.queryForList("SELECT * FROM exportacoes WHERE id=?",id);if(lista.isEmpty())throw new RecursoNaoEncontradoException("Exportação não encontrada");return lista.getFirst();}
    public Exportacao consultar(String id){
        var e=registro(id);
        var itens=jdbc.query("SELECT * FROM exportacao_arquivos WHERE exportacao_id=? ORDER BY localizacao",(rs,n)->new Item(rs.getString("id"),rs.getString("origem"),(Long)rs.getObject("origem_id"),rs.getString("localizacao"),rs.getString("viagens"),rs.getString("sha256"),rs.getLong("tamanho"),rs.getString("situacao"),rs.getString("etapa"),rs.getString("codigo"),rs.getString("mensagem")),id);
        return new Exportacao(id,((Number)e.get("PACOTE_ID")).longValue(),(String)e.get("SUBPASTA"),(String)e.get("SITUACAO"),(String)e.get("MENSAGEM"),(OffsetDateTime)e.get("CRIADA_EM"),itens);
    }
    public List<Exportacao> listar(Long pacoteId){return jdbc.queryForList("SELECT id FROM exportacoes WHERE pacote_id=? ORDER BY criada_em DESC,id DESC",String.class,pacoteId).stream().map(this::consultar).toList();}
}
