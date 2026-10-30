package br.com.rotabrasil.controller;

import br.com.rotabrasil.dto.ArquivamentoPacoteDtos;
import br.com.rotabrasil.service.ArquivamentoPacoteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/pacotes")
public class GerenciamentoPacoteController {
    private final ArquivamentoPacoteService service;
    @GetMapping
    public List<ArquivamentoPacoteDtos.Resumo> listar(@RequestParam(defaultValue="0") @Min(0) int pagina,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int tamanho,@RequestParam(defaultValue="false") boolean arquivados) {
        return service.listar(pagina,tamanho,arquivados);
    }
    @GetMapping("/{id}/conferencia-exclusao")
    public ArquivamentoPacoteDtos.Conferencia conferir(@PathVariable @Positive Long id) { return service.conferir(id); }
    @DeleteMapping("/{id}")
    public ArquivamentoPacoteDtos.Resumo arquivar(@PathVariable @Positive Long id,@RequestParam String conferenciaSha256,
            @RequestParam(defaultValue="false") boolean confirmado,HttpServletRequest request) {
        var resultado=service.arquivar(id,conferenciaSha256,confirmado);
        request.setAttribute("auditoriaReferencia",Map.of("pacoteId",id));
        return resultado;
    }
}
