package br.com.rotabrasil.controller;
import br.com.rotabrasil.service.SaudeCompartilhadaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class SaudeCompartilhadaController {
    private final SaudeCompartilhadaService service;
    @GetMapping("/api/saude") public SaudeCompartilhadaService.Painel consultar(){var a=SecurityContextHolder.getContext().getAuthentication();boolean admin=a!=null&&a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_ADMIN"));return service.consultar(admin);}
}
