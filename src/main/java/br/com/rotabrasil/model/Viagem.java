package br.com.rotabrasil.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "viagens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Viagem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String numero;
    private LocalDate data;
    private String status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false) private Cliente cliente;
    @OneToMany(mappedBy = "viagem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default private List<Documento> documentos = new ArrayList<>();
}
