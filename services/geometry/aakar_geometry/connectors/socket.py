"""Kadi-S: the ribbed press socket (plan §1.2).

A blind bore in the Chhaap's flange that a pin on the base (a tube top, a stud) or a bought-in nylon dowel presses
into. Three **crush ribs**, 120° apart, run the length of the bore and stand a little inside the pin's radius: they
yield on first insertion and take up the pin's variation, so the pin never bears on the socket wall and the wall
never has to stretch (PLA creeps under constant strain; a rib that has crushed is just a tighter fit). A 45° lead-in
chamfer at the mouth swallows the elephant foot and guides the pin.

Modelled from as-printed targets (``compensation``): the bore and the rib crests face inward like a hole wall, so
both grow by ``xy_hole_comp_mm`` and by the material's shrinkage. All dimensions mm. Numbers are proposals until
the fit coupons (``aakar-geometry coupon``) are measured.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import TYPE_CHECKING, Any

import numpy as np
import trimesh
from shapely.geometry import Polygon
from shapely.ops import unary_union

from .. import cad
from .compensation import Compensation

if TYPE_CHECKING:  # pragma: no cover
    from ..features.frames import AnchorFrame
    from .base import Connector

NOMINALS_MM = (8.0, 12.0, 16.0)  # Ø 8 for a palm-sized Chhaap (≤ 40 g), Ø 12 the default (≤ 150 g), Ø 16 for lamps
DEPTH_BY_NOMINAL = {8.0: 12.0, 12.0: 15.0, 16.0: 20.0}
CLEARANCE_MM = 0.30  # diametral, as printed: the bore wall clears the pin by this much
RIB_COUNT = 3
RIB_INTERFERENCE_MM = 0.25  # radial, as printed: each rib's crest stands this far inside the pin's radius
RIB_BASE_MM = 1.2  # a rib's width where it meets the bore wall (three extrusion widths)
RIB_CREST_MM = 0.4  # ... and at its crest (one extrusion width)
ENTRY_CHAMFER_MM = 0.5  # the 45° lead-in at the mouth
OVERCUT_MM = 1.0  # the tool starts this far outside the body so the mouth opens cleanly
MIN_GRIP_MM = 0.05  # the smallest radial bite per rib that still holds
PIN_TOLERANCE_MM = {"machined": 0.10, "molded": 0.15, "wood": 0.50}  # ± on the pin's diameter by base tolerance class


def default_depth(nominal_mm: float) -> float:
    return DEPTH_BY_NOMINAL.get(float(nominal_mm), round(1.25 * float(nominal_mm), 1))


@dataclass(frozen=True)
class SocketDims:
    """One socket at one material: what is modelled and what it is expected to print as."""

    nominal_mm: float
    depth_mm: float
    bore_d_model_mm: float
    crest_d_model_mm: float
    bore_d_printed_mm: float
    crest_d_printed_mm: float
    chamfer_mm: float
    comp: Compensation

    @property
    def rib_height_model_mm(self) -> float:
        return (self.bore_d_model_mm - self.crest_d_model_mm) / 2.0

    @property
    def rib_height_printed_mm(self) -> float:
        return (self.bore_d_printed_mm - self.crest_d_printed_mm) / 2.0

    @property
    def clearance_printed_mm(self) -> float:
        return self.bore_d_printed_mm - self.nominal_mm

    @property
    def interference_printed_mm(self) -> float:
        """Radial bite of each rib on a nominal pin."""
        return (self.nominal_mm - self.crest_d_printed_mm) / 2.0

    @property
    def crush_fraction(self) -> float:
        """How much of each rib a nominal pin crushes (1 = flat: the pin would bear on the wall)."""
        return self.interference_printed_mm / self.rib_height_printed_mm if self.rib_height_printed_mm > 1e-9 else math.inf


def dims(connector: "Connector", comp: Compensation) -> SocketDims:
    nominal = float(connector.nominal_mm)
    shrink = 1.0 + float(comp.shrink_pct) / 100.0
    bore_printed = nominal + CLEARANCE_MM
    crest_printed = nominal - 2.0 * RIB_INTERFERENCE_MM
    return SocketDims(
        nominal_mm=nominal,
        depth_mm=float(connector.depth()),
        bore_d_model_mm=bore_printed * shrink + float(comp.xy_hole_comp_mm),
        crest_d_model_mm=crest_printed * shrink + float(comp.xy_hole_comp_mm),
        bore_d_printed_mm=bore_printed,
        crest_d_printed_mm=crest_printed,
        chamfer_mm=max(ENTRY_CHAMFER_MM, float(comp.elephant_foot_mm)),
        comp=comp,
    )


def rib_angles_deg() -> list[float]:
    return [90.0 + k * 360.0 / RIB_COUNT for k in range(RIB_COUNT)]


def bore_polygon(d: SocketDims) -> Polygon:
    """The bore's cross-section (the material removed): a circumscribed circle minus the three ribs left standing."""
    from ..templates import plates  # the templates package imports connectors first; keep this import lazy

    r_bore = d.bore_d_model_mm / 2.0
    r_crest = d.crest_d_model_mm / 2.0
    rib_h = max(r_bore - r_crest, 1e-6)
    r_out = r_bore + OVERCUT_MM  # the rib's root reaches into the wall so nothing is cut behind it
    w_out = RIB_BASE_MM + (RIB_BASE_MM - RIB_CREST_MM) * (OVERCUT_MM / rib_h)
    ribs = []
    for angle in rib_angles_deg():
        theta = math.radians(angle)
        along = np.array([math.cos(theta), math.sin(theta)])
        across = np.array([-math.sin(theta), math.cos(theta)])
        pts = [
            r_crest * along - (RIB_CREST_MM / 2.0) * across,
            r_crest * along + (RIB_CREST_MM / 2.0) * across,
            r_out * along + (w_out / 2.0) * across,
            r_out * along - (w_out / 2.0) * across,
        ]
        ribs.append(Polygon([(float(p[0]), float(p[1])) for p in pts]))
    return plates.largest_polygon(plates.hole(0.0, 0.0, d.bore_d_model_mm).difference(unary_union(ribs)))


def tool_part(d: SocketDims) -> "cad.Part":
    """The cutting tool in the socket's local frame: mouth on z = 0, the bore running up +z, ``OVERCUT_MM`` below."""
    bore = cad.translate(cad.prism(bore_polygon(d), d.depth_mm + OVERCUT_MM), dz=-OVERCUT_MM)
    r_bore = d.bore_d_model_mm / 2.0
    chamfer = cad.translate(cad.frustum(r_bore + d.chamfer_mm + OVERCUT_MM, r_bore, d.chamfer_mm + OVERCUT_MM), dz=-OVERCUT_MM)
    return cad.union([bore, chamfer])


def tool_mesh(d: SocketDims, frame: "AnchorFrame") -> trimesh.Trimesh:
    """The tool placed in world coordinates (``frame.origin`` the mouth, ``frame.normal`` into the body)."""
    return frame.place(cad.to_trimesh(tool_part(d)))


def report(
    connector: "Connector",
    d: SocketDims,
    frame: "AnchorFrame",
    comp: Compensation,
    material_id: str | None,
    *,
    tolerance_class: str = "machined",
    pin_d_mm: float | None = None,
    fit_tested: bool = False,
) -> dict[str, Any]:
    """The ``connector`` block the inspect service's ``connector_fit`` check reads (modelled numbers, never as-printed:
    inspect works the fit out itself from the compensation)."""
    return {
        "kind": connector.kind,
        "fit": connector.fit,
        "nominal_mm": d.nominal_mm,
        "depth_mm": d.depth_mm,
        "mouth_mm": [round(float(v), 4) for v in frame.origin],
        "axis": [round(float(v), 6) for v in frame.normal],
        "bore_d_mm": round(d.bore_d_model_mm, 4),
        "crest_d_mm": round(d.crest_d_model_mm, 4),
        "rib_count": RIB_COUNT,
        "rib_base_mm": RIB_BASE_MM,
        "chamfer_mm": d.chamfer_mm,
        "pin_d_mm": float(pin_d_mm) if pin_d_mm is not None else d.nominal_mm,
        "pin_tolerance_mm": PIN_TOLERANCE_MM.get(tolerance_class, PIN_TOLERANCE_MM["machined"]),
        "tolerance_class": tolerance_class,
        "min_grip_mm": MIN_GRIP_MM,
        "min_wall_mm": comp.connector_min_wall_mm,
        "compensation": comp.descriptor(),
        "material_id": material_id,
        "adapter_sku": connector.adapter_sku,
        "fit_tested": bool(fit_tested),
    }


def measure(mesh: trimesh.Trimesh, frame: "AnchorFrame", d: SocketDims) -> dict[str, Any]:
    """Read the socket back off a built mesh: bore and crest diameters as modelled and the rib angles, from the ring of
    vertices where the bore's walls meet its floor (straight walls carry no vertices between their ends; the floor's
    outline is the bore polygon itself). What the coupon CLI prints."""
    local = frame.to_local(mesh.vertices)
    z = local[:, 2]
    r = np.hypot(local[:, 0], local[:, 1])
    inside = (np.abs(z - d.depth_mm) < 0.05) & (r < d.bore_d_model_mm / 2.0 + 0.05)
    if not inside.any():
        return {"vertices": 0}
    rs = r[inside]
    angles = np.degrees(np.arctan2(local[inside, 1], local[inside, 0])) % 360.0
    crest = angles[rs < rs.min() + 0.02]
    groups: list[list[float]] = []
    for a in sorted(float(v) for v in crest):
        if groups and a - groups[-1][-1] < 5.0:
            groups[-1].append(a)
        else:
            groups.append([a])
    if len(groups) > 1 and groups[-1][-1] - groups[0][0] > 355.0:  # a cluster straddling 0°
        groups[0] = [a - 360.0 for a in groups.pop()] + groups[0]
    rib_angles = [round(float(np.mean(g)) % 360.0, 2) for g in groups]
    spacing = [round((b - a) % 360.0, 2) for a, b in zip(rib_angles, rib_angles[1:] + rib_angles[:1])] if len(rib_angles) > 1 else []
    return {
        "vertices": int(inside.sum()),
        "bore_d_mm": round(2.0 * float(rs.max()), 3),
        "crest_d_mm": round(2.0 * float(rs.min()), 3),
        "rib_angles_deg": rib_angles,
        "rib_spacing_deg": spacing,
    }


__all__ = [
    "CLEARANCE_MM",
    "DEPTH_BY_NOMINAL",
    "ENTRY_CHAMFER_MM",
    "MIN_GRIP_MM",
    "NOMINALS_MM",
    "PIN_TOLERANCE_MM",
    "RIB_BASE_MM",
    "RIB_COUNT",
    "RIB_CREST_MM",
    "RIB_INTERFERENCE_MM",
    "SocketDims",
    "bore_polygon",
    "default_depth",
    "dims",
    "measure",
    "report",
    "rib_angles_deg",
    "tool_mesh",
    "tool_part",
]
