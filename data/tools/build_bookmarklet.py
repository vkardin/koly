"""Print the one-line `javascript:` bookmark for tools/dtek_bookmarklet.js."""

from __future__ import annotations

import re
from pathlib import Path
from urllib.parse import quote

SOURCE = Path(__file__).resolve().parent / "dtek_bookmarklet.js"


def build() -> str:
    code = re.sub(r"/\*.*?\*/", "", SOURCE.read_text(encoding="utf-8"), flags=re.S)
    code = " ".join(line.strip() for line in code.splitlines() if line.strip())
    return "javascript:" + quote(code, safe="(){}[];,.:=+-*/<>!?&|'\" ")


if __name__ == "__main__":
    print(build())
