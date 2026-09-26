"""Load a mesh from a path, URL or bytes into a single trimesh.Trimesh (mm, Z-up assumed)."""

from __future__ import annotations

import io
import os
from pathlib import Path
from typing import Any

import httpx
import trimesh

MAX_DOWNLOAD_BYTES = 200 * 1024 * 1024
SUPPORTED_FORMATS = ("stl", "glb", "3mf", "obj", "ply", "off", "gltf")


class MeshLoadError(ValueError):
    """The mesh could not be fetched or parsed."""


def _to_single_mesh(loaded: Any) -> trimesh.Trimesh:
    if isinstance(loaded, trimesh.Trimesh):
        return loaded
    if isinstance(loaded, trimesh.Scene):
        if len(loaded.geometry) == 0:
            raise MeshLoadError("scene contains no geometry")
        merged = loaded.to_mesh() if hasattr(loaded, "to_mesh") else loaded.dump(concatenate=True)
        if isinstance(merged, trimesh.Trimesh):
            return merged
    raise MeshLoadError(f"unsupported geometry type {type(loaded).__name__}")


def load_mesh_bytes(data: bytes, file_type: str) -> trimesh.Trimesh:
    file_type = file_type.lower().lstrip(".")
    if file_type not in SUPPORTED_FORMATS:
        raise MeshLoadError(f"unsupported mesh format '{file_type}'")
    try:
        loaded = trimesh.load(io.BytesIO(data), file_type=file_type, force="mesh")
    except Exception as exc:
        raise MeshLoadError(f"could not parse {file_type}: {exc}") from exc
    mesh = _to_single_mesh(loaded)
    if len(mesh.faces) == 0:
        raise MeshLoadError("mesh has no faces")
    return mesh


def load_mesh_path(path: str | os.PathLike[str], file_type: str | None = None) -> trimesh.Trimesh:
    p = Path(path)
    if not p.is_file():
        raise MeshLoadError(f"mesh file not found: {p}")
    return load_mesh_bytes(p.read_bytes(), file_type or p.suffix.lstrip(".") or "stl")


def load_mesh_url(url: str, file_type: str | None = None, timeout_s: float = 30.0) -> trimesh.Trimesh:
    if not file_type:
        tail = url.split("?", 1)[0].rsplit(".", 1)
        file_type = tail[1].lower() if len(tail) == 2 else ""
    if not file_type:
        raise MeshLoadError("mesh.format is required when the URL has no file extension")
    try:
        with httpx.Client(timeout=timeout_s, follow_redirects=True) as client:
            with client.stream("GET", url) as resp:
                resp.raise_for_status()
                buf = bytearray()
                for chunk in resp.iter_bytes():
                    buf.extend(chunk)
                    if len(buf) > MAX_DOWNLOAD_BYTES:
                        raise MeshLoadError("mesh download exceeds 200 MiB")
    except httpx.HTTPError as exc:
        raise MeshLoadError(f"could not fetch {url}: {exc}") from exc
    return load_mesh_bytes(bytes(buf), file_type)


def load_mesh(path: str | None = None, url: str | None = None, file_type: str | None = None) -> trimesh.Trimesh:
    if bool(path) == bool(url):
        raise MeshLoadError("provide exactly one of mesh.path or mesh.url")
    return load_mesh_path(path, file_type) if path else load_mesh_url(url, file_type)  # type: ignore[arg-type]
