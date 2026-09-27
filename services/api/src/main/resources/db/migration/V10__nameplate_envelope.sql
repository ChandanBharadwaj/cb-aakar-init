-- Pehchaan (nameplate): the longest side is capped at 250 mm, the bed the desk_nameplate template prints on
-- (its width parameter tops out at 250 and the printability check fails anything wider). Mirrors families.json.
UPDATE template_families
   SET size_envelope = '{"min_longest_mm": 120, "max_longest_mm": 250}'::jsonb,
       updated_at    = now()
 WHERE id = 'nameplate';
