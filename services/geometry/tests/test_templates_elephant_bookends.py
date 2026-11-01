"""``elephant_bookends@1`` (Kitaab): the L of base and elephant measured on the built solid, the mirror between left and
right, the sand cavity's bridged cells, the centre of gravity of the tallest, shallowest bookend with the cavity empty,
and the face anchor on the flat outer flank."""

from __future__ import annotations

import math

import numpy as np
import pytest
from shapely.geometry import box

from aakar_geometry.templates.elephant_bookends import (
    BASE_MM,
    CAVITY_H_MM,
    FACE_MARGIN_MM,
    MAX_BRIDGE_MM,
    ElephantBookends,
    Layout,
)

from carrier_checks import check_body, check_frames, section


@pytest.mark.parametrize("height, depth, thick", [(120, 90, 20), (160, 110, 24), (200, 130, 30)])
def test_left_and_right_are_mirror_images_of_one_l(height, depth, thick):
    left = ElephantBookends.validate({"side": "left", "height_mm": height, "depth_mm": depth, "thickness_mm": thick})
    right = {**left, "side": "right"}
    a, b = check_body(ElephantBookends, left), check_body(ElephantBookends, right)
    L = Layout(left)
    assert a.extents == pytest.approx([L.W, depth, height], abs=1e-6) and L.W < L.hp
    assert a.volume == pytest.approx(b.volume, rel=1e-4)
    assert a.center_mass[[1, 2]] == pytest.approx(b.center_mass[[1, 2]], abs=0.05)
    assert a.center_mass[0] == pytest.approx(-b.center_mass[0], abs=0.05) and abs(a.center_mass[0]) > 0.5
    # the upright stands at the front: above the base only its thickness of plastic, from the front edge back
    upper = section(a, BASE_MM + 5.0)
    assert upper.bounds[1] == pytest.approx(-depth / 2.0, abs=1e-3) and upper.bounds[3] == pytest.approx(-depth / 2.0 + thick, abs=1e-3)


@pytest.mark.parametrize("depth", [90, 110, 130])
def test_the_sand_cavity_is_open_below_and_no_roof_bridges_more_than_40_mm(depth):
    params = ElephantBookends.validate({"depth_mm": depth})
    L = Layout(params)
    mesh = ElephantBookends.build_body(params)
    assert L.cell_d <= MAX_BRIDGE_MM and L.cells == math.ceil((depth - 6.0 + 3.0) / 43.0)
    cut = section(mesh, CAVITY_H_MM / 2.0)
    assert len(cut.interiors) == L.cells  # each cell is open to the bed, its walls and ribs stand on it
    for y in L.cell_centres():
        hits, _, _ = mesh.ray.intersects_location([[0.0, y, -5.0]], [[0, 0, 1.0]], multiple_hits=True)
        assert min(hits[:, 2]) == pytest.approx(CAVITY_H_MM, abs=1e-6)  # first plastic above the bed is the roof


@pytest.mark.parametrize("side", ["left", "right"])
def test_the_tallest_shallowest_bookend_keeps_its_centre_of_gravity_inside_the_base_when_empty(side):
    params = ElephantBookends.validate({"side": side, "height_mm": 200, "depth_mm": 90, "thickness_mm": 30})
    mesh = ElephantBookends.build_body(params)
    lo, hi = mesh.bounds
    x, y, _ = mesh.center_mass
    margin = min(x - lo[0], hi[0] - x, y - lo[1], hi[1] - y)
    assert margin >= 5.0, margin


def test_no_face_overhangs_more_than_45_degrees_except_the_bridged_cavity_roof():
    for side in ("left", "right"):
        mesh = ElephantBookends.build_body(ElephantBookends.validate({"side": side, "height_mm": 200, "depth_mm": 90}))
        z = mesh.triangles_center[:, 2]
        nz = mesh.face_normals[:, 2]
        free = (z > 0.05) & (np.abs(z - CAVITY_H_MM) > 1e-3)
        assert nz[free].min() >= -math.sin(math.radians(45.0)) - 1e-6


@pytest.mark.parametrize("overrides", [{}, {"side": "right", "height_mm": 120, "thickness_mm": 20}])
def test_the_face_is_a_centred_rectangle_inside_the_flank_on_the_outside(overrides):
    params = ElephantBookends.validate(overrides)
    L = Layout(params)
    mesh = check_body(ElephantBookends, params)
    check_frames(ElephantBookends, params, mesh)
    frame = ElephantBookends.anchor_frame("face", params)
    assert np.allclose(frame.normal, [0, -1, 0]) and frame.origin[1] == pytest.approx(-L.D / 2.0)
    assert frame.origin[0] == pytest.approx(0.0, abs=1e-6)
    w, h = frame.size_mm
    rect = box(-w / 2.0, frame.origin[2] - h / 2.0, w / 2.0, frame.origin[2] + h / 2.0)
    assert L.profile().buffer(-FACE_MARGIN_MM + 1e-3).contains(rect)
    assert rect.bounds[1] >= BASE_MM + FACE_MARGIN_MM - 1e-3 and min(w, h) >= 8.0
