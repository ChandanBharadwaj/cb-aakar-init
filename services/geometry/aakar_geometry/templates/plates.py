"""Shared geometry for the flat carriers: Saathi keychain, Chumbak magnet, Jhoomar ornament.

Profiles are shapely polygons in mm on the XY plane, seen from above with the piece lying face up
(x = the viewer's right, y = up in the picture), extruded along +Z by ``cad.prism``. The helpers:

* ``disc`` / ``hole``: circles as polygons; ``hole`` is circumscribed so the narrowest opening is the
  full diameter (a ring, cord or magnet always passes);
* ``rounded_rect``, ``heart_unit``, ``star_unit``, ``bauble_unit``: outlines (the ``*_unit`` ones are
  one unit wide or across and are scaled by the templates);
* ``symmetric_rect``: the rectangle centred on x = 0 inside a profile that holds the largest 4:3
  picture, i.e. the printable area of a face (a template subtracts its holes and margins first);
* ``cut_in_depth`` / ``check_skin``: the skin rule. A cut-in (deboss) photo, name or motif must leave
  ``MIN_SKIN_MM`` of plastic behind it; raised (emboss) content adds plastic and never counts. Cut-ins on
  opposite faces add up; a name and a motif side by side on one face count once (the deeper).
"""

from __future__ import annotations

import math
from functools import lru_cache
from typing import Any, Iterable, Mapping, Sequence

import numpy as np
from shapely import affinity
from shapely.geometry import LineString, MultiPolygon, Polygon, box
from shapely.geometry.base import BaseGeometry

from ..errors import ParamOutOfRange

CIRCLE_SEGMENTS = 96
HOLE_ALLOWANCE_MM = 0.2  # FDM holes print about 0.2 mm small on a 0.4 mm nozzle: cut them this much larger
RIM_MM = 2.0  # solid plastic kept all round a ring or cord hole (a 1.2 mm split-ring wire needs it)
MIN_SKIN_MM = 1.2  # plastic left behind a cut-in photo, over a magnet pocket, ... (the min wall)
MIN_PRINTABLE_MM = 8.0  # a face narrower or shorter than this cannot carry a photo worth printing

# depth key per feature type (``features.validate.DEPTH_KEY``) and the contract default when absent
_DEPTH = {"relief_image": ("relief_mm", 0.6), "emboss_text": ("depth_mm", 1.2), "motif": ("depth_mm", 1.0)}
_DEFAULT_MODE = {"relief_image": "emboss", "emboss_text": "emboss", "motif": "deboss"}


def disc(cx: float, cy: float, r: float, segments: int = CIRCLE_SEGMENTS) -> Polygon:
    """Circle of radius ``r`` as a polygon with its vertices on the circle."""
    t = np.linspace(0.0, 2.0 * math.pi, segments, endpoint=False)
    return Polygon(np.column_stack([cx + r * np.cos(t), cy + r * np.sin(t)]))


def hole(cx: float, cy: float, d: float, segments: int = CIRCLE_SEGMENTS) -> Polygon:
    """Round hole of diameter ``d``: the polygon's edges touch the circle, so nothing narrower than ``d`` remains."""
    return disc(cx, cy, (d / 2.0) / math.cos(math.pi / segments), segments)


def rounded_rect(w: float, h: float, r: float, quad_segs: int = 16) -> Polygon:
    """``w`` × ``h`` rectangle centred on the origin with corner radius ``r`` (0 = sharp corners)."""
    r = max(0.0, min(float(r), w / 2.0, h / 2.0))
    if r <= 1e-9:
        return box(-w / 2.0, -h / 2.0, w / 2.0, h / 2.0)
    core = box(-w / 2.0 + r, -h / 2.0 + r, w / 2.0 - r, h / 2.0 - r)
    return core.buffer(r, quad_segs=quad_segs)


def largest_polygon(geom: BaseGeometry) -> Polygon:
    """The largest polygon of an area (a union or buffer can leave slivers beside the main piece)."""
    if isinstance(geom, Polygon):
        return geom
    if isinstance(geom, MultiPolygon) or hasattr(geom, "geoms"):
        polys = [g for g in geom.geoms if isinstance(g, Polygon)]
        if polys:
            return max(polys, key=lambda g: g.area)
    raise ValueError(f"expected an area, got {geom.geom_type}")


def soften(poly: Polygon, radius: float) -> Polygon:
    """Round the convex points (tips) of ``poly`` to ``radius``; concave corners such as a heart's notch stay crisp."""
    if radius <= 0:
        return poly
    return largest_polygon(poly.buffer(-radius, quad_segs=8).buffer(radius, quad_segs=8))


@lru_cache(maxsize=None)
def heart_unit() -> Polygon:
    """A heart one unit wide, notch on top at x = 0, tip at the bottom, tips softened (the classic sin³ heart)."""
    t = np.linspace(0.0, 2.0 * math.pi, 360, endpoint=False)
    x = 16.0 * np.sin(t) ** 3
    y = 13.0 * np.cos(t) - 5.0 * np.cos(2 * t) - 2.0 * np.cos(3 * t) - np.cos(4 * t)
    poly = Polygon(np.column_stack([x, y]) / 32.0).buffer(0)
    poly = soften(largest_polygon(poly), 0.03)
    return affinity.translate(poly, -(poly.bounds[0] + poly.bounds[2]) / 2.0, 0.0)


@lru_cache(maxsize=None)
def star_unit(points: int = 5, inner_ratio: float = 0.5) -> Polygon:
    """A ``points``-pointed star with one point straight up, outer radius 0.5, tips softened."""
    angles = math.pi / 2.0 + np.arange(2 * points) * math.pi / points
    radii = np.where(np.arange(2 * points) % 2 == 0, 0.5, 0.5 * inner_ratio)
    poly = Polygon(np.column_stack([radii * np.cos(angles), radii * np.sin(angles)]))
    return soften(poly, 0.03)


@lru_cache(maxsize=None)
def bauble_unit() -> Polygon:
    """A round bauble one unit across with a small crown on top (the hanging hole goes through the crown)."""
    ball = disc(0.0, 0.0, 0.5, 144)
    crown = rounded_rect(0.30, 0.16, 0.03)
    crown = affinity.translate(crown, 0.0, 0.5)  # 0.08 above the ball, 0.08 sunk into it
    return largest_polygon(ball.union(crown))


def top_at(poly: BaseGeometry, x: float = 0.0) -> float | None:
    """Highest y of ``poly`` on the vertical line through ``x`` (None when the line misses it)."""
    if poly.is_empty:
        return None
    minx, miny, maxx, maxy = poly.bounds
    cut = poly.intersection(LineString([(x, miny - 1.0), (x, maxy + 1.0)]))
    if cut.is_empty:
        return None
    return float(cut.bounds[3])


def _segments(geom: BaseGeometry) -> Iterable[BaseGeometry]:
    if geom.is_empty:
        return []
    if hasattr(geom, "geoms"):
        return [g for g in geom.geoms if not g.is_empty]
    return [geom]


PHOTO_ASPECT = 4.0 / 3.0  # phone photos: the printable area is chosen to hold the biggest 4:3 picture


def symmetric_rect(
    region: BaseGeometry, steps: int = 120, aspect: float | None = PHOTO_ASPECT
) -> tuple[float, float, float, float] | None:
    """``(x0, y0, x1, y1)`` of the best axis-aligned rectangle centred on x = 0 inside ``region``.

    "Best" holds the largest picture of ``aspect`` (width / height) fitted inside it, ties going to the
    larger rectangle; ``aspect=None`` means the largest area (a heart's largest rectangle is a thin
    band, useless for a photo). ``region`` may be non-convex or in pieces (a profile minus a hole's
    keep-out). Each horizontal section gives the half-width available around x = 0; a rectangle
    spanning rows a..b is as wide as the narrowest row between them. The winner is checked against the
    region and pulled in if the sampling overshot.
    """
    if region.is_empty or region.area <= 0:
        return None
    minx, miny, maxx, maxy = region.bounds
    ys = np.linspace(miny, maxy, steps)
    half = np.zeros(steps)
    for i, y in enumerate(ys):
        cut = region.intersection(LineString([(minx - 1.0, y), (maxx + 1.0, y)]))
        for seg in _segments(cut):
            x0, _, x1, _ = seg.bounds
            if x0 <= 1e-9 and x1 >= -1e-9:
                half[i] = min(-x0, x1)
                break
    best: tuple[float, float, float, float] | None = None
    best_score = (0.0, 0.0)
    for a in range(steps):
        m = half[a]
        if m <= 0:
            continue
        for b in range(a + 1, steps):
            m = min(m, half[b])
            if m <= 0:
                break
            w, h = 2.0 * m, float(ys[b] - ys[a])
            picture = min(h, w / aspect) if aspect else 0.0  # height of the largest picture that fits
            score = (round(picture, 3), w * h)
            if score > best_score:
                best_score, best = score, (-m, float(ys[a]), m, float(ys[b]))
    if best is None:
        return None
    x0, y0, x1, y1 = (float(v) for v in best)
    grown = region.buffer(1e-6)
    for _ in range(40):  # sampling can overshoot a curved edge between rows by a hair: pull in until it fits
        if grown.contains(box(x0, y0, x1, y1)):
            return (x0, y0, x1, y1)
        x0, x1 = x0 + 0.05, x1 - 0.05
        y0, y1 = y0 + 0.05, y1 - 0.05
        if x1 <= x0 or y1 <= y0:
            break
    return None


_CUT_NOUN = {"relief_image": "photo", "emboss_text": "name", "motif": "motif"}


def cut_in_depth(features: Sequence[Mapping[str, Any]], anchors: Iterable[str]) -> tuple[float, list[str]]:
    """Total depth of cut-in (deboss) content on ``anchors`` and the feature keys that carry it.

    Depths on opposite faces of one plate add up (conservative: the two pictures may overlap). A name and
    a motif side by side on one face do not overlap, so only the deeper of them counts for that face.
    """
    wanted = set(anchors)
    per_anchor: dict[str, float] = {}
    marks: dict[str, float] = {}
    keys: list[str] = []
    for index, feature in enumerate(features or []):
        ftype = feature.get("type")
        anchor = feature.get("anchor")
        if ftype not in _DEPTH or anchor not in wanted:
            continue
        if feature.get("mode", _DEFAULT_MODE[ftype]) != "deboss":
            continue
        key, default = _DEPTH[ftype]
        depth = float(feature.get(key, default))
        if ftype in ("emboss_text", "motif"):
            marks[anchor] = max(marks.get(anchor, 0.0), depth)
        else:
            per_anchor[anchor] = per_anchor.get(anchor, 0.0) + depth
        keys.append(f"features[{index}].{key}")
    total = sum(per_anchor.values()) + sum(marks.values())
    return total, keys


def _cut_nouns(features: Sequence[Mapping[str, Any]], anchors: Iterable[str]) -> str:
    wanted = set(anchors)
    nouns: list[str] = []
    for feature in features or []:
        ftype = feature.get("type")
        if ftype in _DEPTH and feature.get("anchor") in wanted and feature.get("mode", _DEFAULT_MODE[ftype]) == "deboss":
            noun = _CUT_NOUN[ftype]
            if noun not in nouns:
                nouns.append(noun)
    if not nouns:
        return "picture"
    return nouns[0] if len(nouns) == 1 else ", ".join(nouns[:-1]) + " and " + nouns[-1]


def check_skin(
    features: Sequence[Mapping[str, Any]],
    *,
    anchors: Iterable[str],
    thickness_mm: float,
    noun: str,
    behind: float = 0.0,
    behind_what: str = "",
    thickness_key: str = "thickness_mm",
    min_skin_mm: float = MIN_SKIN_MM,
) -> None:
    """Raise ``ParamOutOfRange`` when cut-in content leaves less than ``min_skin_mm`` of plastic.

    ``behind`` is solid already taken away behind the face (a 3.2 mm magnet pocket); ``behind_what``
    names it for the message ("the magnet"). The message names what is cut in (photo, name, motif).
    """
    anchors = tuple(anchors)
    depth, keys = cut_in_depth(features, anchors)
    if depth <= 0:
        return
    skin = float(thickness_mm) - float(behind) - depth
    if skin >= min_skin_mm - 1e-9:
        return
    content = _cut_nouns(features, anchors)
    deepest = float(thickness_mm) - float(behind) - min_skin_mm
    thickest_needed = depth + float(behind) + min_skin_mm
    target = f"between the {content} and {behind_what}" if behind_what else "behind it"
    advice = (
        f"keep the cut-in to {deepest:.1f} mm or less, or make the {noun} at least {thickest_needed:.1f} mm thick"
        if deepest >= 0.2
        else f"raise the {content} instead of cutting it in, or make the {noun} at least {thickest_needed:.1f} mm thick"
    )
    raise ParamOutOfRange(
        keys + [thickness_key],
        f"Cut in {depth:g} mm, the {content} would leave only {max(skin, 0.0):.1f} mm of plastic {target} "
        f"(at least {min_skin_mm:g} mm is needed); {advice}",
        {
            "cut_in_mm": round(depth, 3),
            "skin_mm": round(skin, 3),
            "min_skin_mm": min_skin_mm,
            "thickness_mm": float(thickness_mm),
            "max_cut_in_mm": round(max(deepest, 0.0), 3),
            "min_thickness_mm": round(thickest_needed, 3),
        },
    )


__all__ = [
    "CIRCLE_SEGMENTS",
    "HOLE_ALLOWANCE_MM",
    "MIN_PRINTABLE_MM",
    "MIN_SKIN_MM",
    "PHOTO_ASPECT",
    "RIM_MM",
    "bauble_unit",
    "check_skin",
    "cut_in_depth",
    "disc",
    "heart_unit",
    "hole",
    "largest_polygon",
    "rounded_rect",
    "soften",
    "star_unit",
    "symmetric_rect",
    "top_at",
]
