package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.DiagnosticoOperacao;
import br.com.rotabrasil.dto.DiagnosticoOperacao.*;
import br.com.rotabrasil.dto.PacoteDtos;
import br.com.rotabrasil.exception.*;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class DiagnosticoOperacaoService {
    public static final String VERSAO="0.0.1-SNAPSHOT / diagnostico-V10";
    public static final String CAUSA_DESCONHECIDA="Não foi possível identificar a causa. Use a referência da operação para suporte.";
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public static String causa(String codigo){
        return switch(Objects.toString(codigo,"")){
            case "PDF_NAO_RECONHECIDO" -> "O cabeçalho %PDF- não foi reconhecido. Não foi feita validação estrutural ou detecção de senha do PDF.";
            case "XML_INVALIDO" -> "O XML foi recusado pelo extrator CT-e. O conteúdo e os detalhes privados do parser não são expostos.";
            case "ASSOCIACAO_PENDENTE","ASSOCIACAO_INVALIDA" -> "A associação explícita de PDF e viagens está ausente ou não atende às regras do fluxo.";
            case "CAMINHO_INSEGURO","NOME_AMBIGUO" -> "O caminho da entrada foi recusado pelas regras de segurança do ZIP.";
            case "CRC_DIVERGENTE" -> "O CRC dos bytes descomprimidos diverge do informado no ZIP.";
            case "ARQUIVO_VAZIO" -> "A entrada examinada não contém bytes.";
            case "PASTA_INEXISTENTE" -> "O destino não foi encontrado no momento da cópia.";
            case "SEM_PERMISSAO" -> "O sistema de arquivos recusou o acesso solicitado ao destino.";
            case "SEM_ESPACO" -> "O sistema de arquivos informou falta de espaço.";
            case "ARQUIVO_EXISTENTE" -> "Já existe um arquivo que não foi reconhecido como publicação própria; ele foi preservado.";
            case "ORIGEM_AUSENTE" -> "O arquivo de origem não foi encontrado ou não é um arquivo regular.";
            case "ORIGEM_INELEGIVEL" -> "O registro deixou de corresponder ao plano ativo da exportação.";
            case "HASH_DIVERGENTE" -> "O SHA-256 está ausente ou os bytes da origem divergem do hash registrado.";
            case "DESTINO_ALTERADO" -> "Os bytes do destino divergem do SHA-256 registrado.";
            case "DESTINO_AUSENTE" -> "Um arquivo anteriormente exportado não foi encontrado no destino.";
            case "ARQUIVOS_PENDENTES" -> "O manifesto aguarda a conclusão dos documentos do plano.";
            case "LIMITE_ZIP","LIMITE_ENTRADAS","LIMITE_ARQUIVO","LIMITE_DESCOMPRESSAO","TAXA_COMPRESSAO_EXCEDIDA","LIMITE_TOTAL" -> "Um limite de segurança foi atingido; não se presume que as entradas restantes foram examinadas.";
            default -> CAUSA_DESCONHECIDA;
        };
    }
    public DiagnosticoOperacao requisicao(String codigo,String mensagem,int status){
        String correlacao=MDC.get("operacaoId");boolean incerto=status>=500;
        return new DiagnosticoOperacao(correlacao,correlacao,OffsetDateTime.now(ZoneOffset.UTC),VERSAO,"REQUISICAO",null,null,"REQUISICAO",codigo,
                "ERRO",mensagem,CAUSA_DESCONHECIDA,"Confira os dados e consulte o histórico antes de repetir uma alteração.",
                incerto?"RESULTADO_NAO_CONFIRMADO":"REQUISICAO_RECUSADA","PRECISA_ACAO",
                incerto?"A resposta não comprova o resultado de operações anteriores. Consulte o histórico; não repita efeitos externos automaticamente.":"Esta requisição foi recusada. Nenhuma conclusão sobre outras operações é presumida.",List.of(),basicas(null),null,List.of());
    }
    private static List<Acao> basicas(String id){return List.of(new Acao("VER_DETALHES","Ver detalhes",id),new Acao("COPIAR_SUPORTE","Copiar informações para suporte",id));}
    public DiagnosticoOperacao inspecao(PacoteDtos.Inspecao i,String correlacao,OffsetDateTime data){
        return montarProcessamento(json.valueToTree(i),i.operacaoId(),correlacao,data,"CONFERENCIA",i.situacao(),null,null,null);
    }
    public DiagnosticoOperacao consultar(String origem,String id){
        return switch(origem){case "PROCESSAMENTO"->processamento(id);case "EXPORTACAO"->exportacao(id);case "ENVIO"->envio(id);case "NOTIFICACAO"->notificacao(id);default->throw new ArquivoInvalidoException("Origem de diagnóstico inválida");};
    }
    private Map<String,Object> linha(String tabela,String id){var r=jdbc.queryForList("SELECT * FROM "+tabela+" WHERE id=?",id);if(r.isEmpty())throw new RecursoNaoEncontradoException("Operação não encontrada");return r.getFirst();}
    private JsonNode ler(Object valor){try{return valor==null?json.createObjectNode():json.readTree(valor.toString());}catch(Exception ex){return json.createObjectNode();}}
    public DiagnosticoOperacao processamento(String id){
        var r=linha("processamentos",id);var resolucao=r.get("RESOLVIDO_POR")==null?null:new Resolucao((String)r.get("RESOLVIDO_POR"),(OffsetDateTime)r.get("RESOLVIDO_EM"),(String)r.get("CRITERIO_RESOLUCAO"));
        return montarProcessamento(ler(r.get("DIAGNOSTICO")),id,(String)r.get("CORRELACAO"),(OffsetDateTime)r.get("CRIADO_EM"),
                (String)r.get("FASE"),(String)r.get("SITUACAO"),ler(r.get("RESULTADO")),resolucao,(String)r.get("ANTERIOR_ID"));
    }
    private DiagnosticoOperacao montarProcessamento(JsonNode d,String id,String correlacao,OffsetDateTime data,String fase,String situacao,JsonNode resultado,Resolucao resolucao,String anterior){
        boolean importado=Set.of("IMPORTADO","REUTILIZADO").contains(situacao);
        boolean desconhecido=situacao.contains("DESCONHECIDO")||situacao.equals("EM_PROCESSAMENTO");
        boolean andamento=situacao.equals("EM_PROCESSAMENTO")&&data.isAfter(OffsetDateTime.now().minusMinutes(5));
        var problemas=new ArrayList<Problema>();
        if(!importado)for(JsonNode p:d.path("diagnosticos"))if(!p.path("gravidade").asText().equals("INFO")){
            String arquivo=p.path("arquivo").isNull()?null:p.path("arquivo").asText(null),caminho=p.path("caminhoInterno").asText(null),codigo=p.path("codigo").asText("CAUSA_DESCONHECIDA");
            problemas.add(new Problema(p.path("arquivoId").asText(null),arquivo,d.path("pacote").asText("ZIP")+(caminho==null?" (pacote inteiro)":" > "+caminho),"DENTRO_DO_ZIP",p.path("etapa").asText(fase),codigo,
                    p.path("gravidade").asText().equals("ERRO")?"ERRO":"ATENCAO",p.path("mensagem").asText("Operação precisa de revisão."),causa(codigo),p.path("acaoSugerida").asText("Consulte o diagnóstico e selecione o material corrigido.")));
        }
        boolean erro=problemas.stream().anyMatch(p->p.gravidade().equals("ERRO"));
        String estado=resolucao!=null||importado||situacao.equals("CONFERIDA")?"RESOLVIDO":andamento?"EM_PROCESSAMENTO":"PRECISA_ACAO";
        String gravidade=estado.equals("RESOLVIDO")?"SUCESSO":desconhecido?"ATENCAO":erro?"ERRO":"ATENCAO";
        String codigo=desconhecido?"RESULTADO_NAO_CONFIRMADO":importado?"IMPORTACAO_CONCLUIDA":problemas.isEmpty()?"CONFERENCIA_CONCLUIDA":problemas.getFirst().codigo();
        String mensagem=importado?"Documentos importados. Consulte separadamente o resultado da exportação adicional.":resolucao!=null?"Pendência corrigida por uma operação posterior comprovada.":desconhecido?"Resultado da importação ainda não confirmado. Consulte o histórico antes de repetir.":erro?"Importação bloqueada — arquivo ou pacote precisa de correção.":problemas.isEmpty()?"Conferência concluída; a importação exige confirmação explícita.":"Conferência com pendências — revise as associações antes de confirmar.";
        String impacto=importado?"Documentos preservados no acervo. Este resultado não comprova exportação ou envio de e-mail.":desconhecido?"Não foi possível confirmar o resultado transacional. Não se afirma que os documentos foram perdidos ou gravados.":fase.equals("CONFERENCIA")?"Nenhum documento desta conferência foi gravado no acervo. A inspeção não autoriza importação parcial.":"Esta importação não foi concluída. O rollback preserva os registros anteriores; eventuais resíduos exigem reconciliação.";
        if(!importado&&!desconhecido&&fase.equals("CONFIRMACAO")&&!situacao.equals("IMPORTACAO_REVERTIDA"))impacto="Confirmação bloqueada antes de concluir a importação. Nenhum documento desta solicitação teve gravação confirmada; consulte o diagnóstico transacional.";
        impacto+=" Estado do temporário de leitura: "+d.path("temporario").asText("DESCONHECIDO")+". A localização no ZIP identifica a origem, não um arquivo temporário disponível.";
        if(d.path("interrompida").asBoolean())impacto+=" Inspeção interrompida: entradas não examinadas não são válidas.";
        var acoes=new ArrayList<>(basicas(id));
        if(estado.equals("PRECISA_ACAO")&&!desconhecido){
            acoes.add(new Acao("SELECIONAR_ZIP_CORRIGIDO","Selecionar ZIP corrigido",id));
            if(problemas.stream().anyMatch(p->p.codigo().startsWith("ASSOCIACAO")))acoes.add(new Acao("REVISAR_ASSOCIACOES","Revisar associações",id));
        }
        if(desconhecido)acoes.add(new Acao("CONSULTAR_ACERVO","Consultar acervo e histórico",id));
        String criterio=resolucao==null?(anterior==null?"":"Nova operação vinculada à anterior "+anterior+". "):"Correção comprovada; o diagnóstico original foi preservado. ";
        return new DiagnosticoOperacao(id,correlacao,data,VERSAO,"PROCESSAMENTO",resultado==null||!resultado.hasNonNull("pacoteId")?null:resultado.path("pacoteId").asLong(),d.path("pacote").asText(null),
                problemas.isEmpty()?fase:problemas.getFirst().etapa(),codigo,gravidade,mensagem,problemas.isEmpty()?CAUSA_DESCONHECIDA:problemas.getFirst().causaConhecida(),criterio+(desconhecido?"Consulte a operação e o acervo antes de qualquer nova confirmação.":"Corrija o material e faça nova conferência vinculada; não substitua os originais."),situacao,estado,impacto,problemas,acoes,resolucao,List.of());
    }
    public DiagnosticoOperacao exportacao(String id){
        var r=linha("exportacoes",id);Long pacoteId=((Number)r.get("PACOTE_ID")).longValue();
        var nomes=jdbc.queryForList("SELECT nome_arquivo FROM pacotes WHERE id=?",String.class,pacoteId);String pacote=nomes.isEmpty()?null:nomes.getFirst();
        var problemas=jdbc.query("SELECT * FROM exportacao_arquivos WHERE exportacao_id=? AND situacao<>'CONCLUIDO' ORDER BY localizacao",(rs,n)->{
            String local=rs.getString("localizacao"),codigo=rs.getString("codigo");
            return new Problema(rs.getString("id"),local.substring(local.lastIndexOf('/')+1),"Raiz autorizada do backend/"+Objects.toString(r.get("SUBPASTA"),"")+(Objects.toString(r.get("SUBPASTA"),"").isEmpty()?"":"/")+local,"DESTINO_EXPORTACAO",rs.getString("etapa"),codigo==null?"COPIA_PENDENTE":codigo,
                    codigo==null||codigo.equals("ARQUIVOS_PENDENTES")?"ATENCAO":"ERRO",rs.getString("mensagem"),causa(codigo),"Corrija o destino ou a origem indicada e tente somente a exportação; não reimporte o ZIP.");
        },id);
        String situacao=(String)r.get("SITUACAO");boolean ok=situacao.equals("CONCLUIDA");
        boolean andamento=situacao.equals("EM_PROCESSAMENTO")&&r.get("EXECUCAO_EM") instanceof OffsetDateTime t&&t.isAfter(OffsetDateTime.now().minusMinutes(5));
        long concluidos=jdbc.queryForObject("SELECT COUNT(*) FROM exportacao_arquivos WHERE exportacao_id=? AND situacao='CONCLUIDO'",Long.class,id);
        var recuperacoes=new ArrayList<Recuperacao>();for(JsonNode h:ler(r.get("RECUPERACOES")))try{recuperacoes.add(json.treeToValue(h,Recuperacao.class));}catch(Exception ignorada){}
        var acoes=new ArrayList<>(basicas(id));if(!ok){acoes.add(new Acao("CONFIGURAR_PASTA","Abrir configurações da pasta",id));if(!andamento)acoes.add(new Acao("RETOMAR_EXPORTACAO","Tentar somente a exportação",id));}
        String gravidade=ok?"SUCESSO":andamento||situacao.equals("EM_PROCESSAMENTO")?"ATENCAO":problemas.stream().anyMatch(p->p.gravidade().equals("ERRO"))?"ERRO":"ATENCAO";
        var resolucao=ok?new Resolucao(id,(OffsetDateTime)r.get("ATUALIZADA_EM"),"Todos os itens e o manifesto tiveram a cópia e o SHA-256 confirmados."):null;
        return new DiagnosticoOperacao(id,(String)r.get("CORRELACAO"),(OffsetDateTime)r.get("CRIADA_EM"),VERSAO,"EXPORTACAO",pacoteId,pacote,"EXPORTACAO",ok?"EXPORTACAO_CONCLUIDA":andamento?"EXPORTACAO_EM_PROCESSAMENTO":situacao.equals("EM_PROCESSAMENTO")?"RESULTADO_NAO_CONFIRMADO":"EXPORTACAO_PENDENTE",gravidade,
                ok?"Exportação concluída.":andamento?"Exportação iniciada — resultado ainda não confirmado.":"Documentos importados — exportação pendente.",problemas.isEmpty()?CAUSA_DESCONHECIDA:problemas.getFirst().causaConhecida(),
                "Consulte os itens. Corrija a condição externa e tente somente a exportação. Para outro destino, salve a configuração e inicie uma exportação explícita.",situacao,ok?"RESOLVIDO":andamento?"EM_PROCESSAMENTO":"PRECISA_ACAO",
                "Os documentos estão preservados no sistema. Não é necessário importar novamente. Itens concluídos: "+concluidos+"; pendentes: "+problemas.size()+". Cópias anteriores não são sobrescritas. Nenhum envio de e-mail é autorizado por esta recuperação.",problemas,acoes,resolucao,recuperacoes);
    }
    private DiagnosticoOperacao envio(String id){
        var r=linha("tentativas_email",id);String situacao=(String)r.get("SITUACAO");boolean ok=Set.of("SIMULADO","ACEITO_SMTP").contains(situacao),incerto=situacao.equals("DESCONHECIDO");
        var sucessoras=jdbc.queryForList("SELECT id,concluida_em FROM tentativas_email WHERE anterior_id=? AND situacao IN ('SIMULADO','ACEITO_SMTP') ORDER BY criada_em DESC LIMIT 1",id);
        Resolucao resolucao=ok?new Resolucao(id,(OffsetDateTime)r.get("CONCLUIDA_EM"),"Resultado conhecido do transporte; aceite não comprova entrega."):!incerto&&!sucessoras.isEmpty()?new Resolucao((String)sucessoras.getFirst().get("ID"),(OffsetDateTime)sucessoras.getFirst().get("CONCLUIDA_EM"),"Reenvio explicitamente vinculado com resultado conhecido; a falha original foi preservada."):null;
        return new DiagnosticoOperacao(id,(String)r.get("CORRELACAO"),(OffsetDateTime)r.get("CRIADA_EM"),VERSAO,"ENVIO",null,null,"TRANSPORTE_EMAIL",incerto?"ENVIO_DESCONHECIDO":ok?"ENVIO_RESULTADO_CONHECIDO":"ENVIO_RECUSADO",resolucao!=null?"SUCESSO":incerto?"ATENCAO":"ERRO",
                incerto?"Resultado do envio desconhecido. Consulte a tentativa antes de confirmar um reenvio.":ok?"Resultado do transporte registrado; não equivale a entrega.":"Falha na tentativa de envio.",CAUSA_DESCONHECIDA,
                "Consulte a tentativa e seu snapshot; não reenvie por causa de timeout.",situacao,resolucao!=null?"RESOLVIDO":"PRECISA_ACAO","A tentativa e seu snapshot foram preservados. Esta consulta não confirma nem repete envio.",List.of(),List.of(new Acao("CONSULTAR_ENVIO","Consultar tentativa de envio",r.get("VIAGEM_ID").toString()),new Acao("COPIAR_SUPORTE","Copiar informações para suporte",id)),resolucao,List.of());
    }
    private DiagnosticoOperacao notificacao(String id){
        var r=linha("notificacoes_recebimento",id);String situacao=(String)r.get("SITUACAO");boolean ok=Set.of("SIMULADO","ACEITO_SMTP").contains(situacao),falha="FALHA".equals(situacao);
        var sucessoras=jdbc.queryForList("SELECT id,concluida_em FROM notificacoes_recebimento WHERE anterior_id=? AND situacao IN ('SIMULADO','ACEITO_SMTP') ORDER BY criada_em DESC LIMIT 1",id);
        Resolucao resolucao=ok?new Resolucao(id,(OffsetDateTime)r.get("CONCLUIDA_EM"),"Resultado conhecido do transporte; não comprova entrega ou leitura."):falha&&!sucessoras.isEmpty()?new Resolucao((String)sucessoras.getFirst().get("ID"),(OffsetDateTime)sucessoras.getFirst().get("CONCLUIDA_EM"),"Reenvio explicitamente vinculado com resultado conhecido; falha original preservada."):null;
        String mensagem=ok?(situacao.equals("SIMULADO")?"Confirmação SIMULADA; nenhum e-mail real enviado.":"Confirmação aceita pelo servidor SMTP; entrega e leitura não comprovadas."):falha?"Falha na confirmação ao motorista. Consulte a tentativa.":"Resultado da confirmação desconhecido. Consulte a tentativa antes de reenviar.";
        return new DiagnosticoOperacao(id,(String)r.get("CORRELACAO"),(OffsetDateTime)r.get("CRIADA_EM"),VERSAO,"NOTIFICACAO",null,null,"NOTIFICACAO",falha?"NOTIFICACAO_FALHA":ok?"NOTIFICACAO_RESULTADO_CONHECIDO":"NOTIFICACAO_RESULTADO_NAO_CONFIRMADO",resolucao!=null?"SUCESSO":falha?"ERRO":"ATENCAO",mensagem,CAUSA_DESCONHECIDA,"Consulte o recebimento. Reenvio exige nova conferência, confirmação explícita e vínculo com a tentativa anterior.",
                situacao,resolucao!=null?"RESOLVIDO":"PRECISA_ACAO","Importações e exportações são independentes desta confirmação e permanecem preservadas. Nenhum disparo é feito por esta consulta.",List.of(),List.of(new Acao("CONSULTAR_AVISOS","Consultar avisos do recebimento",(String)r.get("RECEBIMENTO_ID")),new Acao("COPIAR_SUPORTE","Copiar informações para suporte",id)),resolucao,List.of());
    }
}
