"""``raw_print@1`` (Swaroop): the customer's own model printed as it is; size lives on the hero_mesh feature."""

from __future__ import annotations

import pytest
import trimesh

from aakar_geometry.contracts import validate
from aakar_geometry.errors import InvalidSpec, ParamOutOfRange
from aakar_geometry.features.fetch import LocalFileFetcher
from aakar_geometry.pipeline import build_design, http_status_for, is_completed
from aakar_geometry.templates import RawPrint

from carrier_checks import descriptor_matches_family
from conftest import DESIGN_ID, JOB_ID, content_source


def hero(name: str = "block.stl", **extra) -> dict:
    feature = {"type": "hero_mesh", "source": content_source(name, "stl"), "anchor": "body", "fit": "longest", "longest_mm": 80}
    feature.update(extra)
    return feature


@pytest.fixture
def models(content_dir):
    """block.stl (30 × 20 × 10) and thin.stl (a 40 × 40 × 0.6 sheet) next to the conftest content."""
    (content_dir / "block.stl").write_bytes(trimesh.creation.box(extents=[30, 20, 10]).export(file_type="stl"))
    (content_dir / "thin.stl").write_bytes(trimesh.creation.box(extents=[40, 40, 0.6]).export(file_type="stl"))
    return content_dir


def _request(features: list, params: dict | None = None) -> dict:
    return {
        "job_id": JOB_ID,
        "design_id": DESIGN_ID,
        "version_no": 1,
        "outputs": ["glb", "stl"],
        "spec": {
            "spec_version": "1.0",
            "family": "raw_print",
            "template": "raw_print@1",
            "params": params or {},
            "features": features,
            "material": "basic_white",
        },
    }


def test_descriptor_has_no_params_and_one_volume_anchor():
    desc = descriptor_matches_family(RawPrint)
    assert desc["params"] == {}
    assert desc["anchors"] == [
        {"id": "body", "label": "Your model", "kind": "volume", "projection": "planar", "bounds_mm": [240, 240, 240], "accepts": ["hero_mesh"]},
    ]
    assert desc["features_supported"] == ["hero_mesh"] and desc["hardware"] == []
    assert RawPrint.hardware_for({}) == []
    assert RawPrint.build_body({}).is_empty


@pytest.mark.parametrize("longest", [20, 80, 240])
def test_a_block_is_printed_at_the_longest_side_asked_for_seated_on_the_bed(models, longest):
    mesh = RawPrint.build({}, [hero(longest_mm=longest)], LocalFileFetcher(models))
    assert mesh.is_watertight
    assert mesh.extents == pytest.approx([longest, longest * 20 / 30, longest * 10 / 30], abs=1e-6)
    assert mesh.bounds[0][2] == pytest.approx(0.0, abs=1e-9)
    assert (mesh.bounds[0][:2] + mesh.bounds[1][:2]) == pytest.approx([0.0, 0.0], abs=1e-9)


def test_a_sphere_lies_flat_and_keeps_its_size(models):
    mesh = RawPrint.build({}, [hero("sphere.stl", longest_mm=60, orientation="lay_flat")], LocalFileFetcher(models))
    assert max(mesh.extents) == pytest.approx(60, abs=1e-6) and mesh.bounds[0][2] == pytest.approx(0.0, abs=1e-9)


def test_without_its_model_file_a_raw_print_is_refused(models):
    for features in ([], None):
        with pytest.raises(InvalidSpec) as exc:
            RawPrint.build({}, features or [], LocalFileFetcher(models))
        assert exc.value.message == "Add your model file to print it as it is"


@pytest.mark.parametrize("longest", [10, 19.9, 240.1, 250])
def test_sizes_outside_the_family_envelope_are_refused_never_clamped(models, longest):
    with pytest.raises(ParamOutOfRange) as exc:
        RawPrint.build({}, [hero(longest_mm=longest)], LocalFileFetcher(models))
    assert exc.value.keys == ["features[0].longest_mm"]
    assert "20–240 mm" in exc.value.message


def test_the_size_must_be_chosen(models):
    with pytest.raises(InvalidSpec) as exc:
        RawPrint.build({}, [hero(fit="contain", longest_mm=None)], LocalFileFetcher(models))
    assert "longest side" in exc.value.message
    with pytest.raises(InvalidSpec):  # fit: longest without a length is refused by the features check
        RawPrint.build({}, [{k: v for k, v in hero().items() if k != "longest_mm"}], LocalFileFetcher(models))


def test_pipeline_completes_a_raw_print(models, local_storage):
    payload = build_design(_request([hero(longest_mm=90)]), storage=local_storage, fetcher=LocalFileFetcher(models))
    assert is_completed(payload), payload
    validate("design.completed", payload)
    assert payload["template"] == {"id": "raw_print", "version": 1}
    assert payload["hardware"] == []
    assert payload["spec"]["params"] == {} and payload["spec"]["features"][0]["orientation"] == "as_uploaded"
    assert payload["printability"]["geometry"]["bounds_mm"] == pytest.approx([90, 60, 30], abs=1e-3)
    assert payload["printability"]["passed"] is True
    assert "printed as it is" in payload["karigar_note"].lower()


def test_a_thin_walled_model_completes_but_is_not_passed(models, local_storage):
    """The product rule: a model too thin to print well is shown with its checks, not refused."""
    payload = build_design(_request([hero("thin.stl", longest_mm=40)]), storage=local_storage, fetcher=LocalFileFetcher(models))
    assert is_completed(payload), payload
    validate("design.completed", payload)
    assert payload["printability"]["passed"] is False
    assert payload["printability"]["checks"]["thinnest_wall"]["status"] == "fail"
    assert payload["printability"]["checks"]["thinnest_wall"]["value"] == pytest.approx(0.6, abs=0.05)


@pytest.mark.parametrize(
    "features, params, code, needle",
    [
        ([], None, "invalid_spec", "Add your model file"),
        ([hero(longest_mm=10)], None, "param_out_of_range", "20–240 mm"),
        ([hero(longest_mm=245)], None, "param_out_of_range", "20–240 mm"),
        ([hero(fit="contain")], None, "invalid_spec", "longest side"),
        ([{"type": "relief_image", "source": content_source("photo.png", "png"), "anchor": "body"}], None, "unsupported_feature", "can't carry"),
        ([hero(), hero()], None, "invalid_spec", "Only one"),
        ([hero()], {"longest_mm": 80}, "invalid_spec", "Unknown parameter"),  # size lives on the feature
        ([hero("garbage.stl")], None, "content_unusable", "model file"),
    ],
)
def test_raw_print_failures(models, local_storage, features, params, code, needle):
    payload = build_design(_request(features, params), storage=local_storage, fetcher=LocalFileFetcher(models))
    assert not is_completed(payload)
    validate("design.failed", payload)
    assert payload["code"] == code, payload
    assert needle in payload["message"]
    assert http_status_for(payload) == 422
