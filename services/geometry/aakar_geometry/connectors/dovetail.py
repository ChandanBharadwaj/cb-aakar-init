"""Kadi-D: the dovetail channel (plan §1.2, Phase 4).

A female dovetail channel cut into a flat face of the Chhaap; a bought-in PETG rail (``rail_petg_40``) bonded to the
base with a VHB pad slides into it from one end and a small **detent ridge** on the channel floor clicks it in place
at the centre. The rail is ``nominal_mm`` wide at its neck and ``HEAD_OVER_NECK_MM`` wider at its head,
``RAIL_HEIGHT_MM`` tall; the channel clears each flank by ``FLANK_CLEARANCE_MM`` and the head by
``DEPTH_CLEARANCE_MM``, so the slide is free and the hold comes from the detent and the dovetail's own wedge.

Printed with the channel's mouth on the bed (the plaque lies face up): the channel narrows towards the bed, so each
layer sits inside the one below and nothing overhangs; the floor is a bridge the rail's head width across. A 45°
lead-in at the mouth swallows the elephant foot. Widths are modelled from as-printed targets (``compensation``): the
flanks face inward like a hole wall, so they grow by ``xy_hole_comp_mm`` and the material's shrinkage.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING, Any

import numpy as np
import trimesh
from shapely.geometry import Polygon

from .. import cad
from .compensation import Compensation

if TYPE_CHECKING:  # pragma: no cover
    from ..features.frames import AnchorFrame
    from .base import Connector

HEAD_OVER_NECK_MM = 3.0  # the rail's head is this much wider than its neck (12 → 15)
RAIL_HEIGHT_MM = 4.0
FLANK_CLEARANCE_MM = 0.20  # per flank, as printed
DEPTH_CLEARANCE_MM = 0.15
LEAD_IN_MM = 0.5  # the 45° lead-in at the mouth, at least the elephant foot
DETENT_MM = 0.4  # the ridge on the channel floor the rail clicks over
DETENT_W_MM = 2.0
OVERCUT_MM = 1.0
DEFAULT_LENGTH_MM = 50.0  # when neither the frame nor the connector says how long the channel is
MIN_FLANK_GAP_MM = 0.05  # the rail must still enter the narrowest print
LOOSE_FLANK_GAP_MM = 0.60  # beyond this the rail rattles
RAIL_LENGTH_MM = 40.0  # the bought-in rail


def default_depth(connector: "Connector") -> float:
    return RAIL_HEIGHT_MM + DEPTH_CLEARANCE_MM


def descriptor_extras(connector: "Connector") -> dict[str, Any]:
    return {"clearance_mm": 2.0 * FLANK_CLEARANCE_MM}


def label(connector: "Connector") -> str:
    return f"{connector.nominal_mm:g} mm Kadi rail channel"


@dataclass(frozen=True)
class DovetailDims:
    nominal_mm: float
    depth_mm: float
    mouth_w_model_mm: float
    floor_w_model_mm: float
    mouth_w_printed_mm: float
    floor_w_printed_mm: float
    chamfer_mm: float
    length_mm: float
    comp: Compensation

    @property
    def rail_neck_mm(self) -> float:
        return self.nominal_mm

    @property
    def rail_head_mm(self) -> float:
        return self.nominal_mm + HEAD_OVER_NECK_MM

    @property
    def flank_clearance_printed_mm(self) -> float:
        return (self.mouth_w_printed_mm - self.rail_neck_mm) / 2.0

    @property
    def floor_clearance_printed_mm(self) -> float:
        return (self.floor_w_printed_mm - self.rail_head_mm) / 2.0


def dims(connector: "Connector", comp: Compensation) -> DovetailDims:
    nominal = float(connector.nominal_mm)
    shrink = 1.0 + float(comp.shrink_pct) / 100.0
    mouth_printed = nominal + 2.0 * FLANK_CLEARANCE_MM
    floor_printed = nominal + HEAD_OVER_NECK_MM + 2.0 * FLANK_CLEARANCE_MM
    return DovetailDims(
        nominal_mm=nominal,
        depth_mm=float(connector.depth()),
        mouth_w_model_mm=mouth_printed * shrink + float(comp.xy_hole_comp_mm),
        floor_w_model_mm=floor_printed * shrink + float(comp.xy_hole_comp_mm),
        mouth_w_printed_mm=mouth_printed,
        floor_w_printed_mm=floor_printed,
        chamfer_mm=max(LEAD_IN_MM, float(comp.elephant_foot_mm)),
        length_mm=float(connector.length_mm) if connector.length_mm else DEFAULT_LENGTH_MM,
        comp=comp,
    )


def channel_profile(d: DovetailDims) -> Polygon:
    """The channel's cross-section in (across, depth): the lead-in at the mouth, the flanks widening to the floor."""
    mw, fw, depth, ch = d.mouth_w_model_mm, d.floor_w_model_mm, d.depth_mm, d.chamfer_mm

    def w(z: float) -> float:
        return mw + (fw - mw) * z / depth

    return Polygon([
        (-(mw / 2.0 + ch + OVERCUT_MM), -OVERCUT_MM),
        (mw / 2.0 + ch + OVERCUT_MM, -OVERCUT_MM),
        (mw / 2.0 + ch, 0.0),
        (w(ch) / 2.0, ch),
        (fw / 2.0, depth),
        (-fw / 2.0, depth),
        (-w(ch) / 2.0, ch),
        (-(mw / 2.0 + ch), 0.0),
    ])


def tool_part(d: DovetailDims, length_mm: float | None = None) -> "cad.Part":
    """The cutting tool in the channel's local frame: mouth on z = 0, depth up +z, the channel running along y, open at both
    ends (``OVERCUT_MM`` beyond ``length_mm``); the detent ridge is left standing on the floor at y = 0."""
    L = float(length_mm or d.length_mm) + 2.0 * OVERCUT_MM
    channel = cad.translate(cad.rotate_x(cad.prism(channel_profile(d), L), 90.0), dy=L / 2.0)
    ridge_profile = Polygon([
        (-d.floor_w_model_mm, d.depth_mm - DETENT_MM), (d.floor_w_model_mm, d.depth_mm - DETENT_MM),
        (d.floor_w_model_mm, d.depth_mm + OVERCUT_MM), (-d.floor_w_model_mm, d.depth_mm + OVERCUT_MM),
    ])
    ridge = cad.translate(cad.rotate_x(cad.prism(ridge_profile, DETENT_W_MM), 90.0), dy=DETENT_W_MM / 2.0)
    return cad.cut(channel, [ridge])


def tool_mesh(d: DovetailDims, frame: "AnchorFrame") -> trimesh.Trimesh:
    """The tool placed in world coordinates: ``frame.origin`` the mouth's centre, ``frame.normal`` into the body, the channel
    running along ``frame.v``; its length is the frame's ``size_mm[0]`` when given."""
    length = float(frame.size_mm[0]) if frame.size_mm else None
    return frame.place(cad.to_trimesh(tool_part(d, length)))


def report(
    connector: "Connector",
    d: DovetailDims,
    frame: "AnchorFrame",
    comp: Compensation,
    material_id: str | None,
    *,
    tolerance_class: str = "molded",
    pin_d_mm: float | None = None,
    fit_tested: bool = False,
    **_: Any,
) -> dict[str, Any]:
    from .base import tolerance_for

    length = float(frame.size_mm[0]) if frame.size_mm else d.length_mm
    return {
        "kind": connector.kind,
        "fit": connector.fit,
        "nominal_mm": d.nominal_mm,
        "depth_mm": d.depth_mm,
        "mouth_mm": [round(float(v), 4) for v in frame.origin],
        "axis": [round(float(v), 6) for v in frame.normal],
        "along": [round(float(v), 6) for v in frame.v],
        "length_mm": round(length, 3),
        "mouth_w_mm": round(d.mouth_w_model_mm, 4),
        "floor_w_mm": round(d.floor_w_model_mm, 4),
        "rail_neck_mm": d.rail_neck_mm,
        "rail_head_mm": d.rail_head_mm,
        "rail_height_mm": RAIL_HEIGHT_MM,
        "rail_length_mm": RAIL_LENGTH_MM,
        "detent_mm": DETENT_MM,
        "chamfer_mm": d.chamfer_mm,
        "pin_d_mm": float(pin_d_mm) if pin_d_mm is not None else d.nominal_mm,
        "pin_tolerance_mm": tolerance_for(tolerance_class, "molded"),
        "tolerance_class": tolerance_class,
        "min_flank_gap_mm": MIN_FLANK_GAP_MM,
        "loose_flank_gap_mm": LOOSE_FLANK_GAP_MM,
        "min_wall_mm": comp.connector_min_wall_mm,
        "compensation": comp.descriptor(),
        "material_id": material_id,
        "adapter_sku": connector.adapter_sku,
        "fit_tested": bool(fit_tested),
    }


def expected_printed(d: DovetailDims) -> dict[str, float]:
    return {
        "mouth_w_mm": round(d.mouth_w_printed_mm, 3),
        "floor_w_mm": round(d.floor_w_printed_mm, 3),
        "depth_mm": round(d.depth_mm, 3),
        "flank_clearance_mm": round(d.flank_clearance_printed_mm, 3),
        "floor_clearance_mm": round(d.floor_clearance_printed_mm, 3),
        "detent_mm": DETENT_MM,
    }


COUPON_FLOOR_MM = 3.0


def coupon_part(connector: "Connector", d: DovetailDims, wall_mm: float) -> "cad.Part":
    """A block the rail's length with the channel cut along it: ``wall_mm`` beside the floor and over it."""
    width = d.floor_w_model_mm + 2.0 * wall_mm
    height = d.depth_mm + max(wall_mm, COUPON_FLOOR_MM)
    block = cad.box(width, RAIL_LENGTH_MM + 2.0, height)
    return cad.cut(block, [tool_part(d, RAIL_LENGTH_MM + 2.0)])


def coupon_frame(d: DovetailDims) -> "AnchorFrame":
    from ..features.frames import AnchorFrame

    return AnchorFrame(np.array([0.0, 0.0, 0.0]), [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=(RAIL_LENGTH_MM + 2.0, d.floor_w_model_mm))


def measure(mesh: trimesh.Trimesh, frame: "AnchorFrame", d: DovetailDims) -> dict[str, Any]:
    """Read the channel back off a built mesh: the mouth and floor widths from the vertices on the mouth plane and the floor."""
    local = frame.to_local(mesh.vertices)
    x, y, z = local[:, 0], local[:, 1], local[:, 2]
    near_axis = np.abs(y) < d.length_mm / 2.0 - 1.0
    out: dict[str, Any] = {}
    mouth = near_axis & (np.abs(z - d.chamfer_mm) < 0.05) & (np.abs(x) < d.mouth_w_model_mm / 2.0 + 0.5)  # below the lead-in
    floor = near_axis & (np.abs(z - d.depth_mm) < 0.05) & (np.abs(x) < d.floor_w_model_mm / 2.0 + 0.05)
    if mouth.any():
        out["mouth_w_mm"] = round(float(2.0 * np.abs(x[mouth]).max()), 3)
    if floor.any():
        out["floor_w_mm"] = round(float(2.0 * np.abs(x[floor]).max()), 3)
    out["vertices"] = int(mouth.sum() + floor.sum())
    return out


__all__ = [
    "DEPTH_CLEARANCE_MM",
    "DETENT_MM",
    "DovetailDims",
    "FLANK_CLEARANCE_MM",
    "HEAD_OVER_NECK_MM",
    "RAIL_HEIGHT_MM",
    "RAIL_LENGTH_MM",
    "channel_profile",
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
