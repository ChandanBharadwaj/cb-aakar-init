"""``desk_nameplate@1`` - Pehchaan: a nameplate leaning back on a flat foot, like the Jharokha back rest.

Coordinate frame (mm): X = width (left/right), Y = depth (−Y is the front the reader faces, +Y the
back), Z = height. The part sits on Z = 0 and its bounding box is centred in X and Y.

Pieces
  foot   ``width_mm`` × ``base_depth_mm`` × ``BASE_HEIGHT_MM`` slab on the table.
  plate  ``thickness_mm``-thick plate leaning back at ``tilt_deg`` from horizontal. Its face is
         ``height_mm`` tall measured up the slope and starts ``FOOT_LIP_MM`` behind the foot's front
         edge; its lower back edge is buried in the foot, so the two print as one solid.

Anchors (content lands on these, see ``anchor_frame``)
  face        the plate's front, ``FACE_MARGIN_MM`` inside its edges (normal perpendicular to the
              plate, up and towards the reader; v runs up the slope): the name (Naam) first, with a
              motif (Buti) beside it, or a photo instead.
  base_front  the foot's front edge, a strip ``BASE_HEIGHT_MM`` tall (normal −Y): a line of text.
The piece must stand: the combined centre of gravity keeps at least ``MIN_TIPPING_MARGIN_MM`` (the
inspect service's own threshold) plus ``TIP_SAFETY_MM`` inside the foot, or the combination is refused.
Width is capped at the 250 mm bed, which is also the Pehchaan family envelope's longest side (120–250 mm).
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely.geometry import Polygon, box

from .. import cad
from ..errors import ParamOutOfRange
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

BASE_HEIGHT_MM = 10.0
FOOT_LIP_MM = 3.0  # foot left in front of the face
FACE_MARGIN_MM = 2.0
BASE_FRONT_MARGIN_MM = 1.0
BLEED_MM = 1.0
BASE_FRONT_BLEED_MM = 0.5
MAX_RELIEF_MM = 1.5
MIN_TIPPING_MARGIN_MM = 5.0  # aakar_inspect Constraints.min_tipping_margin_mm: below this is "tippy"
TIP_SAFETY_MM = 1.5  # content on the face moves the centre of gravity a little
TEXT_HEIGHT_SHARE = 0.6  # tallest text line as a share of the face height


class Layout:
    """Derived dimensions shared by ``build_body``, ``validate_combination``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.W = float(p["width_mm"])
        self.L = float(p["height_mm"])
        self.tilt = float(p["tilt_deg"])
        self.D = float(p["base_depth_mm"])
        self.t = float(p["thickness_mm"])
        self.H = BASE_HEIGHT_MM
        th = math.radians(self.tilt)
        self.sin, self.cos = math.sin(th), math.cos(th)
        self.y_foot = -self.D / 2.0 + FOOT_LIP_MM  # the face's bottom edge, on the foot's top
        back_top_y = self.y_foot + self.L * self.cos + self.t * self.sin
        self.y_max = max(self.D / 2.0, back_top_y)
        self.dy = -(-self.D / 2.0 + self.y_max) / 2.0  # centres the bounding box in Y

    def plate_point(self, x: float, s: float, n: float = 0.0) -> np.ndarray:
        """World point ``s`` mm up the face from its bottom edge and ``n`` mm into the plate from the face."""
        return np.array([x, self.y_foot + s * self.cos + n * self.sin + self.dy, self.H + s * self.sin - n * self.cos])

    def section(self) -> Polygon:
        """The (Y, Z) cross-section, identical along the whole width (before centring)."""
        foot = box(-self.D / 2.0, 0.0, self.D / 2.0, self.H)
        pts = [(self.y_foot + s * self.cos + n * self.sin, self.H + s * self.sin - n * self.cos) for s, n in ((0, 0), (self.L, 0), (self.L, self.t), (0, self.t))]
        return foot.union(Polygon(pts))

    def tipping_margin(self) -> float:
        """Distance from the centre of gravity's shadow to the nearest edge of the foot (the table contact)."""
        cy = float(self.section().centroid.x)
        return min(self.D / 2.0 - cy, cy + self.D / 2.0, self.W / 2.0)

    @property
    def top_z(self) -> float:
        return self.H + self.L * self.sin

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        face_size = (self.W - 2.0 * FACE_MARGIN_MM, self.L - 2.0 * FACE_MARGIN_MM)
        base_size = (self.W - 2.0 * BASE_FRONT_MARGIN_MM, self.H - 2.0 * BASE_FRONT_MARGIN_MM)
        up_slope = np.array([0.0, self.cos, self.sin])
        face_normal = np.array([0.0, -self.sin, self.cos])  # towards the reader and a little up
        return {
            "face": AnchorFrame(self.plate_point(0.0, self.L / 2.0), [1, 0, 0], up_slope, face_normal, size_mm=face_size),
            "base_front": AnchorFrame(
                np.array([0.0, -self.D / 2.0 + self.dy, self.H / 2.0]), [1, 0, 0], [0, 0, 1], [0, -1, 0], size_mm=base_size
            ),
        }


PARAMS = {
    "width_mm": Param("number", "Width", 180.0, "mm", 120, 250, 1, handle=True, group="Size"),
    "height_mm": Param(
        "number", "Height", 60.0, "mm", 40, 100, 1, handle=True, group="Size",
        description="Height of the face, measured up the slope.",
    ),
    "tilt_deg": Param("number", "Tilt", 70.0, "deg", 60, 80, 1, group="Shape", description="Lean of the face from horizontal."),
    "base_depth_mm": Param("number", "Foot depth", 40.0, "mm", 30, 60, 1, group="Shape"),
    "thickness_mm": Param("number", "Thickness", 5.0, "mm", 4.0, 8.0, 0.5, group="Details"),
}

_DEFAULTS = {k: p.default for k, p in PARAMS.items()}
_DEFAULT_FRAMES = Layout(_DEFAULTS).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class DeskNameplate(Template):
    id = "desk_nameplate"
    version = 1
    family = "nameplate"
    name = "Pehchaan desk nameplate"
    description = (
        "A nameplate that leans back on a flat foot, like a desk sign. Your name stands in relief on the "
        "face, with a motif beside it or a photo instead, and a line of text can run along the foot."
    )
    environment = "studio"
    params = PARAMS
    anchors = (
        Anchor(
            "face", "Face", "planar", max_text_height_mm=round(TEXT_HEIGHT_SHARE * _DEFAULTS["height_mm"], 1),
            size_mm=_size("face"), bleed_mm=BLEED_MM, accepts=("emboss_text", "motif", "relief_image"), max_relief_mm=MAX_RELIEF_MM,
        ),
        Anchor(
            "base_front", "Front of the foot", "planar", max_text_height_mm=_size("base_front")[1],
            size_mm=_size("base_front"), bleed_mm=BASE_FRONT_BLEED_MM, accepts=("emboss_text", "relief_image"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    style_variants = ("comic_pop",)  # Katha's comic-book look: names and motifs raised and bolder by default (features.styles)
    features_supported = ("emboss_text", "motif", "relief_image")
    hardware = (HardwareRef("adhesive_pads", 1),)
    min_feature_mm = 0.8

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        layout = Layout(params)
        margin = layout.tipping_margin()
        needed = MIN_TIPPING_MARGIN_MM + TIP_SAFETY_MM
        if margin < needed:
            raise ParamOutOfRange(
                ["base_depth_mm", "height_mm", "tilt_deg"],
                (
                    f"This nameplate would rock backwards: its weight sits {margin:.1f} mm from the back of the foot "
                    f"(needs {needed:g} mm). Make the foot deeper, the face shorter or stand it more upright."
                ),
                {"tipping_margin_mm": round(margin, 2), "min_tipping_margin_mm": needed},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("face",), thickness_mm=float(params["thickness_mm"]), noun="nameplate")

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        L = Layout(params)
        foot = cad.prism(box(-L.W / 2.0, -L.D / 2.0, L.W / 2.0, L.D / 2.0), L.H)
        # flat plate (x across, y up the face, z through the thickness from back to face), then leaned back
        plate = cad.rotate_x(cad.prism(box(-L.W / 2.0, 0.0, L.W / 2.0, L.L), L.t), L.tilt)
        plate = cad.translate(plate, dy=L.y_foot + L.t * L.sin, dz=L.H - L.t * L.cos)
        return cad.translate(cad.union([foot, plate]), dy=L.dy)

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
            f"A Pehchaan desk nameplate, {L.W:g} mm wide, its {L.L:g} mm face leaning back at {L.tilt:g}° on a "
            f"{L.D:g} mm deep foot. Stick the pair of foam pads under the foot before packing so it sits firm on the desk."
        )
