"""``pendant_disc@1`` (Jhumka): the bail hole and its rim measured on the built solid, the longest side, the room
below the bail, the skin rule, no hardware."""

from __future__ import annotations

import numpy as np
import pytest

from aakar_geometry.contracts import validate
from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.features.fetch import LocalFileFetcher
from aakar_geometry.pipeline import build_design, is_completed
from aakar_geometry.templates.pendant_disc import BAIL_HOLE_MM, BAIL_RIM_MM, PendantDisc
from aakar_geometry.templates.plates import HOLE_ALLOWANCE_MM, MIN_PRINTABLE_MM, MIN_SKIN_MM

from carrier_checks import relief, round_holes, section
from conftest import DESIGN_ID, JOB_ID

HOLE_CUT_MM = BAIL_HOLE_MM + HOLE_ALLOWANCE_MM


def name(text: str = "Asha", **extra) -> dict:
    return {"type": "emboss_text", "text": text, "anchor": "face", **extra}


def test_descriptor_params_anchor_and_no_hardware():
    desc = PendantDisc.descriptor()
    assert (desc["id"], desc["family"], desc["environment"]) == ("pendant_disc", "pendant", "studio")
    p = desc["params"]
    assert p["shape"]["options"] == ["disc", "teardrop", "heart"] and p["shape"]["default"] == "disc"
    assert (p["size_mm"]["min"], p["size_mm"]["max"], p["size_mm"]["default"]) == (20, 40, 30.0)
    assert (p["thickness_mm"]["min"], p["thickness_mm"]["max"], p["thickness_mm"]["default"]) == (2.5, 3.5, 3.0)
    assert [a["id"] for a in desc["anchors"]] == ["face"] and min(desc["anchors"][0]["size_mm"]) >= MIN_PRINTABLE_MM
    assert desc["hardware"] == [] and PendantDisc.hardware_for(PendantDisc.validate({})) == []
    assert desc["name"] == "Jhumka pendant plate" and "earring" not in desc["description"]
    assert (BAIL_HOLE_MM, BAIL_RIM_MM) == (2.6, 1.6)  # a 1 mm jump-ring wire, and a loop wall a chain can pull on


@pytest.mark.parametrize("shape, size", [("disc", 20), ("disc", 40), ("teardrop", 20), ("teardrop", 40), ("heart", 24), ("heart", 40)])
@pytest.mark.parametrize("thickness", [2.5, 3.5])
def test_the_bail_hole_is_cut_with_its_allowance_and_keeps_its_rim(shape, size, thickness):
    params = PendantDisc.validate({"shape": shape, "size_mm": size, "thickness_mm": thickness})
    mesh = PendantDisc.build_body(params)
    assert float(max(mesh.extents)) == pytest.approx(size, abs=1e-3)  # longest side, bail included
    assert float(mesh.extents[2]) == pytest.approx(thickness, abs=1e-6)
    area = section(mesh, thickness / 2)
    holes = round_holes(area)
    assert len(holes) == 1  # the bail hole goes right through, nothing else does
    centre, narrowest, widest = holes[0]
    assert narrowest == pytest.approx(HOLE_CUT_MM / 2, abs=2e-3) and widest < HOLE_CUT_MM / 2 + 0.01
    assert abs(centre.x) < 1e-6 and centre.y > 0  # on the axis, at the top
    assert area.exterior.distance(centre) - widest >= BAIL_RIM_MM - 0.01  # solid plastic all round the hole
    frame = PendantDisc.anchor_frame("face", params)
    assert centre.y - widest > frame.origin[1] + frame.size_mm[1] / 2  # the hole is above the face, not in it
    assert frame.origin[2] == pytest.approx(thickness) and np.allclose(frame.normal, [0, 0, 1])


def test_the_face_has_room_at_the_smallest_size_and_a_small_heart_is_refused():
    for shape in ("disc", "teardrop"):
        frame = PendantDisc.anchor_frame("face", PendantDisc.validate({"shape": shape, "size_mm": 20}))
        assert min(frame.size_mm) >= MIN_PRINTABLE_MM, (shape, frame.size_mm)
    with pytest.raises(ParamOutOfRange) as exc:
        PendantDisc.validate({"shape": "heart", "size_mm": 20})
    assert exc.value.keys == ["size_mm", "shape"] and "make it bigger" in exc.value.message
    # a heart is scaled by its width, so a 20 mm one is 18.6 mm tall and its face is under 8 mm high even with no
    # keep-out round the loop at all: it is refused below 24 mm (23 mm would need the loop's clearance gone)
    for size in (21, 22, 23):
        with pytest.raises(ParamOutOfRange):
            PendantDisc.validate({"shape": "heart", "size_mm": size})
    assert min(PendantDisc.anchor_frame("face", PendantDisc.validate({"shape": "heart", "size_mm": 24})).size_mm) >= MIN_PRINTABLE_MM


@pytest.mark.parametrize(
    "thickness, features, ok",
    [
        (2.5, [relief("face", "deboss", 1.3)], True),  # 2.5 − 1.3 = 1.2 mm of skin
        (2.5, [relief("face", "deboss", 1.4)], False),
        (2.5, [name(mode="deboss", depth_mm=0.8), {"type": "motif", "motif_id": "lotus", "anchor": "face", "depth_mm": 0.6}], True),
        (3.5, [relief("face", "deboss", 2.4)], False),
        (3.5, [relief("face", "emboss", 1.2)], True),
    ],
)
def test_cut_in_content_leaves_skin_behind_the_face(thickness, features, ok):
    params = PendantDisc.validate({"thickness_mm": thickness})
    if ok:
        PendantDisc.validate_content(params, features)
        return
    with pytest.raises(ParamOutOfRange) as exc:
        PendantDisc.validate_content(params, features)
    assert exc.value.keys == ["features[0].relief_mm", "thickness_mm"]
    assert exc.value.detail["min_skin_mm"] == MIN_SKIN_MM and "pendant" in exc.value.message


def test_a_teardrop_with_a_name_reaches_the_completed_payload(content_dir, local_storage):
    request = {
        "job_id": JOB_ID,
        "design_id": DESIGN_ID,
        "version_no": 1,
        "outputs": ["stl"],
        "spec": {
            "spec_version": "1.0",
            "family": "pendant",
            "template": "pendant_disc@1",
            "params": {"shape": "teardrop", "size_mm": 36, "thickness_mm": 2.5},
            "features": [name("Asha", depth_mm=0.8)],
            "material": "basic_white",
        },
    }
    payload = build_design(request, storage=local_storage, fetcher=LocalFileFetcher(content_dir))
    assert is_completed(payload), payload
    validate("design.completed", payload)
    assert payload["hardware"] == []
    note = payload["karigar_note"]
    assert "teardrop" in note and "jump ring" in note and "“Asha”" in note
    assert "36 mm on its longest side" in note and "lowest edge" not in note
    assert "mesh" not in note.lower() and "stl" not in note.lower()
