"""``relief_image`` (Chhavi): a photo becomes a heightfield relief on a surface anchor.

Pipeline: Pillow decode (HEIC through pi-heif) → grayscale (alpha multiplies in, so transparent
pixels have no height; ``invert`` swaps light and dark) → fit into ``size_mm − 2·bleed_mm`` (``contain`` letterboxes,
``cover`` centre-crops) at ≈5 px/mm capped at 400 px on the long side → light gaussian smoothing
(scipy, else a numpy box blur) → normalised heights of ``relief_mm`` → ``heightfield_solid`` in the
anchor frame → ``emboss`` unions it with the body, ``deboss`` subtracts it. ``lithophane`` does not
fuse anything: ``relief_heightmap`` hands the map to the template (``Template.lithophane``).

Bright pixels stand proud (emboss) or are cut deepest (deboss). Every solid sinks ``EMBED_MM`` into
the body so the boolean has volume to grab, and the darkest pixel keeps ``FLOOR_MM`` so no top face
is coplanar with the body's skin.
"""

from __future__ import annotations

import importlib
import io
import logging
from dataclasses import dataclass
from functools import lru_cache
from typing import Any, Mapping

import numpy as np
import trimesh

from ..errors import ContentUnusable, GeometryError
from .booleans import difference, union
from .frames import AnchorFrame
from .heightfield import heightfield_solid

log = logging.getLogger("aakar.geometry.features.relief_image")

PX_PER_MM = 5.0
MAX_PX = 400
MIN_PX = 8
MAX_IMAGE_PIXELS = 40_000_000  # ~6300 × 6300; larger photos are refused before decoding
EMBED_MM = 0.5
FLOOR_MM = 0.05
SMOOTH_SIGMA_PX = 0.8
MIN_CONTRAST = 1e-3


@dataclass(frozen=True)
class ReliefMap:
    heights: np.ndarray  # (ny, nx) mm, >= 0, row 0 at the bottom of the picture (+y up in the anchor frame)
    cell_mm: float
    width_mm: float
    height_mm: float

    @property
    def shape(self) -> tuple[int, int]:
        return (int(self.heights.shape[0]), int(self.heights.shape[1]))


HEIF_DECODERS = ("pi_heif", "pillow_heif")  # pi-heif ships (decode-only, LGPL libs); pillow-heif is dev-only (GPLv2 wheels)


@lru_cache(maxsize=1)
def register_heif() -> str | None:
    """Teach Pillow to open HEIC/HEIF (every iPhone photo); returns the module used, None when neither is installed."""
    for module in HEIF_DECODERS:
        try:
            opener = importlib.import_module(module).register_heif_opener
        except ImportError:
            continue
        opener()
        return module
    log.warning("neither pi-heif nor pillow-heif is installed; HEIC photos cannot be read")  # pragma: no cover
    return None  # pragma: no cover


def decode_image(data: bytes, fmt: str | None = None) -> np.ndarray:
    """Photo bytes (PNG, JPEG, WebP, HEIC) → float32 array in 0..1 (luminance × alpha), EXIF orientation
    applied, or ``ContentUnusable``."""
    try:
        from PIL import Image, ImageOps, UnidentifiedImageError
    except ImportError as exc:  # pragma: no cover
        raise GeometryError("Pillow is not installed", {"error": str(exc)}) from exc
    register_heif()

    try:
        img = Image.open(io.BytesIO(data))
        width, height = img.size
        if width <= 0 or height <= 0:
            raise ContentUnusable("This photo is empty; try another one", {"size": [width, height], "format": fmt})
        if width * height > MAX_IMAGE_PIXELS:
            raise ContentUnusable(
                "This photo is too large; please resize it to under 6000 × 6000 pixels",
                {"size": [width, height], "max_pixels": MAX_IMAGE_PIXELS},
            )
        img = ImageOps.exif_transpose(img) or img
        has_alpha = img.mode in ("RGBA", "LA", "PA") or (img.mode == "P" and "transparency" in img.info)
        if has_alpha:
            rgba = img.convert("RGBA")
            lum = np.asarray(rgba.convert("L"), dtype=np.float32) / 255.0
            alpha = np.asarray(rgba.getchannel("A"), dtype=np.float32) / 255.0
            arr = lum * alpha
        else:
            arr = np.asarray(img.convert("L"), dtype=np.float32) / 255.0
    except ContentUnusable:
        raise
    except (UnidentifiedImageError, Image.DecompressionBombError, OSError, ValueError, SyntaxError) as exc:
        raise ContentUnusable(
            "We couldn't read this photo; please try a JPEG or PNG",
            {"format": fmt, "error": f"{type(exc).__name__}: {exc}"},
        ) from exc
    if arr.ndim != 2 or arr.size == 0:
        raise ContentUnusable("This photo is empty; try another one", {"format": fmt})
    return arr


def _smooth(arr: np.ndarray, sigma_px: float) -> np.ndarray:
    if sigma_px <= 0:
        return arr
    try:
        from scipy.ndimage import gaussian_filter

        return gaussian_filter(arr, sigma=sigma_px, mode="nearest")
    except Exception:  # pragma: no cover - scipy missing: two passes of a 3 × 3 box blur
        out = arr
        for _ in range(2):
            padded = np.pad(out, 1, mode="edge")
            out = sum(padded[1 + dy : padded.shape[0] - 1 + dy, 1 + dx : padded.shape[1] - 1 + dx] for dy in (-1, 0, 1) for dx in (-1, 0, 1)) / 9.0
        return out


def _resample(arr: np.ndarray, nx: int, ny: int, fit: str) -> np.ndarray:
    from PIL import Image, ImageOps

    img = Image.fromarray(np.ascontiguousarray(arr, dtype=np.float32), mode="F")
    if fit == "cover":
        img = ImageOps.fit(img, (nx, ny), method=Image.Resampling.LANCZOS, centering=(0.5, 0.5))
    else:
        img = img.resize((nx, ny), resample=Image.Resampling.LANCZOS)
    return np.asarray(img, dtype=np.float64)


def grid_for(avail_w: float, avail_h: float, aspect: float, fit: str, px_per_mm: float, max_px: int) -> tuple[int, int, float]:
    """(nx, ny, cell_mm) for a picture of ``aspect`` (w/h) fitted into ``avail_w × avail_h`` mm."""
    if fit == "cover":
        target_w, target_h = avail_w, avail_h
    elif avail_w / avail_h > aspect:  # area is wider than the picture: height-bound
        target_h, target_w = avail_h, avail_h * aspect
    else:
        target_w, target_h = avail_w, avail_w / aspect
    long_mm = max(target_w, target_h)
    n_long = int(min(max(round(long_mm * px_per_mm), MIN_PX), max_px))
    cell = long_mm / (n_long - 1)
    nx = max(2, int(round(target_w / cell)) + 1)
    ny = max(2, int(round(target_h / cell)) + 1)
    return nx, ny, cell


def relief_heightmap(
    data: bytes,
    size_mm: tuple[float, float],
    *,
    bleed_mm: float = 0.0,
    fit: str = "contain",
    invert: bool = False,
    relief_mm: float = 0.6,
    px_per_mm: float = PX_PER_MM,
    max_px: int = MAX_PX,
    floor_mm: float = FLOOR_MM,
    fmt: str | None = None,
) -> ReliefMap:
    """Photo bytes → ``ReliefMap`` fitted into the anchor's printable area. Also the lithophane entry point."""
    avail_w = float(size_mm[0]) - 2.0 * float(bleed_mm)
    avail_h = float(size_mm[1]) - 2.0 * float(bleed_mm)
    if avail_w < 1.0 or avail_h < 1.0:
        raise GeometryError(
            "The anchor's printable area is too small for a relief",
            {"size_mm": list(size_mm), "bleed_mm": bleed_mm, "available_mm": [avail_w, avail_h]},
        )
    if fit not in ("contain", "cover"):
        raise GeometryError("relief fit must be contain or cover", {"fit": fit})
    arr = decode_image(data, fmt)
    if invert:
        arr = 1.0 - arr
    h_px, w_px = arr.shape
    nx, ny, cell = grid_for(avail_w, avail_h, w_px / h_px, fit, px_per_mm, max_px)
    # Lanczos rings past hard edges (e.g. a transparent border): clip back to the source's 0..1 so an
    # undershoot cannot become the minimum and lift every truly dark or transparent pixel off the floor
    grid = np.clip(_resample(arr, nx, ny, fit), 0.0, 1.0)
    grid = _smooth(grid, SMOOTH_SIGMA_PX)
    lo, hi = float(grid.min()), float(grid.max())
    if hi - lo < MIN_CONTRAST:
        raise ContentUnusable(
            "This photo has no light and dark to shape a relief from; try one with more contrast",
            {"min": lo, "max": hi, "format": fmt},
        )
    norm = (grid - lo) / (hi - lo)
    relief = float(relief_mm)
    floor = min(float(floor_mm), relief / 4.0)
    heights = floor + norm * (relief - floor)
    heights = np.flipud(heights)  # picture row 0 is the top; the frame's +y is up
    return ReliefMap(heights=np.ascontiguousarray(heights), cell_mm=cell, width_mm=(nx - 1) * cell, height_mm=(ny - 1) * cell)


def relief_solid(relief: ReliefMap, base_mm: float = EMBED_MM) -> trimesh.Trimesh:
    """The relief as a local-frame solid: z from 0 (``base_mm`` below the skin) up to ``base_mm + heights``."""
    return heightfield_solid(relief.heights, relief.cell_mm, base_mm)


def apply(
    body: trimesh.Trimesh,
    frame: AnchorFrame,
    feature: Mapping[str, Any],
    data: bytes,
    *,
    bleed_mm: float = 0.0,
    fmt: str | None = None,
) -> trimesh.Trimesh:
    """Fuse (emboss) or cut (deboss) the photo relief into ``body`` at ``frame``. Returns a new watertight mesh;
    a boolean that fails is reported as ``ContentUnusable`` with a customer-safe message."""
    if body is None or body.is_empty:
        raise GeometryError("A relief needs a body to sit on", {"anchor": feature.get("anchor")})
    if frame.size_mm is None:
        raise GeometryError("Surface anchor frame has no size_mm", {"anchor": feature.get("anchor")})
    mode = feature.get("mode", "emboss")
    if mode not in ("emboss", "deboss"):
        raise GeometryError("relief_image.apply handles emboss and deboss only", {"mode": mode})
    relief = relief_heightmap(
        data,
        frame.size_mm,
        bleed_mm=bleed_mm,
        fit=feature.get("fit", "contain"),
        invert=bool(feature.get("invert", False)),
        relief_mm=float(feature.get("relief_mm", 0.6)),
        fmt=fmt,
    )
    if mode == "emboss":
        solid = relief_solid(relief, EMBED_MM)
        placed = frame.offset(-EMBED_MM).place(solid)  # skin at local z = EMBED, so the solid starts inside the body
        return _fuse(union, body, placed, feature)
    # deboss: mirror the picture left-right and look into the body, starting EMBED above the skin
    cutter = heightfield_solid(np.ascontiguousarray(relief.heights[:, ::-1]), relief.cell_mm, EMBED_MM)
    placed = frame.offset(EMBED_MM).flipped().place(cutter)
    return _fuse(difference, body, placed, feature)


def _fuse(operation: Any, body: trimesh.Trimesh, tool: trimesh.Trimesh, feature: Mapping[str, Any]) -> trimesh.Trimesh:
    """Run the boolean; a failure is the photo's to report (``ContentUnusable``, customer-safe), not a build error."""
    try:
        return operation(body, tool)
    except GeometryError as exc:
        raise ContentUnusable(
            "We couldn't set this photo into the piece; try another photo or a different depth",
            {"error": exc.message, "anchor": feature.get("anchor"), "mode": feature.get("mode", "emboss"), **exc.detail},
        ) from exc


__all__ = [
    "EMBED_MM",
    "FLOOR_MM",
    "HEIF_DECODERS",
    "MAX_PX",
    "PX_PER_MM",
    "ReliefMap",
    "apply",
    "decode_image",
    "grid_for",
    "register_heif",
    "relief_heightmap",
    "relief_solid",
]
