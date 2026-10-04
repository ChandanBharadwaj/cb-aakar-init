"""Kadi-C: the compliant rim clip (plan §1.2, Phase 4). PETG only.

Three forms, all **built into the body** by the template (``Connector.tool`` is None here) from the parts this module
makes, so the coupon and the product share one geometry:

* ``cap``  a skirt over a round cap (a steel bottle's): ``TABS`` tabs ``TAB_T_MM`` thick and ``TAB_LEN_MM`` long, separated by
  slots, each ending in a ``LIP_MM`` inward lip with a 45° lead-in; the cap passes the lips, which spring back under
  its edge.
* ``lid``  a rectangular lip inside an opening (a soap box's): ``TAB_LEN_MM`` tall, the long sides bulge ``LIP_MM``
  outward near the free end and are freed by slits at the corners so they flex.
* ``rim``  C-jaws over a pot's rim: a bridge across the rim's top, an inner jaw split into tabs by slots and ending in
  a lip that hooks under the rim's inside, and a plain outer skirt (stiff, so a name tag can sit on it).

The hold is elastic, so the material matters more than the numbers: a tab's bending strain at the lip's deflection
(``strain_pct``, 3 t δ / 2 L²) must stay under the material's ``max_strain_pct`` on the largest cap or rim of the
class, and a ``creep_class: high`` material (every PLA) is refused outright (``check_material``): PLA relaxes under a
constant preload and the clip lets go in a month. Every Kadi-C family is PETG-only in ``families.json`` for that reason.

Frames: the connector's **seat** is where the Chhaap meets the base (a cap's top, the box's opening, the rim's top):
``connector_frame`` puts the origin there with the normal pointing into the Chhaap's body; the tabs run the other way,
along ``−normal``. The three templates print with their seat facing up (the plate or bridge on the bed), so the normal
is −Z and the tabs rise from the seat; ``connector_fit`` asks only that the axis be vertical.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING, Any

import numpy as np
import trimesh
from shapely.geometry import box

from .. import cad
from ..errors import InvalidSpec
from .compensation import Compensation

if TYPE_CHECKING:  # pragma: no cover
    from ..features.frames import AnchorFrame
    from .base import Connector

CLEARANCE_MM = {"cap": 0.30, "lid": 0.25, "rim": 0.15}  # per side, as printed, between the body and the clip wall
TAB_T_MM = 1.6  # four extrusion widths: stiff enough to hold, thin enough to flex
TAB_LEN_MM = {"cap": 16.0, "lid": 8.0, "rim": 18.0}
LIP_MM = {"cap": 1.4, "lid": 0.4, "rim": 1.4}  # how far the lip stands into the clearance: sized so the smallest base of the class still grips
LIP_H_MM = {"cap": 2.5, "lid": 1.5, "rim": 2.5}  # the lip's height: a flat bearing band plus its lead-in
SLOT_W_MM = 3.0
TABS = {"cap": 4, "lid": 2, "rim": 3}  # cap: tabs round the skirt; lid: the two long sides; rim: tabs per jaw
BRIDGE_MM = 2.0  # the rim form's bridge over the rim's top
MIN_TAB_T_MM = 1.2
MIN_GRIP_MM = 0.05
STRAIN_FACTOR = 1.5  # max bending strain of a cantilever deflected δ at its tip: 3 t δ / (2 L²)
DEFAULT_RANGE_HALF_MM = 1.0  # a base without a class or a tolerance class still varies by this much


def default_depth(connector: "Connector") -> float:
    return TAB_LEN_MM[connector.form]


def descriptor_extras(connector: "Connector") -> dict[str, Any]:
    return {"clearance_mm": 2.0 * CLEARANCE_MM[connector.form]}


def label(connector: "Connector") -> str:
    return {"cap": "cap clip", "lid": "lid clip", "rim": "rim clip"}[connector.form]


def check_material(connector: "Connector", comp: Compensation, material_id: str | None) -> None:
    """A rim clip in a material that creeps (every PLA) is refused, naming the finishes that hold."""
    if comp.creep_class != "high":
        return
    from ..materials import load_materials
    from .compensation import for_material

    holding = [m.get("name") or m["id"] for m in load_materials() if for_material(m["id"]).creep_class != "high"]
    names = ", ".join(holding) if holding else "a PETG finish"
    raise InvalidSpec(
        f"A {label(connector)} holds by springing back, and {material_id or 'this finish'} would let go within weeks; choose {names}",
        {"material": material_id, "creep_class": comp.creep_class, "materials": holding, "kind": "rim_clip", "form": connector.form},
    )


@dataclass(frozen=True)
class RimClipDims:
    form: str
    nominal_mm: float
    range_mm: tuple[float, float]
    clearance_mm: float
    tab_t_mm: float
    tab_len_mm: float
    lip_mm: float
    lip_h_mm: float
    slot_w_mm: float
    tabs: int
    clear_model_mm: float  # the clip wall's clearance size as modelled (cap: inner Ø; lid: outer width; rim: jaw gap)
    comp: Compensation

    @property
    def strain_pct(self) -> float:
        """Bending strain of a tab passing a base of the nominal size."""
        return self.strain_at(self.nominal_mm)

    def deflection_at(self, size_mm: float) -> float:
        """How far a tab must move to pass a base of ``size_mm`` (negative: it never touches)."""
        if self.form == "cap":
            return (size_mm - self.nominal_mm) / 2.0 + self.lip_mm - self.clearance_mm
        if self.form == "rim":  # one lipped jaw takes the whole difference
            return (size_mm - self.nominal_mm) - 2.0 * self.clearance_mm + self.lip_mm
        # lid: the opening shrinking squeezes the bulge
        return (self.nominal_mm - size_mm) / 2.0 + self.lip_mm - self.clearance_mm

    def strain_at(self, size_mm: float) -> float:
        return 100.0 * STRAIN_FACTOR * self.tab_t_mm * max(self.deflection_at(size_mm), 0.0) / (self.tab_len_mm**2)

    @property
    def strain_pct_max(self) -> float:
        """At the largest cap or rim, or the tightest opening, of the class."""
        worst = self.range_mm[1] if self.form in ("cap", "rim") else self.range_mm[0]
        return self.strain_at(worst)

    @property
    def grip_min_mm(self) -> float:
        """How far the lip still bites on the smallest cap or rim, or the loosest opening, of the class."""
        best = self.range_mm[0] if self.form in ("cap", "rim") else self.range_mm[1]
        return self.deflection_at(best)


def dims(connector: "Connector", comp: Compensation, range_mm: tuple[float, float] | None = None, half_mm: float = DEFAULT_RANGE_HALF_MM) -> RimClipDims:
    """``range_mm`` is the base's size class when it has one; else the base varies ± ``half_mm`` (its tolerance class)."""
    form = connector.form
    nominal = float(connector.nominal_mm)
    rng = tuple(float(v) for v in range_mm) if range_mm else (nominal - half_mm, nominal + half_mm)
    shrink = 1.0 + float(comp.shrink_pct) / 100.0
    c = CLEARANCE_MM[form]
    if form == "cap":
        clear = (nominal + 2.0 * c) * shrink + comp.xy_hole_comp_mm  # inner Ø of the skirt
    elif form == "lid":
        clear = (nominal - 2.0 * c) * shrink + comp.xy_outer_comp_mm  # outer width of the lip inside the opening
    else:
        clear = (nominal + 2.0 * c) * shrink + comp.xy_hole_comp_mm  # the gap between the jaws
    return RimClipDims(form, nominal, rng, c, TAB_T_MM, TAB_LEN_MM[form], LIP_MM[form], LIP_H_MM[form], SLOT_W_MM, TABS[form], clear, comp)


def tool_mesh(d: RimClipDims, frame: "AnchorFrame") -> None:
    """A rim clip is built into the body, not cut from it."""
    return None


# ---- the parts a template (or the coupon) builds the clip from, in print orientation --------------------------------


def cap_skirt(d: RimClipDims, z_seat: float, skirt_h: float) -> tuple["cad.Part", "cad.Part"]:
    """``(add, cut)`` for a cap skirt rising from ``z_seat`` to ``z_seat + skirt_h``: the skirt with its lip ring at the free
    end (add) and the slots that split it into tabs (cut). ``skirt_h`` is at least the tab length."""
    from ..templates import plates

    r_clear = d.clear_model_mm / 2.0
    r_lip = r_clear - d.lip_mm
    r_out = r_clear + d.tab_t_mm
    z_top = z_seat + skirt_h
    skirt = cad.translate(cad.prism(plates.disc(0.0, 0.0, r_out).difference(plates.hole(0.0, 0.0, 2.0 * r_clear)), skirt_h), dz=z_seat)
    lip = cad.translate(cad.prism(plates.disc(0.0, 0.0, r_clear + 0.01).difference(plates.hole(0.0, 0.0, 2.0 * r_lip)), d.lip_h_mm), dz=z_top - d.lip_h_mm)
    lead_in = cad.translate(cad.frustum(r_lip, r_clear + 0.02, d.lip_mm), dz=z_top - d.lip_mm)
    add = cad.union([skirt, cad.cut(lip, [lead_in])])
    slots = []
    for k in range(d.tabs):
        slot = cad.box(d.slot_w_mm, r_out + 2.0, d.tab_len_mm + 1.0, cy=(r_out + 2.0) / 2.0 + r_lip - 1.0, z0=z_top - d.tab_len_mm)
        slots.append(cad.rotate_z(slot, 360.0 * k / d.tabs))
    return add, cad.union(slots)


def cap_outer_radius(d: RimClipDims) -> float:
    return d.clear_model_mm / 2.0 + d.tab_t_mm


def lid_lip(d: RimClipDims, opening_w: float, opening_h: float, z_seat: float) -> tuple["cad.Part", "cad.Part"]:
    """``(add, cut)`` for a rectangular lip rising ``TAB_LEN_MM`` from ``z_seat`` inside an ``opening_w`` × ``opening_h`` opening
    (``opening_w`` is the connector's nominal): the lip walls with the bulge on the long sides (add), the corner slits (cut)."""
    shrink = 1.0 + d.comp.shrink_pct / 100.0
    outer_w = d.clear_model_mm
    outer_h = (opening_h - 2.0 * d.clearance_mm) * shrink + d.comp.xy_outer_comp_mm
    inner = box(-outer_w / 2.0 + d.tab_t_mm, -outer_h / 2.0 + d.tab_t_mm, outer_w / 2.0 - d.tab_t_mm, outer_h / 2.0 - d.tab_t_mm)
    walls = cad.translate(cad.prism(box(-outer_w / 2.0, -outer_h / 2.0, outer_w / 2.0, outer_h / 2.0).difference(inner), d.tab_len_mm), dz=z_seat)
    z_top = z_seat + d.tab_len_mm
    x0, x1 = -outer_w / 2.0 + d.tab_t_mm, outer_w / 2.0 - d.tab_t_mm
    bulges = [
        cad.translate(cad.prism(box(x0, outer_h / 2.0 - d.tab_t_mm, x1, outer_h / 2.0 + d.lip_mm), d.lip_h_mm), dz=z_top - d.lip_h_mm),
        cad.translate(cad.prism(box(x0, -outer_h / 2.0 - d.lip_mm, x1, -outer_h / 2.0 + d.tab_t_mm), d.lip_h_mm), dz=z_top - d.lip_h_mm),
    ]
    add = cad.union([walls, *bulges])
    slits = []
    for sx in (-1.0, 1.0):
        for sy in (-1.0, 1.0):
            slits.append(cad.box(d.slot_w_mm, 2.0 * d.tab_t_mm + 2.0 * d.lip_mm + 1.0, d.tab_len_mm - 1.0,
                                 cx=sx * (outer_w / 2.0 - d.tab_t_mm - d.slot_w_mm / 2.0), cy=sy * outer_h / 2.0, z0=z_seat + 1.0))
    return add, cad.union(slits)


def rim_radii(d: RimClipDims, pot_r: float) -> tuple[float, float, float, float]:
    """``(inner jaw inside, inner jaw outside, outer skirt inside, outer skirt outside)`` radii for a pot of ``pot_r``."""
    r_in_jaw_in = pot_r - d.nominal_mm - d.clearance_mm - d.tab_t_mm
    r_in_jaw_out = r_in_jaw_in + d.tab_t_mm
    r_out_jaw_in = r_in_jaw_out + d.clear_model_mm
    return r_in_jaw_in, r_in_jaw_out, r_out_jaw_in, r_out_jaw_in + d.tab_t_mm


def rim_band(d: RimClipDims, pot_r: float, arc_deg: float, z_seat: float) -> tuple["cad.Part", "cad.Part | None"]:
    """``(add, cut)`` for C-jaws over a pot rim ``nominal_mm`` thick whose outer radius is ``pot_r``, spanning ``arc_deg`` centred
    on −Y, printed upside down: the bridge lies from ``z_seat − BRIDGE_MM`` to ``z_seat``, the inner jaw rises ``TAB_LEN_MM``
    above the seat with its lip at the top, the outer skirt rises plain (add); the slots that split the inner jaw into
    tabs (cut)."""
    from ..templates import plates

    r_in_jaw_in, r_in_jaw_out, r_out_jaw_in, r_out_jaw_out = rim_radii(d, pot_r)
    a0, a1 = -90.0 - arc_deg / 2.0, -90.0 + arc_deg / 2.0
    bridge = cad.translate(cad.prism(plates.ring_sector(r_in_jaw_in, r_out_jaw_out, a0, a1), BRIDGE_MM), dz=z_seat - BRIDGE_MM)
    inner_jaw = cad.translate(cad.prism(plates.ring_sector(r_in_jaw_in, r_in_jaw_out, a0, a1), d.tab_len_mm), dz=z_seat)
    outer_jaw = cad.translate(cad.prism(plates.ring_sector(r_out_jaw_in, r_out_jaw_out, a0, a1), d.tab_len_mm), dz=z_seat)
    z_top = z_seat + d.tab_len_mm
    inner_lip = cad.translate(cad.prism(plates.ring_sector(r_in_jaw_out - 0.01, r_in_jaw_out + d.lip_mm, a0, a1), d.lip_h_mm - 1.0), dz=z_top - d.lip_h_mm)
    lead_in = cad.translate(cad.prism(plates.ring_sector(r_in_jaw_out - 0.01, r_in_jaw_out + d.lip_mm / 2.0, a0, a1), 1.0), dz=z_top - 1.0)
    add = cad.union([bridge, inner_jaw, outer_jaw, inner_lip, lead_in])
    slots = []
    for k in range(1, d.tabs):
        angle = a0 + (a1 - a0) * k / d.tabs
        reach = r_in_jaw_out + d.lip_mm + 1.0
        slot = cad.box(d.slot_w_mm, reach - (r_in_jaw_in - 1.0), d.tab_len_mm - 1.0, cy=(reach + r_in_jaw_in - 1.0) / 2.0, z0=z_seat + 1.0)
        slots.append(cad.rotate_z(slot, angle - 90.0))
    return add, cad.union(slots) if slots else None


# ---- report, coupon ----------------------------------------------------------------------------------------------


def report(
    connector: "Connector",
    d: RimClipDims,
    frame: "AnchorFrame",
    comp: Compensation,
    material_id: str | None,
    *,
    tolerance_class: str = "molded",
    pin_d_mm: float | None = None,
    fit_tested: bool = False,
    range_mm: Any = None,
    **_: Any,
) -> dict[str, Any]:
    from .base import tolerance_for

    if range_mm:
        d = RimClipDims(**{**d.__dict__, "range_mm": (float(range_mm[0]), float(range_mm[1]))})
    else:  # one product, not a class: it varies by its tolerance class
        tol = tolerance_for(tolerance_class, "molded")
        d = RimClipDims(**{**d.__dict__, "range_mm": (d.nominal_mm - tol, d.nominal_mm + tol)})
    return {
        "kind": connector.kind,
        "fit": connector.fit,
        "form": d.form,
        "nominal_mm": d.nominal_mm,
        "range_mm": [d.range_mm[0], d.range_mm[1]],
        "depth_mm": d.tab_len_mm,
        "mouth_mm": [round(float(v), 4) for v in frame.origin],
        "axis": [round(float(v), 6) for v in frame.normal],
        "tab_t_mm": d.tab_t_mm,
        "tab_len_mm": d.tab_len_mm,
        "tabs": d.tabs,
        "lip_mm": d.lip_mm,
        "clearance_mm": d.clearance_mm,
        "clear_mm": round(d.clear_model_mm, 4),
        "strain_pct": round(d.strain_pct, 3),
        "strain_pct_max": round(d.strain_pct_max, 3),
        "grip_min_mm": round(d.grip_min_mm, 3),
        "min_grip_mm": MIN_GRIP_MM,
        "max_strain_pct": comp.max_strain_pct,
        "creep_class": comp.creep_class,
        "pin_d_mm": float(pin_d_mm) if pin_d_mm is not None else d.nominal_mm,
        "pin_tolerance_mm": tolerance_for(tolerance_class, "molded"),
        "tolerance_class": tolerance_class,
        "min_wall_mm": MIN_TAB_T_MM,
        "compensation": comp.descriptor(),
        "material_id": material_id,
        "adapter_sku": connector.adapter_sku,
        "fit_tested": bool(fit_tested),
    }


def expected_printed(d: RimClipDims) -> dict[str, float]:
    c = d.clearance_mm
    if d.form == "cap":
        out = {"skirt_id_mm": round(d.nominal_mm + 2.0 * c, 3), "lip_id_mm": round(d.nominal_mm + 2.0 * c - 2.0 * d.lip_mm, 3)}
    elif d.form == "lid":
        out = {"lip_w_mm": round(d.nominal_mm - 2.0 * c, 3), "bulge_w_mm": round(d.nominal_mm - 2.0 * c + 2.0 * d.lip_mm, 3)}
    else:
        out = {"jaw_gap_mm": round(d.nominal_mm + 2.0 * c, 3), "lip_gap_mm": round(d.nominal_mm + 2.0 * c - 2.0 * d.lip_mm, 3)}
    out.update({"tab_t_mm": d.tab_t_mm, "tab_len_mm": d.tab_len_mm, "strain_pct": round(d.strain_pct, 3), "clearance_mm": c})
    return out


COUPON_PLATE_MM = 2.0
COUPON_RIM_POT_R_MM = 60.0
COUPON_RIM_ARC_DEG = 40.0


def coupon_part(connector: "Connector", d: RimClipDims, wall_mm: float) -> "cad.Part":
    """The clip itself on a small plate (cap, lid) or the C-band over a 120 mm pot's rim, seat on z = ``COUPON_PLATE_MM``."""
    from ..templates import plates

    if d.form == "cap":
        plate = cad.prism(plates.disc(0.0, 0.0, cap_outer_radius(d) + 1.0), COUPON_PLATE_MM)
        add, cut = cap_skirt(d, COUPON_PLATE_MM, d.tab_len_mm + 2.0)
        return cad.cut(cad.union([plate, add]), [cut])
    if d.form == "lid":
        opening_h = round(d.nominal_mm * 0.66, 1)
        plate = cad.prism(plates.rounded_rect(d.nominal_mm + 4.0, opening_h + 4.0, 3.0), COUPON_PLATE_MM)
        add, cut = lid_lip(d, d.nominal_mm, opening_h, COUPON_PLATE_MM)
        return cad.cut(cad.union([plate, add]), [cut])
    add, cut = rim_band(d, COUPON_RIM_POT_R_MM, COUPON_RIM_ARC_DEG, BRIDGE_MM)
    return cad.cut(add, [cut]) if cut is not None else add


def coupon_frame(d: RimClipDims) -> "AnchorFrame":
    """The seat: on the plate's top (cap, lid) or the bridge's top (rim), normal into the plate."""
    from ..features.frames import AnchorFrame

    z = COUPON_PLATE_MM if d.form in ("cap", "lid") else BRIDGE_MM
    return AnchorFrame(np.array([0.0, 0.0, z]), [1, 0, 0], [0, -1, 0], [0, 0, -1])


def measure(mesh: trimesh.Trimesh, frame: "AnchorFrame", d: RimClipDims) -> dict[str, Any]:
    """Read the lip back off the mesh: the clip's narrowest opening at the tabs' free end (cap: the lip Ø)."""
    local = frame.to_local(mesh.vertices)
    z_tab = -local[:, 2]  # tabs run against the normal
    free_end = float(z_tab.max())
    near_end = (z_tab > free_end - d.lip_h_mm - 0.05) & (z_tab < free_end + 0.05)
    if not near_end.any():
        return {"vertices": 0}
    if d.form == "cap":
        r = np.hypot(local[near_end, 0], local[near_end, 1])
        return {"vertices": int(near_end.sum()), "lip_id_mm": round(2.0 * float(r.min()), 3)}
    if d.form == "lid":
        return {"vertices": int(near_end.sum()), "bulge_w_mm": round(float(np.ptp(local[near_end, 1])), 3)}
    return {"vertices": int(near_end.sum())}


__all__ = [
    "BRIDGE_MM",
    "CLEARANCE_MM",
    "LIP_MM",
    "MIN_TAB_T_MM",
    "RimClipDims",
    "TABS",
    "TAB_LEN_MM",
    "TAB_T_MM",
    "cap_outer_radius",
    "cap_skirt",
    "check_material",
    "coupon_frame",
    "coupon_part",
    "default_depth",
    "descriptor_extras",
    "dims",
    "expected_printed",
    "label",
    "lid_lip",
    "measure",
    "report",
    "rim_band",
    "rim_radii",
    "tool_mesh",
]
