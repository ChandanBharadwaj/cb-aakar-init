import numpy as np
import pytest
import trimesh


def box_on_bed(w: float, d: float, h: float, dx: float = 0.0, dy: float = 0.0, z0: float = 0.0) -> trimesh.Trimesh:
    m = trimesh.creation.box(extents=[w, d, h])
    m.apply_translation([dx, dy, z0 + h / 2])
    return m


@pytest.fixture
def cube40() -> trimesh.Trimesh:
    return box_on_bed(40, 40, 40)


@pytest.fixture
def pillar_factory():
    """6 × 6 × 120 mm pillar with a 30 × 30 × 20 mm block on top, offset by dx."""

    def make(dx: float) -> trimesh.Trimesh:
        pillar = box_on_bed(6, 6, 120)
        top = box_on_bed(30, 30, 20, dx=dx, z0=120)
        return trimesh.util.concatenate([pillar, top])

    return make


@pytest.fixture
def open_mesh() -> trimesh.Trimesh:
    """A sphere with its whole cap removed: a hole far too big for trimesh.repair.fill_holes."""
    sphere = trimesh.creation.icosphere(subdivisions=3, radius=20)
    sphere.apply_translation([0, 0, 20])
    keep = sphere.triangles_center[:, 2] < 30
    sphere.update_faces(keep)
    sphere.remove_unreferenced_vertices()
    assert not sphere.is_watertight
    return sphere
