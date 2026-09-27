import math

import pytest
from shapely.geometry import Polygon

from aakar_geometry.contracts import validate
from aakar_geometry.errors import InvalidSpec, ParamOutOfRange, UnknownTemplate
from aakar_geometry.templates import get_template, list_templates, parse_ref
from aakar_geometry.templates.jharokha_phone_stand import JharokhaPhoneStand, Layout, arch_rise_factor, cusped_arch


def test_descriptor_validates_and_matches_plan():
    desc = JharokhaPhoneStand.descriptor()
    validate("template-descriptor", desc)
    assert desc["id"] == "jharokha_phone_stand" and desc["version"] == 1 and desc["family"] == "phone_stand"
    assert desc["environment"] == "desk_oak"
    assert [a["id"] for a in desc["anchors"]] == ["side_left", "side_right", "back"]
    assert all(a["projection"] == "planar" for a in desc["anchors"])
    assert desc["constraints"] == {"min_wall_mm": 1.2, "max_overhang_deg": 55, "bed_mm": [250, 250, 250]}
    assert desc["features_supported"] == []
    assert set(desc["materials"]) == {
        "basic_white", "terracotta_matte", "terracotta_silk", "polished_brass", "sandalwood_silk", "indigo_matte",
    }
    p = desc["params"]
    assert (p["width_mm"]["min"], p["width_mm"]["max"], p["width_mm"]["default"], p["width_mm"]["handle"]) == (70, 110, 92, True)
    assert (p["depth_mm"]["min"], p["depth_mm"]["max"], p["depth_mm"]["default"], p["depth_mm"]["handle"]) == (60, 100, 78, True)
    assert (p["height_mm"]["min"], p["height_mm"]["max"], p["height_mm"]["default"], p["height_mm"]["handle"]) == (90, 150, 120, True)
    assert (p["tilt_deg"]["min"], p["tilt_deg"]["max"], p["tilt_deg"]["default"]) == (60, 78, 70)
    assert (p["lip_height_mm"]["min"], p["lip_height_mm"]["max"], p["lip_height_mm"]["default"]) == (8, 18, 12)
    assert (p["wall_mm"]["min"], p["wall_mm"]["max"], p["wall_mm"]["default"]) == (2.4, 4.0, 3.2)
    assert (p["arch_cusps"]["type"], p["arch_cusps"]["min"], p["arch_cusps"]["max"], p["arch_cusps"]["default"]) == ("integer", 3, 7, 5)


def test_registry_lookup():
    assert [t.ref() for t in list_templates()] == ["jharokha_phone_stand@1"]
    assert get_template("jharokha_phone_stand@1") is JharokhaPhoneStand
    assert parse_ref("jharokha_phone_stand@1") == ("jharokha_phone_stand", 1)
    with pytest.raises(UnknownTemplate):
        get_template("jharokha_phone_stand@2")
    with pytest.raises(UnknownTemplate):
        get_template("lotus_lamp@2")
    with pytest.raises(UnknownTemplate):
        get_template("no-version")


def test_validate_fills_defaults_and_keeps_types(example_spec):
    params = JharokhaPhoneStand.validate({})
    assert params == {
        "width_mm": 92.0, "depth_mm": 78.0, "height_mm": 120.0, "tilt_deg": 70.0,
        "lip_height_mm": 12.0, "wall_mm": 3.2, "arch_cusps": 5,
    }
    assert JharokhaPhoneStand.validate(example_spec["params"]) == params
    assert JharokhaPhoneStand.validate({"arch_cusps": 7.0})["arch_cusps"] == 7


def test_validate_rejects_out_of_range_listing_every_key():
    with pytest.raises(ParamOutOfRange) as exc:
        JharokhaPhoneStand.validate({"width_mm": 69.9, "arch_cusps": 8, "tilt_deg": 70})
    assert exc.value.keys == ["width_mm", "arch_cusps"]
    assert exc.value.code == "param_out_of_range"
    with pytest.raises(ParamOutOfRange):
        JharokhaPhoneStand.validate({"arch_cusps": 4.5})  # integers only, never rounded
    with pytest.raises(ParamOutOfRange):
        JharokhaPhoneStand.validate({"wall_mm": float("nan")})


def test_validate_rejects_unknown_keys_and_wrong_types():
    with pytest.raises(InvalidSpec):
        JharokhaPhoneStand.validate({"petal_count": 8})
    with pytest.raises(InvalidSpec):
        JharokhaPhoneStand.validate({"width_mm": "wide"})
    with pytest.raises(InvalidSpec):
        JharokhaPhoneStand.validate({"width_mm": True})


def test_validate_rejects_impossible_combination():
    # Tall, shallow and leaning far back: the back rest footprint eats the phone slot.
    with pytest.raises(ParamOutOfRange) as exc:
        JharokhaPhoneStand.validate({"depth_mm": 60, "height_mm": 150, "tilt_deg": 60})
    assert exc.value.keys == ["depth_mm", "height_mm", "tilt_deg"]
    assert "phone slot" in exc.value.message


def test_cusped_arch_shape():
    for cusps in (3, 5, 7):
        poly = cusped_arch(76.0, 60.0, cusps)
        assert isinstance(poly, Polygon) and poly.is_valid
        minx, miny, maxx, maxy = poly.bounds
        assert minx == pytest.approx(-38.0, abs=0.05) and maxx == pytest.approx(38.0, abs=0.05)
        assert miny == pytest.approx(0.0, abs=1e-6)
        assert maxy == pytest.approx(60.0 + 76.0 * arch_rise_factor(cusps), abs=0.1)
        assert poly.area > 76.0 * 60.0  # the arch adds area above the springing line


def test_layout_bounds_and_slot():
    layout = Layout(JharokhaPhoneStand.validate({}))
    assert layout.phone_slot == pytest.approx(28.3, abs=0.1)
    assert layout.phone_clearance == pytest.approx(92 - 6.4)
    assert layout.back_len * layout.sin + layout.wall * layout.cos == pytest.approx(120.0)


def test_example_spec_builds_watertight_within_bounds(built_example):
    template, params, mesh = built_example
    assert mesh.is_watertight and mesh.is_winding_consistent and mesh.volume > 0
    assert mesh.extents == pytest.approx([92, 78, 120], abs=1.0)
    assert mesh.bounds[0][2] == pytest.approx(0.0, abs=1e-6)
    assert abs(mesh.bounds[0][0] + mesh.bounds[1][0]) < 1e-6  # centred in X
    assert 30 < mesh.volume / 1000 < 60  # cm³ - a hollow-ish stand, not a brick
    assert template.karigar_note(params).startswith("A jharokha-arch phone stand, 92 mm wide with a 12 mm lip")


@pytest.mark.parametrize(
    "overrides",
    [
        {"width_mm": 70, "depth_mm": 60, "height_mm": 90, "tilt_deg": 78, "lip_height_mm": 8, "wall_mm": 2.4, "arch_cusps": 3},
        {"width_mm": 110, "depth_mm": 100, "height_mm": 150, "tilt_deg": 66, "lip_height_mm": 18, "wall_mm": 4.0, "arch_cusps": 7},
        {"tilt_deg": 60, "depth_mm": 100, "height_mm": 110},
    ],
)
def test_parameter_corners_build(overrides):
    params = JharokhaPhoneStand.validate(overrides)
    mesh = JharokhaPhoneStand.build(params)
    assert mesh.is_watertight
    assert mesh.extents == pytest.approx([params["width_mm"], params["depth_mm"], params["height_mm"]], abs=1.0)


def test_example_inspects_as_printable(built_example):
    from aakar_inspect import Constraints, inspect_mesh

    _, params, mesh = built_example
    report, estimate = inspect_mesh(mesh, Constraints(min_wall_mm=1.2))
    assert report["passed"] is True
    assert report["checks"]["thinnest_wall"]["value"] == pytest.approx(3.2, abs=0.15)
    assert report["checks"]["centre_of_gravity"]["status"] == "pass"
    assert report["checks"]["tipping_margin"]["status"] == "pass"
    grams = estimate["extruded_volume_cm3"] * 1.24
    assert 45 <= grams <= 70
    assert 3 * 3600 <= estimate["print_seconds"] <= 5 * 3600
    assert estimate["layers"] == math.ceil(120 / 0.2)


@pytest.mark.parametrize(
    "overrides",
    [
        {},
        {"width_mm": 70, "depth_mm": 60, "height_mm": 90, "tilt_deg": 78, "lip_height_mm": 8, "wall_mm": 2.4, "arch_cusps": 3},
        {"width_mm": 110, "depth_mm": 100, "height_mm": 150, "tilt_deg": 66, "lip_height_mm": 18, "wall_mm": 4.0, "arch_cusps": 7},
    ],
)
def test_anchor_frames_lie_on_the_surface(overrides):
    """Every anchor's centre and printable corners sit on the built skin with the frame's normal pointing out."""
    import numpy as np
    from trimesh.proximity import closest_point

    params = JharokhaPhoneStand.validate(overrides)
    mesh = JharokhaPhoneStand.build_body(params)
    for anchor in JharokhaPhoneStand.anchors:
        frame = JharokhaPhoneStand.anchor_frame(anchor.id, params)
        assert frame.size_mm is not None and min(frame.size_mm) > 5
        points = np.vstack([frame.origin[None, :], frame.corners()])
        _, distance, triangle = closest_point(mesh, points)
        assert distance.max() < 0.05, (anchor.id, distance)
        assert float(np.dot(mesh.face_normals[triangle[0]], frame.normal)) > 0.999
    with pytest.raises(NotImplementedError):
        JharokhaPhoneStand.anchor_frame("lid", params)


def test_descriptor_publishes_default_anchor_sizes_and_no_hardware():
    desc = JharokhaPhoneStand.descriptor()
    validate("template-descriptor", desc)
    by_id = {a["id"]: a for a in desc["anchors"]}
    defaults = JharokhaPhoneStand.validate({})
    for anchor_id, anchor in by_id.items():
        assert anchor["kind"] == "surface" and anchor["max_relief_mm"] == 1.0 and anchor["bleed_mm"] == 0.0
        frame = JharokhaPhoneStand.anchor_frame(anchor_id, defaults)
        assert anchor["size_mm"] == pytest.approx(list(frame.size_mm), abs=0.06)
    assert by_id["back"]["size_mm"][0] == pytest.approx(92 - 2 * 8 - 2 * 1.0, abs=0.06)  # width minus frame margins
    assert desc["hardware"] == [] and "min_feature_mm" not in desc
    assert JharokhaPhoneStand.hardware_for(defaults) == []
