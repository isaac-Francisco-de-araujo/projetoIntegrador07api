package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Leitura pontual do upload, sem persistir o PDF ou modificar a conferência. */
@Service
@RequiredArgsConstructor
public class PreviaPdfPacoteService {
    private final LeitorPacoteZip leitor;

    public LeitorPacoteZip.Entrada obter(MultipartFile arquivo, String sha256, String arquivoId) {
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}") || arquivoId == null || !arquivoId.matches("a(?:0|[1-9][0-9]?)"))
            throw new ArquivoInvalidoException("Identificação do pacote ou do PDF inválida");
        var pacote = leitor.conferir(arquivo);
        if (!sha256.equals(pacote.sha256()))
            throw new ConflitoRecursoException("O ZIP mudou desde a conferência; confira novamente antes de abrir o PDF");
        if (pacote.resumo().interrompida())
            throw new DiagnosticoPacoteException(pacote.resumo(), HttpStatus.BAD_REQUEST);
        return pacote.entradas().stream()
                .filter(e -> e.resumo().id().equals(arquivoId) && e.resumo().tipo().equals("PDF"))
                .findFirst().orElseThrow(() -> new RecursoNaoEncontradoException("PDF não disponível nesta conferência"));
    }
}
