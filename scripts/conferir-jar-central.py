"""Inspeciona somente artefato de código; nunca inicia Java nem abre bancos/config externos."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile


def conferir(jar):
    arquivo = Path(jar).resolve(strict=True)
    with arquivo.open('rb') as entrada:
        digest = hashlib.file_digest(entrada, 'sha256').hexdigest()
    prefixo = 'BOOT-INF/classes/'
    with ZipFile(arquivo) as pacote:
        def ler(nome):
            return pacote.read(prefixo + nome)
        base = ler('static/painel-base.js').decode('utf-8')
        docs = ler('static/painel-documentos.js').decode('utf-8')
        email = ler('static/painel-email.js').decode('utf-8')
        arquivos = {nome: hashlib.sha256(ler('static/' + nome)).hexdigest()
                    for nome in ('index.html', 'painel.js', 'painel-base.js', 'painel-documentos.js', 'painel-email.js', 'painel-versao.js', 'painel-configuracoes.js', 'painel-conta-teste.js') if prefixo + 'static/' + nome in pacote.namelist()}
        build = next((pacote.read(nome).decode('utf-8') for nome in (prefixo + 'META-INF/build-info.properties', 'META-INF/build-info.properties') if nome in pacote.namelist()), '')
        return {'jar': str(arquivo), 'sha256': digest,
                'build': dict((k, v.replace('\\:', ':')) for linha in build.splitlines() if linha.startswith(('build.time=', 'build.version=')) for k, v in [linha.split('=', 1)]),
                'avisoGenericoAntigo': "?'Envio manual':'Envio não configurado'" in base,
                 'previaBloqueadaPorModo': "$('gerar-previa').disabled = !state.envioReal" in docs,
                'regraEdicaoDestinatario': [{'linha': numero, 'trecho': linha.strip()} for numero, linha in enumerate(docs.splitlines(), 1) if "$('destinatario').disabled" in linha],
                'consultaDocumental': "api('/api/envio-documental')" in email,
                'backendDiagnosticoDocumental': prefixo + 'br/com/rotabrasil/controller/EnvioDocumentalController.class' in pacote.namelist(),
                'estaticosSha256': arquivos}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', nargs='+')
    args = parser.parse_args()
    print(json.dumps([conferir(jar) for jar in args.jar], ensure_ascii=False, indent=2))
