package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.PacoteDtos;
import br.com.rotabrasil.exception.DiagnosticoPacoteException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.*;
import java.util.*;

/** Contexto privado da requisição, observado somente depois do término da transação. */
public class DiagnosticoConfirmacaoPacote {
    PacoteDtos.Inspecao inspecao;
    PacoteDtos.Arquivo arquivo;
    PacoteDtos.Etapa etapa=PacoteDtos.Etapa.CONFERENCIA;
    Integer resultadoTransacao;
    public void iniciar(PacoteDtos.Inspecao inspecao) {
        this.inspecao=inspecao;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){resultadoTransacao=status;}
        });
    }
    public void marcar(LeitorPacoteZip.Entrada entrada,String etapa){this.arquivo=entrada==null?null:entrada.resumo();this.etapa=PacoteDtos.Etapa.valueOf(etapa);}
    public void publicacao(LeitorPacoteZip.Entrada entrada) {
        // Registrado antes do callback existente de publicação: identifica também falhas no beforeCommit.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void beforeCommit(boolean readOnly){marcar(entrada,"PUBLICACAO_ARQUIVO");}
        });
    }
    public DiagnosticoPacoteException falha(HttpStatus status,String codigo,String mensagem,String acao) {
        boolean desconhecido=resultadoTransacao==null||resultadoTransacao==TransactionSynchronization.STATUS_UNKNOWN;
        if(desconhecido){codigo="RESULTADO_IMPORTACAO_DESCONHECIDO";mensagem="O resultado da transação não foi confirmado.";acao="Consulte os documentos e a reconciliação de armazenamento antes de repetir. Não repita automaticamente.";}
        var lista=new ArrayList<PacoteDtos.Diagnostico>();
        for(var a:inspecao.arquivos()) {
            boolean atual=arquivo!=null&&a.id().equals(arquivo.id());
            lista.add(new PacoteDtos.Diagnostico(a.id(),a.nomeArquivo(),a.caminhoInterno(),atual?etapa.name():"CONFIRMACAO_TRANSACIONAL",
                    atual?codigo:"IMPORTACAO_NAO_CONFIRMADA",atual?mensagem:"Este arquivo não foi confirmado como parte desta importação.",
                    atual&&!desconhecido?"ERRO":"PENDENCIA",desconhecido?"DESCONHECIDO":atual?"ERRO":"NAO_IMPORTADO",!desconhecido,
                    atual?acao:"Revise o problema do pacote e refaça a conferência antes de confirmar todos os arquivos."));
        }
        if(arquivo==null)lista.add(new PacoteDtos.Diagnostico(null,null,null,etapa.name(),codigo,mensagem,desconhecido?"PENDENCIA":"ERRO",desconhecido?"DESCONHECIDO":"ERRO",!desconhecido,acao));
        String local=desconhecido?"Publicação definitiva não confirmada; consulte a reconciliação. ":
                "Importação revertida; novos registros não foram confirmados. Arquivos anteriores preservados. ";
        local+=inspecao.temporario()==PacoteDtos.EstadoTemporario.LIMPEZA_PENDENTE?"Limpeza do temporário de leitura pendente. ":"Temporário de leitura removido após falha. ";
        local+="Publicações e temporários próprios seguem a compensação existente; resíduos de limpeza devem ser revisados no journal, sem assumir caminho definitivo.";
        return new DiagnosticoPacoteException(new PacoteDtos.Inspecao(UUID.randomUUID().toString(),inspecao.pacote(),inspecao.sha256(),inspecao.arquivos(),List.copyOf(lista),
                desconhecido?"RESULTADO_DESCONHECIDO":"IMPORTACAO_REVERTIDA",false,false,0,desconhecido||arquivo==null?0:1,inspecao.arquivos().size()-(desconhecido||arquivo==null?0:1),0,local),status);
    }
}
