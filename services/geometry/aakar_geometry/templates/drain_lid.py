"""``drain_lid@1`` - Saaf Jod: a slotted lid that snaps into a bought-in acrylic soap box.

The box (``soap_box_acrylic_120x80``) is bought in; the printed lid rests on its opening with a **Kadi-C lid lip**
(``connectors.rim_clip``, ``lid``) inside it, two flexing long sides holding it by a light bulge. PETG only (the
family's ``material_rules``): a spring clip in PLA lets go within weeks, and the lid lives wet.

Coordinate frame (mm), **as printed**: face down. The lid's top (the face the bathroom sees) lies on the bed at Z = 0,
the plate is ``thickness_mm`` thick and the lip rises from its underside (the top of the print); X across the long side,
Y across the short side. The viewer turns it over onto its box.

Pieces
  plate  a rounded rectangle the opening plus ``BRIM_MM`` each way, with ``slots`` drainage slots in two groups left and
         right of a solid centre band.
  lip    Kadi-C ``lid``: walls ``TAB_LEN_MM`` tall inside the opening with the bulge on the long sides, corner slits.

Anchor
  face  the solid centre band of the top (normal −Z, cut-in only: the face prints against the bed): a motif (Buti) or a
        short name; cut-in content leaves the plate's skin (``validate_content``).

No hardware.
"""

from __future__ import annotations

from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely.geometry import Polygon, box
from shapely.ops import unary_union

from .. import cad
from ..connectors import Connector, compensation
from ..connectors import rim_clip as kadi_clip
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, Param, Template, TemplateConstraints

KADI = Connector("rim_clip", 118.0, form="lid")
BRIM_MM = 3.0
CORNER_MM = 4.0
SLOT_W_MM = 4.0
SLOT_MARGIN_MM = 12.0  # slots stay this far from the short edges
SLOT_EDGE_MM = 10.0  # ... and from the long edges' lip walls
BAND_W_MM = 40.0  # the solid centre the motif sits on
MARGIN_MM = 2.0
BLEED_MM = 0.5
MAX_RELIEF_MM = 1.2


class Layout:
    def __init__(self, p: Mapping[str, Any]):
        self.ow = float(p["opening_w_mm"])
        self.od = float(p["opening_d_mm"])
        self.T = float(p["thickness_mm"])
        self.slots = int(p["slots"])
        self.outline: Polygon = plates.rounded_rect(self.ow + 2.0 * BRIM_MM, self.od + 2.0 * BRIM_MM, CORNER_MM)

    def connector(self) -> Connector:
        return Connector("rim_clip", self.ow, form="lid")

    def slot_polys(self) -> list[Polygon]:
        half = self.slots // 2
        y0, y1 = -self.od / 2.0 + SLOT_MARGIN_MM, self.od / 2.0 - SLOT_MARGIN_MM
        x_in, x_out = BAND_W_MM / 2.0 + 2.0, self.ow / 2.0 - SLOT_EDGE_MM
        polys = []
        for side in (-1.0, 1.0):
            for k in range(half):
                x = x_in + (x_out - x_in) * (k + 0.5) / half
                polys.append(box(side * x - SLOT_W_MM / 2.0, y0, side * x + SLOT_W_MM / 2.0, y1))
        return polys

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        return {
            "face": AnchorFrame(
                np.array([0.0, 0.0, 0.0]), [-1, 0, 0], [0, 1, 0], [0, 0, -1],
                size_mm=(BAND_W_MM - 2.0 * MARGIN_MM, self.od - 2.0 * MARGIN_MM),
            ),
        }

    def seat_frame(self) -> AnchorFrame:
        """Where the lid meets the box: the plate's underside (the top of the print), normal into the plate."""
        return AnchorFrame(np.array([0.0, 0.0, self.T]), [1, 0, 0], [0, -1, 0], [0, 0, -1])


PARAMS = {
    "opening_w_mm": Param("number", "Opening width", 118.0, "mm", 100, 130, 0.5, group="Fit", description="The box's inside length; the lip is sized to it."),
    "opening_d_mm": Param("number", "Opening depth", 78.0, "mm", 60, 90, 0.5, group="Fit", description="The box's inside width."),
    "thickness_mm": Param("number", "Thickness", 3.0, "mm", 2.4, 4.0, 0.1, group="Size"),
    "slots": Param("integer", "Drain slots", 6, "count", 4, 10, 2, group="Details", description="In two groups beside the centre band."),
}

_DEFAULT_LAYOUT = Layout({k: p.default for k, p in PARAMS.items()})
_DEFAULT_FRAMES = _DEFAULT_LAYOUT.anchor_frames()


class DrainLid(Template):
    id = "drain_lid"
    version = 1
    family = "drain_lid"
    name = "Saaf Jod soap box lid"
    description = (
        "A slotted lid that snaps into a ready-made acrylic soap box. The box is bought in; the lid with your motif is "
        "printed in PETG so it shrugs off water."
    )
    environment = "kitchen_marble"
    params = PARAMS
    anchors = (
        Anchor(
            "face", "Top of the lid", "planar", size_mm=tuple(round(s, 1) for s in _DEFAULT_FRAMES["face"].size_mm),  # type: ignore[arg-type]
            bleed_mm=BLEED_MM, accepts=("motif", "emboss_text"), max_relief_mm=MAX_RELIEF_MM, modes=("deboss",),
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    features_supported = ("motif", "emboss_text")
    hardware = ()
    min_feature_mm = 0.8
    connector = KADI
    body_uses_material = True

    @classmethod
    def connector_for(cls, params: Mapping[str, Any]) -> Connector:
        return Layout(params).connector()

    @classmethod
    def mode_refusal(cls, anchor: Anchor, ftype: str, mode: str) -> str | None:
        return "The lid prints face down, so its top only takes cut-in content; cut the motif or name in instead."

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("face",), thickness_mm=float(params["thickness_mm"]), noun="lid")

    @classmethod
    def build_part(cls, params: Mapping[str, Any], material: str | None) -> "cad.Part":
        L = Layout(params)
        d = kadi_clip.dims(L.connector(), compensation.for_material(material))
        plate = cad.prism(plates.largest_polygon(L.outline.difference(unary_union(L.slot_polys()))), L.T)
        add, cut = kadi_clip.lid_lip(d, L.ow, L.od, L.T)
        return cad.cut(cad.union([plate, add]), [cut])

    @classmethod
    def build_body(cls, params: Mapping[str, Any], material: str | None = None) -> trimesh.Trimesh:
        mesh = cad.to_trimesh(cls.build_part(params, material))
        mesh.apply_translation([0.0, 0.0, -float(mesh.bounds[0][2])])
        return mesh

    @classmethod
    def connector_frame(cls, params: Mapping[str, Any]) -> AnchorFrame:
        return Layout(params).seat_frame()

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
            f"A Saaf Jod drain lid for a {L.ow:g} × {L.od:g} mm opening, {L.T:g} mm thick with {L.slots} slots, printed face down "
            f"in PETG as modelled. Press it into the box by its long sides; the lip's bulge holds it."
        )
