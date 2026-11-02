package br.com.rotabrasil.controller;

import br.com.rotabrasil.service.FilaTrabalhoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api/fila") @RequiredArgsConstructor
public class FilaTrabalhoController {
    private final FilaTrabalhoService service;
    @GetMapping public FilaTrabalhoService.Pagina listar(@RequestParam(defaultValue="TODOS") String escopo,@RequestParam(defaultValue="TODAS") String situacao,@RequestParam(defaultValue="") String responsavel,@RequestParam(required=false) LocalDate inicio,@RequestParam(required=false) LocalDate fim,@RequestParam(defaultValue="ENTRADA") String ordem,@RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="20") int tamanho){return service.listar(escopo,situacao,responsavel,inicio,fim,ordem,pagina,tamanho);}
    @GetMapping("/responsaveis") public List<Map<String,Object>> responsaveis(){return service.responsaveis();}
    @GetMapping("/{origem}/{id}/historico") public List<Map<String,Object>> historico(@PathVariable String origem,@PathVariable String id){return service.historico(origem,id);}
    public record Atribuir(@NotNull @Pattern(regexp="ASSUMIR|LIBERAR|TRANSFERIR") String acao,@Min(0) long versao,@Positive Long destino,boolean confirmado){}
    @PostMapping("/{origem}/{id}/responsabilidade") public Map<String,Object> atribuir(@PathVariable String origem,@PathVariable String id,@Valid @RequestBody Atribuir pedido){service.atribuir(origem,id,pedido.acao(),pedido.versao(),pedido.destino(),pedido.confirmado());return Map.of("atualizado",true);}
}
