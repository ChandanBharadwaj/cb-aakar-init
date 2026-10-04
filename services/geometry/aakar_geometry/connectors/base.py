"""``Connector``: what a template declares about the Kadi it is cut for (``template-descriptor.v1.json#/properties/connector``)."""

from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING, Any

import trimesh

from .compensation import Compensation

if TYPE_CHECKING:  # pragma: no cover
    from ..features.frames import AnchorFrame

KINDS = ("socket", "dovetail", "magnet", "thread", "rim_clip")
FITS = ("slide", "press_ribbed", "snap", "pocket")
BUILT = ("socket",)  # Phase 1: Kadi-S; the rest follow in Phase 4


@dataclass(frozen=True)
class Connector:
    """A Kadi on the Chhaap side. ``nominal_mm`` is the pin (or rail, thread, magnet) the base presents; ``fit`` the
    as-printed condition the connector module models for; ``depth_mm`` defaults per nominal; ``adapter_sku`` names the
    bought-in male part packed when the base has no pin of its own (``hardware_items``)."""

    kind: str
    nominal_mm: float
    fit: str = "press_ribbed"
    depth_mm: float | None = None
    adapter_sku: str | None = None

    def __post_init__(self) -> None:
        if self.kind not in KINDS:
            raise ValueError(f"Connector kind must be one of {', '.join(KINDS)}, got {self.kind!r}")
        if self.fit not in FITS:
            raise ValueError(f"Connector fit must be one of {', '.join(FITS)}, got {self.fit!r}")
        if self.kind not in BUILT:
            raise NotImplementedError(f"Kadi {self.kind} is not built yet (Phase 4); Phase 1 ships the socket")
        if float(self.nominal_mm) <= 0:
            raise ValueError("Connector nominal_mm must be positive")
        object.__setattr__(self, "nominal_mm", float(self.nominal_mm))
        if self.depth_mm is not None:
            object.__setattr__(self, "depth_mm", float(self.depth_mm))

    def depth(self) -> float:
        from . import socket

        return self.depth_mm if self.depth_mm is not None else socket.default_depth(self.nominal_mm)

    def descriptor(self) -> dict[str, Any]:
        from . import socket

        out: dict[str, Any] = {
            "kind": self.kind,
            "nominal_mm": self.nominal_mm,
            "fit": self.fit,
            "depth_mm": self.depth(),
            "rib_count": socket.RIB_COUNT,
            "clearance_mm": socket.CLEARANCE_MM,
        }
        if self.adapter_sku:
            out["adapter_sku"] = self.adapter_sku
        return out

    def dims(self, comp: Compensation) -> "Any":
        from . import socket

        return socket.dims(self, comp)

    def tool(self, frame: "AnchorFrame", comp: Compensation) -> trimesh.Trimesh:
        """The cutting tool in world coordinates: ``frame.origin`` is the mouth, ``frame.normal`` points into the body."""
        from . import socket

        return socket.tool_mesh(self.dims(comp), frame)

    def report(self, frame: "AnchorFrame", comp: Compensation, material_id: str | None, **base: Any) -> dict[str, Any]:
        """What the inspect service's ``connector_fit`` check needs (modelled dimensions, compensation, the pin)."""
        from . import socket

        return socket.report(self, self.dims(comp), frame, comp, material_id, **base)


__all__ = ["BUILT", "Connector", "FITS", "KINDS"]
