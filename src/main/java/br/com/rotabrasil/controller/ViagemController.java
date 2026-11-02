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
    private final br.com.rotabrasil.service.EdicaoCompartilhada edicao;
    @GetMapping public List<ViagemResponse> listar() { return service.listarViagens(); }
    @GetMapping("/{id}") public org.springframework.http.ResponseEntity<ViagemResponse> buscar(@PathVariable @Positive Long id) { var v=service.buscarViagem(id);return org.springframework.http.ResponseEntity.ok().eTag(edicao.versao(v)).body(v); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ViagemResponse cadastrar(@Valid @RequestBody ViagemRequest viagem) { return service.cadastrarViagem(viagem); }
    @PutMapping("/{id}")
    public ViagemResponse atualizar(@PathVariable @Positive Long id, @Valid @RequestBody ViagemRequest viagem) { return service.atualizarViagem(id, viagem); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable @Positive Long id) { service.excluirViagem(id); }
}
