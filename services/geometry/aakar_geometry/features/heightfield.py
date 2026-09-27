"""Watertight heightfield solids: a height grid on top, a flat bottom at z = 0, closed side walls.

``heights[j, i]`` is the height above the base at ``x = x_i`` (columns, +x) and ``y = y_j``
(rows, +y, so row 0 is the *bottom* of the picture). ``heightfield_solid`` centres the grid on the
origin in x and y with square cells of ``cell_mm``; ``grid_solid`` takes any strictly increasing
column and row positions (a lithophane plate: frame, bevel and photo samples in one surface). The
bottom is a fan from the grid's centre point so no triangle is degenerate, and every edge is shared
by exactly two faces by construction.
"""

from __future__ import annotations

import numpy as np
import trimesh

from ..errors import GeometryError


def heightfield_solid(heights: np.ndarray, cell_mm: float, base_mm: float) -> trimesh.Trimesh:
    """Solid whose top is ``base_mm + heights`` and whose bottom is the plane z = 0 (mm, local frame)."""
    h = np.asarray(heights, dtype=np.float64)
    if h.ndim != 2 or h.shape[0] < 2 or h.shape[1] < 2:
        raise ValueError(f"heights must be a 2D grid of at least 2 × 2 samples, got shape {h.shape}")
    if not np.all(np.isfinite(h)):
        raise ValueError("heights must be finite")
    if float(h.min()) < 0:
        raise ValueError("heights must be >= 0 (put the offset into base_mm)")
    cell = float(cell_mm)
    base = float(base_mm)
    if not (cell > 0) or not (base > 0):
        raise ValueError("cell_mm and base_mm must be positive")

    ny, nx = h.shape
    xs = (np.arange(nx) - (nx - 1) / 2.0) * cell
    ys = (np.arange(ny) - (ny - 1) / 2.0) * cell
    return _grid_solid(xs, ys, base + h)


def grid_solid(xs: np.ndarray, ys: np.ndarray, top: np.ndarray) -> trimesh.Trimesh:
    """Solid over a rectilinear grid: the bottom is the plane z = 0 and the top is ``top[j, i]`` (> 0) at
    ``(xs[i], ys[j])``. ``xs`` and ``ys`` must be strictly increasing; the cells need not be square or equal."""
    x = np.asarray(xs, dtype=np.float64).reshape(-1)
    y = np.asarray(ys, dtype=np.float64).reshape(-1)
    z = np.asarray(top, dtype=np.float64)
    if x.size < 2 or y.size < 2 or z.shape != (y.size, x.size):
        raise ValueError(f"top must be (len(ys), len(xs)) with at least 2 × 2 samples, got {z.shape} for {y.size} × {x.size}")
    if not (np.all(np.diff(x) > 0) and np.all(np.diff(y) > 0)):
        raise ValueError("xs and ys must be strictly increasing")
    if not np.all(np.isfinite(z)) or float(z.min()) <= 0:
        raise ValueError("top must be finite and above the bottom plane (> 0)")
    return _grid_solid(x, y, z)


def _grid_solid(xs: np.ndarray, ys: np.ndarray, top_z: np.ndarray) -> trimesh.Trimesh:
    nx, ny = len(xs), len(ys)
    gx, gy = np.meshgrid(xs, ys)  # (ny, nx)

    # top vertices: index t(i, j) = j * nx + i
    top = np.column_stack([gx.ravel(), gy.ravel(), top_z.ravel()])

    # perimeter ring, counter-clockwise seen from +z, starting at (i=0, j=0)
    ring_i = np.concatenate([np.arange(0, nx), np.full(ny - 2, nx - 1), np.arange(nx - 1, -1, -1), np.zeros(ny - 2, dtype=int)])
    ring_j = np.concatenate([np.zeros(nx, dtype=int), np.arange(1, ny - 1), np.full(nx, ny - 1), np.arange(ny - 2, 0, -1)])
    ring_top = ring_j * nx + ring_i  # top vertex ids around the ring
    n_ring = len(ring_top)

    bottom_ring = np.column_stack([xs[ring_i], ys[ring_j], np.zeros(n_ring)])
    centre = np.array([[(xs[0] + xs[-1]) / 2.0, (ys[0] + ys[-1]) / 2.0, 0.0]])  # the origin for a centred grid
    vertices = np.vstack([top, bottom_ring, centre])
    ring_bot = nx * ny + np.arange(n_ring)
    centre_id = nx * ny + n_ring

    # top faces (normal +z): (a, b, c), (a, c, d) per cell
    i = np.arange(nx - 1)
    j = np.arange(ny - 1)
    ii, jj = np.meshgrid(i, j)
    a = (jj * nx + ii).ravel()
    b = a + 1
    c = a + nx + 1
    d = a + nx
    top_faces = np.vstack([np.column_stack([a, b, c]), np.column_stack([a, c, d])])

    # side walls (outward): ring runs CCW from above, so (bot_k, bot_k1, top_k1) faces outward
    k = np.arange(n_ring)
    k1 = (k + 1) % n_ring
    walls = np.vstack([
        np.column_stack([ring_bot[k], ring_bot[k1], ring_top[k1]]),
        np.column_stack([ring_bot[k], ring_top[k1], ring_top[k]]),
    ])

    # bottom (normal -z): fan from the centre, reversed relative to the CCW ring
    bottom = np.column_stack([np.full(n_ring, centre_id), ring_bot[k1], ring_bot[k]])

    faces = np.vstack([top_faces, walls, bottom]).astype(np.int64)
    mesh = trimesh.Trimesh(vertices=vertices, faces=faces, process=False)
    if not (mesh.is_watertight and mesh.is_winding_consistent and mesh.volume > 0):
        raise GeometryError(
            "Heightfield solid is not closed",
            {"shape": [ny, nx], "watertight": bool(mesh.is_watertight), "volume": float(mesh.volume)},
        )
    return mesh


def heightfield_volume(heights: np.ndarray, cell_mm: float, base_mm: float) -> float:
    """Exact volume of ``heightfield_solid`` (each triangle prism = xy-area × mean corner height)."""
    h = np.asarray(heights, dtype=np.float64) + float(base_mm)
    a = h[:-1, :-1]
    b = h[:-1, 1:]
    c = h[1:, 1:]
    d = h[1:, :-1]
    cell_area = float(cell_mm) ** 2 / 2.0
    return float(cell_area * ((a + b + c) / 3.0 + (a + c + d) / 3.0).sum())


__all__ = ["grid_solid", "heightfield_solid", "heightfield_volume"]
