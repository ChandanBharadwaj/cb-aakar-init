"""Bought-in bases (Buniyaad): schematic preview models the portal asks for through ``POST /v1/bases/preview`` (``shapes``)."""

from .shapes import SHAPES, build_shape, render_preview

__all__ = ["SHAPES", "build_shape", "render_preview"]
