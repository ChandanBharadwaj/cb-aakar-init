"""The saddle a headband rests across: shared by ``headphone_topper@1`` (Sur Jod, on a bought-in stand) and
``pillar_headphone_stand@1`` (Sur, the fully printed stand), so the two forms of the same idea have the same crown.

A block ``width`` wide topped by a crown, an arc of radius ``CROWN_R_MM`` (a cylinder along Y) whose apex is ``height``
above ``base_z`` and whose shoulders meet the vertical sides at ``height − rise``; a headband's inner curve is shallower
than the crown, so it seats on the apex. The profile is drawn in (x, height) and extruded along Y by ``depth``.
"""

from __future__ import annotations

import math

from shapely.geometry import Polygon

from .. import cad

CROWN_R_MM = 60.0
ARC_STEPS = 48
OVERLAP_MM = 0.5  # the saddle starts this far inside what it stands on so the union has volume in common


class Saddle:
    def __init__(self, width: float, depth: float, height: float, base_z: float = 0.0):
        self.W = float(width)
        self.D = float(depth)
        self.H = float(height)  # the apex above z = 0 of the profile
        self.base_z = float(base_z)  # where the block starts (the plate's top, the pillar's top)
        self.rise = CROWN_R_MM - math.sqrt(CROWN_R_MM**2 - (self.W / 2.0) ** 2)
        self.shoulder_z = self.H - self.rise

    def crown_profile(self) -> Polygon:
        """The saddle's cross-section in (x, height): a block from ``base_z − OVERLAP_MM`` topped by the crown's arc."""
        zc = self.H - CROWN_R_MM
        a0 = math.atan2(self.shoulder_z - zc, self.W / 2.0)
        a1 = math.pi - a0
        pts = [(-self.W / 2.0, self.base_z - OVERLAP_MM), (self.W / 2.0, self.base_z - OVERLAP_MM), (self.W / 2.0, self.shoulder_z)]
        for i in range(1, ARC_STEPS):
            a = a0 + (a1 - a0) * i / ARC_STEPS
            pts.append((CROWN_R_MM * math.cos(a), zc + CROWN_R_MM * math.sin(a)))
        pts.append((-self.W / 2.0, self.shoulder_z))
        return Polygon(pts)

    def part(self, y_back: float) -> "cad.Part":
        """The saddle as a solid spanning y from ``y_back − depth`` to ``y_back``."""
        return cad.translate(cad.rotate_x(cad.prism(self.crown_profile(), self.D), 90.0), dy=y_back)


__all__ = ["ARC_STEPS", "CROWN_R_MM", "OVERLAP_MM", "Saddle"]
