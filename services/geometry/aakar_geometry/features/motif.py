"""``motif`` (Buti): a motif from the library, raised or cut into a surface anchor.

**Library** (``packages/design-tokens/motifs``; ``AAKAR_MOTIFS_DIR`` in the image, else the
repo-relative folder): ``index.json`` lists every motif (``id``, plain ``label``, ``file``, ``tags``,
``min_scale``) and each SVG holds exactly one ``<path>`` (any number of closed subpaths, ``fill-rule``
``nonzero`` or ``evenodd``, no ``transform``) inside its ``viewBox``. Paths are parsed by fontTools'
``svgLib`` (every SVG path command, arcs included) into ``outlines.OutlinePen``, filled with the path's
fill rule and flipped (SVG's y runs down) so the motif stands upright in the anchor frame.

**Fit**: at ``scale`` 1 the motif's outline is as large as fits the anchor's printable area
(``size_mm − 2·bleed_mm``, or its share beside a name) and centred; ``scale`` shrinks it. Above 1 it
would run past the edge, so it is refused (``param_out_of_range``, never cropped), as is a ``scale``
under the motif's ``min_scale``: the smallest scale at which it stays printable (strokes and openings
of at least 0.8 mm) when it fills the library's 30 mm reference square. At the real size the stroke rule
(``outlines.stroke_check``, with the template's ``min_feature_mm``) then applies to the motif and to its
openings (a jaali's holes, the ring around a star's centre): a hole that would close up is refused like a
stroke that would not print. The outline is raised or cut ``depth_mm`` like a name (``outlines.set_into``). An unknown ``motif_id`` is
``invalid_spec``; a missing library is a deployment fault (``build_error``).
"""

from __future__ import annotations

import json
import logging
import os
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Any, Mapping

import trimesh
from fontTools.svgLib.path import parse_path
from shapely import affinity
from shapely.geometry.base import BaseGeometry

from ..errors import GeometryError, InvalidSpec, ParamOutOfRange
from . import outlines
from .frames import AnchorFrame

log = logging.getLogger("aakar.geometry.features.motif")

SVG_NS = "{http://www.w3.org/2000/svg}"
ID_RE = re.compile(r"^[a-z][a-z0-9_]*$")
FLATTEN_SHARE = 1.0 / 5000.0  # curve tolerance as a share of the viewBox (0.006 mm on the 30 mm reference)
MAX_SCALE = 1.0  # scale 1 fills the printable area
TOO_FINE = "This motif would be too fine to print at this size; make it larger or put it on a larger piece"


@dataclass(frozen=True)
class Motif:
    """One library entry with its outline in viewBox units (flipped: y up), centred on its ink box."""

    id: str
    label: str
    file: str
    tags: tuple[str, ...]
    min_scale: float
    fill_rule: str
    view_box: tuple[float, float, float, float]
    region: BaseGeometry

    @property
    def aspect(self) -> float:
        x0, y0, x1, y1 = self.region.bounds
        return (x1 - x0) / (y1 - y0)


@dataclass(frozen=True)
class MotifLibrary:
    source: str
    reference_mm: float
    min_stroke_mm: float
    motifs: dict[str, Motif]

    def ids(self) -> list[str]:
        return list(self.motifs)


class MotifLibraryError(GeometryError):
    """The motif library is missing or broken: a deployment fault, never the customer's."""


def motifs_dir() -> Path | None:
    """``AAKAR_MOTIFS_DIR`` when set, else ``packages/design-tokens/motifs`` in the repo (None when absent)."""
    env = os.environ.get("AAKAR_MOTIFS_DIR")
    if env:
        return Path(env).expanduser()
    candidate = (Path(__file__).resolve().parents[2] / "../../packages/design-tokens/motifs").resolve()
    return candidate if candidate.exists() else None


def _svg_attr_fill_rule(path: ET.Element) -> str:
    rule = path.get("fill-rule")
    style = path.get("style") or ""
    match = re.search(r"fill-rule\s*:\s*(nonzero|evenodd)", style)
    if match:
        rule = match.group(1)
    return rule or "nonzero"


def parse_svg(data: bytes | str, name: str = "motif") -> tuple[BaseGeometry, str, tuple[float, float, float, float]]:
    """One-path SVG → ``(outline in viewBox units flipped y-up, fill rule, viewBox)``; ``ValueError`` when the
    file breaks the library format."""
    try:
        root = ET.fromstring(data)
    except ET.ParseError as exc:
        raise ValueError(f"{name}: not an SVG document ({exc})") from exc
    if root.tag != f"{SVG_NS}svg":
        raise ValueError(f"{name}: the root element must be an SVG <svg> in the SVG namespace")
    view_box = root.get("viewBox")
    if not view_box:
        raise ValueError(f"{name}: needs a viewBox")
    try:
        vb = tuple(float(v) for v in re.split(r"[\s,]+", view_box.strip()))
    except ValueError as exc:
        raise ValueError(f"{name}: unreadable viewBox {view_box!r}") from exc
    if len(vb) != 4 or vb[2] <= 0 or vb[3] <= 0:
        raise ValueError(f"{name}: viewBox must be four numbers with a positive width and height")
    shapes = [el for el in root.iter() if el.tag.split("}")[-1] in ("path", "rect", "circle", "ellipse", "polygon", "polyline", "line", "use", "text")]
    paths = [el for el in shapes if el.tag == f"{SVG_NS}path"]
    if len(paths) != 1 or len(shapes) != 1:
        raise ValueError(f"{name}: a motif is exactly one <path> ({len(shapes)} drawing elements found)")
    if any(el.get("transform") for el in root.iter()):
        raise ValueError(f"{name}: transforms are not allowed; bake them into the path")
    path = paths[0]
    d = path.get("d") or ""
    rule = _svg_attr_fill_rule(path)
    if rule not in outlines.FILL_RULES:
        raise ValueError(f"{name}: fill-rule must be nonzero or evenodd")
    pen = outlines.OutlinePen(FLATTEN_SHARE * max(vb[2], vb[3]))
    try:
        parse_path(d, pen)
    except Exception as exc:  # fontTools raises ValueError / IndexError on malformed data
        raise ValueError(f"{name}: unreadable path data ({type(exc).__name__}: {exc})") from exc
    region = outlines.fill(pen.contours, rule)
    if region.is_empty:
        raise ValueError(f"{name}: the path encloses no area")
    flipped = affinity.scale(region, 1.0, -1.0, origin=(0.0, 0.0))  # SVG y runs down; the anchor's +y is up
    x0, y0, x1, y1 = flipped.bounds
    centred = affinity.translate(flipped, -(x0 + x1) / 2.0, -(y0 + y1) / 2.0)
    return outlines.clean(centred), rule, vb  # type: ignore[return-value]


def _library_error(message: str, detail: dict[str, Any]) -> MotifLibraryError:
    return MotifLibraryError("Motifs are not available right now; please try again later", {"reason": message, **detail})


@lru_cache(maxsize=4)
def _load(folder: str) -> MotifLibrary:
    root = Path(folder)
    index_path = root / "index.json"
    try:
        index = json.loads(index_path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise _library_error("index.json is missing", {"path": str(index_path)}) from exc
    except json.JSONDecodeError as exc:
        raise _library_error(f"index.json is not JSON: {exc}", {"path": str(index_path)}) from exc
    entries = index.get("motifs")
    if not isinstance(entries, list) or not entries:
        raise _library_error("index.json lists no motifs", {"path": str(index_path)})
    motifs: dict[str, Motif] = {}
    for entry in entries:
        motif_id = entry.get("id") if isinstance(entry, Mapping) else None
        if not isinstance(motif_id, str) or not ID_RE.match(motif_id) or motif_id in motifs:
            raise _library_error("a motif id is missing, malformed or repeated", {"entry": entry})
        file = entry.get("file") or f"{motif_id}.svg"
        min_scale = float(entry.get("min_scale", 0.2))
        if not 0.2 <= min_scale <= MAX_SCALE:
            raise _library_error("min_scale must be between 0.2 and 1", {"motif": motif_id, "min_scale": min_scale})
        try:
            region, rule, vb = parse_svg((root / file).read_bytes(), file)
        except FileNotFoundError as exc:
            raise _library_error("a motif file is missing", {"motif": motif_id, "file": file}) from exc
        except ValueError as exc:
            raise _library_error(str(exc), {"motif": motif_id, "file": file}) from exc
        motifs[motif_id] = Motif(
            motif_id,
            str(entry.get("label") or motif_id.replace("_", " ").capitalize()),
            file,
            tuple(str(t) for t in entry.get("tags") or ()),
            min_scale,
            rule,
            vb,
            region,
        )
    return MotifLibrary(
        str(root),
        float(index.get("reference_mm", 30.0)),
        float(index.get("min_stroke_mm", outlines.DEFAULT_MIN_FEATURE_MM)),
        motifs,
    )


def load_library() -> MotifLibrary:
    folder = motifs_dir()
    if folder is None or not folder.exists():
        raise _library_error("the motif library folder was not found", {"path": str(folder) if folder else None, "env": "AAKAR_MOTIFS_DIR"})
    return _load(str(folder.resolve()))


def entry(motif_id: str, key: str = "motif_id") -> Motif:
    """The library entry for ``motif_id`` or a customer-safe ``InvalidSpec``."""
    library = load_library()
    try:
        return library.motifs[motif_id]
    except KeyError:
        raise InvalidSpec(
            f"We don't have a motif called “{motif_id}”; choose one from the motif library",
            {"key": key, "motif_id": motif_id, "known": library.ids()},
        ) from None


def check_scale(found: Motif, scale: float, *, anchor_label: str, key: str) -> None:
    """``scale`` within ``[min_scale, 1]``, refused on ``key`` otherwise (never clamped)."""
    place = anchor_label[:1].lower() + anchor_label[1:]
    if scale > MAX_SCALE + 1e-9:
        raise ParamOutOfRange(
            [key],
            f"At scale {scale:g} the {found.label} motif would run past the edge of the {place}; scale 1 fills it",
            {"scale": scale, "max_scale": MAX_SCALE, "motif_id": found.id},
        )
    if scale < found.min_scale - 1e-9:
        raise ParamOutOfRange(
            [key],
            f"The {found.label} motif can't be printed smaller than scale {found.min_scale:g}; choose a larger scale",
            {"scale": scale, "min_scale": found.min_scale, "motif_id": found.id},
        )


@dataclass(frozen=True)
class FittedMotif:
    motif: Motif
    region: BaseGeometry  # mm, anchor frame
    size_mm: tuple[float, float]
    stroke: outlines.StrokeCheck
    openings: outlines.StrokeCheck | None  # None when the motif has no openings

    @property
    def ok(self) -> bool:
        return self.stroke.ok and (self.openings is None or self.openings.ok)


def fit_motif(
    found: Motif,
    *,
    box: tuple[float, float, float, float],
    scale: float = 1.0,
    min_feature_mm: float = outlines.DEFAULT_MIN_FEATURE_MM,
    key: str = "features[0]",
    check_stroke: bool = True,
) -> FittedMotif:
    """``found`` contain-fitted into ``box`` = ``(cx, cy, width, height)`` × ``scale``, stroke rule applied to
    the motif and its openings."""
    cx, cy, box_w, box_h = (float(v) for v in box)
    x0, y0, x1, y1 = found.region.bounds
    k = min(box_w / (x1 - x0), box_h / (y1 - y0)) * float(scale)
    region = affinity.translate(affinity.scale(found.region, k, k, origin=(0.0, 0.0)), cx, cy)
    region = outlines.clean(region)
    stroke = outlines.stroke_check(region, min_feature_mm)
    gaps = outlines.openings(region)
    gap_check = outlines.stroke_check(gaps, min_feature_mm) if not gaps.is_empty else None
    fitted = FittedMotif(found, region, ((x1 - x0) * k, (y1 - y0) * k), stroke, gap_check)
    if check_stroke and not fitted.ok:
        raise ParamOutOfRange(
            [f"{key}.scale"],
            TOO_FINE,
            {
                "motif_id": found.id,
                "scale": scale,
                "size_mm": [round(fitted.size_mm[0], 2), round(fitted.size_mm[1], 2)],
                **stroke.detail(),
                "openings": gap_check.detail() if gap_check is not None else None,
            },
        )
    return fitted


def prepare(
    feature: Mapping[str, Any],
    *,
    box: tuple[float, float, float, float],
    anchor: Any,
    min_feature_mm: float | None,
    key: str,
) -> FittedMotif:
    """Look up, scale-check and fit one ``motif`` feature into ``box`` (no CAD): every refusal happens here."""
    found = entry(str(feature.get("motif_id")), f"{key}.motif_id")
    scale = float(feature.get("scale", 1.0))
    check_scale(found, scale, anchor_label=getattr(anchor, "label", "spot"), key=f"{key}.scale")
    return fit_motif(found, box=box, scale=scale, min_feature_mm=float(min_feature_mm or outlines.DEFAULT_MIN_FEATURE_MM), key=key)


def apply(body: trimesh.Trimesh, frame: AnchorFrame, feature: Mapping[str, Any], fitted: FittedMotif) -> trimesh.Trimesh:
    """Raise or cut the fitted motif into ``body`` at ``frame``; returns a new watertight mesh."""
    return outlines.set_into(
        body,
        frame,
        fitted.region,
        depth_mm=float(feature.get("depth_mm", 1.0)),
        mode=str(feature.get("mode", "deboss")),
        noun="motif",
        detail={"anchor": feature.get("anchor"), "motif_id": fitted.motif.id},
    )


__all__ = [
    "FittedMotif",
    "MAX_SCALE",
    "Motif",
    "MotifLibrary",
    "MotifLibraryError",
    "TOO_FINE",
    "apply",
    "check_scale",
    "entry",
    "fit_motif",
    "load_library",
    "motifs_dir",
    "parse_svg",
    "prepare",
]
