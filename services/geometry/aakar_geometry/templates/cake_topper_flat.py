"""``cake_topper_flat@1`` - Mithai: a flat plaque on two tapered picks; only the picks go into the cake.

Coordinate frame (mm): the topper lies on its back. X = across (the viewer's right), Y = up the picture
(the picks point down, −Y), Z = thickness: the back rests on the bed at Z = 0 and the face is at
Z = ``thickness_mm``. The whole outline (plaque and picks) is centred in X and Y, so the plaque sits a
little above the middle and the pick tips reach the bottom.

Pieces (one 2D outline extruded ``thickness_mm``, so the piece prints flat on its back with no supports)
  plaque  the silhouette, ``width_mm`` across: ``banner`` (a rectangle with swallow-tail ends),
          ``cloud`` (a softened lobed outline), ``heart`` or ``circle``. Its height is a share of its
          width (``PLAQUE_HEIGHT``, or the shape's own proportion for the heart and the circle).
  picks   two tapered picks at x = ±``width_mm``/3, ``PICK_ROOT_MM`` wide where they leave the bottom
          of the plaque and ``PICK_TIP_MM`` at the tip, reaching ``pick_len_mm`` below the plaque's
          lowest point. Where the plaque's edge curves up (a circle, a heart's lobes) a straight bar of
          the root width runs from that edge down to the plaque's lowest level (``Layout.bar_mm``) and on
          up into the plaque so the pick fuses with it; the stick a printer sees, from the edge to the
          tip, is ``Layout.stick_mm`` = ``bar_mm + pick_len_mm`` (the karigar's note states that length).
  The height from pick tip to plaque top is ``plaque height + pick_len_mm``; the longest side of the
  piece (that, or the width) must stay inside the Mithai envelope (``MIN_LONGEST_MM``..``MAX_LONGEST_MM``):
  ``validate_combination`` refuses what falls outside. The sliders (width 90..120, picks 30..50) are
  chosen so that every shape is accepted at every width with the default 40 mm picks except the circle
  above 110 mm; what the envelope still refuses is
    - a banner or cloud under 100 mm wide with picks shorter than ``100 − 0.7 × width`` (too short:
      a 90 mm plaque needs 37 mm picks, a 95 mm one 34 mm; from 100 mm wide the width carries it),
    - a circle wider than ``150 − pick_len_mm`` (too tall: 110 mm at the default 40 mm picks, 100 mm
      at 50 mm; the full 120 mm only with 30 mm picks),
    - a heart whose ``0.85 × width + pick_len_mm`` tops 150 mm (too tall): 120 mm wide with 48 mm picks,
      119 mm with 49, 118 mm or wider with 50; nothing narrower or with shorter picks.

Anchor
  face  the largest symmetric rectangle inside the plaque outline (never on the picks),
        ``ANCHOR_MARGIN_MM`` inside the edge (normal +Z): a name (Naam), a motif (Buti) or a picture.
        A cut-in (deboss) name, motif or picture must leave ``MIN_SKIN_MM`` of plastic behind it
        (``validate_content``).

Hardware: none. The piece is food-adjacent: the karigar's note reminds the printer to wash it.
"""

from __future__ import annotations

from functools import lru_cache
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely import affinity
from shapely.geometry import LineString, Polygon
from shapely.ops import unary_union

from .. import cad
from ..errors import ParamOutOfRange
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

MIN_LONGEST_MM = 100.0  # the Mithai family's size envelope (packages/design-tokens/families.json)
MAX_LONGEST_MM = 150.0
PLAQUE_HEIGHT = 0.7  # banner and cloud: height as a share of the width (a heart and a circle keep their own)
TAIL_DEPTH = 0.1  # the banner's swallow-tail notch, as a share of the width
PICK_ROOT_MM = 7.0  # a pick where it leaves the plaque: stiff enough to push into a sponge
PICK_TIP_MM = 2.5  # the pick's tip: blunt enough to print (never under the 0.8 mm minimum feature)
PICK_OVERLAP_MM = 2.0  # how far the pick's bar runs up into the plaque so the two fuse into one outline
PICK_X_SHARE = 1.0 / 3.0  # picks at ±width/3
ANCHOR_MARGIN_MM = 1.5
BLEED_MM = 1.0
MAX_RELIEF_MM = 1.5

SHAPES = ("banner", "cloud", "heart", "circle")


@lru_cache(maxsize=None)
def _banner_unit() -> Polygon:
    """A banner one unit wide, ``PLAQUE_HEIGHT`` tall, with a swallow-tail notch cut into each end."""
    h = PLAQUE_HEIGHT / 2.0
    return Polygon(
        [(-0.5, -h), (0.5, -h), (0.5 - TAIL_DEPTH, 0.0), (0.5, h), (-0.5, h), (-0.5 + TAIL_DEPTH, 0.0)]
    )


@lru_cache(maxsize=None)
def _cloud_unit() -> Polygon:
    """A cloud one unit wide, ``PLAQUE_HEIGHT`` tall: four lobes on a flat-bottomed base, the notches between
    the lobes filleted. The bottom is flat where the picks leave it. The lobes are drawn 0.6 tall and the
    outline is then stretched in Y to ``PLAQUE_HEIGHT`` so the cloud and the banner share one height rule."""
    base = affinity.translate(plates.rounded_rect(1.0, 0.3, 0.08), 0.0, -0.15)  # y −0.3 .. 0
    lobes = [
        plates.disc(-0.32, 0.00, 0.18),
        plates.disc(-0.08, 0.06, 0.24),  # the tallest lobe: top at 0.30
        plates.disc(0.20, 0.02, 0.20),
        plates.disc(0.38, -0.05, 0.12),
    ]
    cloud = plates.largest_polygon(unary_union([base, *lobes]))
    # a grow-then-shrink buffer fillets the concave notches between the lobes
    cloud = plates.largest_polygon(cloud.buffer(0.02, quad_segs=8).buffer(-0.02, quad_segs=8))
    _, y0, _, y1 = cloud.bounds
    return affinity.scale(cloud, 1.0, PLAQUE_HEIGHT / (y1 - y0), origin=(0.0, 0.0))


def _unit_plaque(shape: str) -> Polygon:
    if shape == "banner":
        return _banner_unit()
    if shape == "cloud":
        return _cloud_unit()
    if shape == "heart":
        return plates.heart_unit()
    if shape == "circle":
        return plates.disc(0.0, 0.0, 0.5, 144)
    raise ValueError(f"unknown shape {shape!r}")


def _bottom_at(poly: Polygon, x: float) -> float:
    """Lowest y of ``poly`` on the vertical line through ``x``."""
    minx, miny, maxx, maxy = poly.bounds
    cut = poly.intersection(LineString([(x, miny - 1.0), (x, maxy + 1.0)]))
    if cut.is_empty:  # pragma: no cover - every shape reaches out past width/3
        raise ValueError(f"the plaque has no edge at x = {x:g}")
    return float(cut.bounds[1])


class Layout:
    """Derived dimensions shared by ``build_body``, ``validate_combination``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.shape = str(p["shape"])
        self.W = float(p["width_mm"])
        self.P = float(p["pick_len_mm"])
        self.T = float(p["thickness_mm"])
        unit = _unit_plaque(self.shape)
        ux0, uy0, ux1, uy1 = unit.bounds
        k = self.W / (ux1 - ux0)
        plaque = affinity.scale(unit, k, k, origin=(0.0, 0.0))
        px0, py0, px1, py1 = plaque.bounds
        plaque = affinity.translate(plaque, -(px0 + px1) / 2.0, 0.0)
        self.H = py1 - py0  # the plaque's height
        self.pick_x = (-self.W * PICK_X_SHARE, self.W * PICK_X_SHARE)
        picks = [self._pick(plaque, x) for x in self.pick_x]
        # where the plaque's edge curves up at the picks (a heart, a circle) the root-width bar shows between
        # that edge and the plaque's lowest level: the stick a printer sees runs from the edge to the tip
        self.bar_mm = max(self._pick_exit_y(plaque, x) for x in self.pick_x) - float(plaque.bounds[1])
        self.stick_mm = self.bar_mm + self.P
        outline = plates.largest_polygon(unary_union([plaque, *picks]))
        dy = -(outline.bounds[1] + outline.bounds[3]) / 2.0
        self.plaque: Polygon = affinity.translate(plaque, 0.0, dy)
        self.outline: Polygon = affinity.translate(outline, 0.0, dy)
        self.pick_tip_y = float(self.outline.bounds[1])
        self.plaque_bottom_y = float(self.plaque.bounds[1])

    @staticmethod
    def _pick_exit_y(plaque: Polygon, x: float) -> float:
        """The level at which a pick's bar leaves the plaque: the plaque's edge at the bar's highest side."""
        half_root = PICK_ROOT_MM / 2.0
        return max(_bottom_at(plaque, x + dx) for dx in (-half_root, 0.0, half_root))

    def _pick(self, plaque: Polygon, x: float) -> Polygon:
        """A bar of the root width from inside the plaque down to its lowest level, then a taper to the tip."""
        half_root, half_tip = PICK_ROOT_MM / 2.0, PICK_TIP_MM / 2.0
        y_base = float(plaque.bounds[1])
        y_root = self._pick_exit_y(plaque, x) + PICK_OVERLAP_MM
        return Polygon(
            [
                (x - half_root, y_root),
                (x - half_root, y_base),
                (x - half_tip, y_base - self.P),
                (x + half_tip, y_base - self.P),
                (x + half_root, y_base),
                (x + half_root, y_root),
            ]
        )

    @property
    def longest_mm(self) -> float:
        """The piece's longest side: its width, or its height from pick tip to plaque top."""
        return max(self.W, self.H + self.P)

    def printable_rect(self) -> tuple[float, float, float, float] | None:
        return plates.symmetric_rect(self.plaque.buffer(-ANCHOR_MARGIN_MM))

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        rect = self.printable_rect()
        if rect is None:  # pragma: no cover - every plaque in range has a face
            raise ParamOutOfRange(["width_mm"], "This topper has no room left for a name or a picture")
        x0, y0, x1, y1 = rect
        return {
            "face": AnchorFrame(
                np.array([(x0 + x1) / 2.0, (y0 + y1) / 2.0, self.T]), [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=(x1 - x0, y1 - y0)
            ),
        }


PARAMS = {
    "shape": Param("enum", "Shape", "banner", options=SHAPES, group="Shape"),
    "width_mm": Param(
        "number", "Width", 110.0, "mm", 90, 120, 1, handle=True, group="Size", description="Across the plaque."
    ),
    "pick_len_mm": Param(
        "number", "Pick length", 40.0, "mm", 30, 50, 1, group="Size",
        description="How far the picks reach below the plaque's lowest point; the plaque stays above the icing.",
    ),
    "thickness_mm": Param("number", "Thickness", 2.8, "mm", 2.4, 3.2, 0.1, group="Details"),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class CakeTopperFlat(Template):
    id = "cake_topper_flat"
    version = 1
    family = "cake_topper"
    name = "Mithai cake topper"
    description = (
        "A flat banner, cloud, heart or circle on two tapered picks, printed lying flat. Your name, motif or "
        "picture sits in relief on the face; only the picks go into the cake."
    )
    environment = "kitchen_marble"
    params = PARAMS
    anchors = (
        Anchor(
            "face", "Face", "planar", size_mm=_size("face"), bleed_mm=BLEED_MM,
            accepts=("emboss_text", "motif", "relief_image"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.6, max_overhang_deg=45, bed_mm=(250, 250, 250))
    features_supported = ("emboss_text", "motif", "relief_image")
    hardware: tuple[HardwareRef, ...] = ()
    min_feature_mm = 0.8

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        layout = Layout(params)
        longest = layout.longest_mm
        if longest < MIN_LONGEST_MM - 1e-6 or longest > MAX_LONGEST_MM + 1e-6:
            fix = (
                "make the plaque wider or the picks longer"
                if longest < MIN_LONGEST_MM
                else "make the plaque narrower or the picks shorter"
            )
            raise ParamOutOfRange(
                ["width_mm", "pick_len_mm"],
                (
                    f"A {layout.W:g} mm {layout.shape} topper with {layout.P:g} mm picks is {longest:.0f} mm from pick tip to "
                    f"top; a cake topper is between {MIN_LONGEST_MM:g} and {MAX_LONGEST_MM:g} mm, so {fix}."
                ),
                {"longest_mm": round(longest, 2), "envelope_mm": [MIN_LONGEST_MM, MAX_LONGEST_MM]},
            )
        rect = layout.printable_rect()
        w = h = 0.0
        if rect is not None:
            w, h = rect[2] - rect[0], rect[3] - rect[1]
        if min(w, h) < plates.MIN_PRINTABLE_MM:  # pragma: no cover - every plaque in range has a face
            raise ParamOutOfRange(
                ["width_mm"],
                f"A {layout.W:g} mm {layout.shape} topper leaves too little room for a name ({w:.0f} × {h:.0f} mm); make it wider.",
                {"printable_mm": [round(w, 2), round(h, 2)], "min_printable_mm": plates.MIN_PRINTABLE_MM},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("face",), thickness_mm=float(params["thickness_mm"]), noun="topper")

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        layout = Layout(params)
        return cad.prism(layout.outline, layout.T)

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
        shape = {"banner": "swallow-tailed banner", "cloud": "cloud", "heart": "heart", "circle": "round"}[L.shape]
        if L.bar_mm < 0.05:  # a flat-bottomed plaque: the whole pick is the taper
            picks = f"two {L.P:g} mm picks tapering from {PICK_ROOT_MM:g} to {PICK_TIP_MM:g} mm"
        else:  # the edge curves up at the picks: a straight bar shows above the taper, and the stick is longer
            bar = round(L.bar_mm)  # the stick figure is the sum of the two stated parts, so the note adds up
            picks = (
                f"two picks {bar + L.P:g} mm long from where they leave the curved edge: a {PICK_ROOT_MM:g} mm bar "
                f"for {bar:g} mm down to the plaque's lowest point, then {L.P:g} mm tapering from "
                f"{PICK_ROOT_MM:g} to {PICK_TIP_MM:g} mm"
            )
        return (
            f"A {shape} Mithai cake topper, {L.W:g} mm wide and {L.T:g} mm thick, printed lying flat on its back, "
            f"with {picks}. Wash it in warm soapy water before use; the picks go into the cake and the plaque "
            "stays above the icing."
        )
