"""``ajrakh_coaster_set@1`` (Aasan): the raised border, the field under the anchor and the scallops measured on the
built solid; the skin rule for a cut-in monogram; no hardware; the note says one coaster, order four; a raised motif stays under the border."""

from __future__ import annotations

import numpy as np
import pytest
from shapely.geometry import Point

from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.features.validate import check_features
from aakar_geometry.templates.ajrakh_coaster_set import MAX_RELIEF_MM, NOTCH_D_MM, RIM_H_MM, AjrakhCoasterSet, Layout
from aakar_geometry.templates.plates import MIN_SKIN_MM

from carrier_checks import section


def _text(text: str, depth: float, mode: str = "deboss") -> dict:
    return {"type": "emboss_text", "text": text, "anchor": "face", "mode": mode, "depth_mm": depth}


def test_descriptor_params_anchor_and_no_hardware():
    desc = AjrakhCoasterSet.descriptor()
    assert (desc["id"], desc["family"], desc["environment"]) == ("ajrakh_coaster_set", "coaster", "kitchen_marble")
    p = desc["params"]
    assert p["shape"]["options"] == ["round", "square", "octagon"] and p["edge"]["options"] == ["notched", "plain"]
    assert (p["size_mm"]["min"], p["size_mm"]["max"], p["size_mm"]["default"]) == (90, 100, 95.0)
    assert (p["thickness_mm"]["min"], p["thickness_mm"]["max"]) == (4.0, 6.0)
    assert (p["border_mm"]["min"], p["border_mm"]["max"], p["border_mm"]["default"]) == (4, 8, 6.0)
    assert [a["id"] for a in desc["anchors"]] == ["face"] and desc["anchors"][0]["max_relief_mm"] == MAX_RELIEF_MM == 0.8
    assert desc["hardware"] == [] and AjrakhCoasterSet.hardware_for(AjrakhCoasterSet.validate({})) == []
    assert "one coaster; order four for a set" in desc["description"] and "set of four" not in desc["description"]


@pytest.mark.parametrize("shape", ["round", "square", "octagon"])
@pytest.mark.parametrize("size, thickness, border", [(90, 4.0, 4), (95, 5.0, 6), (100, 6.0, 8)])
def test_the_border_stands_one_mm_above_the_field_and_the_plate_is_whole(shape, size, thickness, border):
    params = AjrakhCoasterSet.validate({"shape": shape, "size_mm": size, "thickness_mm": thickness, "border_mm": border})
    mesh = AjrakhCoasterSet.build_body(params)
    L = Layout(params)
    assert mesh.bounds[1][2] == pytest.approx(thickness + RIM_H_MM, abs=1e-6)
    # below the field: the whole outline, no hole; above it: only the border ring, one hole the size of the field
    plate = section(mesh, thickness / 2)
    assert not hasattr(plate, "geoms") and len(plate.interiors) == 0
    assert plate.area == pytest.approx(L.outline.area, rel=1e-3)
    ring = section(mesh, thickness + RIM_H_MM / 2)
    assert not hasattr(ring, "geoms") and len(ring.interiors) == 1
    assert ring.interiors[0].length == pytest.approx(L.field.exterior.length, rel=2e-3)
    # from above: the centre of the field is at the thickness, the middle of the border 1 mm higher
    rim_x = size / 2 - border / 2
    hits, _, _ = mesh.ray.intersects_location([[0, 0, 20.0], [rim_x, 0, 20.0]], [[0, 0, -1.0]] * 2, multiple_hits=False)
    zs = sorted(hits[:, 2])
    assert zs == [pytest.approx(thickness, abs=1e-6), pytest.approx(thickness + RIM_H_MM, abs=1e-6)]


def test_the_scallops_are_cut_in_the_rim_only_and_a_plain_edge_has_none():
    notched = AjrakhCoasterSet.validate({"shape": "round", "size_mm": 100})
    plain = AjrakhCoasterSet.validate({"shape": "round", "size_mm": 100, "edge": "plain"})
    L = Layout(notched)
    assert L.notch_count == round(np.pi * 100 / 5.0) and Layout(plain).notch_count == 0
    for params in (notched, plain):
        mesh = AjrakhCoasterSet.build_body(params)
        assert max(mesh.extents) == pytest.approx(100.0, abs=1e-6)  # the scallops never touch the outline
        assert mesh.is_watertight and mesh.body_count == 1
    ring = section(AjrakhCoasterSet.build_body(notched), 5.0 + RIM_H_MM / 2)
    # a scallop on the +X edge: the rim's outer edge dips half a disc in; the edge between two scallops stays out
    scallop = ring.exterior.distance(Point(50.0 - NOTCH_D_MM / 2 + 0.3, 0.0))
    assert ring.contains(Point(50.0 - NOTCH_D_MM / 2 - 0.3, 0.0)) and not ring.contains(Point(50.0 - 0.3, 0.0))
    assert scallop < NOTCH_D_MM
    assert ring.contains(Point((50.0 - 0.3) * np.cos(np.radians(1.8)), (50.0 - 0.3) * np.sin(np.radians(1.8))))  # half a pitch on
    assert section(AjrakhCoasterSet.build_body(notched), 2.5).area == pytest.approx(L.outline.area, rel=1e-3)


def test_the_anchor_sits_on_the_field_clear_of_the_rim():
    for shape in ("round", "square", "octagon"):
        params = AjrakhCoasterSet.validate({"shape": shape, "border_mm": 8})
        L = Layout(params)
        frame = AjrakhCoasterSet.anchor_frame("face", params)
        assert frame.origin[2] == pytest.approx(L.T) and np.allclose(frame.normal, [0, 0, 1])
        assert min(frame.size_mm) >= 8
        for corner in frame.corners():
            assert L.field.buffer(-1.4).contains(Point(corner[0], corner[1])), (shape, corner)


@pytest.mark.parametrize(
    "thickness, features, ok",
    [
        (4.0, [_text("AR", 0.8)], True),  # 4.0 − 0.8 = 3.2 mm under the monogram
        (4.0, [_text("AR", 2.8)], True),  # exactly MIN_SKIN_MM left
        (4.0, [_text("AR", 2.9)], False),
        (6.0, [_text("AR", 1.0, "emboss")], True),  # raised content never counts
    ],
)
def test_a_cut_in_monogram_leaves_plastic_under_it(thickness, features, ok):
    params = AjrakhCoasterSet.validate({"thickness_mm": thickness})
    if ok:
        AjrakhCoasterSet.validate_content(params, features)
        return
    with pytest.raises(ParamOutOfRange) as exc:
        AjrakhCoasterSet.validate_content(params, features)
    assert exc.value.keys == ["features[0].depth_mm", "thickness_mm"]
    assert f"{MIN_SKIN_MM:g} mm" in exc.value.message and "coaster" in exc.value.message


def test_the_note_says_print_four_and_is_heat_safe():
    note = AjrakhCoasterSet.karigar_note(AjrakhCoasterSet.validate({"shape": "octagon"}))
    assert "one coaster; order four for a set" in note and "print four" not in note and "heat-safe" in note and "cork" in note
    assert "eight-sided" in note and "mesh" not in note.lower() and "stl" not in note.lower()


@pytest.mark.parametrize("depth, ok", [(0.8, True), (0.9, False), (RIM_H_MM, False)])
def test_a_raised_motif_stays_under_the_border_so_a_cup_sits_on_the_rim(depth, ok, content_dir):
    """A cup's base (50-80 mm) crosses the field, so the border is its seat: raised content is capped below the rim."""
    assert MAX_RELIEF_MM < RIM_H_MM
    motif = {"type": "motif", "motif_id": "paisley", "anchor": "face", "mode": "emboss", "depth_mm": depth}
    if not ok:
        with pytest.raises(ParamOutOfRange) as exc:
            check_features(AjrakhCoasterSet, [motif])
        assert exc.value.keys == ["features[0].depth_mm"]
        return
    from aakar_geometry.features.fetch import LocalFileFetcher

    params = AjrakhCoasterSet.validate({"thickness_mm": 5.0})
    mesh = AjrakhCoasterSet.build(params, [motif], LocalFileFetcher(content_dir))
    rim_top = 5.0 + RIM_H_MM
    assert mesh.bounds[1][2] == pytest.approx(rim_top, abs=1e-6)  # the border is still the highest thing
    inside = mesh.vertices[np.hypot(mesh.vertices[:, 0], mesh.vertices[:, 1]) < Layout(params).S / 2 - 6.0 - 0.5]
    assert inside[:, 2].max() <= rim_top - 0.2 + 1e-6  # the motif's top stays under the border's
