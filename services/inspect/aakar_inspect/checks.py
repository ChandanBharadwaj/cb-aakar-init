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

    ``connector`` is the block the geometry service reports for the piece's Kadi (``Connector.report``): the modelled
    dimensions, the compensation they were modelled with, the male side the base presents (nominal ± a tolerance by the
    base's tolerance class), the mouth or seat and the axis in mesh coordinates. The check works the as-printed fit out
    itself, by kind:

    * ``socket`` (Kadi-S): the socket must point straight down (``CONNECTOR_AXIS_TOLERANCE_DEG``) and open on the bed
      (``CONNECTOR_MOUTH_TOLERANCE_MM``); the printed bore must clear the pin and the ribs still stand, the fattest pin in
      tolerance must not crush the ribs flat; the wall round the socket (faces within ``CONNECTOR_WALL_BAND_MM`` of the
      bore) at least the connector's minimum; the thinnest pin should still bite the ribs (warn).
    * ``dovetail`` (Kadi-D): same orientation rules; the rail's neck must clear the mouth and its head the floor by at
      least ``min_flank_gap_mm`` (fail), a loose flank (``loose_flank_gap_mm``) warns; the wall under the floor at least
      the minimum.
    * ``thread`` (Kadi-T): same orientation rules; a collar hole or thread minor must clear the neck (fail), a loose one
      warns, a thread needs its turns; the lip or wall round the hole at least the minimum.
    * ``magnet`` (Kadi-M): same orientation rules; each pocket must clear its disc or magnet (fail), a loose one warns;
      the skin over the pockets at least the minimum.
    * ``rim_clip`` (Kadi-C): the axis must be vertical (the seat faces up or down); a material that creeps fails, tabs
      thinner than ``min_wall_mm`` fail, a tab strained past the material's ``max_strain_pct`` on the largest base of
      the class fails, a lip that no longer bites the smallest warns.

    Every kind: a pair of base and finish without a recorded fit test only ever reaches ``warn``.
    """
    kind = connector.get("kind")
    fn = _FIT_CHECKS.get(str(kind))
    if fn is None:
        return check("skipped", f"No fit check for a {kind or 'unknown'} Kadi", detail={"kind": kind})
    try:
        return fn(mesh, connector, constraints, samples)
    except (KeyError, TypeError, ValueError) as exc:
        return check("fail", f"Kadi {kind} details are incomplete", value=None, detail={"error": str(exc), "keys": sorted(connector)})


@dataclass
class _Kadi:
    """What every Kadi report carries, parsed once."""

    kind: str
    axis: np.ndarray
    mouth: np.ndarray
    depth: float
    nominal: float
    pin: float
    tol: float
    hole_comp: float
    outer_comp: float
    shrink: float
    min_wall: float
    fit_tested: bool
    material_id: Any

    def printed(self, model_mm: float) -> float:
        """A modelled hole-like size as it should print (the compensation taken back out)."""
        return (model_mm - self.hole_comp) / self.shrink

    @property
    def tilt_deg(self) -> float:
        return math.degrees(math.acos(float(np.clip(np.dot(self.axis, [0.0, 0.0, 1.0]), -1.0, 1.0))))


def _kadi(connector: Mapping[str, Any], constraints: Constraints) -> _Kadi:
    comp = connector.get("compensation") or {}
    nominal = float(connector.get("nominal_mm", connector.get("pin_d_mm")))
    return _Kadi(
        kind=str(connector.get("kind")),
        axis=_unit(connector["axis"]),
        mouth=np.asarray(connector["mouth_mm"], dtype=float).reshape(3),
        depth=float(connector.get("depth_mm", 0.0)),
        nominal=nominal,
        pin=float(connector.get("pin_d_mm", nominal)),
        tol=float(connector.get("pin_tolerance_mm", 0.1)),
        hole_comp=float(comp.get("xy_hole_comp_mm", 0.0)),
        outer_comp=float(comp.get("xy_outer_comp_mm", 0.0)),
        shrink=1.0 + float(comp.get("shrink_pct", 0.0)) / 100.0,
        min_wall=float(connector.get("min_wall_mm") or comp.get("connector_min_wall_mm") or constraints.min_wall_mm),
        fit_tested=bool(connector.get("fit_tested", False)),
        material_id=connector.get("material_id"),
    )


def _detail(k: _Kadi, **extra: Any) -> dict[str, Any]:
    out: dict[str, Any] = {
        "kind": k.kind,
        "nominal_mm": k.nominal,
        "pin_d_mm": k.pin,
        "pin_tolerance_mm": k.tol,
        "axis_tilt_deg": round(k.tilt_deg, 2),
        "min_wall_mm": k.min_wall,
        "fit_tested": k.fit_tested,
        "material_id": k.material_id,
    }
    out.update(extra)
    return out


def _wall_near(
    mesh: trimesh.Trimesh,
    centre: np.ndarray,
    axis: np.ndarray,
    along_lo: float,
    along_hi: float,
    radial_max: float,
    samples: int,
    *,
    across: np.ndarray | None = None,
    half_across: float = 0.0,
    along_dir: np.ndarray | None = None,
    half_along: float = 0.0,
    radial_min: float = 0.0,
) -> tuple[np.ndarray, int]:
    """Through-distances (``wall_thickness_samples``) of the faces within a band round a connector: a cylinder of
    ``radial_max`` about ``axis`` through ``centre`` (default; ``radial_min`` leaves out what lies inside, a thread's own
    teeth), or a box ``half_across`` × ``half_along`` for a channel."""
    centroids = np.asarray(mesh.triangles_center, dtype=float) - centre
    along = centroids @ axis
    perp = centroids - np.outer(along, axis)
    mask = (along >= along_lo) & (along <= along_hi)
    if across is not None and along_dir is not None:
        mask &= (np.abs(perp @ across) <= half_across) & (np.abs(perp @ along_dir) <= half_along)
    else:
        radial = np.linalg.norm(perp, axis=1)
        mask &= (radial <= radial_max) & (radial >= radial_min)
    faces = np.flatnonzero(mask)
    return wall_thickness_samples(mesh, samples=samples, faces=faces), int(faces.size)


def _mouth_lift(mesh: trimesh.Trimesh, mouth: np.ndarray) -> float:
    return float(mouth[2] - mesh.bounds[0][2])


def _closing(label: str, k: _Kadi, wall: float | None, detail: dict[str, Any], loose: str | None) -> dict[str, Any]:
    """The common tail: a loose fit warns, a pair without a fit test warns, else pass."""
    value = round(wall, 2) if wall is not None else None
    if loose:
        return check("warn", f"{label} · {loose}", value=value, unit="mm", threshold=k.min_wall, detail=detail)
    if not k.fit_tested:
        return check("warn", f"Fits its base · {label} (fit test pending)", value=value, unit="mm", threshold=k.min_wall, detail=detail)
    return check("pass", f"Fits its base · {label}", value=value, unit="mm", threshold=k.min_wall, detail=detail)


def _socket_fit(mesh: trimesh.Trimesh, connector: Mapping[str, Any], constraints: Constraints, samples: int) -> dict[str, Any]:
    k = _kadi(connector, constraints)
    bore_d = float(connector["bore_d_mm"])
    crest_d = float(connector["crest_d_mm"])
    min_grip = float(connector.get("min_grip_mm", 0.05))
    bore_printed = k.printed(bore_d)
    crest_printed = k.printed(crest_d)
    clearance = bore_printed - k.pin
    rib_height = (bore_printed - crest_printed) / 2.0
    bite_min = (k.pin - k.tol - crest_printed) / 2.0
    bite_max = (k.pin + k.tol - crest_printed) / 2.0
    crush_max = bite_max / rib_height if rib_height > 1e-9 else math.inf
    detail = _detail(
        k,
        bore_printed_mm=round(bore_printed, 3),
        crest_printed_mm=round(crest_printed, 3),
        clearance_mm=round(clearance, 3),
        rib_height_mm=round(rib_height, 3),
        rib_bite_mm=[round(bite_min, 3), round(bite_max, 3)],
        crush_fraction_max=round(crush_max, 3) if math.isfinite(crush_max) else None,
        method="as-printed fit from the modelled socket and its compensation; inward ray casting round the bore",
    )
    label = f"{_fmt(k.nominal)} mm Kadi"
    if k.tilt_deg > CONNECTOR_AXIS_TOLERANCE_DEG:
        return check("fail", f"{label} · leans {_fmt(k.tilt_deg, 0)}°, must point straight down", value=round(k.tilt_deg, 2), unit="deg",
                     threshold=CONNECTOR_AXIS_TOLERANCE_DEG, detail=detail)
    if clearance <= 0 or rib_height <= 0.02:
        return check("fail", f"{label} · pin would not enter", value=round(clearance, 3), unit="mm", threshold=0.0, detail=detail)
    if crush_max > 1.0:
        return check("fail", f"{label} · ribs would crush flat on a thick pin", value=round(crush_max, 3), unit="", threshold=1.0, detail=detail)

    band = bore_d / 2.0 + max(CONNECTOR_WALL_BAND_MM, 3.0 * k.min_wall)
    dist, n_faces = _wall_near(mesh, k.mouth, k.axis, -0.5, k.depth + 0.5, band, samples)
    if dist.size == 0:
        detail["socket_faces"] = n_faces
        return check("fail", f"{label} · could not measure the socket wall", value=None, unit="mm", threshold=k.min_wall, detail=detail)
    wall = float(np.percentile(dist, WALL_PERCENTILE))
    detail.update({"socket_faces": n_faces, "wall_samples": int(dist.size), "wall_p05_mm": round(wall, 3), "wall_min_mm": round(float(dist.min()), 3)})
    if wall < k.min_wall:
        return check("fail", f"Socket wall {_fmt(wall)} mm · too thin (min {_fmt(k.min_wall)} mm)", value=round(wall, 2), unit="mm", threshold=k.min_wall, detail=detail)

    lift = _mouth_lift(mesh, k.mouth)
    detail["mouth_lift_mm"] = round(lift, 3)
    if lift > CONNECTOR_MOUTH_TOLERANCE_MM:
        return check("warn", f"{label} · mouth {_fmt(lift)} mm above the bed, needs supports", value=round(wall, 2), unit="mm", threshold=k.min_wall, detail=detail)
    return _closing(label, k, wall, detail, "may sit loose on a thin pin" if bite_min < min_grip else None)


def _orientation_fail(label: str, k: _Kadi, detail: dict[str, Any]) -> dict[str, Any] | None:
    if k.tilt_deg > CONNECTOR_AXIS_TOLERANCE_DEG:
        return check("fail", f"{label} · leans {_fmt(k.tilt_deg, 0)}°, must point straight down", value=round(k.tilt_deg, 2), unit="deg",
                     threshold=CONNECTOR_AXIS_TOLERANCE_DEG, detail=detail)
    return None


def _wall_verdict(label: str, what: str, k: _Kadi, dist: np.ndarray, n_faces: int, detail: dict[str, Any]) -> tuple[float | None, dict[str, Any] | None]:
    if dist.size == 0:
        detail["band_faces"] = n_faces
        return None, check("fail", f"{label} · could not measure the {what}", value=None, unit="mm", threshold=k.min_wall, detail=detail)
    wall = float(np.percentile(dist, WALL_PERCENTILE))
    detail.update({"band_faces": n_faces, "wall_samples": int(dist.size), "wall_p05_mm": round(wall, 3), "wall_min_mm": round(float(dist.min()), 3)})
    if wall < k.min_wall:
        return wall, check("fail", f"{what.capitalize()} {_fmt(wall)} mm · too thin (min {_fmt(k.min_wall)} mm)", value=round(wall, 2), unit="mm", threshold=k.min_wall, detail=detail)
    return wall, None


def _mouth_warn(label: str, k: _Kadi, mesh: trimesh.Trimesh, wall: float | None, detail: dict[str, Any]) -> dict[str, Any] | None:
    lift = _mouth_lift(mesh, k.mouth)
    detail["mouth_lift_mm"] = round(lift, 3)
    if lift > CONNECTOR_MOUTH_TOLERANCE_MM:
        value = round(wall, 2) if wall is not None else None
        return check("warn", f"{label} · mouth {_fmt(lift)} mm above the bed, needs supports", value=value, unit="mm", threshold=k.min_wall, detail=detail)
    return None


def _dovetail_fit(mesh: trimesh.Trimesh, connector: Mapping[str, Any], constraints: Constraints, samples: int) -> dict[str, Any]:
    k = _kadi(connector, constraints)
    mouth_w = k.printed(float(connector["mouth_w_mm"]))
    floor_w = k.printed(float(connector["floor_w_mm"]))
    neck = k.pin
    head = float(connector.get("rail_head_mm", neck + 3.0))
    rail_h = float(connector.get("rail_height_mm", 4.0))
    min_gap = float(connector.get("min_flank_gap_mm", 0.05))
    loose_gap = float(connector.get("loose_flank_gap_mm", 0.6))
    flank_min = (mouth_w - (neck + k.tol)) / 2.0
    flank_max = (mouth_w - (neck - k.tol)) / 2.0
    floor_gap = (floor_w - (head + k.tol)) / 2.0
    depth_gap = k.depth - rail_h
    detail = _detail(
        k,
        mouth_w_printed_mm=round(mouth_w, 3),
        floor_w_printed_mm=round(floor_w, 3),
        flank_gap_mm=[round(flank_min, 3), round(flank_max, 3)],
        floor_gap_mm=round(floor_gap, 3),
        depth_gap_mm=round(depth_gap, 3),
        rail_neck_mm=neck,
        rail_head_mm=head,
        method="as-printed fit from the modelled channel and its compensation; inward ray casting under the floor",
    )
    label = f"{_fmt(k.nominal)} mm rail channel"
    failed = _orientation_fail(label, k, detail)
    if failed:
        return failed
    if flank_min < min_gap or floor_gap < min_gap or depth_gap < min_gap:
        return check("fail", f"{label} · rail would not slide in", value=round(min(flank_min, floor_gap, depth_gap), 3), unit="mm", threshold=min_gap, detail=detail)
    along_dir = _unit(connector.get("along", [1.0, 0.0, 0.0]))
    across = np.cross(k.axis, along_dir)
    length = float(connector.get("length_mm", 50.0))
    band = floor_w / 2.0 + max(CONNECTOR_WALL_BAND_MM, 3.0 * k.min_wall)
    dist, n_faces = _wall_near(mesh, k.mouth, k.axis, -0.5, k.depth + 0.5, band, samples, across=across, half_across=band,
                               along_dir=along_dir, half_along=length / 2.0 + 0.5)
    wall, failed = _wall_verdict(label, "wall over the channel", k, dist, n_faces, detail)
    if failed:
        return failed
    warned = _mouth_warn(label, k, mesh, wall, detail)
    if warned:
        return warned
    return _closing(label, k, wall, detail, "rail may rattle in a wide channel" if flank_max > loose_gap else None)


def _thread_fit(mesh: trimesh.Trimesh, connector: Mapping[str, Any], constraints: Constraints, samples: int) -> dict[str, Any]:
    k = _kadi(connector, constraints)
    form = str(connector.get("thread_form", "collar"))
    standard = str(connector.get("thread", ""))
    hole = k.printed(float(connector["hole_d_mm"]))
    min_gap = float(connector.get("min_gap_mm", 0.1))
    loose_gap = float(connector.get("loose_gap_mm", 1.5))
    turns = float(connector.get("turns", 0.0))
    min_turns = float(connector.get("min_turns", 4))
    pitch = connector.get("pitch_mm")
    if form == "collar":
        gap_min = hole - (k.pin + k.tol)
        gap_max = hole - (k.pin - k.tol)
        what = "collar lip"
    else:  # the female thread's minor against the male thread's minor at nominal
        male_minor = (k.pin + k.tol) - 2.0 * 0.5413 * float(pitch or 1.0)
        gap_min = hole - male_minor
        gap_max = gap_min + 2.0 * k.tol
        what = "wall round the thread"
    detail = _detail(
        k,
        thread=standard,
        thread_form=form,
        hole_printed_mm=round(hole, 3),
        gap_mm=[round(gap_min, 3), round(gap_max, 3)],
        turns=round(turns, 2),
        method="as-printed clearance from the modelled hole and its compensation; inward ray casting round the hole",
    )
    label = {"e27": "E27 collar", "e14": "E14 collar", "unc_1_4": "1/4-20 thread", "m10x1": "M10 thread"}.get(standard, f"{_fmt(k.nominal)} mm thread")
    if form == "insert":
        label = f"{label} (insert)"
    failed = _orientation_fail(label, k, detail)
    if failed:
        return failed
    if gap_min < min_gap:
        return check("fail", f"{label} · neck would not pass", value=round(gap_min, 3), unit="mm", threshold=min_gap, detail=detail)
    if form == "thread" and turns < min_turns:
        return check("fail", f"{label} · only {_fmt(turns)} turns, needs {_fmt(min_turns, 0)}", value=round(turns, 2), unit="", threshold=min_turns, detail=detail)
    band = hole / 2.0 + max(CONNECTOR_WALL_BAND_MM, 3.0 * k.min_wall)
    if form == "collar":
        dist, n_faces = _wall_near(mesh, k.mouth, k.axis, -0.5, k.depth + 0.5, band, samples)
    else:  # the thread's own teeth are thin by design: measure the wall outside the root, and the floor under the bore
        root = float(connector.get("major_d_mm", connector["hole_d_mm"])) / 2.0 + 0.05
        outer, n_outer = _wall_near(mesh, k.mouth, k.axis, -0.5, k.depth + 0.5, band, samples, radial_min=root)
        floor, n_floor = _wall_near(mesh, k.mouth, k.axis, k.depth - 0.2, k.depth + 0.5, root, samples)
        dist, n_faces = np.concatenate([outer, floor]), n_outer + n_floor
    wall, failed = _wall_verdict(label, what, k, dist, n_faces, detail)
    if failed:
        return failed
    warned = _mouth_warn(label, k, mesh, wall, detail)
    if warned:
        return warned
    return _closing(label, k, wall, detail, "may sit loose on the neck" if gap_max > loose_gap else None)


def _magnet_fit(mesh: trimesh.Trimesh, connector: Mapping[str, Any], constraints: Constraints, samples: int) -> dict[str, Any]:
    k = _kadi(connector, constraints)
    mode = str(connector.get("mode", "magnets"))
    pocket = k.printed(float(connector["pocket_d_mm"]))
    min_gap = float(connector.get("min_gap_mm", 0.1))
    loose_gap = float(connector.get("loose_gap_mm", 0.8))
    gap_min = pocket - (k.pin + k.tol)
    gap_max = pocket - (k.pin - k.tol)
    centres = [np.asarray(c, dtype=float).reshape(3) for c in connector.get("pocket_centres_mm") or [k.mouth]]
    detail = _detail(
        k,
        mode=mode,
        pocket_printed_mm=round(pocket, 3),
        gap_mm=[round(gap_min, 3), round(gap_max, 3)],
        count=len(centres),
        method="as-printed clearance from the modelled pocket and its compensation; inward ray casting over the pockets",
    )
    label = f"{_fmt(k.nominal)} mm disc pocket" if mode == "steel_disc" else f"{len(centres)} magnet pockets"
    failed = _orientation_fail(label, k, detail)
    if failed:
        return failed
    if gap_min < min_gap:
        return check("fail", f"{label} · {'disc' if mode == 'steel_disc' else 'magnet'} would not drop in", value=round(gap_min, 3), unit="mm", threshold=min_gap, detail=detail)
    band = pocket / 2.0 + max(CONNECTOR_WALL_BAND_MM, 3.0 * k.min_wall)
    dists, faces = [], 0
    for centre in centres:
        dist, n = _wall_near(mesh, centre, k.axis, -0.5, k.depth + 0.5, band, samples)
        dists.append(dist)
        faces += n
    wall, failed = _wall_verdict(label, "skin over the pocket", k, np.concatenate(dists) if dists else np.empty(0), faces, detail)
    if failed:
        return failed
    warned = _mouth_warn(label, k, mesh, wall, detail)
    if warned:
        return warned
    return _closing(label, k, wall, detail, "may rattle before the glue sets" if gap_max > loose_gap else None)


def _rim_clip_fit(mesh: trimesh.Trimesh, connector: Mapping[str, Any], constraints: Constraints, samples: int) -> dict[str, Any]:
    k = _kadi(connector, constraints)
    form = str(connector.get("form", "cap"))
    tab_t = float(connector["tab_t_mm"])
    strain_max = float(connector["strain_pct_max"])
    grip_min = float(connector["grip_min_mm"])
    allowed = float(connector.get("max_strain_pct", 1.0))
    creep = str(connector.get("creep_class", "high"))
    min_grip = float(connector.get("min_grip_mm", 0.05))
    tilt = min(k.tilt_deg, 180.0 - k.tilt_deg)  # a seat faces up or down; either prints
    noun = {"cap": "cap", "lid": "box", "rim": "rim"}[form]
    detail = _detail(
        k,
        form=form,
        range_mm=connector.get("range_mm"),
        tab_t_mm=tab_t,
        tab_len_mm=connector.get("tab_len_mm"),
        strain_pct_max=round(strain_max, 3),
        max_strain_pct=allowed,
        grip_min_mm=round(grip_min, 3),
        creep_class=creep,
        axis_tilt_deg=round(tilt, 2),
        method="tab bending strain (3 t δ / 2 L²) over the base's size class against the material's limit; creep class",
    )
    label = f"{noun} clip" if form != "lid" else "lid clip"
    if creep == "high":
        return check("fail", f"{label} · {k.material_id or 'this finish'} would lose its grip (creeps under load)", value=None, unit="", detail=detail)
    if tilt > CONNECTOR_AXIS_TOLERANCE_DEG:
        return check("fail", f"{label} · leans {_fmt(tilt, 0)}°, the seat must face up or down", value=round(tilt, 2), unit="deg",
                     threshold=CONNECTOR_AXIS_TOLERANCE_DEG, detail=detail)
    if tab_t < k.min_wall:
        return check("fail", f"{label} · tabs {_fmt(tab_t)} mm, too thin (min {_fmt(k.min_wall)} mm)", value=tab_t, unit="mm", threshold=k.min_wall, detail=detail)
    if strain_max > allowed:
        biggest = {"cap": "the largest cap", "lid": "the tightest box", "rim": "the thickest rim"}[form]
        return check("fail", f"{label} · tabs would crack on {biggest} ({_fmt(strain_max)} % strain)", value=round(strain_max, 2), unit="%", threshold=allowed, detail=detail)
    smallest = {"cap": "the smallest cap", "lid": "the loosest box", "rim": "the thinnest rim"}[form]
    return _closing(label, k, tab_t, detail, f"may sit loose on {smallest}" if grip_min < min_grip else None)


_FIT_CHECKS = {"socket": _socket_fit, "dovetail": _dovetail_fit, "thread": _thread_fit, "magnet": _magnet_fit, "rim_clip": _rim_clip_fit}


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
