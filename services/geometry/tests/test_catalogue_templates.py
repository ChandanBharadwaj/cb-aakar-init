"""The second catalogue wave (fourteen families that had a seed row and no template), recipe step 5 across all of them:

descriptor ↔ family content slot, hardware and environment; defaults and parameter corners build watertight inside the
family envelope and the bed; every anchor frame is right-handed, on the skin and facing out; the content each anchor
takes (names, motifs, photo reliefs) builds closed with the right volume sign and rests on the bed; the primary content
passes the printability checks, stability first; the pipeline completes each one with its hardware and a karigar's note.

Every row of ``SPEC`` names a module in ``aakar_geometry.templates`` and the class in it. A template whose module is
not there yet is skipped (the wave lands one module at a time), so ``-k <template id>`` is each template's acceptance
run; once every module is in, the registry imports them all and the skips disappear.
"""

from __future__ import annotations

import importlib
import itertools
from functools import lru_cache

import pytest

from aakar_geometry.contracts import validate
from aakar_geometry.features.fetch import LocalFileFetcher
from aakar_geometry.pipeline import build_design, is_completed
from aakar_geometry.templates import REGISTRY, list_templates, register

from carrier_checks import check_body, check_frames, descriptor_matches_family, relief
from conftest import DESIGN_ID, JOB_ID


def name(anchor: str, text: str = "Asha", **extra) -> dict:
    return {"type": "emboss_text", "text": text, "anchor": anchor, **extra}


def buti(anchor: str, motif_id: str = "lotus", **extra) -> dict:
    return {"type": "motif", "motif_id": motif_id, "anchor": anchor, **extra}


# template id → (module, class, family, features_supported, accepts per anchor, hardware, material for the pipeline,
#                primary content, (feature, +1 adds plastic / −1 takes it away) cases)
SPEC: dict[str, dict] = {
    # ---- carriers (kind carrier): a plate or silhouette that carries the Chhaap
    "pendant_disc": dict(
        module="pendant_disc", cls="PendantDisc", family="pendant",
        features=["relief_image", "emboss_text", "motif"], accepts={"face": ["relief_image", "emboss_text", "motif"]},
        hardware=[], material="basic_white",
        primary=[name("face", "Asha", depth_mm=0.8)],
        content=[(name("face", "Asha", depth_mm=0.8), +1), (relief("face", "deboss", 0.5), -1), (buti("face", "paisley", depth_mm=0.6), +1)],
    ),
    "cake_topper_flat": dict(
        module="cake_topper_flat", cls="CakeTopperFlat", family="cake_topper",
        features=["emboss_text", "motif", "relief_image"], accepts={"face": ["emboss_text", "motif", "relief_image"]},
        hardware=[], material="basic_white",
        primary=[name("face", "Happy 30", depth_mm=1.0)],
        content=[(name("face", "Happy 30", depth_mm=1.0), +1), (buti("face", "star_rangoli", mode="deboss", depth_mm=0.6), -1), (relief("face"), +1)],
    ),
    "cookie_cutter": dict(
        module="cookie_cutter", cls="CookieCutter", family="cookie_cutter",
        features=["relief_image"], accepts={"outline": ["relief_image"]},
        hardware=[], material="basic_white",
        primary=[relief("outline", "deboss", 0.8)],
        content=[(relief("outline", "deboss", 0.8), -1), (relief("outline", "emboss", 0.8), +1)],
    ),
    "ajrakh_coaster_set": dict(
        module="ajrakh_coaster_set", cls="AjrakhCoasterSet", family="coaster",
        features=["motif", "emboss_text"], accepts={"face": ["motif", "emboss_text"]},
        hardware=[], material="petg_slate",
        primary=[buti("face", "paisley", depth_mm=0.8)],
        content=[(buti("face", "paisley", depth_mm=0.8), +1), (name("face", "AR", mode="deboss", depth_mm=0.8), -1)],
    ),
    "pin_badge": dict(
        module="pin_badge", cls="PinBadge", family="pin",
        features=["relief_image", "emboss_text", "motif"], accepts={"face": ["relief_image", "emboss_text", "motif"]},
        hardware=[{"sku": "tie_tack_pin", "qty": 1}], material="basic_white",
        primary=[name("face", "Ravi", depth_mm=0.8)],
        content=[(name("face", "Ravi", depth_mm=0.8), +1), (relief("face", "deboss", 0.5), -1), (buti("face", "lotus", depth_mm=0.6), +1)],
    ),
    "bookmark_plate": dict(
        module="bookmark_plate", cls="BookmarkPlate", family="bookmark",
        features=["relief_image", "emboss_text", "motif"], accepts={"face": ["relief_image", "emboss_text", "motif"]},
        hardware=[], material="basic_white",
        primary=[name("face", "Asha reads", depth_mm=0.8)],
        content=[(relief("face"), +1), (name("face", "Asha reads", mode="deboss", depth_mm=0.6), -1), (buti("face", "jaali_lattice", depth_mm=0.6), +1)],
    ),
    # ---- objects (kind object): a useful thing whose surfaces take a name or a motif
    "fluted_planter": dict(
        module="fluted_planter", cls="FlutedPlanter", family="planter",
        features=["emboss_text", "motif"], accepts={"band": ["emboss_text", "motif"], "rim": ["emboss_text", "motif"]},
        hardware=[], material="basic_white",
        primary=[name("band", "Tulsi", depth_mm=1.0), buti("rim", "lotus", depth_mm=0.6)],
        content=[(name("band", "Tulsi", depth_mm=1.0), +1), (buti("band", "lotus", mode="deboss", depth_mm=0.6), -1), (buti("rim", "lotus", depth_mm=0.6), +1)],
    ),
    "elephant_bookends": dict(
        module="elephant_bookends", cls="ElephantBookends", family="bookend",
        features=["emboss_text", "motif"], accepts={"face": ["emboss_text", "motif"]},
        hardware=[], material="basic_white",
        primary=[name("face", "Kitaab", depth_mm=1.0)],
        content=[(name("face", "Kitaab", depth_mm=1.0), +1), (buti("face", "paisley", mode="deboss", depth_mm=0.8), -1)],
    ),
    "lotus_lamp": dict(
        module="lotus_lamp", cls="LotusLamp", family="table_lamp",
        features=["emboss_text", "motif"], accepts={"base_front": ["emboss_text", "motif"], "shade_band": ["emboss_text", "motif"]},
        hardware=[{"sku": "led_base_usb", "qty": 1}], material="petg_slate",
        primary=[buti("shade_band", "lotus", depth_mm=0.8), name("base_front", "Deepak", depth_mm=0.8)],
        content=[(buti("shade_band", "lotus", depth_mm=0.8), +1), (name("shade_band", "Deepak", mode="deboss", depth_mm=0.6), -1), (name("base_front", "Deepak", depth_mm=0.8), +1)],
    ),
    "diya_holder": dict(
        module="diya_holder", cls="DiyaHolder", family="diya_holder",
        features=["motif"], accepts={"rim": ["motif"]},
        hardware=[], material="petg_slate",
        primary=[buti("rim", "star_rangoli", depth_mm=0.8)],
        content=[(buti("rim", "star_rangoli", depth_mm=0.8), +1), (buti("rim", "lotus", mode="deboss", depth_mm=0.6), -1)],
    ),
    "pooja_shelf": dict(
        module="pooja_shelf", cls="PoojaShelf", family="pooja_accessory",
        features=["motif", "emboss_text"], accepts={"face": ["motif", "emboss_text"]},
        hardware=[], material="basic_white",
        primary=[name("face", "Om Shanti", depth_mm=1.0)],
        content=[(name("face", "Om Shanti", depth_mm=1.0), +1), (buti("face", "lotus", mode="deboss", depth_mm=0.8), -1)],
    ),
    "paisley_hook": dict(
        module="paisley_hook", cls="PaisleyHook", family="wall_hook",
        features=["motif"], accepts={"face": ["motif"]},
        hardware=[{"sku": "nameplate_screws", "qty": 1}], material="basic_white",
        primary=[buti("face", "paisley", depth_mm=0.8)],
        content=[(buti("face", "paisley", depth_mm=0.8), +1), (buti("face", "lotus", mode="deboss", depth_mm=0.6), -1)],
    ),
    "desk_tray": dict(
        module="desk_tray", cls="DeskTray", family="desk_organizer",
        features=["emboss_text", "motif"], accepts={"front": ["emboss_text", "motif"]},
        hardware=[], material="basic_white",
        primary=[name("front", "Tarteeb", depth_mm=1.0)],
        content=[(name("front", "Tarteeb", depth_mm=1.0), +1), (buti("front", "jaali_lattice", mode="deboss", depth_mm=0.8), -1)],
    ),
    "vent_mount": dict(
        module="vent_mount", cls="VentMount", family="car_accessory",
        features=["motif"], accepts={"face": ["motif"]},
        hardware=[], material="petg_slate",
        primary=[buti("face", "lotus", depth_mm=0.8)],
        content=[(buti("face", "lotus", depth_mm=0.8), +1), (buti("face", "paisley", mode="deboss", depth_mm=0.6), -1)],
    ),
}


def _load(template_id: str):
    """The template class, registered if its module is in and the registry has not imported it yet; None if absent."""
    spec = SPEC[template_id]
    try:
        module = importlib.import_module(f"aakar_geometry.templates.{spec['module']}")
    except ModuleNotFoundError as exc:
        if exc.name == f"aakar_geometry.templates.{spec['module']}":
            return None
        raise
    template = getattr(module, spec["cls"])
    assert template.id == template_id and template.version == 1 and template.family == spec["family"], template.ref()
    if (template.id, template.version) not in REGISTRY:
        register(template)
    return template


LOADED = {tid: _load(tid) for tid in SPEC}
TEMPLATES = [t for t in LOADED.values() if t is not None]
ABSENT = [tid for tid, t in LOADED.items() if t is None]


def _ids(value) -> str:
    if hasattr(value, "id"):
        return value.id
    if isinstance(value, dict):
        if "type" in value:
            return f"{value['anchor']}-{value['type']}-{value.get('mode', '')}"
        return "-".join(f"{v:g}" if isinstance(v, (int, float)) else str(v) for v in value.values()) or "defaults"
    return str(value)


def _corners(template) -> list[dict]:
    """Every combination of each parameter's extremes (enums: every option)."""
    keys = list(template.params)
    options = [list(p.options) if p.type == "enum" else [p.min, p.max] for p in template.params.values()]
    if len(keys) > 6:  # keep the matrix sane for templates with many controls: extremes one at a time plus all-min/all-max
        out = [dict(zip(keys, [o[0] for o in options])), dict(zip(keys, [o[-1] for o in options]))]
        defaults = {k: p.default for k, p in template.params.items()}
        for k, opts in zip(keys, options):
            for o in opts:
                out.append({**defaults, k: o})
        return out
    return [dict(zip(keys, combo)) for combo in itertools.product(*options)]


def _valid_corners(template) -> list[dict]:
    out = []
    for corner in _corners(template):
        try:
            template.validate(corner)
        except Exception:  # a combination validate_combination refuses with a sentence
            continue
        out.append(corner)
    return out


@lru_cache(maxsize=None)
def _body(template):
    return template.build_body(template.validate({}))


def test_the_wave_is_accounted_for():
    """Skips are visible: this lists which templates are still to come."""
    assert set(SPEC) == set(LOADED)
    if ABSENT:
        pytest.skip(f"templates not in yet: {', '.join(ABSENT)}")


# ----------------------------------------------------------------------------- descriptor and family


@pytest.mark.parametrize("template", TEMPLATES, ids=_ids)
def test_descriptor_matches_the_family_content_slot_and_hardware(template):
    spec = SPEC[template.id]
    desc = descriptor_matches_family(template, 0.8)
    assert desc["features_supported"] == spec["features"]
    assert {a["id"]: a["accepts"] for a in desc["anchors"]} == spec["accepts"]
    assert desc["hardware"] == spec["hardware"]
    assert template.hardware_for(template.validate({})) == spec["hardware"]
    for key, param in desc["params"].items():
        if param["type"] in ("number", "integer"):
            assert param["min"] <= param["default"] <= param["max"], key
        elif param["type"] == "enum":
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
        template.anchor_frame("no_such_anchor", defaults)
    assert template in list_templates()  # registered


# ----------------------------------------------------------------------------- bodies and frames


@pytest.mark.parametrize(
    "template, overrides",
    [(t, {}) for t in TEMPLATES] + [(t, corner) for t in TEMPLATES for corner in _valid_corners(t)],
    ids=_ids,
)
def test_defaults_and_corners_build_inside_the_envelope_with_frames_on_the_skin(template, overrides):
    params = template.validate(overrides)
    mesh = check_body(template, params)
    check_frames(template, params, mesh)
    note = template.karigar_note(params)
    assert note and "mesh" not in note.lower() and "stl" not in note.lower()


# ----------------------------------------------------------------------------- content on every anchor


@pytest.mark.parametrize(
    "template, feature, sign",
    [(t, feature, sign) for t in TEMPLATES for feature, sign in SPEC[t.id]["content"]],
    ids=_ids,
)
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


@pytest.mark.parametrize("template", TEMPLATES, ids=_ids)
def test_defaults_with_their_content_pass_the_printability_checks(template, content_dir):
    from aakar_inspect import Constraints, inspect_mesh

    params = template.validate({})
    mesh = template.build(params, SPEC[template.id]["primary"], LocalFileFetcher(content_dir))
    report, estimate = inspect_mesh(mesh, Constraints(min_wall_mm=template.constraints.min_wall_mm))
    assert report["passed"] is True, report["checks"]
    for check in ("manifold", "fits_bed", "centre_of_gravity", "tipping_margin"):
        assert report["checks"][check]["status"] == "pass", (check, report["checks"][check])
    assert report["checks"]["thinnest_wall"]["status"] in ("pass", "warn")
    assert estimate["print_seconds"] > 0


def _request(template, params: dict, features: list, material: str) -> dict:
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
    spec = SPEC[template.id]
    payload = build_design(_request(template, {}, spec["primary"], spec["material"]), storage=local_storage, fetcher=LocalFileFetcher(content_dir))
    assert is_completed(payload), payload
    validate("design.completed", payload)
    assert payload["template"] == {"id": template.id, "version": 1}
    assert payload["hardware"] == spec["hardware"]
    assert payload["printability"]["passed"] is True, payload["printability"]["checks"]
    assert [f["type"] for f in payload["spec"]["features"]] == [f["type"] for f in spec["primary"]]
    note = payload["karigar_note"]
    assert "mesh" not in note.lower() and "stl" not in note.lower()
    for feature in spec["primary"]:
        if feature["type"] == "emboss_text":
            assert f"“{feature['text']}”" in note
