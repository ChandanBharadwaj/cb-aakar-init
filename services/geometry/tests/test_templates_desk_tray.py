"""``desk_tray@1`` (Tarteeb): compartments and walls measured on the built solid, the phone slot (and that a leaning
phone neither tips the tray nor touches the rim), the skin rule on the front wall, the deeper-than-wide and too-small-
for-a-slot refusals, and the no-slot variant (a boolean the shared corner matrix cannot vary)."""

from __future__ import annotations

import numpy as np
import pytest

from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.templates.desk_tray import (
    BACK_RAIL_MM,
    FLOOR_MM,
    LEDGE_D_MM,
    MAX_HEIGHT_MM,
    RAIL_MM,
    RIM_CLEAR_MM,
    SLOT_DEPTH_MM,
    SLOT_MIN_DEPTH_MM,
    SLOT_MIN_HEIGHT_MM,
    SLOT_TILT_DEG,
    SLOT_W_MM,
    WALL_KEY,
    WALL_MM,
    DeskTray,
    Layout,
)
from aakar_geometry.templates.plates import MIN_SKIN_MM

from carrier_checks import check_body, check_frames, section


def name(anchor: str, text: str = "Tarteeb", **extra) -> dict:
    return {"type": "emboss_text", "text": text, "anchor": anchor, **extra}


def test_descriptor_and_no_hardware():
    desc = DeskTray.descriptor()
    assert (desc["id"], desc["family"], desc["environment"]) == ("desk_tray", "desk_organizer", "desk_oak")
    p = desc["params"]
    assert (p["width_mm"]["min"], p["width_mm"]["max"], p["width_mm"]["default"], p["width_mm"]["handle"]) == (100, 250, 160.0, True)
    assert (p["depth_mm"]["min"], p["depth_mm"]["max"], p["depth_mm"]["default"]) == (70, 140, 100.0)
    assert (p["height_mm"]["min"], p["height_mm"]["max"], p["height_mm"]["default"]) == (22, 45, 30.0)
    assert (p["compartments"]["type"], p["compartments"]["min"], p["compartments"]["max"], p["compartments"]["default"]) == ("integer", 1, 3, 2)
    assert (p["phone_slot"]["type"], p["phone_slot"]["default"]) == ("boolean", True)
    assert [a["id"] for a in desc["anchors"]] == ["front"]
    assert desc["anchors"][0]["size_mm"] == [145.0, 27.0]  # 160 − 2·(6 + 1.5) by 30 − 2·1.5
    assert desc["hardware"] == [] and DeskTray.hardware_for(DeskTray.validate({})) == []


@pytest.mark.parametrize("width, depth, height, n", [(100, 70, 22, 1), (160, 100, 30, 2), (250, 140, 45, 3)])
def test_compartments_are_even_with_full_walls_and_the_floor(width, depth, height, n):
    params = DeskTray.validate({"width_mm": width, "depth_mm": depth, "height_mm": height, "compartments": n, "phone_slot": False})
    mesh = DeskTray.build_body(params)
    assert mesh.extents == pytest.approx([width, depth, height], abs=1e-6)
    area = section(mesh, height - 1.0)  # through the rim: one hole per compartment
    assert len(area.interiors) == n
    cw = (width - 2 * WALL_MM - (n - 1) * WALL_MM) / n
    assert sorted(ring.bounds[2] - ring.bounds[0] for ring in area.interiors) == pytest.approx([cw] * n, abs=1e-6)
    for ring in area.interiors:
        x0, y0, x1, y1 = ring.bounds
        assert y1 - y0 == pytest.approx(depth - 2 * WALL_MM, abs=1e-6)  # the full inside depth: no ledge
    # the outer wall is WALL_MM on every straight side, the floor FLOOR_MM under the middle of each compartment
    assert abs(area.bounds[0]) - max(abs(r.bounds[0]) for r in area.interiors) == pytest.approx(WALL_MM, abs=1e-6)
    assert abs(area.bounds[1]) - max(abs(r.bounds[1]) for r in area.interiors) == pytest.approx(WALL_MM, abs=1e-6)
    for ring in area.interiors:
        cx = (ring.bounds[0] + ring.bounds[2]) / 2
        hits, _, _ = mesh.ray.intersects_location([[cx, 0.0, height + 5.0]], [[0, 0, -1.0]], multiple_hits=True)
        assert sorted(hits[:, 2]) == pytest.approx([0.0, FLOOR_MM], abs=1e-6)
    assert len(section(mesh, FLOOR_MM / 2).interiors) == 0  # solid floor


@pytest.mark.parametrize("width, depth", [(100, 90), (160, 100), (250, 140)])
def test_the_phone_slot_is_a_leaning_groove_in_a_ledge_inside_the_back_wall(width, depth):
    params = DeskTray.validate({"width_mm": width, "depth_mm": depth, "compartments": 3, "phone_slot": True})
    L = Layout(params)
    mesh = DeskTray.build_body(params)
    assert mesh.extents[:2] == pytest.approx([width, depth], abs=1e-6)  # the ledge stays inside the walls
    assert section(mesh, FLOOR_MM + 5.0).difference(L.outline).area < 1e-3  # and inside the softened back corners
    assert not mesh.contains([[width / 2 - 1.5, depth / 2 - 1.5, FLOOR_MM + 5.0]])[0]
    _, y_slot = L.slot_profile()
    # down the middle of the groove: the groove floor, then the bed; just in front of the ledge: the tray floor
    down = [[0, 0, -1.0]]
    hits, _, _ = mesh.ray.intersects_location([[0.0, y_slot, L.H + 5.0]], down, multiple_hits=True)
    assert sorted(hits[:, 2]) == pytest.approx([0.0, L.slot_floor], abs=1e-6)
    hits, _, _ = mesh.ray.intersects_location([[0.0, L.y_ledge_front - 1.0, L.H + 5.0]], down, multiple_hits=True)
    assert sorted(hits[:, 2]) == pytest.approx([0.0, FLOOR_MM], abs=1e-6)
    # a section just above the groove floor: the groove is one opening across the whole inside width (the dividers
    # stop over the front rail), SLOT_W_MM / cos(tilt) front to back with RAIL_MM of ledge in front of it
    area = section(mesh, L.slot_floor + 0.1)
    grooves = [ring for ring in area.interiors if ring.bounds[1] > L.y_ledge_front]
    assert len(grooves) == 1
    x0, y0, x1, y1 = grooves[0].bounds
    assert x1 - x0 == pytest.approx(L.inner_w, abs=1e-6)
    assert y0 - L.y_ledge_front == pytest.approx(RAIL_MM + 0.1 * np.tan(np.radians(15)), abs=1e-3)
    assert y1 - y0 == pytest.approx(SLOT_W_MM / np.cos(np.radians(15)), abs=1e-3)
    assert L.y_back - y1 >= WALL_MM  # plastic behind the groove at the ledge top
    # the back rail is BACK_RAIL_MM at the ledge top; 0.1 mm above the groove floor it is the rest of the lean wider
    assert L.back_rail == pytest.approx(BACK_RAIL_MM)
    assert L.y_back - y1 == pytest.approx(BACK_RAIL_MM + (SLOT_DEPTH_MM - 0.1) * np.tan(np.radians(SLOT_TILT_DEG)), abs=1e-3)
    assert L.open_depth == pytest.approx(depth - 2 * WALL_MM - LEDGE_D_MM)
    # the phone leans on the groove's back face, not the rim: that face carried on up at the tilt passes in front of
    # the rim's inner edge at this height, and still by RIM_CLEAR_MM in the tallest tray
    assert L.rim_clearance >= RIM_CLEAR_MM
    tallest = Layout({**params, "height_mm": MAX_HEIGHT_MM})
    assert tallest.rim_clearance >= RIM_CLEAR_MM - 1e-9
    y_rim, _ = tallest.lean_line((MAX_HEIGHT_MM - tallest.slot_floor) / np.cos(np.radians(SLOT_TILT_DEG)))
    assert tallest.y_back - y_rim == pytest.approx(tallest.rim_clearance, abs=1e-9)
    # the dividers reach the ledge: three compartments in front of it, through the rim
    rim = section(mesh, L.H - 1.0)
    assert len(rim.interiors) == 1  # joined behind the dividers, over the ledge
    inner = section(mesh, L.ledge_top + 1.0)
    assert len(inner.interiors) == 1
    assert max(r.bounds[3] for r in inner.interiors) == pytest.approx(L.y_back, abs=1e-6)


@pytest.mark.parametrize("overrides", [{"width_mm": 100, "depth_mm": 70, "height_mm": 22, "compartments": 1}, {"width_mm": 250, "depth_mm": 140, "height_mm": 45, "compartments": 3}, {}])
def test_without_the_slot_the_tray_still_builds_inside_the_envelope_with_the_frame_on_the_front(overrides):
    params = DeskTray.validate({**overrides, "phone_slot": False})
    mesh = check_body(DeskTray, params)
    check_frames(DeskTray, params, mesh)
    frame = DeskTray.anchor_frame("front", params)
    assert np.allclose(frame.normal, [0, -1, 0]) and frame.origin[1] == pytest.approx(-params["depth_mm"] / 2)
    assert frame.size_mm == pytest.approx((params["width_mm"] - 15.0, params["height_mm"] - 3.0))
    note = DeskTray.karigar_note(params)
    assert "phone" not in note and "mesh" not in note.lower() and "stl" not in note.lower()


PHONE_G = 170.0
PHONE_COG_UP_MM = 75.0  # half a 150 mm phone, up its back from the groove floor
PLA_G_PER_MM3 = 1.24e-3


def test_a_phone_leaning_in_the_smallest_slot_tray_does_not_tip_it():
    """The worst accepted corner for the slot (narrowest, shallowest, lowest, no dividers: the least plastic and the
    shortest lever) with a 170 g phone leaning the full tilt: the combined centre of gravity (the printed tray at its
    centroid, the phone 75 mm up the groove's lean line) stays at least 5 mm inside the back edge. The phone's weight is
    taken on the groove's back face line, where its back rests; its own thickness only moves it forward of that."""
    params = DeskTray.validate(
        {"width_mm": 100, "depth_mm": SLOT_MIN_DEPTH_MM, "height_mm": SLOT_MIN_HEIGHT_MM, "compartments": 1, "phone_slot": True}
    )
    L = Layout(params)
    mesh = DeskTray.build_body(params)
    assert mesh.is_watertight and mesh.volume > 0
    tray_g = mesh.volume * PLA_G_PER_MM3
    y_tray = float(mesh.center_mass[1])
    y_phone, z_phone = L.lean_line(PHONE_COG_UP_MM)
    assert z_phone > L.H  # the phone stands well above the rim, leaning back
    y_combined = (tray_g * y_tray + PHONE_G * y_phone) / (tray_g + PHONE_G)
    assert L.D / 2.0 - y_combined >= 5.0, (tray_g, y_tray, y_phone, y_combined)


@pytest.mark.parametrize(
    "depth, height",
    [
        (70, 22),  # the reviewer's example: the phone's weight landed outside the footprint
        (SLOT_MIN_DEPTH_MM, 22),
        (70, SLOT_MIN_HEIGHT_MM),
        (SLOT_MIN_DEPTH_MM - 5, SLOT_MIN_HEIGHT_MM),
        (SLOT_MIN_DEPTH_MM, SLOT_MIN_HEIGHT_MM - 1),
    ],
)
def test_a_tray_too_small_to_hold_a_leaning_phone_is_refused_with_the_slot_but_builds_without_it(depth, height):
    with pytest.raises(ParamOutOfRange) as exc:
        DeskTray.validate({"width_mm": 100, "depth_mm": depth, "height_mm": height, "phone_slot": True})
    assert exc.value.keys == ["depth_mm", "height_mm", "phone_slot"]
    assert "A phone slot needs a tray at least 90 mm deep and 28 mm tall so the tray does not tip" in exc.value.message
    params = DeskTray.validate({"width_mm": 100, "depth_mm": depth, "height_mm": height, "phone_slot": False})
    assert DeskTray.build_body(params).is_watertight
    smallest = DeskTray.validate({"width_mm": 100, "depth_mm": SLOT_MIN_DEPTH_MM, "height_mm": SLOT_MIN_HEIGHT_MM, "phone_slot": True})
    assert (smallest["depth_mm"], smallest["height_mm"], smallest["phone_slot"]) == (90.0, 28.0, True)


def test_a_tray_deeper_than_it_is_wide_is_refused():
    with pytest.raises(ParamOutOfRange) as exc:
        DeskTray.validate({"width_mm": 100, "depth_mm": 120})
    assert exc.value.keys == ["depth_mm", "width_mm"]
    assert DeskTray.validate({"width_mm": 120, "depth_mm": 120})["depth_mm"] == 120.0  # square is fine


@pytest.mark.parametrize(
    "features, ok",
    [
        ([name("front", mode="deboss", depth_mm=WALL_MM - MIN_SKIN_MM)], True),  # 2.4 − 1.2 = 1.2 mm left
        ([name("front", mode="deboss", depth_mm=1.3)], False),
        ([{"type": "motif", "motif_id": "lotus", "anchor": "front", "mode": "deboss", "depth_mm": 0.8}], True),
        ([name("front", depth_mm=1.5)], True),  # raised: adds plastic
    ],
)
def test_a_cut_in_name_leaves_skin_in_the_front_wall(features, ok):
    params = DeskTray.validate({})
    if ok:
        DeskTray.validate_content(params, features)
        return
    with pytest.raises(ParamOutOfRange) as exc:
        DeskTray.validate_content(params, features)
    # the wall is fixed: the refusal names it and the cut, never the Depth slider, and never says "make it thicker"
    assert exc.value.keys == ["features[0].depth_mm", WALL_KEY] and WALL_KEY == "front_wall_mm"
    assert "depth_mm" not in exc.value.keys and "thick" not in exc.value.message
    assert "front wall" in exc.value.message and "keep the cut-in to 1.2 mm or less" in exc.value.message
