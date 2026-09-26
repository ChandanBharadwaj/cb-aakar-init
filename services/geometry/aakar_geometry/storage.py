"""Asset storage: local disk (served by the API at /assets/{key}) or S3/MinIO via boto3.

Keys follow ``designs/{design_id}/v{n}/model.{glb,3mf,stl}``. ``put`` returns the asset record
shape used by ``design.completed.v1.json#/properties/assets``.
"""

from __future__ import annotations

import hashlib
import logging
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol

from .errors import StorageError

log = logging.getLogger("aakar.geometry.storage")


@dataclass(frozen=True)
class AssetRecord:
    key: str
    url: str
    bytes: int
    content_type: str
    sha256: str

    def to_dict(self) -> dict[str, Any]:
        return {"key": self.key, "url": self.url, "bytes": self.bytes, "content_type": self.content_type, "sha256": self.sha256}


def asset_key(design_id: str, version_no: int, kind: str) -> str:
    return f"designs/{design_id}/v{int(version_no)}/model.{kind}"


class Storage(Protocol):
    kind: str

    def put(self, key: str, data: bytes, content_type: str) -> AssetRecord: ...

    def url_for(self, key: str) -> str: ...


def _safe_key(key: str) -> str:
    if key.startswith("/") or ".." in key.split("/") or "\\" in key or not key:
        raise StorageError("Invalid storage key", {"key": key})
    return key


class LocalStorage:
    kind = "local"

    def __init__(self, root: str | os.PathLike[str] = "./.aakar-assets", public_url: str = "http://localhost:8081"):
        self.root = Path(root).expanduser().resolve()
        self.public_url = public_url.rstrip("/")

    def path_for(self, key: str) -> Path:
        return self.root / _safe_key(key)

    def url_for(self, key: str) -> str:
        return f"{self.public_url}/assets/{_safe_key(key)}"

    def put(self, key: str, data: bytes, content_type: str) -> AssetRecord:
        path = self.path_for(key)
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            tmp = path.with_suffix(path.suffix + ".part")
            tmp.write_bytes(data)
            os.replace(tmp, path)
        except OSError as exc:
            raise StorageError("Could not write asset to local storage", {"key": key, "error": str(exc)}) from exc
        return AssetRecord(key, self.url_for(key), len(data), content_type, hashlib.sha256(data).hexdigest())


class S3Storage:
    kind = "s3"

    def __init__(
        self,
        bucket: str,
        endpoint_url: str | None = None,
        public_url: str | None = None,
        region: str | None = None,
        client: Any | None = None,
    ):
        if not bucket:
            raise StorageError("AAKAR_S3_BUCKET is required when AAKAR_STORAGE=s3")
        self.bucket = bucket
        self.endpoint_url = endpoint_url
        self.public_url = (public_url or "").rstrip("/") or None
        self.region = region
        self._client = client

    @property
    def client(self) -> Any:
        if self._client is None:
            try:
                import boto3
                from botocore.config import Config
            except ImportError as exc:  # pragma: no cover
                raise StorageError("boto3 is required for AAKAR_STORAGE=s3") from exc
            self._client = boto3.client(
                "s3",
                endpoint_url=self.endpoint_url,
                region_name=self.region,
                config=Config(s3={"addressing_style": "path"}) if self.endpoint_url else None,
            )
        return self._client

    def url_for(self, key: str) -> str:
        key = _safe_key(key)
        if self.public_url:
            return f"{self.public_url}/{key}"
        try:
            return self.client.generate_presigned_url(
                "get_object", Params={"Bucket": self.bucket, "Key": key}, ExpiresIn=7 * 24 * 3600
            )
        except Exception as exc:
            raise StorageError("Could not presign asset URL", {"key": key, "error": str(exc)}) from exc

    def put(self, key: str, data: bytes, content_type: str) -> AssetRecord:
        key = _safe_key(key)
        try:
            self.client.put_object(Bucket=self.bucket, Key=key, Body=data, ContentType=content_type)
        except Exception as exc:
            raise StorageError("Could not upload asset to S3", {"key": key, "bucket": self.bucket, "error": str(exc)}) from exc
        return AssetRecord(key, self.url_for(key), len(data), content_type, hashlib.sha256(data).hexdigest())


def storage_from_env(env: dict[str, str] | None = None) -> Storage:
    env = dict(os.environ if env is None else env)
    backend = env.get("AAKAR_STORAGE", "local").strip().lower()
    if backend == "local":
        return LocalStorage(env.get("AAKAR_ASSET_DIR", "./.aakar-assets"), env.get("AAKAR_PUBLIC_URL", "http://localhost:8081"))
    if backend == "s3":
        return S3Storage(
            bucket=env.get("AAKAR_S3_BUCKET", ""),
            endpoint_url=env.get("AAKAR_S3_ENDPOINT") or None,
            public_url=env.get("AAKAR_S3_PUBLIC_URL") or None,
            region=env.get("AWS_DEFAULT_REGION") or None,
        )
    raise StorageError(f"Unknown AAKAR_STORAGE backend '{backend}' (use local or s3)")
