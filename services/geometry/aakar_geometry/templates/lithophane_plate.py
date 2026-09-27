"""``lithophane_plate@1`` - Roshni: a photo night light. The photo becomes the plate itself.

A lithophane is a thin white plate whose thickness follows the photo: held in front of a light, the thin
parts glow and the thick parts stay dark, so the picture appears. The darkest pixels are printed
``max_mm`` thick and the lightest ``min_mm`` (``invert`` on the photo swaps them; the feature handles it).
The customer's photo arrives as ``relief_image`` in ``lithophane`` mode and the features stage hands its
heightmap to ``lithophane`` (``Template.lithophane``), which builds the plate from it.

Coordinate frame (mm): Z up, resting on Z = 0, X across (the viewer's right), centred in X and Y.

``stand: night_light`` (default): the plate stands upright with the photo facing the viewer at −Y, its
bottom frame edge set ``SEAT_MM`` into a base and fused to it: **one print**, printed standing as
modelled (the way lithophanes print best: the photo's thickness is drawn by the nozzle, not stepped by
the layers), no supports. Behind the plate a round pocket holds the 70 mm USB LED puck
(``led_base_usb``) face up, lighting the plate from behind and below, with a notch in the back wall for
its cable.
``stand: none``: the plate alone, lying on its flat back (X across, Y up the picture, the photo facing
+Z), to stand in a window or a frame the customer already has. It prints as it lies.

Pieces
  plate  ``size`` square 100 × 100 or portrait 104 × 140 mm (MakerLab's lithophane format). A
         ``BORDER_MM`` frame, ``max_mm`` + ``FRAME_PROUD_MM`` thick, stands proud of the photo on the front
         (the back is flat) and bevels down to it over ``BEVEL_MM``. Inside it the photo area: a flat
         ``max_mm`` slab in the bare body (``build_body``), the photo's heightfield once the photo is set
         (``lithophane``); a photo that does not fill the area (``fit: contain``) is edged at ``max_mm``
         (dark), like the frame. The lightest pixel is ``LIGHT_ALLOWANCE_MM`` thicker than ``min_mm`` (see
         the constant). The plate is one closed heightfield surface over frame, bevel and photo
         (``features.heightfield.grid_solid``), so no boolean touches the photo; the photo is sampled at its
         own spacing (≈ 0.25–0.33 mm), at most ``MAX_PLATE_SAMPLES`` samples.
  base   (night light) ``BASE_DEPTH_MM`` deep, ``BASE_HEIGHT_MM`` tall, ``SIDE_MARGIN_MM`` wider than the
         plate on each side. The plate stands ``FRONT_LIP_MM`` behind its front edge; the puck pocket
         (70 mm + 0.6 mm, ``POCKET_DEPTH_MM`` deep) sits ``BACK_WALL_MM`` inside its back edge, and a
         ``CABLE_NOTCH_MM`` notch runs from the pocket out through the back wall.

Anchor
  plate  the photo area inside the bevel, on the front of the bare plate (the ``max_mm`` plane; normal +Z
         lying, −Y standing), u = the viewer's right, v = up the picture, bleed 0 (the photo runs to the
         frame). It takes only ``relief_image`` in ``lithophane`` mode: ``validate_content`` refuses a spec
         without the photo, and a photo raised or cut into the plate (``emboss`` / ``deboss``). The
         feature's ``relief_mm`` does not apply: ``min_mm`` and ``max_mm`` set the plate.
The family (``lithophane``, families.json) allows only ``basic_white``: light must pass through the plate.
"""

from __future__ import annotations

from functools import lru_cache
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely.geometry import box

from .. import cad
from ..errors import InvalidSpec, ParamOutOfRange, UnsupportedFeature
from ..features.booleans import union
from ..features.frames import AnchorFrame
from ..features.heightfield import grid_solid
from . import plates
from .base import Anchor, HardwareRef, Param, Template, TemplateConstraints

SIZES: dict[str, tuple[float, float]] = {"square": (100.0, 100.0), "portrait": (104.0, 140.0)}
STANDS = ("none", "night_light")
BORDER_MM = 4.0  # frame width around the photo
FRAME_PROUD_MM = 1.0  # the frame stands this much proud of the photo's darkest parts, on the front
BEVEL_MM = 0.5  # the frame's inner edge slopes down to the photo over this width
MIN_RANGE_MM = 1.2  # between the thinnest and the thickest parts, or the photo has no light and dark to show
# The printability check measures a wall from 0.01 mm inside the skin, so a light area exactly min_mm thick
# would read a hair under a min_mm wall limit and fail it; 0.02 mm is far below what a 0.1 mm layer can show.
LIGHT_ALLOWANCE_MM = 0.02
MAX_PLATE_SAMPLES = 120_000  # photo samples over the plate (≈ 240k triangles): a 400 × 300 relief's worth

LED_SKU = "led_base_usb"
PUCK_D_MM = 70.0  # the USB LED puck (led_base_usb)
PUCK_CLEARANCE_MM = 0.6
POCKET_DEPTH_MM = 8.0
BASE_HEIGHT_MM = 12.0
BASE_DEPTH_MM = 88.0
SEAT_MM = 3.0  # the plate's bottom frame edge sits this deep in the base (less than the frame: the photo stands clear)
FRONT_LIP_MM = 5.0  # base in front of the plate
BACK_WALL_MM = 4.0  # base behind the puck pocket
SIDE_MARGIN_MM = 6.0  # base beyond the plate on each side
CABLE_NOTCH_MM = 12.0


class Layout:
    """Derived dimensions shared by ``build_body``, ``lithophane``, ``anchor_frame`` and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.size = str(p["size"])
        self.W, self.H = SIZES[self.size]
        self.t_min = float(p["min_mm"])
        self.t_max = float(p["max_mm"])
        self.stand = str(p["stand"])
        self.t_frame = self.t_max + FRAME_PROUD_MM
        # the photo area, inside the frame and its bevel (plate coordinates: centred, back at z = 0)
        self.inner_w = self.W - 2.0 * (BORDER_MM + BEVEL_MM)
        self.inner_h = self.H - 2.0 * (BORDER_MM + BEVEL_MM)
        # night light: the plate's front, its back and the bottom of its seat in the base
        self.base_w = self.W + 2.0 * SIDE_MARGIN_MM
        self.y_front = -BASE_DEPTH_MM / 2.0 + FRONT_LIP_MM
        self.y_back = self.y_front + self.t_frame
        self.z_seat = BASE_HEIGHT_MM - SEAT_MM
        self.pocket_y = BASE_DEPTH_MM / 2.0 - BACK_WALL_MM - (PUCK_D_MM + PUCK_CLEARANCE_MM) / 2.0

    @property
    def standing(self) -> bool:
        return self.stand == "night_light"

    @property
    def height(self) -> float:
        """Overall height of the piece."""
        return self.z_seat + self.H if self.standing else self.t_frame

    def plate_frame(self) -> AnchorFrame:
        """The photo area on the front of the bare plate (its ``max_mm`` plane)."""
        size = (self.inner_w, self.inner_h)
        if self.standing:
            origin = np.array([0.0, self.y_back - self.t_max, self.z_seat + self.H / 2.0])
            return AnchorFrame(origin, [1, 0, 0], [0, 0, 1], [0, -1, 0], size_mm=size)
        return AnchorFrame(np.array([0.0, 0.0, self.t_max]), [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=size)

    def plate(self, xs_in: np.ndarray, ys_in: np.ndarray, photo: np.ndarray) -> trimesh.Trimesh:
        """The plate in its own frame (centred, flat back on z = 0, front up): the frame all round, and
        ``photo`` (thickness in mm, ``(len(ys_in), len(xs_in))``) over the photo area's samples."""
        hw, hh = self.W / 2.0, self.H / 2.0
        xs = np.concatenate([[-hw, -hw + BORDER_MM], xs_in, [hw - BORDER_MM, hw]])
        ys = np.concatenate([[-hh, -hh + BORDER_MM], ys_in, [hh - BORDER_MM, hh]])
        top = np.full((len(ys), len(xs)), self.t_frame)
        top[2:-2, 2:-2] = photo
        return grid_solid(xs, ys, top)

    def bare_plate(self) -> trimesh.Trimesh:
        xs = np.array([-self.inner_w / 2.0, self.inner_w / 2.0])
        ys = np.array([-self.inner_h / 2.0, self.inner_h / 2.0])
        return self.plate(xs, ys, np.full((2, 2), self.t_max))

    def photo_plate(self, relief: Any) -> trimesh.Trimesh:
        """The plate with the photo: darkest pixel ``max_mm``, lightest ``min_mm`` (+ the allowance)."""
        heights = np.asarray(relief.heights, dtype=np.float64)
        lo, hi = float(heights.min()), float(heights.max())
        light = (heights - lo) / (hi - lo)  # 0 = darkest, 1 = lightest (relief_heightmap refuses a flat photo)
        # the photo's own spacing, but no more samples over the whole area than a 400 × 300 photo relief
        cell = max(float(relief.cell_mm), float(np.sqrt(self.inner_w * self.inner_h / MAX_PLATE_SAMPLES)))
        nx = max(2, int(round(self.inner_w / cell)) + 1)
        ny = max(2, int(round(self.inner_h / cell)) + 1)
        xs = np.linspace(-self.inner_w / 2.0, self.inner_w / 2.0, nx)
        ys = np.linspace(-self.inner_h / 2.0, self.inner_h / 2.0, ny)
        sampled = _sample(light, float(relief.cell_mm), xs, ys)  # outside the photo (letterbox): 0, dark like the frame
        thin = self.t_min + LIGHT_ALLOWANCE_MM
        return self.plate(xs, ys, self.t_max - sampled * (self.t_max - thin))

    def place(self, plate: trimesh.Trimesh) -> trimesh.Trimesh:
        """Plate coordinates → the piece: back on the anchor frame's ``max_mm`` offset."""
        return self.plate_frame().offset(-self.t_max).place(plate)


def _sample(values: np.ndarray, cell: float, xs: np.ndarray, ys: np.ndarray) -> np.ndarray:
    """Bilinear samples of a centred grid (``cell`` apart) at ``(xs, ys)``; 0 outside it."""
    from scipy.ndimage import map_coordinates

    ny, nx = values.shape
    fx = xs / cell + (nx - 1) / 2.0
    fy = ys / cell + (ny - 1) / 2.0
    gx, gy = np.meshgrid(fx, fy)
    tol = 1e-6
    inside = (gx >= -tol) & (gx <= nx - 1 + tol) & (gy >= -tol) & (gy <= ny - 1 + tol)
    coords = [np.clip(gy, 0.0, ny - 1.0), np.clip(gx, 0.0, nx - 1.0)]
    out = map_coordinates(np.asarray(values, dtype=np.float64), coords, order=1, mode="nearest")
    return np.where(inside, out, 0.0)


@lru_cache(maxsize=None)
def _base_mesh(size: str) -> trimesh.Trimesh:
    """The night-light base for a plate ``size`` wide (a copy is handed out; the plate does not change it)."""
    W, _ = SIZES[size]
    w, d, h = W + 2.0 * SIDE_MARGIN_MM, BASE_DEPTH_MM, BASE_HEIGHT_MM
    block = cad.prism(box(-w / 2.0, -d / 2.0, w / 2.0, d / 2.0), h)
    pocket_d = PUCK_D_MM + PUCK_CLEARANCE_MM
    pocket_y = d / 2.0 - BACK_WALL_MM - pocket_d / 2.0
    floor = h - POCKET_DEPTH_MM
    pocket = cad.translate(cad.prism(plates.hole(0.0, pocket_y, pocket_d, 192), POCKET_DEPTH_MM + 1.0), dz=floor)
    notch = cad.translate(
        cad.prism(box(-CABLE_NOTCH_MM / 2.0, pocket_y, CABLE_NOTCH_MM / 2.0, d / 2.0 + 1.0), POCKET_DEPTH_MM + 1.0), dz=floor
    )
    mesh = cad.to_trimesh(cad.cut(block, [pocket, notch]))
    mesh.apply_translation([0.0, 0.0, -float(mesh.bounds[0][2])])
    return mesh


PARAMS = {
    "size": Param(
        "enum", "Plate", "portrait", options=tuple(SIZES), group="Size",
        description="Square 100 × 100 mm or portrait 104 × 140 mm.",
    ),
    "min_mm": Param(
        "number", "Lightest parts", 0.8, "mm", 0.8, 1.8, 0.1, group="Light",
        description="Plate thickness where the photo is lightest: thinner glows brighter.",
    ),
    "max_mm": Param(
        "number", "Darkest parts", 3.0, "mm", 2.0, 3.0, 0.1, group="Light",
        description="Plate thickness where the photo is darkest; at least 1.2 mm more than the lightest parts.",
    ),
    "stand": Param(
        "enum", "Stand", "night_light", options=STANDS, group="Stand",
        description="night_light: the plate stands in a base with the LED light; none: the plate alone.",
    ),
}

_DEFAULTS = {k: p.default for k, p in PARAMS.items()}
_DEFAULT_LAYOUT = Layout(_DEFAULTS)

MISSING_PHOTO = "Add the photo that becomes your Roshni night light"


class LithophanePlate(Template):
    id = "lithophane_plate"
    version = 1
    family = "lithophane"
    name = "Roshni photo night light"
    description = (
        "Your photo as a white lithophane plate: thin where the photo is light, thick where it is dark, so "
        "the picture glows when lit from behind. It stands in a base with a warm LED light, or comes as the "
        "plate alone."
    )
    environment = "teak_table_candlelight"
    params = PARAMS
    anchors = (
        Anchor(
            "plate", "Photo plate", "planar",
            size_mm=(round(_DEFAULT_LAYOUT.inner_w, 1), round(_DEFAULT_LAYOUT.inner_h, 1)), bleed_mm=0.0,
            accepts=("relief_image",),
        ),
    )
    # the lightest parts of the photo are 0.8 mm: two lines of a 0.4 mm nozzle, the contract's thinnest wall
    constraints = TemplateConstraints(min_wall_mm=0.8, max_overhang_deg=55, bed_mm=(250, 250, 250))
    style_variants = ()
    features_supported = ("relief_image",)
    hardware = (HardwareRef(LED_SKU, 1),)  # the default night light; the plate alone ships without it
    min_feature_mm = 0.8

    @classmethod
    def hardware_for(cls, params: Mapping[str, Any]) -> list[dict[str, Any]]:
        return [{"sku": LED_SKU, "qty": 1}] if params.get("stand", "night_light") == "night_light" else []

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        lo, hi = float(params["min_mm"]), float(params["max_mm"])
        if hi - lo < MIN_RANGE_MM - 1e-9:
            raise ParamOutOfRange(
                ["min_mm", "max_mm"],
                (
                    f"The plate needs at least {MIN_RANGE_MM:g} mm between its lightest ({lo:g} mm) and darkest "
                    f"({hi:g} mm) parts to show the photo; make the lightest parts thinner or the darkest thicker."
                ),
                {"min_mm": lo, "max_mm": hi, "min_range_mm": MIN_RANGE_MM},
            )

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        photos = [(i, f) for i, f in enumerate(features or []) if f.get("type") == "relief_image"]
        if not photos:
            raise InvalidSpec(MISSING_PHOTO, {"template": cls.ref(), "needs": "one relief_image in lithophane mode on the plate anchor"})
        index, photo = photos[0]  # check_features allows one photo per anchor, and plate is the only anchor
        mode = photo.get("mode", "emboss")
        if mode != "lithophane":
            raise UnsupportedFeature(
                "In a Roshni night light your photo becomes the glowing plate itself, so it can't be raised or cut "
                "into it; choose the night-light (lithophane) photo",
                {"feature": index, "mode": mode, "needs": "lithophane"},
            )

    @classmethod
    def build_body(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        """The bare piece: the frame around a flat ``max_mm`` photo area (the photo replaces it)."""
        layout = Layout(params)
        return cls._assemble(layout, layout.bare_plate())

    @classmethod
    def lithophane(cls, body, params, frame, anchor, feature, relief) -> trimesh.Trimesh:
        """The photo's heightmap → the plate (the bare body is rebuilt around it, not cut)."""
        layout = Layout(params)
        return cls._assemble(layout, layout.photo_plate(relief))

    @classmethod
    def _assemble(cls, layout: Layout, plate: trimesh.Trimesh) -> trimesh.Trimesh:
        placed = layout.place(plate)
        if layout.standing:
            placed = union(_base_mesh(layout.size).copy(), placed)
        placed.apply_translation([0.0, 0.0, -float(placed.bounds[0][2])])
        return placed

    @classmethod
    def anchor_frame(cls, anchor_id: str, params: Mapping[str, Any]) -> AnchorFrame:
        if anchor_id != "plate":
            raise NotImplementedError(f"{cls.ref()} does not place anchor {anchor_id!r}")
        return Layout(params).plate_frame()

    @classmethod
    def karigar_note(cls, params: Mapping[str, Any]) -> str:
        L = Layout(params)
        shape = f"{L.W:g} × {L.H:g} mm {L.size}"
        plate = (
            f"A {shape} Roshni lithophane plate, {L.t_min:g} mm where the photo is lightest to {L.t_max:g} mm where "
            f"it is darkest, in a {BORDER_MM:g} mm frame."
        )
        settings = "Print it in white at 0.1–0.12 mm layers with 100% infill, so the light passes evenly"
        if L.standing:
            return (
                f"{plate} It stands fused into its base: one print, printed standing as modelled, no supports. "
                f"{settings}. Seat the 70 mm USB LED puck face up in the pocket behind the plate, its cable out "
                "through the back notch, before packing."
            )
        return f"{plate} {settings}, lying on its flat back."
