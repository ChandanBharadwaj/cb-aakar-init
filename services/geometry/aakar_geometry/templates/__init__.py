"""Template registry. Every template in this package registers itself here."""

from __future__ import annotations

import re

from ..errors import UnknownTemplate
from .base import Anchor, Param, Template, TemplateConstraints
from .jharokha_phone_stand import JharokhaPhoneStand

_REF_RE = re.compile(r"^([a-z][a-z0-9_]*)@([0-9]+)$")

REGISTRY: dict[tuple[str, int], type[Template]] = {}


def register(template: type[Template]) -> type[Template]:
    REGISTRY[(template.id, template.version)] = template
    return template


register(JharokhaPhoneStand)


def list_templates() -> list[type[Template]]:
    return [REGISTRY[k] for k in sorted(REGISTRY)]


def parse_ref(ref: str) -> tuple[str, int]:
    m = _REF_RE.match(ref or "")
    if not m:
        raise UnknownTemplate(f"Template reference must look like id@version, got {ref!r}", {"template": ref})
    return m.group(1), int(m.group(2))


def get_template(ref: str) -> type[Template]:
    template_id, version = parse_ref(ref)
    try:
        return REGISTRY[(template_id, version)]
    except KeyError:
        known = [f"{i}@{v}" for i, v in sorted(REGISTRY)]
        raise UnknownTemplate(f"Unknown template {template_id}@{version}", {"template": ref, "known": known}) from None


def latest_by_id(template_id: str) -> type[Template] | None:
    versions = [t for (i, _v), t in REGISTRY.items() if i == template_id]
    return max(versions, key=lambda t: t.version) if versions else None


__all__ = [
    "Anchor",
    "JharokhaPhoneStand",
    "Param",
    "REGISTRY",
    "Template",
    "TemplateConstraints",
    "get_template",
    "latest_by_id",
    "list_templates",
    "parse_ref",
    "register",
]
