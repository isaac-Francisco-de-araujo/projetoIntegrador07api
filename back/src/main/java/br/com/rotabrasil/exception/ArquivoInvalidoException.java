package br.com.rotabrasil.exception;

public class ArquivoInvalidoException extends RuntimeException {
    public ArquivoInvalidoException(String mensagem) { super(mensagem); }
    public ArquivoInvalidoException(String mensagem, Throwable causa) { super(mensagem, causa); }
}
