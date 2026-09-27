"""The committed descriptors (packages/contracts/examples/template-descriptors.json) match the live registry.

The storefront and portal mocks and the API test fixtures read that file, so a template change that is not
re-exported would let them drift. Regenerate with `make descriptors` (or `uv run aakar-geometry templates`).
"""

import json
from pathlib import Path

from aakar_geometry.templates import list_templates

EXAMPLE = Path(__file__).resolve().parents[3] / "packages" / "contracts" / "examples" / "template-descriptors.json"


def test_committed_descriptors_match_the_registry():
    committed = json.loads(EXAMPLE.read_text(encoding="utf-8"))
    live = json.loads(json.dumps([t.descriptor() for t in list_templates()], ensure_ascii=False))
    assert [d["id"] for d in committed] == [d["id"] for d in live], "run `make descriptors`"
    assert committed == live, "template descriptors changed; run `make descriptors` and commit the result"
