"""``hanging_ornament@1`` (Jhoomar): the hanging hole and its rim, the raised border, room for the picture."""

from __future__ import annotations

import numpy as np
import pytest

from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.templates import HangingOrnament
from aakar_geometry.templates.hanging_ornament import BORDER_HEIGHT_MM, HANG_HOLE_MM, MIN_BORDER_MM, Layout
from aakar_geometry.templates.plates import HOLE_ALLOWANCE_MM, RIM_MM

from carrier_checks import relief, round_holes, section

SILHOUETTES = ("disc", "star", "bauble")


def test_descriptor():
    desc = HangingOrnament.descriptor()
    assert (desc["id"], desc["family"], desc["environment"]) == ("hanging_ornament", "ornament", "teak_table_candlelight")
    p = desc["params"]
    assert p["silhouette"]["options"] == list(SILHOUETTES) and p["silhouette"]["default"] == "disc"
    assert (p["diameter_mm"]["min"], p["diameter_mm"]["max"], p["diameter_mm"]["default"]) == (50, 90, 70.0)
    assert (p["thickness_mm"]["min"], p["thickness_mm"]["max"], p["thickness_mm"]["default"]) == (3.0, 5.0, 4.0)
    assert (p["border_mm"]["min"], p["border_mm"]["max"], p["border_mm"]["default"]) == (0, 6, 3.0)
    assert [a["id"] for a in desc["anchors"]] == ["face_front", "face_back"]
    assert desc["hardware"] == [{"sku": "cord_200", "qty": 1}]
    assert HANG_HOLE_MM >= 3.0


@pytest.mark.parametrize("silhouette", SILHOUETTES)
@pytest.mark.parametrize("diameter, border", [(50, 6.0), (70, 3.0), (90, 0.0)])
def test_hanging_hole_at_the_top_with_a_two_mm_rim(silhouette, diameter, border):
    params = HangingOrnament.validate({"silhouette": silhouette, "diameter_mm": diameter, "border_mm": border, "thickness_mm": 3})
    mesh = HangingOrnament.build_body(params)
    area = section(mesh, 1.5)
    holes = round_holes(area)
    assert len(holes) == 1
    centre, narrowest, widest = holes[0]
    cut = HANG_HOLE_MM + HOLE_ALLOWANCE_MM
    assert narrowest == pytest.approx(cut / 2, abs=2e-3) and widest < cut / 2 + 0.01
    rim = area.exterior.distance(centre) - widest
    assert RIM_MM - 1e-6 <= rim <= RIM_MM + 0.05  # 2 mm of plastic, and the hole as high as that allows
    assert centre.x == pytest.approx(0.0, abs=1e-6) and centre.y > 0.25 * diameter
    # the hole goes right through the border as well
    assert not mesh.ray.intersects_any([[centre.x, centre.y, 20.0]], [[0, 0, -1.0]])[0]
    # the photo area stays clear of the hole and its rim
    rect = Layout(params).printable_rect()
    assert rect[3] < centre.y - widest - RIM_MM
    # the longest side is the size asked for
    assert max(mesh.extents[:2]) == pytest.approx(diameter, abs=1e-6)


@pytest.mark.parametrize("border", [0.0, 1.5, 4.0])
def test_the_border_stands_proud_of_the_front_only(border):
    params = HangingOrnament.validate({"border_mm": border})
    mesh = HangingOrnament.build_body(params)
    expected_top = params["thickness_mm"] + (BORDER_HEIGHT_MM if border else 0.0)
    assert mesh.bounds[1][2] == pytest.approx(expected_top, abs=1e-6)
    face = HangingOrnament.anchor_frame("face_front", params)
    assert face.origin[2] == pytest.approx(params["thickness_mm"])  # the picture sits on the field, inside the border
    if border:
        # just inside the edge on the right, the ring is BORDER_HEIGHT_MM above the field
        edge_x = mesh.bounds[1][0] - border / 2
        loc, _, _ = mesh.ray.intersects_location([[edge_x, 0.0, 20.0]], [[0, 0, -1.0]], multiple_hits=False)
        assert loc[0][2] == pytest.approx(expected_top, abs=1e-6)
    # the back is flat on the bed
    assert np.isclose(section(mesh, 0.05).area, section(mesh, params["thickness_mm"] - 0.05).area, rtol=1e-6)


@pytest.mark.parametrize("border", [0.5, 1.0])
def test_a_hairline_border_is_refused(border):
    with pytest.raises(ParamOutOfRange) as exc:
        HangingOrnament.validate({"border_mm": border})
    assert exc.value.keys == ["border_mm"] and f"{MIN_BORDER_MM:g} mm" in exc.value.message


def test_both_faces_add_up_for_cut_in_photos():
    params = HangingOrnament.validate({"thickness_mm": 3.0})
    HangingOrnament.validate_content(params, [relief("face_front", "deboss", 0.9), relief("face_back", "deboss", 0.9)])
    with pytest.raises(ParamOutOfRange) as exc:
        HangingOrnament.validate_content(params, [relief("face_front", "deboss", 1.0), relief("face_back", "deboss", 0.9)])
    assert exc.value.keys == ["features[0].relief_mm", "features[1].relief_mm", "thickness_mm"]


def test_karigar_note_mentions_the_cord():
    note = HangingOrnament.karigar_note(HangingOrnament.validate({"silhouette": "star", "border_mm": 0}))
    assert note.startswith("A star Jhoomar ornament, 70 mm across") and "plain front" in note and "cotton cord" in note
