package br.com.rotabrasil;

import br.com.rotabrasil.dto.PacoteDtos;
import br.com.rotabrasil.exception.DiagnosticoPacoteException;
import br.com.rotabrasil.service.LeitorPacoteZip;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiagnosticoPacoteTest {
    @Test void resultadoTransacionalDesconhecidoNaoETratadoComoFalhaComprovada() throws Exception {
        var lido=leitor.ler(upload(zip(arquivos())));
        var contexto=new br.com.rotabrasil.service.DiagnosticoConfirmacaoPacote();
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            contexto.iniciar(lido.resumo());contexto.marcar(lido.entradas().getFirst(),"PUBLICACAO_ARQUIVO");
            for(var s:org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations())
                s.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_UNKNOWN);
            var r=contexto.falha(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,"FALHA_TESTE","Mensagem sintetica","Revise").diagnostico();
            assertThat(r.situacao()).isEqualTo("RESULTADO_DESCONHECIDO");assertThat(r.invalidos()).isZero();
            assertThat(r.diagnosticos().getFirst().situacao()).isEqualTo("DESCONHECIDO");
            assertThat(r.diagnosticos().getFirst().novaTentativa()).isFalse();assertThat(r.localizacao()).contains("não confirmada");
        } finally {org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();}
    }
    final LeitorPacoteZip leitor=new LeitorPacoteZip();
    static byte[] texto(String texto){return texto.getBytes(StandardCharsets.UTF_8);}
    static byte[] zip(Map<String,byte[]> entradas) throws Exception {
        var bytes=new ByteArrayOutputStream();try(var z=new ZipOutputStream(bytes)) {
            for(var e:entradas.entrySet()){z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue());z.closeEntry();}
        }return bytes.toByteArray();
    }
    static LinkedHashMap<String,byte[]> arquivos(){var a=new LinkedHashMap<String,byte[]>();a.put("ctes/viagem.xml",texto(CteFixture.xml()));return a;}
    MockMultipartFile upload(byte[] bytes){return new MockMultipartFile("arquivo","documentos.zip","application/zip",bytes);}
    PacoteDtos.Inspecao erro(byte[] bytes) {
        try{leitor.ler(upload(bytes));throw new AssertionError("Esperado bloqueio");}
        catch(DiagnosticoPacoteException ex){return ex.diagnostico();}
    }
    PacoteDtos.Diagnostico item(PacoteDtos.Inspecao i,String caminho){return i.diagnosticos().stream().filter(d->Objects.equals(d.caminhoInterno(),caminho)).findFirst().orElseThrow();}
    @Test void zipValidoTemIdentidadeLocalizacaoEAssociacaoPendenteSemPublicar() throws Exception {
        var a=arquivos();a.put("cobrancas/fatura.pdf",texto("%PDF-sintetico"));var r=leitor.ler(upload(zip(a))).resumo();
        assertThat(r.operacaoId()).isNotBlank();assertThat(r.pacote()).isEqualTo("documentos.zip");assertThat(r.conteudoValido()).isTrue();
        assertThat(r.validos()).isEqualTo(1);assertThat(r.pendentes()).isEqualTo(1);assertThat(r.naoExaminados()).isZero();
        var d=item(r,"cobrancas/fatura.pdf");assertThat(d.arquivo()).isEqualTo("fatura.pdf");assertThat(d.etapa()).isEqualTo("ASSOCIACAO_PDF");
        assertThat(d.codigo()).isEqualTo("ASSOCIACAO_PENDENTE");assertThat(d.situacao()).isEqualTo("PENDENTE");assertThat(d.mensagem()).contains("estrutura interna");
        assertThat(r.localizacao()).contains("Nenhum documento publicado","Temporário de leitura removido");
        assertThat(r.arquivos().get(1).nomeArquivo()).isEqualTo("fatura.pdf");
    }
    @Test void pdfInvalidoNaoImpedeConferirOutrasEntradasNemDesaparece() throws Exception {
        var a=arquivos();a.put("cobrancas/fatura.pdf",texto("nao e PDF"));a.put("comprovantes/valido.pdf",texto("%PDF-sintetico"));
        var r=erro(zip(a));assertThat(r.interrompida()).isFalse();assertThat(r.conteudoValido()).isFalse();assertThat(r.arquivos()).hasSize(2);
        assertThat(r.validos()).isEqualTo(1);assertThat(r.invalidos()).isEqualTo(1);assertThat(r.pendentes()).isEqualTo(1);
        var d=item(r,"cobrancas/fatura.pdf");assertThat(d.arquivo()).isEqualTo("fatura.pdf");assertThat(d.etapa()).isEqualTo("VALIDACAO_PDF");
        assertThat(d.codigo()).isEqualTo("PDF_NAO_RECONHECIDO");assertThat(d.situacao()).isEqualTo("INVALIDO");assertThat(d.novaTentativa()).isTrue();assertThat(d.acaoSugerida()).contains("pacote corrigido");
        assertThat(item(r,"comprovantes/valido.pdf").situacao()).isEqualTo("PENDENTE");
    }
    @Test void xmlInvalidoIdentificadoSemRepassarConteudoOuCausaInterna() throws Exception {
        var a=arquivos();a.put("ctes/defeituoso.xml",texto("<!DOCTYPE CTe SYSTEM 'file:///C:/segredo-fiscal-privado'><CTe>"));a.put("outros/valido.pdf",texto("%PDF-sintetico"));
        var r=erro(zip(a));var d=item(r,"ctes/defeituoso.xml");assertThat(d.arquivo()).isEqualTo("defeituoso.xml");assertThat(d.etapa()).isEqualTo("VALIDACAO_XML");
        assertThat(d.codigo()).isEqualTo("XML_INVALIDO");assertThat(d.situacao()).isEqualTo("INVALIDO");assertThat(r.diagnosticos()).hasSize(3);
        assertThat(r.toString()).doesNotContain("segredo-fiscal-privado","DOCTYPE","rotabrasil-pacote-","SAXParseException");
    }
    @Test void mesmoNomeEmDiretoriosDiferentesMantemCaminhosEIdsIndependentes() throws Exception {
        var a=arquivos();a.put("a/fatura.pdf",texto("%PDF-a"));a.put("b/fatura.pdf",texto("%PDF-b"));
        var r=leitor.ler(upload(zip(a))).resumo();assertThat(r.arquivos()).hasSize(3);
        assertThat(item(r,"a/fatura.pdf").arquivo()).isEqualTo("fatura.pdf");assertThat(item(r,"b/fatura.pdf").arquivo()).isEqualTo("fatura.pdf");
        assertThat(item(r,"a/fatura.pdf").arquivoId()).isNotEqualTo(item(r,"b/fatura.pdf").arquivoId());
    }
    @ParameterizedTest @ValueSource(strings={"../escape.pdf","/escape.pdf","C:/escape.pdf","pasta\\escape.pdf","programa.exe"})
    void segurancaInterrompeSemValidarRestantes(String caminho) throws Exception {
        var a=arquivos();a.put(caminho,texto("%PDF-inseguro"));a.put("nao-examinado.pdf",texto("%PDF-outro"));var r=erro(zip(a));
        assertThat(r.interrompida()).isTrue();assertThat(r.naoExaminados()).isEqualTo(1);assertThat(r.arquivos()).hasSize(1);
        assertThat(item(r,caminho).etapa()).isEqualTo("SEGURANCA_ZIP");assertThat(item(r,caminho).situacao()).isEqualTo("INVALIDO");
        assertThat(r.diagnosticos()).noneMatch(d->"nao-examinado.pdf".equals(d.caminhoInterno()));
    }
    @Test void crcCorrompidoEZipProtegidoSaoDiagnosticadosSemExcecaoPrivada() throws Exception {
        byte[] crc=zip(arquivos()),senha=zip(arquivos());
        for(int i=0;i<crc.length-46;i++)if(crc[i]==0x50&&crc[i+1]==0x4b&&crc[i+2]==1&&crc[i+3]==2){crc[i+16]^=1;break;}
        for(int i=0;i<senha.length-46;i++)if(senha[i]==0x50&&senha[i+1]==0x4b&&senha[i+2]==1&&senha[i+3]==2){senha[i+8]|=1;break;}
        var r=erro(crc);assertThat(item(r,"ctes/viagem.xml").codigo()).isEqualTo("CRC_DIVERGENTE");assertThat(r.interrompida()).isTrue();
        var protegido=erro(senha);assertThat(protegido.diagnosticos()).extracting(PacoteDtos.Diagnostico::codigo).contains("ZIP_CORROMPIDO_OU_PROTEGIDO");assertThat(protegido.conteudoValido()).isFalse();
    }
    @Test void limitesInterrompemEContamEntradasNaoExaminadas() throws Exception {
        var a=arquivos();byte[] grande=new byte[2*1024*1024];System.arraycopy(texto("%PDF-"),0,grande,0,5);
        a.put("grande.pdf",grande);a.put("restante.pdf",texto("%PDF-restante"));var r=erro(zip(a));
        assertThat(item(r,"grande.pdf").codigo()).isEqualTo("TAXA_COMPRESSAO_EXCEDIDA");assertThat(item(r,"grande.pdf").etapa()).isEqualTo("DESCOMPRESSAO");
        assertThat(r.naoExaminados()).isEqualTo(1);assertThat(r.interrompida()).isTrue();
        a.put("grande.pdf",new byte[LeitorPacoteZip.MAX_ARQUIVO+1]);assertThat(item(erro(zip(a)),"grande.pdf").codigo()).isEqualTo("LIMITE_ARQUIVO");
    }
    @Test void pacoteCorrigidoGeraNovaOperacaoSemReescreverDiagnosticoAnterior() throws Exception {
        var a=arquivos();a.put("fatura.pdf",texto("invalido"));var anterior=erro(zip(a));a.put("fatura.pdf",texto("%PDF-corrigido"));
        var novo=leitor.ler(upload(zip(a))).resumo();assertThat(novo.operacaoId()).isNotEqualTo(anterior.operacaoId());assertThat(novo.sha256()).isNotEqualTo(anterior.sha256());
        assertThat(novo.conteudoValido()).isTrue();assertThat(item(anterior,"fatura.pdf").codigo()).isEqualTo("PDF_NAO_RECONHECIDO");
    }
    @Test void limiteTotalInterrompeMesmoComArquivosETaxasIndividualmentePermitidos() throws Exception {
        var a=arquivos();byte[] conteudo=new byte[18*1024*1024],bloco=new byte[16*1024];var random=new Random(42);
        for(int offset=0;offset<conteudo.length;offset+=144*1024){random.nextBytes(bloco);System.arraycopy(bloco,0,conteudo,offset,Math.min(bloco.length,conteudo.length-offset));}
        System.arraycopy(texto("%PDF-"),0,conteudo,0,5);
        for(int n=0;n<6;n++)a.put("lote/arquivo-"+n+".pdf",conteudo);
        a.put("restante.pdf",texto("%PDF-restante"));var r=erro(zip(a));
        var d=item(r,"lote/arquivo-5.pdf");assertThat(d.codigo()).isEqualTo("LIMITE_DESCOMPRESSAO");
        assertThat(d.etapa()).isEqualTo("DESCOMPRESSAO");assertThat(d.situacao()).isEqualTo("INVALIDO");
        assertThat(r.interrompida()).isTrue();assertThat(r.naoExaminados()).isEqualTo(1);assertThat(r.pendentes()).isEqualTo(5);
    }
    @Test void cabecalhoNaoEValidacaoEstruturalNemDeteccaoDeSenhaPdf() throws Exception {
        var a=arquivos();a.put("protegido.pdf",texto("%PDF-1.7\n/Encrypt\nconteudo corrompido sintetico"));
        var d=item(leitor.ler(upload(zip(a))).resumo(),"protegido.pdf");assertThat(d.situacao()).isEqualTo("PENDENTE");
        assertThat(d.mensagem()).contains("estrutura interna, senha e conteúdo comercial não foram verificados");
    }
    @Test void falhaLeituraDoUploadNaoExibeCaminhoFisicoOuMensagemPrivada() throws Exception {
        MultipartFile arquivo=mock(MultipartFile.class);when(arquivo.getOriginalFilename()).thenReturn("C:\\fakepath\\documentos.zip");
        when(arquivo.getInputStream()).thenThrow(new IOException("C:/servidor/privado senha=segredo"));
        assertThatThrownBy(()->leitor.ler(arquivo)).isInstanceOfSatisfying(DiagnosticoPacoteException.class,ex->{
            var r=ex.diagnostico();assertThat(r.pacote()).isEqualTo("documentos.zip");assertThat(r.diagnosticos().getFirst().codigo()).isEqualTo("FALHA_LEITURA");
            assertThat(r.naoExaminados()).isEqualTo(-1);assertThat(r.toString()).doesNotContain("C:/servidor","senha=","fakepath");
        });
    }
}
