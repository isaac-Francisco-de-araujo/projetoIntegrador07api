package br.com.rotabrasil.controller;

import br.com.rotabrasil.dto.ProcessamentoXmlResponse;
import br.com.rotabrasil.model.Documento;
import br.com.rotabrasil.service.DocumentoService;
import br.com.rotabrasil.service.ProcessamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController @RequiredArgsConstructor
public class DocumentoController {
    private final DocumentoService documentoService;
    private final ProcessamentoService processamentoService;
    @GetMapping("/api/documentos") public List<Documento> listar() { return documentoService.listarDocumentos(); }
    @GetMapping("/api/viagens/{viagemId}/documentos") public List<Documento> listarPorViagem(@PathVariable Long viagemId) { return documentoService.listarDocumentosDaViagem(viagemId); }
    @PostMapping(value = "/api/processamento/xml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProcessamentoXmlResponse processarXml(@RequestPart("arquivo") MultipartFile arquivo) { return processamentoService.processarArquivoXml(arquivo); }
}
