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
    private final AnexoService anexos;
    private final HistoricoProcessamentoService historico;
    private final NotificacaoRecebimentoService notificacoes;
    private final FluxoPacoteOperacionalService fluxo;
    private final PreviaPdfPacoteService previasPdf;
    @PostMapping(value="/api/pacotes/previa-pdf",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> previaPdf(@RequestPart("arquivo") MultipartFile arquivo,
            @RequestParam String sha256,@RequestParam String arquivoId){
        var entrada=previasPdf.obter(arquivo,sha256,arquivoId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CACHE_CONTROL,"no-store")
                .header("X-Content-Type-Options","nosniff")
                .header("Content-Security-Policy","sandbox; default-src 'none'")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment()
                        .filename(entrada.resumo().nomeArquivo(),java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(entrada.bytes());
    }
    @PostMapping(value="/api/pacotes/inspecao",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public PacoteDtos.Inspecao inspecionar(@RequestPart("arquivo") MultipartFile arquivo,@RequestParam(required=false) String anteriorOperacaoId,
            @RequestParam(required=false) String destinatarioConfirmacao,@RequestParam(defaultValue="false") boolean simularConfirmacao,
            jakarta.servlet.http.HttpServletResponse response){
        String recebimento=notificacoes.receber(arquivo,destinatarioConfirmacao,simularConfirmacao);
        response.setHeader("X-Recebimento-Id",recebimento);
        return fluxo.inspecionar(arquivo,anteriorOperacaoId,recebimento);
    }
    @PostMapping(value="/api/pacotes/confirmacao",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public PacoteDtos.Resultado confirmar(@RequestPart("arquivo") MultipartFile arquivo,@Valid @RequestPart("configuracao") PacoteDtos.Confirmacao configuracao,@RequestParam(required=false) String anteriorOperacaoId,jakarta.servlet.http.HttpServletRequest request){
         String recebimento=notificacoes.receber(arquivo,null,false);
         var resultado=fluxo.confirmar(arquivo,configuracao,anteriorOperacaoId,recebimento);request.setAttribute("auditoriaReferencia",java.util.Map.of("pacoteId",resultado.pacoteId(),"operacaoId",resultado.operacaoId()));return resultado;
    }
    @GetMapping("/api/processamentos") public List<java.util.Map<String,Object>> processamentos(@RequestParam(defaultValue="0") @jakarta.validation.constraints.Min(0) int pagina,@RequestParam(required=false) String situacao){return historico.listar(pagina,situacao);}
    @GetMapping("/api/processamentos/{id}") public java.util.Map<String,Object> processamento(@PathVariable String id){return historico.buscar(id);}
    @GetMapping("/api/viagens/{id}/anexos")
    public List<AnexoResponse> listar(@PathVariable @Positive Long id,@RequestParam(defaultValue="false") boolean arquivados){return anexos.listar(id,arquivados);}
    @DeleteMapping("/api/anexos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void arquivar(@PathVariable @Positive Long id){anexos.arquivar(id);}
    @PostMapping("/api/anexos/{id}/restaurar")
    public AnexoResponse restaurar(@PathVariable @Positive Long id){return anexos.restaurar(id);}
}
