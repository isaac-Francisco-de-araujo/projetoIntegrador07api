package br.com.rotabrasil.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

/** Apresentação pública e o mesmo painel existente, sem consultar dados da operação. */
@Controller
public class EntradaController {
    @GetMapping(value="/", produces=MediaType.TEXT_HTML_VALUE)
    @ResponseBody public String apresentacao(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return pagina("apresentacao.html", request, response);
    }

    @GetMapping(value={"/painel", "/index.html"}, produces=MediaType.TEXT_HTML_VALUE)
    @ResponseBody public String painel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return pagina("index.html", request, response);
    }

    private String pagina(String nome, HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        String contexto=HtmlUtils.htmlEscape(request.getContextPath());
        try (var input=new ClassPathResource("static/"+nome).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("href=\"/", "href=\""+contexto+"/")
                    .replace("src=\"/", "src=\""+contexto+"/");
        }
    }
}
