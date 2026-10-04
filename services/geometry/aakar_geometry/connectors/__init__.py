"""Kadi: the standard connectors a hybrid (Jod) Chhaap plugs into a bought-in base with (docs/research/hybrid-products).

The rule every Kadi follows: the **female** feature is cut into a flange of the printed Chhaap, so the piece
prints flat with no supports; the male side is native to the base (a tube top, a stud) or a bought-in adapter
(a nylon dowel). Phase 1 ships Kadi-S, the ribbed press socket (``socket``), with the per-material print
compensation it is modelled with (``compensation``); the dovetail, magnetic register, thread and rim clip
follow in Phase 4. A template declares its connector as ``Template.connector`` and places it with
``Template.connector_frame(params)``; ``Template.build`` cuts it after the body and before the content.
"""

from .base import Connector, KINDS, FITS
from .compensation import Compensation, for_material, process_of

__all__ = ["Compensation", "Connector", "FITS", "KINDS", "for_material", "process_of"]
