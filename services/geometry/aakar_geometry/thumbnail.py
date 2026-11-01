"""Product thumbnail: a still PNG of the built piece, rendered here so the Shop, cart, checkout and share pages show the
real model rather than a stand-in.

No GL: the mesh (mm, Z-up) is decimated, seen from the front-right and above with an orthographic camera, shaded per
face (key, fill and rim light on the finish's colour from materials.json) and painted back to front with Pillow at 3×,
then downsampled. The backdrop is the storefront's sand gradient with a soft contact shadow, so the image sits in a
Shop card without a seam. Deterministic: the same mesh and finish give the same bytes.
"""

from __future__ import annotations

import io

import numpy as np
import trimesh
from PIL import Image, ImageDraw, ImageFilter

WIDTH, HEIGHT = 800, 600  # the Shop card's 4:3
SUPERSAMPLE = 3
MAX_FACES = 40_000
FILL = 0.74  # share of the frame the piece may take
SAND_TOP, SAND_BOTTOM = (235, 223, 203), (203, 183, 154)
FALLBACK_COLOR = "#C9714F"  # terracotta, for a finish the tokens do not know

VIEW = np.array([0.55, -1.0, 0.62])  # from the camera towards the piece is −VIEW: front (−Y), right, above
KEY = np.array([0.35, -0.6, 0.9])
FILL_LIGHT = np.array([-0.8, -0.4, 0.3])
RIM = np.array([-0.2, 0.9, 0.5])


def _unit(v: np.ndarray) -> np.ndarray:
    return v / np.linalg.norm(v)


def finish_color(material_id: str | None) -> tuple[int, int, int]:
    from .materials import load_materials

    hex_color = next((m.get("pbr", {}).get("color") for m in load_materials() if m["id"] == material_id), None) or FALLBACK_COLOR
    h = hex_color.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def _backdrop(w: int, h: int) -> Image.Image:
    t = np.linspace(0.0, 1.0, h)[:, None]
    rows = (1 - t) * np.array(SAND_TOP) + t * np.array(SAND_BOTTOM)
    return Image.fromarray(np.repeat(rows[:, None, :], w, axis=1).astype(np.uint8), "RGB")


def render_thumbnail(mesh: trimesh.Trimesh, material_id: str | None = None) -> bytes:
    if len(mesh.faces) > MAX_FACES:
        try:
            mesh = mesh.simplify_quadric_decimation(face_count=MAX_FACES)
        except Exception:  # pragma: no cover - decimation is an optimisation; paint the full mesh without it
            pass
    view = _unit(VIEW)
    right = _unit(np.cross(np.array([0.0, 0.0, 1.0]), view))
    up = np.cross(view, right)

    verts = np.asarray(mesh.vertices, dtype=float) - mesh.bounds.mean(axis=0)
    faces = np.asarray(mesh.faces)
    normals = np.asarray(mesh.face_normals, dtype=float)
    facing = normals @ view > 1e-6  # back faces are hidden by the closed solid anyway
    faces, normals = faces[facing], normals[facing]

    xy = np.stack([verts @ right, verts @ up], axis=1)
    depth = (verts @ view)[faces].mean(axis=1)
    order = np.argsort(depth)  # far first

    w, h = WIDTH * SUPERSAMPLE, HEIGHT * SUPERSAMPLE
    span = np.ptp(xy, axis=0)
    scale = min(w * FILL / max(span[0], 1e-6), h * FILL / max(span[1], 1e-6))
    centre = (xy.min(axis=0) + xy.max(axis=0)) / 2
    px = np.empty_like(xy)
    px[:, 0] = w / 2 + (xy[:, 0] - centre[0]) * scale
    px[:, 1] = h * 0.47 - (xy[:, 1] - centre[1]) * scale

    # the contact shadow: the lowest projected points of the piece sit on it
    canvas = _backdrop(w, h)
    floor_y = float(px[:, 1].max())
    shadow = Image.new("L", (w, h), 0)
    half = span[0] * scale * 0.55
    ImageDraw.Draw(shadow).ellipse([w / 2 - half, floor_y - h * 0.05, w / 2 + half, floor_y + h * 0.02], fill=120)
    shadow = shadow.filter(ImageFilter.GaussianBlur(h * 0.025))
    canvas = Image.composite(Image.new("RGB", (w, h), (60, 40, 28)), canvas, shadow)

    base = np.array(finish_color(material_id), dtype=float) / 255.0
    lin = base**2.2
    light = (
        0.30
        + 0.62 * np.clip(normals @ _unit(KEY), 0, None)
        + 0.22 * np.clip(normals @ _unit(FILL_LIGHT), 0, None)
        + 0.18 * np.clip(normals @ _unit(RIM), 0, None)
    )
    shaded = np.clip(lin[None, :] * light[:, None], 0, 1) ** (1 / 2.2)
    colours = (shaded * 255).round().astype(np.uint8)

    draw = ImageDraw.Draw(canvas)
    tris = px[faces]
    for i in order:
        c = tuple(int(v) for v in colours[i])
        draw.polygon([tuple(p) for p in tris[i]], fill=c, outline=c)  # outline closes hairline gaps between faces

    out = io.BytesIO()
    canvas.resize((WIDTH, HEIGHT), Image.LANCZOS).save(out, format="PNG", optimize=True)
    return out.getvalue()
