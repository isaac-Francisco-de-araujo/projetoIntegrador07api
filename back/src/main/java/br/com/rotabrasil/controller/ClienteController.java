package br.com.rotabrasil.controller;

import br.com.rotabrasil.dto.*;
import br.com.rotabrasil.service.CadastroConsultaService;
import br.com.rotabrasil.service.ExclusaoClienteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/clientes") @RequiredArgsConstructor
public class ClienteController {
    private final CadastroConsultaService service;
    private final ExclusaoClienteService exclusao;
    private final br.com.rotabrasil.service.EdicaoCompartilhada edicao;
    @GetMapping public List<ClienteResponse> listar() { return service.listarClientes(); }
    @GetMapping("/{id}") public org.springframework.http.ResponseEntity<ClienteResponse> buscar(@PathVariable @Positive Long id) { var c=service.buscarCliente(id);return org.springframework.http.ResponseEntity.ok().eTag(edicao.versao(c)).body(c); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ClienteResponse cadastrar(@Valid @RequestBody ClienteRequest cliente) { return service.cadastrarCliente(cliente); }
    @PutMapping("/{id}")
    public ClienteResponse atualizar(@PathVariable @Positive Long id, @Valid @RequestBody ClienteRequest cliente) { return service.atualizarCliente(id, cliente); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable @Positive Long id,
            @RequestParam(defaultValue="false") boolean confirmado,
            @RequestParam(required=false) String conferenciaSha256) { exclusao.excluir(id,confirmado,conferenciaSha256); }
    @GetMapping("/{id}/conferencia-exclusao")
    public ExclusaoClienteConferencia conferirExclusao(@PathVariable @Positive Long id) { return exclusao.conferir(id); }
}
