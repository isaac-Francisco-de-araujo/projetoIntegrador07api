package br.com.rotabrasil.controller;

import br.com.rotabrasil.dto.*;
import br.com.rotabrasil.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController @RequiredArgsConstructor
public class PacoteController {
    private final PacoteService pacotes;
    private final AnexoService anexos;
    @PostMapping(value="/api/pacotes/inspecao",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public PacoteDtos.Inspecao inspecionar(@RequestPart("arquivo") MultipartFile arquivo){return pacotes.inspecionar(arquivo);}
    @PostMapping(value="/api/pacotes/confirmacao",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public PacoteDtos.Resultado confirmar(@RequestPart("arquivo") MultipartFile arquivo,@Valid @RequestPart("configuracao") PacoteDtos.Confirmacao configuracao){return pacotes.confirmar(arquivo,configuracao);}
    @GetMapping("/api/viagens/{id}/anexos")
    public List<AnexoResponse> listar(@PathVariable @Positive Long id,@RequestParam(defaultValue="false") boolean arquivados){return anexos.listar(id,arquivados);}
    @DeleteMapping("/api/anexos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void arquivar(@PathVariable @Positive Long id){anexos.arquivar(id);}
    @PostMapping("/api/anexos/{id}/restaurar")
    public AnexoResponse restaurar(@PathVariable @Positive Long id){return anexos.restaurar(id);}
}
