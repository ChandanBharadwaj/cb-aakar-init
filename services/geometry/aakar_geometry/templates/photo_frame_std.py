"""``photo_frame_std@1`` - Chaukhat: a frame for a 4 × 6 in photo behind an acrylic pane, with a motif or a name
on its top rail and a line of text on its bottom rail.

Photo and rabbet (a picture framer's rules, 1/4 in and 1/16 in): the photo is ``PHOTO_MM`` (4 × 6 in,
101.6 × 152.4 mm; the only pane in the seed is ``acrylic_4x6``, so 5 × 7 waits for its hardware SKU). The
frame's lip overlaps the photo by ``RABBET_OVERLAP_MM`` (6.35 mm) on every side, so the window is the photo
less 12.7 mm (88.9 × 139.7 mm). Behind the lip the rabbet (the pocket the pane, the photo and a card backing
drop into from the back) is the photo plus ``RABBET_CLEARANCE_MM`` (1.6 mm overall) and ``RABBET_DEPTH_MM``
(3/8 in, 9.5 mm) deep. The lip in front of it is ``LIP_MM`` thick, so the frame is 12.5 mm deep.

Frame coordinates (mm) before it is set in place: x = the viewer's right, y = up the picture from the frame's
bottom edge, z = towards the viewer, the back on z = 0 and the front face at z = 12.5 (right-handed).

Pieces
  frame   ``border_mm`` (12–25 mm) of face all round the window, ``orientation`` portrait or landscape. The
          solid wall beyond the rabbet is the border less 7.15 mm (at least 4.85 mm).
  stand   ``easel``: two feet behind the side walls at the bottom (the rabbet's opening stays clear), cut so
          the frame leans back ``EASEL_LEAN_DEG`` (15°) with the feet flat on the table and the frame resting
          on its back bottom edge; built standing like that (X across, the front towards −Y, Z up).
          ``hanger``: a tab above the top edge with a ``HANG_HOLE_MM`` hole for a nail or a picture hook
          (``RIM_MM`` round it); built lying on its back, the front facing +Z, as it hangs flat on a wall.
Both print face down (the front on the bed) with no supports: the rabbet, the feet and the tab all open
upward from there. That is why names and motifs are cut into the face, never raised: raised ones would lift
the front off the bed (``validate_content``).

Anchors (surface, the front face; u = the viewer's right, v = up the picture, normal out of the face)
  border      the top rail: the band between the window's top edge and the frame's top edge,
              ``ANCHOR_MARGIN_MM`` inside both and the frame's sides: a motif (Buti) or a name (Naam).
  base_front  the bottom rail, the same band below the window: a line of text.
Decoration never reaches the rabbet: the bands start outside the window, and a cut is at most
``MAX_RELIEF_MM`` (1.5 mm) into the 3 mm lip, leaving ``MIN_SKIN_MM`` (1.2 mm) and more over the rabbet.
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely.geometry import Polygon, box

from .. import cad
from ..errors import UnsupportedFeature
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

PHOTO_MM = (101.6, 152.4)  # 4 × 6 in, portrait
RABBET_OVERLAP_MM = 6.35  # 1/4 in of the photo hidden under the lip on every side
RABBET_CLEARANCE_MM = 1.6  # 1/16 in: the rabbet is this much larger than the photo overall
RABBET_DEPTH_MM = 9.5  # 3/8 in: the acrylic pane, the photo and a card backing
LIP_MM = 3.0
DEPTH_MM = LIP_MM + RABBET_DEPTH_MM
ANCHOR_MARGIN_MM = 1.5
BLEED_MM = 0.5
MAX_RELIEF_MM = 1.5  # a cut of 1.5 mm into the 3 mm lip leaves 1.5 mm over the rabbet
EASEL_LEAN_DEG = 15.0
FOOT_DEPTH_MM = 50.0  # how far the feet reach back, measured square to the frame
FOOT_HEIGHT_MM = 45.0  # how far up the frame's back they are joined
FOOT_MAX_MM = 6.0  # thickness of a foot (less on a narrow wall)
FOOT_INSET_MM = 0.5  # kept clear of the rabbet's edge and the frame's outer edge
HANG_HOLE_MM = 5.0
HANG_RIM_MM = 2.5

ORIENTATIONS = ("portrait", "landscape")
STANDS = ("easel", "hanger")
PANE_SKU = "acrylic_4x6"


def _rect(w: float, h: float, y0: float) -> Polygon:
    return box(-w / 2.0, y0, w / 2.0, y0 + h)


class Layout:
    """Derived dimensions shared by ``build_body``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.orientation = str(p["orientation"])
        self.border = float(p["border_mm"])
        self.stand = str(p["stand"])
        pw, ph = PHOTO_MM if self.orientation == "portrait" else PHOTO_MM[::-1]
        self.photo = (pw, ph)
        self.window = (pw - 2.0 * RABBET_OVERLAP_MM, ph - 2.0 * RABBET_OVERLAP_MM)
        self.rabbet = (pw + RABBET_CLEARANCE_MM, ph + RABBET_CLEARANCE_MM)
        self.outer = (self.window[0] + 2.0 * self.border, self.window[1] + 2.0 * self.border)
        self.lip_band = RABBET_OVERLAP_MM + RABBET_CLEARANCE_MM / 2.0  # lip from the window's edge to the rabbet's
        self.wall = self.border - self.lip_band  # solid plastic beyond the rabbet
        self.T = DEPTH_MM
        self.easel = self.stand == "easel"
        self.lean = EASEL_LEAN_DEG if self.easel else 0.0
        # hanging tab: a round tab on the top edge, the hole ``HANG_RIM_MM`` inside it
        self.hole_cut = HANG_HOLE_MM + plates.HOLE_ALLOWANCE_MM
        self.tab_r = (self.hole_cut / 2.0) / math.cos(math.pi / plates.CIRCLE_SEGMENTS) + HANG_RIM_MM
        self.tab_centre_y = self.outer[1] + self.tab_r
        # feet: centred on the side walls, clear of the rabbet
        self.foot_t = min(FOOT_MAX_MM, self.wall - 2.0 * FOOT_INSET_MM)
        self.foot_x = (self.rabbet[0] / 2.0 + self.outer[0] / 2.0) / 2.0
        tan = math.tan(math.radians(EASEL_LEAN_DEG))
        # (y, z): from the back bottom edge along the table to the back of the foot, up to its top, back to the frame
        self.foot_profile = [(0.0, 0.0), (FOOT_DEPTH_MM * tan, -FOOT_DEPTH_MM), (FOOT_HEIGHT_MM, 0.0), (FOOT_HEIGHT_MM, 1.0), (0.0, 1.0)]
        self.shift = self._shift()

    # ----------------------------------------------------------------------- placing the frame in the world

    def rotation(self) -> np.ndarray:
        """Frame coordinates → the piece: standing and leaning back for the easel, lying on its back for the hanger."""
        if not self.easel:
            return np.eye(3)
        a = math.radians(90.0 - self.lean)  # rotate about X: +y (up the picture) → up and back, +z → the viewer (−Y)
        c, s = math.cos(a), math.sin(a)
        return np.array([[1.0, 0.0, 0.0], [0.0, c, -s], [0.0, s, c]])

    def _extremes(self) -> np.ndarray:
        """Frame-coordinate points that bound the piece (outer box corners, the feet's corners, the tab's top)."""
        ow, oh = self.outer
        pts = [(sx * ow / 2.0, y, z) for sx in (-1, 1) for y in (0.0, oh) for z in (0.0, self.T)]
        if self.easel:
            pts += [(sx * (self.foot_x + self.foot_t / 2.0), y, z) for sx in (-1, 1) for y, z in self.foot_profile]
        else:
            pts += [(0.0, self.tab_centre_y + self.tab_r, z) for z in (0.0, self.T)]
            pts += [(sx * self.tab_r, self.tab_centre_y, z) for sx in (-1, 1) for z in (0.0, self.T)]
        return np.asarray(pts, dtype=np.float64)

    def _shift(self) -> np.ndarray:
        world = self._extremes() @ self.rotation().T
        lo, hi = world.min(axis=0), world.max(axis=0)
        return np.array([-(lo[0] + hi[0]) / 2.0, -(lo[1] + hi[1]) / 2.0, -lo[2]])

    def to_world(self, point: Sequence[float]) -> np.ndarray:
        return self.rotation() @ np.asarray(point, dtype=np.float64) + self.shift

    def axis(self, vector: Sequence[float]) -> np.ndarray:
        return self.rotation() @ np.asarray(vector, dtype=np.float64)

    # ----------------------------------------------------------------------- anchors

    def band(self, top: bool) -> tuple[float, float, float]:
        """``(y_centre, width, height)`` of a rail's band in frame coordinates."""
        ow, oh = self.outer
        h = self.border - 2.0 * ANCHOR_MARGIN_MM
        w = ow - 2.0 * ANCHOR_MARGIN_MM
        y = oh - self.border / 2.0 if top else self.border / 2.0
        return y, w, h

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        u, v, n = self.axis([1, 0, 0]), self.axis([0, 1, 0]), self.axis([0, 0, 1])
        frames = {}
        for anchor_id, top in (("border", True), ("base_front", False)):
            y, w, h = self.band(top)
            frames[anchor_id] = AnchorFrame(self.to_world([0.0, y, self.T]), u, v, n, size_mm=(w, h))
        return frames

    # ----------------------------------------------------------------------- the solid

    def part(self) -> "cad.Part":
        ow, oh = self.outer
        face = _rect(ow, oh, 0.0).difference(_rect(self.window[0], self.window[1], self.border))
        body = cad.prism(face, self.T)
        rabbet = cad.translate(cad.prism(_rect(self.rabbet[0], self.rabbet[1], self.border - self.lip_band), RABBET_DEPTH_MM + 1.0), dz=-1.0)
        body = cad.cut(body, [rabbet])
        if self.easel:
            profile = Polygon(self.foot_profile)
            feet = [
                cad.translate(cad.prism(profile, self.foot_t, cad.Plane.YZ), dx=sx * self.foot_x - self.foot_t / 2.0)
                for sx in (-1, 1)
            ]
            body = cad.union([body, *feet])
        else:
            tab = plates.disc(0.0, self.tab_centre_y, self.tab_r).union(box(-self.tab_r, oh - 1.0, self.tab_r, self.tab_centre_y))
            body = cad.union([body, cad.prism(plates.largest_polygon(tab), self.T)])
            hole = cad.translate(cad.prism(plates.hole(0.0, self.tab_centre_y, self.hole_cut), self.T + 2.0), dz=-1.0)
            body = cad.cut(body, [hole])
        return body


PARAMS = {
    "orientation": Param("enum", "Orientation", "portrait", options=ORIENTATIONS, group="Shape", description="For a 4 × 6 in photo."),
    "border_mm": Param(
        "number", "Border", 18.0, "mm", 12, 25, 0.5, handle=True, group="Size",
        description="Width of the frame's face round the window.",
    ),
    "stand": Param(
        "enum", "Stand", "easel", options=STANDS, group="Stand",
        description="easel: feet at the back so it stands on a table; hanger: a tab with a hole for a nail.",
    ),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class PhotoFrameStd(Template):
    id = "photo_frame_std"
    version = 1
    family = "photo_frame"
    name = "Chaukhat photo frame"
    description = (
        "A frame for a 4 × 6 in photo behind a clear acrylic pane, standing on its own feet or hanging on a "
        "wall. A motif or a name can be cut into the top rail and a line of text into the bottom rail."
    )
    environment = "teak_table_candlelight"
    params = PARAMS
    anchors = (
        Anchor(
            "border", "Top rail", "planar", max_text_height_mm=_size("border")[1], size_mm=_size("border"),
            bleed_mm=BLEED_MM, accepts=("motif", "emboss_text"), max_relief_mm=MAX_RELIEF_MM,
        ),
        Anchor(
            "base_front", "Bottom rail", "planar", max_text_height_mm=_size("base_front")[1], size_mm=_size("base_front"),
            bleed_mm=BLEED_MM, accepts=("emboss_text",), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    style_variants = ()
    features_supported = ("emboss_text", "motif")
    hardware = (HardwareRef(PANE_SKU, 1),)
    min_feature_mm = 0.8

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        """Names and motifs are cut in (the frame prints face down). How deep is capped by ``max_relief_mm``,
        which leaves at least ``MIN_SKIN_MM`` of the lip over the rabbet, so no skin check is needed here."""
        for index, feature in enumerate(features or []):
            ftype = feature.get("type")
            if ftype not in ("emboss_text", "motif"):
                continue
            mode = feature.get("mode", "emboss" if ftype == "emboss_text" else "deboss")
            if mode != "deboss":
                noun = "name" if ftype == "emboss_text" else "motif"
                raise UnsupportedFeature(
                    f"On a Chaukhat frame the {noun} is cut into the face, not raised (the frame prints face down); "
                    "choose cut-in",
                    {"feature": index, "mode": mode, "needs": "deboss", "key": f"features[{index}].mode"},
                )

    @classmethod
    def build_body(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        layout = Layout(params)
        part = layout.part()
        if layout.easel:
            part = cad.rotate_x(part, 90.0 - layout.lean)
        mesh = cad.to_trimesh(part)
        mesh.apply_translation(layout.shift)
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
        ww, wh = L.window
        stand = (
            f"The two feet at the back stand it leaning at {EASEL_LEAN_DEG:g}°."
            if L.easel
            else "Hang it from a nail or a picture hook through the hole in the top tab."
        )
        return (
            f"A {L.orientation} Chaukhat frame for a 4 × 6 in photo, with a {L.border:g} mm border and a "
            f"{ww:g} × {wh:g} mm window; the rabbet behind it is {RABBET_DEPTH_MM:g} mm deep. Print it face down "
            "(the front on the bed), no supports. Clean the 4 × 6 in acrylic pane, then set the pane, the photo and "
            f"a card backing into the rabbet from the back and tape the backing in along its edges. {stand}"
        )
