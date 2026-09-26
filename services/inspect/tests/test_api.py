import trimesh
from fastapi.testclient import TestClient

from aakar_inspect.api import app
from aakar_inspect.contracts import validate

client = TestClient(app)


def test_healthz():
    assert client.get("/healthz").status_code == 200


def test_inspect_by_path(tmp_path):
    cube = trimesh.creation.box(extents=[40, 40, 40])
    cube.apply_translation([0, 0, 20])
    path = tmp_path / "cube.stl"
    cube.export(path)
    resp = client.post("/v1/inspect", json={"mesh": {"path": str(path)}, "constraints": {"min_wall_mm": 1.2}})
    assert resp.status_code == 200, resp.text
    body = resp.json()
    validate("printability-report", body["printability"])
    validate("print-estimate", body["print_estimate"])
    assert body["printability"]["passed"] is True


def test_inspect_rejects_bad_mesh_ref(tmp_path):
    assert client.post("/v1/inspect", json={"mesh": {}}).status_code == 422
    assert client.post("/v1/inspect", json={"mesh": {"path": "/a", "url": "http://x/y.stl"}}).status_code == 422
    assert client.post("/v1/inspect", json={"mesh": {"path": str(tmp_path / "missing.stl")}}).status_code == 422
    garbage = tmp_path / "garbage.stl"
    garbage.write_bytes(b"not a mesh")
    assert client.post("/v1/inspect", json={"mesh": {"path": str(garbage)}}).status_code == 422


def test_inspect_rejects_out_of_range_settings(tmp_path):
    cube = trimesh.creation.box(extents=[10, 10, 10])
    path = tmp_path / "cube.stl"
    cube.export(path)
    resp = client.post("/v1/inspect", json={"mesh": {"path": str(path)}, "slicing": {"infill_pct": 150}})
    assert resp.status_code == 422
    assert resp.json()["code"] == "param_out_of_range"


def test_inspect_by_url(tmp_path, monkeypatch):
    import httpx

    cube = trimesh.creation.box(extents=[20, 20, 20])
    cube.apply_translation([0, 0, 10])
    payload = cube.export(file_type="stl")

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path.endswith("model.stl")
        return httpx.Response(200, content=payload)

    real_client = httpx.Client

    def fake_client(*args, **kwargs):
        kwargs["transport"] = httpx.MockTransport(handler)
        return real_client(*args, **kwargs)

    monkeypatch.setattr("aakar_inspect.loaders.httpx.Client", fake_client)
    resp = client.post("/v1/inspect", json={"mesh": {"url": "http://assets.test/designs/x/v1/model.stl"}})
    assert resp.status_code == 200, resp.text
    assert resp.json()["printability"]["geometry"]["bounds_mm"] == [20, 20, 20]
