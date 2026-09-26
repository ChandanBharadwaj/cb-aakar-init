import json
import logging
from pathlib import Path

import pytest

logging.getLogger("build123d").setLevel(logging.WARNING)

REPO = Path(__file__).resolve().parents[3]
EXAMPLE_SPEC = REPO / "packages/contracts/examples/jharokha-phone-stand.spec.json"
JOB_ID = "6f1c1a2e-9d0b-4a8e-8c1e-2f3d4e5f6a7b"
DESIGN_ID = "0b8e7c6d-5a4b-4c3d-9e2f-1a0b9c8d7e6f"


@pytest.fixture(scope="session")
def example_spec() -> dict:
    return json.loads(EXAMPLE_SPEC.read_text(encoding="utf-8"))


@pytest.fixture
def generate_request(example_spec) -> dict:
    return {"job_id": JOB_ID, "design_id": DESIGN_ID, "version_no": 1, "spec": json.loads(json.dumps(example_spec))}


@pytest.fixture(scope="session")
def built_example(example_spec):
    """Build the example spec once per session (≈1 s) for the geometry assertions."""
    from aakar_geometry.templates import get_template

    template = get_template(example_spec["template"])
    params = template.validate(example_spec["params"])
    return template, params, template.build(params)


@pytest.fixture
def local_storage(tmp_path):
    from aakar_geometry.storage import LocalStorage

    return LocalStorage(tmp_path / "assets", "http://localhost:8081")
