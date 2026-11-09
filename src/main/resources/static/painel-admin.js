import { $,element,api,avisar,podeAdministrar } from './painel-base.js';
let operadores=[];
function listar(){
  const busca=$('buscar-operador').value.trim().toLocaleLowerCase('pt-BR'),filtro=$('filtro-operador').value,target=$('lista-usuarios');target.replaceChildren();
  const itens=operadores.filter(u=>u.USUARIO.toLocaleLowerCase('pt-BR').includes(busca)&&(filtro==='TODOS'||Boolean(u.ATIVO)===(filtro==='ATIVO')));
  $('estado-usuarios').textContent=`${itens.length} operador(es)`;
  for(const u of itens){
    const row=element('article',null,'operator-row');row.append(element('strong',u.USUARIO),element('span',u.PAPEL),element('span',u.ATIVO?'✓ Ativo':'⚠ Desativado'));
    const gerenciar=element('button','Gerenciar','button secondary');gerenciar.type='button';gerenciar.addEventListener('click',()=>editar(u));row.append(gerenciar);target.append(row);
  }
}
async function usuarios(){
  operadores=await api('/api/admin/usuarios');listar();
}
function editar(u){
    const dialog=$('editar-operador'),row=$('editar-operador-conteudo');row.replaceChildren();$('editar-operador-titulo').textContent=`Gerenciar ${u.USUARIO}`;
    const erro=element('p');erro.setAttribute('role','alert');
    const papel=element('select');papel.setAttribute('aria-label',`Papel de ${u.USUARIO}`);for(const p of ['CONSULTA','OPERADOR','ADMIN'])papel.add(new Option(p,p));papel.value=u.PAPEL;
    const labelPapel=element('label','Papel');labelPapel.append(papel);
    const ativo=element('input');ativo.type='checkbox';ativo.checked=u.ATIVO;const label=element('label');label.append(ativo,element('span','Ativo'));
    const salvar=element('button','Salvar permissões','button primary');salvar.type='button';salvar.addEventListener('click',async()=>{salvar.disabled=true;try{await api(`/api/admin/usuarios/${u.ID}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({papel:papel.value,ativo:ativo.checked,versao:u.VERSAO})});dialog.close();await usuarios();avisar('Permissões atualizadas; aplicadas às sessões abertas na próxima requisição.');}catch(e){erro.textContent=`⛔ ${e.message} Recarregue a lista para revisar a versão atual.`;}finally{salvar.disabled=false;}});
    const senha=element('input');senha.type='password';senha.minLength=12;senha.maxLength=72;senha.autocomplete='new-password';senha.setAttribute('aria-label',`Nova senha de ${u.USUARIO}`);
    const redefinir=element('button','Redefinir acesso');redefinir.type='button';redefinir.addEventListener('click',async()=>{if(!senha.reportValidity()||!senha.value)return;if(!confirm(`Redefinir o acesso de ${u.USUARIO} e encerrar suas sessões anteriores?`))return;redefinir.disabled=true;try{await api(`/api/admin/usuarios/${u.ID}/senha`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({senha:senha.value,versao:u.VERSAO})});dialog.close();await usuarios();avisar('Acesso redefinido.');}catch(e){erro.textContent=`⛔ ${e.message} Recarregue a lista antes de tentar novamente.`;}finally{senha.value='';redefinir.disabled=false;}});
    const labelSenha=element('label','Nova senha (mínimo 12 caracteres)');labelSenha.append(senha);
    const acesso=element('details');acesso.append(element('summary','Redefinir acesso'),element('p','Encerra as sessões anteriores. Entregue a nova senha por um canal seguro.'),labelSenha,redefinir);
    row.append(labelPapel,label,salvar,acesso,erro);dialog.showModal();
}
export async function iniciarAdministracao(){
  if(!podeAdministrar()){$('administracao').replaceChildren(element('h2','Acesso negado'),element('p','Administração de operadores exige papel ADMIN. Procure o administrador.'));return;}
  $('adicionar-operador').addEventListener('click',()=>$('cadastro-operador').showModal());
  document.querySelectorAll('[data-fechar-operador]').forEach(b=>b.addEventListener('click',()=>b.closest('dialog').close()));
  $('cadastro-operador').addEventListener('close',()=>{$('nova-senha').value='';$('cadastro-operador-erro').textContent='';});
  $('editar-operador').addEventListener('close',()=>$('editar-operador-conteudo').replaceChildren());
  $('buscar-operador').addEventListener('input',listar);$('filtro-operador').addEventListener('change',listar);
  $('recarregar-usuarios').addEventListener('click',()=>usuarios().catch(e=>avisar(e,true)));
  $('form-usuario').addEventListener('submit',async event=>{
    event.preventDefault();const button=event.submitter;button.disabled=true;
    try{await api('/api/admin/usuarios',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({usuario:$('novo-usuario').value,senha:$('nova-senha').value,papel:$('novo-papel').value})});$('form-usuario').reset();$('cadastro-operador').close();await usuarios();avisar('Operador cadastrado.');}
    catch(e){$('cadastro-operador-erro').textContent=`⛔ ${e.message} Revise os dados antes de tentar novamente.`;}finally{$('nova-senha').value='';button.disabled=false;}
  });
  $('atualizar-auditoria').addEventListener('click',async()=>{try{
    const [saude,itens]=await Promise.all([api('/api/admin/saude'),api('/api/admin/auditoria')]);
    $('saude-operacional').textContent=`Banco: ${saude.banco} · Importações desconhecidas: ${saude.importacoesDesconhecidas} · Tentativas desconhecidas: ${saude.tentativasDesconhecidas} · Processamentos ativos: ${saude.capacidade.emUso}/${saude.capacidade.limite} · Heap usado: ${(saude.capacidade.heapUsadoBytes/1048576).toFixed(1)} MiB · Recusas por capacidade: ${saude.capacidade.recusas}`;
    $('lista-auditoria').replaceChildren(...itens.map(a=>element('p',`${new Date(a.CRIADA_EM).toLocaleString('pt-BR')} · ${a.ATOR} · ${a.ACAO} ${a.RECURSO} · HTTP ${a.STATUS_HTTP} · ${a.CORRELACAO}`)));
  }catch(e){avisar(e.message,true);}});
  try{await usuarios();}catch(e){avisar(e.message,true);}
}
