"""Template base class: descriptor, parameter validation (no silent clamping), build, karigar note."""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Any, ClassVar, Mapping

import trimesh

from ..contracts import validate
from ..errors import InvalidSpec, ParamOutOfRange


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
    id: str
    label: str
    projection: str  # planar | cylindrical | conformal
    max_text_height_mm: float | None = None

    def descriptor(self) -> dict[str, Any]:
        out: dict[str, Any] = {"id": self.id, "label": self.label, "projection": self.projection}
        if self.max_text_height_mm is not None:
            out["max_text_height_mm"] = self.max_text_height_mm
        return out


@dataclass(frozen=True)
class TemplateConstraints:
    min_wall_mm: float = 1.2
    max_overhang_deg: float = 55.0
    bed_mm: tuple[float, float, float] = (250.0, 250.0, 250.0)

    def descriptor(self) -> dict[str, Any]:
        return {"min_wall_mm": self.min_wall_mm, "max_overhang_deg": self.max_overhang_deg, "bed_mm": list(self.bed_mm)}


class Template:
    """A parametric template. Subclasses set the class attributes and implement ``build_part``."""

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

    @classmethod
    def ref(cls) -> str:
        return f"{cls.id}@{cls.version}"

    @classmethod
    def materials(cls) -> list[str]:
        from ..materials import material_ids

        return material_ids()

    @classmethod
    def descriptor(cls) -> dict[str, Any]:
        """Template descriptor v1; validated against the contract before it is returned."""
        doc = {
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
        }
        validate("template-descriptor", doc)
        return doc

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
    def build(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        """Validated params -> watertight trimesh in mm, Z up, sitting on Z = 0."""
        raise NotImplementedError

    @classmethod
    def karigar_note(cls, params: Mapping[str, Any]) -> str:
        raise NotImplementedError
