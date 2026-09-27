"""families.json: every template belongs to a known family, content slots bound features, material rules apply."""

from __future__ import annotations

import json

import pytest

from aakar_geometry import families
from aakar_geometry.contracts import validate
from aakar_geometry.materials import load_materials
from aakar_geometry.templates import REGISTRY, list_templates, register
from aakar_geometry.templates.base import Template


def test_seed_loads_from_the_repo_and_validates():
    doc = families.load_families_doc()
    assert doc["source"].endswith("packages/design-tokens/families.json")
    validate("template-family", {"shelves": doc["shelves"], "hardware_items": doc["hardware_items"], "families": doc["families"]})
    ids = families.family_ids()
    assert {"keychain", "fridge_magnet", "ornament", "nameplate", "lithophane", "figurine_base", "raw_print", "phone_stand"} <= set(ids)
    assert families.family("raw_print")["kind"] == "raw"
    with pytest.raises(families.UnknownFamily):
        families.family("unicorn")
    assert families.default_hardware("keychain") == [{"sku": "split_ring_25", "qty": 1}]
    assert "split_ring_25" in families.hardware_items()


def test_every_registered_template_has_a_known_family_and_a_bounded_content_slot():
    assert list_templates()
    for template in list_templates():
        assert template.family in families.family_ids(), template.ref()
        slot = families.content_slot(template.family)
        assert set(template.features_supported) <= set(slot["accepts"]), (template.ref(), slot["accepts"])
        # every anchor that accepts a hero form is a volume anchor with bounds, surfaces carry sizes
        for anchor in template.anchors:
            if anchor.kind == "volume":
                assert anchor.bounds_mm is not None, (template.ref(), anchor.id)
            if "hero_mesh" in (anchor.accepts or ()):
                assert anchor.kind == "volume"
        assert template.materials(), template.ref()


def test_content_slot_and_material_rules_fill_defaults():
    slot = families.content_slot("keychain")
    assert slot["accepts"] == ["relief_image", "emboss_text", "motif"] and slot["max_text_chars"] == 16 and slot["hero_volume"] is False
    assert families.content_slot("raw_print")["hero_volume"] is True and families.content_slot("raw_print")["max_text_chars"] is None
    rules = families.material_rules("lithophane")
    assert rules == {"heat_safe_only": False, "allowed": ["basic_white"], "excluded_finish_classes": []}
    assert families.material_rules("keychain")["allowed"] is None


def test_material_rules_filter_allowed_heat_safe_and_finish_classes():
    materials = [
        {"id": "basic_white", "finish_class": "matte", "heat_safe": False},
        {"id": "terracotta_silk", "finish_class": "silk", "heat_safe": False},
        {"id": "steel_grey", "finish_class": "matte", "heat_safe": True},
    ]
    assert families.apply_material_rules({"allowed": ["terracotta_silk", "basic_white"]}, materials) == ["basic_white", "terracotta_silk"]
    assert families.apply_material_rules({"heat_safe_only": True}, materials) == ["steel_grey"]
    assert families.apply_material_rules({"excluded_finish_classes": ["silk"]}, materials) == ["basic_white", "steel_grey"]
    assert families.apply_material_rules({"allowed": None, "heat_safe_only": False, "excluded_finish_classes": []}, materials) == [
        "basic_white", "terracotta_silk", "steel_grey",
    ]
    assert families.apply_material_rules({"allowed": ["gold_leaf"]}, materials) == []


def test_template_materials_respect_the_family_rules():
    class Lamp(Template):
        id = "rules_lamp"
        version = 1
        family = "lithophane"  # allowed: [basic_white]
        name = "Rules lamp"

    class Coaster(Template):
        id = "rules_coaster"
        version = 1
        family = "coaster"  # heat_safe_only, and no launch material is heat safe
        name = "Rules coaster"

    class Stray(Template):
        id = "rules_stray"
        version = 1
        family = "unicorn"
        name = "Stray"

    assert Lamp.materials() == ["basic_white"]
    with pytest.raises(families.FamilyConfigError):
        Coaster.materials()
    assert Stray.materials() == [m["id"] for m in load_materials()]  # unknown family (never registrable): no rules


def test_register_refuses_unknown_families():
    class Stray(Template):
        id = "stray_template"
        version = 1
        family = "unicorn"
        name = "Stray"

    with pytest.raises(families.UnknownFamily):
        register(Stray)
    assert ("stray_template", 1) not in REGISTRY

    class Ok(Template):
        id = "ok_template"
        version = 1
        family = "keychain"
        name = "Ok"

    try:
        assert register(Ok) is Ok and REGISTRY[("ok_template", 1)] is Ok
    finally:
        REGISTRY.pop(("ok_template", 1), None)


def test_builtin_copy_when_the_seed_is_not_mounted(monkeypatch, tmp_path):
    monkeypatch.setenv("AAKAR_FAMILIES_FILE", str(tmp_path / "missing.json"))
    families.load_families_doc.cache_clear()
    try:
        doc = families.load_families_doc()
        assert doc["source"] == "builtin"
        assert "phone_stand" in families.family_ids() and "raw_print" in families.family_ids()
        assert families.content_slot("keychain")["max_text_chars"] == 16
        assert families.material_rules("lithophane")["allowed"] == ["basic_white"]
        # the built-in rows are shaped like the seed rows
        validate("template-family", {"shelves": [{"id": "gifting", "label": "Gifting"}], "hardware_items": doc["hardware_items"], "families": doc["families"]})
    finally:
        families.load_families_doc.cache_clear()


def test_builtin_rows_match_the_seed_on_geometry_fields():
    from conftest import REPO

    seed = {f["id"]: f for f in json.loads((REPO / "packages/design-tokens/families.json").read_text(encoding="utf-8"))["families"]}
    for row in families.BUILTIN_FAMILIES:
        real = seed[row["id"]]
        assert row["kind"] == real["kind"]
        assert row["content_slot"]["accepts"] == real["content_slot"]["accepts"], row["id"]
        assert row["content_slot"].get("max_text_chars") == real["content_slot"].get("max_text_chars"), row["id"]
        assert row["hardware"] == real["hardware"], row["id"]
        assert row["material_rules"] == real["material_rules"], row["id"]


def test_drifted_seed_fails_loudly(monkeypatch, tmp_path):
    bad = tmp_path / "families.json"
    bad.write_text(json.dumps({"shelves": [{"id": "gifting", "label": "Gifting"}], "hardware_items": [], "families": [{"id": "keychain"}]}))
    monkeypatch.setenv("AAKAR_FAMILIES_FILE", str(bad))
    families.load_families_doc.cache_clear()
    try:
        with pytest.raises(families.FamilyConfigError):
            families.load_families_doc()
    finally:
        families.load_families_doc.cache_clear()
