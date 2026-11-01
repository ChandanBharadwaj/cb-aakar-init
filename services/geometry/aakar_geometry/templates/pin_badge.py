"""``pin_badge@1`` - Billa: a small badge with a tie-tack pin glued into a pocket on its back; a name, motif or photo in relief on the face.

Coordinate frame (mm): the badge lies face up. X = across (the viewer's right), Y = up the picture,
Z = thickness: the back rests on the bed at Z = 0 and the face is at Z = ``thickness_mm``. The part
is centred in X and Y.

Pieces
  plate   the outline (``disc``, ``shield`` - a rounded-top pentagon, ``star`` with soft points, or
          ``heart``) scaled so that its longest side is exactly ``size_mm``.
  pocket  one round pocket opening on the back, ``POCKET_D_MM`` × ``POCKET_DEPTH_MM`` (the tie-tack
          pin's 8 mm flat pad plus 0.2 mm all round), centred where the outline has the most room: the
          centre of its largest inscribed circle (``pocket_centre``), on the shape's mirror axis. That is
          the middle of a disc, but below the bounding box's centre on a heart (away from the notch) and a
          star (between its points). At least ``POCKET_WALL_MM`` of plastic stays between the pocket and
          the outline, and ``MIN_SKIN_MM`` between its floor and the face, more when the content on the
          face is cut in (``validate_content``).

Anchor
  face  the largest 4:3 rectangle on the top face, ``ANCHOR_MARGIN_MM`` inside the edge (normal +Z): a photo,
        or a name (Naam) and/or a motif (Buti). Where no 4:3 rectangle is ``MIN_PRINTABLE_MM`` on both sides
        (a small heart or star) it is the largest square instead.

Hardware
  one ``tie_tack_pin`` per badge: the studio glues its 8 mm pad into the back pocket; the butterfly
  clutch is packed loose so the customer can pin the badge on.
"""

from __future__ import annotations

import math
from functools import lru_cache
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely import affinity
from shapely.geometry import Polygon
from shapely.ops import polylabel

from .. import cad
from ..errors import ParamOutOfRange
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

PIN_SKU = "tie_tack_pin"
PAD_D_MM = 8.0  # the tie-tack pin's flat pad
POCKET_D_MM = PAD_D_MM + 2.0 * plates.HOLE_ALLOWANCE_MM  # 8.4: the pad plus 0.2 mm all round, so it drops in
POCKET_DEPTH_MM = 0.8  # the pad is a thin stamping; 0.8 mm locates it and takes the glue
POCKET_WALL_MM = 2.0  # plastic kept between the pocket and the outline (a star's inner corners, a heart's notch)
ANCHOR_MARGIN_MM = 1.2  # plastic kept clear between the content and the edge of the badge
BLEED_MM = 1.0
MAX_RELIEF_MM = 1.2  # a badge is small: deeper relief snags on cloth
STAR_INNER_RATIO = 0.62  # a plump star, so a small badge still has room for a name between its points
SHIELD_SHOULDER = 0.08  # the shield's corner rounding, as a share of its width

SHAPES = ("disc", "shield", "star", "heart")


@lru_cache(maxsize=None)
def _shield_unit() -> Polygon:
    """A shield one unit wide: a flat top with rounded shoulders, straight sides tapering to a soft point."""
    pentagon = Polygon([(-0.5, 0.5), (0.5, 0.5), (0.5, 0.05), (0.0, -0.5), (-0.5, 0.05)])
    return plates.soften(pentagon, SHIELD_SHOULDER)


def _unit_outline(shape: str) -> Polygon:
    """The badge outline before scaling (one unit on its longest side)."""
    if shape == "disc":
        return plates.disc(0.0, 0.0, 0.5, 144)
    if shape == "shield":
        return _shield_unit()
    if shape == "star":
        return plates.star_unit(5, STAR_INNER_RATIO)
    if shape == "heart":
        return plates.heart_unit()
    raise ValueError(f"unknown shape {shape!r}")


def pocket_centre(outline: Polygon) -> tuple[float, float]:
    """Where the pocket has the most plastic round it: the centre of the outline's largest inscribed circle
    (shapely's polylabel), put on the mirror axis x = 0 (every shape is symmetric about it) and rounded to 0.01 mm
    so the build is repeatable. ``Layout.pocket_fits`` checks the wall at exactly this point."""
    centre = polylabel(outline, tolerance=0.005)
    return (0.0, round(float(centre.y), 2) + 0.0)


class Layout:
    """Derived dimensions shared by ``build_part``, ``validate_combination``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.shape = str(p["shape"])
        self.S = float(p["size_mm"])
        self.T = float(p["thickness_mm"])
        unit = _unit_outline(self.shape)
        ux0, uy0, ux1, uy1 = unit.bounds
        k = self.S / max(ux1 - ux0, uy1 - uy0)  # longest side = S
        scaled = affinity.scale(unit, k, k, origin=(0.0, 0.0))
        x0, y0, x1, y1 = scaled.bounds
        self.outline: Polygon = affinity.translate(scaled, -(x0 + x1) / 2.0, -(y0 + y1) / 2.0)
        self.pocket_xy = pocket_centre(self.outline)
        self.pocket_poly = plates.hole(*self.pocket_xy, POCKET_D_MM)

    @property
    def skin_mm(self) -> float:
        """Plastic between the pocket's floor and the face."""
        return self.T - POCKET_DEPTH_MM

    def pocket_fits(self) -> bool:
        """The pocket keeps ``POCKET_WALL_MM`` of plastic to the outline all round."""
        outer = POCKET_D_MM / 2.0 / math.cos(math.pi / plates.CIRCLE_SEGMENTS)  # the pocket polygon's corners
        return self.outline.buffer(-POCKET_WALL_MM).contains(plates.disc(*self.pocket_xy, outer))

    def printable_rect(self) -> tuple[float, float, float, float] | None:
        """The largest 4:3 rectangle (a phone photo); on a small heart or star, where that is under
        ``MIN_PRINTABLE_MM`` on a side, the largest square, which is taller there and still takes a name or a motif."""
        region = self.outline.buffer(-ANCHOR_MARGIN_MM)
        rect = plates.symmetric_rect(region)
        def short_side(r: tuple[float, float, float, float] | None) -> float:
            return 0.0 if r is None else min(r[2] - r[0], r[3] - r[1])

        if short_side(rect) < plates.MIN_PRINTABLE_MM:
            square = plates.symmetric_rect(region, aspect=1.0)
            if short_side(square) > short_side(rect):
                return square
        return rect

    def printable_size(self) -> tuple[float, float]:
        rect = self.printable_rect()
        if rect is None:
            return (0.0, 0.0)
        return (rect[2] - rect[0], rect[3] - rect[1])

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        rect = self.printable_rect()
        if rect is None:  # pragma: no cover - validate_combination refuses these
            raise ParamOutOfRange(["size_mm"], "This badge has no room left for a picture")
        x0, y0, x1, y1 = rect
        return {
            "face": AnchorFrame(
                np.array([(x0 + x1) / 2.0, (y0 + y1) / 2.0, self.T]), [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=(x1 - x0, y1 - y0)
            ),
        }


PARAMS = {
    "shape": Param("enum", "Shape", "disc", options=SHAPES, group="Shape"),
    "size_mm": Param("number", "Size", 30.0, "mm", 20, 40, 1, handle=True, group="Size", description="Longest side."),
    "thickness_mm": Param("number", "Thickness", 3.0, "mm", 3.0, 4.0, 0.1, group="Size"),
}


@lru_cache(maxsize=None)
def smallest_size(shape: str, thickness_mm: float) -> float | None:
    """The smallest whole size at which ``shape`` holds the pin pocket and a picture (None when no size in range does)."""
    size, step = PARAMS["size_mm"].min, PARAMS["size_mm"].step
    while size <= PARAMS["size_mm"].max:
        layout = Layout({"shape": shape, "size_mm": size, "thickness_mm": thickness_mm})
        if layout.pocket_fits() and min(layout.printable_size()) >= plates.MIN_PRINTABLE_MM:
            return float(size)
        size += step
    return None


_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class PinBadge(Template):
    id = "pin_badge"
    version = 1
    family = "pin"
    name = "Billa pin badge"
    description = (
        "A small badge with a tie-tack pin set into a pocket on its back and a butterfly clutch to hold it "
        "on a shirt or a bag. Your name, motif or photo sits in relief on the face."
    )
    environment = "studio"
    params = PARAMS
    anchors = (
        Anchor(
            "face", "Face", "planar", size_mm=_size("face"), bleed_mm=BLEED_MM,
            accepts=("relief_image", "emboss_text", "motif"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    # 0.8 mm like the pendant: on a 20-40 mm badge the strokes of a raised name are the thinnest thing inspect samples, and
    # they are held to min_feature_mm; the plate (3-4 mm) and the skin over the pocket (validate_content, 1.2 mm) stay thicker.
    constraints = TemplateConstraints(min_wall_mm=0.8, max_overhang_deg=55, bed_mm=(250, 250, 250))
    style_variants = ("comic_pop",)  # Katha's comic-book look: names and motifs raised and bolder by default (features.styles)
    features_supported = ("relief_image", "emboss_text", "motif")
    hardware = (HardwareRef(PIN_SKU, 1),)
    min_feature_mm = 0.8

    @classmethod
    def hardware_for(cls, params: Mapping[str, Any]) -> list[dict[str, Any]]:
        return [{"sku": PIN_SKU, "qty": 1}]

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        layout = Layout(params)
        if layout.skin_mm < plates.MIN_SKIN_MM - 1e-9:
            raise ParamOutOfRange(
                ["thickness_mm"],
                (
                    f"A {layout.T:g} mm badge leaves {layout.skin_mm:.1f} mm over the {POCKET_DEPTH_MM:g} mm pin pocket; "
                    f"make it at least {POCKET_DEPTH_MM + plates.MIN_SKIN_MM:g} mm thick."
                ),
                {"skin_mm": round(layout.skin_mm, 3), "min_skin_mm": plates.MIN_SKIN_MM},
            )
        w, h = layout.printable_size()
        if not layout.pocket_fits() or min(w, h) < plates.MIN_PRINTABLE_MM:
            smallest = smallest_size(layout.shape, layout.T)
            advice = f"make it at least {smallest:g} mm" if smallest is not None else "choose another shape"
            reason = (
                f"the pin pocket does not keep {POCKET_WALL_MM:g} mm of plastic to the edge"
                if not layout.pocket_fits()
                else f"there is too little room for a picture ({w:.0f} × {h:.0f} mm)"
            )
            raise ParamOutOfRange(
                ["size_mm", "shape"],
                f"On a {layout.S:g} mm {layout.shape} badge {reason}; {advice}.",
                {"printable_mm": [round(w, 2), round(h, 2)], "min_printable_mm": plates.MIN_PRINTABLE_MM, "min_size_mm": smallest},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(
            features,
            anchors=("face",),
            thickness_mm=float(params["thickness_mm"]),
            noun="badge",
            behind=POCKET_DEPTH_MM,
            behind_what="the pin pad",
        )

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        layout = Layout(params)
        plate = cad.prism(layout.outline, layout.T)
        # the pocket opens on the back: from below the bed up to the pocket floor
        pocket = cad.translate(cad.prism(layout.pocket_poly, POCKET_DEPTH_MM + 1.0), dz=-1.0)
        return cad.cut(plate, [pocket])

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
        shape = {"disc": "round", "shield": "shield-shaped", "star": "star-shaped", "heart": "heart-shaped"}[L.shape]
        return (
            f"A {shape} Billa badge, {L.S:g} mm on its longest side and {L.T:g} mm thick, with a "
            f"{POCKET_D_MM:g} × {POCKET_DEPTH_MM:g} mm pocket on the back. Glue the tie-tack pin's "
            f"{PAD_D_MM:g} mm pad into the back pocket and let it set before packing; the butterfly clutch goes in "
            "the box loose, not on the pin."
        )
