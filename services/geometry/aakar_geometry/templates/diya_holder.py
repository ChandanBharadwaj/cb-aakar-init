"""``diya_holder@1`` - Diya: a holder for standard clay diyas in a heat-safe finish; a motif sits on the centre cup's flat rim.

Coordinate frame (mm): X across, Y depth (−Y is the front), Z up. The holder stands on its base at Z = 0, is centred in
X and Y, and is printed standing as modelled: the one overhang is the rim's 45° underside; no supports, no bridges.

Pieces
  base   a ``BASE_MM`` plate: a disc ``size_mm`` across (``single``, ``float``), or the union of the five cups' footprints
         with ``LEDGE_MM`` of plate round each (``panch``). In every style the longest extent is exactly ``size_mm``; a
         single cup on a disc wider than its rim is a drip plate round the diya.
  cups   each cup is a straight drum outside and a tapered cavity inside. The cavity is a frustum ``DIYA_D_MM`` across at
         the mouth, ``DIYA_FLOOR_D_MM`` at the floor and ``DIYA_DEPTH_MM`` deep (a standard clay diya), its floor on the
         base plate; the drum's radius is the mouth's plus ``wall_mm``, so the wall is ``wall_mm`` at the mouth and
         thickens toward the floor as the cavity narrows. ``float`` has one shallow bowl instead: ``size_mm − 2·RIM_W_MM``
         across at the mouth, ``BOWL_TAPER_MM`` less in radius at the floor, ``BOWL_DEPTH_MM`` deep, for a floating candle.
  rim    the centre cup (every style) wears a flat annulus ``RIM_W_MM`` wide from the cavity's edge and ``RIM_T_MM`` thick
         at the top of the drum; its underside is a 45° chamfer down to the drum, so the overhang is exactly 45° and needs
         no support. The chamfer's foot is at Z = ``BASE_MM + wall_mm − 1`` on a cup (higher on the bowl): above the base.
  panch  four plain drums (no rim) on the diagonals at centre distance ``d = √2·(size_mm / 2 − r_wall − LEDGE_MM)`` from
         the middle cup; the web of plastic between a satellite's cavity and the centre's at the mouth is ``d − DIYA_D_MM``
         and must be at least ``wall_mm``. Five 55 mm cups only fit a 160 mm envelope on the diagonals, and the smallest
         size that keeps the web for every wall is 150 mm: that is the style's floor (``SIZE_RANGE``), the only part of
         the size slider a style refuses.

Anchor
  rim  surface, normal +Z: an ``ANCHOR_W_MM`` × ``ANCHOR_H_MM`` rectangle on the front (−Y) part of the centre cup's flat
       rim, its inner edge ``ANCHOR_IN_MM`` out from the cavity's edge and its outer corners at least ``ANCHOR_OUT_MM``
       inside the rim's outer edge (the chord check runs at import): a motif (Buti), raised or cut in. A cut-in motif
       must leave ``plates.MIN_SKIN_MM`` of the ``RIM_T_MM`` rim (``validate_content``).

No hardware. The family is heat-safe only (``petg_slate``): the clay diya sits in the cup, never a bare flame on the plastic.
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely.geometry import Polygon
from shapely.ops import unary_union

from .. import cad
from ..errors import ParamOutOfRange
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

BASE_MM = 4.0  # the base plate: thick enough to stay flat on a warm table and to carry five cups
DIYA_D_MM = 55.0  # a standard clay diya at the mouth of its cup
DIYA_FLOOR_D_MM = 40.0  # ... and at the cup's floor: the cup tapers like the diya does
DIYA_DEPTH_MM = 16.0  # cup depth: the diya's belly sits in, its lip stays above the rim
BOWL_DEPTH_MM = 20.0  # the floating-candle bowl: water deep enough for a floating tealight
BOWL_TAPER_MM = 8.0  # the bowl's radius shrinks this much from the mouth to the floor (a 22° slope)
RIM_W_MM = 14.0  # the flat rim from the cavity's edge: wide enough for the motif's chord (see the anchor assert)
RIM_T_MM = 3.0  # the flat rim's thickness at its outer edge: a 0.8 mm cut-in motif leaves 2.2 mm (MIN_SKIN_MM is 1.2)
LEDGE_MM = 2.0  # base plate kept round each drum in the panch style
OVERLAP_MM = 1.0  # pieces reach this far into their neighbours so no boolean meets a coplanar face
WALL_MIN_MM = 2.0
WALL_MAX_MM = 3.0
ANCHOR_W_MM = 17.0
ANCHOR_H_MM = 11.0  # with BLEED_MM the motif prints 10 mm tall: the Rangoli star's raised strokes stay thicker than 1.6 mm
ANCHOR_IN_MM = 1.0  # the anchor's inner edge this far out from the cavity's edge
ANCHOR_OUT_MM = 1.0  # ... and its outer corners at least this far inside the rim's outer edge
BLEED_MM = 0.5
MAX_RELIEF_MM = 1.5
SIZE_MIN_MM = 85.0  # one rimmed cup (83 mm) on its disc
SIZE_MAX_MM = 160.0  # the family's envelope

STYLES = ("single", "panch", "float")
# the size each style accepts: single = one rimmed cup on a disc (a drip plate when wider); panch = five cups on the
# diagonals, the web between cavities at least the wall; float = a bowl size − 2·RIM_W_MM across (57 mm at 85, 132 at 160)
SIZE_RANGE: dict[str, tuple[float, float]] = {
    "single": (SIZE_MIN_MM, SIZE_MAX_MM),
    "panch": (150.0, SIZE_MAX_MM),
    "float": (SIZE_MIN_MM, SIZE_MAX_MM),
}
STYLE_NOUN = {"single": "A single diya holder", "panch": "A five-cup panch holder", "float": "A floating-candle bowl"}

_R_MOUTH = DIYA_D_MM / 2.0
# the anchor's outer corners stay on the flat rim: the chord of a 17 mm rectangle whose far edge is 12 mm out from a
# 27.5 mm cavity reaches 40.4 mm from the centre, inside the rim's 41.5 mm edge by ANCHOR_OUT_MM; a wider bowl is flatter
assert math.hypot(ANCHOR_W_MM / 2.0, _R_MOUTH + ANCHOR_IN_MM + ANCHOR_H_MM) <= _R_MOUTH + RIM_W_MM - ANCHOR_OUT_MM
assert ANCHOR_H_MM - 2.0 * BLEED_MM >= 7.5  # the smallest box the Rangoli star passes the stroke rule in
assert SIZE_MIN_MM >= 2.0 * (_R_MOUTH + RIM_W_MM)  # the rimmed cup fits on the smallest disc
assert SIZE_MIN_MM / 2.0 - RIM_W_MM - BOWL_TAPER_MM >= 20.0  # the smallest bowl still has a 40 mm floor
assert RIM_T_MM - 0.8 >= plates.MIN_SKIN_MM  # the SPEC's deepest cut-in leaves the skin
assert RIM_W_MM - WALL_MAX_MM > 0  # the chamfer has a height


class Layout:
    """Derived dimensions shared by ``build_part``, ``validate_combination``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.style = str(p["style"])
        self.S = float(p["size_mm"])
        self.w = float(p["wall_mm"])
        lo, hi = SIZE_RANGE[self.style]
        self.size_ok = lo - 1e-9 <= self.S <= hi + 1e-9
        if self.style == "float":
            self.r_mouth = self.S / 2.0 - RIM_W_MM
            self.r_floor = self.r_mouth - BOWL_TAPER_MM
            self.depth = BOWL_DEPTH_MM
        else:
            self.r_mouth = _R_MOUTH
            self.r_floor = DIYA_FLOOR_D_MM / 2.0
            self.depth = DIYA_DEPTH_MM
        self.r_wall = self.r_mouth + self.w  # the drum
        self.r_rim = self.r_mouth + RIM_W_MM  # the flat rim's outer edge
        self.top = BASE_MM + self.depth
        self.flare = RIM_W_MM - self.w  # how far the rim reaches beyond the drum = the 45° chamfer's height
        self.z_chamfer = self.top - RIM_T_MM - self.flare  # where the chamfer meets the drum
        if self.style == "panch":
            self.d = math.sqrt(2.0) * (self.S / 2.0 - self.r_wall - LEDGE_MM)  # centre distance to each satellite
            a = self.d / math.sqrt(2.0)
            self.satellites: list[tuple[float, float]] = [(a, a), (-a, a), (-a, -a), (a, -a)]
            self.web = self.d - 2.0 * self.r_mouth  # plastic between two cavities at the mouth
        else:
            self.d = 0.0
            self.satellites = []
            self.web = math.inf

    def cups(self) -> list[tuple[float, float]]:
        """Cup centres, the rimmed centre cup first."""
        return [(0.0, 0.0), *self.satellites]

    def base_outline(self) -> Polygon:
        if self.style != "panch":
            return plates.disc(0.0, 0.0, self.S / 2.0)
        discs = [plates.disc(0.0, 0.0, self.r_rim + LEDGE_MM)] + [plates.disc(x, y, self.r_wall + LEDGE_MM) for x, y in self.satellites]
        return plates.largest_polygon(unary_union(discs))

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        y = -(self.r_mouth + ANCHOR_IN_MM + ANCHOR_H_MM / 2.0)
        return {"rim": AnchorFrame(np.array([0.0, y, self.top]), [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=(ANCHOR_W_MM, ANCHOR_H_MM))}


PARAMS = {
    "style": Param(
        "enum", "Style", "single", options=STYLES, group="Shape",
        description="One cup, five cups (one in the middle, four around it) or one wide bowl for a floating candle.",
    ),
    "size_mm": Param(
        "number", "Size", 100.0, "mm", SIZE_MIN_MM, SIZE_MAX_MM, 5, handle=True, group="Size",
        description="Across the whole holder: any size for one cup or the bowl; five cups need 150–160 mm.",
    ),
    "wall_mm": Param(
        "number", "Wall", 2.4, "mm", WALL_MIN_MM, WALL_MAX_MM, 0.1, group="Details",
        description="The cup wall at the mouth; it thickens toward the floor as the cup tapers.",
    ),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


# the panch floor keeps a full wall between cavities even with the thickest wall
assert Layout({"style": "panch", "size_mm": SIZE_RANGE["panch"][0], "wall_mm": WALL_MAX_MM}).web >= WALL_MAX_MM


class DiyaHolder(Template):
    id = "diya_holder"
    version = 1
    family = "diya_holder"
    name = "Diya holder"
    description = (
        "A holder for standard clay diyas: one cup, five cups on a plate, or a wide bowl for a floating candle and "
        "petals, in a heat-safe finish. A motif sits on the centre cup's flat rim."
    )
    environment = "teak_table_candlelight"
    params = PARAMS
    anchors = (
        Anchor("rim", "Rim", "planar", size_mm=_size("rim"), bleed_mm=BLEED_MM, accepts=("motif",), max_relief_mm=MAX_RELIEF_MM),
    )
    constraints = TemplateConstraints(min_wall_mm=1.6, max_overhang_deg=45, bed_mm=(250, 250, 250))
    features_supported = ("motif",)
    hardware: tuple[HardwareRef, ...] = ()
    min_feature_mm = 0.8

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        L = Layout(params)
        if not L.size_ok:
            lo, hi = SIZE_RANGE[L.style]
            raise ParamOutOfRange(
                ["size_mm", "style"],
                f"{STYLE_NOUN[L.style]} is between {lo:g} and {hi:g} mm across; {L.S:g} mm is too "
                f"{'small' if L.S < lo else 'large'} for five {DIYA_D_MM:g} mm cups. Choose a size in that range, or one cup.",
                {"size_mm": L.S, "style": L.style, "min_size_mm": lo, "max_size_mm": hi},
            )
        if L.web < L.w - 1e-9:  # pragma: no cover - SIZE_RANGE keeps the web (asserted above)
            raise ParamOutOfRange(
                ["size_mm", "wall_mm"],
                f"Five cups in {L.S:g} mm leave only {L.web:.1f} mm between two cups; make the holder bigger or the wall thinner.",
                {"web_mm": round(L.web, 3), "wall_mm": L.w},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("rim",), thickness_mm=RIM_T_MM, noun="rim", thickness_key="wall_mm")

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        L = Layout(params)
        z_drum = BASE_MM - OVERLAP_MM
        parts = [cad.prism(L.base_outline(), BASE_MM)]
        for cx, cy in L.cups():
            parts.append(cad.cylinder(L.r_wall, L.top - z_drum, cx, cy, z_drum))
        # the rim and its 45° chamfer as one solid: a cone through the rim's edge, trimmed to the rim's drum
        z0 = L.z_chamfer - OVERLAP_MM
        h = L.top - z0
        cone = cad.translate(cad.frustum(L.r_wall - OVERLAP_MM, L.r_wall - OVERLAP_MM + h, h), dz=z0)
        parts.append(cad.intersect(cone, cad.cylinder(L.r_rim, h, 0.0, 0.0, z0)))
        body = cad.union(parts)
        slope = (L.r_mouth - L.r_floor) / L.depth
        cavities = [
            cad.translate(cad.frustum(L.r_floor, L.r_mouth + slope * OVERLAP_MM, L.depth + OVERLAP_MM), dx=cx, dy=cy, dz=BASE_MM)
            for cx, cy in L.cups()
        ]
        return cad.cut(body, cavities)

    @classmethod
    def build_body(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        mesh = cad.to_trimesh(cls.build_part(params))
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
        if L.style == "single":
            cups = (
                f"one cup for a standard {DIYA_D_MM:g} mm clay diya ({DIYA_DEPTH_MM:g} mm deep, straight outside and "
                f"tapered inside, {L.w:g} mm wall at the mouth) on a {L.S:g} mm drip plate"
            )
        elif L.style == "panch":
            cups = (
                f"five cups for standard {DIYA_D_MM:g} mm clay diyas, one in the middle and four plain drums on the "
                f"diagonals, {L.w:g} mm walls at the mouth, all on one {BASE_MM:g} mm plate"
            )
        else:
            cups = (
                f"one shallow bowl {2.0 * L.r_mouth:g} mm across and {BOWL_DEPTH_MM:g} mm deep for a floating candle and "
                f"petals, {L.w:g} mm wall at the mouth"
            )
        return (
            f"A Diya holder, {L.S:g} mm across and {L.top:g} mm tall: {cups}. The centre cup carries a {RIM_W_MM:g} mm "
            f"flat rim {RIM_T_MM:g} mm thick with a 45° chamfer underneath, so it prints standing as modelled with no "
            "supports. Heat-safe finish; the clay diya sits in the cup, never a bare flame on the plastic."
        )
