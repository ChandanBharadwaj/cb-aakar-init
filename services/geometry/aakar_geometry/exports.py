"""Mesh exports: GLB (metres, Y-up, neutral PBR), binary STL (mm), 3MF (mm).

The GLB is for three.js / <model-viewer>: glTF is Y-up and metres, so the mm Z-up mesh is scaled
by 0.001 and rotated −90° about X (Z-up → Y-up; the stand's front, −Y, ends up facing +Z, i.e.
towards the default camera). STL and 3MF stay in mm, Z-up, for slicers.
"""

from __future__ import annotations

import io
import zipfile
from dataclasses import dataclass
from typing import Any

import numpy as np
import trimesh

CONTENT_TYPES = {
    "glb": "model/gltf-binary",
    "stl": "model/stl",
    "3mf": "model/3mf",
}

NEUTRAL_GREY = [180, 180, 180, 255]


@dataclass(frozen=True)
class ExportedFile:
    kind: str
    data: bytes
    content_type: str

    @property
    def filename(self) -> str:
        return f"model.{self.kind}"


ZUP_TO_YUP = np.array(
    [[1.0, 0.0, 0.0, 0.0], [0.0, 0.0, 1.0, 0.0], [0.0, -1.0, 0.0, 0.0], [0.0, 0.0, 0.0, 1.0]]
)  # (x, y, z) -> (x, z, -y): rotation of -90° about X


def to_viewer_frame(mesh: trimesh.Trimesh) -> trimesh.Trimesh:
    """Copy in metres, Y-up."""
    out = mesh.copy()
    out.apply_transform(ZUP_TO_YUP)
    out.apply_scale(0.001)
    return out


def export_glb(mesh: trimesh.Trimesh, name: str = "aakar_model") -> bytes:
    viewer = to_viewer_frame(mesh)
    material = trimesh.visual.material.PBRMaterial(
        name="aakar_neutral_grey",
        baseColorFactor=NEUTRAL_GREY,
        metallicFactor=0.0,
        roughnessFactor=0.6,
        doubleSided=False,
    )
    viewer.visual = trimesh.visual.TextureVisuals(material=material)
    viewer.metadata["name"] = name
    scene = trimesh.Scene()
    scene.add_geometry(viewer, node_name=name, geom_name=name)
    return scene.export(file_type="glb")


def export_stl(mesh: trimesh.Trimesh) -> bytes:
    data = mesh.export(file_type="stl")  # binary STL, mm
    return data if isinstance(data, bytes) else data.encode("utf-8")


def _minimal_3mf(mesh: trimesh.Trimesh, name: str) -> bytes:
    """A minimal, valid 3MF: [Content_Types].xml, _rels/.rels and 3D/3dmodel.model in millimetres."""
    verts = "".join(f'<vertex x="{x:.5f}" y="{y:.5f}" z="{z:.5f}"/>' for x, y, z in mesh.vertices)
    tris = "".join(f'<triangle v1="{a}" v2="{b}" v3="{c}"/>' for a, b, c in mesh.faces)
    model = (
        '<?xml version="1.0" encoding="UTF-8"?>'
        '<model unit="millimeter" xml:lang="en-US" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">'
        f'<metadata name="Title">{name}</metadata><metadata name="Application">Aakar geometry</metadata>'
        f'<resources><object id="1" name="{name}" type="model"><mesh><vertices>{verts}</vertices>'
        f"<triangles>{tris}</triangles></mesh></object></resources>"
        '<build><item objectid="1"/></build></model>'
    )
    content_types = (
        '<?xml version="1.0" encoding="UTF-8"?>'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml"/>'
        "</Types>"
    )
    rels = (
        '<?xml version="1.0" encoding="UTF-8"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Target="/3D/3dmodel.model" Id="rel0" '
        'Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/>'
        "</Relationships>"
    )
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("[Content_Types].xml", content_types)
        zf.writestr("_rels/.rels", rels)
        zf.writestr("3D/3dmodel.model", model)
    return buf.getvalue()


def export_3mf(mesh: trimesh.Trimesh, name: str = "aakar_model") -> bytes:
    """trimesh's 3MF writer when available; otherwise the minimal writer above. Always millimetres."""
    try:
        scene = trimesh.Scene()
        scene.add_geometry(mesh.copy(), node_name=name, geom_name=name)
        data = scene.export(file_type="3mf")
        if isinstance(data, bytes) and data[:2] == b"PK":
            with zipfile.ZipFile(io.BytesIO(data)) as zf:
                model = zf.read("3D/3dmodel.model").decode("utf-8", errors="replace")
            if 'unit="millimeter"' in model:
                return data
    except Exception:  # pragma: no cover - fall through to the minimal writer
        pass
    return _minimal_3mf(mesh, name)


EXPORTERS = {"glb": export_glb, "stl": export_stl, "3mf": export_3mf}
SUPPORTED_OUTPUTS = tuple(EXPORTERS)


def export_all(mesh: trimesh.Trimesh, kinds: list[str], name: str = "aakar_model") -> dict[str, ExportedFile]:
    out: dict[str, ExportedFile] = {}
    for kind in kinds:
        if kind not in EXPORTERS:
            continue
        exporter: Any = EXPORTERS[kind]
        data = exporter(mesh, name) if kind != "stl" else exporter(mesh)
        out[kind] = ExportedFile(kind=kind, data=data, content_type=CONTENT_TYPES[kind])
    return out
