"""``cake_topper_flat@1`` (Mithai): picks measured on the built solid, the envelope rule, the face on the plaque
only, the skin rule, no hardware."""

from __future__ import annotations

import numpy as np
import pytest
from shapely.geometry import LineString, box

from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.templates.cake_topper_flat import (
    ANCHOR_MARGIN_MM,
    MAX_LONGEST_MM,
    MIN_LONGEST_MM,
    PICK_ROOT_MM,
    PICK_TIP_MM,
    SHAPES,
    CakeTopperFlat,
    Layout,
)
from aakar_geometry.templates.plates import MIN_SKIN_MM

from carrier_checks import section


def _pieces_at(area, y: float) -> list[tuple[float, float]]:
    """``(centre x, width)`` of every solid run of ``area`` along the horizontal line at ``y``, left to right."""
    cut = area.intersection(LineString([(-500.0, y), (500.0, y)]))
    segments = list(cut.geoms) if hasattr(cut, "geoms") else [cut]
    out = [((s.bounds[0] + s.bounds[2]) / 2.0, s.bounds[2] - s.bounds[0]) for s in segments if not s.is_empty]
    return sorted(out)


def test_descriptor_params_anchor_and_no_hardware():
    desc = CakeTopperFlat.descriptor()
    assert (desc["id"], desc["family"], desc["environment"]) == ("cake_topper_flat", "cake_topper", "kitchen_marble")
    p = desc["params"]
    assert p["shape"]["options"] == list(SHAPES) == ["banner", "cloud", "heart", "circle"]
    assert (p["width_mm"]["min"], p["width_mm"]["max"], p["width_mm"]["default"]) == (90, 120, 110.0)
    assert (p["pick_len_mm"]["min"], p["pick_len_mm"]["max"], p["pick_len_mm"]["default"]) == (30, 50, 40.0)
    assert (p["thickness_mm"]["min"], p["thickness_mm"]["max"], p["thickness_mm"]["default"]) == (2.4, 3.2, 2.8)
    assert [a["id"] for a in desc["anchors"]] == ["face"]
    assert desc["anchors"][0]["accepts"] == ["emboss_text", "motif", "relief_image"]
    assert desc["hardware"] == [] and CakeTopperFlat.hardware_for(CakeTopperFlat.validate({})) == []
    assert (PICK_ROOT_MM, PICK_TIP_MM) == (7.0, 2.5)


@pytest.mark.parametrize("shape", SHAPES)
@pytest.mark.parametrize("width, pick_len, thickness", [(100, 40, 2.4), (110, 40, 3.2), (100, 50, 2.8)])
def test_two_tapered_picks_at_a_third_of_the_width_in_one_flat_plate(shape, width, pick_len, thickness):
    params = CakeTopperFlat.validate({"shape": shape, "width_mm": width, "pick_len_mm": pick_len, "thickness_mm": thickness})
    layout = Layout(params)
    mesh = CakeTopperFlat.build_body(params)
    # one flat plate: the back on the bed, the face at the thickness, nothing above it
    assert mesh.bounds[0][2] == pytest.approx(0.0, abs=1e-6) and mesh.bounds[1][2] == pytest.approx(thickness, abs=1e-6)
    assert mesh.extents[1] == pytest.approx(layout.H + pick_len, abs=1e-3)  # plaque height + pick length
    area = section(mesh, thickness / 2.0)
    assert all(not g.interiors for g in getattr(area, "geoms", [area]))  # no holes in the plate
    # just above the tips: two picks, each about the tip width, centred at ±width/3
    tips = _pieces_at(area, layout.pick_tip_y + 1.0)
    assert len(tips) == 2
    for (cx, w), expected in zip(tips, (-width / 3.0, width / 3.0)):
        assert cx == pytest.approx(expected, abs=0.05)
        assert PICK_TIP_MM <= w <= PICK_TIP_MM + 1.0 * (PICK_ROOT_MM - PICK_TIP_MM) / pick_len + 0.05
    # just below the plaque's lowest point: the picks at their root width, still two separate pieces
    roots = _pieces_at(area, layout.plaque_bottom_y - 0.5)
    assert len(roots) == 2
    for (cx, w), expected in zip(roots, (-width / 3.0, width / 3.0)):
        assert cx == pytest.approx(expected, abs=0.05)
        assert w == pytest.approx(PICK_ROOT_MM - 0.5 * (PICK_ROOT_MM - PICK_TIP_MM) / pick_len, abs=0.05)
    # inside the plaque the picks have merged into it: one piece across the middle of the plaque
    middle = _pieces_at(area, (layout.plaque_bottom_y + layout.plaque.bounds[3]) / 2.0)
    assert len(middle) == 1 and middle[0][1] > 0.6 * width


@pytest.mark.parametrize(
    "shape, width, pick_len, ok",
    [
        ("banner", 90, 30, False),  # 63 + 30 = 93 mm tall and 90 wide: under 100
        ("banner", 90, 37, True),  # 63 + 37 = 100, the bottom of the envelope
        ("banner", 92, 35, False),  # 64.4 + 35 = 99.4
        ("cloud", 92, 36, True),  # 64.4 + 36 = 100.4
        ("banner", 100, 30, True),  # 100 mm tall and 100 wide: the width carries it
        ("cloud", 120, 50, True),  # 84 + 50 = 134
        ("circle", 90, 30, True),  # 90 + 30 = 120
        ("circle", 120, 30, True),  # 120 + 30 = 150, the top of the envelope
        ("circle", 120, 31, False),  # 151
        ("circle", 110, 40, True),  # 150 at the default picks; 111 mm would be refused
        ("heart", 120, 50, False),  # the heart is 0.85 × its width tall: 102 + 50 = 152
        ("heart", 117, 50, True),  # 99.8 + 50 = 149.8
        ("heart", 90, 50, True),  # 76.8 + 50 = 126.8
    ],
)
def test_the_envelope_rule_refuses_what_is_too_short_or_too_tall(shape, width, pick_len, ok):
    overrides = {"shape": shape, "width_mm": width, "pick_len_mm": pick_len}
    layout = Layout({**{k: p.default for k, p in CakeTopperFlat.params.items()}, **overrides})
    inside = MIN_LONGEST_MM - 1e-6 <= layout.longest_mm <= MAX_LONGEST_MM + 1e-6
    assert inside is ok, layout.longest_mm
    if ok:
        params = CakeTopperFlat.validate(overrides)
        longest = float(max(CakeTopperFlat.build_body(params).extents))
        assert MIN_LONGEST_MM - 1e-6 <= longest <= MAX_LONGEST_MM + 1e-6
        return
    with pytest.raises(ParamOutOfRange) as exc:
        CakeTopperFlat.validate(overrides)
    assert exc.value.keys == ["width_mm", "pick_len_mm"]
    assert "from pick tip to top" in exc.value.message
    assert ("wider or the picks longer" if layout.longest_mm < MIN_LONGEST_MM else "narrower or the picks shorter") in exc.value.message


@pytest.mark.parametrize("shape", SHAPES)
@pytest.mark.parametrize("pick_len", [30, 40])
def test_every_width_is_accepted_at_the_default_picks_except_the_circle_above_110(shape, pick_len):
    """The slider dead zone: at the default 40 mm picks only the circle wider than 150 − 40 is refused."""
    widths = range(90, 121)
    refused = [w for w in widths if not (MIN_LONGEST_MM - 1e-6 <= Layout({"shape": shape, "width_mm": w, "pick_len_mm": pick_len, "thickness_mm": 2.8}).longest_mm <= MAX_LONGEST_MM + 1e-6)]
    if pick_len == 40:
        assert refused == (list(range(111, 121)) if shape == "circle" else [])
    else:  # 30 mm picks: a banner or cloud under 100 mm is too short; the circle fits at every width
        assert refused == (list(range(90, 100)) if shape in ("banner", "cloud") else [])


@pytest.mark.parametrize("shape", SHAPES)
def test_the_karigar_note_states_the_true_stick_length_from_the_plaques_edge(shape):
    """On a heart or a circle a bar of the root width shows between the curved edge and the taper: the note
    gives the stick's length from the edge, not just the slider's pick length."""
    params = CakeTopperFlat.validate({"shape": shape, "width_mm": 100, "pick_len_mm": 40})
    layout = Layout(params)
    note = CakeTopperFlat.karigar_note(params)
    # the bar measured on the layout: from where the pick leaves the plaque's edge down to its lowest level
    exit_y = max(Layout._pick_exit_y(layout.plaque, x) for x in layout.pick_x)
    assert layout.bar_mm == pytest.approx(exit_y - layout.plaque_bottom_y, abs=1e-6)
    assert layout.stick_mm == pytest.approx(exit_y - layout.pick_tip_y, abs=1e-6)
    if shape in ("banner", "cloud"):
        assert layout.bar_mm == pytest.approx(0.0, abs=1e-6)
        assert "two 40 mm picks tapering from 7 to 2.5 mm" in note
    else:
        assert layout.bar_mm > 10.0  # the heart's lobes, the circle's curve
        bar = round(layout.bar_mm)
        assert f"two picks {bar + 40} mm long from where they leave the curved edge" in note
        assert f"a 7 mm bar for {bar} mm down to the plaque's lowest point, then 40 mm tapering" in note


@pytest.mark.parametrize("shape", SHAPES)
def test_the_face_sits_on_the_plaque_clear_of_the_picks(shape):
    params = CakeTopperFlat.validate({"shape": shape, "width_mm": 100, "pick_len_mm": 45})
    layout = Layout(params)
    frame = CakeTopperFlat.anchor_frame("face", params)
    assert frame.origin[2] == pytest.approx(float(params["thickness_mm"])) and np.allclose(frame.normal, [0, 0, 1])
    assert frame.origin[0] == pytest.approx(0.0, abs=1e-6)  # symmetric about the middle
    w, h = frame.size_mm
    assert min(w, h) >= 8.0
    rect = box(frame.origin[0] - w / 2, frame.origin[1] - h / 2, frame.origin[0] + w / 2, frame.origin[1] + h / 2)
    assert layout.plaque.buffer(-ANCHOR_MARGIN_MM + 1e-6).contains(rect)  # inside the plaque, the margin kept
    assert rect.bounds[1] > layout.plaque_bottom_y  # never down on the picks


@pytest.mark.parametrize(
    "thickness, feature, ok",
    [
        (2.4, {"type": "motif", "motif_id": "lotus", "anchor": "face", "mode": "deboss", "depth_mm": 1.2}, True),  # 2.4 − 1.2 = 1.2
        (2.4, {"type": "motif", "motif_id": "lotus", "anchor": "face", "mode": "deboss", "depth_mm": 1.3}, False),
        (2.8, {"type": "emboss_text", "text": "Happy 30", "anchor": "face", "mode": "deboss", "depth_mm": 1.6}, True),
        (2.8, {"type": "emboss_text", "text": "Happy 30", "anchor": "face", "mode": "deboss", "depth_mm": 1.7}, False),
        (2.4, {"type": "emboss_text", "text": "Happy 30", "anchor": "face", "depth_mm": 1.5}, True),  # raised: adds plastic
    ],
)
def test_a_cut_in_name_or_motif_leaves_skin_behind_it(thickness, feature, ok):
    params = CakeTopperFlat.validate({"thickness_mm": thickness})
    if ok:
        CakeTopperFlat.validate_content(params, [feature])
        return
    with pytest.raises(ParamOutOfRange) as exc:
        CakeTopperFlat.validate_content(params, [feature])
    assert exc.value.keys == ["features[0].depth_mm", "thickness_mm"]
    assert f"at least {MIN_SKIN_MM:g} mm" in exc.value.message and "topper" in exc.value.message
