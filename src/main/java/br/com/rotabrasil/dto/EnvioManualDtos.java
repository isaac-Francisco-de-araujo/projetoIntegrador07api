package br.com.rotabrasil.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;

public final class EnvioManualDtos {
    private EnvioManualDtos() {}
    public record Conferir(@NotNull @Valid PreviaEmailRequest selecao,
                          @Pattern(regexp="[a-f0-9-]{36}") String anteriorId) {}
    public record Confirmar(@NotNull @Pattern(regexp="[a-f0-9-]{36}") String conferenciaId,
                            @AssertTrue boolean confirmado) {}
    public record Arquivo(String origem, Long id, String nome, String tipo, String finalidade,
                          List<Long> viagemIds, String sha256, int tamanho) {}
    public record Snapshot(int versao,Long viagemId, String numero, String destinatario, List<Arquivo> arquivos,
            String remetente,String assunto,String mensagem) {
        public Snapshot{if(versao==0)versao=1;if(versao!=1&&versao!=2)throw new IllegalArgumentException("Versão de snapshot não suportada");}
        public Snapshot(Long viagemId,String numero,String destinatario,List<Arquivo> arquivos){this(1,viagemId,numero,destinatario,arquivos,null,null,null);}
    }
    public record Conferencia(String id, String modo, OffsetDateTime expiraEm, String anteriorId, Snapshot snapshot) {}
    public record Tentativa(String id, Long viagemId, String modo, String situacao, String motivo,
                            OffsetDateTime criadaEm, OffsetDateTime concluidaEm, String anteriorId, Snapshot snapshot,String ator) {}
}
