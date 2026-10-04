"""Kadi-M: the magnetic register (plan §1.2, Phase 4).

Two modes, both pockets opening on the Chhaap's underside (the bed):

* ``steel_disc``: one pocket ``nominal_mm`` + ``DISC_CLEARANCE_MM`` across and ``DISC_T_MM`` + ``POCKET_SLACK_MM`` deep for
  a bought-in steel disc (``steel_disc_40``) that holds to the base's magnet (a dashboard puck). The disc is glued in
  flush; the plastic over it is a bridge the disc's width across.
* ``magnets``: ``count`` pockets ``POCKET_D_MM`` × ``POCKET_DEPTH_MM`` (a 10 × 3 mm neodymium disc plus 0.2 mm, the fridge
  magnet's rule) on a circle ``nominal_mm`` across, matching the base's magnet pattern.

The skin over a pocket must stay at least the family's minimum wall (``connector_fit`` measures it by ray casting
from the pocket's floor). Pockets grow by ``xy_hole_comp_mm`` and the material's shrinkage like every hole.
"""

from __future__ import annotations

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

DISC_T_MM = 1.0
DISC_CLEARANCE_MM = 0.4  # diametral, as printed
POCKET_SLACK_MM = 0.2
MAGNET_D_MM = 10.0
MAGNET_T_MM = 3.0
POCKET_D_MM = 10.2
POCKET_DEPTH_MM = 3.2
DEFAULT_COUNT = 2
OVERCUT_MM = 1.0
MIN_GAP_MM = 0.10  # the disc or magnet must still drop in
LOOSE_GAP_MM = 0.80  # beyond this it rattles before the glue sets


def default_depth(connector: "Connector") -> float:
    return DISC_T_MM + POCKET_SLACK_MM if connector.mode == "steel_disc" else POCKET_DEPTH_MM


def descriptor_extras(connector: "Connector") -> dict[str, Any]:
    if connector.mode == "steel_disc":
        return {"clearance_mm": DISC_CLEARANCE_MM}
    return {"clearance_mm": POCKET_D_MM - MAGNET_D_MM}


def label(connector: "Connector") -> str:
    if connector.mode == "steel_disc":
        return f"{connector.nominal_mm:g} mm steel-disc pocket"
    return f"{connector.count or DEFAULT_COUNT} magnet pockets"


@dataclass(frozen=True)
class MagnetDims:
    mode: str
    nominal_mm: float
    depth_mm: float
    pocket_d_model_mm: float
    pocket_d_printed_mm: float
    part_d_mm: float  # the disc or magnet that goes in
    centres_mm: tuple[tuple[float, float], ...]  # pocket centres in the connector's local xy
    comp: Compensation

    @property
    def clearance_printed_mm(self) -> float:
        return self.pocket_d_printed_mm - self.part_d_mm


def dims(connector: "Connector", comp: Compensation) -> MagnetDims:
    shrink = 1.0 + float(comp.shrink_pct) / 100.0
    if connector.mode == "steel_disc":
        printed = float(connector.nominal_mm) + DISC_CLEARANCE_MM
        return MagnetDims("steel_disc", float(connector.nominal_mm), float(connector.depth()), printed * shrink + comp.xy_hole_comp_mm,
                          printed, float(connector.nominal_mm), ((0.0, 0.0),), comp)
    count = int(connector.count or DEFAULT_COUNT)
    r = float(connector.nominal_mm) / 2.0
    centres = tuple((round(r * math.cos(2 * math.pi * k / count), 4), round(r * math.sin(2 * math.pi * k / count), 4)) for k in range(count))
    return MagnetDims("magnets", float(connector.nominal_mm), float(connector.depth()), POCKET_D_MM * shrink + comp.xy_hole_comp_mm,
                      POCKET_D_MM, MAGNET_D_MM, centres, comp)


def tool_part(d: MagnetDims) -> "cad.Part":
    """The pockets in the connector's local frame: mouths on z = 0, running up +z, ``OVERCUT_MM`` below."""
    from ..templates import plates

    pockets = [cad.translate(cad.prism(plates.hole(x, y, d.pocket_d_model_mm), d.depth_mm + OVERCUT_MM), dz=-OVERCUT_MM) for x, y in d.centres_mm]
    return cad.union(pockets)


def tool_mesh(d: MagnetDims, frame: "AnchorFrame") -> trimesh.Trimesh:
    return frame.place(cad.to_trimesh(tool_part(d)))


def report(
    connector: "Connector",
    d: MagnetDims,
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

    centres_world = frame.to_world([[x, y, 0.0] for x, y in d.centres_mm])
    return {
        "kind": connector.kind,
        "fit": connector.fit,
        "mode": d.mode,
        "nominal_mm": d.nominal_mm,
        "depth_mm": d.depth_mm,
        "mouth_mm": [round(float(v), 4) for v in frame.origin],
        "axis": [round(float(v), 6) for v in frame.normal],
        "pocket_d_mm": round(d.pocket_d_model_mm, 4),
        "pocket_depth_mm": d.depth_mm,
        "count": len(d.centres_mm),
        "pocket_centres_mm": [[round(float(v), 4) for v in c] for c in centres_world],
        "pin_d_mm": float(pin_d_mm) if pin_d_mm is not None else d.part_d_mm,
        "pin_tolerance_mm": tolerance_for(tolerance_class, "machined"),
        "tolerance_class": tolerance_class,
        "min_gap_mm": MIN_GAP_MM,
        "loose_gap_mm": LOOSE_GAP_MM,
        "min_wall_mm": comp.connector_min_wall_mm,
        "compensation": comp.descriptor(),
        "material_id": material_id,
        "adapter_sku": connector.adapter_sku,
        "fit_tested": bool(fit_tested),
    }


def expected_printed(d: MagnetDims) -> dict[str, float]:
    return {"pocket_d_mm": round(d.pocket_d_printed_mm, 3), "pocket_depth_mm": round(d.depth_mm, 3), "clearance_mm": round(d.clearance_printed_mm, 3)}


COUPON_SKIN_MM = 1.6


def coupon_part(connector: "Connector", d: MagnetDims, wall_mm: float) -> "cad.Part":
    """A plate covering the pocket pattern with ``wall_mm`` round it and a ``COUPON_SKIN_MM`` skin over it."""
    from ..templates import plates

    reach = max(math.hypot(x, y) for x, y in d.centres_mm) + d.pocket_d_model_mm / 2.0 + wall_mm
    plate = cad.prism(plates.disc(0.0, 0.0, reach), d.depth_mm + max(wall_mm, COUPON_SKIN_MM))
    return cad.cut(plate, [tool_part(d)])


def coupon_frame(d: MagnetDims) -> "AnchorFrame":
    from ..features.frames import AnchorFrame

    return AnchorFrame(np.array([0.0, 0.0, 0.0]), [1, 0, 0], [0, 1, 0], [0, 0, 1])


def measure(mesh: trimesh.Trimesh, frame: "AnchorFrame", d: MagnetDims) -> dict[str, Any]:
    """Read the first pocket back off the mesh: its diameter from the ring where its wall meets its floor."""
    local = frame.to_local(mesh.vertices)
    cx, cy = d.centres_mm[0]
    r = np.hypot(local[:, 0] - cx, local[:, 1] - cy)
    inside = (np.abs(local[:, 2] - d.depth_mm) < 0.05) & (r < d.pocket_d_model_mm / 2.0 + 0.05)
    if not inside.any():
        return {"vertices": 0}
    return {"vertices": int(inside.sum()), "pocket_d_mm": round(2.0 * float(r[inside].max()), 3)}


__all__ = [
    "DISC_CLEARANCE_MM",
    "DISC_T_MM",
    "MAGNET_D_MM",
    "MagnetDims",
    "POCKET_DEPTH_MM",
    "POCKET_D_MM",
    "coupon_frame",
    "coupon_part",
    "default_depth",
    "descriptor_extras",
    "dims",
    "expected_printed",
    "label",
    "measure",
    "report",
    "tool_mesh",
    "tool_part",
]
