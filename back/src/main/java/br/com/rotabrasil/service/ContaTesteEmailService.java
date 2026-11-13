package br.com.rotabrasil.service;

import br.com.rotabrasil.config.ConfiguracaoSmtp;
import br.com.rotabrasil.exception.*;
import br.com.rotabrasil.service.email.*;
import jakarta.validation.constraints.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Conta administrativa de teste; não altera o transporte documental ou o canal Microsoft. */
@Service
public class ContaTesteEmailService {
    public record Salvar(@Min(0) long versao,@NotNull String provedor,@Email @Size(max=255) String remetente,@NotNull String autenticacao) {}
    public record Estado(long versao,String provedor,String remetente,String autenticacao,String modo,
            String estadoAutenticacao,boolean prontoParaTesteReal,List<String> orientacoes,
            String autorizacao,String renovacao,java.time.Instant credencialExpiraEm) {}
    private final JdbcTemplate jdbc;
    private final Environment env;
    private final ConfiguracaoNotificacoes microsoft;
    private final CoordenacaoImportacaoService coordenacao;
    public ContaTesteEmailService(JdbcTemplate jdbc,Environment env,ConfiguracaoNotificacoes microsoft,CoordenacaoImportacaoService coordenacao){this.jdbc=jdbc;this.env=env;this.microsoft=microsoft;this.coordenacao=coordenacao;}
    private String p(String nome,String padrao){return env.getProperty(nome,padrao);}
    private boolean ativo(String nome){return Boolean.parseBoolean(p(nome,"false"));}
    public boolean gmail(){return "GMAIL".equals(jdbc.queryForObject("SELECT provedor FROM conta_teste_email WHERE id=1",String.class));}
    private String senha(){return p("rotabrasil.teste-email.gmail.senha-aplicativo",p("ROTABRASIL_GMAIL_APP_PASSWORD",""));}
    private String usuario(){return p("rotabrasil.teste-email.gmail.usuario",p("ROTABRASIL_GMAIL_REMETENTE",""));}
    private String token(){return p("rotabrasil.teste-email.gmail.token-arquivo","");}
    public Estado consultar(){
        return jdbc.queryForObject("SELECT * FROM conta_teste_email WHERE id=1",(rs,n)->{
            String provedor=rs.getString("provedor"),email=rs.getString("remetente"),auth=rs.getString("autenticacao");
            if(!provedor.equals("GMAIL")){
                email=jdbc.queryForObject("SELECT remetente FROM configuracao_operacao WHERE id=1",String.class);
                var c=microsoft.consultar(email);
                return new Estado(rs.getLong("versao"),provedor,email,c.autenticacao(),c.modo(),"NAO_VERIFICADA",c.prontoParaTesteReal(),c.preRequisitos(),c.autorizacao(),c.renovacao(),c.credencialExpiraEm());
            }
            var faltas=new ArrayList<String>();String modo=p("rotabrasil.teste-email.modo","SIMULADO");
            if(!Set.of("SIMULADO","REAL").contains(modo))throw new IllegalStateException("Modo de teste de e-mail inválido");
            if(email==null||email.isBlank())faltas.add("Salve a conta Gmail autorizada para o teste");
            if(email==null||!email.equalsIgnoreCase(usuario()))faltas.add("A conta salva deve corresponder ao usuário Gmail fornecido externamente");
            var oauth=auth.equals("XOAUTH2")?TokenSmtpExterno.estado(token()):null;
            boolean credencial=oauth!=null?oauth.situacao().equals("DISPONIVEL"):senha().matches("[A-Za-z0-9]{16}");
            if(!credencial)faltas.add(oauth==null?"Forneça externamente uma senha de aplicativo permitida pela conta; nunca a senha normal":switch(oauth.situacao()){
                case "EXPIRADA" -> "Token Google expirado ou próximo da expiração; execute a renovação externa";
                case "RECONEXAO_NECESSARIA" -> "Reconecte a conta Google pelo utilitário externo autorizado";
                case "AUTORIZACAO_PENDENTE","AUSENTE" -> "Autorização Google pendente; forneça o token pelo utilitário externo";
                case "INDISPONIVEL" -> "Renovação Google indisponível; verifique o utilitário externo";
                default -> "Token Google inválido; confira o contrato externo sem expor seu conteúdo";
            });
            if(p("rotabrasil.teste-email.gmail.credencial-versao","").isBlank())faltas.add("Informe externamente a versão não secreta da credencial; altere-a ao substituir credencial ou autorização");
            if(!modo.equals("REAL"))faltas.add("Modo de testes sem envio; REAL depende de ativação externa");
            if(!ativo("rotabrasil.teste-email.gmail.ativado"))faltas.add("Ativação explícita do teste Gmail ausente");
            if(!ativo("rotabrasil.seguranca.ativa"))faltas.add("Teste externo exige ambiente operacional autenticado");
            String banco=p("spring.datasource.url","");
            if(banco.startsWith("jdbc:h2:file:")&&!java.util.regex.Pattern.compile("(?i)(^|;)WRITE_DELAY\\s*=\\s*0(;|$)").matcher(banco).find())faltas.add("Banco H2 em arquivo exige WRITE_DELAY=0");
            return new Estado(rs.getLong("versao"),provedor,email,auth,modo,credencial?"CREDENCIAL_EXTERNA_NAO_VERIFICADA":"NAO_CONFIGURADA",faltas.isEmpty()&&admin(),List.copyOf(faltas),oauth==null?"NAO_VERIFICADA":oauth.autorizacao(),oauth==null?"NAO_APLICAVEL":oauth.renovacao(),oauth==null?null:oauth.expiraEm());
        });
    }
    private boolean admin(){var a=SecurityContextHolder.getContext().getAuthentication();return a!=null&&a.isAuthenticated()&&a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_ADMIN"));}
    @Transactional
    public Estado salvar(Salvar pedido){
        coordenacao.bloquear();
        if(!Set.of("MICROSOFT_EXISTENTE","GMAIL").contains(pedido.provedor())||!Set.of("APP_PASSWORD","XOAUTH2").contains(pedido.autenticacao()))throw new ArquivoInvalidoException("Selecione provedor e autenticação disponíveis");
        String email=pedido.remetente()==null||pedido.remetente().isBlank()?null:pedido.remetente().trim();
        if(email!=null)ConfiguracaoOperacaoService.validarEmail(email);
        if(pedido.provedor().equals("GMAIL")&&email==null)throw new ArquivoInvalidoException("Informe a conta Gmail autorizada; salvar não autentica a conta");
        if(jdbc.update("UPDATE conta_teste_email SET versao=versao+1,provedor=?,remetente=?,autenticacao=?,atualizada_em=CURRENT_TIMESTAMP WHERE id=1 AND versao=?",pedido.provedor(),email,pedido.autenticacao(),pedido.versao())!=1)throw new ConflitoRecursoException("Conta de teste alterada por outro administrador; recarregue e revise o rascunho");
        return consultar();
    }
    public String assinatura(){var c=consultar();return c.provedor().equals("GMAIL")?ConteudoXml.sha256(String.join("|",String.valueOf(c.versao()),c.provedor(),Objects.toString(c.remetente(),""),c.autenticacao(),c.modo(),usuario(),p("rotabrasil.teste-email.gmail.credencial-versao",""),token(),p("rotabrasil.teste-email.gmail.ativado","false"),p("rotabrasil.seguranca.ativa","false")).getBytes(StandardCharsets.UTF_8)):microsoft.assinatura();}
    public Map<String,String> ultimaVerificacao(){
        var c=consultar();String assinatura=assinatura();var json=new com.fasterxml.jackson.databind.ObjectMapper();
        for(var r:jdbc.query("SELECT * FROM testes_configuracao WHERE tipo IN ('EMAIL','EMAIL_REAL') ORDER BY criado_em DESC,id DESC LIMIT 20",(rs,n)->Map.of("snapshot",rs.getString("configuracao"),"situacao",rs.getString("situacao"),"mensagem",rs.getString("mensagem"),"data",rs.getObject("criado_em",java.time.OffsetDateTime.class).toString()))){
            try{var s=json.readTree(r.get("snapshot"));
                boolean mesma=s.path("assinaturaTransporte").asText().equals(assinatura);
                if(mesma)return Map.of("situacao",r.get("situacao"),"mensagem",r.get("mensagem"),"data",r.get("data"));
            }catch(com.fasterxml.jackson.core.JsonProcessingException ignorado){/* Registro antigo não comprova o teste desta conta. */}
        }
        return Map.of("situacao","NAO_VERIFICADO","mensagem","Nenhuma verificação recente desta configuração. Consulte o histórico para resultados anteriores.");
    }
    public void validar(){var c=consultar();if(!c.prontoParaTesteReal())throw new ConflitoRecursoException("Envio não configurado para teste Gmail: "+String.join("; ",c.orientacoes()));}
    public TransporteEmail.Resultado enviar(MensagemNotificacao mensagem){
        if(!gmail())throw new ConflitoRecursoException("Provedor de teste mudou; confira novamente");
        validar();var c=consultar();if(!Objects.equals(c.remetente(),mensagem.remetente()))throw new ConflitoRecursoException("Conta de teste mudou; confira novamente");
        var sender=ConfiguracaoSmtp.criarSender(true,true,"smtp.gmail.com",587,usuario(),senha(),c.remetente(),p("spring.datasource.url",""),10000,c.autenticacao().equals("XOAUTH2")?"XOAUTH2":"BASIC",token());
        return new TransporteSmtp(sender,c.remetente()).enviarNotificacao(mensagem);
    }
}
