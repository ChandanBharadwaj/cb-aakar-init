"""``dash_idol@1`` - Safar Jod: a small plinth for the dashboard that holds to a bought-in magnetic mount.

The mount (``dash_mount_magnetic``: a magnetic puck on an adhesive pad) is bought in; the printed plinth carries a steel
disc (``steel_disc_40``) glued into a **Kadi-M pocket** (``connectors.magnet_register``, ``steel_disc``) in its
underside, and the customer's own form (Roop) stands fused on its top with a name on its front. Heat-safe finishes only
(the family's ``material_rules``): a dashboard reaches 70 °C.

Coordinate frame (mm): X across, Y depth (−Y the front), Z up; the plinth rests on Z = 0 and is centred in X and Y.

Pieces
  plinth  a rounded rectangle ``width_mm`` × ``depth_mm`` × ``height_mm``.
  pocket  Kadi-M Ø 40.4 × 1.2 opening on the underside at the centre; the plastic over it is a bridge.

Anchors
  top   volume: the plinth's top (normal +Z), ``bounds_mm`` = (width − 3) × (depth − 3) × ``TOP_HEIGHT_MM``.
  face  surface: the front (normal −Y): a short name (Naam) or a motif (Buti), raised or cut up to 1 mm.

Hardware: ``steel_disc_40`` ×1.
"""

from __future__ import annotations

from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely.geometry import Polygon

from .. import cad
from ..connectors import Connector
from ..connectors import magnet_register as kadi_magnet
from ..errors import ParamOutOfRange
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

DISC_SKU = "steel_disc_40"
KADI = Connector("magnet", 40.0, mode="steel_disc", adapter_sku=DISC_SKU)
CORNER_MM = 6.0
MARGIN_MM = 1.5
BLEED_MM = 0.5
MAX_RELIEF_MM = 1.0
TOP_HEIGHT_MM = 60.0  # the form's height budget: the family envelope is 80 mm
POCKET_WALL_MM = 2.0  # plastic kept between the pocket and the plinth's edge


class Layout:
    def __init__(self, p: Mapping[str, Any]):
        self.W = float(p["width_mm"])
        self.D = float(p["depth_mm"])
        self.H = float(p["height_mm"])
        self.outline: Polygon = plates.rounded_rect(self.W, self.D, CORNER_MM)

    @property
    def pocket_d(self) -> float:
        return KADI.nominal_mm + kadi_magnet.DISC_CLEARANCE_MM + 0.5  # as printed plus the compensation's reach

    @property
    def skin_mm(self) -> float:
        return self.H - KADI.depth()

    @property
    def top_bounds(self) -> tuple[float, float, float]:
        return (self.W - 2.0 * MARGIN_MM, self.D - 2.0 * MARGIN_MM, TOP_HEIGHT_MM)

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        return {
            "top": AnchorFrame(np.array([0.0, 0.0, self.H]), [1, 0, 0], [0, 1, 0], [0, 0, 1], bounds_mm=self.top_bounds),
            "face": AnchorFrame(
                np.array([0.0, -self.D / 2.0, self.H / 2.0]), [1, 0, 0], [0, 0, 1], [0, -1, 0],
                size_mm=(self.W - 2.0 * CORNER_MM, self.H - 2.0 * BLEED_MM),
            ),
        }

    def pocket_frame(self) -> AnchorFrame:
        return AnchorFrame(np.array([0.0, 0.0, 0.0]), [1, 0, 0], [0, 1, 0], [0, 0, 1])


PARAMS = {
    "width_mm": Param("number", "Width", 55.0, "mm", 45, 70, 1, handle=True, group="Size"),
    "depth_mm": Param("number", "Depth", 50.0, "mm", 45, 60, 1, group="Size"),
    "height_mm": Param("number", "Height", 10.0, "mm", 8, 14, 0.5, group="Size", description="Of the plinth; the disc pocket takes 1.2 mm."),
}

_DEFAULT_LAYOUT = Layout({k: p.default for k, p in PARAMS.items()})
_DEFAULT_FRAMES = _DEFAULT_LAYOUT.anchor_frames()


class DashIdol(Template):
    id = "dash_idol"
    version = 1
    family = "dash_idol"
    name = "Safar Jod dashboard idol"
    description = (
        "A small plinth for your own form that holds to a magnetic dashboard mount through a steel disc set in its "
        "underside. The mount is bought in; the plinth and your form are printed in a heat-safe finish."
    )
    environment = "dashboard"
    params = PARAMS
    anchors = (
        Anchor("top", "Top of the plinth", "planar", kind="volume", bounds_mm=_DEFAULT_LAYOUT.top_bounds, accepts=("hero_mesh",)),
        Anchor(
            "face", "Front of the plinth", "planar", size_mm=tuple(round(s, 1) for s in _DEFAULT_FRAMES["face"].size_mm),  # type: ignore[arg-type]
            bleed_mm=BLEED_MM, accepts=("emboss_text", "motif"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    style_variants = ("comic_pop",)
    features_supported = ("hero_mesh", "emboss_text", "motif")
    hardware = (HardwareRef(DISC_SKU, 1),)
    min_feature_mm = 0.8
    connector = KADI

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        L = Layout(params)
        if min(L.W, L.D) < L.pocket_d + 2.0 * POCKET_WALL_MM - 1e-9:
            raise ParamOutOfRange(
                ["width_mm", "depth_mm"],
                f"A {L.W:g} × {L.D:g} mm plinth has no room round the {KADI.nominal_mm:g} mm disc; make it at least "
                f"{L.pocket_d + 2.0 * POCKET_WALL_MM:.0f} mm each way.",
                {"pocket_d_mm": round(L.pocket_d, 2), "pocket_wall_mm": POCKET_WALL_MM},
            )
        if L.skin_mm < plates.MIN_SKIN_MM - 1e-9:
            raise ParamOutOfRange(["height_mm"], f"A {L.H:g} mm plinth leaves too little over the disc pocket; make it at least {KADI.depth() + plates.MIN_SKIN_MM:g} mm tall.")

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        L = Layout(params)
        plates.check_skin(features, anchors=("face",), thickness_mm=L.D / 2.0, noun="plinth", thickness_key="depth_mm")

    @classmethod
    def build_body(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        L = Layout(params)
        mesh = cad.to_trimesh(cad.prism(L.outline, L.H))
        mesh.apply_translation([0.0, 0.0, -float(mesh.bounds[0][2])])
        return mesh

    @classmethod
    def connector_frame(cls, params: Mapping[str, Any]) -> AnchorFrame:
        return Layout(params).pocket_frame()

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
            f"A Safar Jod plinth, {L.W:g} × {L.D:g} mm and {L.H:g} mm tall, printed standing with the disc pocket on the bed "
            f"(the pocket's roof bridges 40 mm: print with bridging on). Glue the steel disc in flush before packing."
        )

    @classmethod
    def karigar_note_for(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]] = (), style: str | None = None) -> str:
        note = cls.karigar_note(params)
        if any(f.get("type") == "hero_mesh" for f in features or []):
            note += " The customer's form stands fused on the top: support its overhangs."
        return cls.join_note(note, features, style)
