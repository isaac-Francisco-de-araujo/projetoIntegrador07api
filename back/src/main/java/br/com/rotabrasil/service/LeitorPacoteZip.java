package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.PacoteDtos;
import br.com.rotabrasil.dto.PacoteDtos.Diagnostico;
import br.com.rotabrasil.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.*;

/** Não extrai caminhos recebidos. Erros de conteúdo são locais; limites/segurança interrompem. */
@Component
public class LeitorPacoteZip {
    public static final int MAX_ENTRADAS=100, MAX_ARQUIVO=20*1024*1024, MAX_TOTAL=100*1024*1024;
    public record Entrada(PacoteDtos.Arquivo resumo,byte[] bytes,DadosViagemXml xml){}
    public record PacoteLido(PacoteDtos.Inspecao resumo,List<Entrada> entradas) {
        public String sha256(){return resumo.sha256();}
    }
    private static class Conferencia {
        String id=UUID.randomUUID().toString(),pacote,hash,atual,nome,tipo;
        final List<Entrada> entradas=new ArrayList<>();
        final List<Diagnostico> diagnosticos=new ArrayList<>();
        int naoExaminados=-1;
        boolean interrompida,temporarioCriado,temporarioRemovido=true;
        void erro(String etapa,String codigo,String mensagem,String acao,boolean interromper) {
            diagnosticos.add(new Diagnostico(atual,simples(nome),publico(nome),etapa,codigo,mensagem,"ERRO","INVALIDO",true,acao));
            if(interromper){interrompida=true;throw new Interromper();}
        }
        PacoteLido resultado() {
            int validos=(int)diagnosticos.stream().filter(d->d.situacao().equals("VALIDO")).count();
            int invalidos=(int)diagnosticos.stream().filter(d->d.arquivoId()!=null&&d.situacao().equals("INVALIDO")).count();
            int pendentes=(int)diagnosticos.stream().filter(d->d.situacao().equals("PENDENTE")).count();
            boolean valido=!interrompida&&diagnosticos.stream().noneMatch(d->d.gravidade().equals("ERRO"));
            var resumo=new PacoteDtos.Inspecao(id,pacote,hash,entradas.stream().map(Entrada::resumo).toList(),List.copyOf(diagnosticos),
                    interrompida?"INTERROMPIDA":valido?(pendentes>0?"ASSOCIACAO_PENDENTE":"CONFERIDA"):"CONFERENCIA_COM_PENDENCIAS",
                    valido,interrompida,validos,invalidos,pendentes,naoExaminados,
                    "Nenhum documento publicado no armazenamento definitivo. "+(!temporarioCriado?"Nenhum temporário de leitura criado.":temporarioRemovido?
                            "Temporário de leitura removido após a conferência.":"Temporário de leitura com limpeza pendente no encerramento do processo."),
                    !temporarioCriado?PacoteDtos.EstadoTemporario.NAO_CRIADO:temporarioRemovido?PacoteDtos.EstadoTemporario.REMOVIDO:PacoteDtos.EstadoTemporario.LIMPEZA_PENDENTE);
            return new PacoteLido(resumo,List.copyOf(entradas));
        }
    }
    private static class Interromper extends RuntimeException {}

    public PacoteLido ler(MultipartFile arquivo) {
        var lido=conferir(arquivo);
        if(!lido.resumo().conteudoValido())throw new DiagnosticoPacoteException(lido.resumo(),HttpStatus.BAD_REQUEST);
        return lido;
    }

    public PacoteLido conferir(MultipartFile arquivo) {
        var c=new Conferencia();c.pacote=simples(arquivo==null?null:arquivo.getOriginalFilename());
        Path temporario=null;
        try {
            if(arquivo!=null&&arquivo.getOriginalFilename()!=null)try{ValidacaoDocumental.nomeSimples(arquivo.getOriginalFilename());}
            catch(ArquivoInvalidoException ex){c.erro("LEITURA_ZIP","ZIP_INVALIDO","Nome do pacote inválido.","Selecione uma cópia com nome simples válido e confira novamente.",true);}
            if(arquivo==null||arquivo.isEmpty()||c.pacote==null||!c.pacote.toLowerCase(Locale.ROOT).endsWith(".zip"))
                c.erro("LEITURA_ZIP","ZIP_INVALIDO","Selecione um ZIP não vazio.","Selecione o pacote original ou uma cópia corrigida e confira novamente.",true);
            byte[] pacote;
            try(var input=arquivo.getInputStream()){pacote=input.readNBytes(MAX_ARQUIVO+1);}
            catch(IOException ex){c.erro("LEITURA_ZIP","FALHA_LEITURA","Não foi possível ler o upload do ZIP.","Selecione novamente o arquivo original e refaça a conferência.",true);return null;}
            if(pacote.length>MAX_ARQUIVO)c.erro("SEGURANCA_ZIP","LIMITE_ZIP","ZIP excede 20 MiB.","Divida o pacote na origem respeitando os limites e confira novamente.",true);
            c.hash=ConteudoXml.sha256(pacote);
            temporario=Files.createTempFile("rotabrasil-pacote-",".zip");c.temporarioCriado=true;Files.write(temporario,pacote);
            var nomes=new HashSet<String>();var numeros=new HashSet<String>();int total=0,indice=0;
            try(var zip=new ZipFile(temporario.toFile(),StandardCharsets.UTF_8)) {
                c.naoExaminados=zip.size();
                if(zip.size()>MAX_ENTRADAS)c.erro("SEGURANCA_ZIP","LIMITE_ENTRADAS","ZIP excede 100 entradas; nenhuma entrada foi examinada.","Divida o pacote e confira novamente.",true);
                var elementos=zip.entries();
                while(elementos.hasMoreElements()) {
                    var entry=elementos.nextElement();c.naoExaminados--;
                    c.nome=entry.getName();c.atual=entry.isDirectory()?null:"a"+indice++;
                    if(!nomeValido(c.nome))c.erro("SEGURANCA_ZIP","CAMINHO_INSEGURO","Entrada com nome ou caminho interno inseguro.","Recrie o ZIP na origem com caminhos relativos seguros, sem travessias ou caracteres de controle.",true);
                    if(!nomes.add(Normalizer.normalize(c.nome,Normalizer.Form.NFC).toLowerCase(Locale.ROOT)))
                        c.erro("SEGURANCA_ZIP","NOME_AMBIGUO","ZIP contém caminhos repetidos ou ambíguos.","Diferencie os caminhos internos na origem e gere outro ZIP.",true);
                    if(entry.isDirectory())continue;
                    c.tipo=c.nome.toLowerCase(Locale.ROOT).endsWith(".xml")?"XML":c.nome.toLowerCase(Locale.ROOT).endsWith(".pdf")?"PDF":null;
                    if(c.tipo==null)c.erro("SEGURANCA_ZIP","TIPO_NAO_PERMITIDO","Somente XML e PDF são permitidos no ZIP.","Remova da origem os tipos não permitidos e gere outro ZIP.",true);
                    if(entry.getSize()>MAX_ARQUIVO)c.erro("DESCOMPRESSAO","LIMITE_ARQUIVO","Entrada excede 20 MiB descomprimidos.","Obtenha arquivos dentro dos limites e gere novo pacote.",true);
                    byte[] bytes;
                    try(var input=zip.getInputStream(entry)){bytes=input.readNBytes(Math.min(MAX_ARQUIVO,MAX_TOTAL-total)+1);}
                    catch(IOException ex){c.erro("DESCOMPRESSAO","ZIP_CORROMPIDO_OU_PROTEGIDO","A leitura desta entrada falhou; a integridade do ZIP não está garantida.","Obtenha uma cópia íntegra e sem senha do ZIP. Entradas restantes não foram examinadas.",true);return null;}
                    total+=bytes.length;
                    if(bytes.length>MAX_ARQUIVO||total>MAX_TOTAL)c.erro("DESCOMPRESSAO","LIMITE_DESCOMPRESSAO","Limite de descompressão excedido (20 MiB por arquivo, 100 MiB no total).","Divida o pacote na origem. A inspeção das entradas restantes foi interrompida.",true);
                    if(entry.getCompressedSize()>0&&bytes.length>1024*1024&&(long)bytes.length>entry.getCompressedSize()*100)
                        c.erro("DESCOMPRESSAO","TAXA_COMPRESSAO_EXCEDIDA","Taxa de compressão excede o limite de segurança de 100:1.","Revise o pacote na origem e gere um ZIP dentro dos limites.",true);
                    var crc=new CRC32();crc.update(bytes);
                    if(crc.getValue()!=entry.getCrc())c.erro("DESCOMPRESSAO","CRC_DIVERGENTE","Conteúdo da entrada diverge do CRC do ZIP.","Obtenha uma cópia íntegra do pacote; a inspeção foi interrompida.",true);
                    if(bytes.length==0){c.erro("VALIDACAO_CONTEUDO","ARQUIVO_VAZIO","O arquivo está vazio.","Obtenha uma cópia com conteúdo e gere um pacote corrigido.",false);continue;}
                    DadosViagemXml dados=null;
                    if(c.tipo.equals("XML")) {
                        try{dados=new ExtratorDadosXml().extrair(new ByteArrayInputStream(bytes));}
                        catch(DadosXmlInvalidosException ex){c.erro("VALIDACAO_XML",ex.codigo(),ex.getMessage(),"Corrija o cadastro no sistema de origem e gere novo XML, preservando o documento anterior.",false);continue;}
                        catch(ArquivoInvalidoException ex){c.erro("VALIDACAO_XML","XML_INVALIDO","XML inválido ou incompatível com as verificações de CT-e 4.00.","Abra o XML original e obtenha uma cópia válida no sistema de origem; gere outro ZIP e confira novamente.",false);continue;}
                        if(!numeros.add(dados.numero())){c.erro("IDENTIFICACAO_VIAGEM","NCT_DUPLICADO","Mais de um CT-e para a mesma viagem no ZIP.","Revise os CT-es da viagem na origem; não escolha uma versão automaticamente.",false);continue;}
                    } else if(bytes.length<5||!new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-")) {
                        c.erro("VALIDACAO_PDF","PDF_NAO_RECONHECIDO","Arquivo não reconhecido como PDF pelo cabeçalho %PDF-.","Abra o arquivo original, obtenha uma cópia válida e refaça a conferência com o pacote corrigido.",false);continue;
                    }
                    var resumo=new PacoteDtos.Arquivo(c.atual,simples(c.nome),c.nome,c.tipo,bytes.length,ConteudoXml.sha256(bytes),dados==null?null:dados.numero(),dados==null?null:dados.chaveAcesso(),dados==null?null:publico(dados.nomeCliente()),dados==null?null:publico(dados.cnpjCliente()));
                    c.entradas.add(new Entrada(resumo,bytes,dados));
                    c.diagnosticos.add(new Diagnostico(c.atual,simples(c.nome),c.nome,dados==null?"ASSOCIACAO_PDF":"VALIDACAO_XML",
                            dados==null?"ASSOCIACAO_PENDENTE":"XML_CONFERIDO",dados==null?"Cabeçalho PDF reconhecido; estrutura interna, senha e conteúdo comercial não foram verificados.":"CT-e conferido pelas regras atuais; ainda não importado.",
                            dados==null?"PENDENCIA":"INFO",dados==null?"PENDENTE":"VALIDO",true,dados==null?"Escolha a finalidade e as viagens deste PDF. Abra o original para revisão manual.":"Confira os metadados antes de confirmar a importação integral."));
                }
            }
            c.atual=null;c.nome=null;
            if(numeros.isEmpty())c.erro("IDENTIFICACAO_VIAGEM","CTE_VALIDO_AUSENTE","O pacote precisa de pelo menos um CT-e válido para identificar as viagens.","Inclua um XML de CT-e válido e confira o pacote corrigido.",false);
        }catch(Interromper ex){/* A interrupção já foi diagnosticada; não examinar entradas restantes. */}
        catch(ZipException|IllegalArgumentException ex){
            c.interrompida=true;c.diagnosticos.add(new Diagnostico(c.atual,simples(c.nome),publico(c.nome),"LEITURA_ZIP","ZIP_CORROMPIDO_OU_PROTEGIDO","Não foi possível abrir o ZIP; pode estar corrompido, protegido ou em formato incompatível.","ERRO","INVALIDO",true,"Obtenha uma cópia íntegra do ZIP, sem senha, e confira novamente."));
        }catch(IOException ex){
            c.interrompida=true;c.diagnosticos.add(new Diagnostico(c.atual,simples(c.nome),publico(c.nome),"LEITURA_TEMPORARIA","FALHA_ARMAZENAMENTO_TEMPORARIO","Falha na área temporária de leitura do pacote.","ERRO","INVALIDO",true,"Tente uma nova conferência. Se persistir, solicite revisão do armazenamento ao responsável."));
        }finally{if(temporario!=null)try{Files.deleteIfExists(temporario);}catch(IOException ex){c.temporarioRemovido=false;temporario.toFile().deleteOnExit();}}
        return c.resultado();
    }
    private boolean nomeValido(String nome) {
        if(nome==null||nome.isBlank()||nome.length()>255||nome.indexOf('\\')>=0||nome.startsWith("/")||nome.contains(":")||nome.chars().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))return false;
        for(String parte:nome.split("/"))if(parte.equals("..")||parte.equals(".")||parte.isEmpty())return false;
        return true;
    }
    public static String publico(String valor){if(valor==null)return null;String seguro=valor.replaceAll("[\\p{Cc}\\p{Cf}]","�");return seguro.substring(0,Math.min(seguro.length(),255));}
    public static String simples(String nome){if(nome==null)return null;String s=nome.replace('\\','/');return publico(s.substring(s.lastIndexOf('/')+1));}
}
