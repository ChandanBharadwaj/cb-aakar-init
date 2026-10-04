"""Mesh booleans through ``trimesh.boolean`` with the ``manifold`` engine (manifold3d).

Inputs must be volumes (watertight, consistently wound, positive volume: exactly what Manifold
needs); results are checked the same way, so a boolean either returns a printable solid or raises
``GeometryError``. Never call the engine directly from a template: go through here.
"""

from __future__ import annotations

import logging
from typing import Any

import trimesh

from ..errors import GeometryError

log = logging.getLogger("aakar.geometry.features.booleans")

ENGINE = "manifold"
_REL_TOL = 1e-4  # Manifold works in float32; volumes compare to this relative tolerance


def _volume(mesh: trimesh.Trimesh) -> float:
    try:
        return float(mesh.volume)
    except Exception:  # pragma: no cover - degenerate input
        return float("nan")


def _describe(mesh: trimesh.Trimesh) -> dict[str, Any]:
    return {
        "faces": int(len(mesh.faces)),
        "watertight": bool(mesh.is_watertight),
        "winding_consistent": bool(mesh.is_winding_consistent),
        "volume_mm3": round(_volume(mesh), 3),
    }


def _run(operation: str, a: trimesh.Trimesh, b: trimesh.Trimesh) -> trimesh.Trimesh:
    for name, mesh in (("a", a), ("b", b)):
        if mesh is None or mesh.is_empty:
            raise GeometryError(f"Boolean {operation}: operand {name} is empty", {"operand": name})
    try:
        fn = trimesh.boolean.union if operation == "union" else trimesh.boolean.difference
        result = fn([a, b], engine=ENGINE, check_volume=True)
    except ValueError as exc:  # "Not all meshes are volumes!"
        raise GeometryError(
            f"Boolean {operation}: an operand is not a closed volume",
            {"error": str(exc), "a": _describe(a), "b": _describe(b)},
        ) from exc
    except Exception as exc:  # engine missing or crashed
        raise GeometryError(
            f"Boolean {operation} failed in the {ENGINE} engine",
            {"error": f"{type(exc).__name__}: {exc}", "a": _describe(a), "b": _describe(b)},
        ) from exc
    result.merge_vertices()
    result.remove_unreferenced_vertices()
    va, vb, vr = _volume(a), _volume(b), _volume(result)
    tol = _REL_TOL * (abs(va) + abs(vb)) + 1e-6
    ok = bool(len(result.faces) > 0 and result.is_watertight and result.is_winding_consistent and vr > 0)
    if ok and operation == "union":
        ok = vr >= max(va, vb) - tol
    if ok and operation == "difference":
        ok = vr <= va + tol
    if not ok:
        raise GeometryError(
            f"Boolean {operation} did not produce a closed volume",
            {"a": _describe(a), "b": _describe(b), "result": _describe(result)},
        )
    return result


def union(a: trimesh.Trimesh, b: trimesh.Trimesh) -> trimesh.Trimesh:
    """``a ∪ b`` as a new watertight mesh."""
    return _run("union", a, b)


def difference(a: trimesh.Trimesh, b: trimesh.Trimesh) -> trimesh.Trimesh:
    """``a − b`` as a new watertight mesh."""
    return _run("difference", a, b)


def available() -> bool:
    """True when manifold3d imports (used by health checks and tests)."""
    try:
        import manifold3d  # noqa: F401

        return True
    except Exception:  # pragma: no cover
        return False


__all__ = ["ENGINE", "available", "difference", "union"]
