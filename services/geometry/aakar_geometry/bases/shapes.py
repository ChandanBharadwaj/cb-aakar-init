"""Schematic bodies for bought-in bases, rendered as the preview GLB the storefront's two-model viewer shows muted and
locked next to the editable Chhaap until a photographed or authored model is uploaded (plan §3, ``preview_shape`` in
``template-family.v1.json``).

Every shape stands on z = 0 centred on the z axis, faces −Y, and returns the **mount frame**: where a Chhaap's connector
seat sits (``origin_mm``), the connector's axis (``up``) and the piece's front (``forward``), in the base's own frame.
Dimensions in mm; each shape documents its keys and takes a default for any it is not given.
"""

from __future__ import annotations

from typing import Any, Callable, Mapping

import trimesh

from .. import cad
from ..errors import InvalidSpec
from ..exports import CONTENT_TYPES, export_glb
from ..storage import AssetRecord, Storage
from ..templates import plates

FORWARD = [0.0, -1.0, 0.0]
UP = [0.0, 0.0, 1.0]

DEFAULTS: dict[str, dict[str, float]] = {
    "pillar_stand": {"foot_d_mm": 120, "foot_h_mm": 8, "pillar_d_mm": 14, "pillar_h_mm": 240, "tube_id_mm": 12, "tube_od_mm": 16, "tube_h_mm": 12},
    "hook_plate": {"width_mm": 260, "height_mm": 60, "thickness_mm": 2, "hooks": 4, "hook_len_mm": 35},
    "lamp_base": {"foot_d_mm": 140, "foot_h_mm": 15, "stem_d_mm": 16, "stem_h_mm": 150, "holder_d_mm": 40, "holder_h_mm": 15},
    "puck_mount": {"puck_d_mm": 40, "puck_h_mm": 8, "pad_w_mm": 45, "pad_h_mm": 10},
    "box": {"width_mm": 120, "depth_mm": 80, "height_mm": 40, "wall_mm": 2},
    "bottle": {"body_d_mm": 70, "body_h_mm": 190, "cap_d_mm": 42, "cap_h_mm": 30},
    "pot": {"top_d_mm": 120, "bottom_d_mm": 85, "height_mm": 110, "rim_t_mm": 8, "rim_h_mm": 12},
}
MAX_MM = 1000.0


def _frame(origin: list[float], up: list[float] = UP, forward: list[float] = FORWARD) -> dict[str, list[float]]:
    return {"origin_mm": [round(float(v), 3) for v in origin], "up": [float(v) for v in up], "forward": [float(v) for v in forward]}


def _pillar_stand(d: dict[str, float]) -> tuple["cad.Part", dict[str, Any]]:
    foot = cad.prism(plates.disc(0.0, 0.0, d["foot_d_mm"] / 2.0), d["foot_h_mm"])
    pillar = cad.cylinder(d["pillar_d_mm"] / 2.0, d["pillar_h_mm"] + 0.5, z0=d["foot_h_mm"] - 0.5)
    z_tube = d["foot_h_mm"] + d["pillar_h_mm"]
    tube = cad.cylinder(d["tube_od_mm"] / 2.0, d["tube_h_mm"] + 0.5, z0=z_tube - 0.5)
    bore = cad.cylinder(d["tube_id_mm"] / 2.0, d["tube_h_mm"] + 1.0, z0=z_tube - 0.5)
    part = cad.cut(cad.union([foot, pillar, tube]), [bore])
    return part, _frame([0.0, 0.0, z_tube + d["tube_h_mm"]])


def _hook_plate(d: dict[str, float]) -> tuple["cad.Part", dict[str, Any]]:
    w, h, t = d["width_mm"], d["height_mm"], d["thickness_mm"]
    plate = cad.box(w, t, h, cy=t / 2.0)
    parts = [plate]
    hooks = max(int(d["hooks"]), 1)
    for k in range(hooks):
        x = -w / 2.0 + w * (k + 0.5) / hooks
        arm = cad.translate(cad.rotate_x(cad.cylinder(3.0, d["hook_len_mm"] + 0.5), 90.0), dx=x, dy=0.5, dz=h * 0.3)
        tip = cad.cylinder(3.0, 10.0, cx=x, cy=-d["hook_len_mm"] + 3.0, z0=h * 0.3 - 3.0)
        parts.extend([arm, tip])
    part = cad.union(parts)
    # the dovetail rail is bonded along the plate's top edge on its front; a plaque's channel seats against it
    return part, _frame([0.0, -1.0, h - 2.0])


def _lamp_base(d: dict[str, float]) -> tuple["cad.Part", dict[str, Any]]:
    foot = cad.prism(plates.disc(0.0, 0.0, d["foot_d_mm"] / 2.0), d["foot_h_mm"])
    stem = cad.cylinder(d["stem_d_mm"] / 2.0, d["stem_h_mm"] + 0.5, z0=d["foot_h_mm"] - 0.5)
    z_holder = d["foot_h_mm"] + d["stem_h_mm"]
    holder = cad.cylinder(d["holder_d_mm"] / 2.0, d["holder_h_mm"] + 0.5, z0=z_holder - 0.5)
    neck = cad.cylinder(d["holder_d_mm"] / 2.0 - 2.0, 10.0, z0=z_holder + d["holder_h_mm"] - 0.5)
    part = cad.union([foot, stem, holder, neck])
    # the shade's collar sits on the holder's shoulder and the shade hangs down round the bulb: its body runs along -z
    return part, _frame([0.0, 0.0, z_holder + d["holder_h_mm"]], up=[0.0, 0.0, -1.0])


def _puck_mount(d: dict[str, float]) -> tuple["cad.Part", dict[str, Any]]:
    pad = cad.prism(plates.rounded_rect(d["pad_w_mm"], d["pad_w_mm"], 6.0), d["pad_h_mm"])
    puck = cad.cylinder(d["puck_d_mm"] / 2.0, d["puck_h_mm"] + 0.5, z0=d["pad_h_mm"] - 0.5)
    return cad.union([pad, puck]), _frame([0.0, 0.0, d["pad_h_mm"] + d["puck_h_mm"]])


def _box(d: dict[str, float]) -> tuple["cad.Part", dict[str, Any]]:
    w, dp, h, t = d["width_mm"], d["depth_mm"], d["height_mm"], d["wall_mm"]
    outer = cad.prism(plates.rounded_rect(w, dp, 3.0), h)
    inner = cad.translate(cad.prism(plates.rounded_rect(w - 2.0 * t, dp - 2.0 * t, max(3.0 - t, 0.5)), h), dz=t)
    return cad.cut(outer, [inner]), _frame([0.0, 0.0, h])


def _bottle(d: dict[str, float]) -> tuple["cad.Part", dict[str, Any]]:
    body_r, cap_r = d["body_d_mm"] / 2.0, d["cap_d_mm"] / 2.0
    shoulder_h = max((body_r - cap_r) * 0.8, 6.0)
    body = cad.cylinder(body_r, d["body_h_mm"])
    shoulder = cad.translate(cad.frustum(body_r, cap_r * 0.9, shoulder_h + 0.5), dz=d["body_h_mm"] - 0.5)
    z_cap = d["body_h_mm"] + shoulder_h
    cap = cad.cylinder(cap_r, d["cap_h_mm"] + 0.5, z0=z_cap - 0.5)
    return cad.union([body, shoulder, cap]), _frame([0.0, 0.0, z_cap + d["cap_h_mm"]])


def _pot(d: dict[str, float]) -> tuple["cad.Part", dict[str, Any]]:
    top_r, bottom_r, h, t, rim_h = d["top_d_mm"] / 2.0, d["bottom_d_mm"] / 2.0, d["height_mm"], d["rim_t_mm"], d["rim_h_mm"]
    wall = max(t * 0.6, 3.0)
    outer = cad.frustum(bottom_r, top_r, h)
    inner = cad.translate(cad.frustum(bottom_r - wall, top_r - wall, h), dz=wall)
    rim = cad.cylinder(top_r, rim_h + 0.5, z0=h - rim_h - 0.5)
    rim_bore = cad.cylinder(top_r - t, rim_h + 2.0, z0=h - rim_h - 1.0)
    part = cad.cut(cad.union([outer, rim]), [inner, rim_bore])
    return part, _frame([0.0, -(top_r - t / 2.0), h])


SHAPES: dict[str, Callable[[dict[str, float]], tuple["cad.Part", dict[str, Any]]]] = {
    "pillar_stand": _pillar_stand,
    "hook_plate": _hook_plate,
    "lamp_base": _lamp_base,
    "puck_mount": _puck_mount,
    "box": _box,
    "bottle": _bottle,
    "pot": _pot,
}


def _dims(shape: str, given: Mapping[str, Any] | None) -> dict[str, float]:
    out = dict(DEFAULTS[shape])
    for key, value in (given or {}).items():
        if key not in out:
            raise InvalidSpec(f"{shape} has no dimension {key}", {"shape": shape, "key": key, "keys": sorted(out)})
        try:
            number = float(value)
        except (TypeError, ValueError):
            raise InvalidSpec(f"{key} must be a number", {"shape": shape, "key": key, "value": value}) from None
        if not (0 < number <= MAX_MM):
            raise InvalidSpec(f"{key} must be between 0 and {MAX_MM:g} mm", {"shape": shape, "key": key, "value": number})
        out[key] = number
    return out


def build_shape(shape: str, dims: Mapping[str, Any] | None = None) -> tuple[trimesh.Trimesh, dict[str, Any], dict[str, float]]:
    """``(mesh, mount_frame, dims)`` for a shape; unknown shapes and bad dimensions are refused (``InvalidSpec``)."""
    if shape not in SHAPES:
        raise InvalidSpec(f"Unknown preview shape {shape!r}", {"shape": shape, "shapes": sorted(SHAPES)})
    d = _dims(shape, dims)
    part, frame = SHAPES[shape](d)
    mesh = cad.to_trimesh(part)
    lift = -float(mesh.bounds[0][2])
    if abs(lift) > 1e-6:
        mesh.apply_translation([0.0, 0.0, lift])
        frame["origin_mm"][2] = round(frame["origin_mm"][2] + lift, 3)
    return mesh, frame, d


def render_preview(sku: str, shape: str, dims: Mapping[str, Any] | None, storage: Storage) -> dict[str, Any]:
    """Build the shape, export it as the viewer's GLB under ``bases/<sku>/preview.glb`` and return the payload of
    ``POST /v1/bases/preview``."""
    mesh, frame, d = build_shape(shape, dims)
    record: AssetRecord = storage.put(f"bases/{sku}/preview.glb", export_glb(mesh, name=f"base_{sku}"), CONTENT_TYPES["glb"])
    return {
        "asset": record.to_dict(),
        "mount_frame": frame,
        "dimensions_mm": [round(float(e), 3) for e in mesh.extents],
        "shape": shape,
        "dims": d,
        "triangles": int(len(mesh.faces)),
        "watertight": bool(mesh.is_watertight),
    }


__all__ = ["DEFAULTS", "SHAPES", "build_shape", "render_preview"]
