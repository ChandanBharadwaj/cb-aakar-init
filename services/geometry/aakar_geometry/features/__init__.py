"""The features stage (Chhaap): personal content landing on a template's anchors (plan §2).

``apply_features(template, body, params, features, fetcher)`` runs after ``Template.build_body``:
validate → prepare every name (``emboss_text``, Naam: shaped, fitted, stroke rule) and motif (``motif``,
Buti: looked up, fitted, stroke rule) so that every refusal comes before any boolean → then, in spec
order, resolve the anchor frame and apply: names and motifs are raised or cut into the body
(``outlines.set_into``), photos become heightfield reliefs (``relief_image``, Chhavi), customer forms
are repaired and fused on or stand alone (``hero_mesh``, Roop). A name and a motif on the same anchor
sit side by side (``placement``). ``content_note`` describes the names and motifs for the karigar's note;
``styles`` holds what a style variant changes (``comic_pop``: names and motifs raised and bolder by default).
"""

from __future__ import annotations

import logging
from typing import Any, Iterable, Mapping

import trimesh

from ..errors import ContentUnusable, GeometryError, InvalidSpec, UnsupportedFeature
from . import booleans, emboss_text, hero_mesh, motif, outlines, placement, relief_image, styles, validate
from .fetch import ContentFetcher, HttpFetcher, LocalFileFetcher, content_kind, resolve_format
from .frames import AnchorFrame
from .heightfield import heightfield_solid, heightfield_volume
from .relief_image import ReliefMap, relief_heightmap
from .validate import check, check_features, normalise_features

log = logging.getLogger("aakar.geometry.features")

_NEEDS = {"relief_image": "image", "hero_mesh": "model"}
_NOUNS = {"relief_image": "photo", "hero_mesh": "model", "emboss_text": "name", "motif": "motif"}


def _frame_for(template: Any, anchor: Any, params: Mapping[str, Any]) -> AnchorFrame:
    try:
        frame = template.anchor_frame(anchor.id, params)
    except NotImplementedError as exc:
        raise GeometryError(
            f"{template.ref()} has not placed its {anchor.id} anchor yet (anchor_frame is not implemented)",
            {"template": template.ref(), "anchor": anchor.id},
        ) from exc
    if not isinstance(frame, AnchorFrame):
        raise GeometryError(f"{template.ref()}.anchor_frame must return an AnchorFrame", {"anchor": anchor.id})
    if anchor.kind == "surface" and frame.size_mm is None and anchor.size_mm is not None:
        frame = AnchorFrame(frame.origin, frame.u, frame.v, frame.normal, tuple(anchor.size_mm), frame.bounds_mm)
    if anchor.kind == "volume" and frame.bounds_mm is None and anchor.bounds_mm is not None:
        frame = AnchorFrame(frame.origin, frame.u, frame.v, frame.normal, frame.size_mm, tuple(anchor.bounds_mm))
    return frame


def _content(fetcher: ContentFetcher, feature: Mapping[str, Any], index: int) -> tuple[bytes, str]:
    source = feature.get("source")
    if not isinstance(source, Mapping) or not source.get("url"):
        raise ContentUnusable("This content has no file behind it; please upload it again", {"feature": index})
    data = fetcher.fetch(source)
    fmt = resolve_format(source, data)
    needs = _NEEDS[feature["type"]]
    kind = content_kind(fmt)
    if kind is None:
        raise ContentUnusable(
            "We couldn't tell what kind of file this is; use a PNG or JPEG photo, or a model file from your 3D program (.stl, .obj or .3mf)",
            {"format": fmt, "url": source.get("url"), "feature": index},
        )
    if kind != needs:
        noun_have = "photo" if kind == "image" else "3D model"
        noun_need = "photo" if needs == "image" else "3D model"
        raise ContentUnusable(
            f"A {noun_have} was given where a {noun_need} is needed",
            {"format": fmt, "expected": needs, "feature": index},
        )
    return data, fmt


def _min_feature(template: Any) -> float:
    return float(getattr(template, "min_feature_mm", None) or outlines.DEFAULT_MIN_FEATURE_MM)


def prepare_marks(
    template: Any,
    params: Mapping[str, Any],
    features: list[dict[str, Any]],
    frames: dict[str, AnchorFrame] | None = None,
) -> dict[int, Any]:
    """Shape, fit and check every name and motif (no CAD) → ``{feature index: FittedText | FittedMotif}``.

    Refusals (script, fit, stroke rule, scale) surface here, before the first boolean runs."""
    frames = {} if frames is None else frames
    prepared: dict[int, Any] = {}
    kinds: dict[str, set[str]] = {}
    for f in features:
        if f["type"] in validate.TEXT_TYPES:
            kinds.setdefault(f["anchor"], set()).add(f["type"])
    for index, feature in enumerate(features):
        ftype = feature["type"]
        if ftype not in validate.TEXT_TYPES:
            continue
        anchor = template.anchor(feature["anchor"])
        if anchor.id not in frames:
            frames[anchor.id] = _frame_for(template, anchor, params)
        on_anchor = kinds[anchor.id]
        boxes = placement.content_boxes(
            frames[anchor.id], anchor.bleed_mm, text="emboss_text" in on_anchor, motif="motif" in on_anchor
        )
        module = emboss_text if ftype == "emboss_text" else motif
        prepared[index] = module.prepare(
            feature, box=boxes[ftype], anchor=anchor, min_feature_mm=_min_feature(template), key=f"features[{index}]"
        )
    return prepared


def apply_features(
    template: Any,
    body: trimesh.Trimesh | None,
    params: Mapping[str, Any],
    features: Iterable[Mapping[str, Any]] | None,
    fetcher: ContentFetcher | None = None,
) -> tuple[trimesh.Trimesh, list[dict[str, Any]]]:
    """Apply ``features`` (spec order) to ``body``; returns ``(mesh, hardware)`` with hardware as ``[{sku, qty}]``."""
    normalised = check_features(template, features)
    hardware = template.hardware_for(params)
    mesh = body
    if not normalised:
        if mesh is None or mesh.is_empty:
            raise GeometryError(f"{template.ref()} produced no geometry", {"template": template.ref()})
        return mesh, hardware

    frames: dict[str, AnchorFrame] = {}
    marks = prepare_marks(template, params, normalised, frames)
    if fetcher is None and any(f["type"] in _NEEDS for f in normalised):
        fetcher = HttpFetcher()
    for index, feature in enumerate(normalised):
        ftype = feature["type"]
        anchor = template.anchor(feature["anchor"])
        if anchor.id not in frames:
            frames[anchor.id] = _frame_for(template, anchor, params)
        frame = frames[anchor.id]
        if ftype in validate.TEXT_TYPES:
            if mesh is None or mesh.is_empty:
                raise GeometryError(f"{template.ref()} has no body for a {_NOUNS[ftype]}", {"anchor": anchor.id})
            module = emboss_text if ftype == "emboss_text" else motif
            mesh = module.apply(mesh, frame, feature, marks[index])
        else:
            assert fetcher is not None
            data, fmt = _content(fetcher, feature, index)
            key = f"features[{index}]"
            if ftype == "relief_image":
                if feature.get("mode") == "lithophane":
                    if frame.size_mm is None:
                        raise GeometryError("Surface anchor frame has no size_mm", {"anchor": anchor.id})
                    relief = relief_heightmap(
                        data,
                        frame.size_mm,
                        bleed_mm=anchor.bleed_mm,
                        fit=feature.get("fit", "contain"),
                        invert=bool(feature.get("invert", False)),
                        relief_mm=float(feature.get("relief_mm", 0.6)),
                        fmt=fmt,
                    )
                    mesh = template.lithophane(mesh, params, frame, anchor, feature, relief)
                else:
                    if mesh is None or mesh.is_empty:
                        raise GeometryError(f"{template.ref()} has no body for a relief", {"anchor": anchor.id})
                    mesh = relief_image.apply(mesh, frame, feature, data, bleed_mm=anchor.bleed_mm, fmt=fmt)
            elif ftype == "hero_mesh":
                mesh = hero_mesh.apply(mesh, frame, feature, data, fmt=fmt, anchor_label=anchor.label, key=f"{key}.longest_mm")
            else:  # pragma: no cover - guarded by check_features
                raise UnsupportedFeature(f"Unknown feature {ftype}", {"type": ftype})
        if mesh is None or mesh.is_empty or not mesh.is_watertight:
            raise GeometryError(
                f"Something went wrong while adding your {_NOUNS.get(ftype, 'content')}; please try again",
                {"type": ftype, "anchor": anchor.id, "feature": index, "reason": f"applying {ftype} on {anchor.id} did not leave a closed solid"},
            )
    return mesh, hardware


def _place(label: str) -> str:
    return "the " + label[:1].lower() + label[1:]


def content_note(template: Any, features: Iterable[Mapping[str, Any]] | None) -> str:
    """One sentence for the karigar's note naming the names and motifs on the piece ("" when there are none):
    “Asha” stands 0.6 mm proud on the back; the Lotus motif is cut 1 mm into the face."""
    parts: list[str] = []
    for f in normalise_features(features):
        if f["type"] not in validate.TEXT_TYPES:
            continue
        try:
            anchor = template.anchor(f["anchor"])
        except InvalidSpec:
            continue
        depth = float(f.get("depth_mm", validate.DEFAULTS[f["type"]]["depth_mm"]))
        how = f"stands {depth:g} mm proud on" if f.get("mode") == "emboss" else f"is cut {depth:g} mm into"
        if f["type"] == "emboss_text":
            what = f"“{emboss_text.normalise_text(f.get('text', ''))}”"
        else:
            try:
                what = f"the {motif.entry(str(f.get('motif_id'))).label} motif"
            except (InvalidSpec, GeometryError):
                what = "a motif"
        parts.append(f"{what} {how} {_place(anchor.label)}")
    if not parts:
        return ""
    sentence = "; ".join(parts)
    return sentence[:1].upper() + sentence[1:] + "."


__all__ = [
    "AnchorFrame",
    "ContentFetcher",
    "HttpFetcher",
    "LocalFileFetcher",
    "ReliefMap",
    "apply_features",
    "booleans",
    "check",
    "check_features",
    "content_note",
    "emboss_text",
    "heightfield_solid",
    "heightfield_volume",
    "hero_mesh",
    "motif",
    "normalise_features",
    "outlines",
    "placement",
    "prepare_marks",
    "relief_heightmap",
    "relief_image",
    "styles",
    "validate",
]
