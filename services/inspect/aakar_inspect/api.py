"""FastAPI surface for the inspect service (packages/contracts/openapi/inspect.v1.yaml). Port 8082."""

from __future__ import annotations

import logging
from typing import Any, Literal

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field

from .core import inspect_mesh
from .loaders import MeshLoadError, load_mesh
from .settings import Constraints, InvalidSettings, SlicingSettings

log = logging.getLogger("aakar.inspect.api")


class MeshRef(BaseModel):
    model_config = ConfigDict(extra="forbid")
    url: str | None = None
    path: str | None = None
    format: Literal["stl", "glb", "3mf", "obj"] | None = None


class ConstraintsIn(BaseModel):
    model_config = ConfigDict(extra="forbid")
    min_wall_mm: float = 1.2
    max_overhang_deg: float = 55
    bed_mm: list[float] = Field(default_factory=lambda: [250.0, 250.0, 250.0], min_length=3, max_length=3)
    min_tipping_margin_mm: float = 5


class SlicingIn(BaseModel):
    model_config = ConfigDict(extra="forbid")
    layer_height_mm: float = 0.2
    infill_pct: float = 15
    nozzle_mm: float = 0.4
    walls: int = 3


class InspectRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    mesh: MeshRef
    constraints: ConstraintsIn = Field(default_factory=ConstraintsIn)
    slicing: SlicingIn = Field(default_factory=SlicingIn)


app = FastAPI(title="Aakar Inspect Service", version="1.0.0-phase0")


@app.get("/healthz")
def healthz() -> dict[str, str]:
    return {"status": "ok", "service": "aakar-inspect"}


@app.post("/v1/inspect")
def inspect(req: InspectRequest) -> Any:
    if bool(req.mesh.url) == bool(req.mesh.path):
        return JSONResponse(status_code=422, content={"error": "mesh needs exactly one of url or path"})
    try:
        constraints = Constraints.from_dict(req.constraints.model_dump())
        slicing = SlicingSettings.from_dict(req.slicing.model_dump())
    except InvalidSettings as exc:
        return JSONResponse(status_code=422, content={"error": str(exc), "keys": exc.keys, "code": "param_out_of_range"})
    try:
        mesh = load_mesh(path=req.mesh.path, url=req.mesh.url, file_type=req.mesh.format)
    except MeshLoadError as exc:
        return JSONResponse(status_code=422, content={"error": str(exc), "code": "mesh_load_failed"})
    report, estimate = inspect_mesh(mesh, constraints, slicing)
    return {"printability": report, "print_estimate": estimate}
