"""``desk_nameplate@1`` (Pehchaan): it stands, it refuses combinations that would rock backwards, its face leans."""

from __future__ import annotations

import itertools
import math

import numpy as np
import pytest

from aakar_geometry.errors import ParamOutOfRange
from aakar_geometry.templates import DeskNameplate
from aakar_geometry.templates.desk_nameplate import BASE_HEIGHT_MM, MIN_TIPPING_MARGIN_MM, TIP_SAFETY_MM, Layout

EXTREMES = {
    "width_mm": (120, 250),
    "height_mm": (40, 100),
    "tilt_deg": (60, 80),
    "base_depth_mm": (30, 60),
    "thickness_mm": (4, 8),
}


def _corners():
    keys = list(EXTREMES)
    for values in itertools.product(*(EXTREMES[k] for k in keys)):
        yield dict(zip(keys, values))


def test_descriptor():
    desc = DeskNameplate.descriptor()
    assert (desc["id"], desc["family"], desc["name"]) == ("desk_nameplate", "nameplate", "Pehchaan desk nameplate")
    p = desc["params"]
    # 250 is the bed: the family envelope reaches 300 mm but a longer plate cannot be printed in one piece here
    assert (p["width_mm"]["min"], p["width_mm"]["max"], p["width_mm"]["default"]) == (120, 250, 180.0)
    assert p["width_mm"]["max"] <= min(desc["constraints"]["bed_mm"])
    assert (p["height_mm"]["min"], p["height_mm"]["max"], p["height_mm"]["default"]) == (40, 100, 60.0)
    assert (p["tilt_deg"]["min"], p["tilt_deg"]["max"], p["tilt_deg"]["default"]) == (60, 80, 70.0)
    assert (p["base_depth_mm"]["min"], p["base_depth_mm"]["max"], p["base_depth_mm"]["default"]) == (30, 60, 40.0)
    assert (p["thickness_mm"]["min"], p["thickness_mm"]["max"], p["thickness_mm"]["default"]) == (4.0, 8.0, 5.0)
    face, base_front = desc["anchors"]
    assert face["id"] == "face" and face["max_text_height_mm"] == pytest.approx(0.6 * 60)
    assert base_front["id"] == "base_front" and base_front["size_mm"][1] == pytest.approx(BASE_HEIGHT_MM - 2.0)
    assert desc["hardware"] == [{"sku": "adhesive_pads", "qty": 1}]


def test_every_corner_either_stands_or_is_refused_up_front():
    """validate_combination refuses exactly the corners whose centre of gravity sits too near the back edge."""
    from aakar_inspect import Constraints, inspect_mesh

    refused = []
    for corner in _corners():
        margin = Layout(corner).tipping_margin()
        if margin < MIN_TIPPING_MARGIN_MM + TIP_SAFETY_MM:
            with pytest.raises(ParamOutOfRange) as exc:
                DeskNameplate.validate(corner)
            assert exc.value.keys == ["base_depth_mm", "height_mm", "tilt_deg"]
            assert "foot deeper" in exc.value.message
            refused.append(corner)
            continue
        params = DeskNameplate.validate(corner)
        if corner["width_mm"] == 250 or corner["base_depth_mm"] == 30:  # inspect a representative half (≈4 s)
            report, _ = inspect_mesh(DeskNameplate.build_body(params), Constraints(min_wall_mm=1.2))
            assert report["checks"]["centre_of_gravity"]["status"] == "pass", corner
            assert report["checks"]["tipping_margin"]["status"] == "pass", corner
            assert report["checks"]["tipping_margin"]["value"] == pytest.approx(margin, abs=0.05), corner
            assert report["passed"] is True, corner
    # only a tall face at the shallowest lean on the shortest foot tips over
    assert refused and all(c["height_mm"] == 100 and c["tilt_deg"] == 60 and c["base_depth_mm"] == 30 for c in refused)


def test_the_face_frame_is_perpendicular_to_the_leaning_plate():
    params = DeskNameplate.validate({"tilt_deg": 65})
    frame = DeskNameplate.anchor_frame("face", params)
    th = math.radians(65)
    assert np.allclose(frame.normal, [0.0, -math.sin(th), math.cos(th)])  # towards the reader, tipped up
    assert np.allclose(frame.v, [0.0, math.cos(th), math.sin(th)])  # up the slope
    assert np.allclose(frame.u, [1.0, 0.0, 0.0])
    assert frame.size_mm == pytest.approx((180 - 4.0, 60 - 4.0))
    mesh = DeskNameplate.build_body(params)
    # the face spans the plate: its top corners sit on the skin at the plate's top edge (less the margin)
    top = frame.to_world([[0.0, frame.size_mm[1] / 2 + 2.0, 0.0]])[0]
    assert top[2] == pytest.approx(mesh.bounds[1][2], abs=1e-6)
    base = DeskNameplate.anchor_frame("base_front", params)
    assert np.allclose(base.normal, [0, -1, 0]) and base.origin[1] == pytest.approx(mesh.bounds[0][1], abs=1e-6)


def test_the_bounding_box_is_centred_even_when_the_plate_leans_past_the_foot():
    params = DeskNameplate.validate({"height_mm": 100, "tilt_deg": 62, "base_depth_mm": 40, "thickness_mm": 4})
    layout = Layout(params)
    assert layout.y_foot + layout.L * layout.cos + layout.t * layout.sin > layout.D / 2  # leans past the foot
    mesh = DeskNameplate.build_body(params)
    assert (mesh.bounds[0][1] + mesh.bounds[1][1]) / 2 == pytest.approx(0.0, abs=1e-6)
    assert mesh.extents[1] > params["base_depth_mm"]


def test_karigar_note():
    note = DeskNameplate.karigar_note(DeskNameplate.validate({}))
    assert note.startswith("A Pehchaan desk nameplate, 180 mm wide, its 60 mm face leaning back at 70°") and "foam pads" in note
