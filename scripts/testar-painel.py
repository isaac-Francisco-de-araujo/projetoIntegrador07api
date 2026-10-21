#!/usr/bin/env python3
"""Teste de navegador com JAR, H2 e XML sintéticos; nunca usa o banco padrão."""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time

from playwright.sync_api import sync_playwright, expect


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--java', default=shutil.which('java'))
    parser.add_argument('--chromium', default=shutil.which('chromium'))
    parser.add_argument('--saida', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    jar = args.jar.resolve(strict=True)
    output = args.saida or Path(tempfile.mkdtemp(prefix='rotabrasil-painel-resultados-'))
    output.mkdir(parents=True, exist_ok=True)
    xml = (root / 'src/main/resources/email/cte-sintetico.xml').read_bytes()
    checks = []
    errors = []
    with tempfile.TemporaryDirectory(prefix='rotabrasil-painel-banco-') as base:
        with (output / 'servidor.log').open('w') as log:
            process = subprocess.Popen([
                args.java, '-jar', str(jar), '--server.port=0',
                '--spring.datasource.url=jdbc:h2:file:' + base + '/banco',
                '--spring.flyway.enabled=true', '--spring.h2.console.enabled=false',
                '--rotabrasil.arquivos.viagens=' + base + '/viagens'
            ], cwd=root, stdout=log, stderr=subprocess.STDOUT)
            try:
                deadline = time.monotonic() + 40
                while True:
                    port = re.search(r'Tomcat started on port (\d+)', (output / 'servidor.log').read_text())
                    if port:
                        break
                    if process.poll() is not None or time.monotonic() > deadline:
                        raise RuntimeError('Servidor não iniciou; conferir servidor.log')
                    time.sleep(.1)
                url = 'http://127.0.0.1:' + port.group(1)
                with sync_playwright() as playwright:
                    browser = playwright.chromium.launch(executable_path=args.chromium, headless=True, args=['--no-sandbox'])
                    try:
                        page = browser.new_page(viewport={'width': 1440, 'height': 1000}, locale='pt-BR')
                        page.on('pageerror', lambda error: errors.append(str(error)))
                        page.goto(url, wait_until='networkidle')
                        expect(page).to_have_title('RotaBrasil · Painel de testes')
                        expect(page.locator('#total-documentos')).to_have_text('0')
                        expect(page.locator('#gerar-previa')).to_be_disabled()
                        checks.append('Página e estado vazio carregados pelo JAR')
                        page.locator('#arquivo').set_input_files({'name': 'sintetico.xml', 'mimeType': 'application/xml', 'buffer': xml})
                        page.locator('#importar').click()
                        expect(page.locator('#total-documentos')).to_have_text('1')
                        expect(page.locator('#total-clientes')).to_have_text('1')
                        expect(page.locator('#total-viagens')).to_have_text('1')
                        expect(page.locator('#lista-documentos input[type=checkbox]')).to_have_count(1)
                        expect(page.locator('#lista-clientes')).to_contain_text('CLIENTE SINTETICO')
                        checks.append('Importação HTTP completa refletida em viagens, clientes e documentos')
                        page.locator('#lista-documentos input[type=checkbox]').check()
                        page.locator('#destinatario').fill('conferencia@example.invalid')
                        page.locator('#gerar-previa').click()
                        expect(page.locator('#resultado-previa')).to_contain_text('Nenhum e-mail enviado.')
                        expect(page.locator('#resultado-previa')).to_contain_text('sintetico.xml')
                        assert page.request.get(url + '/api/viagens/1/envios').json() == []
                        checks.append('Prévia com seleção explícita, sem criar histórico ou enviar e-mail')
                        page.evaluate('window.scrollTo(0, 0)')
                        page.screenshot(path=str(output / 'desktop.png'), full_page=True)
                        page.locator('#destinatario').fill('outro@example.invalid')
                        expect(page.locator('#resultado-previa')).to_be_hidden()
                        page.locator('#arquivo').set_input_files({'name': 'renomeado.xml', 'mimeType': 'application/xml', 'buffer': xml})
                        page.locator('#importar').click()
                        expect(page.locator('#aviso')).to_contain_text('conteúdo idêntico')
                        expect(page.locator('#importar')).to_be_enabled()
                        expect(page.locator('#total-documentos')).to_have_text('1')
                        checks.append('Reimportação com outro nome não duplica documentos')
                        page.locator('#buscar-viagem').fill('inexistente')
                        expect(page.locator('#lista-viagens')).to_contain_text('Nenhuma viagem corresponde')
                        page.locator('#buscar-viagem').fill('66213')
                        expect(page.get_by_role('button', name='Ver documentos da viagem 66213')).to_be_visible()
                        page.locator('#buscar-viagem').fill('')
                        checks.append('Busca de viagem e estado sem resultados')
                        page.locator('#arquivo').set_input_files({'name': 'invalido.xml', 'mimeType': 'application/xml', 'buffer': b'<CTe>'})
                        page.locator('#importar').click()
                        expect(page.locator('#aviso')).to_have_class('notice error')
                        expect(page.locator('#importar')).to_be_enabled()
                        expect(page.locator('#total-documentos')).to_have_text('1')
                        checks.append('Erro de XML exibido sem alterar dados ou travar o formulário')
                        page.reload(wait_until='networkidle')
                        expect(page.locator('#total-documentos')).to_have_text('1')
                        expect(page.locator('#gerar-previa')).to_be_disabled()
                        checks.append('Dados persistem após recarregar, sem seleção de envio automática')
                        malicious = '<img src=x onerror="window.xss=true">'
                        response = page.request.put(url + '/api/clientes/1', data={'nome': malicious, 'cnpj': '00000000000000'})
                        assert response.ok
                        page.locator('#atualizar').click()
                        expect(page.locator('#lista-clientes')).to_contain_text(malicious)
                        assert page.locator('#lista-clientes img').count() == 0
                        assert page.evaluate('window.xss') is None
                        checks.append('Dados vindos da API são texto, sem execução de HTML')
                        page.route('**/api/viagens/*/documentos', lambda route: route.fulfill(status=503, content_type='application/json', body=json.dumps({'mensagem': 'Falha sintética', 'campos': []})))
                        page.locator('#viagem-selecionada').select_option('1')
                        expect(page.locator('#lista-documentos')).to_contain_text('Não foi possível carregar')
                        expect(page.locator('#gerar-previa')).to_be_disabled()
                        page.unroute('**/api/viagens/*/documentos')
                        page.locator('#atualizar').click()
                        expect(page.locator('#lista-documentos input[type=checkbox]')).to_have_count(1)
                        checks.append('Falha de consulta bloqueia seleção antiga e permite recuperação')
                        assert page.request.put(url + '/api/clientes/1', data={'nome': 'CLIENTE SINTETICO - SEM VALOR FISCAL', 'cnpj': '00000000000000'}).ok
                        page.reload(wait_until='networkidle')
                        page.set_viewport_size({'width': 390, 'height': 844})
                        expect(page.locator('#total-documentos')).to_have_text('1')
                        assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
                        page.screenshot(path=str(output / 'mobile.png'), full_page=True)
                        checks.append('Layout móvel sem rolagem horizontal da página')
                        deletes = []
                        page.on('request', lambda request: deletes.append(request.url) if request.method == 'DELETE' else None)
                        response = page.request.post(url + '/api/clientes', data={'nome': 'Cliente sem vínculos', 'cnpj': '11111111111111'})
                        assert response.status == 201
                        cliente_teste = response.json()['id']
                        page.locator('#atualizar').click()
                        expect(page.locator('#total-clientes')).to_have_text('2')
                        page.locator(f'[data-excluir-cliente="{cliente_teste}"]').click()
                        expect(page.get_by_role('dialog')).to_be_visible()
                        expect(page.locator('#cancelar-exclusao')).to_be_focused()
                        page.locator('#cancelar-exclusao').click()
                        assert deletes == []
                        expect(page.locator('#total-clientes')).to_have_text('2')
                        checks.append('Cancelar confirmação não emite pedido DELETE')
                        page.locator(f'[data-excluir-cliente="{cliente_teste}"]').click()
                        page.locator('#executar-exclusao').click()
                        expect(page.get_by_role('dialog')).not_to_be_visible()
                        expect(page.locator('#total-clientes')).to_have_text('1')
                        expect(page.locator('#aviso')).to_contain_text('Cliente excluído com sucesso')
                        assert page.request.get(url + '/api/clientes/' + str(cliente_teste)).status == 404
                        checks.append('Cliente sem vínculos excluído com resposta 204 e tela atualizada')
                        response = page.request.post(url + '/api/clientes', data={'nome': 'Cliente com viagem', 'cnpj': '11111111111111'})
                        cliente_teste = response.json()['id']
                        response = page.request.post(url + '/api/viagens', data={'numero': '1', 'clienteId': cliente_teste})
                        assert response.status == 201
                        viagem_teste = response.json()['id']
                        page.locator('#atualizar').click()
                        expect(page.locator('#total-viagens')).to_have_text('2')
                        page.locator(f'[data-excluir-cliente="{cliente_teste}"]').click()
                        page.locator('#executar-exclusao').click()
                        expect(page.get_by_role('dialog')).not_to_be_visible()
                        expect(page.locator('#aviso')).to_contain_text('não pode ser excluído')
                        expect(page.locator('#total-clientes')).to_have_text('2')
                        checks.append('Cliente vinculado é preservado e o motivo é exibido')
                        page.locator(f'[data-excluir-viagem="{viagem_teste}"]').click()
                        count = len(deletes)
                        page.keyboard.press('Escape')
                        expect(page.get_by_role('dialog')).not_to_be_visible()
                        assert len(deletes) == count
                        page.locator(f'[data-excluir-viagem="{viagem_teste}"]').click()
                        page.locator('#executar-exclusao').click()
                        expect(page.get_by_role('dialog')).not_to_be_visible()
                        expect(page.locator('#total-viagens')).to_have_text('1')
                        assert page.request.get(url + '/api/clientes/' + str(cliente_teste)).status == 200
                        page.locator(f'[data-excluir-cliente="{cliente_teste}"]').click()
                        page.locator('#executar-exclusao').click()
                        expect(page.locator('#total-clientes')).to_have_text('1')
                        checks.append('Viagem vazia excluída sem apagar cliente; Escape cancela a confirmação')
                        page.locator('#viagem-selecionada').select_option('1')
                        expect(page.locator('#lista-documentos input[type=checkbox]')).to_have_count(1)
                        page.get_by_role('button', name='Arquivar documento sintetico.xml', exact=True).click()
                        expect(page.locator('#descricao-exclusao')).to_contain_text('preservado')
                        count = len(deletes)
                        page.locator('#cancelar-exclusao').click()
                        assert len(deletes) == count
                        expect(page.locator('#total-documentos')).to_have_text('1')
                        checks.append('Arquivamento tem confirmação própria e cancelamento sem efeitos')
                        files = list((Path(base) / 'viagens/66213').glob('*.xml'))
                        assert len(files) == 1
                        arquivo = files[0]
                        antes = arquivo.read_bytes()
                        modificacao = arquivo.stat().st_mtime_ns
                        page.get_by_role('button', name='Arquivar documento sintetico.xml', exact=True).click()
                        page.locator('#executar-exclusao').click()
                        expect(page.get_by_role('dialog')).not_to_be_visible()
                        expect(page.locator('#total-documentos')).to_have_text('0')
                        expect(page.locator('#gerar-previa')).to_be_disabled()
                        assert arquivo.read_bytes() == antes
                        response = page.request.post(url + '/api/viagens/1/previa-email', data={'destinatario': 'teste@example.invalid', 'documentoIds': [1]})
                        assert response.status == 409
                        page.locator('#arquivo').set_input_files({'name': 'sintetico.xml', 'mimeType': 'application/xml', 'buffer': xml})
                        page.locator('#importar').click()
                        expect(page.locator('#aviso')).to_contain_text('restaure')
                        checks.append('Arquivamento preserva XML e bloqueia prévia/reimportação sem restauração')
                        page.locator('#mostrar-arquivados').check()
                        expect(page.get_by_role('button', name='Restaurar documento sintetico.xml', exact=True)).to_be_visible()
                        expect(page.locator('#lista-documentos input[type=checkbox]')).to_have_count(0)
                        page.get_by_role('button', name='Restaurar documento sintetico.xml', exact=True).click()
                        expect(page.locator('#total-documentos')).to_have_text('1')
                        expect(page.locator('#lista-documentos')).to_contain_text('Nenhum documento arquivado')
                        page.locator('#mostrar-arquivados').uncheck()
                        expect(page.locator('#lista-documentos input[type=checkbox]')).to_have_count(1)
                        assert arquivo.read_bytes() == antes and arquivo.stat().st_mtime_ns == modificacao
                        checks.append('Documento restaurado com mesmo ID e arquivo intocado, sem seleção automática')
                        assert not errors, errors
                        assert page.request.get(url + '/api/viagens/1/envios').json() == []
                        result = {'verificacoes': checks, 'total': len(checks), 'errosJavaScript': errors, 'somenteDadosSinteticos': True, 'envioRealExecutado': False}
                        (output / 'resultado-ui.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
                        print(json.dumps(result, ensure_ascii=False))
                    finally:
                        browser.close()
            finally:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)


if __name__ == '__main__':
    main()
