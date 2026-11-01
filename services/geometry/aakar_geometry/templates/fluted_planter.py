"""``fluted_planter@1`` - Gamla: a faceted pot with a flat rim and a built-in catch reservoir; a name or motif sits on the
front facet or on the rim.

Coordinate frame (mm): X across, Y depth (−Y is the front), Z up. The pot stands on its base at Z = 0, is centred in
X and Y, and is printed standing as modelled: no supports.

Pieces
  shell      a regular ``facets``-gon (even, so one flat faces the front) lofted from the base to the rim; ``width_mm``
             is the pot's longest side at the rim (across the flats, or across the corners when a corner faces ±X, as
             with ten facets) and ``height_mm`` its full height. The wall is ``wall_mm`` thick and leans outward going
             up: the base is ``TAPER_SHARE`` of the width narrower than the top of the wall (under 8°).
  rim        the wall thickens at the top into a lip ``LIP_H_MM`` tall whose flat top is a polygonal ring ``RIM_W_MM``
             wide (from the inside of the pot to the lip's outer edge). Under the lip a chamfer rises
             ``CHAMFER_RISE`` mm per mm it reaches out (41° from vertical), so the lip prints without support.
  floor      the base plate ``FLOOR_MM`` thick; the inner floor, also ``FLOOR_MM``, sits ``RESERVOIR_H_MM`` above it.
             Water that drains through the inner floor collects in the sealed reservoir between the two and spills
             from a ``SPOUT_W_MM`` × ``SPOUT_H_MM`` spout slot in the front wall just under the inner floor; tip the pot
             forward to empty it. Chosen over a conical floor: the inner floor stays flat and bridges the reservoir on
             ``RIB_MM`` ribs that stand on the base plate, running front to back at a pitch that keeps every bridge
             under ``MAX_BRIDGE_MM`` (a 45° cone would stand 40 mm tall in the widest pot). The ribs stop
             ``RIB_GAP_MM`` short of the wall so the water reaches the spout from every bay.
  drains     five ``DRAIN_D_MM`` holes (plus ``plates.HOLE_ALLOWANCE_MM``) through the inner floor: one in the middle,
             four around it; a hole over a rib nicks the rib's top by ``HOLE_INTO_RIB_MM`` so the water runs off it.

Anchors
  band  the front facet (its true outward normal, tilted down with the lean; u = the viewer's right, v up the facet):
        from ``BAND_FROM`` to ``BAND_TO`` of the height (stopping short of the chamfer in a short wide pot), the facet's
        width less ``MARGIN_MM`` each side. A cut-in name or motif must leave ``plates.MIN_SKIN_MM`` of the wall.
  rim   the front facet's segment of the ring's flat top (normal +Z, u = the viewer's right, v away from the viewer):
        the facet's width at the rim less ``MARGIN_MM`` each side by ``RIM_W_MM`` less ``RIM_MARGIN_MM`` each side. A
        cut-in must leave ``plates.MIN_SKIN_MM`` of the ``LIP_H_MM`` lip.
No hardware; pot only, the plants are the customer's.
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely.geometry import Polygon, box
from shapely.ops import unary_union

from .. import cad
from ..errors import ParamOutOfRange
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

RIM_W_MM = 11.0  # the ring's flat top, inside face to lip edge; 10 would leave the rim anchor 8.0 mm deep, where a lotus is only just coarse enough to print
RIM_MARGIN_MM = 1.0  # the rim anchor stays this far inside the ring's two edges
LIP_H_MM = 6.0  # the lip's straight band under the rim
CHAMFER_RISE = 1.15  # the chamfer under the lip rises this much per mm it reaches out: 41° from vertical, inside the 45° rule
CHAMFER_INSET_MM = 0.5  # the chamfer starts this far inside the wall so its union with the shell has no coplanar seam
TAPER_SHARE = 0.12  # the base is this share of the width narrower than the wall's top (the walls lean under 8°)
FLOOR_MM = 2.4  # the base plate and the inner floor: six layers at 0.4 mm, stiff enough to bridge between the ribs
RESERVOIR_H_MM = 8.0  # clear height of the catch reservoir between the base plate and the inner floor
RIB_MM = 2.0  # ribs under the inner floor, five perimeters
RIB_GAP_MM = 6.0  # a rib stops this far short of the wall so every bay drains to the spout
MAX_BRIDGE_MM = 40.0  # the longest bridge an FDM printer lays cleanly: the floor spans no more than this between ribs
SPOUT_W_MM = 6.0
SPOUT_H_MM = 3.0
SPOUT_DROP_MM = 0.5  # the spout's roof sits this far under the inner floor (no coplanar face with the reservoir's ceiling)
SPOUT_REACH_MM = 2.0  # the spout cut reaches this far into the reservoir past the wall
DRAIN_D_MM = 5.0
DRAIN_RING_SHARE = 0.5  # the four outer drain holes sit at this share of the inside half-width from the centre
HOLE_INTO_RIB_MM = 1.0  # a drain hole cut reaches this far into a rib under it
BAND_FROM, BAND_TO = 0.25, 0.75  # the band anchor spans this part of the height
BAND_CLEAR_MM = 1.5  # the band stops this far below where the chamfer leaves the wall
MARGIN_MM = 1.5  # the anchors stay this far inside the facet's two edges
BLEED_MM = 1.0
MAX_RELIEF_MM = 1.5
OVERCUT_MM = 1.0  # cuts run this far past the faces they open so no boolean meets a coplanar face


def ngon(n: int, apothem: float) -> Polygon:
    """A regular ``n``-gon with a flat facing −Y (and +Y: ``n`` is even), ``apothem`` from the centre to each flat."""
    R = apothem / math.cos(math.pi / n)
    a0 = -math.pi / 2.0 + math.pi / n
    return Polygon([(R * math.cos(a0 + 2.0 * math.pi * k / n), R * math.sin(a0 + 2.0 * math.pi * k / n)) for k in range(n)])


def extent_factor(n: int) -> float:
    """The longest axis-aligned extent of ``ngon(n, a)`` over its across-flats width ``2a``: 1 when flats face ±X
    (8 or 12 facets), ``1 / cos(π / n)`` when corners do (10 facets)."""
    R = 1.0 / math.cos(math.pi / n)
    a0 = -math.pi / 2.0 + math.pi / n
    return max(1.0, max(abs(R * math.cos(a0 + 2.0 * math.pi * k / n)) for k in range(n)))


class Layout:
    """Derived dimensions shared by ``build_part``, ``validate_combination``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.W = float(p["width_mm"])
        self.H = float(p["height_mm"])
        self.n = int(p["facets"])
        self.wall = float(p["wall_mm"])
        self.A = self.W / (2.0 * extent_factor(self.n))  # the rim's outer apothem: the pot is exactly W on its long axis
        self.ai_top = self.A - RIM_W_MM  # inside apothem at the rim
        self.ao_top = self.ai_top + self.wall  # the wall's outer apothem at the top
        self.ao_bot = self.ao_top - TAPER_SHARE * self.A  # and at the base
        self.slope = (self.ao_top - self.ao_bot) / self.H  # the wall's lean, outward per mm of height
        self.lip_z0 = self.H - LIP_H_MM
        # the chamfer under the lip: a loft from just inside the wall at z_chamfer out to the lip's edge at lip_z0
        run0 = self.A - self.ao_top + CHAMFER_INSET_MM + self.slope * LIP_H_MM
        self.chamfer_h = CHAMFER_RISE * run0 / (1.0 - CHAMFER_RISE * self.slope)
        self.z_chamfer = self.lip_z0 - self.chamfer_h
        self.ao_chamfer = self.ao(self.z_chamfer) - CHAMFER_INSET_MM
        self.z_chamfer_exit = self.z_chamfer + CHAMFER_INSET_MM / (1.0 / CHAMFER_RISE - self.slope)  # where it leaves the wall
        # floors and the reservoir
        self.z_res0 = FLOOR_MM
        self.z_res1 = FLOOR_MM + RESERVOIR_H_MM
        self.z_floor = self.z_res1 + FLOOR_MM  # the inner floor's top
        self.ai_res = self.ai(self.z_res0)  # inside apothem at the reservoir floor (the narrowest point of the reservoir)
        self.inner_w = 2.0 * self.ai_res
        self.rib_count = max(0, math.ceil(self.inner_w / MAX_BRIDGE_MM) - 1)
        self.rib_xs = [-self.inner_w / 2.0 + (i + 1) * self.inner_w / (self.rib_count + 1) for i in range(self.rib_count)]
        r = DRAIN_RING_SHARE * self.ai_res
        self.drain_xy: list[tuple[float, float]] = [(0.0, 0.0), (r, 0.0), (-r, 0.0), (0.0, r), (0.0, -r)]
        self.drain_cut = DRAIN_D_MM + plates.HOLE_ALLOWANCE_MM

    def ao(self, z: float) -> float:
        """The wall's outer apothem at height ``z``."""
        return self.ao_bot + self.slope * z

    def ai(self, z: float) -> float:
        """The inside apothem at height ``z``."""
        return self.ao(z) - self.wall

    def facet_w(self, z: float) -> float:
        """Width of one outer facet at height ``z``."""
        return 2.0 * self.ao(z) * math.tan(math.pi / self.n)

    @property
    def bay_mm(self) -> float:
        """The widest span the inner floor bridges between the wall and a rib or between two ribs."""
        return (self.inner_w - self.rib_count * RIB_MM) / (self.rib_count + 1)

    def facet_normal(self) -> np.ndarray:
        n = np.array([0.0, -1.0, -self.slope])  # the front facet leans outward going up, so its normal tips down
        return n / np.linalg.norm(n)

    def reservoir(self) -> Any:
        """The reservoir's footprint at its floor less the rib strips (one bay, or several joined round the rib ends)."""
        footprint = ngon(self.n, self.ai_res)
        if not self.rib_count:
            return footprint
        reach = footprint.buffer(-RIB_GAP_MM)
        strips = [box(x - RIB_MM / 2.0, -self.A, x + RIB_MM / 2.0, self.A).intersection(reach) for x in self.rib_xs]
        return footprint.difference(unary_union(strips))

    def band_z(self) -> tuple[float, float]:
        z0 = BAND_FROM * self.H
        z1 = min(BAND_TO * self.H, self.z_chamfer_exit - BAND_CLEAR_MM)
        return z0, z1

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        z0, z1 = self.band_z()
        zc = (z0 + z1) / 2.0
        n = self.facet_normal()
        u = np.array([1.0, 0.0, 0.0])
        slant = (z1 - z0) * math.hypot(1.0, self.slope)
        return {
            "band": AnchorFrame(
                np.array([0.0, -self.ao(zc), zc]), u, np.cross(n, u), n,
                size_mm=(self.facet_w(z0) - 2.0 * MARGIN_MM, slant),
            ),
            "rim": AnchorFrame(
                np.array([0.0, -(self.A - RIM_W_MM / 2.0), self.H]), [1, 0, 0], [0, 1, 0], [0, 0, 1],
                size_mm=(2.0 * self.A * math.tan(math.pi / self.n) - 2.0 * MARGIN_MM, RIM_W_MM - 2.0 * RIM_MARGIN_MM),
            ),
        }


PARAMS = {
    "width_mm": Param(
        "number", "Width", 110.0, "mm", 80, 160, 5, handle=True, group="Size",
        description="Across the rim at its widest; the pot's longest side.",
    ),
    "height_mm": Param("number", "Height", 100.0, "mm", 70, 150, 5, handle=True, group="Size", description="Base to rim, lip included."),
    "facets": Param(
        "integer", "Facets", 10, "count", 8, 12, 2, group="Shape",
        description="Flat sides, an even number so one faces the front and carries the name.",
    ),
    "wall_mm": Param(
        "number", "Wall", 2.4, "mm", 2.0, 3.0, 0.1, group="Details",
        description="The pot's wall; a cut-in name needs 1.2 mm of it left behind.",
    ),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class FlutedPlanter(Template):
    id = "fluted_planter"
    version = 1
    family = "planter"
    name = "Gamla faceted planter"
    description = (
        "A faceted pot with a flat rim and a catch reservoir built into its base: water drains through the inner "
        "floor and spills from a small spout at the front. Your name or a motif sits on the front facet or on the rim."
    )
    environment = "balcony_daylight"
    params = PARAMS
    anchors = (
        Anchor(
            "band", "Front facet", "planar", size_mm=_size("band"), bleed_mm=BLEED_MM,
            accepts=("emboss_text", "motif"), max_relief_mm=MAX_RELIEF_MM,
        ),
        Anchor(
            "rim", "Rim", "planar", size_mm=_size("rim"), bleed_mm=BLEED_MM,
            accepts=("emboss_text", "motif"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.6, max_overhang_deg=45, bed_mm=(250, 250, 250))
    features_supported = ("emboss_text", "motif")
    hardware: tuple[HardwareRef, ...] = ()
    min_feature_mm = 0.8

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        n = int(params["facets"])
        if n % 2:
            raise ParamOutOfRange(
                ["facets"],
                f"A Gamla pot has 8, 10 or 12 facets: an even count puts a flat facet at the front for the name, {n} does not.",
                {"facets": n, "options": [8, 10, 12]},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("band",), thickness_mm=float(params["wall_mm"]), noun="wall", thickness_key="wall_mm")
        plates.check_skin(features, anchors=("rim",), thickness_mm=LIP_H_MM, noun="rim", thickness_key="height_mm")

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        L = Layout(params)
        n = L.n
        shell = cad.loft([(ngon(n, L.ao_bot), 0.0), (ngon(n, L.ao_top), L.H)])
        # the lip and its chamfer in one piece: a 41° loft that would overshoot the lip's edge, clipped to the lip's faces
        flare = cad.loft([(ngon(n, L.ao_chamfer), L.z_chamfer), (ngon(n, L.A + LIP_H_MM / CHAMFER_RISE), L.H)])
        lip = cad.intersect(flare, cad.prism(ngon(n, L.A), L.H + OVERCUT_MM))
        part = cad.union([shell, lip])
        cavity = cad.loft([(ngon(n, L.ai(L.z_floor)), L.z_floor), (ngon(n, L.ai(L.H + OVERCUT_MM)), L.H + OVERCUT_MM)])
        reservoir = cad.translate(cad.prism(L.reservoir(), RESERVOIR_H_MM), dz=L.z_res0)
        y_out, y_in = -(L.A + OVERCUT_MM), -(L.ai_res - SPOUT_REACH_MM)
        spout = cad.box(SPOUT_W_MM, y_in - y_out, SPOUT_H_MM, cy=(y_out + y_in) / 2.0, z0=L.z_res1 - SPOUT_DROP_MM - SPOUT_H_MM)
        z_hole = L.z_res1 - HOLE_INTO_RIB_MM
        drains = [
            cad.translate(cad.prism(plates.hole(x, y, L.drain_cut), L.z_floor + OVERCUT_MM - z_hole), dz=z_hole) for x, y in L.drain_xy
        ]
        return cad.cut(part, [cavity, reservoir, spout, *drains])

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
        ribs = {0: "with no rib needed", 1: "on one rib", 2: "on two ribs", 3: "on three ribs"}.get(L.rib_count, f"on {L.rib_count} ribs")
        return (
            f"A Gamla planter with {L.n} facets, {L.W:g} mm across the rim and {L.H:g} mm tall, {L.wall:g} mm walls and a "
            f"flat rim {RIM_W_MM:g} mm wide. The inner floor sits {RESERVOIR_H_MM:g} mm above the base {ribs}, with five "
            f"{DRAIN_D_MM:g} mm drain holes; water collects in the sealed reservoir under it and spills from the "
            f"{SPOUT_W_MM:g} × {SPOUT_H_MM:g} mm spout low on the front wall, so tip the pot forward to empty it. It stands on "
            f"its base, print as is: no supports, the lip's underside is chamfered and no bridge is longer than "
            f"{MAX_BRIDGE_MM:g} mm. Pot only — the plants and the soil are the customer's."
        )
