package br.com.rotabrasil.exception;

import br.com.rotabrasil.dto.PacoteDtos;
import org.springframework.http.HttpStatus;

/** Somente metadados públicos limitados; nunca propaga o texto da causa interna. */
public class DiagnosticoPacoteException extends RuntimeException {
    private final PacoteDtos.Inspecao diagnostico;
    private final HttpStatus status;
    public DiagnosticoPacoteException(PacoteDtos.Inspecao diagnostico,HttpStatus status) {
        super("Pacote com bloqueios; consulte o diagnóstico por arquivo");
        this.diagnostico=diagnostico;this.status=status;
    }
    public PacoteDtos.Inspecao diagnostico(){return diagnostico;}
    public HttpStatus status(){return status;}
}
