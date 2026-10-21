#!/usr/bin/env python3
"""Valida o fluxo ZIP no navegador com banco e arquivos exclusivamente sintéticos."""
import argparse
import io
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import zipfile
from playwright.sync_api import sync_playwright, expect


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',required=True,type=Path)
    parser.add_argument('--java',default=shutil.which('java'))
    parser.add_argument('--chromium',default=shutil.which('chromium'))
    parser.add_argument('--saida',required=True,type=Path)
    args=parser.parse_args()
    root=Path(__file__).resolve().parent.parent
    output=args.saida.resolve();output.mkdir(parents=True,exist_ok=True)
    xml=(root/'src/main/resources/email/cte-sintetico.xml').read_text()
    chave='35250412345678000195570020000662131001747719'
    base=chave[:25]+'000066214'+chave[34:43]
    soma=sum(int(c)*(2+i%8) for i,c in enumerate(reversed(base)))
    dv=11-soma%11;dv=0 if dv>=10 else dv
    segundo=xml.replace(chave,base+str(dv)).replace('<nCT>66213</nCT>','<nCT>66214</nCT>')
    pacote=io.BytesIO()
    with zipfile.ZipFile(pacote,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('cte-1.xml',xml);z.writestr('cte-2.xml',segundo)
        z.writestr('fatura-compartilhada.pdf',b'%PDF-1.4\n% sintetico\n%%EOF')
        z.writestr('comprovante.pdf',b'%PDF-1.4\n% comprovante sintetico\n%%EOF')
    checks=[];errors=[]
    with tempfile.TemporaryDirectory(prefix='rotabrasil-pacotes-ui-') as base_dir:
        with (output/'servidor.log').open('w') as log:
            process=subprocess.Popen([args.java,'-jar',str(args.jar.resolve()),'--server.port=0',
                '--spring.datasource.url=jdbc:h2:file:'+base_dir+'/banco','--spring.flyway.enabled=true',
                '--spring.h2.console.enabled=false','--rotabrasil.arquivos.viagens='+base_dir+'/viagens'],cwd=root,stdout=log,stderr=subprocess.STDOUT)
            try:
                deadline=time.monotonic()+45
                while True:
                    match=re.search(r'Tomcat started on port (\d+)',(output/'servidor.log').read_text())
                    if match:break
                    if process.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Servidor não iniciou; conferir log')
                    time.sleep(.1)
                url='http://127.0.0.1:'+match.group(1)
                with sync_playwright() as p:
                    browser=p.chromium.launch(executable_path=args.chromium,headless=True,args=['--no-sandbox'])
                    try:
                        page=browser.new_page(viewport={'width':1440,'height':1000},locale='pt-BR')
                        page.on('pageerror',lambda e:errors.append(str(e)))
                        page.goto(url,wait_until='networkidle')
                        page.locator('#arquivo-zip').set_input_files({'name':'viagens.zip','mimeType':'application/zip','buffer':pacote.getvalue()})
                        page.locator('#inspecionar-zip').click()
                        expect(page.locator('#resumo-zip')).to_contain_text('2 viagem(ns) e 2 PDF(s)')
                        expect(page.locator('#total-viagens')).to_have_text('0')
                        expect(page.locator('#confirmar-zip')).to_be_disabled()
                        checks.append('Conferência do ZIP identifica CT-es/PDFs sem gravar e sem associação automática')
                        fatura=page.locator('#vinculo-a2');comprovante=page.locator('#vinculo-a3')
                        fatura.locator('select').select_option('FATURA')
                        for box in fatura.locator('input').all():box.check()
                        comprovante.locator('select').select_option('VIAGEM')
                        for box in comprovante.locator('input').all():box.check()
                        expect(page.locator('#confirmar-zip')).to_be_disabled()
                        comprovante.locator('input').nth(1).uncheck()
                        expect(page.locator('#confirmar-zip')).to_be_enabled()
                        checks.append('Fatura aceita várias viagens; comprovante exige uma e todos os PDFs devem ser associados')
                        page.locator('#confirmar-zip').click()
                        expect(page.locator('#total-viagens')).to_have_text('2')
                        expect(page.locator('#lista-anexos input')).to_have_count(2)
                        expect(page.locator('#gerar-previa')).to_be_disabled()
                        assert len(list(Path(base_dir,'viagens').rglob('*.pdf')))==2
                        checks.append('Importação cria duas viagens e um único arquivo por PDF, sem seleção de envio automática')
                        page.locator('#lista-documentos input').check()
                        page.locator('#lista-anexos input').first.check()
                        page.locator('#destinatario').fill('conferencia@example.invalid')
                        page.locator('#gerar-previa').click()
                        expect(page.locator('#resultado-previa')).to_contain_text('fatura-compartilhada.pdf')
                        expect(page.locator('#resultado-previa')).to_contain_text('Nenhum e-mail enviado')
                        assert page.request.get(url+'/api/viagens/1/envios').json()==[]
                        checks.append('Prévia combina XML e fatura com destinatário explícito sem enviar ou criar histórico')
                        page.locator('#viagem-selecionada').select_option('2')
                        expect(page.locator('#lista-anexos input')).to_have_count(1)
                        expect(page.locator('#lista-anexos')).to_contain_text('fatura-compartilhada.pdf')
                        expect(page.locator('#gerar-previa')).to_be_disabled()
                        expect(page.locator('#resultado-previa')).to_be_hidden()
                        checks.append('Outra viagem mostra mesma fatura compartilhada e limpa seleção/prévia anteriores')
                        page.get_by_role('button',name='Arquivar PDF fatura-compartilhada.pdf',exact=True).click()
                        page.locator('#executar-exclusao').click()
                        expect(page.locator('#lista-anexos input')).to_have_count(0)
                        page.locator('#mostrar-arquivados').check()
                        page.get_by_role('button',name='Restaurar PDF fatura-compartilhada.pdf',exact=True).click()
                        expect(page.locator('#aviso')).to_contain_text('PDF restaurado')
                        page.locator('#mostrar-arquivados').uncheck()
                        expect(page.locator('#lista-anexos input')).to_have_count(1)
                        checks.append('PDF compartilhado arquiva e restaura preservando o arquivo e os vínculos')
                        page.locator('#confirmar-zip').click()
                        expect(page.locator('#aviso')).to_contain_text('já registrado, sem duplicação')
                        expect(page.locator('#total-viagens')).to_have_text('2')
                        assert len(list(Path(base_dir,'viagens').rglob('*.pdf')))==2
                        checks.append('Repetir confirmação reutiliza pacote e não duplica arquivos')
                        page.reload(wait_until='networkidle')
                        expect(page.locator('#total-viagens')).to_have_text('2')
                        page.locator('#arquivo-zip').set_input_files({'name':'viagens.zip','mimeType':'application/zip','buffer':pacote.getvalue()})
                        page.locator('#inspecionar-zip').click()
                        expect(page.locator('#conferencia-zip')).to_be_visible()
                        page.screenshot(path=str(output/'desktop.png'),full_page=True)
                        page.set_viewport_size({'width':390,'height':844})
                        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
                        page.screenshot(path=str(output/'mobile.png'),full_page=True)
                        checks.append('Persistência após recarga, conferência em desktop e celular sem overflow')
                        assert not errors,errors
                        result={'total':len(checks),'verificacoes':checks,'errosJavaScript':errors,'somenteDadosSinteticos':True,'envioRealExecutado':False}
                        (output/'resultado-ui.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
                        print(json.dumps(result,ensure_ascii=False))
                    finally:browser.close()
            finally:
                process.terminate()
                try:process.wait(timeout=15)
                except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)


if __name__=='__main__':main()
