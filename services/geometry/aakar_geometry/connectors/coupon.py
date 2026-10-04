"""The Kadi fit coupon (plan §1.4): one small print per base × finish that the bench measures before a hybrid family is
switched on. Built from the portal through ``POST /v1/coupons`` (``api.py``); the result's STL is downloaded and
printed, the calipers and the pull-off scale are read against ``expected_printed``, and the fit test is recorded in
the API's ``fit_tests`` table.

``build_coupon`` makes the coupon body for a connector in a finish (each Kadi module's ``coupon_part``: a flanged socket,
a rail block, a collar washer, a disc plate, the clip itself), cuts or carries the connector, runs the inspect
service's ``connector_fit`` on it, reads the connector back off the mesh and returns everything the portal shows.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Mapping

import trimesh

from .. import cad
from ..errors import GeometryError, InvalidSpec
from ..features.frames import AnchorFrame
from .base import Connector
from .compensation import Compensation, for_material

DEFAULT_WALL_MM = 2.4
MIN_WALL_MM, MAX_WALL_MM = 1.0, 6.0
INSPECT_CONSTRAINTS = {"min_wall_mm": 1.2, "max_overhang_deg": 55, "bed_mm": [250, 250, 250]}


@dataclass
class Coupon:
    connector: Connector
    material_id: str
    wall_mm: float
    comp: Compensation
    mesh: trimesh.Trimesh
    frame: AnchorFrame
    report: dict[str, Any]
    dims: dict[str, Any]
    expected_printed: dict[str, float]
    measured: dict[str, Any]
    printability: dict[str, Any]
    connector_fit: dict[str, Any]
    mass_g: float
    name: str
    extras: dict[str, Any] = field(default_factory=dict)

    def summary(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "connector": self.report,
            "dims": self.dims,
            "expected_printed": self.expected_printed,
            "measured": self.measured,
            "connector_fit": self.connector_fit,
            "printability": self.printability,
            "mass_g": round(self.mass_g, 2),
            "material": self.material_id,
            "wall_mm": self.wall_mm,
            "process": self.comp.process,
        }


def coupon_name(connector: Connector, material_id: str, wall_mm: float) -> str:
    detail = connector.form or connector.thread or (connector.mode if connector.kind == "magnet" else None)
    size = f"{connector.nominal_mm:g}".replace(".", "p")
    return "kadi_" + "_".join(p for p in (connector.kind, detail, size, material_id, f"w{wall_mm:g}".replace(".", "p")) if p)


def _dims_dict(d: Any) -> dict[str, Any]:
    out: dict[str, Any] = {}
    for key, value in vars(d).items():
        if key == "comp":
            continue
        if isinstance(value, float):
            out[key] = round(value, 4)
        elif isinstance(value, (int, str, bool)) or value is None:
            out[key] = value
        else:
            out[key] = [list(v) if isinstance(v, tuple) else v for v in value] if isinstance(value, tuple) else value
    return out


def _base_kwargs(base: Mapping[str, Any] | None) -> dict[str, Any]:
    if not base:
        return {}
    out: dict[str, Any] = {}
    for key in ("tolerance_class", "pin_d_mm", "fit_tested", "range_mm"):
        if base.get(key) is not None:
            out[key] = base[key]
    return out


def build_coupon(connector: Connector, material_id: str, wall_mm: float = DEFAULT_WALL_MM, base: Mapping[str, Any] | None = None) -> Coupon:
    """Build, inspect and read back one fit coupon. Refusals (``InvalidSpec``) are a Kadi the material cannot carry or a
    wall out of range; a kernel failure is a ``GeometryError``."""
    from aakar_inspect import Constraints, inspect_mesh

    from ..materials import density_g_cm3, material_ids

    if material_id not in material_ids():
        raise InvalidSpec(f"Unknown material {material_id}", {"material": material_id, "materials": material_ids()})
    wall = float(wall_mm)
    if not (MIN_WALL_MM <= wall <= MAX_WALL_MM):
        raise InvalidSpec(f"The coupon wall must be between {MIN_WALL_MM:g} and {MAX_WALL_MM:g} mm", {"wall_mm": wall})
    comp = for_material(material_id)
    connector.check_material(comp, material_id)
    try:
        part = connector.coupon_part(comp, wall)
        mesh = cad.to_trimesh(part)
    except InvalidSpec:
        raise
    except Exception as exc:
        raise GeometryError("The coupon could not be built", {"error": f"{type(exc).__name__}: {exc}", "connector": connector.descriptor()}) from exc
    lift = -float(mesh.bounds[0][2])
    mesh.apply_translation([0.0, 0.0, lift])
    frame = connector.coupon_frame(comp)
    frame = AnchorFrame(frame.origin + [0.0, 0.0, lift], frame.u, frame.v, frame.normal, frame.size_mm, frame.bounds_mm)
    report = connector.report(frame, comp, material_id, **_base_kwargs(base))
    printability, _estimate = inspect_mesh(mesh, Constraints.from_dict(INSPECT_CONSTRAINTS), connector=report)
    fit = printability["checks"]["connector_fit"]
    measured = connector.measure(mesh, frame, comp)
    mass_g = float(mesh.volume) / 1000.0 * density_g_cm3(material_id, 1.24)
    return Coupon(
        connector=connector, material_id=material_id, wall_mm=wall, comp=comp, mesh=mesh, frame=frame, report=report,
        dims=_dims_dict(connector.dims(comp)), expected_printed=connector.expected_printed(comp), measured=measured,
        printability=printability, connector_fit=fit, mass_g=mass_g, name=coupon_name(connector, material_id, wall),
    )


__all__ = ["Coupon", "DEFAULT_WALL_MM", "build_coupon", "coupon_name"]
