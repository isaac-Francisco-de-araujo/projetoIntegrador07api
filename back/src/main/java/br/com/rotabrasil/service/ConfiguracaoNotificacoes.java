package br.com.rotabrasil.service;

import br.com.rotabrasil.config.ConfiguracaoSmtp;
import br.com.rotabrasil.exception.ConflitoRecursoException;
import br.com.rotabrasil.service.email.*;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Configuração externa do mesmo adaptador SMTP, independente do modo documental. */
@Service
public class ConfiguracaoNotificacoes {
    private final Environment env;
    public ConfiguracaoNotificacoes(Environment env){this.env=env;}
    public record Estado(String modo,String tipoConta,String autenticacao,boolean realDisponivel,String situacao,List<String> preRequisitos,
            boolean autenticacaoConfigurada,String autorizacao,String renovacao,java.time.Instant credencialExpiraEm,
            String permissaoSessao,boolean sessaoPodeEnviarReal,boolean prontoParaTesteReal){}
    private String p(String nome,String padrao){return env.getProperty(nome,padrao);}
    private boolean ativo(String nome){return Boolean.parseBoolean(p(nome,"false"));}
    public String modo(){String m=p("rotabrasil.notificacoes.modo","SIMULADO");if(!Set.of("SIMULADO","REAL").contains(m))throw new IllegalStateException("Modo de notificações inválido");return m;}
    public Estado consultar(String remetente){
        String conta=p("rotabrasil.notificacoes.tipo-conta","NAO_IDENTIFICADA"),auth=p("rotabrasil.email.smtp.autenticacao","BASIC");var faltas=new ArrayList<String>();
        if(!modo().equals("REAL"))faltas.add("Modo REAL de notificações não ativado externamente");
        if(!ativo("rotabrasil.email.smtp.ativado"))faltas.add("Ativação explícita do SMTP ausente");
        if(!ativo("rotabrasil.seguranca.ativa"))faltas.add("Segurança da aplicação precisa estar ativa; o perfil local permanece simulado");
        if(!Set.of("OUTLOOK_PESSOAL","MICROSOFT_365").contains(conta))faltas.add("Identificar conta Outlook pessoal ou Microsoft 365");
        String host=p("rotabrasil.email.smtp.host","");
        String esperado=conta.equals("OUTLOOK_PESSOAL")?"smtp-mail.outlook.com":"smtp.office365.com";
        if(!host.equalsIgnoreCase(esperado))faltas.add("Servidor SMTP externo incompatível com o tipo de conta Microsoft identificado");
        if(!p("rotabrasil.email.smtp.porta","587").equals("587"))faltas.add("SMTP Microsoft exige porta 587 com STARTTLS");
        if(!auth.equals("XOAUTH2"))faltas.add("Configurar OAuth2/XOAUTH2 restrito a envio; não se presume autenticação básica disponível");
        if(p("rotabrasil.email.smtp.usuario","").isBlank())faltas.add("Usuário SMTP externo ausente");
        if(remetente==null||!remetente.equalsIgnoreCase(p("rotabrasil.email.smtp.remetente","")))faltas.add("Remetente salvo precisa corresponder ao remetente SMTP externo");
        var credencial=TokenSmtpExterno.estado(p("rotabrasil.email.smtp.oauth2.token-arquivo",""));
        if(!credencial.situacao().equals("DISPONIVEL"))faltas.add(switch(credencial.situacao()){
            case "EXPIRADA" -> "Credencial OAuth2 expirada; execute a renovação externa. Reconecte a conta se a autorização exigir interação";
            case "RECONEXAO_NECESSARIA" -> "Autorização requer reconexão da conta Microsoft; execute a autorização externa novamente";
            case "AUTORIZACAO_PENDENTE","AUSENTE" -> "Endereço salvo não autentica a conta; autorização OAuth2 externa pendente";
            case "INDISPONIVEL" -> "Renovação da autorização indisponível; verifique o utilitário externo e a conexão";
            default -> "Configuração da credencial OAuth2 externa inválida; confira a autorização sem expor seu conteúdo";
        });
        int timeout=0;try{timeout=Integer.parseInt(p("rotabrasil.email.smtp.timeout-ms","10000"));}catch(NumberFormatException ignorada){}
        if(timeout<1000||timeout>30000)faltas.add("Timeout SMTP deve estar entre 1000 e 30000 ms");
        String banco=p("spring.datasource.url","");if(banco.startsWith("jdbc:h2:file:")&&!java.util.regex.Pattern.compile("(?i)(^|;)WRITE_DELAY\\s*=\\s*0(;|$)").matcher(banco).find())faltas.add("Banco H2 em arquivo exige WRITE_DELAY=0");
        var a=SecurityContextHolder.getContext().getAuthentication();
        boolean autenticado=ativo("rotabrasil.seguranca.ativa")&&a!=null&&a.isAuthenticated()&&!a.getName().equals("anonymousUser");
        boolean admin=autenticado&&a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_ADMIN"));
        boolean operador=autenticado&&a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_OPERADOR"));
        String permissao=!ativo("rotabrasil.seguranca.ativa")?"LOCAL_SIMULADO":!autenticado?"SEM_SESSAO":admin?"ADMIN":operador?"OPERADOR":"CONSULTA";
        return new Estado(modo(),conta,auth,faltas.isEmpty(),faltas.isEmpty()?"PRONTO_PARA_TESTE_REAL_NAO_HOMOLOGADO":"ENVIO_REAL_BLOQUEADO",List.copyOf(faltas),
                auth.equals("XOAUTH2")&&host.equalsIgnoreCase(esperado)&&!p("rotabrasil.email.smtp.usuario","").isBlank(),
                credencial.autorizacao(),credencial.renovacao(),credencial.expiraEm(),permissao,admin||operador,faltas.isEmpty()&&admin);
    }
    public String assinatura(){
        return ConteudoXml.sha256(String.join("|",modo(),p("rotabrasil.notificacoes.tipo-conta",""),p("rotabrasil.email.smtp.host",""),p("rotabrasil.email.smtp.porta","587"),p("rotabrasil.email.smtp.usuario",""),p("rotabrasil.email.smtp.remetente",""),p("rotabrasil.email.smtp.autenticacao","BASIC")).getBytes(StandardCharsets.UTF_8));
    }
    public void validar(String remetente){
        if(!modo().equals("REAL"))return;
        var estado=consultar(remetente);if(!estado.realDisponivel())throw new ConflitoRecursoException("Envio real bloqueado: "+String.join("; ",estado.preRequisitos()));
        var a=SecurityContextHolder.getContext().getAuthentication();
        if(a==null||!a.isAuthenticated()||a.getAuthorities().stream().noneMatch(r->Set.of("ROLE_ADMIN","ROLE_OPERADOR").contains(r.getAuthority())))throw new ConflitoRecursoException("Envio real exige sessão autenticada OPERADOR ou ADMIN; não disponível na sessão local");
    }
    public TransporteEmail.Resultado enviar(MensagemNotificacao mensagem){
        validar(mensagem.remetente());
        if(modo().equals("SIMULADO"))return new TransporteEmailSimulado().enviarNotificacao(mensagem);
        var sender=ConfiguracaoSmtp.criarSender(true,true,p("rotabrasil.email.smtp.host",""),587,p("rotabrasil.email.smtp.usuario",""),"",p("rotabrasil.email.smtp.remetente",""),p("spring.datasource.url",""),Integer.parseInt(p("rotabrasil.email.smtp.timeout-ms","10000")),"XOAUTH2",p("rotabrasil.email.smtp.oauth2.token-arquivo",""));
        return new TransporteSmtp(sender,p("rotabrasil.email.smtp.remetente","")).enviarNotificacao(mensagem);
    }
}
