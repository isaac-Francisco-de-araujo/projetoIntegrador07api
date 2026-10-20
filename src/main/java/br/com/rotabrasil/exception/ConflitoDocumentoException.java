package br.com.rotabrasil.exception;

public class ConflitoDocumentoException extends RuntimeException {
    public ConflitoDocumentoException(String mensagem) {
        super(mensagem);
    }
}
