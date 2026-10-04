-- Hybrid products (Jod, docs/research/hybrid-products, ADR-0015): a family may be a printed Chhaap that plugs into a
-- bought-in base through a standard Kadi connector. Phase 1 (proof of concept) adds the kind, the first hybrid family
-- (Sur Jod, the headphone stand topper, switched off until its fit coupons pass on real prints) and the nylon dowel it
-- packs as the Kadi-S adapter pin. Mirrors packages/design-tokens/families.json (FamiliesSeedTest). Base items, stock,
-- fit tests and the price lines arrive with Phase 2.
ALTER TABLE template_families DROP CONSTRAINT template_families_kind_check;
ALTER TABLE template_families ADD CONSTRAINT template_families_kind_check CHECK (kind IN ('carrier', 'object', 'raw', 'hybrid'));

INSERT INTO hardware_items (sku, name, unit_cost_paise, weight_g, supplier, url, notes, available)
VALUES ('dowel_nylon_12x30', 'Nylon dowel 12 × 30 mm', 800, 4, 'placeholder', NULL,
        'Kadi-S adapter pin: one end presses into the Chhaap''s socket, the other into the stand''s tube', true);

INSERT INTO template_families (id, codename, name, tagline, description, kind, tier, shelf, demand_rank, default_template_id, environment,
                               size_envelope, hardware, material_rules, shape_tolerance, content_slot, available, sort_order)
VALUES
  ('headphone_topper', 'Sur Jod', 'Headphone stand topper', 'Your top on a ready-made stand',
   'A saddle that plugs onto a steel or wooden headphone stand through a 12 mm Kadi socket. Your own form stands in front of it and your name runs along its face; the stand is bought in, only the topper is printed.',
   'hybrid', 'launch', 'desk_tech', 3, 'headphone_topper', 'desk_oak',
   '{"min_longest_mm": 60, "max_longest_mm": 100}'::jsonb,
   '[{"sku": "dowel_nylon_12x30", "qty": 1}]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'strict',
   '{"accepts": ["hero_mesh", "emboss_text", "motif"], "anchors": ["front", "face"], "hero_volume": true, "max_text_chars": 16}'::jsonb,
   false, 205);
