package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.ProcessamentoXmlResponse;
import br.com.rotabrasil.exception.ArquivoInvalidoException;
import br.com.rotabrasil.model.Cliente;
import br.com.rotabrasil.model.Documento;
import br.com.rotabrasil.model.Viagem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;

@Service @RequiredArgsConstructor
public class ProcessamentoService {
    private final ExtratorDadosXml extratorDadosXml;
    private final OrganizadorArquivosService organizadorArquivosService;
    private final ViagemService viagemService;
    private final DocumentoService documentoService;
    private final ClienteService clienteService;

    @Transactional
    public ProcessamentoXmlResponse processarArquivoXml(MultipartFile arquivo) {
        validarArquivo(arquivo);
        DadosViagemXml dados;
        try { dados = extratorDadosXml.extrair(arquivo.getInputStream()); }
        catch (IOException ex) { throw new ArquivoInvalidoException("Não foi possível ler o arquivo XML", ex); }
        if (dados.numero() == null || dados.numero().isBlank()) {
            throw new ArquivoInvalidoException("O identificador da viagem ainda não está configurado para o XML recebido");
        }

        Cliente cliente = clienteService.buscarOuCadastrar(dados.nomeCliente(), dados.cnpjCliente(), dados.emailCliente(), dados.telefoneCliente());
        Viagem viagem = viagemService.buscarOuCadastrar(dados.numero(), dados.data(), dados.status(), cliente);
        Path caminho = organizadorArquivosService.organizarXml(arquivo, dados.numero());
        Documento documento = documentoService.registrarDocumento(Documento.builder()
                .nomeArquivo(arquivo.getOriginalFilename())
                .tipo("XML")
                .caminho(caminho.toString())
                .viagem(viagem)
                .build());
        return new ProcessamentoXmlResponse(true, "XML de teste processado com sucesso", viagem.getId(), documento.getId());
    }

    private void validarArquivo(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) throw new ArquivoInvalidoException("Arquivo XML ausente ou vazio");
        String nome = arquivo.getOriginalFilename() == null ? "" : arquivo.getOriginalFilename();
        if (!nome.toLowerCase().endsWith(".xml")) throw new ArquivoInvalidoException("A entrada principal deve possuir extensão XML");
        if (arquivo.getSize() > 20 * 1024 * 1024) throw new ArquivoInvalidoException("Arquivo XML excede o limite de 20 MB");
    }
}
