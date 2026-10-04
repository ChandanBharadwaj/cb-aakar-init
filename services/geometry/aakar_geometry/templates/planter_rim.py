"""``planter_rim@1`` - Gamla Jod: a band that clips over the rim of a terracotta pot the customer already owns.

Nothing is bought in: the base is a **class** of the customer's pot (``planter_rim_class_8``, rims 7 to 10 mm thick,
pots 80 to 140 mm across). The printed band carries **Kadi-C rim jaws** (``connectors.rim_clip``, ``rim``): a bridge
over the rim's top, an inner jaw split into tabs whose lips hook under the rim's inside, and an outer skirt that carries
a flat name tag. PETG only (the family's ``material_rules``).

Coordinate frame (mm), **as printed**: upside down. The bridge lies on the bed (Z = 0 to ``BRIDGE_MM``) and the jaws rise
from it; the arc is centred on −Y, the tag's face on −Y. The viewer turns it over onto the pot.

Pieces
  jaws  Kadi-C ``rim`` over a pot ``pot_d_mm`` across, spanning ``arc_deg``.
  tag   a flat plate ``tag_w_mm`` × ``tag_h_mm`` on the outer skirt at the arc's centre, ``TAG_T_MM`` proud of the skirt.

Anchor
  band  the tag's face (normal −Y): a motif (Buti) or the plant's name (Naam), raised or cut up to 1 mm; cut-in content
        leaves the tag's skin (``validate_content``).

No hardware.
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh

from .. import cad
from ..connectors import Connector, compensation
from ..connectors import rim_clip as kadi_clip
from ..errors import ParamOutOfRange
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, Param, Template, TemplateConstraints

KADI = Connector("rim_clip", 8.0, form="rim")
TAG_T_MM = 3.0
TAG_CORNER_MM = 2.0
MARGIN_MM = 2.0
BLEED_MM = 0.5
MAX_RELIEF_MM = 1.0
TAG_OVERLAP_MM = 0.5


class Layout:
    def __init__(self, p: Mapping[str, Any]):
        self.pot_d = float(p["pot_d_mm"])
        self.arc = float(p["arc_deg"])
        self.tag_w = float(p["tag_w_mm"])
        self.tag_h = float(p["tag_h_mm"])
        self.pot_r = self.pot_d / 2.0

    def outer_r(self, d: kadi_clip.RimClipDims) -> float:
        """The outer skirt's outside radius."""
        return kadi_clip.rim_radii(d, self.pot_r)[3]

    @property
    def chord(self) -> float:
        return 2.0 * self.pot_r * math.sin(math.radians(self.arc / 2.0))

    def tag_face_y(self, d: kadi_clip.RimClipDims) -> float:
        return -(self.outer_r(d) + TAG_T_MM)

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        d = kadi_clip.dims(KADI, compensation.DEFAULTS["petg"])
        return {
            "band": AnchorFrame(
                np.array([0.0, self.tag_face_y(d), kadi_clip.BRIDGE_MM + self.tag_h / 2.0]), [-1, 0, 0], [0, 0, -1], [0, -1, 0],
                size_mm=(self.tag_w - 2.0 * MARGIN_MM, self.tag_h - 2.0 * MARGIN_MM),
            ),
        }

    def seat_frame(self) -> AnchorFrame:
        """Where the band meets the rim's top: the bridge's upper face at the arc's centre, normal into the bridge."""
        d = kadi_clip.dims(KADI, compensation.DEFAULTS["petg"])
        y = -(self.pot_r - d.nominal_mm / 2.0)
        return AnchorFrame(np.array([0.0, y, kadi_clip.BRIDGE_MM]), [1, 0, 0], [0, -1, 0], [0, 0, -1])


PARAMS = {
    "pot_d_mm": Param("number", "Pot", 110.0, "mm", 80, 140, 5, handle=True, group="Fit", description="Across the pot at its rim."),
    "arc_deg": Param("number", "Reach", 70.0, "deg", 45, 120, 5, group="Shape", description="How far round the rim the band reaches."),
    "tag_w_mm": Param("number", "Tag width", 40.0, "mm", 30, 60, 1, group="Shape"),
    "tag_h_mm": Param("number", "Tag height", 16.0, "mm", 12, 24, 1, group="Shape"),
}

_DEFAULT_LAYOUT = Layout({k: p.default for k, p in PARAMS.items()})
_DEFAULT_FRAMES = _DEFAULT_LAYOUT.anchor_frames()


class PlanterRim(Template):
    id = "planter_rim"
    version = 1
    family = "planter_rim"
    name = "Gamla Jod planter band"
    description = (
        "A band that clips over the rim of a terracotta pot (rims 7 to 10 mm thick) and carries a motif and the plant's "
        "name on a tag. The pot is yours; the band is printed in PETG for the balcony."
    )
    environment = "balcony_daylight"
    params = PARAMS
    anchors = (
        Anchor(
            "band", "Name tag", "planar", size_mm=tuple(round(s, 1) for s in _DEFAULT_FRAMES["band"].size_mm),  # type: ignore[arg-type]
            bleed_mm=BLEED_MM, accepts=("motif", "emboss_text"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    features_supported = ("motif", "emboss_text")
    hardware = ()
    min_feature_mm = 0.8
    connector = KADI
    body_uses_material = True

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        L = Layout(params)
        if L.tag_w > L.chord - 6.0 + 1e-9:
            raise ParamOutOfRange(
                ["tag_w_mm", "arc_deg"],
                f"A {L.tag_w:g} mm tag does not fit a band that reaches {L.arc:g}° round a {L.pot_d:g} mm pot; "
                f"reach further round or narrow the tag to {max(L.chord - 6.0, 0):.0f} mm.",
                {"chord_mm": round(L.chord, 2)},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("band",), thickness_mm=TAG_T_MM, noun="tag", thickness_key="tag_w_mm")

    @classmethod
    def build_part(cls, params: Mapping[str, Any], material: str | None) -> "cad.Part":
        L = Layout(params)
        d = kadi_clip.dims(KADI, compensation.for_material(material))
        add, cut = kadi_clip.rim_band(d, L.pot_r, L.arc, kadi_clip.BRIDGE_MM)
        r_out = L.outer_r(d)
        # the tag: a block from the skirt's chord at the tag's ends out to the flat face, clipped to the skirt's inside
        y_chord = -math.sqrt(max(r_out**2 - (L.tag_w / 2.0) ** 2, 1.0)) + TAG_OVERLAP_MM
        y_face = L.tag_face_y(d)
        tag = cad.prism(plates.rounded_rect(L.tag_w, y_chord - y_face, TAG_CORNER_MM), L.tag_h)
        tag = cad.translate(tag, dy=(y_chord + y_face) / 2.0, dz=kadi_clip.BRIDGE_MM)
        tag = cad.cut(tag, [cad.cylinder(kadi_clip.rim_radii(d, L.pot_r)[2] + 0.2, L.tag_h + 2.0, z0=kadi_clip.BRIDGE_MM - 1.0)])
        body = cad.union([add, tag])
        return cad.cut(body, [cut]) if cut is not None else body

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
            f"A Gamla Jod band for a {L.pot_d:g} mm pot, reaching {L.arc:g}° round the rim with a {L.tag_w:g} × {L.tag_h:g} mm tag, "
            f"printed upside down in PETG as modelled. Hook the inner tabs under the rim first, then press the bridge down."
        )
