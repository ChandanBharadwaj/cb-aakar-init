"""``pillar_headphone_stand@1`` - Sur: a fully printed headphone stand, the whole-print sibling of the Sur Jod topper.

Coordinate frame (mm): X across, Y depth (−Y is the front), Z up. The stand rests on Z = 0 and is centred in X and Y
on its pillar; printed standing as modelled.

Pieces
  foot    a D-shaped disc ``foot_d_mm`` across (a flat front facet ``FLAT_SHARE`` of the diameter wide, so the name has a
          flat face), ``FOOT_H_MM`` thick.
  pillar  a rounded rectangle ``PILLAR_W_MM`` × ``PILLAR_D_MM`` from the foot to the saddle.
  saddle  the same crown as the topper (``saddle.Saddle``): ``saddle_width_mm`` across, ``saddle_depth_mm`` deep,
          ``SADDLE_H_MM`` tall, its apex at ``height_mm``.

Anchors
  base_front  surface: the foot's flat front (normal −Y): a short name (Naam) or a motif (Buti).
  pillar      surface: the pillar's front face (normal −Y, u = up the pillar): a name that runs up the stand.
No hardware. The compare card pairs it with ``headphone_topper`` (``pair_family_id``): same crown, 540 printer-minutes
against 157 plus a bought-in stand.
"""

from __future__ import annotations

import math
from typing import Any, Mapping

import numpy as np
import trimesh
from shapely import affinity
from shapely.geometry import Polygon, box

from .. import cad
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, Param, Template, TemplateConstraints
from .saddle import Saddle

FOOT_H_MM = 8.0
FLAT_SHARE = 0.6
PILLAR_W_MM = 20.0
PILLAR_D_MM = 14.0
PILLAR_CORNER_MM = 4.0
SADDLE_H_MM = 38.0
MARGIN_MM = 1.5
PILLAR_MARGIN_MM = 5.0
BLEED_MM = 0.5
MAX_RELIEF_MM = 1.5


def _foot_outline(D: float) -> Polygon:
    R = D / 2.0
    disc = plates.disc(0.0, 0.0, R, 192)
    flat_y = -R * math.sqrt(1.0 - FLAT_SHARE**2)
    raw = plates.largest_polygon(disc.intersection(box(-R - 1.0, flat_y, R + 1.0, R + 1.0)))
    x0, y0, x1, y1 = raw.bounds
    return affinity.translate(raw, -(x0 + x1) / 2.0, -(y0 + y1) / 2.0)


class Layout:
    def __init__(self, p: Mapping[str, Any]):
        self.H = float(p["height_mm"])
        self.foot_d = float(p["foot_d_mm"])
        self.sw = float(p["saddle_width_mm"])
        self.sd = float(p["saddle_depth_mm"])
        self.foot: Polygon = _foot_outline(self.foot_d)
        self.front_y = float(self.foot.bounds[1])
        edge = self.foot.intersection(box(-self.foot_d, self.front_y - 1e-6, self.foot_d, self.front_y + 1e-6))
        self.front_w = float(edge.bounds[2] - edge.bounds[0])
        self.pillar_top = self.H - SADDLE_H_MM
        self.pillar: Polygon = plates.rounded_rect(PILLAR_W_MM, PILLAR_D_MM, PILLAR_CORNER_MM)
        self.saddle = Saddle(self.sw, self.sd, SADDLE_H_MM)

    @property
    def pillar_len(self) -> float:
        return self.pillar_top - FOOT_H_MM

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        return {
            "base_front": AnchorFrame(
                np.array([0.0, self.front_y, FOOT_H_MM / 2.0]), [1, 0, 0], [0, 0, 1], [0, -1, 0],
                size_mm=(self.front_w - 2.0 * MARGIN_MM, FOOT_H_MM - 2.0 * BLEED_MM),
            ),
            "pillar": AnchorFrame(
                np.array([0.0, -PILLAR_D_MM / 2.0, (FOOT_H_MM + self.pillar_top) / 2.0]), [0, 0, 1], [-1, 0, 0], [0, -1, 0],
                size_mm=(self.pillar_len - 2.0 * PILLAR_MARGIN_MM, PILLAR_W_MM - 2.0 * MARGIN_MM),
            ),
        }


PARAMS = {
    "height_mm": Param("number", "Height", 240.0, "mm", 200, 250, 5, handle=True, group="Size", description="To the crown's apex; the bed is 250 mm."),
    "foot_d_mm": Param("number", "Foot", 120.0, "mm", 100, 140, 5, group="Size", description="Across the foot."),
    "saddle_width_mm": Param("number", "Saddle width", 70.0, "mm", 60, 90, 1, group="Shape", description="Across the saddle; the headband rests along it."),
    "saddle_depth_mm": Param("number", "Saddle depth", 40.0, "mm", 30, 50, 1, group="Shape"),
}

_DEFAULT_LAYOUT = Layout({k: p.default for k, p in PARAMS.items()})
_DEFAULT_FRAMES = _DEFAULT_LAYOUT.anchor_frames()


class PillarHeadphoneStand(Template):
    id = "pillar_headphone_stand"
    version = 1
    family = "headphone_stand"
    name = "Sur pillar headphone stand"
    description = (
        "A fully printed headphone stand: a D-shaped foot, a slim pillar and the same crown as the Sur Jod topper. "
        "A name runs up the pillar or sits on the foot's front."
    )
    environment = "desk_oak"
    params = PARAMS
    anchors = (
        Anchor("base_front", "Front of the foot", "planar", size_mm=tuple(round(s, 1) for s in _DEFAULT_FRAMES["base_front"].size_mm),  # type: ignore[arg-type]
               bleed_mm=BLEED_MM, accepts=("emboss_text", "motif"), max_relief_mm=MAX_RELIEF_MM),
        Anchor("pillar", "Up the pillar", "planar", size_mm=tuple(round(s, 1) for s in _DEFAULT_FRAMES["pillar"].size_mm),  # type: ignore[arg-type]
               bleed_mm=BLEED_MM, accepts=("emboss_text", "motif"), max_relief_mm=MAX_RELIEF_MM),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    style_variants = ("comic_pop",)
    features_supported = ("emboss_text", "motif")
    hardware = ()
    min_feature_mm = 0.8

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        L = Layout(params)
        foot = cad.prism(L.foot, FOOT_H_MM)
        pillar = cad.translate(cad.prism(L.pillar, L.pillar_len + 1.0), dz=FOOT_H_MM - 0.5)
        saddle = cad.translate(L.saddle.part(L.sd / 2.0), dz=L.pillar_top)
        return cad.union([foot, pillar, saddle])

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
        return (
            f"A Sur headphone stand, {L.H:g} mm tall on a {L.foot_d:g} mm foot, with a {L.sw:g} mm saddle; printed standing as "
            f"modelled, no supports (the crown is a gentle arc). About nine printer-hours: the Sur Jod topper on a bought-in "
            f"stand is the quicker form of the same idea."
        )
