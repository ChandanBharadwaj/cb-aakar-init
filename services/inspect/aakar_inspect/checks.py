"""Individual printability checks (PLAN §7.9).

Every check returns a dict shaped like ``printability-report.v1.json#/$defs/check``:
``status`` (pass | warn | fail | skipped), a customer-facing ``summary`` in the checkout-board
register ("Inside base · 3 mm", "3.2 mm · safe", "Fits 250 × 250 × 250 mm bed"), and optional
``value`` / ``unit`` / ``threshold`` / ``detail``.

Units are mm everywhere. The mesh is assumed to be Z-up and sitting on (or near) Z = 0.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Any

import numpy as np
import trimesh
from shapely.geometry import MultiPoint, Point, Polygon

from .settings import Constraints

CONTACT_TOLERANCE_MM = 0.3
WALL_SAMPLES = 3000
WALL_PERCENTILE = 5.0
WALL_WARN_FACTOR = 1.2  # warn when within 20 % above the minimum
SELF_HIT_MM = 0.05  # ray hits closer than this are numerical self-intersections


def _fmt(value: float, digits: int = 1) -> str:
    """Compact number: 3 -> '3', 3.25 -> '3.2', 21.5 -> '21.5'."""
    rounded = round(float(value), digits)
    if abs(rounded - round(rounded)) < 1e-9:
        return str(int(round(rounded)))
    return f"{rounded:.{digits}f}"


def check(status: str, summary: str, **extra: Any) -> dict[str, Any]:
    out: dict[str, Any] = {"status": status, "summary": summary}
    for key in ("value", "unit", "threshold", "detail"):
        if key in extra and extra[key] is not None:
            out[key] = extra[key]
    return out


# --------------------------------------------------------------------------- manifold


@dataclass
class ManifoldResult:
    check: dict[str, Any]
    mesh: trimesh.Trimesh  # the (possibly repaired) mesh that the other checks should use


def manifold(mesh: trimesh.Trimesh) -> ManifoldResult:
    """Watertight + consistent winding, after conservative trimesh repairs.

    pass  : watertight and consistently wound as delivered
    warn  : became watertight only after repair (merge vertices, fix winding/normals, fill holes)
    fail  : still open or inconsistently wound
    """
    with np.errstate(all="ignore"):
        original_ok = bool(mesh.is_watertight and mesh.is_winding_consistent and mesh.volume > 0)
    if original_ok:
        return ManifoldResult(
            check("pass", "Watertight", value=True, detail={"repaired": False, "triangles": int(len(mesh.faces))}),
            mesh,
        )

    repaired = mesh.copy()
    steps: list[str] = []
    try:
        repaired.merge_vertices()
        steps.append("merge_vertices")
        repaired.remove_unreferenced_vertices()
        trimesh.repair.fix_winding(repaired)
        steps.append("fix_winding")
        if not repaired.is_watertight:
            trimesh.repair.fill_holes(repaired)
            steps.append("fill_holes")
        trimesh.repair.fix_normals(repaired)
        steps.append("fix_normals")
        if repaired.is_watertight and repaired.volume < 0:
            trimesh.repair.fix_inversion(repaired)
            steps.append("fix_inversion")
    except Exception as exc:  # pragma: no cover - trimesh internals
        steps.append(f"error:{type(exc).__name__}")

    with np.errstate(all="ignore"):
        repaired_ok = bool(repaired.is_watertight and repaired.is_winding_consistent and repaired.volume > 0)
    detail = {
        "repaired": repaired_ok,
        "repair_steps": steps,
        "watertight_before": bool(mesh.is_watertight),
        "winding_consistent_before": bool(mesh.is_winding_consistent),
        "watertight_after": bool(repaired.is_watertight),
        "winding_consistent_after": bool(repaired.is_winding_consistent),
        "triangles": int(len(repaired.faces)),
    }
    if repaired_ok:
        return ManifoldResult(
            check("warn", "Watertight after repair", value=True, detail=detail), repaired
        )
    # Report open-edge count for developers.
    try:
        detail["open_edges"] = int(len(trimesh.grouping.group_rows(mesh.edges_sorted, require_count=1)))
    except Exception:  # pragma: no cover
        pass
    return ManifoldResult(check("fail", "Not watertight · cannot be printed", value=False, detail=detail), mesh)


# --------------------------------------------------------------------------- fits_bed


def fits_bed(mesh: trimesh.Trimesh, constraints: Constraints) -> dict[str, Any]:
    """Bounding box vs bed. Any axis permutation counts (the studio can lay the part down)."""
    extents = np.sort(np.asarray(mesh.extents, dtype=float))
    bed = np.sort(np.asarray(constraints.bed_mm, dtype=float))
    ok = bool(np.all(extents <= bed + 1e-6))
    bed_label = " × ".join(_fmt(b, 0) for b in constraints.bed_mm)
    size_label = " × ".join(_fmt(e, 0) for e in mesh.extents)
    detail = {"extents_mm": [round(float(e), 3) for e in mesh.extents], "bed_mm": list(constraints.bed_mm)}
    if ok:
        return check("pass", f"Fits {bed_label} mm bed", value=True, detail=detail)
    return check("fail", f"{size_label} mm · too big for {bed_label} mm bed", value=False, detail=detail)


# --------------------------------------------------------------------------- thinnest_wall


def wall_thickness_samples(
    mesh: trimesh.Trimesh, samples: int = WALL_SAMPLES, seed: int = 0
) -> np.ndarray:
    """Cast rays inward from area-weighted sampled face centroids; return through-distances (mm).

    Each ray starts just inside the surface and travels along the inverted face normal; the
    distance to the first exit is the local wall thickness. Rays that never exit (open meshes)
    and numerical self-hits are dropped.
    """
    n_faces = len(mesh.faces)
    if n_faces == 0:
        return np.empty(0)
    rng = np.random.default_rng(seed)
    weights = np.asarray(mesh.area_faces, dtype=float)
    total = weights.sum()
    if total <= 0:
        return np.empty(0)
    if n_faces <= samples:
        # Small meshes: use every face, repeated in proportion to its area for the percentile.
        reps = np.maximum(1, np.round(weights / total * samples).astype(int))
        idx = np.repeat(np.arange(n_faces), reps)
    else:
        idx = rng.choice(n_faces, size=samples, p=weights / total)
    normals = np.asarray(mesh.face_normals)[idx]
    origins = np.asarray(mesh.triangles_center)[idx] - normals * SELF_HIT_MM * 0.2
    directions = -normals
    locations, index_ray, _ = mesh.ray.intersects_location(origins, directions, multiple_hits=False)
    if len(locations) == 0:
        return np.empty(0)
    dist = np.linalg.norm(locations - origins[index_ray], axis=1)
    return dist[dist > SELF_HIT_MM]


def thinnest_wall(mesh: trimesh.Trimesh, constraints: Constraints, samples: int = WALL_SAMPLES) -> dict[str, Any]:
    dist = wall_thickness_samples(mesh, samples=samples)
    threshold = float(constraints.min_wall_mm)
    if dist.size == 0:
        return check(
            "fail",
            "Could not measure walls",
            value=None,
            unit="mm",
            threshold=threshold,
            detail={"samples": 0, "reason": "no ray exited the mesh; is it open?"},
        )
    thinnest = float(np.percentile(dist, WALL_PERCENTILE))
    smallest_extent = float(np.min(mesh.extents))
    detail = {
        "samples": int(dist.size),
        "percentile": WALL_PERCENTILE,
        "p05_mm": round(float(np.percentile(dist, 5)), 3),
        "p50_mm": round(float(np.percentile(dist, 50)), 3),
        "min_mm": round(float(dist.min()), 3),
        "method": "inward ray casting from area-weighted face centroids",
    }
    value = round(thinnest, 2)
    if thinnest < threshold:
        return check(
            "fail", f"{_fmt(thinnest)} mm · too thin (min {_fmt(threshold)} mm)", value=value, unit="mm", threshold=threshold, detail=detail
        )
    if thinnest < threshold * WALL_WARN_FACTOR:
        return check("warn", f"{_fmt(thinnest)} mm · borderline", value=value, unit="mm", threshold=threshold, detail=detail)
    if thinnest >= 0.4 * smallest_extent:
        # No wall thinner than 40 % of the smallest dimension: effectively a solid body.
        return check("pass", f"Solid · {_fmt(thinnest)} mm", value=value, unit="mm", threshold=threshold, detail=detail)
    return check("pass", f"{_fmt(thinnest)} mm · safe", value=value, unit="mm", threshold=threshold, detail=detail)


# --------------------------------------------------------------------------- stability


@dataclass
class Stability:
    cog: np.ndarray  # 3D centre of mass (uniform density)
    support: Polygon | None  # 2D convex hull of the contact patch, None when degenerate
    contact_points: int
    offset_mm: float | None  # horizontal offset of the COG projection from the hull centroid
    margin_mm: float | None  # signed distance to the nearest hull edge (+ inside)
    inside: bool


def stability(mesh: trimesh.Trimesh, tolerance_mm: float = CONTACT_TOLERANCE_MM) -> Stability:
    """Centre of mass vs the support polygon of the contact patch (vertices within 0.3 mm of min Z)."""
    if mesh.is_watertight and mesh.volume > 0:
        cog = np.asarray(mesh.center_mass, dtype=float)
    else:  # open mesh: fall back to the vertex centroid, still gives a usable picture
        cog = np.asarray(mesh.vertices, dtype=float).mean(axis=0)
    z_min = float(mesh.bounds[0][2])
    verts = np.asarray(mesh.vertices, dtype=float)
    contact = verts[verts[:, 2] <= z_min + tolerance_mm][:, :2]
    if len(contact) == 0:
        return Stability(cog, None, 0, None, None, False)
    hull = MultiPoint([tuple(p) for p in np.unique(np.round(contact, 4), axis=0)]).convex_hull
    if not isinstance(hull, Polygon) or hull.area < 1e-6:
        return Stability(cog, None, int(len(contact)), None, None, False)
    projection = Point(float(cog[0]), float(cog[1]))
    centroid = hull.centroid
    offset = float(projection.distance(centroid))
    inside = bool(hull.covers(projection))
    edge_distance = float(hull.exterior.distance(projection))
    margin = edge_distance if inside else -edge_distance
    return Stability(cog, hull, int(len(contact)), offset, margin, inside)


def centre_of_gravity(stab: Stability) -> dict[str, Any]:
    detail = {
        "cog_mm": [round(float(v), 3) for v in stab.cog],
        "contact_points": stab.contact_points,
        "support_polygon_area_mm2": round(float(stab.support.area), 3) if stab.support is not None else 0.0,
        "support_polygon_centroid_mm": (
            [round(stab.support.centroid.x, 3), round(stab.support.centroid.y, 3)] if stab.support is not None else None
        ),
    }
    if stab.support is None or stab.offset_mm is None:
        return check("fail", "No flat base to stand on", value=None, unit="mm", detail=detail)
    value = round(stab.offset_mm, 2)
    if stab.inside:
        return check("pass", f"Inside base · {_fmt(stab.offset_mm, 0)} mm", value=value, unit="mm", detail=detail)
    return check("fail", f"Outside base · {_fmt(stab.offset_mm, 0)} mm", value=value, unit="mm", detail=detail)


def tipping_margin(stab: Stability, constraints: Constraints) -> dict[str, Any]:
    threshold = float(constraints.min_tipping_margin_mm)
    detail = {"support_polygon_wkt": stab.support.wkt if stab.support is not None else None}
    if stab.support is None or stab.margin_mm is None:
        return check("fail", "Tips over · no flat base", value=None, unit="mm", threshold=threshold, detail=detail)
    value = round(stab.margin_mm, 2)
    if stab.margin_mm < 0:
        return check("fail", f"Tips over · {_fmt(-stab.margin_mm)} mm outside", value=value, unit="mm", threshold=threshold, detail=detail)
    if stab.margin_mm < threshold:
        return check("warn", f"Tippy · {_fmt(stab.margin_mm)} mm margin", value=value, unit="mm", threshold=threshold, detail=detail)
    return check("pass", f"Stable · {_fmt(stab.margin_mm)} mm margin", value=value, unit="mm", threshold=threshold, detail=detail)


# --------------------------------------------------------------------------- later phases


def overhangs_skipped() -> dict[str, Any]:
    return check("skipped", "Overhang analysis arrives in Phase 2")


def load_capacity_skipped() -> dict[str, Any]:
    return check("skipped", "Load estimate arrives in Phase 3")


# --------------------------------------------------------------------------- helpers shared with the estimator


def overhang_area_mm2(mesh: trimesh.Trimesh, max_overhang_deg: float, layer_height_mm: float = 0.2) -> float:
    """Area of downward-facing faces steeper than the overhang limit, excluding the bed contact.

    Coarse: used only to hint ``supports_required`` in the estimate. The real overhang check
    (with bridging detection) arrives in Phase 2.
    """
    normals = np.asarray(mesh.face_normals)
    centers = np.asarray(mesh.triangles_center)
    z_min = float(mesh.bounds[0][2])
    # angle between the outward normal and straight down; small angle == flat overhang
    cos_limit = math.cos(math.radians(90.0 - max_overhang_deg))
    down = -normals[:, 2]
    mask = (down > cos_limit) & (centers[:, 2] > z_min + layer_height_mm)
    return float(np.asarray(mesh.area_faces)[mask].sum())
