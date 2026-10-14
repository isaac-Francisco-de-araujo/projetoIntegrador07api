package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.ArquivoInvalidoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;

@Service
public class OrganizadorArquivosService {
    private final Path raizViagens;
    public OrganizadorArquivosService(@Value("${rotabrasil.arquivos.viagens:arquivos/viagens}") String raiz) { this.raizViagens = Paths.get(raiz).toAbsolutePath().normalize(); }
    public Path organizarXml(MultipartFile arquivo, String identificadorViagem) {
        if (identificadorViagem == null || identificadorViagem.isBlank()) throw new ArquivoInvalidoException("Identificador válido da viagem é obrigatório antes de salvar o XML");
        String nome = Path.of(arquivo.getOriginalFilename() == null ? "documento.xml" : arquivo.getOriginalFilename()).getFileName().toString();
        if (!nome.toLowerCase().endsWith(".xml")) throw new ArquivoInvalidoException("A entrada principal deve ser um arquivo XML");
        try {
            Path pasta = raizViagens.resolve(identificadorViagem).normalize();
            if (!pasta.startsWith(raizViagens)) throw new ArquivoInvalidoException("Identificador de viagem inválido");
            Files.createDirectories(pasta);
            Path destino = pasta.resolve(nome).normalize();
            if (!destino.startsWith(pasta)) throw new ArquivoInvalidoException("Nome de arquivo inválido");
            arquivo.transferTo(destino);
            return destino;
        } catch (IOException ex) { throw new ArquivoInvalidoException("Não foi possível organizar o XML", ex); }
    }
}
