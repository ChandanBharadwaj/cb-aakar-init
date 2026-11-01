"""``vent_mount@1`` - Safar: a car-vent phone cradle that pinches a louvre between two sprung tongues; a motif sits on
the cradle's face.

The clip: two tongues stand out of the back plate, perpendicular to it, near its top edge. Mounted they point into
the vent; the gap between them, ``SLOT_MM``, is narrower than a louvre (1.5-2.5 mm), so a horizontal louvre slid
between them springs the PETG tongues apart and is pinched. A small barb on each tongue's inner face near its tip
holds the louvre once it is past. Two standoff rails down the back's side edges bear on the vent's face so the
cradle sits square and does not rock on the louvre.

Mounted frame (mm), used to describe the pieces: x = across (the viewer's right when the phone faces them), y = up
the cradle (the lip at −y, the clip near +y), d = out of the dashboard towards the cabin. The plate's back is at
d = 0 and its face (the cabin side, where the phone rests and the motif sits) at d = ``PLATE_MM``. The lip, the
wings and the motif stand out to +d; the tongues and the rails stand back to −d.

Print frame, **as printed = as modelled** (``Layout.to_print``): the cradle lies ON ITS SIDE, its right-hand edge
(+x) on the bed. X = d (the cabin side towards +X), Y = y, Z = W/2 − x. The part is two-sided (lip and wings one
way, tongues the other, all perpendicular to the plate), so no flat orientation prints without supports; on its
side the plate, the lip, the rails and both tongues stand as vertical walls, the 1.6 mm slot between the tongues is
a vertical slit that prints clean, and the barbs are prisms along Z with no overhang at all. The tongues widen at 45°
towards their root (``tongue_half_width``) and the rails carry a 45° gusset, so their undersides print without
support. Two overhangs remain on the upper half: the upper wing's inner face, a 7 mm ledge over the plate's face, and
the cable-gap end of the upper lip half, which starts in mid-air; both want a support strip. The body is centred in
X and Y and rests on Z = 0; its longest extent is the plate's height ``cradle_h_mm``.

Pieces (mounted frame)
  plate   ``cradle_w_mm`` × ``cradle_h_mm`` × ``PLATE_MM``: the phone's back rests on its face.
  lip     a straight ``LIP_T_MM`` thick wall along the plate's bottom edge (−y), standing ``lip_h_mm`` from the back
          (d = 0 .. ``lip_h_mm``); the phone's bottom edge stands on it. A ``cable_gap_mm`` opening in its middle
          passes a charging cable.
  wings   two ``WING_T_MM`` thick walls, d = 0 .. ``WING_H_MM``, along the plate's left and right edges from the lip
          up to ``WING_SHARE`` of the plate's height; they keep the phone from sliding sideways.
  tongues two ``TONGUE_T_MM`` plates, ``TONGUE_L_MM`` long (d = −``TONGUE_L_MM`` .. 0), ``TONGUE_W_MM`` wide at the
          tip and widening at 45° to the root, either side of a ``SLOT_MM`` slot centred ``CLIP_DROP_MM`` below the
          plate's top edge and on x = 0. A ``BARB_MM`` 45° bump on each inner face near the tip.
  rails   two strips down the back's side edges, standing ``RAIL_MM`` back (d = −``RAIL_MM`` .. 0), ``RAIL_T_MM`` at
          the foot with a 45° gusset on the inner face: they rest on the vent's face, and on the bed they widen the
          footprint so the part does not tip while printing.

Anchor
  face  the largest rectangle on the plate's cabin face (d = ``PLATE_MM``, normal +d = print +X), ``WING_T_MM`` +
        ``ANCHOR_MARGIN_MM`` inside the side edges and ``ANCHOR_MARGIN_MM`` clear of the lip and of the plate's top
        edge: a motif (Buti), raised or cut in. The tongues are on the back, so they never cross it. A cut-in motif
        must leave ``plates.MIN_SKIN_MM`` of the plate (``validate_content``).

No hardware. The family is heat-safe only (a closed car reaches 70 °C): the pipeline prints it in petg_slate.
"""

from __future__ import annotations

from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely.geometry import Polygon, box
from shapely.ops import unary_union

from .. import cad
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

PLATE_MM = 3.0  # the back plate: stiff enough to carry a phone, and 1.2 mm of skin stays under a 1.8 mm cut-in
LIP_T_MM = 2.4  # six perimeters on a 0.4 mm nozzle: the lip takes the phone's weight
WING_T_MM = 2.4
WING_H_MM = 10.0  # from the back: 7 mm above the plate's face, enough to stop a phone sliding sideways
WING_SHARE = 0.6  # the wings run from the lip up to this share of the plate's height; the top stays open for a thumb
TONGUE_T_MM = 2.4  # each tongue: thin enough to spring 0.5 mm in PETG over its length, thick enough not to snap
TONGUE_L_MM = 25.0  # how far the tongues reach into the vent, from the plate's back
TONGUE_W_MM = 20.0  # across the tongue at its tip; it widens at 45° towards the root
SLOT_MM = 1.6  # the gap between the tongues: a 1.5-2.5 mm louvre is pinched as the PETG tongues spring apart
CLIP_DROP_MM = 10.0  # the slot's centre below the plate's top edge
BARB_MM = 0.4  # the bump on each tongue's inner face that holds the louvre once it is past
BARB_FROM_TIP_MM = 0.5  # where the barb's lead-in ramp starts, measured back from the tip
BARB_FLAT_MM = 1.0  # the barb's flat crest between its two 45° ramps
RAIL_MM = 7.0  # the standoff rails stand this far back; they also put the print's centre of mass well inside its footprint
RAIL_T_MM = 2.4
ANCHOR_MARGIN_MM = 1.5
BLEED_MM = 1.0
MAX_RELIEF_MM = 1.5
CABIN_C = 70  # a closed car in the sun: the reason the family is heat-safe only
FUSE_MM = 1.0  # tongues and rails start this far inside the plate: one solid, no coplanar seam


class Layout:
    """Derived dimensions shared by ``build_part``, ``anchor_frame`` and ``karigar_note`` (mounted frame unless noted)."""

    def __init__(self, p: Mapping[str, Any]):
        self.W = float(p["cradle_w_mm"])
        self.H = float(p["cradle_h_mm"])
        self.lip_h = float(p["lip_h_mm"])
        self.gap = float(p["cable_gap_mm"])
        self.y0 = -self.H / 2.0  # the plate's bottom edge (the lip)
        self.y_top = self.H / 2.0  # the plate's top edge
        self.y_slot = self.y_top - CLIP_DROP_MM  # the slot's centre line
        self.d_tip = -TONGUE_L_MM  # the tongues' tips, the furthest back the part reaches
        self.d_front = self.lip_h  # the lip, the furthest forward
        self.d_mid = (self.d_tip + self.d_front) / 2.0  # print X = d − d_mid centres the part
        self.L = self.H  # the longest extent: the plate's height

    # ------------------------------------------------------------------ frames

    def to_print(self, points: Any) -> np.ndarray:
        """Mounted ``(x, y, d)`` points to the print frame: on its side, the +x edge on the bed."""
        pts = np.asarray(points, dtype=float).reshape(-1, 3)
        return np.c_[pts[:, 2] - self.d_mid, pts[:, 1], self.W / 2.0 - pts[:, 0]]

    def print_matrix(self) -> np.ndarray:
        """The 4 × 4 transform that takes the solid as modelled (x, y, d along X, Y, Z) to the print frame."""
        m = np.eye(4)
        m[:3, :3] = [[0.0, 0.0, 1.0], [0.0, 1.0, 0.0], [-1.0, 0.0, 0.0]]
        m[:3, 3] = [-self.d_mid, 0.0, self.W / 2.0]
        return m

    # ------------------------------------------------------------------ pieces

    def tongue_half_width(self) -> float:
        """Half the tongue's width at its root: 45° out from the tip, at most the plate's half-width."""
        return min(self.W / 2.0, TONGUE_W_MM / 2.0 + TONGUE_L_MM)

    def tongue_profile(self) -> Polygon:
        """A tongue's outline in the (x, d) plane: ``TONGUE_W_MM`` at the tip, widening at exactly 45° until it meets the
        root width, then straight to the plate (flush with its side edges when the cradle is narrow), its root inside
        the plate."""
        tip, root = TONGUE_W_MM / 2.0, self.tongue_half_width()
        d_flank = self.d_tip + (root - tip)  # where the 45° flank reaches the root width
        return Polygon(
            [(-root, FUSE_MM), (root, FUSE_MM), (root, d_flank), (tip, self.d_tip), (-tip, self.d_tip), (-root, d_flank)]
        )

    def tongue_spans(self) -> list[tuple[float, float]]:
        """``(y_low, y_high)`` of the lower and upper tongue."""
        half = SLOT_MM / 2.0
        return [(self.y_slot - half - TONGUE_T_MM, self.y_slot - half), (self.y_slot + half, self.y_slot + half + TONGUE_T_MM)]

    def barb_profile(self, side: int) -> Polygon:
        """A barb's outline in the (y, d) plane on the lower (``side`` −1) or upper (+1) tongue's inner face."""
        face = self.y_slot + side * SLOT_MM / 2.0  # the inner face
        a = self.d_tip + BARB_FROM_TIP_MM
        b, c = a + BARB_MM, a + BARB_MM + BARB_FLAT_MM
        e = c + BARB_MM
        back = face + side * 0.5  # into the tongue
        crest = face - side * BARB_MM  # into the slot
        return Polygon([(back, a), (face, a), (crest, b), (crest, c), (face, e), (back, e)])

    def lip_region(self) -> Polygon:
        """The lip's footprint: the bottom band of the plate minus the cable gap in its middle."""
        band = box(-self.W / 2.0, self.y0, self.W / 2.0, self.y0 + LIP_T_MM)
        gap = box(-self.gap / 2.0, self.y0 - 1.0, self.gap / 2.0, self.y0 + LIP_T_MM + 1.0)
        return band.difference(gap)

    def wing_region(self) -> Polygon:
        """The two wings' footprints: the side bands from the lip up to ``WING_SHARE`` of the plate."""
        y1 = self.y0 + WING_SHARE * self.H
        left = box(-self.W / 2.0, self.y0, -self.W / 2.0 + WING_T_MM, y1)
        right = box(self.W / 2.0 - WING_T_MM, self.y0, self.W / 2.0, y1)
        return unary_union([left, right])

    def rail_profiles(self) -> list[Polygon]:
        """The two standoff rails' outlines in the (x, d) plane: ``RAIL_T_MM`` at the foot that rests on the vent, a 45°
        gusset on the inner face back to the plate (so the upper rail's underside prints without support)."""
        e, t, r = self.W / 2.0, RAIL_T_MM, RAIL_MM
        right = Polygon([(e, FUSE_MM), (e, -r), (e - t, -r), (e - t - r - FUSE_MM, FUSE_MM)])
        left = Polygon([(-x, d) for x, d in reversed(right.exterior.coords[:-1])])
        return [left, right]

    def anchor_rect(self) -> tuple[float, float, float, float]:
        """``(x0, y0, x1, y1)`` of the motif area on the plate's face: inside the wings, clear of the lip and the top edge."""
        half = self.W / 2.0 - WING_T_MM - ANCHOR_MARGIN_MM
        return (-half, self.y0 + LIP_T_MM + ANCHOR_MARGIN_MM, half, self.y_top - ANCHOR_MARGIN_MM)

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        """In the print frame: the face looks along +X; u (mounted +x) runs down −Z, v (mounted +y) along +Y."""
        x0, y0, x1, y1 = self.anchor_rect()
        origin = self.to_print([(x0 + x1) / 2.0, (y0 + y1) / 2.0, PLATE_MM])[0]
        return {"face": AnchorFrame(origin, [0, 0, -1], [0, 1, 0], [1, 0, 0], size_mm=(x1 - x0, y1 - y0))}


PARAMS = {
    "cradle_w_mm": Param(
        "number", "Width", 70.0, "mm", 60, 80, 1, handle=True, group="Size",
        description="Across the cradle; a phone in a case is 70-80 mm wide.",
    ),
    "cradle_h_mm": Param("number", "Height", 110.0, "mm", 90, 130, 1, group="Size", description="From the lip to the cradle's top edge."),
    "lip_h_mm": Param("number", "Lip", 12.0, "mm", 10, 15, 0.5, group="Details", description="How tall the lip the phone stands on is."),
    "cable_gap_mm": Param(
        "number", "Cable gap", 20.0, "mm", 16, 26, 1, group="Details",
        description="The opening in the middle of the lip for a charging cable.",
    ),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


def _along_y(profile: Polygon, y_low: float, y_high: float) -> "cad.Part":
    """A prism of an (x, d) outline spanning ``y_low`` .. ``y_high`` (modelled with d along Z)."""
    part = cad.rotate_x(cad.prism(profile, y_high - y_low), 90.0)  # (x, d) on XY, extruded: x, −extrusion, d
    return cad.translate(part, dy=y_high)


def _along_x(profile: Polygon, x_low: float, x_high: float) -> "cad.Part":
    """A prism of a (y, d) outline spanning ``x_low`` .. ``x_high`` (modelled with d along Z)."""
    part = cad.rotate_z(cad.rotate_x(cad.prism(profile, x_high - x_low), 90.0), 90.0)  # → extrusion, y, d
    return cad.translate(part, dx=x_low)


class VentMount(Template):
    id = "vent_mount"
    version = 1
    family = "car_accessory"
    name = "Safar vent mount"
    description = (
        "A phone cradle that clips onto a horizontal car-vent louvre: two sprung tongues on its back pinch the louvre "
        "and a small barb holds it, a lip with a cable gap takes the phone's weight and two short wings keep it from "
        "sliding. Your motif sits on the cradle's face, in a heat-safe finish."
    )
    environment = "dashboard"
    params = PARAMS
    anchors = (
        Anchor("face", "Face", "planar", size_mm=_size("face"), bleed_mm=BLEED_MM, accepts=("motif",), max_relief_mm=MAX_RELIEF_MM),
    )
    constraints = TemplateConstraints(min_wall_mm=2.0, max_overhang_deg=45, bed_mm=(250, 250, 250))
    features_supported = ("motif",)
    hardware: tuple[HardwareRef, ...] = ()
    min_feature_mm = 0.8

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("face",), thickness_mm=PLATE_MM, noun="back plate", thickness_key="cradle_h_mm")

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        """The solid in the mounted frame (x, y, d along X, Y, Z); ``build_body`` lays it on its side."""
        L = Layout(params)
        plate = cad.prism(box(-L.W / 2.0, L.y0, L.W / 2.0, L.y_top), PLATE_MM)
        # the lip and the wings rise from the back through the plate: no coplanar seam at the plate's face
        lip = cad.prism(L.lip_region(), L.lip_h)
        wings = cad.prism(L.wing_region(), WING_H_MM)
        rails = [_along_y(profile, L.y0, L.y_top) for profile in L.rail_profiles()]
        tongues = [_along_y(L.tongue_profile(), lo, hi) for lo, hi in L.tongue_spans()]
        barbs = [_along_x(L.barb_profile(side), -TONGUE_W_MM / 2.0, TONGUE_W_MM / 2.0) for side in (-1, 1)]
        return cad.union([plate, lip, wings, *rails, *tongues, *barbs])

    @classmethod
    def build_body(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        L = Layout(params)
        mesh = cad.to_trimesh(cls.build_part(params))
        mesh.apply_transform(L.print_matrix())
        mesh.apply_translation([0.0, 0.0, -float(mesh.bounds[0][2])])  # exact already; this only clears rounding
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
            f"A Safar vent mount, {L.W:g} mm wide and {L.H:g} mm tall, on a {PLATE_MM:g} mm plate. Print it on its side, "
            f"one long edge on the bed, as laid out; it wants support in two places only: under the upper wing's "
            f"{WING_H_MM - PLATE_MM:g} mm ledge and under the cable-gap end of the upper half of the lip. On the face, a straight {LIP_T_MM:g} mm lip {L.lip_h:g} mm tall runs along "
            f"the bottom with a {L.gap:g} mm gap for the charging cable, and {WING_T_MM:g} mm wings run up the sides to "
            f"{WING_SHARE * 100:.0f} % of the height. On the back, two {TONGUE_T_MM:g} mm tongues {TONGUE_L_MM:g} mm long "
            f"stand out near the top with a {SLOT_MM:g} mm slot between them and a small barb inside each tip: push them "
            f"onto a horizontal vent louvre (1.5-2.5 mm thick) until the barbs click past it: the tongues spring "
            f"to pinch a thick louvre, and a thin one is held behind the barbs. Two {RAIL_MM:g} mm rails on the back rest on the vent's face. Heat-safe finish only: the cabin "
            f"reaches {CABIN_C} °C. The lip takes the phone's weight."
        )
