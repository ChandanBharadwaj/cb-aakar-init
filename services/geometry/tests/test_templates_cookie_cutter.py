"""``cookie_cutter@1`` (Saancha): the wall ring and solid plate measured on the built solid, the stamp face, the skin rule."""

from __future__ import annotations

import numpy as np
import pytest
from shapely.geometry import Polygon, box

from aakar_geometry.contracts import validate
from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.features.fetch import LocalFileFetcher
from aakar_geometry.pipeline import build_design, is_completed
from aakar_geometry.templates import REGISTRY, register
from aakar_geometry.templates.cookie_cutter import ANCHOR_MARGIN_MM, OUTLINES, WALL_MM, CookieCutter, outline_polygon
from aakar_geometry.templates.plates import MIN_SKIN_MM

from carrier_checks import relief, section
from conftest import DESIGN_ID, JOB_ID


def test_descriptor_params_and_no_hardware():
    desc = CookieCutter.descriptor()
    validate("template-descriptor", desc)
    assert (desc["id"], desc["family"], desc["environment"]) == ("cookie_cutter", "cookie_cutter", "kitchen_marble")
    p = desc["params"]
    assert p["outline"]["options"] == ["round", "scalloped", "square", "heart", "star"]
    assert (p["size_mm"]["min"], p["size_mm"]["max"], p["size_mm"]["default"]) == (50, 100, 70.0)
    assert (p["plate_mm"]["min"], p["plate_mm"]["max"], p["plate_mm"]["default"]) == (3.0, 4.0, 3.0)
    assert (p["blade_mm"]["min"], p["blade_mm"]["max"], p["blade_mm"]["default"]) == (4, 8, 6.0)
    assert [a["id"] for a in desc["anchors"]] == ["outline"]
    assert desc["anchors"][0]["accepts"] == ["relief_image"] and desc["features_supported"] == ["relief_image"]
    assert desc["hardware"] == [] and CookieCutter.hardware_for(CookieCutter.validate({})) == []


@pytest.mark.parametrize("outline", OUTLINES)
@pytest.mark.parametrize("size, plate, blade", [(50, 3.0, 4), (70, 3.0, 6), (100, 4.0, 8)])
def test_a_solid_plate_under_a_wall_ring_of_wall_mm(outline, size, plate, blade):
    params = CookieCutter.validate({"outline": outline, "size_mm": size, "plate_mm": plate, "blade_mm": blade})
    mesh = CookieCutter.build_body(params)
    assert max(mesh.extents) == pytest.approx(size, abs=0.05)
    assert mesh.bounds[1][2] == pytest.approx(plate + blade, abs=1e-6)
    # the plate is solid: one area with no holes, the full outline
    plate_cut = section(mesh, plate / 2)
    assert isinstance(plate_cut, Polygon) and len(plate_cut.interiors) == 0
    assert plate_cut.area == pytest.approx(outline_polygon(outline, size).area, rel=0.01)
    # the wall is a closed ring WALL_MM thick everywhere, the same outline outside
    ring = section(mesh, plate + blade / 2)
    assert isinstance(ring, Polygon) and len(ring.interiors) == 1
    assert ring.exterior.distance(ring.interiors[0]) == pytest.approx(WALL_MM, abs=0.05)
    assert ring.area == pytest.approx(plate_cut.area - Polygon(ring.interiors[0]).area, rel=1e-6)
    assert mesh.volume == pytest.approx(plate_cut.area * plate + ring.area * blade, rel=0.02)


@pytest.mark.parametrize("outline", OUTLINES)
def test_the_stamp_face_is_flat_at_plate_mm_inside_the_wall(outline):
    params = CookieCutter.validate({"outline": outline, "size_mm": 50})
    frame = CookieCutter.anchor_frame("outline", params)
    w, h = frame.size_mm
    assert min(w, h) >= 8.0, (outline, frame.size_mm)  # room for a picture even at the smallest size
    assert frame.origin[2] == pytest.approx(3.0) and np.allclose(frame.normal, [0, 0, 1])
    rect = box(frame.origin[0] - w / 2, frame.origin[1] - h / 2, frame.origin[0] + w / 2, frame.origin[1] + h / 2)
    assert outline_polygon(outline, 50).buffer(-(WALL_MM + ANCHOR_MARGIN_MM)).buffer(1e-6).contains(rect), outline
    # from above, the first thing a ray meets at every corner is the stamp face, not the wall's cutting edge
    mesh = CookieCutter.build_body(params)
    corners = frame.corners()
    hits, _, _ = mesh.ray.intersects_location(np.c_[corners[:, :2], np.full(4, 30.0)], np.tile([0, 0, -1.0], (4, 1)), multiple_hits=False)
    assert len(hits) == 4 and np.allclose(hits[:, 2], 3.0, atol=1e-6)


@pytest.mark.parametrize(
    "plate, features, ok",
    [
        (3.0, [relief("outline", "deboss", 0.8)], True),  # 3.0 − 0.8 = 2.2 mm of plate left
        (3.0, [relief("outline", "deboss", 1.5)], True),  # 1.5 left: the deepest the anchor allows
        (3.0, [relief("outline", "deboss", 1.9)], False),  # 1.1 < MIN_SKIN_MM
        (3.0, [relief("outline", "emboss", 1.5)], True),  # raised content never counts
        (4.0, [relief("outline", "deboss", 1.9)], True),
    ],
)
def test_a_cut_in_picture_leaves_plastic_in_the_plate(plate, features, ok):
    params = CookieCutter.validate({"plate_mm": plate})
    if ok:
        CookieCutter.validate_content(params, features)
        return
    with pytest.raises(ParamOutOfRange) as exc:
        CookieCutter.validate_content(params, features)
    assert exc.value.keys == ["features[0].relief_mm", "plate_mm"]
    assert "stamp plate" in exc.value.message and exc.value.detail["min_skin_mm"] == MIN_SKIN_MM


def test_the_pipeline_completes_with_the_food_contact_note(content_dir, local_storage):
    if (CookieCutter.id, CookieCutter.version) not in REGISTRY:  # the pipeline resolves templates by ref
        register(CookieCutter)
    request = {
        "job_id": JOB_ID,
        "design_id": DESIGN_ID,
        "version_no": 1,
        "outputs": ["stl"],
        "spec": {
            "spec_version": "1.0",
            "family": "cookie_cutter",
            "template": "cookie_cutter@1",
            "params": {"outline": "heart", "size_mm": 60, "blade_mm": 7},
            "features": [relief("outline", "deboss", 0.8)],
            "material": "basic_white",
        },
    }
    payload = build_design(request, storage=local_storage, fetcher=LocalFileFetcher(content_dir))
    assert is_completed(payload), payload
    validate("design.completed", payload)
    assert payload["hardware"] == []
    note = payload["karigar_note"]
    assert "heart" in note and "7 mm" in note and "hand-wash only" in note and "face down" in note
    assert "roll the dough 7 mm thick" in note  # the cutting depth is the dough thickness
    assert "mesh" not in note.lower() and "stl" not in note.lower()
