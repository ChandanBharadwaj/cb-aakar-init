"""2D outlines for Naam (text) and Buti (motifs): curves → rings → filled areas → relief solids.

* ``OutlinePen`` is a fontTools pen: HarfBuzz (``uharfbuzz.Font.draw_glyph_with_pen``) draws glyph
  outlines into it and fontTools' ``svgLib.path.parse_path`` draws motif paths into it. It flattens
  quadratic and cubic Béziers into polylines within ``tolerance`` (in the drawing's own units) and
  collects the closed rings.
* ``fill`` turns rings into the area they enclose under a fill rule: the rings are noded into one
  arrangement (shapely) and a face is kept when the rings' total winding number around a point inside
  it is non-zero (``nonzero``: fonts and SVG's default) or odd (``evenodd``). Holes, overlapping
  contours and self-intersections come out right whichever way each ring is wound.
* ``stroke_check`` is the stroke rule: an opening (erode by half the smallest printable stroke, grow
  back with mitred corners) keeps every part at least ``min_feature_mm`` wide and restores sharp corners
  exactly, so what it removes is the too-thin strokes. The outline fails when a separate piece (a dot, a
  vowel sign) disappears entirely, or when more than ``MAX_THIN_SHARE`` of its area does.
* ``relief_solid`` extrudes an area between two heights with manifold3d (``CrossSection`` →
  ``extrude``, the same kernel as the booleans): a closed, consistently wound solid in milliseconds even
  for thousands of edges, where the OCCT prism path (``cad.prism``) takes about a second for a name.
* ``set_into`` raises (emboss: union) or cuts (deboss: difference) an area in a local anchor frame
  (x = the viewer's right, y = up the picture, z out of the body) into a body. Nothing is mirrored: the
  deboss cutter is built in the same frame as the emboss, from ``depth`` below the skin to ``EMBED_MM``
  above it, so cut-in letters read left to right from outside exactly like raised ones.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Any, Callable, Iterable, Mapping, Sequence

import manifold3d
import numpy as np
import shapely
import trimesh
from fontTools.pens.basePen import BasePen
from shapely.geometry import LineString, MultiPolygon, Polygon
from shapely.geometry.base import BaseGeometry
from shapely.geometry.polygon import orient
from shapely.ops import polygonize, unary_union

from ..errors import ContentUnusable, GeometryError
from .booleans import difference, union
from .frames import AnchorFrame

FILL_RULES = ("nonzero", "evenodd")
EMBED_MM = 0.5  # raised content starts this far inside the body; a cutter starts this far outside it
MAX_THIN_SHARE = 0.05  # share of an outline's area allowed to be thinner than the smallest printable stroke
STROKE_SLACK_MM = 0.01  # a stroke exactly min_feature_mm wide passes
DEFAULT_MIN_FEATURE_MM = 0.8  # a 0.4 mm nozzle prints two lines side by side at the least
SIMPLIFY_MM = 0.002  # drops collinear points left by flattening, far below what a printer resolves
_EPS_AREA = 1e-9


# --------------------------------------------------------------------------- curves → rings


class OutlinePen(BasePen):
    """Collects closed rings (``contours``: ``(n, 2)`` float arrays, first point not repeated), flattening
    curves so that no point of a curve is further than ``tolerance`` from its polyline. ``offset`` moves
    every point (a glyph's position in its line)."""

    def __init__(self, tolerance: float, offset: tuple[float, float] = (0.0, 0.0)):
        super().__init__(glyphSet=None)
        if not tolerance > 0:
            raise ValueError("tolerance must be positive")
        self.tolerance = float(tolerance)
        self.dx, self.dy = float(offset[0]), float(offset[1])
        self.contours: list[np.ndarray] = []
        self._points: list[tuple[float, float]] | None = None

    def _at(self, pt: Sequence[float]) -> tuple[float, float]:
        return (float(pt[0]) + self.dx, float(pt[1]) + self.dy)

    def _moveTo(self, pt):  # noqa: N802 - fontTools pen protocol
        self._flush()
        self._points = [self._at(pt)]

    def _lineTo(self, pt):  # noqa: N802
        self._require().append(self._at(pt))

    def _qCurveToOne(self, pt1, pt2):  # noqa: N802
        points = self._require()
        p0 = np.asarray(points[-1])
        p1, p2 = np.asarray(self._at(pt1)), np.asarray(self._at(pt2))
        # |B''| = 2|p0 - 2p1 + p2| everywhere; n equal steps leave at most |p0 - 2p1 + p2| / (4 n²)
        n = self._steps(float(np.linalg.norm(p0 - 2.0 * p1 + p2)) / 4.0)
        t = np.arange(1, n + 1, dtype=np.float64)[:, None] / n
        points.extend(map(tuple, (1 - t) ** 2 * p0 + 2 * t * (1 - t) * p1 + t**2 * p2))

    def _curveToOne(self, pt1, pt2, pt3):  # noqa: N802
        points = self._require()
        p0 = np.asarray(points[-1])
        p1, p2, p3 = (np.asarray(self._at(p)) for p in (pt1, pt2, pt3))
        # |B''| <= 6 max(|p0 - 2p1 + p2|, |p1 - 2p2 + p3|); the chord error of a step h is |B''| h² / 8
        second = max(float(np.linalg.norm(p0 - 2.0 * p1 + p2)), float(np.linalg.norm(p1 - 2.0 * p2 + p3)))
        n = self._steps(0.75 * second)
        t = np.arange(1, n + 1, dtype=np.float64)[:, None] / n
        points.extend(map(tuple, (1 - t) ** 3 * p0 + 3 * t * (1 - t) ** 2 * p1 + 3 * t**2 * (1 - t) * p2 + t**3 * p3))

    def _closePath(self):  # noqa: N802
        self._flush()

    def _endPath(self):  # noqa: N802 - an open path still bounds an area when filled (SVG closes it)
        self._flush()

    def _steps(self, error_at_one_step: float) -> int:
        return max(1, int(math.ceil(math.sqrt(max(error_at_one_step, 0.0) / self.tolerance))))

    def _require(self) -> list[tuple[float, float]]:
        if self._points is None:  # pragma: no cover - fontTools' BasePen raises before this
            raise ValueError("drawing command before moveTo")
        return self._points

    def _flush(self) -> None:
        points, self._points = self._points, None
        if not points:
            return
        ring = np.asarray(points, dtype=np.float64)
        keep = np.ones(len(ring), dtype=bool)
        keep[1:] = np.any(np.abs(np.diff(ring, axis=0)) > 1e-12, axis=1)  # consecutive duplicates
        ring = ring[keep]
        if len(ring) > 1 and np.allclose(ring[0], ring[-1], rtol=0.0, atol=1e-12):
            ring = ring[:-1]
        if len(ring) >= 3:
            self.contours.append(ring)


# --------------------------------------------------------------------------- rings → area


def winding_number(ring: np.ndarray, x: float, y: float) -> int:
    """How many times ``ring`` (closed implicitly) winds around ``(x, y)``: +1 per anticlockwise turn."""
    a = ring
    b = np.roll(ring, -1, axis=0)
    upward = (a[:, 1] <= y) & (b[:, 1] > y)
    downward = (a[:, 1] > y) & (b[:, 1] <= y)
    side = (b[:, 0] - a[:, 0]) * (y - a[:, 1]) - (x - a[:, 0]) * (b[:, 1] - a[:, 1])
    return int(np.count_nonzero(upward & (side > 0)) - np.count_nonzero(downward & (side < 0)))


def fill(rings: Iterable[np.ndarray], rule: str = "nonzero") -> BaseGeometry:
    """The area enclosed by ``rings`` under ``rule`` (``nonzero`` or ``evenodd``), as a valid shapely area."""
    if rule not in FILL_RULES:
        raise ValueError(f"fill rule must be one of {FILL_RULES}, got {rule!r}")
    # no signed-area filter: a figure-of-eight ring has zero net area but two filled lobes; rings that bound
    # nothing (all points on a line) simply produce no faces below
    closed = [np.asarray(r, dtype=np.float64) for r in rings if len(r) >= 3]
    if not closed:
        return Polygon()
    noded = unary_union([LineString(np.vstack([r, r[:1]])) for r in closed])
    kept = []
    for face in polygonize(noded):
        if face.area <= _EPS_AREA:
            continue
        inside = face.representative_point()
        turns = sum(winding_number(r, inside.x, inside.y) for r in closed)
        if (turns != 0) if rule == "nonzero" else (turns % 2 != 0):
            kept.append(face)
    return clean(unary_union(kept))


def polygons(geom: BaseGeometry) -> list[Polygon]:
    """The polygons of an area (a Polygon, a MultiPolygon or a collection), largest first, empties dropped."""
    if geom is None or geom.is_empty:
        return []
    if isinstance(geom, Polygon):
        return [geom]
    parts: list[Polygon] = []
    for member in getattr(geom, "geoms", []):
        parts.extend(polygons(member))
    return sorted((p for p in parts if p.area > _EPS_AREA), key=lambda p: -p.area)


def clean(geom: BaseGeometry, simplify: float = 0.0) -> BaseGeometry:
    """A valid area made only of polygons (optionally simplified, topology kept)."""
    if geom.is_empty:
        return Polygon()
    if not geom.is_valid:
        geom = shapely.make_valid(geom)
    if simplify > 0:
        geom = geom.simplify(simplify, preserve_topology=True)
        if not geom.is_valid:
            geom = shapely.make_valid(geom)
    parts = polygons(geom)
    if not parts:
        return Polygon()
    return parts[0] if len(parts) == 1 else MultiPolygon(parts)


# --------------------------------------------------------------------------- the stroke rule


@dataclass(frozen=True)
class StrokeCheck:
    """``thin_share``: share of the area narrower than ``min_feature_mm``; ``lost_pieces``: separate pieces
    narrower than that everywhere (they would not print at all)."""

    ok: bool
    thin_share: float
    lost_pieces: int
    min_feature_mm: float

    def detail(self) -> dict[str, Any]:
        return {
            "thin_share": round(self.thin_share, 4),
            "max_thin_share": MAX_THIN_SHARE,
            "lost_pieces": self.lost_pieces,
            "min_feature_mm": self.min_feature_mm,
        }


def stroke_check(region: BaseGeometry, min_feature_mm: float, max_thin_share: float = MAX_THIN_SHARE) -> StrokeCheck:
    """Apply the stroke rule to an area in mm (see the module docstring)."""
    min_feature = float(min_feature_mm)
    area = float(region.area) if region is not None else 0.0
    if area <= _EPS_AREA:
        return StrokeCheck(False, 1.0, 0, min_feature)
    radius = max(min_feature / 2.0 - STROKE_SLACK_MM, 1e-6)
    eroded = region.buffer(-radius, quad_segs=8)
    opened = eroded.buffer(radius, quad_segs=8, join_style="mitre", mitre_limit=10.0) if not eroded.is_empty else Polygon()
    thin = region.difference(opened).area / area if not opened.is_empty else 1.0
    lost = sum(1 for piece in polygons(region) if piece.buffer(-radius, quad_segs=8).is_empty)
    share = float(min(max(thin, 0.0), 1.0))
    return StrokeCheck(lost == 0 and share <= max_thin_share, share, lost, min_feature)


def openings(region: BaseGeometry) -> BaseGeometry:
    """The negative space inside an area: its holes, less any islands standing in them (a motif's openings)."""
    holes = [Polygon(ring) for poly in polygons(region) for ring in poly.interiors]
    if not holes:
        return Polygon()
    return clean(unary_union(holes).difference(region))


# --------------------------------------------------------------------------- area → solid → body


def relief_solid(region: BaseGeometry, z0: float, z1: float) -> trimesh.Trimesh:
    """``region`` (mm, local frame) extruded from ``z0`` to ``z1``: closed, outward-wound, one shell per piece."""
    if not z1 > z0:
        raise ValueError("relief_solid needs z1 > z0")
    rings: list[np.ndarray] = []
    for poly in polygons(region):
        poly = orient(poly, sign=1.0)  # exterior anticlockwise, holes clockwise: the Positive fill rule
        rings.append(np.asarray(poly.exterior.coords, dtype=np.float64)[:-1])
        rings.extend(np.asarray(ring.coords, dtype=np.float64)[:-1] for ring in poly.interiors)
    if not rings:
        raise GeometryError("Nothing to raise or cut: the outline is empty", {})
    section = manifold3d.CrossSection(rings, manifold3d.FillRule.Positive)
    solid = manifold3d.Manifold.extrude(section, float(z1 - z0)).translate((0.0, 0.0, float(z0)))
    out = solid.to_mesh()
    mesh = trimesh.Trimesh(
        vertices=np.asarray(out.vert_properties, dtype=np.float64)[:, :3],
        faces=np.asarray(out.tri_verts, dtype=np.int64),
        process=True,
    )
    if mesh.is_empty or not mesh.is_watertight or mesh.volume <= 0:
        raise GeometryError(
            "The raised outline did not close into a solid",
            {"pieces": len(polygons(region)), "watertight": bool(mesh.is_watertight), "area_mm2": round(float(region.area), 3)},
        )
    return mesh


def set_into(
    body: trimesh.Trimesh,
    frame: AnchorFrame,
    region: BaseGeometry,
    *,
    depth_mm: float,
    mode: str,
    noun: str,
    detail: Mapping[str, Any] | None = None,
    embed_mm: float = EMBED_MM,
) -> trimesh.Trimesh:
    """Raise (``emboss``) or cut (``deboss``) ``region`` ``depth_mm`` into ``body`` at ``frame``.

    A boolean that fails is reported as ``ContentUnusable`` naming ``noun`` ("name", "motif"): the
    customer changes the content or its depth, never a mesh."""
    if body is None or body.is_empty:
        raise GeometryError(f"A {noun} needs a body to sit on", dict(detail or {}))
    depth = float(depth_mm)
    if mode == "emboss":
        solid, operation = relief_solid(region, -embed_mm, depth), union
    elif mode == "deboss":
        solid, operation = relief_solid(region, -depth, embed_mm), difference
    else:
        raise GeometryError(f"A {noun} is raised (emboss) or cut in (deboss)", {"mode": mode, **dict(detail or {})})
    placed = frame.place(solid)
    return _fuse(operation, body, placed, noun, {"mode": mode, **dict(detail or {})})


def _fuse(
    operation: Callable[[trimesh.Trimesh, trimesh.Trimesh], trimesh.Trimesh],
    body: trimesh.Trimesh,
    tool: trimesh.Trimesh,
    noun: str,
    detail: Mapping[str, Any],
) -> trimesh.Trimesh:
    try:
        return operation(body, tool)
    except GeometryError as exc:
        raise ContentUnusable(
            f"We couldn't set this {noun} into the piece; try another depth or size",
            {"error": exc.message, **dict(detail), **exc.detail},
        ) from exc


__all__ = [
    "DEFAULT_MIN_FEATURE_MM",
    "EMBED_MM",
    "FILL_RULES",
    "MAX_THIN_SHARE",
    "OutlinePen",
    "SIMPLIFY_MM",
    "StrokeCheck",
    "clean",
    "fill",
    "openings",
    "polygons",
    "relief_solid",
    "set_into",
    "stroke_check",
    "winding_number",
]
