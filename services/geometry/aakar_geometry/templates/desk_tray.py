"""``desk_tray@1`` - Tarteeb: an open desk tray with dividers and a phone slot; a name or motif sits on the front wall.

Coordinate frame (mm): X across the front (the viewer's right when facing the tray), Y depth (−Y is the front,
+Y the back wall with the phone slot), Z up. The tray stands on its floor at Z = 0, is centred in X and Y, and is
printed open side up as modelled: no supports, no bridges.

Pieces
  shell     a ``width_mm`` × ``depth_mm`` rounded rectangle (outside corners ``OUTER_CORNER_MM``), ``height_mm`` tall,
            hollowed from the floor up: walls ``WALL_MM``, floor ``FLOOR_MM``, inside corners ``INNER_CORNER_MM``.
  dividers  ``compartments`` − 1 walls of ``WALL_MM`` running front to back, splitting the inside evenly across X;
            they stand flush with the rim and stop over the phone ledge's front rail when the slot is on.
  ledge     (``phone_slot``) a block ``LEDGE_D_MM`` deep and ``LEDGE_H_MM`` tall along the inside of the back wall,
            with a ``SLOT_W_MM`` groove ``SLOT_DEPTH_MM`` deep along its top, leaning back ``SLOT_TILT_DEG``: a phone
            stands on the groove's floor and leans on the groove's back face. ``RAIL_MM`` of plastic stays in front
            of the groove and ``BACK_RAIL_MM`` behind it, enough that the phone's lean line, carried on up from the
            groove's back face, passes ``RIM_CLEAR_MM`` in front of the rim's inner edge even in the tallest tray
            (``Layout.rim_clearance``): the groove holds the phone, never the rim.
            A phone leaning in the groove loads the back of the tray, so the slot needs a tray at least
            ``SLOT_MIN_DEPTH_MM`` deep and ``SLOT_MIN_HEIGHT_MM`` tall (``validate_combination``): at that smallest
            corner a 170 g, 150 mm phone leaning the full tilt keeps the combined centre of gravity more than 5 mm
            inside the back edge (the test measures it on the built solid).

Anchor
  front  the outer front wall (normal −Y): ``ANCHOR_MARGIN_MM`` inside the straight part of the wall, between the
         rounded corners: a name (Naam) and/or a motif (Buti). A cut-in (deboss) name or motif must leave
         ``plates.MIN_SKIN_MM`` of the ``WALL_MM`` wall behind it (``validate_content``); the wall is fixed, so the
         refusal names the wall (``WALL_KEY``) and the content's depth, never the Depth slider.
No hardware.
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

WALL_MM = 2.4  # six perimeters on a 0.4 mm nozzle: stiff enough for a tray that is picked up by one end
FLOOR_MM = 2.4
OUTER_CORNER_MM = 6.0
INNER_CORNER_MM = 3.0
LEDGE_H_MM = 14.0  # above the floor; always under the lowest rim a slot is allowed (SLOT_MIN_HEIGHT_MM)
SLOT_W_MM = 12.0  # across the groove, measured square to its lean: a phone in a case is up to 11 mm thick
SLOT_DEPTH_MM = 10.0  # from the ledge top down to the groove's flat floor
SLOT_TILT_DEG = 15.0  # the groove leans back so the phone rests against its back face
RAIL_MM = 2.4  # plastic kept in front of the groove, at its floor
MAX_HEIGHT_MM = 45.0  # the height_mm ceiling (PARAMS); the back rail is sized against it
RIM_CLEAR_MM = 2.0  # how far in front of the rim's inner edge the phone's lean line passes in the tallest tray
# plastic behind the groove at the ledge top: the groove's back face carried on up at SLOT_TILT_DEG must still be
# inside the back wall at the MAX_HEIGHT_MM rim (so the phone leans on the groove, not the rim) with RIM_CLEAR_MM spare
BACK_RAIL_MM = 10.0
assert BACK_RAIL_MM >= (MAX_HEIGHT_MM - FLOOR_MM - LEDGE_H_MM) * math.tan(math.radians(SLOT_TILT_DEG)) + RIM_CLEAR_MM
# front rail + the leaning groove's footprint (its floor plus the run of its lean through the ledge) + back rail
LEDGE_D_MM = RAIL_MM + SLOT_W_MM / math.cos(math.radians(SLOT_TILT_DEG)) + SLOT_DEPTH_MM * math.tan(math.radians(SLOT_TILT_DEG)) + BACK_RAIL_MM
SLOT_MIN_DEPTH_MM = 90.0  # a slot tray this deep and tall does not tip with a 170 g phone leaning the full tilt
SLOT_MIN_HEIGHT_MM = 28.0
OVERLAP_MM = 1.0  # pieces reach this far into their neighbours so no boolean meets a coplanar face
MIN_COMPARTMENT_MM = 25.0  # a pocket narrower than this holds nothing but a pen
ANCHOR_MARGIN_MM = 1.5
BLEED_MM = 1.0
MAX_RELIEF_MM = 1.5
WALL_KEY = "front_wall_mm"  # names the fixed front wall in a refusal; no slider changes it


class Layout:
    """Derived dimensions shared by ``build_part``, ``validate_combination``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.W = float(p["width_mm"])
        self.D = float(p["depth_mm"])
        self.H = float(p["height_mm"])
        self.n = int(p["compartments"])
        self.slot = bool(p["phone_slot"])
        self.inner_w = self.W - 2.0 * WALL_MM
        self.inner_d = self.D - 2.0 * WALL_MM
        self.y_back = self.inner_d / 2.0  # inside face of the back wall
        self.y_ledge_front = self.y_back - LEDGE_D_MM  # the ledge's front face
        self.outline: Polygon = plates.rounded_rect(self.W, self.D, OUTER_CORNER_MM)
        self.inner: Polygon = plates.rounded_rect(self.inner_w, self.inner_d, INNER_CORNER_MM)

    @property
    def compartment_w(self) -> float:
        """Inside width of one compartment."""
        return (self.inner_w - (self.n - 1) * WALL_MM) / self.n

    @property
    def divider_xs(self) -> list[float]:
        """Centre lines of the dividers, symmetric about X = 0."""
        cw = self.compartment_w
        return [-self.inner_w / 2.0 + cw * i + WALL_MM * (i - 0.5) for i in range(1, self.n)]

    @property
    def open_depth(self) -> float:
        """Inside depth left in front of the phone ledge."""
        return self.inner_d - LEDGE_D_MM if self.slot else self.inner_d

    @property
    def ledge_top(self) -> float:
        return FLOOR_MM + LEDGE_H_MM

    @property
    def slot_floor(self) -> float:
        return self.ledge_top - SLOT_DEPTH_MM

    def cavity(self) -> Polygon:
        """The inside outline minus the divider strips (the strips run out past the front wall, and past the back
        wall too, or stop inside the ledge's front rail when the slot is on)."""
        y_end = self.y_ledge_front + OVERLAP_MM if self.slot else self.D
        cavity = self.inner
        for x in self.divider_xs:
            cavity = cavity.difference(box(x - WALL_MM / 2.0, -self.D, x + WALL_MM / 2.0, y_end))
        return cavity

    def slot_profile(self) -> tuple[Polygon, float]:
        """The ledge's section in the (Y, Z) plane with the groove cut out, and the Y of the groove's centre at its floor.

        The groove is a parallelogram: a flat floor ``SLOT_W_MM / cos(tilt)`` wide (so it is ``SLOT_W_MM`` across
        square to the lean) and sides leaning back by ``SLOT_TILT_DEG``, running out above the ledge top. The ledge
        reaches ``OVERLAP_MM`` into the floor and the back wall.
        """
        tan = math.tan(math.radians(SLOT_TILT_DEG))
        floor_w = SLOT_W_MM / math.cos(math.radians(SLOT_TILT_DEG))
        y0 = self.y_ledge_front + RAIL_MM  # the groove's front edge at its floor
        z0 = self.slot_floor
        rise = SLOT_DEPTH_MM + 5.0  # out through the ledge top
        groove = Polygon([(y0, z0), (y0 + floor_w, z0), (y0 + floor_w + rise * tan, z0 + rise), (y0 + rise * tan, z0 + rise)])
        # oversize at the back: build_part clips the ledge to the inside outline grown by OVERLAP_MM, so it follows the
        # rounded inside corners instead of poking a square corner out through the softened outside ones
        ledge = box(self.y_ledge_front, FLOOR_MM - OVERLAP_MM, self.y_back + 2.0 * OVERLAP_MM, self.ledge_top)
        return plates.largest_polygon(ledge.difference(groove)), y0 + floor_w / 2.0

    @property
    def back_rail(self) -> float:
        """Plastic between the groove's back edge at the ledge top and the inside of the back wall."""
        tan = math.tan(math.radians(SLOT_TILT_DEG))
        floor_w = SLOT_W_MM / math.cos(math.radians(SLOT_TILT_DEG))
        return LEDGE_D_MM - RAIL_MM - floor_w - SLOT_DEPTH_MM * tan

    @property
    def rim_clearance(self) -> float:
        """How far in front of the rim's inner edge the groove's back face passes, carried on up at the tilt to the
        rim: positive means a leaning phone touches the groove's back face and clears the rim."""
        return self.back_rail - (self.H - self.ledge_top) * math.tan(math.radians(SLOT_TILT_DEG))

    def lean_line(self, up_mm: float) -> tuple[float, float]:
        """(Y, Z) of the point ``up_mm`` along a leaning phone's back, from the groove's back edge at its floor."""
        _, y_slot = self.slot_profile()
        y_foot = y_slot + SLOT_W_MM / math.cos(math.radians(SLOT_TILT_DEG)) / 2.0
        tilt = math.radians(SLOT_TILT_DEG)
        return y_foot + up_mm * math.sin(tilt), self.slot_floor + up_mm * math.cos(tilt)

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        flat_w = self.W - 2.0 * OUTER_CORNER_MM  # the straight part of the front wall
        return {
            "front": AnchorFrame(
                np.array([0.0, -self.D / 2.0, self.H / 2.0]), [1, 0, 0], [0, 0, 1], [0, -1, 0],
                size_mm=(flat_w - 2.0 * ANCHOR_MARGIN_MM, self.H - 2.0 * ANCHOR_MARGIN_MM),
            ),
        }


PARAMS = {
    "width_mm": Param(
        "number", "Width", 160.0, "mm", 100, 250, 5, handle=True, group="Size",
        description="Across the front; the name goes on this side.",
    ),
    "depth_mm": Param(
        "number", "Depth", 100.0, "mm", 70, 140, 5, group="Size",
        description=f"Front to back; never more than the width, and at least {SLOT_MIN_DEPTH_MM:g} mm with the phone slot.",
    ),
    "height_mm": Param(
        "number", "Height", 30.0, "mm", 22, MAX_HEIGHT_MM, 1, group="Size",
        description=f"At least {SLOT_MIN_HEIGHT_MM:g} mm with the phone slot.",
    ),
    "compartments": Param(
        "integer", "Compartments", 2, "count", 1, 3, 1, group="Shape",
        description="Dividers split the tray evenly from left to right.",
    ),
    "phone_slot": Param(
        "boolean", "Phone slot", True, group="Details",
        description=(
            "A groove in a ledge along the back wall; a phone stands in it and leans back. Needs a tray at least "
            f"{SLOT_MIN_DEPTH_MM:g} mm deep and {SLOT_MIN_HEIGHT_MM:g} mm tall."
        ),
    ),
}

_DEFAULT_FRAMES = Layout({k: p.default for k, p in PARAMS.items()}).anchor_frames()


def _size(anchor_id: str) -> tuple[float, float]:
    w, h = _DEFAULT_FRAMES[anchor_id].size_mm  # type: ignore[misc]
    return (round(w, 1), round(h, 1))


class DeskTray(Template):
    id = "desk_tray"
    version = 1
    family = "desk_organizer"
    name = "Tarteeb desk tray"
    description = (
        "An open tray with rounded corners, up to three compartments and a slot along the back where your phone "
        "leans. Your name or a motif sits on the front wall."
    )
    environment = "desk_oak"
    params = PARAMS
    anchors = (
        Anchor(
            "front", "Front wall", "planar", size_mm=_size("front"), bleed_mm=BLEED_MM,
            accepts=("emboss_text", "motif"), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.6, max_overhang_deg=45, bed_mm=(250, 250, 250))
    features_supported = ("emboss_text", "motif")
    hardware: tuple[HardwareRef, ...] = ()
    min_feature_mm = 0.8

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        L = Layout(params)
        if L.D > L.W + 1e-9:
            raise ParamOutOfRange(
                ["depth_mm", "width_mm"],
                f"A tray {L.D:g} mm deep and {L.W:g} mm wide would be deeper than it is wide; the front, with the name, is the long side, so make it wider or shallower.",
                {"depth_mm": L.D, "width_mm": L.W},
            )
        if L.compartment_w < MIN_COMPARTMENT_MM - 1e-9:
            raise ParamOutOfRange(
                ["compartments", "width_mm"],
                f"{L.n} compartments in a {L.W:g} mm tray would each be {L.compartment_w:.0f} mm wide; make it wider or use fewer compartments.",
                {"compartment_mm": round(L.compartment_w, 2), "min_compartment_mm": MIN_COMPARTMENT_MM},
            )
        if L.slot and (L.D < SLOT_MIN_DEPTH_MM - 1e-9 or L.H < SLOT_MIN_HEIGHT_MM - 1e-9):
            raise ParamOutOfRange(
                ["depth_mm", "height_mm", "phone_slot"],
                f"A phone slot needs a tray at least {SLOT_MIN_DEPTH_MM:g} mm deep and {SLOT_MIN_HEIGHT_MM:g} mm tall "
                f"so the tray does not tip; this one is {L.D:g} mm deep and {L.H:g} mm tall. Make it bigger or turn the phone slot off.",
                {"depth_mm": L.D, "height_mm": L.H, "min_depth_mm": SLOT_MIN_DEPTH_MM, "min_height_mm": SLOT_MIN_HEIGHT_MM},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        # The front wall is a fixed WALL_MM, not a slider (Depth only moves the back wall away), so a refusal names the
        # wall (WALL_KEY) and the content's depth, and its advice is a shallower cut or raised content, never "thicker".
        try:
            plates.check_skin(features, anchors=("front",), thickness_mm=WALL_MM, noun="front wall", thickness_key=WALL_KEY)
        except ParamOutOfRange as exc:
            d = exc.detail
            content = plates._cut_nouns(features, ("front",))
            advice = (
                f"keep the cut-in to {d['max_cut_in_mm']:.1f} mm or less, or raise the {content} instead of cutting it in"
                if d["max_cut_in_mm"] >= 0.2
                else f"raise the {content} instead of cutting it in"
            )
            raise ParamOutOfRange(
                exc.keys,
                f"Cut in {d['cut_in_mm']:g} mm, the {content} would leave only {max(d['skin_mm'], 0.0):.1f} mm of the "
                f"{WALL_MM:g} mm front wall behind it (at least {d['min_skin_mm']:g} mm is needed); {advice}.",
                {k: v for k, v in d.items() if k not in ("keys", "min_thickness_mm")} | {"wall_mm": WALL_MM},
            ) from None

    @classmethod
    def build_part(cls, params: Mapping[str, Any]) -> "cad.Part":
        L = Layout(params)
        shell = cad.prism(L.outline, L.H)
        cavity = cad.translate(cad.prism(L.cavity(), L.H - FLOOR_MM + OVERLAP_MM), dz=FLOOR_MM)
        part = cad.cut(shell, [cavity])
        if L.slot:
            profile, _ = L.slot_profile()
            width = L.inner_w + 4.0 * OVERLAP_MM  # oversize, then clipped to OVERLAP_MM into the side and back walls
            ledge = cad.translate(cad.prism(profile, width, cad.Plane.YZ), dx=-width / 2.0)
            ledge = cad.intersect(ledge, cad.prism(L.inner.buffer(OVERLAP_MM), L.H))
            part = cad.union([part, ledge])
        return part

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
        pockets = {1: "one open compartment", 2: "two equal compartments", 3: "three equal compartments"}[L.n]
        slot = (
            f", and a phone slot along the back: a {SLOT_W_MM:g} mm groove in the rear ledge, leaning back "
            f"{SLOT_TILT_DEG:g}°; a phone stands on the groove's floor and leans on the groove's back face, clear of the rim"
            if L.slot
            else ""
        )
        return (
            f"A Tarteeb desk tray, {L.W:g} × {L.D:g} mm and {L.H:g} mm tall, with {pockets}{slot}. Printed open side up "
            f"as modelled, no supports; walls and floor are {WALL_MM:g} mm. Felt pads under the four corners are optional, "
            "for a desk that marks easily."
        )
