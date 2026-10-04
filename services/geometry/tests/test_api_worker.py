import json

import pytest
from fastapi.testclient import TestClient

from aakar_geometry.api import app
from aakar_geometry.contracts import validate
from aakar_geometry.events import EXCHANGE, new_envelope
from aakar_geometry.worker import QUEUE, ROUTING_KEY, declare_topology, handle_message

from conftest import DESIGN_ID, JOB_ID


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("AAKAR_STORAGE", "local")
    monkeypatch.setenv("AAKAR_ASSET_DIR", str(tmp_path / "assets"))
    monkeypatch.setenv("AAKAR_PUBLIC_URL", "http://localhost:8081")
    monkeypatch.delenv("AAKAR_INSPECT_URL", raising=False)
    return TestClient(app)


def test_healthz(client):
    body = client.get("/healthz").json()
    assert body["status"] == "ok" and body["cad_kernel"] == "build123d"


def test_templates_endpoints(client):
    resp = client.get("/v1/templates")
    assert resp.status_code == 200
    descriptors = resp.json()
    assert [d["id"] for d in descriptors] == [
        "desk_nameplate", "fridge_magnet", "hanging_ornament", "headphone_topper", "jharokha_phone_stand", "keycap_mx",
        "keychain_tag", "lithophane_plate", "pet_tag", "photo_frame_std", "plinth_round", "raw_print",
    ]
    for d in descriptors:
        validate("template-descriptor", d)
    assert client.get("/v1/templates/jharokha_phone_stand").json()["version"] == 1
    assert client.get("/v1/templates/jharokha_phone_stand@1").json()["id"] == "jharokha_phone_stand"
    assert client.get("/v1/templates/jharokha_phone_stand@9").status_code == 404
    assert client.get("/v1/templates/lotus_lamp").status_code == 404


def test_build_endpoint_and_assets(client, generate_request):
    resp = client.post("/v1/build", json=generate_request)
    assert resp.status_code == 200, resp.text
    payload = resp.json()
    validate("design.completed", payload)
    for kind, rec in payload["assets"].items():
        asset = client.get(f"/assets/{rec['key']}")
        assert asset.status_code == 200
        assert asset.headers["content-type"].startswith(rec["content_type"])
        assert len(asset.content) == rec["bytes"]
    assert client.get("/assets/designs/nope/v1/model.glb").status_code == 404
    assert client.get("/assets/../../etc/passwd").status_code in (404, 422)


def test_build_endpoint_failures(client, generate_request):
    generate_request["spec"]["params"]["height_mm"] = 10
    resp = client.post("/v1/build", json=generate_request)
    assert resp.status_code == 422
    validate("design.failed", resp.json())
    assert resp.json()["code"] == "param_out_of_range"
    assert client.post("/v1/build", content=b"{not json").status_code == 422
    assert client.post("/v1/build", json=[1, 2]).json()["code"] == "invalid_spec"


class FakeChannel:
    def __init__(self):
        self.published = []
        self.acked = []
        self.nacked = []
        self.declared = []

    def basic_publish(self, exchange, routing_key, body, properties=None):
        self.published.append((exchange, routing_key, json.loads(body)))

    def basic_ack(self, delivery_tag):
        self.acked.append(delivery_tag)

    def basic_nack(self, delivery_tag, requeue):
        self.nacked.append((delivery_tag, requeue))

    def exchange_declare(self, exchange, exchange_type, durable):
        self.declared.append(("exchange", exchange, exchange_type, durable))

    def queue_declare(self, queue, durable):
        self.declared.append(("queue", queue, durable))

    def queue_bind(self, queue, exchange, routing_key):
        self.declared.append(("bind", queue, exchange, routing_key))


class Method:
    delivery_tag = 7


def test_declare_topology():
    ch = FakeChannel()
    declare_topology(ch)
    assert ("exchange", "aakar.design", "topic", True) in ch.declared
    assert ("queue", "geometry.design.generate", True) in ch.declared
    assert ("bind", QUEUE, EXCHANGE, ROUTING_KEY) in ch.declared


def test_worker_handles_generate_message(generate_request, local_storage):
    ch = FakeChannel()
    envelope = new_envelope("design.generate", JOB_ID, DESIGN_ID, generate_request)
    result = handle_message(
        ch, Method(), None, json.dumps(envelope).encode(),
        build=lambda payload, sink: __import__("aakar_geometry.pipeline", fromlist=["build_design"]).build_design(payload, sink=sink, storage=local_storage),
    )
    assert result is not None and "assets" in result
    assert ch.acked == [7] and ch.nacked == []
    types = [rk for _, rk, _ in ch.published]
    assert types == ["design.progress", "design.progress", "design.progress", "design.completed"]
    assert all(ex == EXCHANGE for ex, _, _ in ch.published)
    for _, rk, env in ch.published:
        validate("envelope", env)
        assert env["type"] == rk and env["job_id"] == JOB_ID
    assert [env["sequence"] for _, _, env in ch.published] == [0, 1, 2, 3]
    validate("design.completed", ch.published[-1][2]["payload"])


def test_worker_publishes_failed_for_bad_spec(generate_request):
    ch = FakeChannel()
    generate_request["spec"]["template"] = "lotus_lamp@2"
    envelope = new_envelope("design.generate", JOB_ID, DESIGN_ID, generate_request)
    handle_message(ch, Method(), None, json.dumps(envelope).encode())
    assert ch.acked == [7]
    assert ch.published[-1][1] == "design.failed"
    assert ch.published[-1][2]["payload"]["code"] == "unknown_template"


def test_worker_nacks_invalid_messages():
    for body in (b"not json", b"[1]", json.dumps({"type": "design.generate"}).encode(),
                 json.dumps(new_envelope("design.progress", JOB_ID, DESIGN_ID, {"stage": "ready", "message": "x"})).encode()):
        ch = FakeChannel()
        assert handle_message(ch, Method(), None, body) is None
        assert ch.nacked == [(7, False)] and ch.acked == [] and ch.published == []


def test_worker_survives_raising_builder(generate_request):
    ch = FakeChannel()
    envelope = new_envelope("design.generate", JOB_ID, DESIGN_ID, generate_request)

    def broken(payload, sink):
        raise RuntimeError("boom")

    result = handle_message(ch, Method(), None, json.dumps(envelope).encode(), build=broken)
    assert result["code"] == "build_error"
    assert ch.published[-1][1] == "design.failed" and ch.acked == [7]
