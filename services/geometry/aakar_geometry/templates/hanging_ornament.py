"""``hanging_ornament@1`` - Jhoomar: a two-sided ornament with a hanging hole and an optional raised border.

Coordinate frame (mm): the ornament lies front up. X = across (the viewer's right), Y = up the picture
(the hanging hole is at the top, +Y), Z = thickness: the back rests on the bed at Z = 0 and the front
field is at Z = ``thickness_mm``. The part is centred in X and Y.

Pieces
  plate   the silhouette (``disc``, ``star`` or ``bauble``) whose longest side is ``diameter_mm``.
  hole    the hanging hole at the top, cut ``HANG_HOLE_MM`` + 0.2 mm with ``RIM_MM`` of solid plastic
          between it and the edge, for the cotton cord.
  border  a ring ``border_mm`` wide just inside the edge, standing ``BORDER_HEIGHT_MM`` proud of the
          front (0 = no border). The back stays flat so the ornament prints lying on it.

Anchors
  face_front  the largest rectangle inside the border, clear of the hole (normal +Z).
  face_back   the same rectangle on the back, seen with the ornament turned over left to right (normal −Z).
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely import affinity
from shapely.geometry import Polygon

from .. import cad
from ..errors import ParamOutOfRange
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

HANG_HOLE_MM = 3.5  # finished hole: a 200 mm cotton cord threads through without a needle
BORDER_HEIGHT_MM = 1.0
MIN_BORDER_MM = 1.5  # a narrower raised ring prints as a hair-thin line
ANCHOR_MARGIN_MM = 1.5
BLEED_MM = 1.0
MAX_RELIEF_MM = 1.5

SILHOUETTES = ("disc", "star", "bauble")


def _unit_silhouette(silhouette: str) -> Polygon:
    if silhouette == "disc":
        return plates.disc(0.0, 0.0, 0.5, 192)
    if silhouette == "star":
        return plates.star_unit()
    if silhouette == "bauble":
        return plates.bauble_unit()
    raise ValueError(f"unknown silhouette {silhouette!r}")


class Layout:
    """Derived dimensions shared by ``build_body``, ``validate_combination``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.silhouette = str(p["silhouette"])
        self.D = float(p["diameter_mm"])
        self.T = float(p["thickness_mm"])
        self.border = float(p["border_mm"])
        unit = _unit_silhouette(self.silhouette)
        ux0, uy0, ux1, uy1 = unit.bounds
        k = self.D / max(ux1 - ux0, uy1 - uy0)
        shape = affinity.scale(unit, k, k, origin=(0.0, 0.0))
        x0, y0, x1, y1 = shape.bounds
        self.outline: Polygon = affinity.translate(shape, -(x0 + x1) / 2.0, -(y0 + y1) / 2.0)
        self.hole_cut = HANG_HOLE_MM + plates.HOLE_ALLOWANCE_MM
        hole_outer = (self.hole_cut / 2.0) / math.cos(math.pi / plates.CIRCLE_SEGMENTS)
        self.keep_r = hole_outer + plates.RIM_MM  # the hole's corners stay RIM_MM from the edge
        top = plates.top_at(self.outline.buffer(-self.keep_r - 1e-6), 0.0)
        if top is None:  # pragma: no cover - every silhouette in range has room at the top
            raise ParamOutOfRange(["diameter_mm"], "This ornament is too small for a hanging hole")
        self.hole_xy = (0.0, top)
        self.hole_poly = plates.hole(0.0, top, self.hole_cut)
        self.profile: Polygon = self.outline.difference(self.hole_poly)

    def field(self) -> Polygon:
        """The front area inside the raised border (the whole silhouette when there is none)."""
        return self.outline.buffer(-self.border) if self.border > 0 else self.outline

    def border_ring(self) -> Polygon | None:
        if self.border <= 0:
            return None
        ring = self.outline.difference(self.field()).difference(self.hole_poly)
        return None if ring.is_empty else ring

    def printable_rect(self) -> tuple[float, float, float, float] | None:
        keep_out = plates.disc(self.hole_xy[0], self.hole_xy[1], self.keep_r + ANCHOR_MARGIN_MM)
        region = self.outline.buffer(-(self.border + ANCHOR_MARGIN_MM)).difference(keep_out)
        return plates.symmetric_rect(region)

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        rect = self.printable_rect()
        if rect is None:  # pragma: no cover - validate_combination refuses these
            raise ParamOutOfRange(["border_mm", "diameter_mm"], "This ornament has no room left for a picture")
        x0, y0, x1, y1 = rect
        size = (x1 - x0, y1 - y0)
        xc, yc = (x0 + x1) / 2.0, (y0 + y1) / 2.0
        return {
            "face_front": AnchorFrame(np.array([xc, yc, self.T]), [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=size),
            "face_back": AnchorFrame(np.array([xc, yc, 0.0]), [-1, 0, 0], [0, 1, 0], [0, 0, -1], size_mm=size),
        }


PARAMS = {
    "silhouette": Param("enum", "Silhouette", "disc", options=SILHOUETTES, group="Shape"),
    "diameter_mm": Param(
        "number", "Size", 70.0, "mm", 50, 90, 1, handle=True, group="Size",
        description="Across the widest part.",
    ),
    "thickness_mm": Param("number", "Thickness", 4.0, "mm", 3.0, 5.0, 0.1, group="Size"),
    "border_mm": Param(
        "number", "Raised border", 3.0, "mm", 0, 6, 0.5, group="Details",
        description="Width of the raised rim on the front; 0 for none, otherwise at least 1.5 mm.",
    ),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class HangingOrnament(Template):
    id = "hanging_ornament"
    version = 1
    family = "ornament"
    name = "Jhoomar hanging ornament"
    description = (
        "A two-sided disc, star or bauble with a hanging hole and a raised border. A photo can sit in "
        "relief on the front and another on the back."
    )
    environment = "teak_table_candlelight"
    params = PARAMS
    anchors = (
        Anchor("face_front", "Front", "planar", size_mm=_size("face_front"), bleed_mm=BLEED_MM, accepts=("relief_image",), max_relief_mm=MAX_RELIEF_MM),
        Anchor("face_back", "Back", "planar", size_mm=_size("face_back"), bleed_mm=BLEED_MM, accepts=("relief_image",), max_relief_mm=MAX_RELIEF_MM),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    style_variants = ()
    features_supported = ("relief_image",)  # text and motifs land with the text release (PR 3b)
    hardware = (HardwareRef("cord_200", 1),)
    min_feature_mm = 0.8

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        border = float(params["border_mm"])
        if 0 < border < MIN_BORDER_MM:
            raise ParamOutOfRange(
                ["border_mm"],
                f"A raised border needs to be at least {MIN_BORDER_MM:g} mm wide to print cleanly; use 0 for none.",
                {"border_mm": border, "min_border_mm": MIN_BORDER_MM},
            )
        rect = Layout(params).printable_rect()
        w = h = 0.0
        if rect is not None:
            w, h = rect[2] - rect[0], rect[3] - rect[1]
        if min(w, h) < plates.MIN_PRINTABLE_MM:
            raise ParamOutOfRange(
                ["border_mm", "diameter_mm"],
                (
                    f"A {params['diameter_mm']:g} mm {params['silhouette']} with a {border:g} mm border leaves too little room "
                    f"for a picture ({w:.0f} × {h:.0f} mm); make the border narrower or the ornament bigger."
                ),
                {"printable_mm": [round(w, 2), round(h, 2)], "min_printable_mm": plates.MIN_PRINTABLE_MM},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("face_front", "face_back"), thickness_mm=float(params["thickness_mm"]), noun="ornament")

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        layout = Layout(params)
        if layout.border_ring() is None:
            return cad.prism(layout.profile, layout.T)
        # the full height everywhere, then the field inside the border lowered to the plate's face
        # (one cut is about twice as fast as fusing a separate ring on, and gives the same solid)
        body = cad.prism(layout.profile, layout.T + BORDER_HEIGHT_MM)
        fields = [cad.translate(cad.prism(piece, BORDER_HEIGHT_MM + 1.0), dz=layout.T) for piece in _polygons(layout.field())]
        return cad.cut(body, fields)

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
        border = f"a {L.border:g} mm raised border on the front" if L.border > 0 else "a plain front"
        return (
            f"A {L.silhouette} Jhoomar ornament, {L.D:g} mm across and {L.T:g} mm thick, with {border} and a "
            f"{HANG_HOLE_MM:g} mm hanging hole at the top (cut {L.hole_cut:.1f} mm). Thread the 200 mm cotton cord "
            "through the hole and knot it before packing."
        )


def _polygons(geom: Any) -> list[Polygon]:
    if isinstance(geom, Polygon):
        return [geom]
    return [g for g in getattr(geom, "geoms", []) if isinstance(g, Polygon) and g.area > 1e-6]
