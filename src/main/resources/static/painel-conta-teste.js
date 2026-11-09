import { $,api,element,podeAdministrar } from './painel-base.js';
let salvo=null,alterado=false,ocupado=false;
function controles(){
  const gmail=$('conta-teste-provedor').value==='GMAIL';
  $('conta-teste-gmail').hidden=!gmail;$('conta-teste-remetente').required=gmail;
  $('campos-conta-teste').disabled=!salvo||ocupado||!podeAdministrar();
  $('salvar-conta-teste').disabled=!alterado||ocupado;
  $('recarregar-conta-teste').disabled=ocupado;
  document.dispatchEvent(new CustomEvent('conta-teste-estado',{detail:{pronto:Boolean(salvo?.prontoParaTesteReal)&&!alterado&&!ocupado,alterado}}));
}
function preencher(c){
  salvo=c;alterado=false;$('conta-teste-provedor').value=c.provedor;$('conta-teste-remetente').value=c.remetente||'';$('conta-teste-autenticacao').value=c.autenticacao==='XOAUTH2'?'XOAUTH2':'APP_PASSWORD';
  $('conta-teste-modo').textContent=`Modo do teste: ${c.modo==='REAL'?'real — exige confirmação':'Teste sem envio'}`;
  $('conta-teste-autorizacao').textContent=c.estadoAutenticacao==='NAO_CONFIGURADA'?'⚠ Autenticação não configurada.':'⚠ Autenticação não verificada. Credencial/configuração externa não comprova acesso ao provedor.';
  $('conta-teste-orientacoes').replaceChildren(...c.orientacoes.map(t=>element('li',t)));
  $('conta-teste-estado').textContent='Conta salva. Nenhum e-mail enviado.';
  atualizarUltimaVerificacao();
}
export async function atualizarUltimaVerificacao(){try{const v=await api('/api/configuracoes/conta-teste/ultima-verificacao');$('conta-teste-ultima').textContent=`Última verificação da conta de teste: ${v.mensagem}${v.data?' · '+new Date(v.data).toLocaleString('pt-BR'):''}`;}catch{$('conta-teste-ultima').textContent='⚠ Última verificação indisponível. Recarregue a conta para consultar.';}}
async function carregar(){ocupado=true;controles();try{preencher(await api('/api/configuracoes/conta-teste'));}catch(e){salvo=null;$('conta-teste-estado').textContent=`⛔ ${e.message} Recarregue a conta para tentar novamente.`;}finally{ocupado=false;controles();}}
export function iniciarContaTeste(){
  if(!podeAdministrar())return;
  for(const id of ['conta-teste-provedor','conta-teste-remetente','conta-teste-autenticacao'])$(id).addEventListener('input',()=>{alterado=true;$('conta-teste-estado').textContent='⚠ Alterações da conta ainda não salvas.';$('resultado-teste-real').replaceChildren();controles();});
  $('form-conta-teste').addEventListener('submit',async event=>{
    event.preventDefault();if(!salvo||ocupado||!alterado)return;ocupado=true;controles();
    try{preencher(await api('/api/configuracoes/conta-teste',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({versao:salvo.versao,provedor:$('conta-teste-provedor').value,remetente:$('conta-teste-remetente').value||null,autenticacao:$('conta-teste-autenticacao').value})}));}
    catch(e){$('conta-teste-estado').textContent=`${e.incerto?'⚠ Resultado não confirmado':'⛔ Conta não salva'}: ${e.message} O rascunho foi preservado; recarregue para conferir.`;}
    finally{ocupado=false;controles();}
  });
  $('recarregar-conta-teste').addEventListener('click',()=>{if(!alterado||confirm('Descartar o rascunho da conta de teste e carregar a versão atual?'))carregar();});
  window.addEventListener('beforeunload',event=>{if(alterado){event.preventDefault();event.returnValue='';}});
  return carregar();
}
