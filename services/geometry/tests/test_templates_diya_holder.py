"""``diya_holder@1`` (Diya): the cup and rim measured on the built solid, the five-cup plate as one body with a web
between cavities, the floating bowl, each style's size range, and the skin rule on the rim."""

from __future__ import annotations

import numpy as np
import pytest

from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.templates.diya_holder import (
    BASE_MM,
    BOWL_DEPTH_MM,
    DIYA_D_MM,
    DIYA_DEPTH_MM,
    RIM_T_MM,
    RIM_W_MM,
    SIZE_RANGE,
    DiyaHolder,
    Layout,
)
from aakar_geometry.templates.plates import MIN_SKIN_MM

from carrier_checks import check_body, check_frames, round_holes, section


def buti(motif_id: str = "lotus", **extra) -> dict:
    return {"type": "motif", "motif_id": motif_id, "anchor": "rim", **extra}


def _down_hits(mesh, x: float, y: float, z: float) -> list[float]:
    hits, _, _ = mesh.ray.intersects_location([[x, y, z]], [[0, 0, -1.0]], multiple_hits=True)
    return sorted(float(h) for h in hits[:, 2])


@pytest.mark.parametrize("size, wall", [(85, 2.0), (100, 2.4), (160, 3.0)])
def test_the_single_cup_holds_a_55_mm_diya_under_a_flat_rim_with_the_anchor_on_it(size, wall):
    params = DiyaHolder.validate({"style": "single", "size_mm": size, "wall_mm": wall})
    L = Layout(params)
    mesh = check_body(DiyaHolder, params)
    check_frames(DiyaHolder, params, mesh)
    assert mesh.extents == pytest.approx([size, size, BASE_MM + DIYA_DEPTH_MM], abs=1e-6)
    (centre, _, widest), = round_holes(section(mesh, L.top - 0.5))
    assert abs(centre.x) < 1e-3 and abs(centre.y) < 1e-3
    assert widest == pytest.approx(DIYA_D_MM / 2.0, abs=0.3)  # the cavity's mouth
    # the cavity floor is the base plate; the rim is RIM_T_MM of solid plastic out to RIM_W_MM from the mouth
    assert _down_hits(mesh, 0.0, 0.0, L.top + 5.0) == pytest.approx([0.0, BASE_MM], abs=1e-6)
    r = L.r_mouth + RIM_W_MM - 0.5
    assert _down_hits(mesh, 0.0, -r, L.top + 5.0)[-2:] == pytest.approx([L.top - RIM_T_MM - 0.5, L.top], abs=0.25)
    # the anchor sits wholly on the flat rim, in front (−Y)
    frame = DiyaHolder.anchor_frame("rim", params)
    assert np.allclose(frame.normal, [0, 0, 1]) and frame.origin[1] < 0
    for x, y, _ in frame.corners():
        assert L.r_mouth < np.hypot(x, y) < L.r_rim


@pytest.mark.parametrize("size, wall", [(150, 2.0), (150, 3.0), (160, 2.4)])
def test_five_cups_make_one_plate_with_a_web_between_the_cavities(size, wall):
    params = DiyaHolder.validate({"style": "panch", "size_mm": size, "wall_mm": wall})
    L = Layout(params)
    mesh = check_body(DiyaHolder, params)
    check_frames(DiyaHolder, params, mesh)
    assert float(max(mesh.extents[:2])) == pytest.approx(size, abs=1e-6)
    rim = section(mesh, L.top - 0.5)
    assert not hasattr(rim, "geoms")  # one polygon: the cups join into one holder
    assert len(round_holes(rim)) == 5
    assert L.web >= wall
    for x, y in L.satellites:
        assert _down_hits(mesh, x, y, L.top + 5.0) == pytest.approx([0.0, BASE_MM], abs=1e-6)


@pytest.mark.parametrize("size", [85, 120, 160])
def test_the_floating_bowl_is_the_size_less_two_rims(size):
    params = DiyaHolder.validate({"style": "float", "size_mm": size})
    L = Layout(params)
    mesh = check_body(DiyaHolder, params)
    assert mesh.extents == pytest.approx([size, size, BASE_MM + BOWL_DEPTH_MM], abs=1e-6)
    (_, _, widest), = round_holes(section(mesh, L.top - 0.5))
    assert widest == pytest.approx(size / 2.0 - RIM_W_MM, abs=0.3)
    assert _down_hits(mesh, 0.0, 0.0, L.top + 5.0) == pytest.approx([0.0, BASE_MM], abs=1e-6)


@pytest.mark.parametrize(
    "style, size, ok",
    [("single", 85, True), ("single", 160, True), ("float", 85, True), ("panch", 145, False), ("panch", 150, True)],
)
def test_only_five_cups_refuse_part_of_the_size_slider(style, size, ok):
    lo, hi = SIZE_RANGE[style]
    assert (style == "panch") == (lo > DiyaHolder.params["size_mm"].min)
    if ok:
        assert DiyaHolder.validate({"style": style, "size_mm": size})["size_mm"] == float(size)
        return
    with pytest.raises(ParamOutOfRange) as exc:
        DiyaHolder.validate({"style": style, "size_mm": size})
    assert exc.value.keys == ["size_mm", "style"]
    assert f"between {lo:g} and {hi:g} mm" in exc.value.message and "too small" in exc.value.message


@pytest.mark.parametrize(
    "features, ok",
    [
        ([buti("lotus", depth_mm=0.6)], True),  # a motif's default mode is deboss: 3 − 0.6 = 2.4 mm left
        ([buti("lotus", mode="deboss", depth_mm=RIM_T_MM - MIN_SKIN_MM)], True),  # exactly the skin
        ([buti("lotus", mode="deboss", depth_mm=RIM_T_MM - MIN_SKIN_MM + 0.1)], False),
        ([buti("star_rangoli", mode="emboss", depth_mm=1.5)], True),  # raised: adds plastic
    ],
)
def test_a_cut_in_motif_leaves_skin_in_the_rim(features, ok):
    params = DiyaHolder.validate({})
    if ok:
        DiyaHolder.validate_content(params, features)
        return
    with pytest.raises(ParamOutOfRange) as exc:
        DiyaHolder.validate_content(params, features)
    assert exc.value.keys == ["features[0].depth_mm", "wall_mm"]
    assert "rim" in exc.value.message and "motif" in exc.value.message
