"""``photo_frame_std@1`` (Chaukhat): the framer's rabbet measured on the solid, decoration that never reaches the
rabbet and is always cut in, an easel that stands and leaves the rabbet open, a hanger with a nail hole."""

from __future__ import annotations

import itertools
import math

import numpy as np
import pytest

from aakar_geometry.contracts import validate
from aakar_geometry.errors import UnsupportedFeature
from aakar_geometry.features import check_features
from aakar_geometry.pipeline import build_design, is_completed
from aakar_geometry.templates import PhotoFrameStd
from aakar_geometry.templates.photo_frame_std import (
    DEPTH_MM,
    EASEL_LEAN_DEG,
    HANG_HOLE_MM,
    HANG_RIM_MM,
    LIP_MM,
    MAX_RELIEF_MM,
    RABBET_DEPTH_MM,
    Layout,
)
from aakar_geometry.templates.plates import HOLE_ALLOWANCE_MM, MIN_SKIN_MM

from carrier_checks import round_holes, section
from conftest import DESIGN_ID, JOB_ID

CORNERS = [
    {"orientation": o, "border_mm": b, "stand": s}
    for o, b, s in itertools.product(("portrait", "landscape"), (12, 25), ("easel", "hanger"))
]


def name(anchor: str, text: str = "Asha", **extra) -> dict:
    return {"type": "emboss_text", "text": text, "anchor": anchor, "mode": "deboss", **extra}


def _holes(area) -> list[tuple[float, float]]:
    """(width, height) of every hole in a section, largest first."""
    polys = list(area.geoms) if hasattr(area, "geoms") else [area]
    sizes = []
    for poly in polys:
        for ring in poly.interiors:
            x = np.asarray(ring.coords)
            sizes.append((float(np.ptp(x[:, 0])), float(np.ptp(x[:, 1]))))
    return sorted(sizes, key=lambda s: -s[0] * s[1])


def test_descriptor():
    desc = PhotoFrameStd.descriptor()
    assert (desc["id"], desc["family"], desc["name"], desc["environment"]) == (
        "photo_frame_std", "photo_frame", "Chaukhat photo frame", "teak_table_candlelight",
    )
    p = desc["params"]
    assert p["orientation"]["options"] == ["portrait", "landscape"] and p["orientation"]["default"] == "portrait"
    assert (p["border_mm"]["min"], p["border_mm"]["max"], p["border_mm"]["default"]) == (12, 25, 18.0)
    assert p["stand"]["options"] == ["easel", "hanger"] and p["stand"]["default"] == "easel"
    border, base_front = desc["anchors"]
    assert border["id"] == "border" and border["accepts"] == ["motif", "emboss_text"]
    assert base_front["id"] == "base_front" and base_front["accepts"] == ["emboss_text"]
    assert border["max_relief_mm"] == base_front["max_relief_mm"] == MAX_RELIEF_MM
    assert desc["hardware"] == [{"sku": "acrylic_4x6", "qty": 1}]


@pytest.mark.parametrize("orientation", ["portrait", "landscape"])
@pytest.mark.parametrize("border", [12, 18, 25])
def test_window_and_rabbet_follow_the_framers_rules(orientation, border):
    params = PhotoFrameStd.validate({"orientation": orientation, "border_mm": border, "stand": "hanger"})
    mesh = PhotoFrameStd.build_body(params)  # lying on its back, the front up
    assert mesh.extents[2] == pytest.approx(DEPTH_MM)
    photo = (101.6, 152.4) if orientation == "portrait" else (152.4, 101.6)
    window = _holes(section(mesh, DEPTH_MM - LIP_MM / 2.0))[0]  # through the lip
    assert window == pytest.approx((photo[0] - 12.7, photo[1] - 12.7), abs=1e-3)  # 1/4 in of the photo under the lip
    rabbet = _holes(section(mesh, RABBET_DEPTH_MM / 2.0))[0]  # behind it
    assert rabbet == pytest.approx((photo[0] + 1.6, photo[1] + 1.6), abs=1e-3)  # 1/16 in larger than the photo
    # from the back: the rabbet is open, 9.5 mm (3/8 in) deep to the lip, and the lip is 3 mm
    L = Layout(params)
    x = photo[0] / 2.0 - 2.0
    y = float(mesh.bounds[:, 1].mean())  # mid-height, in the lip's band beside the window
    hits, _, _ = mesh.ray.intersects_location([[x, y, -5.0]], [[0, 0, 1.0]], multiple_hits=True)
    zs = sorted(hits[:, 2])
    assert zs[0] == pytest.approx(RABBET_DEPTH_MM) and zs[1] == pytest.approx(DEPTH_MM)
    outer = L.outer
    assert mesh.extents[0] == pytest.approx(outer[0]) and outer[0] == pytest.approx(photo[0] - 12.7 + 2 * border)


@pytest.mark.parametrize("corner", CORNERS, ids=lambda c: "-".join(str(v) for v in c.values()))
def test_decoration_areas_sit_on_the_rails_clear_of_the_window(corner):
    L = Layout(PhotoFrameStd.validate(corner))
    window_bottom, window_top = L.border, L.border + L.window[1]
    for top in (True, False):
        y, w, h = L.band(top)
        y0, y1 = y - h / 2.0, y + h / 2.0
        assert (y0 >= window_top) if top else (y1 <= window_bottom)  # never over the photo
        assert y0 >= 0.0 and y1 <= L.outer[1] and w <= L.outer[0]
        assert min(w, h) >= 8.0
    # the deepest cut over the rabbet leaves the minimum skin of the lip
    assert LIP_MM - MAX_RELIEF_MM >= MIN_SKIN_MM


def test_a_deepest_cut_name_over_the_rabbet_leaves_the_lip_whole():
    params = PhotoFrameStd.validate({"border_mm": 12, "stand": "hanger"})
    body = PhotoFrameStd.build_body(params)
    mesh = PhotoFrameStd.build(params, [name("border", "Ghar Ghar", depth_mm=MAX_RELIEF_MM)])
    assert mesh.is_watertight and mesh.body_count == 1
    # the rabbet behind the lip is untouched: same section, same volume behind the lip
    assert section(mesh, RABBET_DEPTH_MM / 2.0).area == pytest.approx(section(body, RABBET_DEPTH_MM / 2.0).area, rel=1e-6)
    frame = PhotoFrameStd.anchor_frame("border", params)
    w, h = frame.size_mm
    lip_band = Layout(params).lip_band - 1.5  # the part of the band over the rabbet
    cut_floor = DEPTH_MM - MAX_RELIEF_MM
    skins = []
    for u in np.linspace(-w / 2 + 1.0, w / 2 - 1.0, 60):
        for v in np.linspace(-h / 2 + 0.2, -h / 2 + lip_band - 0.2, 6):
            start = frame.to_world([[u, v, 5.0]])[0]
            hits, _, _ = mesh.ray.intersects_location([start], [-frame.normal], multiple_hits=True)
            zs = sorted(hits[:, 2], reverse=True)
            if len(zs) >= 2 and zs[0] == pytest.approx(cut_floor, abs=1e-6):
                skins.append(zs[0] - zs[1])
    assert skins and min(skins) == pytest.approx(DEPTH_MM - MAX_RELIEF_MM - RABBET_DEPTH_MM, abs=1e-6)
    assert min(skins) >= MIN_SKIN_MM


@pytest.mark.parametrize("feature", [name("border", mode="emboss"), {"type": "emboss_text", "text": "Asha", "anchor": "base_front"}, {"type": "motif", "motif_id": "lotus", "anchor": "border", "mode": "emboss"}])
def test_raised_decoration_is_refused_it_is_cut_in(feature):
    params = PhotoFrameStd.validate({})
    with pytest.raises(UnsupportedFeature) as exc:
        PhotoFrameStd.validate_content(params, check_features(PhotoFrameStd, [feature]))
    assert "is cut into the face, not raised" in exc.value.message and "mesh" not in exc.value.message.lower()
    assert exc.value.detail["key"] == "features[0].mode"


@pytest.mark.parametrize("corner", [c for c in CORNERS if c["stand"] == "easel"], ids=lambda c: "-".join(str(v) for v in c.values()))
def test_the_easel_stands_leaning_back_on_its_feet_with_the_rabbet_open(corner):
    from aakar_inspect import Constraints, inspect_mesh

    params = PhotoFrameStd.validate(corner)
    L = Layout(params)
    mesh = PhotoFrameStd.build_body(params)
    report, _ = inspect_mesh(mesh, Constraints(min_wall_mm=1.2))
    assert report["checks"]["centre_of_gravity"]["status"] == "pass" and report["checks"]["tipping_margin"]["status"] == "pass"
    # the frame rests on its back bottom edge and the feet lie flat on the table, reaching back behind it
    on_table = mesh.vertices[np.abs(mesh.vertices[:, 2]) < 1e-6]
    assert np.ptp(on_table[:, 1]) > 40.0
    lean = math.radians(EASEL_LEAN_DEG)
    face = PhotoFrameStd.anchor_frame("border", params)
    assert np.allclose(face.normal, [0.0, -math.cos(lean), math.sin(lean)]) and np.allclose(face.u, [1, 0, 0])
    # the feet stand on the side walls, outside the rabbet's opening
    assert L.foot_x - L.foot_t / 2.0 > L.rabbet[0] / 2.0 and L.foot_x + L.foot_t / 2.0 < L.outer[0] / 2.0
    assert L.foot_t >= 3.0


def test_the_hanger_has_a_nail_hole_in_a_tab_at_the_top():
    params = PhotoFrameStd.validate({"stand": "hanger"})
    mesh = PhotoFrameStd.build_body(params)
    tab_holes = [h for h in round_holes(section(mesh, DEPTH_MM / 2.0)) if h[2] < 5.0]
    assert len(tab_holes) == 1
    centre, narrowest, widest = tab_holes[0]
    cut = HANG_HOLE_MM + HOLE_ALLOWANCE_MM
    assert narrowest == pytest.approx(cut / 2.0, abs=2e-3) and widest < cut / 2.0 + 0.01
    assert centre.x == pytest.approx(0.0, abs=1e-6) and centre.y > mesh.bounds[1][1] - 12.0
    assert mesh.bounds[1][1] - centre.y - widest >= HANG_RIM_MM - 1e-3
    assert np.allclose(PhotoFrameStd.anchor_frame("border", params).normal, [0, 0, 1])  # lying on its back


def test_the_pipeline_completes_a_frame_with_its_pane_and_refuses_raised_names(local_storage):
    request = {
        "job_id": JOB_ID,
        "design_id": DESIGN_ID,
        "version_no": 1,
        "outputs": ["stl"],
        "spec": {
            "spec_version": "1.0",
            "family": "photo_frame",
            "template": "photo_frame_std@1",
            "params": {"orientation": "landscape", "stand": "hanger"},
            "features": [{"type": "motif", "motif_id": "paisley", "anchor": "border"}, name("base_front", "Our first home")],
            "material": "sandalwood_silk",
        },
    }
    payload = build_design(request, storage=local_storage)
    assert is_completed(payload), payload
    assert payload["hardware"] == [{"sku": "acrylic_4x6", "qty": 1}] and payload["printability"]["passed"] is True
    assert "“Our first home” is cut 1.2 mm into the bottom rail" in payload["karigar_note"]
    request["spec"]["features"] = [{"type": "emboss_text", "text": "Asha", "anchor": "base_front"}]
    payload = build_design(request, storage=local_storage)
    assert payload["code"] == "unsupported_feature" and "cut into the face" in payload["message"]
    validate("design.failed", payload)


def test_karigar_note():
    note = PhotoFrameStd.karigar_note(PhotoFrameStd.validate({}))
    assert note.startswith("A portrait Chaukhat frame for a 4 × 6 in photo, with a 18 mm border and a 88.9 × 139.7 mm window")
    assert "face down" in note and "acrylic pane" in note and "leaning at 15°" in note
    assert "nail" in PhotoFrameStd.karigar_note(PhotoFrameStd.validate({"stand": "hanger"}))
