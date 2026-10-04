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
from typing import Any, Mapping

import numpy as np
import trimesh
from shapely.geometry import MultiPoint, Point, Polygon

from .settings import Constraints

CONTACT_TOLERANCE_MM = 0.3
WALL_SAMPLES = 3000
WALL_PERCENTILE = 5.0
WALL_WARN_FACTOR = 1.2  # warn when within 20 % above the minimum
SELF_HIT_MM = 0.05  # ray hits closer than this are numerical self-intersections
CONNECTOR_AXIS_TOLERANCE_DEG = 5.0  # a Kadi socket must point straight down to print without supports
CONNECTOR_MOUTH_TOLERANCE_MM = 0.3  # ... and open on the bed
CONNECTOR_WALL_BAND_MM = 4.0  # the wall round the socket is measured within this band of its bore


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
    mesh: trimesh.Trimesh, samples: int = WALL_SAMPLES, seed: int = 0, faces: np.ndarray | None = None
) -> np.ndarray:
    """Cast rays inward from area-weighted sampled face centroids; return through-distances (mm).

    Each ray starts just inside the surface and travels along the inverted face normal; the
    distance to the first exit is the local wall thickness. Rays that never exit (open meshes)
    and numerical self-hits are dropped. ``faces`` restricts the sampling to those face indices
    (the wall round a Kadi socket); None samples the whole mesh.
    """
    candidates = np.arange(len(mesh.faces)) if faces is None else np.asarray(faces, dtype=int)
    n_faces = int(candidates.size)
    if n_faces == 0:
        return np.empty(0)
    rng = np.random.default_rng(seed)
    weights = np.asarray(mesh.area_faces, dtype=float)[candidates]
    total = weights.sum()
    if total <= 0:
        return np.empty(0)
    if n_faces <= samples:
        # Small meshes: use every face, repeated in proportion to its area for the percentile.
        reps = np.maximum(1, np.round(weights / total * samples).astype(int))
        idx = np.repeat(candidates, reps)
    else:
        idx = rng.choice(candidates, size=samples, p=weights / total)
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


# --------------------------------------------------------------------------- connector_fit


def _unit(vec: Any) -> np.ndarray:
    arr = np.asarray(vec, dtype=float).reshape(3)
    norm = float(np.linalg.norm(arr))
    if not np.isfinite(norm) or norm < 1e-12:
        raise ValueError("axis must be a non-zero vector")
    return arr / norm


def connector_fit(mesh: trimesh.Trimesh, connector: Mapping[str, Any], constraints: Constraints, samples: int = WALL_SAMPLES) -> dict[str, Any]:
    """Does the Kadi this piece was cut for fit its base and print clean? (hybrid products, plan §1.4)

    ``connector`` is the block the geometry service reports for a Kadi-S socket: the modelled bore and rib-crest
    diameters, the compensation they were modelled with, the pin the base presents (nominal ± a tolerance by the
    base's tolerance class), the mouth and axis in mesh coordinates. The check works the as-printed fit out itself:
      * the socket must point straight down (within ``CONNECTOR_AXIS_TOLERANCE_DEG``) and open on the bed
        (``CONNECTOR_MOUTH_TOLERANCE_MM``), so it prints without supports;
      * the printed bore must clear the pin and the ribs must still stand (fail otherwise), and the fattest pin in
        tolerance must not crush the ribs flat, or it would bear on the wall (fail);
      * the wall round the socket (faces within ``CONNECTOR_WALL_BAND_MM`` of the bore, down its depth) must be at
        least the connector's own minimum, 1.6 mm in PLA, measured by the same inward ray casting as
        ``thinnest_wall`` (fail);
      * the thinnest pin in tolerance should still bite the ribs by ``min_grip_mm`` (warn), and a pair of base and
        finish without a recorded fit test only ever reaches warn.
    Kinds other than ``socket`` are skipped until their checks exist.
    """
    kind = connector.get("kind")
    if kind != "socket":
        return check("skipped", f"No fit check for a {kind or 'unknown'} Kadi yet", detail={"kind": kind})
    try:
        axis = _unit(connector["axis"])
        mouth = np.asarray(connector["mouth_mm"], dtype=float).reshape(3)
        depth = float(connector["depth_mm"])
        bore_d = float(connector["bore_d_mm"])
        crest_d = float(connector["crest_d_mm"])
        pin = float(connector.get("pin_d_mm", connector.get("nominal_mm")))
        tol = float(connector.get("pin_tolerance_mm", 0.1))
        comp = connector.get("compensation") or {}
        hole_comp = float(comp.get("xy_hole_comp_mm", 0.0))
        shrink = 1.0 + float(comp.get("shrink_pct", 0.0)) / 100.0
        min_wall = float(connector.get("min_wall_mm") or comp.get("connector_min_wall_mm") or constraints.min_wall_mm)
        min_grip = float(connector.get("min_grip_mm", 0.05))
        nominal = float(connector.get("nominal_mm", pin))
        fit_tested = bool(connector.get("fit_tested", False))
    except (KeyError, TypeError, ValueError) as exc:
        return check("fail", "Kadi socket details are incomplete", value=None, detail={"error": str(exc), "keys": sorted(connector)})

    bore_printed = (bore_d - hole_comp) / shrink
    crest_printed = (crest_d - hole_comp) / shrink
    clearance = bore_printed - pin
    rib_height = (bore_printed - crest_printed) / 2.0
    bite_min = (pin - tol - crest_printed) / 2.0
    bite_max = (pin + tol - crest_printed) / 2.0
    crush_max = bite_max / rib_height if rib_height > 1e-9 else math.inf
    angle = math.degrees(math.acos(float(np.clip(np.dot(axis, [0.0, 0.0, 1.0]), -1.0, 1.0))))
    detail: dict[str, Any] = {
        "kind": kind,
        "nominal_mm": nominal,
        "pin_d_mm": pin,
        "pin_tolerance_mm": tol,
        "bore_printed_mm": round(bore_printed, 3),
        "crest_printed_mm": round(crest_printed, 3),
        "clearance_mm": round(clearance, 3),
        "rib_height_mm": round(rib_height, 3),
        "rib_bite_mm": [round(bite_min, 3), round(bite_max, 3)],
        "crush_fraction_max": round(crush_max, 3) if math.isfinite(crush_max) else None,
        "axis_tilt_deg": round(angle, 2),
        "min_wall_mm": min_wall,
        "fit_tested": fit_tested,
        "material_id": connector.get("material_id"),
        "method": "as-printed fit from the modelled socket and its compensation; inward ray casting round the bore",
    }
    label = f"{_fmt(nominal)} mm Kadi"
    if angle > CONNECTOR_AXIS_TOLERANCE_DEG:
        return check("fail", f"{label} · leans {_fmt(angle, 0)}°, must point straight down", value=round(angle, 2), unit="deg",
                     threshold=CONNECTOR_AXIS_TOLERANCE_DEG, detail=detail)
    if clearance <= 0 or rib_height <= 0.02:
        return check("fail", f"{label} · pin would not enter", value=round(clearance, 3), unit="mm", threshold=0.0, detail=detail)
    if crush_max > 1.0:
        return check("fail", f"{label} · ribs would crush flat on a thick pin", value=round(crush_max, 3), unit="", threshold=1.0, detail=detail)

    centroids = np.asarray(mesh.triangles_center, dtype=float) - mouth
    along = centroids @ axis
    radial = np.linalg.norm(centroids - np.outer(along, axis), axis=1)
    band = bore_d / 2.0 + max(CONNECTOR_WALL_BAND_MM, 3.0 * min_wall)
    faces = np.flatnonzero((along >= -0.5) & (along <= depth + 0.5) & (radial <= band))
    dist = wall_thickness_samples(mesh, samples=samples, faces=faces)
    if dist.size == 0:
        detail["socket_faces"] = int(faces.size)
        return check("fail", f"{label} · could not measure the socket wall", value=None, unit="mm", threshold=min_wall, detail=detail)
    wall = float(np.percentile(dist, WALL_PERCENTILE))
    detail.update({"socket_faces": int(faces.size), "wall_samples": int(dist.size), "wall_p05_mm": round(wall, 3), "wall_min_mm": round(float(dist.min()), 3)})
    if wall < min_wall:
        return check("fail", f"Socket wall {_fmt(wall)} mm · too thin (min {_fmt(min_wall)} mm)", value=round(wall, 2), unit="mm", threshold=min_wall, detail=detail)

    lift = float(mouth[2] - mesh.bounds[0][2])
    detail["mouth_lift_mm"] = round(lift, 3)
    if lift > CONNECTOR_MOUTH_TOLERANCE_MM:
        return check("warn", f"{label} · mouth {_fmt(lift)} mm above the bed, needs supports", value=round(wall, 2), unit="mm", threshold=min_wall, detail=detail)
    if bite_min < min_grip:
        return check("warn", f"{label} · may sit loose on a thin pin", value=round(wall, 2), unit="mm", threshold=min_wall, detail=detail)
    if not fit_tested:
        return check("warn", f"Fits its base · {label} (fit test pending)", value=round(wall, 2), unit="mm", threshold=min_wall, detail=detail)
    return check("pass", f"Fits its base · {label}", value=round(wall, 2), unit="mm", threshold=min_wall, detail=detail)


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
