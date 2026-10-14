"""Auxiliar opcional; não substitui a organização oficial feita pelo Java."""
from pathlib import Path
import shutil


def organizar(arquivo: str, raiz: str, identificador: str) -> Path:
    origem = Path(arquivo).resolve()
    destino_raiz = Path(raiz).resolve()
    if not identificador or Path(identificador).name != identificador:
        raise ValueError("Identificador inválido")
    pasta = (destino_raiz / identificador).resolve()
    if destino_raiz not in pasta.parents:
        raise ValueError("Destino fora da raiz permitida")
    pasta.mkdir(parents=True, exist_ok=True)
    destino = (pasta / origem.name).resolve()
    if destino.parent != pasta:
        raise ValueError("Nome de arquivo inválido")
    shutil.copy2(origem, destino)
    return destino
