"""``raw_print@1`` - Swaroop: the customer's own model file, printed as it is (ADR-0014).

The one template whose whole geometry is customer content. It has no parameters and no body of its
own: exactly one ``hero_mesh`` feature on the volume anchor ``body`` carries the form, and the size and
orientation live on that feature (``fit: longest`` with ``longest_mm``, ``orientation``, ``yaw_deg``;
design-spec.v1.json). The features stage loads, repairs, orients and scales the model and seats it
centred on the bed at Z = 0 (``features.hero_mesh``); then the normal export → inspect → estimate runs.

Rules (``validate_content``, before anything is fetched or built):
  * a spec without the model is refused (``invalid_spec``: "Add your model file to print it as it is");
  * the size is explicit: ``fit`` must be ``longest`` (``contain`` would silently print it as big as the
    bed allows);
  * ``longest_mm`` must sit inside the family envelope (``raw_print`` in families.json: 20–240 mm);
    outside it is ``param_out_of_range``, never clamped. ``bounds_mm`` is the bed limited by the same
    envelope, so the features stage also refuses a model whose shorter sides would overflow it.
A model with walls under the printability threshold still completes, with ``printability.passed``
false (the product rule: the customer is told, not refused).
"""

from __future__ import annotations

from typing import Any, Mapping, Sequence

import numpy as np
import trimesh

from .. import families
from ..errors import InvalidSpec, ParamOutOfRange
from ..features.frames import AnchorFrame
from .base import Anchor, Template, TemplateConstraints

BED_MM = (250.0, 250.0, 250.0)
_FALLBACK_ENVELOPE = (20.0, 240.0)


def _envelope() -> tuple[float, float]:
    try:
        return families.size_envelope("raw_print") or _FALLBACK_ENVELOPE
    except families.UnknownFamily:  # pragma: no cover - the registry refuses the template first
        return _FALLBACK_ENVELOPE


MIN_LONGEST_MM, MAX_LONGEST_MM = _envelope()
BOUNDS_MM = tuple(min(bed, MAX_LONGEST_MM) for bed in BED_MM)

MISSING_MODEL = "Add your model file to print it as it is"


class RawPrint(Template):
    id = "raw_print"
    version = 1
    family = "raw_print"
    name = "Swaroop · Print as it is"
    description = (
        "Your own 3D model file, checked for gaps and thin walls, repaired where needed, sized to the "
        "longest side you choose and printed as it is."
    )
    environment = "studio"
    params = {}
    anchors = (
        Anchor("body", "Your model", "planar", kind="volume", bounds_mm=BOUNDS_MM, accepts=("hero_mesh",)),
    )
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=BED_MM)
    style_variants = ()
    features_supported = ("hero_mesh",)
    hardware = ()
    min_feature_mm = 0.8

    @classmethod
    def validate_content(cls, params: Mapping[str, Any], features: Sequence[Mapping[str, Any]]) -> None:
        heroes = [(index, f) for index, f in enumerate(features or []) if f.get("type") == "hero_mesh"]
        if not heroes:
            raise InvalidSpec(MISSING_MODEL, {"template": cls.ref(), "needs": "one hero_mesh feature on the body anchor"})
        # check_features already allows one form per anchor, and body is the only anchor
        index, hero = heroes[0]
        lo, hi = MIN_LONGEST_MM, MAX_LONGEST_MM
        if hero.get("fit", "contain") != "longest":
            raise InvalidSpec(
                f"Choose how long your model should be on its longest side ({lo:g}–{hi:g} mm)",
                {"feature": index, "fit": hero.get("fit"), "needs": "fit: longest with longest_mm"},
            )
        longest = float(hero["longest_mm"])  # check_features insists on it when fit is longest
        if not lo <= longest <= hi:
            raise ParamOutOfRange(
                [f"features[{index}].longest_mm"],
                f"Your model can be printed {lo:g}–{hi:g} mm on its longest side; {longest:g} mm was asked",
                {"longest_mm": longest, "min_longest_mm": lo, "max_longest_mm": hi},
            )

    @classmethod
    def build_body(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        return trimesh.Trimesh()  # no body: the customer's form is the whole print

    @classmethod
    def anchor_frame(cls, anchor_id: str, params: Mapping[str, Any]) -> AnchorFrame:
        if anchor_id != "body":
            raise NotImplementedError(f"{cls.ref()} does not place anchor {anchor_id!r}")
        # centre of the bed, seated on it, Z up
        return AnchorFrame(np.zeros(3), [1, 0, 0], [0, 1, 0], [0, 0, 1], bounds_mm=BOUNDS_MM)

    @classmethod
    def karigar_note(cls, params: Mapping[str, Any]) -> str:
        return (
            "Printed as it is from your own model file: checked for gaps and thin walls, repaired where needed "
            "and sized to the longest side you chose. Supports and infill are the karigar's call."
        )
