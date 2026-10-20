package br.com.rotabrasil.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class ArmazenamentoTransacionalService {
    private static final Logger LOG = LoggerFactory.getLogger(ArmazenamentoTransacionalService.class);
    private final OrganizadorArquivosService arquivos;

    public Path preparar(byte[] conteudo, String numero, String chave) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() ||
                !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Armazenamento do importador exige transação ativa");
        }
        var operacao = arquivos.prepararXml(conteudo, numero, chave);
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    arquivos.publicar(operacao);
                }

                @Override
                public void afterCompletion(int status) {
                    try {
                        if (status == STATUS_COMMITTED) {
                            arquivos.confirmar(operacao);
                        } else if (status == STATUS_ROLLED_BACK) {
                            arquivos.desfazer(operacao);
                        } else {
                            arquivos.fechar(operacao);
                            LOG.warn("Resultado transacional desconhecido; operação {} preservada para reconciliação", operacao.id());
                        }
                    } catch (RuntimeException ex) {
                        // O commit já pode ter ocorrido; não retornar uma falsa falha ao cliente.
                        LOG.warn("Limpeza incompleta da operação {}; executar reconciliação", operacao.id());
                        try { arquivos.fechar(operacao); } catch (RuntimeException fechamento) {
                            LOG.warn("Trava da operação {} não pôde ser liberada", operacao.id());
                        }
                    }
                }
            });
        } catch (RuntimeException ex) {
            try { arquivos.desfazer(operacao); } catch (RuntimeException limpeza) { ex.addSuppressed(limpeza); }
            throw ex;
        }
        return operacao.destino();
    }
}
