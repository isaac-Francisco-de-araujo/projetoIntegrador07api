package br.com.rotabrasil.exception;

public class ArmazenamentoException extends RuntimeException {
    public ArmazenamentoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }

    public ArmazenamentoException(String mensagem) {
        super(mensagem);
    }
}
