"""``AnchorFrame``: where content lands on a body, as a right-handed orthonormal frame in mm.

Local coordinates: x along ``u`` (the viewer's right when looking at the anchor), y along ``v``
(up in the picture), z along ``normal``. For a **surface** anchor the origin is the centre of the
printable rectangle on the body's skin and ``normal`` points out of the body; ``size_mm`` is the
printable width × height. For a **volume** anchor the origin is the centre of the volume's bottom
plane (the plane a hero form is seated on), ``normal`` is up and ``bounds_mm`` is width × depth ×
height. Templates return frames from ``Template.anchor_frame(anchor_id, params)``.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Sequence

import numpy as np
import trimesh

ArrayLike = Sequence[float] | np.ndarray


def _unit(vec: ArrayLike, name: str) -> np.ndarray:
    arr = np.asarray(vec, dtype=np.float64).reshape(3)
    norm = float(np.linalg.norm(arr))
    if not np.isfinite(norm) or norm < 1e-12:
        raise ValueError(f"AnchorFrame.{name} must be a non-zero vector, got {arr.tolist()}")
    return arr / norm


@dataclass(frozen=True)
class AnchorFrame:
    origin: np.ndarray
    u: np.ndarray
    v: np.ndarray
    normal: np.ndarray
    size_mm: tuple[float, float] | None = None
    bounds_mm: tuple[float, float, float] | None = None

    def __post_init__(self) -> None:
        origin = np.asarray(self.origin, dtype=np.float64).reshape(3)
        u = _unit(self.u, "u")
        normal = _unit(self.normal, "normal")
        if abs(float(np.dot(u, normal))) > 1e-6:
            raise ValueError("AnchorFrame.u must be perpendicular to AnchorFrame.normal")
        v = np.cross(normal, u)  # right-handed: u × v == normal
        v_given = np.asarray(self.v, dtype=np.float64).reshape(3)
        if np.linalg.norm(v_given) > 1e-12 and float(np.dot(_unit(v_given, "v"), v)) < 1.0 - 1e-6:
            raise ValueError("AnchorFrame.v must equal normal × u (right-handed frame)")
        object.__setattr__(self, "origin", origin)
        object.__setattr__(self, "u", u)
        object.__setattr__(self, "v", v)
        object.__setattr__(self, "normal", normal)
        if self.size_mm is not None:
            size = tuple(float(s) for s in self.size_mm)
            if len(size) != 2 or any(s <= 0 for s in size):
                raise ValueError(f"AnchorFrame.size_mm must be two positive lengths, got {self.size_mm}")
            object.__setattr__(self, "size_mm", size)
        if self.bounds_mm is not None:
            bounds = tuple(float(b) for b in self.bounds_mm)
            if len(bounds) != 3 or any(b <= 0 for b in bounds):
                raise ValueError(f"AnchorFrame.bounds_mm must be three positive lengths, got {self.bounds_mm}")
            object.__setattr__(self, "bounds_mm", bounds)

    @classmethod
    def from_normal(
        cls,
        origin: ArrayLike,
        normal: ArrayLike,
        u_hint: ArrayLike | None = None,
        **kwargs: Any,
    ) -> "AnchorFrame":
        """Frame from an outward normal; ``u`` is the hint projected onto the plane (default: X, or Z for a
        horizontal normal... i.e. whichever world axis is least aligned with the normal)."""
        n = _unit(normal, "normal")
        if u_hint is None:
            u_hint = np.array([1.0, 0.0, 0.0]) if abs(n[0]) < 0.9 else np.array([0.0, 1.0, 0.0])
        hint = np.asarray(u_hint, dtype=np.float64).reshape(3)
        u = hint - np.dot(hint, n) * n
        if np.linalg.norm(u) < 1e-9:
            raise ValueError("u_hint is parallel to the normal")
        u = u / np.linalg.norm(u)
        return cls(np.asarray(origin, dtype=np.float64), u, np.cross(n, u), n, **kwargs)

    def matrix(self) -> np.ndarray:
        """4 × 4 homogeneous transform taking local (x along u, y along v, z along normal) to world."""
        m = np.eye(4)
        m[:3, 0] = self.u
        m[:3, 1] = self.v
        m[:3, 2] = self.normal
        m[:3, 3] = self.origin
        return m

    def to_world(self, points: ArrayLike) -> np.ndarray:
        pts = np.asarray(points, dtype=np.float64).reshape(-1, 3)
        return pts @ np.column_stack([self.u, self.v, self.normal]).T + self.origin

    def to_local(self, points: ArrayLike) -> np.ndarray:
        pts = np.asarray(points, dtype=np.float64).reshape(-1, 3) - self.origin
        return pts @ np.column_stack([self.u, self.v, self.normal])

    def place(self, mesh: trimesh.Trimesh) -> trimesh.Trimesh:
        """Copy of a local-frame mesh moved into world coordinates (a proper rotation: winding is kept)."""
        placed = mesh.copy()
        placed.apply_transform(self.matrix())
        return placed

    def offset(self, dz: float) -> "AnchorFrame":
        """The same frame moved ``dz`` mm along its normal."""
        return AnchorFrame(self.origin + dz * self.normal, self.u, self.v, self.normal, self.size_mm, self.bounds_mm)

    def flipped(self) -> "AnchorFrame":
        """The frame looking into the body (normal and u reversed, v kept: still right-handed)."""
        return AnchorFrame(self.origin, -self.u, self.v, -self.normal, self.size_mm, self.bounds_mm)

    def corners(self) -> np.ndarray:
        """World positions of the printable rectangle's four corners (surface anchors with ``size_mm``)."""
        if self.size_mm is None:
            raise ValueError("this frame has no size_mm")
        w, h = self.size_mm
        local = np.array([[-w / 2, -h / 2, 0], [w / 2, -h / 2, 0], [w / 2, h / 2, 0], [-w / 2, h / 2, 0]])
        return self.to_world(local)


__all__ = ["AnchorFrame"]
