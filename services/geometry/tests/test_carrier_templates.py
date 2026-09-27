"""The first carriers (PR 4), recipe step 5 across all four planar templates:

descriptor ↔ family content slot; defaults and parameter corners build watertight inside the family
envelope and the bed; every anchor frame is right-handed, on the skin and facing out; a photo relief on
every surface anchor adds (emboss) or removes (deboss) plastic and stays closed; the inspector passes
at the defaults; the pipeline completes with the right hardware.
"""

from __future__ import annotations

import pytest

from aakar_geometry.contracts import validate
from aakar_geometry.features.fetch import LocalFileFetcher
from aakar_geometry.pipeline import build_design, is_completed
from aakar_geometry.templates import DeskNameplate, FridgeMagnet, HangingOrnament, KeychainTag

from carrier_checks import check_body, check_frames, descriptor_matches_family, relief
from conftest import DESIGN_ID, JOB_ID

CARRIERS = [KeychainTag, FridgeMagnet, HangingOrnament, DeskNameplate]

HARDWARE = {
    KeychainTag: [{"sku": "split_ring_25", "qty": 1}],
    FridgeMagnet: [{"sku": "magnet_d10x3", "qty": 1}],
    HangingOrnament: [{"sku": "cord_200", "qty": 1}],
    DeskNameplate: [{"sku": "adhesive_pads", "qty": 1}],
}

# the extremes of every parameter (and every shape) that validate_combination accepts
CORNERS = {
    KeychainTag: [
        {"shape": shape, "width_mm": width, "thickness_mm": thickness, "hole_d_mm": hole}
        for shape in ("rect", "rounded", "circle", "heart")
        for width, thickness, hole in ((30, 2.4, 6.0), (60, 4.0, 4.0))
    ],
    FridgeMagnet: [
        {"shape": shape, "size_mm": size, "thickness_mm": thickness, "magnet_count": count}
        for shape in ("rect", "rounded", "circle")
        for size, thickness, count in ((40, 4.5, 2), (70, 6.0, 1))
    ],
    HangingOrnament: [
        {"silhouette": silhouette, "diameter_mm": diameter, "thickness_mm": thickness, "border_mm": border}
        for silhouette in ("disc", "star", "bauble")
        for diameter, thickness, border in ((50, 3.0, 6.0), (90, 5.0, 0.0))
    ],
    DeskNameplate: [
        {"width_mm": 120, "height_mm": 40, "tilt_deg": 60, "base_depth_mm": 30, "thickness_mm": 4},
        {"width_mm": 250, "height_mm": 100, "tilt_deg": 80, "base_depth_mm": 60, "thickness_mm": 8},
        {"width_mm": 250, "height_mm": 100, "tilt_deg": 60, "base_depth_mm": 60, "thickness_mm": 8},
        {"width_mm": 120, "height_mm": 100, "tilt_deg": 80, "base_depth_mm": 30, "thickness_mm": 4},
    ],
}

SURFACES = [(t, a.id) for t in CARRIERS for a in t.anchors]


@pytest.mark.parametrize("template", CARRIERS, ids=lambda t: t.id)
def test_descriptor_matches_the_family_content_slot(template):
    desc = descriptor_matches_family(template)
    assert desc["features_supported"] == ["relief_image"]  # text and motifs land with PR 3b
    assert desc["hardware"] == HARDWARE[template]
    assert template.hardware_for(template.validate({})) == HARDWARE[template]
    assert all(a["kind"] == "surface" and a["max_relief_mm"] == 1.5 and a["accepts"] == ["relief_image"] for a in desc["anchors"])
    for key, param in desc["params"].items():
        if param["type"] in ("number", "integer"):
            assert param["min"] <= param["default"] <= param["max"], key
        else:
            assert param["default"] in param["options"], key
    # the published sizes are the frames at the default parameters
    defaults = template.validate({})
    for anchor in desc["anchors"]:
        frame = template.anchor_frame(anchor["id"], defaults)
        assert anchor["size_mm"] == pytest.approx(list(frame.size_mm), abs=0.06), anchor["id"]
    with pytest.raises(NotImplementedError):
        template.anchor_frame("lid", defaults)


@pytest.mark.parametrize(
    "template, overrides",
    [(t, {}) for t in CARRIERS] + [(t, corner) for t in CARRIERS for corner in CORNERS[t]],
    ids=lambda v: v.id if hasattr(v, "id") else "-".join(f"{x:g}" if isinstance(x, (int, float)) else str(x) for x in v.values()) or "defaults",
)
def test_defaults_and_corners_build_inside_the_envelope_with_frames_on_the_skin(template, overrides):
    params = template.validate(overrides)
    mesh = check_body(template, params)
    check_frames(template, params, mesh)
    note = template.karigar_note(params)
    assert note and "mesh" not in note.lower() and "stl" not in note.lower()


@pytest.mark.parametrize("template, anchor", SURFACES, ids=lambda v: v.id if hasattr(v, "id") else v)
@pytest.mark.parametrize("mode", ["emboss", "deboss"])
def test_a_photo_relief_on_every_surface_anchor(template, anchor, mode, content_dir):
    params = template.validate({})
    body = template.build_body(params)
    mesh = template.build(params, [relief(anchor, mode)], LocalFileFetcher(content_dir))
    assert mesh.is_watertight and mesh.is_winding_consistent
    delta = mesh.volume - body.volume
    frame = template.anchor_frame(anchor, params)
    cap = 0.6 * frame.size_mm[0] * frame.size_mm[1]
    if mode == "emboss":
        assert 0 < delta < cap
    else:
        assert -cap < delta < 0
    # still resting on the bed, even with a raised photo on the underside
    assert mesh.bounds[0][2] == pytest.approx(0.0, abs=1e-6)


@pytest.mark.parametrize("template", CARRIERS, ids=lambda t: t.id)
def test_defaults_pass_the_printability_checks(template):
    from aakar_inspect import Constraints, inspect_mesh

    params = template.validate({})
    report, estimate = inspect_mesh(template.build(params), Constraints(min_wall_mm=template.constraints.min_wall_mm))
    assert report["passed"] is True, report["checks"]
    for check in ("manifold", "fits_bed", "centre_of_gravity", "tipping_margin"):
        assert report["checks"][check]["status"] == "pass", (check, report["checks"][check])
    assert report["checks"]["thinnest_wall"]["status"] in ("pass", "warn")
    assert estimate["print_seconds"] > 0


def _request(template, params: dict, features: list) -> dict:
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
            "material": "basic_white",
        },
    }


@pytest.mark.parametrize("template", CARRIERS, ids=lambda t: t.id)
def test_pipeline_builds_each_carrier_with_a_photo_and_its_hardware(template, content_dir, local_storage):
    anchor = template.anchors[0].id
    payload = build_design(_request(template, {}, [relief(anchor)]), storage=local_storage, fetcher=LocalFileFetcher(content_dir))
    assert is_completed(payload), payload
    validate("design.completed", payload)
    assert payload["template"] == {"id": template.id, "version": 1}
    assert payload["hardware"] == HARDWARE[template]
    assert payload["spec"]["features"][0]["anchor"] == anchor and payload["spec"]["features"][0]["mode"] == "emboss"
    assert payload["printability"]["passed"] is True
    assert payload["printability"]["geometry"]["bounds_mm"][2] > 0
    assert sorted(payload["assets"]) == ["glb", "stl"]


def test_a_cut_in_photo_too_deep_for_the_plastic_fails_before_any_building(content_dir, local_storage, monkeypatch):
    def never(*args, **kwargs):  # pragma: no cover - the check must stop the job first
        raise AssertionError("built anyway")

    monkeypatch.setattr(KeychainTag, "build_body", classmethod(lambda cls, params: never()))
    request = _request(KeychainTag, {"thickness_mm": 2.4}, [relief("face", "deboss", 1.5)])
    payload = build_design(request, storage=local_storage, fetcher=LocalFileFetcher(content_dir))
    assert payload["code"] == "param_out_of_range", payload
    validate("design.failed", payload)
    assert payload["detail"]["keys"] == ["features[0].relief_mm", "thickness_mm"]
    assert "0.9 mm of plastic" in payload["message"] and "1.2 mm" in payload["message"]
