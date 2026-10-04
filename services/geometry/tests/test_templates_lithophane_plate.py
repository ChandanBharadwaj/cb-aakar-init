"""``lithophane_plate@1`` (Roshni): darker is thicker, the photo is required and never raised, white only, the
night light is one print with the puck pocket, the plate alone lies flat."""

from __future__ import annotations

import io

import numpy as np
import pytest

from aakar_geometry.contracts import validate
from aakar_geometry.errors import InvalidSpec, ParamOutOfRange, UnsupportedFeature
from aakar_geometry.features.fetch import LocalFileFetcher
from aakar_geometry.pipeline import build_design, http_status_for, is_completed
from aakar_geometry.templates import LithophanePlate
from aakar_geometry.templates.lithophane_plate import (
    BASE_DEPTH_MM,
    BASE_HEIGHT_MM,
    BORDER_MM,
    CABLE_NOTCH_MM,
    FRAME_PROUD_MM,
    LIGHT_ALLOWANCE_MM,
    MISSING_PHOTO,
    POCKET_DEPTH_MM,
    PUCK_CLEARANCE_MM,
    PUCK_D_MM,
    Layout,
)

from carrier_checks import relief
from conftest import DESIGN_ID, JOB_ID


def _png(arr: np.ndarray) -> bytes:
    from PIL import Image

    buf = io.BytesIO()
    Image.fromarray(arr.astype(np.uint8), "L").save(buf, format="PNG")
    return buf.getvalue()


@pytest.fixture
def photos(content_dir):
    """halves.png: the left half black, the right half white (3:4, like the portrait plate); wide.png: a 2:1 grey
    ramp (letterboxed on the plate); white.png: nearly all white, a small dark square in the middle."""
    halves = np.zeros((400, 300))
    halves[:, 150:] = 255
    (content_dir / "halves.png").write_bytes(_png(halves))
    (content_dir / "wide.png").write_bytes(_png(np.tile(np.linspace(40, 220, 200), (100, 1))))
    white = np.full((300, 400), 250.0)
    white[120:180, 170:230] = 20
    (content_dir / "white.png").write_bytes(_png(white))
    return content_dir


def photo(name: str = "photo.png", **extra) -> dict:
    return relief("plate", "lithophane", name=name) | extra


def top_at(mesh, x: float, y: float) -> float:
    """Height of the plate's front at (x, y) for a plate lying on its back."""
    hits, _, _ = mesh.ray.intersects_location([[x, y, 50.0]], [[0.0, 0.0, -1.0]], multiple_hits=False)
    return float(hits[0][2])


def test_descriptor():
    desc = LithophanePlate.descriptor()
    assert (desc["id"], desc["family"], desc["name"], desc["environment"]) == (
        "lithophane_plate", "lithophane", "Roshni photo night light", "teak_table_candlelight",
    )
    p = desc["params"]
    assert p["size"]["options"] == ["square", "portrait"] and p["size"]["default"] == "portrait"
    assert (p["min_mm"]["min"], p["min_mm"]["max"], p["min_mm"]["default"]) == (0.8, 1.8, 0.8)
    assert (p["max_mm"]["min"], p["max_mm"]["max"], p["max_mm"]["default"]) == (2.0, 3.0, 3.0)
    assert p["stand"]["options"] == ["none", "night_light"] and p["stand"]["default"] == "night_light"
    # the photo is the plate itself (lithophane mode only) and the night light can't be made without it
    assert desc["anchors"] == [
        {
            "id": "plate", "label": "Photo plate", "kind": "surface", "projection": "planar", "size_mm": [95.0, 131.0],
            "bleed_mm": 0.0, "accepts": ["relief_image"], "modes": ["lithophane"], "required": True,
        },
    ]
    assert desc["materials"] == ["basic_white"]  # the family's rule: light must pass through the plate
    assert desc["constraints"]["min_wall_mm"] == 0.8
    assert desc["hardware"] == [{"sku": "led_base_usb", "qty": 1}]
    assert LithophanePlate.hardware_for(LithophanePlate.validate({"stand": "none"})) == []


@pytest.mark.parametrize("lo, hi, ok", [(0.8, 3.0, True), (1.8, 3.0, True), (0.8, 2.0, True), (1.8, 2.9, False), (1.5, 2.0, False)])
def test_the_plate_needs_room_between_its_lightest_and_darkest_parts(lo, hi, ok):
    if ok:
        assert LithophanePlate.validate({"min_mm": lo, "max_mm": hi})["max_mm"] == hi
        return
    with pytest.raises(ParamOutOfRange) as exc:
        LithophanePlate.validate({"min_mm": lo, "max_mm": hi})
    assert exc.value.keys == ["min_mm", "max_mm"] and "at least 1.2 mm between" in exc.value.message


def test_without_the_photo_or_with_a_raised_one_it_is_refused(photos):
    params = LithophanePlate.validate({"stand": "none"})
    with pytest.raises(InvalidSpec) as exc:
        LithophanePlate.build(params)
    assert exc.value.message == MISSING_PHOTO
    for mode in ("emboss", "deboss"):
        with pytest.raises(UnsupportedFeature) as exc:
            LithophanePlate.build(params, [relief("plate", mode)], LocalFileFetcher(photos))
        assert "becomes the glowing plate itself" in exc.value.message and "mesh" not in exc.value.message.lower()


def test_darker_is_thicker_and_invert_swaps_them(photos):
    params = LithophanePlate.validate({"stand": "none"})
    L = Layout(params)
    left, right = (-L.inner_w / 4.0, 0.0), (L.inner_w / 4.0, 0.0)
    mesh = LithophanePlate.build(params, [photo("halves.png", fit="cover")], LocalFileFetcher(photos))
    assert mesh.is_watertight and mesh.body_count == 1
    assert top_at(mesh, *left) == pytest.approx(3.0, abs=1e-6)  # black: the darkest, max_mm
    assert top_at(mesh, *right) == pytest.approx(0.8 + LIGHT_ALLOWANCE_MM, abs=1e-6)  # white: min_mm
    assert top_at(mesh, 0.0, L.H / 2.0 - BORDER_MM / 2.0) == pytest.approx(3.0 + FRAME_PROUD_MM)  # the frame
    swapped = LithophanePlate.build(params, [photo("halves.png", fit="cover", invert=True)], LocalFileFetcher(photos))
    assert top_at(swapped, *left) == pytest.approx(0.8 + LIGHT_ALLOWANCE_MM, abs=1e-6)
    assert top_at(swapped, *right) == pytest.approx(3.0, abs=1e-6)


def test_a_photo_that_does_not_fill_the_plate_is_edged_dark_and_relief_mm_does_not_apply(photos):
    params = LithophanePlate.validate({"stand": "none", "min_mm": 1.0, "max_mm": 2.4})
    L = Layout(params)
    mesh = LithophanePlate.build(params, [photo("wide.png")], LocalFileFetcher(photos))
    # a 2:1 photo in the 95 × 131 mm area: 47.5 mm tall in the middle, dark (max_mm) above and below it
    assert top_at(mesh, 0.0, L.inner_h / 2.0 - 5.0) == pytest.approx(2.4, abs=1e-6)
    assert 1.0 < top_at(mesh, 0.0, 0.0) < 2.4
    deeper = LithophanePlate.build(params, [photo("wide.png", relief_mm=2.5)], LocalFileFetcher(photos))
    assert deeper.volume == pytest.approx(mesh.volume, rel=1e-9)  # min_mm and max_mm set the plate


def test_a_nearly_white_photo_at_the_thinnest_still_passes_the_printability_checks(photos):
    from aakar_inspect import Constraints, inspect_mesh

    params = LithophanePlate.validate({"stand": "none"})
    mesh = LithophanePlate.build(params, [photo("white.png", fit="cover")], LocalFileFetcher(photos))
    report, _ = inspect_mesh(mesh, Constraints(min_wall_mm=LithophanePlate.constraints.min_wall_mm))
    wall = report["checks"]["thinnest_wall"]
    # most of the plate is at the 0.8 mm floor: the check reads it (from just inside the skin) at the limit, not under
    assert report["passed"] is True and wall["status"] == "warn" and wall["value"] == pytest.approx(0.81, abs=0.005)


def test_the_night_light_is_one_print_with_a_pocket_for_the_puck_and_a_cable_notch():
    params = LithophanePlate.validate({})
    L = Layout(params)
    mesh = LithophanePlate.build_body(params)
    assert mesh.body_count == 1  # the plate is fused into its base
    assert mesh.extents == pytest.approx([104 + 12.0, BASE_DEPTH_MM, L.z_seat + 140.0])
    assert mesh.extents[2] <= 150.0  # the lithophane family's envelope

    def floor_at(x: float, y: float) -> float:
        hits, _, _ = mesh.ray.intersects_location([[x, y, 200.0]], [[0.0, 0.0, -1.0]], multiple_hits=False)
        return float(hits[0][2])

    floor = BASE_HEIGHT_MM - POCKET_DEPTH_MM
    r = (PUCK_D_MM + PUCK_CLEARANCE_MM) / 2.0
    assert floor_at(0.0, L.pocket_y) == pytest.approx(floor)
    assert floor_at(r - 0.1, L.pocket_y) == pytest.approx(floor) and floor_at(-(r - 0.1), L.pocket_y) == pytest.approx(floor)
    assert floor_at(r + 0.2, L.pocket_y) == pytest.approx(BASE_HEIGHT_MM)  # 70.6 mm across
    back = BASE_DEPTH_MM / 2.0 - 1.0
    assert floor_at(0.0, back) == pytest.approx(floor)  # the notch runs out through the back wall
    assert floor_at(CABLE_NOTCH_MM / 2.0 + 1.0, back) == pytest.approx(BASE_HEIGHT_MM)
    # the pocket sits behind the plate, clear of it; the plate's photo stands above the base
    assert L.pocket_y - r > L.y_back + 3.0
    assert L.z_seat + BORDER_MM >= BASE_HEIGHT_MM
    frame = LithophanePlate.anchor_frame("plate", params)
    assert np.allclose(frame.normal, [0, -1, 0]) and np.allclose(frame.v, [0, 0, 1])


def test_the_plate_alone_lies_on_its_flat_back():
    params = LithophanePlate.validate({"stand": "none", "size": "square"})
    mesh = LithophanePlate.build_body(params)
    assert mesh.extents == pytest.approx([100.0, 100.0, 3.0 + FRAME_PROUD_MM])
    on_bed = np.isclose(mesh.triangles_center[:, 2], 0.0)
    assert mesh.area_faces[on_bed].sum() == pytest.approx(100.0 * 100.0)  # the whole back rests on the bed
    frame = LithophanePlate.anchor_frame("plate", params)
    assert np.allclose(frame.normal, [0, 0, 1]) and frame.origin[2] == pytest.approx(3.0)


def _request(params: dict, features: list, material: str = "basic_white") -> dict:
    return {
        "job_id": JOB_ID,
        "design_id": DESIGN_ID,
        "version_no": 1,
        "outputs": ["stl"],
        "spec": {"spec_version": "1.0", "family": "lithophane", "template": "lithophane_plate@1", "params": params, "features": features, "material": material},
    }


@pytest.mark.parametrize(
    "material, features, code, needle",
    [
        ("indigo_matte", [photo()], "invalid_spec", "Roshni photo night light comes in Basic White only"),
        ("gold_leaf", [photo()], "invalid_spec", "Unknown material gold_leaf"),
        ("basic_white", [], "invalid_spec", MISSING_PHOTO),
        ("basic_white", [relief("plate")], "unsupported_feature", "can't be raised or cut"),
        ("basic_white", [relief("plate", "lithophane"), relief("plate", "lithophane")], "invalid_spec", "Only one photo"),
    ],
)
def test_the_pipeline_refuses_other_finishes_and_a_missing_or_raised_photo(content_dir, local_storage, material, features, code, needle):
    payload = build_design(_request({"stand": "none"}, features, material), storage=local_storage, fetcher=LocalFileFetcher(content_dir))
    assert not is_completed(payload) and payload["code"] == code, payload
    validate("design.failed", payload)
    assert needle in payload["message"] and http_status_for(payload) == 422


def test_the_pipeline_completes_the_plate_alone_without_the_light(content_dir, local_storage):
    payload = build_design(_request({"stand": "none", "size": "square"}, [photo()]), storage=local_storage, fetcher=LocalFileFetcher(content_dir))
    assert is_completed(payload), payload
    assert payload["hardware"] == []
    assert payload["printability"]["geometry"]["bounds_mm"] == pytest.approx([100, 100, 4], abs=1e-3)
    assert payload["spec"]["features"][0]["mode"] == "lithophane"


def test_karigar_note_asks_for_white_fine_layers_and_full_infill():
    for stand in ("night_light", "none"):
        note = LithophanePlate.karigar_note(LithophanePlate.validate({"stand": stand}))
        assert "Print it in white at 0.1–0.12 mm layers with 100% infill" in note
        assert note.startswith("A 104 × 140 mm portrait Roshni lithophane plate, 0.8 mm where the photo is lightest to 3 mm")
    night = LithophanePlate.karigar_note(LithophanePlate.validate({}))
    assert "one print" in night and "70 mm USB LED puck" in night and "cable" in night
