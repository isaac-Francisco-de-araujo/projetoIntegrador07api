package br.com.rotabrasil.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clientes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Cliente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String nome;
    @Column(unique = true) private String cnpj;
    private String email;
    private String telefone;
    @OneToMany(mappedBy = "cliente")
    @Builder.Default private List<Viagem> viagens = new ArrayList<>();
}
