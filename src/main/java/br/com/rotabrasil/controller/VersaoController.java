package br.com.rotabrasil.controller;

import br.com.rotabrasil.service.ConteudoXml;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Identifica o backend e os recursos do mesmo empacotamento, sem dados operacionais. */
@RestController
public class VersaoController {
    private final ObjectProvider<BuildProperties> build;
    private final ResourceLoader resources;
    public VersaoController(ObjectProvider<BuildProperties> build,ResourceLoader resources){this.build=build;this.resources=resources;}
    @GetMapping("/api/versao")
    public Map<String,Object> consultar() throws IOException {
        var b=build.getIfAvailable();var hashes=new LinkedHashMap<String,String>();
        for(String nome:new String[]{"index.html","painel.js","painel-base.js","painel-configuracoes.js","painel-conta-teste.js","painel-email.js","painel-historico.js","painel-diagnostico.css","painel-admin.js","painel-fila.js","painel-busca.js","painel-saude.js"}){
            try(var in=resources.getResource("classpath:static/"+nome).getInputStream()){hashes.put(nome,ConteudoXml.sha256(in.readAllBytes()));}
        }
        return Map.of("versao",b==null?"desenvolvimento":b.getVersion(),"compiladoEm",b==null?"não empacotado":b.getTime().toString(),
                "contrato","equipe-V15-20261109","estaticosSha256",hashes);
    }
}
