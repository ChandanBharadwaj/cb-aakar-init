"""How the pipeline reaches the inspect service: in-process (default) or HTTP (``AAKAR_INSPECT_URL``)."""

from __future__ import annotations

import logging
import os
from typing import Any, Mapping, Protocol

import httpx
import trimesh

from .errors import GeometryError
from .storage import AssetRecord, LocalStorage, Storage

log = logging.getLogger("aakar.geometry.inspection")


class Inspector(Protocol):
    mode: str

    def inspect(
        self,
        mesh: trimesh.Trimesh,
        constraints: Mapping[str, Any],
        slicing: Mapping[str, Any],
        assets: Mapping[str, AssetRecord],
        storage: Storage,
    ) -> tuple[dict[str, Any], dict[str, Any]]: ...


class InProcessInspector:
    mode = "in-process"

    def inspect(self, mesh, constraints, slicing, assets, storage):
        from aakar_inspect import Constraints, SlicingSettings, inspect_mesh

        return inspect_mesh(mesh, Constraints.from_dict(constraints), SlicingSettings.from_dict(slicing))


class HttpInspector:
    """POST /v1/inspect on a remote inspect service, pointing it at the STL asset."""

    mode = "http"

    def __init__(self, base_url: str, timeout_s: float = 120.0, client: httpx.Client | None = None):
        self.base_url = base_url.rstrip("/")
        self.timeout_s = timeout_s
        self._client = client

    def inspect(self, mesh, constraints, slicing, assets, storage):
        stl = assets.get("stl")
        if stl is None:
            raise GeometryError("HTTP inspection needs the STL output", {"outputs": sorted(assets)})
        if isinstance(storage, LocalStorage):
            mesh_ref: dict[str, Any] = {"path": str(storage.path_for(stl.key)), "format": "stl"}
        else:
            mesh_ref = {"url": stl.url, "format": "stl"}
        body = {"mesh": mesh_ref, "constraints": dict(constraints), "slicing": dict(slicing)}
        try:
            if self._client is not None:
                resp = self._client.post(f"{self.base_url}/v1/inspect", json=body, timeout=self.timeout_s)
            else:
                resp = httpx.post(f"{self.base_url}/v1/inspect", json=body, timeout=self.timeout_s)
        except httpx.HTTPError as exc:
            raise GeometryError("Inspect service unreachable", {"inspect_url": self.base_url, "error": str(exc)}) from exc
        if resp.status_code != 200:
            raise GeometryError("Inspect service rejected the mesh", {"status": resp.status_code, "body": resp.text[:2000]})
        data = resp.json()
        return data["printability"], data["print_estimate"]


def inspector_from_env(env: Mapping[str, str] | None = None) -> Inspector:
    env = os.environ if env is None else env
    url = (env.get("AAKAR_INSPECT_URL") or "").strip()
    return HttpInspector(url) if url else InProcessInspector()
