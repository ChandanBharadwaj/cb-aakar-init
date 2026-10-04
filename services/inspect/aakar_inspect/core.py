"""``inspect_mesh``: Printability Report v1 + Print Estimate v1 for a mesh (PLAN §7.9, §7.10)."""

from __future__ import annotations

import logging
import time
from typing import Any, Mapping

import trimesh

from . import checks
from .contracts import validate
from .estimate import HeuristicEstimator, SlicerBackend, SlicerError, default_backend
from .settings import Constraints, SlicingSettings

log = logging.getLogger("aakar.inspect")


def geometry_summary(mesh: trimesh.Trimesh) -> dict[str, Any]:
    extents = [round(float(e), 3) for e in mesh.extents]
    volume = float(abs(mesh.volume)) if mesh.is_watertight else float(abs(mesh.convex_hull.volume))
    return {
        "bounds_mm": extents,  # width, depth, height
        "volume_cm3": round(volume / 1000.0, 3),
        "surface_cm2": round(float(mesh.area) / 100.0, 3),
        "triangles": int(len(mesh.faces)),
    }


def inspect_mesh(
    mesh: trimesh.Trimesh,
    constraints: Constraints | None = None,
    slicing: SlicingSettings | None = None,
    backend: SlicerBackend | None = None,
    *,
    validate_contracts: bool = True,
    connector: Mapping[str, Any] | None = None,
) -> tuple[dict[str, Any], dict[str, Any]]:
    """Return ``(printability_report, print_estimate)`` for a mm, Z-up mesh.

    The mesh is not mutated; if a repair makes it watertight the repaired copy feeds the other checks.
    Both dicts are validated against the contracts before being returned. ``connector`` (the Kadi block a
    hybrid template reports) adds the ``connector_fit`` check; without it the report is as before.
    """
    constraints = constraints or Constraints()
    slicing = slicing or SlicingSettings()
    backend = backend or default_backend()
    started = time.perf_counter()

    if not isinstance(mesh, trimesh.Trimesh):
        raise TypeError("inspect_mesh expects a trimesh.Trimesh (use aakar_inspect.loaders.load_mesh)")
    if len(mesh.faces) == 0:
        raise ValueError("mesh has no faces")

    manifold = checks.manifold(mesh)
    working = manifold.mesh

    stab = checks.stability(working)
    report_checks = {
        "manifold": manifold.check,
        "fits_bed": checks.fits_bed(working, constraints),
        "thinnest_wall": checks.thinnest_wall(working, constraints),
        "centre_of_gravity": checks.centre_of_gravity(stab),
        "tipping_margin": checks.tipping_margin(stab, constraints),
        "overhangs": checks.overhangs_skipped(),
        "load_capacity": checks.load_capacity_skipped(),
    }
    if connector:
        report_checks["connector_fit"] = checks.connector_fit(working, connector, constraints)

    try:
        estimate = backend.estimate(working, slicing, constraints)
    except SlicerError as exc:
        log.warning("slicer backend %s failed (%s); using heuristic", getattr(backend, "method", backend), exc)
        estimate = HeuristicEstimator().estimate(working, slicing, constraints)

    report = {
        "report_version": "1.0",
        "passed": all(c["status"] != "fail" for c in report_checks.values()),
        "geometry": geometry_summary(working),
        "checks": report_checks,
        "check_duration_ms": int(round((time.perf_counter() - started) * 1000)),
    }
    if validate_contracts:
        validate("printability-report", report)
        validate("print-estimate", estimate)
    return report, estimate
