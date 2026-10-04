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

Anchors (content lands on these, see ``anchor_frame``): each takes a name (Naam) and/or a motif (Buti),
raised or cut up to ``MAX_RELIEF_MM`` (1.2 mm: a cut leaves at least 1.2 mm of the thinnest 2.4 mm wall).
  side_left / side_right  the largest rectangle inscribed in each rail's outer face (normal ∓X).
  back                    the band of the back rest's rear face below the window (normal leaning +Y).
The descriptor publishes the anchor sizes at the default parameters; ``anchor_frame`` gives the
exact frame for any parameters.
"""

from __future__ import annotations

import math
from typing import Any, Mapping

import numpy as np
import trimesh
from shapely.geometry import LineString, Point, Polygon, box
from shapely.ops import unary_union

from .. import cad
from ..errors import ParamOutOfRange
from ..features.frames import AnchorFrame
from .base import Anchor, Param, Template, TemplateConstraints

CABLE_SLOT_MM = 12.0
MIN_PHONE_SLOT_MM = 12.0  # horizontal gap between lip and back rest at the base (phone + case leaning)
WINDOW_BOTTOM_BAND_MM = 14.0
MIN_WINDOW_BODY_MM = 10.0
ARC_QUAD_SEGS = 12
ANCHOR_MARGIN_MM = 1.0  # kept clear between content and the edge of a face
# names and motifs on the rails and the back rest stand or cut at most this deep: the contract's default letter
# depth, and a 1.2 mm cut into the thinnest (2.4 mm) wall still leaves the 1.2 mm minimum wall behind it
MAX_RELIEF_MM = 1.2


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


def largest_inscribed_rect(poly: Polygon, margin: float = 0.0, steps: int = 48) -> tuple[float, float, float, float] | None:
    """``(y0, z0, y1, z1)`` of the largest axis-aligned rectangle inside a convex polygon shrunk by ``margin``.

    For a convex shape a rectangle fits iff its two horizontal edges do, so the search scans pairs of
    heights and intersects the polygon's horizontal sections there.
    """
    inner = poly.buffer(-margin, join_style=2) if margin > 0 else poly
    if inner.is_empty:
        return None
    if inner.geom_type != "Polygon":
        inner = max(inner.geoms, key=lambda g: g.area)
    miny, minz, maxy, maxz = inner.bounds
    zs = np.linspace(minz, maxz, steps)
    sections: list[tuple[float, float] | None] = []
    for z in zs:
        seg = inner.intersection(LineString([(miny - 1.0, z), (maxy + 1.0, z)]))
        if seg.is_empty or seg.length <= 1e-9:
            sections.append(None)
        else:
            b = seg.bounds
            sections.append((b[0], b[2]))
    best: tuple[float, float, float, float, float] | None = None
    for a in range(steps):
        sa = sections[a]
        if sa is None:
            continue
        for b in range(a + 1, steps):
            sb = sections[b]
            if sb is None:
                continue
            lo, hi = max(sa[0], sb[0]), min(sa[1], sb[1])
            if hi <= lo:
                continue
            area = (hi - lo) * (zs[b] - zs[a])
            if best is None or area > best[0]:
                best = (area, lo, float(zs[a]), hi, float(zs[b]))
    return None if best is None else best[1:]


class Layout:
    """Derived dimensions shared by ``build_body``, ``validate_combination``, ``anchor_frame`` and ``karigar_note``."""

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

    @property
    def rail_height(self) -> float:
        return max(0.45 * self.H, self.wall + self.lip + 8.0)

    def rail_profile(self) -> Polygon:
        """Side rail profile in the (Y, Z) plane (shared by the build and the side anchors)."""
        z_rail = self.rail_height
        inset = 0.6 * self.wall / self.sin
        return Polygon(
            [
                (-self.D / 2, 0.0),
                (self.back_front_y(0.0) + inset, 0.0),
                (self.back_front_y(z_rail) + inset, z_rail),
                (self.y_lip - self.wall * self.sin + (self.wall + self.lip - self.wall * self.cos) * self.cot, self.wall + self.lip),
            ]
        )

    def rail_rect(self) -> tuple[float, float, float, float]:
        """``(y0, z0, y1, z1)`` of the content rectangle on a rail's outer face."""
        rect = largest_inscribed_rect(self.rail_profile(), ANCHOR_MARGIN_MM)
        if rect is None:  # pragma: no cover - the rail is always big enough for a sliver
            raise ParamOutOfRange(["height_mm", "lip_height_mm"], "The side rails are too small to carry content")
        return rect

    def back_plate_point(self, x: float, along: float) -> np.ndarray:
        """World point on the back rest's rear face, ``along`` mm up the plate from its foot."""
        return np.array([x, self.y_bb + along * self.cos, along * self.sin])

    def back_band(self) -> tuple[float, float, float, float]:
        """``(x0, along0, x1, along1)`` in plate coordinates: the band below the window on the rear face."""
        f = _frame_margin(self.wall)
        along0 = self.wall / self.sin + ANCHOR_MARGIN_MM  # where the rear face emerges from the base plate
        along1 = WINDOW_BOTTOM_BAND_MM - ANCHOR_MARGIN_MM
        return (-(self.W / 2 - f - ANCHOR_MARGIN_MM), along0, self.W / 2 - f - ANCHOR_MARGIN_MM, along1)

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        y0, z0, y1, z1 = self.rail_rect()
        rail_size = (y1 - y0, z1 - z0)
        yc, zc = (y0 + y1) / 2.0, (z0 + z1) / 2.0
        x0, a0, x1, a1 = self.back_band()
        back_size = (x1 - x0, a1 - a0)
        # Rear face normal of the leaning plate: +Y and down; "up" runs along the plate.
        back_normal = np.array([0.0, self.sin, -self.cos])
        return {
            "side_left": AnchorFrame(np.array([-self.W / 2, yc, zc]), [0, -1, 0], [0, 0, 1], [-1, 0, 0], size_mm=rail_size),
            "side_right": AnchorFrame(np.array([self.W / 2, yc, zc]), [0, 1, 0], [0, 0, 1], [1, 0, 0], size_mm=rail_size),
            "back": AnchorFrame(
                self.back_plate_point((x0 + x1) / 2.0, (a0 + a1) / 2.0),
                [-1, 0, 0],
                [0, self.cos, self.sin],
                back_normal,
                size_mm=back_size,
            ),
        }


PARAMS = {
    "width_mm": Param("number", "Width", 92.0, "mm", 70, 110, 1, handle=True, group="Size"),
    "depth_mm": Param("number", "Depth", 78.0, "mm", 60, 100, 1, handle=True, group="Size"),
    "height_mm": Param("number", "Height", 120.0, "mm", 90, 150, 1, handle=True, group="Size"),
    "tilt_deg": Param("number", "Tilt", 70.0, "deg", 60, 78, 1, group="Shape", description="Back rest angle from horizontal."),
    "lip_height_mm": Param("number", "Lip height", 12.0, "mm", 8, 18, 0.5, group="Shape"),
    "wall_mm": Param("number", "Wall thickness", 3.2, "mm", 2.4, 4.0, 0.1, group="Details"),
    "arch_cusps": Param("integer", "Arch cusps", 5, "count", 3, 7, 1, group="Details", description="Lobes in the jharokha arch."),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class JharokhaPhoneStand(Template):
    id = "jharokha_phone_stand"
    version = 1
    family = "phone_stand"
    name = "Jharokha phone stand"
    description = (
        "A leaning phone stand with a Mughal cusped-arch jharokha window in the back rest, "
        "a cable slot in the lip and side rails for stiffness. A name or a motif can go on either side rail "
        "and on the back of the rest."
    )
    environment = "desk_oak"
    params = PARAMS
    anchors = (
        Anchor(
            "side_left", "Left side rail", "planar", max_text_height_mm=12, size_mm=_size("side_left"),
            accepts=("emboss_text", "motif"), max_relief_mm=MAX_RELIEF_MM,
        ),
        Anchor(
            "side_right", "Right side rail", "planar", max_text_height_mm=12, size_mm=_size("side_right"),
            accepts=("emboss_text", "motif"), max_relief_mm=MAX_RELIEF_MM,
        ),
        Anchor(
            "back", "Back of the rest", "planar", max_text_height_mm=20, size_mm=_size("back"),
            accepts=("emboss_text", "motif"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    style_variants = ()
    features_supported = ("emboss_text", "motif")
    min_feature_mm = 0.8

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
        rail_profile = L.rail_profile()
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
    def build_body(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        mesh = cad.to_trimesh(cls.build_part(params))
        # Sit exactly on Z = 0 and centre in X/Y (guards against kernel rounding).
        mesh.apply_translation([0.0, 0.0, -float(mesh.bounds[0][2])])
        return mesh

    @classmethod
    def anchor_frame(cls, anchor_id: str, params: Mapping[str, Any]) -> AnchorFrame:
        frames = Layout(params).anchor_frames()
        try:
            return frames[anchor_id]
        except KeyError:
            raise NotImplementedError(f"{cls.ref()} does not place anchor {anchor_id!r}") from None

    @classmethod
    def karigar_note(cls, params: Mapping[str, Any]) -> str:
        L = Layout(params)
        return (
            f"A jharokha-arch phone stand, {L.W:g} mm wide with a {L.lip:g} mm lip and a "
            f"{L.cusps}-cusp arch. It holds phones up to {L.phone_clearance:.0f} mm wide, leaning at {L.tilt:g}°."
        )
