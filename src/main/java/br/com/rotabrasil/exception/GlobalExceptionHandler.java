package br.com.rotabrasil.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ResponseEntity<ErroResponse> recursoNaoEncontrado(RecursoNaoEncontradoException ex) { return response(HttpStatus.NOT_FOUND, ex.getMessage()); }
    @ExceptionHandler(ArquivoInvalidoException.class)
    ResponseEntity<ErroResponse> arquivoInvalido(ArquivoInvalidoException ex) { return response(HttpStatus.BAD_REQUEST, ex.getMessage()); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErroResponse> persistencia(DataAccessException ex) { return response(HttpStatus.INTERNAL_SERVER_ERROR, "Erro de persistência de dados"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErroResponse> erroGenerico(Exception ex) { return response(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor"); }
    private ResponseEntity<ErroResponse> response(HttpStatus status, String mensagem) { return ResponseEntity.status(status).body(new ErroResponse(LocalDateTime.now(), status.value(), mensagem)); }
    public record ErroResponse(LocalDateTime timestamp, int status, String mensagem) {}
}
