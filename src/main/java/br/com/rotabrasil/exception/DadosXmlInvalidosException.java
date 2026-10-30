package br.com.rotabrasil.exception;
/** Código e mensagem fixos, sem valores fiscais rejeitados. */
public class DadosXmlInvalidosException extends ArquivoInvalidoException {
    private final String codigo;
    public DadosXmlInvalidosException(String codigo,String mensagem){super(mensagem);this.codigo=codigo;}
    public String codigo(){return codigo;}
}
