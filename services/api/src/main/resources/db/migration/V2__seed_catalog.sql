-- The six Shop-board SKUs (PLAN §1.1, board 02 · Shop). Only the Jharokha phone stand has a template
-- end to end in Phase 0 (PLAN §18.2 step 5); the others are listed but not yet orderable.
INSERT INTO catalog_items (slug, name, category, description, template_id, default_params, default_material,
                           base_price_paise, specs_line, environment, available, media)
VALUES
  ('jharokha-phone-stand', 'Jharokha Phone Stand', 'desk_tech',
   'A cusped jharokha arch that holds your phone at a reading angle. Cable slot in the back, soft lip in front.',
   'jharokha_phone_stand',
   '{"width_mm": 92, "depth_mm": 78, "height_mm": 120, "tilt_deg": 70, "lip_height_mm": 12, "wall_mm": 3.2, "arch_cusps": 5}'::jsonb,
   'terracotta_silk', 49900, 'Fits phones to 6.9″ · 92 × 78 × 120 mm · 64 g', 'desk_oak', true, '[]'::jsonb),

  ('ajrakh-coasters', 'Ajrakh Coasters · set of 4', 'kitchen',
   'Four coasters carrying a debossed Ajrakh block-print lattice. Sold as a set; heat-safe finish.',
   'ajrakh_coaster_set',
   '{"diameter_mm": 100, "thickness_mm": 6, "count": 4, "pattern_depth_mm": 1.0}'::jsonb,
   'terracotta_matte', 64900, '100 mm · heat-safe to 90 °C · 28 g each', 'kitchen_marble', false, '[]'::jsonb),

  ('fluted-planter', 'Fluted Planter · drainage tray', 'home_decor',
   'A fluted planter with a matching drainage tray, sized for a 4-inch nursery pot.',
   'fluted_planter',
   '{"diameter_mm": 140, "height_mm": 130, "flutes": 24, "wall_mm": 2.4, "tray": true}'::jsonb,
   'terracotta_matte', 89900, '140 × 130 mm · fits a 4″ pot · drainage tray included', 'balcony_daylight', false, '[]'::jsonb),

  ('kantha-nameplate', 'The Iyers Nameplate · Kantha border', 'nameplates',
   'Raised lettering framed by a running Kantha stitch border. Screw or adhesive mount.',
   'kantha_nameplate',
   '{"width_mm": 300, "height_mm": 100, "thickness_mm": 8, "letter_height_mm": 32, "border_mm": 12}'::jsonb,
   'indigo_matte', 119900, '300 × 100 mm · raised letters · screw or adhesive mount', 'studio', false, '[]'::jsonb),

  ('elephant-bookends', 'Elephant Bookends', 'home_decor',
   'A pair of elephant bookends with a weighted cavity for sand or steel shot.',
   'elephant_bookends',
   '{"height_mm": 160, "depth_mm": 90, "pair": true, "cavity": true}'::jsonb,
   'sandalwood_silk', 134900, 'Pair · 160 mm tall · weighted cavity', 'teak_table_candlelight', false, '[]'::jsonb),

  ('pillar-headphone-stand', 'Headphone Stand · Pillar', 'desk_tech',
   'A single fluted pillar with a saddle for your headphones and room on the base for a smartwatch dock.',
   'pillar_headphone_stand',
   '{"height_mm": 260, "base_diameter_mm": 120, "saddle_width_mm": 70, "flutes": 16}'::jsonb,
   'indigo_matte', 57900, '260 mm tall · 120 mm base · fits over-ear headphones', 'desk_oak', false, '[]'::jsonb);
