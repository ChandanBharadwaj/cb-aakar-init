"""Template base class: descriptor, parameter validation (no silent clamping), body + features build, karigar note."""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Any, ClassVar, Mapping, Sequence

import trimesh

from ..connectors.base import Connector
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


CONTENT_MODES = ("emboss", "deboss", "lithophane")


@dataclass(frozen=True)
class Anchor:
    """One entry of ``template-descriptor.v1.json#/$defs/anchor``: a place where content may land.

    ``kind`` ``surface`` (text, motif, photo relief; ``size_mm`` = printable width × height, ``bleed_mm``
    the safe margin inside it) or ``volume`` (a customer's own 3D form; ``bounds_mm`` = width × depth ×
    height above the anchor's bottom plane). ``accepts`` narrows the template's ``features_supported``
    for this anchor (None = all of them); ``max_relief_mm`` caps relief and emboss depth. ``modes`` are the
    only modes content may take here (raised ``emboss``, cut-in ``deboss``, or the photo as the plate itself,
    ``lithophane``; None = every mode the feature type allows); ``required`` says the piece cannot be built
    without content here. Both are published only when set and enforced in ``features.validate``.
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
    modes: tuple[str, ...] | None = None
    required: bool = False

    def __post_init__(self) -> None:
        if self.kind not in ("surface", "volume"):
            raise ValueError(f"Anchor {self.id}: kind must be surface or volume, got {self.kind!r}")
        if self.size_mm is not None:
            object.__setattr__(self, "size_mm", tuple(float(s) for s in self.size_mm))
        if self.bounds_mm is not None:
            object.__setattr__(self, "bounds_mm", tuple(float(b) for b in self.bounds_mm))
        if self.accepts is not None:
            object.__setattr__(self, "accepts", tuple(self.accepts))
        if self.modes is not None:
            modes = tuple(self.modes)
            if not modes or len(set(modes)) != len(modes) or any(m not in CONTENT_MODES for m in modes):
                raise ValueError(f"Anchor {self.id}: modes must be distinct values from {', '.join(CONTENT_MODES)}, got {modes!r}")
            object.__setattr__(self, "modes", modes)
        object.__setattr__(self, "required", bool(self.required))

    def allows(self, mode: str) -> bool:
        """True when content may take ``mode`` here (an anchor that lists no modes takes every mode)."""
        return self.modes is None or mode in self.modes

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
        if self.modes is not None:
            out["modes"] = list(self.modes)
        if self.required:
            out["required"] = True
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
    # Hybrid (Jod) templates: the Kadi cut into the piece so it plugs into a bought-in base (connectors/)
    connector: ClassVar[Connector | None] = None
    # True when build_body(params, material) needs the material (a rim clip's compliant walls are sized with the
    # finish's compensation); every other template keeps build_body(params)
    body_uses_material: ClassVar[bool] = False

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
        if cls.connector is not None:
            doc["connector"] = cls.connector.descriptor()
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
        plastic behind it, a raw print needs its model. Raise ``ParamOutOfRange`` / ``InvalidSpec``.
        (Which modes an anchor takes and whether it needs content are anchor data, ``Anchor.modes`` and
        ``Anchor.required``, checked for every template in ``features.validate``.)"""

    @classmethod
    def mode_refusal(cls, anchor: Anchor, ftype: str, mode: str) -> str | None:
        """Customer words for content in a mode ``anchor.modes`` leaves out (a raised name on a frame that
        prints face down); None keeps the generic sentence of ``features.validate``."""
        return None

    @classmethod
    def missing_content(cls, anchor: Anchor) -> str | None:
        """Customer words for a ``required`` anchor left empty (the night light's photo); None keeps the generic
        sentence of ``features.validate``."""
        return None

    @classmethod
    def style_note(cls, style: str | None, features: Sequence[Mapping[str, Any]] = ()) -> str:
        """The karigar's sentence on the piece's style variant ("" for none, or for a style this template does
        not list in ``style_variants``)."""
        from ..features import styles

        return styles.note(cls, style, features)

    @classmethod
    def anchor_frame(cls, anchor_id: str, params: Mapping[str, Any]) -> AnchorFrame:
        """Where content lands for ``anchor_id`` at these params (see ``features.frames.AnchorFrame``)."""
        raise NotImplementedError(f"{cls.ref()} does not place anchor {anchor_id!r}")

    @classmethod
    def connector_for(cls, params: Mapping[str, Any]) -> Connector | None:
        """The Kadi this piece is cut for at these params (``connector`` unless a parameter picks the size)."""
        return cls.connector

    @classmethod
    def connector_frame(cls, params: Mapping[str, Any]) -> AnchorFrame:
        """Where the Kadi is cut: origin at the mouth (on the bed for a socket) or the seat (a rim clip), normal pointing
        into the body."""
        raise NotImplementedError(f"{cls.ref()} declares a connector but does not place it (connector_frame)")

    @classmethod
    def apply_connector(cls, body: trimesh.Trimesh, params: Mapping[str, Any], material: str | None) -> trimesh.Trimesh:
        """Cut the Kadi into the body, modelled with the material's print compensation (``connectors.compensation``);
        a template without a connector, or whose Kadi is built into the body (a rim clip), returns the body untouched."""
        connector = cls.connector_for(params)
        if connector is None or body is None or body.is_empty:
            return body
        from ..connectors import compensation
        from ..features.booleans import difference

        comp = compensation.for_material(material)
        connector.check_material(comp, material)
        tool = connector.tool(cls.connector_frame(params), comp)
        if tool is None:
            return body
        return difference(body, tool)

    @classmethod
    def connector_report(cls, params: Mapping[str, Any], material: str | None, **base: Any) -> dict[str, Any] | None:
        """The ``connector`` block the inspect service's ``connector_fit`` check reads, or None without a Kadi."""
        connector = cls.connector_for(params)
        if connector is None:
            return None
        from ..connectors import compensation

        return connector.report(cls.connector_frame(params), compensation.for_material(material), material, **base)

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
        material: str | None = None,
    ) -> trimesh.Trimesh:
        """``validate_content`` → ``build_body`` → ``apply_connector`` → ``features.apply_features``.

        ``material`` only matters to a template with a Kadi: its socket is modelled with that material's print
        compensation (PLA when unknown). Everything else ignores it.

        ``validate_content`` runs even without features (a raw print with no model is refused here, not
        exported as nothing). With no features the body is returned untouched. Content on the underside
        (a raised photo on a keychain's back) can reach below the bed; the finished piece is lifted back
        onto Z = 0 so it still rests on the bed.
        """
        from ..features.validate import check_features

        normalised = check_features(cls, features)
        cls.validate_content(params, normalised)
        connector = cls.connector_for(params)
        if connector is not None:  # a Kadi the finish cannot carry is refused before any CAD
            from ..connectors import compensation

            connector.check_material(compensation.for_material(material), material)
        body = cls.build_body(params, material) if cls.body_uses_material else cls.build_body(params)  # type: ignore[call-arg]
        body = cls.apply_connector(body, params, material)
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
    def karigar_note_for(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]] = (), style: str | None = None) -> str:
        """The karigar's note for a finished piece: ``karigar_note(params)`` followed by a sentence naming the
        names (Naam) and motifs (Buti) set into it, e.g. “Asha” stands 0.6 mm proud on the back, and one on its
        style variant when it has one (``style_note``)."""
        return cls.join_note(cls.karigar_note(params), features, style)

    @classmethod
    def join_note(cls, note: str, features: Sequence[Mapping[str, Any]] = (), style: str | None = None) -> str:
        """``note``, then the sentence naming the names and motifs, then the style's sentence (empty parts skipped)."""
        parts = [note]
        if features:
            from ..features import content_note

            parts.append(content_note(cls, features))
        parts.append(cls.style_note(style, features))
        return " ".join(p for p in parts if p)
