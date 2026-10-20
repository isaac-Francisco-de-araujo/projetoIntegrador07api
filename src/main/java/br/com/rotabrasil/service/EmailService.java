package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.*;
import br.com.rotabrasil.exception.ConflitoRecursoException;
import br.com.rotabrasil.model.Viagem;
import br.com.rotabrasil.repository.EnvioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

/** Prévia e consulta somente de leitura. Nenhum transporte, agendamento ou envio real. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailService {
    private final ViagemService viagens;
    private final DocumentoService documentos;
    private final EnvioRepository envios;

    public PreviaEmailResponse prepararPrevia(Long viagemId, PreviaEmailRequest pedido) {
        viagens.buscarViagem(viagemId);
        var selecionados = pedido.documentoIds().stream().map(id -> {
            var documento = documentos.buscarDocumento(id);
            if (!Objects.equals(documento.getViagem().getId(), viagemId)) {
                throw new ConflitoRecursoException("Documento selecionado não pertence à viagem informada");
            }
            return DocumentoResponse.de(documento);
        }).toList();
        // Metadados para conferência: não lê anexos, não resolve destinatário e não altera histórico.
        return new PreviaEmailResponse(viagemId, pedido.destinatario(), false, selecionados);
    }

    public List<EnvioResponse> listarHistorico(Long viagemId) {
        viagens.buscarViagem(viagemId);
        return envios.findByViagemIdOrderByIdAsc(viagemId).stream().map(EnvioResponse::de).toList();
    }

    /** Compatibilidade interna: continua falhando antes de qualquer tentativa de transporte. */
    public void enviarDocumentos(Viagem viagem) {
        throw new UnsupportedOperationException("Envio real de e-mail aguardando regras operacionais e configuração SMTP");
    }
}
