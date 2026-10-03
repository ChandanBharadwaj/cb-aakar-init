"""Semantic checks on ``spec.features`` against a template, before any CAD runs.

Checks (in this order, per feature):
  * the type is known and in ``template.features_supported`` (``UnsupportedFeature``),
  * the anchor exists (``InvalidSpec``) and its ``accepts`` allows the type (``UnsupportedFeature``),
  * ``hero_mesh`` only on ``kind == "volume"`` anchors, everything else only on surfaces
    (``UnsupportedFeature``),
  * the mode (explicit, or the type's default: names and photos ``emboss``, motifs ``deboss``) is one the
    anchor lists in ``modes`` (``UnsupportedFeature`` on ``features[i].mode``; an anchor without ``modes`` takes
    every mode its types allow), worded by the template's ``mode_refusal`` when it has its own words,
  * what one anchor may hold (``InvalidSpec``): at most one photo or form (``relief_image``/``hero_mesh``),
    at most one name (``emboss_text``) and at most one motif (``motif``); a name and a motif may share an
    anchor (they sit side by side, ``features.placement``), but a photo never shares its anchor with a
    name or a motif: that is refused as crowded,
  * names and motifs only on ``planar`` anchors with ``planar`` projection (``UnsupportedFeature``: no
    anchor is curved yet, so cylindrical and conformal lettering are not built),
  * ``relief_mm`` / ``depth_mm`` within the anchor's ``max_relief_mm`` (``ParamOutOfRange``),
  * text no longer than the family's ``max_text_chars`` (``ParamOutOfRange``), in one of the seven launch
    scripts and matching ``script`` / ``font`` when given (``InvalidSpec`` / ``UnsupportedFeature``, see
    ``features.emboss_text``), ``height_mm`` within the anchor's ``max_text_height_mm``
    (``ParamOutOfRange``),
  * ``motif_id`` in the motif library (``InvalidSpec``), ``scale`` between the motif's ``min_scale`` and
    1 (``ParamOutOfRange``),
  * ``fit: longest`` carries ``longest_mm`` (``InvalidSpec``); ``cutout: silhouette`` is not built yet,
and, for the spec as a whole, every ``required`` anchor holds content (``InvalidSpec``, worded by the template's
``missing_content``), even when the spec has no features at all.

Whether a name or motif fits its anchor at a printable stroke depends on the parameters (the anchor's
size), so that is checked when the features are applied (``features.prepare_marks``), still before any
boolean runs. ``normalise_features`` fills the contract defaults (and a name's detected ``script``) so
later stages and the completed payload see them; a style variant the template offers may put its own
defaults first (``features.styles``: under ``comic_pop`` names and motifs stand raised and bolder).
"""

from __future__ import annotations

import unicodedata
from typing import Any, Iterable, Mapping

from .. import families
from ..errors import BuildError, GeometryError, InvalidSpec, ParamOutOfRange, UnsupportedFeature
from . import emboss_text, motif, styles

FEATURE_TYPES = families.FEATURE_TYPES
SURFACE_TYPES = ("emboss_text", "motif", "relief_image")
VOLUME_TYPES = ("hero_mesh",)
CONTENT_TYPES = ("relief_image", "hero_mesh")  # one of these per anchor at most
TEXT_TYPES = ("emboss_text", "motif")

LABELS = {"emboss_text": "Text (Naam)", "motif": "Motif (Buti)", "relief_image": "Photo relief (Chhavi)", "hero_mesh": "Your own 3D form (Roop)"}

DEFAULTS: dict[str, dict[str, Any]] = {
    "relief_image": {"mode": "emboss", "relief_mm": 0.6, "fit": "contain", "invert": False, "cutout": "none"},
    "hero_mesh": {"fit": "contain", "yaw_deg": 0, "orientation": "as_uploaded"},
    "emboss_text": {"depth_mm": 1.2, "projection": "planar", "mode": "emboss"},
    "motif": {"scale": 1, "depth_mm": 1.0, "mode": "deboss"},
}

DEPTH_KEY = {"relief_image": "relief_mm", "emboss_text": "depth_mm", "motif": "depth_mm"}
_NOUN = {"emboss_text": "name", "motif": "motif"}

# The modes each type can take (design-spec.v1.json), what an anchor's ``modes`` narrows, and the customer's words
# for them: generic sentences a template may replace with its own (``Template.mode_refusal`` / ``missing_content``).
TYPE_MODES: dict[str, tuple[str, ...]] = {
    "emboss_text": ("emboss", "deboss"),
    "motif": ("emboss", "deboss"),
    "relief_image": ("emboss", "deboss", "lithophane"),
}
_MODE_WORDS = {"emboss": "raised", "deboss": "cut in", "lithophane": "lit from behind as the plate itself"}
_MODE_CHOICE = {"emboss": "raised", "deboss": "cut in", "lithophane": "lit from behind"}
_MODE_NOUN = {"emboss_text": "name", "motif": "motif", "relief_image": "photo"}
_ADD_WORDS = {"relief_image": "a photo", "emboss_text": "a name", "motif": "a motif", "hero_mesh": "your model file"}


def _customer_label(ftype: str) -> str:
    """Customer-safe name for a feature type: "your own 3D form (Roop)", never the code word."""
    label = LABELS.get(ftype, "this kind of content")
    return label[:1].lower() + label[1:]


def _place(anchor: Any) -> str:
    label = str(anchor.label)
    return label[:1].lower() + label[1:]


def _either(words: list[str]) -> str:
    """["cut in"] → "cut in"; ["raised", "cut in"] → "raised or cut in"."""
    return words[0] if len(words) == 1 else ", ".join(words[:-1]) + " or " + words[-1]


def _mode_refusal(anchor: Any, ftype: str, mode: str) -> str:
    """"On the top rail the name can only be cut in, not raised; choose cut in"."""
    allowed = [_MODE_WORDS[m] for m in anchor.modes]
    return (
        f"On the {_place(anchor)} the {_MODE_NOUN.get(ftype, 'content')} can only be {_either(allowed)}, "
        f"not {_MODE_WORDS.get(mode, mode)}; choose {_MODE_CHOICE[anchor.modes[0]]}"
    )


def _missing_content(template: Any, anchor: Any, accepts: tuple[str, ...]) -> str:
    """"Add a photo to the photo plate: Roshni photo night light can't be made without it"."""
    words = [_ADD_WORDS[t] for t in FEATURE_TYPES if t in accepts and t in _ADD_WORDS]
    what = _either(words) if words else "something"
    return f"Add {what} to the {_place(anchor)}: {template.name} can't be made without it"


def text_length(text: str) -> int:
    """Characters as a reader counts them: marks (Indic matras and viramas, accents) and joiners do not add.

    ``unicodedata.combining`` is not enough: most Indic vowel signs have combining class 0, so the
    general category decides (Mn, Mc, Me are marks; Cf covers ZWJ/ZWNJ). "नमस्ते" counts 4.
    """
    return sum(1 for ch in str(text) if unicodedata.category(ch) not in ("Mn", "Mc", "Me", "Cf"))


def normalise_feature(feature: Mapping[str, Any], *, anchor: Any = None, style: str | None = None) -> dict[str, Any]:
    """The feature with the contract defaults filled in; ``style`` (one the template offers) and the feature's
    ``anchor`` let a style variant put its own defaults first (``features.styles``). Given values always win."""
    if not isinstance(feature, Mapping) or not isinstance(feature.get("type"), str):
        raise InvalidSpec("Each feature must be an object with a type", {"feature": feature})
    ftype = feature["type"]
    if ftype not in FEATURE_TYPES:
        raise InvalidSpec(f"Unknown feature type {ftype}", {"type": ftype, "known": list(FEATURE_TYPES)})
    given = {k: v for k, v in feature.items() if v is not None}
    out = dict(DEFAULTS.get(ftype, {}))
    out.update(styles.feature_defaults(style, ftype, given, anchor))
    out.update(given)
    if ftype == "hero_mesh" and "source" in out and isinstance(out["source"], Mapping):
        source = dict(out["source"])
        source.setdefault("origin", "upload")
        out["source"] = source
    if ftype == "relief_image" and "source" in out and isinstance(out["source"], Mapping):
        source = dict(out["source"])
        source.setdefault("origin", "upload")
        out["source"] = source
    if ftype == "emboss_text" and not out.get("script") and isinstance(out.get("text"), str):
        try:  # "Detected or chosen script": echo the one the lettering is shaped in
            out["script"] = emboss_text.detect_script(emboss_text.normalise_text(out["text"]))
        except BuildError:
            pass  # mixed or unsupported letters: check_features refuses them with the feature's key
    return out


def normalise_features(
    features: Iterable[Mapping[str, Any]] | None,
    template: Any = None,
    style: str | None = None,
) -> list[dict[str, Any]]:
    """Every feature with its defaults filled in. With a ``template`` and a ``style`` it offers (its
    ``style_variants``), the style's defaults come first (``comic_pop``: names and motifs raised and bolder where
    their anchor allows); a style the template does not offer changes nothing (the pipeline refuses it)."""
    look = style if template is not None and styles.offered(template, style) else None
    anchors = {a.id: a for a in getattr(template, "anchors", ())} if look else {}
    out = []
    for f in features or []:
        anchor_id = f.get("anchor") if isinstance(f, Mapping) else None
        out.append(normalise_feature(f, anchor=anchors.get(anchor_id) if isinstance(anchor_id, str) else None, style=look))
    return out


def _anchor_by_id(template: Any) -> dict[str, Any]:
    return {a.id: a for a in template.anchors}


def _max_text_chars(template: Any) -> int | None:
    try:
        return families.content_slot(template.family).get("max_text_chars")
    except families.UnknownFamily:
        return None


def _require_content(template: Any, normalised: list[dict[str, Any]]) -> None:
    """Every ``required`` anchor holds content (``InvalidSpec`` worded by the template's ``missing_content``)."""
    filled = {f.get("anchor") for f in normalised}
    supported = tuple(getattr(template, "features_supported", ()) or ())
    for anchor in getattr(template, "anchors", ()):
        if not getattr(anchor, "required", False) or anchor.id in filled:
            continue
        accepts = tuple(anchor.accepts) if anchor.accepts is not None else supported
        hook = getattr(template, "missing_content", None)
        message = (hook(anchor) if callable(hook) else None) or _missing_content(template, anchor, accepts)
        raise InvalidSpec(
            message,
            {"template": template.ref() if hasattr(template, "ref") else getattr(template, "id", None), "anchor": anchor.id, "required": True, "accepts": list(accepts)},
        )


def check_features(template: Any, features: Iterable[Mapping[str, Any]] | None, style: str | None = None) -> list[dict[str, Any]]:
    """Validate and return the normalised features (defaults filled; ``style``, when the template offers it, puts
    its own defaults first). Raises the first error found; a ``required`` anchor left empty is refused even when
    there are no features at all."""
    normalised = normalise_features(features, template=template, style=style)
    if not normalised:
        _require_content(template, normalised)
        return normalised

    supported = tuple(template.features_supported)
    unsupported = sorted({f["type"] for f in normalised if f["type"] not in supported})
    if unsupported:
        wanted = ", ".join(_customer_label(t) for t in unsupported)
        raise UnsupportedFeature(
            f"{template.name} can't carry {wanted} yet",
            {"unsupported": unsupported, "features_supported": list(supported)},
        )

    anchors = _anchor_by_id(template)
    content_on_anchor: dict[str, list[str]] = {}
    max_chars = _max_text_chars(template)

    for index, f in enumerate(normalised):
        ftype = f["type"]
        prefix = f"features[{index}]"
        anchor_id = f.get("anchor")
        anchor = anchors.get(anchor_id) if isinstance(anchor_id, str) else None
        if anchor is None:
            raise InvalidSpec(
                f"{template.name} has no anchor called {anchor_id!r}",
                {"anchor": anchor_id, "anchors": sorted(anchors), "feature": index},
            )
        accepts = tuple(anchor.accepts) if anchor.accepts is not None else supported
        if ftype not in accepts:
            raise UnsupportedFeature(
                f"The {anchor.label} of {template.name} does not take {LABELS[ftype].lower()}",
                {"anchor": anchor.id, "accepts": list(accepts), "type": ftype, "feature": index},
            )
        kind = getattr(anchor, "kind", "surface") or "surface"
        if ftype in VOLUME_TYPES and kind != "volume":
            raise UnsupportedFeature(
                f"The {anchor.label} is a surface; your own 3D form needs a place with room for it",
                {"anchor": anchor.id, "kind": kind, "type": ftype, "feature": index},
            )
        if ftype in SURFACE_TYPES and kind != "surface":
            raise UnsupportedFeature(
                f"The {anchor.label} holds a 3D form; {LABELS[ftype].lower()} needs a flat surface",
                {"anchor": anchor.id, "kind": kind, "type": ftype, "feature": index},
            )
        modes = getattr(anchor, "modes", None)
        if ftype in TYPE_MODES and modes is not None:
            mode = str(f.get("mode", DEFAULTS[ftype]["mode"]))
            if mode not in modes:
                hook = getattr(template, "mode_refusal", None)
                detail: dict[str, Any] = {"anchor": anchor.id, "mode": mode, "modes": list(modes), "feature": index, "key": f"{prefix}.mode"}
                if len(modes) == 1:
                    detail["needs"] = modes[0]
                raise UnsupportedFeature((hook(anchor, ftype, mode) if callable(hook) else None) or _mode_refusal(anchor, ftype, mode), detail)
        seen = content_on_anchor.setdefault(anchor.id, [])
        if ftype in CONTENT_TYPES and any(t in CONTENT_TYPES for t in seen):
            raise InvalidSpec(
                f"Only one photo or 3D form can go on the {anchor.label}",
                {"anchor": anchor.id, "types": seen + [ftype], "feature": index},
            )
        if ftype in TEXT_TYPES and ftype in seen:
            noun = _NOUN[ftype]
            raise InvalidSpec(
                f"Only one {noun} can go on the {anchor.label}; put the other {noun} on another spot",
                {"anchor": anchor.id, "types": seen + [ftype], "feature": index},
            )
        # a photo fills its anchor: a name or motif beside it is refused as crowded, whichever came first
        if ftype == "relief_image":
            crowding = next((t for t in seen if t in TEXT_TYPES), None)
        else:
            crowding = ftype if ftype in TEXT_TYPES and "relief_image" in seen else None
        if crowding is not None:
            noun = _NOUN[crowding]
            raise InvalidSpec(
                f"The {anchor.label} is too crowded for a photo and a {noun} together; put the {noun} on another spot",
                {"anchor": anchor.id, "types": seen + [ftype], "feature": index},
            )
        seen.append(ftype)
        if ftype in TEXT_TYPES:
            projection = f.get("projection", "planar") if ftype == "emboss_text" else "planar"
            if anchor.projection != "planar" or projection != "planar":
                raise UnsupportedFeature(
                    "Lettering and motifs that wrap around a curved surface are not available yet; every spot today is flat",
                    {"anchor": anchor.id, "projection": projection, "anchor_projection": anchor.projection, "feature": index},
                )
        if ftype == "hero_mesh":
            if anchor.bounds_mm is None:
                raise GeometryError(
                    f"Volume anchor {anchor.id} of {template.ref()} has no bounds_mm",
                    {"anchor": anchor.id, "template": template.ref()},
                )
            if f.get("fit") == "longest" and f.get("longest_mm") is None:
                raise InvalidSpec(
                    "Choose how long the form should be (longest_mm) when fit is longest",
                    {"anchor": anchor.id, "feature": index},
                )
        if ftype == "relief_image" and f.get("cutout", "none") != "none":
            raise UnsupportedFeature(
                "Cut-out silhouettes are not available yet; choose cutout none",
                {"cutout": f.get("cutout"), "feature": index},
            )
        depth_key = DEPTH_KEY.get(ftype)
        # lithophane mode is the plate itself (0.8–3 mm of light), not a relief on the skin: the template's
        # lithophane hook owns that range, so the surface cap does not apply
        is_lithophane = ftype == "relief_image" and f.get("mode") == "lithophane"
        if depth_key and anchor.max_relief_mm is not None and not is_lithophane:
            depth = float(f.get(depth_key, DEFAULTS[ftype][depth_key]))
            if depth > float(anchor.max_relief_mm) + 1e-9:
                raise ParamOutOfRange(
                    [f"{prefix}.{depth_key}"],
                    f"The relief on the {anchor.label} can be at most {float(anchor.max_relief_mm):g} mm deep; {depth:g} mm was asked",
                    {"anchor": anchor.id, "value": depth, "max_relief_mm": anchor.max_relief_mm, "feature": index},
                )
        if ftype == "emboss_text" and max_chars is not None:
            length = text_length(f.get("text", ""))
            if length > int(max_chars):
                raise ParamOutOfRange(
                    [f"{prefix}.text"],
                    f"Text on the {anchor.label} can be at most {int(max_chars)} characters; {length} were given",
                    {"anchor": anchor.id, "length": length, "max_text_chars": int(max_chars), "feature": index},
                )
        if ftype == "emboss_text":
            emboss_text.resolve_script(emboss_text.normalise_text(f.get("text", "")), f.get("script"), f.get("font"), prefix)
            height = f.get("height_mm")
            tallest = anchor.max_text_height_mm
            if height is not None and tallest is not None and float(height) > float(tallest) + 1e-9:
                raise ParamOutOfRange(
                    [f"{prefix}.height_mm"],
                    f"Letters on the {anchor.label} can be at most {float(tallest):g} mm tall; {float(height):g} mm was asked",
                    {"anchor": anchor.id, "height_mm": float(height), "max_text_height_mm": float(tallest), "feature": index},
                )
        if ftype == "motif":
            found = motif.entry(str(f.get("motif_id")), f"{prefix}.motif_id")
            motif.check_scale(found, float(f.get("scale", 1)), anchor_label=anchor.label, key=f"{prefix}.scale")
    _require_content(template, normalised)
    return normalised


def check(template: Any, spec: Mapping[str, Any]) -> list[dict[str, Any]]:
    """Validate ``spec.features`` against ``template`` (with the spec's ``style``); returns the normalised features."""
    return check_features(template, spec.get("features") or [], style=spec.get("style"))


__all__ = [
    "CONTENT_TYPES",
    "DEFAULTS",
    "FEATURE_TYPES",
    "LABELS",
    "SURFACE_TYPES",
    "TEXT_TYPES",
    "TYPE_MODES",
    "VOLUME_TYPES",
    "check",
    "check_features",
    "normalise_feature",
    "normalise_features",
    "text_length",
]
