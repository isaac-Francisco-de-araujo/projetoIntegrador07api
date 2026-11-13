package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.ArmazenamentoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Journal de limpeza: só remove bytes sem referências depois do commit do cadastro. */
@Service
public class LimpezaArquivosClienteService {
    private static final Logger log=LoggerFactory.getLogger(LimpezaArquivosClienteService.class);
    private final Path raiz;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CoordenacaoImportacaoService coordenacao;
    private final TransactionTemplate tx;
    public record Arquivo(String caminho,String sha256) {}

    public LimpezaArquivosClienteService(@Value("${rotabrasil.arquivos.viagens:arquivos/viagens}") String raiz,
            JdbcTemplate jdbc,ObjectMapper json,CoordenacaoImportacaoService coordenacao,PlatformTransactionManager manager) {
        this.raiz=Path.of(raiz).toAbsolutePath().normalize(); this.jdbc=jdbc; this.json=json; this.coordenacao=coordenacao;
        tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public void agendar(List<Map<String,Object>> registros) {
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Limpeza exige transação");
        if(registros.isEmpty())return;
        Path journal=null;
        try {
            var arquivos=new LinkedHashMap<Path,Arquivo>();
            for(var registro:registros) {
                Path caminho=LeituraSeguraArquivos.resolver(raiz,(String)registro.get("CAMINHO"));
                if(!Files.exists(caminho,LinkOption.NOFOLLOW_LINKS))continue;
                String hash=ConteudoXml.sha256(LeituraSeguraArquivos.ler(raiz,caminho.toString(),ConteudoXml.LIMITE_BYTES));
                if(registro.get("SHA256")!=null&&!hash.equals(registro.get("SHA256")))
                    throw new IOException("Integridade divergente");
                arquivos.put(caminho,new Arquivo(raiz.relativize(caminho).toString(),hash));
            }
            if(arquivos.isEmpty())return;
            Path pasta=LeituraSeguraArquivos.resolver(raiz,".exclusoes-clientes");
            Files.createDirectories(pasta);
            journal=LeituraSeguraArquivos.resolver(raiz,".exclusoes-clientes/"+UUID.randomUUID()+".json");
            byte[] bytes=json.writeValueAsBytes(arquivos.values());
            try(var canal=FileChannel.open(journal,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) {
                var buffer=java.nio.ByteBuffer.wrap(bytes); while(buffer.hasRemaining())canal.write(buffer); canal.force(true);
            }
            Path pendencia=journal;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if(status==STATUS_COMMITTED)limpar(pendencia);
                    else if(status==STATUS_ROLLED_BACK)try{Files.deleteIfExists(pendencia);}catch(IOException ex){log.warn("Journal de exclusão pendente de revisão: {}",pendencia.getFileName());}
                }
            });
        }catch(IOException|IllegalArgumentException ex){
            if(journal!=null)try{Files.deleteIfExists(journal);}catch(IOException ignorada){}
            throw new ArmazenamentoException("Não foi possível preparar a remoção íntegra dos arquivos do cliente");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recuperarPendentes() {
        try {
            Path pasta=LeituraSeguraArquivos.resolver(raiz,".exclusoes-clientes");
            if(!Files.isDirectory(pasta,LinkOption.NOFOLLOW_LINKS))return;
            try(var lista=Files.newDirectoryStream(pasta,"*.json")){for(Path journal:lista)limpar(journal);}
        }catch(IOException|RuntimeException ex){log.warn("Limpeza de clientes pendente; revisão do armazenamento necessária");}
    }

    private void limpar(Path journal) {
        try {
            tx.executeWithoutResult(status->{
                coordenacao.bloquear();
                try {
                    var candidatos=json.readValue(LeituraSeguraArquivos.ler(raiz,journal.toString(),ConteudoXml.LIMITE_BYTES),Arquivo[].class);
                    var referenciados=new HashSet<Path>();
                    for(String caminho:jdbc.queryForList("SELECT caminho FROM documentos UNION SELECT caminho FROM anexos",String.class))
                        referenciados.add(LeituraSeguraArquivos.resolver(raiz,caminho));
                    for(var arquivo:candidatos) {
                        Path caminho=LeituraSeguraArquivos.resolver(raiz,arquivo.caminho());
                        if(caminho.startsWith(journal.getParent()))throw new IOException("Alvo de limpeza inválido");
                        if(referenciados.contains(caminho)||!Files.exists(caminho,LinkOption.NOFOLLOW_LINKS))continue;
                        String hash=ConteudoXml.sha256(LeituraSeguraArquivos.ler(raiz,caminho.toString(),ConteudoXml.LIMITE_BYTES));
                        if(!hash.equals(arquivo.sha256()))throw new IOException("Arquivo alterado após exclusão");
                        Files.delete(caminho);
                    }
                    Files.deleteIfExists(journal);
                }catch(IOException ex){throw new IllegalStateException(ex);}
            });
        }catch(RuntimeException ex){log.warn("Registros excluídos; limpeza de arquivos pendente no journal {}",journal.getFileName());}
    }
}
