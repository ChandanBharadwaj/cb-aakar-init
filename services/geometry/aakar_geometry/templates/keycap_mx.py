"""``keycap_mx@1`` - Kunji: one 1u keycap for a Cherry MX switch, with an emblem or a letter on top.

Coordinate frame (mm): the keycap sits as it does on a switch, rim and stem end on Z = 0, top up, X across
(the viewer's right), centred in X and Y. It is also printed this way, **stem down**: the cross slot is then
drawn by the nozzle in the plane FDM holds best, the top (and its emblem) prints last and clean, and the
hollow's ceiling only bridges the few millimetres between the walls and the stem. Resin is recommended; on
FDM a test fit on a switch decides the slop.

Pieces (DSA-like, KeyV2's numbers)
  shell  a rounded square ``BOTTOM_MM`` (18.16, the 1u footprint) at the rim, straight for ``SKIRT_MM``, then
         tapering to ``TOP_MM`` (12.7) at ``HEIGHT_MM``; ``WALL_MM`` (1.5 mm) walls square to the slope and a
         ``TOP_THICKNESS_MM`` top over the hollow. The top is flat with a slight spherical dish, ``DISH_MM``
         deep at the centre, meeting the flat ``DISH_RIM_MM`` inside the middle of each edge.
  stem   a ``STEM_OD_MM`` round stem from the rim up to the top, with the MX cross cut ``STEM_DEPTH_MM`` deep
         (at least the 3.6 mm the switch needs): KeyV2's ``cherry_cross`` with ``stem_slop_mm`` s, a bar
         (4.03 + s) × (1.25 + s/3) and a bar (1.15 + s/3) × (4.23 + s/3). The slop defaults to 0.4 mm for FDM
         (KeyV2 uses 0.3–0.35 for resin). By the MX standard the stem is only about 0.5 mm thick beyond the
         cross's tips, thinner than FDM's 0.8 mm floor: another reason resin is recommended.

Anchor
  top  the top face (normal +Z), origin at the bottom of the dish, ``ANCHOR_MARGIN_MM`` inside the top's
       edges: a photo relief (an emblem) or 1–3 letters (the family's ``max_text_chars``), raised or cut at
       most ``MAX_RELIEF_MM`` (0.6 mm). Raised content starts 0.5 mm below the anchor plane and cutters end
       0.5 mm above it, both more than the 0.3 mm dish, so content fuses to the dished top everywhere; the
       deepest cut leaves 1.3 mm over the hollow. Letters must keep strokes of ``MIN_FEATURE_MM`` (1 mm, stricter
       than the 0.8 mm elsewhere: on a cap this small thinner strokes read as walls too thin to print).
No hardware.
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely.geometry.polygon import orient

from ..errors import GeometryError
from ..features.booleans import difference, union
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, Param, Template, TemplateConstraints

BOTTOM_MM = 18.16  # KeyV2 $bottom_key_width: the 1u footprint
TOP_MM = 12.7  # DSA top
HEIGHT_MM = 7.4  # DSA
CORNER_MM = 1.5  # corner radius at the rim (scaled with the taper)
SKIRT_MM = 1.2  # straight sides at the rim before the taper begins
WALL_MM = 1.5  # square to the sloping side (KeyV2 $wall_thickness 3 is both walls)
TOP_THICKNESS_MM = 2.2  # top plane to the hollow's ceiling: dish 0.3 + deepest cut 0.6 + 1.3 mm left
DISH_MM = 0.3
DISH_RIM_MM = 0.5  # the dish meets the flat top this far inside the middle of each edge
STEM_OD_MM = 5.5  # KeyV2 $rounded_cherry_stem_d
STEM_DEPTH_MM = 4.0  # KeyV2 $stem_throw; the switch needs at least 3.6
ANCHOR_MARGIN_MM = 0.8
BLEED_MM = 0.5
MAX_RELIEF_MM = 0.6
MIN_FEATURE_MM = 1.0


def cross_bars(slop: float) -> tuple[tuple[float, float], tuple[float, float]]:
    """KeyV2 ``cherry_cross(slop)``: the two bars of the cross slot, ``(width x, height y)`` each."""
    return ((4.03 + slop, 1.25 + slop / 3.0), (1.15 + slop / 3.0, 4.23 + slop / 3.0))


def loft(width: float, corner: float, levels: Sequence[tuple[float, float]]) -> trimesh.Trimesh:
    """A closed solid through rounded squares: ``levels`` = ``[(z, width at z), ...]`` from the bottom up, each
    the ``width`` × ``width`` square with ``corner`` radius scaled to it. Convex sections, so each cap is a fan."""
    ring = np.asarray(orient(plates.rounded_rect(width, width, corner, quad_segs=8), sign=1.0).exterior.coords)[:-1]
    n = len(ring)
    vertices = [np.column_stack([ring * (w / width), np.full(n, z)]) for z, w in levels]
    bottom_c, top_c = len(levels) * n, len(levels) * n + 1
    vertices.append(np.array([[0.0, 0.0, levels[0][0]], [0.0, 0.0, levels[-1][0]]]))
    k = np.arange(n)
    faces = []
    for level in range(len(levels) - 1):
        a = level * n + k
        b = level * n + (k + 1) % n
        faces += [np.column_stack([a, b, b + n]), np.column_stack([a, b + n, a + n])]
    last = (len(levels) - 1) * n
    faces.append(np.column_stack([np.full(n, bottom_c), (k + 1) % n, k]))  # bottom, facing −Z
    faces.append(np.column_stack([np.full(n, top_c), last + k, last + (k + 1) % n]))  # top, facing +Z
    mesh = trimesh.Trimesh(vertices=np.vstack(vertices), faces=np.vstack(faces), process=False)
    if not (mesh.is_watertight and mesh.is_winding_consistent and mesh.volume > 0):  # pragma: no cover
        raise GeometryError("The keycap's shell did not close", {"levels": [list(v) for v in levels]})
    return mesh


class Layout:
    """Derived dimensions shared by ``build_body``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.slop = float(p["stem_slop_mm"])
        self.bars = cross_bars(self.slop)
        self.slope = (BOTTOM_MM - TOP_MM) / 2.0 / (HEIGHT_MM - SKIRT_MM)  # inward per mm of height, above the skirt
        self.wall_h = WALL_MM * math.sqrt(1.0 + self.slope**2)  # the tapered wall measured level
        self.ceiling = HEIGHT_MM - TOP_THICKNESS_MM
        dish_r = TOP_MM / 2.0 - DISH_RIM_MM
        self.dish_radius = (dish_r**2 + DISH_MM**2) / (2.0 * DISH_MM)  # the sphere through the rim and the dish bottom
        self.anchor_size = TOP_MM - 2.0 * ANCHOR_MARGIN_MM

    def width_at(self, z: float) -> float:
        return BOTTOM_MM - 2.0 * self.slope * max(z - SKIRT_MM, 0.0)

    def body(self) -> trimesh.Trimesh:
        shell = loft(BOTTOM_MM, CORNER_MM, [(0.0, BOTTOM_MM), (SKIRT_MM, BOTTOM_MM), (HEIGHT_MM, TOP_MM)])
        # the hollow: its sides parallel to the shell's, WALL_MM in, open below the rim
        inner = BOTTOM_MM - 2.0 * self.wall_h
        hollow = loft(inner, 0.5, [(-1.0, inner), (SKIRT_MM, inner), (self.ceiling, self.width_at(self.ceiling) - 2.0 * self.wall_h)])
        body = difference(shell, hollow)
        stem = trimesh.creation.cylinder(radius=STEM_OD_MM / 2.0, height=self.ceiling + 0.5, sections=96)
        stem.apply_translation([0.0, 0.0, (self.ceiling + 0.5) / 2.0])
        body = union(body, stem)
        for w, h in self.bars:
            slot = trimesh.creation.box(extents=[w, h, STEM_DEPTH_MM + 1.0])
            slot.apply_translation([0.0, 0.0, (STEM_DEPTH_MM - 1.0) / 2.0])  # from 1 mm below the rim to the slot's floor
            body = difference(body, slot)
        dish = trimesh.creation.icosphere(subdivisions=5, radius=self.dish_radius)
        dish.apply_translation([0.0, 0.0, HEIGHT_MM - DISH_MM + self.dish_radius])
        return difference(body, dish)

    def anchor_frame(self) -> AnchorFrame:
        size = (self.anchor_size, self.anchor_size)
        return AnchorFrame(np.array([0.0, 0.0, HEIGHT_MM - DISH_MM]), [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=size)


PARAMS = {
    "stem_slop_mm": Param(
        "number", "Stem fit", 0.4, "mm", 0.3, 0.5, 0.05, group="Details",
        description="Extra room in the cross slot: 0.3 for resin, 0.4 for FDM; more if it binds on the switch.",
    ),
}

_DEFAULT_LAYOUT = Layout({k: p.default for k, p in PARAMS.items()})


class KeycapMx(Template):
    id = "keycap_mx"
    version = 1
    family = "keycap"
    name = "Kunji keycap"
    description = (
        "One 1u keycap for Cherry MX style switches, with a slightly dished top carrying your emblem or up to "
        "three letters."
    )
    environment = "desk_oak"
    params = PARAMS
    anchors = (
        Anchor(
            "top", "Top", "planar", max_text_height_mm=round(_DEFAULT_LAYOUT.anchor_size - 2.0 * BLEED_MM, 1),
            size_mm=(round(_DEFAULT_LAYOUT.anchor_size, 2), round(_DEFAULT_LAYOUT.anchor_size, 2)), bleed_mm=BLEED_MM,
            accepts=("relief_image", "emboss_text"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    # the walls are 1.5 mm; the MX stem is thinner at its cross's tips by the standard, so the check's floor is FDM's 0.8
    constraints = TemplateConstraints(min_wall_mm=0.8, max_overhang_deg=55, bed_mm=(250, 250, 250))
    style_variants = ("comic_pop",)  # Katha's comic-book look: names and motifs raised and bolder by default (features.styles)
    features_supported = ("relief_image", "emboss_text")
    hardware = ()
    min_feature_mm = MIN_FEATURE_MM

    @classmethod
    def build_body(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        mesh = Layout(params).body()
        mesh.apply_translation([0.0, 0.0, -float(mesh.bounds[0][2])])
        return mesh

    @classmethod
    def anchor_frame(cls, anchor_id: str, params: Mapping[str, Any]) -> AnchorFrame:
        if anchor_id != "top":
            raise NotImplementedError(f"{cls.ref()} does not place anchor {anchor_id!r}")
        return Layout(params).anchor_frame()

    @classmethod
    def karigar_note(cls, params: Mapping[str, Any]) -> str:
        L = Layout(params)
        (aw, ah), (bw, bh) = L.bars
        return (
            f"A Kunji 1u keycap for a Cherry MX switch, {BOTTOM_MM:g} mm square at the rim with a {TOP_MM:g} mm dished top, "
            f"{WALL_MM:g} mm walls and a cross slot {aw:.2f} × {ah:.2f} and {bw:.2f} × {bh:.2f} mm ({L.slop:g} mm slop), "
            f"{STEM_DEPTH_MM:g} mm deep. Resin is recommended: it holds the stem's fit best. On FDM print it stem down as "
            "modelled at 0.1–0.12 mm layers, and test-fit the first one on a switch: more slop if it binds, less if it "
            "wobbles."
        )
