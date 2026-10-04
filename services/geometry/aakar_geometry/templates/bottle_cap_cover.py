"""``bottle_cap_cover@1`` - Botal Jod: a cover that snaps over the cap of a steel school bottle the customer already owns.

Nothing is bought in: the base is a **class** of the customer's own bottle (``bottle_cap_class_40``, caps 40 to 44 mm
across). The printed cover carries a **Kadi-C cap skirt** (``connectors.rim_clip``, ``cap``): four tabs with inward lips
that spring over the cap's edge. PETG only (the family's ``material_rules``).

Coordinate frame (mm), **as printed**: top down. The cover's top (the face that shows) lies on the bed at Z = 0, the
top plate is ``top_t_mm`` thick and the skirt rises from it (the top of the print). The viewer turns it over onto the cap.

Pieces
  top    a disc the skirt's outer diameter plus ``brim_mm`` each side, ``top_t_mm`` thick.
  skirt  Kadi-C ``cap``: ``skirt_h_mm`` tall (at least the 16 mm tab length), four tabs, lips at the free end.

Anchor
  top  the top's face (normal −Z, cut-in only: it prints against the bed): a name (Naam), a motif (Buti) or a photo relief
       (Chhavi) cut in up to 1.2 mm, leaving the plate's skin (``validate_content``).

No hardware.
"""

from __future__ import annotations

from typing import Any, Mapping, Sequence

import numpy as np
import trimesh

from .. import cad
from ..connectors import Connector, compensation
from ..connectors import rim_clip as kadi_clip
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, Param, Template, TemplateConstraints

KADI = Connector("rim_clip", 42.0, form="cap")
MARGIN_MM = 2.0
BLEED_MM = 0.5
MAX_RELIEF_MM = 1.2


class Layout:
    def __init__(self, p: Mapping[str, Any]):
        self.T = float(p["top_t_mm"])
        self.skirt_h = float(p["skirt_h_mm"])
        self.brim = float(p["brim_mm"])

    def outer_r(self, material: str | None) -> float:
        return kadi_clip.cap_outer_radius(kadi_clip.dims(KADI, compensation.for_material(material))) + self.brim

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        r = kadi_clip.cap_outer_radius(kadi_clip.dims(KADI, compensation.DEFAULTS["petg"])) + self.brim
        rect = plates.symmetric_rect(plates.disc(0.0, 0.0, r - MARGIN_MM), aspect=1.0)
        if rect is None:  # pragma: no cover - every cover has a face
            raise ValueError("the cover has no room for a picture")
        x0, y0, x1, y1 = rect
        return {"top": AnchorFrame(np.array([0.0, 0.0, 0.0]), [-1, 0, 0], [0, 1, 0], [0, 0, -1], size_mm=(x1 - x0, y1 - y0))}

    def seat_frame(self) -> AnchorFrame:
        """Where the cover meets the cap's top: the plate's underside (the top of the print), normal into the plate."""
        return AnchorFrame(np.array([0.0, 0.0, self.T]), [1, 0, 0], [0, -1, 0], [0, 0, -1])


PARAMS = {
    "top_t_mm": Param("number", "Top", 3.0, "mm", 2.4, 4.0, 0.1, group="Size", description="The top plate's thickness."),
    "skirt_h_mm": Param("number", "Skirt", 20.0, "mm", 18, 26, 1, handle=True, group="Size", description="How far the cover reaches down the cap."),
    "brim_mm": Param("number", "Brim", 2.0, "mm", 0, 6, 0.5, group="Shape", description="A brim round the top to grip when opening."),
}

_DEFAULT_LAYOUT = Layout({k: p.default for k, p in PARAMS.items()})
_DEFAULT_FRAMES = _DEFAULT_LAYOUT.anchor_frames()


class BottleCapCover(Template):
    id = "bottle_cap_cover"
    version = 1
    family = "bottle_cap_cover"
    name = "Botal Jod bottle cap cover"
    description = (
        "A cover that snaps over the cap of a steel school bottle (caps 40 to 44 mm across) with a name and a motif on "
        "top. The bottle is the one they already own; only the cover is printed, in PETG."
    )
    environment = "balcony_daylight"
    params = PARAMS
    anchors = (
        Anchor(
            "top", "Top of the cover", "planar", size_mm=tuple(round(s, 1) for s in _DEFAULT_FRAMES["top"].size_mm),  # type: ignore[arg-type]
            bleed_mm=BLEED_MM, accepts=("emboss_text", "motif", "relief_image"), max_relief_mm=MAX_RELIEF_MM, modes=("deboss",),
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    features_supported = ("emboss_text", "motif", "relief_image")
    hardware = ()
    min_feature_mm = 0.8
    connector = KADI
    body_uses_material = True

    @classmethod
    def mode_refusal(cls, anchor: Anchor, ftype: str, mode: str) -> str | None:
        return "The cover prints top down, so its top only takes cut-in content; cut the name or motif in instead."

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("top",), thickness_mm=float(params["top_t_mm"]), noun="cover", thickness_key="top_t_mm")

    @classmethod
    def build_part(cls, params: Mapping[str, Any], material: str | None) -> "cad.Part":
        L = Layout(params)
        d = kadi_clip.dims(KADI, compensation.for_material(material))
        top = cad.prism(plates.disc(0.0, 0.0, kadi_clip.cap_outer_radius(d) + L.brim), L.T)
        add, cut = kadi_clip.cap_skirt(d, L.T, L.skirt_h)
        return cad.cut(cad.union([top, add]), [cut])

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
            f"A Botal Jod cap cover, {L.T:g} mm top and an {L.skirt_h:g} mm skirt with four tabs, printed top down in PETG as "
            f"modelled. Snap it over the cap lips first; it fits caps 40 to 44 mm across."
        )
