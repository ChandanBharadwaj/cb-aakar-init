"""``comic_pop``, Katha's comic-book look (plan §8): which carriers offer it, the content defaults it changes (names
and motifs that leave their mode out stand raised wherever the anchor allows it, raised ones bolder but never past
the anchor's ``max_relief_mm``; explicit values and photos untouched), that it changes nothing else (a piece without
content builds exactly the same), and the karigar's note naming the look."""

from __future__ import annotations

import pytest

from aakar_geometry.contracts import validate
from aakar_geometry.errors import ParamOutOfRange, UnsupportedFeature
from aakar_geometry.features import check_features
from aakar_geometry.features.styles import COMIC_POP, COMIC_POP_DEPTH_MM
from aakar_geometry.features.validate import normalise_features
from aakar_geometry.pipeline import build_design, is_completed
from aakar_geometry.templates import (
    DeskNameplate,
    FridgeMagnet,
    HangingOrnament,
    HeadphoneTopper,
    HookPlaque,
    DashIdol,
    PillarHeadphoneStand,
    JharokhaPhoneStand,
    KeycapMx,
    KeychainTag,
    LithophanePlate,
    PetTag,
    PhotoFrameStd,
    PlinthRound,
    RawPrint,
    list_templates,
)
from aakar_geometry.templates.base import Anchor, Template

from carrier_checks import relief
from conftest import DESIGN_ID, JOB_ID, content_source

COMIC = [KeychainTag, PetTag, FridgeMagnet, HangingOrnament, DeskNameplate, KeycapMx, PlinthRound, HeadphoneTopper, PillarHeadphoneStand, HookPlaque, DashIdol]
# every spot of a comic carrier that takes a name or a motif
MARKS = [
    (t, a, ftype)
    for t in COMIC
    for a in t.anchors
    for ftype in ("emboss_text", "motif")
    if ftype in (a.accepts if a.accepts is not None else t.features_supported)
]


def mark(ftype: str, anchor: str, **extra) -> dict:
    if ftype == "emboss_text":
        return {"type": "emboss_text", "text": "Ra", "anchor": anchor, **extra}  # two letters fit every family
    return {"type": "motif", "motif_id": "lotus", "anchor": anchor, **extra}


def _ids(value) -> str:
    return value.id if hasattr(value, "id") else str(value)


def test_comic_pop_is_offered_where_names_and_motifs_can_stand_raised():
    offered = {t.id for t in list_templates() if COMIC_POP in t.descriptor()["style_variants"]}
    assert offered == {t.id for t in COMIC}
    # not on the night light (the photo is the plate), the frame (it prints face down), the stand or a raw print
    assert not offered & {t.id for t in (LithophanePlate, PhotoFrameStd, JharokhaPhoneStand, RawPrint)}
    for template, anchor, _ in MARKS:
        assert anchor.allows("emboss"), (template.id, anchor.id)


@pytest.mark.parametrize("template, anchor, ftype", MARKS, ids=lambda v: _ids(v))
def test_names_and_motifs_that_leave_their_mode_out_stand_raised_and_bolder(template, anchor, ftype):
    (out,) = check_features(template, [mark(ftype, anchor.id)], style=COMIC_POP)
    assert out["mode"] == "emboss"
    assert out["depth_mm"] == pytest.approx(min(COMIC_POP_DEPTH_MM, anchor.max_relief_mm))  # keycap 0.6, pet tag 1.2
    (plain,) = normalise_features([mark(ftype, anchor.id)], template)  # no style: the contract's defaults
    assert (plain["mode"], plain["depth_mm"]) == (("emboss", 1.2) if ftype == "emboss_text" else ("deboss", 1.0))


@pytest.mark.parametrize(
    "feature, expected",
    [
        (mark("emboss_text", "face", mode="deboss"), ("deboss", 1.2)),  # a chosen cut-in keeps the contract's depth
        (mark("motif", "face", mode="deboss"), ("deboss", 1.0)),
        (mark("emboss_text", "face", depth_mm=0.8), ("emboss", 0.8)),  # a chosen depth is kept
        (mark("motif", "face", depth_mm=0.6), ("emboss", 0.6)),
        (mark("emboss_text", "face", mode="emboss"), ("emboss", 1.5)),  # raised by choice, bolder by default
    ],
    ids=["name-cut-in", "motif-cut-in", "name-depth", "motif-depth", "name-raised"],
)
def test_a_chosen_mode_or_depth_wins(feature, expected):
    (out,) = check_features(KeychainTag, [feature], style=COMIC_POP)
    assert (out["mode"], out["depth_mm"]) == expected


def test_photos_and_templates_without_the_style_keep_the_contract_defaults():
    photo = {"type": "relief_image", "source": content_source("photo.png", "png"), "anchor": "face"}
    (out,) = check_features(KeychainTag, [photo], style=COMIC_POP)
    assert (out["mode"], out["relief_mm"]) == ("emboss", 0.6)
    # the Jharokha does not offer comic_pop (the pipeline refuses the style): its defaults do not move
    (name,) = check_features(JharokhaPhoneStand, [mark("emboss_text", "back")], style=COMIC_POP)
    assert (name["mode"], name["depth_mm"]) == ("emboss", 1.2)
    (motif,) = check_features(KeychainTag, [mark("motif", "face")], style="none")
    assert (motif["mode"], motif["depth_mm"]) == ("deboss", 1.0)


class CutRail(Template):
    """A comic carrier whose one spot only cuts in."""

    id = "test_cut_rail"
    version = 1
    family = "keychain"
    name = "Test cut rail"
    anchors = (Anchor("rail", "Rail", "planar", size_mm=(60, 12), max_relief_mm=1.5, modes=("deboss",)),)
    features_supported = ("emboss_text", "motif")
    style_variants = (COMIC_POP,)


def test_a_spot_that_only_cuts_in_keeps_the_types_default():
    (motif,) = check_features(CutRail, [mark("motif", "rail")], style=COMIC_POP)
    assert (motif["mode"], motif["depth_mm"]) == ("deboss", 1.0)
    with pytest.raises(UnsupportedFeature) as exc:  # a name's default (raised) is not listed: the client sends cut-in
        check_features(CutRail, [mark("emboss_text", "rail")], style=COMIC_POP)
    assert exc.value.detail["key"] == "features[0].mode"


def test_a_raised_comic_motif_needs_no_skin_over_the_magnet():
    """The default cut-in motif eats into the magnet's skin and is refused; under comic_pop it stands raised."""
    params = FridgeMagnet.validate({})
    with pytest.raises(ParamOutOfRange):
        FridgeMagnet.validate_content(params, check_features(FridgeMagnet, [mark("motif", "face")]))
    FridgeMagnet.validate_content(params, check_features(FridgeMagnet, [mark("motif", "face")], style=COMIC_POP))


# ----------------------------------------------------------------------------- the pipeline


def _request(template, features: list, style: str, material: str = "basic_white") -> dict:
    return {
        "job_id": JOB_ID,
        "design_id": DESIGN_ID,
        "version_no": 1,
        "outputs": ["glb", "stl"],
        "spec": {
            "spec_version": "1.0",
            "family": template.family,
            "template": template.ref(),
            "params": {},
            "features": features,
            "style": style,
            "material": material,
        },
    }


def test_comic_pop_without_content_builds_the_same_piece(local_storage):
    plain = build_design(_request(KeychainTag, [], "none"), storage=local_storage)
    comic = build_design(_request(KeychainTag, [], COMIC_POP), storage=local_storage)
    assert is_completed(plain) and is_completed(comic), comic
    validate("design.completed", comic)
    assert (plain["spec"]["style"], comic["spec"]["style"]) == ("none", COMIC_POP)
    assert comic["printability"]["geometry"] == plain["printability"]["geometry"]
    assert comic["print_estimate"] == plain["print_estimate"]
    assert comic["assets"]["stl"]["bytes"] == plain["assets"]["stl"]["bytes"]
    assert comic["karigar_note"] == (
        plain["karigar_note"] + " Comic pop style: it reads best in a high-contrast finish such as Basic White or Indigo Matte, like a comic panel."
    )


def test_a_comic_name_stands_raised_and_bold_through_the_pipeline(local_storage):
    bare = build_design(_request(KeychainTag, [], COMIC_POP), storage=local_storage)
    payload = build_design(_request(KeychainTag, [{"type": "emboss_text", "text": "Asha", "anchor": "face"}], COMIC_POP), storage=local_storage)
    assert is_completed(payload), payload
    validate("design.completed", payload)
    (name,) = payload["spec"]["features"]
    assert (name["mode"], name["depth_mm"]) == ("emboss", COMIC_POP_DEPTH_MM)
    assert payload["printability"]["geometry"]["volume_cm3"] > bare["printability"]["geometry"]["volume_cm3"]
    assert payload["printability"]["geometry"]["bounds_mm"][2] == pytest.approx(3.0 + COMIC_POP_DEPTH_MM, abs=0.01)
    note = payload["karigar_note"]
    assert "“Asha” stands 1.5 mm proud on the face." in note
    assert note.endswith(
        "Comic pop style: the lettering and motifs stand bold and raised, like inked panel art, so keep their edges crisp; "
        "they read best in a high-contrast finish such as Basic White or Indigo Matte."
    )


@pytest.mark.parametrize(
    "template, features",
    [(PhotoFrameStd, []), (JharokhaPhoneStand, []), (LithophanePlate, [relief("plate", "lithophane")])],
    ids=_ids,
)
def test_comic_pop_is_refused_where_it_is_not_offered(template, features, local_storage):
    payload = build_design(_request(template, features, COMIC_POP), storage=local_storage)
    validate("design.failed", payload)
    assert payload["code"] == "unsupported_feature" and "comic_pop" in payload["message"]
