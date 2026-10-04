"""Kadi-T: a female thread, or the collar an E27 / E14 lampholder's shade ring clamps (plan §1.2, Phase 4).

Two forms share the module:

* **thread** (``unc_1_4``, the camera-tripod 1/4-20; ``m10x1``, the lamp nipple): a 60° thread cut as a helical sweep
  of the trapezoid profile along a ``build123d.Helix``, ``RADIAL_CLEARANCE_MM`` wider than the standard so a printed
  internal thread turns onto a machined male one, at least ``MIN_TURNS`` deep. When the kernel refuses the sweep at a
  size, the tool falls back to a plain bore for a heat-set brass insert (``insert_brass_1420`` for 1/4-20) and the
  report says so (``thread_form: insert``).
* **collar** (``e27`` 40.5 mm, ``e14`` 28.9 mm): no thread at all. The shade carries a flat lip ``LIP_MM`` thick with a
  round hole the holder's neck passes; the holder's own shade ring screws down and clamps the lip. The hole clears
  the neck by ``COLLAR_CLEARANCE_MM``.

Printed with the mouth on the bed (a shade prints collar down). Holes grow by ``xy_hole_comp_mm`` and the material's
shrinkage like every inward-facing wall.
"""

from __future__ import annotations

import logging
import math
from dataclasses import dataclass
from typing import TYPE_CHECKING, Any

import numpy as np
import trimesh

from .. import cad
from .compensation import Compensation

if TYPE_CHECKING:  # pragma: no cover
    from ..features.frames import AnchorFrame
    from .base import Connector

log = logging.getLogger("aakar.geometry.connectors.thread")

# standard: (major diameter, pitch)
STANDARDS: dict[str, tuple[float, float]] = {"unc_1_4": (6.35, 1.27), "m10x1": (10.0, 1.0)}
# collar: the hole a lampholder's neck passes (its shade-ring thread's major diameter)
COLLARS: dict[str, float] = {"e27": 40.5, "e14": 28.9}
INSERT_SKU: dict[str, str] = {"unc_1_4": "insert_brass_1420"}
INSERT_BORE_MM: dict[str, float] = {"unc_1_4": 7.2, "m10x1": 9.1}  # heat-set insert bores; m10 has no insert, a plain tap bore
RADIAL_CLEARANCE_MM = 0.30
THREAD_DEPTH_FACTOR = 0.5413  # 60° thread: the female thread's radial depth = 0.5413 × pitch (5/8 H)
MIN_TURNS = 4
COLLAR_CLEARANCE_MM = 0.5  # diametral
LIP_MM = 3.0  # the clamped lip
LEAD_IN_MM = 0.5
OVERCUT_MM = 1.0
MIN_COLLAR_GAP_MM = 0.10
LOOSE_COLLAR_GAP_MM = 1.50


def standard_for(nominal_mm: float) -> str | None:
    """The standard whose size matches ``nominal_mm`` (so ``Connector("thread", 40.5)`` means the E27 collar)."""
    for key, value in COLLARS.items():
        if abs(value - float(nominal_mm)) < 1e-6:
            return key
    for key, (major, _pitch) in STANDARDS.items():
        if abs(major - float(nominal_mm)) < 1e-6:
            return key
    return None


def is_collar(connector: "Connector") -> bool:
    return connector.thread in COLLARS


def default_depth(connector: "Connector") -> float:
    if is_collar(connector):
        return LIP_MM
    _major, pitch = STANDARDS[connector.thread]
    return max(MIN_TURNS * pitch, 6.0)


def descriptor_extras(connector: "Connector") -> dict[str, Any]:
    if is_collar(connector):
        return {"clearance_mm": COLLAR_CLEARANCE_MM}
    return {"clearance_mm": 2.0 * RADIAL_CLEARANCE_MM}


def label(connector: "Connector") -> str:
    if is_collar(connector):
        return f"{connector.thread.upper()} collar"
    return {"unc_1_4": "1/4-20 thread", "m10x1": "M10 × 1 thread"}[connector.thread]


@dataclass(frozen=True)
class ThreadDims:
    standard: str
    collar: bool
    nominal_mm: float
    depth_mm: float
    hole_d_model_mm: float  # collar hole, or the female thread's minor diameter
    hole_d_printed_mm: float
    major_d_model_mm: float | None  # the female thread's root diameter (None for a collar)
    major_d_printed_mm: float | None
    pitch_mm: float | None
    turns: float
    lip_mm: float
    chamfer_mm: float
    comp: Compensation

    @property
    def clearance_printed_mm(self) -> float:
        """Collar: hole − neck (diametral). Thread: minor − the male thread's minor at nominal (radial × 2)."""
        if self.collar:
            return self.hole_d_printed_mm - self.nominal_mm
        major, pitch = STANDARDS[self.standard]
        male_minor = major - 2.0 * THREAD_DEPTH_FACTOR * pitch
        return self.hole_d_printed_mm - male_minor


def dims(connector: "Connector", comp: Compensation) -> ThreadDims:
    shrink = 1.0 + float(comp.shrink_pct) / 100.0
    depth = float(connector.depth())
    chamfer = max(LEAD_IN_MM, float(comp.elephant_foot_mm))
    if is_collar(connector):
        hole_printed = COLLARS[connector.thread] + COLLAR_CLEARANCE_MM
        return ThreadDims(connector.thread, True, float(connector.nominal_mm), depth, hole_printed * shrink + comp.xy_hole_comp_mm,
                          hole_printed, None, None, None, 0.0, depth, chamfer, comp)
    major, pitch = STANDARDS[connector.thread]
    minor_printed = major - 2.0 * THREAD_DEPTH_FACTOR * pitch + 2.0 * RADIAL_CLEARANCE_MM
    major_printed = major + 2.0 * RADIAL_CLEARANCE_MM
    return ThreadDims(
        connector.thread, False, float(connector.nominal_mm), depth,
        minor_printed * shrink + comp.xy_hole_comp_mm, minor_printed,
        major_printed * shrink + comp.xy_hole_comp_mm, major_printed,
        pitch, depth / pitch, 0.0, chamfer, comp,
    )


def _collar_tool(d: ThreadDims) -> "cad.Part":
    from ..templates import plates

    hole = cad.translate(cad.prism(plates.hole(0.0, 0.0, d.hole_d_model_mm), d.depth_mm + 2.0 * OVERCUT_MM), dz=-OVERCUT_MM)
    r = d.hole_d_model_mm / 2.0
    lead_in = cad.translate(cad.frustum(r + d.chamfer_mm + OVERCUT_MM, r, d.chamfer_mm + OVERCUT_MM), dz=-OVERCUT_MM)
    return cad.union([hole, lead_in])


def _helical_tool(d: ThreadDims) -> "cad.Part":
    """Bore at the minor diameter plus the helical groove out to the root: what the body loses to a female thread."""
    from build123d import Helix, Plane, Polyline, make_face, sweep

    assert d.pitch_mm and d.major_d_model_mm
    r_minor = d.hole_d_model_mm / 2.0
    r_major = d.major_d_model_mm / 2.0
    depth = r_major - r_minor
    pitch = d.pitch_mm
    height = d.depth_mm + 2.0 * pitch
    helix = Helix(pitch=pitch, height=height, radius=r_minor - 0.05)
    start = helix @ 0
    plane = Plane(origin=start, z_dir=helix % 0, x_dir=(start.X, start.Y, 0))
    half_root = 0.125 * pitch  # the flat at the root of a 60° thread
    half_crest = half_root + depth * math.tan(math.radians(30.0))
    groove = make_face(Polyline((-half_crest, -0.05), (half_crest, -0.05), (half_root, depth + 0.05), (-half_root, depth + 0.05), close=True))
    ridge = sweep(plane * groove, path=helix, is_frenet=True)
    ridge = ridge.moved(cad.Location((0.0, 0.0, -pitch)))
    bore = cad.cylinder(r_minor, d.depth_mm + OVERCUT_MM, z0=-OVERCUT_MM)
    lead_in = cad.translate(cad.frustum(r_major + d.chamfer_mm + OVERCUT_MM, r_minor, d.chamfer_mm + OVERCUT_MM), dz=-OVERCUT_MM)
    # the groove above the depth would cut into the floor: keep only the part inside the bore's height
    keep = cad.cylinder(r_major + 1.0, d.depth_mm + OVERCUT_MM, z0=-OVERCUT_MM)
    tool = cad.union([bore, lead_in, cad.intersect(ridge, keep)])
    if not tool.is_valid:
        raise RuntimeError("helical tool is not a valid solid")
    return tool


def _insert_tool(d: ThreadDims) -> "cad.Part":
    from ..templates import plates

    bore_d = INSERT_BORE_MM[d.standard] * (1.0 + d.comp.shrink_pct / 100.0) + d.comp.xy_hole_comp_mm
    hole = cad.translate(cad.prism(plates.hole(0.0, 0.0, bore_d), d.depth_mm + OVERCUT_MM), dz=-OVERCUT_MM)
    lead_in = cad.translate(cad.frustum(bore_d / 2.0 + d.chamfer_mm + OVERCUT_MM, bore_d / 2.0, d.chamfer_mm + OVERCUT_MM), dz=-OVERCUT_MM)
    return cad.union([hole, lead_in])


_FALLBACK: dict[str, bool] = {}


def tool_part(d: ThreadDims) -> "cad.Part":
    """The tool in the thread's local frame: mouth on z = 0, running up +z, ``OVERCUT_MM`` below."""
    if d.collar:
        return _collar_tool(d)
    if not _FALLBACK.get(d.standard):
        try:
            return _helical_tool(d)
        except Exception as exc:  # the kernel refused this size: a plain bore for an insert from now on
            log.warning("helical thread %s failed (%s); falling back to an insert bore", d.standard, exc)
            _FALLBACK[d.standard] = True
    return _insert_tool(d)


def thread_form(d: ThreadDims) -> str:
    if d.collar:
        return "collar"
    return "insert" if _FALLBACK.get(d.standard) else "thread"


def tool_mesh(d: ThreadDims, frame: "AnchorFrame") -> trimesh.Trimesh:
    return frame.place(cad.to_trimesh(tool_part(d)))


def report(
    connector: "Connector",
    d: ThreadDims,
    frame: "AnchorFrame",
    comp: Compensation,
    material_id: str | None,
    *,
    tolerance_class: str = "machined",
    pin_d_mm: float | None = None,
    fit_tested: bool = False,
    **_: Any,
) -> dict[str, Any]:
    from .base import tolerance_for

    form = thread_form(d)
    out: dict[str, Any] = {
        "kind": connector.kind,
        "fit": connector.fit,
        "thread": d.standard,
        "thread_form": form,
        "nominal_mm": d.nominal_mm,
        "depth_mm": d.depth_mm,
        "mouth_mm": [round(float(v), 4) for v in frame.origin],
        "axis": [round(float(v), 6) for v in frame.normal],
        "hole_d_mm": round(d.hole_d_model_mm, 4),
        "lip_mm": d.lip_mm if d.collar else None,
        "pitch_mm": d.pitch_mm,
        "turns": round(d.turns, 2),
        "min_turns": MIN_TURNS,
        "chamfer_mm": d.chamfer_mm,
        "pin_d_mm": float(pin_d_mm) if pin_d_mm is not None else d.nominal_mm,
        "pin_tolerance_mm": tolerance_for(tolerance_class, "machined"),
        "tolerance_class": tolerance_class,
        "min_gap_mm": MIN_COLLAR_GAP_MM,
        "loose_gap_mm": LOOSE_COLLAR_GAP_MM,
        "min_wall_mm": comp.connector_min_wall_mm,
        "compensation": comp.descriptor(),
        "material_id": material_id,
        "adapter_sku": connector.adapter_sku or (INSERT_SKU.get(d.standard) if form == "insert" else None),
        "fit_tested": bool(fit_tested),
    }
    if d.major_d_model_mm is not None:
        out["major_d_mm"] = round(d.major_d_model_mm, 4)
    return {k: v for k, v in out.items() if v is not None or k in ("material_id", "adapter_sku")}


def expected_printed(d: ThreadDims) -> dict[str, float]:
    out = {"hole_d_mm": round(d.hole_d_printed_mm, 3), "clearance_mm": round(d.clearance_printed_mm, 3), "depth_mm": round(d.depth_mm, 3)}
    if d.collar:
        out["lip_mm"] = d.lip_mm
    else:
        out["major_d_mm"] = round(float(d.major_d_printed_mm or 0.0), 3)
        out["turns"] = round(d.turns, 2)
    return out


def coupon_part(connector: "Connector", d: ThreadDims, wall_mm: float) -> "cad.Part":
    """A collar coupon is the lip itself (a washer ``wall_mm`` wide); a thread coupon a block the thread's depth plus a floor."""
    from ..templates import plates

    outer = (d.major_d_model_mm or d.hole_d_model_mm) + 2.0 * wall_mm
    if d.collar:
        body = cad.prism(plates.disc(0.0, 0.0, outer / 2.0), d.depth_mm)
    else:
        body = cad.prism(plates.disc(0.0, 0.0, outer / 2.0), d.depth_mm + max(wall_mm, 3.0))
    return cad.cut(body, [tool_part(d)])


def coupon_frame(d: ThreadDims) -> "AnchorFrame":
    from ..features.frames import AnchorFrame

    return AnchorFrame(np.array([0.0, 0.0, 0.0]), [1, 0, 0], [0, 1, 0], [0, 0, 1])


def measure(mesh: trimesh.Trimesh, frame: "AnchorFrame", d: ThreadDims) -> dict[str, Any]:
    """Read the hole back off the mesh: the smallest radius on the mouth-side wall (minor or collar) and the largest."""
    local = frame.to_local(mesh.vertices)
    z = local[:, 2]
    r = np.hypot(local[:, 0], local[:, 1])
    limit = (d.major_d_model_mm or d.hole_d_model_mm) / 2.0 + 0.1
    inside = (z > d.chamfer_mm + 0.05) & (z < d.depth_mm + 0.05) & (r < limit)
    if not inside.any():
        return {"vertices": 0}
    rs = r[inside]
    return {"vertices": int(inside.sum()), "hole_d_mm": round(2.0 * float(rs.min()), 3), "root_d_mm": round(2.0 * float(rs.max()), 3)}


__all__ = [
    "COLLARS",
    "COLLAR_CLEARANCE_MM",
    "INSERT_SKU",
    "LIP_MM",
    "MIN_TURNS",
    "RADIAL_CLEARANCE_MM",
    "STANDARDS",
    "ThreadDims",
    "coupon_frame",
    "coupon_part",
    "default_depth",
    "descriptor_extras",
    "dims",
    "expected_printed",
    "is_collar",
    "label",
    "measure",
    "report",
    "standard_for",
    "thread_form",
    "tool_mesh",
    "tool_part",
]
