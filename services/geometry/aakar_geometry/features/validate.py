"""Semantic checks on ``spec.features`` against a template, before any CAD runs.

Checks (in this order, per feature):
  * the type is known and in ``template.features_supported`` (``UnsupportedFeature``),
  * the anchor exists (``InvalidSpec``) and its ``accepts`` allows the type (``UnsupportedFeature``),
  * ``hero_mesh`` only on ``kind == "volume"`` anchors, everything else only on surfaces
    (``UnsupportedFeature``),
  * at most one ``relief_image``/``hero_mesh`` per anchor (``InvalidSpec``),
  * ``relief_mm`` / ``depth_mm`` within the anchor's ``max_relief_mm`` (``ParamOutOfRange``),
  * text no longer than the family's ``max_text_chars`` (``ParamOutOfRange``),
  * ``fit: longest`` carries ``longest_mm`` (``InvalidSpec``); ``cutout: silhouette`` is not built yet.

``normalise_features`` fills the contract defaults so later stages and the completed payload see them.
"""

from __future__ import annotations

import unicodedata
from typing import Any, Iterable, Mapping

from .. import families
from ..errors import GeometryError, InvalidSpec, ParamOutOfRange, UnsupportedFeature

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


def _customer_label(ftype: str) -> str:
    """Customer-safe name for a feature type: "your own 3D form (Roop)", never the code word."""
    label = LABELS.get(ftype, "this kind of content")
    return label[:1].lower() + label[1:]


def text_length(text: str) -> int:
    """Characters as a reader counts them: marks (Indic matras and viramas, accents) and joiners do not add.

    ``unicodedata.combining`` is not enough: most Indic vowel signs have combining class 0, so the
    general category decides (Mn, Mc, Me are marks; Cf covers ZWJ/ZWNJ). "नमस्ते" counts 4.
    """
    return sum(1 for ch in str(text) if unicodedata.category(ch) not in ("Mn", "Mc", "Me", "Cf"))


def normalise_feature(feature: Mapping[str, Any]) -> dict[str, Any]:
    if not isinstance(feature, Mapping) or not isinstance(feature.get("type"), str):
        raise InvalidSpec("Each feature must be an object with a type", {"feature": feature})
    ftype = feature["type"]
    if ftype not in FEATURE_TYPES:
        raise InvalidSpec(f"Unknown feature type {ftype}", {"type": ftype, "known": list(FEATURE_TYPES)})
    out = dict(DEFAULTS.get(ftype, {}))
    out.update({k: v for k, v in feature.items() if v is not None})
    if ftype == "hero_mesh" and "source" in out and isinstance(out["source"], Mapping):
        source = dict(out["source"])
        source.setdefault("origin", "upload")
        out["source"] = source
    if ftype == "relief_image" and "source" in out and isinstance(out["source"], Mapping):
        source = dict(out["source"])
        source.setdefault("origin", "upload")
        out["source"] = source
    return out


def normalise_features(features: Iterable[Mapping[str, Any]] | None) -> list[dict[str, Any]]:
    return [normalise_feature(f) for f in (features or [])]


def _anchor_by_id(template: Any) -> dict[str, Any]:
    return {a.id: a for a in template.anchors}


def _max_text_chars(template: Any) -> int | None:
    try:
        return families.content_slot(template.family).get("max_text_chars")
    except families.UnknownFamily:
        return None


def check_features(template: Any, features: Iterable[Mapping[str, Any]] | None) -> list[dict[str, Any]]:
    """Validate and return the normalised features (defaults filled). Raises the first error found."""
    normalised = normalise_features(features)
    if not normalised:
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
        if ftype in CONTENT_TYPES:
            seen = content_on_anchor.setdefault(anchor.id, [])
            if seen:
                raise InvalidSpec(
                    f"Only one photo or 3D form can go on the {anchor.label}",
                    {"anchor": anchor.id, "types": seen + [ftype], "feature": index},
                )
            seen.append(ftype)
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
    return normalised


def check(template: Any, spec: Mapping[str, Any]) -> list[dict[str, Any]]:
    """Validate ``spec.features`` against ``template``; returns the normalised features."""
    return check_features(template, spec.get("features") or [])


__all__ = [
    "CONTENT_TYPES",
    "DEFAULTS",
    "FEATURE_TYPES",
    "LABELS",
    "SURFACE_TYPES",
    "TEXT_TYPES",
    "VOLUME_TYPES",
    "check",
    "check_features",
    "normalise_feature",
    "normalise_features",
    "text_length",
]
