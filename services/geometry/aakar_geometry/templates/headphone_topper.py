"""``headphone_topper@1`` - Sur Jod: a saddle for a bought-in headphone stand, with your own form in front and a name on its face.

The first hybrid (Jod) template: the stand is bought in, only the topper is printed. Underneath it a **Kadi-S
socket** (``connectors.socket``, Ø 12 pin) presses onto the stand's tube top or onto the nylon dowel packed with
it (``dowel_nylon_12x30``); the headphones rest their band across the saddle's crown.

Coordinate frame (mm): X across the saddle (the headband runs along X), Y front to back (−Y is the front the
reader faces), Z up. The piece rests on Z = 0, the socket opens on the bed, and prints as modelled with no
supports (the crown is a gentle arc).

Pieces
  plate   a rounded rectangle ``width_mm`` × (``depth_mm`` + ``room_mm``), ``PLATE_MM`` thick, centred in X and Y.
  saddle  the back ``depth_mm`` of the plate: a block ``width_mm`` wide topped by a crown, an arc of radius
          ``CROWN_R_MM`` (a cylinder along Y) whose apex is ``height_mm`` above the bed and whose shoulders meet the
          vertical sides at ``height_mm − rise``; the headband seats on the apex.
  room    the front ``room_mm`` of the plate: where the customer's form (Roop) stands.
  socket  Kadi-S Ø 12 under the saddle's centre, 15 mm deep, three crush ribs, a 45° lead-in at the mouth, modelled
          with the chosen finish's print compensation (``connectors.compensation``).

Anchors
  front  volume: origin at the room's centre on the plate top (normal +Z), ``bounds_mm`` = (``width_mm`` − 3) ×
         (``room_mm`` − 3) × (``height_mm`` − ``PLATE_MM`` − ``ROOM_CLEAR_MM``): the form never stands above the
         saddle where the headphones rest (``validate_content`` refuses a ``longest`` size that would, in those words;
         ``contain`` always fits).
  face   surface: the saddle's front face between the plate and the shoulders (normal −Y, u = the viewer's right,
         v = up), ``MARGIN_MM`` inside its edges: a name (Naam) and/or a motif (Buti), raised or cut up to 1.5 mm. A
         wide, low saddle leaves no face for a name and is refused (``validate_combination``).

Hardware: ``dowel_nylon_12x30`` ×1, the adapter pin for a stand whose tube the dowel fits; a stand with a 12 mm pin
of its own needs none (the base item decides, Phase 2).
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely.geometry import Polygon

from .. import cad
from ..connectors import Connector
from ..errors import ParamOutOfRange
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

DOWEL_SKU = "dowel_nylon_12x30"
KADI = Connector("socket", 12.0, adapter_sku=DOWEL_SKU)
PLATE_MM = 4.0  # the plate everything stands on
CROWN_R_MM = 60.0  # the crown's radius: a headband's inner curve is shallower, so it seats on the apex
CORNER_MM = 6.0  # plate corner radius
MARGIN_MM = 1.5  # content kept inside the edges of the face and the room
FACE_BLEED_MM = 0.5
MAX_RELIEF_MM = 1.5
ROOM_CLEAR_MM = 1.0  # the form stays this far under the crown's apex
MIN_FACE_MM = plates.MIN_PRINTABLE_MM  # the face must be at least this tall to carry a name
ARC_STEPS = 48
OVERLAP_MM = 0.5  # the saddle starts this far inside the plate so the union has volume in common


class Layout:
    """Derived dimensions shared by ``build_body``, ``validate_combination``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.W = float(p["width_mm"])
        self.D = float(p["depth_mm"])
        self.H = float(p["height_mm"])
        self.room = float(p["room_mm"])
        self.L = self.D + self.room
        self.rise = CROWN_R_MM - math.sqrt(CROWN_R_MM**2 - (self.W / 2.0) ** 2)
        self.shoulder_z = self.H - self.rise  # where the crown's arc meets the vertical sides
        self.y_saddle_front = self.L / 2.0 - self.D  # the saddle's front face (the back wall of the room)
        self.y_socket = self.L / 2.0 - self.D / 2.0  # the stand's axis, under the saddle's centre
        self.y_room = -self.L / 2.0 + self.room / 2.0
        self.outline: Polygon = plates.rounded_rect(self.W, self.L, CORNER_MM)

    @property
    def face_height(self) -> float:
        """Height of the name's face: from the plate top to the shoulders, less the margins."""
        return self.shoulder_z - PLATE_MM - 2.0 * MARGIN_MM

    @property
    def face_size(self) -> tuple[float, float]:
        return (self.W - 2.0 * MARGIN_MM, self.face_height)

    @property
    def room_height(self) -> float:
        """How tall the form may stand: under the crown's apex."""
        return self.H - PLATE_MM - ROOM_CLEAR_MM

    @property
    def room_bounds(self) -> tuple[float, float, float]:
        return (self.W - 2.0 * MARGIN_MM, self.room - 2.0 * MARGIN_MM, self.room_height)

    def crown_profile(self) -> Polygon:
        """The saddle's cross-section in (x, height): a block topped by the crown's arc."""
        zc = self.H - CROWN_R_MM
        a0 = math.atan2(self.shoulder_z - zc, self.W / 2.0)
        a1 = math.pi - a0
        pts = [(-self.W / 2.0, PLATE_MM - OVERLAP_MM), (self.W / 2.0, PLATE_MM - OVERLAP_MM), (self.W / 2.0, self.shoulder_z)]
        for i in range(1, ARC_STEPS):
            a = a0 + (a1 - a0) * i / ARC_STEPS
            pts.append((CROWN_R_MM * math.cos(a), zc + CROWN_R_MM * math.sin(a)))
        pts.append((-self.W / 2.0, self.shoulder_z))
        return Polygon(pts)

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        return {
            "front": AnchorFrame(np.array([0.0, self.y_room, PLATE_MM]), [1, 0, 0], [0, 1, 0], [0, 0, 1], bounds_mm=self.room_bounds),
            "face": AnchorFrame(
                np.array([0.0, self.y_saddle_front, (PLATE_MM + self.shoulder_z) / 2.0]), [1, 0, 0], [0, 0, 1], [0, -1, 0], size_mm=self.face_size
            ),
        }

    def socket_frame(self) -> AnchorFrame:
        """The Kadi's mouth on the bed under the saddle's centre, running up into the saddle."""
        return AnchorFrame(np.array([0.0, self.y_socket, 0.0]), [1, 0, 0], [0, 1, 0], [0, 0, 1])


PARAMS = {
    "width_mm": Param("number", "Width", 70.0, "mm", 60, 90, 1, handle=True, group="Size", description="Across the saddle; the headband rests along it."),
    "depth_mm": Param("number", "Depth", 40.0, "mm", 30, 50, 1, group="Size", description="Front to back of the saddle."),
    "height_mm": Param("number", "Height", 38.0, "mm", 30, 50, 1, handle=True, group="Size", description="The crown's apex above the stand's tube top."),
    "room_mm": Param("number", "Room in front", 30.0, "mm", 20, 50, 1, group="Shape", description="Plate in front of the saddle where your form stands."),
}

_DEFAULT_LAYOUT = Layout({k: p.default for k, p in PARAMS.items()})
_DEFAULT_FRAMES = _DEFAULT_LAYOUT.anchor_frames()


class HeadphoneTopper(Template):
    id = "headphone_topper"
    version = 1
    family = "headphone_topper"
    name = "Sur Jod headphone topper"
    description = (
        "A saddle that plugs onto a ready-made headphone stand through a 12 mm Kadi socket. Your own form stands in "
        "front of it and your name runs along its face; the headphones rest their band across the crown."
    )
    environment = "desk_oak"
    params = PARAMS
    anchors = (
        Anchor("front", "Room in front of the saddle", "planar", kind="volume", bounds_mm=_DEFAULT_LAYOUT.room_bounds, accepts=("hero_mesh",)),
        Anchor(
            "face", "Front of the saddle", "planar",
            size_mm=tuple(round(s, 1) for s in _DEFAULT_LAYOUT.face_size), bleed_mm=FACE_BLEED_MM,
            accepts=("emboss_text", "motif"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    style_variants = ("comic_pop",)  # Katha's comic-book look: names and motifs raised and bolder by default (features.styles)
    features_supported = ("hero_mesh", "emboss_text", "motif")
    hardware = (HardwareRef(DOWEL_SKU, 1),)
    min_feature_mm = 0.8
    connector = KADI

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        L = Layout(params)
        if L.face_height < MIN_FACE_MM - 1e-9:
            raise ParamOutOfRange(
                ["height_mm", "width_mm"],
                (
                    f"A {L.W:g} mm wide saddle only {L.H:g} mm tall leaves {max(L.face_height, 0):.1f} mm of face for a name; "
                    f"make it taller or narrower (at least {MIN_FACE_MM:g} mm of face)."
                ),
                {"face_height_mm": round(L.face_height, 2), "min_face_mm": MIN_FACE_MM, "rise_mm": round(L.rise, 2)},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        """A form sized by ``longest`` must stay under the crown: the headphones rest there."""
        L = Layout(params)
        for index, f in enumerate(features):
            if f.get("type") != "hero_mesh" or f.get("anchor") != "front" or f.get("fit") != "longest":
                continue
            longest = f.get("longest_mm")
            if longest is not None and float(longest) > L.room_height + 1e-9:
                raise ParamOutOfRange(
                    [f"features[{index}].longest_mm"],
                    (
                        f"Your form would stand above the saddle where the headphones rest; keep it under "
                        f"{L.room_height:g} mm or let it fit the room in front."
                    ),
                    {"longest_mm": float(longest), "max_longest_mm": round(L.room_height, 2), "crown_mm": L.H, "feature": index},
                )

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        L = Layout(params)
        plate = cad.prism(L.outline, PLATE_MM)
        # the saddle's profile is drawn in (x, height), extruded along +Z by its depth, stood up (+Y → +Z) and moved to the back
        saddle = cad.translate(cad.rotate_x(cad.prism(L.crown_profile(), L.D), 90.0), dy=L.L / 2.0)
        return cad.union([plate, saddle])

    @classmethod
    def build_body(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        mesh = cad.to_trimesh(cls.build_part(params))
        mesh.apply_translation([0.0, 0.0, -float(mesh.bounds[0][2])])
        return mesh

    @classmethod
    def connector_frame(cls, params: Mapping[str, Any]) -> AnchorFrame:
        return Layout(params).socket_frame()

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
            f"A Sur Jod headphone topper, {L.W:g} mm across, {L.D:g} mm deep and {L.H:g} mm tall at the crown, with "
            f"{L.room:g} mm of plate in front for a form; printed standing as modelled, no supports. Underneath, a 12 mm "
            f"Kadi socket with three ribs: press the nylon dowel in ribs first until it seats, then push the piece onto "
            f"the stand's tube."
        )

    @classmethod
    def karigar_note_for(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]] = (), style: str | None = None) -> str:
        note = cls.karigar_note(params)
        if any(f.get("type") == "hero_mesh" for f in features or []):
            note += " The customer's form stands fused on the plate in front of the saddle: support its overhangs."
        return cls.join_note(note, features, style)
