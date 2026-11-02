package br.com.rotabrasil.controller;

import br.com.rotabrasil.dto.OperacaoDtos.*;
import br.com.rotabrasil.exception.ArquivoInvalidoException;
import br.com.rotabrasil.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.List;

@RestController @RequiredArgsConstructor
public class ConfiguracaoOperacaoController {
    private final ConfiguracaoOperacaoService configuracao;
    private final DestinoExportacaoService destinos;
    private final ExportacaoPacoteService exportacoes;
    private final NotificacaoRecebimentoService notificacoes;
    @GetMapping("/api/configuracoes") public Configuracao consultar(){return configuracao.consultar();}
    @PutMapping("/api/configuracoes") public Configuracao salvar(@Valid @RequestBody Salvar pedido){return configuracao.salvar(pedido);}
    @GetMapping("/api/configuracoes/testes") public List<Teste> testes(){return configuracao.testes();}
    @PostMapping("/api/configuracoes/teste-pasta") public Teste testarPasta(){return configuracao.testarPasta();}
    @PostMapping("/api/configuracoes/teste-email") public Teste testarEmail(@Valid @RequestBody TestarEmail pedido){return configuracao.testarEmail(pedido);}
    @PostMapping("/api/configuracoes/subpastas") public List<String> criarPasta(@Valid @RequestBody CriarPasta pedido){
        try{destinos.criar(pedido.nome());return destinos.listar();}
        catch(IOException ex){throw new ArquivoInvalidoException("Não foi possível criar a subpasta: "+DestinoExportacaoService.codigo(ex));}
    }
    @GetMapping("/api/pacotes/{id}/exportacoes") public List<Exportacao> listar(@PathVariable @Positive Long id){return exportacoes.listar(id);}
    @PostMapping("/api/pacotes/{id}/exportacoes") public Exportacao exportar(@PathVariable @Positive Long id,@Valid @RequestBody Confirmar pedido){return exportacoes.exportar(id,pedido.requisicaoId());}
    @GetMapping("/api/exportacoes/{id}") public Exportacao consultarExportacao(@PathVariable java.util.UUID id){return exportacoes.consultar(id.toString());}
    @PostMapping("/api/exportacoes/{id}/retomar") public Exportacao retomar(@PathVariable java.util.UUID id,@Valid @RequestBody Confirmar pedido){return exportacoes.retomar(id.toString(),pedido.requisicaoId());}
    @GetMapping("/api/recebimentos") public Recebimento porHash(@RequestParam @Pattern(regexp="[0-9a-f]{64}") String sha256){return notificacoes.porHash(sha256);}
    @GetMapping("/api/recebimentos/{id}") public Recebimento consultarRecebimento(@PathVariable java.util.UUID id){return notificacoes.consultar(id.toString());}
    @PostMapping("/api/recebimentos/{id}/notificacoes/{notificacaoId}/simular")
    public Notificacao simular(@PathVariable java.util.UUID id,@PathVariable java.util.UUID notificacaoId,@Valid @RequestBody Confirmar pedido){return notificacoes.simular(id.toString(),notificacaoId.toString());}
    @PostMapping("/api/recebimentos/{id}/conferencias-confirmacao")
    public br.com.rotabrasil.dto.ConfirmacaoRecebimentoDtos.Conferencia conferir(@PathVariable java.util.UUID id,@Valid @RequestBody br.com.rotabrasil.dto.ConfirmacaoRecebimentoDtos.Conferir pedido){return notificacoes.conferir(id.toString(),pedido);}
    @PostMapping("/api/recebimentos/{id}/confirmacoes")
    public Notificacao confirmar(@PathVariable java.util.UUID id,@Valid @RequestBody br.com.rotabrasil.dto.ConfirmacaoRecebimentoDtos.Confirmar pedido){return notificacoes.confirmar(id.toString(),pedido);}
    @PostMapping("/api/configuracoes/conferencias-email-real")
    public br.com.rotabrasil.dto.ConfirmacaoRecebimentoDtos.Conferencia conferirTeste(@Valid @RequestBody br.com.rotabrasil.dto.ConfirmacaoRecebimentoDtos.Conferir pedido){return notificacoes.conferirTeste(pedido);}
    @PostMapping("/api/configuracoes/confirmacoes-email-real")
    public Teste confirmarTeste(@Valid @RequestBody br.com.rotabrasil.dto.ConfirmacaoRecebimentoDtos.Confirmar pedido){return notificacoes.confirmarTeste(pedido);}
    @GetMapping("/api/configuracoes/conferencias-email-real/{id}")
    public br.com.rotabrasil.dto.ConfirmacaoRecebimentoDtos.Conferencia consultarTeste(@PathVariable java.util.UUID id){var c=notificacoes.consultarConferencia(id.toString());if(!c.snapshot().teste())throw new br.com.rotabrasil.exception.RecursoNaoEncontradoException("Conferência de teste não encontrada");return c;}
}
