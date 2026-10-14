package br.com.rotabrasil.controller;

import br.com.rotabrasil.model.Viagem;
import br.com.rotabrasil.service.ViagemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/viagens") @RequiredArgsConstructor
public class ViagemController {
    private final ViagemService service;
    @GetMapping public List<Viagem> listar() { return service.listarViagens(); }
    @GetMapping("/{id}") public Viagem buscar(@PathVariable Long id) { return service.buscarViagem(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Viagem cadastrar(@Valid @RequestBody Viagem viagem) { return service.cadastrarViagem(viagem); }
    @PutMapping("/{id}") public Viagem atualizar(@PathVariable Long id, @Valid @RequestBody Viagem viagem) { return service.atualizarViagem(id, viagem); }
}
