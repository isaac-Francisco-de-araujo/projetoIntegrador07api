import { $, state, api, avisar, podeOperar, tamanhoArquivo } from './painel-base.js';
import { receberPacoteIndividual } from './painel-pacotes.js';
import { sha256 } from './painel-pdf.js';

const LIMITE = 20 * 1024 * 1024;

export function iniciarImportacao({ atualizar }) {
  let busy = false, generation = 0, xmls = [], request;
  const isPdf = () => $('arquivo').files[0]?.name.toLowerCase().endsWith('.pdf');
  function controles() {
    $('importar').disabled = busy || !podeOperar(); $('arquivo').disabled = busy || !podeOperar();
    $('pdf-viagem-base').disabled = busy || !podeOperar();
    $('pdf-xml-base').disabled = busy || !podeOperar() || !xmls.length;
    $('importar').textContent = busy ? isPdf() ? 'Preparando conferência…' : 'Validando e importando…' : isPdf() ? 'Preparar PDF para conferência →' : 'Importar XML →';
    $('form-importacao').setAttribute('aria-busy', String(busy));
    $('remover-arquivo-selecao').disabled=busy||!$('arquivo').files.length;
  }
  function resultado(text, error = false) {
    const target = $('resultado-importacao'); target.hidden = false; target.textContent = `${error?'⛔':'✓'} ${text}`; target.classList.toggle('error',error);
  }
  function viagens() {
    const chosen = $('pdf-viagem-base').value;
    $('pdf-viagem-base').replaceChildren(new Option('Escolha uma viagem existente',''));
    const clientes = new Map(state.clientes.map(cliente => [cliente.id, cliente.nome]));
    for (const viagem of [...state.viagens].sort((a,b) => a.numero.localeCompare(b.numero,'pt-BR',{numeric:true})))
      $('pdf-viagem-base').add(new Option(`CT-e ${viagem.numero} · ${clientes.get(viagem.clienteId) || `Cliente #${viagem.clienteId}`}`,String(viagem.id)));
    $('pdf-viagem-base').value = state.viagens.some(viagem => String(viagem.id) === chosen) ? chosen : '';
    if (!$('pdf-viagem-base').value) { xmls=[]; $('pdf-xml-base').replaceChildren(new Option('Escolha primeiro a viagem','')); }
    controles();
  }
  document.addEventListener('dados-atualizados', viagens);
  $('remover-arquivo-selecao').addEventListener('click',()=>{
    if(busy)return;
    $('arquivo').value='';$('arquivo').dispatchEvent(new Event('change',{bubbles:true}));
    avisar('Arquivo removido da seleção. Nenhum documento já importado foi excluído.');
  });
  $('arquivo').addEventListener('change', () => {
    generation++; request?.abort();
    xmls=[];$('pdf-viagem-base').value='';$('pdf-xml-base').replaceChildren(new Option('Escolha primeiro a viagem',''));
    const file = $('arquivo').files[0];
    $('arquivo-info').textContent = file ? `${file.name} · ${tamanhoArquivo(file.size)}` : 'Nenhum arquivo selecionado';
    $('opcoes-pdf-avulso').hidden = !isPdf();
    $('pdf-avulso-orientacao').textContent = state.viagens.length ? 'Escolha a viagem e seu CT-e original. Depois, confira o PDF no fluxo ZIP e confirme a importação.' : 'Nenhuma viagem disponível. Importe primeiro o XML de CT-e da viagem ou um ZIP com XML e PDF; depois adicione o PDF a essa viagem.';
    for (const id of ['pdf-viagem-base','pdf-xml-base']) $(id).required = Boolean(isPdf());
    $('resultado-importacao').hidden = true; controles();
  });
  $('pdf-viagem-base').addEventListener('change', async () => {
    const id = $('pdf-viagem-base').value, token = ++generation;
    request?.abort(); request = new AbortController(); xmls=[];
    $('pdf-xml-base').replaceChildren(new Option(id ? 'Carregando CT-es…' : 'Escolha primeiro a viagem','')); controles();
    if (!id) return;
    $('pdf-avulso-orientacao').textContent = 'Consultando os CT-es originais desta viagem…';
    try {
      const documents = await api(`/api/viagens/${encodeURIComponent(id)}/documentos`,{signal:request.signal});
      if (token !== generation) return;
      xmls = documents.filter(doc => doc.tipo === 'XML' && /^[0-9a-f]{64}$/.test(doc.sha256 || ''));
      $('pdf-xml-base').replaceChildren(new Option(xmls.length ? 'Escolha o CT-e original' : 'Nenhum XML ativo com integridade disponível',''));
      for (const doc of xmls) $('pdf-xml-base').add(new Option(`${doc.nomeArquivo} · #${doc.id}`,String(doc.id)));
      $('pdf-avulso-orientacao').textContent = xmls.length ? 'O XML escolhido será reutilizado sem alteração. Na conferência ZIP, escolha a finalidade e confirme o vínculo do PDF.' : 'Esta viagem não possui um XML ativo com SHA-256 disponível. Importe o CT-e ou use o ZIP original para fazer a associação.';
    } catch (error) { if (token === generation) { $('pdf-xml-base').replaceChildren(new Option('Consulta indisponível','')); $('pdf-avulso-orientacao').textContent = error.message; } }
    finally { if (token === generation) controles(); }
  });
  $('form-importacao').addEventListener('submit', async event => {
    event.preventDefault(); if (busy || !podeOperar()) return;
    const file = $('arquivo').files[0];
    if (!file || !/\.(xml|pdf)$/i.test(file.name)) { resultado('Escolha um arquivo XML ou PDF.',true); return; }
    if (!file.size || file.size > LIMITE) { resultado('O arquivo deve ter conteúdo e no máximo 20 MiB.',true); return; }
    const pdf = isPdf(), token = generation;
    busy = true; controles(); $('resultado-importacao').hidden = false; $('resultado-importacao').classList.remove('error'); $('resultado-importacao').textContent = pdf ? 'Preparando o PDF e o CT-e original para conferência…' : 'Validando o XML e processando a importação…';
    try {
      if (!pdf) {
        const form = new FormData(); form.append('arquivo',file);
        const response = await api('/api/processamento/xml',{method:'POST',body:form});
        resultado(response.mensagem);
        try { await atualizar(response.viagemId); } catch { resultado('A importação foi concluída, mas a atualização falhou. Use Atualizar dados para conferir.',true); }
      } else {
        const doc = xmls.find(item => String(item.id) === $('pdf-xml-base').value);
        if (!doc || String(doc.viagemId) !== $('pdf-viagem-base').value) throw new Error('Escolha a viagem e um CT-e original disponível para conferir o PDF.');
        if (file.name.length > 200 || /[\\/:\p{Cc}\p{Cf}]/u.test(file.name)) throw new Error('Use um nome de PDF simples, sem caminhos ou caracteres de controle, com até 200 caracteres.');
        const bytes = new Uint8Array(await file.arrayBuffer());
        if (new TextDecoder('ascii').decode(bytes.slice(0,5)) !== '%PDF-') throw new Error('O arquivo não possui o cabeçalho de um PDF. Confira o documento de origem.');
        const original = await api(`/api/documentos/${doc.id}/arquivo`,{resposta:'bytes'});
        if (await sha256(original) !== doc.sha256) throw new Error('O XML original mudou ou está inconsistente. Atualize os dados antes de preparar o PDF.');
        if (bytes.length + original.length > LIMITE) throw new Error('O PDF e o XML juntos ultrapassam o limite de 20 MiB do pacote.');
        const { zip } = await import('/webjars/fflate/0.8.3/esm/browser.js');
        const mtime = new Date(2000,0,1,0,0,0);
        const entries = { [`xml/cte-${doc.id}.xml`]:[original,{level:0,mtime}], [`pdf/${file.name}`]:[bytes,{level:0,mtime}] };
        const packed = await new Promise((resolve,reject) => zip(entries,{level:0,mtime},(error,result) => error ? reject(error) : resolve(result)));
        if (packed.length > LIMITE) throw new Error('O pacote com o PDF ultrapassa o limite de 20 MiB.');
        if (token !== generation) return;
        const hash = await sha256(bytes);
        receberPacoteIndividual(new File([packed],`pdf-${doc.id}-${hash.slice(0,12)}.zip`,{type:'application/zip'}));
        resultado('PDF preparado para conferência no fluxo ZIP. Confira o documento, escolha a finalidade e confirme a importação.');
      }
    } catch (error) { resultado(error.message,true); }
    finally { busy = false; controles(); }
  });
  viagens(); controles();
}
