package br.com.rotabrasil.dto;

public record ProcessamentoXmlResponse(boolean sucesso, String mensagem, Long viagemId, Long documentoId) {}
