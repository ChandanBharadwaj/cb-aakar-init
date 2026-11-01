"""``pendant_disc@1`` - Jhumka (the family codename; it is a pendant plate, not a bell earring): a small pendant plate with an integral bail; a name, a motif or a photo sits in relief on its face.

Coordinate frame (mm): the pendant lies face up. X = across (the viewer's right), Y = up the picture
(the bail is at +Y), Z = thickness: the back rests on the bed at Z = 0 and the face is at
Z = ``thickness_mm``. The part is centred in X and Y.

Pieces
  plate  the outline (``disc``, ``teardrop`` or ``heart``) scaled so that the longest side, bail
         included, is exactly ``size_mm``. The teardrop hangs point up, the bail at its point; the
         heart's bail rises out of its notch.
  bail   a round loop on the top edge carrying the jump-ring hole. The hole is cut ``BAIL_HOLE_MM`` +
         0.2 mm because printed holes come out about 0.2 mm small, and keeps ``BAIL_RIM_MM`` (1.6 mm)
         of solid plastic all round it: a jump ring's 1 mm wire passes a 2.6 mm hole with room to turn.
         The hole's centre sits ``BAIL_SINK_MM`` inside the plate's edge, so the loop adds as little
         height as it can and the face below keeps its room; on the heart the loop rises out of the
         notch until its top stands ``BAIL_PEEK_MM`` above the shoulders, on a tab that fills the notch.

Anchor (content lands on this, see ``anchor_frame``)
  face  the largest rectangle on the top face below the bail, ``ANCHOR_MARGIN_MM`` inside the edge and
        clear of the loop's rim (normal +Z): a name (Naam), a motif (Buti) or a small photo relief.
The descriptor publishes the anchor size at the default parameters; ``anchor_frame`` gives the exact
frame for any parameters. A cut-in (deboss) name, motif or photo must leave ``MIN_SKIN_MM`` of plastic
behind it (``validate_content``).

Hardware: none. The customer threads their own jump ring and chain through the bail.
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
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

BAIL_HOLE_MM = 2.6  # finished hole: a 1.0 mm jump-ring wire plus room to turn
BAIL_RIM_MM = 1.6  # solid plastic kept all round the bail hole (the min wall for a loop that carries the piece)
BAIL_SINK_MM = 0.6  # the hole's centre this far inside the plate's edge: the loop adds little height, the face keeps its room
BAIL_PEEK_MM = 1.5  # the loop's top stands at least this far above the plate's highest point (it rises out of a heart's notch)
ANCHOR_MARGIN_MM = 1.2  # plastic kept clear between content and the edge of the pendant
LOOP_CLEARANCE_MM = 0.6  # plastic kept clear between content and the loop's rim
BLEED_MM = 0.8
MAX_RELIEF_MM = 1.2  # a pendant is thin and small: taller relief snags on clothing
TEARDROP_POINT = 0.6  # the teardrop's apex above the disc's centre, in unit widths (the disc is 0.5 across): a plump drop
TEARDROP_SOFTEN = 0.05  # radius that rounds the teardrop's point, in unit widths

SHAPES = ("disc", "teardrop", "heart")


def _teardrop_unit() -> Polygon:
    """A teardrop one unit wide, point up at x = 0: the apex joined by its two tangents to the arc of a disc of
    radius 0.5 below it (one polygon, so no sliver where a tangent meets the arc), tip softened."""
    r, d = 0.5, TEARDROP_POINT
    half = math.acos(r / d)  # angle from +Y to the tangent points, seen from the disc's centre
    arc = np.linspace(math.pi / 2.0 - half, math.pi / 2.0 + half - 2.0 * math.pi, 192)  # clockwise, under the apex
    points = [(0.0, d)] + [(r * math.cos(t), r * math.sin(t)) for t in arc]
    return plates.soften(Polygon(points), TEARDROP_SOFTEN)


def _unit_outline(shape: str) -> Polygon:
    """The pendant outline one unit wide (bail not included)."""
    if shape == "disc":
        return plates.disc(0.0, 0.0, 0.5, 192)
    if shape == "teardrop":
        return _teardrop_unit()
    if shape == "heart":
        return plates.heart_unit()
    raise ValueError(f"unknown shape {shape!r}")


class Layout:
    """Derived dimensions shared by ``build_body``, ``validate_combination``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.shape = str(p["shape"])
        self.S = float(p["size_mm"])
        self.T = float(p["thickness_mm"])
        self.hole_cut = BAIL_HOLE_MM + plates.HOLE_ALLOWANCE_MM
        segs = plates.CIRCLE_SEGMENTS
        hole_outer = (self.hole_cut / 2.0) / math.cos(math.pi / segs)  # the hole polygon's corners
        self.loop_r = hole_outer + BAIL_RIM_MM  # the loop polygon's edges; the rim is exactly BAIL_RIM_MM
        loop_outer = self.loop_r / math.cos(math.pi / segs)  # the loop polygon's corners
        bail_above = loop_outer - BAIL_SINK_MM  # how far the loop reaches above the plate's edge at x = 0

        unit = _unit_outline(self.shape)
        ux0, uy0, ux1, uy1 = unit.bounds
        u_top = plates.top_at(unit, 0.0)
        assert u_top is not None
        # Longest side (bail included) = S: the smallest scale at which the width, the plate's height
        # plus the loop's peek, or the height with the bail on the edge reaches S.
        self.scale = min(
            self.S / (ux1 - ux0),
            (self.S - BAIL_PEEK_MM) / (uy1 - uy0),
            (self.S - bail_above) / (u_top - uy0),
        )
        k = self.scale
        plate = affinity.scale(unit, k, k, origin=(0.0, 0.0))
        plate_top = k * u_top
        hole_y = max(plate_top - BAIL_SINK_MM, k * uy1 + BAIL_PEEK_MM - loop_outer)
        tab = box(-self.loop_r, plate_top - self.loop_r, self.loop_r, hole_y)  # fills a notch under the loop
        loop = plates.hole(0.0, hole_y, 2.0 * self.loop_r).union(tab)  # corners at loop_outer, edges at loop_r
        outline = plates.largest_polygon(plate.union(loop))
        dy = -(outline.bounds[1] + outline.bounds[3]) / 2.0
        self.outline: Polygon = affinity.translate(outline, 0.0, dy)
        self.hole_xy = (0.0, hole_y + dy)
        self.hole_poly = plates.hole(0.0, hole_y + dy, self.hole_cut)
        self.profile: Polygon = self.outline.difference(self.hole_poly)

    @property
    def extents(self) -> tuple[float, float]:
        x0, y0, x1, y1 = self.outline.bounds
        return (x1 - x0, y1 - y0)

    def printable_rect(self) -> tuple[float, float, float, float] | None:
        """``(x0, y0, x1, y1)`` of the content area: inside the edge by the margin, clear of the loop and its rim."""
        keep_out = plates.disc(self.hole_xy[0], self.hole_xy[1], self.loop_r + LOOP_CLEARANCE_MM)
        region = self.outline.buffer(-ANCHOR_MARGIN_MM).difference(keep_out)
        return plates.symmetric_rect(region)

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        rect = self.printable_rect()
        if rect is None:  # pragma: no cover - validate_combination refuses these
            raise ParamOutOfRange(["size_mm", "shape"], "This pendant has no room left for a name or a picture")
        x0, y0, x1, y1 = rect
        return {
            "face": AnchorFrame(
                np.array([(x0 + x1) / 2.0, (y0 + y1) / 2.0, self.T]), [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=(x1 - x0, y1 - y0)
            ),
        }


PARAMS = {
    "shape": Param("enum", "Shape", "disc", options=SHAPES, group="Shape"),
    "size_mm": Param(
        "number", "Size", 30.0, "mm", 20, 40, 1, handle=True, group="Size",
        description="Longest side, bail included.",
    ),
    "thickness_mm": Param("number", "Thickness", 3.0, "mm", 2.5, 3.5, 0.1, group="Size"),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class PendantDisc(Template):
    id = "pendant_disc"
    version = 1
    family = "pendant"
    name = "Jhumka pendant plate"
    description = (
        "A small disc, teardrop or heart pendant with a loop on top for a jump ring. Your name, motif or "
        "a small photo sits in relief on the face; thread it on your own chain."
    )
    environment = "studio"
    params = PARAMS
    anchors = (
        Anchor(
            "face", "Face", "planar", size_mm=_size("face"), bleed_mm=BLEED_MM,
            accepts=("relief_image", "emboss_text", "motif"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    # the plate is 2.5 mm and the loop's rim 1.6; on a face this small the raised lettering is the thinnest thing on the
    # piece and reads as walls to the check, so its floor is FDM's 0.8 (the stroke rule keeps lettering above it)
    constraints = TemplateConstraints(min_wall_mm=0.8, max_overhang_deg=45, bed_mm=(250, 250, 250))
    features_supported = ("relief_image", "emboss_text", "motif")
    hardware: tuple[HardwareRef, ...] = ()  # the customer's own jump ring and chain
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
                ["size_mm", "shape"],
                (
                    f"A {params['size_mm']:g} mm {params['shape']} pendant leaves too little room below its bail "
                    f"for a name or a picture ({w:.0f} × {h:.0f} mm); make it bigger."
                ),
                {"printable_mm": [round(w, 2), round(h, 2)], "min_printable_mm": plates.MIN_PRINTABLE_MM},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("face",), thickness_mm=float(params["thickness_mm"]), noun="pendant")

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
        shape = {"disc": "round", "teardrop": "teardrop", "heart": "heart-shaped"}[L.shape]
        return (
            f"A {shape} Jhumka pendant plate, {L.S:g} mm on its longest side (bail included) and {L.T:g} mm thick, "
            f"with a {BAIL_HOLE_MM:g} mm bail hole (cut {L.hole_cut:.1f} mm, since printed holes close up a little) "
            f"and {BAIL_RIM_MM:g} mm of plastic round it. Thread a jump ring through the bail so the chain hangs "
            "from it, and check the loop is clean of stringing before packing."
        )
