"""Template registry. Every template in this package registers itself here."""

from __future__ import annotations

import re

from ..errors import UnknownTemplate
from ..families import UnknownFamily, family_ids
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints
from .bottle_cap_cover import BottleCapCover
from .dash_idol import DashIdol
from .desk_nameplate import DeskNameplate
from .drain_lid import DrainLid
from .fridge_magnet import FridgeMagnet
from .hanging_ornament import HangingOrnament
from .headphone_topper import HeadphoneTopper
from .hook_plaque import HookPlaque
from .jharokha_phone_stand import JharokhaPhoneStand
from .keycap_mx import KeycapMx
from .keychain_tag import KeychainTag
from .lamp_shade_e27 import LampShadeE27
from .lithophane_plate import LithophanePlate
from .pet_tag import PetTag
from .photo_frame_std import PhotoFrameStd
from .pillar_headphone_stand import PillarHeadphoneStand
from .planter_rim import PlanterRim
from .plinth_round import PlinthRound
from .raw_print import RawPrint

_REF_RE = re.compile(r"^([a-z][a-z0-9_]*)@([0-9]+)$")

REGISTRY: dict[tuple[str, int], type[Template]] = {}


def register(template: type[Template]) -> type[Template]:
    """Add a template to the registry. Its ``family`` must be an id in families.json (plan §1)."""
    known = family_ids()
    if template.family not in known:
        raise UnknownFamily(
            f"Template {template.id}@{template.version} declares family {template.family!r}, "
            f"which is not in families.json (known: {', '.join(known)})"
        )
    REGISTRY[(template.id, template.version)] = template
    return template


register(JharokhaPhoneStand)
register(KeychainTag)
register(FridgeMagnet)
register(HangingOrnament)
register(DeskNameplate)
register(RawPrint)
# PR 8: the keepsakes (Roshni, Pratima, Saathi Pet) and the second wave (Chaukhat, Kunji)
register(LithophanePlate)
register(PlinthRound)
register(PetTag)
register(PhotoFrameStd)
register(KeycapMx)
# Hybrid products (Jod): a printed Chhaap on a bought-in base through a Kadi (docs/research/hybrid-products)
register(HeadphoneTopper)  # Kadi-S socket
register(PillarHeadphoneStand)  # the fully printed sibling the compare card pairs it with
register(HookPlaque)  # Kadi-D dovetail
register(LampShadeE27)  # Kadi-T E27 collar
register(DashIdol)  # Kadi-M steel disc
register(DrainLid)  # Kadi-C lid
register(BottleCapCover)  # Kadi-C cap
register(PlanterRim)  # Kadi-C rim


def list_templates() -> list[type[Template]]:
    return [REGISTRY[k] for k in sorted(REGISTRY)]


def parse_ref(ref: str) -> tuple[str, int]:
    m = _REF_RE.match(ref or "")
    if not m:
        raise UnknownTemplate(f"Template reference must look like id@version, got {ref!r}", {"template": ref})
    return m.group(1), int(m.group(2))


def get_template(ref: str) -> type[Template]:
    template_id, version = parse_ref(ref)
    try:
        return REGISTRY[(template_id, version)]
    except KeyError:
        known = [f"{i}@{v}" for i, v in sorted(REGISTRY)]
        raise UnknownTemplate(f"Unknown template {template_id}@{version}", {"template": ref, "known": known}) from None


def latest_by_id(template_id: str) -> type[Template] | None:
    versions = [t for (i, _v), t in REGISTRY.items() if i == template_id]
    return max(versions, key=lambda t: t.version) if versions else None


__all__ = [
    "Anchor",
    "BottleCapCover",
    "DashIdol",
    "DeskNameplate",
    "DrainLid",
    "FridgeMagnet",
    "HangingOrnament",
    "HardwareRef",
    "HeadphoneTopper",
    "HookPlaque",
    "JharokhaPhoneStand",
    "KeycapMx",
    "KeychainTag",
    "LampShadeE27",
    "LithophanePlate",
    "Param",
    "PetTag",
    "PhotoFrameStd",
    "PillarHeadphoneStand",
    "PlanterRim",
    "PlinthRound",
    "REGISTRY",
    "RawPrint",
    "Template",
    "TemplateConstraints",
    "get_template",
    "latest_by_id",
    "list_templates",
    "parse_ref",
    "register",
]
