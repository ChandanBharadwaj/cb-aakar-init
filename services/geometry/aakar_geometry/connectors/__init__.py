"""Kadi: the standard connectors a hybrid (Jod) Chhaap plugs into a bought-in base with (docs/research/hybrid-products).

The rule every Kadi follows: the **female** feature belongs to the printed Chhaap, so the piece prints flat with no
supports; the male side is native to the base (a tube top, a stud, a lampholder's neck, a magnet, a cap, a rim) or a
bought-in adapter (a nylon dowel, a PETG rail, a steel disc). Five standards, each in its own module behind one
``Connector``: Kadi-S the ribbed press socket (``socket``), Kadi-D the dovetail channel (``dovetail``), Kadi-T a thread
or lamp collar (``thread``), Kadi-M the magnetic register (``magnet_register``), Kadi-C the compliant rim clip
(``rim_clip``, PETG only); all modelled with the per-material print compensation (``compensation``), each with a fit
coupon the portal builds over HTTP (``coupon``). A template declares its connector as ``Template.connector`` and places
it with ``Template.connector_frame(params)``; ``Template.build`` cuts it after the body and before the content (a rim
clip is built into the body instead).
"""

from .base import BUILT, FITS, FORMS, KINDS, MODES, THREADS, Connector, tolerance_for
from .compensation import Compensation, for_material, process_of

__all__ = [
    "BUILT",
    "Compensation",
    "Connector",
    "FITS",
    "FORMS",
    "KINDS",
    "MODES",
    "THREADS",
    "for_material",
    "process_of",
    "tolerance_for",
]
