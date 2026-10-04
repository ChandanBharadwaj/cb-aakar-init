"""``hook_plaque@1`` - Pehchaan Jod: a nameplate that slides onto a bought-in steel key-hook plate through a Kadi-D rail.

The plate (``hook_plate_steel_4``) is bought in and screwed to the wall; a PETG rail (``rail_petg_40``) is bonded along
its top edge with a VHB pad, and the printed plaque's **dovetail channel** (``connectors.dovetail``) slides onto the
rail from one side until the detent clicks. Only the plaque is printed.

Coordinate frame (mm): the plaque lies face up as printed: X across (the viewer's right), Y up the plaque, Z its
thickness; the back rests on the bed at Z = 0 and the face is at Z = ``thickness_mm``. Centred in X and Y.

Pieces
  plaque   a rounded rectangle ``width_mm`` × ``height_mm`` × ``thickness_mm``.
  channel  Kadi-D along X across the whole back (open at both ends), centred on Y = 0: 12.4 mm at the mouth widening to
           15.4 mm at the floor, 4.15 mm deep, a detent ridge at the centre; modelled with the finish's compensation.

Anchor
  face  the whole face ``MARGIN_MM`` inside its edges (normal +Z): a name (Naam), a motif (Buti) or a photo relief
        (Chhavi), raised or cut up to 1.5 mm. Cut-in content must leave ``plates.MIN_SKIN_MM`` over the channel's floor
        (``validate_content``).

Hardware: ``rail_petg_40`` ×1 and ``vhb_pad_25x40`` ×1, bonded to the plate by the studio at assembly.
"""

from __future__ import annotations

from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely.geometry import Polygon

from .. import cad
from ..connectors import Connector
from ..connectors import dovetail as kadi_dovetail
from ..errors import ParamOutOfRange
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

RAIL_SKU = "rail_petg_40"
PAD_SKU = "vhb_pad_25x40"
KADI = Connector("dovetail", 12.0, adapter_sku=RAIL_SKU)
CORNER_MM = 5.0
MARGIN_MM = 2.0
BLEED_MM = 0.5
MAX_RELIEF_MM = 1.5
CHANNEL_WALL_MM = 3.0  # plastic beside the channel's floor, within the plaque's height


class Layout:
    def __init__(self, p: Mapping[str, Any]):
        self.W = float(p["width_mm"])
        self.Hh = float(p["height_mm"])
        self.T = float(p["thickness_mm"])
        self.outline: Polygon = plates.rounded_rect(self.W, self.Hh, CORNER_MM)

    @property
    def channel_depth(self) -> float:
        return KADI.depth()

    @property
    def skin_mm(self) -> float:
        """Plastic between the channel's floor and the face."""
        return self.T - self.channel_depth

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        return {
            "face": AnchorFrame(
                np.array([0.0, 0.0, self.T]), [1, 0, 0], [0, 1, 0], [0, 0, 1],
                size_mm=(self.W - 2.0 * MARGIN_MM, self.Hh - 2.0 * MARGIN_MM),
            ),
        }

    def channel_frame(self) -> AnchorFrame:
        """The channel's mouth on the bed at the plaque's centre, running along X, cut up into the plaque."""
        return AnchorFrame(np.array([0.0, 0.0, 0.0]), [0, -1, 0], [1, 0, 0], [0, 0, 1], size_mm=(self.W + 2.0, kadi_dovetail.RAIL_HEIGHT_MM))


PARAMS = {
    "width_mm": Param("number", "Width", 220.0, "mm", 160, 260, 5, handle=True, group="Size", description="Across the plaque; the plate is 260 mm."),
    "height_mm": Param("number", "Height", 55.0, "mm", 40, 70, 1, handle=True, group="Size"),
    "thickness_mm": Param("number", "Thickness", 8.0, "mm", 6, 10, 0.5, group="Size", description="The rail channel takes 4.15 mm of it."),
}

_DEFAULT_LAYOUT = Layout({k: p.default for k, p in PARAMS.items()})
_DEFAULT_FRAMES = _DEFAULT_LAYOUT.anchor_frames()


class HookPlaque(Template):
    id = "hook_plaque"
    version = 1
    family = "hook_plaque"
    name = "Pehchaan Jod key-hook nameplate"
    description = (
        "A plaque that slides onto a steel four-hook plate through a dovetail rail. The plate is bought in and screwed "
        "to the wall; your name, a motif or a photo relief are printed on the plaque."
    )
    environment = "studio"
    params = PARAMS
    anchors = (
        Anchor(
            "face", "Face", "planar", size_mm=tuple(round(s, 1) for s in _DEFAULT_FRAMES["face"].size_mm),  # type: ignore[arg-type]
            bleed_mm=BLEED_MM, accepts=("emboss_text", "motif", "relief_image"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    style_variants = ("comic_pop",)
    features_supported = ("emboss_text", "motif", "relief_image")
    hardware = (HardwareRef(RAIL_SKU, 1), HardwareRef(PAD_SKU, 1))
    min_feature_mm = 0.8
    connector = KADI

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        L = Layout(params)
        if L.skin_mm < plates.MIN_SKIN_MM - 1e-9:
            raise ParamOutOfRange(
                ["thickness_mm"],
                f"A {L.T:g} mm plaque leaves {L.skin_mm:.1f} mm over the {L.channel_depth:g} mm rail channel; make it at least "
                f"{L.channel_depth + plates.MIN_SKIN_MM:.1f} mm thick.",
                {"skin_mm": round(L.skin_mm, 3), "min_skin_mm": plates.MIN_SKIN_MM},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        L = Layout(params)
        plates.check_skin(features, anchors=("face",), thickness_mm=L.T, noun="plaque", behind=L.channel_depth, behind_what="the rail channel")

    @classmethod
    def build_body(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        L = Layout(params)
        mesh = cad.to_trimesh(cad.prism(L.outline, L.T))
        mesh.apply_translation([0.0, 0.0, -float(mesh.bounds[0][2])])
        return mesh

    @classmethod
    def connector_frame(cls, params: Mapping[str, Any]) -> AnchorFrame:
        return Layout(params).channel_frame()

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
            f"A Pehchaan Jod plaque, {L.W:g} × {L.Hh:g} mm and {L.T:g} mm thick, printed face up with the rail channel on the bed. "
            f"Bond the PETG rail to the plate's top edge with the VHB pad, centred, and let it cure a day; slide the plaque on from "
            f"the side until the detent clicks."
        )
