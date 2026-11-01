"""``bookmark_plate@1`` (Nishaan): the tassel hole and its rim, the face below the hole, the skin rule for cut-ins."""

from __future__ import annotations

import itertools

import numpy as np
import pytest
from trimesh.proximity import closest_point

from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.templates.bookmark_plate import ANCHOR_MARGIN_MM, ENDS, HOLE_D_MM, BookmarkPlate, Layout
from aakar_geometry.templates.plates import HOLE_ALLOWANCE_MM, MIN_PRINTABLE_MM, MIN_SKIN_MM, RIM_MM

from carrier_checks import relief, round_holes, section

CORNERS = [(120, 28), (160, 40), (120, 40), (160, 28)]  # (length_mm, width_mm)


def test_descriptor_params_anchor_and_no_hardware():
    desc = BookmarkPlate.descriptor()
    assert (desc["id"], desc["family"], desc["name"], desc["environment"]) == ("bookmark_plate", "bookmark", "Nishaan bookmark", "studio")
    p = desc["params"]
    assert p["end"]["options"] == list(ENDS) and p["end"]["default"] == "rounded"
    assert (p["length_mm"]["min"], p["length_mm"]["max"], p["length_mm"]["default"], p["length_mm"]["handle"]) == (120, 160, 140.0, True)
    assert (p["width_mm"]["min"], p["width_mm"]["max"], p["width_mm"]["default"]) == (28, 40, 32.0)
    assert (p["thickness_mm"]["min"], p["thickness_mm"]["max"], p["thickness_mm"]["default"]) == (2.0, 3.0, 2.4)
    assert [a["id"] for a in desc["anchors"]] == ["face"]
    assert desc["anchors"][0]["accepts"] == ["relief_image", "emboss_text", "motif"]
    assert desc["hardware"] == [] and BookmarkPlate.hardware_for(BookmarkPlate.validate({})) == []
    assert HOLE_D_MM == 4.0


@pytest.mark.parametrize("end", ENDS)
@pytest.mark.parametrize("length, width", CORNERS)
def test_tassel_hole_is_cut_oversize_at_the_top_with_a_two_mm_rim_and_goes_right_through(end, length, width):
    params = BookmarkPlate.validate({"end": end, "length_mm": length, "width_mm": width, "thickness_mm": 2.0})
    mesh = BookmarkPlate.build_body(params)
    assert mesh.extents[1] == pytest.approx(length, abs=1e-6) and mesh.extents[0] == pytest.approx(width, abs=1e-6)
    hole_cut = HOLE_D_MM + HOLE_ALLOWANCE_MM
    for z in (0.5, 1.0, 1.5):  # the same hole at every height: it goes right through
        area = section(mesh, z)
        holes = round_holes(area)
        assert len(holes) == 1
        centre, narrowest, widest = holes[0]
        assert abs(centre.x) < 1e-6 and centre.y > length / 4  # on the centre line, well up the +Y end
        assert narrowest == pytest.approx(hole_cut / 2, abs=2e-3) and widest < hole_cut / 2 + 0.01
        polys = list(area.geoms) if hasattr(area, "geoms") else [area]
        hole_ring = next(ring for poly in polys for ring in poly.interiors)
        assert min(poly.exterior.distance(hole_ring) for poly in polys) >= RIM_MM - 1e-6


@pytest.mark.parametrize("end", ENDS)
@pytest.mark.parametrize("length, width", CORNERS)
def test_the_face_sits_below_the_hole_and_reads_along_the_length(end, length, width):
    params = BookmarkPlate.validate({"end": end, "length_mm": length, "width_mm": width})
    layout = Layout(params)
    frame = BookmarkPlate.anchor_frame("face", params)
    assert np.allclose(frame.u, [0, 1, 0]) and np.allclose(frame.v, [-1, 0, 0]) and np.allclose(frame.normal, [0, 0, 1])
    assert frame.origin[2] == pytest.approx(params["thickness_mm"])
    along, across = frame.size_mm
    assert along >= MIN_PRINTABLE_MM and across >= MIN_PRINTABLE_MM
    assert across <= width - 2 * ANCHOR_MARGIN_MM + 1e-6
    # the whole rectangle is below the hole, its rim and the margin: content never crosses the hole
    top_y = max(c[1] for c in frame.corners())
    assert top_y <= layout.hole_xy[1] - layout.hole_outer - RIM_MM - ANCHOR_MARGIN_MM + 1e-6
    # and every corner lies on the top face
    mesh = BookmarkPlate.build_body(params)
    _, distance, _ = closest_point(mesh, frame.corners())
    assert distance.max() < 0.05


def test_every_corner_of_the_ranges_is_accepted_with_room_for_a_picture():
    for end, length, width, thickness in itertools.product(ENDS, (120, 160), (28, 40), (2.0, 3.0)):
        params = BookmarkPlate.validate({"end": end, "length_mm": length, "width_mm": width, "thickness_mm": thickness})
        rect = Layout(params).printable_rect()
        assert rect is not None
        assert min(rect[2] - rect[0], rect[3] - rect[1]) >= MIN_PRINTABLE_MM


@pytest.mark.parametrize(
    "thickness, features, ok",
    [
        (2.0, [{"type": "emboss_text", "text": "Asha", "anchor": "face", "mode": "deboss", "depth_mm": 0.6}], True),  # 2.0 − 0.6 = 1.4 ≥ 1.2
        (2.0, [{"type": "emboss_text", "text": "Asha", "anchor": "face", "mode": "deboss", "depth_mm": 0.9}], False),
        (2.0, [relief("face", "deboss", 0.8)], True),
        (2.0, [relief("face", "emboss", 1.5)], True),
        (3.0, [relief("face", "deboss", 1.5)], True),
        (2.4, [relief("face", "deboss", 0.6), {"type": "motif", "motif_id": "lotus", "anchor": "face", "mode": "deboss", "depth_mm": 0.8}], False),
    ],
)
def test_a_cut_in_leaves_the_skin_behind_it(thickness, features, ok):
    params = BookmarkPlate.validate({"thickness_mm": thickness})
    if ok:
        BookmarkPlate.validate_content(params, features)
        return
    with pytest.raises(ParamOutOfRange) as exc:
        BookmarkPlate.validate_content(params, features)
    assert "bookmark" in exc.value.message and f"{MIN_SKIN_MM:g} mm" in exc.value.message
    assert exc.value.keys[-1] == "thickness_mm"


def test_karigar_note_says_print_flat_hole_up_and_sand_the_edge():
    note = BookmarkPlate.karigar_note(BookmarkPlate.validate({"end": "pointed", "length_mm": 150}))
    assert note.startswith("A pointed Nishaan bookmark, 150 mm long")
    assert "tassel hole up" in note and "sand the edge lightly" in note
    assert "mesh" not in note.lower() and "stl" not in note.lower()
