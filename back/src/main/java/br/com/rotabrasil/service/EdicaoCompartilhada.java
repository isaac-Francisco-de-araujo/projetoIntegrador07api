package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.ConflitoRecursoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Comparação executada dentro da transação curta já coordenada pelo serviço. */
@Component @RequiredArgsConstructor
public class EdicaoCompartilhada {
    private final ObjectMapper json;
    public String versao(Object atual){
        try{return "\""+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(atual).getBytes(StandardCharsets.UTF_8)))+"\"";}
        catch(Exception ex){throw new IllegalStateException("Falha ao conferir edição");}
    }
    public void conferir(Object atual){
        var contexto=RequestContextHolder.getRequestAttributes();
        // Chamadas internas não são edições HTTP; mantêm as regras transacionais existentes.
        if(contexto instanceof ServletRequestAttributes r && !versao(atual).equals(r.getRequest().getHeader("If-Match")))
            throw new ConflitoRecursoException("Registro atualizado ou versão não informada. Recarregue para revisar; seu rascunho não foi aplicado.");
    }
}
