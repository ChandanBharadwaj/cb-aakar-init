"""Per-material print compensation for connector features (plan §1.4).

A socket is modelled from its **as-printed** targets: FDM holes print small (segment chords and the extrusion's
squeeze), outer walls print fat, the first layer squishes out (elephant foot) and the part shrinks a little as it
cools. The values live here as defaults per process (PLA, PETG, resin) and may be overridden per material by a
``compensation`` block on the material row (``materials.json``; the ``materials`` table gets the columns in Phase 2).
Every launch finish is PLA. These are proposals until the Phase 1 fit coupons are measured; the coupon loop
(``aakar-geometry coupon``) is how the numbers get corrected.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from typing import Any, Mapping

from ..materials import load_materials

PROCESSES = ("pla", "petg", "resin")


@dataclass(frozen=True)
class Compensation:
    process: str
    xy_hole_comp_mm: float  # holes print small: add this to a modelled hole or socket diameter
    xy_outer_comp_mm: float  # outer walls print fat: add this (negative) to a modelled outer diameter
    elephant_foot_mm: float  # first-layer squish; a socket mouth gets a lead-in chamfer at least this deep
    shrink_pct: float  # linear shrinkage, applied to connector radii only (never to the decorative body)
    max_strain_pct: float  # the most an elastic feature (a snap bead, a clip tab) may be strained
    creep_class: str  # high | medium | low: ``high`` forbids any Kadi held by constant elastic preload
    connector_min_wall_mm: float  # the wall round a socket carries the load: above the family's 1.2 mm

    def descriptor(self) -> dict[str, Any]:
        return {
            "process": self.process,
            "xy_hole_comp_mm": self.xy_hole_comp_mm,
            "xy_outer_comp_mm": self.xy_outer_comp_mm,
            "elephant_foot_mm": self.elephant_foot_mm,
            "shrink_pct": self.shrink_pct,
            "max_strain_pct": self.max_strain_pct,
            "creep_class": self.creep_class,
            "connector_min_wall_mm": self.connector_min_wall_mm,
        }


FIELDS = tuple(k for k in Compensation.__dataclass_fields__ if k != "process")

DEFAULTS: dict[str, Compensation] = {
    "pla": Compensation("pla", 0.20, -0.10, 0.30, 0.3, 1.0, "high", 1.6),
    "petg": Compensation("petg", 0.25, -0.15, 0.35, 0.6, 3.0, "medium", 1.6),
    "resin": Compensation("resin", 0.05, 0.0, 0.0, 0.3, 2.0, "low", 1.0),
}


def process_of(material: Mapping[str, Any] | None) -> str:
    """``pla`` | ``petg`` | ``resin`` from a material row's ``process`` or ``filament`` text (PLA when unsure)."""
    if not material:
        return "pla"
    explicit = str(material.get("process") or "").lower()
    if explicit in PROCESSES:
        return explicit
    text = " ".join(str(material.get(k) or "") for k in ("filament", "name", "id")).lower()
    if "petg" in text:
        return "petg"
    if "resin" in text or "sla" in text.split():
        return "resin"
    return "pla"


def for_material(material_id: str | None) -> Compensation:
    """The compensation a connector is modelled with for ``material_id`` (PLA defaults for an unknown or missing one)."""
    row = next((m for m in load_materials() if m.get("id") == material_id), None) if material_id else None
    base = DEFAULTS[process_of(row)]
    override = (row or {}).get("compensation") or {}
    kwargs = {k: (str(override[k]) if k == "creep_class" else float(override[k])) for k in FIELDS if k in override}
    return replace(base, **kwargs) if kwargs else base


__all__ = ["DEFAULTS", "FIELDS", "PROCESSES", "Compensation", "for_material", "process_of"]
