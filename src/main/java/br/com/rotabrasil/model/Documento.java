package br.com.rotabrasil.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "documentos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Documento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String nomeArquivo;
    @Column(nullable = false) private String tipo;
    @Column(nullable = false) private String caminho;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viagem_id", nullable = false) private Viagem viagem;
}
