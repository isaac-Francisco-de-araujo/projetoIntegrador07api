package br.com.rotabrasil.service.email;

import br.com.rotabrasil.dto.PreviaEmailRequest;
import br.com.rotabrasil.exception.ConflitoRecursoException;
import br.com.rotabrasil.service.*;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Objects;

/** Base interna para uma tentativa futura, distinta da prévia pública de metadados. */
@Service
public class PreparacaoEnvioService {
    @Value("${rotabrasil.email.limite-arquivo-bytes:20971520}")
    private int limiteArquivo = 20 * 1024 * 1024;
    @Value("${rotabrasil.email.limite-total-bytes:104857600}")
    private int limiteTotal = 100 * 1024 * 1024;
    private final Path raiz;
    private final EmailService email;
    private final DocumentoService documentos;
    private final AnexoService anexos;
    private final CoordenacaoImportacaoService coordenacao;
    private final Validator validator;
    @jakarta.annotation.PostConstruct void conferirLimites(){if(limiteArquivo<1||limiteArquivo>100*1024*1024||limiteTotal<1||limiteTotal>200*1024*1024)throw new IllegalStateException("Limites de e-mail fora dos intervalos permitidos");}

    public PreparacaoEnvioService(@Value("${rotabrasil.arquivos.viagens:arquivos/viagens}") String raiz,
            EmailService email, DocumentoService documentos, AnexoService anexos,
            CoordenacaoImportacaoService coordenacao, Validator validator) {
        this.raiz = Path.of(raiz).toAbsolutePath().normalize();
        this.email = email;
        this.documentos = documentos;
        this.anexos = anexos;
        this.coordenacao = coordenacao;
        this.validator = validator;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MensagemPreparada preparar(Long viagemId, PreviaEmailRequest pedido) {
        if (pedido == null || !validator.validate(pedido).isEmpty())
            throw new IllegalArgumentException("Seleção de envio inválida");
        try{ValidacaoDocumental.validarEnderecoUnico(pedido.destinatario());}catch(jakarta.mail.MessagingException ex){throw new br.com.rotabrasil.exception.ArquivoInvalidoException("Destinatário inválido; informe um único endereço de e-mail");}
        coordenacao.bloquear();
        email.prepararPrevia(viagemId, pedido);
        var arquivos = new ArrayList<MensagemPreparada.Arquivo>();
        int total = 0;
        for (Long id : pedido.documentoIds()) {
            var d = documentos.buscarDocumento(id);
            var arquivo = ler("DOCUMENTO", id, d.getNomeArquivo(), d.getCaminho(), d.getSha256(), limiteTotal - total);
            arquivos.add(arquivo);
            total += arquivo.tamanho();
        }
        for (Long id : pedido.anexoIds()) {
            var a = anexos.buscar(id);
            var arquivo = ler("ANEXO", id, a.getNomeArquivo(), a.getCaminho(), a.getSha256(), limiteTotal - total);
            arquivos.add(arquivo);
            total += arquivo.tamanho();
        }
        return new MensagemPreparada(viagemId, pedido.destinatario(), arquivos);
    }

    private MensagemPreparada.Arquivo ler(String origem, Long id, String nome, String caminho, String hash, int restante) {
        try {
            String nomeSeguro=ValidacaoDocumental.nomeSimples(nome);
            if (hash == null || !hash.matches("[a-f0-9]{64}"))
                throw new IOException();
            byte[] bytes=LeituraSeguraArquivos.ler(raiz,caminho,Math.min(limiteArquivo,restante));
            if (bytes.length == 0 || bytes.length > limiteArquivo || bytes.length > restante || !Objects.equals(hash, ConteudoXml.sha256(bytes)))
                throw new IOException();
            return new MensagemPreparada.Arquivo(origem, id, nomeSeguro, hash, bytes);
        } catch (IOException | InvalidPathException | NullPointerException | br.com.rotabrasil.exception.ArquivoInvalidoException ex) {
            throw new ConflitoRecursoException("Anexo indisponível ou com integridade inválida; revise a seleção");
        }
    }
}
