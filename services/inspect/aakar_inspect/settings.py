"""Typed inputs for an inspection: printability constraints and slicing settings."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Mapping


class InvalidSettings(ValueError):
    """A constraint or slicing setting is outside its sane range (never silently clamped)."""

    def __init__(self, keys: list[str], message: str):
        self.keys = keys
        super().__init__(message)


@dataclass(frozen=True)
class Constraints:
    """Printability thresholds (PLAN §7.9). Units: mm and degrees."""

    min_wall_mm: float = 1.2
    max_overhang_deg: float = 55.0
    bed_mm: tuple[float, float, float] = (250.0, 250.0, 250.0)
    min_tipping_margin_mm: float = 5.0

    def __post_init__(self) -> None:
        bad: list[str] = []
        if not (0 < self.min_wall_mm <= 50):
            bad.append("min_wall_mm")
        if not (30 <= self.max_overhang_deg <= 90):
            bad.append("max_overhang_deg")
        if len(self.bed_mm) != 3 or any(not (0 < b <= 2000) for b in self.bed_mm):
            bad.append("bed_mm")
        if not (0 <= self.min_tipping_margin_mm <= 100):
            bad.append("min_tipping_margin_mm")
        if bad:
            raise InvalidSettings(bad, f"constraints out of range: {', '.join(bad)}")

    @classmethod
    def from_dict(cls, data: Mapping[str, Any] | None) -> "Constraints":
        data = dict(data or {})
        kwargs: dict[str, Any] = {}
        for key in ("min_wall_mm", "max_overhang_deg", "min_tipping_margin_mm"):
            if data.get(key) is not None:
                kwargs[key] = float(data[key])
        if data.get("bed_mm") is not None:
            kwargs["bed_mm"] = tuple(float(v) for v in data["bed_mm"])
        return cls(**kwargs)

    def to_dict(self) -> dict[str, Any]:
        return {
            "min_wall_mm": self.min_wall_mm,
            "max_overhang_deg": self.max_overhang_deg,
            "bed_mm": list(self.bed_mm),
            "min_tipping_margin_mm": self.min_tipping_margin_mm,
        }


@dataclass(frozen=True)
class SlicingSettings:
    """Slicer inputs shared by every backend. Material-independent (PLAN §7.10)."""

    layer_height_mm: float = 0.2
    infill_pct: float = 15.0
    nozzle_mm: float = 0.4
    walls: int = 3
    extra: dict[str, Any] = field(default_factory=dict, compare=False)

    def __post_init__(self) -> None:
        bad: list[str] = []
        if not (0.04 <= self.layer_height_mm <= 0.6):
            bad.append("layer_height_mm")
        if not (0 <= self.infill_pct <= 100):
            bad.append("infill_pct")
        if not (0.1 <= self.nozzle_mm <= 2.0):
            bad.append("nozzle_mm")
        if not (1 <= int(self.walls) <= 20) or int(self.walls) != self.walls:
            bad.append("walls")
        if bad:
            raise InvalidSettings(bad, f"slicing settings out of range: {', '.join(bad)}")

    @property
    def line_width_mm(self) -> float:
        """Typical extrusion width for a nozzle (PrusaSlicer default ≈ 1.125 × nozzle)."""
        return round(self.nozzle_mm * 1.125, 4)

    @classmethod
    def from_dict(cls, data: Mapping[str, Any] | None) -> "SlicingSettings":
        data = dict(data or {})
        kwargs: dict[str, Any] = {}
        for key in ("layer_height_mm", "infill_pct", "nozzle_mm"):
            if data.get(key) is not None:
                kwargs[key] = float(data[key])
        if data.get("walls") is not None:
            kwargs["walls"] = int(data["walls"])
        return cls(**kwargs)

    def to_dict(self) -> dict[str, Any]:
        return {
            "layer_height_mm": self.layer_height_mm,
            "infill_pct": self.infill_pct,
            "nozzle_mm": self.nozzle_mm,
            "walls": self.walls,
        }
