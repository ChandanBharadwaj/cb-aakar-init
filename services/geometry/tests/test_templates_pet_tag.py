"""``pet_tag@1`` (Saathi pet tag): the 4 mm ring hole and its 2 mm rim, the longest side, room for a name on both
faces, the skin rule, PETG in the karigar's note."""

from __future__ import annotations

import numpy as np
import pytest

from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.features import check_features, prepare_marks
from aakar_geometry.templates import PetTag
from aakar_geometry.templates.pet_tag import HOLE_D_MM, Layout
from aakar_geometry.templates.plates import HOLE_ALLOWANCE_MM, MIN_PRINTABLE_MM, MIN_SKIN_MM, RIM_MM

from carrier_checks import round_holes, section

SHAPES = ("bone", "disc")


def name(anchor: str, text: str, **extra) -> dict:
    return {"type": "emboss_text", "text": text, "anchor": anchor, **extra}


def test_descriptor():
    desc = PetTag.descriptor()
    assert (desc["id"], desc["family"], desc["name"], desc["environment"]) == ("pet_tag", "keychain", "Saathi pet tag", "studio")
    p = desc["params"]
    assert p["shape"]["options"] == list(SHAPES) and p["shape"]["default"] == "bone"
    assert (p["size_mm"]["min"], p["size_mm"]["max"], p["size_mm"]["default"], p["size_mm"]["handle"]) == (25, 35, 30.0, True)
    assert (p["thickness_mm"]["min"], p["thickness_mm"]["max"], p["thickness_mm"]["default"]) == (3.0, 4.0, 3.5)
    assert [a["id"] for a in desc["anchors"]] == ["face", "back"]
    assert all(a["accepts"][0] == "emboss_text" and a["max_relief_mm"] == 1.2 for a in desc["anchors"])  # names first
    assert desc["hardware"] == [{"sku": "split_ring_25", "qty": 1}]


@pytest.mark.parametrize("shape", SHAPES)
@pytest.mark.parametrize("size", [25, 30, 35])
def test_the_ring_hole_is_4_mm_cut_oversize_with_a_2_mm_rim_and_goes_right_through(shape, size):
    params = PetTag.validate({"shape": shape, "size_mm": size, "thickness_mm": 3.0})
    mesh = PetTag.build_body(params)
    area = section(mesh, 1.5)
    holes = round_holes(area)
    assert len(holes) == 1
    centre, narrowest, widest = holes[0]
    cut = HOLE_D_MM + HOLE_ALLOWANCE_MM
    assert narrowest == pytest.approx(cut / 2, abs=2e-3) and widest < cut / 2 + 0.01
    assert area.exterior.distance(centre) - widest >= RIM_MM - 1e-6
    assert centre.x == pytest.approx(0.0, abs=1e-6) and centre.y > 0  # on the centre line, at the top
    rect = Layout(params).printable_rect()
    assert centre.y - widest - RIM_MM > rect[3]  # the name stays below the hole and its rim
    hits = mesh.ray.intersects_any(np.array([[centre.x, centre.y, 10.0], [centre.x + cut / 2 + 1.0, centre.y, 10.0]]), np.array([[0, 0, -1.0]] * 2))
    assert list(hits) == [False, True]


@pytest.mark.parametrize("shape", SHAPES)
@pytest.mark.parametrize("size", [25, 35])
def test_the_longest_side_is_the_size_and_a_name_fits_on_both_faces(shape, size):
    params = PetTag.validate({"shape": shape, "size_mm": size})
    extents = PetTag.build_body(params).extents
    assert max(extents[:2]) == pytest.approx(size, abs=1e-6)
    if shape == "bone":
        assert extents[0] == pytest.approx(size, abs=1e-6)  # end to end; the hole's tab only adds height
    for anchor in ("face", "back"):
        w, h = PetTag.anchor_frame(anchor, params).size_mm
        assert min(w, h) >= MIN_PRINTABLE_MM and w >= 1.8 * h  # a name is wider than tall
    marks = prepare_marks(PetTag, params, check_features(PetTag, [name("face", "Moti"), name("back", "Ravi")]))
    assert all(m.height_mm >= 5.0 and m.stroke.ok for m in marks.values())


def test_the_back_reads_left_to_right_with_the_hole_at_the_top():
    params = PetTag.validate({})
    face, back = PetTag.anchor_frame("face", params), PetTag.anchor_frame("back", params)
    assert np.allclose(face.normal, [0, 0, 1]) and np.allclose(back.normal, [0, 0, -1])
    assert np.allclose(back.u, [-1, 0, 0]) and np.allclose(back.v, [0, 1, 0])


@pytest.mark.parametrize(
    "thickness, features, ok",
    [
        (3.0, [name("face", "Moti", mode="deboss", depth_mm=1.2)], True),  # 1.8 mm left
        (3.0, [name("face", "Moti", mode="deboss", depth_mm=1.0), name("back", "Ravi", mode="deboss", depth_mm=1.0)], False),
        (4.0, [name("face", "Moti", mode="deboss", depth_mm=1.2), name("back", "Ravi", mode="deboss", depth_mm=1.2)], True),
        (3.0, [name("face", "Moti", depth_mm=1.2), name("back", "Ravi", depth_mm=1.2)], True),  # raised names add plastic
    ],
)
def test_cut_in_names_on_both_faces_keep_the_minimum_skin(thickness, features, ok):
    params = PetTag.validate({"thickness_mm": thickness})
    normalised = check_features(PetTag, features)
    if ok:
        PetTag.validate_content(params, normalised)
        return
    with pytest.raises(ParamOutOfRange) as exc:
        PetTag.validate_content(params, normalised)
    assert exc.value.keys[-1] == "thickness_mm" and exc.value.detail["min_skin_mm"] == MIN_SKIN_MM


def test_a_raised_name_on_both_faces_builds_and_rests_on_the_back_letters():
    params = PetTag.validate({})
    mesh = PetTag.build(params, [name("face", "Moti", depth_mm=0.8), name("back", "Ravi", depth_mm=0.6)])
    assert mesh.is_watertight and mesh.body_count == 1
    assert mesh.bounds[0][2] == pytest.approx(0.0, abs=1e-9) and mesh.extents[2] == pytest.approx(3.5 + 0.8 + 0.6, abs=1e-3)


def test_karigar_note_asks_for_petg_and_the_split_ring():
    note = PetTag.karigar_note(PetTag.validate({"shape": "disc", "size_mm": 28}))
    assert note.startswith("A round Saathi pet tag, 28 mm on its longest side and 3.5 mm thick, with a 4 mm ring hole (cut 4.2 mm")
    assert "PETG" in note and "25 mm steel split ring" in note and "mesh" not in note.lower()
