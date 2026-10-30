package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.PacoteDtos;
import br.com.rotabrasil.exception.DiagnosticoPacoteException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Efeitos complementares somente depois do resultado transacional da importação. */
@Service @RequiredArgsConstructor
public class FluxoPacoteOperacionalService {
    private final HistoricoProcessamentoService historico;
    private final NotificacaoRecebimentoService notificacoes;
    private final ExportacaoPacoteService exportacoes;
    public PacoteDtos.Inspecao inspecionar(MultipartFile arquivo,String anterior,String recebimento){
        try{
            var r=historico.inspecionar(arquivo,anterior);
            notificacoes.evento(recebimento,"CONFERENCIA_CONCLUIDA","conferencia",r.operacaoId(),
                    "Conferência dos arquivos finalizada. A importação ainda exige associações e confirmação explícita. Válidos: "+r.validos()+"; inválidos: "+r.invalidos()+"; pendentes: "+r.pendentes()+".");
            if(r.invalidos()>0||r.pendentes()>0)pendencias(recebimento,r.operacaoId(),"pendencia-conferencia");
            return r;
        }catch(DiagnosticoPacoteException ex){pendencias(recebimento,ex.diagnostico().operacaoId(),"pendencia-conferencia");throw ex;}
    }
    public PacoteDtos.Resultado confirmar(MultipartFile arquivo,PacoteDtos.Confirmacao pedido,String anterior,String recebimento){
        PacoteDtos.Resultado r;
        try{r=historico.confirmar(arquivo,pedido,anterior);}
        catch(DiagnosticoPacoteException ex){pendencias(recebimento,ex.diagnostico().operacaoId(),"pendencia-importacao");throw ex;}
        try{notificacoes.evento(recebimento,"IMPORTACAO_CONCLUIDA","importacao",r.operacaoId(),
                "Importação interna concluída: "+r.documentos().size()+" XML(s) e "+r.anexos().size()+" PDF(s). Isto não confirma exportação ou entrega de documentos ao cliente.");}
        catch(RuntimeException ex){registrarFalhaComplementar(r.operacaoId());}
        try{exportacoes.automatico(r.pacoteId(),r.reutilizado());}
        catch(RuntimeException ex){registrarFalhaComplementar(r.operacaoId());}
        return r;
    }
    private void pendencias(String recebimento,String referencia,String chave){notificacoes.evento(recebimento,"PROCESSAMENTO_COM_PENDENCIAS",chave,referencia,
            "Processamento com pendências. Há arquivos recusados ou associações a revisar; não se confirma importação integral nem exportação. Consulte o diagnóstico da operação.");}
    private void registrarFalhaComplementar(String referencia){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Importação preservada; efeito complementar pendente. Referência {}",referencia);}
}
