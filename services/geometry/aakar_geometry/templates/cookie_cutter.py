"""``cookie_cutter@1`` - Saancha: a cutter-stamp in one piece; the customer's picture prints into the cookie as the wall cuts it.

Product decision: a CUTTER-STAMP, not a bare outline. A solid stamp plate carries the customer's picture on its face,
and a cutting wall rises round the plate's perimeter above that face. The baker presses the piece face down into the
dough: the wall cuts the outline and the picture prints into the cookie, in one push.

Coordinate frame (mm), **as printed = as modelled**: the stamp plate lies on the bed, the cutting wall points up.
X = across (the viewer's right with the stamp face up), Y = up the picture, Z = out of the plate: the plate's back
rests on the bed at Z = 0, the stamp face is at Z = ``plate_mm`` and the wall's cutting edge is at
Z = ``plate_mm + blade_mm``. The part is centred in X and Y; the outline's longest extent is ``size_mm``.

Cutting depth: ``blade_mm`` is how far the wall stands proud of the stamp face, so it is the thickness the baker rolls
the dough to (4-8 mm, rolled cookie dough is 3-8 mm). Pressed down until the stamp face meets the dough, the wall cuts
right through and the picture prints in the same push; a deeper wall would hold the picture off the dough.

Pieces
  plate  the outline (``round``: a disc; ``scalloped``: a disc with twelve round lobes; ``square``: a rounded square;
         ``heart``: ``plates.heart_unit``; ``star``: ``plates.star_unit`` with a generous ``STAR_INNER`` and soft
         points), scaled so its longest extent is ``size_mm``, ``plate_mm`` thick, solid.
  wall   a straight ``WALL_MM`` (1.6 mm) ring on the plate's perimeter rising ``blade_mm`` above the stamp face. (A
         tapered loft, thick at the plate and thin at the edge, fails in the kernel on the heart's notch and the
         star's re-entrant points, so the wall is straight; 1.6 mm is four perimeters on a 0.4 mm nozzle, thin
         enough to cut rolled dough cleanly and stiff enough not to flex over a 6 mm push.)

Anchor
  outline  the largest symmetric rectangle on the stamp face inside the outline shrunk by ``WALL_MM + ANCHOR_MARGIN_MM``
           (normal +Z): a photo relief (Chhavi), cut in (the default, so the picture stands proud on the cookie) or
           raised. A cut-in picture must leave ``plates.MIN_SKIN_MM`` of the plate (``validate_content``).

No hardware. Food contact: wash before the first use, hand-wash only.
"""

from __future__ import annotations

import math
from functools import lru_cache
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely import affinity
from shapely.geometry import Polygon
from shapely.ops import unary_union

from .. import cad
from ..errors import ParamOutOfRange
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, Param, Template, TemplateConstraints

WALL_MM = 1.6  # the cutting wall, straight: four perimeters on a 0.4 mm nozzle (the taper loft fails on the heart and star)
ANCHOR_MARGIN_MM = 1.5  # between the wall's inner face and the picture
BLEED_MM = 1.0
MAX_RELIEF_MM = 1.5  # a 1.5 mm cut into the thinnest 3 mm plate still leaves MIN_SKIN_MM + 0.3
OVERCUT_MM = 1.0  # the cavity cutter reaches past the cutting edge so no coplanar faces are left
SQUARE_CORNER = 0.12  # corner radius of the square, as a share of its side
STAR_INNER = 0.6  # a fat star: its core must hold an 8 mm picture at size 50
SCALLOP_LOBES = 12
SCALLOP_CORE_R = 0.42  # the scalloped disc's core radius, in units of the outline's size
SCALLOP_LOBE_R = 0.10  # each lobe's radius; lobes centred at SCALLOP_RING_R reach exactly 0.5
SCALLOP_RING_R = 0.40
SIMPLIFY_MM = 0.05  # vertices closer than the tessellation tolerance are dropped: the heart and scallop then build in ~1 s

OUTLINES = ("round", "scalloped", "square", "heart", "star")


@lru_cache(maxsize=None)
def scallop_unit() -> Polygon:
    """A disc one unit across with ``SCALLOP_LOBES`` round lobes on its rim."""
    core = plates.disc(0.0, 0.0, SCALLOP_CORE_R, 96)
    angles = np.arange(SCALLOP_LOBES) * 2.0 * math.pi / SCALLOP_LOBES
    lobes = [plates.disc(SCALLOP_RING_R * math.cos(a), SCALLOP_RING_R * math.sin(a), SCALLOP_LOBE_R, 24) for a in angles]
    return plates.largest_polygon(unary_union([core, *lobes]))


def unit_outline(kind: str) -> Polygon:
    if kind == "round":
        return plates.disc(0.0, 0.0, 0.5, 144)
    if kind == "scalloped":
        return scallop_unit()
    if kind == "square":
        return plates.rounded_rect(1.0, 1.0, SQUARE_CORNER)
    if kind == "heart":
        return plates.heart_unit()
    if kind == "star":
        return plates.star_unit(5, STAR_INNER)
    raise ParamOutOfRange(["outline"], f"outline must be one of {', '.join(OUTLINES)}", {"value": kind})


def _centred(poly: Polygon) -> Polygon:
    minx, miny, maxx, maxy = poly.bounds
    return affinity.translate(poly, -(minx + maxx) / 2.0, -(miny + maxy) / 2.0)


def outline_polygon(kind: str, size_mm: float) -> Polygon:
    """The outline in mm, its longest extent exactly ``size_mm``, centred on the origin by its bounds."""
    unit = unit_outline(kind)
    minx, miny, maxx, maxy = unit.bounds
    k = size_mm / max(maxx - minx, maxy - miny)
    poly = plates.largest_polygon(affinity.scale(unit, k, k, origin=(0, 0)).simplify(SIMPLIFY_MM))
    poly = _centred(poly)
    minx, miny, maxx, maxy = poly.bounds
    k = size_mm / max(maxx - minx, maxy - miny)  # simplify can shave a hair off the extents: scale back to size
    return _centred(affinity.scale(poly, k, k, origin=(0, 0)))


class Layout:
    """Derived dimensions shared by ``build_part``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.kind = str(p["outline"])
        self.S = float(p["size_mm"])
        self.plate = float(p["plate_mm"])
        self.blade = float(p["blade_mm"])
        self.outline: Polygon = outline_polygon(self.kind, self.S)
        self.inner: Polygon = plates.largest_polygon(self.outline.buffer(-WALL_MM))

    @property
    def height(self) -> float:
        return self.plate + self.blade

    def printable_rect(self) -> tuple[float, float, float, float] | None:
        return plates.symmetric_rect(self.outline.buffer(-(WALL_MM + ANCHOR_MARGIN_MM)))

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        rect = self.printable_rect()
        if rect is None:  # pragma: no cover - every in-range cutter has a stamp face
            raise ParamOutOfRange(["size_mm"], "This cutter has no room left for a picture")
        x0, y0, x1, y1 = rect
        return {
            "outline": AnchorFrame(
                np.array([(x0 + x1) / 2.0, (y0 + y1) / 2.0, self.plate]), [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=(x1 - x0, y1 - y0)
            ),
        }


PARAMS = {
    "outline": Param("enum", "Outline", "round", options=OUTLINES, group="Shape"),
    "size_mm": Param("number", "Size", 70.0, "mm", 50, 100, 1, handle=True, group="Size", description="Across the outline, its longest side."),
    "plate_mm": Param("number", "Stamp plate", 3.0, "mm", 3.0, 4.0, 0.1, group="Size", description="The plate that carries the picture."),
    "blade_mm": Param(
        "number", "Cutting depth", 6.0, "mm", 4, 8, 0.5, group="Details",
        description=(
            "How far the cutting wall stands proud of the picture. Roll your dough to exactly this thickness: "
            "the wall cuts through it and the picture presses in, in one push."
        ),
    ),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class CookieCutter(Template):
    id = "cookie_cutter"
    version = 1
    family = "cookie_cutter"
    name = "Saancha cookie cutter"
    description = (
        "A cutter and stamp in one piece: a cutting wall round a solid plate that carries your picture. "
        "Press it face down into rolled dough and it cuts the outline and prints the picture in one push."
    )
    environment = "kitchen_marble"
    params = PARAMS
    anchors = (
        Anchor(
            "outline", "Stamp face", "planar", size_mm=_size("outline"), bleed_mm=BLEED_MM,
            accepts=("relief_image",), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    # the wall is WALL_MM; the inspector's rays start a hair inside the skin and read it as 1.59, so the floor sits just under
    constraints = TemplateConstraints(min_wall_mm=1.5, max_overhang_deg=45, bed_mm=(250, 250, 250))
    features_supported = ("relief_image",)
    hardware = ()
    min_feature_mm = 0.8

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(
            features, anchors=("outline",), thickness_mm=float(params["plate_mm"]), noun="stamp plate", thickness_key="plate_mm"
        )

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        L = Layout(params)
        block = cad.prism(L.outline, L.height)
        # the cavity inside the wall, from the stamp face up past the cutting edge
        cavity = cad.translate(cad.prism(L.inner, L.blade + OVERCUT_MM), dz=L.plate)
        return cad.cut(block, [cavity])

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
        shape = {"round": "round", "scalloped": "scalloped", "square": "rounded-square", "heart": "heart", "star": "star"}[L.kind]
        return (
            f"A {shape} Saancha cutter-stamp, {L.S:g} mm across, on a solid {L.plate:g} mm stamp plate with a straight "
            f"{WALL_MM:g} mm cutting wall standing {L.blade:g} mm proud of the picture. Printed plate down as modelled, "
            "no supports. It touches food: wash before the first use, hand-wash only. Tell the customer to roll the "
            f"dough {L.blade:g} mm thick and press the cutter face down until the plate meets the dough, so the wall "
            "cuts the outline and the picture prints in one push."
        )
