import { $,api,podeOperar,podeAdministrar } from './painel-base.js';
import { navegarPara } from './painel-navegacao.js';
import { corrigirProcessamento,revisarAssociacoes } from './painel-pacotes.js';
import { renderizarDiagnostico,apresentarErro,referenciaLocal } from './painel-diagnostico.js';
import { mostrarRecebimento,detalhesPlanoExportacao } from './painel-exportacoes.js';
import { mostrarConsultaTentativa } from './painel-email.js';

export function iniciarRecuperacao(){
  document.addEventListener('recuperar-operacao',async event=>{
    const {acao,diagnostico:d,alvo}=event.detail;
    if(acao.codigo==='SELECIONAR_ZIP_CORRIGIDO'){if(podeOperar()){corrigirProcessamento(d.operacaoId);$('arquivo-zip').click();}return;}
    if(acao.codigo==='REVISAR_ASSOCIACOES'){if(podeOperar())revisarAssociacoes(d.operacaoId);return;}
    if(acao.codigo==='CONFIGURAR_PASTA'){if(podeAdministrar())navegarPara('configuracoes',{foco:'config-subpasta'});return;}
    if(acao.codigo==='ABRIR_CENTRAL'){navegarPara('processamentos');document.dispatchEvent(new Event('pendencias-atualizadas'));return;}
    if(acao.codigo==='CONSULTAR_ACERVO'){navegarPara('viagens');return;}
    const atual=()=>alvo.isConnected&&alvo.querySelector(`[data-operacao-id="${d.operacaoId}"]`);
    const botoes=alvo.querySelectorAll('[data-acao-recuperacao="RETOMAR_EXPORTACAO"]');
    try{
      if(acao.codigo==='RETOMAR_EXPORTACAO'&&podeOperar()){
        botoes.forEach(b=>b.disabled=true);const chave='rotabrasil-recuperacao-'+d.operacaoId;
        let requisicaoId;try{requisicaoId=sessionStorage.getItem(chave);}catch{}requisicaoId||=referenciaLocal();try{sessionStorage.setItem(chave,requisicaoId);}catch{}
        const exportacao=await api(`/api/exportacoes/${d.operacaoId}/retomar`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({confirmado:true,requisicaoId}),timeoutMs:120000});
        try{sessionStorage.removeItem(chave);}catch{}
        const novo=await api(`/api/diagnosticos/EXPORTACAO/${d.operacaoId}`);if(atual())renderizarDiagnostico(alvo,novo)?.append(detalhesPlanoExportacao(exportacao));
        renderizarDiagnostico($('resultado-recuperacao-central'),novo)?.append(detalhesPlanoExportacao(exportacao));
        document.dispatchEvent(new Event('pendencias-atualizadas'));return;
      }
      if(acao.codigo==='CONSULTAR_ENVIO'){
        const tentativa=await api(`/api/viagens/${encodeURIComponent(acao.referencia)}/tentativas-email/${d.operacaoId}`);
        const novo=await api(`/api/diagnosticos/ENVIO/${d.operacaoId}`);
        navegarPara('email');mostrarConsultaTentativa(tentativa,novo);$('consulta-tentativa').scrollIntoView({block:'start'});return;
      }
      if(acao.codigo==='CONSULTAR_AVISOS'){navegarPara('pacotes');await mostrarRecebimento(acao.referencia,$('avisos-recebimento'));$('recebimento-opcional').open=true;$('recebimento-opcional').scrollIntoView({block:'start'});}
    }catch(error){if(atual())apresentarErro(alvo,error);}
    finally{botoes.forEach(b=>b.disabled=false);}
  });
}
