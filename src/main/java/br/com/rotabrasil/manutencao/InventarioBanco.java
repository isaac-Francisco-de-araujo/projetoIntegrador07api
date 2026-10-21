package br.com.rotabrasil.manutencao;

import br.com.rotabrasil.service.ConteudoXml;
import br.com.rotabrasil.service.ExtratorDadosXml;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Inventário somente de leitura; dados fiscais completos ficam apenas no relatório local. */
public final class InventarioBanco {
    private final Path raiz;
    public InventarioBanco(Path raiz) { this.raiz = raiz.toAbsolutePath().normalize(); }
    public record Achado(String codigo, Long registroId) {}
    public record Identidade(long documentoId, String chave, String sha256) {}
    public record Arquivo(String relativo, String sha256) {}
    public record Relatorio(String impressao, List<Achado> achados, List<Identidade> identidades,
                            List<Arquivo> arquivos, Map<String, Long> totais) {
        public boolean aprovado() { return achados.isEmpty(); }
    }

    public Relatorio analisar(Connection conexao) throws Exception {
        var achados = new ArrayList<Achado>();
        var dados = new LinkedHashMap<String, List<Map<String, String>>>();
        for (String tabela : List.of("clientes", "viagens", "documentos", "envios", "pacotes", "anexos", "pacote_documentos", "anexo_viagens")) {
            if (List.of("pacotes", "anexos", "pacote_documentos", "anexo_viagens").contains(tabela)) {
                try (var rs = conexao.getMetaData().getTables(null,"PUBLIC",tabela.toUpperCase(Locale.ROOT),new String[]{"TABLE"})) {
                    if (!rs.next()) continue;
                }
            }
            String ordem = tabela.equals("pacote_documentos") ? "pacote_id, documento_id" : tabela.equals("anexo_viagens") ? "anexo_id, viagem_id" : "id";
            var linhas = new ArrayList<Map<String, String>>();
            try (var consulta = conexao.createStatement(); var rs = consulta.executeQuery("SELECT * FROM " + tabela + " ORDER BY " + ordem)) {
                while (rs.next()) {
                    var linha = new TreeMap<String, String>();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        linha.put(rs.getMetaData().getColumnName(i).toLowerCase(Locale.ROOT), rs.getString(i));
                    }
                    if (tabela.equals("documentos")) {
                        linha.putIfAbsent("chave_acesso", null);
                        linha.putIfAbsent("sha256", null);
                    }
                    linhas.add(linha);
                }
            }
            dados.put(tabela, linhas);
        }
        duplicados(dados.get("clientes"), "cnpj", "CNPJ_DUPLICADO", achados);
        duplicados(dados.get("viagens"), "numero", "NUMERO_VIAGEM_DUPLICADO", achados);
        duplicados(dados.get("documentos"), "chave_acesso", "CHAVE_REGISTRADA_DUPLICADA", achados);
        var clientes = indexar(dados.get("clientes"));
        var viagens = indexar(dados.get("viagens"));
        var usados = new HashSet<Path>();
        var xmlLidos = new HashMap<Path, String>();
        var identidades = new ArrayList<Identidade>();
        var chaves = new HashMap<String, Long>();
        for (var viagem : dados.get("viagens")) {
            if (!clientes.containsKey(viagem.get("cliente_id"))) achados.add(new Achado("VIAGEM_SEM_CLIENTE", id(viagem)));
        }
        for (var envio : dados.get("envios")) {
            var viagem = viagens.get(envio.get("viagem_id"));
            if (viagem == null || !Objects.equals(viagem.get("cliente_id"), envio.get("cliente_id"))) {
                achados.add(new Achado("ENVIO_COM_RELACIONAMENTO_INCONSISTENTE", id(envio)));
            }
        }
        for (var doc : dados.get("documentos")) {
            long id = id(doc);
            try {
                Path caminho = Path.of(Objects.requireNonNull(doc.get("caminho"))).toAbsolutePath().normalize();
                if (!caminho.startsWith(raiz)) { achados.add(new Achado("CAMINHO_FORA_DA_RAIZ", id)); continue; }
                usados.add(caminho);
                if (!Files.isRegularFile(caminho, LinkOption.NOFOLLOW_LINKS)) {
                    achados.add(new Achado("DOCUMENTO_SEM_ARQUIVO", id)); continue;
                }
                if (!caminho.toRealPath().startsWith(raiz.toRealPath())) {
                    achados.add(new Achado("CAMINHO_FORA_DA_RAIZ", id)); continue;
                }
                var viagem = viagens.get(doc.get("viagem_id"));
                if (viagem == null) { achados.add(new Achado("DOCUMENTO_SEM_VIAGEM", id)); continue; }
                if (!"XML".equalsIgnoreCase(doc.get("tipo"))) continue;
                byte[] bytes;
                try (var entrada = Files.newInputStream(caminho)) { bytes = ConteudoXml.ler(entrada); }
                var xml = new ExtratorDadosXml().extrair(new ByteArrayInputStream(bytes));
                String hash = ConteudoXml.sha256(bytes);
                xmlLidos.put(caminho, hash);
                var cliente = clientes.get(viagem.get("cliente_id"));
                if (!xml.numero().equals(viagem.get("numero")) || cliente == null || !xml.cnpjCliente().equals(cliente.get("cnpj"))) {
                    achados.add(new Achado("IDENTIDADE_RELACIONAMENTO_DIVERGENTE", id));
                }
                if ((doc.get("chave_acesso") != null && !xml.chaveAcesso().equals(doc.get("chave_acesso"))) ||
                        (doc.get("sha256") != null && !hash.equals(doc.get("sha256")))) {
                    achados.add(new Achado("IDENTIDADE_REGISTRADA_DIVERGENTE", id));
                }
                if (chaves.putIfAbsent(xml.chaveAcesso(), id) != null) achados.add(new Achado("CHAVE_FISCAL_DUPLICADA", id));
                identidades.add(new Identidade(id, xml.chaveAcesso(), hash));
            } catch (Exception ex) {
                achados.add(new Achado("ARQUIVO_NAO_IDENTIFICAVEL", id));
            }
        }
        var pdfs = indexar(dados.getOrDefault("anexos", List.of()));
        var pacotes = indexar(dados.getOrDefault("pacotes", List.of()));
        var docs = indexar(dados.get("documentos"));
        var destinosPdf = new HashMap<String, Set<String>>();
        for (var vinculo : dados.getOrDefault("anexo_viagens", List.of())) {
            if (!pdfs.containsKey(vinculo.get("anexo_id")) || !viagens.containsKey(vinculo.get("viagem_id")))
                achados.add(new Achado("ANEXO_VINCULO_INVALIDO", null));
            destinosPdf.computeIfAbsent(vinculo.get("anexo_id"), k -> new HashSet<>()).add(vinculo.get("viagem_id"));
        }
        var viagensPacote = new HashMap<String, Set<String>>();
        for (var vinculo : dados.getOrDefault("pacote_documentos", List.of())) {
            if (!pacotes.containsKey(vinculo.get("pacote_id")) || !docs.containsKey(vinculo.get("documento_id")))
                achados.add(new Achado("PACOTE_VINCULO_INVALIDO", null));
            else viagensPacote.computeIfAbsent(vinculo.get("pacote_id"), k -> new HashSet<>()).add(docs.get(vinculo.get("documento_id")).get("viagem_id"));
        }
        for (var pdf : pdfs.values()) {
            long id = id(pdf);
            var destinos = destinosPdf.getOrDefault(pdf.get("id"), Set.of());
            if (!pacotes.containsKey(pdf.get("pacote_id")) || destinos.isEmpty() || !viagensPacote.getOrDefault(pdf.get("pacote_id"),Set.of()).containsAll(destinos) ||
                    ("VIAGEM".equals(pdf.get("finalidade")) && destinos.size()!=1))
                achados.add(new Achado("ANEXO_RELACIONAMENTO_INVALIDO",id));
            try {
                Path caminho=Path.of(Objects.requireNonNull(pdf.get("caminho"))).toAbsolutePath().normalize();
                if (!caminho.startsWith(raiz)) {achados.add(new Achado("CAMINHO_FORA_DA_RAIZ",id));continue;}
                usados.add(caminho);
                if (!Files.isRegularFile(caminho,LinkOption.NOFOLLOW_LINKS) || !caminho.toRealPath().startsWith(raiz.toRealPath())) {
                    achados.add(new Achado("ANEXO_SEM_ARQUIVO",id));continue;
                }
                String hash=hashArquivo(caminho);xmlLidos.put(caminho,hash);
                if (!hash.equals(pdf.get("sha256"))) achados.add(new Achado("ANEXO_CONTEUDO_DIVERGENTE",id));
            } catch (Exception ex) {achados.add(new Achado("ANEXO_ARQUIVO_INVALIDO",id));}
        }
        var arquivos = new ArrayList<Arquivo>();
        if (Files.exists(raiz, LinkOption.NOFOLLOW_LINKS)) {
            Files.walkFileTree(raiz, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    if (dir.equals(raiz.resolve(".pendentes"))) {
                        try (var filhos = Files.list(dir)) {
                            if (filhos.findAny().isPresent()) achados.add(new Achado("OPERACOES_PENDENTES", null));
                        }
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path arquivo, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    if (attrs.isSymbolicLink() || !attrs.isRegularFile()) {
                        achados.add(new Achado("ARTEFATO_NAO_REGULAR", null));
                    } else {
                        String hash = hashArquivo(arquivo);
                        arquivos.add(new Arquivo(raiz.relativize(arquivo).toString(), hash));
                        if (xmlLidos.containsKey(arquivo) && !xmlLidos.get(arquivo).equals(hash)) {
                            achados.add(new Achado("ARQUIVO_MUDOU_DURANTE_LEITURA", null));
                        }
                        if (!usados.contains(arquivo.toAbsolutePath().normalize())) achados.add(new Achado("ARQUIVO_SEM_DOCUMENTO", null));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        arquivos.sort(Comparator.comparing(Arquivo::relativo));
        var digest = MessageDigest.getInstance("SHA-256");
        for (var tabela : dados.entrySet()) {
            atualizar(digest, tabela.getKey());
            for (var linha : tabela.getValue()) {
                for (var campo : linha.entrySet()) { atualizar(digest, campo.getKey()); atualizar(digest, campo.getValue()); }
            }
        }
        for (var arquivo : arquivos) { atualizar(digest, arquivo.relativo()); atualizar(digest, arquivo.sha256()); }
        var totais = new TreeMap<String, Long>();
        dados.forEach((tabela, linhas) -> totais.put(tabela, (long) linhas.size()));
        return new Relatorio(HexFormat.of().formatHex(digest.digest()), List.copyOf(achados), List.copyOf(identidades),
                List.copyOf(arquivos), Map.copyOf(totais));
    }

    private static void atualizar(MessageDigest digest, String texto) {
        digest.update((texto == null ? "N" : "S" + texto.length() + ":" + texto).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
    public static String hashArquivo(Path caminho) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var entrada = new java.security.DigestInputStream(Files.newInputStream(caminho), digest)) {
                entrada.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static long id(Map<String, String> linha) { return Long.parseLong(linha.get("id")); }
    private static Map<String, Map<String, String>> indexar(List<Map<String, String>> linhas) {
        var indice = new HashMap<String, Map<String, String>>();
        linhas.forEach(linha -> indice.put(linha.get("id"), linha));
        return indice;
    }
    private static void duplicados(List<Map<String, String>> linhas, String campo, String codigo, List<Achado> achados) {
        var vistos = new HashSet<String>();
        for (var linha : linhas) {
            String valor = linha.get(campo);
            if (valor != null && !vistos.add(valor)) achados.add(new Achado(codigo, id(linha)));
        }
    }
}
