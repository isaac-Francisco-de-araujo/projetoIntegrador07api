import { api, $ } from './painel-base.js';
import { urlAplicacao } from './painel-rotas.js';
export async function verificarVersao(){
  const versao=await api('/api/versao');
  const falha=()=>Object.assign(new Error('Frontend e backend incompatíveis ou JAR antigo. Inicie o JAR corrigido na URL própria e recarregue o painel. Nenhum envio foi iniciado.'),{codigo:'VERSOES_INCOMPATIVEIS',incerto:false});
  if(versao.contrato!=='equipe-V15-20261109'||versao.centralEnviosContrato!=='documental-conferencia-v2')throw falha();
  // Revalidação sem cache na mesma origem, incluindo o módulo que decide Gerar prévia.
  for(const nome of ['painel.js','painel-base.js','painel-documentos.js','painel-email.js','painel-versao.js','painel-configuracoes.js','painel-conta-teste.js']){
    const resposta=await fetch(urlAplicacao('/'+nome),{credentials:'same-origin',cache:'no-store',signal:AbortSignal.timeout(15000)});
    if(!resposta.ok)throw falha();
    const bytes=await resposta.arrayBuffer();
    const hash=[...new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))].map(b=>b.toString(16).padStart(2,'0')).join('');
    if(hash!==versao.estaticosSha256?.[nome])throw falha();
  }
  $('versao-envios').textContent=`Central documental v2 · compilação ${versao.compiladoEm} · recursos conferidos com o backend desta URL`;
  return versao;
}
