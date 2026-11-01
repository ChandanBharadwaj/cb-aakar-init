"""``pin_badge@1`` (Billa): the pin-pad pocket (placed where the outline has most room) measured on the built solid, the skin rule, the small-badge refusals, the note."""

from __future__ import annotations

import numpy as np
import pytest

from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.templates.pin_badge import (
    ANCHOR_MARGIN_MM,
    POCKET_D_MM,
    POCKET_DEPTH_MM,
    POCKET_WALL_MM,
    Layout,
    PinBadge,
    smallest_size,
)
from aakar_geometry.templates.plates import MIN_SKIN_MM

from carrier_checks import relief, round_holes, section


def test_descriptor_and_hardware():
    desc = PinBadge.descriptor()
    assert (desc["id"], desc["family"], desc["environment"]) == ("pin_badge", "pin", "studio")
    p = desc["params"]
    assert p["shape"]["options"] == ["disc", "shield", "star", "heart"]
    assert (p["size_mm"]["min"], p["size_mm"]["max"], p["size_mm"]["default"]) == (20, 40, 30.0)
    assert (p["thickness_mm"]["min"], p["thickness_mm"]["max"], p["thickness_mm"]["default"]) == (3.0, 4.0, 3.0)
    assert [a["id"] for a in desc["anchors"]] == ["face"]
    assert desc["hardware"] == [{"sku": "tie_tack_pin", "qty": 1}]
    for shape in ("disc", "shield", "star", "heart"):
        assert PinBadge.hardware_for(PinBadge.validate({"shape": shape, "size_mm": 40})) == [{"sku": "tie_tack_pin", "qty": 1}]
    assert (POCKET_D_MM, POCKET_DEPTH_MM) == (8.4, 0.8)  # an 8 mm pin pad + 0.2 mm all round


@pytest.mark.parametrize("shape", ["disc", "shield", "star", "heart"])
@pytest.mark.parametrize("size, thickness", [(30, 3.0), (40, 4.0)])
def test_the_pocket_opens_on_the_back_where_the_outline_has_most_room_with_the_pad_size_and_skin(shape, size, thickness):
    params = PinBadge.validate({"shape": shape, "size_mm": size, "thickness_mm": thickness})
    px, py = Layout(params).pocket_xy
    mesh = PinBadge.build_body(params)
    assert float(max(mesh.extents)) == pytest.approx(size, abs=1e-3)  # longest side is the size
    pockets = round_holes(section(mesh, POCKET_DEPTH_MM / 2))
    assert len(pockets) == 1
    centre, narrowest, widest = pockets[0]
    assert narrowest == pytest.approx(POCKET_D_MM / 2, abs=2e-3) and widest < POCKET_D_MM / 2 + 0.01
    assert abs(centre.x - px) < 1e-6 and abs(centre.y - py) < 1e-6 and px == 0.0  # on the mirror axis
    if shape == "disc":
        assert py == 0.0
    elif shape in ("heart", "star"):
        assert py < -0.5  # below the bounding box's centre: away from the heart's notch, between the star's points
    assert round_holes(section(mesh, POCKET_DEPTH_MM + 0.1)) == []  # closed above the pocket floor
    # the pocket keeps POCKET_WALL_MM of plastic to the outline all round
    face = section(mesh, thickness - 0.1)
    assert face.buffer(-POCKET_WALL_MM + 1e-6).contains(pockets[0][0].buffer(widest))
    # from below: open up to the pocket floor; from above: the face, then the floor, then the bed
    below, _, _ = mesh.ray.intersects_location([[px, py, -5.0]], [[0, 0, 1.0]], multiple_hits=False)
    assert below[0][2] == pytest.approx(POCKET_DEPTH_MM, abs=1e-6)
    above, _, _ = mesh.ray.intersects_location([[px, py, 20.0]], [[0, 0, -1.0]], multiple_hits=True)
    zs = sorted(above[:, 2], reverse=True)
    assert zs[0] == pytest.approx(thickness, abs=1e-6) and zs[1] == pytest.approx(POCKET_DEPTH_MM, abs=1e-6)
    assert zs[0] - zs[1] >= MIN_SKIN_MM


@pytest.mark.parametrize(
    "thickness, features, ok",
    [
        (3.0, [relief("face", "deboss", 1.0)], True),  # 3 − 0.8 − 1.0 = 1.2 mm over the pad
        (3.0, [relief("face", "deboss", 1.1)], False),
        (4.0, [relief("face", "deboss", 1.2)], True),
        (3.0, [relief("face", "emboss", 1.2)], True),  # raised content never counts
    ],
)
def test_a_cut_in_picture_leaves_plastic_over_the_pin_pad(thickness, features, ok):
    params = PinBadge.validate({"thickness_mm": thickness})
    if ok:
        PinBadge.validate_content(params, features)
        return
    with pytest.raises(ParamOutOfRange) as exc:
        PinBadge.validate_content(params, features)
    assert exc.value.keys == ["features[0].relief_mm", "thickness_mm"]
    assert "the pin pad" in exc.value.message


def test_every_shape_but_the_smallest_hearts_takes_the_pocket_and_a_picture():
    # with the pocket where the outline has most room, a disc, shield or star works from 20 mm and a heart from 24 mm
    for shape in ("disc", "shield", "star"):
        assert smallest_size(shape, 3.0) == 20.0
        PinBadge.validate({"shape": shape, "size_mm": 20})
    assert smallest_size("heart", 3.0) == 24.0
    for size in (20, 22, 23):
        with pytest.raises(ParamOutOfRange) as exc:
            PinBadge.validate({"shape": "heart", "size_mm": size})
        assert exc.value.keys == ["size_mm", "shape"] and "make it at least 24 mm" in exc.value.message
    for size in range(24, 41):  # the whole rest of the slider
        PinBadge.validate({"shape": "heart", "size_mm": size})
    for shape in ("star", "heart"):
        smallest = smallest_size(shape, 3.0)
        mesh = PinBadge.build_body(PinBadge.validate({"shape": shape, "size_mm": smallest}))
        pockets = round_holes(section(mesh, POCKET_DEPTH_MM / 2))
        assert len(pockets) == 1
        face = section(mesh, 3.0 - 0.1)
        assert face.buffer(-POCKET_WALL_MM + 1e-6).contains(pockets[0][0].buffer(pockets[0][2]))  # still 2 mm of wall
        params = PinBadge.validate({"shape": shape, "size_mm": smallest})
        frame = PinBadge.anchor_frame("face", params)
        assert min(frame.size_mm) >= 8.0
        # the face rectangle sits inside the outline by the margin
        face = section(PinBadge.build_body(params), params["thickness_mm"] - 0.1)
        x0, y0 = frame.origin[0] - frame.size_mm[0] / 2, frame.origin[1] - frame.size_mm[1] / 2
        from shapely.geometry import box

        assert face.buffer(-ANCHOR_MARGIN_MM + 0.05).contains(box(x0, y0, x0 + frame.size_mm[0], y0 + frame.size_mm[1]))


def test_the_face_sits_over_the_pocket_and_the_note_tells_the_printer_about_the_pin():
    params = PinBadge.validate({})
    frame = PinBadge.anchor_frame("face", params)
    assert np.allclose(frame.origin[:2], [0.0, 0.0], atol=1e-6) and frame.origin[2] == pytest.approx(3.0)
    assert np.allclose(frame.normal, [0, 0, 1])
    note = PinBadge.karigar_note(params)
    assert "glue" in note.lower() and "into the back pocket" in note and "flush" not in note
    assert "clutch" in note.lower() and "loose" in note
    assert "mesh" not in note.lower() and "stl" not in note.lower()
    assert "“Ravi”" in PinBadge.karigar_note_for(params, [{"type": "emboss_text", "text": "Ravi", "anchor": "face", "depth_mm": 0.8}])
