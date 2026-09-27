"""The keepsakes and the second wave (PR 8), recipe step 5 across the five templates:

descriptor ↔ family content slot and hardware; defaults and parameter corners build watertight inside the
family envelope and the bed; every anchor frame is right-handed, on the skin and facing out; the content each
anchor takes (the lithophane photo, a customer's form, names, motifs, photo reliefs) builds closed with the
right volume sign and rests on the bed; the primary content passes the printability checks, stability first;
the pipeline completes each one with its hardware and a karigar's note in the craft register.
"""

from __future__ import annotations

import itertools
from functools import lru_cache

import pytest

from aakar_geometry.contracts import validate
from aakar_geometry.features.fetch import LocalFileFetcher
from aakar_geometry.pipeline import build_design, is_completed
from aakar_geometry.templates import KeycapMx, LithophanePlate, PetTag, PhotoFrameStd, PlinthRound, list_templates

from carrier_checks import check_body, check_frames, descriptor_matches_family, relief
from conftest import DESIGN_ID, JOB_ID, content_source

TEMPLATES = [LithophanePlate, PlinthRound, PetTag, PhotoFrameStd, KeycapMx]

FEATURES = {
    LithophanePlate: ["relief_image"],
    PlinthRound: ["hero_mesh", "emboss_text"],
    PetTag: ["emboss_text", "motif", "relief_image"],
    PhotoFrameStd: ["emboss_text", "motif"],
    KeycapMx: ["relief_image", "emboss_text"],
}
ACCEPTS = {
    LithophanePlate: {"plate": ["relief_image"]},
    PlinthRound: {"top": ["hero_mesh"], "base_front": ["emboss_text"]},
    PetTag: {"face": ["emboss_text", "motif", "relief_image"], "back": ["emboss_text", "motif", "relief_image"]},
    PhotoFrameStd: {"border": ["motif", "emboss_text"], "base_front": ["emboss_text"]},
    KeycapMx: {"top": ["relief_image", "emboss_text"]},
}
HARDWARE = {
    LithophanePlate: [{"sku": "led_base_usb", "qty": 1}],
    PlinthRound: [],
    PetTag: [{"sku": "split_ring_25", "qty": 1}],
    PhotoFrameStd: [{"sku": "acrylic_4x6", "qty": 1}],
    KeycapMx: [],
}
MIN_FEATURE = {KeycapMx: 1.0}  # letters on a keycap keep 1 mm strokes; 0.8 mm everywhere else
# Where a template leaves its family's seed envelope on purpose (reported, for the seed to follow):
# pet tags are 25–35 mm (the keychain family starts at 30); a Pratima plinth alone is 40–120 mm (the family's
# 50–200 mm is plinth and form together)
ENVELOPE = {PetTag: (25.0, 60.0), PlinthRound: (40.0, 200.0)}
# the small plinth's front is under 8 mm tall; the keycap's anchor plane is the bottom of its 0.3 mm dish
FRAME_CHECK = {PlinthRound: {"min_size_mm": 5.0}, KeycapMx: {"tolerance_mm": 0.35}}


def _corners(template) -> list[dict]:
    """Every combination of each parameter's extremes (enums: every option)."""
    keys = list(template.params)
    options = [list(p.options) if p.type == "enum" else [p.min, p.max] for p in template.params.values()]
    return [dict(zip(keys, combo)) for combo in itertools.product(*options)]


def _valid_corners(template) -> list[dict]:
    out = []
    for corner in _corners(template):
        try:
            template.validate(corner)
        except Exception:  # the lithophane's thinnest and thickest parts must differ by 1.2 mm
            continue
        out.append(corner)
    return out


def _ids(value) -> str:
    if hasattr(value, "id"):
        return value.id
    if isinstance(value, dict):
        return "-".join(f"{v:g}" if isinstance(v, (int, float)) else str(v) for v in value.values()) or "defaults"
    return str(value)


def name(anchor: str, text: str = "Asha", **extra) -> dict:
    return {"type": "emboss_text", "text": text, "anchor": anchor, **extra}


def buti(anchor: str, motif_id: str = "lotus", **extra) -> dict:
    return {"type": "motif", "motif_id": motif_id, "anchor": anchor, **extra}


def hero(model: str = "sphere.stl", **extra) -> dict:
    return {"type": "hero_mesh", "source": content_source(model, "stl"), "anchor": "top", **extra}


def lithophane(**extra) -> dict:
    return relief("plate", "lithophane") | extra


@lru_cache(maxsize=None)
def _body(template):
    return template.build_body(template.validate({}))


# ----------------------------------------------------------------------------- descriptor and family


@pytest.mark.parametrize("template", TEMPLATES, ids=_ids)
def test_descriptor_matches_the_family_content_slot_and_hardware(template):
    desc = descriptor_matches_family(template, MIN_FEATURE.get(template, 0.8))
    assert desc["features_supported"] == FEATURES[template]
    assert {a["id"]: a["accepts"] for a in desc["anchors"]} == ACCEPTS[template]
    assert desc["hardware"] == HARDWARE[template]
    assert template.hardware_for(template.validate({})) == HARDWARE[template]
    for key, param in desc["params"].items():
        if param["type"] in ("number", "integer"):
            assert param["min"] <= param["default"] <= param["max"], key
        else:
            assert param["default"] in param["options"], key
    # the published sizes are the frames at the default parameters
    defaults = template.validate({})
    for anchor in desc["anchors"]:
        frame = template.anchor_frame(anchor["id"], defaults)
        if anchor["kind"] == "surface":
            assert anchor["size_mm"] == pytest.approx(list(frame.size_mm), abs=0.06), anchor["id"]
        else:
            assert anchor["bounds_mm"] == pytest.approx(list(frame.bounds_mm), abs=1e-6), anchor["id"]
    with pytest.raises(NotImplementedError):
        template.anchor_frame("lid", defaults)
    assert template in list_templates()  # registered


# ----------------------------------------------------------------------------- bodies and frames


@pytest.mark.parametrize(
    "template, overrides",
    [(t, {}) for t in TEMPLATES] + [(t, corner) for t in TEMPLATES for corner in _valid_corners(t)],
    ids=_ids,
)
def test_defaults_and_corners_build_inside_the_envelope_with_frames_on_the_skin(template, overrides):
    params = template.validate(overrides)
    mesh = check_body(template, params, ENVELOPE.get(template))
    check_frames(template, params, mesh, **FRAME_CHECK.get(template, {}))
    note = template.karigar_note(params)
    assert note and "mesh" not in note.lower() and "stl" not in note.lower()


# ----------------------------------------------------------------------------- content on every anchor

# (template, feature, +1 adds plastic / −1 takes it away)
CONTENT = [
    (LithophanePlate, lithophane(), -1),  # the photo thins the bare max-thickness plate where it is light
    (PlinthRound, hero(), +1),
    (PlinthRound, name("base_front", "Bruno", depth_mm=1.0), +1),
    (PlinthRound, name("base_front", "Bruno", mode="deboss", depth_mm=1.0), -1),
    (PetTag, name("face", "Moti", depth_mm=1.0), +1),
    (PetTag, name("back", "Ravi", mode="deboss", depth_mm=1.0), -1),
    (PetTag, buti("face", "lotus", mode="emboss", depth_mm=0.8), +1),
    (PetTag, buti("back", "lotus", depth_mm=0.8), -1),
    (PetTag, relief("face"), +1),
    (PetTag, relief("back", "deboss"), -1),
    (PhotoFrameStd, buti("border", "jaali_lattice", depth_mm=1.0), -1),
    (PhotoFrameStd, name("border", "Ghar", mode="deboss", depth_mm=1.5), -1),
    (PhotoFrameStd, name("base_front", "Asha & Ravi", mode="deboss", depth_mm=1.0), -1),
    (KeycapMx, name("top", "A", depth_mm=0.6), +1),
    (KeycapMx, name("top", "K", mode="deboss", depth_mm=0.6), -1),
    (KeycapMx, relief("top"), +1),
    (KeycapMx, relief("top", "deboss"), -1),
]


@pytest.mark.parametrize("template, feature, sign", CONTENT, ids=lambda v: v.id if hasattr(v, "id") else (f"{v['anchor']}-{v['type']}-{v.get('mode', '')}" if isinstance(v, dict) else str(v)))
def test_content_on_every_anchor_builds_closed_with_the_right_volume_sign(template, feature, sign, content_dir):
    params = template.validate({})
    body = _body(template)
    mesh = template.build(params, [feature], LocalFileFetcher(content_dir))
    assert mesh.is_watertight and mesh.is_winding_consistent and mesh.volume > 0
    delta = mesh.volume - body.volume
    assert delta * sign > 0, delta
    assert mesh.bounds[0][2] == pytest.approx(0.0, abs=1e-6)  # resting on the bed
    assert mesh.body_count == 1  # one piece: nothing floats free of the body


# ----------------------------------------------------------------------------- printability and the pipeline

PRIMARY = {
    LithophanePlate: [lithophane()],
    PlinthRound: [hero(), name("base_front", "Bruno")],
    PetTag: [name("face", "Moti"), name("back", "Ravi", mode="deboss", depth_mm=0.8)],
    PhotoFrameStd: [buti("border", "jaali_lattice", depth_mm=1.0), name("base_front", "Asha & Ravi", mode="deboss", depth_mm=1.0)],
    KeycapMx: [name("top", "A", depth_mm=0.6)],
}


@pytest.mark.parametrize("template", TEMPLATES, ids=_ids)
def test_defaults_with_their_content_pass_the_printability_checks(template, content_dir):
    from aakar_inspect import Constraints, inspect_mesh

    params = template.validate({})
    mesh = template.build(params, PRIMARY[template], LocalFileFetcher(content_dir))
    report, estimate = inspect_mesh(mesh, Constraints(min_wall_mm=template.constraints.min_wall_mm))
    assert report["passed"] is True, report["checks"]
    for check in ("manifold", "fits_bed", "centre_of_gravity", "tipping_margin"):
        assert report["checks"][check]["status"] == "pass", (check, report["checks"][check])
    assert report["checks"]["thinnest_wall"]["status"] in ("pass", "warn")
    assert estimate["print_seconds"] > 0


def _request(template, params: dict, features: list, material: str = "basic_white") -> dict:
    return {
        "job_id": JOB_ID,
        "design_id": DESIGN_ID,
        "version_no": 1,
        "outputs": ["glb", "stl"],
        "spec": {
            "spec_version": "1.0",
            "family": template.family,
            "template": template.ref(),
            "params": params,
            "features": features,
            "material": material,
        },
    }


@pytest.mark.parametrize("template", TEMPLATES, ids=_ids)
def test_pipeline_completes_each_with_its_content_hardware_and_note(template, content_dir, local_storage):
    payload = build_design(_request(template, {}, PRIMARY[template]), storage=local_storage, fetcher=LocalFileFetcher(content_dir))
    assert is_completed(payload), payload
    validate("design.completed", payload)
    assert payload["template"] == {"id": template.id, "version": 1}
    assert payload["hardware"] == HARDWARE[template]
    assert payload["printability"]["passed"] is True, payload["printability"]["checks"]
    assert [f["type"] for f in payload["spec"]["features"]] == [f["type"] for f in PRIMARY[template]]
    note = payload["karigar_note"]
    assert "mesh" not in note.lower() and "stl" not in note.lower()
    for feature in PRIMARY[template]:
        if feature["type"] == "emboss_text":
            assert f"“{feature['text']}”" in note
