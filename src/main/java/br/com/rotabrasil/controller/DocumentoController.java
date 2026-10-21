package br.com.rotabrasil.controller;

import br.com.rotabrasil.dto.*;
import br.com.rotabrasil.service.CadastroConsultaService;
import br.com.rotabrasil.service.ProcessamentoService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController @RequiredArgsConstructor
public class DocumentoController {
    private final CadastroConsultaService consultas;
    private final ProcessamentoService processamentoService;
    @GetMapping("/api/documentos") public List<DocumentoResponse> listar() { return consultas.listarDocumentos(); }
    @GetMapping("/api/viagens/{viagemId}/documentos")
    public List<DocumentoResponse> listarPorViagem(@PathVariable @Positive Long viagemId) { return consultas.listarDocumentosDaViagem(viagemId); }
    @PostMapping(value = "/api/processamento/xml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProcessamentoXmlResponse processarXml(@RequestPart("arquivo") MultipartFile arquivo) { return processamentoService.processarArquivoXml(arquivo); }
    @DeleteMapping("/api/documentos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void arquivar(@PathVariable @Positive Long id) { consultas.arquivarDocumento(id); }
    @PostMapping("/api/documentos/{id}/restaurar")
    public DocumentoResponse restaurar(@PathVariable @Positive Long id) { return consultas.restaurarDocumento(id); }
    @GetMapping("/api/viagens/{viagemId}/documentos/arquivados")
    public List<DocumentoArquivadoResponse> arquivados(@PathVariable @Positive Long viagemId) { return consultas.listarArquivados(viagemId); }
}
