"""Buti (``motif``): the motif library in packages/design-tokens/motifs, SVG path parsing, fit and scale rules,
raised or cut motifs, and a missing or broken library."""

from __future__ import annotations

import json
import math
import re
import xml.etree.ElementTree as ET
from pathlib import Path

import pytest
import trimesh
from aakar_geometry.errors import InvalidSpec, ParamOutOfRange
from aakar_geometry.features import AnchorFrame, motif, outlines
from aakar_geometry.features.motif import MotifLibraryError, check_scale, entry, fit_motif, load_library, parse_svg

from conftest import REPO

LIBRARY = REPO / "packages/design-tokens/motifs"
INDEX = json.loads((LIBRARY / "index.json").read_text(encoding="utf-8"))
IDS = [m["id"] for m in INDEX["motifs"]]
CODENAMES = {"Avatar", "Chhaap", "Naam", "Buti", "Chhavi", "Roop", "Swaroop", "Duniya", "Mahaul", "Saathi", "Chumbak", "Pehchaan", "Jhoomar"}


# ----------------------------------------------------------------------------- the library itself


def test_the_library_loads_from_the_repo_and_matches_its_files():
    library = load_library()
    assert Path(library.source) == LIBRARY.resolve()
    assert library.ids() == IDS
    assert {"paisley", "lotus", "star_rangoli", "jaali_lattice", "warli_dancer"} <= set(IDS)
    assert len(IDS) == len(set(IDS))
    assert sorted(p.name for p in LIBRARY.glob("*.svg")) == sorted(m["file"] for m in INDEX["motifs"])  # no orphans
    assert library.reference_mm == 30 and library.min_stroke_mm == 0.8


@pytest.mark.parametrize("motif_id", IDS)
def test_every_motif_is_one_closed_original_path(motif_id):
    row = next(m for m in INDEX["motifs"] if m["id"] == motif_id)
    assert re.fullmatch(r"[a-z][a-z0-9_]*", motif_id) and row["file"] == f"{motif_id}.svg"
    # a plain, codename-free label and some tags for the storefront's picker (snake_case: a shared tag names a pack)
    assert re.fullmatch(r"[A-Z][a-z]+( [a-z]+)*", row["label"]) and not set(row["label"].split()) & CODENAMES
    assert row["tags"] and all(re.fullmatch(r"[a-z][a-z0-9_]*", t) for t in row["tags"])
    assert 0.2 <= row["min_scale"] <= 1
    root = ET.fromstring((LIBRARY / row["file"]).read_bytes())
    tags = [el.tag.split("}")[-1] for el in root.iter()]
    assert tags.count("path") == 1 and not set(tags) & {"image", "use", "text", "style", "script", "g", "rect", "circle"}
    assert not any(el.get("transform") or el.get("{http://www.w3.org/1999/xlink}href") or el.get("href") for el in root.iter())
    path = next(el for el in root.iter() if el.tag.endswith("path"))
    assert path.get("fill-rule") in ("nonzero", "evenodd")
    # every subpath is closed: each one that starts with M ends with Z before the next starts
    d = path.get("d")
    subpaths = [s for s in re.split(r"(?=[Mm])", d) if s.strip()]
    assert subpaths and all(s.rstrip().endswith(("Z", "z")) for s in subpaths)
    found = entry(motif_id)
    assert found.region.is_valid and not found.region.is_empty and found.label == row["label"]
    assert found.min_scale == row["min_scale"] and found.fill_rule == path.get("fill-rule")


@pytest.mark.parametrize("motif_id", IDS)
def test_every_motif_prints_on_the_30_mm_reference_down_to_its_min_scale(motif_id):
    """Strokes and openings stay at least 0.8 mm (the stroke rule) when the motif fills the 30 mm reference
    square at scale 1 and at its min_scale."""
    found = entry(motif_id)
    for scale in (1.0, found.min_scale):
        fitted = fit_motif(found, box=(0.0, 0.0, 30.0, 30.0), scale=scale)
        assert fitted.ok and fitted.stroke.ok, (motif_id, scale, fitted.stroke, fitted.openings)
        assert max(fitted.size_mm) == pytest.approx(30.0 * scale, rel=1e-6)
        assert (fitted.openings is None) == outlines.openings(fitted.region).is_empty


COMIC_PACK = ["action_burst", "speech_bubble", "thought_bubble", "lightning_bolt", "domino_mask", "hero_cape"]


def test_the_comic_pack_is_a_tag_on_original_comic_motifs():
    """Katha's motif pack (``comic_bursts``) is a tag the comic motifs share, so the storefront and the validator
    resolve it without a list of its own; every pack entry of every experience names a motif or a tag."""
    library = load_library().motifs
    assert [m for m in IDS if "comic_bursts" in library[m].tags] == COMIC_PACK  # in library order, 4–6 of them
    assert all("comic" in library[m].tags for m in COMIC_PACK)
    known = set(IDS) | {t for m in library.values() for t in m.tags}
    experiences = json.loads((REPO / "packages/design-tokens/experiences.json").read_text(encoding="utf-8"))["experiences"]
    for experience in experiences:
        assert set(experience.get("motif_pack") or []) <= known, experience["id"]
    assert next(x for x in experiences if x["id"] == "comics")["motif_pack"] == ["comic_bursts"]
    # generic shapes: the mask has its two eye openings, the thought bubble trails two bubbles off its cloud
    assert len(outlines.polygons(outlines.openings(library["domino_mask"].region))) == 2
    assert len(outlines.polygons(library["thought_bubble"].region)) == 3
    assert len(outlines.polygons(library["lightning_bolt"].region)) == 1 and library["lightning_bolt"].fill_rule == "nonzero"


def test_the_library_holds_several_kinds_of_outline():
    """The fill rules and structures the parser must get right are all exercised by real motifs."""
    library = load_library().motifs
    assert {m.fill_rule for m in library.values()} == {"nonzero", "evenodd"}
    assert len(outlines.polygons(outlines.openings(library["jaali_lattice"].region))) == 13  # thirteen openings
    star = library["star_rangoli"].region  # a star with a round opening and a dot in it (arcs, evenodd)
    assert len(outlines.polygons(star)) == 2 and len(outlines.polygons(star)[0].interiors) == 1
    paisley = library["paisley"].region  # a border ring and an eye inside it
    assert len(outlines.polygons(paisley)) == 2
    warli = library["warli_dancer"].region
    assert len(outlines.polygons(warli)) == 1 and warli.bounds[3] - warli.bounds[1] > warli.bounds[2] - warli.bounds[0]  # upright


# ----------------------------------------------------------------------------- parsing


def _svg(d: str, rule: str = "nonzero", extra: str = "", view_box: str = "0 0 100 100") -> bytes:
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view_box}">{extra}'
        f'<path fill-rule="{rule}" d="{d}"/></svg>'
    ).encode()


def test_parse_svg_handles_arcs_curves_relative_commands_and_flips_y():
    circle = "M60 50A10 10 0 1 0 40 50A10 10 0 1 0 60 50Z"
    region, rule, vb = parse_svg(_svg(circle))
    assert rule == "nonzero" and vb == (0, 0, 100, 100)
    assert region.area == pytest.approx(math.pi * 100, rel=5e-3)  # flattened within 1/5000 of the viewBox
    assert region.bounds == pytest.approx((-10, -10, 10, 10), abs=1e-3)  # centred on its ink box
    # the same square three ways: absolute, relative with H/V, and cubic curves that happen to be straight
    for d in ("M10 10L30 10L30 30L10 30Z", "m10 10h20v20h-20z", "M10 10C15 10 25 10 30 10L30 30C25 30 15 30 10 30Z"):
        square, _, _ = parse_svg(_svg(d))
        assert square.area == pytest.approx(400, rel=1e-6)
    # SVG's y runs down; a triangle pointing down in the file points down in the anchor frame (+y up) too
    tri, _, _ = parse_svg(_svg("M0 0L20 0L10 30Z"))
    top = max(tri.exterior.coords, key=lambda p: p[1])
    bottom = min(tri.exterior.coords, key=lambda p: p[1])
    assert bottom[0] == pytest.approx(0, abs=1e-6) and top[1] - bottom[1] == pytest.approx(30)
    # style="fill-rule: evenodd" counts as well as the attribute
    nested = "M0 0H40V40H0ZM10 10H30V30H10Z"
    assert parse_svg(_svg(nested, "nonzero"))[0].area == pytest.approx(1600)  # same winding: filled
    evenodd = _svg(nested).replace(b'fill-rule="nonzero"', b'style="fill:#000;fill-rule:evenodd"')
    assert parse_svg(evenodd)[0].area == pytest.approx(1200)


@pytest.mark.parametrize(
    "data, needle",
    [
        (b"<svg", "not an SVG"),
        (b'<svg viewBox="0 0 10 10"><path d="M0 0H5V5Z"/></svg>', "SVG namespace"),
        (_svg("M0 0H5V5Z", view_box=""), "viewBox"),
        (_svg("M0 0H5V5Z", extra='<path d="M6 6H9V9Z"/>'), "exactly one <path>"),
        (_svg("M0 0H5V5Z", extra='<circle r="3"/>'), "exactly one <path>"),
        (_svg("M0 0H5V5Z").replace(b"<path", b'<path transform="scale(2)"'), "transforms"),
        (_svg("M0 0H5V5Z", rule="winding"), "fill-rule"),
        (_svg("M0 0L10 0"), "no area"),
        (_svg("M0 0L10 0Q"), "unreadable path"),
    ],
)
def test_parse_svg_refuses_files_that_break_the_library_format(data, needle):
    with pytest.raises(ValueError) as exc:
        parse_svg(data, "bad.svg")
    assert needle in str(exc.value)


# ----------------------------------------------------------------------------- lookup, scale and fit


def test_an_unknown_motif_is_a_customer_safe_invalid_spec():
    with pytest.raises(InvalidSpec) as exc:
        entry("warli_dancers_01", "features[1].motif_id")
    assert exc.value.code == "invalid_spec"
    assert exc.value.message == "We don't have a motif called “warli_dancers_01”; choose one from the motif library"
    assert exc.value.detail["key"] == "features[1].motif_id" and "warli_dancer" in exc.value.detail["known"]


def test_scale_runs_from_min_scale_to_one_and_is_never_clamped():
    lotus = entry("lotus")
    check_scale(lotus, 1.0, anchor_label="Face", key="k")
    check_scale(lotus, lotus.min_scale, anchor_label="Face", key="k")
    with pytest.raises(ParamOutOfRange) as exc:
        check_scale(lotus, 1.5, anchor_label="Face", key="features[0].scale")
    assert exc.value.keys == ["features[0].scale"] and "run past the edge of the face; scale 1 fills it" in exc.value.message
    with pytest.raises(ParamOutOfRange) as exc:
        check_scale(lotus, 0.2, anchor_label="Face", key="features[0].scale")
    assert "can't be printed smaller than scale 0.25" in exc.value.message


def test_fit_is_contain_centred_and_scaled():
    warli = entry("warli_dancer")  # taller than wide
    fitted = fit_motif(warli, box=(10.0, -4.0, 60.0, 20.0))
    x0, y0, x1, y1 = fitted.region.bounds
    assert y1 - y0 == pytest.approx(20.0) and x1 - x0 == pytest.approx(20.0 * warli.aspect)  # height-bound
    assert ((x0 + x1) / 2, (y0 + y1) / 2) == pytest.approx((10.0, -4.0))
    half = fit_motif(warli, box=(10.0, -4.0, 60.0, 20.0), scale=0.5)
    assert half.size_mm == pytest.approx((fitted.size_mm[0] / 2, fitted.size_mm[1] / 2))
    assert half.region.area == pytest.approx(fitted.region.area / 4, rel=1e-6)


def test_a_motif_too_small_to_print_is_refused():
    star = entry("star_rangoli")
    assert fit_motif(star, box=(0, 0, 40, 9.0)).ok
    # at 6 mm the star's points still print, but the ring opening around its centre would close up
    small = fit_motif(star, box=(0, 0, 40, 6.0), check_stroke=False)
    assert small.stroke.ok and not small.openings.ok
    with pytest.raises(ParamOutOfRange) as exc:
        fit_motif(star, box=(0, 0, 40, 6.0), key="features[3]")
    assert exc.value.message == "This motif would be too fine to print at this size; make it larger or put it on a larger piece"
    assert exc.value.keys == ["features[3].scale"] and exc.value.detail["motif_id"] == "star_rangoli"
    with pytest.raises(ParamOutOfRange):  # and at 4 mm its strokes are too thin as well
        fit_motif(entry("warli_dancer"), box=(0, 0, 40, 4.0))


# ----------------------------------------------------------------------------- raised or cut


@pytest.mark.parametrize("mode", ["emboss", "deboss"])
def test_a_motif_is_raised_or_cut_into_a_body(mode):
    body = trimesh.creation.box(extents=[40, 30, 4])
    body.apply_translation([0, 0, 2])
    frame = AnchorFrame([0, 0, 4], [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=(36, 26))
    feature = {"type": "motif", "motif_id": "paisley", "anchor": "face", "mode": mode, "depth_mm": 0.8, "scale": 1}
    fitted = motif.prepare(feature, box=(0, 0, 34, 24), anchor=None, min_feature_mm=0.8, key="features[0]")
    result = motif.apply(body, frame, feature, fitted)
    assert result.is_watertight and result.is_winding_consistent
    delta = result.volume - body.volume
    assert abs(delta) == pytest.approx(fitted.region.area * 0.8, rel=0.01)
    assert (delta > 0) == (mode == "emboss")


# ----------------------------------------------------------------------------- a missing or broken library


def test_a_missing_library_is_a_deployment_fault_not_the_customers(monkeypatch, tmp_path):
    monkeypatch.setenv("AAKAR_MOTIFS_DIR", str(tmp_path / "nowhere"))
    with pytest.raises(MotifLibraryError) as exc:
        entry("lotus")
    assert exc.value.code == "build_error" and exc.value.message == "Motifs are not available right now; please try again later"
    assert exc.value.detail["env"] == "AAKAR_MOTIFS_DIR"


def test_a_broken_library_fails_loudly(monkeypatch, tmp_path):
    folder = tmp_path / "motifs"
    folder.mkdir()
    (folder / "index.json").write_text(json.dumps({"motifs": [{"id": "ghost", "label": "Ghost", "file": "ghost.svg", "tags": ["x"], "min_scale": 0.5}]}))
    monkeypatch.setenv("AAKAR_MOTIFS_DIR", str(folder))
    with pytest.raises(MotifLibraryError) as exc:
        load_library()
    assert exc.value.detail["reason"] == "a motif file is missing"
    (folder / "ghost.svg").write_bytes(_svg("M0 0H50V50H0Z"))
    assert load_library().ids() == ["ghost"]  # failures are not cached: the repaired library loads
    other = tmp_path / "other"
    other.mkdir()
    (other / "index.json").write_text("{not json")
    monkeypatch.setenv("AAKAR_MOTIFS_DIR", str(other))
    with pytest.raises(MotifLibraryError):
        load_library()


def test_a_library_can_be_mounted_elsewhere(monkeypatch, tmp_path):
    folder = tmp_path / "mounted"
    folder.mkdir()
    for name in ("index.json", "lotus.svg"):
        (folder / name).write_bytes((LIBRARY / name).read_bytes())
    index = json.loads((folder / "index.json").read_text())
    index["motifs"] = [m for m in index["motifs"] if m["id"] == "lotus"]
    (folder / "index.json").write_text(json.dumps(index))
    monkeypatch.setenv("AAKAR_MOTIFS_DIR", str(folder))
    assert load_library().ids() == ["lotus"] and motif.motifs_dir() == folder
    with pytest.raises(InvalidSpec):
        entry("paisley")
