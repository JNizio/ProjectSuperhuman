from __future__ import annotations
import json
from pathlib import Path
from typing import Iterable
from .schema import validate_example_dict


def canonical_json(data: dict) -> str:
    return json.dumps(data, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def write_jsonl(path: str | Path, examples: Iterable[dict]) -> None:
    lines = []
    for example in examples:
        errors = validate_example_dict(example)
        if errors:
            raise ValueError("; ".join(errors))
        lines.append(canonical_json(example))
    Path(path).write_text("\n".join(lines) + ("\n" if lines else ""), encoding="utf-8")


def read_jsonl(path: str | Path) -> list[dict]:
    out: list[dict] = []
    for line_no, raw in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip():
            continue
        data = json.loads(raw)
        errors = validate_example_dict(data)
        if errors:
            raise ValueError(f"line {line_no}: " + "; ".join(errors))
        out.append(data)
    return out
