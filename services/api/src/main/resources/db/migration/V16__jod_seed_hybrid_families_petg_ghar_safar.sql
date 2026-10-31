-- Hybrid products (Jod, ADR-0015): everything the design tokens gained with the Kadi connectors, mirrored into the seed so
-- FamiliesSeedTest, MaterialsSeedTest and ExperiencesSeedTest agree with packages/design-tokens again. The hybrid families
-- are seeded switched OFF: the API does not yet resolve a base, price its base and assembly lines or run the assembling
-- stage (implementation-plan Phase 2), so a customer must not reach them until that lands; staff flip `available` in the
-- portal (or a later migration does) once Phase 2 is in and the fit coupons have passed on real prints.
-- Base items (Buniyaad), stock and fit tests still have no tables: they arrive with the Phase 2 catalog module.

INSERT INTO shelves (id, label, sort_order)
VALUES
  ('utility', 'Everyday utility', 35);

INSERT INTO hardware_items (sku, name, unit_cost_paise, weight_g, supplier, url, notes, available)
VALUES
  ('rail_petg_40', 'PETG dovetail rail 40 mm', 1200, 6, 'placeholder', NULL, 'Kadi-D male rail, bonded along the hook plate''s top edge with the VHB pad; the plaque slides onto it', true),
  ('vhb_pad_25x40', 'VHB foam pad 25 × 40 mm', 400, 1, 'placeholder', NULL, 'Bonds the dovetail rail to a steel plate; cure 24 h before loading', true),
  ('steel_disc_40', 'Steel disc 40 × 1 mm', 900, 10, 'placeholder', NULL, 'Kadi-M register: sits in the Chhaap''s underside pocket and holds to the magnetic dash mount', true),
  ('insert_brass_1420', 'Brass heat-set insert 1/4-20', 1500, 3, 'placeholder', NULL, 'Kadi-T fallback when a thread will not print at that size: pressed hot into a plain bore', true);

-- PETG Slate: the heat-safe, low-creep finish the rim-clip (Kadi-C) families require; the `compensation` block in the tokens is
-- read by the geometry service, not the API, so it has no column here.
INSERT INTO materials (id, name, filament, density_g_cm3, finish_class, rate_per_g_paise, heat_safe, pbr, sort_order)
VALUES
  ('petg_slate', 'PETG Slate', 'PETG, slate grey', 1.27, 'matte', 520, true,
   '{"color": "#4A5563", "roughness": 0.6, "metalness": 0.0, "clearcoat": 0.0, "clearcoat_roughness": 0.0, "sheen": 0.0, "sheen_color": "#FFFFFF"}'::jsonb, 7);

-- The connector, bases, assembly_minutes, pair_family_id and typical_print_minutes fields of a hybrid family live in the
-- tokens only until the Phase 2 catalog module gives them columns.
INSERT INTO template_families (id, codename, name, tagline, description, kind, tier, shelf, demand_rank, default_template_id, environment,
                               size_envelope, hardware, material_rules, shape_tolerance, content_slot, available, sort_order)
VALUES
  ('hook_plaque', 'Pehchaan Jod', 'Key-hook nameplate', 'Your name above the hooks',
   'A plaque that slides onto a steel four-hook plate through a Kadi-D dovetail rail. The plate is bought in and screwed to the wall; your name, a motif or a photo relief are printed on the plaque.',
   'hybrid', 'launch', 'nameplates', 9, 'hook_plaque', 'studio',
   '{"min_longest_mm": 160, "max_longest_mm": 260}'::jsonb,
   '[{"sku": "rail_petg_40", "qty": 1}, {"sku": "vhb_pad_25x40", "qty": 1}]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'strict',
   '{"accepts": ["emboss_text", "motif", "relief_image"], "anchors": ["face"], "hero_volume": false, "max_text_chars": 24}'::jsonb,
   false, 206),
  ('lamp_shade_e27', 'Deepak Jod', 'Lamp shade for an E27 base', 'A patterned shade on a ready lamp',
   'A jaali or lotus shade that clamps onto a bought-in E27 lamp base with the holder''s shade ring (a Kadi-T collar). The base, cord and LED bulb are bought in; only the shade is printed. LED bulbs up to 5 W.',
   'hybrid', 'launch', 'home_decor', 8, 'lamp_shade_e27', 'teak_table_candlelight',
   '{"min_longest_mm": 120, "max_longest_mm": 220}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'constrained',
   '{"accepts": ["motif", "relief_image", "emboss_text"], "anchors": ["band"], "hero_volume": false, "max_text_chars": 16}'::jsonb,
   false, 207),
  ('dash_idol', 'Safar Jod', 'Dashboard idol', 'Your form, steady on the dash',
   'A small plinth that holds to a magnetic dashboard mount through a steel disc set in its underside (Kadi-M). The mount is bought in; the plinth and your form are printed in a heat-safe finish.',
   'hybrid', 'next', 'desk_tech', 8, 'dash_idol', 'dashboard',
   '{"min_longest_mm": 40, "max_longest_mm": 80}'::jsonb,
   '[{"sku": "steel_disc_40", "qty": 1}]'::jsonb,
   '{"heat_safe_only": true, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'strict',
   '{"accepts": ["hero_mesh", "emboss_text", "motif"], "anchors": ["top", "face"], "hero_volume": true, "max_text_chars": 12}'::jsonb,
   false, 208),
  ('drain_lid', 'Saaf Jod', 'Soap box drain lid', 'A lid that drains',
   'A slotted lid that snaps into a bought-in acrylic soap box on a lip (Kadi-C). The box is bought in; the lid with your motif is printed in PETG so it shrugs off water.',
   'hybrid', 'launch', 'utility', 10, 'drain_lid', 'kitchen_marble',
   '{"min_longest_mm": 100, "max_longest_mm": 130}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": ["petg_slate"], "excluded_finish_classes": []}'::jsonb,
   'strict',
   '{"accepts": ["motif", "emboss_text"], "anchors": ["face"], "hero_volume": false, "max_text_chars": 12}'::jsonb,
   false, 209),
  ('bottle_cap_cover', 'Botal Jod', 'Bottle cap cover', 'Their name on their bottle',
   'A cover that snaps over the cap of a steel school bottle (a Kadi-C skirt) with a name and a motif on top. The bottle is the one they already own; only the cover is printed, in PETG.',
   'hybrid', 'launch', 'utility', 6, 'bottle_cap_cover', 'balcony_daylight',
   '{"min_longest_mm": 40, "max_longest_mm": 60}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": ["petg_slate"], "excluded_finish_classes": []}'::jsonb,
   'strict',
   '{"accepts": ["emboss_text", "motif", "relief_image"], "anchors": ["top"], "hero_volume": false, "max_text_chars": 12}'::jsonb,
   false, 211),
  ('planter_rim', 'Gamla Jod', 'Planter rim band', 'Dress the pot you have',
   'A C-shaped band that clips over the rim of a terracotta pot (Kadi-C jaws) and carries a motif and the plant''s name. The pot is yours; the band is printed in PETG for the balcony.',
   'hybrid', 'launch', 'home_decor', 8, 'planter_rim', 'balcony_daylight',
   '{"min_longest_mm": 80, "max_longest_mm": 140}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": ["petg_slate"], "excluded_finish_classes": []}'::jsonb,
   'strict',
   '{"accepts": ["motif", "emboss_text"], "anchors": ["band"], "hero_volume": false, "max_text_chars": 16}'::jsonb,
   false, 212);

-- The fully printed Pillar headphone stand (object kind, pillar_headphone_stand@1) is orderable now that its template builds.
UPDATE template_families SET available = true, updated_at = now() WHERE id = 'headphone_stand';

-- Sur Jod (V15) stays off; the row is restated so a token edit between V15 and V16 cannot drift.
UPDATE template_families SET codename = 'Sur Jod', name = 'Headphone stand topper', tagline = 'Your top on a ready-made stand', description = 'A saddle that plugs onto a steel or wooden headphone stand through a 12 mm Kadi socket. Your own form stands in front of it and your name runs along its face; the stand is bought in, only the topper is printed.', tier = 'launch', shelf = 'desk_tech', demand_rank = 3,
    default_template_id = 'headphone_topper', environment = 'desk_oak', size_envelope = '{"min_longest_mm": 60, "max_longest_mm": 100}'::jsonb, hardware = '[{"sku": "dowel_nylon_12x30", "qty": 1}]'::jsonb, material_rules = '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb, shape_tolerance = 'strict',
    content_slot = '{"accepts": ["hero_mesh", "emboss_text", "motif"], "anchors": ["front", "face"], "hero_volume": true, "max_text_chars": 16}'::jsonb, available = false, sort_order = 205, updated_at = now()
 WHERE id = 'headphone_topper';

-- Ghar and Safar, the two Duniyas built around the Jod; `promote` has no column yet (the storefront ranks hybrids first on its own).
INSERT INTO experiences (id, codename, slug, title, tagline, description, environment, surface, style, motif_pack, collections, season,
                         available, sort_order)
VALUES
  ('home_utility', 'Ghar', 'ghar', 'Home utility', 'Small fixes with your name on',
   'Things the house uses every day, done your way: a nameplate above the key hooks, a drain lid for the soap box, a band for the planter, a shade for the lamp. The base is bought in, the top is printed for you.',
   'kitchen_marble', '{"accent": "#7E9A7B", "paper_tint": null, "hero_media": null}'::jsonb, 'modern_zen',
   '["lotus", "jaali_lattice"]'::jsonb, '[]'::jsonb, '[]'::jsonb,
   true, 60),
  ('on_the_road', 'Safar', 'safar', 'On the road', 'For the dashboard and the bag',
   'A small idol that holds to a magnetic dash mount, a charm for the keys and a cover for the bottle that rides along: pieces for the journey, in heat-safe finishes.',
   'dashboard', '{"accent": "#5E8C61", "paper_tint": null, "hero_media": null}'::jsonb, 'cyber_desi',
   '["jaali_lattice", "paisley"]'::jsonb, '[]'::jsonb, '[]'::jsonb,
   true, 70);

-- Every Duniya now lists at least one Jod among its avatars, so the ordered lists are rewritten from the tokens.
DELETE FROM experience_avatars;
INSERT INTO experience_avatars (experience_id, family_id, sort_order)
VALUES
  ('festive', 'lithophane', 1),
  ('festive', 'lamp_shade_e27', 2),
  ('festive', 'ornament', 3),
  ('festive', 'nameplate', 4),
  ('festive', 'keychain', 5),
  ('festive', 'fridge_magnet', 6),
  ('desk_gaming', 'headphone_topper', 1),
  ('desk_gaming', 'hook_plaque', 2),
  ('desk_gaming', 'keycap', 3),
  ('desk_gaming', 'keychain', 4),
  ('desk_gaming', 'nameplate', 5),
  ('desk_gaming', 'phone_stand', 6),
  ('desk_gaming', 'desk_organizer', 7),
  ('desk_gaming', 'headphone_stand', 8),
  ('memories', 'lithophane', 1),
  ('memories', 'lamp_shade_e27', 2),
  ('memories', 'figurine_base', 3),
  ('memories', 'photo_frame', 4),
  ('memories', 'fridge_magnet', 5),
  ('kids_party', 'bottle_cap_cover', 1),
  ('kids_party', 'keychain', 2),
  ('kids_party', 'fridge_magnet', 3),
  ('kids_party', 'ornament', 4),
  ('kids_party', 'cake_topper', 5),
  ('comics', 'headphone_topper', 1),
  ('comics', 'keychain', 2),
  ('comics', 'keycap', 3),
  ('comics', 'figurine_base', 4),
  ('comics', 'nameplate', 5),
  ('comics', 'fridge_magnet', 6),
  ('comics', 'lithophane', 7),
  ('home_utility', 'hook_plaque', 1),
  ('home_utility', 'drain_lid', 2),
  ('home_utility', 'planter_rim', 3),
  ('home_utility', 'lamp_shade_e27', 4),
  ('home_utility', 'fridge_magnet', 5),
  ('home_utility', 'planter', 6),
  ('on_the_road', 'dash_idol', 1),
  ('on_the_road', 'bottle_cap_cover', 2),
  ('on_the_road', 'keychain', 3),
  ('on_the_road', 'car_accessory', 4);

INSERT INTO experience_items (experience_id, catalog_item_slug, sort_order)
VALUES
  ('home_utility', 'fluted-planter', 1);

-- ADR-0008: the new policy version copies the active policy (prices the owner published in the portal stay as they are) and adds
-- the Jod placeholders: base markup, labour rate, base packaging fee, weight-tiered shipping and a minimum (and assembly fee)
-- per hybrid family where the active policy has none. PricingPolicy ignores the fields it does not read yet (Phase 2 reads them).
-- On a fresh database the result equals materials.json -> pricing_policy.
INSERT INTO pricing_policies (id, version, active, policy, created_at, created_by)
SELECT gen_random_uuid(), '2026-11-jod', false,
       jsonb_set(p.policy || '{"base_markup_pct": 35, "labour_rate_paise_per_minute": 500, "base_packaging_fee_paise": 2500, "shipping_tiers": [{"max_g": 500, "paise": 7900}, {"max_g": 1000, "paise": 12900}, {"max_g": 2000, "paise": 17900}, {"max_g": 5000, "paise": 24900}]}'::jsonb, '{version}', '"2026-11-jod"'::jsonb)
         || jsonb_build_object('family_rules',
              COALESCE(p.policy -> 'family_rules', '{}'::jsonb) || (
                SELECT jsonb_object_agg(r.key, COALESCE(p.policy -> 'family_rules' -> r.key, r.value))
                  FROM jsonb_each('{"headphone_topper": {"minimum_subtotal_paise": 29900, "assembly_fee_paise": 4900}, "hook_plaque": {"minimum_subtotal_paise": 34900, "assembly_fee_paise": 5900}, "lamp_shade_e27": {"minimum_subtotal_paise": 49900, "assembly_fee_paise": 3900}, "dash_idol": {"minimum_subtotal_paise": 29900, "assembly_fee_paise": 2900}, "drain_lid": {"minimum_subtotal_paise": 19900, "assembly_fee_paise": 1900}, "bottle_cap_cover": {"minimum_subtotal_paise": 19900}, "planter_rim": {"minimum_subtotal_paise": 24900}}'::jsonb) r)),
       now(), 'seed'
  FROM pricing_policies p
 WHERE p.active;
UPDATE pricing_policies SET active = false
 WHERE active AND EXISTS (SELECT 1 FROM pricing_policies WHERE version = '2026-11-jod');
UPDATE pricing_policies SET active = true WHERE version = '2026-11-jod';
