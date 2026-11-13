// Componentes públicos de diagnóstico. Sem dependência do cliente HTTP para evitar ciclos de erro.
const node=(tag,text,classe)=>{const n=document.createElement(tag);if(text!=null)n.textContent=String(text);if(classe)n.className=classe;return n;};
const ultimos=new WeakMap();
export const referenciaLocal=()=>globalThis.crypto?.randomUUID?.()||`interface-${Date.now()}`;
export function diagnosticoLocal(error,{codigo='INTERFACE_INDISPONIVEL',mensagem}={}){
  if(error?.diagnosticoOperacao)return error.diagnosticoOperacao;
  const incerto=error?.incerto===true,consulta=error?.metodo==='GET';
  return {operacaoId:error?.referencia||referenciaLocal(),correlacaoId:error?.correlacaoId||null,dataHora:new Date().toISOString(),versao:'frontend / diagnostico-V10',origem:'INTERFACE',
    pacote:null,etapa:'COMUNICACAO',codigo:error?.codigo||codigo,gravidade:incerto?'ATENCAO':'ERRO',mensagem:mensagem||error?.message||'Não foi possível concluir a consulta.',
    causaConhecida:'Não foi possível identificar a causa. Use a referência da operação para suporte.',orientacao:consulta?'Tente carregar novamente. Se persistir, consulte os detalhes e confira a versão do servidor.':incerto?'Consulte Processamentos e o estado atual antes de repetir uma alteração.':'Revise os dados e as permissões antes de tentar novamente.',
    situacao:consulta?'CONSULTA_INDISPONIVEL':incerto?'RESULTADO_NAO_CONFIRMADO':'OPERACAO_RECUSADA',estado:'PRECISA_ACAO',impacto:consulta?'A consulta não alterou documentos nem configurações.':incerto?'A resposta não confirma o resultado da alteração. Não repita automaticamente.':'A operação foi recusada; não foi apresentado sucesso.',
    problemas:[],acoes:[...(!consulta&&incerto?[{codigo:'ABRIR_CENTRAL',rotulo:'Consultar histórico e pendências'}]:[]),{codigo:'COPIAR_SUPORTE',rotulo:'Copiar informações para suporte'}],recuperacoes:[]};
}
export function informacoesSuporte(d){
  const simples=valor=>valor==null?null:String(valor).replace(/[\u0000-\u001f\u007f]/g,'').slice(0,255);
  return {operacaoId:simples(d.operacaoId),correlacaoId:simples(d.correlacaoId),dataHora:simples(d.dataHora),codigo:simples(d.codigo),etapa:simples(d.etapa),versao:simples(d.versao),
    arquivos:(d.problemas||[]).filter(p=>p.arquivo).map(p=>({id:simples(p.arquivoId),nome:simples(p.arquivo)}))};
}
async function copiar(d,alvo){
  const texto=JSON.stringify(informacoesSuporte(d),null,2);const resultado=node('p');resultado.setAttribute('role','status');alvo.append(resultado);
  try{await navigator.clipboard.writeText(texto);resultado.textContent='Informações de suporte copiadas. A pendência não foi encerrada.';}
  catch{const campo=node('textarea');campo.readOnly=true;campo.value=texto;campo.setAttribute('aria-label','Informações seguras para copiar manualmente');alvo.append(campo);campo.focus();campo.select();resultado.textContent='Cópia automática indisponível. Copie o texto selecionado; a pendência permanece aberta.';}
}
export function cartaoProblema(p){
  const gravidade=p.gravidade==='ERRO'?'erro':p.gravidade==='SUCESSO'?'sucesso':'atencao';
  const li=node('li',null,`diag-problema diag-${gravidade}`);li.dataset.gravidade=p.gravidade;
  li.append(node('strong',`${gravidade==='erro'?'⛔ Erro':gravidade==='sucesso'?'✓ Concluído':'⚠ Atenção'} · ${p.arquivo||'Operação sem arquivo individual'}`),
    node('p',`Localização ${p.tipoLocalizacao==='DESTINO_EXPORTACAO'?'do destino (backend)':p.tipoLocalizacao==='DENTRO_DO_ZIP'?'dentro do ZIP':'lógica'}: ${p.localizacao||'não identificada'}`),
    node('p',`Etapa: ${p.etapa}`),node('p',p.mensagem),node('p',`Causa conhecida: ${p.causaConhecida}`),node('p',`Como resolver: ${p.orientacao}`));
  return li;
}
export function renderizarDiagnostico(alvo,d,{substituir=true,compacto=true}={}){
  if(!alvo)return;delete alvo.dataset.falhaConsulta;const classe=d.gravidade==='ERRO'?'erro':d.gravidade==='SUCESSO'?'sucesso':'atencao';
  const artigo=node('article',null,`diag-card diag-${classe}`);artigo.dataset.operacaoId=d.operacaoId||'';artigo.dataset.codigo=d.codigo;artigo.setAttribute('role','region');artigo.setAttribute('aria-label',`${classe==='erro'?'Erro':classe==='sucesso'?'Concluído':'Atenção'} da operação`);
  artigo.append(node('h3',d.situacao==='SIMULADO'?'Teste sem envio':`${classe==='erro'?'⛔ Erro / Falha':classe==='sucesso'?'✓ Concluído':'⚠ Atenção / Pendente'} · ${d.mensagem}`));
  if(d.situacao==='SIMULADO')artigo.className='diag-card';
  if(compacto){
    artigo.append(node('p',[d.pacote,d.estado==='RESOLVIDO'?'Resolvido':d.estado==='EM_PROCESSAMENTO'?'Em processamento':'Precisa de ação',d.dataHora?new Date(d.dataHora).toLocaleString('pt-BR'):null].filter(Boolean).join(' · ')));
  }
  if(!compacto&&d.problemas?.length){const lista=node('ul',null,'diag-problemas');lista.append(...d.problemas.map(cartaoProblema));artigo.append(lista);}
  const detalhes=node('details');detalhes.append(node('summary','Ver detalhes'));
  if(d.situacao==='SIMULADO')detalhes.append(node('p',d.mensagem));
  if(d.pacote)detalhes.append(node('p',`Pacote: ${d.pacote}${d.pacoteId?' · #'+d.pacoteId:''}`));
  detalhes.append(node('p',`Etapa: ${d.etapa} · Situação: ${d.situacao}`),node('p',d.impacto,'diag-impacto'),node('p',`Orientação: ${d.orientacao}`));
  if(d.resolucao)detalhes.append(node('p',`Correção comprovada: ${d.resolucao.criterio} · ${d.resolucao.referencia}`,'diag-resolucao'));
  detalhes.append(node('p',`Referência da operação: ${d.operacaoId||'não disponível'}`),node('p',`Referência da requisição: ${d.correlacaoId||'não disponível'}`),node('p',`Data: ${d.dataHora?new Date(d.dataHora).toLocaleString('pt-BR'):'não informada'}`),node('p',`Código: ${d.codigo} · Versão: ${d.versao}`),node('p',`Causa conhecida: ${d.causaConhecida}`));
  if(compacto&&d.problemas?.length){const lista=node('ul',null,'diag-problemas');lista.append(...d.problemas.map(cartaoProblema));detalhes.append(lista);}
  for(const h of d.recuperacoes||[]){
    detalhes.append(node('p',`Recuperação ${h.id} · ${h.situacao} · ${h.concluidos} concluído(s), ${h.pendentes} pendente(s) · Início ${new Date(h.inicio).toLocaleString('pt-BR')}${h.fim?' · Fim '+new Date(h.fim).toLocaleString('pt-BR'):' · Resultado desta tentativa ainda não confirmado'}`));
    if(h.problemas?.length){detalhes.append(node('p','Diagnóstico histórico desta tentativa — não representa o estado atual dos arquivos:'));const lista=node('ul',null,'diag-problemas');lista.append(...h.problemas.map(cartaoProblema));detalhes.append(lista);}
  }
  artigo.append(detalhes);const acoes=node('div',null,'diag-acoes');
  for(const acao of d.acoes||[]){
    if(acao.codigo==='VER_DETALHES')continue;
    if(['RETOMAR_EXPORTACAO','SELECIONAR_ZIP_CORRIGIDO','REVISAR_ASSOCIACOES'].includes(acao.codigo)&&document.documentElement.dataset.operador!=='true')continue;
    if(acao.codigo==='CONFIGURAR_PASTA'&&document.documentElement.dataset.administrador!=='true')continue;
    const button=node('button',acao.rotulo,'button secondary');button.type='button';button.dataset.acaoRecuperacao=acao.codigo;
    button.addEventListener('click',()=>{if(acao.codigo==='COPIAR_SUPORTE')copiar(d,detalhes);else artigo.dispatchEvent(new CustomEvent('recuperar-operacao',{bubbles:true,detail:{acao,diagnostico:d,alvo}}));});(acao.codigo==='COPIAR_SUPORTE'?detalhes:acoes).append(button);
  }
  artigo.append(acoes);if(substituir)alvo.replaceChildren(artigo);else alvo.append(artigo);
  const chave=`${d.operacaoId}:${d.codigo}:${d.estado}`;
  if(ultimos.get(alvo)!==chave){ultimos.set(alvo,chave);const anuncio=document.getElementById('anuncios-diagnostico');if(anuncio&&classe==='erro'&&alvo.getClientRects().length)anuncio.textContent=`Erro: ${d.mensagem} Consulte o diagnóstico e as ações disponíveis.`;}
  return artigo;
}
export function apresentarErro(alvo,error,{tentarNovamente}={}){const card=renderizarDiagnostico(alvo,diagnosticoLocal(error));if(card){const detalhes=card.querySelector('details');if(error?.rota)detalhes.append(node('p',`${error.metodo} ${error.rota}${error.status?' · HTTP '+error.status:''}`));if(error?.detalhes)detalhes.append(node('p',error.detalhes));if(tentarNovamente){const button=node('button','Tentar novamente','button secondary');button.type='button';button.addEventListener('click',tentarNovamente);card.querySelector('.diag-acoes').prepend(button);}}if(alvo&&error?.metodo==='GET')alvo.dataset.falhaConsulta=error.rota;return card;}
document.addEventListener('consulta-confirmada',event=>{for(const alvo of document.querySelectorAll('[data-falha-consulta]'))if(alvo.dataset.falhaConsulta===event.detail.rota){alvo.replaceChildren();delete alvo.dataset.falhaConsulta;}});
