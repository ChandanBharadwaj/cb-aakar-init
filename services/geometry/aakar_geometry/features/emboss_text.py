"""``emboss_text`` (Naam): a name shaped with HarfBuzz, drawn as outlines, raised or cut into a surface anchor.

Pipeline (PLAN §7.5, ADR-0003):

1. **Font** (``resolve_script``): the seven launch scripts each have one bundled Noto Sans Bold font
   (``aakar_geometry/fonts``). ``script`` picks it; without one the script is detected from the letters
   (Unicode blocks). A name uses one script: letters from two scripts, letters from any other script,
   or a ``script`` that does not match the letters are refused with a customer-safe ``invalid_spec``.
   Digits, spaces and punctuation that a script's font lacks (Gujarati and Bengali have no ASCII digits)
   come from Noto Sans. ``font`` may only name the bundled lettering (``Noto Sans`` or the script's own
   ``Noto Sans <Script>``); anything else is ``unsupported_feature``.
2. **Shape** (``shape_text``) with uharfbuzz, direction, script and language set explicitly so the result
   never depends on the server's locale: conjuncts, half forms, vowel signs and reordering happen here,
   before any outline exists.
3. **Outlines**: HarfBuzz draws every glyph at its shaped position into ``outlines.OutlinePen`` (curves
   flattened to 1/2000 em); each glyph is filled with the nonzero rule, the glyphs are unioned.
4. **Fit** (``fit_text``): ``height_mm`` is the height of the lettering as printed, i.e. its ink box from
   the lowest to the highest point (descenders and vowel signs above or below count). Without
   ``height_mm`` the lettering is the largest that fits the anchor's printable area
   (``size_mm − 2·bleed_mm``, or its share of it when a motif sits beside it) and ``max_text_height_mm``;
   it is centred there. Text that would be under ``MIN_TEXT_HEIGHT_MM`` (the contract's smallest
   ``height_mm``) is refused as too long; a ``height_mm`` that does not fit is refused on that key.
5. **Stroke rule** (``outlines.stroke_check``) against the template's ``min_feature_mm`` (0.8 mm when the
   template does not say): "These letters would be too thin to print at this size; try fewer letters or
   a larger piece".
6. **Relief**: extruded ``depth_mm`` (raised ones start ``EMBED_MM`` inside the body) and unioned
   (emboss) or subtracted (deboss) in the anchor frame by ``outlines.set_into``; reads left to right from
   outside either way.

Only ``planar`` projection exists: no anchor is curved yet, so ``cylindrical`` and ``conformal`` are
refused as ``unsupported_feature`` by ``features.validate``.
"""

from __future__ import annotations

import logging
import unicodedata
from dataclasses import dataclass
from functools import lru_cache
from importlib import resources
from typing import Any, Mapping

import numpy as np
import trimesh
import uharfbuzz as hb
from shapely import affinity
from shapely.geometry import Polygon
from shapely.geometry.base import BaseGeometry
from shapely.ops import unary_union

from ..errors import GeometryError, InvalidSpec, ParamOutOfRange, UnsupportedFeature
from . import outlines
from .frames import AnchorFrame

log = logging.getLogger("aakar.geometry.features.emboss_text")

MIN_TEXT_HEIGHT_MM = 4.0  # design-spec emboss_text.height_mm minimum: smaller lettering is not offered
FLATTEN_EM = 1.0 / 2000.0  # curve flattening tolerance as a share of the em (0.025 mm on 50 mm lettering)
TOO_THIN = "These letters would be too thin to print at this size; try fewer letters or a larger piece"
JOINERS = frozenset({0x200C, 0x200D})  # ZWNJ / ZWJ steer Indic shaping and print nothing
LATIN = "latin"


@dataclass(frozen=True)
class ScriptFont:
    """One launch script: the contract id, its bundled font, the HarfBuzz script and language tags."""

    id: str
    label: str  # customer-facing script name
    file: str
    family: str
    ot_script: str  # ISO 15924
    language: str  # BCP 47, so locale-specific forms never depend on the server
    ranges: tuple[tuple[int, int], ...]  # the letters and signs of the script (Unicode blocks)

    def owns(self, cp: int) -> bool:
        return any(lo <= cp <= hi for lo, hi in self.ranges)


SCRIPTS: dict[str, ScriptFont] = {
    s.id: s
    for s in (
        ScriptFont(
            "latin", "Latin", "NotoSans-Bold.ttf", "Noto Sans", "Latn", "en",
            ((0x41, 0x5A), (0x61, 0x7A), (0xAA, 0xAA), (0xBA, 0xBA), (0xC0, 0xD6), (0xD8, 0xF6), (0xF8, 0x24F), (0x1E00, 0x1EFF)),
        ),
        # the dandas U+0964/U+0965 are shared by every Indian script, so they count as punctuation
        ScriptFont(
            "devanagari", "Devanagari", "NotoSansDevanagari-Bold.ttf", "Noto Sans Devanagari", "Deva", "hi",
            ((0x900, 0x963), (0x966, 0x97F), (0xA8E0, 0xA8FF), (0x1CD0, 0x1CFF)),
        ),
        ScriptFont("telugu", "Telugu", "NotoSansTelugu-Bold.ttf", "Noto Sans Telugu", "Telu", "te", ((0xC00, 0xC7F),)),
        ScriptFont("tamil", "Tamil", "NotoSansTamil-Bold.ttf", "Noto Sans Tamil", "Taml", "ta", ((0xB80, 0xBFF), (0x11FC0, 0x11FFF))),
        ScriptFont("kannada", "Kannada", "NotoSansKannada-Bold.ttf", "Noto Sans Kannada", "Knda", "kn", ((0xC80, 0xCFF),)),
        ScriptFont("bengali", "Bengali", "NotoSansBengali-Bold.ttf", "Noto Sans Bengali", "Beng", "bn", ((0x980, 0x9FF),)),
        ScriptFont("gujarati", "Gujarati", "NotoSansGujarati-Bold.ttf", "Noto Sans Gujarati", "Gujr", "gu", ((0xA80, 0xAFF),)),
    )
}
SCRIPT_IDS = tuple(SCRIPTS)


# --------------------------------------------------------------------------- fonts


@dataclass(frozen=True)
class LoadedFont:
    face: Any  # uharfbuzz.Face (immutable, shared between threads; a Font is made per shaping call)
    upem: int
    unicodes: frozenset[int]


def font_bytes(file: str) -> bytes:
    """A bundled font file (package data, so it ships in the wheel and the image)."""
    return resources.files("aakar_geometry").joinpath("fonts", file).read_bytes()


@lru_cache(maxsize=None)
def load_font(file: str) -> LoadedFont:
    try:
        face = hb.Face(hb.Blob(font_bytes(file)))
    except (FileNotFoundError, OSError) as exc:  # pragma: no cover - a broken install
        raise GeometryError("The lettering fonts are missing from this build", {"font": file, "error": str(exc)}) from exc
    return LoadedFont(face, int(face.upem), frozenset(face.unicodes))


def fonts_available() -> dict[str, bool]:
    """Script id → whether its font loads (health checks and tests)."""
    out = {}
    for script in SCRIPTS.values():
        try:
            out[script.id] = bool(load_font(script.file).unicodes)
        except GeometryError:  # pragma: no cover
            out[script.id] = False
    return out


# --------------------------------------------------------------------------- script detection


def normalise_text(text: Any) -> str:
    """NFC, surrounding spaces trimmed (the storefront trims too); one line only."""
    value = unicodedata.normalize("NFC", str(text or "")).strip()
    return value


def _is_letter(ch: str) -> bool:
    return unicodedata.category(ch)[0] in ("L", "M")


def script_of(ch: str) -> str | None:
    """The launch script a letter or sign belongs to, or None (digits, spaces, punctuation, symbols, other scripts)."""
    cp = ord(ch)
    for script in SCRIPTS.values():
        if script.owns(cp):
            return script.id
    return None


def detect_scripts(text: str) -> tuple[list[str], list[str]]:
    """``(scripts, foreign)``: the launch scripts whose letters appear, in order of first appearance, and the
    letters that belong to none of them (Greek, Arabic, emoji modifiers are not letters, ...)."""
    scripts: list[str] = []
    foreign: list[str] = []
    for ch in text:
        found = script_of(ch)
        if found is not None:
            if found not in scripts:
                scripts.append(found)
        elif unicodedata.category(ch)[0] == "L" and ch not in foreign:
            foreign.append(ch)
    return scripts, foreign


def detect_script(text: str) -> str:
    """The one launch script of ``text`` (Latin when it has no letters at all, e.g. "2024"); raises like
    ``resolve_script`` when it mixes scripts or has letters from another script."""
    return resolve_script(text).id


def _font_choice(font: Any, script: ScriptFont, key: str) -> None:
    """``font`` may only name the bundled lettering for this script."""
    if font is None or str(font).strip() == "":
        return
    wanted = "".join(ch for ch in str(font).lower() if ch.isalnum())
    own = "".join(ch for ch in script.family.lower() if ch.isalnum())
    allowed = {"notosans", "notosansbold", own, own + "bold"}
    if wanted not in allowed:
        raise UnsupportedFeature(
            f"The lettering style “{font}” is not available yet; leave the font empty for our standard {script.label} lettering",
            {"font": font, "available": sorted({"Noto Sans", script.family}), "key": f"{key}.font"},
        )


def resolve_script(text: str, script: str | None = None, font: Any = None, key: str = "text") -> ScriptFont:
    """The ``ScriptFont`` to shape ``text`` with, or a customer-safe refusal (see the module docstring)."""
    if not text:
        raise InvalidSpec("Type the name or words to print", {"key": f"{key}.text"})
    control = sorted({ch for ch in text if unicodedata.category(ch) == "Cc"})
    if control:
        raise InvalidSpec("Names go on a single line", {"key": f"{key}.text", "characters": [f"U+{ord(c):04X}" for c in control]})
    scripts, foreign = detect_scripts(text)
    if foreign:
        raise InvalidSpec(
            f"We can print Latin letters and six Indian scripts (Devanagari, Telugu, Tamil, Kannada, Bengali, Gujarati); "
            f"“{''.join(foreign[:3])}” is not one of them yet",
            {"key": f"{key}.text", "letters": foreign[:10]},
        )
    if len(scripts) > 1:
        labels = " and ".join(SCRIPTS[s].label for s in scripts[:2]) if len(scripts) == 2 else ", ".join(SCRIPTS[s].label for s in scripts)
        raise InvalidSpec(
            f"Please write the name in one script: this mixes {labels} letters. Each can go on its own spot",
            {"key": f"{key}.text", "scripts": scripts},
        )
    detected = scripts[0] if scripts else None
    if script is not None:
        if script not in SCRIPTS:
            raise InvalidSpec(f"Unknown script {script!r}", {"key": f"{key}.script", "scripts": list(SCRIPT_IDS)})
        if detected is not None and detected != script:
            raise InvalidSpec(
                f"This name is written in {SCRIPTS[detected].label} letters, but {SCRIPTS[script].label} lettering was chosen; "
                f"choose {SCRIPTS[detected].label} or let us pick",
                {"key": f"{key}.script", "script": script, "detected": detected},
            )
        chosen = SCRIPTS[script]
    else:
        chosen = SCRIPTS[detected or LATIN]
    _font_choice(font, chosen, key)
    return chosen


# --------------------------------------------------------------------------- shaping


@dataclass(frozen=True)
class Glyph:
    name: str
    gid: int
    cluster: int  # index into the text of the first character this glyph came from
    x: float  # pen position + offset, font units
    y: float
    font: str  # file the glyph came from (the script font, or Noto Sans for a digit it lacks)


@dataclass(frozen=True)
class ShapedText:
    """A shaped line: glyphs in visual order, their filled outline in font units (baseline y = 0, pen
    starting at x = 0) and the total advance."""

    text: str
    script: str
    glyphs: tuple[Glyph, ...]
    region: BaseGeometry
    advance: float
    upem: int

    @property
    def glyph_names(self) -> list[str]:
        return [g.name for g in self.glyphs]

    @property
    def clusters(self) -> list[int]:
        return sorted({g.cluster for g in self.glyphs})

    @property
    def ink_bounds(self) -> tuple[float, float, float, float]:
        return tuple(float(v) for v in self.region.bounds)  # type: ignore[return-value]

    @property
    def aspect(self) -> float:
        """Ink width / ink height."""
        x0, y0, x1, y1 = self.ink_bounds
        return (x1 - x0) / (y1 - y0)


def _runs(text: str, primary: ScriptFont, key: str) -> list[tuple[str, int, str]]:
    """``(file, start, chunk)`` runs: the script font where it has the character, Noto Sans for digits and
    punctuation it lacks. Anything neither font has is refused."""
    main = load_font(primary.file)
    fallback = load_font(SCRIPTS[LATIN].file)
    runs: list[tuple[str, int, str]] = []
    missing: list[str] = []
    for index, ch in enumerate(text):
        cp = ord(ch)
        if cp in main.unicodes or cp in JOINERS:
            file = primary.file
        elif cp in fallback.unicodes and not _is_letter(ch):
            file = SCRIPTS[LATIN].file
        else:
            if ch not in missing:
                missing.append(ch)
            continue
        if runs and runs[-1][0] == file:
            runs[-1] = (file, runs[-1][1], runs[-1][2] + ch)
        else:
            runs.append((file, index, ch))
    if missing:
        raise InvalidSpec(
            f"We can't print “{''.join(missing[:3])}” yet; use letters, numbers and simple punctuation",
            {"key": f"{key}.text", "characters": [f"U+{ord(c):04X}" for c in missing[:10]], "script": primary.id},
        )
    return runs


@lru_cache(maxsize=256)
def _shape_cached(text: str, script_id: str) -> ShapedText:
    primary = SCRIPTS[script_id]
    glyphs: list[Glyph] = []
    pieces: list[BaseGeometry] = []
    pen_x = 0.0
    upem = load_font(primary.file).upem
    for file, start, chunk in _runs(text, primary, "text"):
        loaded = load_font(file)
        if loaded.upem != upem:  # pragma: no cover - every bundled Noto font is 1000 units per em
            raise GeometryError("Lettering fonts disagree on their em size", {"font": file, "upem": loaded.upem})
        font = hb.Font(loaded.face)
        buf = hb.Buffer()
        buf.add_str(chunk)
        buf.direction = "ltr"
        if file == primary.file:
            buf.script, buf.language = primary.ot_script, primary.language
        else:
            buf.script, buf.language = SCRIPTS[LATIN].ot_script, SCRIPTS[LATIN].language
        hb.shape(font, buf, {})
        tolerance = FLATTEN_EM * upem
        for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
            x = pen_x + pos.x_offset
            y = float(pos.y_offset)
            if info.codepoint == 0:
                bad = chunk[info.cluster] if info.cluster < len(chunk) else "?"
                raise InvalidSpec(
                    f"We can't print “{bad}” yet; use letters, numbers and simple punctuation",
                    {"key": "text", "characters": [f"U+{ord(bad):04X}"], "script": script_id},
                )
            glyphs.append(Glyph(font.glyph_to_string(info.codepoint), int(info.codepoint), start + int(info.cluster), x, y, file))
            pen = outlines.OutlinePen(tolerance, (x, y))
            font.draw_glyph_with_pen(info.codepoint, pen)
            if pen.contours:
                piece = outlines.fill(pen.contours, "nonzero")
                if not piece.is_empty:
                    pieces.append(piece)
            pen_x += pos.x_advance
    region = outlines.clean(unary_union(pieces), simplify=FLATTEN_EM * upem / 4.0) if pieces else Polygon()
    return ShapedText(text, script_id, tuple(glyphs), region, pen_x, upem)


def shape_text(text: str, script: str | None = None, font: Any = None, key: str = "text") -> ShapedText:
    """Shape ``text`` (normalised: NFC, trimmed) in its script and return its glyphs and outline."""
    value = normalise_text(text)
    chosen = resolve_script(value, script, font, key)
    try:
        shaped = _shape_cached(value, chosen.id)
    except InvalidSpec as exc:
        raise InvalidSpec(exc.message, {**exc.detail, "key": f"{key}.text"}) from exc
    if shaped.region.is_empty:
        raise InvalidSpec("There is nothing to print in this text; type a name or word", {"key": f"{key}.text", "text": value})
    return shaped


# --------------------------------------------------------------------------- fit + stroke rule


@dataclass(frozen=True)
class FittedText:
    region: BaseGeometry  # mm, centred on the box centre in the anchor's local frame
    height_mm: float  # ink height as printed
    width_mm: float
    stroke: outlines.StrokeCheck


def fit_text(
    shaped: ShapedText,
    *,
    box: tuple[float, float, float, float],
    height_mm: float | None = None,
    max_height_mm: float | None = None,
    min_feature_mm: float = outlines.DEFAULT_MIN_FEATURE_MM,
    anchor_label: str = "spot",
    key: str = "features[0]",
) -> FittedText:
    """Scale ``shaped`` into ``box`` = ``(cx, cy, width, height)`` (mm, anchor frame) and apply the stroke rule."""
    cx, cy, box_w, box_h = (float(v) for v in box)
    x0, y0, x1, y1 = shaped.ink_bounds
    ink_w, ink_h = x1 - x0, y1 - y0
    place = anchor_label[:1].lower() + anchor_label[1:]
    tallest = min(box_h, float(max_height_mm)) if max_height_mm else box_h
    if height_mm is not None:
        height = float(height_mm)
        if height > tallest + 1e-9:
            raise ParamOutOfRange(
                [f"{key}.height_mm"],
                f"The {place} has room for letters up to {tallest:.0f} mm tall; {height:g} mm was asked",
                {"height_mm": height, "max_height_mm": round(tallest, 2)},
            )
        width = height * ink_w / ink_h
        if width > box_w + 1e-9:
            raise ParamOutOfRange(
                [f"{key}.height_mm"],
                f"At {height:g} mm tall this text is {width:.0f} mm long, but the {place} has room for {box_w:.0f} mm; "
                "make the letters smaller or the text shorter",
                {"height_mm": height, "width_mm": round(width, 2), "room_mm": [round(box_w, 2), round(box_h, 2)]},
            )
    else:
        height = min(tallest, box_w * ink_h / ink_w)
        if height < MIN_TEXT_HEIGHT_MM - 1e-9:
            raise ParamOutOfRange(
                [f"{key}.text"],
                f"This text is too long for the {place}: its letters would be under {MIN_TEXT_HEIGHT_MM:g} mm tall; "
                "try fewer letters or a larger piece",
                {"height_mm": round(height, 2), "min_height_mm": MIN_TEXT_HEIGHT_MM, "room_mm": [round(box_w, 2), round(box_h, 2)]},
            )
    k = height / ink_h
    centred = affinity.translate(shaped.region, -(x0 + x1) / 2.0, -(y0 + y1) / 2.0)
    region = affinity.translate(affinity.scale(centred, k, k, origin=(0.0, 0.0)), cx, cy)
    region = outlines.clean(region)
    stroke = outlines.stroke_check(region, min_feature_mm)
    if not stroke.ok:
        keys = [f"{key}.text"] + ([f"{key}.height_mm"] if height_mm is not None else [])
        raise ParamOutOfRange(keys, TOO_THIN, {"height_mm": round(height, 2), **stroke.detail()})
    return FittedText(region, height, ink_w * k, stroke)


# --------------------------------------------------------------------------- apply


def prepare(
    feature: Mapping[str, Any],
    *,
    box: tuple[float, float, float, float],
    anchor: Any,
    min_feature_mm: float | None,
    key: str,
) -> FittedText:
    """Shape and fit one ``emboss_text`` feature into ``box`` (no CAD): every refusal happens here."""
    if feature.get("projection", "planar") != "planar":  # pragma: no cover - features.validate refuses first
        raise UnsupportedFeature("Lettering that wraps around a curve is not available yet", {"key": f"{key}.projection"})
    shaped = shape_text(feature.get("text", ""), feature.get("script"), feature.get("font"), key)
    height = feature.get("height_mm")
    return fit_text(
        shaped,
        box=box,
        height_mm=float(height) if height is not None else None,
        max_height_mm=getattr(anchor, "max_text_height_mm", None),
        min_feature_mm=float(min_feature_mm or outlines.DEFAULT_MIN_FEATURE_MM),
        anchor_label=getattr(anchor, "label", "spot"),
        key=key,
    )


def apply(body: trimesh.Trimesh, frame: AnchorFrame, feature: Mapping[str, Any], fitted: FittedText) -> trimesh.Trimesh:
    """Raise or cut the fitted lettering into ``body`` at ``frame``; returns a new watertight mesh."""
    return outlines.set_into(
        body,
        frame,
        fitted.region,
        depth_mm=float(feature.get("depth_mm", 1.2)),
        mode=str(feature.get("mode", "emboss")),
        noun="name",
        detail={"anchor": feature.get("anchor"), "text": feature.get("text")},
    )


def local_extent(fitted: FittedText) -> np.ndarray:
    """``(x0, y0, x1, y1)`` of the fitted lettering in the anchor frame (tests and layout checks)."""
    return np.asarray(fitted.region.bounds, dtype=np.float64)


__all__ = [
    "FittedText",
    "Glyph",
    "MIN_TEXT_HEIGHT_MM",
    "SCRIPTS",
    "SCRIPT_IDS",
    "ScriptFont",
    "ShapedText",
    "TOO_THIN",
    "apply",
    "detect_script",
    "detect_scripts",
    "fit_text",
    "font_bytes",
    "fonts_available",
    "load_font",
    "normalise_text",
    "prepare",
    "resolve_script",
    "script_of",
    "shape_text",
]
