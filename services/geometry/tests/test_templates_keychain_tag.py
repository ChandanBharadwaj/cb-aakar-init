"""``keychain_tag@1`` (Saathi): the ring hole and its rim, the longest side, the skin rule for cut-in photos."""

from __future__ import annotations

import numpy as np
import pytest

from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.features.fetch import LocalFileFetcher
from aakar_geometry.templates import KeychainTag
from aakar_geometry.templates.keychain_tag import Layout
from aakar_geometry.templates.plates import HOLE_ALLOWANCE_MM, MIN_SKIN_MM, RIM_MM

from carrier_checks import relief, round_holes, section

SHAPES = ("rect", "rounded", "circle", "heart")


def test_descriptor():
    desc = KeychainTag.descriptor()
    assert (desc["id"], desc["family"], desc["name"], desc["environment"]) == ("keychain_tag", "keychain", "Saathi keychain tag", "studio")
    p = desc["params"]
    assert p["shape"]["options"] == list(SHAPES) and p["shape"]["default"] == "rounded"
    assert (p["width_mm"]["min"], p["width_mm"]["max"], p["width_mm"]["default"], p["width_mm"]["handle"]) == (30, 60, 45.0, True)
    assert (p["thickness_mm"]["min"], p["thickness_mm"]["max"], p["thickness_mm"]["default"]) == (2.4, 4.0, 3.0)
    assert (p["hole_d_mm"]["min"], p["hole_d_mm"]["max"], p["hole_d_mm"]["default"]) == (4.0, 6.0, 4.2)
    assert [a["id"] for a in desc["anchors"]] == ["face", "back"]
    assert desc["hardware"] == [{"sku": "split_ring_25", "qty": 1}]
    # the committed contract example's parameters are valid for this template
    assert KeychainTag.validate({"shape": "rounded", "width_mm": 45, "thickness_mm": 3, "hole_d_mm": 4.2})["hole_d_mm"] == 4.2


@pytest.mark.parametrize("shape", SHAPES)
@pytest.mark.parametrize("width, hole_d", [(30, 6.0), (45, 4.2), (60, 4.0)])
def test_ring_hole_is_cut_oversize_with_a_two_mm_rim_and_goes_right_through(shape, width, hole_d):
    params = KeychainTag.validate({"shape": shape, "width_mm": width, "hole_d_mm": hole_d, "thickness_mm": 2.4})
    mesh = KeychainTag.build_body(params)
    area = section(mesh, params["thickness_mm"] / 2)
    holes = round_holes(area)
    assert len(holes) == 1
    centre, narrowest, widest = holes[0]
    cut = hole_d + HOLE_ALLOWANCE_MM
    assert narrowest == pytest.approx(cut / 2, abs=2e-3) and widest < cut / 2 + 0.01  # never smaller than the cut
    assert area.exterior.distance(centre) - widest >= RIM_MM - 1e-6  # 2 mm of plastic all round
    assert centre.x == pytest.approx(0.0, abs=1e-6) and centre.y > 0  # on the centre line, at the top
    rect = Layout(params).printable_rect()
    assert centre.y - widest - RIM_MM > rect[3]  # the photo area stays below the loop and its rim
    # a ring through the hole: rays straight down its middle meet no plastic, a ray beside it does
    origins = np.array([[centre.x, centre.y, 10.0], [centre.x + cut / 2 + 1.0, centre.y, 10.0]])
    hits = mesh.ray.intersects_any(origins, np.array([[0, 0, -1.0], [0, 0, -1.0]]))
    assert list(hits) == [False, True]


@pytest.mark.parametrize("shape", SHAPES)
@pytest.mark.parametrize("width", [30, 45, 60])
def test_the_longest_side_including_the_loop_is_the_width(shape, width):
    extents = KeychainTag.build_body(KeychainTag.validate({"shape": shape, "width_mm": width, "hole_d_mm": 6.0})).extents
    assert max(extents[:2]) == pytest.approx(width, abs=1e-6)


@pytest.mark.parametrize(
    "thickness, features, ok",
    [
        (2.4, [relief("face", "deboss", 1.2)], True),  # leaves exactly 1.2 mm
        (2.4, [relief("face", "deboss", 1.3)], False),
        (2.4, [relief("face", "deboss", 0.6), relief("back", "deboss", 0.6)], True),
        (2.4, [relief("face", "deboss", 0.8), relief("back", "deboss", 0.6)], False),  # both faces add up
        (2.4, [relief("face", "emboss", 1.5), relief("back", "emboss", 1.5)], True),  # raised photos add plastic
        (4.0, [relief("face", "deboss", 1.5), relief("back", "deboss", 1.2)], True),
        (3.0, [], True),
    ],
)
def test_a_cut_in_photo_keeps_the_minimum_skin(thickness, features, ok):
    params = KeychainTag.validate({"thickness_mm": thickness})
    if ok:
        KeychainTag.validate_content(params, features)
        return
    with pytest.raises(ParamOutOfRange) as exc:
        KeychainTag.validate_content(params, features)
    assert exc.value.keys[-1] == "thickness_mm" and all(k.startswith("features[") for k in exc.value.keys[:-1])
    assert exc.value.detail["min_skin_mm"] == MIN_SKIN_MM
    assert "mesh" not in exc.value.message.lower()


def test_deepest_cut_in_at_the_thinnest_tag_builds_and_keeps_its_skin(content_dir):
    params = KeychainTag.validate({"thickness_mm": 2.4, "shape": "circle"})
    mesh = KeychainTag.build(params, [relief("face", "deboss", 1.2)], LocalFileFetcher(content_dir))
    assert mesh.is_watertight and mesh.extents[2] == pytest.approx(2.4, abs=1e-6)
    frame = KeychainTag.anchor_frame("face", params)
    # straight down through the photo's bright middle (cut deepest): 1.2 mm of plastic is left above the bed
    locations, _, _ = mesh.ray.intersects_location([frame.origin + [0, 0, 5.0]], [[0, 0, -1.0]], multiple_hits=True)
    floor = float(locations[:, 2].max())
    assert floor >= MIN_SKIN_MM - 1e-6 and floor == pytest.approx(2.4 - 1.2, abs=0.05)
    with pytest.raises(ParamOutOfRange):
        KeychainTag.build(params, [relief("face", "deboss", 1.5)], LocalFileFetcher(content_dir))


def test_a_raised_photo_on_the_back_is_lifted_back_onto_the_bed(content_dir):
    params = KeychainTag.validate({})
    mesh = KeychainTag.build(params, [relief("back", "emboss", 0.8)], LocalFileFetcher(content_dir))
    assert mesh.bounds[0][2] == pytest.approx(0.0, abs=1e-9)
    assert mesh.bounds[1][2] == pytest.approx(3.0 + 0.8, abs=1e-3)


def test_karigar_note_speaks_of_the_ring():
    note = KeychainTag.karigar_note(KeychainTag.validate({"shape": "heart", "width_mm": 50, "hole_d_mm": 5}))
    assert note.startswith("A heart-shaped Saathi tag, 50 mm on its longest side")
    assert "5 mm ring hole (cut 5.2 mm" in note and "split ring" in note
