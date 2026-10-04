"""Outcome families (Avatars) from ``packages/design-tokens/families.json`` (plan §1).

A family is the form a customer's idea takes: a carrier (keychain, magnet, ornament, ...), one of
the parametric object families (phone stand, ...) or the single raw ``raw_print`` family. Every
template declares its ``family``; the registry refuses templates whose family is not in this file,
``Template.materials()`` applies the family's ``material_rules`` and the features stage reads the
``content_slot`` (``accepts``, ``max_text_chars``).

Loaded from ``AAKAR_FAMILIES_FILE`` when set (the Dockerfile copies the seed to
``/design-tokens/families.json``), else the repo-relative path, else the built-in copy of every
family that has a template, so the registry still resolves when the design tokens are not mounted
(wheels). A mounted file is validated against ``template-family.v1.json`` so a drifted seed fails
loudly at import.
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
    connector: dict[str, Any] | None = None,
    bases: list[str] | None = None,
    pair: str | None = None,
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
    if connector is not None:  # a hybrid (Jod): its Kadi and the bases it fits, the first the default
        row["connector"] = dict(connector)
        row["bases"] = [{"sku": sku, "default": i == 0} for i, sku in enumerate(bases or [])]
    if pair is not None:
        row["pair_family_id"] = pair
    return row


# Built-in copy of the rows that have templates (ids, kinds, content slots, size envelopes, hardware and material
# rules only; brand copy lives in the seed). Keep in step with packages/design-tokens/families.json.
BUILTIN_FAMILIES: list[dict[str, Any]] = [
    _row("keychain", "carrier", "keychain_tag", ["relief_image", "emboss_text", "motif"], ["face", "back"],
         max_text_chars=16, hardware=[{"sku": "split_ring_25", "qty": 1}], envelope=(25, 60)),
    _row("fridge_magnet", "carrier", "fridge_magnet", ["relief_image", "emboss_text", "motif"], ["face"],
         max_text_chars=12, hardware=[{"sku": "magnet_d10x3", "qty": 1}], envelope=(40, 70)),
    _row("ornament", "carrier", "hanging_ornament", ["relief_image", "emboss_text", "motif"], ["face_front", "face_back"],
         max_text_chars=12, hardware=[{"sku": "cord_200", "qty": 1}], envelope=(50, 90)),
    _row("nameplate", "carrier", "desk_nameplate", ["emboss_text", "motif", "relief_image"], ["face", "base_front"],
         max_text_chars=24, hardware=[{"sku": "adhesive_pads", "qty": 1}], envelope=(120, 250)),
    _row("lithophane", "carrier", "lithophane_plate", ["relief_image"], ["plate"],
         hardware=[{"sku": "led_base_usb", "qty": 1}], allowed=["basic_white"], shape_tolerance="strict",
         envelope=(100, 150)),
    _row("figurine_base", "carrier", "plinth_round", ["hero_mesh", "emboss_text"], ["top", "base_front"],
         hero_volume=True, max_text_chars=16, envelope=(40, 200)),
    _row("raw_print", "raw", "raw_print", ["hero_mesh"], ["body"], hero_volume=True, envelope=(20, 240)),
    # tier next, templates in PR 8 (Chaukhat, Kunji): the registry must resolve them without the seed too
    _row("photo_frame", "carrier", "photo_frame_std", ["emboss_text", "motif"], ["base_front", "border"],
         max_text_chars=24, hardware=[{"sku": "acrylic_4x6", "qty": 1}], shape_tolerance="constrained", available=False,
         envelope=(120, 220)),
    _row("keycap", "carrier", "keycap_mx", ["relief_image", "emboss_text"], ["top"],
         max_text_chars=3, shape_tolerance="strict", available=False, envelope=(18, 20)),
    _row("phone_stand", "object", "jharokha_phone_stand", ["emboss_text", "motif"], ["side_left", "side_right", "back"],
         max_text_chars=16, shape_tolerance="strict", envelope=(80, 160)),
    _row("headphone_stand", "object", "pillar_headphone_stand", ["emboss_text", "motif"], ["base_front", "pillar"],
         max_text_chars=16, shape_tolerance="strict", envelope=(200, 300), pair="headphone_topper"),
    # hybrid (Jod): a printed Chhaap on a bought-in base through a Kadi (docs/research/hybrid-products)
    _row("headphone_topper", "hybrid", "headphone_topper", ["hero_mesh", "emboss_text", "motif"], ["front", "face"],
         hero_volume=True, max_text_chars=16, hardware=[{"sku": "dowel_nylon_12x30", "qty": 1}], shape_tolerance="strict",
         envelope=(60, 100), connector={"kind": "socket", "nominal_mm": 12, "fit": "press_ribbed", "adapter_sku": "dowel_nylon_12x30"},
         bases=["headphone_stand_steel_12"], pair="headphone_stand"),
    _row("hook_plaque", "hybrid", "hook_plaque", ["emboss_text", "motif", "relief_image"], ["face"],
         max_text_chars=24, hardware=[{"sku": "rail_petg_40", "qty": 1}, {"sku": "vhb_pad_25x40", "qty": 1}], shape_tolerance="strict",
         envelope=(160, 260), connector={"kind": "dovetail", "nominal_mm": 12, "fit": "slide", "adapter_sku": "rail_petg_40"},
         bases=["hook_plate_steel_4"], pair="nameplate"),
    _row("lamp_shade_e27", "hybrid", "lamp_shade_e27", ["motif", "relief_image", "emboss_text"], ["band"],
         max_text_chars=16, shape_tolerance="constrained", envelope=(120, 220),
         connector={"kind": "thread", "nominal_mm": 40.5, "fit": "slide", "thread": "e27"}, bases=["led_lamp_base_e27"]),
    _row("dash_idol", "hybrid", "dash_idol", ["hero_mesh", "emboss_text", "motif"], ["top", "face"],
         hero_volume=True, max_text_chars=12, hardware=[{"sku": "steel_disc_40", "qty": 1}], heat_safe_only=True,
         shape_tolerance="strict", available=False, envelope=(40, 80),
         connector={"kind": "magnet", "nominal_mm": 40, "fit": "pocket", "mode": "steel_disc", "adapter_sku": "steel_disc_40"},
         bases=["dash_mount_magnetic"]),
    _row("drain_lid", "hybrid", "drain_lid", ["motif", "emboss_text"], ["face"], max_text_chars=12, allowed=["petg_slate"],
         shape_tolerance="strict", envelope=(100, 130), connector={"kind": "rim_clip", "nominal_mm": 118, "fit": "snap", "form": "lid"},
         bases=["soap_box_acrylic_120x80"]),
    _row("bottle_cap_cover", "hybrid", "bottle_cap_cover", ["emboss_text", "motif", "relief_image"], ["top"], max_text_chars=12,
         allowed=["petg_slate"], shape_tolerance="strict", envelope=(40, 60),
         connector={"kind": "rim_clip", "nominal_mm": 42, "fit": "snap", "form": "cap"}, bases=["bottle_cap_class_40"]),
    _row("planter_rim", "hybrid", "planter_rim", ["motif", "emboss_text"], ["band"], max_text_chars=16, allowed=["petg_slate"],
         shape_tolerance="strict", envelope=(80, 140), connector={"kind": "rim_clip", "nominal_mm": 8, "fit": "snap", "form": "rim"},
         bases=["planter_rim_class_8"], pair="planter"),
]

BUILTIN_HARDWARE: list[dict[str, Any]] = [
    {"sku": "split_ring_25", "name": "Steel split ring 25 mm", "unit_cost_paise": 300},
    {"sku": "magnet_d10x3", "name": "Neodymium disc magnet 10 × 3 mm", "unit_cost_paise": 1500},
    {"sku": "cord_200", "name": "Cotton hanging cord 200 mm", "unit_cost_paise": 200},
    {"sku": "led_base_usb", "name": "USB LED puck base 70 mm", "unit_cost_paise": 18000},
    {"sku": "acrylic_4x6", "name": "Acrylic pane 4 × 6 in", "unit_cost_paise": 4000},
    {"sku": "nameplate_screws", "name": "Wall screws and anchors, pair", "unit_cost_paise": 600},
    {"sku": "adhesive_pads", "name": "Foam adhesive pads, pair", "unit_cost_paise": 300},
    {"sku": "dowel_nylon_12x30", "name": "Nylon dowel 12 × 30 mm", "unit_cost_paise": 800},
    {"sku": "rail_petg_40", "name": "PETG dovetail rail 40 mm", "unit_cost_paise": 1200},
    {"sku": "vhb_pad_25x40", "name": "VHB foam pad 25 × 40 mm", "unit_cost_paise": 400},
    {"sku": "steel_disc_40", "name": "Steel disc 40 × 1 mm", "unit_cost_paise": 900},
    {"sku": "insert_brass_1420", "name": "Brass heat-set insert 1/4-20", "unit_cost_paise": 1500},
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
                    "base_items": [dict(b) for b in doc.get("base_items") or []],
                    "families": [dict(f) for f in doc["families"]]}
    return {"source": "builtin", "shelves": [], "hardware_items": [dict(h) for h in BUILTIN_HARDWARE], "base_items": [],
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


def base_items() -> dict[str, dict[str, Any]]:
    """Bought-in bases (Buniyaad) keyed by sku, from the seed (none in the built-in copy)."""
    return {b["sku"]: b for b in load_families_doc().get("base_items") or []}


def connector(family_id: str) -> dict[str, Any] | None:
    """A hybrid family's Kadi as the seed declares it (``connector``), None for the other kinds."""
    return family(family_id).get("connector")


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
    "base_items",
    "connector",
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
