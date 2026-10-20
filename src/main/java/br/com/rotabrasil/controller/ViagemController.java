package br.com.rotabrasil.controller;

import br.com.rotabrasil.dto.*;
import br.com.rotabrasil.service.CadastroConsultaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/viagens") @RequiredArgsConstructor
public class ViagemController {
    private final CadastroConsultaService service;
    @GetMapping public List<ViagemResponse> listar() { return service.listarViagens(); }
    @GetMapping("/{id}") public ViagemResponse buscar(@PathVariable @Positive Long id) { return service.buscarViagem(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ViagemResponse cadastrar(@Valid @RequestBody ViagemRequest viagem) { return service.cadastrarViagem(viagem); }
    @PutMapping("/{id}")
    public ViagemResponse atualizar(@PathVariable @Positive Long id, @Valid @RequestBody ViagemRequest viagem) { return service.atualizarViagem(id, viagem); }
}
