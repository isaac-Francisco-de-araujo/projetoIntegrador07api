package br.com.rotabrasil.controller;

import br.com.rotabrasil.dto.EnvioManualDtos.*;
import br.com.rotabrasil.service.email.EnvioManualService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/viagens/{viagemId}")
@RequiredArgsConstructor
public class EnvioManualController {
    private final EnvioManualService service;
    @PostMapping("/conferencias-email")
    public Conferencia conferir(@PathVariable @Positive Long viagemId, @Valid @RequestBody Conferir pedido) {
        return service.conferir(viagemId,pedido);
    }
    @PostMapping("/tentativas-email")
    public Tentativa confirmar(@PathVariable @Positive Long viagemId, @Valid @RequestBody Confirmar pedido) {
        return service.confirmar(viagemId,pedido);
    }
    @GetMapping("/tentativas-email")
    public List<Tentativa> listar(@PathVariable @Positive Long viagemId) { return service.listar(viagemId); }
    @GetMapping("/tentativas-email/{id}")
    public Tentativa buscar(@PathVariable @Positive Long viagemId, @PathVariable String id) { return service.buscar(viagemId,id); }
}
