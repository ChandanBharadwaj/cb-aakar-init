"""``lamp_shade_e27@1`` - Deepak Jod: an eight-sided lantern shade that clamps onto a bought-in E27 lamp base.

The base (``led_lamp_base_e27``: foot, stem, holder, cord, switch) is bought in with an LED bulb; the printed shade is a
faceted shell whose collar (``connectors.thread``, ``e27``) the holder's shade ring clamps. Only the shade is printed.

Coordinate frame (mm), **as printed**: collar down. Z = 0 is the collar plane (the top of the shade in use), the shell
flares to its open bottom at Z = ``height_mm``; X across, Y depth, the front facet on −Y. The viewer turns the piece over
when it shows it on its base (the connector's axis points along the base's ``up``).

Pieces
  shell   a loft between two regular octagons (across flats ``top_w_mm`` at the collar, ``bottom_w_mm`` at the open end),
          ``wall_mm`` thick, open at the bottom.
  collar  a flat lip ``LIP_MM`` thick closing the small end, with the Kadi-T collar hole the holder's neck passes.

Anchor
  band  the front facet's lower (wider) part (normal tilted outward with the flare): a motif (Buti), a photo relief
        (Chhavi) or a short name, raised or cut up to 1 mm; cut-in content must leave the shell's skin
        (``validate_content``).

No hardware; LED bulbs up to 5 W only (the base item's compliance note).
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely.geometry import Polygon

from .. import cad
from ..connectors import Connector
from ..connectors import thread as kadi_thread
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, Param, Template, TemplateConstraints

KADI = Connector("thread", kadi_thread.COLLARS["e27"], thread="e27")
SIDES = 8
LIP_MM = kadi_thread.LIP_MM
MARGIN_MM = 4.0
BLEED_MM = 1.0
MAX_RELIEF_MM = 1.0
BAND_FROM = 0.4  # the band starts this far down the facet from the collar (the wider, lower part in use)
OVERCUT_MM = 1.0


def octagon(across_flats: float) -> Polygon:
    """A regular octagon with flats facing ±X and ±Y."""
    R = (across_flats / 2.0) / math.cos(math.pi / SIDES)
    pts = [(R * math.cos(math.pi / SIDES + 2 * math.pi * k / SIDES), R * math.sin(math.pi / SIDES + 2 * math.pi * k / SIDES)) for k in range(SIDES)]
    return Polygon(pts)


class Layout:
    def __init__(self, p: Mapping[str, Any]):
        self.h = float(p["height_mm"])
        self.tw = float(p["top_w_mm"])
        self.bw = float(p["bottom_w_mm"])
        self.wall = float(p["wall_mm"])
        self.flare = (self.bw - self.tw) / 2.0  # how far each facet moves out over the height
        self.slant = math.hypot(self.h, self.flare)

    def width_at(self, z: float) -> float:
        return self.tw + (self.bw - self.tw) * z / self.h

    def facet_normal(self) -> np.ndarray:
        n = np.array([0.0, -self.h, -self.flare])
        return n / np.linalg.norm(n)

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        z0, z1 = BAND_FROM * self.h, self.h - MARGIN_MM
        zc = (z0 + z1) / 2.0
        yc = -self.width_at(zc) / 2.0
        n = self.facet_normal()
        u = np.array([-1.0, 0.0, 0.0])  # the viewer's right once the shade is turned over onto its base
        side_at_band_top = self.width_at(z0) * math.tan(math.pi / SIDES)
        height = (z1 - z0) * self.slant / self.h
        return {"band": AnchorFrame(np.array([0.0, yc, zc]), u, np.cross(n, u), n, size_mm=(side_at_band_top - 2.0 * MARGIN_MM, height - 2.0 * MARGIN_MM))}

    def collar_frame(self) -> AnchorFrame:
        return AnchorFrame(np.array([0.0, 0.0, 0.0]), [1, 0, 0], [0, 1, 0], [0, 0, 1])


PARAMS = {
    "height_mm": Param("number", "Height", 160.0, "mm", 120, 220, 5, handle=True, group="Size"),
    "top_w_mm": Param("number", "Top width", 70.0, "mm", 60, 90, 2, group="Shape", description="Across the flats at the collar."),
    "bottom_w_mm": Param("number", "Bottom width", 150.0, "mm", 120, 200, 5, handle=True, group="Shape", description="Across the flats at the open end."),
    "wall_mm": Param("number", "Wall", 2.0, "mm", 1.6, 2.4, 0.1, group="Details", description="The shell's thickness; light glows through a thin wall."),
}

_DEFAULT_LAYOUT = Layout({k: p.default for k, p in PARAMS.items()})
_DEFAULT_FRAMES = _DEFAULT_LAYOUT.anchor_frames()


class LampShadeE27(Template):
    id = "lamp_shade_e27"
    version = 1
    family = "lamp_shade_e27"
    name = "Deepak Jod lantern shade"
    description = (
        "An eight-sided lantern shade that clamps onto a ready-made E27 lamp base with the holder's shade ring. "
        "Your motif or photo sits on the front facet; the base, cord and LED bulb are bought in."
    )
    environment = "teak_table_candlelight"
    params = PARAMS
    anchors = (
        Anchor(
            "band", "Front facet", "planar", size_mm=tuple(round(s, 1) for s in _DEFAULT_FRAMES["band"].size_mm),  # type: ignore[arg-type]
            bleed_mm=BLEED_MM, accepts=("motif", "relief_image", "emboss_text"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    features_supported = ("motif", "relief_image", "emboss_text")
    hardware = ()
    min_feature_mm = 0.8
    connector = KADI

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("band",), thickness_mm=float(params["wall_mm"]), noun="shade", thickness_key="wall_mm")

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        L = Layout(params)
        outer = cad.loft([(octagon(L.tw), 0.0), (octagon(L.bw), L.h)])
        inner = cad.loft([(octagon(L.width_at(LIP_MM) - 2.0 * L.wall), LIP_MM), (octagon(L.width_at(L.h + OVERCUT_MM) - 2.0 * L.wall), L.h + OVERCUT_MM)])
        return cad.cut(outer, [inner])

    @classmethod
    def build_body(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        mesh = cad.to_trimesh(cls.build_part(params))
        mesh.apply_translation([0.0, 0.0, -float(mesh.bounds[0][2])])
        return mesh

    @classmethod
    def connector_frame(cls, params: Mapping[str, Any]) -> AnchorFrame:
        return Layout(params).collar_frame()

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
            f"A Deepak Jod lantern shade, {L.h:g} mm tall, {L.tw:g} mm across at the collar and {L.bw:g} mm at the open end, "
            f"{L.wall:g} mm walls; printed collar down as modelled, no supports. Slip the collar over the holder's neck and screw "
            f"the shade ring down onto the lip; LED bulbs up to 5 W only."
        )
