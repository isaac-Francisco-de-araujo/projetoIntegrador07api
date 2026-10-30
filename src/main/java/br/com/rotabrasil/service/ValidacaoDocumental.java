package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.ArquivoInvalidoException;
import jakarta.mail.internet.InternetAddress;

/** Regras compartilhadas de entrada, sem alterar os bytes do documento original. */
public final class ValidacaoDocumental {
    private ValidacaoDocumental() {}
    public static String nomeSimples(String original) {
        if(original==null||original.chars().anyMatch(Character::isISOControl))throw invalido("Nome de arquivo inválido");
        String nome=original.replace('\\','/');nome=nome.substring(nome.lastIndexOf('/')+1);
        if(nome.isBlank()||nome.length()>255||nome.equals(".")||nome.equals(".."))throw invalido("Nome de arquivo inválido");
        return nome;
    }
    public static DadosViagemXml conferir(DadosViagemXml d) {
        conferirCliente(d.nomeCliente(),d.cnpjCliente(),d.emailCliente(),d.telefoneCliente());campo(d.status(),"Situação do CT-e",false);return d;
    }
    public static void conferirCliente(String nome,String cnpj,String email,String telefone){
        if(cnpj==null||!cnpj.matches("[0-9]{14}"))throw new br.com.rotabrasil.exception.DadosXmlInvalidosException("CNPJ_INVALIDO","CNPJ do destinatário deve conter 14 dígitos");
        campo(nome,"Nome do cliente",true);campo(email,"E-mail",false);campo(telefone,"Telefone",false);
        if(email!=null&&!email.isBlank())try {
            validarEnderecoUnico(email);
        }catch(Exception ex){throw new br.com.rotabrasil.exception.DadosXmlInvalidosException("CONTATO_XML_INVALIDO","E-mail do cadastro possui formato inválido");}
    }
    public static void validarEnderecoUnico(String endereco)throws jakarta.mail.MessagingException {
        if(endereco==null||endereco.isBlank()||endereco.length()>255||endereco.contains("\r")||endereco.contains("\n"))throw new jakarta.mail.MessagingException("Endereço de e-mail inválido");
        try{var e=new InternetAddress(endereco,true);e.validate();if(!e.getAddress().equals(endereco)||!endereco.contains("@")||e.getPersonal()!=null)throw new jakarta.mail.MessagingException("Informe um único endereço sem nome de exibição");}
        catch(jakarta.mail.internet.AddressException ex){throw new jakarta.mail.MessagingException("Endereço de e-mail inválido");}
    }
    private static void campo(String valor,String nome,boolean obrigatorio) {
        if((obrigatorio&&(valor==null||valor.isBlank()))||(valor!=null&&(valor.length()>255||valor.chars().anyMatch(Character::isISOControl))))
            throw new br.com.rotabrasil.exception.DadosXmlInvalidosException("CAMPO_CADASTRAL_INVALIDO",nome+" ausente, excede 255 caracteres ou contém controles inválidos");
    }
    private static ArquivoInvalidoException invalido(String mensagem){return new ArquivoInvalidoException(mensagem);}
}
