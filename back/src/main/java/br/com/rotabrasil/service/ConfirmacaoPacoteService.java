package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.PacoteDtos;
import br.com.rotabrasil.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Fronteira externa: captura inclusive falhas no commit após rollback/compensação. */
@Service @RequiredArgsConstructor
public class ConfirmacaoPacoteService {
    private final PacoteService pacotes;
    @Transactional(propagation=Propagation.NEVER)
    public PacoteDtos.Resultado confirmar(MultipartFile arquivo,PacoteDtos.Confirmacao pedido) {
        var contexto=new DiagnosticoConfirmacaoPacote();
        try{return pacotes.confirmar(arquivo,pedido,contexto);}
        catch(DiagnosticoPacoteException ex){throw ex;}
        catch(RuntimeException ex) {
            if(contexto.inspecao==null)throw ex;
            if(ex instanceof ArquivoInvalidoException)
                throw contexto.falha(HttpStatus.BAD_REQUEST,"ASSOCIACAO_INVALIDA","Associação incompleta ou inválida para este PDF.","Escolha uma finalidade e viagens válidas para cada PDF; documento de viagem exige exatamente uma viagem.");
            if(ex instanceof PessimisticLockingFailureException)
                throw contexto.falha(HttpStatus.SERVICE_UNAVAILABLE,"BANCO_OCUPADO","Banco ocupado durante a confirmação.","Aguarde a operação concorrente terminar, consulte os dados e confira novamente.");
            if(ex instanceof DataIntegrityViolationException)
                throw contexto.falha(HttpStatus.CONFLICT,"CONFLITO_PERSISTENCIA","Os registros não puderam ser confirmados por uma restrição de integridade.","Revise os documentos existentes e os vínculos; se persistir, solicite revisão ao responsável.");
            if(ex instanceof ConflitoRecursoException||ex instanceof ConflitoDocumentoException)
                throw contexto.falha(HttpStatus.CONFLICT,contexto.etapa==PacoteDtos.Etapa.PUBLICACAO_ARQUIVO?"CONFLITO_ARMAZENAMENTO":"CONFLITO_VERSAO_OU_SELECAO",
                        "Conflito com seleção, versão ou registro existente; nenhum original foi substituído.","Confira os dados já importados e a versão original. Correções precisam preservar os anteriores; gere nova conferência.");
            throw contexto.falha(HttpStatus.INTERNAL_SERVER_ERROR,ex instanceof ArmazenamentoException?"FALHA_ARMAZENAMENTO":"FALHA_CONFIRMACAO",
                    "Não foi possível concluir esta etapa da importação.","Consulte os dados e as pendências de armazenamento antes de tentar novamente. Se persistir, solicite revisão ao responsável.");
        }
    }
}
