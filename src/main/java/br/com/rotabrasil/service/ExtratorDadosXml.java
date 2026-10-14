package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.ArquivoInvalidoException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Extrator CT-e versão 4.00.
 * Regra de escolha do cliente: Destinatário (dest). Confirmar se essa é a regra correta.
 */
@Component
public class ExtratorDadosXml {
    private static final String NAMESPACE_CTE = "http://www.portalfiscal.inf.br/cte";

    public DadosViagemXml extrair(InputStream xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            
            Document document = factory.newDocumentBuilder().parse(xml);
            document.getDocumentElement().normalize();
            
            Element infCte = (Element) document.getElementsByTagNameNS(NAMESPACE_CTE, "infCte").item(0);
            if (infCte == null) {
                throw new ArquivoInvalidoException("XML não possui infCte");
            }
            
            Element ide = (Element) infCte.getElementsByTagNameNS(NAMESPACE_CTE, "ide").item(0);
            Element dest = (Element) infCte.getElementsByTagNameNS(NAMESPACE_CTE, "dest").item(0);
            
            String nCT = textoObrigatorio(ide, "nCT");
            ZonedDateTime dhEmi = ZonedDateTime.parse(textoObrigatorio(ide, "dhEmi"));
            
            String clienteNome = dest != null ? textoObrigatorio(dest, "xNome") : "Nao informado";
            String clienteCnpj = dest != null ? textoObrigatorio(dest, "CNPJ") : "00000000000000";
            String clienteEmail = dest != null ? getElementoTexto(dest, "email") : null;
            if (clienteEmail == null) clienteEmail = "nao@informado.com";
            
            String clienteTelefone = dest != null ? getElementoTexto(dest, "fone") : null;
            if (clienteTelefone == null) clienteTelefone = "0000000000";
            
            Element infProt = (Element) document.getElementsByTagNameNS(NAMESPACE_CTE, "infProt").item(0);
            String status = infProt != null ? getElementoTexto(infProt, "xMotivo") : "Autorizado";

            return new DadosViagemXml(
                    nCT,
                    dhEmi.toLocalDate(),
                    status,
                    clienteNome,
                    clienteCnpj,
                    clienteEmail,
                    clienteTelefone);
        } catch (ArquivoInvalidoException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ArquivoInvalidoException("XML inválido ou não foi possível realizar a leitura estruturada", ex);
        }
    }

    private String getElementoTexto(Element pai, String nome) {
        NodeList encontrados = pai.getElementsByTagNameNS(NAMESPACE_CTE, nome);
        if (encontrados.getLength() == 0) return null;
        return encontrados.item(0).getTextContent().trim();
    }

    private String textoObrigatorio(Element pai, String nome) {
        NodeList encontrados = pai.getElementsByTagNameNS(NAMESPACE_CTE, nome);
        if (encontrados.getLength() == 0) {
            throw new ArquivoInvalidoException("Campo obrigatório ausente: " + nome);
        }
        String valor = encontrados.item(0).getTextContent().trim();
        if (valor.isBlank()) throw new ArquivoInvalidoException("Campo obrigatório vazio: " + nome);
        return valor;
    }
}
