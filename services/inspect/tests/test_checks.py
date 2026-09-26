import numpy as np
import pytest

from aakar_inspect import Constraints, InvalidSettings, SlicingSettings, inspect_mesh
from aakar_inspect.contracts import is_valid, validate


def test_cube_report(cube40):
    report, estimate = inspect_mesh(cube40)
    validate("printability-report", report)
    validate("print-estimate", estimate)
    checks = report["checks"]
    assert report["passed"] is True
    assert checks["manifold"]["status"] == "pass"
    assert checks["fits_bed"]["status"] == "pass"
    assert checks["fits_bed"]["summary"] == "Fits 250 × 250 × 250 mm bed"
    assert checks["centre_of_gravity"]["status"] == "pass"
    assert checks["centre_of_gravity"]["summary"].startswith("Inside base")
    assert checks["centre_of_gravity"]["value"] == pytest.approx(0.0, abs=0.1)
    assert checks["tipping_margin"]["status"] == "pass"
    assert checks["tipping_margin"]["value"] == pytest.approx(20.0, abs=0.2)
    assert checks["thinnest_wall"]["status"] == "pass"
    assert checks["thinnest_wall"]["summary"].startswith("Solid")
    assert checks["thinnest_wall"]["value"] == pytest.approx(40.0, abs=0.2)
    assert checks["overhangs"]["status"] == "skipped"
    assert checks["load_capacity"]["status"] == "skipped"
    assert report["geometry"]["bounds_mm"] == pytest.approx([40, 40, 40])
    assert report["geometry"]["volume_cm3"] == pytest.approx(64.0)
    assert report["geometry"]["triangles"] == 12


def test_pillar_centred_is_tippy_but_inside(pillar_factory):
    report, _ = inspect_mesh(pillar_factory(dx=0.0))
    checks = report["checks"]
    assert checks["centre_of_gravity"]["status"] == "pass"
    assert checks["tipping_margin"]["status"] == "warn"  # 3 mm to the edge of a 6 mm base
    assert checks["tipping_margin"]["value"] == pytest.approx(3.0, abs=0.05)
    assert report["passed"] is True


def test_pillar_offset_top_tips_over(pillar_factory):
    report, _ = inspect_mesh(pillar_factory(dx=14.0))
    validate("printability-report", report)
    checks = report["checks"]
    assert checks["centre_of_gravity"]["status"] == "fail"
    assert checks["centre_of_gravity"]["summary"].startswith("Outside base")
    assert checks["centre_of_gravity"]["value"] > 3.0
    assert checks["tipping_margin"]["status"] == "fail"
    assert checks["tipping_margin"]["value"] < 0
    assert report["passed"] is False


def test_pillar_walls_measure_pillar_thickness(pillar_factory):
    report, _ = inspect_mesh(pillar_factory(dx=0.0))
    assert report["checks"]["thinnest_wall"]["value"] == pytest.approx(6.0, abs=0.1)


def test_non_watertight_fails_manifold(open_mesh):
    report, estimate = inspect_mesh(open_mesh)
    validate("printability-report", report)
    validate("print-estimate", estimate)
    assert report["checks"]["manifold"]["status"] == "fail"
    assert report["checks"]["manifold"]["value"] is False
    assert report["passed"] is False


def test_thin_wall_fails_below_minimum():
    import trimesh

    plate = trimesh.creation.box(extents=[60, 60, 0.8])
    plate.apply_translation([0, 0, 0.4])
    report, _ = inspect_mesh(plate, Constraints(min_wall_mm=1.2))
    assert report["checks"]["thinnest_wall"]["status"] == "fail"
    assert report["checks"]["thinnest_wall"]["value"] == pytest.approx(0.8, abs=0.05)
    assert report["passed"] is False
    report_warn, _ = inspect_mesh(plate, Constraints(min_wall_mm=0.75))
    assert report_warn["checks"]["thinnest_wall"]["status"] == "warn"


def test_fits_bed_any_permutation():
    import trimesh

    tall = trimesh.creation.box(extents=[50, 50, 300])
    tall.apply_translation([0, 0, 150])
    report, _ = inspect_mesh(tall, Constraints(bed_mm=(320, 250, 250)))
    assert report["checks"]["fits_bed"]["status"] == "pass"
    report, _ = inspect_mesh(tall, Constraints(bed_mm=(250, 250, 250)))
    assert report["checks"]["fits_bed"]["status"] == "fail"


def test_settings_reject_out_of_range():
    with pytest.raises(InvalidSettings) as exc:
        Constraints(min_wall_mm=-1)
    assert exc.value.keys == ["min_wall_mm"]
    with pytest.raises(InvalidSettings):
        SlicingSettings(infill_pct=120)
    with pytest.raises(InvalidSettings):
        SlicingSettings(walls=0)


def test_every_check_shape_validates(cube40, pillar_factory, open_mesh):
    for mesh in (cube40, pillar_factory(5.0), open_mesh):
        report, estimate = inspect_mesh(mesh)
        assert is_valid("printability-report", report)
        assert is_valid("print-estimate", estimate)
        assert set(report["checks"]) == {
            "manifold",
            "fits_bed",
            "thinnest_wall",
            "centre_of_gravity",
            "tipping_margin",
            "overhangs",
            "load_capacity",
        }
