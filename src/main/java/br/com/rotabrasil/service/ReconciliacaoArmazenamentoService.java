package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.ArmazenamentoException;
import br.com.rotabrasil.exception.ConflitoDocumentoException;
import br.com.rotabrasil.model.Documento;
import br.com.rotabrasil.repository.DocumentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReconciliacaoArmazenamentoService {
    private final OrganizadorArquivosService arquivos;
    private final DocumentoRepository documentos;
    private final br.com.rotabrasil.repository.AnexoRepository anexos;
    private record Registro(Long id, String hash, String caminho, String numero, boolean tipoValido) {}

    public enum Estado {
        EM_ANDAMENTO, PREPARADO_SEM_REGISTRO, PUBLICADO_SEM_REGISTRO,
        COMMIT_CONFIRMADO, REGISTRO_SEM_ARQUIVO, RECUPERADA, REVISAO_NECESSARIA
    }

    public record Pendencia(String operacaoId, Estado estado, Long documentoId) {}

    /** Não adota arquivos nem altera registros, mesmo que o conteúdo pareça válido. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public List<Pendencia> inspecionar() {
        return executar(false);
    }

    /** Só recupera operações cujo documento já está confirmado no banco. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public List<Pendencia> recuperarConfirmadas() {
        return executar(true);
    }

    private List<Pendencia> executar(boolean recuperar) {
        List<Path> diretorios;
        try {
            diretorios = arquivos.listarPendentes();
        } catch (IOException ex) {
            throw new ArmazenamentoException("Não foi possível inspecionar operações pendentes", ex);
        }
        var resultado = new ArrayList<Pendencia>();
        for (Path diretorio : diretorios) {
            String id = diretorio.getFileName().toString();
            if (!id.matches("op-[0-9a-f-]{36}")) id = "artefato-desconhecido";
            try {
                var aberta = arquivos.reabrir(diretorio);
                if (aberta.isEmpty()) {
                    resultado.add(new Pendencia(id, Estado.EM_ANDAMENTO, null));
                    continue;
                }
                try (var operacao = aberta.get()) {
                    resultado.add(conferir(operacao, recuperar));
                }
            } catch (IOException | ArmazenamentoException | ConflitoDocumentoException | IllegalArgumentException ex) {
                resultado.add(new Pendencia(id, Estado.REVISAO_NECESSARIA, null));
            }
        }
        return List.copyOf(resultado);
    }

    private Pendencia conferir(OperacaoArquivo operacao, boolean recuperar) throws IOException {
        // Mantém o registro confirmado protegido contra exclusão durante a recuperação.
        java.util.List<Registro> registros;
        if (operacao.chave.startsWith("pdf-")) {
            String token = operacao.chave.substring(4);
            registros = (recuperar ? anexos.buscarParaReconciliacao(token) : anexos.findByToken(token))
                .stream().map(a -> new Registro(a.getId(), a.getSha256(), a.getCaminho(), a.getNumeroArmazenamento(), true)).toList();
        } else {
            registros = (recuperar ? documentos.buscarParaReconciliacao(operacao.chave) : documentos.findAllByChaveAcesso(operacao.chave))
                .stream().map(d -> new Registro(d.getId(), d.getSha256(), d.getCaminho(), d.getViagem() == null ? null : d.getViagem().getNumero(), "XML".equalsIgnoreCase(d.getTipo()))).toList();
        }
        if (registros.size() > 1) return pendencia(operacao, Estado.REVISAO_NECESSARIA, null);
        boolean temporarioExiste = Files.exists(operacao.temporario, LinkOption.NOFOLLOW_LINKS);
        boolean temporarioValido = temporarioExiste && arquivos.confere(operacao.temporario, operacao.hash);
        boolean destinoExiste = Files.exists(operacao.destino, LinkOption.NOFOLLOW_LINKS);
        if (registros.isEmpty()) {
            if (!temporarioValido) return pendencia(operacao, Estado.REVISAO_NECESSARIA, null);
            if (!destinoExiste) return pendencia(operacao, Estado.PREPARADO_SEM_REGISTRO, null);
            if (Files.isRegularFile(operacao.destino, LinkOption.NOFOLLOW_LINKS) &&
                    Files.isSameFile(operacao.destino, operacao.temporario)) {
                return pendencia(operacao, Estado.PUBLICADO_SEM_REGISTRO, null);
            }
            return pendencia(operacao, Estado.REVISAO_NECESSARIA, null);
        }

        Registro documento = registros.getFirst();
        if (!compativel(documento, operacao) || (temporarioExiste && !temporarioValido)) {
            return pendencia(operacao, Estado.REVISAO_NECESSARIA, documento.id());
        }
        if (destinoExiste) {
            if (!arquivos.confere(operacao.destino, operacao.hash)) {
                return pendencia(operacao, Estado.REVISAO_NECESSARIA, documento.id());
            }
        } else if (!temporarioValido) {
            return pendencia(operacao, Estado.REVISAO_NECESSARIA, documento.id());
        }
        if (!recuperar) {
            return pendencia(operacao, destinoExiste ? Estado.COMMIT_CONFIRMADO : Estado.REGISTRO_SEM_ARQUIVO, documento.id());
        }
        // Nunca sobrescreve o destino: a recuperação usa a mesma publicação exclusiva.
        if (!destinoExiste) arquivos.publicar(operacao);
        arquivos.confirmar(operacao);
        return pendencia(operacao, Estado.RECUPERADA, documento.id());
    }

    private boolean compativel(Registro documento, OperacaoArquivo operacao) {
        return documento.tipoValido() && operacao.hash.equals(documento.hash()) && operacao.numero.equals(documento.numero()) &&
                documento.caminho() != null && Path.of(documento.caminho()).toAbsolutePath().normalize().equals(operacao.destino);
    }

    private Pendencia pendencia(OperacaoArquivo operacao, Estado estado, Long documento) {
        return new Pendencia(operacao.id(), estado, documento);
    }
}
