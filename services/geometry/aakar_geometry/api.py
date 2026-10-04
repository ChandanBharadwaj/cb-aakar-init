"""FastAPI surface (packages/contracts/openapi/geometry.v1.yaml). Port 8081."""

from __future__ import annotations

import logging
import os
import mimetypes
from typing import Any

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse

from .errors import BuildError, InvalidSpec, UnknownTemplate
from .exports import CONTENT_TYPES
from .pipeline import build_design, failed_payload, http_status_for
from .storage import LocalStorage, storage_from_env
from .templates import get_template, latest_by_id, list_templates

log = logging.getLogger("aakar.geometry.api")

app = FastAPI(title="Aakar Geometry Service", version="1.0.0-phase0")

# Browsers load GLBs straight from /assets when AAKAR_STORAGE=local, so the storefront origin must be allowed; the
# portal (localhost:3100) fetches base previews and coupon STLs the same way. Comma-separated AAKAR_CORS_ORIGINS
# overrides the localhost default.
DEFAULT_CORS_ORIGINS = "http://localhost:3000,http://localhost:3100"
app.add_middleware(
    CORSMiddleware,
    allow_origins=[o.strip() for o in os.environ.get("AAKAR_CORS_ORIGINS", DEFAULT_CORS_ORIGINS).split(",") if o.strip()],
    allow_methods=["GET", "HEAD", "OPTIONS", "POST"],
    allow_headers=["*"],
    max_age=3600,
)


@app.get("/healthz")
def healthz() -> dict[str, str]:
    from .cad import KERNEL

    return {"status": "ok", "service": "aakar-geometry", "cad_kernel": KERNEL}


@app.get("/v1/templates")
def templates() -> list[dict[str, Any]]:
    return [t.descriptor() for t in list_templates()]


@app.get("/v1/templates/{template_id}")
def template(template_id: str) -> Any:
    if "@" in template_id:
        try:
            return get_template(template_id).descriptor()
        except UnknownTemplate as exc:
            return JSONResponse(status_code=404, content={"code": exc.code, "message": exc.message})
    found = latest_by_id(template_id)
    if found is None:
        return JSONResponse(status_code=404, content={"code": "unknown_template", "message": f"Unknown template {template_id}"})
    return found.descriptor()


@app.post("/v1/build")
async def build(request: Request) -> Any:
    try:
        body = await request.json()
    except Exception:
        body = {}
    if not isinstance(body, dict):
        body = {}
    import anyio

    payload = await anyio.to_thread.run_sync(build_design, body)
    return JSONResponse(status_code=http_status_for(payload), content=payload)


def _failed(exc: BuildError) -> JSONResponse:
    return JSONResponse(status_code=exc.http_status, content=failed_payload(None, None, exc.code, exc.message, exc.detail))


def coupon_payload(body: dict[str, Any]) -> tuple[int, dict[str, Any]]:
    """``POST /v1/coupons``: build a fit coupon for a connector in a finish, store its STL under
    ``coupons/<name>.stl`` and return the asset with the numbers the bench measures against."""
    from .connectors import Connector
    from .connectors.coupon import DEFAULT_WALL_MM, build_coupon
    from .exports import export_stl

    try:
        try:
            connector = Connector.from_dict(body.get("connector"))
        except (ValueError, TypeError, KeyError) as exc:
            raise InvalidSpec(f"Not a Kadi connector: {exc}", {"connector": body.get("connector")}) from exc
        material = body.get("material")
        if not isinstance(material, str) or not material:
            raise InvalidSpec("material is required", {"material": material})
        wall = body.get("wall_mm", DEFAULT_WALL_MM)
        try:
            wall = float(wall)
        except (TypeError, ValueError):
            raise InvalidSpec("wall_mm must be a number", {"wall_mm": wall}) from None
        base = body.get("base") if isinstance(body.get("base"), dict) else None
        coupon = build_coupon(connector, material, wall, base)
        storage = storage_from_env()
        record = storage.put(f"coupons/{coupon.name}.stl", export_stl(coupon.mesh), CONTENT_TYPES["stl"])
        return 200, {"asset": record.to_dict(), **coupon.summary()}
    except BuildError as exc:
        return exc.http_status, failed_payload(None, None, exc.code, exc.message, exc.detail)
    except Exception as exc:  # pragma: no cover - last line of defence
        log.exception("coupon build failed")
        return 500, failed_payload(None, None, "build_error", "The coupon could not be built", {"error": f"{type(exc).__name__}: {exc}"})


def base_preview_payload(body: dict[str, Any]) -> tuple[int, dict[str, Any]]:
    """``POST /v1/bases/preview``: a schematic GLB of a bought-in base from its preview shape, with the mount frame."""
    import re

    from .bases import render_preview

    try:
        sku = body.get("sku")
        if not isinstance(sku, str) or not re.fullmatch(r"[a-z][a-z0-9_]*", sku):
            raise InvalidSpec("sku must be a snake_case id", {"sku": sku})
        shape = body.get("shape")
        if not isinstance(shape, str):
            raise InvalidSpec("shape is required", {"shape": shape})
        dims = body.get("dims") if isinstance(body.get("dims"), dict) else {}
        return 200, render_preview(sku, shape, dims, storage_from_env())
    except BuildError as exc:
        return exc.http_status, failed_payload(None, None, exc.code, exc.message, exc.detail)
    except Exception as exc:  # pragma: no cover
        log.exception("base preview failed")
        return 500, failed_payload(None, None, "build_error", "The base preview could not be built", {"error": f"{type(exc).__name__}: {exc}"})


async def _json_body(request: Request) -> dict[str, Any]:
    try:
        body = await request.json()
    except Exception:
        body = {}
    return body if isinstance(body, dict) else {}


@app.post("/v1/coupons")
async def coupons(request: Request) -> Any:
    import anyio

    status, payload = await anyio.to_thread.run_sync(coupon_payload, await _json_body(request))
    return JSONResponse(status_code=status, content=payload)


@app.post("/v1/bases/preview")
async def bases_preview(request: Request) -> Any:
    import anyio

    status, payload = await anyio.to_thread.run_sync(base_preview_payload, await _json_body(request))
    return JSONResponse(status_code=status, content=payload)


@app.get("/assets/{path:path}")
def asset(path: str) -> Any:
    storage = storage_from_env()
    if not isinstance(storage, LocalStorage):
        return JSONResponse(status_code=404, content={"message": "assets are served from object storage, not this service"})
    try:
        file = storage.path_for(path)
    except Exception:
        return JSONResponse(status_code=404, content={"message": "not found"})
    if not file.is_file():
        return JSONResponse(status_code=404, content={"message": "not found"})
    media = CONTENT_TYPES.get(file.suffix.lstrip(".").lower()) or mimetypes.guess_type(str(file))[0] or "application/octet-stream"
    return FileResponse(file, media_type=media)
