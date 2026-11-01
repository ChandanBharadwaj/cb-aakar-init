"""``bookmark_plate@1`` - Nishaan: a slim plate with a tassel hole at the top; a photo, a name or a motif sits in relief on it.

Coordinate frame (mm): the bookmark lies flat, face up. X = across (the viewer's right), Y = along the
length (the tassel hole is at +Y), Z = thickness: the back rests on the bed at Z = 0 and the face is at
Z = ``thickness_mm``. The part is centred in X and Y, so its longest side is exactly ``length_mm``.

Pieces
  plate  the outline, ``width_mm`` × ``length_mm``, square at the bottom. The top ``end`` is ``square``
         (plain), ``rounded`` (a semicircle as wide as the plate) or ``pointed`` (an equilateral Gothic
         arch, its tip softened to ``TIP_SOFTEN_MM``); every end fits inside ``length_mm``.
  hole   the tassel hole, ``HOLE_D_MM`` cut ``HOLE_ALLOWANCE_MM`` larger because printed holes close up a
         little, on the centre line as high as it goes while keeping ``RIM_MM`` (2 mm) of solid plastic
         all round, whatever the end.

Anchor
  face  the largest rectangle on the top face below the hole, ``ANCHOR_MARGIN_MM`` inside the edge and
        clear of the hole's rim by the same margin (normal +Z). It reads along the length of the
        bookmark (u = +Y, the tassel to the reader's right), so a name runs down the plate the way a
        bookmark is read: a photo, or a name (Naam) and/or a motif (Buti). A cut-in (deboss) photo, name
        or motif must leave ``MIN_SKIN_MM`` of plastic behind it (``validate_content``).

Hardware: none; the customer ties on their own tassel or ribbon.
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely import affinity
from shapely.geometry import Polygon, box

from .. import cad
from ..errors import ParamOutOfRange
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, Param, Template, TemplateConstraints

HOLE_D_MM = 4.0  # finished tassel hole: a cord or a ribbon's loop passes, a 2 mm rim stays
ANCHOR_MARGIN_MM = 1.5  # plastic kept clear between the content and the edge (or the hole's rim)
BLEED_MM = 1.0
MAX_RELIEF_MM = 1.5
TIP_SOFTEN_MM = 1.5  # the pointed end's tip is rounded to this radius: a sharp tip chips and pricks
ARCH_SEGMENTS = 48  # points along each arc of the pointed end
CAP_OVERLAP_MM = 5.0  # the pointed cap reaches this far down into the plate so softening never notches the sides

ENDS = ("square", "rounded", "pointed")


def _outline(end: str, w: float, length: float) -> Polygon:
    """The plate outline, ``w`` wide and exactly ``length`` tall, centred on the origin, the shaped end at +Y."""
    if end == "square":
        return box(-w / 2.0, -length / 2.0, w / 2.0, length / 2.0)
    if end == "rounded":
        body = box(-w / 2.0, -length / 2.0, w / 2.0, length / 2.0 - w / 2.0)
        cap = plates.disc(0.0, length / 2.0 - w / 2.0, w / 2.0, 144)
        return plates.largest_polygon(body.union(cap))
    if end == "pointed":
        # an equilateral arch: two arcs of radius w centred on the opposite springing corners, meeting at
        # the tip w·√3/2 above them. The tip's interior angle is 120°, so softening it to r pulls it down by
        # r / sin 60° − r; the arch is drawn that much taller and the result scaled to exactly ``length``.
        r = TIP_SOFTEN_MM
        overshoot = r / math.sin(math.radians(60.0)) - r
        rise = w * math.sqrt(3.0) / 2.0
        spring = length / 2.0 + overshoot - rise
        pts: list[tuple[float, float]] = [(-w / 2.0, spring - CAP_OVERLAP_MM), (w / 2.0, spring - CAP_OVERLAP_MM), (w / 2.0, spring)]
        left_arc = np.linspace(0.0, math.pi / 3.0, ARCH_SEGMENTS + 1)  # from the right corner up to the tip
        pts += [(-w / 2.0 + w * math.cos(a), spring + w * math.sin(a)) for a in left_arc]
        right_arc = np.linspace(2.0 * math.pi / 3.0, math.pi, ARCH_SEGMENTS + 1)[1:]  # from the tip down to the left corner
        pts += [(w / 2.0 + w * math.cos(a), spring + w * math.sin(a)) for a in right_arc]
        cap = plates.soften(Polygon(pts), r)
        body = box(-w / 2.0, -length / 2.0, w / 2.0, spring)
        outline = plates.largest_polygon(body.union(cap))
        x0, y0, x1, y1 = outline.bounds
        outline = affinity.scale(outline, 1.0, length / (y1 - y0), origin=(0.0, 0.0))
        x0, y0, x1, y1 = outline.bounds
        return affinity.translate(outline, 0.0, -(y0 + y1) / 2.0)
    raise ValueError(f"unknown end {end!r}")


class Layout:
    """Derived dimensions shared by ``build_body``, ``validate_combination``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.end = str(p["end"])
        self.L = float(p["length_mm"])
        self.W = float(p["width_mm"])
        self.T = float(p["thickness_mm"])
        self.hole_cut = HOLE_D_MM + plates.HOLE_ALLOWANCE_MM
        self.hole_outer = (self.hole_cut / 2.0) / math.cos(math.pi / plates.CIRCLE_SEGMENTS)  # the hole polygon's corners
        self.outline: Polygon = _outline(self.end, self.W, self.L)
        # the hole sits as high as the end allows with exactly RIM_MM of plastic to the nearest edge
        hole_y = plates.top_at(self.outline.buffer(-(plates.RIM_MM + self.hole_outer)), 0.0)
        if hole_y is None:  # pragma: no cover - every in-range plate has room for the hole
            raise ParamOutOfRange(["width_mm"], "This bookmark is too narrow for its tassel hole")
        self.hole_xy = (0.0, float(hole_y))
        self.hole_poly = plates.hole(0.0, self.hole_xy[1], self.hole_cut)
        self.profile: Polygon = self.outline.difference(self.hole_poly)

    @property
    def extents(self) -> tuple[float, float]:
        x0, y0, x1, y1 = self.outline.bounds
        return (x1 - x0, y1 - y0)

    @property
    def hole_band_y(self) -> float:
        """Below this Y the face is clear of the hole, its rim and the content margin."""
        return self.hole_xy[1] - self.hole_outer - plates.RIM_MM - ANCHOR_MARGIN_MM

    def printable_rect(self) -> tuple[float, float, float, float] | None:
        """``(x0, y0, x1, y1)`` of the content area: inside the edge by the margin and wholly below the hole band."""
        band = box(-self.W, self.hole_band_y, self.W, self.L)
        region = self.outline.buffer(-ANCHOR_MARGIN_MM).difference(band)
        return plates.symmetric_rect(region, aspect=None)

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        rect = self.printable_rect()
        if rect is None:  # pragma: no cover - validate_combination refuses these
            raise ParamOutOfRange(["length_mm", "width_mm"], "This bookmark has no room left for a picture")
        x0, y0, x1, y1 = rect
        xc, yc = (x0 + x1) / 2.0, (y0 + y1) / 2.0
        # read along the length: u = +Y (the tassel to the reader's right), v = −X, normal +Z
        return {
            "face": AnchorFrame(np.array([xc, yc, self.T]), [0, 1, 0], [-1, 0, 0], [0, 0, 1], size_mm=(y1 - y0, x1 - x0)),
        }


PARAMS = {
    "end": Param("enum", "Top end", "rounded", options=ENDS, group="Shape", description="The shape of the end with the tassel hole."),
    "length_mm": Param("number", "Length", 140.0, "mm", 120, 160, 1, handle=True, group="Size"),
    "width_mm": Param("number", "Width", 32.0, "mm", 28, 40, 1, group="Size"),
    "thickness_mm": Param("number", "Thickness", 2.4, "mm", 2.0, 3.0, 0.1, group="Size"),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class BookmarkPlate(Template):
    id = "bookmark_plate"
    version = 1
    family = "bookmark"
    name = "Nishaan bookmark"
    description = (
        "A slim plate that marks your page, with a hole at the top for a tassel or a ribbon. Your photo, "
        "name or motif sits in relief along its face."
    )
    environment = "studio"
    params = PARAMS
    anchors = (
        Anchor(
            "face", "Face", "planar", size_mm=_size("face"), bleed_mm=BLEED_MM,
            accepts=("relief_image", "emboss_text", "motif"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    features_supported = ("relief_image", "emboss_text", "motif")
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
                ["length_mm", "width_mm"],
                (
                    f"A {layout.L:g} × {layout.W:g} mm {layout.end} bookmark leaves too little room below the tassel hole "
                    f"for a picture ({w:.0f} × {h:.0f} mm); make it longer or wider."
                ),
                {"printable_mm": [round(w, 2), round(h, 2)], "min_printable_mm": plates.MIN_PRINTABLE_MM},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("face",), thickness_mm=float(params["thickness_mm"]), noun="bookmark")

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        layout = Layout(params)
        return cad.prism(layout.profile, layout.T)

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
        end = {"square": "square-ended", "rounded": "round-ended", "pointed": "pointed"}[L.end]
        return (
            f"A {end} Nishaan bookmark, {L.L:g} mm long, {L.W:g} mm wide and {L.T:g} mm thick, with a "
            f"{HOLE_D_MM:g} mm tassel hole at the top (cut {L.hole_cut:.1f} mm, since printed holes close up a little) "
            f"and a {plates.RIM_MM:g} mm rim around it. Print it flat with the tassel hole up, and sand the edge "
            "lightly before packing."
        )
