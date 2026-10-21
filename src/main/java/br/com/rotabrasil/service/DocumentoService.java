package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.ArquivoInvalidoException;
import br.com.rotabrasil.exception.ConflitoDocumentoException;
import br.com.rotabrasil.exception.ConflitoRecursoException;
import br.com.rotabrasil.exception.RecursoNaoEncontradoException;
import br.com.rotabrasil.model.Documento;
import br.com.rotabrasil.repository.DocumentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DocumentoService {
    private final DocumentoRepository repository;
    private final ExtratorDadosXml extrator;

    public Documento buscarDocumento(Long id) {
        var documento = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Documento não encontrado: " + id));
        if (documento.getArquivadoEm() != null) throw new ConflitoRecursoException("Documento arquivado; restaure antes de selecioná-lo");
        return documento;
    }

    public List<Documento> listarDocumentos() {
        return repository.findByArquivadoEmIsNull();
    }

    public List<Documento> listarDocumentosDaViagem(Long viagemId) {
        return repository.findByViagemIdAndArquivadoEmIsNull(viagemId);
    }

    /** Somente leitura: não preenche identidade nem hash dos registros antigos. */
    @Transactional(readOnly = true)
    public Optional<Documento> buscarExistente(DadosViagemXml dados, String hashRecebido) {
        var encontrados = new ArrayList<>(repository.findAllByChaveAcesso(dados.chaveAcesso()));
        // A identidade fiscal independe da associação histórica à viagem.
        for (Documento legado : repository.findByTipoIgnoreCaseAndChaveAcessoIsNull("XML")) {
            byte[] conteudo = lerArquivoExistente(legado);
            DadosViagemXml identidade;
            try {
                identidade = extrator.extrair(new ByteArrayInputStream(conteudo));
            } catch (ArquivoInvalidoException ex) {
                throw new ConflitoDocumentoException("XML legado não pôde ser identificado; reconciliação necessária");
            }
            if (dados.chaveAcesso().equals(identidade.chaveAcesso())) {
                encontrados.add(legado);
            }
        }
        if (encontrados.size() > 1) {
            throw new ConflitoDocumentoException("Há múltiplos registros para a chave fiscal; reconciliação necessária");
        }
        if (encontrados.isEmpty()) return Optional.empty();

        Documento existente = encontrados.getFirst();
        if (existente.getArquivadoEm() != null) throw new ConflitoDocumentoException("Documento arquivado; restaure o cadastro antes de reimportar");
        validarRelacionamento(existente, dados);
        String hashEmDisco = ConteudoXml.sha256(lerArquivoExistente(existente));
        if (existente.getSha256() != null && !existente.getSha256().equals(hashEmDisco)) {
            throw new ConflitoDocumentoException("Arquivo diverge do hash registrado; reconciliação necessária");
        }
        if (!hashRecebido.equals(hashEmDisco)) {
            throw new ConflitoDocumentoException("Já existe documento com esta chave fiscal e conteúdo diferente");
        }
        return Optional.of(existente);
    }

    private void validarRelacionamento(Documento documento, DadosViagemXml dados) {
        var viagem = documento.getViagem();
        if (!"XML".equalsIgnoreCase(documento.getTipo()) || viagem == null || viagem.getCliente() == null ||
                !dados.numero().equals(viagem.getNumero()) ||
                !dados.cnpjCliente().equals(viagem.getCliente().getCnpj())) {
            throw new ConflitoDocumentoException("Documento associado a viagem ou cliente incompatível; reconciliação necessária");
        }
    }

    private byte[] lerArquivoExistente(Documento documento) {
        if (documento.getCaminho() == null || documento.getCaminho().isBlank()) {
            throw new ConflitoDocumentoException("Arquivo registrado sem caminho; reconciliação necessária");
        }
        try {
            Path caminho = Path.of(documento.getCaminho());
            if (!Files.isRegularFile(caminho, LinkOption.NOFOLLOW_LINKS)) {
                throw new ConflitoDocumentoException("Arquivo registrado ausente ou inacessível; reconciliação necessária");
            }
            try (var entrada = Files.newInputStream(caminho)) {
                return ConteudoXml.ler(entrada);
            }
        } catch (IOException | IllegalArgumentException | ArquivoInvalidoException ex) {
            throw new ConflitoDocumentoException("Não foi possível conferir arquivo registrado; reconciliação necessária");
        }
    }

    /** Cadastro do importador CT-e. Não é um fluxo genérico de associação de anexos. */
    @Transactional
    public Documento registrarDocumento(Documento documento) {
        if (documento == null || !"XML".equalsIgnoreCase(documento.getTipo())) {
            throw new IllegalArgumentException("Este fluxo registra somente documentos XML de CT-e");
        }
        if (documento.getChaveAcesso() == null || !documento.getChaveAcesso().matches("[0-9]{44}") ||
                documento.getSha256() == null || !documento.getSha256().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Identidade fiscal e SHA-256 são obrigatórios para novos documentos XML");
        }
        // A consulta trata conflitos legíveis; a restrição única protege também inserções concorrentes.
        if (!repository.findAllByChaveAcesso(documento.getChaveAcesso()).isEmpty()) {
            throw new ConflitoDocumentoException("Chave fiscal registrada durante o processamento; reconciliação necessária");
        }
        // Expõe falhas de persistência antes de publicar o nome definitivo no beforeCommit.
        return repository.saveAndFlush(documento);
    }
}
