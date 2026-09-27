"""Shared assertions for the carrier templates (recipe step 5): descriptor vs family, envelope, frames, holes."""

from __future__ import annotations

import numpy as np
import pytest
import trimesh
from shapely.geometry import Point, Polygon
from shapely.ops import unary_union
from trimesh.proximity import closest_point

from aakar_geometry import families
from aakar_geometry.contracts import validate

from conftest import content_source


def descriptor_matches_family(template) -> dict:
    """The descriptor validates, stays inside its family's content slot and names the slot's anchors."""
    desc = template.descriptor()
    validate("template-descriptor", desc)
    slot = families.content_slot(template.family)
    assert set(desc["features_supported"]) <= set(slot["accepts"]), (template.ref(), slot["accepts"])
    assert {a["id"] for a in desc["anchors"]} == set(slot["anchors"]), (template.ref(), slot["anchors"])
    for anchor in desc["anchors"]:
        assert set(anchor.get("accepts", desc["features_supported"])) <= set(desc["features_supported"]), anchor
        if anchor["kind"] == "surface":
            assert len(anchor["size_mm"]) == 2 and min(anchor["size_mm"]) >= 8, anchor
        else:
            assert len(anchor["bounds_mm"]) == 3, anchor
    assert desc["min_feature_mm"] == 0.8
    assert desc["environment"] == families.family(template.family).get("environment", "studio")
    return desc


def check_body(template, params) -> trimesh.Trimesh:
    """Watertight, on Z = 0, centred in X/Y, longest side inside the family envelope, fits the bed."""
    mesh = template.build_body(params)
    assert mesh.is_watertight and mesh.is_winding_consistent and mesh.volume > 0, (template.ref(), params)
    lo, hi = mesh.bounds
    assert lo[2] == pytest.approx(0.0, abs=1e-6)
    assert (lo[0] + hi[0]) / 2 == pytest.approx(0.0, abs=1e-6) and (lo[1] + hi[1]) / 2 == pytest.approx(0.0, abs=1e-6)
    envelope = families.size_envelope(template.family)
    assert envelope is not None
    longest = float(max(mesh.extents))
    assert envelope[0] - 1e-6 <= longest <= envelope[1] + 1e-6, (template.ref(), params, longest, envelope)
    assert np.all(np.sort(mesh.extents) <= np.sort(template.constraints.bed_mm) + 1e-6)
    return mesh


def check_frames(template, params, mesh: trimesh.Trimesh, tolerance_mm: float = 0.05) -> None:
    """Every surface anchor is a right-handed frame whose centre and corners lie on the skin, facing out."""
    for anchor in template.anchors:
        frame = template.anchor_frame(anchor.id, params)
        assert np.allclose(np.cross(frame.u, frame.v), frame.normal), anchor.id
        assert frame.size_mm is not None and min(frame.size_mm) >= 8, (anchor.id, frame.size_mm)
        points = np.vstack([frame.origin[None, :], frame.corners()])
        _, distance, triangle = closest_point(mesh, points)
        assert distance.max() < tolerance_mm, (anchor.id, distance)
        assert float(np.dot(mesh.face_normals[triangle[0]], frame.normal)) > 0.999, anchor.id
        outside = frame.origin + 0.2 * frame.normal
        inside = frame.origin - 0.2 * frame.normal
        assert list(mesh.contains([outside, inside])) == [False, True], anchor.id


def relief(anchor: str, mode: str = "emboss", relief_mm: float = 0.6, name: str = "photo.png") -> dict:
    return {"type": "relief_image", "source": content_source(name, "png"), "anchor": anchor, "mode": mode, "relief_mm": relief_mm}


def section(mesh: trimesh.Trimesh, z: float) -> Polygon:
    """The mesh's cross-section at height ``z`` as one shapely area (holes as interiors)."""
    path = mesh.section(plane_origin=[0, 0, z], plane_normal=[0, 0, 1])
    assert path is not None, z
    drop = np.eye(4)
    drop[2, 3] = -z  # onto z = 0, keeping world X/Y
    planar, _ = path.to_planar(to_2D=drop)
    return unary_union(list(planar.polygons_full))


def round_holes(area: Polygon) -> list[tuple[Point, float, float]]:
    """``(centre, narrowest radius, widest radius)`` of every hole in ``area``."""
    polys = list(area.geoms) if hasattr(area, "geoms") else [area]
    out = []
    for poly in polys:
        for ring in poly.interiors:
            hole = Polygon(ring)
            centre = hole.centroid
            radii = np.hypot(*(np.asarray(ring.coords)[:, :2] - [centre.x, centre.y]).T)
            out.append((centre, float(hole.exterior.distance(centre)), float(radii.max())))
    return out
