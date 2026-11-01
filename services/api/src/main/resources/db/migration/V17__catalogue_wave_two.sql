-- The second catalogue wave: fourteen families that had a seed row and no template now have one each in the geometry service
-- (services/geometry/aakar_geometry/templates, accepted by tests/test_catalogue_templates.py), so they open to customers. Mirrors
-- packages/design-tokens (families.json, experiences.json, materials.json → pricing_policy); the drift tests fail if the two diverge.
-- Staff can switch any family off again in the portal. The hybrid (Jod) families stay off: the API still has no bases (Phase 2).

-- Hardware: the pin badge packs a tie-tack pin (its seed row packed nothing).
INSERT INTO hardware_items (sku, name, unit_cost_paise, weight_g, supplier, url, notes, available)
VALUES ('tie_tack_pin', 'Tie-tack pin with butterfly clutch', 200, 1, 'placeholder', NULL, 'Flat 8 mm pad glued into the badge''s back pocket; keep away from small children', true);
UPDATE template_families SET hardware = '[{"sku": "tie_tack_pin", "qty": 1}]'::jsonb, updated_at = now() WHERE id = 'pin';

-- The fourteen families open; their default templates are live in the geometry service.
UPDATE template_families SET available = true, updated_at = now()
 WHERE id IN ('pendant', 'cake_topper', 'cookie_cutter', 'coaster', 'pin', 'bookmark', 'planter', 'bookend', 'table_lamp', 'diya_holder', 'pooja_accessory', 'wall_hook', 'desk_organizer', 'car_accessory');

-- Duniya: the new Avatars join the worlds they belong to (experiences.json is the source; the list is replaced whole).
DELETE FROM experience_avatars;
INSERT INTO experience_avatars (experience_id, family_id, sort_order)
VALUES
  ('festive', 'lithophane', 1),
  ('festive', 'lamp_shade_e27', 2),
  ('festive', 'diya_holder', 3),
  ('festive', 'ornament', 4),
  ('festive', 'nameplate', 5),
  ('festive', 'pooja_accessory', 6),
  ('festive', 'keychain', 7),
  ('festive', 'fridge_magnet', 8),
  ('festive', 'pendant', 9),
  ('desk_gaming', 'headphone_topper', 1),
  ('desk_gaming', 'hook_plaque', 2),
  ('desk_gaming', 'keycap', 3),
  ('desk_gaming', 'keychain', 4),
  ('desk_gaming', 'nameplate', 5),
  ('desk_gaming', 'phone_stand', 6),
  ('desk_gaming', 'desk_organizer', 7),
  ('desk_gaming', 'headphone_stand', 8),
  ('desk_gaming', 'pin', 9),
  ('memories', 'lithophane', 1),
  ('memories', 'lamp_shade_e27', 2),
  ('memories', 'figurine_base', 3),
  ('memories', 'photo_frame', 4),
  ('memories', 'fridge_magnet', 5),
  ('memories', 'bookmark', 6),
  ('memories', 'pendant', 7),
  ('memories', 'pin', 8),
  ('kids_party', 'bottle_cap_cover', 1),
  ('kids_party', 'keychain', 2),
  ('kids_party', 'fridge_magnet', 3),
  ('kids_party', 'ornament', 4),
  ('kids_party', 'cake_topper', 5),
  ('kids_party', 'cookie_cutter', 6),
  ('kids_party', 'pin', 7),
  ('kids_party', 'bookmark', 8),
  ('comics', 'headphone_topper', 1),
  ('comics', 'keychain', 2),
  ('comics', 'keycap', 3),
  ('comics', 'figurine_base', 4),
  ('comics', 'nameplate', 5),
  ('comics', 'fridge_magnet', 6),
  ('comics', 'lithophane', 7),
  ('comics', 'pin', 8),
  ('comics', 'bookmark', 9),
  ('home_utility', 'hook_plaque', 1),
  ('home_utility', 'drain_lid', 2),
  ('home_utility', 'planter_rim', 3),
  ('home_utility', 'lamp_shade_e27', 4),
  ('home_utility', 'fridge_magnet', 5),
  ('home_utility', 'planter', 6),
  ('home_utility', 'wall_hook', 7),
  ('home_utility', 'bookend', 8),
  ('home_utility', 'coaster', 9),
  ('home_utility', 'table_lamp', 10),
  ('on_the_road', 'dash_idol', 1),
  ('on_the_road', 'bottle_cap_cover', 2),
  ('on_the_road', 'keychain', 3),
  ('on_the_road', 'car_accessory', 4),
  ('on_the_road', 'pin', 5);

-- Shop: the curated pieces name parameters their templates have (the V2 rows pre-dated the templates) and go on sale;
-- the Kantha nameplate is served by the desk nameplate template until a kantha_nameplate template exists.
UPDATE catalog_items SET template_id = 'ajrakh_coaster_set', default_params = '{"shape": "round", "edge": "notched", "size_mm": 100.0, "thickness_mm": 6.0, "border_mm": 6.0}'::jsonb, default_material = 'petg_slate', available = true, updated_at = now() WHERE slug = 'ajrakh-coasters';
UPDATE catalog_items SET template_id = 'fluted_planter', default_params = '{"width_mm": 140.0, "height_mm": 130.0, "facets": 12, "wall_mm": 2.4}'::jsonb, available = true, updated_at = now() WHERE slug = 'fluted-planter';
UPDATE catalog_items SET template_id = 'elephant_bookends', default_params = '{"side": "left", "height_mm": 160.0, "depth_mm": 100.0, "thickness_mm": 24.0}'::jsonb, available = true, updated_at = now() WHERE slug = 'elephant-bookends';
UPDATE catalog_items SET template_id = 'pillar_headphone_stand', default_params = '{"height_mm": 240.0, "foot_d_mm": 120.0, "saddle_width_mm": 70.0, "saddle_depth_mm": 40.0}'::jsonb, available = true, updated_at = now() WHERE slug = 'pillar-headphone-stand';
UPDATE catalog_items SET template_id = 'desk_nameplate', default_params = '{"width_mm": 240.0, "height_mm": 80.0, "tilt_deg": 70.0, "base_depth_mm": 50.0, "thickness_mm": 6.0}'::jsonb, available = true, updated_at = now() WHERE slug = 'kantha-nameplate';

-- ADR-0008: the new policy version copies the active policy (prices the owner published in the portal stay as they are) and adds a
-- placeholder minimum per newly orderable family where the active policy has none. On a fresh database the result equals
-- materials.json -> pricing_policy.
INSERT INTO pricing_policies (id, version, active, policy, created_at, created_by)
SELECT gen_random_uuid(), '2026-11-catalogue', false,
       jsonb_set(p.policy, '{version}', '"2026-11-catalogue"'::jsonb)
         || jsonb_build_object('family_rules',
              COALESCE(p.policy -> 'family_rules', '{}'::jsonb) || (
                SELECT jsonb_object_agg(r.key, COALESCE(p.policy -> 'family_rules' -> r.key, r.value))
                  FROM jsonb_each('{"pendant": {"minimum_subtotal_paise": 19900}, "cake_topper": {"minimum_subtotal_paise": 29900}, "cookie_cutter": {"minimum_subtotal_paise": 24900}, "coaster": {"minimum_subtotal_paise": 29900}, "pin": {"minimum_subtotal_paise": 19900}, "bookmark": {"minimum_subtotal_paise": 19900}, "planter": {"minimum_subtotal_paise": 69900}, "bookend": {"minimum_subtotal_paise": 89900}, "table_lamp": {"minimum_subtotal_paise": 99900}, "diya_holder": {"minimum_subtotal_paise": 39900}, "pooja_accessory": {"minimum_subtotal_paise": 59900}, "wall_hook": {"minimum_subtotal_paise": 34900}, "desk_organizer": {"minimum_subtotal_paise": 59900}, "car_accessory": {"minimum_subtotal_paise": 39900}}'::jsonb) r)),
       now(), 'seed'
  FROM pricing_policies p
 WHERE p.active;
UPDATE pricing_policies SET active = false
 WHERE active AND EXISTS (SELECT 1 FROM pricing_policies WHERE version = '2026-11-catalogue');
UPDATE pricing_policies SET active = true WHERE version = '2026-11-catalogue';
