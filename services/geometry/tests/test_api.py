"""HTTP-level checks for the geometry API that need no built geometry."""

from fastapi.testclient import TestClient

from aakar_geometry.api import app


def test_assets_route_sends_cors_headers_for_storefront_origin():
    """The storefront (localhost:3000) loads GLBs straight from /assets, so CORS must allow it."""
    with TestClient(app) as client:
        r = client.options(
            "/assets/designs/x/v1/model.glb",
            headers={"Origin": "http://localhost:3000", "Access-Control-Request-Method": "GET"},
        )
        assert r.status_code == 200
        assert r.headers.get("access-control-allow-origin") == "http://localhost:3000"


def test_assets_route_rejects_unknown_origin():
    with TestClient(app) as client:
        r = client.options(
            "/assets/designs/x/v1/model.glb",
            headers={"Origin": "http://evil.example", "Access-Control-Request-Method": "GET"},
        )
        assert r.headers.get("access-control-allow-origin") is None
