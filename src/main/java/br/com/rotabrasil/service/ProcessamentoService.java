package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.ProcessamentoXmlResponse;
import br.com.rotabrasil.exception.ArquivoInvalidoException;
import br.com.rotabrasil.model.Documento;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProcessamentoService {
    private final ExtratorDadosXml extratorDadosXml;
    private final ArmazenamentoTransacionalService armazenamento;
    private final ViagemService viagemService;
    private final DocumentoService documentoService;
    private final ClienteService clienteService;
    private final CoordenacaoImportacaoService coordenacao;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProcessamentoXmlResponse processarArquivoXml(MultipartFile arquivo) {
        validarArquivo(arquivo);
        byte[] conteudo;
        try (var entrada = arquivo.getInputStream()) {
            conteudo = ConteudoXml.ler(entrada);
        } catch (IOException ex) {
            throw new ArquivoInvalidoException("Não foi possível ler o arquivo XML", ex);
        }
        var dados = extratorDadosXml.extrair(new ByteArrayInputStream(conteudo));
        String hash = ConteudoXml.sha256(conteudo);
        coordenacao.bloquear();
        var existente = documentoService.buscarExistente(dados, hash);
        if (existente.isPresent()) {
            Documento documento = existente.get();
            return new ProcessamentoXmlResponse(true, "Documento já registrado; conteúdo idêntico",
                    documento.getViagem().getId(), documento.getId());
        }

        var cliente = clienteService.buscarOuCadastrar(dados.nomeCliente(), dados.cnpjCliente(),
                dados.emailCliente(), dados.telefoneCliente());
        var viagem = viagemService.buscarOuCadastrar(dados.numero(), dados.data(), dados.status(), cliente);
        var caminho = armazenamento.preparar(conteudo, dados.numero(), dados.chaveAcesso());
        var documento = documentoService.registrarDocumento(Documento.builder()
                .nomeArquivo(arquivo.getOriginalFilename())
                .tipo("XML")
                .caminho(caminho.toString())
                .chaveAcesso(dados.chaveAcesso())
                .sha256(hash)
                .viagem(viagem)
                .build());
        return new ProcessamentoXmlResponse(true, "XML processado com sucesso", viagem.getId(), documento.getId());
    }

    private void validarArquivo(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) throw new ArquivoInvalidoException("Arquivo XML ausente ou vazio");
        String nome = arquivo.getOriginalFilename() == null ? "" : arquivo.getOriginalFilename();
        if (!nome.toLowerCase(Locale.ROOT).endsWith(".xml")) throw new ArquivoInvalidoException("A entrada principal deve possuir extensão XML");
        if (arquivo.getSize() > ConteudoXml.LIMITE_BYTES) throw new ArquivoInvalidoException("Arquivo XML excede o limite de 20 MB");
    }
}
