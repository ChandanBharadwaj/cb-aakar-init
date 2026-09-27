import json
import subprocess
import sys

import pytest

from aakar_geometry.cli import main
from aakar_geometry.contracts import validate

from conftest import EXAMPLE_SPEC


def test_cli_templates(capsys):
    assert main(["templates"]) == 0
    descriptors = json.loads(capsys.readouterr().out)
    assert [d["id"] for d in descriptors] == ["desk_nameplate", "fridge_magnet", "hanging_ornament", "jharokha_phone_stand", "keychain_tag", "raw_print"]
    for descriptor in descriptors:
        validate("template-descriptor", descriptor)


def test_cli_build_writes_files_and_summary(tmp_path, capsys):
    out = tmp_path / "slice"
    code = main(["build", str(EXAMPLE_SPEC), "--out", str(out), "--design-id", "0b8e7c6d-5a4b-4c3d-9e2f-1a0b9c8d7e6f"])
    assert code == 0
    summary = capsys.readouterr().out.strip()
    assert "bounds 92 × 78 × 120 mm" in summary and "passed" in summary and "triangles" in summary and " g (" in summary
    for name in ("model.glb", "model.3mf", "model.stl", "result.json"):
        assert (out / name).stat().st_size > 0
    result = json.loads((out / "result.json").read_text())
    validate("design.completed", result)
    assert result["design_id"] == "0b8e7c6d-5a4b-4c3d-9e2f-1a0b9c8d7e6f"


def test_cli_build_reports_failure(tmp_path, capsys):
    spec = json.loads(EXAMPLE_SPEC.read_text())
    spec["params"]["wall_mm"] = 9
    bad = tmp_path / "bad.json"
    bad.write_text(json.dumps(spec))
    assert main(["build", str(bad), "--out", str(tmp_path / "o")]) == 1
    assert "param_out_of_range" in capsys.readouterr().err
    assert json.loads((tmp_path / "o" / "result.json").read_text())["code"] == "param_out_of_range"


def test_console_script_help():
    proc = subprocess.run([sys.executable, "-m", "aakar_geometry.cli", "--help"], capture_output=True, text=True)
    assert proc.returncode == 0 and "templates" in proc.stdout and "worker" in proc.stdout


def test_cli_build_with_content_dir(test_templates, content_dir, tmp_path, capsys, monkeypatch):
    from aakar_geometry.errors import ContentUnusable
    from conftest import content_source

    spec = {
        "spec_version": "1.0",
        "family": "keychain",
        "template": "test_plate@1",
        "params": {"thickness_mm": 3},
        "features": [{"type": "relief_image", "source": content_source("photo.png", "png"), "anchor": "face"}],
        "material": "basic_white",
    }
    spec_path = tmp_path / "plate.json"
    spec_path.write_text(json.dumps(spec))
    out = tmp_path / "out"
    assert main(["build", str(spec_path), "--out", str(out), "--content-dir", str(content_dir)]) == 0
    summary = capsys.readouterr().out.strip()
    assert "test_plate@1" in summary and "hardware split_ring_25 ×1" in summary
    result = json.loads((out / "result.json").read_text())
    validate("design.completed", result)
    assert result["hardware"] == [{"sku": "split_ring_25", "qty": 1}]
    # without --content-dir the pipeline's HttpFetcher is used; a failed fetch is a clean design.failed
    class Offline:
        def fetch(self, source):
            raise ContentUnusable("We couldn't find your photo; please upload it again", {"url": source.get("url")})

    monkeypatch.setattr("aakar_geometry.pipeline.HttpFetcher", Offline)
    assert main(["build", str(spec_path), "--out", str(tmp_path / "out2")]) == 1
    assert json.loads((tmp_path / "out2" / "result.json").read_text())["code"] == "content_unusable"


def _example(name: str) -> dict:
    from conftest import REPO

    return json.loads((REPO / "packages/contracts/examples" / name).read_text(encoding="utf-8"))


def _content_name(spec: dict) -> str:
    from pathlib import PurePosixPath
    from urllib.parse import urlparse

    return PurePosixPath(urlparse(spec["features"][0]["source"]["url"]).path).name


def test_cli_builds_the_keychain_photo_example_from_a_local_png(tmp_path, capsys):
    """keychain-photo.spec.json with its photo only (its emboss_text feature needs the text release, PR 3b)."""
    from conftest import gradient_png

    spec = _example("keychain-photo.spec.json")
    spec["features"] = [f for f in spec["features"] if f["type"] == "relief_image"]
    assert len(spec["features"]) == 1
    content = tmp_path / "content"
    content.mkdir()
    (content / _content_name(spec)).write_bytes(gradient_png())
    spec_path = tmp_path / "keychain.json"
    spec_path.write_text(json.dumps(spec))
    out = tmp_path / "keychain"
    assert main(["build", str(spec_path), "--out", str(out), "--content-dir", str(content)]) == 0
    summary = capsys.readouterr().out.strip()
    assert summary.startswith("keychain_tag@1 · bounds 45 × ") and "passed" in summary and "hardware split_ring_25 ×1" in summary
    result = json.loads((out / "result.json").read_text())
    validate("design.completed", result)
    assert result["hardware"] == [{"sku": "split_ring_25", "qty": 1}]
    assert result["spec"]["params"] == {"shape": "rounded", "width_mm": 45.0, "thickness_mm": 3.0, "hole_d_mm": 4.2}
    assert result["printability"]["geometry"]["bounds_mm"][2] == pytest.approx(3.6, abs=1e-3)  # 3 mm tag + 0.6 mm relief
    for name in ("model.glb", "model.3mf", "model.stl"):
        assert (out / name).stat().st_size > 0


def test_cli_builds_the_raw_print_example_from_a_local_model(tmp_path, capsys):
    """raw-print.spec.json as committed: no template params, the size lives on the hero_mesh feature."""
    from conftest import REPO, sphere_stl

    spec = _example("raw-print.spec.json")
    assert spec["params"] == {} and spec["features"][0]["fit"] == "longest"
    content = tmp_path / "content"
    content.mkdir()
    (content / _content_name(spec)).write_bytes(sphere_stl(radius=12.0))
    out = tmp_path / "raw"
    example = REPO / "packages/contracts/examples/raw-print.spec.json"
    assert main(["build", str(example), "--out", str(out), "--content-dir", str(content)]) == 0
    assert capsys.readouterr().out.startswith("raw_print@1 · bounds 80 × ")
    result = json.loads((out / "result.json").read_text())
    validate("design.completed", result)
    assert result["hardware"] == [] and max(result["printability"]["geometry"]["bounds_mm"]) == pytest.approx(80, abs=1e-3)
