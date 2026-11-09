package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.OperacaoDtos.*;
import br.com.rotabrasil.exception.*;
import br.com.rotabrasil.service.email.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ConfiguracaoOperacaoService {
    private final JdbcTemplate jdbc;private final CoordenacaoImportacaoService coordenacao;
    private final DestinoExportacaoService destinos;private final ObjectMapper json;
    private final String modo,remetenteSmtp;private final ConfiguracaoNotificacoes canal;private final ContaTesteEmailService contaTeste;
    public ConfiguracaoOperacaoService(JdbcTemplate jdbc,CoordenacaoImportacaoService coordenacao,DestinoExportacaoService destinos,ObjectMapper json,
            @Value("${rotabrasil.email.modo:SIMULADO}") String modo,@Value("${rotabrasil.email.smtp.remetente:}") String remetenteSmtp,ConfiguracaoNotificacoes canal,ContaTesteEmailService contaTeste){
        this.jdbc=jdbc;this.coordenacao=coordenacao;this.destinos=destinos;this.json=json;this.modo=modo;this.remetenteSmtp=remetenteSmtp;this.canal=canal;this.contaTeste=contaTeste;
    }
    public Configuracao consultar(){
        return jdbc.queryForObject("SELECT * FROM configuracao_operacao WHERE id=1",(rs,n)->{
            String email=rs.getString("remetente");
            String situacao=email==null?"REMETENTE_NAO_INFORMADO":modo.equals("REAL")&&!email.equalsIgnoreCase(remetenteSmtp)?"REMETENTE_DIVERGE_SMTP":"ENDERECO_SALVO_SMTP_EXTERNO";
            return new Configuracao(rs.getLong("versao"),email,rs.getBoolean("exportacao_automatica"),rs.getString("subpasta"),
                    "REMETENTE_CONFIRMACAO_AO_REMETENTE_ORIGINAL",modo,canal.modo(),situacao,destinos.autorizada(),
                    "Computador onde o backend RotaBrasil está executando; não é necessariamente o computador deste navegador.",destinos.listar(),rs.getObject("atualizada_em",OffsetDateTime.class),canal.consultar(email));
        });
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Configuracao salvar(Salvar pedido){
        coordenacao.bloquear();destinos.validarNome(pedido.subpasta());
        String email=pedido.remetente()==null||pedido.remetente().isBlank()?null:pedido.remetente().trim();
        if(email!=null)validarEmail(email);
        if(pedido.exportacaoAutomatica()&&!destinos.autorizada())throw new ArquivoInvalidoException("Defina a raiz autorizada externamente antes de ativar a cópia automática");
        String anterior=jdbc.queryForObject("SELECT subpasta FROM configuracao_operacao WHERE id=1",String.class);
        if(!pedido.subpasta().isEmpty()&&!pedido.subpasta().equals(anterior)&&!destinos.listar().contains(pedido.subpasta()))throw new ArquivoInvalidoException("Selecione uma subpasta existente na raiz autorizada");
        int atualizadas=jdbc.update("UPDATE configuracao_operacao SET versao=versao+1,remetente=?,exportacao_automatica=?,subpasta=?,atualizada_em=CURRENT_TIMESTAMP WHERE id=1 AND versao=?",
                email,pedido.exportacaoAutomatica(),pedido.subpasta(),pedido.versao());
        if(atualizadas!=1)throw new ConflitoRecursoException("Configuração alterada por outra operação; recarregue antes de salvar");
        return consultar();
    }
    public String snapshot(){return escrever(consultar());}
    public static void validarEmail(String email){
        try{ValidacaoDocumental.validarEnderecoUnico(email);}
        catch(jakarta.mail.MessagingException ex){throw new ArquivoInvalidoException("Informe um único endereço de e-mail válido, sem nome de exibição ou controles");}
    }
    public String escrever(Object valor){try{return json.writeValueAsString(valor);}catch(Exception ex){throw new IllegalStateException("Falha ao registrar configuração");}}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Teste testarPasta(){
        coordenacao.bloquear();var c=consultar();String erro=destinos.testar(c.subpasta());
        return registrarTeste(UUID.randomUUID().toString(),"PASTA",escrever(c),erro==null?"GRAVAVEL":erro,
                erro==null?"Destino no backend acessível e gravável; publicação sem sobrescrita disponível.":"Teste não concluído: "+erro+". Confira a pasta e as permissões no computador do backend.");
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Teste testarEmail(TestarEmail pedido){
        coordenacao.bloquear();String id=pedido.id().toString();
        var anterior=jdbc.query("SELECT * FROM testes_configuracao WHERE id=?",this::teste,id);
        if(!anterior.isEmpty())return anterior.getFirst();
        var c=consultar();String remetente=contaTeste.gmail()?contaTeste.consultar().remetente():c.remetente();if(remetente==null)throw new ArquivoInvalidoException("Salve o remetente da conta antes do teste sem envio");
        validarEmail(pedido.destinatario());
        var mensagem=new MensagemNotificacao(remetente,pedido.destinatario(),"Teste sem envio RotaBrasil","Teste de configuração; nenhum arquivo recebido ou importado por este teste. Referência: "+id);
        new TransporteEmailSimulado().enviarNotificacao(mensagem);
        return registrarTeste(id,"EMAIL",escrever(Map.of("configuracao",c,"contaTeste",contaTeste.consultar(),"destinatario",pedido.destinatario())),"SIMULADO","Teste sem envio registrado. Nenhuma conexão SMTP; autenticação e entrega não verificadas.");
    }
    private Teste registrarTeste(String id,String tipo,String snapshot,String situacao,String mensagem){
        jdbc.update("INSERT INTO testes_configuracao VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",id,tipo,snapshot,situacao,mensagem);
        return jdbc.queryForObject("SELECT * FROM testes_configuracao WHERE id=?",this::teste,id);
    }
    private Teste teste(java.sql.ResultSet rs,int n)throws java.sql.SQLException{return new Teste(rs.getString("id"),rs.getString("tipo"),rs.getString("situacao"),rs.getString("mensagem"),rs.getObject("criado_em",OffsetDateTime.class));}
    public List<Teste> testes(){return jdbc.query("SELECT * FROM testes_configuracao ORDER BY criado_em DESC,id DESC LIMIT 20",this::teste);}
    public Teste buscarTeste(String id){return jdbc.query("SELECT * FROM testes_configuracao WHERE id=?",this::teste,id).stream().findFirst().orElseThrow(()->new RecursoNaoEncontradoException("Teste não encontrado"));}
}
