-- Digital material → filament mapping, copied from packages/design-tokens/materials.json (PLAN §7.10).
-- MaterialsSeedTest fails when this drifts from the JSON.
INSERT INTO materials (id, name, filament, density_g_cm3, finish_class, rate_per_g_paise, heat_safe, pbr, sort_order)
VALUES
  ('basic_white', 'Basic White', 'Matte PLA, white', 1.24, 'matte', 380, false,
   '{"color": "#F4F1EA", "roughness": 0.55, "metalness": 0.0, "clearcoat": 0.0, "clearcoat_roughness": 0.0, "sheen": 0.0, "sheen_color": "#FFFFFF"}'::jsonb, 1),
  ('terracotta_matte', 'Terracotta Matte', 'Matte PLA, terracotta', 1.24, 'matte', 420, false,
   '{"color": "#B56E52", "roughness": 0.75, "metalness": 0.0, "clearcoat": 0.0, "clearcoat_roughness": 0.0, "sheen": 0.0, "sheen_color": "#FFFFFF"}'::jsonb, 2),
  ('terracotta_silk', 'Terracotta Silk', 'Silk PLA, copper-terracotta', 1.24, 'silk', 463, false,
   '{"color": "#C4785A", "roughness": 0.28, "metalness": 0.15, "clearcoat": 0.6, "clearcoat_roughness": 0.25, "sheen": 0.5, "sheen_color": "#E8B48F"}'::jsonb, 3),
  ('polished_brass', 'Polished Brass', 'Silk PLA, gold', 1.24, 'silk', 490, false,
   '{"color": "#C9A44C", "roughness": 0.22, "metalness": 0.55, "clearcoat": 0.5, "clearcoat_roughness": 0.2, "sheen": 0.3, "sheen_color": "#F1D48A"}'::jsonb, 4),
  ('sandalwood_silk', 'Sandalwood Silk', 'Silk PLA, beige (wood-fill PLA option)', 1.26, 'silk', 475, false,
   '{"color": "#D3B58B", "roughness": 0.30, "metalness": 0.10, "clearcoat": 0.5, "clearcoat_roughness": 0.3, "sheen": 0.4, "sheen_color": "#EBD8B8"}'::jsonb, 5),
  ('indigo_matte', 'Indigo Matte', 'Matte PLA, navy', 1.24, 'matte', 420, false,
   '{"color": "#34426B", "roughness": 0.78, "metalness": 0.0, "clearcoat": 0.0, "clearcoat_roughness": 0.0, "sheen": 0.0, "sheen_color": "#FFFFFF"}'::jsonb, 6);
