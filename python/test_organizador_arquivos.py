"""Somente arquivos sintéticos e diretórios temporários."""
import tempfile
import unittest
from pathlib import Path
from organizador_arquivos import organizar


class OrganizadorTest(unittest.TestCase):
    def test_preserva_destino_existente(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            origem = base / 'sintetico.xml'
            origem.write_text('<sintetico/>', encoding='utf-8')
            destino = organizar(str(origem), str(base / 'saida'), '123')
            origem.write_text('<corrigido/>', encoding='utf-8')
            with self.assertRaises(FileExistsError):
                organizar(str(origem), str(base / 'saida'), '123')
            self.assertEqual(destino.read_text(encoding='utf-8'), '<sintetico/>')

    def test_travessia_nao_cria_destino(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            origem = base / 'sintetico.xml'
            origem.write_text('<sintetico/>', encoding='utf-8')
            with self.assertRaises(ValueError):
                organizar(str(origem), str(base / 'saida'), '../escape')
            self.assertFalse((base / 'escape').exists())


if __name__ == '__main__':
    unittest.main()
