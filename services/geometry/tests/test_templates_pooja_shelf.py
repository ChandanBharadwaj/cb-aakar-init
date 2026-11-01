"""``pooja_shelf@1`` (Aradhana): keyholes, plate / shelf / gusset / lip thicknesses, the skin rule, the anchor band."""

from __future__ import annotations

import numpy as np
import pytest
import trimesh
from shapely.geometry import LineString, Point, Polygon

from aakar_geometry.contracts import validate
from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.features.fetch import LocalFileFetcher
from aakar_geometry.pipeline import build_design, is_completed
from aakar_geometry.templates.plates import HOLE_ALLOWANCE_MM, MIN_SKIN_MM
from aakar_geometry.templates.pooja_shelf import (
    ANCHOR_MARGIN_MM,
    BACK_H_MM,
    CLEAR_MM,
    GUSSET_MM,
    KEY_BELOW_TOP_MM,
    KEY_D_MM,
    KEY_INSET_MM,
    LIP_H_MM,
    LIP_T_MM,
    PLATE_MM,
    SHELF_MM,
    SLOT_RISE_MM,
    SLOT_W_MM,
    Layout,
    PoojaShelf,
)

from carrier_checks import section
from conftest import DESIGN_ID, JOB_ID

KEY_CUT = KEY_D_MM + HOLE_ALLOWANCE_MM
SLOT_CUT = SLOT_W_MM + HOLE_ALLOWANCE_MM


def _plate_section(mesh: trimesh.Trimesh, y: float) -> Polygon:
    """The plate's section on the plane Y = ``y`` as an (X, −Z) area: keyholes come out as interiors."""
    turned = mesh.copy()
    turned.apply_transform(trimesh.transformations.rotation_matrix(np.pi / 2, [1, 0, 0]))  # (x, y, z) → (x, −z, y)
    return section(turned, y)


def _motif(mode: str, depth_mm: float) -> dict:
    return {"type": "motif", "motif_id": "lotus", "anchor": "face", "mode": mode, "depth_mm": depth_mm}


def test_descriptor_params_and_no_hardware():
    desc = PoojaShelf.descriptor()
    assert (desc["id"], desc["family"], desc["environment"]) == ("pooja_shelf", "pooja_accessory", "teak_table_candlelight")
    p = desc["params"]
    assert (p["width_mm"]["min"], p["width_mm"]["max"], p["width_mm"]["default"]) == (80, 250, 150.0)
    assert (p["depth_mm"]["min"], p["depth_mm"]["max"], p["depth_mm"]["default"]) == (50, 100, 70.0)
    assert (p["lip"]["type"], p["lip"]["default"]) == ("boolean", True)
    assert [a["id"] for a in desc["anchors"]] == ["face"]
    assert desc["hardware"] == [] and PoojaShelf.hardware_for(PoojaShelf.validate({})) == []
    assert (BACK_H_MM, PLATE_MM, SHELF_MM) == (70.0, 6.0, 6.0)
    assert PLATE_MM - 0.8 >= MIN_SKIN_MM  # the SPEC's deepest cut-in leaves the skin behind the plate


@pytest.mark.parametrize("width, depth", [(80, 50), (150, 70), (250, 100)])
def test_two_keyholes_cut_through_the_plate_at_the_right_size(width, depth):
    params = PoojaShelf.validate({"width_mm": width, "depth_mm": depth})
    mesh = PoojaShelf.build_body(params)
    L = Layout(params)
    z_key = BACK_H_MM - KEY_BELOW_TOP_MM
    area = _plate_section(mesh, L.y_front + PLATE_MM / 2)
    polys = list(area.geoms) if hasattr(area, "geoms") else [area]
    holes = sorted((Polygon(ring) for poly in polys for ring in poly.interiors), key=lambda h: h.centroid.x)
    assert len(holes) == 2
    for hole, x_key in zip(holes, [-(width / 2 - KEY_INSET_MM), width / 2 - KEY_INSET_MM]):
        centre = Point(x_key, -z_key)  # the section's Y is −Z
        assert hole.exterior.distance(centre) == pytest.approx(KEY_CUT / 2, abs=2e-3)  # the screw-head circle
        x0, y0, x1, y1 = hole.bounds
        assert x1 - x0 == pytest.approx(KEY_CUT, abs=0.01) and (x0 + x1) / 2 == pytest.approx(x_key, abs=1e-6)
        assert -y1 == pytest.approx(z_key - KEY_CUT / 2, abs=0.01)  # the circle's bottom
        assert -y0 == pytest.approx(z_key + SLOT_RISE_MM, abs=0.01)  # the slot's rounded top
        chord = hole.intersection(LineString([(x_key - 10, -(z_key + 5)), (x_key + 10, -(z_key + 5))]))
        assert chord.length == pytest.approx(SLOT_CUT, abs=1e-6)  # the shank slot
        # through the plate: a ray along +Y through the circle's centre meets no plastic, one beside it meets both faces
        hits, _, _ = mesh.ray.intersects_location([[x_key, -60.0, z_key]], [[0, 1.0, 0]], multiple_hits=True)
        assert len(hits) == 0
        hits, _, _ = mesh.ray.intersects_location([[x_key + KEY_CUT, -60.0, z_key]], [[0, 1.0, 0]], multiple_hits=True)
        assert sorted(hits[:, 1]) == pytest.approx([L.y_front, L.y_front + PLATE_MM], abs=1e-6)
    assert z_key + SLOT_RISE_MM + 5.0 <= BACK_H_MM  # plastic stays above the slot


@pytest.mark.parametrize("lip", [True, False])
@pytest.mark.parametrize("depth", [50, 100])
def test_plate_shelf_gusset_and_lip_measure_as_drawn(lip, depth):
    params = PoojaShelf.validate({"depth_mm": depth, "lip": lip})
    mesh = PoojaShelf.build_body(params)
    L = Layout(params)
    y_back = L.y_front + PLATE_MM
    y_edge = L.y_front - depth  # the shelf's front edge
    assert mesh.extents[1] == pytest.approx(depth + PLATE_MM) and mesh.extents[2] == pytest.approx(BACK_H_MM)
    # the plate alone, above the gusset and below the keyholes: exactly PLATE_MM thick
    plate = section(mesh, 30.0)
    assert plate.geom_type == "Polygon" and plate.bounds[1::2] == pytest.approx((L.y_front, y_back), abs=1e-6)
    # inside the shelf: one slab from the front edge to the back of the plate
    slab = section(mesh, SHELF_MM / 2)
    assert slab.geom_type == "Polygon" and slab.bounds[1::2] == pytest.approx((y_edge, y_back), abs=1e-6)
    # half-way up the gusset the 45° slope stands GUSSET_MM / 2 in front of the plate
    brace = section(mesh, SHELF_MM + GUSSET_MM / 2)
    rear = max((brace.geoms if hasattr(brace, "geoms") else [brace]), key=lambda g: g.bounds[1])  # the lip may sit in front
    assert rear.bounds[1::2] == pytest.approx((L.y_front - GUSSET_MM / 2, y_back), abs=1e-6)
    # above the shelf, below the lip's top: the lip rail is a separate LIP_T_MM strip at the front edge, or nothing
    band = section(mesh, SHELF_MM + LIP_H_MM / 2)
    strips = sorted((g.bounds for g in (band.geoms if hasattr(band, "geoms") else [band])), key=lambda b: b[1])
    if lip:
        assert len(strips) == 2 and strips[0][1::2] == pytest.approx((y_edge, y_edge + LIP_T_MM), abs=1e-6)
    else:
        assert len(strips) == 1 and strips[0][1] > y_edge + GUSSET_MM / 2
    # a diya set on the front edge meets the lip's top, or the bare shelf
    top, _, _ = mesh.ray.intersects_location([[0.0, y_edge + LIP_T_MM / 2, 50.0]], [[0, 0, -1.0]], multiple_hits=False)
    assert top[0][2] == pytest.approx(SHELF_MM + (LIP_H_MM if lip else 0.0), abs=1e-6)
    assert mesh.body_count == 1 and mesh.is_watertight


@pytest.mark.parametrize(
    "features, ok",
    [
        ([_motif("deboss", 0.8)], True),  # the SPEC's cut-in: 6 − 0.8 = 5.2 mm stays
        ([{"type": "emboss_text", "text": "Om", "anchor": "face", "mode": "deboss", "depth_mm": PLATE_MM - MIN_SKIN_MM}], True),
        ([_motif("deboss", PLATE_MM - MIN_SKIN_MM + 0.1)], False),
        ([_motif("emboss", 1.5)], True),  # raised content never thins the plate
    ],
)
def test_a_cut_in_leaves_skin_behind_the_back_plate(features, ok):
    params = PoojaShelf.validate({})
    if ok:
        PoojaShelf.validate_content(params, features)
        return
    with pytest.raises(ParamOutOfRange) as exc:
        PoojaShelf.validate_content(params, features)
    assert exc.value.keys[0] == "features[0].depth_mm"
    assert "back plate" in exc.value.message and exc.value.detail["min_skin_mm"] == MIN_SKIN_MM


@pytest.mark.parametrize("width", [80, 250])
def test_the_face_anchor_sits_on_the_plate_front_between_the_gusset_and_the_keyholes(width):
    params = PoojaShelf.validate({"width_mm": width})
    mesh = PoojaShelf.build_body(params)
    L = Layout(params)
    frame = PoojaShelf.anchor_frame("face", params)
    assert np.allclose(frame.normal, [0, -1, 0]) and np.allclose(frame.u, [1, 0, 0]) and np.allclose(frame.v, [0, 0, 1])
    assert frame.origin[0] == pytest.approx(0.0) and frame.origin[1] == pytest.approx(L.y_front)
    w, h = frame.size_mm
    assert w == pytest.approx(width - 2 * ANCHOR_MARGIN_MM) and min(w, h) >= 8.0
    z_lo, z_hi = frame.origin[2] - h / 2, frame.origin[2] + h / 2
    assert z_lo == pytest.approx(SHELF_MM + GUSSET_MM + ANCHOR_MARGIN_MM)  # clear of the brace
    assert z_hi == pytest.approx(BACK_H_MM - KEY_BELOW_TOP_MM - KEY_CUT / 2 - CLEAR_MM)  # clear of the screw heads
    # the whole plate stands behind the anchor: a ray through its origin into the wall leaves at the back face
    hits, _, _ = mesh.ray.intersects_location([frame.origin - [0, 1.0, 0]], [[0, 1.0, 0]], multiple_hits=True)
    assert sorted(hits[:, 1]) == pytest.approx([L.y_front, L.y_front + PLATE_MM], abs=1e-6)
    assert float(np.ptp(mesh.vertices[:, 0])) == pytest.approx(width)  # the width is the envelope figure


def test_the_pipeline_completes_with_no_hardware_and_an_honest_note(content_dir, local_storage):
    request = {
        "job_id": JOB_ID,
        "design_id": DESIGN_ID,
        "version_no": 1,
        "outputs": ["stl"],
        "spec": {
            "spec_version": "1.0",
            "family": "pooja_accessory",
            "template": "pooja_shelf@1",
            "params": {"width_mm": 200, "depth_mm": 60, "lip": False},
            "features": [{"type": "emboss_text", "text": "Om Shanti", "anchor": "face", "depth_mm": 1.0}, _motif("deboss", 0.8)],
            "material": "basic_white",
        },
    }
    payload = build_design(request, storage=local_storage, fetcher=LocalFileFetcher(content_dir))
    assert is_completed(payload), payload
    validate("design.completed", payload)
    assert payload["hardware"] == [] and payload["printability"]["passed"] is True
    note = payload["karigar_note"]
    assert "“Om Shanti”" in note and "no lip" in note and "keyholes" in note and "not supplied" in note
    assert "200 mm wide" in note and "60 mm deep" in note
    assert "mesh" not in note.lower() and "stl" not in note.lower()
