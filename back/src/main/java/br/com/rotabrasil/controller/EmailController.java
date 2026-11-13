package br.com.rotabrasil.controller;

import br.com.rotabrasil.dto.*;
import br.com.rotabrasil.service.EmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/viagens/{viagemId}")
@RequiredArgsConstructor
public class EmailController {
    private final EmailService service;

    @PostMapping("/previa-email")
    public PreviaEmailResponse previa(@PathVariable @Positive Long viagemId, @Valid @RequestBody PreviaEmailRequest pedido) {
        return service.prepararPrevia(viagemId, pedido);
    }

    @GetMapping("/envios")
    public List<EnvioResponse> historico(@PathVariable @Positive Long viagemId) {
        return service.listarHistorico(viagemId);
    }
}
