package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.ArquivoInvalidoException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

/** Projeções SQL parametrizadas; um PDF compartilhado é uma linha, não uma por viagem. */
@Service @RequiredArgsConstructor
public class BuscaCompartilhadaService {
    private final JdbcTemplate jdbc;
    public record Pagina(String entidade,int pagina,int tamanho,long total,List<Map<String,Object>> itens,String dataUtilizada){}
    private static final String VIAGENS="""
        SELECT v.id,v.numero AS nome,v.numero,v.data,v.status,v.cliente_id AS clienteId,
          c.nome AS clienteNome,c.cnpj FROM viagens v JOIN clientes c ON c.id=v.cliente_id
        """;
    private static final String ARQUIVOS="""
        SELECT 'DOCUMENTO' AS origem,d.id,d.nome_arquivo AS nome,d.tipo,d.arquivado_em AS arquivadoEm,
          d.viagem_id AS viagemId,(SELECT MIN(p.criado_em) FROM pacotes p JOIN pacote_documentos pd ON pd.pacote_id=p.id WHERE pd.documento_id=d.id) AS data
        FROM documentos d
        UNION ALL
        SELECT 'ANEXO',a.id,a.nome_arquivo,'PDF',a.arquivado_em,
          (SELECT MIN(av.viagem_id) FROM anexo_viagens av WHERE av.anexo_id=a.id),p.criado_em
        FROM anexos a JOIN pacotes p ON p.id=a.pacote_id
        """;
    public Pagina consultar(String entidade,String busca,String numero,Long cliente,Long pacote,String tipo,String estado,String situacao,LocalDate inicio,LocalDate fim,String ordem,String direcao,int pagina,int tamanho){
        if(!Set.of("CLIENTE","VIAGEM","ARQUIVO","PACOTE").contains(entidade)||busca.length()>255||numero.length()>9||!numero.isEmpty()&&!numero.matches("[0-9]+")||cliente!=null&&cliente<1||pacote!=null&&pacote<1||!Set.of("TODOS","XML","PDF").contains(tipo)||!Set.of("TODOS","ATIVO","ARQUIVADO").contains(estado)||!Set.of("nome","numero","cliente","data","status").contains(ordem)||!Set.of("ASC","DESC").contains(direcao)||pagina<0||pagina>1000000||tamanho<1||tamanho>100||inicio!=null&&fim!=null&&inicio.isAfter(fim))throw new ArquivoInvalidoException("Busca, intervalo, ordenação ou paginação inválidos");
        if(busca.matches("[0-9\\s./-]+"))busca=busca.replaceAll("[^0-9]","");
        String sql=switch(entidade){case "CLIENTE"->"SELECT id,nome,cnpj,email,telefone FROM clientes";case "VIAGEM"->VIAGENS;case "ARQUIVO"->ARQUIVOS;default->"SELECT p.id,p.nome_arquivo AS nome,p.criado_em AS data,CASE WHEN EXISTS(SELECT 1 FROM pacote_documentos pd JOIN documentos d ON d.id=pd.documento_id WHERE pd.pacote_id=p.id AND d.arquivado_em IS NULL) OR EXISTS(SELECT 1 FROM anexos a WHERE a.pacote_id=p.id AND a.arquivado_em IS NULL) THEN 'ATIVO' ELSE 'ARQUIVADO' END AS estado FROM pacotes p";};
        String where=" WHERE 1=1";var args=new ArrayList<Object>();String termo="%"+busca.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        if(situacao.length()>255)throw new ArquivoInvalidoException("Situação inválida");
        if(!busca.isBlank()){
            where+=switch(entidade){case "CLIENTE"->" AND (LOWER(nome) LIKE ? ESCAPE '!' OR cnpj LIKE ? ESCAPE '!')";case "VIAGEM"->" AND (LOWER(nome) LIKE ? ESCAPE '!' OR LOWER(clienteNome) LIKE ? ESCAPE '!' OR cnpj LIKE ? ESCAPE '!')";case "ARQUIVO"->" AND (LOWER(nome) LIKE ? ESCAPE '!' OR EXISTS(SELECT 1 FROM viagens v JOIN clientes c ON c.id=v.cliente_id WHERE ((x.origem='DOCUMENTO' AND v.id=x.viagemId) OR (x.origem='ANEXO' AND EXISTS(SELECT 1 FROM anexo_viagens av WHERE av.anexo_id=x.id AND av.viagem_id=v.id))) AND (LOWER(c.nome) LIKE ? ESCAPE '!' OR c.cnpj LIKE ? ESCAPE '!' OR v.numero LIKE ? ESCAPE '!')))";default->" AND LOWER(nome) LIKE ? ESCAPE '!'";};
            args.add(termo);if(entidade.equals("CLIENTE")||entidade.equals("VIAGEM"))args.add(termo);if(entidade.equals("VIAGEM"))args.add(termo);
            if(entidade.equals("ARQUIVO")){args.add(termo);args.add(termo);args.add(termo);}
        }
        if(entidade.equals("VIAGEM")){
            if(!situacao.isBlank()){where+=" AND status=?";args.add(situacao);}
            if(cliente!=null){where+=" AND clienteId=?";args.add(cliente);}
            if(!numero.isEmpty()){where+=" AND numero=?";args.add(numero);}
        }
        if(entidade.equals("ARQUIVO")){
            if(!tipo.equals("TODOS")){where+=" AND UPPER(tipo)=?";args.add(tipo);}
            if(!estado.equals("TODOS"))where+=" AND arquivadoEm IS "+(estado.equals("ATIVO")?"NULL":"NOT NULL");
            if(pacote!=null){where+=" AND ((origem='DOCUMENTO' AND EXISTS(SELECT 1 FROM pacote_documentos pd WHERE pd.documento_id=x.id AND pd.pacote_id=?)) OR (origem='ANEXO' AND EXISTS(SELECT 1 FROM anexos a WHERE a.id=x.id AND a.pacote_id=?)))";args.add(pacote);args.add(pacote);}
            if(cliente!=null||!numero.isEmpty()||!busca.isBlank()){
                // Arquivos também podem ser localizados pelo cliente/CT-e sem multiplicar faturas.
                if(cliente!=null||!numero.isEmpty()){
                    where+=" AND EXISTS(SELECT 1 FROM viagens v WHERE ((x.origem='DOCUMENTO' AND v.id=x.viagemId) OR (x.origem='ANEXO' AND EXISTS(SELECT 1 FROM anexo_viagens av WHERE av.anexo_id=x.id AND av.viagem_id=v.id)))";
                    if(cliente!=null){where+=" AND v.cliente_id=?";args.add(cliente);}if(!numero.isEmpty()){where+=" AND v.numero=?";args.add(numero);}where+=")";
                }
            }
        }
        if(entidade.equals("PACOTE")){if(pacote!=null){where+=" AND id=?";args.add(pacote);}if(!estado.equals("TODOS")){where+=" AND estado=?";args.add(estado);}}
        if(inicio!=null||fim!=null){
            if(entidade.equals("CLIENTE"))throw new ArquivoInvalidoException("Cliente não possui data de emissão/importação registrada para este filtro");
            if(inicio!=null){where+=" AND data>=?";args.add(inicio);}
            if(fim!=null){where+=" AND data<?";args.add(fim.plusDays(1));}
        }
        String base="WITH x AS ("+sql+") ";long total=jdbc.queryForObject(base+"SELECT COUNT(*) FROM x"+where,Long.class,args.toArray());
        String coluna=switch(ordem){case "cliente"->entidade.equals("VIAGEM")?"clienteNome":"nome";case "numero"->entidade.equals("VIAGEM")?"numero":"nome";case "status"->entidade.equals("VIAGEM")?"status":"nome";case "data"->entidade.equals("CLIENTE")?"nome":"data";default->"nome";};
        args.add(tamanho);args.add((long)pagina*tamanho);
        var itens=jdbc.query(base+"SELECT * FROM x"+where+" ORDER BY "+coluna+" "+direcao+" NULLS LAST,"+(entidade.equals("ARQUIVO")?"origem,":"")+"id DESC LIMIT ? OFFSET ?",(rs,n)->{
            Map<String,Object> item=new LinkedHashMap<>();var m=rs.getMetaData();for(int i=1;i<=m.getColumnCount();i++){
                String key=m.getColumnLabel(i).toLowerCase(Locale.ROOT);key=switch(key){case "clienteid"->"clienteId";case "clientenome"->"clienteNome";case "viagemid"->"viagemId";case "arquivadoem"->"arquivadoEm";default->key;};item.put(key,rs.getObject(i));
            }return item;
        },args.toArray());
        return new Pagina(entidade,pagina,tamanho,total,itens,switch(entidade){case "VIAGEM"->"Data da viagem (emissão extraída do CT-e quando importada)";case "CLIENTE"->"Não disponível";default->"Importação do pacote em UTC; XML individual sem pacote pode não ter data registrada";});
    }
}
