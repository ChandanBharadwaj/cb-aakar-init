"""``fridge_magnet@1`` - Chumbak: a flat plate with hidden magnet pockets; a photo sits in relief on the face.

Coordinate frame (mm): the magnet lies face up. X = across (the viewer's right), Y = up the picture,
Z = thickness: the back rests on the bed at Z = 0 and the face is at Z = ``thickness_mm``. The part
is centred in X and Y.

Pieces
  plate    the outline (``rect``, ``rounded`` or ``circle``) whose longest side is ``size_mm``.
  pockets  ``magnet_count`` round pockets opening on the back, ``POCKET_D_MM`` × ``POCKET_DEPTH_MM``
           (a 10 × 3 mm neodymium disc plus 0.2 mm), one in the middle or two side by side,
           symmetric about the centre. At least ``MIN_SKIN_MM`` of plastic stays between a pocket and
           the face, and more when the photo on the face is cut in (``validate_content``).

Anchor
  face  the largest rectangle on the top face, ``ANCHOR_MARGIN_MM`` inside the edge (normal +Z).
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely.geometry import Polygon

from .. import cad
from ..errors import ParamOutOfRange
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

MAGNET_SKU = "magnet_d10x3"
POCKET_D_MM = 10.2  # 10 mm magnet + 0.2 mm
POCKET_DEPTH_MM = 3.2  # 3 mm magnet + 0.2 mm
POCKET_WALL_MM = 2.0  # plastic kept between a pocket and the edge, and between two pockets
ANCHOR_MARGIN_MM = 1.5
BLEED_MM = 1.0
MAX_RELIEF_MM = 1.5
PLATE_ASPECT = 0.75  # rect and rounded magnets are 4:3, like a phone photo
ROUNDED_CORNER = 0.2  # corner radius of the rounded magnet, as a share of its height

SHAPES = ("rect", "rounded", "circle")


class Layout:
    """Derived dimensions shared by ``build_body``, ``validate_combination``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.shape = str(p["shape"])
        self.S = float(p["size_mm"])
        self.T = float(p["thickness_mm"])
        self.count = int(p["magnet_count"])
        if self.shape == "circle":
            self.outline: Polygon = plates.disc(0.0, 0.0, self.S / 2.0, 144)
            width = self.S
        else:
            h = self.S * PLATE_ASPECT
            corner = ROUNDED_CORNER * h if self.shape == "rounded" else 0.0
            self.outline = plates.rounded_rect(self.S, h, corner)
            width = self.S
        # one pocket in the middle, or two on the long axis at the quarter points
        self.pocket_xy: list[tuple[float, float]] = [(0.0, 0.0)] if self.count == 1 else [(-width / 4.0, 0.0), (width / 4.0, 0.0)]
        self.pocket_polys = [plates.hole(x, y, POCKET_D_MM) for x, y in self.pocket_xy]

    @property
    def skin_mm(self) -> float:
        """Plastic between a pocket's floor and the face."""
        return self.T - POCKET_DEPTH_MM

    def pockets_fit(self) -> bool:
        """Every pocket keeps ``POCKET_WALL_MM`` to the edge and to its neighbour."""
        outer = POCKET_D_MM / 2.0 / math.cos(math.pi / plates.CIRCLE_SEGMENTS)
        inner = self.outline.buffer(-POCKET_WALL_MM)
        for x, y in self.pocket_xy:
            if not inner.contains(plates.disc(x, y, outer)):
                return False
        if len(self.pocket_xy) == 2:
            (x0, y0), (x1, y1) = self.pocket_xy
            if math.hypot(x1 - x0, y1 - y0) - 2.0 * outer < POCKET_WALL_MM:
                return False
        return True

    def printable_rect(self) -> tuple[float, float, float, float] | None:
        return plates.symmetric_rect(self.outline.buffer(-ANCHOR_MARGIN_MM))

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        rect = self.printable_rect()
        if rect is None:  # pragma: no cover - every in-range magnet has a face
            raise ParamOutOfRange(["size_mm"], "This magnet has no room left for a picture")
        x0, y0, x1, y1 = rect
        return {
            "face": AnchorFrame(
                np.array([(x0 + x1) / 2.0, (y0 + y1) / 2.0, self.T]), [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=(x1 - x0, y1 - y0)
            ),
        }


PARAMS = {
    "shape": Param("enum", "Shape", "rounded", options=SHAPES, group="Shape"),
    "size_mm": Param("number", "Size", 55.0, "mm", 40, 70, 1, handle=True, group="Size", description="Longest side."),
    "thickness_mm": Param("number", "Thickness", 5.0, "mm", 4.5, 6.0, 0.1, group="Size"),
    "magnet_count": Param(
        "integer", "Magnets", 1, "count", 1, 2, 1, group="Details",
        description="10 × 3 mm magnets set into the back; two hold a bigger magnet straighter.",
    ),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class FridgeMagnet(Template):
    id = "fridge_magnet"
    version = 1
    family = "fridge_magnet"
    name = "Chumbak fridge magnet"
    description = (
        "A flat plate with hidden pockets for strong 10 × 3 mm magnets in the back. Your photo sits in "
        "relief on the face."
    )
    environment = "kitchen_marble"
    params = PARAMS
    anchors = (
        Anchor("face", "Face", "planar", size_mm=_size("face"), bleed_mm=BLEED_MM, accepts=("relief_image",), max_relief_mm=MAX_RELIEF_MM),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    style_variants = ()
    features_supported = ("relief_image",)  # text and motifs land with the text release (PR 3b)
    hardware = (HardwareRef(MAGNET_SKU, 1),)  # the default build; hardware_for counts the pockets
    min_feature_mm = 0.8

    @classmethod
    def hardware_for(cls, params: Mapping[str, Any]) -> list[dict[str, Any]]:
        return [{"sku": MAGNET_SKU, "qty": int(params.get("magnet_count", 1))}]

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        layout = Layout(params)
        if layout.skin_mm < plates.MIN_SKIN_MM - 1e-9:
            raise ParamOutOfRange(
                ["thickness_mm"],
                (
                    f"A {layout.T:g} mm magnet leaves {layout.skin_mm:.1f} mm over the {POCKET_DEPTH_MM:g} mm magnet pocket; "
                    f"make it at least {POCKET_DEPTH_MM + plates.MIN_SKIN_MM:g} mm thick."
                ),
                {"skin_mm": round(layout.skin_mm, 3), "min_skin_mm": plates.MIN_SKIN_MM},
            )
        if not layout.pockets_fit():
            raise ParamOutOfRange(
                ["size_mm", "magnet_count"],
                f"{layout.count} magnets do not fit in a {layout.S:g} mm {layout.shape} magnet; make it bigger or use one magnet.",
                {"pockets": layout.pocket_xy},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(
            features,
            anchors=("face",),
            thickness_mm=float(params["thickness_mm"]),
            noun="magnet",
            behind=POCKET_DEPTH_MM,
            behind_what="the magnet",
        )

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        layout = Layout(params)
        plate = cad.prism(layout.outline, layout.T)
        # pockets open on the back: from below the bed up to the pocket floor
        pockets = [cad.translate(cad.prism(poly, POCKET_DEPTH_MM + 1.0), dz=-1.0) for poly in layout.pocket_polys]
        return cad.cut(plate, pockets)

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
        shape = {"rect": "square-cornered", "rounded": "rounded", "circle": "round"}[L.shape]
        pockets = "one pocket" if L.count == 1 else "two pockets"
        magnets = "a 10 × 3 mm magnet" if L.count == 1 else "the two 10 × 3 mm magnets"
        return (
            f"A {shape} Chumbak magnet, {L.S:g} mm on its longest side and {L.T:g} mm thick, with {pockets} "
            f"({POCKET_D_MM:g} × {POCKET_DEPTH_MM:g} mm) in the back. Press {magnets} in flush with a drop of glue "
            "before packing, and keep loose magnets away from small children."
        )
