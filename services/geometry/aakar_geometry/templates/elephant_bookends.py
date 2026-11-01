"""``elephant_bookends@1`` - Kitaab: one elephant bookend, an L of a weighted base plate and an upright elephant in profile.

Coordinate frame (mm): X across, Y depth (−Y is the front), Z up. The bookend rests on its base at Z = 0, is centred in
X and Y, and is printed standing as modelled: no supports (every downward-facing edge of the silhouette is steeper than
45°), and the sand cavity's roof bridges at most ``MAX_BRIDGE_MM``.

One build is one bookend. ``side`` mirrors it in X so a pair (two builds, one ``left`` and one ``right``) has both
elephants facing the room: ``left`` has its trunk toward +X, ``right`` toward −X.

Pieces
  base     a plate ``BASE_W`` (= the silhouette's width) × ``depth_mm`` along Y × ``BASE_MM`` thick; the books stand on its
           back part (+Y of the upright). Its underside is a sand cavity open to the bed: ``CAVITY_H_MM`` deep under a
           ``ROOF_MM`` roof, ``CAVITY_WALL_MM`` walls, split along Y by ``RIB_MM`` ribs so no cell is wider than
           ``MAX_BRIDGE_MM`` (the roof prints as a bridge over each cell).
  upright  the elephant: a softened polygon in the X–Z plane (body, domed head, trunk down to the base, a pointed notch
           for the belly and one between the front leg and the trunk), ``WIDTH_RATIO`` as wide as it is tall, extruded
           ``thickness_mm`` along +Y from the base's front edge and sunk ``OVERLAP_MM`` into the base. Its top is
           ``height_mm``.

Anchor
  face  surface, normal −Y: the upright's outer flank (the side away from the books). The largest rectangle centred on
        X = 0 inside the silhouette (above the base) shrunk ``FACE_MARGIN_MM``: a short name (Naam) or a motif (Buti),
        raised or cut in. A cut-in mark leaves the upright's thickness less its depth (``validate_content``).

No hardware. The longest extent is ``height_mm`` (or ``depth_mm`` when that is larger), inside the family's 120–200 mm.
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely import affinity
from shapely.geometry import Polygon, box

from .. import cad
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

BASE_MM = 12.0  # the base plate
ROOF_MM = 3.0  # plastic over the sand cavity
CAVITY_H_MM = BASE_MM - ROOF_MM
CAVITY_WALL_MM = 3.0  # the cavity's outer walls
RIB_MM = 3.0  # ribs that split the cavity along Y
MAX_BRIDGE_MM = 40.0  # widest roof span over one cavity cell
OVERLAP_MM = 1.0  # the upright reaches this far into the base so no boolean meets a coplanar face
WIDTH_RATIO = 0.85  # the silhouette's width over its height (above the base): narrower than tall
SOFTEN_MM = 3.0  # convex corners of the silhouette rounded to this radius
FACE_MARGIN_MM = 2.0
BLEED_MM = 0.5
MAX_RELIEF_MM = 1.5
SIDES = ("left", "right")

# the elephant in unit coordinates, trunk toward +X: x across 0..1 (× the width), z up 0..1 (× the height above the base);
# the feet run below z = 0 so the rounding at the bottom is trimmed off inside the base. Counter-clockwise from the rear foot.
_UNIT = [
    (0.00, -0.08), (0.27, -0.08),
    (0.43, 0.20),  # the belly: a pointed notch, its sides 58° from the bed
    (0.59, -0.08), (0.74, -0.08),
    (0.80, 0.28),  # between the front leg and the trunk
    (0.86, -0.08), (1.00, -0.08),
    (1.00, 0.04), (0.95, 0.12),  # the trunk's tip curls forward
    (0.93, 0.40), (0.93, 0.62),  # the trunk rises into the forehead
    (0.88, 0.80), (0.80, 0.93), (0.70, 1.00), (0.60, 0.99),  # the domed head
    (0.53, 0.92),  # the dip behind the head
    (0.44, 0.93), (0.30, 0.91), (0.17, 0.85),  # the back
    (0.08, 0.74), (0.03, 0.55), (0.00, 0.30),  # the rump down to the rear leg
]


class Layout:
    """Derived dimensions shared by ``build_part``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.side = str(p["side"])
        self.H = float(p["height_mm"])
        self.D = float(p["depth_mm"])
        self.t = float(p["thickness_mm"])
        self.hp = self.H - BASE_MM  # the elephant's height above the base
        self.W = WIDTH_RATIO * self.hp  # its width = the base's width
        self.front_y = -self.D / 2.0
        # the sand cavity: cells along Y, each roof span at most MAX_BRIDGE_MM
        span = self.D - 2.0 * CAVITY_WALL_MM
        self.cells = max(1, math.ceil((span + RIB_MM) / (MAX_BRIDGE_MM + RIB_MM)))
        self.cell_d = (span - (self.cells - 1) * RIB_MM) / self.cells
        self.cell_w = self.W - 2.0 * CAVITY_WALL_MM

    def profile(self) -> Polygon:
        """The silhouette in world X–Z (as shapely x, y), sunk OVERLAP_MM into the base."""
        sign = 1.0 if self.side == "left" else -1.0
        raw = Polygon([(sign * (x - 0.5) * self.W, BASE_MM + z * self.hp) for x, z in _UNIT])
        if not raw.exterior.is_ccw:
            raw = Polygon(list(raw.exterior.coords)[::-1])
        soft = plates.soften(raw, SOFTEN_MM)
        top = float(soft.bounds[3])  # rounding the dome lowers the top a hair: stretch it back to height_mm
        soft = affinity.scale(soft, 1.0, (self.H - BASE_MM) / (top - BASE_MM), origin=(0.0, BASE_MM))
        return plates.largest_polygon(soft.intersection(box(-self.W, BASE_MM - OVERLAP_MM, self.W, self.H + 1.0)))

    def cell_centres(self) -> list[float]:
        y0 = self.front_y + CAVITY_WALL_MM
        return [y0 + self.cell_d / 2.0 + i * (self.cell_d + RIB_MM) for i in range(self.cells)]

    def face_rect(self) -> tuple[float, float, float, float]:
        above = self.profile().intersection(box(-self.W, BASE_MM, self.W, self.H + 1.0))
        rect = plates.symmetric_rect(above.buffer(-FACE_MARGIN_MM), aspect=None)
        assert rect is not None
        return rect

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        x0, z0, x1, z1 = self.face_rect()
        return {
            "face": AnchorFrame(
                np.array([(x0 + x1) / 2.0, self.front_y, (z0 + z1) / 2.0]), [1, 0, 0], [0, 0, 1], [0, -1, 0],
                size_mm=(x1 - x0, z1 - z0),
            )
        }


PARAMS = {
    "side": Param(
        "enum", "Side", "left", options=SIDES, group="Shape",
        description="Left or right end of the row: the two are mirror images, so a pair is one of each.",
    ),
    "height_mm": Param("number", "Height", 160.0, "mm", 120, 200, 5, handle=True, group="Size", description="To the top of the elephant's head."),
    "depth_mm": Param(
        "number", "Base depth", 110.0, "mm", 90, 130, 5, group="Size",
        description="Front to back of the base plate; the books stand on the part behind the elephant.",
    ),
    "thickness_mm": Param("number", "Thickness", 24.0, "mm", 20, 30, 1, group="Details", description="Of the upright elephant."),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class ElephantBookends(Template):
    id = "elephant_bookends"
    version = 1
    family = "bookend"
    name = "Elephant bookend"
    description = (
        "One elephant bookend: an upright elephant in profile on a base plate with a sand cavity underneath. Print a "
        "left and a right for a pair. A name or a motif sits on the elephant's outer flank."
    )
    environment = "teak_table_candlelight"
    params = PARAMS
    anchors = (
        Anchor(
            "face", "Elephant's flank", "planar", size_mm=_size("face"), bleed_mm=BLEED_MM,
            accepts=("emboss_text", "motif"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.6, max_overhang_deg=45, bed_mm=(250, 250, 250))
    features_supported = ("emboss_text", "motif")
    hardware: tuple[HardwareRef, ...] = ()
    min_feature_mm = 0.8

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("face",), thickness_mm=float(params["thickness_mm"]), noun="elephant")

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        L = Layout(params)
        base = cad.box(L.W, L.D, BASE_MM)
        upright = cad.translate(cad.rotate_x(cad.prism(L.profile(), L.t), 90.0), dy=L.front_y + L.t)
        cells = [cad.box(L.cell_w, L.cell_d, CAVITY_H_MM + 1.0, 0.0, y, -1.0) for y in L.cell_centres()]
        return cad.union([cad.cut(base, cells), upright])

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
        other = "right" if L.side == "left" else "left"
        return (
            f"One elephant bookend (the {L.side} one), {L.H:g} mm tall: a {L.t:g} mm thick elephant standing on a "
            f"{L.W:.0f} × {L.D:g} mm base. A pair is two builds: print the {other} one too, its mirror image. Fill the "
            f"cavity under the base ({L.cells} cell{'s' if L.cells > 1 else ''}, {CAVITY_H_MM:g} mm deep) with sand or "
            "coins and tape it shut. Print standing as modelled; no supports."
        )
