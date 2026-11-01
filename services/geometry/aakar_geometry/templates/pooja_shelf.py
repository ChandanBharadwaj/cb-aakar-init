"""``pooja_shelf@1`` - Aradhana: a small wall shelf for a diya, a bell or an agarbatti holder; a name or motif sits on the back plate.

Coordinate frame (mm), **as printed = as mounted**: X across the shelf (the viewer's right when facing it on the wall),
Y depth (+Y is the wall, −Y the front edge of the shelf), Z up. The piece stands on the bed on its whole underside
(the shelf's bottom and the plate's bottom edge are one flat face at Z = 0), is centred in X and Y, and prints as
modelled with no supports: every face is vertical, flat on top or a 45° slope that faces up. Its width along X is
the longest extent (the plate is ``BACK_H_MM`` tall and the shelf never reaches as far as the narrowest width), so
``width_mm`` is the family's envelope figure.

Pieces (one prism: the profile below in the YZ plane, extruded ``width_mm`` along X)
  plate   the back plate against the wall, ``PLATE_MM`` thick (Y) and ``BACK_H_MM`` tall (Z).
  shelf   a slab ``depth_mm`` deep (−Y from the plate's front face) and ``SHELF_MM`` thick at the plate's bottom.
  gusset  a 45° fillet filling the inside corner between the shelf's top and the plate's front, ``GUSSET_MM`` along
          each leg and the full width: the brace that keeps a loaded shelf square to the wall.
  lip     (``lip``) a rail ``LIP_T_MM`` thick and ``LIP_H_MM`` above the shelf along its front edge, so a diya cannot
          slide off.
  keyholes  two slots cut through the plate for the customer's own screws, ``KEY_INSET_MM`` from each end: a
          ``KEY_D_MM`` circle for the screw head, ``KEY_BELOW_TOP_MM`` below the top, with a ``SLOT_W_MM`` slot
          rising ``SLOT_RISE_MM`` above it (round-ended) for the shank: hang the plate over the heads and let it
          down. Both are cut ``plates.HOLE_ALLOWANCE_MM`` larger.

Anchor
  face  the plate's front face above the gusset and below the keyholes (normal −Y, u = +X, v = +Z): the band
        ``ANCHOR_MARGIN_MM`` inside the plate's ends, from ``ANCHOR_MARGIN_MM`` above the gusset up to ``CLEAR_MM``
        under the keyhole circles: a name (Naam) and/or a motif (Buti), raised or cut in. A cut-in must leave
        ``plates.MIN_SKIN_MM`` of the ``PLATE_MM`` plate behind it (``validate_content``).
No hardware: the two wall screws are the customer's own.
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

BACK_H_MM = 70.0  # the plate's height: room for the keyholes, the content band and the gusset
PLATE_MM = 6.0  # the plate: a 0.8 mm cut-in leaves 5.2 mm, and six millimetres carries a screw head without crushing
SHELF_MM = 6.0  # the shelf: a brass diya or a bell hook on a 100 mm cantilever wants a stiff slab
GUSSET_MM = 8.0  # the 45° fillet's legs: the brace in the corner, and the slope faces up so it prints as modelled
LIP_T_MM = 3.0  # the lip rail: well over min wall, standing 6 mm tall on the shelf's front edge
LIP_H_MM = 6.0  # the lip's height above the shelf's top
KEY_D_MM = 9.0  # keyhole circle: a #8 / 4 mm screw's head passes
SLOT_W_MM = 5.0  # keyhole slot: the shank slides up it
SLOT_RISE_MM = 10.0  # from the circle's centre to the slot's rounded top
KEY_INSET_MM = 25.0  # the keyholes' centres from each end of the plate
KEY_BELOW_TOP_MM = 15.0  # the circle's centre below the plate's top: 5 mm of plastic stays above the slot's end
CLEAR_MM = 2.0  # plastic kept between the content band and the keyhole circles
ANCHOR_MARGIN_MM = 1.5
BLEED_MM = 1.0
MAX_RELIEF_MM = 1.5
OVERLAP_MM = 1.0  # the keyhole cutters reach this far past both faces of the plate so no cut meets a coplanar face


class Layout:
    """Derived dimensions shared by ``build_part``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.W = float(p["width_mm"])
        self.D = float(p["depth_mm"])
        self.lip = bool(p["lip"])
        self.H = BACK_H_MM
        self.T = PLATE_MM
        self.S = SHELF_MM
        # the profile is drawn with the plate's front face at Y = 0; the part is then shifted so Y is centred
        self.y_front = (self.D - self.T) / 2.0
        self.z_key = self.H - KEY_BELOW_TOP_MM  # the keyhole circles' centres
        self.key_cut = KEY_D_MM + plates.HOLE_ALLOWANCE_MM
        self.slot_cut = SLOT_W_MM + plates.HOLE_ALLOWANCE_MM
        self.key_xs = [-(self.W / 2.0 - KEY_INSET_MM), self.W / 2.0 - KEY_INSET_MM]

    @property
    def z_slot_top(self) -> float:
        return self.z_key + SLOT_RISE_MM

    def profile(self) -> Polygon:
        """The L-with-fillet section in the (Y, Z) plane, the plate's front face on Y = 0, the bed on Z = 0."""
        D, T, H, S, G = self.D, self.T, self.H, self.S, GUSSET_MM
        pts = [(-D, 0.0), (T, 0.0), (T, H), (0.0, H), (0.0, S + G), (-G, S)]
        if self.lip:
            pts += [(-D + LIP_T_MM, S), (-D + LIP_T_MM, S + LIP_H_MM), (-D, S + LIP_H_MM)]
        else:
            pts.append((-D, S))
        return Polygon(pts)

    def keyhole(self, x: float) -> Polygon:
        """One keyhole in the (X, Z) plane: the circle, the slot above it and the slot's rounded end."""
        r_slot = self.slot_cut / 2.0
        z_end = self.z_slot_top - r_slot  # the round end's centre
        circle = plates.hole(x, self.z_key, self.key_cut)
        slot = box(x - r_slot, self.z_key, x + r_slot, z_end)
        end = plates.hole(x, z_end, self.slot_cut)
        return plates.largest_polygon(unary_union([circle, slot, end]))

    def anchor_rect(self) -> tuple[float, float, float, float]:
        """``(x0, z0, x1, z1)`` of the content band on the plate's front face."""
        x0, x1 = -self.W / 2.0 + ANCHOR_MARGIN_MM, self.W / 2.0 - ANCHOR_MARGIN_MM
        z0 = self.S + GUSSET_MM + ANCHOR_MARGIN_MM
        z1 = self.z_key - self.key_cut / 2.0 - CLEAR_MM
        return (x0, z0, x1, z1)

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        x0, z0, x1, z1 = self.anchor_rect()
        return {
            "face": AnchorFrame(
                np.array([(x0 + x1) / 2.0, self.y_front, (z0 + z1) / 2.0]), [1, 0, 0], [0, 0, 1], [0, -1, 0],
                size_mm=(x1 - x0, z1 - z0),
            ),
        }


PARAMS = {
    "width_mm": Param(
        "number", "Width", 150.0, "mm", 80, 250, 5, handle=True, group="Size",
        description="Across the shelf; the name goes on the back plate along this side.",
    ),
    "depth_mm": Param(
        "number", "Depth", 70.0, "mm", 50, 100, 5, handle=True, group="Size",
        description="How far the shelf stands out from the wall.",
    ),
    "lip": Param("boolean", "Front lip", True, group="Details", description="A low rail along the shelf's front edge so a diya cannot slide off."),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class PoojaShelf(Template):
    id = "pooja_shelf"
    version = 1
    family = "pooja_accessory"
    name = "Aradhana pooja shelf"
    description = (
        "A small wall shelf for a diya, a bell or an agarbatti holder: a back plate with two keyhole slots for your "
        "own screws, a braced shelf and a low lip so nothing slides off. Your name or a motif sits on the back plate "
        "above the shelf."
    )
    environment = "teak_table_candlelight"
    params = PARAMS
    anchors = (
        Anchor(
            "face", "Back plate", "planar", size_mm=_size("face"), bleed_mm=BLEED_MM,
            accepts=("motif", "emboss_text"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.6, max_overhang_deg=45, bed_mm=(250, 250, 250))
    features_supported = ("motif", "emboss_text")
    hardware: tuple[HardwareRef, ...] = ()
    min_feature_mm = 0.8

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        plates.check_skin(features, anchors=("face",), thickness_mm=PLATE_MM, noun="back plate", thickness_key="depth_mm")

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        L = Layout(params)
        body = cad.translate(cad.prism(L.profile(), L.W, cad.Plane.YZ), dx=-L.W / 2.0)
        # keyholes: drawn in the XZ plane, which extrudes toward −Y, so the cutter is placed to run from one
        # OVERLAP_MM behind the plate's back face to one in front of its front face
        cutters = [
            cad.translate(cad.prism(L.keyhole(x), L.T + 2.0 * OVERLAP_MM, cad.Plane.XZ), dy=L.T + OVERLAP_MM)
            for x in L.key_xs
        ]
        return cad.translate(cad.cut(body, cutters), dy=L.y_front)

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
        lip = f", with a {LIP_H_MM:g} mm lip along the front edge" if L.lip else ", no lip"
        return (
            f"An Aradhana pooja shelf, {L.W:g} mm wide, {L.D:g} mm deep and {L.H:g} mm tall: a {L.T:g} mm back plate "
            f"with two keyhole slots, a {L.S:g} mm shelf braced by a 45° fillet in the corner{lip}. Print it standing "
            "on its bottom edge as modelled, the shelf flat on the bed and the plate rising, no supports. It fixes to "
            f"the wall with two screws through the keyholes, heads up to {KEY_D_MM:g} mm (screws not supplied)."
        )
