package br.com.rotabrasil.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErroResponse> integridade(DataIntegrityViolationException ex) { return response(HttpStatus.CONFLICT, "Operação viola a integridade ou unicidade dos dados"); }
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ErroResponse> concorrencia(PessimisticLockingFailureException ex) { return response(HttpStatus.SERVICE_UNAVAILABLE, "Banco ocupado; tente novamente"); }
    @ExceptionHandler(ArmazenamentoException.class)
    ResponseEntity<ErroResponse> armazenamento(ArmazenamentoException ex) { return response(HttpStatus.INTERNAL_SERVER_ERROR, "Falha de armazenamento; consulte as pendências de reconciliação"); }
    @ExceptionHandler(ConflitoDocumentoException.class)
    ResponseEntity<ErroResponse> conflitoDocumento(ConflitoDocumentoException ex) { return response(HttpStatus.CONFLICT, ex.getMessage()); }
    @ExceptionHandler(ConflitoRecursoException.class)
    ResponseEntity<ErroResponse> conflitoRecurso(ConflitoRecursoException ex) { return response(HttpStatus.CONFLICT, ex.getMessage()); }
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ResponseEntity<ErroResponse> recursoNaoEncontrado(RecursoNaoEncontradoException ex) { return response(HttpStatus.NOT_FOUND, ex.getMessage()); }
    @ExceptionHandler(ArquivoInvalidoException.class)
    ResponseEntity<ErroResponse> arquivoInvalido(ArquivoInvalidoException ex) { return response(HttpStatus.BAD_REQUEST, ex.getMessage()); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErroResponse> persistencia(DataAccessException ex) { return response(HttpStatus.INTERNAL_SERVER_ERROR, "Erro de persistência de dados"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErroResponse> erroGenerico(Exception ex) { return response(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor"); }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var campos = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> new CampoInvalido(erro.getField(), erro.getDefaultMessage()))
                .distinct().sorted(Comparator.comparing(CampoInvalido::campo).thenComparing(CampoInvalido::mensagem)).toList();
        return new ResponseEntity<>(erro(status, "Dados de entrada inválidos", campos), headers, status);
    }

    /** Conserva o status e os headers definidos pelo Spring, sem expor SQL, valores ou exceções. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String mensagem = switch (status.value()) {
            case 400 -> "Requisição inválida: confira o corpo, os parâmetros e as partes obrigatórias";
            case 404 -> "Recurso não encontrado";
            case 405 -> "Método HTTP não permitido";
            case 406 -> "Formato de resposta não suportado";
            case 413 -> "Arquivo ou requisição excede o limite permitido";
            case 415 -> "Tipo de conteúdo não suportado";
            default -> status.is5xxServerError() ? "Erro interno no servidor" : "Requisição não pôde ser atendida";
        };
        return new ResponseEntity<>(erro(status, mensagem, List.of()), headers, status);
    }

    private ResponseEntity<ErroResponse> response(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(erro(status, mensagem, List.of()));
    }
    private ErroResponse erro(HttpStatusCode status, String mensagem, List<CampoInvalido> campos) {
        return new ErroResponse(LocalDateTime.now(), status.value(), mensagem, campos);
    }
    public record CampoInvalido(String campo, String mensagem) {}
    public record ErroResponse(LocalDateTime timestamp, int status, String mensagem, List<CampoInvalido> campos) {}
}
