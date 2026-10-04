"""Where a name (Naam) and a motif (Buti) sit inside a surface anchor's printable area.

Boxes are ``(cx, cy, width, height)`` in mm in the anchor frame (x = the viewer's right, y = up the
picture, origin at the centre of ``size_mm``). The printable area is ``size_mm − 2·bleed_mm`` around the
origin. A name or a motif alone takes all of it. A name and a motif on the same anchor sit side by side
and never overlap: the motif in a square cell at the left end (as tall as the area, at most
``MOTIF_SHARE`` of its width), the name in the rest after ``GAP_MM``, the way a nameplate carries an
emblem before the name. A photo relief never shares an anchor with either (``features.validate``).
"""

from __future__ import annotations

from ..errors import GeometryError
from .frames import AnchorFrame

Box = tuple[float, float, float, float]

MOTIF_SHARE = 0.4
GAP_MM = 2.0


def printable_box(frame: AnchorFrame, bleed_mm: float = 0.0) -> Box:
    """The whole printable area of a surface anchor frame."""
    if frame.size_mm is None:
        raise GeometryError("Surface anchor frame has no size_mm", {})
    w = float(frame.size_mm[0]) - 2.0 * float(bleed_mm)
    h = float(frame.size_mm[1]) - 2.0 * float(bleed_mm)
    if w <= 0 or h <= 0:
        raise GeometryError("The anchor's printable area is used up by its bleed", {"size_mm": list(frame.size_mm), "bleed_mm": bleed_mm})
    return (0.0, 0.0, w, h)


def content_boxes(frame: AnchorFrame, bleed_mm: float = 0.0, *, text: bool = False, motif: bool = False) -> dict[str, Box]:
    """``{"emboss_text": box, "motif": box}`` for the content present on one anchor."""
    cx, cy, w, h = printable_box(frame, bleed_mm)
    if not (text and motif):
        out: dict[str, Box] = {}
        if text:
            out["emboss_text"] = (cx, cy, w, h)
        if motif:
            out["motif"] = (cx, cy, w, h)
        return out
    cell = min(h, MOTIF_SHARE * w)
    left = cx - w / 2.0
    text_w = w - cell - GAP_MM
    if text_w <= 0:  # pragma: no cover - every anchor is at least 8 mm wide
        raise GeometryError("The anchor is too narrow for a name beside a motif", {"printable_mm": [w, h]})
    return {
        "motif": (left + cell / 2.0, cy, cell, cell),
        "emboss_text": (left + cell + GAP_MM + text_w / 2.0, cy, text_w, h),
    }


__all__ = ["Box", "GAP_MM", "MOTIF_SHARE", "content_boxes", "printable_box"]
