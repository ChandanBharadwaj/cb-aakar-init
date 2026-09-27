import json
import subprocess
import sys

from aakar_geometry.cli import main
from aakar_geometry.contracts import validate

from conftest import EXAMPLE_SPEC


def test_cli_templates(capsys):
    assert main(["templates"]) == 0
    descriptors = json.loads(capsys.readouterr().out)
    assert descriptors[0]["id"] == "jharokha_phone_stand"


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
