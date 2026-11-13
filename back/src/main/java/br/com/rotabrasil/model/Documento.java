package br.com.rotabrasil.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "documentos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Documento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String nomeArquivo;
    @Column(nullable = false) private String tipo;
    @Column(nullable = false) private String caminho;
    // Identidade fiscal específica de XML; outros tipos não exigem chave de CT-e.
    // XMLs legados só são preenchidos por inventário e migração explícita.
    @Column(name = "chave_acesso", length = 44) private String chaveAcesso;
    @Column(length = 64) private String sha256;
    @Column(name = "arquivado_em") private OffsetDateTime arquivadoEm;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viagem_id", nullable = false) private Viagem viagem;
}
