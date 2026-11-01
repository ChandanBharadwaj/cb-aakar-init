"""``paisley_hook@1`` (Khoonti): screw holes and countersinks measured on the built solid, the leaning peg, the motif area
above the peg, the skin rule and the hardware."""

from __future__ import annotations

import math

import numpy as np
import pytest
from shapely.geometry import Point

from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.templates.paisley_hook import (
    CLEAR_MM,
    CSK_D_MM,
    CSK_DEPTH_MM,
    LEAN_DEG,
    SCREW_D_MM,
    STOP_H_MM,
    STOP_MM,
    STOP_RISE_MM,
    Layout,
    PaisleyHook,
)
from aakar_geometry.templates.plates import HOLE_ALLOWANCE_MM, MIN_SKIN_MM

from carrier_checks import round_holes, section


def buti(motif_id: str = "paisley", mode: str = "emboss", depth_mm: float = 0.8) -> dict:
    return {"type": "motif", "motif_id": motif_id, "anchor": "face", "mode": mode, "depth_mm": depth_mm}


def test_descriptor_and_hardware():
    desc = PaisleyHook.descriptor()
    assert (desc["id"], desc["family"], desc["environment"]) == ("paisley_hook", "wall_hook", "studio")
    p = desc["params"]
    assert p["shape"]["options"] == ["paisley", "lotus", "arrow"]
    assert (p["height_mm"]["min"], p["height_mm"]["max"], p["height_mm"]["default"]) == (60, 120, 90.0)
    assert (p["plate_mm"]["min"], p["plate_mm"]["max"]) == (4, 6)
    assert (p["peg_len_mm"]["min"], p["peg_len_mm"]["max"]) == (20, 35)
    assert (p["peg_d_mm"]["min"], p["peg_d_mm"]["max"]) == (10, 14)
    assert [a["id"] for a in desc["anchors"]] == ["face"] and desc["features_supported"] == ["motif"]
    assert desc["hardware"] == [{"sku": "nameplate_screws", "qty": 1}]
    assert PaisleyHook.hardware_for(PaisleyHook.validate({"height_mm": 120})) == [{"sku": "nameplate_screws", "qty": 1}]
    assert desc["constraints"]["max_overhang_deg"] == 45


@pytest.mark.parametrize("shape", ["paisley", "lotus", "arrow"])
@pytest.mark.parametrize("height, plate, peg_len, peg_d", [(60, 4, 35, 14), (90, 5, 28, 12), (120, 6, 20, 10)])
def test_two_countersunk_screw_holes_on_the_centre_line(shape, height, plate, peg_len, peg_d):
    params = PaisleyHook.validate({"shape": shape, "height_mm": height, "plate_mm": plate, "peg_len_mm": peg_len, "peg_d_mm": peg_d})
    L = Layout(params)
    mesh = PaisleyHook.build_body(params)
    # below the countersink: two through-holes of the screw size plus the allowance, on x = 0, top and bottom
    holes = round_holes(section(mesh, plate - CSK_DEPTH_MM - 0.5))
    assert len(holes) == 2
    for centre, narrowest, widest in holes:
        assert abs(centre.x) < 1e-6
        assert narrowest == pytest.approx((SCREW_D_MM + HOLE_ALLOWANCE_MM) / 2, abs=2e-3) and widest < (SCREW_D_MM + HOLE_ALLOWANCE_MM) / 2 + 0.01
    ys = sorted(c.y for c, _, _ in holes)
    assert ys == [pytest.approx(L.y_bot, abs=1e-3), pytest.approx(L.y_top, abs=1e-3)]
    assert ys[1] > 0 > ys[0]
    # the countersink opens toward the front face: wider just under the face than halfway down the cone
    mouth = {round(c.y, 2): widest for c, _, widest in round_holes(section(mesh, plate - 0.1))}
    mid = {round(c.y, 2): widest for c, _, widest in round_holes(section(mesh, plate - CSK_DEPTH_MM / 2))}
    for y in ys:
        assert mouth[round(y, 2)] == pytest.approx(CSK_D_MM / 2, abs=0.15)
        assert (SCREW_D_MM + HOLE_ALLOWANCE_MM) / 2 + 0.3 < mid[round(y, 2)] < mouth[round(y, 2)] - 0.3
    # the peg is clear of the bottom countersink
    assert L.y_peg - L.r_y - (L.y_bot + CSK_D_MM / 2) >= CLEAR_MM - 1e-6


@pytest.mark.parametrize("height, peg_len, peg_d", [(60, 35, 14), (90, 28, 12), (120, 20, 10)])
def test_the_peg_leans_up_the_plate_and_ends_in_a_stop(height, peg_len, peg_d):
    params = PaisleyHook.validate({"height_mm": height, "peg_len_mm": peg_len, "peg_d_mm": peg_d})
    L = Layout(params)
    mesh = PaisleyHook.build_body(params)
    # above the face only the peg is cut: an ellipse whose centre climbs +Y with height at the lean angle
    low = section(mesh, L.T + 5.0)
    high = section(mesh, L.T + 15.0)
    assert low.geom_type == "Polygon" and high.geom_type == "Polygon" and not low.interiors
    assert high.centroid.y - low.centroid.y == pytest.approx(10.0 * math.tan(math.radians(LEAN_DEG)), abs=0.05)
    assert abs(low.centroid.x) < 0.01  # on the centre line (the tessellated ellipse's centroid wanders a hair)
    x0, _, x1, _ = low.bounds
    assert x1 - x0 == pytest.approx(peg_d, abs=0.05)  # the shaft is peg_d across
    # the stop at the tip is STOP_MM proud of the shaft all round
    band_z = L.T + (peg_len + STOP_RISE_MM + STOP_H_MM / 2) * math.cos(L.lean)  # the bed plane through the band's centre
    stop = section(mesh, band_z)
    sx0, _, sx1, _ = stop.bounds
    assert sx1 - sx0 == pytest.approx(peg_d + 2 * STOP_MM, abs=0.1)
    # the plate stays the longest extent and the whole hook rests on the bed
    assert max(mesh.extents) == pytest.approx(height, abs=1e-6)
    assert mesh.bounds[0][2] == pytest.approx(0.0, abs=1e-6)
    assert mesh.bounds[1][2] == pytest.approx(L.reach[0], abs=0.1)


@pytest.mark.parametrize("shape", ["paisley", "lotus", "arrow"])
def test_the_motif_area_sits_in_the_upper_half_clear_of_the_top_hole_and_the_peg(shape):
    params = PaisleyHook.validate({"shape": shape, "height_mm": 60, "peg_d_mm": 14})
    L = Layout(params)
    frame = PaisleyHook.anchor_frame("face", params)
    w, h = frame.size_mm
    assert min(w, h) >= 8.0
    assert frame.origin[2] == pytest.approx(L.T) and abs(frame.origin[0]) < 1e-6
    corners = frame.corners()
    assert corners[:, 1].min() >= 0.0  # the upper half
    assert np.hypot(corners[:, 0], corners[:, 1] - L.y_top).min() >= CSK_D_MM / 2 + CLEAR_MM - 1e-6
    assert corners[:, 1].min() - (L.y_peg + L.r_y) >= CLEAR_MM - 1e-6
    for x, y, _ in corners:
        assert L.outline.buffer(-1.5 + 1e-6).covers(Point(x, y))


@pytest.mark.parametrize(
    "plate, feature, ok",
    [
        (4.0, buti("lotus", "deboss", 0.8), True),  # 4 − 0.8 = 3.2 mm left
        (4.0, buti("lotus", "deboss", 2.8), True),  # exactly MIN_SKIN_MM left
        (4.0, buti("lotus", "deboss", 3.0), False),
        (4.0, buti("paisley", "emboss", 1.5), True),  # raised: adds plastic
    ],
)
def test_a_cut_in_motif_leaves_plastic_in_the_plate(plate, feature, ok):
    params = PaisleyHook.validate({"plate_mm": plate})
    if ok:
        PaisleyHook.validate_content(params, [feature])
        return
    with pytest.raises(ParamOutOfRange) as exc:
        PaisleyHook.validate_content(params, [feature])
    assert exc.value.keys == ["features[0].depth_mm", "plate_mm"]
    assert f"{MIN_SKIN_MM:g} mm" in exc.value.message


def test_a_plate_too_short_for_its_holes_and_peg_is_refused_with_a_sentence():
    params = {k: p.default for k, p in PaisleyHook.params.items()}
    params.update({"height_mm": 40.0, "peg_d_mm": 14.0})
    with pytest.raises(ParamOutOfRange) as exc:
        PaisleyHook.validate_combination(params)
    assert exc.value.keys == ["height_mm", "peg_d_mm"] and "make it taller" in exc.value.message


def test_karigar_note_gives_the_load_rating_and_the_screws():
    note = PaisleyHook.karigar_note(PaisleyHook.validate({"shape": "lotus"}))
    assert "rated 2 kg on two wall anchors" in note.lower()
    assert "countersunk" in note and "4.5 mm screws" in note and "lotus" in note
    assert "mesh" not in note.lower() and "stl" not in note.lower()
