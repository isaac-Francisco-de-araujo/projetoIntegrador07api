package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.ArquivoInvalidoException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.time.OffsetDateTime;

/** Extrai CT-e 4.00: nCT identifica a viagem e dest identifica o cliente. */
@Component
public class ExtratorDadosXml {
    private static final String NS = "http://www.portalfiscal.inf.br/cte";

    public DadosViagemXml extrair(InputStream xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Element raiz = factory.newDocumentBuilder().parse(xml).getDocumentElement();
            if (!NS.equals(raiz.getNamespaceURI()) ||
                    !("CTe".equals(raiz.getLocalName()) || "cteProc".equals(raiz.getLocalName()))) {
                throw new ArquivoInvalidoException("Estrutura incompatível: esperado CTe ou cteProc");
            }
            boolean processado = "cteProc".equals(raiz.getLocalName());
            if (processado) validarVersao(raiz);
            Element cte = processado ? obrigatorio(raiz, "CTe") : raiz;
            Element inf = obrigatorio(cte, "infCte");
            validarVersao(inf);
            String chave = inf.getAttribute("Id");
            if (chave.startsWith("CTe")) chave = chave.substring(3);
            validarChave(chave);
            Element ide = obrigatorio(inf, "ide");
            Element dest = obrigatorio(inf, "dest");
            String numero = textoObrigatorio(ide, "nCT");
            if (!numero.matches("[0-9]{1,9}") || Long.parseLong(numero) == 0 ||
                    Long.parseLong(numero) != Long.parseLong(chave.substring(25, 34))) {
                throw new ArquivoInvalidoException("nCT inválido ou divergente da chave de acesso");
            }
            var data = OffsetDateTime.parse(textoObrigatorio(ide, "dhEmi")).toLocalDate();
            String status = "Sem protocolo de autorização";
            Element prot = filho(raiz, "protCTe");
            if (prot != null) {
                validarVersao(prot);
                Element info = obrigatorio(prot, "infProt");
                if (!chave.equals(textoObrigatorio(info, "chCTe"))) {
                    throw new ArquivoInvalidoException("Chave do protocolo diverge da chave do CT-e");
                }
                String motivo = texto(info, "xMotivo");
                status = motivo == null ? "Protocolo sem motivo informado" : motivo;
            }
            Element endereco = filho(dest, "enderDest");
            return new DadosViagemXml(numero, data, status,
                    textoObrigatorio(dest, "xNome"), textoObrigatorio(dest, "CNPJ"),
                    texto(dest, "email"), endereco == null ? null : texto(endereco, "fone"), chave);
        } catch (ArquivoInvalidoException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ArquivoInvalidoException("XML inválido ou não foi possível realizar a leitura estruturada", ex);
        }
    }

    private void validarVersao(Element elemento) {
        if (!"4.00".equals(elemento.getAttribute("versao"))) {
            throw new ArquivoInvalidoException("Versão CT-e não suportada: esperado 4.00 em " + elemento.getLocalName());
        }
    }

    private void validarChave(String chave) {
        if (!chave.matches("[0-9]{44}") || !"57".equals(chave.substring(20, 22))) {
            throw new ArquivoInvalidoException("Chave de acesso deve conter 44 dígitos e modelo CT-e 57");
        }
        int soma = 0;
        int peso = 2;
        for (int i = 42; i >= 0; i--) {
            soma += (chave.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int dv = 11 - soma % 11;
        if (dv >= 10) dv = 0;
        if (dv != chave.charAt(43) - '0') {
            throw new ArquivoInvalidoException("Dígito verificador da chave de acesso inválido");
        }
    }

    private Element filho(Element pai, String nome) {
        Element encontrado = null;
        for (var node = pai.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element elemento && NS.equals(elemento.getNamespaceURI()) &&
                    nome.equals(elemento.getLocalName())) {
                if (encontrado != null) throw new ArquivoInvalidoException("Elemento duplicado: " + nome);
                encontrado = elemento;
            }
        }
        return encontrado;
    }

    private Element obrigatorio(Element pai, String nome) {
        Element elemento = filho(pai, nome);
        if (elemento == null) throw new ArquivoInvalidoException("Campo obrigatório ausente: " + nome);
        return elemento;
    }

    private String texto(Element pai, String nome) {
        Element elemento = filho(pai, nome);
        if (elemento == null) return null;
        String valor = elemento.getTextContent().trim();
        return valor.isEmpty() ? null : valor;
    }

    private String textoObrigatorio(Element pai, String nome) {
        String valor = texto(pai, nome);
        if (valor == null) throw new ArquivoInvalidoException("Campo obrigatório ausente ou vazio: " + nome);
        return valor;
    }
}
