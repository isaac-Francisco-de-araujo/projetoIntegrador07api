package br.com.rotabrasil.controller;

import br.com.rotabrasil.model.Cliente;
import br.com.rotabrasil.service.ClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/clientes") @RequiredArgsConstructor
public class ClienteController {
    private final ClienteService service;
    @GetMapping public List<Cliente> listar() { return service.listarClientes(); }
    @GetMapping("/{id}") public Cliente buscar(@PathVariable Long id) { return service.buscarCliente(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Cliente cadastrar(@Valid @RequestBody Cliente cliente) { return service.cadastrarCliente(cliente); }
    @PutMapping("/{id}") public Cliente atualizar(@PathVariable Long id, @Valid @RequestBody Cliente cliente) { return service.atualizarCliente(id, cliente); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void excluir(@PathVariable Long id) { service.excluirCliente(id); }
}
