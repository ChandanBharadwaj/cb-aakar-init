"""``lotus_lamp@1`` - Deepak: an eight-sided lotus-bud shade on a foot that holds a bought-in USB LED puck; LED only.

The lamp module (``led_base_usb``: a 70 mm USB LED puck, warm white 3 W) is bought in and slides into a pocket under
the foot; the whole lamp is one printed body in a heat-safe finish (the family's ``material_rules.heat_safe_only``).

Coordinate frame (mm): X across the front (the viewer's right when facing the lamp), Y depth (−Y is the front, +Y the
back with the cable notch), Z up. The lamp stands on its foot at Z = 0, is centred in X and Y, and is printed standing
as modelled: the shade only ever leans inward, so there are no overhangs past the taper and no supports.

Pieces
  foot    a regular octagon ``foot_w_mm`` across the flats (flats facing ±X and ±Y), ``FOOT_H_MM`` tall. From the
          underside a ``POCKET_D_MM`` round pocket ``POCKET_DEPTH_MM`` deep takes the LED puck (it slides in from
          below), with a ``NOTCH_W_MM`` notch through the pocket wall at the back for the USB cable. The pocket's roof
          (``FOOT_H_MM − POCKET_DEPTH_MM`` of plastic) is pierced by a ``LIGHT_HOLE_D_MM`` central hole so the light
          rises into the shade.
  shade   an eight-sided shell on top of the foot, tapering from the foot's flats to ``top_w_mm`` across the flats at
          its open top, ``height_mm`` tall in all (foot included); its wall is ``SHELL_MM`` measured square to the
          facet, so the light glows through PETG. The taper from vertical is atan(flare / shade height), at most 18°
          over the whole range: always printable, so no parameter combination is refused. The top edge is a plain
          octagon: an eight-petal crown (a cosine point per facet, cut with eight prisms in the facets' own planes)
          was built and measured at about 4.7 s per build against the 2 s allowance, so it is left out; the shade
          reads as a closed lotus bud instead.

Anchors
  base_front  the foot's front facet (normal −Y), ``MARGIN_MM`` inside its edges: a name (Naam) or a motif (Buti).
              A cut-in must leave ``plates.MIN_SKIN_MM`` of the foot wall in front of the pocket (``validate_content``).
  shade_band  the front facet of the shade (planar, normal tilted up with the taper), a rectangle from ``BAND_FROM`` to
              ``BAND_TO`` of the shade's height, ``BAND_MARGIN_MM`` inside the facet's sides: a motif or a short name,
              raised or cut up to ``SHADE_RELIEF_MM``; a cut-in must leave ``plates.MIN_SKIN_MM`` of the ``SHELL_MM``
              shell (``validate_content``).

Hardware: ``led_base_usb`` × 1. LED only, never a bulb.
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely.geometry import Polygon, box

from .. import cad
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

LED_SKU = "led_base_usb"
SIDES = 8
FOOT_H_MM = 24.0  # the puck pocket plus an 8 mm roof that carries the shade
PUCK_D_MM = 70.0  # the USB LED puck (led_base_usb)
PUCK_CLEARANCE_MM = 0.8  # a slide fit for the puck
POCKET_D_MM = PUCK_D_MM + PUCK_CLEARANCE_MM + plates.HOLE_ALLOWANCE_MM  # 71.0
POCKET_DEPTH_MM = 16.0  # from the underside; the puck sits flush or just inside
LIGHT_HOLE_D_MM = 50.0  # through the pocket's roof: the light rises into the shade
NOTCH_W_MM = 10.0  # the USB cable's way out through the back of the pocket wall
SHELL_MM = 2.0  # square to the facet; 1.6 would glow brighter but a 0.8 mm cut-in must leave MIN_SKIN_MM behind it
MARGIN_MM = 1.5  # base_front, inside the foot facet's edges
BAND_MARGIN_MM = 3.0  # shade_band, inside the facet's sides
BAND_FROM, BAND_TO = 0.2, 0.7  # the band's share of the shade's height
BLEED_MM = 1.0
FOOT_RELIEF_MM = 1.5
SHADE_RELIEF_MM = 1.0
OVERLAP_MM = 1.0  # tools reach this far past the faces they cut so no boolean meets a coplanar face


def octagon(across_flats: float) -> Polygon:
    """A regular octagon with flats facing ±X and ±Y."""
    R = (across_flats / 2.0) / math.cos(math.pi / SIDES)
    pts = [(R * math.cos(math.pi / SIDES + 2 * math.pi * k / SIDES), R * math.sin(math.pi / SIDES + 2 * math.pi * k / SIDES)) for k in range(SIDES)]
    return Polygon(pts)


class Layout:
    """Derived dimensions shared by ``build_part``, ``validate_content``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.foot_w = float(p["foot_w_mm"])
        self.top_w = float(p["top_w_mm"])
        self.H = float(p["height_mm"])
        self.sh = self.H - FOOT_H_MM  # the shade's height
        self.flare = (self.foot_w - self.top_w) / 2.0  # how far each facet moves in over the shade's height
        self.slant = math.hypot(self.sh, self.flare)
        self.taper_deg = math.degrees(math.atan2(self.flare, self.sh))
        self.wall_offset = SHELL_MM * self.slant / self.sh  # horizontal set-in of the inner octagon for a SHELL_MM wall

    def width_at(self, z: float) -> float:
        """The shade's across-flats at height ``z`` (extrapolated past its ends)."""
        return self.foot_w + (self.top_w - self.foot_w) * (z - FOOT_H_MM) / self.sh

    def facet_w(self, z: float) -> float:
        return self.width_at(z) * math.tan(math.pi / SIDES)

    @property
    def foot_facet_w(self) -> float:
        return self.foot_w * math.tan(math.pi / SIDES)

    @property
    def foot_wall_mm(self) -> float:
        """Plastic between the pocket and the foot's flats."""
        return self.foot_w / 2.0 - POCKET_D_MM / 2.0

    def facet_normal(self, k: int = 6) -> np.ndarray:
        """Outward unit normal of facet ``k`` (facets face angle 45° × k; k = 6 is the front, −Y)."""
        a = 2.0 * math.pi * k / SIDES
        n = np.array([math.cos(a) * self.sh, math.sin(a) * self.sh, self.flare])
        return n / np.linalg.norm(n)

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        z0, z1 = FOOT_H_MM + BAND_FROM * self.sh, FOOT_H_MM + BAND_TO * self.sh
        zc = (z0 + z1) / 2.0
        n = self.facet_normal()
        u = np.array([1.0, 0.0, 0.0])
        return {
            "base_front": AnchorFrame(
                np.array([0.0, -self.foot_w / 2.0, FOOT_H_MM / 2.0]), u, [0, 0, 1], [0, -1, 0],
                size_mm=(self.foot_facet_w - 2.0 * MARGIN_MM, FOOT_H_MM - 2.0 * MARGIN_MM),
            ),
            "shade_band": AnchorFrame(
                np.array([0.0, -self.width_at(zc) / 2.0, zc]), u, np.cross(n, u), n,
                size_mm=(self.facet_w(z1) - 2.0 * BAND_MARGIN_MM, (BAND_TO - BAND_FROM) * self.slant),
            ),
        }


PARAMS = {
    "height_mm": Param("number", "Height", 200.0, "mm", 150, 250, 5, handle=True, group="Size", description="Foot and shade together; the bed is 250 mm."),
    "foot_w_mm": Param("number", "Foot", 115.0, "mm", 100, 130, 5, group="Size", description="Across the flats of the eight-sided foot."),
    "top_w_mm": Param("number", "Top", 65.0, "mm", 50, 80, 5, handle=True, group="Shape", description="Across the flats at the shade's open top; smaller tapers more."),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class LotusLamp(Template):
    id = "lotus_lamp"
    version = 1
    family = "table_lamp"
    name = "Deepak lotus lamp"
    description = (
        "An eight-sided shade tapering like a closed lotus bud on a matching foot, printed as one piece in a heat-safe "
        "finish; a USB LED puck slides into the foot from below and glows up through the shade. Your name sits on the "
        "foot, a motif on the shade."
    )
    environment = "teak_table_candlelight"
    params = PARAMS
    anchors = (
        Anchor(
            "base_front", "Front of the foot", "planar", size_mm=_size("base_front"), bleed_mm=BLEED_MM,
            accepts=("emboss_text", "motif"), max_relief_mm=FOOT_RELIEF_MM,
        ),
        Anchor(
            "shade_band", "Front of the shade", "planar", size_mm=_size("shade_band"), bleed_mm=BLEED_MM,
            accepts=("emboss_text", "motif"), max_relief_mm=SHADE_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.6, max_overhang_deg=45, bed_mm=(250, 250, 250))
    features_supported = ("emboss_text", "motif")
    hardware = (HardwareRef(LED_SKU, 1),)
    min_feature_mm = 0.8

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        L = Layout(params)
        plates.check_skin(features, anchors=("shade_band",), thickness_mm=SHELL_MM, noun="shade", thickness_key="top_w_mm")
        plates.check_skin(
            features, anchors=("base_front",), thickness_mm=L.foot_wall_mm, noun="foot wall",
            behind_what="the LED pocket", thickness_key="foot_w_mm",
        )

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        L = Layout(params)
        foot = cad.prism(octagon(L.foot_w), FOOT_H_MM)
        shade = cad.loft([(octagon(L.foot_w), FOOT_H_MM), (octagon(L.top_w), L.H)])
        outer = cad.union([foot, shade])
        cavity = cad.loft([
            (octagon(L.foot_w - 2.0 * L.wall_offset), FOOT_H_MM),
            (octagon(L.width_at(L.H + OVERLAP_MM) - 2.0 * L.wall_offset), L.H + OVERLAP_MM),
        ])
        pocket = plates.hole(0.0, 0.0, POCKET_D_MM).union(box(-NOTCH_W_MM / 2.0, 0.0, NOTCH_W_MM / 2.0, L.foot_w / 2.0 + OVERLAP_MM))
        pocket_tool = cad.translate(cad.prism(pocket, POCKET_DEPTH_MM + OVERLAP_MM), dz=-OVERLAP_MM)
        light = cad.translate(cad.prism(plates.hole(0.0, 0.0, LIGHT_HOLE_D_MM), FOOT_H_MM - POCKET_DEPTH_MM + 2.0 * OVERLAP_MM), dz=POCKET_DEPTH_MM - OVERLAP_MM)
        return cad.cut(outer, [cavity, pocket_tool, light])

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
        return (
            f"A Deepak lotus lamp, {L.H:g} mm tall on a {L.foot_w:g} mm eight-sided foot, the shade tapering to {L.top_w:g} mm "
            f"at its plain open top; printed standing as modelled, no supports, a {SHELL_MM:g} mm shell that glows through PETG. "
            f"The LED puck slides in from below into the {POCKET_D_MM:g} mm pocket, cable through the notch at the back; "
            "LED only, never a bulb."
        )
