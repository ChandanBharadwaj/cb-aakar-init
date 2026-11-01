"""``ajrakh_coaster_set@1`` - Aasan: a heat-safe coaster with a raised ajrakh-style border; a motif or a monogram
sits in the field. One build is ONE coaster; a set of four is four orders (order four for a set).

Coordinate frame (mm): the coaster lies face up. X = across (the viewer's right), Y = up the picture,
Z = thickness: the flat back rests on the bed at Z = 0, the field (inside the border) is at
Z = ``thickness_mm`` and the border rim stands ``RIM_H_MM`` higher. The field is 74-92 mm across and a cup's base
is about 50-80 mm, so the cup crosses the field: the raised border is its seat, and a raised motif or monogram is
kept low (``MAX_RELIEF_MM`` = ``RIM_H_MM`` - ``RELIEF_CLEAR_MM`` = 0.8 mm), its top always under the border's, so the
cup never rocks on it. The part is centred in X and Y.

Pieces
  plate   the outline (``round``, ``square`` with ``SQUARE_CORNER_MM`` corners, or a regular ``octagon`` with
          its flats on the axes) ``size_mm`` across; the longest side of every shape is exactly ``size_mm``.
  rim     a ``border_mm`` wide band round the edge, ``RIM_H_MM`` proud of the field. With ``edge`` =
          ``notched`` a repeating scallop (``NOTCH_D_MM`` wide, half a disc deep, one every ``NOTCH_PITCH_MM``
          along the edge) is cut into the band's outer edge in plan, like the block-printed border of an
          ajrakh cloth; the plate under it stays whole.
  back    flat and plain (no feet), so the coaster prints without supports and sits flat.

Anchor
  face  the largest square-ish rectangle on the field (the outline shrunk by ``border_mm`` + ``ANCHOR_MARGIN_MM``),
        normal +Z: a motif (Buti) or a short monogram (Naam). A cut-in motif or monogram must leave
        ``plates.MIN_SKIN_MM`` of plastic under it (``validate_content``).

Hardware: none. The family is heat-safe only, so the pipeline prints it in PETG.
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
from .base import Anchor, Param, Template, TemplateConstraints

RIM_H_MM = 1.0  # the border stands this much above the field: the cup sits on the rim, not on the motif
RELIEF_CLEAR_MM = 0.2  # a raised motif's top stays this far under the border's top (a layer or so), so a cup never rocks on it
MAX_RELIEF_MM = RIM_H_MM - RELIEF_CLEAR_MM  # 0.8: raised content never stands as high as the border (caps cut-in depth too)
SQUARE_CORNER_MM = 10.0  # corner radius of the square coaster
OCTAGON_CORNER_MM = 3.0  # the octagon's eight points are softened this much (its flats stay exactly size_mm apart)
ANCHOR_MARGIN_MM = 1.5  # field kept clear between content and the foot of the rim
BLEED_MM = 1.0
NOTCH_D_MM = 2.0  # a scallop is a half disc this wide (≥ 1.2 mm so a 0.4 mm nozzle draws it) and half as deep
NOTCH_PITCH_MM = 5.0  # one scallop every 5 mm leaves 3 mm teeth of rim between them
NOTCH_SEGMENTS = 16  # a scallop is small: a coarse disc keeps the rim's edge count (and the build time) down

SHAPES = ("round", "square", "octagon")
EDGES = ("notched", "plain")


def _outline(shape: str, size: float) -> Polygon:
    """The coaster outline ``size`` across, centred on the origin, longest side exactly ``size``."""
    if shape == "round":
        return plates.disc(0.0, 0.0, size / 2.0, 144)  # a vertex on each axis: the extents are exactly the diameter
    if shape == "square":
        return plates.rounded_rect(size, size, SQUARE_CORNER_MM)
    if shape == "octagon":
        # flats parallel to the axes and size/2 from the centre, so the bounding box (what the envelope measures)
        # is size × size; the corner-to-corner span lies on the diagonals
        r = (size / 2.0) / math.cos(math.pi / 8.0)
        angles = math.pi / 8.0 + np.arange(8) * math.pi / 4.0
        octagon = Polygon(np.column_stack([r * np.cos(angles), r * np.sin(angles)]))
        return plates.soften(octagon, OCTAGON_CORNER_MM)
    raise ValueError(f"unknown shape {shape!r}")


class Layout:
    """Derived dimensions shared by ``build_body``, ``validate_combination``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.shape = str(p["shape"])
        self.S = float(p["size_mm"])
        self.T = float(p["thickness_mm"])
        self.border = float(p["border_mm"])
        self.edge = str(p["edge"])
        self.outline: Polygon = _outline(self.shape, self.S)
        self.field: Polygon = plates.largest_polygon(self.outline.buffer(-self.border))
        self.rim: Polygon = self.outline.difference(self.field)
        self.notch_count = 0
        if self.edge == "notched":
            ring = self.outline.exterior
            self.notch_count = max(8, int(round(ring.length / NOTCH_PITCH_MM)))
            scallops = []
            for i in range(self.notch_count):
                pt = ring.interpolate(i / self.notch_count, normalized=True)
                scallops.append(plates.disc(pt.x, pt.y, NOTCH_D_MM / 2.0, NOTCH_SEGMENTS))
            self.rim = plates.largest_polygon(self.rim.difference(unary_union(scallops)))

    @property
    def top_mm(self) -> float:
        """Height of the rim above the bed."""
        return self.T + RIM_H_MM

    def printable_rect(self) -> tuple[float, float, float, float] | None:
        """The content rectangle on the field, ``ANCHOR_MARGIN_MM`` clear of the rim's foot: the largest square-ish
        one (a motif or a monogram, not a 4:3 photo)."""
        region = self.outline.buffer(-(self.border + ANCHOR_MARGIN_MM))
        return plates.symmetric_rect(region, aspect=1.0)

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        rect = self.printable_rect()
        if rect is None:  # pragma: no cover - validate_combination refuses these
            raise ParamOutOfRange(["size_mm", "border_mm"], "This coaster has no field left for a motif")
        x0, y0, x1, y1 = rect
        return {
            "face": AnchorFrame(
                np.array([(x0 + x1) / 2.0, (y0 + y1) / 2.0, self.T]), [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=(x1 - x0, y1 - y0)
            ),
        }


PARAMS = {
    "shape": Param("enum", "Shape", "round", options=SHAPES, group="Shape"),
    "edge": Param(
        "enum", "Border edge", "notched", options=EDGES, group="Shape",
        description="A scalloped ajrakh-style edge on the raised border, or a plain one.",
    ),
    "size_mm": Param("number", "Size", 95.0, "mm", 90, 100, 1, handle=True, group="Size", description="Across the coaster."),
    "thickness_mm": Param("number", "Thickness", 5.0, "mm", 4.0, 6.0, 0.1, group="Size", description="The field; the border stands 1 mm higher."),
    "border_mm": Param("number", "Border", 6.0, "mm", 4, 8, 0.5, group="Details", description="Width of the raised border."),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class AjrakhCoasterSet(Template):
    id = "ajrakh_coaster_set"
    version = 1
    family = "coaster"
    name = "Aasan ajrakh coaster"
    description = (
        "A heat-safe coaster with a raised ajrakh-style border and your motif or monogram in the field, "
        "printed in PETG so a hot cup does not mark it. The cup sits on the border, so a raised motif stays low "
        "(at most 0.8 mm, under the 1 mm border); a cut-in one is flush. This is one coaster; order four for a set."
    )
    environment = "kitchen_marble"
    params = PARAMS
    anchors = (
        Anchor(
            "face", "Face", "planar", size_mm=_size("face"), bleed_mm=BLEED_MM,
            accepts=("motif", "emboss_text"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=45, bed_mm=(250, 250, 250))
    features_supported = ("motif", "emboss_text")
    hardware = ()
    min_feature_mm = 0.8

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        layout = Layout(params)
        rect = layout.printable_rect()
        w = h = 0.0
        if rect is not None:
            w, h = rect[2] - rect[0], rect[3] - rect[1]
        if min(w, h) < plates.MIN_PRINTABLE_MM:
            raise ParamOutOfRange(
                ["size_mm", "border_mm"],
                (
                    f"A {layout.S:g} mm {layout.shape} coaster with a {layout.border:g} mm border leaves too little field "
                    f"for a motif ({w:.0f} × {h:.0f} mm); make it bigger or the border narrower."
                ),
                {"printable_mm": [round(w, 2), round(h, 2)], "min_printable_mm": plates.MIN_PRINTABLE_MM},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("face",), thickness_mm=float(params["thickness_mm"]), noun="coaster")

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        layout = Layout(params)
        plate = cad.prism(layout.outline, layout.T)
        rim = cad.translate(cad.prism(layout.rim, RIM_H_MM), dz=layout.T)
        return cad.union([plate, rim])

    @classmethod
    def build_body(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        mesh = cad.to_trimesh(cls.build_part(params))
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
        shape = {"round": "round", "square": "square", "octagon": "eight-sided"}[L.shape]
        edge = "scalloped along its edge" if L.edge == "notched" else "with a plain edge"
        return (
            f"A {shape} Aasan coaster, {L.S:g} mm across and {L.T:g} mm thick, with a {L.border:g} mm border standing "
            f"{RIM_H_MM:g} mm proud of the field, {edge}. This is one coaster; order four for a set. Print it in the "
            "heat-safe finish, face up with no supports, and give the field a clean top layer; a cork pad on the back is optional."
        )
