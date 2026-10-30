import { $,element,api,avisar,podeAdministrar } from './painel-base.js';
async function usuarios(){
  const items=await api('/api/admin/usuarios'),target=$('lista-usuarios');target.replaceChildren();
  for(const u of items){
    const row=element('article');row.append(element('strong',`${u.USUARIO} · ${u.PAPEL} · ${u.ATIVO?'Ativo':'Desativado'}`));
    const papel=element('select');papel.setAttribute('aria-label',`Papel de ${u.USUARIO}`);for(const p of ['CONSULTA','OPERADOR','ADMIN'])papel.add(new Option(p,p));papel.value=u.PAPEL;
    const ativo=element('input');ativo.type='checkbox';ativo.checked=u.ATIVO;const label=element('label');label.append(ativo,element('span','Ativo'));
    const salvar=element('button','Salvar permissões');salvar.type='button';salvar.addEventListener('click',async()=>{salvar.disabled=true;try{await api(`/api/admin/usuarios/${u.ID}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({papel:papel.value,ativo:ativo.checked})});await usuarios();avisar('Permissões atualizadas; aplicadas às sessões abertas na próxima requisição.');}catch(e){avisar(e.message,true);}finally{salvar.disabled=false;}});
    row.append(papel,label,salvar);target.append(row);
  }
}
export async function iniciarAdministracao(){
  if(!podeAdministrar())return;$('administracao').hidden=false;
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
