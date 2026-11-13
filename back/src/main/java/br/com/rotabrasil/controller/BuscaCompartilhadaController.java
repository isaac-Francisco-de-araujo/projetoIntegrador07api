package br.com.rotabrasil.controller;
import br.com.rotabrasil.service.BuscaCompartilhadaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController @RequiredArgsConstructor
public class BuscaCompartilhadaController {
    private final BuscaCompartilhadaService service;
    @GetMapping("/api/busca") public BuscaCompartilhadaService.Pagina consultar(
        @RequestParam(defaultValue="VIAGEM") String entidade,@RequestParam(defaultValue="") String busca,@RequestParam(defaultValue="") String numero,
        @RequestParam(required=false) Long cliente,@RequestParam(required=false) Long pacote,@RequestParam(defaultValue="TODOS") String tipo,@RequestParam(defaultValue="TODOS") String estado,
        @RequestParam(defaultValue="") String situacao,@RequestParam(required=false) LocalDate inicio,@RequestParam(required=false) LocalDate fim,@RequestParam(defaultValue="data") String ordem,@RequestParam(defaultValue="DESC") String direcao,
        @RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="20") int tamanho){return service.consultar(entidade,busca,numero,cliente,pacote,tipo,estado,situacao,inicio,fim,ordem,direcao,pagina,tamanho);}
}
