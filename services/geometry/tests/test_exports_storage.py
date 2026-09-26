import hashlib
import io
import zipfile

import pytest
import trimesh

from aakar_geometry.errors import StorageError
from aakar_geometry.exports import export_3mf, export_all, export_glb, export_stl, _minimal_3mf
from aakar_geometry.storage import LocalStorage, S3Storage, asset_key, storage_from_env


def _cube():
    m = trimesh.creation.box(extents=[10, 20, 30])
    m.apply_translation([0, 0, 15])
    return m


def test_glb_is_metres_y_up_with_material():
    data = export_glb(_cube(), "cube")
    scene = trimesh.load(io.BytesIO(data), file_type="glb")
    assert scene.extents == pytest.approx([0.010, 0.030, 0.020], abs=1e-6)  # height (30 mm) is now along Y
    mesh = scene.to_mesh() if hasattr(scene, "to_mesh") else scene.dump(concatenate=True)
    assert mesh.is_watertight
    assert mesh.visual.material.name == "aakar_neutral_grey"


def test_stl_is_binary_mm():
    data = export_stl(_cube())
    assert not data.startswith(b"solid")
    mesh = trimesh.load(io.BytesIO(data), file_type="stl")
    assert mesh.extents == pytest.approx([10, 20, 30])


def test_3mf_in_millimetres_reloads():
    for data in (export_3mf(_cube(), "cube"), _minimal_3mf(_cube(), "cube")):
        with zipfile.ZipFile(io.BytesIO(data)) as zf:
            names = set(zf.namelist())
            assert {"[Content_Types].xml", "_rels/.rels", "3D/3dmodel.model"} <= names
            model = zf.read("3D/3dmodel.model").decode("utf-8")
        assert 'unit="millimeter"' in model
        loaded = trimesh.load(io.BytesIO(data), file_type="3mf")
        mesh = loaded.to_mesh() if isinstance(loaded, trimesh.Scene) else loaded
        assert mesh.extents == pytest.approx([10, 20, 30], abs=1e-3)
        assert mesh.is_watertight


def test_export_all_skips_unknown_kinds():
    files = export_all(_cube(), ["glb", "usdz", "thumb", "stl", "3mf"])
    assert sorted(files) == ["3mf", "glb", "stl"]
    assert files["glb"].content_type == "model/gltf-binary"
    assert files["3mf"].content_type == "model/3mf"
    assert files["stl"].content_type == "model/stl"


def test_local_storage_put_and_url(tmp_path):
    storage = LocalStorage(tmp_path, "http://localhost:8081/")
    key = asset_key("0b8e7c6d-5a4b-4c3d-9e2f-1a0b9c8d7e6f", 2, "glb")
    assert key == "designs/0b8e7c6d-5a4b-4c3d-9e2f-1a0b9c8d7e6f/v2/model.glb"
    rec = storage.put(key, b"hello", "model/gltf-binary")
    assert (tmp_path / key).read_bytes() == b"hello"
    assert rec.url == f"http://localhost:8081/assets/{key}"
    assert rec.bytes == 5 and rec.sha256 == hashlib.sha256(b"hello").hexdigest()
    with pytest.raises(StorageError):
        storage.put("../escape.glb", b"x", "model/gltf-binary")


class FakeS3:
    def __init__(self):
        self.objects = {}

    def put_object(self, Bucket, Key, Body, ContentType):
        self.objects[(Bucket, Key)] = (Body, ContentType)


def test_s3_storage_with_fake_client():
    client = FakeS3()
    storage = S3Storage(bucket="aakar-assets", endpoint_url="http://minio:9000", public_url="http://localhost:9000/aakar-assets", client=client)
    rec = storage.put("designs/x/v1/model.stl", b"stl", "model/stl")
    assert client.objects[("aakar-assets", "designs/x/v1/model.stl")] == (b"stl", "model/stl")
    assert rec.url == "http://localhost:9000/aakar-assets/designs/x/v1/model.stl"


def test_storage_from_env(tmp_path):
    local = storage_from_env({"AAKAR_ASSET_DIR": str(tmp_path), "AAKAR_PUBLIC_URL": "http://x:1"})
    assert isinstance(local, LocalStorage) and local.public_url == "http://x:1"
    s3 = storage_from_env({"AAKAR_STORAGE": "s3", "AAKAR_S3_BUCKET": "b", "AAKAR_S3_PUBLIC_URL": "http://p"})
    assert isinstance(s3, S3Storage) and s3.bucket == "b"
    with pytest.raises(StorageError):
        storage_from_env({"AAKAR_STORAGE": "s3"})
    with pytest.raises(StorageError):
        storage_from_env({"AAKAR_STORAGE": "ftp"})
