/* Somente fixture sintética: um CT-e e dois PDFs sem valor fiscal. */
'use strict';
const fs=require('node:fs'),path=require('node:path');
function pacoteSintetico(){
  const entries=[['central-sintetico.xml',fs.readFileSync(path.join(__dirname,'../back/src/main/resources/email/cte-sintetico.xml'))],['recibo-sintetico.pdf',Buffer.from('%PDF-1.4\n% RECIBO SINTETICO SEM VALOR FISCAL\n%%EOF')],['fatura-sintetica.pdf',Buffer.from('%PDF-1.4\n% FATURA SINTETICA SEM VALOR FISCAL\n%%EOF')]];
  const locals=[],centrals=[];let offset=0;
  for(const [name,data] of entries){
    const filename=Buffer.from(name);let crc=0xffffffff;
    for(const b of data){crc^=b;for(let i=0;i<8;i++)crc=(crc>>>1)^((crc&1)?0xedb88320:0);}crc=(crc^0xffffffff)>>>0;
    const local=Buffer.alloc(30);local.writeUInt32LE(0x04034b50);local.writeUInt16LE(20,4);local.writeUInt32LE(crc,14);local.writeUInt32LE(data.length,18);local.writeUInt32LE(data.length,22);local.writeUInt16LE(filename.length,26);
    const central=Buffer.alloc(46);central.writeUInt32LE(0x02014b50);central.writeUInt16LE(20,4);central.writeUInt16LE(20,6);central.writeUInt32LE(crc,16);central.writeUInt32LE(data.length,20);central.writeUInt32LE(data.length,24);central.writeUInt16LE(filename.length,28);central.writeUInt32LE(offset,42);
    locals.push(local,filename,data);centrals.push(central,filename);offset+=local.length+filename.length+data.length;
  }
  const central=Buffer.concat(centrals),end=Buffer.alloc(22);end.writeUInt32LE(0x06054b50);end.writeUInt16LE(entries.length,8);end.writeUInt16LE(entries.length,10);end.writeUInt32LE(central.length,12);end.writeUInt32LE(offset,16);
  return Buffer.concat([...locals,central,end]);
}
function configuracao(inspecao){
  const xml=inspecao.arquivos.find(a=>a.tipo==='XML');
  if(!xml)throw Error('Fixture sem XML');
  return {sha256:inspecao.sha256,vinculos:inspecao.arquivos.filter(a=>a.tipo==='PDF').map(a=>({arquivoId:a.id,finalidade:a.nomeArquivo.includes('fatura')?'FATURA':'VIAGEM',xmlIds:[xml.id]}))};
}
module.exports={pacoteSintetico,configuracao};
