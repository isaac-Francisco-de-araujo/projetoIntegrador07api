package br.com.rotabrasil.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name="anexos") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Anexo {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) private Pacote pacote;
    @Column(nullable=false) private String nomeArquivo;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private FinalidadeAnexo finalidade;
    @Column(nullable=false) private String caminho;
    @Column(nullable=false,length=64) private String sha256;
    @Column(nullable=false,length=36,unique=true) private String token;
    @Column(nullable=false,length=9) private String numeroArmazenamento;
    private OffsetDateTime arquivadoEm;
    @ManyToMany
    @JoinTable(name="anexo_viagens",joinColumns=@JoinColumn(name="anexo_id"),inverseJoinColumns=@JoinColumn(name="viagem_id"))
    @Builder.Default private Set<Viagem> viagens=new LinkedHashSet<>();
}
