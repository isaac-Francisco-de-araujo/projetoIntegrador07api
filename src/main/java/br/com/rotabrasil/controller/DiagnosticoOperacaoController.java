package br.com.rotabrasil.controller;

import br.com.rotabrasil.dto.DiagnosticoOperacao;
import br.com.rotabrasil.service.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class DiagnosticoOperacaoController {
    private final CentralPendenciasService central;private final DiagnosticoOperacaoService diagnosticos;
    @GetMapping("/api/pendencias")
    public CentralPendenciasService.Pagina listar(@RequestParam(defaultValue="PRECISA_ACAO") String filtro,
            @RequestParam(defaultValue="0") @Min(0) int pagina,@RequestParam(defaultValue="20") @Min(1) @Max(50) int tamanho){return central.listar(filtro,pagina,tamanho);}
    @GetMapping("/api/pendencias/resumo") public CentralPendenciasService.Resumo resumo(){return central.resumo();}
    @GetMapping("/api/diagnosticos/{origem}/{id}")
    public DiagnosticoOperacao consultar(@PathVariable String origem,@PathVariable java.util.UUID id){return diagnosticos.consultar(origem,id.toString());}
}
