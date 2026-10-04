"""Style variants (``design-spec.v1.json`` ``style``) and what they change in the geometry.

A template offers a style by listing it in ``style_variants``; the pipeline refuses a style the template does not
list, so nothing here applies to one. Only ``comic_pop`` (Katha's comic-book look, plan §8) changes anything, and
only the content set into the piece:

* a name (``emboss_text``) or a motif (``motif``) that leaves ``mode`` out stands raised (``emboss``) wherever its
  anchor allows raised content (``Anchor.modes``); an anchor that only cuts in keeps the type's default;
* raised lettering and motifs that leave ``depth_mm`` out stand ``COMIC_POP_DEPTH_MM`` proud, never more than the
  anchor's ``max_relief_mm``;
* explicit values always win, and photo reliefs (``relief_image``) and customers' forms are never touched.

A piece without content builds exactly as it would without the style. The karigar's note names the look and
suggests a high-contrast finish (``note``).
"""

from __future__ import annotations

from typing import Any, Iterable, Mapping

COMIC_POP = "comic_pop"
COMIC_POP_DEPTH_MM = 1.5  # plan §8: comic relief 1.0–1.5 mm; bolder than the contract's 1.2 (names) and 1.0 (motifs)
MARK_TYPES = ("emboss_text", "motif")
# the lightest and the darkest matte finishes: the raised lettering's shadows read like inked lines on either
HIGH_CONTRAST_FINISHES = ("basic_white", "indigo_matte")


def offered(template: Any, style: str | None) -> bool:
    """True when ``template`` lists ``style`` among its variants (``none`` and unknown styles never are)."""
    return bool(style) and style != "none" and style in tuple(getattr(template, "style_variants", ()) or ())


def _raises(anchor: Any) -> bool:
    modes = getattr(anchor, "modes", None)
    return modes is None or "emboss" in modes


def feature_defaults(style: str | None, ftype: str, given: Mapping[str, Any], anchor: Any) -> dict[str, Any]:
    """Defaults a style puts in place of the contract's for one feature; ``given`` holds the values the spec set
    (they still win). Empty unless the style is ``comic_pop`` and the feature is a name or a motif on a known anchor."""
    if style != COMIC_POP or ftype not in MARK_TYPES or anchor is None:
        return {}
    out: dict[str, Any] = {}
    raises = _raises(anchor)
    mode = given.get("mode")
    if mode is None and raises:
        out["mode"] = mode = "emboss"
    if mode == "emboss" and raises and given.get("depth_mm") is None:
        cap = getattr(anchor, "max_relief_mm", None)
        out["depth_mm"] = min(COMIC_POP_DEPTH_MM, float(cap)) if cap is not None else COMIC_POP_DEPTH_MM
    return out


def _finishes(template: Any) -> str:
    """"Basic White or Indigo Matte": the high-contrast finishes this template's family offers, by name."""
    from ..materials import load_materials

    try:
        offered_ids = set(template.materials())
    except Exception:  # a template whose family is unknown: name the finishes without the family's rules
        offered_ids = set(HIGH_CONTRAST_FINISHES)
    names = {m["id"]: m.get("name") or m["id"] for m in load_materials()}
    picks = [names[i] for i in HIGH_CONTRAST_FINISHES if i in names and i in offered_ids]
    return " or ".join(picks)


def note(template: Any, style: str | None, features: Iterable[Mapping[str, Any]] | None = ()) -> str:
    """The karigar's sentence on the piece's style ("" unless ``template`` offers it and it changes anything)."""
    if style != COMIC_POP or not offered(template, style):
        return ""
    from .validate import normalise_features  # validate imports this module: import here, not at the top

    finishes = _finishes(template)
    finish = f"a high-contrast finish such as {finishes}" if finishes else "a high-contrast finish"
    marks = [f for f in normalise_features(features, template=template, style=style) if f["type"] in MARK_TYPES]
    raised = any(f.get("mode") == "emboss" for f in marks)
    if raised:
        return (
            "Comic pop style: the lettering and motifs stand bold and raised, like inked panel art, so keep their edges "
            f"crisp; they read best in {finish}."
        )
    return f"Comic pop style: it reads best in {finish}, like a comic panel."


__all__ = ["COMIC_POP", "COMIC_POP_DEPTH_MM", "HIGH_CONTRAST_FINISHES", "MARK_TYPES", "feature_defaults", "note", "offered"]
