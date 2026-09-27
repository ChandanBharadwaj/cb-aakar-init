"""``plinth_round@1`` (Pratima): the form's room scales with the plinth, the form is seated and fused, the whole
piece stays inside the family envelope, and a piece that would lean off or tip at a nudge is refused."""

from __future__ import annotations

import math

import numpy as np
import pytest
import trimesh

from aakar_geometry.contracts import validate
from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.features import hero_mesh
from aakar_geometry.features.fetch import LocalFileFetcher
from aakar_geometry.pipeline import build_design, is_completed
from aakar_geometry.templates import PlinthRound
from aakar_geometry.templates.plinth_round import CORNER_SHARE, FLAT_SHARE, MIN_TIP_ANGLE_DEG, MIN_TIPPING_MARGIN_MM, TOP_HEIGHT_MM, Layout

from conftest import DESIGN_ID, JOB_ID, content_source


@pytest.fixture
def forms(content_dir):
    """column.stl (6 × 6 × 150), wall.stl (a 40 × 4 × 150 slab at the front of a 40 mm square, a thin rod at its
    back: nearly all its weight at the front) and lollipop.stl (a 40 mm ball on a 3 mm stick, 170 mm tall) beside
    the conftest content."""
    column = trimesh.creation.box(extents=[6, 6, 150])
    heavy = trimesh.creation.box(extents=[40, 4, 150])
    heavy.apply_translation([0, -18, 0])
    rod = trimesh.creation.box(extents=[2, 2, 150])
    rod.apply_translation([0, 19, 0])
    stick = trimesh.creation.cylinder(radius=1.5, height=150)
    ball = trimesh.creation.icosphere(subdivisions=3, radius=20)
    ball.apply_translation([0, 0, 75])
    lollipop = trimesh.boolean.union([stick, ball], engine="manifold")
    for name, mesh in (("column.stl", column), ("wall.stl", trimesh.util.concatenate([heavy, rod])), ("lollipop.stl", lollipop)):
        (content_dir / name).write_bytes(mesh.export(file_type="stl"))
    return content_dir


def hero(model: str = "sphere.stl", **extra) -> dict:
    return {"type": "hero_mesh", "source": content_source(model, "stl"), "anchor": "top", **extra}


def name(text: str = "Bruno", **extra) -> dict:
    return {"type": "emboss_text", "text": text, "anchor": "base_front", **extra}


def test_descriptor():
    desc = PlinthRound.descriptor()
    assert (desc["id"], desc["family"], desc["name"], desc["environment"]) == ("plinth_round", "figurine_base", "Pratima plinth", "studio")
    p = desc["params"]
    assert p["shape"]["options"] == ["round", "rounded_square"] and p["shape"]["default"] == "round"
    assert (p["diameter_mm"]["min"], p["diameter_mm"]["max"], p["diameter_mm"]["default"], p["diameter_mm"]["handle"]) == (40, 120, 70.0, True)
    assert (p["height_mm"]["min"], p["height_mm"]["max"], p["height_mm"]["default"]) == (6, 20, 12.0)
    top, front = desc["anchors"]
    assert top == {"id": "top", "label": "Top of the plinth", "kind": "volume", "projection": "planar", "bounds_mm": [70, 70, 180], "accepts": ["hero_mesh"]}
    assert front["id"] == "base_front" and front["accepts"] == ["emboss_text"] and front["max_relief_mm"] == 1.5
    assert desc["features_supported"] == ["hero_mesh", "emboss_text"] and desc["hardware"] == []


@pytest.mark.parametrize("shape", ["round", "rounded_square"])
@pytest.mark.parametrize("diameter, height", [(40, 6), (70, 12), (120, 20)])
def test_the_forms_room_scales_with_the_plinth_and_the_name_has_a_flat_front(shape, diameter, height):
    params = PlinthRound.validate({"shape": shape, "diameter_mm": diameter, "height_mm": height})
    top = PlinthRound.anchor_frame("top", params)
    assert top.bounds_mm == (diameter, diameter, TOP_HEIGHT_MM)
    assert height + TOP_HEIGHT_MM <= 200.0  # the figurine family's envelope
    assert top.origin[2] == pytest.approx(height) and np.allclose(top.normal, [0, 0, 1])
    mesh = PlinthRound.build_body(params)
    front = PlinthRound.anchor_frame("base_front", params)
    assert np.allclose(front.normal, [0, -1, 0]) and front.origin[1] == pytest.approx(mesh.bounds[0][1])
    flat = FLAT_SHARE * diameter if shape == "round" else diameter * (1.0 - 2.0 * CORNER_SHARE)
    assert front.size_mm[0] == pytest.approx(flat - 3.0, abs=0.05)  # the flat front, 1.5 mm in from each end
    assert front.size_mm[1] == pytest.approx(height - 1.0)
    # the top anchor stands at the footprint's centroid
    c = Layout(params).outline.centroid
    assert top.origin[:2] == pytest.approx([c.x, c.y], abs=1e-9)


def test_a_form_is_seated_on_the_top_and_fused(forms):
    params = PlinthRound.validate({})
    mesh = PlinthRound.build(params, [hero()], LocalFileFetcher(forms))
    assert mesh.is_watertight and mesh.body_count == 1
    # the 20 mm ball fills the 70 mm room (contain) and sinks 0.5 mm into the plinth
    assert mesh.extents[2] == pytest.approx(12.0 + 70.0 - hero_mesh.EMBED_MM, abs=1e-3)
    top = PlinthRound.anchor_frame("top", params)
    ball = mesh.vertices[mesh.vertices[:, 2] > 12.5]
    assert (ball[:, :2].min(axis=0) + ball[:, :2].max(axis=0)) / 2.0 == pytest.approx(top.origin[:2], abs=0.05)


def test_the_tallest_form_on_the_tallest_plinth_stays_inside_the_envelope(forms):
    params = PlinthRound.validate({"height_mm": 20})
    mesh = PlinthRound.build(params, [hero("column.stl")], LocalFileFetcher(forms))
    assert mesh.extents[2] == pytest.approx(20.0 + TOP_HEIGHT_MM - hero_mesh.EMBED_MM, abs=1e-3)
    assert max(mesh.extents) <= 200.0


def test_a_form_too_big_for_its_room_is_refused_never_shrunk(forms):
    params = PlinthRound.validate({"diameter_mm": 50})
    with pytest.raises(ParamOutOfRange) as exc:
        PlinthRound.build(params, [hero(fit="longest", longest_mm=60)], LocalFileFetcher(forms))
    assert exc.value.keys == ["features[0].longest_mm"] and "60 mm is too big for the Top of the plinth; 50 mm is the most that fits" == exc.value.message
    mesh = PlinthRound.build(params, [hero(fit="longest", longest_mm=50)], LocalFileFetcher(forms))
    assert mesh.extents[0] == pytest.approx(50.0, abs=1e-3)


def test_a_form_that_leans_off_the_plinth_is_refused_with_a_plinth_that_would_hold_it(forms):
    params = PlinthRound.validate({"diameter_mm": 40, "height_mm": 6})
    with pytest.raises(ParamOutOfRange) as exc:
        PlinthRound.build(params, [hero("wall.stl")], LocalFileFetcher(forms))
    detail = exc.value.detail
    assert exc.value.keys == ["diameter_mm"]
    wider = detail["suggested_diameter_mm"]
    assert exc.value.message == f"Your form would lean off a 40 mm plinth; choose a plinth at least {wider:g} mm across, or a smaller size for your form."
    assert detail["tipping_margin_mm"] < MIN_TIPPING_MARGIN_MM
    # the suggestion is worked out, not guessed: the form grows with a wider plinth's room (contain), and still stands
    assert PlinthRound.build(PlinthRound.validate({"diameter_mm": wider, "height_mm": 6}), [hero("wall.stl")], LocalFileFetcher(forms)).is_watertight
    with pytest.raises(ParamOutOfRange):
        PlinthRound.build(PlinthRound.validate({"diameter_mm": wider - 5, "height_mm": 6}), [hero("wall.stl")], LocalFileFetcher(forms))


def test_a_tall_form_on_a_narrow_plinth_is_refused_and_stands_on_a_wide_one(forms):
    from aakar_inspect import Constraints, inspect_mesh

    narrow = PlinthRound.validate({"diameter_mm": 40, "height_mm": 6})
    with pytest.raises(ParamOutOfRange) as exc:
        PlinthRound.build(narrow, [hero("lollipop.stl", fit="longest", longest_mm=170)], LocalFileFetcher(forms))
    assert exc.value.keys == ["diameter_mm", "features[0].longest_mm"]
    assert "would tip over at a light nudge on a 40 mm plinth" in exc.value.message
    assert exc.value.detail["tip_angle_deg"] < MIN_TIP_ANGLE_DEG
    wide = PlinthRound.validate({"diameter_mm": 120})
    mesh = PlinthRound.build(wide, [hero("lollipop.stl", fit="longest", longest_mm=170)], LocalFileFetcher(forms))
    report, _ = inspect_mesh(mesh, Constraints(min_wall_mm=1.2))
    assert report["checks"]["centre_of_gravity"]["status"] == "pass"
    assert report["checks"]["tipping_margin"]["status"] == "pass"
    cog_height = float(mesh.center_mass[2])
    assert math.degrees(math.atan2(report["checks"]["tipping_margin"]["value"], cog_height)) >= MIN_TIP_ANGLE_DEG


def test_a_name_on_the_front_and_a_front_too_low_for_letters(forms):
    params = PlinthRound.validate({})
    mesh = PlinthRound.build(params, [hero(), name()], LocalFileFetcher(forms))
    assert mesh.is_watertight and mesh.body_count == 1
    low = PlinthRound.validate({"height_mm": 6})
    with pytest.raises(ParamOutOfRange) as exc:
        PlinthRound.build(low, [name("Bruno Singh")])
    assert exc.value.keys == ["features[0].text"]


def test_karigar_note_speaks_of_the_form_only_when_there_is_one(forms):
    params = PlinthRound.validate({"shape": "rounded_square", "diameter_mm": 90})
    alone = PlinthRound.karigar_note_for(params, [])
    assert alone == "A rounded-square Pratima plinth, 90 mm across and 12 mm tall, printed standing as modelled."
    with_form = PlinthRound.karigar_note_for(params, [hero(), name()])
    assert "form stands fused on its top" in with_form and "resin" in with_form and "“Bruno” stands 1.2 mm proud" in with_form


def test_the_pipeline_reports_a_tippy_piece_as_a_customer_safe_failure(forms, local_storage):
    request = {
        "job_id": JOB_ID,
        "design_id": DESIGN_ID,
        "version_no": 1,
        "outputs": ["stl"],
        "spec": {
            "spec_version": "1.0",
            "family": "figurine_base",
            "template": "plinth_round@1",
            "params": {"diameter_mm": 40, "height_mm": 6},
            "features": [hero("lollipop.stl")],
            "material": "terracotta_matte",
        },
    }
    payload = build_design(request, storage=local_storage, fetcher=LocalFileFetcher(forms))
    assert payload["code"] == "param_out_of_range", payload
    validate("design.failed", payload)
    assert "choose a plinth at least" in payload["message"]
    request["spec"]["params"] = {"diameter_mm": 120}
    payload = build_design(request, storage=local_storage, fetcher=LocalFileFetcher(forms))
    assert is_completed(payload), payload
    assert payload["hardware"] == [] and payload["printability"]["passed"] is True
