"""``keycap_mx@1`` (Kunji): the MX cross slot measured on the solid for every slop, the 1u footprint and 1.5 mm
walls, the dished top that content fuses to, legends that must stay printable, resin in the karigar's note."""

from __future__ import annotations

import numpy as np
import pytest
from shapely.geometry import Polygon

from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.features import check_features, prepare_marks
from aakar_geometry.features.fetch import LocalFileFetcher
from aakar_geometry.templates import KeycapMx
from aakar_geometry.templates.keycap_mx import (
    BOTTOM_MM,
    DISH_MM,
    HEIGHT_MM,
    SKIRT_MM,
    STEM_DEPTH_MM,
    STEM_OD_MM,
    TOP_MM,
    WALL_MM,
    Layout,
    cross_bars,
)

from carrier_checks import relief, section

SLOPS = (0.3, 0.4, 0.5)


def name(text: str, **extra) -> dict:
    return {"type": "emboss_text", "text": text, "anchor": "top", "depth_mm": 0.6, **extra}


def _parts(area):
    return list(area.geoms) if hasattr(area, "geoms") else [area]


def _stem(mesh, z: float):
    """The stem's cross-section at height ``z``: the smaller of the two pieces (the shell's ring is the other)."""
    return min(_parts(section(mesh, z)), key=lambda p: p.area)


def test_descriptor():
    desc = KeycapMx.descriptor()
    assert (desc["id"], desc["family"], desc["name"], desc["environment"]) == ("keycap_mx", "keycap", "Kunji keycap", "desk_oak")
    p = desc["params"]["stem_slop_mm"]
    assert (p["min"], p["max"], p["default"]) == (0.3, 0.5, 0.4)
    (top,) = desc["anchors"]
    assert top["id"] == "top" and top["accepts"] == ["relief_image", "emboss_text"] and top["max_relief_mm"] == 0.6
    assert desc["min_feature_mm"] == 1.0 and desc["constraints"]["min_wall_mm"] == 0.8
    assert desc["hardware"] == []


@pytest.mark.parametrize("slop", SLOPS)
def test_the_mx_cross_slot_is_keyv2s_cross_with_the_slop(slop):
    mesh = KeycapMx.build_body(KeycapMx.validate({"stem_slop_mm": slop}))
    (aw, ah), (bw, bh) = cross_bars(slop)
    assert (aw, ah) == pytest.approx((4.03 + slop, 1.25 + slop / 3)) and (bw, bh) == pytest.approx((1.15 + slop / 3, 4.23 + slop / 3))
    stem = _stem(mesh, 2.0)
    assert len(stem.interiors) == 1  # the cross
    cross = np.asarray(stem.interiors[0].coords)
    assert np.ptp(cross[:, 0]) == pytest.approx(aw, abs=1e-4) and np.ptp(cross[:, 1]) == pytest.approx(bh, abs=1e-4)
    assert Polygon(stem.interiors[0]).area == pytest.approx(aw * ah + bw * bh - bw * ah, rel=1e-4)
    x0, y0, x1, y1 = stem.exterior.bounds
    assert x1 - x0 == pytest.approx(STEM_OD_MM, abs=0.01)
    # open at the rim, STEM_DEPTH_MM deep (the switch needs 3.6 mm), solid above
    assert len(_stem(mesh, 0.2).interiors) == 1 and len(_stem(mesh, STEM_DEPTH_MM - 0.2).interiors) == 1
    assert STEM_DEPTH_MM >= 3.6 and len(_stem(mesh, STEM_DEPTH_MM + 0.2).interiors) == 0


def test_the_1u_footprint_the_straight_skirt_the_walls_and_the_dish():
    params = KeycapMx.validate({})
    L = Layout(params)
    mesh = KeycapMx.build_body(params)
    assert mesh.extents == pytest.approx([BOTTOM_MM, BOTTOM_MM, HEIGHT_MM], abs=1e-6)
    shell = max(_parts(section(mesh, SKIRT_MM / 2.0)), key=lambda p: p.area)
    assert np.ptp(np.asarray(shell.exterior.coords)[:, 0]) == pytest.approx(BOTTOM_MM, abs=1e-6)  # straight at the rim
    for z in (SKIRT_MM / 2.0, 3.0, 4.5):
        shell = max(_parts(section(mesh, z)), key=lambda p: p.area)
        outer = np.ptp(np.asarray(shell.exterior.coords)[:, 0])
        inner = max(np.ptp(np.asarray(r.coords)[:, 0]) for r in shell.interiors)
        assert outer == pytest.approx(L.width_at(z), abs=1e-6)
        assert (outer - inner) / 2.0 == pytest.approx(L.wall_h, abs=1e-6) and L.wall_h >= WALL_MM  # 1.5 mm square to the slope

    def top_at(x, y):
        hits, _, _ = mesh.ray.intersects_location([[x, y, 20.0]], [[0, 0, -1.0]], multiple_hits=False)
        return float(hits[0][2])

    assert top_at(0.0, 0.0) == pytest.approx(HEIGHT_MM - DISH_MM, abs=0.02)  # the dish's bottom
    assert top_at(5.5, 5.5) == pytest.approx(HEIGHT_MM)  # the flat corners of the top
    frame = KeycapMx.anchor_frame("top", params)
    assert frame.origin == pytest.approx([0.0, 0.0, HEIGHT_MM - DISH_MM]) and frame.size_mm == pytest.approx((TOP_MM - 1.6, TOP_MM - 1.6))


def test_legends_must_stay_printable_on_a_cap_this_small():
    params = KeycapMx.validate({})
    for legend in ("A", "K", "Fn", "OK", "अ"):
        marks = prepare_marks(KeycapMx, params, check_features(KeycapMx, [name(legend)]))
        assert marks[0].stroke.ok and marks[0].stroke.min_feature_mm == 1.0, legend
    with pytest.raises(ParamOutOfRange) as exc:  # three wide letters: strokes under 1 mm
        prepare_marks(KeycapMx, params, check_features(KeycapMx, [name("Esc")]))
    assert exc.value.message == "These letters would be too thin to print at this size; try fewer letters or a larger piece"
    with pytest.raises(ParamOutOfRange) as exc:
        check_features(KeycapMx, [name("Ctrl")])  # the family takes three characters
    assert exc.value.keys == ["features[0].text"]
    with pytest.raises(ParamOutOfRange) as exc:
        check_features(KeycapMx, [{"type": "emboss_text", "text": "A", "anchor": "top"}])  # the default 1.2 mm is too deep
    assert exc.value.keys == ["features[0].depth_mm"] and "at most 0.6 mm deep" in exc.value.message
    with pytest.raises(ParamOutOfRange):
        check_features(KeycapMx, [relief("top", relief_mm=0.8)])


@pytest.mark.parametrize("feature", [name("A"), name("K", mode="deboss"), relief("top"), relief("top", "deboss")], ids=["A-raised", "K-cut", "photo-raised", "photo-cut"])
def test_content_on_the_dished_top_fuses_and_passes_the_printability_checks(feature, content_dir):
    from aakar_inspect import Constraints, inspect_mesh

    params = KeycapMx.validate({})
    mesh = KeycapMx.build(params, [feature], LocalFileFetcher(content_dir))
    assert mesh.is_watertight and mesh.body_count == 1
    assert mesh.extents[:2] == pytest.approx([BOTTOM_MM, BOTTOM_MM], abs=1e-6) and mesh.extents[2] <= HEIGHT_MM + 0.6 - DISH_MM + 1e-6
    report, _ = inspect_mesh(mesh, Constraints(min_wall_mm=KeycapMx.constraints.min_wall_mm))
    assert report["passed"] is True, report["checks"]["thinnest_wall"]


def test_karigar_note_recommends_resin_and_a_test_fit():
    note = KeycapMx.karigar_note(KeycapMx.validate({"stem_slop_mm": 0.35}))
    assert note.startswith("A Kunji 1u keycap for a Cherry MX switch, 18.16 mm square at the rim")
    assert "cross slot 4.38 × 1.37 and 1.27 × 4.35 mm (0.35 mm slop), 4 mm deep" in note
    assert "Resin is recommended" in note and "test-fit" in note and "stem down" in note
