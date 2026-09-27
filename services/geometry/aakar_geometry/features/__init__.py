"""The features stage (Chhaap): personal content landing on a template's anchors (plan §2).

``apply_features(template, body, params, features, fetcher)`` runs after ``Template.build_body``:
validate → for each feature in spec order: resolve the anchor frame, fetch the content, apply it
(``relief_image`` → heightfield union/difference, ``hero_mesh`` → repaired form fused on or standing
alone). ``emboss_text`` and ``motif`` arrive with the text release (PR 3b) and raise
``UnsupportedFeature`` until then, which is consistent because no template declares them yet.
"""

from __future__ import annotations

import logging
from typing import Any, Iterable, Mapping

import trimesh

from ..errors import ContentUnusable, GeometryError, UnsupportedFeature
from . import booleans, hero_mesh, relief_image, validate
from .fetch import ContentFetcher, HttpFetcher, LocalFileFetcher, content_kind, resolve_format
from .frames import AnchorFrame
from .heightfield import heightfield_solid, heightfield_volume
from .relief_image import ReliefMap, relief_heightmap
from .validate import check, check_features, normalise_features

log = logging.getLogger("aakar.geometry.features")

_NEEDS = {"relief_image": "image", "hero_mesh": "model"}
_NOUNS = {"relief_image": "photo", "hero_mesh": "model", "emboss_text": "text", "motif": "motif"}


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

    fetcher = fetcher or HttpFetcher()
    for index, feature in enumerate(normalised):
        ftype = feature["type"]
        if ftype in validate.TEXT_TYPES:
            raise UnsupportedFeature(
                f"{validate.LABELS[ftype]} arrives with the text release",
                {"type": ftype, "feature": index},
            )
        anchor = template.anchor(feature["anchor"])
        frame = _frame_for(template, anchor, params)
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
    "heightfield_solid",
    "heightfield_volume",
    "hero_mesh",
    "normalise_features",
    "relief_heightmap",
    "relief_image",
    "validate",
]
