package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.ArquivamentoPacoteDtos;
import br.com.rotabrasil.exception.*;
import br.com.rotabrasil.model.Pacote;
import br.com.rotabrasil.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Arquiva os documentos do pacote, sem remover identidades, vínculos, bytes ou históricos. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ArquivamentoPacoteService {
    private final PacoteRepository pacotes;
    private final DocumentoRepository documentos;
    private final AnexoRepository anexos;
    private final CoordenacaoImportacaoService coordenacao;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public List<ArquivamentoPacoteDtos.Resumo> listar(int pagina,int tamanho,boolean arquivados) {
        if(pagina<0||tamanho<1||tamanho>100)throw new ArquivoInvalidoException("Paginação de pacotes inválida");
        String inativo="""
            NOT EXISTS (SELECT 1 FROM pacote_documentos pd JOIN documentos d ON d.id=pd.documento_id WHERE pd.pacote_id=p.id AND d.arquivado_em IS NULL)
            AND NOT EXISTS (SELECT 1 FROM anexos a WHERE a.pacote_id=p.id AND a.arquivado_em IS NULL)
            """;
        String sql="""
            SELECT p.id,p.nome_arquivo,p.sha256,p.criado_em,
              (SELECT COUNT(*) FROM pacote_documentos pd WHERE pd.pacote_id=p.id) AS documentos,
              (SELECT COUNT(*) FROM pacote_documentos pd JOIN documentos d ON d.id=pd.documento_id WHERE pd.pacote_id=p.id AND d.arquivado_em IS NULL) AS documentos_ativos,
              (SELECT COUNT(*) FROM anexos a WHERE a.pacote_id=p.id) AS anexos,
              (SELECT COUNT(*) FROM anexos a WHERE a.pacote_id=p.id AND a.arquivado_em IS NULL) AS anexos_ativos
            FROM pacotes p WHERE
            """+(arquivados?"(":"NOT (")+inativo+") ORDER BY p.criado_em DESC,p.id DESC LIMIT ? OFFSET ?";
        return jdbc.query(sql,(rs,n)->new ArquivamentoPacoteDtos.Resumo(rs.getLong("id"),rs.getString("nome_arquivo"),rs.getString("sha256"),
                rs.getObject("criado_em",OffsetDateTime.class),rs.getInt("documentos"),rs.getInt("documentos_ativos"),
                rs.getInt("anexos"),rs.getInt("anexos_ativos"),rs.getInt("documentos_ativos")+rs.getInt("anexos_ativos")==0),tamanho,(long)pagina*tamanho);
    }

    public ArquivamentoPacoteDtos.Conferencia conferir(Long id) { return conferencia(buscar(id)); }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ArquivamentoPacoteDtos.Resumo arquivar(Long id,String conferenciaSha256,boolean confirmado) {
        if(!confirmado||conferenciaSha256==null||!conferenciaSha256.matches("[0-9a-f]{64}"))
            throw new ArquivoInvalidoException("Confirme explicitamente a conferência dos arquivos do ZIP");
        coordenacao.bloquear();
        var pacote=buscar(id);
        var conferencia=conferencia(pacote);
        // Recupera com segurança uma exclusão já concluída após repetição ou timeout.
        if(conferencia.pacote().arquivado())return conferencia.pacote();
        if(!conferencia.conferenciaSha256().equals(conferenciaSha256))
            throw new ConflitoRecursoException("Os arquivos ou vínculos mudaram desde a conferência; atualize a lista e confira novamente antes de excluir o ZIP");
        var agora=OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        for(var documento:pacote.getDocumentos())if(documento.getArquivadoEm()==null)documento.setArquivadoEm(agora);
        var listaAnexos=anexos.findByPacoteId(id);
        for(var anexo:listaAnexos)if(anexo.getArquivadoEm()==null)anexo.setArquivadoEm(agora);
        documentos.flush();anexos.flush();
        return resumo(pacote,listaAnexos);
    }

    private Pacote buscar(Long id) {
        if(id==null||id<=0)throw new ArquivoInvalidoException("Identificador de pacote inválido");
        return pacotes.findById(id).orElseThrow(()->new RecursoNaoEncontradoException("ZIP importado não encontrado"));
    }
    private ArquivamentoPacoteDtos.Viagem viagem(br.com.rotabrasil.model.Viagem viagem) {
        return new ArquivamentoPacoteDtos.Viagem(viagem.getId(),viagem.getNumero(),viagem.getCliente().getNome());
    }
    private ArquivamentoPacoteDtos.Resumo resumo(Pacote pacote,List<br.com.rotabrasil.model.Anexo> listaAnexos) {
        int ativos=(int)pacote.getDocumentos().stream().filter(d->d.getArquivadoEm()==null).count();
        int anexosAtivos=(int)listaAnexos.stream().filter(a->a.getArquivadoEm()==null).count();
        return new ArquivamentoPacoteDtos.Resumo(pacote.getId(),pacote.getNomeArquivo(),pacote.getSha256(),pacote.getCriadoEm(),
                pacote.getDocumentos().size(),ativos,listaAnexos.size(),anexosAtivos,ativos+anexosAtivos==0);
    }
    private ArquivamentoPacoteDtos.Conferencia conferencia(Pacote pacote) {
        var listaAnexos=anexos.findByPacoteId(pacote.getId());
        var docs=pacote.getDocumentos().stream().sorted(Comparator.comparing(br.com.rotabrasil.model.Documento::getId))
                .map(d->new ArquivamentoPacoteDtos.Documento(d.getId(),d.getNomeArquivo(),d.getTipo(),d.getSha256(),d.getArquivadoEm(),viagem(d.getViagem()),
                        pacotes.findByDocumentos_Id(d.getId()).stream().map(Pacote::getId).filter(id->!id.equals(pacote.getId())).sorted().toList())).toList();
        var pdfs=listaAnexos.stream().sorted(Comparator.comparing(br.com.rotabrasil.model.Anexo::getId))
                .map(a->new ArquivamentoPacoteDtos.Anexo(a.getId(),a.getNomeArquivo(),a.getSha256(),a.getArquivadoEm(),
                        a.getViagens().stream().sorted(Comparator.comparing(br.com.rotabrasil.model.Viagem::getId)).map(this::viagem).toList())).toList();
        var resumo=resumo(pacote,listaAnexos);
        try {
            String hash=ConteudoXml.sha256(json.writeValueAsBytes(List.of(resumo,docs,pdfs)));
            return new ArquivamentoPacoteDtos.Conferencia(resumo,docs,pdfs,hash);
        }catch(JsonProcessingException ex){throw new IllegalStateException("Não foi possível conferir o arquivamento do ZIP");}
    }
}
