package br.com.rotabrasil.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "envios")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Envio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Viagem viagem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Cliente cliente;
    private LocalDateTime dataEnvio;
    @Enumerated(EnumType.STRING) private Status status;
    private String mensagem;
    public enum Status { PENDENTE, ENVIADO, ERRO }
}
