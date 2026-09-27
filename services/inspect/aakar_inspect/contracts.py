"""Access to the shared JSON Schemas in packages/contracts.

Schemas are loaded from ``AAKAR_CONTRACTS_DIR`` (Dockerfiles set it to ``/contracts/schemas``),
defaulting to the repo-relative ``../../packages/contracts/schemas`` resolved from this service.
Cross-schema ``$ref``s (``https://aakar.studio/schemas/...``) are resolved from the same folder,
so no network access is ever needed to validate a contract.
"""

from __future__ import annotations

import json
import os
from functools import lru_cache
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator
from jsonschema.exceptions import ValidationError
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT202012

SCHEMA_ID_BASE = "https://aakar.studio/schemas/"

# Short names used across both services -> schema file (relative to the schemas dir).
SCHEMA_FILES = {
    "design-spec": "design-spec.v1.json",
    "template-descriptor": "template-descriptor.v1.json",
    "template-family": "template-family.v1.json",
    "printability-report": "printability-report.v1.json",
    "print-estimate": "print-estimate.v1.json",
    "price-breakdown": "price-breakdown.v1.json",
    "envelope": "events/envelope.v1.json",
    "design.generate": "events/design.generate.v1.json",
    "design.progress": "events/design.progress.v1.json",
    "design.completed": "events/design.completed.v1.json",
    "design.failed": "events/design.failed.v1.json",
}


class ContractError(ValueError):
    """An instance does not validate against a contract schema."""

    def __init__(self, schema: str, errors: list[ValidationError]):
        self.schema = schema
        self.errors = errors
        lines = [f"{'/'.join(str(p) for p in e.absolute_path) or '<root>'}: {e.message}" for e in errors]
        super().__init__(f"{schema} contract violated: " + "; ".join(lines[:8]))

    def as_detail(self) -> list[dict[str, str]]:
        return [
            {"path": "/".join(str(p) for p in e.absolute_path), "message": e.message}
            for e in self.errors
        ]


def schemas_dir() -> Path:
    env = os.environ.get("AAKAR_CONTRACTS_DIR")
    if env:
        return Path(env).expanduser().resolve()
    # <repo>/services/inspect/aakar_inspect/contracts.py -> <repo>/packages/contracts/schemas
    service_dir = Path(__file__).resolve().parent.parent
    return (service_dir / "../../packages/contracts/schemas").resolve()


def schema_path(name: str) -> Path:
    try:
        rel = SCHEMA_FILES[name]
    except KeyError as exc:  # pragma: no cover - programming error
        raise KeyError(f"unknown contract schema '{name}'; known: {sorted(SCHEMA_FILES)}") from exc
    path = schemas_dir() / rel
    if not path.exists():
        raise FileNotFoundError(
            f"contract schema {rel} not found under {schemas_dir()} "
            "(set AAKAR_CONTRACTS_DIR to the packages/contracts/schemas folder)"
        )
    return path


@lru_cache(maxsize=None)
def load_schema(name: str) -> dict[str, Any]:
    return json.loads(schema_path(name).read_text(encoding="utf-8"))


@lru_cache(maxsize=1)
def registry() -> Registry:
    """Registry of every schema in the contracts folder, keyed by ``$id``."""
    reg: Registry = Registry()
    base = schemas_dir()
    files = sorted(base.glob("*.json")) + sorted((base / "events").glob("*.json"))
    for file in files:
        doc = json.loads(file.read_text(encoding="utf-8"))
        ident = doc.get("$id") or SCHEMA_ID_BASE + file.relative_to(base).as_posix()
        reg = reg.with_resource(ident, Resource.from_contents(doc, default_specification=DRAFT202012))
    return reg


@lru_cache(maxsize=None)
def validator(name: str) -> Draft202012Validator:
    schema = load_schema(name)
    return Draft202012Validator(
        schema, registry=registry(), format_checker=Draft202012Validator.FORMAT_CHECKER
    )


def validation_errors(name: str, instance: Any) -> list[ValidationError]:
    return sorted(validator(name).iter_errors(instance), key=lambda e: list(e.absolute_path))


def validate(name: str, instance: Any) -> None:
    """Raise :class:`ContractError` when ``instance`` violates contract ``name``."""
    errors = validation_errors(name, instance)
    if errors:
        raise ContractError(name, errors)


def is_valid(name: str, instance: Any) -> bool:
    return not validation_errors(name, instance)
