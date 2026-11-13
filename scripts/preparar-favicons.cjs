/* Favicon oficial em PNG e ICO/DIB 32 bits, sem modificar a logo ou instalar bibliotecas. */
'use strict';
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),destino=path.join(root,'front/assets/marca');
function ico(imagens){
 const cabecalho=Buffer.alloc(6+16*imagens.length);cabecalho.writeUInt16LE(1,2);cabecalho.writeUInt16LE(imagens.length,4);
 let offset=cabecalho.length;const partes=[];
 imagens.forEach(({size,pixels},i)=>{
  const stride=Math.ceil(size/32)*4,bitmap=Buffer.alloc(40+size*size*4+stride*size);
  bitmap.writeUInt32LE(40,0);bitmap.writeInt32LE(size,4);bitmap.writeInt32LE(size*2,8);bitmap.writeUInt16LE(1,12);bitmap.writeUInt16LE(32,14);bitmap.writeUInt32LE(size*size*4,20);
  for(let y=0;y<size;y++)for(let x=0;x<size;x++){const src=(y*size+x)*4,pos=40+((size-1-y)*size+x)*4;bitmap[pos]=pixels[src+2];bitmap[pos+1]=pixels[src+1];bitmap[pos+2]=pixels[src];bitmap[pos+3]=pixels[src+3];if(pixels[src+3]===0)bitmap[40+size*size*4+(size-1-y)*stride+(x>>3)]|=128>>(x%8);}
  const p=6+16*i;cabecalho[p]=size;cabecalho[p+1]=size;cabecalho.writeUInt16LE(1,p+4);cabecalho.writeUInt16LE(32,p+6);cabecalho.writeUInt32LE(bitmap.length,p+8);cabecalho.writeUInt32LE(offset,p+12);offset+=bitmap.length;partes.push(bitmap);
 });return Buffer.concat([cabecalho,...partes]);
}
(async()=>{const browser=await chromium.launch({channel:'chrome',headless:true});try{
 const p=await browser.newPage();const fonte='data:image/png;base64,'+fs.readFileSync(path.join(root,'back/src/test/imagem/Logo R Futurista com Estrada Neon.png')).toString('base64');
 const resultado=await p.evaluate(async src=>{
  const i=new Image();i.src=src;await i.decode();const original=document.createElement('canvas');original.width=i.width;original.height=i.height;const g=original.getContext('2d');g.drawImage(i,0,0);const px=g.getImageData(0,0,i.width,i.height).data;let x0=i.width,y0=i.height,x1=0,y1=0;
  for(let y=0;y<i.height;y++)for(let x=0;x<i.width;x++){const k=(y*i.width+x)*4;if(px[k+3]>=160&&Math.max(px[k],px[k+1],px[k+2])>=80){x0=Math.min(x0,x);y0=Math.min(y0,y);x1=Math.max(x1,x);y1=Math.max(y1,y);}}
  // Retirar apenas margem transparente/halo tênue; manter o símbolo inteiro e sua proporção.
  const w=x1-x0+1,h=y1-y0+1;const imagens=[16,32].map(size=>{const c=document.createElement('canvas');c.width=c.height=size;const ctx=c.getContext('2d');ctx.fillStyle='#0b1410';ctx.beginPath();ctx.roundRect(0,0,size,size,size*.2);ctx.fill();ctx.imageSmoothingQuality='high';const margem=size===16?.75:1.5,escala=Math.min((size-2*margem)/w,(size-2*margem)/h);ctx.drawImage(i,x0,y0,w,h,(size-w*escala)/2,(size-h*escala)/2,w*escala,h*escala);return {size,png:c.toDataURL('image/png').split(',')[1],pixels:[...ctx.getImageData(0,0,size,size).data]};});return {recorte:{x:x0,y:y0,w,h},imagens};
 },fonte);
 for(const im of resultado.imagens)fs.writeFileSync(path.join(destino,`favicon-${im.size}.png`),Buffer.from(im.png,'base64'));
 fs.writeFileSync(path.join(root,'front/favicon.ico'),ico(resultado.imagens));
 console.log(JSON.stringify({recorte:resultado.recorte,arquivos:['front/favicon.ico','front/assets/marca/favicon-16.png','front/assets/marca/favicon-32.png']},null,2));
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
