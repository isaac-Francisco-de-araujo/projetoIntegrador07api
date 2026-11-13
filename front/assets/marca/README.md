# Assets oficiais de apresentação

Originais preservados em `back/src/test/imagem/`:

- `Logo R Futurista com Estrada Neon.png`: logo completa, sem corte, proporção
  preservada e transparência mantida; versão de uso em `logo-rotabrasil.webp`.
- `Gestão de Viagens em Neon Verde.png`: recorte direito x=824, y=0,
  848×941 px, contendo caminhão/mapa, sem chamada ou botão desenhados;
  versão de uso em `landing-rota.webp`.

Geração reproduzível: `node scripts/preparar-assets-marca.cjs`, com o Playwright
de testes disponível. Canvas local no navegador; nenhum serviço externo.
O Maven inclui somente os assets de imagem, não este documento.

Favicons derivados da mesma logo: `favicon-16.png` (16×16), `favicon-32.png`
(32×32) e `../../favicon.ico` (DIB 32 bits, 16/32 px). Recorte apenas das margens
transparentes/halo tênue, proporção preservada e base grafite para os dois temas.
Geração local: `node scripts/preparar-favicons.cjs`. As páginas usam a versão
`rb-20261113-1` para atualização de cache.
