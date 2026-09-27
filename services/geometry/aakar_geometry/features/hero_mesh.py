"""``hero_mesh`` (Roop): the customer's own 3D form, repaired, scaled into a volume anchor and fused on.

Steps: load with the inspect service's loaders → repair (``aakar_inspect.checks.manifold``: merge,
fix winding, fill holes; still open or empty → ``ContentUnusable``) → decimate above ``MAX_FACES``
(fast-simplification via ``Trimesh.simplify_quadric_decimation``) → orient (``lay_flat`` picks the
most stable pose from ``trimesh.poses.compute_stable_poses``, then ``yaw_deg`` about Z) → scale
(``contain`` into ``bounds_mm``, or ``longest`` to ``longest_mm``; a ``longest`` that overflows the
bounds raises, never clamps) → seat centred on the anchor's bottom plane → union with the body, or
return the placed form alone when the body is empty (the ``raw_print`` case).

Orientation runs before scaling because ``contain`` measures the axis-aligned box in the final pose.
glTF/GLB files are Y-up by convention and are turned Z-up; units do not matter because the form is
always rescaled.
"""

from __future__ import annotations

import logging
from typing import Any, Mapping

import numpy as np
import trimesh

from aakar_inspect import checks
from aakar_inspect.loaders import MeshLoadError, load_mesh_bytes

from ..errors import ContentUnusable, GeometryError, ParamOutOfRange
from .booleans import union
from .frames import AnchorFrame

log = logging.getLogger("aakar.geometry.features.hero_mesh")

MAX_FACES = 300_000
MAX_INPUT_FACES = 4_000_000
MIN_VOLUME_MM3 = 1.0
EMBED_MM = 0.5
Y_UP_FORMATS = ("glb", "gltf")
_Y_UP_TO_Z_UP = trimesh.transformations.rotation_matrix(np.pi / 2.0, [1.0, 0.0, 0.0])

UNREADABLE = "We couldn't read this model; please export it again as STL, OBJ or 3MF"
OPEN = "This model has gaps we couldn't close; please repair it in your 3D program and try again"
NO_VOLUME = "This model has no volume to print; please check it in your 3D program"
TOO_DETAILED = "This model is too detailed for us to work with; please export it with fewer triangles"


def load(data: bytes, fmt: str | None) -> trimesh.Trimesh:
    if not fmt:
        raise ContentUnusable(UNREADABLE, {"reason": "unknown format"})
    try:
        mesh = load_mesh_bytes(data, fmt)
    except MeshLoadError as exc:
        raise ContentUnusable(UNREADABLE, {"format": fmt, "error": str(exc)}) from exc
    except Exception as exc:  # trimesh loaders raise all sorts on corrupt files
        raise ContentUnusable(UNREADABLE, {"format": fmt, "error": f"{type(exc).__name__}: {exc}"}) from exc
    if len(mesh.faces) > MAX_INPUT_FACES:
        raise ContentUnusable(TOO_DETAILED, {"faces": int(len(mesh.faces)), "max_faces": MAX_INPUT_FACES})
    if fmt in Y_UP_FORMATS:
        mesh.apply_transform(_Y_UP_TO_Z_UP)
    return mesh


def repair(mesh: trimesh.Trimesh) -> trimesh.Trimesh:
    """Watertight, consistently wound copy with positive volume, or ``ContentUnusable``."""
    result = checks.manifold(mesh)
    fixed = result.mesh
    if result.check["status"] == "fail":
        raise ContentUnusable(OPEN, {"manifold": result.check})
    with np.errstate(all="ignore"):
        volume = float(fixed.volume)
    if not np.isfinite(volume) or volume < MIN_VOLUME_MM3:
        raise ContentUnusable(NO_VOLUME, {"volume_mm3": volume, "manifold": result.check})
    if fixed is mesh:
        fixed = mesh.copy()
    return fixed


def decimate(mesh: trimesh.Trimesh, max_faces: int = MAX_FACES) -> trimesh.Trimesh:
    """Reduce to about ``max_faces``; falls back to the input when the reduction breaks the surface."""
    if len(mesh.faces) <= max_faces:
        return mesh
    try:
        reduced = mesh.simplify_quadric_decimation(face_count=max_faces)
    except Exception as exc:  # fast-simplification missing or unhappy: keep the detailed mesh
        log.warning("decimation failed (%s); keeping %d faces", exc, len(mesh.faces))
        return mesh
    reduced = trimesh.Trimesh(vertices=reduced.vertices, faces=reduced.faces, process=True)
    if not (reduced.is_watertight and reduced.is_winding_consistent and reduced.volume > 0):
        fixed = checks.manifold(reduced)
        if fixed.check["status"] == "fail":
            log.warning("decimated mesh is open; keeping %d faces", len(mesh.faces))
            return mesh
        reduced = fixed.mesh
    return reduced


def lay_flat(mesh: trimesh.Trimesh) -> trimesh.Trimesh:
    """Most probable stable resting pose (Z up); the input pose when none can be computed."""
    try:
        transforms, probabilities = trimesh.poses.compute_stable_poses(mesh)
    except Exception as exc:  # pragma: no cover - convex hull failures on odd input
        log.warning("stable poses failed (%s); keeping the uploaded orientation", exc)
        return mesh
    if len(transforms) == 0:
        return mesh
    out = mesh.copy()
    out.apply_transform(transforms[int(np.argmax(probabilities))])
    return out


def orient(mesh: trimesh.Trimesh, orientation: str, yaw_deg: float) -> trimesh.Trimesh:
    out = lay_flat(mesh) if orientation == "lay_flat" else mesh.copy()
    yaw = float(yaw_deg or 0.0) % 360.0
    if yaw:
        out.apply_transform(trimesh.transformations.rotation_matrix(np.radians(yaw), [0.0, 0.0, 1.0]))
    return out


def scale(
    mesh: trimesh.Trimesh,
    fit: str,
    bounds_mm: tuple[float, float, float] | None,
    longest_mm: float | None,
    *,
    key: str = "longest_mm",
    anchor_label: str = "anchor",
) -> trimesh.Trimesh:
    extents = np.asarray(mesh.extents, dtype=np.float64)
    if np.any(extents <= 1e-9):
        raise ContentUnusable(NO_VOLUME, {"extents_mm": extents.tolist()})
    if fit == "contain":
        if bounds_mm is None:
            raise GeometryError("contain needs the anchor's bounds_mm", {"fit": fit})
        factor = float(np.min(np.asarray(bounds_mm, dtype=np.float64) / extents))
    elif fit == "longest":
        if longest_mm is None:
            raise GeometryError("longest needs longest_mm", {"fit": fit})
        factor = float(longest_mm) / float(extents.max())
        if bounds_mm is not None:
            scaled = extents * factor
            b = np.asarray(bounds_mm, dtype=np.float64)
            if np.any(scaled > b + 1e-6):
                max_fit = float(longest_mm) * float(np.min(b / scaled))
                raise ParamOutOfRange(
                    [key],
                    f"{float(longest_mm):g} mm is too big for the {anchor_label}; {max_fit:.0f} mm is the most that fits",
                    {"longest_mm": longest_mm, "max_longest_mm": round(max_fit, 2), "bounds_mm": list(b), "extents_mm": scaled.tolist()},
                )
    else:
        raise GeometryError("hero fit must be contain or longest", {"fit": fit})
    out = mesh.copy()
    out.apply_scale(factor)
    return out


def seat(mesh: trimesh.Trimesh) -> trimesh.Trimesh:
    """Centre the box in x and y and put its lowest point on z = 0 (local frame)."""
    out = mesh.copy()
    lo, hi = out.bounds
    out.apply_translation([-(lo[0] + hi[0]) / 2.0, -(lo[1] + hi[1]) / 2.0, -lo[2]])
    return out


def prepare(
    data: bytes,
    fmt: str | None,
    feature: Mapping[str, Any],
    frame: AnchorFrame,
    *,
    anchor_label: str = "anchor",
    key: str = "longest_mm",
    max_faces: int = MAX_FACES,
) -> trimesh.Trimesh:
    """Load → repair → decimate → orient → scale → seat, in the anchor's local frame (bottom on z = 0)."""
    mesh = repair(load(data, fmt))
    mesh = decimate(mesh, max_faces)
    mesh = orient(mesh, feature.get("orientation", "as_uploaded"), float(feature.get("yaw_deg", 0.0) or 0.0))
    longest = feature.get("longest_mm")
    mesh = scale(
        mesh,
        feature.get("fit", "contain"),
        frame.bounds_mm,
        float(longest) if longest is not None else None,
        key=key,
        anchor_label=anchor_label,
    )
    return seat(mesh)


def apply(
    body: trimesh.Trimesh | None,
    frame: AnchorFrame,
    feature: Mapping[str, Any],
    data: bytes,
    *,
    fmt: str | None = None,
    anchor_label: str = "anchor",
    key: str = "longest_mm",
) -> trimesh.Trimesh:
    """Place the hero form on the anchor and fuse it with ``body``; the form alone when the body is empty."""
    hero = prepare(data, fmt, feature, frame, anchor_label=anchor_label, key=key)
    if body is None or body.is_empty:
        return frame.place(hero)
    hero.apply_translation([0.0, 0.0, -EMBED_MM])  # sink into the carrier so the union has volume to grab
    placed = frame.place(hero)
    try:
        return union(body, placed)
    except GeometryError as exc:
        raise ContentUnusable(
            f"We couldn't fuse this model onto the {anchor_label}; try a different model or size",
            {"error": exc.message, **exc.detail},
        ) from exc


__all__ = ["EMBED_MM", "MAX_FACES", "apply", "decimate", "lay_flat", "load", "orient", "prepare", "repair", "scale", "seat"]
