package br.com.rotabrasil.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Linha sentinela criada por migração; a trava é mantida pelo banco, não pela JVM. */
@Entity
@Table(name = "controle_importacao")
public class ControleImportacao {
    @Id private Integer id;
    protected ControleImportacao() {}
}
