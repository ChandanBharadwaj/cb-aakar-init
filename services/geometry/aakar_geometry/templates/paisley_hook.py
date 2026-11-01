"""``paisley_hook@1`` - Khoonti: a paisley, lotus or arrow wall plate with a leaning peg; a motif sits on the plate's upper half.

Coordinate frame (mm), **as printed = as modelled**: the plate lies on the bed on its back. X = across (the viewer's
right when the hook is on the wall), Y = up the plate (the top of the plate when mounted is +Y), Z = out of the wall:
the back rests on the bed at Z = 0, the mounted front face is at Z = ``plate_mm`` and the peg rises from it in +Z.
The part is centred in X and Y; the plate's height along Y is its longest extent (the peg's reach is always shorter),
so ``height_mm`` is the family's envelope figure.

Pieces
  plate   the outline (``paisley``: a teardrop, fat end down, with a curl lobe on the tip's flank; ``lotus``: five
          round-tipped petals on a rounded base; ``arrow``: a rounded rectangle with a 90° point at the top and a
          shallow chevron notch at the bottom), scaled so it is exactly ``height_mm`` tall; its width follows the
          shape (about 0.55 × height for the paisley and the arrow, 0.69 × for the lotus), ``plate_mm`` thick.
  holes   two ``SCREW_D_MM`` screw holes (cut ``HOLE_ALLOWANCE_MM`` larger) on the centre line, one as near the top
          and one as near the bottom as ``HOLE_RIM_MM`` of plastic round the countersink allows, each with a
          ``CSK_D_MM`` × ``CSK_DEPTH_MM`` conical countersink cut from the front face (the face prints up, so the
          cone needs no support).
  peg     a ``peg_d_mm`` cylinder ``peg_len_mm`` long rising from the front face just above the bottom screw hole
          (the plate's lower third), leaning ``LEAN_DEG`` toward +Y so a bag strap slides back toward the wall; its
          tip carries a stop ``STOP_MM`` larger in radius on a shallow cone whose leaned underside stays within a
          45° overhang. The peg is buried 1 mm into the plate and trimmed there so nothing reaches the bed.

Anchor
  face  the largest symmetric rectangle on the front face in the plate's upper half, ``ANCHOR_MARGIN_MM`` inside
        the edge, ``CLEAR_MM`` clear of the top countersink and of the peg's footprint (normal +Z): a motif (Buti),
        raised or cut in. A cut-in motif must leave ``plates.MIN_SKIN_MM`` of the plate (``validate_content``).

Hardware: ``nameplate_screws`` × 1, the pair of wall screws and anchors; rated 2 kg on two wall anchors.
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely import affinity
from shapely.geometry import LineString, Polygon, box
from shapely.ops import unary_union

from .. import cad
from ..errors import ParamOutOfRange
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

SCREW_SKU = "nameplate_screws"
LOAD_RATING_KG = 2  # shown on the listing: two wall anchors in masonry, a strap or a coat, not a ladder
SCREW_D_MM = 4.5  # the pack's wall screws
CSK_D_MM = 9.0  # countersink across, at the front face
CSK_DEPTH_MM = 2.0  # countersink depth: the screw head sits flush on a 4 mm plate and still leaves 2 mm under it
HOLE_RIM_MM = 2.0  # plastic kept between a countersink and the plate's edge (min wall 1.6 plus a little)
CLEAR_MM = 2.0  # between the bottom countersink and the peg, and between the anchor and the top countersink / the peg
ANCHOR_MARGIN_MM = 1.5
BLEED_MM = 0.5
MAX_RELIEF_MM = 1.5
LEAN_DEG = 20.0  # the peg leans toward the top of the plate so a strap stays on
STOP_MM = 1.0  # the tip's stop stands this much proud of the shaft all round (a 2 mm larger disc)
STOP_RISE_MM = 2.4  # the stop's underside cone rises this much for STOP_MM out: 22.6° off the axis, so with the 20° lean
#                     the downhill side overhangs 42.6° from vertical, inside the 45° rule (a 45° chamfer would be 65°)
STOP_H_MM = 1.5  # the stop's straight band above the cone
BURY_MM = 1.0  # the peg reaches this far into the plate so the union has no coplanar seam at the face
WIDTH_SHARE = 0.55  # the unit outlines are drawn height / width = 1 / WIDTH_SHARE; the lotus comes out a little wider

SHAPES = ("paisley", "lotus", "arrow")


def _ellipse(cx: float, cy: float, a: float, b: float, segments: int = 96) -> Polygon:
    t = np.linspace(0.0, 2.0 * math.pi, segments, endpoint=False)
    return Polygon(np.column_stack([cx + a * np.cos(t), cy + b * np.sin(t)]))


def _paisley_unit(aspect: float) -> Polygon:
    """A teardrop ``aspect`` tall and one unit wide: a big disc low, a fat tip disc nudged to +x (the hole still sits on
    the centre line), their hull, plus a curl lobe on the tip's flank; tips softened."""
    big = plates.disc(0.0, 0.5, 0.5, 144)
    tip = plates.disc(0.04, aspect - 0.26, 0.26, 96)
    hull = unary_union([big, tip]).convex_hull
    curl = plates.disc(0.27, aspect - 0.46, 0.17, 96)
    return plates.soften(plates.largest_polygon(hull.union(curl)), 0.04)


def _lotus_unit(aspect: float) -> Polygon:
    """Five round-tipped petals (the hull of a tip disc and a base disc each) on a rounded base, ``aspect`` tall."""
    base = affinity.translate(plates.rounded_rect(1.0, 0.7, 0.2), 0.0, 0.35)
    lobes = [base]
    petals = {0: (0.0, 0.24, 1.0), 1: (0.26, 0.19, 0.84), 2: (0.48, 0.15, 0.64)}  # |i| -> (x, tip radius, tip height share)
    for i in range(-2, 3):
        x, rho, share = petals[abs(i)]
        x = math.copysign(x, i) if i else 0.0
        tip = plates.disc(x, share * aspect - rho, rho, 96)
        root = plates.disc(x * 0.8, 0.45, rho * 0.9, 96)
        lobes.append(unary_union([tip, root]).convex_hull)
    return plates.soften(plates.largest_polygon(unary_union(lobes)), 0.03)


def _arrow_unit(aspect: float) -> Polygon:
    """One unit wide and ``aspect`` tall: a 90° point at the top, a shallow chevron notch at the bottom, tips softened."""
    notch = 0.10
    pts = [(-0.5, 0.0), (0.0, notch), (0.5, 0.0), (0.5, aspect - 0.5), (0.0, aspect), (-0.5, aspect - 0.5)]
    return plates.soften(Polygon(pts), 0.06)


_UNITS = {"paisley": _paisley_unit, "lotus": _lotus_unit, "arrow": _arrow_unit}


def _bottom_at(poly: Polygon, x: float = 0.0) -> float | None:
    """Lowest y of ``poly`` on the vertical line through ``x`` (None when the line misses it)."""
    if poly.is_empty:
        return None
    minx, miny, maxx, maxy = poly.bounds
    cut = poly.intersection(LineString([(x, miny - 1.0), (x, maxy + 1.0)]))
    return None if cut.is_empty else float(cut.bounds[1])


class Layout:
    """Derived dimensions shared by ``build_part``, ``validate_combination``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.shape = str(p["shape"])
        self.H = float(p["height_mm"])
        self.T = float(p["plate_mm"])
        self.L = float(p["peg_len_mm"])
        self.D = float(p["peg_d_mm"])
        unit = _UNITS[self.shape](1.0 / WIDTH_SHARE)
        x0, y0, x1, y1 = unit.bounds
        k = self.H / (y1 - y0)  # exactly H tall; the width follows the shape
        poly = affinity.scale(unit, k, k, origin=(0.0, 0.0))
        x0, y0, x1, y1 = poly.bounds
        self.outline: Polygon = affinity.translate(poly, -(x0 + x1) / 2.0, -(y0 + y1) / 2.0)
        self.W = x1 - x0
        # screw holes: as near the top and the bottom of the centre line as the countersink's rim allows
        self.screw_cut = SCREW_D_MM + plates.HOLE_ALLOWANCE_MM
        inner = self.outline.buffer(-(CSK_D_MM / 2.0 + HOLE_RIM_MM))
        y_top, y_bot = plates.top_at(inner, 0.0), _bottom_at(inner, 0.0)
        self.holes_fit = y_top is not None and y_bot is not None and y_top - y_bot >= CSK_D_MM + CLEAR_MM
        self.y_top = float(y_top) if y_top is not None else self.H / 2.0
        self.y_bot = float(y_bot) if y_bot is not None else -self.H / 2.0
        self.hole_polys = [plates.hole(0.0, y, self.screw_cut) for y in (self.y_top, self.y_bot)]
        # the peg: its footprint on the face is an ellipse (the lean stretches it along Y), just above the bottom hole
        self.r = self.D / 2.0
        self.lean = math.radians(LEAN_DEG)
        self.r_y = self.r / math.cos(self.lean)
        self.y_peg = self.y_bot + CSK_D_MM / 2.0 + CLEAR_MM + self.r_y
        self.footprint: Polygon = _ellipse(0.0, self.y_peg, self.r, self.r_y)
        self.profile: Polygon = self.outline.difference(unary_union(self.hole_polys))

    @property
    def reach(self) -> tuple[float, float]:
        """``(z, y)`` of the peg's farthest point: the stop's rim at the tip."""
        tip = self.L + STOP_RISE_MM + STOP_H_MM
        z = self.T + tip * math.cos(self.lean) + (self.r + STOP_MM) * math.sin(self.lean)
        y = self.y_peg + tip * math.sin(self.lean) + (self.r + STOP_MM) * math.cos(self.lean)
        return z, y

    def peg_fits(self) -> bool:
        """The peg's footprint keeps ``ANCHOR_MARGIN_MM`` of plate round it."""
        return self.outline.buffer(-ANCHOR_MARGIN_MM).contains(self.footprint)

    def printable_rect(self) -> tuple[float, float, float, float] | None:
        """``(x0, y0, x1, y1)`` of the motif area: the upper half, inside the edge, clear of the top countersink and the peg."""
        upper = box(-self.W, 0.0, self.W, self.H)
        keep_hole = plates.disc(0.0, self.y_top, CSK_D_MM / 2.0 + CLEAR_MM)
        keep_peg = _ellipse(0.0, self.y_peg, self.r + CLEAR_MM, self.r_y + CLEAR_MM)
        region = self.outline.buffer(-ANCHOR_MARGIN_MM).intersection(upper).difference(keep_hole).difference(keep_peg)
        return plates.symmetric_rect(region)

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        rect = self.printable_rect()
        if rect is None:  # pragma: no cover - validate_combination refuses these
            raise ParamOutOfRange(["height_mm", "peg_d_mm"], "This hook has no room left for a motif")
        x0, y0, x1, y1 = rect
        return {
            "face": AnchorFrame(
                np.array([(x0 + x1) / 2.0, (y0 + y1) / 2.0, self.T]), [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=(x1 - x0, y1 - y0)
            ),
        }


PARAMS = {
    "shape": Param("enum", "Shape", "paisley", options=SHAPES, group="Shape"),
    "height_mm": Param("number", "Height", 90.0, "mm", 60, 120, 1, handle=True, group="Size", description="Top to bottom of the plate."),
    "plate_mm": Param("number", "Plate", 5.0, "mm", 4, 6, 0.5, group="Size", description="Thickness of the plate against the wall."),
    "peg_len_mm": Param("number", "Peg reach", 28.0, "mm", 20, 35, 1, handle=True, group="Details", description="How far the peg stands out from the plate."),
    "peg_d_mm": Param("number", "Peg", 12.0, "mm", 10, 14, 0.5, group="Details", description="Across the peg."),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class PaisleyHook(Template):
    id = "paisley_hook"
    version = 1
    family = "wall_hook"
    name = "Khoonti wall hook"
    description = (
        "A paisley, lotus or arrow plate with a stout peg that leans up so a bag strap stays on; rated 2 kg on the "
        "two wall anchors in the pack. Your motif sits on the plate above the peg."
    )
    environment = "studio"
    params = PARAMS
    anchors = (
        Anchor("face", "Face", "planar", size_mm=_size("face"), bleed_mm=BLEED_MM, accepts=("motif",), max_relief_mm=MAX_RELIEF_MM),
    )
    constraints = TemplateConstraints(min_wall_mm=1.6, max_overhang_deg=45, bed_mm=(250, 250, 250))
    features_supported = ("motif",)
    hardware = (HardwareRef(SCREW_SKU, 1),)  # the pair of wall screws and anchors
    min_feature_mm = 0.8

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        L = Layout(params)
        if not L.holes_fit or not L.peg_fits():
            raise ParamOutOfRange(
                ["height_mm", "peg_d_mm"],
                f"A {L.H:g} mm {L.shape} plate has no room for two screw holes and a {L.D:g} mm peg; make it taller or the peg slimmer.",
                {"height_mm": L.H, "peg_d_mm": L.D},
            )
        rect = L.printable_rect()
        w = h = 0.0
        if rect is not None:
            w, h = rect[2] - rect[0], rect[3] - rect[1]
        if min(w, h) < plates.MIN_PRINTABLE_MM:
            raise ParamOutOfRange(
                ["height_mm", "peg_d_mm"],
                (
                    f"A {L.H:g} mm {L.shape} plate with a {L.D:g} mm peg leaves too little room above the peg for a motif "
                    f"({w:.0f} × {h:.0f} mm); make it taller or the peg slimmer."
                ),
                {"printable_mm": [round(w, 2), round(h, 2)], "min_printable_mm": plates.MIN_PRINTABLE_MM},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("face",), thickness_mm=float(params["plate_mm"]), noun="plate", thickness_key="plate_mm")

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        L = Layout(params)
        plate = cad.prism(L.profile, L.T)
        # countersinks from the front: a cone from the hole's cut size at CSK_DEPTH_MM below the face to CSK_D_MM at the
        # face, carried on past the face so the cut has no coplanar seam
        hole_outer = (L.screw_cut / 2.0) / math.cos(math.pi / plates.CIRCLE_SEGMENTS)  # the hole polygon's corners
        slope = (CSK_D_MM / 2.0 - hole_outer) / CSK_DEPTH_MM
        sinks = [
            cad.translate(cad.frustum(hole_outer, CSK_D_MM / 2.0 + slope, CSK_DEPTH_MM + 1.0), 0.0, y, L.T - CSK_DEPTH_MM)
            for y in (L.y_top, L.y_bot)
        ]
        plate = cad.cut(plate, sinks)
        # the peg, built upright on the origin (the face point its axis passes through), then leaned and placed
        bury = L.r * math.tan(L.lean) + BURY_MM  # the leaned base disc sits wholly inside the plate
        shaft = cad.cylinder(L.r, L.L + bury, 0.0, 0.0, -bury)
        cone = cad.translate(cad.frustum(L.r, L.r + STOP_MM, STOP_RISE_MM), 0.0, 0.0, L.L)
        cap = cad.cylinder(L.r + STOP_MM, STOP_H_MM, 0.0, 0.0, L.L + STOP_RISE_MM)
        peg = cad.rotate_x(cad.union([shaft, cone, cap]), -LEAN_DEG)  # +Z turns toward +Y: the tip leans up the plate
        peg = cad.translate(peg, 0.0, L.y_peg, L.T)
        peg = cad.intersect(peg, cad.box(L.W + 40.0, L.H + 40.0, 200.0, 0.0, 0.0, L.T - BURY_MM))  # nothing below the plate's skin
        return cad.union([plate, peg])

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
        shape = {"paisley": "paisley", "lotus": "lotus", "arrow": "arrow"}[L.shape]
        return (
            f"A {shape} Khoonti hook, {L.H:g} mm tall and {L.W:.0f} mm wide on a {L.T:g} mm plate, with a {L.D:g} mm peg "
            f"reaching {L.L:g} mm and leaning {LEAN_DEG:g}° up the plate so a strap stays on; printed flat on its back, "
            f"no supports. Rated {LOAD_RATING_KG:g} kg on two wall anchors: pack the pair of {SCREW_D_MM:g} mm screws and "
            f"anchors, which go through the two countersunk holes top and bottom, countersinks on the front."
        )
