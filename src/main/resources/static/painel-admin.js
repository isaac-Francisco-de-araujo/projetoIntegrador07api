import { $,element,api,avisar,podeAdministrar } from './painel-base.js';
async function usuarios(){
  const items=await api('/api/admin/usuarios'),target=$('lista-usuarios');target.replaceChildren();
  for(const u of items){
    const row=element('article');row.append(element('strong',`${u.USUARIO} · ${u.PAPEL} · ${u.ATIVO?'Ativo':'Desativado'}`));
    const papel=element('select');papel.setAttribute('aria-label',`Papel de ${u.USUARIO}`);for(const p of ['CONSULTA','OPERADOR','ADMIN'])papel.add(new Option(p,p));papel.value=u.PAPEL;
    const ativo=element('input');ativo.type='checkbox';ativo.checked=u.ATIVO;const label=element('label');label.append(ativo,element('span','Ativo'));
    const salvar=element('button','Salvar permissões');salvar.type='button';salvar.addEventListener('click',async()=>{salvar.disabled=true;try{await api(`/api/admin/usuarios/${u.ID}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({papel:papel.value,ativo:ativo.checked,versao:u.VERSAO})});await usuarios();avisar('Permissões atualizadas; aplicadas às sessões abertas na próxima requisição.');}catch(e){avisar(e,true);}finally{salvar.disabled=false;}});
    const senha=element('input');senha.type='password';senha.minLength=12;senha.maxLength=72;senha.autocomplete='new-password';senha.setAttribute('aria-label',`Nova senha de ${u.USUARIO}`);
    const redefinir=element('button','Redefinir acesso');redefinir.type='button';redefinir.addEventListener('click',async()=>{if(!senha.reportValidity()||!senha.value)return;if(!confirm(`Redefinir o acesso de ${u.USUARIO} e encerrar suas sessões anteriores?`))return;redefinir.disabled=true;try{await api(`/api/admin/usuarios/${u.ID}/senha`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({senha:senha.value,versao:u.VERSAO})});await usuarios();avisar('Acesso redefinido.');}catch(e){avisar(e,true);}finally{senha.value='';redefinir.disabled=false;}});
    const labelSenha=element('label','Nova senha (mínimo 12 caracteres)');labelSenha.append(senha);
    row.append(papel,label,salvar,labelSenha,redefinir);target.append(row);
  }
}
export async function iniciarAdministracao(){
  if(!podeAdministrar()){$('administracao').replaceChildren(element('h2','Acesso negado'),element('p','Administração de operadores exige papel ADMIN. Procure o administrador.'));return;}
  const link=element('a','Operadores');link.href='#administracao';document.querySelector('.sidebar nav').append(link);
  $('recarregar-usuarios').addEventListener('click',()=>usuarios().catch(e=>avisar(e,true)));
  $('form-usuario').addEventListener('submit',async event=>{
    event.preventDefault();const button=event.submitter;button.disabled=true;
    try{await api('/api/admin/usuarios',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({usuario:$('novo-usuario').value,senha:$('nova-senha').value,papel:$('novo-papel').value})});$('form-usuario').reset();await usuarios();avisar('Operador cadastrado.');}
    catch(e){avisar(e.message,true);}finally{$('nova-senha').value='';button.disabled=false;}
  });
  $('atualizar-auditoria').addEventListener('click',async()=>{try{
    const [saude,itens]=await Promise.all([api('/api/admin/saude'),api('/api/admin/auditoria')]);
    $('saude-operacional').textContent=`Banco: ${saude.banco} · Importações desconhecidas: ${saude.importacoesDesconhecidas} · Tentativas desconhecidas: ${saude.tentativasDesconhecidas} · Processamentos ativos: ${saude.capacidade.emUso}/${saude.capacidade.limite} · Heap usado: ${(saude.capacidade.heapUsadoBytes/1048576).toFixed(1)} MiB · Recusas por capacidade: ${saude.capacidade.recusas}`;
    $('lista-auditoria').replaceChildren(...itens.map(a=>element('p',`${new Date(a.CRIADA_EM).toLocaleString('pt-BR')} · ${a.ATOR} · ${a.ACAO} ${a.RECURSO} · HTTP ${a.STATUS_HTTP} · ${a.CORRELACAO}`)));
  }catch(e){avisar(e.message,true);}});
  try{await usuarios();}catch(e){avisar(e.message,true);}
}
