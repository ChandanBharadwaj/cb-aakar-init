"""``kadi_coupon@1``: the Kadi fit coupon, a flange with the socket only (plan §1.4).

Not a product and not registered: ``aakar-geometry coupon`` builds one per socket size and finish, runs inspect's
``connector_fit`` on it and prints the modelled numbers the bench measures the prints against (bore and crest
diameters, rib angles). The outer wall is sized from the PLA-modelled bore so ``wall_mm`` is the true wall in PLA; a
``wall_mm`` under the connector's 1.6 mm minimum is how the check's failure is demonstrated.
"""

from __future__ import annotations

from typing import Any, Mapping

import numpy as np
import trimesh

from .. import cad
from ..connectors import Connector, compensation
from ..connectors import socket as kadi_socket
from ..features.frames import AnchorFrame
from . import plates
from .base import Param, Template, TemplateConstraints

NOMINAL_OPTIONS = tuple(f"{n:g}" for n in kadi_socket.NOMINALS_MM)
FLOOR_MM = 3.0  # plastic over the socket's floor

PARAMS = {
    "nominal_mm": Param("enum", "Kadi size", "12", options=NOMINAL_OPTIONS, group="Kadi", description="The pin diameter the socket presses onto."),
    "wall_mm": Param(
        "number", "Wall", 3.0, "mm", 1.0, 6.0, 0.1, group="Size",
        description="Plastic round the socket (in PLA); connector_fit wants at least 1.6 mm.",
    ),
}


class KadiCoupon(Template):
    id = "kadi_coupon"
    version = 1
    family = "kadi_coupon"  # not a family in families.json: this template is never registered
    name = "Kadi fit coupon"
    description = "A flange with one Kadi-S socket, printed per size and finish to measure the fit before a base is sold."
    environment = "studio"
    params = PARAMS
    anchors = ()
    constraints = TemplateConstraints(min_wall_mm=1.2, max_overhang_deg=55, bed_mm=(250, 250, 250))
    features_supported = ()
    hardware = ()
    min_feature_mm = 0.8
    connector = Connector("socket", 12.0)  # the descriptor's default; connector_for picks the size from the params

    @classmethod
    def connector_for(cls, params: Mapping[str, Any]) -> Connector:
        return Connector("socket", float(params["nominal_mm"]))

    @classmethod
    def outer_d(cls, params: Mapping[str, Any]) -> float:
        d = kadi_socket.dims(cls.connector_for(params), compensation.DEFAULTS["pla"])
        return d.bore_d_model_mm + 2.0 * float(params["wall_mm"])

    @classmethod
    def build_body(cls, params: Mapping[str, Any]) -> trimesh.Trimesh:
        connector = cls.connector_for(params)
        disc = plates.disc(0.0, 0.0, cls.outer_d(params) / 2.0)
        mesh = cad.to_trimesh(cad.prism(disc, connector.depth() + FLOOR_MM))
        mesh.apply_translation([0.0, 0.0, -float(mesh.bounds[0][2])])
        return mesh

    @classmethod
    def connector_frame(cls, params: Mapping[str, Any]) -> AnchorFrame:
        return AnchorFrame(np.array([0.0, 0.0, 0.0]), [1, 0, 0], [0, 1, 0], [0, 0, 1])

    @classmethod
    def karigar_note(cls, params: Mapping[str, Any]) -> str:
        return (
            f"A Kadi fit coupon for a {float(params['nominal_mm']):g} mm pin with a {float(params['wall_mm']):g} mm wall: print it "
            "flat, measure the socket, press the pin in ten times and record the pull-off."
        )
