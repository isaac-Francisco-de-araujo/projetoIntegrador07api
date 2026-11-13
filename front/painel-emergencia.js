// Canal clássico independente do grafo de módulos. Uma só apresentação de falha inicial.
(()=>{
  let registrado=false, prazo, essencialPronta=false, pulada=false, encerrada=false;
  const inicio=performance.now(), minimoVisual=10000, limiteInicializacao=45000;
  performance.mark('rotabrasil-abertura-inicio');
  function programar(acao, espera) { clearTimeout(prazo); prazo=setTimeout(acao, Math.max(0,espera)); }
  function liberarQuandoPronta() {
    const abertura=document.getElementById('abertura');
    if(encerrada||!abertura||abertura.classList.contains('has-error')||!essencialPronta)return;
    const restante=minimoVisual-(performance.now()-inicio);
    if(!pulada&&restante>0){
      document.getElementById('abertura-status').textContent='Sistema pronto. Finalizando a apresentação…';
      programar(liberarQuandoPronta,restante);
      return;
    }
    encerrada=true; clearTimeout(prazo);
    performance.mark('rotabrasil-abertura-fim');
    document.dispatchEvent(new Event('abertura-liberada'));
  }
  const dom=acao=>{if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',acao,{once:true});else acao();};
  function falhaInicial({mensagem,semSessao=false}) {
    const abertura=document.getElementById('abertura');
    if(!abertura||abertura.hidden)return false;
    encerrada=true; clearTimeout(prazo);
    abertura.classList.add('has-error');
    document.getElementById('abertura-titulo').textContent='Não foi possível abrir o painel';
    document.getElementById('abertura-status').textContent='A abertura foi interrompida. Confira a orientação abaixo.';
    document.getElementById('abertura-erro-texto').textContent=mensagem;
    document.getElementById('abertura-erro').hidden=false;
    document.getElementById('abertura-entrar').hidden=!semSessao;
    document.getElementById('abertura-pular').hidden=true;
    return true;
  }
  document.addEventListener('inicializacao-falhou',event=>dom(()=>falhaInicial(event.detail)));
  document.addEventListener('workspace-pronto',()=>{encerrada=true;clearTimeout(prazo);});
  document.addEventListener('abertura-essencial-pronta',()=>{
    essencialPronta=true; performance.mark('rotabrasil-abertura-essencial'); liberarQuandoPronta();
  },{once:true});
  document.addEventListener('click',event=>{
    const botao=event.target.closest?.('#abertura-pular');
    if(!botao||encerrada||pulada)return;
    // Manter o controle focável durante a espera real: ao liberar, o controlador
    // transfere esse foco para o painel, sem deixá-lo num botão removido/desabilitado.
    pulada=true; botao.setAttribute('aria-disabled','true'); botao.textContent='Animação pulada';
    document.getElementById('abertura').classList.add('animation-skipped');
    liberarQuandoPronta();
  });
  // Inicia antes de DOMContentLoaded: o download de um módulo pode prender esse evento.
  // Um só agendamento: prazo essencial enquanto inicializa; restante visual depois de pronta.
  programar(()=>falhaInicial({mensagem:'A abertura demorou além do esperado. Confira a conexão e recarregue a página. Nenhuma operação será repetida automaticamente.'}),limiteInicializacao);
  const mostrar=()=>{
    if(registrado)return;registrado=true;
    dom(()=>{try{
      if(falhaInicial({mensagem:'Não foi possível carregar a interface. Recarregue a página para tentar novamente. Nenhuma operação é repetida automaticamente.'}))return;
      const alvo=document.getElementById('diagnostico-global');if(!alvo)return;
      const referencia=globalThis.crypto?.randomUUID?.()||'interface-'+Date.now();
      const artigo=document.createElement('article');artigo.className='diag-card diag-erro';artigo.setAttribute('role','alert');
      const titulo=document.createElement('h3');titulo.textContent='⛔ Erro na interface — o resultado das operações não foi confirmado.';
      const texto=document.createElement('p');texto.textContent='Consulte Processamentos após restabelecer a interface. Não repita importações ou envios por causa deste erro. Referência local: '+referencia;
      const recarregar=document.createElement('button');recarregar.type='button';recarregar.textContent='Recarregar interface';recarregar.addEventListener('click',()=>location.reload());
      artigo.append(titulo,texto,recarregar);alvo.replaceChildren(artigo);
    }catch{}});
  };
  window.addEventListener('error',event=>{if(event instanceof ErrorEvent||event.target instanceof HTMLScriptElement)mostrar();},true);
  window.addEventListener('unhandledrejection',mostrar);
})();
