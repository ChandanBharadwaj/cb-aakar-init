"""Outcome families (Avatars) from ``packages/design-tokens/families.json`` (plan §1).

A family is the form a customer's idea takes: a carrier (keychain, magnet, ornament, ...), one of
the parametric object families (phone stand, ...) or the single raw ``raw_print`` family. Every
template declares its ``family``; the registry refuses templates whose family is not in this file,
``Template.materials()`` applies the family's ``material_rules`` and the features stage reads the
``content_slot`` (``accepts``, ``max_text_chars``).

Loaded from ``AAKAR_FAMILIES_FILE`` when set (the Dockerfile copies the seed to
``/design-tokens/families.json``), else the repo-relative path, else the built-in copy of the launch
families so the registry still resolves when the design tokens are not mounted (wheels). A mounted
file is validated against ``template-family.v1.json`` so a drifted seed fails loudly at import.
"""

from __future__ import annotations

import json
import os
from functools import lru_cache
from pathlib import Path
from typing import Any, Mapping

from .contracts import ContractError, validate

FEATURE_TYPES = ("emboss_text", "motif", "relief_image", "hero_mesh")


class UnknownFamily(LookupError):
    """``family`` is not an id in families.json."""


class FamilyConfigError(RuntimeError):
    """families.json is unusable: it violates the contract or its rules leave a template with no material."""


def _row(
    id: str,
    kind: str,
    default_template_id: str,
    accepts: list[str],
    anchors: list[str],
    *,
    hero_volume: bool = False,
    max_text_chars: int | None = None,
    hardware: list[dict[str, Any]] | None = None,
    allowed: list[str] | None = None,
    heat_safe_only: bool = False,
    shape_tolerance: str = "any",
    available: bool = True,
    envelope: tuple[float, float] | None = None,
) -> dict[str, Any]:
    slot: dict[str, Any] = {"accepts": list(accepts), "anchors": list(anchors), "hero_volume": hero_volume}
    if max_text_chars is not None:
        slot["max_text_chars"] = max_text_chars
    row: dict[str, Any] = {
        "id": id,
        "codename": id,
        "name": id.replace("_", " "),
        "kind": kind,
        "tier": "launch",
        "shelf": "gifting",
        "default_template_id": default_template_id,
        "hardware": list(hardware or []),
        "material_rules": {"heat_safe_only": heat_safe_only, "allowed": allowed, "excluded_finish_classes": []},
        "shape_tolerance": shape_tolerance,
        "content_slot": slot,
        "available": available,
    }
    if envelope is not None:
        row["size_envelope_mm"] = {"min_longest_mm": envelope[0], "max_longest_mm": envelope[1]}
    return row


# Built-in copy of the launch-tier rows (ids, kinds, content slots, size envelopes, hardware and material
# rules only; brand copy lives in the seed). Keep in step with packages/design-tokens/families.json.
BUILTIN_FAMILIES: list[dict[str, Any]] = [
    _row("keychain", "carrier", "keychain_tag", ["relief_image", "emboss_text", "motif"], ["face", "back"],
         max_text_chars=16, hardware=[{"sku": "split_ring_25", "qty": 1}], envelope=(30, 60)),
    _row("fridge_magnet", "carrier", "fridge_magnet", ["relief_image", "emboss_text", "motif"], ["face"],
         max_text_chars=12, hardware=[{"sku": "magnet_d10x3", "qty": 1}], envelope=(40, 70)),
    _row("ornament", "carrier", "hanging_ornament", ["relief_image", "emboss_text", "motif"], ["face_front", "face_back"],
         max_text_chars=12, hardware=[{"sku": "cord_200", "qty": 1}], envelope=(50, 90)),
    _row("nameplate", "carrier", "desk_nameplate", ["emboss_text", "motif", "relief_image"], ["face", "base_front"],
         max_text_chars=24, hardware=[{"sku": "adhesive_pads", "qty": 1}], envelope=(120, 250)),
    _row("lithophane", "carrier", "lithophane_plate", ["relief_image"], ["plate"],
         hardware=[{"sku": "led_base_usb", "qty": 1}], allowed=["basic_white"], shape_tolerance="strict", available=False,
         envelope=(100, 150)),
    _row("figurine_base", "carrier", "plinth_round", ["hero_mesh", "emboss_text"], ["top", "base_front"],
         hero_volume=True, max_text_chars=16, available=False, envelope=(50, 200)),
    _row("raw_print", "raw", "raw_print", ["hero_mesh"], ["body"], hero_volume=True, envelope=(20, 240)),
    _row("phone_stand", "object", "jharokha_phone_stand", ["emboss_text", "motif"], ["side_left", "side_right", "back"],
         max_text_chars=16, shape_tolerance="strict", envelope=(80, 160)),
]

BUILTIN_HARDWARE: list[dict[str, Any]] = [
    {"sku": "split_ring_25", "name": "Steel split ring 25 mm", "unit_cost_paise": 300},
    {"sku": "magnet_d10x3", "name": "Neodymium disc magnet 10 × 3 mm", "unit_cost_paise": 1500},
    {"sku": "cord_200", "name": "Cotton hanging cord 200 mm", "unit_cost_paise": 200},
    {"sku": "led_base_usb", "name": "USB LED puck base 70 mm", "unit_cost_paise": 18000},
    {"sku": "acrylic_4x6", "name": "Acrylic pane 4 × 6 in", "unit_cost_paise": 4000},
    {"sku": "nameplate_screws", "name": "Wall screws and anchors, pair", "unit_cost_paise": 600},
    {"sku": "adhesive_pads", "name": "Foam adhesive pads, pair", "unit_cost_paise": 300},
]


def families_file() -> Path | None:
    env = os.environ.get("AAKAR_FAMILIES_FILE")
    if env:
        return Path(env).expanduser()
    candidate = (Path(__file__).resolve().parent.parent / "../../packages/design-tokens/families.json").resolve()
    return candidate if candidate.exists() else None


@lru_cache(maxsize=1)
def load_families_doc() -> dict[str, Any]:
    """The whole ``{shelves, hardware_items, families}`` document (validated when read from a file)."""
    path = families_file()
    if path and path.exists():
        doc = json.loads(path.read_text(encoding="utf-8"))
        try:
            validate("template-family", doc)
        except ContractError as exc:
            raise FamilyConfigError(f"{path} does not match template-family.v1.json: {exc}") from exc
        if doc.get("families"):
            return {"source": str(path), "shelves": list(doc.get("shelves") or []),
                    "hardware_items": [dict(h) for h in doc.get("hardware_items") or []],
                    "families": [dict(f) for f in doc["families"]]}
    return {"source": "builtin", "shelves": [], "hardware_items": [dict(h) for h in BUILTIN_HARDWARE],
            "families": [json.loads(json.dumps(f)) for f in BUILTIN_FAMILIES]}


def load_families() -> dict[str, dict[str, Any]]:
    """Family rows keyed by id, in seed order."""
    return {f["id"]: f for f in load_families_doc()["families"]}


def family_ids() -> list[str]:
    return list(load_families())


def family(family_id: str) -> dict[str, Any]:
    try:
        return load_families()[family_id]
    except KeyError:
        raise UnknownFamily(f"Unknown family '{family_id}'; known: {', '.join(family_ids())}") from None


def content_slot(family_id: str) -> dict[str, Any]:
    """``content_slot`` with the contract defaults filled (``accepts``, ``anchors``, ``hero_volume``, ``max_text_chars``)."""
    slot = dict(family(family_id).get("content_slot") or {})
    return {
        "accepts": list(slot.get("accepts") or []),
        "anchors": list(slot.get("anchors") or []),
        "hero_volume": bool(slot.get("hero_volume", False)),
        "max_text_chars": slot.get("max_text_chars"),
    }


def material_rules(family_id: str) -> dict[str, Any]:
    """``material_rules`` with the contract defaults filled."""
    rules = dict(family(family_id).get("material_rules") or {})
    allowed = rules.get("allowed")
    return {
        "heat_safe_only": bool(rules.get("heat_safe_only", False)),
        "allowed": list(allowed) if allowed is not None else None,
        "excluded_finish_classes": list(rules.get("excluded_finish_classes") or []),
    }


def size_envelope(family_id: str) -> tuple[float, float] | None:
    """``(min_longest_mm, max_longest_mm)``: the longest side a piece of this family may have (None when unset)."""
    envelope = family(family_id).get("size_envelope_mm")
    if not envelope:
        return None
    return (float(envelope["min_longest_mm"]), float(envelope["max_longest_mm"]))


def default_hardware(family_id: str) -> list[dict[str, Any]]:
    """The family's default bill of materials, ``[{sku, qty}]`` (a template's ``hardware`` overrides it)."""
    return [{"sku": h["sku"], "qty": int(h["qty"])} for h in family(family_id).get("hardware") or []]


def hardware_items() -> dict[str, dict[str, Any]]:
    return {h["sku"]: h for h in load_families_doc()["hardware_items"]}


def apply_material_rules(rules: Mapping[str, Any], materials: list[Mapping[str, Any]]) -> list[str]:
    """Filter material rows (``id``, ``finish_class``, ``heat_safe``) by a family's rules; returns ids in input order."""
    allowed = rules.get("allowed")
    excluded = set(rules.get("excluded_finish_classes") or [])
    heat_safe_only = bool(rules.get("heat_safe_only", False))
    out: list[str] = []
    for m in materials:
        if allowed is not None and m["id"] not in allowed:
            continue
        if heat_safe_only and not bool(m.get("heat_safe", False)):
            continue
        if m.get("finish_class") in excluded:
            continue
        out.append(m["id"])
    return out


def allowed_material_ids(family_id: str) -> list[str]:
    """Material ids a template of this family may offer. Unknown families (never registered) get every material."""
    from .materials import load_materials

    materials = load_materials()
    try:
        rules = material_rules(family_id)
    except UnknownFamily:
        return [m["id"] for m in materials]
    ids = apply_material_rules(rules, materials)
    if not ids:
        raise FamilyConfigError(
            f"material_rules of family '{family_id}' ({rules}) leave no material out of "
            f"{[m['id'] for m in materials]}; fix families.json or materials.json"
        )
    return ids


__all__ = [
    "BUILTIN_FAMILIES",
    "FEATURE_TYPES",
    "FamilyConfigError",
    "UnknownFamily",
    "allowed_material_ids",
    "apply_material_rules",
    "content_slot",
    "default_hardware",
    "families_file",
    "family",
    "family_ids",
    "hardware_items",
    "load_families",
    "load_families_doc",
    "material_rules",
    "size_envelope",
]
