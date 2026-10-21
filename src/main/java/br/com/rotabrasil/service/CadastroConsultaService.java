package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.*;
import br.com.rotabrasil.exception.ConflitoRecursoException;
import br.com.rotabrasil.model.*;
import br.com.rotabrasil.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.List;
import java.util.Objects;

/** Converte entidades em DTOs dentro da transação e protege mutações feitas pela API. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CadastroConsultaService {
    private final ClienteService clientes;
    private final ViagemService viagens;
    private final DocumentoService documentos;
    private final ViagemRepository viagensRepository;
    private final DocumentoRepository documentosRepository;
    private final EnvioRepository enviosRepository;
    private final AnexoRepository anexosRepository;
    private final CoordenacaoImportacaoService coordenacao;

    public List<ClienteResponse> listarClientes() { return clientes.listarClientes().stream().map(ClienteResponse::de).toList(); }
    public ClienteResponse buscarCliente(Long id) { return ClienteResponse.de(clientes.buscarCliente(id)); }
    public List<ViagemResponse> listarViagens() { return viagens.listarViagens().stream().map(ViagemResponse::de).toList(); }
    public ViagemResponse buscarViagem(Long id) { return ViagemResponse.de(viagens.buscarViagem(id)); }
    public List<DocumentoResponse> listarDocumentos() { return documentos.listarDocumentos().stream().map(DocumentoResponse::de).toList(); }
    public List<DocumentoResponse> listarDocumentosDaViagem(Long id) {
        viagens.buscarViagem(id);
        return documentos.listarDocumentosDaViagem(id).stream().map(DocumentoResponse::de).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClienteResponse cadastrarCliente(ClienteRequest dados) {
        coordenacao.bloquear();
        return ClienteResponse.de(clientes.cadastrarCliente(cliente(dados)));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClienteResponse atualizarCliente(Long id, ClienteRequest dados) {
        coordenacao.bloquear();
        Cliente atual = clientes.buscarCliente(id);
        if (!Objects.equals(atual.getCnpj(), dados.cnpj()) &&
                (viagensRepository.existsByClienteId(id) || enviosRepository.existsByClienteId(id))) {
            throw new ConflitoRecursoException("CNPJ de cliente vinculado não pode ser alterado; reconciliação necessária");
        }
        return ClienteResponse.de(clientes.atualizarCliente(id, cliente(dados)));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void excluirCliente(Long id) {
        coordenacao.bloquear();
        clientes.buscarCliente(id);
        if (viagensRepository.existsByClienteId(id) || enviosRepository.existsByClienteId(id)) {
            throw new ConflitoRecursoException("Cliente possui viagens ou histórico de envio e não pode ser excluído");
        }
        clientes.excluirCliente(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ViagemResponse cadastrarViagem(ViagemRequest dados) {
        coordenacao.bloquear();
        return ViagemResponse.de(viagens.cadastrarViagem(viagem(dados)));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ViagemResponse atualizarViagem(Long id, ViagemRequest dados) {
        coordenacao.bloquear();
        Viagem atual = viagens.buscarViagem(id);
        boolean mudaIdentidade = !Objects.equals(atual.getNumero(), dados.numero()) ||
                !Objects.equals(atual.getCliente().getId(), dados.clienteId());
        if (mudaIdentidade && (documentosRepository.existsByViagemId(id) || anexosRepository.existsByViagensId(id) || enviosRepository.existsByViagemId(id))) {
            throw new ConflitoRecursoException("Viagem possui documentos ou histórico de envio; identidade exige reconciliação");
        }
        return ViagemResponse.de(viagens.atualizarViagem(id, viagem(dados)));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void excluirViagem(Long id) {
        coordenacao.bloquear();
        var viagem = viagens.buscarViagem(id);
        if (documentosRepository.existsByViagemId(id) || anexosRepository.existsByViagensId(id) || enviosRepository.existsByViagemId(id)) {
            throw new ConflitoRecursoException("Viagem possui documentos ou histórico de envio e não pode ser excluída");
        }
        viagensRepository.delete(viagem);
        viagensRepository.flush();
    }

    public List<DocumentoArquivadoResponse> listarArquivados(Long viagemId) {
        viagens.buscarViagem(viagemId);
        return documentosRepository.findByViagemIdAndArquivadoEmIsNotNull(viagemId).stream().map(DocumentoArquivadoResponse::de).toList();
    }

    private Documento documentoIncluindoArquivado(Long id) {
        return documentosRepository.findById(id).orElseThrow(() ->
                new br.com.rotabrasil.exception.RecursoNaoEncontradoException("Documento não encontrado: " + id));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void arquivarDocumento(Long id) {
        coordenacao.bloquear();
        var documento = documentoIncluindoArquivado(id);
        if (documento.getArquivadoEm() == null) {
            documento.setArquivadoEm(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
            documentosRepository.saveAndFlush(documento);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DocumentoResponse restaurarDocumento(Long id) {
        coordenacao.bloquear();
        var documento = documentoIncluindoArquivado(id);
        documento.setArquivadoEm(null);
        documentosRepository.saveAndFlush(documento);
        return DocumentoResponse.de(documento);
    }

    private Cliente cliente(ClienteRequest dados) {
        return Cliente.builder().nome(dados.nome()).cnpj(dados.cnpj()).email(dados.email()).telefone(dados.telefone()).build();
    }
    private Viagem viagem(ViagemRequest dados) {
        return Viagem.builder().numero(dados.numero()).data(dados.data()).status(dados.status())
                .cliente(clientes.buscarCliente(dados.clienteId())).build();
    }
}
