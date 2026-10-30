package br.com.rotabrasil.controller;

import br.com.rotabrasil.exception.*;
import br.com.rotabrasil.repository.*;
import br.com.rotabrasil.service.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@RestController @RequiredArgsConstructor
public class ConsultaOperacionalController {
    private final JdbcTemplate jdbc;private final DocumentoRepository documentos;private final AnexoRepository anexos;
    private final CentralPendenciasService central;
    @Value("${rotabrasil.arquivos.viagens:arquivos/viagens}") private String raiz;
    @GetMapping("/api/consulta/viagens")
    public List<Map<String,Object>> viagens(@RequestParam(defaultValue="0") @Min(0) int pagina,
            @RequestParam(defaultValue="50") @Min(1) @Max(100) int tamanho,@RequestParam(defaultValue="") @Size(max=255) String busca,
            @RequestParam(required=false) LocalDate inicio,@RequestParam(required=false) LocalDate fim,@RequestParam(defaultValue="") @Size(max=255) String situacao){
        String filtro="%"+busca.toLowerCase(Locale.ROOT)+"%";
        return jdbc.queryForList("SELECT v.id AS \"id\",v.numero AS \"numero\",v.data AS \"data\",v.status AS \"status\",v.cliente_id AS \"clienteId\",c.nome AS \"clienteNome\" FROM viagens v JOIN clientes c ON c.id=v.cliente_id WHERE (LOWER(v.numero) LIKE ? OR LOWER(c.nome) LIKE ?) AND (? IS NULL OR v.data>=?) AND (? IS NULL OR v.data<=?) AND (?='' OR v.status=?) ORDER BY v.id LIMIT ? OFFSET ?",
                filtro,filtro,inicio,inicio,fim,fim,situacao,situacao,tamanho,(long)pagina*tamanho);
    }
    @GetMapping("/api/consulta/clientes") public List<Map<String,Object>> clientes(@RequestParam(defaultValue="0") @Min(0) int pagina){
        return jdbc.queryForList("SELECT id AS \"id\",nome AS \"nome\",cnpj AS \"cnpj\",email AS \"email\" FROM clientes ORDER BY id LIMIT 50 OFFSET ?",(long)pagina*50);
    }
    @GetMapping("/api/consulta/resumo") public Map<String,Object> resumo(){
        var pendencias=central.resumo();
        return Map.of("clientes",contar("SELECT COUNT(*) FROM clientes"),"viagens",contar("SELECT COUNT(*) FROM viagens"),"documentos",contar("SELECT COUNT(*) FROM documentos WHERE arquivado_em IS NULL"),
                "processamentosPendentes",pendencias.falhas()+pendencias.atencao(),"operacoesEmProcessamento",pendencias.emProcessamento(),
                "tentativasDesconhecidas",contar("SELECT COUNT(*) FROM tentativas_email WHERE situacao='DESCONHECIDO'"));
    }
    private long contar(String sql){return jdbc.queryForObject(sql,Long.class);}
    @GetMapping("/api/documentos/{id}/arquivo") public ResponseEntity<byte[]> documento(@PathVariable @Positive Long id){
        var d=documentos.findById(id).orElseThrow(()->new RecursoNaoEncontradoException("Documento não encontrado"));return arquivo(d.getCaminho(),d.getSha256(),d.getNomeArquivo(),d.getTipo());
    }
    @GetMapping("/api/anexos/{id}/arquivo") public ResponseEntity<byte[]> anexo(@PathVariable @Positive Long id){
        var a=anexos.findById(id).orElseThrow(()->new RecursoNaoEncontradoException("Anexo não encontrado"));return arquivo(a.getCaminho(),a.getSha256(),a.getNomeArquivo(),"PDF");
    }
    private ResponseEntity<byte[]> arquivo(String caminho,String hash,String nome,String tipo){
        try{
            byte[] bytes=LeituraSeguraArquivos.ler(Path.of(raiz),caminho,20*1024*1024);
            if(hash==null||!hash.equals(ConteudoXml.sha256(bytes)))throw new java.io.IOException();
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(ValidacaoDocumental.nomeSimples(nome),StandardCharsets.UTF_8).build().toString())
                    .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CACHE_CONTROL,"no-store").contentType("PDF".equalsIgnoreCase(tipo)?MediaType.APPLICATION_PDF:MediaType.APPLICATION_OCTET_STREAM).body(bytes);
        }catch(java.io.IOException|IllegalArgumentException ex){throw new ConflitoRecursoException("Arquivo ausente, alterado ou fora da área permitida; solicite reconciliação");}
    }
}
