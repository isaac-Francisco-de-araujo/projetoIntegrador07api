package br.com.rotabrasil.controller;

import br.com.rotabrasil.dto.*;
import br.com.rotabrasil.service.CadastroConsultaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/clientes") @RequiredArgsConstructor
public class ClienteController {
    private final CadastroConsultaService service;
    @GetMapping public List<ClienteResponse> listar() { return service.listarClientes(); }
    @GetMapping("/{id}") public ClienteResponse buscar(@PathVariable @Positive Long id) { return service.buscarCliente(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ClienteResponse cadastrar(@Valid @RequestBody ClienteRequest cliente) { return service.cadastrarCliente(cliente); }
    @PutMapping("/{id}")
    public ClienteResponse atualizar(@PathVariable @Positive Long id, @Valid @RequestBody ClienteRequest cliente) { return service.atualizarCliente(id, cliente); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable @Positive Long id) { service.excluirCliente(id); }
}
