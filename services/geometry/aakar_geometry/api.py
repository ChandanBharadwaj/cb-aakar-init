"""FastAPI surface (packages/contracts/openapi/geometry.v1.yaml). Port 8081."""

from __future__ import annotations

import logging
import mimetypes
from typing import Any

from fastapi import FastAPI, Request
from fastapi.responses import FileResponse, JSONResponse

from .exports import CONTENT_TYPES
from .pipeline import build_design, http_status_for
from .storage import LocalStorage, storage_from_env
from .templates import get_template, latest_by_id, list_templates
from .errors import UnknownTemplate

log = logging.getLogger("aakar.geometry.api")

app = FastAPI(title="Aakar Geometry Service", version="1.0.0-phase0")


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
