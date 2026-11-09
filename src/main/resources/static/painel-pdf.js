import { $, element, api } from './painel-base.js';
import { urlAplicacao } from './painel-rotas.js';

const MAX_PDF = 20 * 1024 * 1024;
let libraries;
async function dependencias() {
  if (!libraries) libraries = Promise.all([
    import(urlAplicacao('/webjars/pdfjs-dist/6.4.299/build/pdf.mjs')),
    import(urlAplicacao('/webjars/pdf-lib/1.17.1/dist/pdf-lib.esm.min.js'))
  ]).then(([viewer, writer]) => {
    viewer.GlobalWorkerOptions.workerSrc = urlAplicacao('/webjars/pdfjs-dist/6.4.299/build/pdf.worker.mjs');
    return { viewer, writer };
  }).catch(error => { libraries = null; throw error; });
  return libraries;
}

export async function sha256(bytes) {
  return [...new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))].map(b => b.toString(16).padStart(2, '0')).join('');
}

export function baixar(bytes, nome, tipo) {
  const url = URL.createObjectURL(new Blob([bytes], { type: tipo }));
  const link = element('a'); link.href = url; link.download = nome; document.body.append(link); link.click(); link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}

export function criarEditorPdf({ aoSalvar }) {
  const dialog = $('editor-pdf');
  let context, original, documentPdf, loadingTask, renderTask, deps, generation = 0;
  let pageNumber = 1, pageView, scale = 1, textItems = [], selection = null, edits = [], busy = false;
  let request;

  function status(text, error = false) {
    $('pdf-status').textContent = text; $('pdf-status').classList.toggle('error', error);
  }
  function controles() {
    $('pdf-anterior').disabled = busy || !documentPdf || pageNumber <= 1;
    $('pdf-proxima').disabled = busy || !documentPdf || pageNumber >= documentPdf.numPages;
    $('pdf-baixar-original').disabled = busy || !original;
    $('pdf-adicionar').disabled = busy || !selection || !$('pdf-texto').value.trim();
    $('pdf-salvar').disabled = busy || !original || !documentPdf || (!edits.length && !context?.revisao);
    for (const id of ['pdf-trecho','pdf-texto','pdf-fonte','pdf-largura','pdf-cobrir','pdf-zoom']) $(id).disabled = busy || !documentPdf;
    $('pdf-fechar').disabled = busy && Boolean(original);
  }
  function limpar() {
    generation++; request?.abort(); renderTask?.cancel();
    const task = loadingTask; loadingTask = null;
    if (task) task.destroy().catch(() => {});
    documentPdf = null; original = null; context = null; pageView = null; selection = null; edits = []; busy = false;
    const canvas = $('pdf-canvas'); canvas.width = 0; canvas.height = 0;
    $('pdf-stage').hidden = true; $('pdf-marcacoes').replaceChildren();
  }
  function fechar() { if (!busy || !original) dialog.close(); }
  dialog.addEventListener('close', limpar);
  dialog.addEventListener('cancel', event => { if (busy && original) event.preventDefault(); });
  $('pdf-fechar').addEventListener('click', fechar);

  function marcar() {
    const target = $('pdf-marcacoes'); target.replaceChildren();
    if (!pageView) return;
    const boxes = edits.filter(edit => edit.page === pageNumber).map(edit => ({ ...edit, applied: true }));
    if (selection) boxes.push({ ...selection, text: '', applied: false });
    for (const box of boxes) {
      const node = element('div', box.applied ? box.text : '', `pdf-mark ${box.applied ? 'is-applied' : 'is-selected'}`);
      node.style.left = `${box.x * scale}px`; node.style.top = `${box.y * scale}px`;
      node.style.width = `${box.width * scale}px`; node.style.minHeight = `${box.height * scale}px`;
      node.style.fontSize = `${box.fontSize * scale}px`;
      node.style.transformOrigin = '0 0'; node.style.transform = `rotate(${box.angle || 0}rad)`;
      if (box.applied) { node.style.background = box.cover ? '#fff' : 'transparent'; node.style.color = '#111'; }
      target.append(node);
    }
  }
  function listar() {
    const list = $('pdf-lista-correcoes'); list.replaceChildren();
    edits.forEach((edit, index) => {
      const li = element('li'); const copy = element('div');
      copy.append(element('strong', `Página ${edit.page}`), element('span', edit.text));
      const remove = element('button', 'Remover'); remove.type = 'button';
      remove.setAttribute('aria-label', `Remover correção ${index + 1} da página ${edit.page}`);
      remove.addEventListener('click', () => { if (busy) return; edits.splice(index, 1); listar(); marcar(); controles(); });
      li.append(copy, remove); list.append(li);
    });
    if (!edits.length) list.append(element('li', 'Nenhuma correção adicionada.'));
    $('pdf-revisao-resumo').textContent = `${edits.length} correção(ões) nesta cópia · original preservado`;
  }
  function selecionar(item) {
    selection = { ...item };
    $('pdf-texto').value = item.text || '';
    $('pdf-fonte').value = Math.max(6, Math.min(40, Math.round(item.fontSize)));
    $('pdf-largura').value = Math.round(item.width);
    $('pdf-cobrir').checked = Boolean(item.text);
    marcar(); controles();
  }
  async function renderizar() {
    if (!documentPdf) return;
    const token = generation;
    busy = true; selection = null; controles(); renderTask?.cancel();
    status('Renderizando a página…');
    try {
      const page = await documentPdf.getPage(pageNumber);
      if (token !== generation) return;
      pageView = page.getViewport({ scale: 1 });
      const available = Math.max(200, document.querySelector('.pdf-canvas-scroll').clientWidth - 40);
      const zoom = $('pdf-zoom').value;
      scale = zoom === 'fit' ? Math.min(1.5, available / pageView.width, 1600 / pageView.height) : Math.min(Number(zoom), 2400 / pageView.width, 3200 / pageView.height);
      const viewport = page.getViewport({ scale });
      const density = Math.min(window.devicePixelRatio || 1, 1.5, Math.sqrt(16000000 / (viewport.width * viewport.height)));
      const canvas = $('pdf-canvas');
      canvas.width = Math.ceil(viewport.width * density); canvas.height = Math.ceil(viewport.height * density);
      canvas.style.width = `${viewport.width}px`; canvas.style.height = `${viewport.height}px`;
      $('pdf-stage').style.width = `${viewport.width}px`; $('pdf-stage').style.height = `${viewport.height}px`; $('pdf-stage').hidden = false;
      renderTask = page.render({ canvasContext: canvas.getContext('2d'), viewport, transform: [density,0,0,density,0,0] });
      await renderTask.promise;
      const content = await page.getTextContent();
      if (token !== generation) return;
      textItems = content.items.filter(item => typeof item.str === 'string' && item.str.trim()).slice(0, 1000).map(item => {
        const transform = deps.viewer.Util.transform(pageView.transform, item.transform);
        const fontSize = Math.max(6, Math.hypot(transform[2], transform[3]));
        const ascent = content.styles[item.fontName]?.ascent || .8;
        const angle = Math.atan2(transform[1], transform[0]);
        return { x: transform[4] + Math.sin(angle) * fontSize * ascent, y: transform[5] - Math.cos(angle) * fontSize * ascent,
          angle, width: Math.max(15, item.width + 3), height: fontSize * 1.3, fontSize, text: item.str };
      });
      const select = $('pdf-trecho'); select.replaceChildren(new Option('Selecione um trecho ou clique na página', ''));
      textItems.forEach((item, index) => select.add(new Option(item.text.slice(0, 110), String(index))));
      $('pdf-paginacao').textContent = `Página ${pageNumber} de ${documentPdf.numPages}`;
      status(textItems.length ? 'Escolha um trecho para corrigir ou clique na página para inserir texto.' : 'Página sem texto selecionável. Clique na página para posicionar uma correção visual.');
      marcar();
    } catch (error) {
      if (token === generation && error.name !== 'RenderingCancelledException') status('Não foi possível renderizar esta página. Baixe o original para conferir.', true);
    } finally { if (token === generation) { busy = false; controles(); } }
  }
  $('pdf-anterior').addEventListener('click', () => { if (!busy && pageNumber > 1) { pageNumber--; renderizar(); } });
  $('pdf-proxima').addEventListener('click', () => { if (!busy && documentPdf && pageNumber < documentPdf.numPages) { pageNumber++; renderizar(); } });
  $('pdf-trecho').addEventListener('change', event => {
    const item = textItems[Number(event.target.value)];
    if (event.target.value !== '' && item) selecionar(item); else { selection = null; marcar(); controles(); }
  });
  $('pdf-stage').addEventListener('click', event => {
    if (busy || !pageView) return;
    const rect = $('pdf-stage').getBoundingClientRect();
    const x = (event.clientX - rect.left) / scale, y = (event.clientY - rect.top) / scale;
    const found = textItems.find(item => {
      const dx = x - item.x, dy = y - item.y, cos = Math.cos(item.angle), sin = Math.sin(item.angle);
      const u = dx * cos + dy * sin, v = -dx * sin + dy * cos;
      return u >= 0 && u <= item.width && v >= 0 && v <= item.height;
    });
    selecionar(found || { x, y, angle:0, width: Math.min(200, pageView.width - x), height: 18, fontSize: 12, text: '' });
    $('pdf-trecho').value = found ? String(textItems.indexOf(found)) : '';
    $('pdf-texto').focus();
  });
  $('pdf-texto').addEventListener('input', controles);
  $('pdf-zoom').addEventListener('change', () => { if (!busy && documentPdf) renderizar(); });
  let resizeTimer;
  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => { if (dialog.open && documentPdf && !busy && $('pdf-zoom').value === 'fit') renderizar(); }, 150);
  }, { passive:true });
  $('pdf-adicionar').addEventListener('click', () => {
    if (busy || !selection || !pageView) return;
    const text = $('pdf-texto').value.trim(); const fontSize = Number($('pdf-fonte').value), width = Number($('pdf-largura').value);
    if (!text || !Number.isFinite(fontSize) || fontSize < 6 || fontSize > 40 || !Number.isFinite(width) || width < 10 || width > 1200) {
      status('Revise o texto, o tamanho (6 a 40) e a largura; a correção precisa caber na página.', true); return;
    }
    const height = Math.max(selection.height, text.split('\n').length * fontSize * 1.3);
    const angle = selection.angle || 0, cos = Math.cos(angle), sin = Math.sin(angle);
    const outside = [[0,0],[width,0],[0,height],[width,height]].some(([u,v]) => {
      const x = selection.x + u*cos - v*sin, y = selection.y + u*sin + v*cos;
      return x < -.01 || y < -.01 || x > pageView.width + .01 || y > pageView.height + .01;
    });
    if (outside || edits.length >= 100) { status('A correção ultrapassa a página ou o limite de 100 correções.', true); return; }
    edits.push({ ...selection, page: pageNumber, rotation: pageView.rotation, text, fontSize, width, height, cover: $('pdf-cobrir').checked });
    selection = null; $('pdf-texto').value = ''; $('pdf-trecho').value = '';
    listar(); marcar(); controles(); status('Correção adicionada ao rascunho. Aplique as revisões para criar a cópia.');
  });
  $('pdf-baixar-original').addEventListener('click', () => { if (original) baixar(original, context.pdf.nomeArquivo, 'application/pdf'); });
  $('pdf-salvar').addEventListener('click', async () => {
    if (busy || !original || !context?.valido()) return;
    busy = true; controles(); status('Gerando a cópia revisada…');
    try {
      if (!edits.length) { await aoSalvar(context.pdf, null); dialog.close(); return; }
      const { PDFDocument, StandardFonts, rgb, degrees } = deps.writer;
      const copy = await PDFDocument.load(original.slice(), { updateMetadata: false });
      const font = await copy.embedFont(StandardFonts.Helvetica);
      for (const edit of edits) {
        const page = copy.getPage(edit.page - 1);
        const view = (await documentPdf.getPage(edit.page)).getViewport({ scale: 1 });
        for (const line of edit.text.split('\n')) if (font.widthOfTextAtSize(line, edit.fontSize) > edit.width - 3) throw new Error('Um texto não cabe na área escolhida. Diminua o tamanho ou aumente a largura da correção.');
        const angle = edit.angle || 0, cos = Math.cos(angle), sin = Math.sin(angle);
        const rotation = degrees(view.rotation - angle * 180 / Math.PI);
        const [bx, by] = view.convertToPdfPoint(edit.x - sin * edit.height, edit.y + cos * edit.height);
        if (edit.cover) page.drawRectangle({ x: bx, y: by, width: edit.width, height: edit.height, rotate: rotation, color: rgb(1,1,1) });
        edit.text.split('\n').forEach((line, index) => {
          const down = edit.fontSize * .9 + index * edit.fontSize * 1.3;
          const [x, y] = view.convertToPdfPoint(edit.x + cos - sin * down, edit.y + sin + cos * down);
          page.drawText(line, { x, y, font, size: edit.fontSize, rotate: rotation, color: rgb(.06,.06,.06) });
        });
      }
      copy.setProducer('RotaBrasil - revisão visual em cópia'); copy.setModificationDate(new Date());
      const bytes = await copy.save();
      if (bytes.length > MAX_PDF) throw new Error('A cópia ultrapassa o limite de 20 MiB por PDF.');
      if (!context.valido()) throw new Error('O pacote mudou; confira novamente antes de aplicar a revisão.');
      await aoSalvar(context.pdf, { bytes, edits: structuredClone(edits) }); dialog.close();
    } catch (error) {
      status(error.message?.includes('WinAnsi') ? 'O texto contém um caractere não suportado pela fonte. Use caracteres latinos na correção.' : error.message || 'Não foi possível gerar a revisão. O original permanece preservado.', true);
    } finally { busy = false; controles(); }
  });

  return {
    cancelar() { if (dialog.open) dialog.close(); limpar(); },
    async abrir(config) {
      if (document.querySelector('dialog[open]')) return;
      limpar(); context = config; const token = generation;
      edits = structuredClone(config.revisao?.edits || []); pageNumber = 1; busy = true;
      $('pdf-editor-titulo').textContent = config.pdf.caminhoInterno;
      $('pdf-zoom').value = 'fit';
      $('pdf-paginacao').textContent = 'Carregando…'; $('pdf-texto').value = ''; $('pdf-trecho').replaceChildren(new Option('Carregando…', ''));
      listar(); controles(); status('Carregando o PDF do mesmo ZIP conferido…'); dialog.showModal();
      request = new AbortController();
      try {
        const form = new FormData(); form.append('arquivo', config.file);
        const params = new URLSearchParams({ sha256: config.pacote.sha256, arquivoId: config.pdf.id });
        const [bytes, loaded] = await Promise.all([api('/api/pacotes/previa-pdf?' + params, { method:'POST', body:form, resposta:'bytes', timeoutMs:30000, signal:request.signal }), dependencias()]);
        if (token !== generation || !config.valido()) return;
        if (bytes.length !== config.pdf.tamanho || bytes.length > MAX_PDF || await sha256(bytes) !== config.pdf.sha256) throw new Error('O PDF não corresponde à conferência. Selecione o pacote e confira novamente.');
        if (token !== generation || !config.valido()) return;
        original = bytes; deps = loaded;
        loadingTask = deps.viewer.getDocument({ data:bytes.slice(), isEvalSupported:false, enableXfa:false,
          cMapUrl:urlAplicacao('/webjars/pdfjs-dist/6.4.299/cmaps/'), cMapPacked:true,
          standardFontDataUrl:urlAplicacao('/webjars/pdfjs-dist/6.4.299/standard_fonts/'), wasmUrl:urlAplicacao('/webjars/pdfjs-dist/6.4.299/wasm/') });
        documentPdf = await loadingTask.promise;
        if (token !== generation) return;
        await renderizar();
      } catch (error) {
        if (token !== generation) return;
        status(error.status ? error.message : error.name === 'PasswordException' ? 'PDF protegido por senha. Obtenha uma cópia sem senha no sistema de origem.' : 'Não foi possível abrir este PDF. Ele pode estar inválido ou a leitura pode estar indisponível. O cabeçalho reconhecido na conferência não garante que o PDF possa ser renderizado.', true);
      } finally { if (token === generation) { busy = false; controles(); } }
    }
  };
}

export async function gerarZipRevisado(file, pacote, revisions) {
  if (!pacote.conteudoValido || pacote.interrompida) throw new Error('Corrija primeiro os erros do pacote na origem e confira novamente.');
  const original = new Uint8Array(await file.arrayBuffer());
  if (original.length > MAX_PDF || await sha256(original) !== pacote.sha256) throw new Error('O ZIP mudou desde a conferência.');
  const { unzip, zip } = await import(urlAplicacao('/webjars/fflate/0.8.3/esm/browser.js'));
  const expected = new Map(pacote.arquivos.map(item => [item.caminhoInterno, item]));
  let total = 0;
  const entries = await new Promise((resolve, reject) => unzip(original, { filter: item => {
    const metadata = expected.get(item.name);
    if (!metadata) return false;
    total += item.originalSize;
    if (item.originalSize !== metadata.tamanho || item.originalSize > MAX_PDF || total > 100 * 1024 * 1024) throw new Error('Limites ou metadados do ZIP divergentes.');
    return true;
  } }, (error, result) => error ? reject(error) : resolve(result)));
  if (Object.keys(entries).length !== expected.size) throw new Error('Não foi possível recuperar todos os arquivos conferidos.');
  const output = Object.create(null); let outputSize = 0;
  for (const [name, metadata] of expected) {
    if (await sha256(entries[name]) !== metadata.sha256) throw new Error('Um arquivo diverge da conferência; gere o pacote novamente na origem.');
    const bytes = revisions.get(metadata.id)?.bytes || entries[name];
    outputSize += bytes.length;
    if (outputSize > MAX_PDF) throw new Error('O ZIP revisado ultrapassa 20 MiB. Divida o pacote na origem; você pode baixar as cópias revisadas individualmente.');
    output[name] = [bytes, { level:0 }];
  }
  const bytes = await new Promise((resolve, reject) => zip(output, { level:0 }, (error, result) => error ? reject(error) : resolve(result)));
  if (bytes.length > MAX_PDF) throw new Error('O ZIP revisado ultrapassa 20 MiB. Divida o pacote na origem para revisar dentro do limite.');
  return new File([bytes], file.name.replace(/\.zip$/i, '') + '-revisado.zip', { type:'application/zip' });
}
