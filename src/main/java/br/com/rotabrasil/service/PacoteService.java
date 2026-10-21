package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.*;
import br.com.rotabrasil.exception.*;
import br.com.rotabrasil.model.*;
import br.com.rotabrasil.repository.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service @RequiredArgsConstructor @Validated
public class PacoteService {
    private final LeitorPacoteZip leitor;
    private final PacoteRepository pacotes;
    private final AnexoRepository anexos;
    private final DocumentoRepository documentos;
    private final ViagemRepository viagens;
    private final ProcessamentoService processamento;
    private final ArmazenamentoTransacionalService armazenamento;
    private final CoordenacaoImportacaoService coordenacao;

    public PacoteDtos.Inspecao inspecionar(MultipartFile arquivo) { return leitor.ler(arquivo).resumo(); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PacoteDtos.Resultado confirmar(MultipartFile arquivo, @Valid PacoteDtos.Confirmacao pedido) {
        var lido = leitor.ler(arquivo);
        if (!lido.sha256().equals(pedido.sha256())) throw new ConflitoRecursoException("ZIP mudou após a conferência; inspecione novamente");
        var xmls = new LinkedHashMap<String, LeitorPacoteZip.Entrada>();
        var pdfs = new LinkedHashMap<String, LeitorPacoteZip.Entrada>();
        lido.entradas().forEach(e -> (e.xml() == null ? pdfs : xmls).put(e.resumo().id(), e));
        var vinculos = new TreeMap<String, PacoteDtos.Vinculo>();
        for (var v : pedido.vinculos()) {
            if (!pdfs.containsKey(v.arquivoId()) || vinculos.putIfAbsent(v.arquivoId(), v) != null ||
                    v.xmlIds().stream().anyMatch(id -> !xmls.containsKey(id)) ||
                    (v.finalidade() == FinalidadeAnexo.VIAGEM && v.xmlIds().size() != 1))
                throw new ArquivoInvalidoException("Associe cada PDF uma única vez; documento de viagem exige uma viagem e fatura permite várias");
        }
        if (!vinculos.keySet().equals(pdfs.keySet())) throw new ArquivoInvalidoException("Todos os PDFs precisam de associação explícita");
        StringBuilder config = new StringBuilder();
        vinculos.forEach((id,v) -> config.append(id).append(':').append(v.finalidade()).append(':').append(String.join(",",v.xmlIds().stream().sorted().toList())).append('\n'));
        String hashConfig = ConteudoXml.sha256(config.toString().getBytes(StandardCharsets.UTF_8));
        coordenacao.bloquear();
        var existente = pacotes.findBySha256(lido.sha256());
        if (existente.isPresent()) {
            if (!existente.get().getConfiguracaoSha256().equals(hashConfig)) throw new ConflitoRecursoException("ZIP já importado com outra associação; registros originais foram preservados");
            return resultado(existente.get(), true);
        }
        // A regra de um CT-e por viagem vale neste fluxo; legado divergente exige revisão.
        for (var e : xmls.values()) {
            for (var viagem : viagens.findAllByNumero(e.xml().numero())) {
                if (documentos.findByViagemId(viagem.getId()).stream().anyMatch(d -> !Objects.equals(d.getChaveAcesso(),e.xml().chaveAcesso())))
                    throw new ConflitoRecursoException("Viagem já possui outro CT-e; confira a correção antes de importar");
            }
        }
        var porXml = new LinkedHashMap<String, Documento>();
        for (var e : xmls.values()) {
            var resposta = processamento.processarArquivoXml(new EntradaMultipart(e.resumo().nomeArquivo(),e.bytes()));
            porXml.put(e.resumo().id(),documentos.findById(resposta.documentoId()).orElseThrow());
        }
        var pacote = pacotes.saveAndFlush(Pacote.builder().nomeArquivo(nomeSeguro(arquivo.getOriginalFilename()))
                .sha256(lido.sha256()).configuracaoSha256(hashConfig).criadoEm(OffsetDateTime.now(ZoneOffset.UTC))
                .documentos(new LinkedHashSet<>(porXml.values())).build());
        for (var v : vinculos.values()) {
            var entrada = pdfs.get(v.arquivoId());
            var destinos = new LinkedHashSet<Viagem>();
            v.xmlIds().stream().sorted().forEach(id -> destinos.add(porXml.get(id).getViagem()));
            String numero = destinos.iterator().next().getNumero();
            String token = UUID.randomUUID().toString();
            var caminho = armazenamento.prepararPdf(entrada.bytes(), numero, token);
            anexos.saveAndFlush(Anexo.builder().pacote(pacote).nomeArquivo(entrada.resumo().nomeArquivo())
                    .finalidade(v.finalidade()).sha256(entrada.resumo().sha256()).token(token)
                    .numeroArmazenamento(numero).caminho(caminho.toString()).viagens(destinos).build());
        }
        return resultado(pacote,false);
    }

    private PacoteDtos.Resultado resultado(Pacote pacote, boolean reutilizado) {
        return new PacoteDtos.Resultado(pacote.getId(),reutilizado,pacote.getDocumentos().stream()
                .sorted(Comparator.comparing(Documento::getId)).map(DocumentoResponse::de).toList(),
                anexos.findByPacoteId(pacote.getId()).stream().map(AnexoResponse::de).toList());
    }
    private String nomeSeguro(String nome) {
        String simples = nome.replace('\\','/'); simples = simples.substring(simples.lastIndexOf('/')+1);
        if (simples.isBlank() || simples.length()>255 || simples.chars().anyMatch(c -> c<32)) throw new ArquivoInvalidoException("Nome do ZIP inválido");
        return simples;
    }
    private record EntradaMultipart(String nome, byte[] bytes) implements MultipartFile {
        public String getName(){return "arquivo";}
        public String getOriginalFilename(){return nome;}
        public String getContentType(){return "application/xml";}
        public boolean isEmpty(){return bytes.length==0;}
        public long getSize(){return bytes.length;}
        public byte[] getBytes(){return bytes.clone();}
        public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}
        public void transferTo(File destino) throws IOException {throw new UnsupportedOperationException("Use armazenamento transacional");}
    }
}
