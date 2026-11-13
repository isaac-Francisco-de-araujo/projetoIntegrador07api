package br.com.rotabrasil.controller;

import br.com.rotabrasil.service.email.ProntidaoEnvioDocumental;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class EnvioDocumentalController {
    private final ProntidaoEnvioDocumental prontidao;
    @GetMapping("/api/envio-documental")
    public ProntidaoEnvioDocumental.Estado consultar() { return prontidao.consultar(); }
}
