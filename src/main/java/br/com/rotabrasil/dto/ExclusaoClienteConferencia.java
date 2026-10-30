package br.com.rotabrasil.dto;

public record ExclusaoClienteConferencia(Long clienteId, String nome, int viagens, int documentos,
        int pdfsExclusivos, int pdfsCompartilhados, int envios, int conferenciasEmail,
        int tentativasEmail, int tentativasDesconhecidas, String conferenciaSha256) {}
