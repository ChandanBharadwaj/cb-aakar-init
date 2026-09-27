"""The features stage (Chhaap): frames, heightfields, photo reliefs, hero forms, booleans, fetchers, validation."""

from __future__ import annotations

import numpy as np
import httpx
import pytest
import trimesh

from aakar_geometry.errors import ContentUnusable, GeometryError, InvalidSpec, ParamOutOfRange, UnsupportedFeature
from aakar_geometry.features import AnchorFrame, apply_features, booleans, hero_mesh, relief_image
from aakar_geometry.features.fetch import HttpFetcher, LocalFileFetcher, format_from_url, resolve_format, sniff_format
from aakar_geometry.features.heightfield import heightfield_solid, heightfield_volume
from aakar_geometry.features.relief_image import ReliefMap, relief_heightmap
from aakar_geometry.features.validate import check, check_features, normalise_features, text_length
from aakar_geometry.templates.base import Anchor, HardwareRef, Template

from conftest import constant_png, content_source, gradient_png, make_test_templates, open_sphere_stl, soup_stl, sphere_stl


def box_on_bed(w, d, h):
    m = trimesh.creation.box(extents=[w, d, h])
    m.apply_translation([0, 0, h / 2])
    return m


def top_frame(z, size=None, bounds=None):
    return AnchorFrame([0, 0, z], [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=size, bounds_mm=bounds)


# --------------------------------------------------------------------------- frames


def test_anchor_frame_is_right_handed_and_places_meshes():
    f = AnchorFrame([10, 0, 5], [0, -1, 0], [0, 0, 1], [-1, 0, 0], size_mm=(20, 10))
    assert np.allclose(np.cross(f.u, f.v), f.normal)
    assert np.allclose(f.to_world([[1, 2, 3]]), [[10 - 3, -1, 5 + 2]])
    assert np.allclose(f.to_local(f.to_world([[1, 2, 3]])), [[1, 2, 3]])
    box = trimesh.creation.box(extents=[2, 4, 6])
    placed = f.place(box)
    assert placed.is_watertight and placed.volume == pytest.approx(box.volume)
    assert placed.extents == pytest.approx([6, 2, 4])  # local z (6) -> world -x, local x (2) -> world -y
    corners = f.corners()
    assert corners.shape == (4, 3) and np.allclose(corners[:, 0], 10)
    g = AnchorFrame.from_normal([0, 0, 0], [0, 0, 1])
    assert np.allclose(g.u, [1, 0, 0]) and np.allclose(g.v, [0, 1, 0])
    assert np.allclose(f.flipped().normal, -f.normal) and np.allclose(np.cross(f.flipped().u, f.flipped().v), f.flipped().normal)
    assert np.allclose(f.offset(-0.5).origin, [10.5, 0, 5])
    with pytest.raises(ValueError):
        AnchorFrame([0, 0, 0], [1, 0, 0], [0, 1, 0], [1, 0, 0])  # u parallel to the normal
    with pytest.raises(ValueError):
        AnchorFrame([0, 0, 0], [1, 0, 0], [0, -1, 0], [0, 0, 1])  # left-handed v


# --------------------------------------------------------------------------- heightfield


@pytest.mark.parametrize(
    "heights, cell, base, expected",
    [
        (np.full((5, 7), 0.5), 1.0, 1.0, 6 * 4 * 1.5),
        (np.tile(np.linspace(0, 2, 7), (5, 1)), 0.5, 0.3, (3.0 * 2.0) * (0.3 + 1.0)),  # ramp: mean height 1.0
        (np.zeros((2, 2)), 2.0, 0.7, 4 * 0.7),
    ],
)
def test_heightfield_solid_is_watertight_with_expected_volume(heights, cell, base, expected):
    solid = heightfield_solid(heights, cell, base)
    assert solid.is_watertight and solid.is_winding_consistent and solid.is_volume
    assert solid.volume == pytest.approx(expected, rel=1e-9)
    assert heightfield_volume(heights, cell, base) == pytest.approx(expected, rel=1e-9)
    ny, nx = heights.shape
    assert solid.extents == pytest.approx([(nx - 1) * cell, (ny - 1) * cell, base + float(heights.max())])
    assert solid.bounds[0][2] == pytest.approx(0.0)
    assert abs(solid.bounds[0][0] + solid.bounds[1][0]) < 1e-9  # centred


def test_heightfield_rejects_bad_input():
    with pytest.raises(ValueError):
        heightfield_solid(np.zeros((1, 5)), 1.0, 1.0)
    with pytest.raises(ValueError):
        heightfield_solid(np.full((3, 3), -0.1), 1.0, 1.0)
    with pytest.raises(ValueError):
        heightfield_solid(np.zeros((3, 3)), 1.0, 0.0)


# --------------------------------------------------------------------------- booleans


def test_booleans_union_and_difference_are_watertight():
    a = box_on_bed(40, 30, 4)
    b = box_on_bed(10, 10, 8)
    u = booleans.union(a, b)
    assert u.is_watertight and u.volume == pytest.approx(40 * 30 * 4 + 10 * 10 * 4)
    d = booleans.difference(a, b)
    assert d.is_watertight and d.volume == pytest.approx(40 * 30 * 4 - 10 * 10 * 4)
    with pytest.raises(GeometryError):
        booleans.union(a, trimesh.creation.random_soup(10))
    with pytest.raises(GeometryError):
        booleans.difference(a, trimesh.Trimesh())


# --------------------------------------------------------------------------- relief_image


def test_relief_heightmap_fits_the_printable_area():
    relief = relief_heightmap(gradient_png(64, 48), (40, 30), bleed_mm=2, relief_mm=0.6)
    assert isinstance(relief, ReliefMap)
    ny, nx = relief.shape
    assert nx <= 400 and ny <= 400
    # 36 × 26 mm is wider than the 4:3 photo, so contain is height-bound: 26 mm tall, 26 · 4/3 ≈ 34.7 mm wide
    assert relief.height_mm == pytest.approx(26, abs=relief.cell_mm)
    assert relief.width_mm == pytest.approx(26 * 64 / 48, abs=relief.cell_mm) and relief.width_mm <= 36
    assert relief.cell_mm == pytest.approx(1 / 5, rel=0.05)  # ~5 px/mm
    assert relief.heights.min() == pytest.approx(relief_image.FLOOR_MM) and relief.heights.max() == pytest.approx(0.6)
    # +y is up in the picture and the ramp runs left -> right: the right edge is higher than the left
    assert relief.heights[:, -1].mean() > relief.heights[:, 0].mean()
    inverted = relief_heightmap(gradient_png(64, 48), (40, 30), bleed_mm=2, relief_mm=0.6, invert=True)
    assert inverted.heights[:, -1].mean() < inverted.heights[:, 0].mean()
    # a tall picture on a wide anchor is height-bound with contain, and fills the area with cover
    tall = relief_heightmap(gradient_png(30, 90), (40, 30), fit="contain")
    assert tall.height_mm == pytest.approx(30, abs=tall.cell_mm) and tall.width_mm < 15
    cover = relief_heightmap(gradient_png(30, 90), (40, 30), fit="cover")
    assert cover.width_mm == pytest.approx(40, abs=cover.cell_mm) and cover.height_mm == pytest.approx(30, abs=cover.cell_mm)
    # the long side is capped at 400 samples
    big = relief_heightmap(gradient_png(400, 300), (200, 150))
    assert max(big.shape) == 400
    # transparent pixels have no height
    alpha = relief_heightmap(gradient_png(64, 48, alpha=True), (40, 30))
    assert alpha.heights[:, 0].max() == pytest.approx(relief_image.FLOOR_MM)


@pytest.mark.parametrize(
    "data",
    [b"", b"not an image at all", constant_png(), constant_png(0), b"\x89PNG\r\n\x1a\n" + b"\x00" * 40],
)
def test_relief_rejects_empty_or_unusable_images(data):
    with pytest.raises(ContentUnusable):
        relief_heightmap(data, (40, 30))


def test_relief_needs_room_inside_the_bleed():
    with pytest.raises(GeometryError):
        relief_heightmap(gradient_png(), (10, 10), bleed_mm=5)


def test_emboss_adds_volume_and_deboss_removes_it():
    body = box_on_bed(40, 30, 4)
    frame = top_frame(4, size=(36, 26))
    png = gradient_png()
    emb = relief_image.apply(body, frame, {"mode": "emboss", "relief_mm": 0.6}, png, bleed_mm=2)
    assert emb.is_watertight and emb.is_winding_consistent
    delta = emb.volume - body.volume
    assert 0 < delta < 0.6 * 36 * 26
    assert emb.bounds[1][2] == pytest.approx(4.6, abs=1e-3)
    assert emb.bounds[0][2] == pytest.approx(0.0, abs=1e-6)
    assert emb.extents[:2] == pytest.approx([40, 30], abs=1e-3)  # never outside the body

    deb = relief_image.apply(body, frame, {"mode": "deboss", "relief_mm": 0.6}, png, bleed_mm=2)
    assert deb.is_watertight and deb.is_winding_consistent
    assert deb.volume - body.volume == pytest.approx(-delta, rel=1e-3)
    assert deb.extents == pytest.approx([40, 30, 4], abs=1e-3)
    with pytest.raises(GeometryError):
        relief_image.apply(body, frame, {"mode": "lithophane"}, png)


def test_relief_on_a_tilted_frame_stays_watertight():
    body = box_on_bed(40, 30, 20)
    # the +x face seen from outside (looking along -x): +y is the viewer's right, +z is up, u × v = +x
    frame = AnchorFrame([20, 0, 10], [0, 1, 0], [0, 0, 1], [1, 0, 0], size_mm=(24, 16))
    emb = relief_image.apply(body, frame, {"mode": "emboss", "relief_mm": 1.0}, gradient_png(), bleed_mm=1)
    assert emb.is_watertight and emb.volume > body.volume
    assert emb.bounds[1][0] == pytest.approx(21.0, abs=1e-3)
    deb = relief_image.apply(body, frame, {"mode": "deboss", "relief_mm": 1.0}, gradient_png(), bleed_mm=1)
    assert deb.is_watertight and deb.volume < body.volume and deb.extents == pytest.approx([40, 30, 20], abs=1e-3)


# --------------------------------------------------------------------------- hero_mesh


def test_hero_contain_scales_into_bounds_and_seats_on_the_plane():
    frame = top_frame(10, bounds=(40, 40, 60))
    hero = hero_mesh.prepare(sphere_stl(), "stl", {"fit": "contain"}, frame)
    assert hero.is_watertight
    assert hero.extents == pytest.approx([40, 40, 40], abs=1e-6)
    assert hero.bounds[0][2] == pytest.approx(0.0, abs=1e-9) and abs(hero.bounds[0][0] + hero.bounds[1][0]) < 1e-9
    slab = trimesh.creation.box(extents=[20, 10, 5]).export(file_type="stl")
    hero = hero_mesh.prepare(slab, "stl", {"fit": "contain"}, frame)
    assert hero.extents == pytest.approx([40, 20, 10])


def test_hero_longest_scales_the_longest_side_and_never_clamps():
    frame = top_frame(10, bounds=(40, 40, 60))
    hero = hero_mesh.prepare(sphere_stl(), "stl", {"fit": "longest", "longest_mm": 25}, frame)
    assert hero.extents.max() == pytest.approx(25, abs=1e-6)
    assert hero.bounds[0][2] == pytest.approx(0.0, abs=1e-9)
    with pytest.raises(ParamOutOfRange) as exc:
        hero_mesh.prepare(sphere_stl(), "stl", {"fit": "longest", "longest_mm": 50}, frame, key="features[0].longest_mm")
    assert exc.value.keys == ["features[0].longest_mm"] and exc.value.detail["max_longest_mm"] == pytest.approx(40)


def test_hero_fuses_with_a_body_or_stands_alone():
    plinth = box_on_bed(50, 50, 10)
    frame = top_frame(10, bounds=(40, 40, 60))
    fused = hero_mesh.apply(plinth, frame, {"fit": "contain"}, sphere_stl(), fmt="stl", anchor_label="top")
    assert fused.is_watertight and fused.volume > plinth.volume
    assert fused.bounds[1][2] == pytest.approx(10 + 40 - hero_mesh.EMBED_MM, abs=1e-3)
    alone = hero_mesh.apply(trimesh.Trimesh(), top_frame(0, bounds=(250, 250, 250)), {"fit": "longest", "longest_mm": 80}, sphere_stl(), fmt="stl")
    assert alone.is_watertight and alone.extents == pytest.approx([80, 80, 80], abs=1e-6)
    assert alone.bounds[0][2] == pytest.approx(0.0, abs=1e-9)


def test_hero_orientation_and_yaw():
    frame = top_frame(0, bounds=(250, 250, 250))
    tilted = trimesh.creation.box(extents=[20, 10, 5])
    tilted.apply_transform(trimesh.transformations.rotation_matrix(np.radians(30), [1, 0, 0]))
    flat = hero_mesh.prepare(tilted.export(file_type="stl"), "stl", {"fit": "longest", "longest_mm": 20, "orientation": "lay_flat"}, frame)
    assert sorted(flat.extents) == pytest.approx([5, 10, 20], abs=1e-3)
    yawed = hero_mesh.prepare(trimesh.creation.box(extents=[20, 10, 5]).export(file_type="stl"), "stl", {"fit": "longest", "longest_mm": 20, "yaw_deg": 90}, frame)
    assert yawed.extents == pytest.approx([10, 20, 5], abs=1e-6)
    # glTF is Y-up: the model's up (Y) becomes Z
    glb = trimesh.creation.box(extents=[20, 5, 10]).export(file_type="glb")
    assert hero_mesh.prepare(glb, "glb", {"fit": "longest", "longest_mm": 20}, frame).extents == pytest.approx([20, 10, 5], abs=1e-6)


@pytest.mark.parametrize("data, fmt", [(soup_stl(), "stl"), (open_sphere_stl(), "stl"), (b"garbage", "stl"), (gradient_png(), "png"), (sphere_stl(), None)])
def test_hero_rejects_unrepairable_or_unreadable_input(data, fmt):
    with pytest.raises(ContentUnusable):
        hero_mesh.prepare(data, fmt, {"fit": "contain"}, top_frame(0, bounds=(40, 40, 40)))


def test_hero_decimates_dense_models():
    dense = trimesh.creation.icosphere(subdivisions=6, radius=10)
    assert len(dense.faces) > 50_000
    reduced = hero_mesh.decimate(dense, 20_000)
    assert len(reduced.faces) <= 20_000 and reduced.is_watertight and reduced.volume == pytest.approx(dense.volume, rel=0.02)
    hero = hero_mesh.prepare(dense.export(file_type="stl"), "stl", {"fit": "contain"}, top_frame(0, bounds=(30, 30, 30)), max_faces=20_000)
    # contain puts the longest side exactly on the bound; a decimated sphere is a hair out of round elsewhere
    assert len(hero.faces) <= 20_000 and max(hero.extents) == pytest.approx(30, abs=1e-6)
    assert hero.extents == pytest.approx([30, 30, 30], abs=0.05)


# --------------------------------------------------------------------------- fetchers


def test_format_detection_precedence():
    assert resolve_format({"format": "jpeg", "url": "http://x/a.png"}) == "jpg"
    assert resolve_format({"url": "http://x/a.PNG?token=1"}) == "png"
    assert format_from_url("http://x/noext") is None
    assert resolve_format({"url": "http://x/blob"}, gradient_png()) == "png"
    assert sniff_format(sphere_stl()) == "stl"
    assert sniff_format(trimesh.creation.box().export(file_type="glb")) == "glb"
    assert sniff_format(b"solid ascii\nfacet normal 0 0 1\n") == "stl"
    assert sniff_format(b"hello") is None


def test_local_file_fetcher_round_trip(content_dir, monkeypatch):
    png = (content_dir / "photo.png").read_bytes()
    by_uri = LocalFileFetcher()
    assert by_uri.fetch({"url": (content_dir / "photo.png").as_uri(), "format": "png"}) == png
    by_name = LocalFileFetcher(content_dir)
    assert by_name.fetch(content_source("photo.png", "png")) == png
    assert by_name.fetch({"url": "photo.png"}) == png
    with pytest.raises(ContentUnusable):
        by_name.fetch(content_source("missing.png", "png"))
    with pytest.raises(InvalidSpec):
        by_uri.fetch(content_source("photo.png", "png"))  # no base dir, not a file:// URL
    (content_dir / "page.html").write_text("<!doctype html><html><body>login</body></html>")
    with pytest.raises(ContentUnusable):
        by_name.fetch({"url": "page.html", "format": "png"})
    monkeypatch.setattr("aakar_geometry.features.fetch.MAX_IMAGE_BYTES", 16)
    with pytest.raises(ContentUnusable) as exc:
        by_name.fetch(content_source("photo.png", "png"))
    assert "too large" in exc.value.message


def test_http_fetcher_caps_and_status_codes(monkeypatch):
    png = gradient_png()

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        if path.endswith("photo.png"):
            return httpx.Response(200, content=png, headers={"content-type": "image/png"})
        if path.endswith("gone.png"):
            return httpx.Response(404)
        if path.endswith("down.stl"):
            return httpx.Response(503)
        if path.endswith("login.png"):
            return httpx.Response(200, content=b"<html>sign in</html>", headers={"content-type": "text/html"})
        if path.endswith("big.png"):
            return httpx.Response(200, content=b"\x00" * 64, headers={"content-type": "image/png"})
        raise httpx.ConnectError("refused")

    fetcher = HttpFetcher(client=httpx.Client(transport=httpx.MockTransport(handler)))
    assert fetcher.fetch(content_source("photo.png", "png")) == png
    assert fetcher.fetch(content_source("photo.png")) == png
    with pytest.raises(ContentUnusable):
        fetcher.fetch(content_source("gone.png", "png"))
    with pytest.raises(GeometryError):
        fetcher.fetch(content_source("down.stl", "stl"))
    with pytest.raises(ContentUnusable):
        fetcher.fetch(content_source("login.png", "png"))
    with pytest.raises(GeometryError):
        fetcher.fetch(content_source("dead.stl", "stl"))
    with pytest.raises(InvalidSpec):
        fetcher.fetch({"url": "file:///etc/passwd", "format": "png"})
    monkeypatch.setattr("aakar_geometry.features.fetch.MAX_IMAGE_BYTES", 32)
    with pytest.raises(ContentUnusable):
        fetcher.fetch(content_source("big.png", "png"))


# --------------------------------------------------------------------------- validation


class ValidationTemplate(Template):
    id = "validation_plate"
    version = 1
    family = "keychain"  # max_text_chars 16 in families.json
    name = "Validation plate"
    anchors = (
        Anchor("face", "Face", "planar", size_mm=(36, 26), bleed_mm=2, max_relief_mm=1.5),
        Anchor("back", "Back", "planar", size_mm=(36, 26), accepts=("emboss_text",)),
        Anchor("top", "Top", "planar", kind="volume", bounds_mm=(30, 30, 40)),
    )
    features_supported = ("relief_image", "emboss_text", "hero_mesh")


def relief(anchor="face", **extra):
    return {"type": "relief_image", "source": content_source("photo.png", "png"), "anchor": anchor, **extra}


def hero(anchor="top", **extra):
    return {"type": "hero_mesh", "source": content_source("sphere.stl", "stl"), "anchor": anchor, **extra}


def text(anchor="face", value="Asha", **extra):
    return {"type": "emboss_text", "text": value, "anchor": anchor, **extra}


@pytest.mark.parametrize(
    "features, error, needle",
    [
        ([relief(anchor="nowhere")], InvalidSpec, "no anchor"),
        ([{"type": "motif", "motif_id": "warli_dancers_01", "anchor": "face"}], UnsupportedFeature, "can't carry motif (Buti)"),
        ([relief(anchor="back")], UnsupportedFeature, "does not take"),
        ([hero(anchor="face")], UnsupportedFeature, "surface"),
        ([relief(anchor="top")], UnsupportedFeature, "flat surface"),
        ([relief(relief_mm=2.0)], ParamOutOfRange, "at most 1.5 mm"),
        ([relief(), relief()], InvalidSpec, "Only one photo"),
        ([relief(), hero(anchor="face")], UnsupportedFeature, "surface"),
        ([text(value="a name that is far too long")], ParamOutOfRange, "at most 16 characters"),
        ([hero(fit="longest")], InvalidSpec, "longest_mm"),
        ([relief(cutout="silhouette")], UnsupportedFeature, "silhouette"),
        ([{"type": "hologram", "anchor": "face"}], InvalidSpec, "Unknown feature type"),
    ],
)
def test_feature_validation_errors(features, error, needle):
    with pytest.raises(error) as exc:
        check(ValidationTemplate, {"features": features})
    assert needle in exc.value.message
    assert exc.value.code in ("invalid_spec", "unsupported_feature", "param_out_of_range")


def test_feature_validation_accepts_sane_specs_and_fills_defaults():
    out = check_features(ValidationTemplate, [relief(), text(anchor="back"), text(value="नमस्ते दुनिया"), hero(fit="longest", longest_mm=20)])
    assert out[0]["mode"] == "emboss" and out[0]["relief_mm"] == 0.6 and out[0]["fit"] == "contain" and out[0]["cutout"] == "none"
    assert out[1]["depth_mm"] == 1.2 and out[1]["mode"] == "emboss" and out[1]["projection"] == "planar"
    assert out[3]["orientation"] == "as_uploaded" and out[3]["yaw_deg"] == 0
    assert out[0]["source"]["origin"] == "upload"
    assert check(ValidationTemplate, {}) == []
    assert normalise_features(None) == []
    assert text_length("नमस्ते") == 4 and text_length("Asha") == 4
    depth_error = pytest.raises(ParamOutOfRange, check_features, ValidationTemplate, [text(depth_mm=1.6)])
    assert depth_error.value.keys == ["features[0].depth_mm"]


def test_relief_over_max_relief_reports_the_feature_key():
    with pytest.raises(ParamOutOfRange) as exc:
        check(ValidationTemplate, {"features": [text(anchor="back"), relief(relief_mm=3.0)]})
    assert exc.value.keys == ["features[1].relief_mm"]


# --------------------------------------------------------------------------- apply_features


def test_apply_features_relief_returns_mesh_and_hardware(content_dir):
    Plate, Plinth, Raw = make_test_templates()
    params = Plate.validate({})
    body = Plate.build_body(params)
    mesh, hardware = apply_features(Plate, body, params, [relief()], LocalFileFetcher(content_dir))
    assert mesh.is_watertight and mesh.volume > body.volume
    assert hardware == [{"sku": "split_ring_25", "qty": 1}]
    # Template.build with features goes through the same stage; without features it is the body itself
    assert Plate.build(params, [relief()], LocalFileFetcher(content_dir)).volume == pytest.approx(mesh.volume)
    assert Plate.build(params).volume == pytest.approx(body.volume)
    assert apply_features(Plate, body, params, [], None)[0] is body


def test_apply_features_rejects_wrong_content_kind_and_text_features(content_dir):
    Plate, Plinth, Raw = make_test_templates()
    params = Plate.validate({})
    body = Plate.build_body(params)
    fetcher = LocalFileFetcher(content_dir)
    with pytest.raises(ContentUnusable):  # a model file where a photo is needed
        apply_features(Plate, body, params, [relief() | {"source": content_source("sphere.stl", "stl")}], fetcher)
    with pytest.raises(ContentUnusable):  # a text file is neither
        apply_features(Plate, body, params, [relief() | {"source": content_source("notes.txt")}], fetcher)
    with pytest.raises(ContentUnusable):  # a flat photo has nothing to emboss
        apply_features(Plate, body, params, [relief() | {"source": content_source("flat.png", "png")}], fetcher)

    class Plaque(Plate):
        id = "test_plaque"
        features_supported = ("relief_image", "emboss_text")
        anchors = (Anchor("face", "Face", "planar", size_mm=(36, 26), bleed_mm=2, max_relief_mm=1.5),)

    with pytest.raises(UnsupportedFeature) as exc:
        apply_features(Plaque, body, params, [text()], fetcher)
    assert "text release" in exc.value.message


def test_lithophane_mode_hands_the_heightmap_to_the_template(content_dir):
    Plate, _, _ = make_test_templates()
    params = Plate.validate({})
    body = Plate.build_body(params)
    with pytest.raises(UnsupportedFeature):  # the default hook: this template makes no night light
        apply_features(Plate, body, params, [relief(mode="lithophane", relief_mm=2.0)], LocalFileFetcher(content_dir))

    received = {}

    class NightLight(Plate):
        id = "test_lithophane"

        @classmethod
        def lithophane(cls, body, params, frame, anchor, feature, relief):
            received.update(anchor=anchor.id, shape=relief.shape, max=float(relief.heights.max()))
            return heightfield_solid(relief.heights, relief.cell_mm, 0.8)

    mesh, _ = apply_features(NightLight, body, params, [relief(mode="lithophane", relief_mm=1.5)], LocalFileFetcher(content_dir))
    assert received["anchor"] == "face" and received["max"] == pytest.approx(1.5)
    assert mesh.is_watertight and mesh.extents[2] == pytest.approx(0.8 + 1.5)


def test_hero_on_plinth_and_raw_print_through_apply_features(content_dir):
    Plate, Plinth, Raw = make_test_templates()
    fetcher = LocalFileFetcher(content_dir)
    plinth = Plinth.build_body({})
    fused, hardware = apply_features(Plinth, plinth, {}, [hero()], fetcher)
    assert fused.is_watertight and fused.volume > plinth.volume and hardware == []
    assert fused.bounds[1][2] == pytest.approx(10 + 40 - hero_mesh.EMBED_MM, abs=1e-3)
    raw_params = Raw.validate({"longest_mm": 60})
    alone, _ = apply_features(Raw, Raw.build_body(raw_params), raw_params, [hero(anchor="body", fit="longest", longest_mm=60)], fetcher)
    assert alone.is_watertight and alone.extents == pytest.approx([60, 60, 60], abs=1e-6) and alone.bounds[0][2] == pytest.approx(0)
    with pytest.raises(ContentUnusable):
        apply_features(Plinth, plinth, {}, [hero() | {"source": content_source("open.stl", "stl")}], fetcher)
    with pytest.raises(GeometryError):  # nothing at all is not a design
        apply_features(Raw, Raw.build_body(raw_params), raw_params, [], fetcher)


def test_descriptor_publishes_anchor_fields_and_hardware():
    from aakar_geometry.contracts import validate

    Plate, Plinth, Raw = make_test_templates()
    desc = Plate.descriptor()
    validate("template-descriptor", desc)
    face = desc["anchors"][0]
    assert face == {
        "id": "face", "label": "Face", "kind": "surface", "projection": "planar",
        "size_mm": [36, 26], "bleed_mm": 2.0, "accepts": ["relief_image"], "max_relief_mm": 1.5,
    }
    assert desc["hardware"] == [{"sku": "split_ring_25", "qty": 1}] and "min_feature_mm" not in desc
    top = Plinth.descriptor()["anchors"][0]
    assert top["kind"] == "volume" and top["bounds_mm"] == [40, 40, 60] and "size_mm" not in top
    with pytest.raises(ValueError):
        Anchor("x", "X", "planar", kind="blob")
    assert HardwareRef("cord_200", 2).descriptor() == {"sku": "cord_200", "qty": 2}
