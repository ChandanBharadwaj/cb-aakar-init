"""Build failures, each mapped to a ``design.failed`` code (events/design.failed.v1.json)."""

from __future__ import annotations

from typing import Any


class BuildError(Exception):
    """Base class. ``code`` is the design.failed code; ``message`` is customer-safe; ``detail`` is for developers."""

    code = "build_error"
    http_status = 500

    def __init__(self, message: str, detail: dict[str, Any] | None = None):
        super().__init__(message)
        self.message = message
        self.detail = dict(detail or {})


class InvalidSpec(BuildError):
    code = "invalid_spec"
    http_status = 422


class UnknownTemplate(BuildError):
    code = "unknown_template"
    http_status = 422


class UnsupportedFeature(BuildError):
    code = "unsupported_feature"
    http_status = 422


class ParamOutOfRange(BuildError):
    """One or more parameters are outside the template's ranges. Never clamped silently."""

    code = "param_out_of_range"
    http_status = 422

    def __init__(self, keys: list[str], message: str | None = None, detail: dict[str, Any] | None = None):
        self.keys = list(keys)
        detail = dict(detail or {})
        detail.setdefault("keys", self.keys)
        super().__init__(message or f"Parameters out of range: {', '.join(self.keys)}", detail)


class ContentUnusable(BuildError):
    """The customer's photo or 3D model could not be turned into printable geometry (Chhaap content).

    The default message is customer-safe; ``detail`` carries the developer reason (decoder error,
    open edges, boolean failure). Never mention meshes or STL in the message: say photo, model, form.
    """

    code = "content_unusable"
    http_status = 422
    default_message = "We couldn't make this content printable; try a different photo/model or a larger size"

    def __init__(self, message: str | None = None, detail: dict[str, Any] | None = None):
        super().__init__(message or self.default_message, detail)


class NotPrintable(BuildError):
    code = "not_printable"
    http_status = 422


class IncompatibleBase(BuildError):
    """The base the spec names presents a different Kadi (kind, size, form) from the one this template cuts."""

    code = "incompatible_base"
    http_status = 422


class GeometryError(BuildError):
    code = "build_error"
    http_status = 500


class StorageError(BuildError):
    code = "storage_error"
    http_status = 500


class BuildTimeout(BuildError):
    code = "timeout"
    http_status = 500
