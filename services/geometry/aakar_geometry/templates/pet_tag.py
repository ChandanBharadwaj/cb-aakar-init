"""``pet_tag@1`` - Saathi Pet: a pet's name tag, a bone or a disc on a split ring (the keychain family's second template).

Coordinate frame (mm): the tag lies face up. X = across (the viewer's right), Y = up the picture (the ring
hole is at the top, +Y), Z = thickness: the back rests on the bed at Z = 0 and the face is at
Z = ``thickness_mm``. The part is centred in X and Y.

Pieces
  bone  a dog bone: a bar between two double-lobed ends, ``size_mm`` from end to end (its longest side).
        A round tab in the notch between the top lobes carries the ring hole, like the Saathi keychain's loop.
  disc  a round tag ``size_mm`` across; the ring hole sits inside it at the top.
  hole  ``HOLE_D_MM`` (4 mm) finished, cut 0.2 mm larger because printed holes close up, with ``RIM_MM``
        (2 mm) of solid plastic all round: what a 25 mm split ring's 1.2 mm wire needs.

Anchors (text first: the pet's name on the face, a name or a number on the back; a motif or a photo also
takes either face where it fits, the lettering and motif rules refuse what would print too fine)
  face  the largest ``TEXT_ASPECT`` (2:1, a name is wider than tall) rectangle on the top face,
        ``ANCHOR_MARGIN_MM`` inside the edge and clear of the hole and its rim (normal +Z).
  back  the same rectangle on the underside, seen with the tag turned over left to right, so the hole stays
        at the top of the picture (normal −Z).
A cut-in name, motif or photo must leave ``MIN_SKIN_MM`` of plastic behind it; cut-ins on both faces add up.
Tags of 25–29 mm sit below the keychain family's envelope (families.json: 30–60 mm), whose floor should come
down to 25 mm for pet tags.
"""

from __future__ import annotations

import math
from functools import lru_cache
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely import affinity
from shapely.geometry import Polygon, box
from shapely.ops import unary_union

from .. import cad
from ..errors import ParamOutOfRange
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

HOLE_D_MM = 4.0  # finished ring hole
ANCHOR_MARGIN_MM = 1.0  # plastic kept clear between content and the edge (or the hole's rim)
BLEED_MM = 0.5
MAX_RELIEF_MM = 1.2  # a name's default depth; 1.2 mm cut into a 3 mm tag still leaves 1.8 mm
TEXT_ASPECT = 2.0

SHAPES = ("bone", "disc")


@lru_cache(maxsize=None)
def bone_unit() -> Polygon:
    """A dog bone one unit long: a bar 0.44 tall between two pairs of lobes (radius 0.18), 0.66 tall at the ends."""
    bar = box(-0.34, -0.22, 0.34, 0.22)
    lobes = [plates.disc(sx * 0.32, sy * 0.15, 0.18, 96) for sx in (-1, 1) for sy in (-1, 1)]
    return plates.largest_polygon(unary_union([bar, *lobes]))


class Layout:
    """Derived dimensions shared by ``build_body``, ``validate_combination``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.shape = str(p["shape"])
        self.S = float(p["size_mm"])
        self.T = float(p["thickness_mm"])
        self.hole_cut = HOLE_D_MM + plates.HOLE_ALLOWANCE_MM
        segs = plates.CIRCLE_SEGMENTS
        self.hole_outer = (self.hole_cut / 2.0) / math.cos(math.pi / segs)  # the hole polygon's corners
        self.keep_r = self.hole_outer + plates.RIM_MM  # the rim is exactly RIM_MM from the hole's corners
        if self.shape == "bone":
            self._bone()
        elif self.shape == "disc":
            self._disc()
        else:  # pragma: no cover - Param validation refuses it
            raise ValueError(f"unknown shape {self.shape!r}")
        self.hole_poly = plates.hole(self.hole_xy[0], self.hole_xy[1], self.hole_cut)
        self.profile: Polygon = self.outline.difference(self.hole_poly)

    def _bone(self) -> None:
        unit = bone_unit()
        k = self.S / (unit.bounds[2] - unit.bounds[0])  # end to end is the longest side
        plate = affinity.scale(unit, k, k, origin=(0.0, 0.0))
        plate_top = plates.top_at(plate, 0.0)
        assert plate_top is not None
        loop_r = self.keep_r
        hole_y = plate_top + loop_r
        tab = box(-loop_r, plate_top - loop_r, loop_r, hole_y)
        loop = plates.hole(0.0, hole_y, 2.0 * loop_r).union(tab)
        outline = plates.largest_polygon(plate.union(loop))
        dy = -(outline.bounds[1] + outline.bounds[3]) / 2.0
        self.outline: Polygon = affinity.translate(outline, 0.0, dy)
        self.hole_xy = (0.0, hole_y + dy)

    def _disc(self) -> None:
        R = self.S / 2.0
        self.outline = plates.disc(0.0, 0.0, R, 144)
        inner_r = R * math.cos(math.pi / 144)  # the polygon's edges sit this far out
        self.hole_xy = (0.0, inner_r - self.keep_r)

    @property
    def extents(self) -> tuple[float, float]:
        x0, y0, x1, y1 = self.outline.bounds
        return (x1 - x0, y1 - y0)

    def printable_rect(self) -> tuple[float, float, float, float] | None:
        """``(x0, y0, x1, y1)`` of the text area: inside the edge by the margin, clear of the hole and its rim."""
        keep_out = plates.disc(self.hole_xy[0], self.hole_xy[1], self.keep_r + ANCHOR_MARGIN_MM)
        region = self.outline.buffer(-ANCHOR_MARGIN_MM).difference(keep_out)
        return plates.symmetric_rect(region, aspect=TEXT_ASPECT)

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        rect = self.printable_rect()
        if rect is None:  # pragma: no cover - every size in range has room
            raise ParamOutOfRange(["size_mm"], "This tag has no room left for a name")
        x0, y0, x1, y1 = rect
        size = (x1 - x0, y1 - y0)
        xc, yc = (x0 + x1) / 2.0, (y0 + y1) / 2.0
        return {
            "face": AnchorFrame(np.array([xc, yc, self.T]), [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=size),
            # turned over left to right: the hole stays up, the viewer's right is −X
            "back": AnchorFrame(np.array([xc, yc, 0.0]), [-1, 0, 0], [0, 1, 0], [0, 0, -1], size_mm=size),
        }


PARAMS = {
    "shape": Param("enum", "Shape", "bone", options=SHAPES, group="Shape"),
    "size_mm": Param(
        "number", "Size", 30.0, "mm", 25, 35, 1, handle=True, group="Size",
        description="Longest side: end to end for a bone, across for a disc.",
    ),
    "thickness_mm": Param("number", "Thickness", 3.5, "mm", 3.0, 4.0, 0.1, group="Size"),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class PetTag(Template):
    id = "pet_tag"
    version = 1
    family = "keychain"
    name = "Saathi pet tag"
    description = (
        "A bone or round name tag for your pet's collar, with a steel split ring. The name stands on the face "
        "and a second line can go on the back; a small motif or photo fits too."
    )
    environment = "studio"
    params = PARAMS
    anchors = (
        Anchor(
            "face", "Face", "planar", size_mm=_size("face"), bleed_mm=BLEED_MM,
            accepts=("emboss_text", "motif", "relief_image"), max_relief_mm=MAX_RELIEF_MM,
        ),
        Anchor(
            "back", "Back", "planar", size_mm=_size("back"), bleed_mm=BLEED_MM,
            accepts=("emboss_text", "motif", "relief_image"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    style_variants = ("comic_pop",)  # Katha's comic-book look: names and motifs raised and bolder by default (features.styles)
    features_supported = ("emboss_text", "motif", "relief_image")
    hardware = (HardwareRef("split_ring_25", 1),)
    min_feature_mm = 0.8

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        rect = Layout(params).printable_rect()
        w = h = 0.0
        if rect is not None:
            w, h = rect[2] - rect[0], rect[3] - rect[1]
        if min(w, h) < plates.MIN_PRINTABLE_MM:  # pragma: no cover - every shape and size in range has room
            raise ParamOutOfRange(
                ["size_mm"],
                f"A {params['size_mm']:g} mm {params['shape']} tag leaves too little room for a name ({w:.0f} × {h:.0f} mm); make it bigger.",
                {"printable_mm": [round(w, 2), round(h, 2)], "min_printable_mm": plates.MIN_PRINTABLE_MM},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("face", "back"), thickness_mm=float(params["thickness_mm"]), noun="tag")

    @classmethod
    def build_body(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        layout = Layout(params)
        mesh = cad.to_trimesh(cad.prism(layout.profile, layout.T))
        mesh.apply_translation([0.0, 0.0, -float(mesh.bounds[0][2])])  # exactly on Z = 0
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
        shape = {"bone": "bone-shaped", "disc": "round"}[L.shape]
        return (
            f"A {shape} Saathi pet tag, {L.S:g} mm on its longest side and {L.T:g} mm thick, with a {HOLE_D_MM:g} mm "
            f"ring hole (cut {L.hole_cut:.1f} mm, since printed holes close up a little) and a {plates.RIM_MM:g} mm rim "
            "around it. Print it in PETG if you have it (a collar tag lives outdoors and gets chewed); PLA softens "
            "in a hot car. Thread the 25 mm steel split ring through the hole before packing."
        )
