import json

import httpx
import pytest
import trimesh

from aakar_geometry.contracts import validate
from aakar_geometry.events import CallbackSink, CollectingSink
from aakar_geometry.inspection import HttpInspector, InProcessInspector, inspector_from_env
from aakar_geometry.pipeline import build_design, http_status_for, is_completed

from conftest import DESIGN_ID, JOB_ID


def test_build_design_completed_payload(generate_request, local_storage, tmp_path):
    sink = CollectingSink(JOB_ID, DESIGN_ID)
    payload = build_design(generate_request, sink=sink, storage=local_storage)
    assert is_completed(payload), payload
    validate("design.completed", payload)
    assert http_status_for(payload) == 200
    assert payload["template"] == {"id": "jharokha_phone_stand", "version": 1}
    assert payload["spec"]["params"]["arch_cusps"] == 5
    assert payload["spec"]["constraints"] == {"min_wall_mm": 1.2, "max_overhang_deg": 55, "bed_mm": [250, 250, 250]}
    assert payload["spec"]["material"] == "terracotta_silk"
    assert sorted(payload["assets"]) == ["3mf", "glb", "stl"]
    for kind, rec in payload["assets"].items():
        assert rec["key"] == f"designs/{DESIGN_ID}/v1/model.{kind}"
        assert rec["url"] == f"http://localhost:8081/assets/{rec['key']}"
        file = local_storage.path_for(rec["key"])
        assert file.is_file() and file.stat().st_size == rec["bytes"] > 0
    assert payload["printability"]["passed"] is True
    assert payload["print_estimate"]["method"] == "heuristic"
    assert payload["karigar_note"].startswith("A jharokha-arch phone stand")
    assert payload["build_ms"] > 0
    # progress: understanding -> sculpting -> checking, increasing sequence
    assert [e["payload"]["stage"] for e in sink.envelopes] == ["understanding", "sculpting", "checking"]
    assert [e["sequence"] for e in sink.envelopes] == [0, 1, 2]
    for env in sink.envelopes:
        validate("envelope", env)
        validate("design.progress", env["payload"])


def test_glb_asset_reloads_y_up_metres(generate_request, local_storage):
    payload = build_design(generate_request, storage=local_storage)
    scene = trimesh.load(local_storage.path_for(payload["assets"]["glb"]["key"]))
    assert scene.extents[1] == pytest.approx(0.120, abs=0.001)  # height along Y
    assert scene.extents[0] == pytest.approx(0.092, abs=0.001)
    assert scene.extents[2] == pytest.approx(0.078, abs=0.001)


@pytest.mark.parametrize(
    "mutate, code",
    [
        (lambda s: s["params"].update(width_mm=500), "param_out_of_range"),
        (lambda s: s["params"].update(arch_cusps=2.5), "param_out_of_range"),
        (lambda s: s.update(template="lotus_lamp@2"), "unknown_template"),
        (lambda s: s.update(features=[{"type": "emboss_text", "text": "hi", "anchor": "back"}]), "unsupported_feature"),
        (lambda s: s.update(features=[{"type": "motif", "motif_id": "warli_dancers_01", "anchor": "back"}]), "unsupported_feature"),
        (lambda s: s.update(style="jaipur_heritage"), "unsupported_feature"),
        (lambda s: s.update(family="table_lamp"), "invalid_spec"),
        (lambda s: s.update(material="gold_leaf"), "invalid_spec"),
        (lambda s: s.update(spec_version="2.0"), "invalid_spec"),
        (lambda s: s["params"].update(petal_count=8), "invalid_spec"),
    ],
)
def test_build_design_failure_codes(generate_request, local_storage, mutate, code):
    mutate(generate_request["spec"])
    payload = build_design(generate_request, storage=local_storage)
    assert not is_completed(payload)
    validate("design.failed", payload)
    assert payload["code"] == code
    assert payload["job_id"] == JOB_ID and payload["design_id"] == DESIGN_ID
    assert http_status_for(payload) == 422


def test_param_out_of_range_lists_keys(generate_request, local_storage):
    generate_request["spec"]["params"].update(width_mm=10, tilt_deg=90)
    payload = build_design(generate_request, storage=local_storage)
    assert payload["code"] == "param_out_of_range"
    assert payload["detail"]["keys"] == ["width_mm", "tilt_deg"]


def test_invalid_request_shape_still_yields_failed_payload(local_storage):
    payload = build_design({"garbage": True}, storage=local_storage)
    validate("design.failed", payload)
    assert payload["code"] == "invalid_spec"
    assert payload["job_id"] == "00000000-0000-0000-0000-000000000000"


def test_storage_error_code(generate_request, tmp_path):
    from aakar_geometry.errors import StorageError

    class Broken:
        kind = "local"

        def put(self, key, data, content_type):
            raise StorageError("disk full", {"key": key})

        def url_for(self, key):
            return key

    payload = build_design(generate_request, storage=Broken())
    assert payload["code"] == "storage_error" and http_status_for(payload) == 500


def test_build_error_and_timeout_codes(generate_request, local_storage, monkeypatch):
    from aakar_geometry.templates import JharokhaPhoneStand

    def boom(params):
        raise RuntimeError("kernel exploded")

    monkeypatch.setattr(JharokhaPhoneStand, "build", staticmethod(boom))
    payload = build_design(generate_request, storage=local_storage)
    assert payload["code"] == "build_error" and "kernel exploded" in payload["detail"]["error"]

    import time

    def slow(params):
        time.sleep(0.5)
        return trimesh.creation.box()

    monkeypatch.setattr(JharokhaPhoneStand, "build", staticmethod(slow))
    payload = build_design(generate_request, storage=local_storage, build_timeout_s=0.05)
    assert payload["code"] == "timeout"


def test_not_printable_when_template_yields_open_mesh(generate_request, local_storage, monkeypatch):
    from aakar_geometry.templates import JharokhaPhoneStand

    def open_mesh(params):
        sphere = trimesh.creation.icosphere(subdivisions=2, radius=20)
        sphere.apply_translation([0, 0, 20])
        sphere.update_faces(sphere.triangles_center[:, 2] < 30)
        return sphere

    monkeypatch.setattr(JharokhaPhoneStand, "build", staticmethod(open_mesh))
    payload = build_design(generate_request, storage=local_storage)
    assert payload["code"] == "not_printable"


def test_outputs_filter(generate_request, local_storage):
    generate_request["outputs"] = ["glb", "usdz", "thumb"]
    payload = build_design(generate_request, storage=local_storage)
    assert sorted(payload["assets"]) == ["glb"]


def test_callback_sink_posts_progress_and_survives_failures(generate_request, local_storage):
    received = []

    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        received.append(body)
        if body["sequence"] == 1:
            return httpx.Response(500)
        return httpx.Response(204)

    client = httpx.Client(transport=httpx.MockTransport(handler))
    sink = CallbackSink("http://api.test/callbacks/x", JOB_ID, DESIGN_ID, client=client)
    payload = build_design(generate_request, sink=sink, storage=local_storage)
    assert is_completed(payload)
    assert [b["payload"]["stage"] for b in received] == ["understanding", "sculpting", "checking"]
    assert [b["sequence"] for b in received] == [0, 1, 2]
    assert all(b["type"] == "design.progress" and b["job_id"] == JOB_ID for b in received)

    def dead(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("refused")

    sink2 = CallbackSink("http://api.test/callbacks/x", JOB_ID, DESIGN_ID, client=httpx.Client(transport=httpx.MockTransport(dead)))
    assert is_completed(build_design(generate_request, sink=sink2, storage=local_storage))
    assert len(sink2.envelopes) == 3


def test_callback_url_in_request_creates_sink(generate_request, local_storage, monkeypatch):
    posted = []
    monkeypatch.setattr("aakar_geometry.events.httpx.post", lambda url, **kw: posted.append((url, json.loads(kw["content"]))) or httpx.Response(200))
    generate_request["callback_url"] = "http://api.test/cb"
    assert is_completed(build_design(generate_request, storage=local_storage))
    assert [p[1]["payload"]["stage"] for p in posted] == ["understanding", "sculpting", "checking"]
    assert all(p[0] == "http://api.test/cb" for p in posted)


def test_http_inspector_against_inspect_app(generate_request, local_storage, monkeypatch):
    from starlette.testclient import TestClient

    from aakar_inspect.api import app as inspect_app

    inspector = HttpInspector("http://testserver", client=TestClient(inspect_app))
    generate_request["outputs"] = ["glb"]  # STL is added automatically for HTTP inspection
    payload = build_design(generate_request, storage=local_storage, inspector=inspector)
    assert is_completed(payload), payload
    assert sorted(payload["assets"]) == ["glb", "stl"]
    assert payload["printability"]["passed"] is True


def test_inspector_from_env():
    assert isinstance(inspector_from_env({}), InProcessInspector)
    http = inspector_from_env({"AAKAR_INSPECT_URL": "http://inspect:8082/"})
    assert isinstance(http, HttpInspector) and http.base_url == "http://inspect:8082"


# --------------------------------------------------------------------------- Chhaap: content features through the pipeline

from aakar_geometry.features.fetch import LocalFileFetcher  # noqa: E402

from conftest import content_source  # noqa: E402


def _request(template_ref: str, family: str, params: dict, features: list, material: str = "basic_white") -> dict:
    return {
        "job_id": JOB_ID,
        "design_id": DESIGN_ID,
        "version_no": 2,
        "outputs": ["glb", "stl"],
        "spec": {"spec_version": "1.0", "family": family, "template": template_ref, "params": params, "features": features, "material": material},
    }


def test_jharokha_example_is_unchanged_by_the_features_stage(generate_request, local_storage, built_example):
    payload = build_design(generate_request, storage=local_storage)
    assert is_completed(payload)
    validate("design.completed", payload)
    assert payload["hardware"] == []
    assert payload["spec"]["features"] == []
    _, _, mesh = built_example
    assert payload["printability"]["geometry"]["bounds_mm"] == pytest.approx(list(mesh.extents), abs=1e-3)
    assert payload["printability"]["geometry"]["bounds_mm"] == pytest.approx([92, 78, 120], abs=1.0)


def test_relief_image_spec_completes_with_hardware(test_templates, content_dir, local_storage):
    Plate, _, _ = test_templates
    feature = {"type": "relief_image", "source": content_source("photo.png", "png"), "anchor": "face", "relief_mm": 0.8}
    request = _request("test_plate@1", "keychain", {"thickness_mm": 3}, [feature], material="indigo_matte")
    payload = build_design(request, storage=local_storage, fetcher=LocalFileFetcher(content_dir))
    assert is_completed(payload), payload
    validate("design.completed", payload)
    assert payload["hardware"] == [{"sku": "split_ring_25", "qty": 1}]
    assert payload["template"] == {"id": "test_plate", "version": 1}
    normalised = payload["spec"]["features"][0]
    assert normalised["mode"] == "emboss" and normalised["fit"] == "contain" and normalised["relief_mm"] == 0.8 and normalised["cutout"] == "none"
    geometry = payload["printability"]["geometry"]
    assert geometry["bounds_mm"] == pytest.approx([40, 30, 3.8], abs=1e-3)
    assert geometry["volume_cm3"] > 40 * 30 * 3 / 1000
    assert payload["printability"]["checks"]["manifold"]["status"] == "pass"
    assert sorted(payload["assets"]) == ["glb", "stl"]

    deboss = dict(feature, mode="deboss")
    payload = build_design(_request("test_plate@1", "keychain", {"thickness_mm": 3}, [deboss]), storage=local_storage, fetcher=LocalFileFetcher(content_dir))
    assert is_completed(payload) and payload["printability"]["geometry"]["volume_cm3"] < 40 * 30 * 3 / 1000


def test_hero_mesh_spec_completes_on_a_volume_anchor_and_as_raw_print(test_templates, content_dir, local_storage):
    fetcher = LocalFileFetcher(content_dir)
    hero = {"type": "hero_mesh", "source": content_source("sphere.stl", "stl"), "anchor": "top"}
    payload = build_design(_request("test_plinth@1", "figurine_base", {}, [hero]), storage=local_storage, fetcher=fetcher)
    assert is_completed(payload), payload
    validate("design.completed", payload)
    assert payload["hardware"] == []
    assert payload["printability"]["geometry"]["bounds_mm"] == pytest.approx([50, 50, 49.5], abs=1e-3)
    assert payload["spec"]["features"][0]["orientation"] == "as_uploaded"

    raw = {"type": "hero_mesh", "source": content_source("sphere.stl"), "anchor": "body", "fit": "longest", "longest_mm": 80, "orientation": "lay_flat"}
    payload = build_design(_request("test_raw@1", "raw_print", {"longest_mm": 80}, [raw]), storage=local_storage, fetcher=fetcher)
    assert is_completed(payload), payload
    assert payload["printability"]["geometry"]["bounds_mm"] == pytest.approx([80, 80, 80], abs=1e-3)
    assert payload["printability"]["passed"] is True


@pytest.mark.parametrize(
    "template_ref, family, params, feature, code",
    [
        ("test_plinth@1", "figurine_base", {}, {"type": "hero_mesh", "source": content_source("soup.stl", "stl"), "anchor": "top"}, "content_unusable"),
        ("test_plinth@1", "figurine_base", {}, {"type": "hero_mesh", "source": content_source("open.stl", "stl"), "anchor": "top"}, "content_unusable"),
        ("test_plate@1", "keychain", {}, {"type": "relief_image", "source": content_source("flat.png", "png"), "anchor": "face"}, "content_unusable"),
        ("test_plate@1", "keychain", {}, {"type": "relief_image", "source": content_source("notes.txt"), "anchor": "face"}, "content_unusable"),
        ("test_plate@1", "keychain", {}, {"type": "relief_image", "source": content_source("missing.png", "png"), "anchor": "face"}, "content_unusable"),
        ("test_plate@1", "keychain", {}, {"type": "relief_image", "source": content_source("photo.png", "png"), "anchor": "lid"}, "invalid_spec"),
        ("test_plate@1", "keychain", {}, {"type": "relief_image", "source": content_source("photo.png", "png"), "anchor": "face", "relief_mm": 2.5}, "param_out_of_range"),
        ("test_plate@1", "keychain", {}, {"type": "hero_mesh", "source": content_source("sphere.stl", "stl"), "anchor": "face"}, "unsupported_feature"),
        ("test_plinth@1", "figurine_base", {}, {"type": "hero_mesh", "source": content_source("sphere.stl", "stl"), "anchor": "top", "fit": "longest", "longest_mm": 90}, "param_out_of_range"),
    ],
)
def test_content_failures_surface_as_design_failed(test_templates, content_dir, local_storage, template_ref, family, params, feature, code):
    payload = build_design(_request(template_ref, family, params, [feature]), storage=local_storage, fetcher=LocalFileFetcher(content_dir))
    assert not is_completed(payload)
    validate("design.failed", payload)
    assert payload["code"] == code, payload
    assert http_status_for(payload) == 422
    for word in ("mesh", "STL", "stl"):
        assert word not in payload["message"], payload["message"]
