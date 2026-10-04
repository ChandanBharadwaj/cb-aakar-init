"""Digital materials from packages/design-tokens/materials.json (PLAN §7.10).

Loaded from ``AAKAR_MATERIALS_FILE`` when set (the Dockerfile copies the file to
``/design-tokens/materials.json``), else the repo-relative path, else the built-in copy of the
six launch ids so descriptors stay correct even when the tokens are not mounted.
"""

from __future__ import annotations

import json
import os
from functools import lru_cache
from pathlib import Path
from typing import Any

LAUNCH_MATERIALS: list[dict[str, Any]] = [
    {"id": "basic_white", "name": "Basic White", "density_g_cm3": 1.24, "finish_class": "matte", "heat_safe": False},
    {"id": "terracotta_matte", "name": "Terracotta Matte", "density_g_cm3": 1.24, "finish_class": "matte", "heat_safe": False},
    {"id": "terracotta_silk", "name": "Terracotta Silk", "density_g_cm3": 1.24, "finish_class": "silk", "heat_safe": False},
    {"id": "polished_brass", "name": "Polished Brass", "density_g_cm3": 1.24, "finish_class": "silk", "heat_safe": False},
    {"id": "sandalwood_silk", "name": "Sandalwood Silk", "density_g_cm3": 1.26, "finish_class": "silk", "heat_safe": False},
    {"id": "indigo_matte", "name": "Indigo Matte", "density_g_cm3": 1.24, "finish_class": "matte", "heat_safe": False},
]


def materials_file() -> Path | None:
    env = os.environ.get("AAKAR_MATERIALS_FILE")
    if env:
        return Path(env).expanduser()
    candidate = (Path(__file__).resolve().parent.parent / "../../packages/design-tokens/materials.json").resolve()
    return candidate if candidate.exists() else None


@lru_cache(maxsize=1)
def load_materials() -> list[dict[str, Any]]:
    path = materials_file()
    if path and path.exists():
        doc = json.loads(path.read_text(encoding="utf-8"))
        mats = doc.get("materials") or []
        if mats:
            return [dict(m) for m in mats]
    return [dict(m) for m in LAUNCH_MATERIALS]


def material_ids() -> list[str]:
    return [m["id"] for m in load_materials()]


def density_g_cm3(material_id: str, default: float = 1.24) -> float:
    for m in load_materials():
        if m["id"] == material_id:
            return float(m.get("density_g_cm3", default))
    return default
