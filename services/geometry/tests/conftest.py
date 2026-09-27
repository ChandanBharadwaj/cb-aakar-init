import json
import logging
from pathlib import Path

import pytest

logging.getLogger("build123d").setLevel(logging.WARNING)

REPO = Path(__file__).resolve().parents[3]
EXAMPLE_SPEC = REPO / "packages/contracts/examples/jharokha-phone-stand.spec.json"
JOB_ID = "6f1c1a2e-9d0b-4a8e-8c1e-2f3d4e5f6a7b"
DESIGN_ID = "0b8e7c6d-5a4b-4c3d-9e2f-1a0b9c8d7e6f"


@pytest.fixture(scope="session")
def example_spec() -> dict:
    return json.loads(EXAMPLE_SPEC.read_text(encoding="utf-8"))


@pytest.fixture
def generate_request(example_spec) -> dict:
    return {"job_id": JOB_ID, "design_id": DESIGN_ID, "version_no": 1, "spec": json.loads(json.dumps(example_spec))}


@pytest.fixture(scope="session")
def built_example(example_spec):
    """Build the example spec once per session (≈1 s) for the geometry assertions."""
    from aakar_geometry.templates import get_template

    template = get_template(example_spec["template"])
    params = template.validate(example_spec["params"])
    return template, params, template.build(params)


@pytest.fixture
def local_storage(tmp_path):
    from aakar_geometry.storage import LocalStorage

    return LocalStorage(tmp_path / "assets", "http://localhost:8081")


# --------------------------------------------------------------------------- Chhaap (features) fixtures

CONTENT_UUID = "3f2b6a1e-8c4d-4e5f-9a0b-1c2d3e4f5a6b"


def gradient_png(width: int = 64, height: int = 48, *, alpha: bool = False) -> bytes:
    """Left-to-right ramp with a bright disc in the middle: plenty of contrast for a relief."""
    import io

    import numpy as np
    from PIL import Image

    yy, xx = np.mgrid[0:height, 0:width]
    lum = (xx / max(width - 1, 1) * 200).astype(np.uint8)
    lum[(xx - width // 2) ** 2 + (yy - height // 2) ** 2 < (min(width, height) // 5) ** 2] = 255
    if alpha:
        a = np.full_like(lum, 255)
        a[:, : width // 4] = 0  # transparent left quarter
        img = Image.fromarray(np.dstack([lum, lum, lum, a]), "RGBA")
    else:
        img = Image.fromarray(lum, "L")
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def constant_png(value: int = 128) -> bytes:
    import io

    import numpy as np
    from PIL import Image

    buf = io.BytesIO()
    Image.fromarray(np.full((32, 32), value, dtype=np.uint8), "L").save(buf, format="PNG")
    return buf.getvalue()


def sphere_stl(radius: float = 10.0, subdivisions: int = 3) -> bytes:
    import trimesh

    return trimesh.creation.icosphere(subdivisions=subdivisions, radius=radius).export(file_type="stl")


def open_sphere_stl() -> bytes:
    """A sphere with its cap removed: a hole far too big for trimesh's fill_holes."""
    import trimesh

    sphere = trimesh.creation.icosphere(subdivisions=3, radius=20)
    sphere.apply_translation([0, 0, 20])
    sphere.update_faces(sphere.triangles_center[:, 2] < 30)
    sphere.remove_unreferenced_vertices()
    return sphere.export(file_type="stl")


def soup_stl() -> bytes:
    import trimesh

    return trimesh.creation.random_soup(30, seed=1).export(file_type="stl")


def content_source(name: str, fmt: str | None = None) -> dict:
    src = {"upload_id": CONTENT_UUID, "url": f"http://api:8080/media/uploads/{name}", "origin": "upload"}
    if fmt:
        src["format"] = fmt
    return src


def _box_on_bed(w: float, d: float, h: float):
    import trimesh

    m = trimesh.creation.box(extents=[w, d, h])
    m.apply_translation([0, 0, h / 2])
    return m


def make_test_templates():
    """Three tiny templates (no CAD kernel) exercising the content slot: a plate (surface anchor),
    a plinth (volume anchor on a body) and a raw carrier (volume anchor, no body)."""
    import trimesh

    from aakar_geometry.features.frames import AnchorFrame
    from aakar_geometry.templates.base import Anchor, HardwareRef, Param, Template, TemplateConstraints

    class PlateTemplate(Template):
        id = "test_plate"
        version = 1
        family = "keychain"
        name = "Test plate"
        params = {"thickness_mm": Param("number", "Thickness", 3.0, "mm", 2, 6, 0.5)}
        anchors = (
            Anchor("face", "Face", "planar", size_mm=(36, 26), bleed_mm=2, max_relief_mm=1.5, accepts=("relief_image",)),
        )
        constraints = TemplateConstraints()
        features_supported = ("relief_image",)
        hardware = (HardwareRef("split_ring_25", 1),)

        @classmethod
        def build_body(cls, params):
            return _box_on_bed(40, 30, float(params["thickness_mm"]))

        @classmethod
        def anchor_frame(cls, anchor_id, params):
            if anchor_id != "face":
                raise NotImplementedError(anchor_id)
            return AnchorFrame([0, 0, float(params["thickness_mm"])], [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=(36, 26))

        @classmethod
        def karigar_note(cls, params):
            return "A test plate."

    class PlinthTemplate(Template):
        id = "test_plinth"
        version = 1
        family = "figurine_base"
        name = "Test plinth"
        params = {}
        anchors = (Anchor("top", "Top of the plinth", "planar", kind="volume", bounds_mm=(40, 40, 60)),)
        features_supported = ("hero_mesh",)

        @classmethod
        def build_body(cls, params):
            return _box_on_bed(50, 50, 10)

        @classmethod
        def anchor_frame(cls, anchor_id, params):
            return AnchorFrame([0, 0, 10], [1, 0, 0], [0, 1, 0], [0, 0, 1], bounds_mm=(40, 40, 60))

        @classmethod
        def karigar_note(cls, params):
            return "A test plinth."

    class RawTemplate(Template):
        id = "test_raw"
        version = 1
        family = "raw_print"
        name = "Test raw print"
        params = {"longest_mm": Param("number", "Size", 80.0, "mm", 20, 240, 1)}
        anchors = (Anchor("body", "The whole print", "planar", kind="volume", bounds_mm=(250, 250, 250)),)
        features_supported = ("hero_mesh",)

        @classmethod
        def build_body(cls, params):
            return trimesh.Trimesh()

        @classmethod
        def anchor_frame(cls, anchor_id, params):
            return AnchorFrame([0, 0, 0], [1, 0, 0], [0, 1, 0], [0, 0, 1], bounds_mm=(250, 250, 250))

        @classmethod
        def karigar_note(cls, params):
            return "Your own model, printed as it is."

    return PlateTemplate, PlinthTemplate, RawTemplate


@pytest.fixture
def test_templates(monkeypatch):
    """Registers the three test templates for one test only (the registry is restored afterwards)."""
    from aakar_geometry.templates import REGISTRY

    templates = make_test_templates()
    for t in templates:
        monkeypatch.setitem(REGISTRY, (t.id, t.version), t)
    return templates


@pytest.fixture
def content_dir(tmp_path):
    """A folder with photo.png, alpha.png, flat.png, sphere.stl, open.stl, soup.stl and garbage.stl (words, not a
    model) for LocalFileFetcher."""
    folder = tmp_path / "content"
    folder.mkdir()
    (folder / "photo.png").write_bytes(gradient_png())
    (folder / "alpha.png").write_bytes(gradient_png(alpha=True))
    (folder / "flat.png").write_bytes(constant_png())
    (folder / "sphere.stl").write_bytes(sphere_stl())
    (folder / "open.stl").write_bytes(open_sphere_stl())
    (folder / "soup.stl").write_bytes(soup_stl())
    (folder / "garbage.stl").write_bytes(b"this is not a model file at all, only words " * 4)
    (folder / "notes.txt").write_bytes(b"not a photo, not a model")
    return folder
