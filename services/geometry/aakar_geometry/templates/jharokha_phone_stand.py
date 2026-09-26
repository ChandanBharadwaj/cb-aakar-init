"""``jharokha_phone_stand@1`` - a phone stand whose back rest carries a Mughal cusped-arch window.

Coordinate frame (mm): X = width (left/right), Y = depth (−Y is the front where the phone's lip
is, +Y the back), Z = height. The part sits on Z = 0 and is centred in X and Y.

Pieces
  base      W × D × wall plate.
  back rest  wall-thick plate leaning back at ``tilt_deg`` from horizontal, top-back corner flush
             with the base's rear edge, top-front corner at exactly Z = height. A multifoil
             (``arch_cusps`` lobes) jharokha window is cut through it.
  lip       wall-thick plate at the front, parallel to the back rest, ``lip_height_mm`` above the base.
  rails     two wall-thick gussets at the outer edges joining lip, base and back rest.
  cable slot 12 mm notch through the lip and a channel in the base for a charging cable.
"""

from __future__ import annotations

import math
from typing import Any, Mapping

import trimesh
from shapely.geometry import Point, Polygon, box
from shapely.ops import unary_union

from .. import cad
from ..errors import ParamOutOfRange
from .base import Anchor, Param, Template, TemplateConstraints

CABLE_SLOT_MM = 12.0
MIN_PHONE_SLOT_MM = 12.0  # horizontal gap between lip and back rest at the base (phone + case leaning)
WINDOW_BOTTOM_BAND_MM = 14.0
MIN_WINDOW_BODY_MM = 10.0
ARC_QUAD_SEGS = 12


def _frame_margin(wall: float) -> float:
    return max(8.0, 2.5 * wall)


def cusped_arch(width: float, body_height: float, cusps: int, quad_segs: int = ARC_QUAD_SEGS) -> Polygon:
    """Multifoil (jharokha) arch opening: a rectangle body topped by ``cusps`` lobes.

    Lobes are centred on a semicircle of radius R above the springing line and touch their
    neighbours at cusps that point into the opening. R is chosen so the lowest lobes are flush
    with the jambs (width). Local frame: x across, y up, origin at the bottom centre.
    """
    if cusps < 1:
        raise ValueError("cusps must be >= 1")
    half_step = math.pi / (2 * cusps)
    s, c = math.sin(half_step), math.cos(half_step)
    field_radius = width / (2.0 * (c + s))
    lobe_radius = field_radius * s
    body = box(-width / 2.0, 0.0, width / 2.0, body_height)
    spring = Point(0.0, body_height)
    upper_disc = spring.buffer(field_radius, quad_segs=quad_segs).intersection(
        box(-width, body_height, width, body_height + 2 * field_radius)
    )
    lobes = []
    for k in range(cusps):
        phi = half_step + k * (math.pi / cusps)
        centre = Point(field_radius * math.cos(phi), body_height + field_radius * math.sin(phi))
        lobes.append(centre.buffer(lobe_radius, quad_segs=quad_segs))
    poly = unary_union([body, upper_disc, *lobes])
    if poly.geom_type != "Polygon":  # pragma: no cover - defensive
        poly = max(poly.geoms, key=lambda g: g.area)
    return poly.simplify(0.01)


def arch_rise_factor(cusps: int) -> float:
    """Height of the arch above the springing line as a fraction of its width."""
    half_step = math.pi / (2 * cusps)
    s, c = math.sin(half_step), math.cos(half_step)
    return (1.0 + s) / (2.0 * (c + s))


class Layout:
    """Derived dimensions shared by ``build``, ``validate_combination`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.W = float(p["width_mm"])
        self.D = float(p["depth_mm"])
        self.H = float(p["height_mm"])
        self.tilt = float(p["tilt_deg"])
        self.lip = float(p["lip_height_mm"])
        self.wall = float(p["wall_mm"])
        self.cusps = int(p["arch_cusps"])
        th = math.radians(self.tilt)
        self.sin, self.cos, self.cot = math.sin(th), math.cos(th), 1.0 / math.tan(th)
        # Back rest plate length so its top-front corner sits at Z = H after rotation.
        self.back_len = (self.H - self.wall * self.cos) / self.sin
        # Rotation about X puts the plate's back face at y = y_bb + z·cot; place top-back corner at D/2.
        self.y_bb = self.D / 2.0 - self.back_len * self.cos
        # Lip plate length so its top reaches wall + lip above the ground.
        self.lip_len = (self.wall + self.lip - self.wall * self.cos) / self.sin
        self.y_lip = -self.D / 2.0 + self.wall * self.sin  # lip's back-bottom corner
        self.phone_slot = self.back_front_y(self.wall) - self.lip_back_y(self.wall)

    def back_front_y(self, z: float) -> float:
        """Y of the back rest's front face at height z."""
        return self.y_bb - self.wall * self.sin + (z - self.wall * self.cos) * self.cot

    def lip_back_y(self, z: float) -> float:
        return self.y_lip + z * self.cot

    @property
    def phone_clearance(self) -> float:
        return self.W - 2.0 * self.wall

    def window(self) -> tuple[Polygon, float, float]:
        """(polygon in plate coordinates, window width, body height)."""
        f = _frame_margin(self.wall)
        width = self.W - 2.0 * f
        available = self.back_len - WINDOW_BOTTOM_BAND_MM - f
        rise = arch_rise_factor(self.cusps)
        body = available - width * rise
        if body < MIN_WINDOW_BODY_MM:  # short back rest: narrow the arch so it still fits
            width = max((available - MIN_WINDOW_BODY_MM) / rise, 10.0)
            body = available - width * rise
        poly = cusped_arch(width, body, self.cusps)
        return poly, width, body


class JharokhaPhoneStand(Template):
    id = "jharokha_phone_stand"
    version = 1
    family = "phone_stand"
    name = "Jharokha phone stand"
    description = (
        "A leaning phone stand with a Mughal cusped-arch jharokha window in the back rest, "
        "a cable slot in the lip and side rails for stiffness."
    )
    environment = "desk_oak"
    params = {
        "width_mm": Param("number", "Width", 92.0, "mm", 70, 110, 1, handle=True, group="Size"),
        "depth_mm": Param("number", "Depth", 78.0, "mm", 60, 100, 1, handle=True, group="Size"),
        "height_mm": Param("number", "Height", 120.0, "mm", 90, 150, 1, handle=True, group="Size"),
        "tilt_deg": Param("number", "Tilt", 70.0, "deg", 60, 78, 1, group="Shape", description="Back rest angle from horizontal."),
        "lip_height_mm": Param("number", "Lip height", 12.0, "mm", 8, 18, 0.5, group="Shape"),
        "wall_mm": Param("number", "Wall thickness", 3.2, "mm", 2.4, 4.0, 0.1, group="Details"),
        "arch_cusps": Param("integer", "Arch cusps", 5, "count", 3, 7, 1, group="Details", description="Lobes in the jharokha arch."),
    }
    anchors = (
        Anchor("side_left", "Left side rail", "planar", max_text_height_mm=12),
        Anchor("side_right", "Right side rail", "planar", max_text_height_mm=12),
        Anchor("back", "Back of the rest", "planar", max_text_height_mm=20),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    style_variants = ()
    features_supported = ()

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        layout = Layout(params)
        if layout.phone_slot < MIN_PHONE_SLOT_MM:
            raise ParamOutOfRange(
                ["depth_mm", "height_mm", "tilt_deg"],
                (
                    f"The back rest leans too far for this depth: the phone slot would be "
                    f"{layout.phone_slot:.1f} mm (needs {MIN_PHONE_SLOT_MM:g} mm). Increase depth_mm, "
                    "reduce height_mm or steepen tilt_deg."
                ),
                {"phone_slot_mm": round(layout.phone_slot, 2), "min_phone_slot_mm": MIN_PHONE_SLOT_MM},
            )

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        L = Layout(params)
        W, D, H, wall = L.W, L.D, L.H, L.wall

        base = cad.prism(box(-W / 2, -D / 2, W / 2, D / 2), wall)

        # Back rest: flat plate in XY (x across, y along the plate), window cut, then rotated up.
        plate = cad.prism(box(-W / 2, 0.0, W / 2, L.back_len), wall)
        window_poly, _, _ = L.window()
        # window polygon is in (x, y_along_plate) with its bottom at the band height
        window = cad.translate(cad.prism(window_poly, wall + 2.0), dy=WINDOW_BOTTOM_BAND_MM, dz=-1.0)
        plate = cad.cut(plate, [window])
        back = cad.translate(cad.rotate_x(plate, L.tilt), dy=L.y_bb)

        lip = cad.translate(cad.rotate_x(cad.prism(box(-W / 2, 0.0, W / 2, L.lip_len), wall), L.tilt), dy=L.y_lip)

        # Side rails: profile in the (Y, Z) plane, extruded along +X by `wall`.
        z_rail = max(0.45 * H, wall + L.lip + 8.0)
        inset = 0.6 * wall / L.sin
        rail_profile = Polygon(
            [
                (-D / 2, 0.0),
                (L.back_front_y(0.0) + inset, 0.0),
                (L.back_front_y(z_rail) + inset, z_rail),
                (L.y_lip - wall * L.sin + (wall + L.lip - wall * L.cos) * L.cot, wall + L.lip),
            ]
        )
        rail_left = cad.translate(cad.prism(rail_profile, wall, cad.Plane.YZ), dx=-W / 2)
        rail_right = cad.translate(cad.prism(rail_profile, wall, cad.Plane.YZ), dx=W / 2 - wall)

        body = cad.union([base, back, lip, rail_left, rail_right])

        slot_end = L.back_front_y(wall) - 4.0
        cable = cad.translate(
            cad.prism(box(-CABLE_SLOT_MM / 2, -D / 2 - 1.0, CABLE_SLOT_MM / 2, slot_end), wall + L.lip + 3.0),
            dz=-1.0,
        )
        return cad.cut(body, [cable])

    @classmethod
    def build(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        mesh = cad.to_trimesh(cls.build_part(params))
        # Sit exactly on Z = 0 and centre in X/Y (guards against kernel rounding).
        mesh.apply_translation([0.0, 0.0, -float(mesh.bounds[0][2])])
        return mesh

    @classmethod
    def karigar_note(cls, params: Mapping[str, Any]) -> str:
        L = Layout(params)
        return (
            f"A jharokha-arch phone stand, {L.W:g} mm wide with a {L.lip:g} mm lip and a "
            f"{L.cusps}-cusp arch. It holds phones up to {L.phone_clearance:.0f} mm wide, leaning at {L.tilt:g}°."
        )
