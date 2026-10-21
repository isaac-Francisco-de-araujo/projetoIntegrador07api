package br.com.rotabrasil.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name="pacotes") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Pacote {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String nomeArquivo;
    @Column(nullable=false,length=64,unique=true) private String sha256;
    @Column(nullable=false,length=64) private String configuracaoSha256;
    @Column(nullable=false) private OffsetDateTime criadoEm;
    @ManyToMany
    @JoinTable(name="pacote_documentos",joinColumns=@JoinColumn(name="pacote_id"),inverseJoinColumns=@JoinColumn(name="documento_id"))
    @Builder.Default private Set<Documento> documentos=new LinkedHashSet<>();
}
