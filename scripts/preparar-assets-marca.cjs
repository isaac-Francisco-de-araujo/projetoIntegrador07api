/* Conversão local das referências fornecidas. Não modifica os originais. */
'use strict';
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),destino=path.join(root,'front/assets/marca');
const origens={logo:'Logo R Futurista com Estrada Neon.png',arte:'Gestão de Viagens em Neon Verde.png'};
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const page=await browser.newPage();
  const fontes=Object.fromEntries(Object.entries(origens).map(([k,n])=>[k,'data:image/png;base64,'+fs.readFileSync(path.join(root,'back/src/test/imagem',n)).toString('base64')]));
  const imagens=await page.evaluate(async fontes=>{
   async function abrir(src){const i=new Image();i.src=src;await i.decode();return i;}
   async function converter(i,rect,width,quality){const c=document.createElement('canvas');c.width=width;c.height=Math.round(width*rect.h/rect.w);const g=c.getContext('2d');g.imageSmoothingQuality='high';g.drawImage(i,rect.x,rect.y,rect.w,rect.h,0,0,c.width,c.height);const alpha=g.getImageData(0,0,1,1).data[3];return {data:c.toDataURL('image/webp',quality).split(',')[1],largura:c.width,altura:c.height,alphaCanto:alpha};}
   const logo=await abrir(fontes.logo),arte=await abrir(fontes.arte);
   if(arte.width!==1672||arte.height!==941)throw Error('Dimensão da referência mudou; revise o recorte antes de gerar.');
   return {logo:await converter(logo,{x:0,y:0,w:logo.width,h:logo.height},384,.96),arte:await converter(arte,{x:824,y:0,w:848,h:941},848,.88)};
  },fontes);
  for(const [key,nome] of [['logo','logo-rotabrasil.webp'],['arte','landing-rota.webp']]){const bytes=Buffer.from(imagens[key].data,'base64');fs.writeFileSync(path.join(destino,nome),bytes);console.log(JSON.stringify({arquivo:nome,bytes:bytes.length,largura:imagens[key].largura,altura:imagens[key].altura,alphaCanto:imagens[key].alphaCanto}));}
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
