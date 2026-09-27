"""Naam (``emboss_text``): fonts, script detection, HarfBuzz shaping per launch script (golden values pinned
to the bundled font versions in aakar_geometry/fonts/README.md), outlines and fill rules, fit, the stroke
rule, and raised or cut lettering that reads left to right from outside."""

from __future__ import annotations

import numpy as np
import pytest
import trimesh
from fontTools.pens.recordingPen import RecordingPen
from shapely.geometry import Polygon
from shapely.geometry import box as rect

from aakar_geometry.errors import GeometryError, InvalidSpec, ParamOutOfRange, UnsupportedFeature
from aakar_geometry.features import AnchorFrame, booleans, content_note, emboss_text, outlines
from aakar_geometry.features.emboss_text import MIN_TEXT_HEIGHT_MM, SCRIPTS, TOO_THIN, fit_text, resolve_script, shape_text

# ----------------------------------------------------------------------------- golden shaping per script

# (script, word, glyph names in visual order, clusters, ink width / ink height). The glyph names prove the
# shaper formed the conjuncts, half forms and vowel signs; they change only when a font is updated, and then
# the new shaping should be reviewed with a native reader before these values are.
GOLDEN = [
    ("latin", "Asha", ["A", "s", "h", "a"], [0, 1, 2, 3], 3.081),
    # न म स्ते: स्त is a conjunct (half sa + ta), the e-matra sits over it
    ("devanagari", "नमस्ते", ["na-deva", "ma-deva", "s-deva", "ta-deva", "eMatra-deva"], [0, 1, 2, 4], 2.529),
    # వి రా జ్: vowel signs fused into the consonants, the final halant fused into ja
    ("telugu", "విరాజ్", ["vivoweltelu", "raavoweltelu", "jahalanttelu"], [0, 2, 4], 3.005),
    # வ ண க் க ம்: the pulli (virama) forms of ka and ma
    ("tamil", "வணக்கம்", ["vatamil", "nnatamil", "kaprehalftamil", "katamil", "maprehalftamil"], [0, 1, 2, 4, 5], 6.677),
    # ನ ಮ ಸ್ಕಾ ರ: sa without its crest, the aa sign, ka as a subscript under sa
    ("kannada", "ನಮಸ್ಕಾರ", ["naknda", "maknda", "sanocrestknda", "aavowelsignknda", "kasubscriptknda", "raknda"], [0, 1, 2, 6], 3.496),
    # ন ম স্কা র: half sa, the ka part and its headline piece, the aa sign
    (
        "bengali",
        "নমস্কার",
        ["na-beng", "ma-beng", "s-beng.half", "baphala-beng.alt4", "ka-beng.part", "headline-beng.250", "aaMatra-beng", "ra-beng"],
        [0, 1, 2, 4, 6],
        3.897,
    ),
    # ન મ સ્તે: the prehalf form of sa, the e sign on ta
    ("gujarati", "નમસ્તે", ["nagujr", "magujr", "saprehalfgujr", "tagujr", "evowelsigngujr"], [0, 1, 2, 4], 2.554),
]


@pytest.mark.parametrize("script, word, names, clusters, aspect", GOLDEN, ids=[g[0] for g in GOLDEN])
def test_each_launch_script_shapes_to_its_golden_glyphs(script, word, names, clusters, aspect):
    shaped = shape_text(word)
    assert shaped.script == script  # detected from the letters
    assert shaped.glyph_names == names
    assert len(shaped.glyphs) == len(names) and shaped.clusters == clusters
    assert all(g.font == SCRIPTS[script].file for g in shaped.glyphs)
    assert shaped.region.is_valid and not shaped.region.is_empty and shaped.region.area > 0
    assert shaped.aspect == pytest.approx(aspect, rel=0.01)
    x0, y0, x1, y1 = shaped.ink_bounds
    upem = shaped.upem
    # plausible extents: the ink sits around the baseline, no taller than 1.5 em, within the advance
    assert -0.45 * upem < y0 < 0.1 * upem and 0.6 * upem < y1 < 1.0 * upem
    assert x0 > -0.05 * upem and x1 < shaped.advance + 0.15 * upem
    # the same word, fitted 10 mm tall: exactly 10 mm of ink, as wide as its aspect says, centred on the box
    fitted = fit_text(shaped, box=(3.0, -2.0, 400.0, 10.0))
    bx0, by0, bx1, by1 = fitted.region.bounds
    assert by1 - by0 == pytest.approx(10.0, abs=1e-6) and bx1 - bx0 == pytest.approx(10.0 * aspect, rel=0.01)
    assert ((bx0 + bx1) / 2, (by0 + by1) / 2) == pytest.approx((3.0, -2.0), abs=1e-6)
    assert fitted.stroke.ok and fitted.stroke.lost_pieces == 0


def test_the_bundled_fonts_load_and_cover_their_scripts():
    assert emboss_text.fonts_available() == {s: True for s in SCRIPTS}
    for script in SCRIPTS.values():
        font = emboss_text.load_font(script.file)
        assert font.upem == 1000
        covered = sum(1 for lo, hi in script.ranges for cp in range(lo, hi + 1) if cp in font.unicodes)
        assert covered > 60, (script.id, covered)
        assert {0x200C, 0x200D} <= font.unicodes  # ZWNJ / ZWJ
    assert emboss_text.font_bytes("OFL.txt").startswith(b"Copyright 2022 The Noto Project Authors")


def test_joiners_steer_the_shaping():
    """ZWJ asks for the half form, ZWNJ for the explicit virama, nothing for the conjunct ligature."""
    assert shape_text("क्ष").glyph_names == ["k_ssa-deva"]
    assert shape_text("क्‍ष").glyph_names[0] == "k-deva"
    assert shape_text("क्‌ष").glyph_names[:2] == ["ka-deva", "halant-deva"]
    # the i-matra is drawn before the consonant it follows in the text, in the width variant for va
    assert shape_text("रवि").glyph_names == ["ra-deva", "iMatra-deva.04", "va-deva"]


def test_digits_and_punctuation_a_script_font_lacks_come_from_noto_sans():
    shaped = shape_text("નમસ્તે 2024")
    assert shaped.script == "gujarati"
    assert [g.font for g in shaped.glyphs[-4:]] == ["NotoSans-Bold.ttf"] * 4 and shaped.glyph_names[-4:] == ["two", "zero", "two", "four"]
    assert shaped.glyphs[-4].x > shaped.glyphs[4].x  # the digits follow the word on the same line
    # Bengali has its own digits; the ampersand in a Devanagari name comes from Noto Sans too
    assert {g.font for g in shape_text("২০২৪").glyphs} == {"NotoSansBengali-Bold.ttf"}
    amp = shape_text("आशा & रवि")
    assert [g.font for g in amp.glyphs if g.name == "ampersand"] == ["NotoSans-Bold.ttf"]


# ----------------------------------------------------------------------------- script detection and refusals


@pytest.mark.parametrize(
    "text, script",
    [("Asha", "latin"), ("2024", "latin"), ("José", "latin"), ("नमस्ते दुनिया", "devanagari"), ("నమస్తే", "telugu"),
     ("வணக்கம்", "tamil"), ("ನಮಸ್ಕಾರ", "kannada"), ("নমস্কার", "bengali"), ("નમસ્તે", "gujarati"), ("राम।", "devanagari")],
)
def test_script_detection(text, script):
    assert emboss_text.detect_script(text) == script
    assert resolve_script(text, script).id == script  # naming the right script is fine too


@pytest.mark.parametrize(
    "text, script, font, error, needle",
    [
        ("Asha आशा", None, None, InvalidSpec, "one script: this mixes Latin and Devanagari"),
        ("Αθηνά", None, None, InvalidSpec, "six Indian scripts"),
        ("नमस्ते", "telugu", None, InvalidSpec, "written in Devanagari letters, but Telugu lettering was chosen"),
        ("Asha ❤", None, None, InvalidSpec, "can't print “❤”"),
        ("Asha\nRao", None, None, InvalidSpec, "single line"),
        ("   ", None, None, InvalidSpec, "Type the name"),
        ("Asha", None, "Comic Sans", UnsupportedFeature, "“Comic Sans” is not available"),
        ("नमस्ते", None, "Noto Sans Tamil", UnsupportedFeature, "is not available"),
    ],
)
def test_script_and_font_refusals_are_customer_safe(text, script, font, error, needle):
    with pytest.raises(error) as exc:
        shape_text(text, script, font, key="features[2]")
    assert needle in exc.value.message
    assert exc.value.code in ("invalid_spec", "unsupported_feature")
    assert "glyph" not in exc.value.message.lower() and "mesh" not in exc.value.message.lower()
    assert str(exc.value.detail["key"]).startswith("features[2].")


def test_the_bundled_lettering_may_be_named():
    for font in ("Noto Sans", "noto_sans", "Noto Sans Bold", "NotoSans-Bold"):
        assert resolve_script("Asha", None, font).id == "latin"
    assert resolve_script("नमस्ते", None, "Noto Sans Devanagari").id == "devanagari"
    assert resolve_script("नमस्ते", None, "").id == "devanagari"


# ----------------------------------------------------------------------------- outlines and fill rules


def _square(x0, y0, size, clockwise=False):
    ring = np.array([[x0, y0], [x0 + size, y0], [x0 + size, y0 + size], [x0, y0 + size]], dtype=float)
    return ring[::-1] if clockwise else ring


def test_fill_rules_on_overlaps_holes_and_windings():
    a, b = _square(0, 0, 10), _square(5, 0, 10)  # overlap 5 × 10, both anticlockwise
    assert outlines.fill([a, b], "nonzero").area == pytest.approx(150)
    assert outlines.fill([a, b], "evenodd").area == pytest.approx(100)  # the overlap is a hole
    outer, inner = _square(0, 0, 10), _square(3, 3, 4)
    assert outlines.fill([outer, inner], "nonzero").area == pytest.approx(100)  # same winding: filled
    assert outlines.fill([outer, inner], "evenodd").area == pytest.approx(84)
    assert outlines.fill([outer, _square(3, 3, 4, clockwise=True)], "nonzero").area == pytest.approx(84)  # a real hole
    assert outlines.fill([_square(0, 0, 10, clockwise=True), _square(3, 3, 4)], "nonzero").area == pytest.approx(84)
    island = outlines.fill([outer, _square(2, 2, 6, clockwise=True), _square(4, 4, 2)], "nonzero")
    assert island.area == pytest.approx(100 - 36 + 4) and len(outlines.polygons(island)) == 2
    bowtie = np.array([[0, 0], [10, 10], [10, 0], [0, 10]], dtype=float)  # self-intersecting
    assert outlines.fill([bowtie], "nonzero").area == pytest.approx(50) and outlines.fill([bowtie], "evenodd").area == pytest.approx(50)
    assert outlines.fill([], "nonzero").is_empty
    with pytest.raises(ValueError):
        outlines.fill([a], "winding")
    assert outlines.winding_number(a, 5, 5) == 1 and outlines.winding_number(a[::-1], 5, 5) == -1 and outlines.winding_number(a, 20, 5) == 0


def test_the_pen_flattens_curves_within_its_tolerance():
    pen = outlines.OutlinePen(0.01)
    pen.moveTo((0, 0))
    pen.qCurveTo((5, 10), (10, 0))
    pen.curveTo((12, -5), (-2, -5), (0, 0))
    pen.closePath()
    (ring,) = pen.contours
    t = np.linspace(0, 1, 2001)[:, None]
    quad = (1 - t) ** 2 * np.array([0, 0]) + 2 * t * (1 - t) * np.array([5, 10]) + t**2 * np.array([10, 0])
    cubic = (1 - t) ** 3 * np.array([10, 0]) + 3 * t * (1 - t) ** 2 * np.array([12, -5]) + 3 * t**2 * (1 - t) * np.array([-2, -5]) + t**3 * np.array([0, 0])
    from shapely.geometry import LinearRing, Point

    polyline = LinearRing(ring)
    worst = max(polyline.distance(Point(p)) for p in np.vstack([quad, cubic]))
    assert worst <= 0.01 + 1e-9
    assert len(ring) < 200  # and without runaway subdivision
    # glyph outlines drawn by HarfBuzz arrive as quadratic curves through the same pen protocol
    recording = RecordingPen()
    import uharfbuzz as hb

    font = hb.Font(emboss_text.load_font("NotoSans-Bold.ttf").face)
    font.draw_glyph_with_pen(font.get_nominal_glyph(ord("O")), recording)
    assert {op for op, _ in recording.value} >= {"moveTo", "qCurveTo", "closePath"}


def test_relief_solid_is_closed_with_the_expected_volume():
    region = rect(-5, -3, 5, 3).difference(rect(-1, -1, 1, 1))
    solid = outlines.relief_solid(region, -0.5, 1.2)
    assert solid.is_watertight and solid.is_winding_consistent and solid.volume == pytest.approx(region.area * 1.7, rel=1e-6)
    assert solid.bounds[0][2] == pytest.approx(-0.5) and solid.bounds[1][2] == pytest.approx(1.2)
    with pytest.raises(ValueError):
        outlines.relief_solid(region, 1.0, 1.0)
    with pytest.raises(GeometryError):
        outlines.relief_solid(rect(0, 0, 0, 0), 0, 1)


# ----------------------------------------------------------------------------- the stroke rule


def test_the_stroke_rule():
    assert outlines.stroke_check(rect(0, 0, 30, 0.9), 0.8).ok  # a 0.9 mm bar prints
    thin = outlines.stroke_check(rect(0, 0, 30, 0.7), 0.8)
    assert not thin.ok and thin.lost_pieces == 1 and thin.thin_share == pytest.approx(1.0)
    assert outlines.stroke_check(rect(0, 0, 30, 0.8), 0.8).ok  # exactly the minimum passes
    # sharp corners are not thin strokes: the opening grows back mitred, so a 19° point keeps its whole area
    spike = rect(0, 0, 10, 10).union(Polygon([(10, 3), (22, 5), (10, 7)]))
    assert outlines.stroke_check(spike, 0.8).thin_share < 1e-6
    # a small thin spur (under 5 % of the area) is tolerated, a long thin tail is not
    assert outlines.stroke_check(rect(0, 0, 10, 10).union(rect(10, 5, 18, 5.5)), 0.8).ok
    tail = outlines.stroke_check(rect(0, 0, 10, 10).union(rect(10, 5, 40, 5.5)), 0.8)
    assert not tail.ok and tail.lost_pieces == 0 and tail.thin_share > 0.1
    # a dot narrower than the minimum beside a good bar is lost: it would not print at all
    dotted = outlines.stroke_check(rect(0, 0, 30, 2).union(rect(0, 4, 0.6, 4.6)), 0.8)
    assert not dotted.ok and dotted.lost_pieces == 1 and dotted.thin_share < 0.05


def test_letters_too_thin_to_print_are_refused_with_the_customer_message():
    asha = shape_text("Asha")
    assert fit_text(asha, box=(0, 0, 100, 6.0)).stroke.ok  # 6 mm lettering prints
    with pytest.raises(ParamOutOfRange) as exc:
        fit_text(asha, box=(0, 0, 100, 5.0), key="features[1]")  # 5 mm: the strokes go under 0.8 mm
    assert exc.value.message == TOO_THIN == "These letters would be too thin to print at this size; try fewer letters or a larger piece"
    assert exc.value.keys == ["features[1].text"] and exc.value.detail["thin_share"] > 0.05
    with pytest.raises(ParamOutOfRange) as exc:
        fit_text(asha, box=(0, 0, 100, 20.0), height_mm=5.0, key="features[1]")
    assert exc.value.keys == ["features[1].text", "features[1].height_mm"]
    # a coarser nozzle rule (min_feature 0.4) lets the same lettering through
    assert fit_text(asha, box=(0, 0, 100, 5.0), min_feature_mm=0.4).stroke.ok
    # Kannada strokes are finer than Latin ones: the same height that suits "Asha" is too thin for ನಮಸ್ಕಾರ
    kannada = shape_text("ನಮಸ್ಕಾರ")
    with pytest.raises(ParamOutOfRange):
        fit_text(kannada, box=(0, 0, 200, 7.0))
    assert fit_text(kannada, box=(0, 0, 200, 10.0)).stroke.ok


# ----------------------------------------------------------------------------- fit


def test_fit_to_the_printable_area_and_max_text_height():
    asha = shape_text("Asha")
    # a keychain back (32 × 23.5 mm, 1 mm bleed): width-bound, so as tall as 30 mm of width allows
    fitted = fit_text(asha, box=(0, 0, 30, 21.5))
    assert fitted.width_mm == pytest.approx(30) and fitted.height_mm == pytest.approx(30 / asha.aspect)
    # a nameplate face with room to spare: max_text_height_mm caps it
    capped = fit_text(asha, box=(0, 0, 174, 54), max_height_mm=36)
    assert capped.height_mm == pytest.approx(36) and capped.width_mm == pytest.approx(36 * asha.aspect)
    # an explicit height is kept exactly
    exact = fit_text(asha, box=(0, 0, 174, 54), height_mm=12, max_height_mm=36)
    x0, y0, x1, y1 = exact.region.bounds
    assert (y1 - y0, x1 - x0) == pytest.approx((12, 12 * asha.aspect))
    with pytest.raises(ParamOutOfRange) as exc:
        fit_text(asha, box=(0, 0, 174, 54), height_mm=40, max_height_mm=36, key="features[0]")
    assert exc.value.keys == ["features[0].height_mm"] and "up to 36 mm tall" in exc.value.message
    with pytest.raises(ParamOutOfRange) as exc:
        fit_text(asha, box=(0, 0, 30, 21.5), height_mm=20, anchor_label="Back", key="features[0]")
    assert exc.value.keys == ["features[0].height_mm"] and "62 mm long, but the back has room for 30 mm" in exc.value.message


def test_text_too_long_for_its_anchor_is_refused_before_it_gets_unreadable():
    long_name = shape_text("Aditya Venkatesh")  # 16 letters: the keychain family's maximum
    with pytest.raises(ParamOutOfRange) as exc:
        fit_text(long_name, box=(0, 0, 30, 21.5), anchor_label="Face", key="features[0]")
    assert exc.value.keys == ["features[0].text"]
    assert exc.value.message.startswith("This text is too long for the face: its letters would be under 4 mm tall")
    assert exc.value.detail["height_mm"] < MIN_TEXT_HEIGHT_MM
    assert fit_text(long_name, box=(0, 0, 100, 21.5)).stroke.ok  # the same name on a longer spot is fine


# ----------------------------------------------------------------------------- raised or cut, read from outside


def _plate(w=40.0, d=30.0, t=4.0):
    m = trimesh.creation.box(extents=[w, d, t])
    m.apply_translation([0, 0, t / 2])
    return m


FRONT = AnchorFrame([0, 0, 4], [1, 0, 0], [0, 1, 0], [0, 0, 1], size_mm=(36, 26))
# the underside seen with the plate turned over left to right (like keychain_tag's back anchor)
BACK = AnchorFrame([0, 0, 0], [-1, 0, 0], [0, 1, 0], [0, 0, -1], size_mm=(36, 26))


def _lettering(frame, text="Asha", mode="emboss", depth=1.0):
    fitted = fit_text(shape_text(text), box=(0, 0, 34, 24))
    feature = {"type": "emboss_text", "text": text, "anchor": "face", "mode": mode, "depth_mm": depth}
    return fitted, emboss_text.apply(_plate(), frame, feature, fitted)


@pytest.mark.parametrize("frame", [FRONT, BACK], ids=["front", "back"])
def test_emboss_adds_and_deboss_removes_the_letters_volume(frame):
    body = _plate()
    fitted, raised = _lettering(frame, mode="emboss", depth=1.0)
    assert raised.is_watertight and raised.is_winding_consistent
    assert raised.volume - body.volume == pytest.approx(fitted.region.area * 1.0, rel=0.01)
    _, cut = _lettering(frame, mode="deboss", depth=0.8)
    assert cut.is_watertight and cut.is_winding_consistent
    assert body.volume - cut.volume == pytest.approx(fitted.region.area * 0.8, rel=0.01)
    assert cut.extents == pytest.approx(body.extents, abs=1e-6)  # nothing sticks out of a cut-in name
    assert raised.extents[2] == pytest.approx(5.0, abs=1e-6)


@pytest.mark.parametrize("frame", [FRONT, BACK], ids=["front", "back"])
@pytest.mark.parametrize("mode", ["emboss", "deboss"])
def test_lettering_reads_left_to_right_from_outside(frame, mode):
    """An "L" has its stem on the viewer's left and its foot at the bottom, raised or cut, on either face."""
    body = _plate()
    fitted, result = _lettering(frame, text="L", mode=mode)
    ink = fitted.region.centroid
    assert ink.x < 0 and ink.y < 0  # laid out in the anchor frame: left and low
    letters = booleans.difference(result, body) if mode == "emboss" else booleans.difference(body, result)
    local = frame.to_local(letters.center_mass[None, :])[0]
    assert local[0] == pytest.approx(ink.x, abs=0.05) and local[1] == pytest.approx(ink.y, abs=0.05)
    assert (local[2] > 0) == (mode == "emboss")  # raised letters stand out of the face, cut ones sink in


def test_a_failed_boolean_is_reported_as_unusable_content(monkeypatch):
    from aakar_geometry.errors import ContentUnusable

    def broken(a, b):
        raise GeometryError("Boolean union did not produce a closed volume", {"result": {"watertight": False}})

    monkeypatch.setattr("aakar_geometry.features.outlines.union", broken)
    with pytest.raises(ContentUnusable) as exc:
        _lettering(FRONT)
    assert exc.value.message == "We couldn't set this name into the piece; try another depth or size"
    assert exc.value.detail["text"] == "Asha" and exc.value.detail["mode"] == "emboss"


def test_content_note_names_the_lettering_and_motifs():
    from aakar_geometry.templates import KeychainTag

    features = [
        {"type": "emboss_text", "text": " Asha ", "anchor": "back", "depth_mm": 0.6},
        {"type": "motif", "motif_id": "paisley", "anchor": "face"},
        {"type": "relief_image", "anchor": "face", "source": {"upload_id": "x", "url": "photo.png"}},
    ]
    assert content_note(KeychainTag, features) == "“Asha” stands 0.6 mm proud on the back; the Paisley motif is cut 1 mm into the face."
    assert content_note(KeychainTag, []) == ""
    params = KeychainTag.validate({})
    assert KeychainTag.karigar_note_for(params, features).startswith(KeychainTag.karigar_note(params) + " “Asha”")
    assert KeychainTag.karigar_note_for(params) == KeychainTag.karigar_note(params)
