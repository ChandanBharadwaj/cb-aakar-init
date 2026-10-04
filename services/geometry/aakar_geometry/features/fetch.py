"""Fetching customer content (``content_source``): photos for ``relief_image``, model files for ``hero_mesh``.

``ContentFetcher.fetch(source) -> bytes`` is the only thing the features stage needs. ``HttpFetcher``
is the production fetcher (the API hands geometry an internal URL per upload); ``LocalFileFetcher``
serves tests and the CLI. Format detection is separate and pure: ``source.format`` first, then the
URL suffix, then the file's magic bytes (``resolve_format``).

Caps: 15 MiB for photos, 200 MiB for models (the inspect service's own download cap).
"""

from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Any, Mapping, Protocol
from urllib.parse import unquote, urlparse

import httpx

from aakar_inspect.loaders import SUPPORTED_FORMATS as MODEL_FORMATS

from ..errors import ContentUnusable, GeometryError, InvalidSpec

log = logging.getLogger("aakar.geometry.features.fetch")

IMAGE_FORMATS = ("png", "jpg", "webp", "heic")
FORMAT_ALIASES = {"jpeg": "jpg", "jpe": "jpg", "tif": "tiff", "heif": "heic"}
MAX_IMAGE_BYTES = 15 * 1024 * 1024
MAX_MODEL_BYTES = 200 * 1024 * 1024
CONNECT_TIMEOUT_S = 10.0
READ_TIMEOUT_S = 60.0
_CHUNK = 256 * 1024

_NOUN = {"image": "photo", "model": "model"}


def normalise_format(fmt: Any) -> str | None:
    if not fmt or not isinstance(fmt, str):
        return None
    f = fmt.strip().lower().lstrip(".")
    return FORMAT_ALIASES.get(f, f) or None


def format_from_url(url: Any) -> str | None:
    if not url or not isinstance(url, str):
        return None
    path = urlparse(url).path
    tail = path.rsplit("/", 1)[-1]
    if "." not in tail:
        return None
    return normalise_format(tail.rsplit(".", 1)[1])


def sniff_format(data: bytes) -> str | None:
    """Best-effort format from magic bytes (photos and model files)."""
    head = data[:64]
    if head.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if head.startswith(b"\xff\xd8\xff"):
        return "jpg"
    if head[:4] == b"RIFF" and head[8:12] == b"WEBP":
        return "webp"
    if head[4:8] == b"ftyp" and head[8:12] in (b"heic", b"heix", b"hevc", b"heim", b"heis", b"mif1", b"msf1"):
        return "heic"
    if head.startswith(b"glTF"):
        return "glb"
    if head.startswith(b"PK\x03\x04"):
        return "3mf"
    if head.startswith(b"ply"):
        return "ply"
    if head.startswith(b"OFF") or head.startswith(b"COFF"):
        return "off"
    stripped = head.lstrip()
    if stripped.startswith(b"{"):
        return "gltf"
    if stripped.startswith(b"solid"):
        return "stl"
    if stripped[:2] in (b"v ", b"o ", b"g ", b"# ") or stripped.startswith(b"mtllib"):
        return "obj"
    if len(data) >= 84:
        # binary STL: 80-byte header + uint32 triangle count that matches the file length
        count = int.from_bytes(data[80:84], "little")
        if 84 + 50 * count == len(data):
            return "stl"
    return None


def content_kind(fmt: str | None) -> str | None:
    """``image`` | ``model`` | None for an unknown format."""
    if fmt in IMAGE_FORMATS:
        return "image"
    if fmt in MODEL_FORMATS:
        return "model"
    return None


def size_cap(kind: str | None) -> int:
    return MAX_IMAGE_BYTES if kind == "image" else MAX_MODEL_BYTES


def resolve_format(source: Mapping[str, Any], data: bytes | None = None) -> str | None:
    """``source.format`` → URL suffix → magic bytes; None when nothing matched."""
    fmt = normalise_format(source.get("format"))
    if content_kind(fmt):
        return fmt
    fmt = format_from_url(source.get("url"))
    if content_kind(fmt):
        return fmt
    if data:
        return sniff_format(data)
    return None


def expected_kind(source: Mapping[str, Any]) -> str | None:
    return content_kind(resolve_format(source))


def _too_large(kind: str | None, size: int | None, url: str) -> ContentUnusable:
    noun = _NOUN.get(kind or "", "file")
    return ContentUnusable(
        f"This {noun} is too large; photos may be up to 15 MB and models up to 200 MB",
        {"url": url, "bytes": size, "cap": size_cap(kind)},
    )


def _looks_like_html(data: bytes) -> bool:
    head = data[:512].lstrip().lower()
    return head.startswith(b"<!doctype html") or head.startswith(b"<html")


def check_payload(data: bytes, source: Mapping[str, Any], content_type: str | None = None) -> None:
    """Common sanity for fetched bytes: not empty, not an HTML page, within the cap for its kind."""
    url = str(source.get("url") or "")
    if not data:
        raise ContentUnusable("This file is empty; please upload it again", {"url": url})
    if (content_type or "").split(";")[0].strip().lower() == "text/html" or _looks_like_html(data):
        raise ContentUnusable(
            "We received a web page instead of your file; please upload it again",
            {"url": url, "content_type": content_type},
        )
    kind = content_kind(resolve_format(source, data))
    if len(data) > size_cap(kind):
        raise _too_large(kind, len(data), url)


class ContentFetcher(Protocol):
    def fetch(self, source: Mapping[str, Any]) -> bytes: ...


class HttpFetcher:
    """Streams ``source.url`` over http(s) with timeouts and size caps."""

    def __init__(
        self,
        client: httpx.Client | None = None,
        connect_timeout_s: float = CONNECT_TIMEOUT_S,
        read_timeout_s: float = READ_TIMEOUT_S,
    ):
        self._client = client
        self.timeout = httpx.Timeout(connect=connect_timeout_s, read=read_timeout_s, write=connect_timeout_s, pool=connect_timeout_s)

    def fetch(self, source: Mapping[str, Any]) -> bytes:
        url = str(source.get("url") or "")
        scheme = urlparse(url).scheme.lower()
        if scheme not in ("http", "https"):
            raise InvalidSpec("Content URL must start with http:// or https://", {"url": url})
        kind = expected_kind(source)
        cap = size_cap(kind)
        client = self._client or httpx.Client(timeout=self.timeout, follow_redirects=True)
        try:
            with client.stream("GET", url) as resp:
                if resp.status_code >= 500:
                    raise GeometryError(
                        "Your content could not be fetched right now; please try again",
                        {"url": url, "status": resp.status_code},
                    )
                if resp.status_code >= 400:
                    noun = _NOUN.get(kind or "", "file")
                    raise ContentUnusable(
                        f"We couldn't find your {noun}; please upload it again",
                        {"url": url, "status": resp.status_code},
                    )
                content_type = resp.headers.get("content-type")
                declared = resp.headers.get("content-length")
                if declared and declared.isdigit() and int(declared) > cap:
                    raise _too_large(kind, int(declared), url)
                buf = bytearray()
                for chunk in resp.iter_bytes(_CHUNK):
                    buf.extend(chunk)
                    if len(buf) > cap:
                        raise _too_large(kind, len(buf), url)
        except (ContentUnusable, GeometryError, InvalidSpec):
            raise
        except httpx.HTTPError as exc:
            raise GeometryError(
                "Your content could not be fetched right now; please try again",
                {"url": url, "error": f"{type(exc).__name__}: {exc}"},
            ) from exc
        finally:
            if self._client is None:
                client.close()
        data = bytes(buf)
        check_payload(data, source, content_type)
        return data


class LocalFileFetcher:
    """Reads ``file://`` URLs, or any URL's basename inside ``base_dir`` (tests, CLI ``--content-dir``)."""

    def __init__(self, base_dir: str | os.PathLike[str] | None = None):
        self.base_dir = Path(base_dir).expanduser().resolve() if base_dir is not None else None

    def path_for(self, source: Mapping[str, Any]) -> Path:
        url = str(source.get("url") or "")
        parsed = urlparse(url)
        if parsed.scheme == "file":
            return Path(unquote(parsed.path))
        if self.base_dir is None:
            raise InvalidSpec("LocalFileFetcher needs a file:// URL or a base directory", {"url": url})
        name = Path(unquote(parsed.path if parsed.scheme else url)).name
        if not name or name in (".", ".."):
            raise InvalidSpec("Content URL has no file name", {"url": url})
        return self.base_dir / name

    def fetch(self, source: Mapping[str, Any]) -> bytes:
        path = self.path_for(source)
        noun = _NOUN.get(expected_kind(source) or "", "file")
        if not path.is_file():
            raise ContentUnusable(f"We couldn't find your {noun}; please upload it again", {"path": str(path)})
        size = path.stat().st_size
        cap = size_cap(expected_kind(source))
        if size > cap:
            raise _too_large(expected_kind(source), size, str(path))
        data = path.read_bytes()
        check_payload(data, source)
        return data


__all__ = [
    "ContentFetcher",
    "HttpFetcher",
    "IMAGE_FORMATS",
    "LocalFileFetcher",
    "MAX_IMAGE_BYTES",
    "MAX_MODEL_BYTES",
    "MODEL_FORMATS",
    "check_payload",
    "content_kind",
    "expected_kind",
    "format_from_url",
    "normalise_format",
    "resolve_format",
    "size_cap",
    "sniff_format",
]
