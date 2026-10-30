package br.com.rotabrasil.dto;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;

public final class OperacaoDtos {
    private OperacaoDtos() {}
    public record Salvar(@Min(0) long versao, @Email @Size(max=255) String remetente,
            boolean exportacaoAutomatica, @NotNull @Size(max=60) String subpasta) {}
    public record Configuracao(long versao,String remetente,boolean exportacaoAutomatica,String subpasta,
            String finalidade,String modoEnvioDocumental,String modoNotificacoes,String situacaoEmail,
            boolean raizAutorizada,String localDaPasta,List<String> subpastas,OffsetDateTime atualizadaEm) {}
    public record CriarPasta(@NotBlank @Size(max=60) String nome) {}
    public record TestarEmail(@NotNull java.util.UUID id,@NotBlank @Email @Size(max=255) String destinatario) {}
    public record Confirmar(@AssertTrue boolean confirmado,java.util.UUID requisicaoId) {
        public Confirmar(boolean confirmado){this(confirmado,null);}
    }
    public record Teste(String id,String tipo,String situacao,String mensagem,OffsetDateTime criadoEm) {}
    public record Item(String id,String origem,Long origemId,String localizacao,String viagens,String sha256,
            long tamanho,String situacao,String etapa,String codigo,String mensagem) {}
    public record Exportacao(String id,Long pacoteId,String subpasta,String situacao,String mensagem,
            OffsetDateTime criadaEm,List<Item> arquivos) {}
    public record Notificacao(String id,String evento,String referencia,String remetente,String destinatario,
            String assunto,String mensagem,String modo,String situacao,OffsetDateTime criadaEm) {}
    public record Recebimento(String id,String sha256,String destinatario,String remetente,
            boolean simulacaoAutorizada,List<Notificacao> notificacoes) {}
}
