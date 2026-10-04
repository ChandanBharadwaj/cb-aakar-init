"""Anchors say which modes their content may take (``modes``) and whether the piece needs content there
(``required``): published in the descriptor only when set, enforced for every template in feature validation
(the pipeline, ``Template.build`` and ``apply_features`` all go through it), worded by the template when it has its
own words (``mode_refusal``, ``missing_content``)."""

from __future__ import annotations

import pytest

from aakar_geometry.contracts import validate
from aakar_geometry.errors import InvalidSpec, UnsupportedFeature
from aakar_geometry.features import check, check_features
from aakar_geometry.pipeline import build_design
from aakar_geometry.templates import LithophanePlate, PhotoFrameStd, list_templates
from aakar_geometry.templates.base import Anchor, Template
from aakar_geometry.templates.lithophane_plate import MISSING_PHOTO

from carrier_checks import relief
from conftest import DESIGN_ID, JOB_ID, content_source


class Rails(Template):
    """A rail that only cuts in, a plate that is a lithophane and must hold one, and a face that takes anything."""

    id = "test_rails"
    version = 1
    family = "keychain"  # max_text_chars 16 in families.json
    name = "Test rails"
    anchors = (
        Anchor("top", "Top rail", "planar", size_mm=(60, 12), max_relief_mm=1.5, accepts=("emboss_text", "motif"), modes=("deboss",)),
        Anchor("plate", "Plate", "planar", size_mm=(40, 30), accepts=("relief_image",), modes=("lithophane",), required=True),
        Anchor("face", "Face", "planar", size_mm=(40, 30), max_relief_mm=1.5, accepts=("emboss_text", "relief_image")),
    )
    features_supported = ("emboss_text", "motif", "relief_image")


def name(anchor: str, text: str = "Asha", **extra) -> dict:
    return {"type": "emboss_text", "text": text, "anchor": anchor, **extra}


def buti(anchor: str, motif_id: str = "lotus", **extra) -> dict:
    return {"type": "motif", "motif_id": motif_id, "anchor": anchor, **extra}


def photo(anchor: str = "plate", **extra) -> dict:
    return {"type": "relief_image", "source": content_source("photo.png", "png"), "anchor": anchor, "mode": "lithophane", **extra}


# ----------------------------------------------------------------------------- published only when set

# (template id, anchor id) -> (modes, required) on the live templates; every other anchor publishes neither
PUBLISHED = {
    ("photo_frame_std", "border"): (["deboss"], None),
    ("photo_frame_std", "base_front"): (["deboss"], None),
    ("lithophane_plate", "plate"): (["lithophane"], True),
    # the Kadi-C lid and cap cover print face down, so their faces only cut in
    ("drain_lid", "face"): (["deboss"], None),
    ("bottle_cap_cover", "top"): (["deboss"], None),
}


def test_live_descriptors_publish_modes_and_required_only_where_they_are_set():
    seen = set()
    for template in list_templates():
        desc = template.descriptor()  # validated against template-descriptor.v1.json
        for anchor in desc["anchors"]:
            modes, required = PUBLISHED.get((template.id, anchor["id"]), (None, None))
            assert anchor.get("modes") == modes and anchor.get("required") == required, (template.id, anchor)
            if modes is not None:
                seen.add((template.id, anchor["id"]))
    assert seen == set(PUBLISHED)


def test_an_anchor_takes_only_distinct_known_modes():
    for modes in [(), ("raised",), ("emboss", "emboss")]:
        with pytest.raises(ValueError):
            Anchor("x", "X", "planar", modes=modes)
    rail = Anchor("x", "X", "planar", modes=["deboss"], required=1)
    assert rail.modes == ("deboss",) and rail.required is True
    assert rail.allows("deboss") and not rail.allows("emboss")
    assert Anchor("y", "Y", "planar").allows("lithophane")  # no modes listed: every mode its types allow
    top, plate, face = Rails.descriptor()["anchors"]
    validate("template-descriptor", Rails.descriptor())
    assert top["modes"] == ["deboss"] and "required" not in top
    assert plate["modes"] == ["lithophane"] and plate["required"] is True
    assert "modes" not in face and "required" not in face


# ----------------------------------------------------------------------------- modes


@pytest.mark.parametrize(
    "features, message, index",
    [
        ([photo(), name("top", mode="emboss")], "On the top rail the name can only be cut in, not raised; choose cut in", 1),
        # a name that leaves its mode out is raised (the type's default), which the rail does not list
        ([photo(), name("top")], "On the top rail the name can only be cut in, not raised; choose cut in", 1),
        ([photo(), buti("top", mode="emboss")], "On the top rail the motif can only be cut in, not raised; choose cut in", 1),
        ([photo(mode="emboss")], "On the plate the photo can only be lit from behind as the plate itself, not raised; choose lit from behind", 0),
        ([photo(mode="deboss")], "On the plate the photo can only be lit from behind as the plate itself, not cut in; choose lit from behind", 0),
    ],
)
def test_a_mode_the_anchor_leaves_out_is_refused_on_the_features_mode_key(features, message, index):
    with pytest.raises(UnsupportedFeature) as exc:
        check_features(Rails, features)
    assert exc.value.code == "unsupported_feature" and exc.value.message == message
    assert exc.value.detail["key"] == f"features[{index}].mode" and exc.value.detail["feature"] == index
    assert exc.value.detail["needs"] == exc.value.detail["modes"][0] and exc.value.detail["mode"] in ("emboss", "deboss")


def test_modes_the_anchor_lists_pass_and_an_unlisted_anchor_takes_every_mode():
    out = check_features(Rails, [photo(), name("top", mode="deboss"), buti("top")])  # a motif's default is cut in
    assert [(f["anchor"], f["mode"]) for f in out] == [("plate", "lithophane"), ("top", "deboss"), ("top", "deboss")]
    # the face lists no modes: a raised or cut-in name, even a lithophane photo, pass validation (a template without
    # a lithophane hook refuses the night light when it is built)
    assert check_features(Rails, [photo(), name("face", mode="emboss")])[1]["mode"] == "emboss"
    assert check_features(Rails, [photo(), photo("face")])[1]["mode"] == "lithophane"


# ----------------------------------------------------------------------------- required content


def test_a_required_anchor_needs_its_content_even_when_there_are_no_features():
    for spec in ({}, {"features": None}, {"features": []}, {"features": [name("face"), buti("top")]}):
        with pytest.raises(InvalidSpec) as exc:
            check(Rails, spec)
        assert exc.value.code == "invalid_spec"
        assert exc.value.message == "Add a photo to the plate: Test rails can't be made without it"
        assert exc.value.detail == {"template": "test_rails@1", "anchor": "plate", "required": True, "accepts": ["relief_image"]}
    with pytest.raises(InvalidSpec):
        Rails.build({})  # Template.build validates the features first, so nothing is built
    assert len(check(Rails, {"features": [photo()]})) == 1


# ----------------------------------------------------------------------------- the templates' own words


def test_the_frame_and_the_night_light_keep_their_own_words():
    with pytest.raises(UnsupportedFeature) as exc:
        check_features(PhotoFrameStd, [buti("border", mode="emboss")])
    assert exc.value.message == "On a Chaukhat frame the motif is cut into the face, not raised (the frame prints face down); choose cut-in"
    assert exc.value.detail == {"anchor": "border", "mode": "emboss", "modes": ["deboss"], "feature": 0, "key": "features[0].mode", "needs": "deboss"}
    assert check_features(PhotoFrameStd, [buti("border")])[0]["mode"] == "deboss"  # the motif's default is listed
    with pytest.raises(InvalidSpec) as exc:
        check_features(LithophanePlate, [])
    assert exc.value.message == MISSING_PHOTO and exc.value.detail["anchor"] == "plate"
    with pytest.raises(UnsupportedFeature) as exc:
        check_features(LithophanePlate, [relief("plate", "deboss")])
    assert "becomes the glowing plate itself" in exc.value.message and exc.value.detail["needs"] == "lithophane"
    # a photo that leaves its mode out is raised, the type's default, which the plate does not list
    with pytest.raises(UnsupportedFeature):
        check_features(LithophanePlate, [{"type": "relief_image", "source": content_source("photo.png", "png"), "anchor": "plate"}])


@pytest.mark.parametrize(
    "template, features, code, needle",
    [
        (LithophanePlate, [], "invalid_spec", MISSING_PHOTO),
        (PhotoFrameStd, [name("border")], "unsupported_feature", "cut into the face, not raised"),
    ],
)
def test_the_pipeline_refuses_them_before_anything_is_built(template, features, code, needle, local_storage):
    request = {
        "job_id": JOB_ID,
        "design_id": DESIGN_ID,
        "version_no": 1,
        "spec": {"spec_version": "1.0", "family": template.family, "template": template.ref(), "params": {}, "features": features, "material": "basic_white"},
    }
    payload = build_design(request, storage=local_storage)
    validate("design.failed", payload)
    assert payload["code"] == code and needle in payload["message"]
    assert not local_storage.root.exists() or not any(local_storage.root.rglob("*.*"))  # nothing was exported
