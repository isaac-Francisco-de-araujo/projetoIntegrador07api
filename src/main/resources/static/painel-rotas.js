// Derivado do próprio módulo, também quando a aplicação usa um context path.
const contexto = new URL('.', import.meta.url).pathname.replace(/\/$/, '');
export const urlAplicacao = caminho => contexto + caminho;
