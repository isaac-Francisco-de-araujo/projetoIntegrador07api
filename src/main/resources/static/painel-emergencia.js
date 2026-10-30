// Canal independente: continua disponível se um módulo do painel não inicializar.
(()=>{
  let registrado=false;
  const mostrar=()=>{
    if(registrado)return;registrado=true;
    const desenhar=()=>{try{
      const alvo=document.getElementById('diagnostico-global');if(!alvo)return;
      const referencia=globalThis.crypto?.randomUUID?.()||'interface-'+Date.now();
      const artigo=document.createElement('article');artigo.className='diag-card diag-erro';artigo.setAttribute('role','alert');
      const titulo=document.createElement('h3');titulo.textContent='⛔ Erro na interface — o resultado das operações não foi confirmado.';
      const texto=document.createElement('p');texto.textContent='Consulte Processamentos após restabelecer a interface. Não repita importações ou envios por causa deste erro. Referência local: '+referencia;
      const recarregar=document.createElement('button');recarregar.type='button';recarregar.textContent='Recarregar interface';recarregar.addEventListener('click',()=>location.reload());
      artigo.append(titulo,texto,recarregar);alvo.replaceChildren(artigo);
    }catch{}};
    if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',desenhar,{once:true});else desenhar();
  };
  window.addEventListener('error',event=>{if(event instanceof ErrorEvent||event.target instanceof HTMLScriptElement)mostrar();},true);window.addEventListener('unhandledrejection',mostrar);
})();
