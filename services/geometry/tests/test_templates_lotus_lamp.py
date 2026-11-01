"""``lotus_lamp@1`` (Deepak): the LED pocket, cable notch and light hole measured on the built solid, the shell, the
skin rule on both anchors, the taper across the range, hardware and the note through the pipeline."""

from __future__ import annotations

import numpy as np
import pytest
from shapely.geometry import Point

from aakar_geometry.contracts import validate
from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.features.fetch import LocalFileFetcher
from aakar_geometry.pipeline import build_design, is_completed
from aakar_geometry.templates import REGISTRY, register
from aakar_geometry.templates.lotus_lamp import (
    FOOT_H_MM,
    LIGHT_HOLE_D_MM,
    NOTCH_W_MM,
    POCKET_D_MM,
    POCKET_DEPTH_MM,
    SHELL_MM,
    Layout,
    LotusLamp,
)
from aakar_geometry.templates.plates import MIN_SKIN_MM

from carrier_checks import round_holes, section
from conftest import DESIGN_ID, JOB_ID

if (LotusLamp.id, LotusLamp.version) not in REGISTRY:  # the pipeline resolves "lotus_lamp@1" through the registry
    register(LotusLamp)


def _name(anchor: str, text: str, **extra) -> dict:
    return {"type": "emboss_text", "text": text, "anchor": anchor, **extra}


def _buti(anchor: str, motif_id: str, **extra) -> dict:
    return {"type": "motif", "motif_id": motif_id, "anchor": anchor, **extra}


def test_descriptor_hardware_and_the_puck_pocket_size():
    desc = LotusLamp.descriptor()
    assert (desc["id"], desc["family"], desc["environment"]) == ("lotus_lamp", "table_lamp", "teak_table_candlelight")
    assert [a["id"] for a in desc["anchors"]] == ["base_front", "shade_band"]
    assert desc["hardware"] == [{"sku": "led_base_usb", "qty": 1}]
    assert LotusLamp.hardware_for(LotusLamp.validate({"foot_w_mm": 130})) == [{"sku": "led_base_usb", "qty": 1}]
    assert (POCKET_D_MM, POCKET_DEPTH_MM, FOOT_H_MM) == (71.0, 16.0, 24.0)  # a 70 mm puck + 0.8 slide fit + 0.2 hole allowance
    assert FOOT_H_MM - POCKET_DEPTH_MM >= 2.0 * MIN_SKIN_MM  # the roof carries the shade


@pytest.mark.parametrize("foot_w, top_w, height", [(100, 50, 150), (115, 65, 200), (130, 80, 250)])
def test_the_pocket_opens_below_with_a_notch_at_the_back_and_the_light_hole_through_its_roof(foot_w, top_w, height):
    params = LotusLamp.validate({"foot_w_mm": foot_w, "top_w_mm": top_w, "height_mm": height})
    mesh = LotusLamp.build_body(params)
    # mid-pocket: the pocket is the nearest wall to the axis all round, and the notch cuts through the back wall only
    mid = section(mesh, POCKET_DEPTH_MM / 2)
    assert round_holes(mid) == []  # the notch joins the pocket to the outside, so there is no closed ring here
    assert mid.exterior.distance(Point(0.0, 0.0)) == pytest.approx(POCKET_D_MM / 2, abs=2e-3)
    assert not mid.contains(Point(0.0, foot_w / 2 - 1.0))  # the notch
    assert mid.contains(Point(NOTCH_W_MM / 2 + 2.0, foot_w / 2 - 1.0)) and mid.contains(Point(0.0, -(foot_w / 2 - 1.0)))
    # the roof: one round hole for the light, the pocket closed around it
    roof = section(mesh, (POCKET_DEPTH_MM + FOOT_H_MM) / 2)
    holes = round_holes(roof)
    assert len(holes) == 1
    centre, narrowest, widest = holes[0]
    assert abs(centre.x) < 1e-6 and abs(centre.y) < 1e-6
    assert narrowest == pytest.approx(LIGHT_HOLE_D_MM / 2, abs=2e-3) and widest < LIGHT_HOLE_D_MM / 2 + 0.02  # 96-gon vertices
    # up the axis the light meets nothing: pocket, hole, shade, open top
    hits, _, _ = mesh.ray.intersects_location([[0.0, 0.0, -5.0]], [[0.0, 0.0, 1.0]], multiple_hits=True)
    assert len(hits) == 0
    # beside the hole the puck's light meets the roof: POCKET_DEPTH_MM up to FOOT_H_MM
    hits, _, _ = mesh.ray.intersects_location([[30.0, 0.0, -5.0]], [[0.0, 0.0, 1.0]], multiple_hits=True)
    zs = sorted(hits[:, 2])
    assert zs[0] == pytest.approx(POCKET_DEPTH_MM, abs=1e-6) and zs[1] == pytest.approx(FOOT_H_MM, abs=1e-6)
    assert mesh.extents[2] == pytest.approx(height, abs=1e-6)


@pytest.mark.parametrize("foot_w, top_w, height", [(100, 80, 150), (115, 65, 200), (130, 50, 250)])
def test_the_shell_and_the_foot_wall_measure_as_declared(foot_w, top_w, height):
    params = LotusLamp.validate({"foot_w_mm": foot_w, "top_w_mm": top_w, "height_mm": height})
    L = Layout(params)
    mesh = LotusLamp.build_body(params)
    # through the front facet square to it, at the middle of the band: outer skin then inner skin, SHELL_MM apart
    frame = LotusLamp.anchor_frame("shade_band", params)
    assert frame.normal[1] < 0 and frame.normal[2] > 0  # facing the viewer, tilted up with the taper
    start = frame.origin + 5.0 * frame.normal
    hits, _, _ = mesh.ray.intersects_location([start], [-frame.normal], multiple_hits=True)
    d = np.sort(np.linalg.norm(hits - start, axis=1))
    assert d[0] == pytest.approx(5.0, abs=1e-3) and d[1] - d[0] == pytest.approx(SHELL_MM, abs=0.05)
    # through the foot's front wall at pocket height: the flat, then the pocket wall
    hits, _, _ = mesh.ray.intersects_location([[0.0, -100.0, POCKET_DEPTH_MM / 2]], [[0.0, 1.0, 0.0]], multiple_hits=True)
    ys = sorted(hits[:, 1])
    assert ys[0] == pytest.approx(-foot_w / 2, abs=1e-6) and ys[1] - ys[0] == pytest.approx(L.foot_wall_mm, abs=0.03)  # the ray may meet a 96-gon vertex
    assert L.foot_wall_mm >= 14.0  # a 100 mm foot still leaves 14.5 mm in front of the puck
    assert L.taper_deg <= 45.0  # the shade only leans inward, printable standing without supports


def test_the_taper_never_passes_forty_five_degrees():
    # the steepest corner of the range: the widest foot, the narrowest top, the shortest shade
    assert Layout(LotusLamp.validate({"foot_w_mm": 130, "top_w_mm": 50, "height_mm": 150})).taper_deg == pytest.approx(17.6, abs=0.1)
    assert Layout(LotusLamp.validate({"foot_w_mm": 100, "top_w_mm": 80, "height_mm": 250})).taper_deg == pytest.approx(2.5, abs=0.1)


@pytest.mark.parametrize(
    "features, ok, keys",
    [
        ([_name("shade_band", "Deepak", mode="deboss", depth_mm=0.6)], True, None),  # 2.0 − 0.6 = 1.4 mm of shell
        ([_buti("shade_band", "lotus", depth_mm=0.8)], True, None),  # a motif cuts in by default: 1.2 mm left
        ([_buti("shade_band", "lotus", depth_mm=0.9)], False, ["features[0].depth_mm", "top_w_mm"]),
        ([_buti("shade_band", "lotus", mode="emboss", depth_mm=1.0)], True, None),
        ([_name("base_front", "Deepak", mode="deboss", depth_mm=1.5)], True, None),  # 14.5 mm of foot wall
    ],
)
def test_a_cut_in_on_the_shade_leaves_skin_in_the_shell(features, ok, keys):
    params = LotusLamp.validate({"foot_w_mm": 100})
    if ok:
        LotusLamp.validate_content(params, features)
        return
    with pytest.raises(ParamOutOfRange) as exc:
        LotusLamp.validate_content(params, features)
    assert exc.value.keys == keys
    assert "shade" in exc.value.message


def test_the_lamp_reaches_the_completed_payload_with_its_puck_and_note(content_dir, local_storage):
    request = {
        "job_id": JOB_ID,
        "design_id": DESIGN_ID,
        "version_no": 1,
        "outputs": ["stl"],
        "spec": {
            "spec_version": "1.0",
            "family": "table_lamp",
            "template": "lotus_lamp@1",
            "params": {"height_mm": 180, "foot_w_mm": 110, "top_w_mm": 60},
            "features": [_name("base_front", "Deepak", depth_mm=0.8), _buti("shade_band", "lotus", mode="emboss", depth_mm=0.8)],
            "material": "petg_slate",
        },
    }
    payload = build_design(request, storage=local_storage, fetcher=LocalFileFetcher(content_dir))
    assert is_completed(payload), payload
    validate("design.completed", payload)
    assert payload["hardware"] == [{"sku": "led_base_usb", "qty": 1}]
    note = payload["karigar_note"]
    assert "“Deepak”" in note and "slides in from below" in note and "LED only, never a bulb" in note
    assert "mesh" not in note.lower() and "stl" not in note.lower()
