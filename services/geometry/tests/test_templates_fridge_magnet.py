"""``fridge_magnet@1`` (Chumbak): magnet pockets measured on the built solid, the skin rule, hardware per build."""

from __future__ import annotations

import numpy as np
import pytest

from aakar_geometry.contracts import validate
from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.features.fetch import LocalFileFetcher
from aakar_geometry.pipeline import build_design, is_completed
from aakar_geometry.templates import FridgeMagnet
from aakar_geometry.templates.fridge_magnet import POCKET_D_MM, POCKET_DEPTH_MM
from aakar_geometry.templates.plates import MIN_SKIN_MM

from carrier_checks import relief, round_holes, section
from conftest import DESIGN_ID, JOB_ID


def test_descriptor_and_hardware_per_build():
    desc = FridgeMagnet.descriptor()
    assert (desc["id"], desc["family"], desc["environment"]) == ("fridge_magnet", "fridge_magnet", "kitchen_marble")
    p = desc["params"]
    assert p["shape"]["options"] == ["rect", "rounded", "circle"]
    assert (p["size_mm"]["min"], p["size_mm"]["max"], p["size_mm"]["default"]) == (40, 70, 55.0)
    assert (p["thickness_mm"]["min"], p["thickness_mm"]["max"], p["thickness_mm"]["default"]) == (4.5, 6.0, 5.0)
    assert (p["magnet_count"]["type"], p["magnet_count"]["min"], p["magnet_count"]["max"], p["magnet_count"]["default"]) == ("integer", 1, 2, 1)
    assert [a["id"] for a in desc["anchors"]] == ["face"]
    assert desc["hardware"] == [{"sku": "magnet_d10x3", "qty": 1}]
    assert FridgeMagnet.hardware_for(FridgeMagnet.validate({"magnet_count": 2})) == [{"sku": "magnet_d10x3", "qty": 2}]
    assert (POCKET_D_MM, POCKET_DEPTH_MM) == (10.2, 3.2)  # a 10 × 3 mm magnet + 0.2 mm


@pytest.mark.parametrize("shape", ["rect", "rounded", "circle"])
@pytest.mark.parametrize("size, thickness, count", [(40, 4.5, 2), (55, 5.0, 1), (70, 6.0, 2)])
def test_pockets_open_on_the_back_with_the_right_size_and_skin(shape, size, thickness, count):
    params = FridgeMagnet.validate({"shape": shape, "size_mm": size, "thickness_mm": thickness, "magnet_count": count})
    mesh = FridgeMagnet.build_body(params)
    pockets = round_holes(section(mesh, POCKET_DEPTH_MM / 2))
    assert len(pockets) == count
    for centre, narrowest, widest in pockets:
        assert narrowest == pytest.approx(POCKET_D_MM / 2, abs=2e-3) and widest < POCKET_D_MM / 2 + 0.01
    xs = sorted(round(c.x, 6) for c, _, _ in pockets)
    assert all(abs(c.y) < 1e-6 for c, _, _ in pockets)
    assert xs == ([0.0] if count == 1 else [pytest.approx(-size / 4, abs=1e-6), pytest.approx(size / 4, abs=1e-6)])
    assert round_holes(section(mesh, POCKET_DEPTH_MM + 0.1)) == []  # closed above the pocket floor
    for centre, _, _ in pockets:
        # from below: open up to the pocket floor; from above: the face, then the floor, then the bed
        below, _, _ = mesh.ray.intersects_location([[centre.x, centre.y, -5.0]], [[0, 0, 1.0]], multiple_hits=False)
        assert below[0][2] == pytest.approx(POCKET_DEPTH_MM, abs=1e-6)
        above, _, _ = mesh.ray.intersects_location([[centre.x, centre.y, 20.0]], [[0, 0, -1.0]], multiple_hits=True)
        zs = sorted(above[:, 2], reverse=True)
        assert zs[0] == pytest.approx(thickness, abs=1e-6) and zs[1] == pytest.approx(POCKET_DEPTH_MM, abs=1e-6)
        assert zs[0] - zs[1] >= MIN_SKIN_MM


@pytest.mark.parametrize(
    "thickness, features, ok",
    [
        (5.0, [relief("face", "deboss", 0.6)], True),  # 5 − 3.2 − 0.6 = 1.2 mm over the magnet
        (5.0, [relief("face", "deboss", 0.7)], False),
        (4.5, [relief("face", "deboss", 0.2)], False),  # only 0.1 mm to spare: raise it instead
        (4.5, [relief("face", "emboss", 1.5)], True),
        (6.0, [relief("face", "deboss", 1.5)], True),
    ],
)
def test_a_cut_in_photo_leaves_plastic_over_the_magnet(thickness, features, ok):
    params = FridgeMagnet.validate({"thickness_mm": thickness})
    if ok:
        FridgeMagnet.validate_content(params, features)
        return
    with pytest.raises(ParamOutOfRange) as exc:
        FridgeMagnet.validate_content(params, features)
    assert exc.value.keys == ["features[0].relief_mm", "thickness_mm"]
    assert "the magnet" in exc.value.message
    if thickness == 4.5:
        assert "raise the picture" in exc.value.message


def test_two_magnets_reach_the_completed_payload(content_dir, local_storage):
    request = {
        "job_id": JOB_ID,
        "design_id": DESIGN_ID,
        "version_no": 1,
        "outputs": ["stl"],
        "spec": {
            "spec_version": "1.0",
            "family": "fridge_magnet",
            "template": "fridge_magnet@1",
            "params": {"shape": "circle", "size_mm": 60, "magnet_count": 2},
            "features": [relief("face", "deboss", 0.5)],
            "material": "indigo_matte",
        },
    }
    payload = build_design(request, storage=local_storage, fetcher=LocalFileFetcher(content_dir))
    assert is_completed(payload), payload
    validate("design.completed", payload)
    assert payload["hardware"] == [{"sku": "magnet_d10x3", "qty": 2}]
    assert "two pockets" in payload["karigar_note"] and "small children" in payload["karigar_note"]


def test_the_pocket_is_centred_under_the_photo_area():
    params = FridgeMagnet.validate({})
    frame = FridgeMagnet.anchor_frame("face", params)
    assert np.allclose(frame.origin[:2], [0.0, 0.0], atol=1e-6) and frame.origin[2] == pytest.approx(5.0)
