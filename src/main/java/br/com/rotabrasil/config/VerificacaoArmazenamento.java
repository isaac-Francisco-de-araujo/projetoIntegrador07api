package br.com.rotabrasil.config;

import br.com.rotabrasil.service.ReconciliacaoArmazenamentoService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Manutenção opt-in; não publica caminhos, documentos ou contatos no log. */
@Component
@ConditionalOnProperty(name = "rotabrasil.armazenamento.verificar-pendencias", havingValue = "true")
@RequiredArgsConstructor
public class VerificacaoArmazenamento implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(VerificacaoArmazenamento.class);
    private final ReconciliacaoArmazenamentoService reconciliacao;
    @Value("${rotabrasil.armazenamento.recuperar-confirmadas:false}")
    private boolean recuperar;

    @Override
    public void run(ApplicationArguments args) {
        var pendencias = recuperar ? reconciliacao.recuperarConfirmadas() : reconciliacao.inspecionar();
        LOG.info("Reconciliação de armazenamento: {} operações examinadas", pendencias.size());
        for (var pendencia : pendencias) {
            LOG.info("Operação {}: {}, documentoId={}", pendencia.operacaoId(), pendencia.estado(), pendencia.documentoId());
        }
    }
}
