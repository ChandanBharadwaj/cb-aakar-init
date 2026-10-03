-- Roshni (lithophane), Pratima (figurine_base) and Katha (comics) open to customers: their templates build (PR 8), the
-- composer sends only the modes each anchor allows and asks for required content (PR 12b), the API refuses anything else at
-- design time, and Katha has its rooftop backdrop, comic style and motif pack (PR 12b) behind its content-term guardrail
-- (V13, PR 12a). Mirrors families.json and experiences.json; staff can switch any of them off again in the portal.
UPDATE template_families SET available = true, updated_at = now() WHERE id IN ('lithophane', 'figurine_base');
UPDATE experiences       SET available = true, updated_at = now() WHERE id = 'comics';

-- ADR-0008: a family that can be ordered has a minimum order, like every launch carrier. The new policy version copies the
-- active policy (so prices the owner published in the portal stay as they are) and adds a placeholder minimum for each of
-- the two keepsakes only where the active policy has none: Roshni ₹599 (the research's delivered floor for the night light
-- is about ₹570), Pratima ₹699 (about ₹420). On a fresh database the result equals materials.json → pricing_policy.
INSERT INTO pricing_policies (id, version, active, policy, created_at, created_by)
SELECT gen_random_uuid(), '2026-10-keepsakes', false,
       jsonb_set(
           jsonb_set(p.policy, '{version}', '"2026-10-keepsakes"'::jsonb),
           '{family_rules}',
           COALESCE(p.policy -> 'family_rules', '{}'::jsonb)
             || jsonb_build_object(
                  'lithophane',    COALESCE(p.policy -> 'family_rules' -> 'lithophane',    '{"minimum_subtotal_paise": 59900}'::jsonb),
                  'figurine_base', COALESCE(p.policy -> 'family_rules' -> 'figurine_base', '{"minimum_subtotal_paise": 69900}'::jsonb))),
       now(), 'seed'
  FROM pricing_policies p
 WHERE p.active;
UPDATE pricing_policies SET active = false
 WHERE active AND EXISTS (SELECT 1 FROM pricing_policies WHERE version = '2026-10-keepsakes');
UPDATE pricing_policies SET active = true WHERE version = '2026-10-keepsakes';
