"""``Connector``: what a template declares about the Kadi it is cut for (``template-descriptor.v1.json#/properties/connector``).

One dataclass, five standards. Each kind lives in its own module with the same surface (``default_depth``,
``descriptor_extras``, ``dims``, ``tool_mesh``, ``report``, ``expected_printed``, ``coupon_part``, ``coupon_frame`` and,
where it makes sense, ``measure`` and ``check_material``); ``Connector`` dispatches to it:

* ``socket``   Kadi-S, the ribbed press socket (``socket``), fit ``press_ribbed``;
* ``dovetail`` Kadi-D, the dovetail channel a bonded rail slides into (``dovetail``), fit ``slide``;
* ``thread``   Kadi-T, a female thread or an E27 / E14 shade collar (``thread``), fit ``slide``;
* ``magnet``   Kadi-M, magnet pockets or a steel-disc pocket (``magnet_register``), fit ``pocket``;
* ``rim_clip`` Kadi-C, the compliant clip over a cap, inside an opening or over a rim (``rim_clip``), fit ``snap``;
  built into the body by the template rather than cut from it (``tool`` is None), PETG only.
"""

from __future__ import annotations

from dataclasses import dataclass
from types import ModuleType
from typing import TYPE_CHECKING, Any

import trimesh

from .compensation import Compensation

if TYPE_CHECKING:  # pragma: no cover
    from .. import cad
    from ..features.frames import AnchorFrame

KINDS = ("socket", "dovetail", "magnet", "thread", "rim_clip")
FITS = ("slide", "press_ribbed", "snap", "pocket")
FORMS = ("cap", "lid", "rim")
THREADS = ("unc_1_4", "m10x1", "e27", "e14")
MODES = ("magnets", "steel_disc")
BUILT = KINDS
DEFAULT_FIT = {"socket": "press_ribbed", "dovetail": "slide", "thread": "slide", "magnet": "pocket", "rim_clip": "snap"}
# ± on the male feature's size by the base's tolerance class (how much a pin, rail, cap or rim varies piece to piece)
TOLERANCE_MM = {"machined": 0.10, "molded": 0.15, "wood": 0.50, "steel": 0.20, "glass": 0.30, "ceramic": 0.50}


def tolerance_for(tolerance_class: str | None, default: str = "machined") -> float:
    return TOLERANCE_MM.get(str(tolerance_class or default), TOLERANCE_MM[default])


@dataclass(frozen=True)
class Connector:
    """A Kadi on the Chhaap side. ``nominal_mm`` is the size the base presents (a pin or rail width, a thread's major or a
    collar hole, a disc, a cap, an opening, a rim); ``fit`` the as-printed condition the module models for (each kind's
    default when None); ``depth_mm`` defaults per kind and size; ``adapter_sku`` names the bought-in male part packed
    when the base has none of its own (``hardware_items``). ``form`` is the rim clip's sub-form, ``thread`` the
    thread standard, ``mode`` the magnet register's; ``count`` the number of magnet pockets, ``length_mm`` a dovetail
    channel's length when the frame it is cut from carries no size."""

    kind: str
    nominal_mm: float
    fit: str | None = None
    depth_mm: float | None = None
    adapter_sku: str | None = None
    form: str | None = None
    thread: str | None = None
    mode: str | None = None
    count: int | None = None
    length_mm: float | None = None

    def __post_init__(self) -> None:
        if self.kind not in KINDS:
            raise ValueError(f"Connector kind must be one of {', '.join(KINDS)}, got {self.kind!r}")
        if self.kind not in BUILT:  # pragma: no cover - every kind is built now
            raise NotImplementedError(f"Kadi {self.kind} is not built")
        fit = self.fit or DEFAULT_FIT[self.kind]
        if fit not in FITS:
            raise ValueError(f"Connector fit must be one of {', '.join(FITS)}, got {self.fit!r}")
        object.__setattr__(self, "fit", fit)
        if float(self.nominal_mm) <= 0:
            raise ValueError("Connector nominal_mm must be positive")
        object.__setattr__(self, "nominal_mm", float(self.nominal_mm))
        if self.depth_mm is not None:
            object.__setattr__(self, "depth_mm", float(self.depth_mm))
        if self.kind == "rim_clip":
            if self.form not in FORMS:
                raise ValueError(f"A rim_clip connector needs a form in {', '.join(FORMS)}, got {self.form!r}")
        elif self.form is not None:
            raise ValueError("form belongs to a rim_clip connector only")
        if self.kind == "thread":
            from . import thread as kadi_thread

            standard = self.thread or kadi_thread.standard_for(self.nominal_mm)
            if standard not in THREADS:
                raise ValueError(f"A thread connector needs a standard in {', '.join(THREADS)}, got {self.thread!r}")
            object.__setattr__(self, "thread", standard)
        elif self.thread is not None:
            raise ValueError("thread belongs to a thread connector only")
        if self.kind == "magnet":
            mode = self.mode or "magnets"
            if mode not in MODES:
                raise ValueError(f"A magnet connector's mode is one of {', '.join(MODES)}, got {self.mode!r}")
            object.__setattr__(self, "mode", mode)
        elif self.mode is not None:
            raise ValueError("mode belongs to a magnet connector only")
        if self.count is not None:
            object.__setattr__(self, "count", int(self.count))
        if self.length_mm is not None:
            object.__setattr__(self, "length_mm", float(self.length_mm))

    # ---- dispatch ----------------------------------------------------------------------------------------------------

    def module(self) -> ModuleType:
        if self.kind == "socket":
            from . import socket as mod
        elif self.kind == "dovetail":
            from . import dovetail as mod
        elif self.kind == "thread":
            from . import thread as mod
        elif self.kind == "magnet":
            from . import magnet_register as mod
        else:
            from . import rim_clip as mod
        return mod

    def depth(self) -> float:
        return self.depth_mm if self.depth_mm is not None else float(self.module().default_depth(self))

    def descriptor(self) -> dict[str, Any]:
        out: dict[str, Any] = {"kind": self.kind, "nominal_mm": self.nominal_mm, "fit": self.fit, "depth_mm": self.depth()}
        out.update(self.module().descriptor_extras(self))
        for key in ("form", "thread", "mode", "adapter_sku"):
            value = getattr(self, key)
            if value:
                out[key] = value
        return out

    def dims(self, comp: Compensation) -> Any:
        return self.module().dims(self, comp)

    def tool(self, frame: "AnchorFrame", comp: Compensation) -> trimesh.Trimesh | None:
        """The cutting tool in world coordinates (``frame.origin`` is the mouth, ``frame.normal`` points into the body),
        or None for a Kadi the template builds into its body (the rim clip)."""
        return self.module().tool_mesh(self.dims(comp), frame)

    def report(self, frame: "AnchorFrame", comp: Compensation, material_id: str | None, **base: Any) -> dict[str, Any]:
        """What the inspect service's ``connector_fit`` check needs (modelled dimensions, compensation, the male side)."""
        return self.module().report(self, self.dims(comp), frame, comp, material_id, **base)

    def check_material(self, comp: Compensation, material_id: str | None) -> None:
        """Refuse a finish this Kadi cannot be printed in (a spring clip in a material that creeps)."""
        fn = getattr(self.module(), "check_material", None)
        if fn is not None:
            fn(self, comp, material_id)

    def expected_printed(self, comp: Compensation) -> dict[str, float]:
        """What a good print of this connector should measure (the fit coupon's bench numbers)."""
        return self.module().expected_printed(self.dims(comp))

    def coupon_part(self, comp: Compensation, wall_mm: float) -> "cad.Part":
        """The fit coupon body in its local frame: the connector's mouth (or seat) on z = 0, the body along +z."""
        return self.module().coupon_part(self, self.dims(comp), float(wall_mm))

    def coupon_frame(self, comp: Compensation) -> "AnchorFrame":
        return self.module().coupon_frame(self.dims(comp))

    def measure(self, mesh: trimesh.Trimesh, frame: "AnchorFrame", comp: Compensation) -> dict[str, Any]:
        """Read the connector back off a built mesh where the module knows how (a self-check of the modelling)."""
        fn = getattr(self.module(), "measure", None)
        return fn(mesh, frame, self.dims(comp)) if fn is not None else {}

    def label(self) -> str:
        """Customer words: "12 mm Kadi socket", "E27 collar", "rail channel", "steel-disc pocket", "cap clip"."""
        return str(self.module().label(self))

    @classmethod
    def from_dict(cls, data: Any) -> "Connector":
        """A connector from a request body (``POST /v1/coupons``): unknown keys are refused."""
        if not isinstance(data, dict) or not data.get("kind"):
            raise ValueError("connector must be an object with a kind")
        allowed = {"kind", "nominal_mm", "fit", "depth_mm", "adapter_sku", "form", "thread", "mode", "count", "length_mm"}
        unknown = sorted(set(data) - allowed)
        if unknown:
            raise ValueError(f"unknown connector keys: {', '.join(unknown)}")
        if data.get("nominal_mm") is None:
            raise ValueError("connector.nominal_mm is required")
        return cls(**{k: data[k] for k in allowed if k in data})


__all__ = ["BUILT", "Connector", "DEFAULT_FIT", "FITS", "FORMS", "KINDS", "MODES", "THREADS", "TOLERANCE_MM", "tolerance_for"]
