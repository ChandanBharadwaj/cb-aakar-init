"""``fluted_planter@1`` (Gamla): the drains, the reservoir and its spout measured on the built solid, the wall under the
band, the flat rim, the skin rule and the even-facets rule."""

from __future__ import annotations

import math

import numpy as np
import pytest

from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.templates.fluted_planter import (
    DRAIN_D_MM,
    FLOOR_MM,
    LIP_H_MM,
    RESERVOIR_H_MM,
    RIM_W_MM,
    SPOUT_DROP_MM,
    SPOUT_H_MM,
    SPOUT_W_MM,
    FlutedPlanter,
    Layout,
)
from aakar_geometry.templates.plates import HOLE_ALLOWANCE_MM, MIN_SKIN_MM

from carrier_checks import round_holes, section

CORNERS = [
    dict(width_mm=80, height_mm=70, facets=12, wall_mm=2.0),
    dict(width_mm=110, height_mm=100, facets=10, wall_mm=2.4),
    dict(width_mm=160, height_mm=150, facets=8, wall_mm=3.0),
]


def _hits(mesh, origin, direction, axis: int) -> list[float]:
    locations, _, _ = mesh.ray.intersects_location([origin], [direction], multiple_hits=True)
    return sorted(float(v) for v in np.asarray(locations).reshape(-1, 3)[:, axis])


def motif(anchor: str, mode: str, depth_mm: float) -> dict:
    return {"type": "motif", "motif_id": "lotus", "anchor": anchor, "mode": mode, "depth_mm": depth_mm}


def test_descriptor_note_and_no_hardware():
    desc = FlutedPlanter.descriptor()
    assert (desc["id"], desc["family"], desc["environment"]) == ("fluted_planter", "planter", "balcony_daylight")
    p = desc["params"]
    assert (p["width_mm"]["min"], p["width_mm"]["max"], p["width_mm"]["default"]) == (80, 160, 110.0)
    assert (p["height_mm"]["min"], p["height_mm"]["max"], p["height_mm"]["default"]) == (70, 150, 100.0)
    assert (p["facets"]["type"], p["facets"]["min"], p["facets"]["max"], p["facets"]["step"], p["facets"]["default"]) == ("integer", 8, 12, 2, 10)
    assert (p["wall_mm"]["min"], p["wall_mm"]["max"], p["wall_mm"]["default"]) == (2.0, 3.0, 2.4)
    assert [a["id"] for a in desc["anchors"]] == ["band", "rim"]
    assert desc["hardware"] == [] and FlutedPlanter.hardware_for(FlutedPlanter.validate({})) == []
    with pytest.raises(ParamOutOfRange) as exc:  # an odd count puts a corner, not a flat, at the front
        FlutedPlanter.validate_combination({"width_mm": 110, "height_mm": 100, "facets": 9, "wall_mm": 2.4})
    assert exc.value.keys == ["facets"]
    # the printer's note: the spout, "print as is", the plants are the customer's, the name in curly quotes
    params = FlutedPlanter.validate({"width_mm": 160, "height_mm": 90, "facets": 8})
    features = [{"type": "emboss_text", "text": "Tulsi", "anchor": "band", "depth_mm": 1.0}, motif("rim", "emboss", 0.6)]
    note = FlutedPlanter.karigar_note_for(params, features)
    assert "spout" in note and "print as is" in note and "Pot only" in note and "“Tulsi”" in note
    assert "8 facets" in note and "160 mm" in note and "on two ribs" in note  # 160 wide: the floor bridges on two ribs
    assert "mesh" not in note.lower() and "stl" not in note.lower()
    assert FlutedPlanter.hardware_for(params) == []


@pytest.mark.parametrize("raw", CORNERS)
def test_width_is_the_longest_side_and_the_floors_stack_over_a_reservoir(raw):
    params = FlutedPlanter.validate(raw)
    L = Layout(params)
    mesh = FlutedPlanter.build_body(params)
    assert mesh.body_count == 1 and mesh.is_watertight
    assert mesh.extents.max() == pytest.approx(raw["width_mm"], abs=1e-3)  # the rim lip adds nothing beyond it
    assert mesh.extents[2] == pytest.approx(raw["height_mm"], abs=1e-3)
    # straight up through a bay (between the drains and the ribs): base plate, the reservoir, then the inner floor
    x = y = 0.25 * L.ai_res
    assert all(abs(x - rx) > 2.0 for rx in L.rib_xs)
    zs = _hits(mesh, [x, y, -5.0], [0, 0, 1.0], axis=2)
    assert zs == pytest.approx([0.0, FLOOR_MM, FLOOR_MM + RESERVOIR_H_MM, 2 * FLOOR_MM + RESERVOIR_H_MM], abs=1e-6)
    assert FLOOR_MM >= 2.0 and L.bay_mm <= 40.0  # the inner floor bridges no more than an FDM printer lays cleanly


@pytest.mark.parametrize("raw", CORNERS)
def test_five_drains_pierce_the_inner_floor_only(raw):
    params = FlutedPlanter.validate(raw)
    L = Layout(params)
    mesh = FlutedPlanter.build_body(params)
    drains = round_holes(section(mesh, L.z_res1 + FLOOR_MM / 2))
    assert len(drains) == 5
    r = (DRAIN_D_MM + HOLE_ALLOWANCE_MM) / 2
    for centre, narrowest, widest in drains:
        assert narrowest == pytest.approx(r, abs=2e-3) and widest < r + 0.01
    centres = sorted((round(c.x, 3), round(c.y, 3)) for c, _, _ in drains)
    ring = 0.5 * L.ai_res
    assert centres == sorted((round(x, 3), round(y, 3)) for x, y in [(0, 0), (ring, 0), (-ring, 0), (0, ring), (0, -ring)])
    assert round_holes(section(mesh, FLOOR_MM / 2)) == []  # the base plate under the reservoir is sealed
    for centre, _, _ in drains:  # from above the hole opens into the reservoir: the first plastic is a rib's top or the base plate
        zs = _hits(mesh, [centre.x + 0.3, centre.y + 0.3, L.H + 5.0], [0, 0, -1.0], axis=2)  # off the rib's centre line
        assert FLOOR_MM - 1e-6 <= zs[-1] < L.z_res1 - 0.5 and zs[0] == pytest.approx(0.0, abs=1e-6)


@pytest.mark.parametrize("raw", CORNERS)
def test_the_spout_opens_the_front_wall_at_reservoir_level_and_nowhere_else(raw):
    params = FlutedPlanter.validate(raw)
    L = Layout(params)
    mesh = FlutedPlanter.build_body(params)
    z_spout = L.z_res1 - SPOUT_DROP_MM - SPOUT_H_MM / 2
    through = _hits(mesh, [0.0, -200.0, z_spout], [0, 1.0, 0], axis=1)
    assert through[0] > -L.ai_res  # the first plastic the ray meets is inside the pot: the front wall is open here
    beside = _hits(mesh, [SPOUT_W_MM, -200.0, z_spout], [0, 1.0, 0], axis=1)
    assert beside[0] == pytest.approx(-L.ao(z_spout), abs=1e-3)  # a spout-width to the side the wall is whole
    # the reservoir is a straight prism inside the leaning wall, so down here the wall is wall_mm plus the lean's gain
    assert beside[1] - beside[0] == pytest.approx(L.ao(z_spout) - L.ai_res, abs=1e-3) and beside[1] - beside[0] >= raw["wall_mm"]
    above = _hits(mesh, [0.0, -200.0, L.z_res1 - SPOUT_DROP_MM / 2], [0, 1.0, 0], axis=1)
    assert above[0] == pytest.approx(-L.ao(L.z_res1 - SPOUT_DROP_MM / 2), abs=1e-3)  # closed again under the inner floor
    below = _hits(mesh, [0.0, -200.0, FLOOR_MM / 2], [0, 1.0, 0], axis=1)
    assert below[0] == pytest.approx(-L.ao(FLOOR_MM / 2), abs=1e-3)  # and through the base plate


@pytest.mark.parametrize("raw", CORNERS)
def test_the_band_sits_on_a_wall_of_wall_mm_and_the_rim_on_a_flat_ring(raw):
    params = FlutedPlanter.validate(raw)
    L = Layout(params)
    mesh = FlutedPlanter.build_body(params)
    band = FlutedPlanter.anchor_frame("band", params)
    rim = FlutedPlanter.anchor_frame("rim", params)
    ys = _hits(mesh, [0.0, -200.0, band.origin[2]], [0, 1.0, 0], axis=1)
    assert ys[0] == pytest.approx(band.origin[1], abs=1e-3) and ys[1] - ys[0] == pytest.approx(raw["wall_mm"], abs=1e-3)
    assert band.normal[1] < 0 and band.normal[2] < 0  # the facet leans out going up, so its normal tips down
    assert math.degrees(math.atan2(-band.normal[2], -band.normal[1])) < 10.0  # under the 10° lean the walls are allowed
    assert min(band.size_mm) >= 8.0 and min(rim.size_mm) >= 8.0
    assert np.allclose(rim.normal, [0, 0, 1]) and rim.origin[2] == pytest.approx(raw["height_mm"])
    # the rim's flat top: every point across the ring's width in front is at Z = height, RIM_W_MM wide from the lip's
    # edge at −A; just inside the ring the ray falls into the pot, just outside the lip it meets nothing
    for dy in (-RIM_W_MM / 2 + 0.3, 0.0, RIM_W_MM / 2 - 0.3):
        zs = _hits(mesh, [0.0, rim.origin[1] + dy, 200.0], [0, 0, -1.0], axis=2)
        assert zs[-1] == pytest.approx(raw["height_mm"], abs=1e-6)
    ys = _hits(mesh, [0.0, -200.0, raw["height_mm"] - 0.05], [0, 1.0, 0], axis=1)
    assert ys[0] == pytest.approx(-L.A, abs=1e-3) and ys[1] - ys[0] == pytest.approx(RIM_W_MM, abs=0.02)
    assert _hits(mesh, [0.0, -(L.A - RIM_W_MM) + 0.3, 200.0], [0, 0, -1.0], axis=2)[-1] < raw["height_mm"] - 1e-3
    assert _hits(mesh, [0.0, -(L.A + 0.3), 200.0], [0, 0, -1.0], axis=2) == []
    # the lip's straight band is LIP_H_MM tall: half-way down it the outer face is still at −A
    ys = _hits(mesh, [0.0, -200.0, raw["height_mm"] - LIP_H_MM / 2], [0, 1.0, 0], axis=1)
    assert ys[0] == pytest.approx(-L.A, abs=1e-3)


@pytest.mark.parametrize(
    "wall, features, ok",
    [
        (2.0, [motif("band", "deboss", 0.8)], True),  # 2.0 − 0.8 = 1.2 mm of wall left
        (2.0, [motif("band", "deboss", 0.9)], False),
        (3.0, [motif("band", "deboss", 1.5)], True),
        (2.0, [motif("band", "emboss", 1.5)], True),
        (2.0, [motif("rim", "deboss", 1.5)], True),  # the 6 mm lip has plenty under a rim cut-in
        (2.4, [motif("rim", "deboss", 0.6), motif("band", "deboss", 1.3)], False),
    ],
)
def test_a_cut_in_motif_leaves_skin_in_the_wall(wall, features, ok):
    params = FlutedPlanter.validate({"wall_mm": wall})
    if ok:
        FlutedPlanter.validate_content(params, features)
        return
    with pytest.raises(ParamOutOfRange) as exc:
        FlutedPlanter.validate_content(params, features)
    assert exc.value.keys[-1] == "wall_mm" and "the wall" in exc.value.message
    assert f"{MIN_SKIN_MM:.1f} mm" in exc.value.message
