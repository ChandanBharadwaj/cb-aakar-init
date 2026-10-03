"""``plinth_round@1`` - Pratima: the customer's own 3D form (Roop) standing on a plinth, with a name on its front.

Coordinate frame (mm): X across, Y depth (−Y is the front the reader faces, where the name is), Z up.
The plinth sits on Z = 0 and its bounding box is centred in X and Y.

Pieces
  plinth  ``shape`` ``round`` (a disc ``diameter_mm`` across with a flat front facet ``FLAT_SHARE`` of the
          diameter wide, so the name has a flat face: a D-shaped footprint) or ``rounded_square``
          (``diameter_mm`` square, corners rounded ``CORNER_SHARE`` of it), ``height_mm`` tall.
  form    the customer's ``hero_mesh`` on the ``top`` anchor: repaired, scaled (``fit: contain`` into the
          anchor's bounds, or ``longest`` to ``longest_mm``, never clamped), seated centred on the top face and
          fused to it (``features.hero_mesh``).

Anchors
  top         volume: origin at the footprint's centroid on the top face (normal +Z), ``bounds_mm`` =
              ``diameter_mm`` × ``diameter_mm`` × ``TOP_HEIGHT_MM`` (180), so plinth and form together stay
              under the family's 200 mm envelope (at most 20 + 180 − 0.5 mm tall).
  base_front  surface: the plinth's flat front face, ``FRONT_MARGIN_MM`` inside its sides and
              ``EDGE_MARGIN_MM`` inside its top and bottom (normal −Y, u = the viewer's right, v = up): a name
              (Naam), raised or cut up to 1.5 mm. A plinth under 7 mm leaves less than 4 mm for the letters and
              a name there is refused as too long (the lettering's own rule).

Stability (``build``, after the form is fused): with the inspect service's own check
(``aakar_inspect.checks.stability``) the combined centre of gravity must sit at least
``MIN_TIPPING_MARGIN_MM`` inside the plinth's footprint, and the piece must stand a ``MIN_TIP_ANGLE_DEG``
nudge (the angle it can lean before its centre of gravity passes the footprint's edge). A form that
leans off the plinth, or a tall form on a narrow plinth, is refused (``param_out_of_range``) naming the
narrowest plinth that would hold it, worked out rather than guessed (``_wider_plinth``: a wider plinth is
heavier, and a form fitted with ``contain`` grows with its room), or asking for a smaller form when no
plinth on offer would. A plinth alone (or with only a name) always stands; the form is not required.
No hardware.
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

import numpy as np
import trimesh
from shapely import affinity
from shapely.geometry import Point, Polygon, box

from .. import cad
from ..errors import ParamOutOfRange
from ..features import hero_mesh
from ..features.frames import AnchorFrame
from . import plates
from .base import Anchor, Param, Template, TemplateConstraints

SHAPES = ("round", "rounded_square")
FLAT_SHARE = 0.6  # the round plinth's flat front facet, as a share of its diameter
CORNER_SHARE = 0.15  # corner radius of the rounded-square plinth, as a share of its side
TOP_HEIGHT_MM = 180.0  # the form's height budget: 20 mm plinth + 180 mm form stays inside the 200 mm envelope
FRONT_MARGIN_MM = 1.5  # kept clear between a name and the ends of the flat front
EDGE_MARGIN_MM = 0.5  # ... and its top and bottom edges
FRONT_BLEED_MM = 0.5
MAX_RELIEF_MM = 1.5
MIN_TIPPING_MARGIN_MM = 5.0  # aakar_inspect Constraints.min_tipping_margin_mm: below this is "tippy"
MIN_TIP_ANGLE_DEG = 10.0  # the piece must stand a 10° nudge before its weight passes the plinth's edge
SUGGEST_STEP_MM = 5.0


def _outline(shape: str, D: float) -> Polygon:
    """The footprint before centring: the round plinth is centred on its circle, the square on itself."""
    if shape == "round":
        R = D / 2.0
        disc = plates.disc(0.0, 0.0, R, 192)
        flat_y = -R * math.sqrt(1.0 - FLAT_SHARE**2)  # the chord FLAT_SHARE·D wide
        return plates.largest_polygon(disc.intersection(box(-R - 1.0, flat_y, R + 1.0, R + 1.0)))
    if shape == "rounded_square":
        return plates.rounded_rect(D, D, CORNER_SHARE * D)
    raise ValueError(f"unknown shape {shape!r}")


class Layout:
    """Derived dimensions shared by ``build_body``, ``anchor_frame``, the stability check and ``karigar_note``."""

    def __init__(self, p: Mapping[str, Any]):
        self.shape = str(p["shape"])
        self.D = float(p["diameter_mm"])
        self.h = float(p["height_mm"])
        raw = _outline(self.shape, self.D)
        x0, y0, x1, y1 = raw.bounds
        self.outline: Polygon = affinity.translate(raw, -(x0 + x1) / 2.0, -(y0 + y1) / 2.0)
        self.front_y = float(self.outline.bounds[1])
        # the flat front: where the outline meets its front edge
        edge = self.outline.intersection(box(-self.D, self.front_y - 1e-6, self.D, self.front_y + 1e-6))
        self.front_w = float(edge.bounds[2] - edge.bounds[0])
        c = self.outline.centroid
        self.centroid = (float(c.x), float(c.y))

    def anchor_frames(self) -> dict[str, AnchorFrame]:
        cx, cy = self.centroid
        front_size = (self.front_w - 2.0 * FRONT_MARGIN_MM, self.h - 2.0 * EDGE_MARGIN_MM)
        return {
            "top": AnchorFrame(np.array([cx, cy, self.h]), [1, 0, 0], [0, 1, 0], [0, 0, 1], bounds_mm=self.top_bounds),
            "base_front": AnchorFrame(
                np.array([0.0, self.front_y, self.h / 2.0]), [1, 0, 0], [0, 0, 1], [0, -1, 0], size_mm=front_size
            ),
        }

    @property
    def top_bounds(self) -> tuple[float, float, float]:
        return (self.D, self.D, TOP_HEIGHT_MM)


def stands(outline: Polygon, cog: np.ndarray) -> tuple[bool, float, float]:
    """``(stands, margin, needed)``: the centre of gravity's distance inside the footprint against the larger of
    ``MIN_TIPPING_MARGIN_MM`` and what a ``MIN_TIP_ANGLE_DEG`` nudge needs at its height."""
    point = Point(float(cog[0]), float(cog[1]))
    margin = float(outline.exterior.distance(point)) * (1.0 if outline.covers(point) else -1.0)
    needed = max(MIN_TIPPING_MARGIN_MM, float(cog[2]) * math.tan(math.radians(MIN_TIP_ANGLE_DEG)))
    return margin >= needed - 1e-9, margin, needed


def _wider_plinth(mesh: trimesh.Trimesh, layout: "Layout", longest: bool) -> float | None:
    """The narrowest plinth (in ``SUGGEST_STEP_MM`` steps, up to the largest offered) that would hold this form,
    or None. The fused piece is split into the plinth (its outline × height) and the form; a wider plinth
    scales the plinth's mass with its area and its footprint about the centre, and a form fitted with
    ``contain`` grows with its room until its height reaches ``TOP_HEIGHT_MM`` (``longest`` keeps its size)."""
    top_z = layout.h - hero_mesh.EMBED_MM
    cx, cy = layout.centroid
    plinth_v = float(layout.outline.area) * layout.h
    total_v = float(mesh.volume)
    form_v = max(total_v - plinth_v, 1e-9)
    form_c = (np.asarray(mesh.center_mass) * total_v - np.array([cx, cy, layout.h / 2.0]) * plinth_v) / form_v
    above = mesh.vertices[mesh.vertices[:, 2] > layout.h + 1e-6]
    if len(above) == 0:  # pragma: no cover - a hero always stands above the top
        return None
    extent = np.ptp(above, axis=0)
    extent[2] = float(above[:, 2].max()) - top_z
    seat = np.array([cx, cy, top_z])
    largest = float(PARAMS["diameter_mm"].max or layout.D)
    candidate = math.floor(layout.D / SUGGEST_STEP_MM + 1.0) * SUGGEST_STEP_MM  # the next step up
    while candidate <= largest + 1e-9:
        k = candidate / layout.D
        r = 1.0 if longest else min(candidate / max(extent[0], 1e-9), candidate / max(extent[1], 1e-9), TOP_HEIGHT_MM / max(extent[2], 1e-9))
        seat_k = np.array([cx * k, cy * k, top_z])
        plinth_k, form_k = plinth_v * k * k, form_v * r**3
        cog = (np.array([cx * k, cy * k, layout.h / 2.0]) * plinth_k + (seat_k + r * (form_c - seat)) * form_k) / (plinth_k + form_k)
        if stands(affinity.scale(layout.outline, k, k, origin=(0.0, 0.0)), cog)[0]:
            return float(candidate)
        candidate += SUGGEST_STEP_MM
    return None


PARAMS = {
    "shape": Param("enum", "Shape", "round", options=SHAPES, group="Shape", description="Round with a flat front for the name, or a rounded square."),
    "diameter_mm": Param("number", "Size", 70.0, "mm", 40, 120, 1, handle=True, group="Size", description="Across the plinth."),
    "height_mm": Param("number", "Height", 12.0, "mm", 6, 20, 0.5, group="Size", description="Of the plinth; the name on its front needs at least 7 mm."),
}

_DEFAULT_LAYOUT = Layout({k: p.default for k, p in PARAMS.items()})
_DEFAULT_FRAMES = _DEFAULT_LAYOUT.anchor_frames()


class PlinthRound(Template):
    id = "plinth_round"
    version = 1
    family = "figurine_base"
    name = "Pratima plinth"
    description = (
        "Your own 3D form, repaired and set on a round or rounded-square plinth, with a name along its "
        "flat front."
    )
    environment = "studio"
    params = PARAMS
    anchors = (
        Anchor("top", "Top of the plinth", "planar", kind="volume", bounds_mm=_DEFAULT_LAYOUT.top_bounds, accepts=("hero_mesh",)),
        Anchor(
            "base_front", "Front of the plinth", "planar",
            size_mm=tuple(round(s, 1) for s in _DEFAULT_FRAMES["base_front"].size_mm),  # type: ignore[union-attr]
            bleed_mm=FRONT_BLEED_MM, accepts=("emboss_text",), max_relief_mm=MAX_RELIEF_MM,
        ),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    style_variants = ("comic_pop",)  # Katha's comic-book look: names and motifs raised and bolder by default (features.styles)
    features_supported = ("hero_mesh", "emboss_text")
    hardware = ()
    min_feature_mm = 0.8

    @classmethod
    def build_body(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        layout = Layout(params)
        mesh = cad.to_trimesh(cad.prism(layout.outline, layout.h))
        mesh.apply_translation([0.0, 0.0, -float(mesh.bounds[0][2])])
        return mesh

    @classmethod
    def build(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]] = (), fetcher: Any | None = None) -> trimesh.Trimesh:
        """The plinth, the form fused on and the name set in; then the stability rule on the whole piece."""
        mesh = super().build(params, features, fetcher)
        heroes = [i for i, f in enumerate(features or []) if f.get("type") == "hero_mesh"]
        if heroes:
            cls.check_stability(mesh, params, features[heroes[0]], heroes[0])
        return mesh

    @classmethod
    def check_stability(cls, mesh: trimesh.Trimesh, params: Mapping[str, Any], hero: Mapping[str, Any], index: int) -> None:
        """Refuse a piece whose centre of gravity sits under ``MIN_TIPPING_MARGIN_MM`` from the footprint's edge
        or that tips before a ``MIN_TIP_ANGLE_DEG`` nudge, naming the plinth size that would hold it."""
        from aakar_inspect import checks

        layout = Layout(params)
        stab = checks.stability(mesh)
        margin = float(stab.margin_mm) if stab.margin_mm is not None else -math.inf
        cog_height = max(float(stab.cog[2]) - float(mesh.bounds[0][2]), 1e-6)
        angle = math.degrees(math.atan2(margin, cog_height))
        needed = max(MIN_TIPPING_MARGIN_MM, cog_height * math.tan(math.radians(MIN_TIP_ANGLE_DEG)))
        if margin >= needed - 1e-9:
            return
        longest = hero.get("fit") == "longest"
        wider = _wider_plinth(mesh, layout, longest)
        keys = ["diameter_mm"] + ([f"features[{index}].longest_mm"] if longest else [])
        advice = (
            f"choose a plinth at least {wider:g} mm across, or a smaller size for your form"
            if wider is not None
            else "choose a smaller size for your form"
        )
        what = "would lean off" if margin < MIN_TIPPING_MARGIN_MM else "would tip over at a light nudge on"
        raise ParamOutOfRange(
            keys,
            f"Your form {what} a {layout.D:g} mm plinth; {advice}.",
            {
                "tipping_margin_mm": round(margin, 2) if math.isfinite(margin) else None,
                "min_tipping_margin_mm": round(needed, 2),
                "tip_angle_deg": round(angle, 1) if math.isfinite(angle) else None,
                "min_tip_angle_deg": MIN_TIP_ANGLE_DEG,
                "cog_mm": [round(float(v), 2) for v in stab.cog],
                "suggested_diameter_mm": wider,
            },
        )

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
        shape = "round (flat at the front)" if L.shape == "round" else "rounded-square"
        return f"A {shape} Pratima plinth, {L.D:g} mm across and {L.h:g} mm tall, printed standing as modelled."

    @classmethod
    def karigar_note_for(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]] = (), style: str | None = None) -> str:
        """The plinth's note, a line on the customer's form when there is one, then the names set into it and the
        style's sentence (``Template.join_note``)."""
        note = cls.karigar_note(params)
        if any(f.get("type") == "hero_mesh" for f in features or []):
            note += (
                " The customer's form stands fused on its top: support its overhangs, and print small, detailed "
                "forms (under 100 mm) in resin."
            )
        return cls.join_note(note, features, style)
