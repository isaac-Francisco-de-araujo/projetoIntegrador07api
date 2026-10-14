package br.com.rotabrasil.service;

import br.com.rotabrasil.model.Viagem;
import org.springframework.stereotype.Service;

/** Preparado para envio futuro. SMTP não foi configurado e nenhum e-mail real é enviado. */
@Service
public class EmailService {
    public void enviarDocumentos(Viagem viagem) {
        throw new UnsupportedOperationException("Envio de e-mail aguardando configuração SMTP");
    }
}
