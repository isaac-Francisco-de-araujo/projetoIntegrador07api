package br.com.rotabrasil.dto;

import br.com.rotabrasil.model.FinalidadeAnexo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.UniqueElements;
import java.util.List;

public final class PacoteDtos {
    private PacoteDtos(){}
    public enum Etapa { CONFERENCIA,LEITURA_ZIP,SEGURANCA_ZIP,DESCOMPRESSAO,VALIDACAO_CONTEUDO,VALIDACAO_XML,IDENTIFICACAO_VIAGEM,VALIDACAO_PDF,ASSOCIACAO_PDF,LEITURA_TEMPORARIA,REVALIDACAO_PACOTE,CONFERENCIA_DUPLICIDADE,CONFERENCIA_VERSAO_XML,IMPORTACAO_XML,REGISTRO_PACOTE,ARMAZENAMENTO_PDF,REGISTRO_PDF,PUBLICACAO_ARQUIVO,CONFIRMACAO_TRANSACIONAL }
    public enum Codigo { ZIP_INVALIDO,FALHA_LEITURA,LIMITE_ZIP,LIMITE_ENTRADAS,CAMINHO_INSEGURO,NOME_AMBIGUO,TIPO_NAO_PERMITIDO,LIMITE_ARQUIVO,ZIP_CORROMPIDO_OU_PROTEGIDO,LIMITE_DESCOMPRESSAO,TAXA_COMPRESSAO_EXCEDIDA,CRC_DIVERGENTE,ARQUIVO_VAZIO,XML_INVALIDO,CNPJ_INVALIDO,CAMPO_CADASTRAL_INVALIDO,CONTATO_XML_INVALIDO,NCT_DUPLICADO,PDF_NAO_RECONHECIDO,ASSOCIACAO_PENDENTE,XML_CONFERIDO,CTE_VALIDO_AUSENTE,FALHA_ARMAZENAMENTO_TEMPORARIO,ASSOCIACAO_INVALIDA,BANCO_OCUPADO,CONFLITO_PERSISTENCIA,CONFLITO_ARMAZENAMENTO,CONFLITO_VERSAO_OU_SELECAO,FALHA_ARMAZENAMENTO,FALHA_CONFIRMACAO,RESULTADO_IMPORTACAO_DESCONHECIDO,IMPORTACAO_NAO_CONFIRMADA }
    public enum Gravidade { INFO,ERRO,PENDENCIA }
    public enum SituacaoArquivo { VALIDO,INVALIDO,PENDENTE,ERRO,NAO_IMPORTADO,DESCONHECIDO,IMPORTADO,REUTILIZADO,ASSOCIADO }
    public record Arquivo(String id,String nomeArquivo,String caminhoInterno,String tipo,long tamanho,String sha256,String numero,String chaveAcesso,String cliente,String cnpj){}
    public record Diagnostico(String arquivoId,String arquivo,String caminhoInterno,String etapa,String codigo,
                             String mensagem,String gravidade,String situacao,boolean novaTentativa,String acaoSugerida) {
        public Diagnostico{Etapa.valueOf(etapa);Codigo.valueOf(codigo);Gravidade.valueOf(gravidade);SituacaoArquivo.valueOf(situacao);}
    }
    public enum EstadoTemporario { NAO_CRIADO, REMOVIDO, LIMPEZA_PENDENTE, DESCONHECIDO }
    public record Inspecao(String operacaoId,String pacote,String sha256,List<Arquivo> arquivos,
                           List<Diagnostico> diagnosticos,String situacao,boolean conteudoValido,boolean interrompida,
                           int validos,int invalidos,int pendentes,int naoExaminados,String localizacao,EstadoTemporario temporario) {
        public Inspecao{if(temporario==null)temporario=EstadoTemporario.DESCONHECIDO;}
        public Inspecao(String operacaoId,String pacote,String sha256,List<Arquivo> arquivos,List<Diagnostico> diagnosticos,String situacao,boolean conteudoValido,boolean interrompida,int validos,int invalidos,int pendentes,int naoExaminados,String localizacao){this(operacaoId,pacote,sha256,arquivos,diagnosticos,situacao,conteudoValido,interrompida,validos,invalidos,pendentes,naoExaminados,localizacao,EstadoTemporario.DESCONHECIDO);}
    }
    public record Vinculo(@NotBlank String arquivoId,@NotNull FinalidadeAnexo finalidade,@NotEmpty @Size(max=100) @UniqueElements List<@NotBlank String> xmlIds){}
    public record Confirmacao(@NotBlank @Pattern(regexp="[0-9a-f]{64}") String sha256,@NotNull @Size(max=100) List<@Valid Vinculo> vinculos){}
    public record Resultado(Long pacoteId,boolean reutilizado,List<DocumentoResponse> documentos,List<AnexoResponse> anexos,String operacaoId){
        public Resultado(Long pacoteId,boolean reutilizado,List<DocumentoResponse> documentos,List<AnexoResponse> anexos){this(pacoteId,reutilizado,documentos,anexos,null);}
    }
}
