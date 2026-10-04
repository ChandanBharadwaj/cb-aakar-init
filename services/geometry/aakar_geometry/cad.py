"""Thin layer over build123d: 2D shapely profiles -> OCCT solids -> watertight trimesh (mm, Z-up).

Templates describe their geometry as extruded 2D profiles plus rigid transforms and booleans, so
this is the only module that touches the CAD kernel.
"""

from __future__ import annotations

import logging
from typing import Iterable, Sequence

import numpy as np
import trimesh
from shapely.geometry import Polygon

log = logging.getLogger("aakar.geometry.cad")

try:  # build123d (OCCT) - the preferred kernel (PLAN §7.1)
    from build123d import Align, Axis, Cone, Face, Location, Part, Plane, Polyline, extrude, make_face

    KERNEL = "build123d"
    logging.getLogger("build123d").setLevel(logging.WARNING)  # it logs every extrude at INFO
except Exception as exc:  # pragma: no cover - only when the OCP wheel is unusable
    KERNEL = "unavailable"
    _IMPORT_ERROR = exc

TESSELLATION_TOLERANCE_MM = 0.05
TESSELLATION_ANGULAR_RAD = 0.3


def _require_kernel() -> None:
    if KERNEL != "build123d":  # pragma: no cover
        raise RuntimeError(f"build123d is not importable: {_IMPORT_ERROR!r}")


def _ring(coords: Sequence[tuple[float, float]]) -> "Polyline":
    pts = [(float(x), float(y)) for x, y in coords]
    if pts[0] == pts[-1]:
        pts = pts[:-1]
    return Polyline(*pts, close=True)


def face_from_polygon(polygon: Polygon, plane: "Plane | None" = None) -> "Face":
    """A planar face (with holes) from a shapely polygon, placed on ``plane`` (default XY)."""
    _require_kernel()
    if polygon.is_empty or polygon.area <= 0:
        raise ValueError("cannot make a face from an empty polygon")
    face = make_face(_ring(list(polygon.exterior.coords)))
    for interior in polygon.interiors:
        face -= make_face(_ring(list(interior.coords)))
    return (plane or Plane.XY) * face


def prism(polygon: Polygon, thickness: float, plane: "Plane | None" = None) -> "Part":
    """Extrude a polygon on ``plane`` along the plane normal by ``thickness`` (mm)."""
    return extrude(face_from_polygon(polygon, plane), amount=float(thickness))


def frustum(r_bottom: float, r_top: float, height: float) -> "Part":
    """A cone frustum standing on the XY plane: radius ``r_bottom`` at z = 0, ``r_top`` at z = ``height`` (mm)."""
    _require_kernel()
    return Cone(float(r_bottom), float(r_top), float(height), align=(Align.CENTER, Align.CENTER, Align.MIN))


def rotate_x(part: "Part", degrees: float) -> "Part":
    """Rotate about the global X axis; positive angles turn +Y towards +Z (right-hand rule)."""
    return part.rotate(Axis.X, float(degrees))


def translate(part: "Part", dx: float = 0.0, dy: float = 0.0, dz: float = 0.0) -> "Part":
    return part.moved(Location((float(dx), float(dy), float(dz))))


def union(parts: Iterable["Part"]) -> "Part":
    result = None
    for p in parts:
        result = p if result is None else result + p
    if result is None:
        raise ValueError("union of nothing")
    return result


def cut(part: "Part", tools: Iterable["Part"]) -> "Part":
    for tool in tools:
        part = part - tool
    return part


def to_trimesh(
    part: "Part",
    tolerance_mm: float = TESSELLATION_TOLERANCE_MM,
    angular_tolerance_rad: float = TESSELLATION_ANGULAR_RAD,
) -> trimesh.Trimesh:
    """Tessellate an OCCT solid to a merged, outward-wound trimesh in mm."""
    _require_kernel()
    verts, tris = part.tessellate(tolerance_mm, angular_tolerance_rad)
    vertices = np.array([[v.X, v.Y, v.Z] for v in verts], dtype=np.float64)
    faces = np.array(tris, dtype=np.int64)
    mesh = trimesh.Trimesh(vertices, faces, process=True, validate=True)
    mesh.merge_vertices()
    mesh.remove_unreferenced_vertices()
    if mesh.is_watertight and mesh.volume < 0:
        mesh.invert()
    if not mesh.is_winding_consistent:
        trimesh.repair.fix_normals(mesh)
    return mesh


def solid_volume_mm3(part: "Part") -> float:
    return float(part.volume)
