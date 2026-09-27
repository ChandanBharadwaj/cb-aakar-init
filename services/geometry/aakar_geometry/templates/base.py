"""Template base class: descriptor, parameter validation (no silent clamping), body + features build, karigar note."""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Any, ClassVar, Mapping, Sequence

import trimesh

from ..contracts import validate
from ..errors import InvalidSpec, ParamOutOfRange, UnsupportedFeature
from ..features.frames import AnchorFrame


@dataclass(frozen=True)
class Param:
    """One entry of ``template-descriptor.v1.json#/$defs/param``."""

    type: str  # number | integer | boolean | enum
    label: str
    default: Any
    unit: str = ""
    min: float | None = None
    max: float | None = None
    step: float | None = None
    options: tuple[str, ...] = ()
    handle: bool = False
    group: str | None = None
    description: str | None = None

    def descriptor(self) -> dict[str, Any]:
        out: dict[str, Any] = {"type": self.type, "label": self.label, "default": self.default, "handle": self.handle}
        if self.unit or self.type in ("number", "integer"):
            out["unit"] = self.unit
        if self.description:
            out["description"] = self.description
        if self.min is not None:
            out["min"] = self.min
        if self.max is not None:
            out["max"] = self.max
        if self.step is not None:
            out["step"] = self.step
        if self.options:
            out["options"] = list(self.options)
        if self.group:
            out["group"] = self.group
        return out

    def coerce(self, key: str, value: Any) -> Any:
        """Type-check one value; returns the normalised value or raises ``InvalidSpec``/``ParamOutOfRange``."""
        if self.type == "boolean":
            if not isinstance(value, bool):
                raise InvalidSpec(f"Parameter {key} must be true or false", {"param": key, "value": value})
            return value
        if self.type == "enum":
            if not isinstance(value, str) or value not in self.options:
                raise ParamOutOfRange([key], f"{key} must be one of {', '.join(self.options)}", {"value": value})
            return value
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise InvalidSpec(f"Parameter {key} must be a number", {"param": key, "value": value})
        if not math.isfinite(value):
            raise ParamOutOfRange([key], f"{key} must be a finite number", {"value": value})
        if self.type == "integer":
            if float(value) != int(value):
                raise ParamOutOfRange([key], f"{key} must be a whole number", {"value": value})
            value = int(value)
        else:
            value = float(value)
        if (self.min is not None and value < self.min) or (self.max is not None and value > self.max):
            raise ParamOutOfRange(
                [key],
                f"{key} must be between {self.min:g} and {self.max:g} {self.unit}".rstrip(),
                {"value": value, "min": self.min, "max": self.max},
            )
        return value


@dataclass(frozen=True)
class Anchor:
    """One entry of ``template-descriptor.v1.json#/$defs/anchor``: a place where content may land.

    ``kind`` ``surface`` (text, motif, photo relief; ``size_mm`` = printable width × height, ``bleed_mm``
    the safe margin inside it) or ``volume`` (a customer's own 3D form; ``bounds_mm`` = width × depth ×
    height above the anchor's bottom plane). ``accepts`` narrows the template's ``features_supported``
    for this anchor (None = all of them); ``max_relief_mm`` caps relief and emboss depth.
    """

    id: str
    label: str
    projection: str  # planar | cylindrical | conformal
    max_text_height_mm: float | None = None
    kind: str = "surface"
    size_mm: tuple[float, float] | None = None
    bleed_mm: float = 0.0
    bounds_mm: tuple[float, float, float] | None = None
    accepts: tuple[str, ...] | None = None
    max_relief_mm: float | None = None

    def __post_init__(self) -> None:
        if self.kind not in ("surface", "volume"):
            raise ValueError(f"Anchor {self.id}: kind must be surface or volume, got {self.kind!r}")
        if self.size_mm is not None:
            object.__setattr__(self, "size_mm", tuple(float(s) for s in self.size_mm))
        if self.bounds_mm is not None:
            object.__setattr__(self, "bounds_mm", tuple(float(b) for b in self.bounds_mm))
        if self.accepts is not None:
            object.__setattr__(self, "accepts", tuple(self.accepts))

    def descriptor(self) -> dict[str, Any]:
        out: dict[str, Any] = {"id": self.id, "label": self.label, "kind": self.kind, "projection": self.projection}
        if self.max_text_height_mm is not None:
            out["max_text_height_mm"] = self.max_text_height_mm
        if self.size_mm is not None:
            out["size_mm"] = [round(float(s), 3) for s in self.size_mm]
            out["bleed_mm"] = float(self.bleed_mm)
        elif self.bleed_mm:
            out["bleed_mm"] = float(self.bleed_mm)
        if self.bounds_mm is not None:
            out["bounds_mm"] = [round(float(b), 3) for b in self.bounds_mm]
        if self.accepts is not None:
            out["accepts"] = list(self.accepts)
        if self.max_relief_mm is not None:
            out["max_relief_mm"] = float(self.max_relief_mm)
        return out


@dataclass(frozen=True)
class HardwareRef:
    """A bought-in part packed with every piece (``template-descriptor.v1.json#/properties/hardware``)."""

    sku: str
    qty: int = 1

    def descriptor(self) -> dict[str, Any]:
        return {"sku": self.sku, "qty": int(self.qty)}


@dataclass(frozen=True)
class TemplateConstraints:
    min_wall_mm: float = 1.2
    max_overhang_deg: float = 55.0
    bed_mm: tuple[float, float, float] = (250.0, 250.0, 250.0)

    def descriptor(self) -> dict[str, Any]:
        return {"min_wall_mm": self.min_wall_mm, "max_overhang_deg": self.max_overhang_deg, "bed_mm": list(self.bed_mm)}


class Template:
    """A parametric template. Subclasses set the class attributes and implement ``build_body`` (and
    ``anchor_frame`` for every anchor that accepts content); ``validate_combination`` and
    ``validate_content`` hold coupled limits, ``hardware_for`` varies quantities with the params."""

    id: ClassVar[str]
    version: ClassVar[int]
    family: ClassVar[str]
    name: ClassVar[str]
    description: ClassVar[str] = ""
    environment: ClassVar[str] = "studio"
    params: ClassVar[Mapping[str, Param]] = {}
    anchors: ClassVar[tuple[Anchor, ...]] = ()
    constraints: ClassVar[TemplateConstraints] = TemplateConstraints()
    style_variants: ClassVar[tuple[str, ...]] = ()
    features_supported: ClassVar[tuple[str, ...]] = ()
    hardware: ClassVar[tuple[HardwareRef, ...]] = ()
    min_feature_mm: ClassVar[float | None] = None

    @classmethod
    def ref(cls) -> str:
        return f"{cls.id}@{cls.version}"

    @classmethod
    def materials(cls) -> list[str]:
        """Material ids this template offers: every material, filtered by the family's ``material_rules``."""
        from ..families import allowed_material_ids

        return allowed_material_ids(cls.family)

    @classmethod
    def descriptor(cls) -> dict[str, Any]:
        """Template descriptor v1; validated against the contract before it is returned."""
        doc: dict[str, Any] = {
            "id": cls.id,
            "version": cls.version,
            "family": cls.family,
            "name": cls.name,
            "description": cls.description,
            "environment": cls.environment,
            "params": {key: p.descriptor() for key, p in cls.params.items()},
            "anchors": [a.descriptor() for a in cls.anchors],
            "constraints": cls.constraints.descriptor(),
            "materials": cls.materials(),
            "style_variants": list(cls.style_variants),
            "features_supported": list(cls.features_supported),
            "hardware": [h.descriptor() for h in cls.hardware],
        }
        if cls.min_feature_mm is not None:
            doc["min_feature_mm"] = float(cls.min_feature_mm)
        validate("template-descriptor", doc)
        return doc

    @classmethod
    def anchor(cls, anchor_id: str) -> Anchor:
        for a in cls.anchors:
            if a.id == anchor_id:
                return a
        raise InvalidSpec(
            f"{cls.name} has no anchor called {anchor_id!r}",
            {"anchor": anchor_id, "anchors": [a.id for a in cls.anchors]},
        )

    @classmethod
    def hardware_for(cls, params: Mapping[str, Any]) -> list[dict[str, Any]]:
        """Bill of materials for this piece as ``[{sku, qty}]``; override when a parameter changes quantities."""
        return [h.descriptor() for h in cls.hardware]

    @classmethod
    def validate(cls, params: Mapping[str, Any] | None) -> dict[str, Any]:
        """Fill defaults and type-check every parameter. Out-of-range values raise ``ParamOutOfRange``
        listing every offending key at once; unknown keys raise ``InvalidSpec``."""
        given = dict(params or {})
        unknown = sorted(k for k in given if k not in cls.params)
        if unknown:
            raise InvalidSpec(
                f"Unknown parameter(s) for {cls.ref()}: {', '.join(unknown)}",
                {"unknown_params": unknown, "known_params": sorted(cls.params)},
            )
        normalised: dict[str, Any] = {}
        offending: list[str] = []
        messages: dict[str, str] = {}
        for key, spec in cls.params.items():
            if key not in given or given[key] is None:
                normalised[key] = spec.default
                continue
            try:
                normalised[key] = spec.coerce(key, given[key])
            except ParamOutOfRange as exc:
                offending.append(key)
                messages[key] = exc.message
        if offending:
            raise ParamOutOfRange(
                offending,
                "; ".join(messages[k] for k in offending),
                {"messages": messages, "ranges": {k: {"min": cls.params[k].min, "max": cls.params[k].max} for k in offending}},
            )
        cls.validate_combination(normalised)
        return normalised

    @classmethod
    def validate_combination(cls, params: dict[str, Any]) -> None:
        """Hook for coupled constraints between parameters (raise ``ParamOutOfRange``)."""

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        """Hook for limits that couple parameters and content (Chhaap), run before any CAD with the validated
        params and the normalised features, even when there are none: a cut-in photo must leave enough
        plastic behind it, a raw print needs its model. Raise ``ParamOutOfRange`` / ``InvalidSpec``."""

    @classmethod
    def anchor_frame(cls, anchor_id: str, params: Mapping[str, Any]) -> AnchorFrame:
        """Where content lands for ``anchor_id`` at these params (see ``features.frames.AnchorFrame``)."""
        raise NotImplementedError(f"{cls.ref()} does not place anchor {anchor_id!r}")

    @classmethod
    def build_body(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        """Validated params -> watertight trimesh in mm, Z up, sitting on Z = 0, before any content is added.
        A template whose whole geometry is a customer's form (``raw_print``) returns an empty ``trimesh.Trimesh()``."""
        raise NotImplementedError

    @classmethod
    def build(
        cls,
        params: Mapping[str, Any],
        features: Sequence[Mapping[str, Any]] = (),
        fetcher: Any | None = None,
    ) -> trimesh.Trimesh:
        """``validate_content`` → ``build_body`` → ``features.apply_features``.

        ``validate_content`` runs even without features (a raw print with no model is refused here, not
        exported as nothing). With no features the body is returned untouched. Content on the underside
        (a raised photo on a keychain's back) can reach below the bed; the finished piece is lifted back
        onto Z = 0 so it still rests on the bed.
        """
        from ..features.validate import check_features

        normalised = check_features(cls, features)
        cls.validate_content(params, normalised)
        body = cls.build_body(params)
        if not normalised:
            return body
        from ..features import apply_features

        mesh, _hardware = apply_features(cls, body, params, normalised, fetcher)
        lowest = float(mesh.bounds[0][2])
        if lowest < -1e-9:
            mesh.apply_translation([0.0, 0.0, -lowest])
        return mesh

    @classmethod
    def lithophane(
        cls,
        body: trimesh.Trimesh | None,
        params: Mapping[str, Any],
        frame: AnchorFrame,
        anchor: Anchor,
        feature: Mapping[str, Any],
        relief: Any,
    ) -> trimesh.Trimesh:
        """Hook for ``relief_image`` in ``lithophane`` mode: the template turns the ``ReliefMap`` into its plate."""
        raise UnsupportedFeature(
            f"{cls.name} cannot make a photo night light",
            {"template": cls.ref(), "anchor": anchor.id, "mode": "lithophane"},
        )

    @classmethod
    def karigar_note(cls, params: Mapping[str, Any]) -> str:
        raise NotImplementedError

    @classmethod
    def karigar_note_for(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]] = ()) -> str:
        """The karigar's note for a finished piece: ``karigar_note(params)`` followed by a sentence naming the
        names (Naam) and motifs (Buti) set into it, e.g. “Asha” stands 0.6 mm proud on the back."""
        note = cls.karigar_note(params)
        if not features:
            return note
        from ..features import content_note

        extra = content_note(cls, features)
        return f"{note} {extra}" if extra else note
