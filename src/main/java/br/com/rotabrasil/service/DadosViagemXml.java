package br.com.rotabrasil.service;

import java.time.LocalDate;

/** Identidade fiscal separada do número operacional; contatos ausentes permanecem nulos. */
public record DadosViagemXml(String numero, LocalDate data, String status, String nomeCliente,
                             String cnpjCliente, String emailCliente, String telefoneCliente,
                             String chaveAcesso) {}
