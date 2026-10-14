package br.com.rotabrasil.service;

import java.time.LocalDate;

/** Dados extraídos do XML. Os valores permanecem nulos quando o mapeamento real ainda não foi configurado. */
public record DadosViagemXml(String numero, LocalDate data, String status, String nomeCliente, String cnpjCliente, String emailCliente, String telefoneCliente) {}
