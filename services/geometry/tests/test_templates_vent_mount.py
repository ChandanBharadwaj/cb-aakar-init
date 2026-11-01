"""``vent_mount@1`` (Safar): the louvre clip (two sprung tongues, the slot, the barbs), the lip with its cable gap, the
wings and the standoff rails measured on the built solid lying on its side; the anchor on the cabin face, the skin
rule on the 3 mm plate, the print's stability, and no hardware."""

from __future__ import annotations

import numpy as np
import pytest

from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.templates.plates import MIN_SKIN_MM
from aakar_geometry.templates.vent_mount import (
    BARB_FROM_TIP_MM,
    BARB_MM,
    CLIP_DROP_MM,
    LIP_T_MM,
    PLATE_MM,
    RAIL_MM,
    RAIL_T_MM,
    SLOT_MM,
    TONGUE_L_MM,
    TONGUE_T_MM,
    TONGUE_W_MM,
    WING_H_MM,
    WING_SHARE,
    WING_T_MM,
    Layout,
    VentMount,
)

from carrier_checks import check_body, check_frames

CORNERS = [(60, 90, 10, 16), (70, 110, 12, 20), (80, 130, 15, 26)]


def buti(mode: str, depth: float) -> dict:
    return {"type": "motif", "motif_id": "lotus", "anchor": "face", "mode": mode, "depth_mm": depth}


def _params(width, height, lip_h, gap):
    return VentMount.validate({"cradle_w_mm": width, "cradle_h_mm": height, "lip_h_mm": lip_h, "cable_gap_mm": gap})


def _hits_along(mesh, L: Layout, start, direction) -> np.ndarray:
    """Every crossing of the solid on a line given in the mounted frame (x, y, d), returned in the mounted frame."""
    origin = L.to_print([start])[0]
    tip = L.to_print([np.add(start, direction)])[0]
    hits, _, _ = mesh.ray.intersects_location([origin], [tip - origin], multiple_hits=True)
    back = np.c_[L.W / 2.0 - hits[:, 2], hits[:, 1], hits[:, 0] + L.d_mid]  # print → mounted
    return back


def _front(mesh, L: Layout, x: float, y: float) -> float:
    """How far forward (towards the cabin) the solid reaches on the line through mounted (x, y)."""
    return float(_hits_along(mesh, L, (x, y, 60.0), (0, 0, -1.0))[:, 2].max())


def _back(mesh, L: Layout, x: float, y: float) -> float:
    """How far back (into the vent) the solid reaches on the line through mounted (x, y)."""
    return float(_hits_along(mesh, L, (x, y, -60.0), (0, 0, 1.0))[:, 2].min())


def _inside(mesh, L: Layout, points) -> list[bool]:
    return list(mesh.contains(L.to_print(points)))


def test_descriptor_motif_only_and_no_hardware():
    desc = VentMount.descriptor()
    assert (desc["id"], desc["family"], desc["environment"]) == ("vent_mount", "car_accessory", "dashboard")
    p = desc["params"]
    assert (p["cradle_w_mm"]["min"], p["cradle_w_mm"]["max"], p["cradle_w_mm"]["default"], p["cradle_w_mm"]["handle"]) == (60, 80, 70.0, True)
    assert (p["cradle_h_mm"]["min"], p["cradle_h_mm"]["max"], p["cradle_h_mm"]["default"]) == (90, 130, 110.0)
    assert (p["lip_h_mm"]["min"], p["lip_h_mm"]["max"], p["lip_h_mm"]["default"]) == (10, 15, 12.0)
    assert (p["cable_gap_mm"]["min"], p["cable_gap_mm"]["max"], p["cable_gap_mm"]["default"]) == (16, 26, 20.0)
    assert [(a["id"], a["accepts"]) for a in desc["anchors"]] == [("face", ["motif"])]
    assert desc["anchors"][0]["size_mm"] == [62.2, 104.6]  # 70 − 2·(2.4 + 1.5) by 110 − 2.4 − 2·1.5
    assert desc["features_supported"] == ["motif"]
    assert desc["hardware"] == [] and VentMount.hardware_for(VentMount.validate({})) == []
    assert "petg_slate" in desc["materials"] and all("petg" in m for m in desc["materials"])  # heat-safe only


def test_the_clip_dimensions_pinch_a_louvre():
    assert (TONGUE_T_MM, TONGUE_L_MM, TONGUE_W_MM, SLOT_MM) == (2.4, 25.0, 20.0, 1.6)
    assert SLOT_MM - 2 * BARB_MM < 1.5  # the barbs catch even the thinnest louvre
    assert 2.5 - SLOT_MM <= 2 * 0.5  # the thickest louvre opens the slot no more than PETG springs, 0.5 mm a side
    assert 0.3 <= BARB_MM <= 0.5


@pytest.mark.parametrize("width, height, lip_h, gap", CORNERS)
def test_it_lies_on_its_side_with_the_plate_height_longest(width, height, lip_h, gap):
    params = _params(width, height, lip_h, gap)
    mesh = check_body(VentMount, params)
    # print X = depth (tongue tips to the lip), Y = the cradle's height, Z = its width
    assert mesh.extents == pytest.approx([TONGUE_L_MM + lip_h, height, width], abs=1e-6)
    assert mesh.body_count == 1


@pytest.mark.parametrize("width, height, lip_h, gap", CORNERS)
def test_two_tongues_stand_back_from_the_plate_either_side_of_the_slot(width, height, lip_h, gap):
    params = _params(width, height, lip_h, gap)
    L = Layout(params)
    mesh = VentMount.build_body(params)
    y_slot = L.y_top - CLIP_DROP_MM
    half = SLOT_MM / 2
    # half-way along the tongues, a vertical line meets lower tongue, slot, upper tongue
    ys = sorted(_hits_along(mesh, L, (0.0, L.y_top + 20.0, -TONGUE_L_MM / 2), (0, -1.0, 0))[:, 1])
    expected = [y_slot - half - TONGUE_T_MM, y_slot - half, y_slot + half, y_slot + half + TONGUE_T_MM]
    assert ys == pytest.approx(expected, abs=1e-6)
    assert _inside(mesh, L, [(0, y_slot, -12.0), (0, y_slot - half - 1.2, -12.0), (0, y_slot + half + 1.2, -12.0)]) == [False, True, True]
    # the tongues reach TONGUE_L_MM back from the plate, perpendicular to it
    assert _back(mesh, L, 0.0, y_slot + half + 1.2) == pytest.approx(-TONGUE_L_MM, abs=1e-6)
    assert _back(mesh, L, 0.0, y_slot - half - 1.2) == pytest.approx(-TONGUE_L_MM, abs=1e-6)
    # TONGUE_W_MM wide at the tip, widening towards the root no steeper than 45° (it leans on the plate in print)
    root = L.tongue_half_width()
    assert root == pytest.approx(min(width / 2, TONGUE_W_MM / 2 + TONGUE_L_MM))
    for back in (0.0, 5.0, 15.0, TONGUE_L_MM - RAIL_MM - 1.0):  # past the rails (they stand RAIL_MM back), the line meets the tongue alone
        xs = sorted(_hits_along(mesh, L, (-50.0, y_slot + half + 1.2, -TONGUE_L_MM + back), (1.0, 0, 0))[:, 0])
        w = min(TONGUE_W_MM / 2 + back, root)  # a true 45° flank from the tip, then straight to the root
        assert xs == pytest.approx([-w, w], abs=1e-6), back
    # the slot is open at the tip and runs all the way to the plate's back
    assert _back(mesh, L, 0.0, y_slot) == pytest.approx(0.0, abs=1e-6)


@pytest.mark.parametrize("width, height, lip_h, gap", CORNERS)
def test_a_barb_near_each_tip_narrows_the_slot(width, height, lip_h, gap):
    params = _params(width, height, lip_h, gap)
    L = Layout(params)
    mesh = VentMount.build_body(params)
    y_slot = L.y_top - CLIP_DROP_MM
    d_crest = -TONGUE_L_MM + BARB_FROM_TIP_MM + BARB_MM + 0.5  # on the barb's flat crest
    ys = sorted(_hits_along(mesh, L, (0.0, L.y_top + 20.0, d_crest), (0, -1.0, 0))[:, 1])
    assert ys[1:3] == pytest.approx([y_slot - SLOT_MM / 2 + BARB_MM, y_slot + SLOT_MM / 2 - BARB_MM], abs=1e-6)
    # right at the tip, ahead of the barb's ramp, the slot is its full width: the louvre is led in
    ys = sorted(_hits_along(mesh, L, (0.0, L.y_top + 20.0, -TONGUE_L_MM + 0.2), (0, -1.0, 0))[:, 1])
    assert ys[1:3] == pytest.approx([y_slot - SLOT_MM / 2, y_slot + SLOT_MM / 2], abs=1e-6)


@pytest.mark.parametrize("width, height, lip_h, gap", CORNERS)
def test_the_lip_has_a_cable_gap_and_the_wings_stop_at_sixty_percent(width, height, lip_h, gap):
    params = _params(width, height, lip_h, gap)
    L = Layout(params)
    mesh = VentMount.build_body(params)
    y_lip = L.y0 + LIP_T_MM / 2
    assert _front(mesh, L, width / 4, y_lip) == pytest.approx(lip_h, abs=1e-6)  # the lip
    assert _front(mesh, L, 0.0, y_lip) == pytest.approx(PLATE_MM, abs=1e-6)  # the cable gap: plate only
    assert _front(mesh, L, gap / 2 - 0.5, y_lip) == pytest.approx(PLATE_MM, abs=1e-6)
    assert _front(mesh, L, gap / 2 + 0.5, y_lip) == pytest.approx(lip_h, abs=1e-6)
    x_wing = width / 2 - WING_T_MM / 2
    assert _front(mesh, L, x_wing, L.y0 + 0.3 * height) == pytest.approx(WING_H_MM, abs=1e-6)
    assert _front(mesh, L, -x_wing, L.y0 + 0.3 * height) == pytest.approx(WING_H_MM, abs=1e-6)
    assert _front(mesh, L, x_wing, L.y0 + (WING_SHARE + 0.05) * height) == pytest.approx(PLATE_MM, abs=1e-6)
    assert _front(mesh, L, 0.0, L.y0 + 0.5 * height) == pytest.approx(PLATE_MM, abs=1e-6)


@pytest.mark.parametrize("width, height, lip_h, gap", CORNERS)
def test_standoff_rails_run_down_the_backs_side_edges(width, height, lip_h, gap):
    params = _params(width, height, lip_h, gap)
    L = Layout(params)
    mesh = VentMount.build_body(params)
    for x in (width / 2 - RAIL_T_MM / 2, -(width / 2 - RAIL_T_MM / 2)):
        for share in (0.05, 0.5, 0.95):
            assert _back(mesh, L, x, L.y0 + share * height) == pytest.approx(-RAIL_MM, abs=1e-6)
        # the gusset: half-way across it the rail stands half as far back, a 45° face
        x_mid = np.sign(x) * (width / 2 - RAIL_T_MM - RAIL_MM / 2)
        assert _back(mesh, L, x_mid, L.y0 + 0.4 * height) == pytest.approx(-RAIL_MM / 2, abs=1e-6)
    # between the rails and away from the tongues the back is the flat plate
    assert _back(mesh, L, 0.0, L.y0 + 0.4 * height) == pytest.approx(0.0, abs=1e-6)


@pytest.mark.parametrize("width, height, lip_h, gap", CORNERS)
def test_it_stands_on_its_side_without_tipping(width, height, lip_h, gap):
    from aakar_inspect.checks import stability

    mesh = VentMount.build_body(_params(width, height, lip_h, gap))
    stab = stability(mesh)
    assert stab.inside and stab.margin_mm >= 5.0, stab.margin_mm  # the inspector's tipping threshold


@pytest.mark.parametrize("overrides", [{"cradle_w_mm": 60, "cradle_h_mm": 130, "lip_h_mm": 15}, {"cradle_w_mm": 80, "cradle_h_mm": 90, "cable_gap_mm": 26}, {}])
def test_the_face_sits_on_the_cabin_side_inside_the_wings_and_clear_of_the_lip(overrides):
    params = VentMount.validate(overrides)
    L = Layout(params)
    mesh = check_body(VentMount, params)
    check_frames(VentMount, params, mesh)
    frame = VentMount.anchor_frame("face", params)
    # the cabin face looks along print +X; the motif's up (v) is the cradle's up
    assert np.allclose(frame.normal, [1, 0, 0]) and np.allclose(frame.v, [0, 1, 0])
    assert frame.origin[0] + L.d_mid == pytest.approx(PLATE_MM)
    assert frame.size_mm == pytest.approx((L.W - 2 * (WING_T_MM + 1.5), L.H - LIP_T_MM - 3.0))
    x0, y0, x1, y1 = L.anchor_rect()
    assert y0 == pytest.approx(L.y0 + LIP_T_MM + 1.5) and y1 == pytest.approx(L.y_top - 1.5)  # clear of the lip and the top edge
    assert x1 == pytest.approx(L.W / 2 - WING_T_MM - 1.5)
    # the face is flat plate under every corner: nothing stands in front of it
    for corner in frame.corners():
        x, y = L.W / 2 - corner[2], corner[1]
        assert _front(mesh, L, x, y) == pytest.approx(PLATE_MM, abs=1e-6)
    note = VentMount.karigar_note(params)
    assert "louvre" in note and "tongues" in note and "barb" in note and "on its side" in note
    assert "heat-safe" in note.lower() and "mesh" not in note.lower() and "stl" not in note.lower()


@pytest.mark.parametrize(
    "features, ok",
    [
        ([buti("deboss", PLATE_MM - MIN_SKIN_MM)], True),  # 3 − 1.2 = 1.8 mm cut leaves exactly the skin
        ([buti("deboss", 1.9)], False),
        ([buti("deboss", 0.6)], True),  # the catalogue's cut-in: 2.4 mm of plate left
        ([buti("emboss", 1.5)], True),  # raised: adds plastic
    ],
)
def test_a_cut_in_motif_leaves_skin_in_the_plate(features, ok):
    params = VentMount.validate({})
    if ok:
        VentMount.validate_content(params, features)
        return
    with pytest.raises(ParamOutOfRange) as exc:
        VentMount.validate_content(params, features)
    assert exc.value.keys == ["features[0].depth_mm", "cradle_h_mm"]
    assert "back plate" in exc.value.message
