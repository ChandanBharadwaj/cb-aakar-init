-- ADR-0008: pricing policies are versioned data. The seed row copies aakar.pricing.* (application.yml),
-- itself copied from packages/design-tokens/materials.json → pricing_policy. Exactly one row is active.
CREATE TABLE pricing_policies (
    id          uuid         PRIMARY KEY,
    version     varchar(60)  NOT NULL UNIQUE,
    active      boolean      NOT NULL DEFAULT false,
    policy      jsonb        NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  varchar(120) NOT NULL
);
CREATE UNIQUE INDEX pricing_policies_active_uq ON pricing_policies (active) WHERE active;

INSERT INTO pricing_policies (id, version, active, policy, created_at, created_by)
VALUES (gen_random_uuid(), '2026-09-phase0', true, '{
  "version": "2026-09-phase0",
  "machine_rate_paise_per_hour": 20000,
  "finishing_fee_paise": {"matte": 8000, "silk": 12000},
  "packaging_fee_paise": 0,
  "margin_pct": 0,
  "round_to_rupees_ending_in": 9,
  "shipping_flat_paise": 7900,
  "free_shipping_above_paise": 99900,
  "shipping_label": "Shipping · Delhivery, 4 days"
}'::jsonb, now(), 'seed');
