package br.com.rotabrasil.dto;
import java.util.List;
public record PreviaEmailResponse(Long viagemId,String destinatario,boolean enviado,List<DocumentoResponse> documentos,List<AnexoResponse> anexos) {}
