package br.com.rotabrasil.controller;

import br.com.rotabrasil.service.ContaTesteEmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/configuracoes/conta-teste")
public class ContaTesteEmailController {
    private final ContaTesteEmailService conta;
    @GetMapping public ContaTesteEmailService.Estado consultar(){return conta.consultar();}
    @GetMapping("/ultima-verificacao") public java.util.Map<String,String> ultimaVerificacao(){return conta.ultimaVerificacao();}
    @PutMapping public ContaTesteEmailService.Estado salvar(@Valid @RequestBody ContaTesteEmailService.Salvar pedido){return conta.salvar(pedido);}
}
