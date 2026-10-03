-- Keepsakes (PR 8): the longest-side ranges follow the templates that now exist, and the photo frame's copy promises
-- only the 4 × 6 in pane the studio stocks. Mirrors families.json. Each row changes only while it still holds the
-- value seeded by V9, so an edit made in the portal is never overwritten.

-- Saathi (keychain): the pet tag (pet_tag@1) is 25–35 mm on its longest side, so the family starts at 25 mm.
UPDATE template_families
   SET size_envelope = '{"min_longest_mm": 25, "max_longest_mm": 60}'::jsonb,
       updated_at    = now()
 WHERE id = 'keychain'
   AND size_envelope = '{"min_longest_mm": 30, "max_longest_mm": 60}'::jsonb;

-- Pratima (figurine_base): the narrowest plinth (plinth_round@1) is 40 mm across and is a piece of its own before a
-- form stands on it.
UPDATE template_families
   SET size_envelope = '{"min_longest_mm": 40, "max_longest_mm": 200}'::jsonb,
       updated_at    = now()
 WHERE id = 'figurine_base'
   AND size_envelope = '{"min_longest_mm": 50, "max_longest_mm": 200}'::jsonb;

-- Chaukhat (photo_frame): photo_frame_std@1 takes a 4 × 6 in photo behind the acrylic_4x6 pane; 5 × 7 waits for its
-- own pane, and names and motifs are cut in, never raised.
UPDATE template_families
   SET description = 'A 4 × 6 in photo frame with your motif or name cut into the border and a line of text on the base.',
       updated_at  = now()
 WHERE id = 'photo_frame'
   AND description = 'A 4 × 6 or 5 × 7 frame with your motif on the border and a line of text on the base.';
