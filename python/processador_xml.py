"""Validação estrutural opcional de XML; o processamento de negócio permanece no Java."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def validar_xml(caminho: str) -> None:
    arquivo = Path(caminho).resolve()
    if arquivo.suffix.lower() != ".xml":
        raise ValueError("A entrada deve ser XML")
    ET.parse(arquivo)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Uso: python processador_xml.py caminho.xml")
    validar_xml(sys.argv[1])
    print("XML estruturalmente válido")
