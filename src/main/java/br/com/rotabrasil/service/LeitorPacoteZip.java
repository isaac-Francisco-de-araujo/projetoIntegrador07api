package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.PacoteDtos;
import br.com.rotabrasil.exception.ArquivoInvalidoException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.*;

/** Lê entradas como bytes limitados, nunca extrai nomes recebidos para o sistema de arquivos. */
@Component
public class LeitorPacoteZip {
    public static final int MAX_ENTRADAS=100, MAX_ARQUIVO=20*1024*1024, MAX_TOTAL=100*1024*1024;
    public record Entrada(PacoteDtos.Arquivo resumo,byte[] bytes,DadosViagemXml xml){}
    public record PacoteLido(String sha256,List<Entrada> entradas) {
        public PacoteDtos.Inspecao resumo(){return new PacoteDtos.Inspecao(sha256,entradas.stream().map(Entrada::resumo).toList());}
    }
    public PacoteLido ler(MultipartFile arquivo) {
        if(arquivo==null||arquivo.isEmpty()||arquivo.getOriginalFilename()==null||!arquivo.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".zip"))throw invalido("Selecione um ZIP não vazio");
        Path temporario=null;
        try {
            byte[] pacote;try(var input=arquivo.getInputStream()){pacote=input.readNBytes(MAX_ARQUIVO+1);}
            if(pacote.length>MAX_ARQUIVO)throw invalido("ZIP excede 20 MB");
            temporario=Files.createTempFile("rotabrasil-pacote-",".zip");Files.write(temporario,pacote);
            var entradas=new ArrayList<Entrada>();var nomes=new HashSet<String>();var numeros=new HashSet<String>();int total=0,quantidade=0;
            try(var zip=new ZipFile(temporario.toFile(),StandardCharsets.UTF_8)) {
                var elementos=zip.entries();
                while(elementos.hasMoreElements()) {
                    var entry=elementos.nextElement();if(++quantidade>MAX_ENTRADAS)throw invalido("ZIP excede 100 entradas");
                    String nome=entry.getName();validarNome(nome);
                    if(!nomes.add(Normalizer.normalize(nome,Normalizer.Form.NFC).toLowerCase(Locale.ROOT)))throw invalido("ZIP contém nomes repetidos ou ambíguos");
                    if(entry.isDirectory())continue;
                    String tipo=nome.toLowerCase(Locale.ROOT).endsWith(".xml")?"XML":nome.toLowerCase(Locale.ROOT).endsWith(".pdf")?"PDF":null;
                    if(tipo==null)throw invalido("O ZIP deve conter somente XMLs e PDFs");
                    if(entry.getSize()>MAX_ARQUIVO || entry.getSize()<1)throw invalido("Arquivo vazio ou excede 20 MB");
                    byte[] bytes;try(var input=zip.getInputStream(entry)){bytes=input.readNBytes(Math.min(MAX_ARQUIVO,MAX_TOTAL-total)+1);}
                    total+=bytes.length;if(bytes.length>MAX_ARQUIVO||total>MAX_TOTAL)throw invalido("Limite de descompressão excedido (20 MB por arquivo, 100 MB no total)");
                    if(entry.getCompressedSize()>0 && bytes.length>1024*1024 && (long)bytes.length>entry.getCompressedSize()*100)throw invalido("Taxa de compressão excede o limite de segurança");
                    var crc=new CRC32();crc.update(bytes);if(crc.getValue()!=entry.getCrc())throw invalido("ZIP corrompido");
                    DadosViagemXml dados=null;
                    if(tipo.equals("XML")) {
                        dados=new ExtratorDadosXml().extrair(new ByteArrayInputStream(bytes));
                        if(!numeros.add(dados.numero()))throw invalido("Mais de um CT-e para a mesma viagem no ZIP; confira os documentos");
                    }else if(bytes.length<5 || !new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))throw invalido("Arquivo PDF sem assinatura reconhecida");
                    var resumo=new PacoteDtos.Arquivo("a"+entradas.size(),nome,tipo,bytes.length,ConteudoXml.sha256(bytes),dados==null?null:dados.numero(),dados==null?null:dados.chaveAcesso(),dados==null?null:dados.nomeCliente(),dados==null?null:dados.cnpjCliente());
                    entradas.add(new Entrada(resumo,bytes,dados));
                }
            }
            if(numeros.isEmpty())throw invalido("O ZIP precisa conter pelo menos um CT-e válido para identificar as viagens");
            return new PacoteLido(ConteudoXml.sha256(pacote),List.copyOf(entradas));
        }catch(ArquivoInvalidoException ex){throw ex;}
        catch(IOException|IllegalArgumentException ex){throw new ArquivoInvalidoException("Não foi possível ler o ZIP; confira se está íntegro e sem senha",ex);}
        finally{if(temporario!=null)try{Files.deleteIfExists(temporario);}catch(IOException ex){temporario.toFile().deleteOnExit();}}
    }
    private void validarNome(String nome) {
        if(nome==null||nome.isBlank()||nome.length()>255||nome.indexOf('\\')>=0||nome.startsWith("/")||nome.contains(":")||nome.chars().anyMatch(c->c<32))throw invalido("Nome de entrada inválido no ZIP");
        for(String parte:nome.split("/"))if(parte.equals("..")||parte.equals("."))throw invalido("Caminho inseguro no ZIP");
    }
    private ArquivoInvalidoException invalido(String mensagem){return new ArquivoInvalidoException(mensagem);}
}
