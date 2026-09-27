"""Naam (names) and Buti (motifs) on the live templates, PR 3b.

Which anchors take them; the one-per-anchor and crowding rules; raised and cut names and motifs on every
carrier anchor and on the Jharokha (closed, the right volume added or removed, resting on the bed); the
skin rule under cut-in lettering; the karigar's note; and the pipeline end to end with printability.
"""

from __future__ import annotations

from functools import lru_cache

import pytest

from aakar_geometry.contracts import validate
from aakar_geometry.errors import InvalidSpec, ParamOutOfRange, UnsupportedFeature
from aakar_geometry.features import apply_features, check_features, placement, prepare_marks
from aakar_geometry.pipeline import build_design, is_completed
from aakar_geometry.templates import DeskNameplate, FridgeMagnet, HangingOrnament, JharokhaPhoneStand, KeychainTag

from carrier_checks import relief
from conftest import DESIGN_ID, JOB_ID

TEMPLATES = [KeychainTag, FridgeMagnet, HangingOrnament, DeskNameplate, JharokhaPhoneStand]

# what each anchor takes after the text release (the plan: the keychain's back is for a name, its face for a
# photo, a name or a motif; the nameplate leads with text; the Jharokha's rails and back take names and motifs)
ACCEPTS = {
    KeychainTag: {"face": ("relief_image", "emboss_text", "motif"), "back": ("relief_image", "emboss_text")},
    FridgeMagnet: {"face": ("relief_image", "emboss_text", "motif")},
    HangingOrnament: {"face_front": ("relief_image", "emboss_text", "motif"), "face_back": ("relief_image", "emboss_text", "motif")},
    DeskNameplate: {"face": ("emboss_text", "motif", "relief_image"), "base_front": ("emboss_text", "relief_image")},
    JharokhaPhoneStand: {"side_left": ("emboss_text", "motif"), "side_right": ("emboss_text", "motif"), "back": ("emboss_text", "motif")},
}
TEXT_ANCHORS = [(t, a) for t in TEMPLATES for a, accepts in ACCEPTS[t].items() if "emboss_text" in accepts]
MOTIF_ANCHORS = [(t, a) for t in TEMPLATES for a, accepts in ACCEPTS[t].items() if "motif" in accepts]


def _ids(value):
    return value.id if hasattr(value, "id") else str(value)


@lru_cache(maxsize=None)
def _body(template):
    """Each template's default body, built once (features never modify their input mesh)."""
    return template.build_body(template.validate({}))


def name(anchor: str, text: str = "Asha", **extra) -> dict:
    return {"type": "emboss_text", "text": text, "anchor": anchor, **extra}


def buti(anchor: str, motif_id: str = "lotus", **extra) -> dict:
    return {"type": "motif", "motif_id": motif_id, "anchor": anchor, **extra}


# ----------------------------------------------------------------------------- which anchors take what


@pytest.mark.parametrize("template", TEMPLATES, ids=_ids)
def test_anchors_take_names_and_motifs_where_the_plan_says(template):
    assert {a.id: a.accepts for a in template.anchors} == ACCEPTS[template]
    desc = template.descriptor()
    assert set(desc["features_supported"]) == {t for accepts in ACCEPTS[template].values() for t in accepts}
    assert desc["min_feature_mm"] == 0.8
    for anchor in template.anchors:  # every name or motif spot is flat, and deep enough for the default letters
        assert anchor.projection == "planar" and anchor.max_relief_mm >= 1.2


# ----------------------------------------------------------------------------- raised and cut on every anchor


def _apply(template, features):
    params = template.validate({})
    normalised = check_features(template, features)
    marks = prepare_marks(template, params, normalised)
    mesh, _ = apply_features(template, _body(template), params, normalised)
    return mesh, marks


@pytest.mark.parametrize("template, anchor", TEXT_ANCHORS, ids=_ids)
@pytest.mark.parametrize("mode", ["emboss", "deboss"])
def test_a_name_on_every_text_anchor(template, anchor, mode):
    body = _body(template)
    mesh, marks = _apply(template, [name(anchor, mode=mode, depth_mm=1.0)])
    assert mesh.is_watertight and mesh.is_winding_consistent
    # the letters sit on a flat part of the face: exactly their area × depth is added or taken away
    delta = mesh.volume - body.volume
    assert abs(delta) == pytest.approx(marks[0].region.area * 1.0, rel=0.01)
    assert (delta > 0) == (mode == "emboss")
    # they fit the anchor's printable area and never exceed its tallest letters
    anchor_obj = template.anchor(anchor)
    _, _, w, h = placement.printable_box(template.anchor_frame(anchor, template.validate({})), anchor_obj.bleed_mm)
    x0, y0, x1, y1 = marks[0].region.bounds
    assert x1 - x0 <= w + 1e-6 and y1 - y0 <= h + 1e-6
    if anchor_obj.max_text_height_mm:
        assert marks[0].height_mm <= anchor_obj.max_text_height_mm + 1e-6


@pytest.mark.parametrize("template, anchor", MOTIF_ANCHORS, ids=_ids)
@pytest.mark.parametrize("mode", ["emboss", "deboss"])
def test_a_motif_on_every_motif_anchor(template, anchor, mode):
    body = _body(template)
    mesh, marks = _apply(template, [buti(anchor, "jaali_lattice", mode=mode, depth_mm=0.8)])
    assert mesh.is_watertight and mesh.is_winding_consistent
    delta = mesh.volume - body.volume
    assert abs(delta) == pytest.approx(marks[0].region.area * 0.8, rel=0.01)
    assert (delta > 0) == (mode == "emboss")


@pytest.mark.parametrize("template, anchor", [(KeychainTag, "back"), (HangingOrnament, "face_back")], ids=_ids)
def test_a_raised_name_on_the_underside_rests_the_piece_on_its_letters(template, anchor):
    params = template.validate({})
    mesh = template.build(params, [name(anchor, depth_mm=0.6)])
    thickness = float(params["thickness_mm"])
    assert mesh.bounds[0][2] == pytest.approx(0.0, abs=1e-9)
    top = thickness + (1.0 if template is HangingOrnament else 0.0)  # the ornament's raised border
    assert mesh.bounds[1][2] == pytest.approx(top + 0.6, abs=1e-3)


def test_every_launch_script_on_the_nameplate_face():
    words = ["Asha", "नमस्ते", "విరాజ్", "வணக்கம்", "ನಮಸ್ಕಾರ", "নমস্কার", "નમસ્તે"]
    for word in words:
        mesh, marks = _apply(DeskNameplate, [name("face", word)])
        assert mesh.is_watertight and marks[0].stroke.ok, word
        assert marks[0].height_mm == pytest.approx(36.0) or marks[0].width_mm == pytest.approx(174.0), word


# ----------------------------------------------------------------------------- one per anchor, and crowding


@pytest.mark.parametrize(
    "features, needle",
    [
        ([relief("face"), name("face")], "The Face is too crowded for a photo and a name together; put the name on another spot"),
        ([name("face"), relief("face")], "too crowded for a photo and a name together"),
        ([relief("face"), buti("face")], "too crowded for a photo and a motif together; put the motif on another spot"),
        ([buti("face"), relief("face")], "too crowded for a photo and a motif"),
        ([name("face"), name("face", "Ravi")], "Only one name can go on the Face"),
        ([buti("face"), buti("face", "paisley")], "Only one motif can go on the Face"),
        ([name("back"), name("back", "Ravi")], "Only one name can go on the Back"),
    ],
)
def test_one_name_and_one_motif_per_anchor_and_never_beside_a_photo(features, needle):
    with pytest.raises(InvalidSpec) as exc:
        check_features(KeychainTag, features)
    assert needle in exc.value.message and exc.value.code == "invalid_spec"


def test_names_and_photos_on_different_faces_are_fine():
    out = check_features(KeychainTag, [relief("face"), name("back")])
    assert [f["type"] for f in out] == ["relief_image", "emboss_text"]
    assert check_features(KeychainTag, [name("face"), buti("face"), name("back", "Ravi")])  # a name and a motif may share


def test_a_name_and_a_motif_share_an_anchor_side_by_side():
    params = DeskNameplate.validate({})
    features = check_features(DeskNameplate, [name("face", "Asha Rao"), buti("face", "lotus")])
    marks = prepare_marks(DeskNameplate, params, features)
    text, flower = marks[0].region, marks[1].region
    assert text.distance(flower) >= placement.GAP_MM - 1e-6  # never overlapping
    assert flower.bounds[2] < text.bounds[0]  # the motif first, then the name
    frame = DeskNameplate.anchor_frame("face", params)
    _, _, w, h = placement.printable_box(frame, DeskNameplate.anchor("face").bleed_mm)
    assert flower.bounds[0] >= -w / 2 - 1e-6 and text.bounds[2] <= w / 2 + 1e-6
    mesh, _ = apply_features(DeskNameplate, _body(DeskNameplate), params, features)
    # the name is raised 1.2 mm and the lotus cut 1 mm (each type's default), side by side
    assert mesh.is_watertight
    assert mesh.volume - _body(DeskNameplate).volume == pytest.approx(text.area * 1.2 - flower.area * 1.0, rel=0.01)


def test_curved_lettering_is_not_built_yet():
    with pytest.raises(UnsupportedFeature) as exc:
        check_features(KeychainTag, [name("back", projection="cylindrical")])
    assert "wrap around a curved surface are not available yet" in exc.value.message


# ----------------------------------------------------------------------------- limits


def test_letter_height_and_depth_limits_per_anchor():
    with pytest.raises(ParamOutOfRange) as exc:
        check_features(JharokhaPhoneStand, [name("side_left", height_mm=14)])
    assert exc.value.keys == ["features[0].height_mm"] and "at most 12 mm tall" in exc.value.message
    assert check_features(JharokhaPhoneStand, [name("side_left", height_mm=12)])
    with pytest.raises(ParamOutOfRange) as exc:
        check_features(JharokhaPhoneStand, [buti("back", depth_mm=1.5)])
    assert exc.value.keys == ["features[0].depth_mm"]
    with pytest.raises(ParamOutOfRange) as exc:
        check_features(KeychainTag, [name("face", "Aditya Venkatesh R")])  # 18 letters, the keychain takes 16
    assert exc.value.keys == ["features[0].text"]


def test_lettering_too_fine_for_a_narrow_spot_is_refused_before_anything_is_cut():
    """The Jharokha's back band is 8.6 mm tall: Latin fits, Kannada's finer strokes do not."""
    params = JharokhaPhoneStand.validate({})
    assert prepare_marks(JharokhaPhoneStand, params, check_features(JharokhaPhoneStand, [name("back")]))[0].stroke.ok
    with pytest.raises(ParamOutOfRange) as exc:
        prepare_marks(JharokhaPhoneStand, params, check_features(JharokhaPhoneStand, [name("back", "ನಮಸ್ಕಾರ")]))
    assert exc.value.message == "These letters would be too thin to print at this size; try fewer letters or a larger piece"
    # every motif in the library still prints on that band at scale 1
    from aakar_geometry.features.motif import load_library

    for motif_id in load_library().ids():
        assert prepare_marks(JharokhaPhoneStand, params, check_features(JharokhaPhoneStand, [buti("back", motif_id)]))[0].ok, motif_id


def test_a_full_depth_cut_in_the_thinnest_jharokha_wall_leaves_the_minimum_wall():
    """No validate_content on the Jharokha: its 1.2 mm relief cap is the rule, and a 1.2 mm cut into the
    thinnest (2.4 mm) rail still leaves the 1.2 mm minimum wall."""
    import numpy as np

    params = JharokhaPhoneStand.validate({"wall_mm": 2.4})
    mesh = JharokhaPhoneStand.build(params, [buti("side_left", "star_rangoli", depth_mm=1.2)])
    assert mesh.is_watertight
    frame = JharokhaPhoneStand.anchor_frame("side_left", params)
    start = frame.origin + 5.0 * frame.normal  # outside the rail, straight into the star's centre dot
    hits, _, _ = mesh.ray.intersects_location([start], [-frame.normal], multiple_hits=True)
    depths = sorted(float(np.dot(start - h, frame.normal)) - 5.0 for h in hits)
    assert depths[0] == pytest.approx(1.2, abs=1e-3)  # the floor of the cut
    assert depths[1] == pytest.approx(2.4, abs=1e-3)  # the rail's inner face
    assert depths[1] - depths[0] >= 1.2 - 1e-3


@pytest.mark.parametrize(
    "template, overrides, features, ok",
    [
        (KeychainTag, {"thickness_mm": 2.4}, [name("face", mode="deboss", depth_mm=1.2)], True),
        (KeychainTag, {"thickness_mm": 2.4}, [name("face", mode="deboss", depth_mm=0.8), name("back", mode="deboss", depth_mm=0.6)], False),
        # a name and a motif side by side on one face never overlap: only the deeper cut counts
        (KeychainTag, {"thickness_mm": 2.4}, [name("face", mode="deboss", depth_mm=1.2), buti("face", depth_mm=1.2)], True),
        (FridgeMagnet, {}, [buti("face")], False),  # the default motif (cut 1 mm) over a 3.2 mm magnet pocket
        (FridgeMagnet, {}, [buti("face", mode="emboss")], True),
        (FridgeMagnet, {"thickness_mm": 5.4}, [buti("face")], True),
        (HangingOrnament, {"thickness_mm": 3.0}, [name("face_front", mode="deboss", depth_mm=1.0), buti("face_back", depth_mm=1.0)], False),
        (DeskNameplate, {"thickness_mm": 4.0}, [name("face", mode="deboss", depth_mm=1.5)], True),
    ],
)
def test_cut_in_names_and_motifs_keep_the_minimum_skin(template, overrides, features, ok):
    params = template.validate(overrides)
    normalised = check_features(template, features)
    if ok:
        template.validate_content(params, normalised)
        return
    with pytest.raises(ParamOutOfRange) as exc:
        template.validate_content(params, normalised)
    assert exc.value.keys[-1] == "thickness_mm" and exc.value.detail["min_skin_mm"] == 1.2
    assert "picture" not in exc.value.message and ("name" in exc.value.message or "motif" in exc.value.message)


# ----------------------------------------------------------------------------- the pipeline end to end


def _request(template, features, params=None) -> dict:
    return {
        "job_id": JOB_ID,
        "design_id": DESIGN_ID,
        "version_no": 1,
        "outputs": ["glb", "stl"],
        "spec": {
            "spec_version": "1.0",
            "family": template.family,
            "template": template.ref(),
            "params": params or {},
            "features": features,
            "material": "basic_white",
        },
    }


PRIMARY = {
    KeychainTag: [name("back", depth_mm=0.6), buti("face", "paisley", mode="emboss", depth_mm=0.8)],
    FridgeMagnet: [name("face", "Maa"), buti("face", "star_rangoli", mode="emboss")],
    HangingOrnament: [name("face_front", "शुभ दीपावली"), buti("face_back", "lotus", depth_mm=0.8)],
    DeskNameplate: [name("face", "Asha Rao"), buti("face", "jaali_lattice"), name("base_front", "Product Design")],
    JharokhaPhoneStand: [name("side_left", "Asha"), buti("side_right", "warli_dancer", depth_mm=1.0), name("back", "రవి", mode="deboss")],
}


@pytest.mark.parametrize("template", TEMPLATES, ids=_ids)
def test_the_pipeline_builds_names_and_motifs_that_pass_the_printability_checks(template, local_storage):
    payload = build_design(_request(template, PRIMARY[template]), storage=local_storage)  # names and motifs fetch nothing
    assert is_completed(payload), payload
    validate("design.completed", payload)
    assert payload["printability"]["passed"] is True, payload["printability"]["checks"]
    assert payload["printability"]["checks"]["manifold"]["status"] == "pass"
    echoed = payload["spec"]["features"]
    assert all(f["type"] in ("emboss_text", "motif") for f in echoed)
    assert all(f.get("script") in ("latin", "devanagari", "telugu") for f in echoed if f["type"] == "emboss_text")
    note = payload["karigar_note"]
    for f in PRIMARY[template]:
        if f["type"] == "emboss_text":
            assert f"“{f['text']}”" in note
    assert "motif" in note and "mesh" not in note.lower()
    if template is JharokhaPhoneStand:
        assert note.startswith("A jharokha-arch phone stand") and "on the left side rail" in note and "into the back of the rest" in note


def test_the_pipeline_reports_lettering_problems_as_customer_safe_failures(local_storage):
    cases = [
        (KeychainTag, [name("face", "Asha आशा")], "invalid_spec", "one script"),
        (KeychainTag, [buti("face", "tiger")], "invalid_spec", "don't have a motif called “tiger”"),
        (KeychainTag, [name("face", "Aditya Venkatesh")], "param_out_of_range", "too long for the face"),
        (JharokhaPhoneStand, [name("back", "ನಮಸ್ಕಾರ")], "param_out_of_range", "too thin to print"),
        (FridgeMagnet, [buti("face")], "param_out_of_range", "between the motif and the magnet"),
    ]
    for template, features, code, needle in cases:
        payload = build_design(_request(template, features), storage=local_storage)
        assert payload.get("code") == code, (features, payload)
        validate("design.failed", payload)
        assert needle in payload["message"], payload["message"]
