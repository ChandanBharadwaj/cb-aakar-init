"""``keychain_tag@1`` - Saathi: a palm-sized tag with a ring loop; a photo sits in relief on its face or back.

Coordinate frame (mm): the tag lies face up. X = across (the viewer's right), Y = up the picture (the
ring loop is at +Y), Z = thickness: the back rests on the bed at Z = 0 and the face is at
Z = ``thickness_mm``. The part is centred in X and Y.

Pieces
  plate  the outline (``rect``, ``rounded``, ``circle`` or ``heart``) scaled so that the longest side,
         ring loop included, is exactly ``width_mm``.
  loop   a round tab above the plate (in the heart's notch) carrying the ring hole. The hole is cut
         ``hole_d_mm`` + 0.2 mm because printed holes come out about 0.2 mm small, and keeps
         ``RIM_MM`` (2 mm) of solid plastic all round: a 25 mm split ring's 1.2 mm wire needs a 4 mm
         hole with a 2 mm rim.

Anchors (content lands on these, see ``anchor_frame``)
  face  the largest rectangle on the top face clear of the loop, ``ANCHOR_MARGIN_MM`` inside the edge
        (normal +Z).
  back  the same rectangle on the underside, seen with the tag turned over left to right, so the
        loop stays at the top of the picture (normal −Z).
The descriptor publishes the anchor sizes at the default parameters; ``anchor_frame`` gives the exact
frame for any parameters. A cut-in (deboss) photo must leave ``MIN_SKIN_MM`` of plastic behind it; cut-ins
on both faces add up (``validate_content``).
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

ANCHOR_MARGIN_MM = 1.5  # plastic kept clear between a photo and the edge of the tag (or the loop's rim)
BLEED_MM = 1.0
MAX_RELIEF_MM = 1.5
PLATE_ASPECT = 0.6  # rect and rounded tags are 5:3
ROUNDED_CORNER = 0.3  # corner radius of the rounded tag, as a share of its height

SHAPES = ("rect", "rounded", "circle", "heart")


def _unit_outline(shape: str) -> Polygon:
    """The tag outline one unit wide (loop not included)."""
    if shape == "rect":
        return plates.rounded_rect(1.0, PLATE_ASPECT, 0.0)
    if shape == "rounded":
        return plates.rounded_rect(1.0, PLATE_ASPECT, ROUNDED_CORNER * PLATE_ASPECT)
    if shape == "circle":
        return plates.disc(0.0, 0.0, 0.5, 144)
    if shape == "heart":
        return plates.heart_unit()
    raise ValueError(f"unknown shape {shape!r}")


class Layout:
    """Derived dimensions shared by ``build_body``, ``validate_combination``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.shape = str(p["shape"])
        self.W = float(p["width_mm"])
        self.T = float(p["thickness_mm"])
        self.hole_d = float(p["hole_d_mm"])
        self.hole_cut = self.hole_d + plates.HOLE_ALLOWANCE_MM
        segs = plates.CIRCLE_SEGMENTS
        hole_outer = (self.hole_cut / 2.0) / math.cos(math.pi / segs)  # the hole polygon's corners
        self.loop_r = hole_outer + plates.RIM_MM  # the loop polygon's edges; the rim is exactly RIM_MM
        loop_outer = self.loop_r / math.cos(math.pi / segs)  # the loop polygon's corners

        unit = _unit_outline(self.shape)
        ux0, uy0, ux1, uy1 = unit.bounds
        u_top = plates.top_at(unit, 0.0)
        assert u_top is not None
        # Longest side (loop included) = W: the smallest scale at which the width, the plate's height
        # or the height with the loop reaches W.
        self.scale = min(
            self.W / (ux1 - ux0),
            self.W / (uy1 - uy0),
            (self.W - self.loop_r - loop_outer) / (u_top - uy0),
        )
        k = self.scale
        plate = affinity.scale(unit, k, k, origin=(0.0, 0.0))
        plate_top = k * u_top
        hole_y = plate_top + self.loop_r
        tab = box(-self.loop_r, plate_top - self.loop_r, self.loop_r, hole_y)
        loop = plates.hole(0.0, hole_y, 2.0 * self.loop_r).union(tab)
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
        """``(x0, y0, x1, y1)`` of the photo area: inside the edge by the margin, clear of the loop and its rim."""
        keep_out = plates.disc(self.hole_xy[0], self.hole_xy[1], self.loop_r + ANCHOR_MARGIN_MM)
        region = self.outline.buffer(-ANCHOR_MARGIN_MM).difference(keep_out)
        return plates.symmetric_rect(region)

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        rect = self.printable_rect()
        if rect is None:  # pragma: no cover - validate_combination refuses these
            raise ParamOutOfRange(["width_mm", "hole_d_mm"], "This tag has no room left for a picture")
        x0, y0, x1, y1 = rect
        size = (x1 - x0, y1 - y0)
        xc, yc = (x0 + x1) / 2.0, (y0 + y1) / 2.0
        return {
            "face": AnchorFrame(np.array([xc, yc, self.T]), [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=size),
            # turned over left to right: the loop stays up, the viewer's right is −X
            "back": AnchorFrame(np.array([xc, yc, 0.0]), [-1, 0, 0], [0, 1, 0], [0, 0, -1], size_mm=size),
        }


PARAMS = {
    "shape": Param("enum", "Shape", "rounded", options=SHAPES, group="Shape"),
    "width_mm": Param(
        "number", "Size", 45.0, "mm", 30, 60, 1, handle=True, group="Size",
        description="Longest side, ring loop included.",
    ),
    "thickness_mm": Param("number", "Thickness", 3.0, "mm", 2.4, 4.0, 0.1, group="Size"),
    "hole_d_mm": Param(
        "number", "Ring hole", 4.2, "mm", 4.0, 6.0, 0.1, group="Details",
        description="Finished hole for the split ring; it is cut 0.2 mm larger because printed holes shrink.",
    ),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class KeychainTag(Template):
    id = "keychain_tag"
    version = 1
    family = "keychain"
    name = "Saathi keychain tag"
    description = (
        "A palm-sized tag with a loop for a steel split ring. Your photo sits in relief on the face, "
        "and another can go on the back."
    )
    environment = "studio"
    params = PARAMS
    anchors = (
        Anchor("face", "Face", "planar", size_mm=_size("face"), bleed_mm=BLEED_MM, accepts=("relief_image",), max_relief_mm=MAX_RELIEF_MM),
        Anchor("back", "Back", "planar", size_mm=_size("back"), bleed_mm=BLEED_MM, accepts=("relief_image",), max_relief_mm=MAX_RELIEF_MM),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    style_variants = ()
    features_supported = ("relief_image",)  # text and motifs land with the text release (PR 3b)
    hardware = (HardwareRef("split_ring_25", 1),)
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
                ["width_mm", "hole_d_mm"],
                (
                    f"A {params['width_mm']:g} mm {params['shape']} tag with a {params['hole_d_mm']:g} mm ring hole leaves "
                    f"too little room for a picture ({w:.0f} × {h:.0f} mm); make it bigger or the hole smaller."
                ),
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
        shape = {"rect": "square-cornered", "rounded": "rounded", "circle": "round", "heart": "heart-shaped"}[L.shape]
        return (
            f"A {shape} Saathi tag, {L.W:g} mm on its longest side and {L.T:g} mm thick, with a "
            f"{L.hole_d:g} mm ring hole (cut {L.hole_cut:.1f} mm, since printed holes close up a little) and a "
            f"{plates.RIM_MM:g} mm rim around it. Thread the 25 mm steel split ring through the loop before packing."
        )
