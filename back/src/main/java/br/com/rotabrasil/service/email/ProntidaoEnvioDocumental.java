package br.com.rotabrasil.service.email;

import br.com.rotabrasil.exception.ConflitoRecursoException;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Diagnóstico local, sem conexão SMTP e independente dos canais motorista/teste Gmail. */
@Service
public class ProntidaoEnvioDocumental {
    public record Estado(String modo, boolean confirmacaoRealDisponivel, List<String> bloqueios,
                         String provedor, String autenticacao, boolean autenticacaoVerificada) {}
    private final Environment env;
    private final JdbcTemplate jdbc;
    public ProntidaoEnvioDocumental(Environment env, JdbcTemplate jdbc) { this.env=env; this.jdbc=jdbc; }
    public Estado consultar() {
        String modo=env.getProperty("rotabrasil.email.modo","SIMULADO");
        String salvo=jdbc.queryForObject("SELECT remetente FROM configuracao_operacao WHERE id=1",String.class);
        String smtp=env.getProperty("rotabrasil.email.smtp.remetente","");
        String host=env.getProperty("rotabrasil.email.smtp.host","");
        String autenticacao=env.getProperty("rotabrasil.email.smtp.autenticacao","BASIC");
        var faltas=new ArrayList<String>();
        if (!"REAL".equals(modo)) faltas.add("Transporte real não configurado: modo documental SIMULADO. A preparação não envia e-mail.");
        if (salvo==null||salvo.isBlank()) faltas.add("Configure o remetente da operação antes de confirmar envio real.");
        if (smtp.isBlank()) faltas.add("Remetente SMTP externo não configurado. Salvar endereço no painel não autentica a conta.");
        else if (salvo!=null&&!salvo.isBlank()&&!salvo.equalsIgnoreCase(smtp)) faltas.add("O remetente salvo difere da conta de envio SMTP. Solicite ao administrador que alinhe os endereços.");
        if (host.isBlank()) faltas.add("Host SMTP documental ausente: configure rotabrasil.email.smtp.host externamente (smtp.gmail.com para Gmail).");
        if (env.getProperty("rotabrasil.email.smtp.usuario","").isBlank()) faltas.add("Usuário SMTP documental ausente: configure rotabrasil.email.smtp.usuario com a conta autorizada.");
        if (!Set.of("BASIC","XOAUTH2").contains(autenticacao)) faltas.add("Autenticação SMTP documental não suportada: configure BASIC (senha de aplicativo Gmail) ou XOAUTH2 externamente.");
        else if (autenticacao.equals("BASIC")&&env.getProperty("rotabrasil.email.smtp.senha","").isBlank()) faltas.add("Credencial SMTP documental ausente: rotabrasil.email.smtp.senha deve ser provisionada externamente; para Gmail use somente senha de aplicativo autorizada, nunca a senha normal.");
        if (autenticacao.equals("XOAUTH2")) {
            try { TokenSmtpExterno.ler(env.getProperty("rotabrasil.email.smtp.oauth2.token-arquivo","")); }
            catch (RuntimeException ex) { faltas.add("Autorização SMTP externa indisponível ou expirada. Provisione ou renove rotabrasil.email.smtp.oauth2.token-arquivo no servidor Java."); }
        }
        if ("REAL".equals(modo)) {
            if (!env.getProperty("rotabrasil.email.smtp.ativado",Boolean.class,false)) faltas.add("Transporte SMTP documental sem ativação externa explícita.");
            if (!env.getProperty("rotabrasil.seguranca.ativa",Boolean.class,false)) faltas.add("Envio real exige sessão protegida e CSRF.");
        }
        return new Estado(modo,faltas.isEmpty(),List.copyOf(faltas),host.isBlank()?"NAO_CONFIGURADO":host.equalsIgnoreCase("smtp.gmail.com")?"GMAIL":"SMTP_EXTERNO",Set.of("BASIC","XOAUTH2").contains(autenticacao)?autenticacao:"NAO_SUPORTADA",false);
    }
    public void validarConfirmacaoReal() {
        var estado=consultar();
        if (!estado.confirmacaoRealDisponivel()) throw new ConflitoRecursoException("Confirmação real bloqueada: "+String.join(" ",estado.bloqueios()));
    }
}
