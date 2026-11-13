package br.com.rotabrasil.controller;

import br.com.rotabrasil.exception.*;
import br.com.rotabrasil.service.CoordenacaoImportacaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor
public class AdministracaoController {
    private final JdbcTemplate jdbc;private final PasswordEncoder encoder;private final CoordenacaoImportacaoService coordenacao;
    private final br.com.rotabrasil.config.LimiteProcessamentosFilter capacidade;
    public record NovoUsuario(@Pattern(regexp="[A-Za-z0-9._-]{3,100}") @NotNull String usuario,
                              @NotBlank @Size(min=12,max=72) String senha,@NotNull @Pattern(regexp="CONSULTA|OPERADOR|ADMIN") String papel){
        @Override public String toString(){return "NovoUsuario[credenciais omitidas]";}
    }
    public record AlterarUsuario(@NotNull @Pattern(regexp="CONSULTA|OPERADOR|ADMIN") String papel,boolean ativo,@Min(0) long versao){}
    public record RedefinirSenha(@NotBlank @Size(min=12,max=72) String senha,@Min(0) long versao) {
        @Override public String toString(){return "RedefinirSenha[omitida]";}
    }
    @PostMapping("/usuarios/{id}/senha") @Transactional
    public Map<String,Object> redefinir(@PathVariable @Positive long id,@Valid @RequestBody RedefinirSenha pedido){
        coordenacao.bloquear();
        if(pedido.senha().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new ArquivoInvalidoException("Senha excede 72 bytes UTF-8");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE id=?",Integer.class,id)==0)throw new RecursoNaoEncontradoException("Usuário não encontrado");
        if(jdbc.update("UPDATE usuarios SET senha_hash=?,versao=versao+1 WHERE id=? AND versao=?",encoder.encode(pedido.senha()),id,pedido.versao())!=1)throw new ConflitoRecursoException("Operador atualizado; recarregue antes de redefinir o acesso");
        return Map.of("atualizado",true);
    }
    @GetMapping("/usuarios") public List<Map<String,Object>> usuarios(){return jdbc.queryForList("SELECT id,usuario,papel,ativo,criado_em,versao FROM usuarios ORDER BY id");}
    @PostMapping("/usuarios") @Transactional
    public Map<String,Object> criar(@Valid @RequestBody NovoUsuario pedido){
        coordenacao.bloquear();if(pedido.senha().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new ArquivoInvalidoException("Senha excede 72 bytes UTF-8");
        jdbc.update("INSERT INTO usuarios(usuario,senha_hash,papel,criado_em) VALUES(?,?,?,CURRENT_TIMESTAMP)",pedido.usuario(),encoder.encode(pedido.senha()),pedido.papel());
        return Map.of("usuario",pedido.usuario(),"papel",pedido.papel());
    }
    @PutMapping("/usuarios/{id}") @Transactional
    public Map<String,Object> alterar(@PathVariable @Positive long id,@Valid @RequestBody AlterarUsuario pedido){
        coordenacao.bloquear();var atual=jdbc.queryForList("SELECT papel,ativo FROM usuarios WHERE id=?",id);
        if(atual.isEmpty())throw new RecursoNaoEncontradoException("Usuário não encontrado");
        if("ADMIN".equals(atual.getFirst().get("PAPEL"))&&Boolean.TRUE.equals(atual.getFirst().get("ATIVO"))&&(!pedido.ativo()||!pedido.papel().equals("ADMIN"))&&
                jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE papel='ADMIN' AND ativo=TRUE",Integer.class)<=1)
            throw new ConflitoRecursoException("É necessário manter ao menos um administrador ativo");
        if(jdbc.update("UPDATE usuarios SET papel=?,ativo=?,versao=versao+1 WHERE id=? AND versao=?",pedido.papel(),pedido.ativo(),id,pedido.versao())!=1)throw new ConflitoRecursoException("Operador atualizado; recarregue para revisar as permissões");
        return Map.of("atualizado",true);
    }
    @GetMapping("/auditoria") public List<Map<String,Object>> auditoria(@RequestParam(defaultValue="0") @Min(0) int pagina){
        return jdbc.queryForList("SELECT * FROM auditoria ORDER BY id DESC LIMIT 50 OFFSET ?",(long)pagina*50);
    }
    public record Retencao(@Min(30) @Max(3650) int diasProcessamentos,@AssertTrue boolean confirmado){}
    @GetMapping("/retencao") public Map<String,Object> previaRetencao(@RequestParam(defaultValue="365") @Min(30) @Max(3650) int dias){
        return Map.of("dias",dias,"conferenciasExpiradasSemTentativa",jdbc.queryForObject("SELECT COUNT(*) FROM conferencias_email c WHERE expira_em<CURRENT_TIMESTAMP AND NOT EXISTS(SELECT 1 FROM tentativas_email t WHERE t.conferencia_id=c.id)",Integer.class),
                "processamentosAntigosSemDescendentes",jdbc.queryForObject("SELECT COUNT(*) FROM processamentos p WHERE situacao<>'RESULTADO_DESCONHECIDO' AND criado_em<DATEADD('DAY',?,CURRENT_TIMESTAMP) AND NOT EXISTS(SELECT 1 FROM processamentos f WHERE f.anterior_id=p.id) AND NOT EXISTS(SELECT 1 FROM responsabilidades r WHERE r.origem='PROCESSAMENTO' AND r.referencia=p.id)",Integer.class,-dias));
    }
    @PostMapping("/retencao") @Transactional public Map<String,Object> aplicarRetencao(@Valid @RequestBody Retencao pedido){
        coordenacao.bloquear();var conf=jdbc.queryForList("SELECT id FROM conferencias_email c WHERE expira_em<CURRENT_TIMESTAMP AND NOT EXISTS(SELECT 1 FROM tentativas_email t WHERE t.conferencia_id=c.id) LIMIT 1000",String.class);
        conf.forEach(id->jdbc.update("DELETE FROM conferencias_email WHERE id=?",id));
        var historicos=jdbc.queryForList("SELECT id FROM processamentos p WHERE situacao<>'RESULTADO_DESCONHECIDO' AND criado_em<DATEADD('DAY',?,CURRENT_TIMESTAMP) AND NOT EXISTS(SELECT 1 FROM processamentos f WHERE f.anterior_id=p.id) AND NOT EXISTS(SELECT 1 FROM responsabilidades r WHERE r.origem='PROCESSAMENTO' AND r.referencia=p.id) LIMIT 1000",String.class,-pedido.diasProcessamentos());
        historicos.forEach(id->jdbc.update("DELETE FROM processamentos WHERE id=?",id));
        return Map.of("conferenciasRemovidas",conf.size(),"processamentosRemovidos",historicos.size(),"limitePorTabela",1000);
    }
    @GetMapping("/saude") public Map<String,Object> saude(){
        return Map.of("banco",jdbc.queryForObject("SELECT 1",Integer.class)==1?"DISPONIVEL":"INDISPONIVEL",
                "importacoesDesconhecidas",jdbc.queryForObject("SELECT COUNT(*) FROM processamentos WHERE situacao='RESULTADO_DESCONHECIDO'",Integer.class),
                "tentativasDesconhecidas",jdbc.queryForObject("SELECT COUNT(*) FROM tentativas_email WHERE situacao='DESCONHECIDO'",Integer.class),"capacidade",capacidade.metricas());
    }
}
