-- Outcome categories (docs/research/outcome-categories/implementation-plan.md §1, PLAN §7.3/§9): Shop shelves become a
-- table that catalog_items.category references; outcome families (Avatars: carriers, parametric object families and the
-- single raw-print family) and the bought-in hardware they pack are seeded from packages/design-tokens/families.json
-- (FamiliesSeedTest fails when this drifts from the JSON; rows are edited afterwards in the management portal). Designs
-- may start from an upload and name their family; versions carry the hardware packed with them. Customer uploads and
-- their content reviews get their tables now (the entities arrive with the upload path). A new pricing policy version
-- adds the hardware markup and per-family rules from materials.json → pricing_policy.

CREATE TABLE shelves (
    id          varchar(40)  PRIMARY KEY,
    label       varchar(60)  NOT NULL,
    sort_order  integer      NOT NULL DEFAULT 100
);

INSERT INTO shelves (id, label, sort_order)
VALUES
  ('home_decor', 'Home & decor', 10),
  ('nameplates', 'Nameplates', 20),
  ('kitchen', 'Kitchen', 30),
  ('desk_tech', 'Desk & tech', 40),
  ('gifting', 'Gifting', 50),
  ('keychains_charms', 'Keychains & charms', 60);

ALTER TABLE catalog_items ADD CONSTRAINT catalog_items_category_fk FOREIGN KEY (category) REFERENCES shelves (id);

-- Bought-in parts (split rings, magnets, cords, LED bases…): placeholder costs in paise until supplier quotes land.
CREATE TABLE hardware_items (
    sku              varchar(40)   PRIMARY KEY,
    name             varchar(120)  NOT NULL,
    unit_cost_paise  bigint        NOT NULL CHECK (unit_cost_paise >= 0),
    weight_g         numeric(7,2)  CHECK (weight_g >= 0),
    supplier         varchar(120),
    url              varchar(500),
    notes            varchar(200),
    available        boolean       NOT NULL DEFAULT true,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now()
);

INSERT INTO hardware_items (sku, name, unit_cost_paise, weight_g, supplier, url, notes, available)
VALUES
  ('split_ring_25', 'Steel split ring 25 mm', 300, 2, 'placeholder', NULL, NULL, true),
  ('magnet_d10x3', 'Neodymium disc magnet 10 × 3 mm', 1500, 2, 'placeholder', NULL, 'Keep away from small children; pocket 10.2 × 3.2 mm', true),
  ('cord_200', 'Cotton hanging cord 200 mm', 200, 1, 'placeholder', NULL, NULL, true),
  ('led_base_usb', 'USB LED puck base 70 mm', 18000, 60, 'placeholder', NULL, 'Warm white, 3 W; LED only, never incandescent', true),
  ('acrylic_4x6', 'Acrylic pane 4 × 6 in', 4000, 30, 'placeholder', NULL, NULL, true),
  ('nameplate_screws', 'Wall screws and anchors, pair', 600, 6, 'placeholder', NULL, NULL, true),
  ('adhesive_pads', 'Foam adhesive pads, pair', 300, 2, 'placeholder', NULL, NULL, true);

-- One row per outcome family; id equals `family` in design specs and template descriptors and never changes,
-- codename/name/tagline are brand copy. JSONB columns hold the template-family.v1.json objects verbatim.
CREATE TABLE template_families (
    id                   varchar(40)   PRIMARY KEY,
    codename             varchar(40)   NOT NULL,
    name                 varchar(80)   NOT NULL,
    tagline              varchar(120),
    description          text,
    kind                 varchar(10)   NOT NULL CHECK (kind IN ('carrier', 'object', 'raw')),
    tier                 varchar(10)   NOT NULL CHECK (tier IN ('launch', 'next', 'later')),
    shelf                varchar(40)   NOT NULL REFERENCES shelves (id),
    demand_rank          integer       CHECK (demand_rank >= 1),
    default_template_id  varchar(80)   NOT NULL,
    environment          varchar(40)   NOT NULL DEFAULT 'studio',
    size_envelope        jsonb,
    hardware             jsonb         NOT NULL DEFAULT '[]'::jsonb,
    material_rules       jsonb         NOT NULL DEFAULT '{}'::jsonb,
    shape_tolerance      varchar(12)   NOT NULL CHECK (shape_tolerance IN ('any', 'constrained', 'strict')),
    content_slot         jsonb         NOT NULL,
    available            boolean       NOT NULL DEFAULT false,
    sort_order           integer       NOT NULL DEFAULT 100,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_at           timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX template_families_shelf_idx ON template_families (shelf);

INSERT INTO template_families (id, codename, name, tagline, description, kind, tier, shelf, demand_rank, default_template_id, environment,
                               size_envelope, hardware, material_rules, shape_tolerance, content_slot, available, sort_order)
VALUES
  ('keychain', 'Saathi', 'Keychain & bag charm', 'Your idea, on your keys',
   'A palm-sized charm with a steel ring. Your photo, name or motif sits in relief on a sturdy plate; the ring loop is part of the print.',
   'carrier', 'launch', 'keychains_charms', 1, 'keychain_tag', 'studio',
   '{"min_longest_mm": 30, "max_longest_mm": 60}'::jsonb,
   '[{"sku": "split_ring_25", "qty": 1}]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'any',
   '{"accepts": ["relief_image", "emboss_text", "motif"], "anchors": ["face", "back"], "hero_volume": false, "max_text_chars": 16}'::jsonb,
   true, 10),

  ('fridge_magnet', 'Chumbak', 'Fridge magnet', 'Stick your idea to the fridge',
   'A flat plate with a hidden magnet pocket. Best in sets.',
   'carrier', 'launch', 'home_decor', 8, 'fridge_magnet', 'kitchen_marble',
   '{"min_longest_mm": 40, "max_longest_mm": 70}'::jsonb,
   '[{"sku": "magnet_d10x3", "qty": 1}]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'any',
   '{"accepts": ["relief_image", "emboss_text", "motif"], "anchors": ["face"], "hero_volume": false, "max_text_chars": 12}'::jsonb,
   true, 20),

  ('ornament', 'Jhoomar', 'Hanging ornament', 'Hang your idea up',
   'A two-sided disc or silhouette with a loop for cord or hook. Festive or year-round.',
   'carrier', 'launch', 'home_decor', 12, 'hanging_ornament', 'teak_table_candlelight',
   '{"min_longest_mm": 50, "max_longest_mm": 90}'::jsonb,
   '[{"sku": "cord_200", "qty": 1}]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'any',
   '{"accepts": ["relief_image", "emboss_text", "motif"], "anchors": ["face_front", "face_back"], "hero_volume": false, "max_text_chars": 12}'::jsonb,
   true, 30),

  ('nameplate', 'Pehchaan', 'Nameplate & plaque', 'Say who lives here',
   'Raised letters and a motif border on a plaque, for a desk or a door.',
   'carrier', 'launch', 'nameplates', 9, 'desk_nameplate', 'studio',
   '{"min_longest_mm": 120, "max_longest_mm": 300}'::jsonb,
   '[{"sku": "adhesive_pads", "qty": 1}]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'any',
   '{"accepts": ["emboss_text", "motif", "relief_image"], "anchors": ["face", "base_front"], "hero_volume": false, "max_text_chars": 24}'::jsonb,
   true, 40),

  ('lithophane', 'Roshni', 'Photo night light', 'A photo that glows',
   'Your photo becomes a translucent relief plate that comes alive on a warm LED base.',
   'carrier', 'launch', 'gifting', 4, 'lithophane_plate', 'teak_table_candlelight',
   '{"min_longest_mm": 100, "max_longest_mm": 150}'::jsonb,
   '[{"sku": "led_base_usb", "qty": 1}]'::jsonb,
   '{"heat_safe_only": false, "allowed": ["basic_white"], "excluded_finish_classes": []}'::jsonb,
   'strict',
   '{"accepts": ["relief_image"], "anchors": ["plate"], "hero_volume": false}'::jsonb,
   false, 50),

  ('figurine_base', 'Pratima', 'Figurine on a plinth', 'Your own form, standing tall',
   'Your 3D form, repaired and set on a plinth with a line of text.',
   'carrier', 'launch', 'gifting', 5, 'plinth_round', 'studio',
   '{"min_longest_mm": 50, "max_longest_mm": 200}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'any',
   '{"accepts": ["hero_mesh", "emboss_text"], "anchors": ["top", "base_front"], "hero_volume": true, "max_text_chars": 16}'::jsonb,
   false, 60),

  ('raw_print', 'Swaroop', 'Print as it is', 'Your own 3D model, printed',
   'Upload the file from your 3D program. We check it, size it and print it as it is.',
   'raw', 'launch', 'desk_tech', 15, 'raw_print', 'studio',
   '{"min_longest_mm": 20, "max_longest_mm": 240}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'any',
   '{"accepts": ["hero_mesh"], "anchors": ["body"], "hero_volume": true}'::jsonb,
   true, 999),

  ('photo_frame', 'Chaukhat', 'Photo frame', 'Frame the moment',
   'A 4 × 6 or 5 × 7 frame with your motif on the border and a line of text on the base.',
   'carrier', 'next', 'gifting', 8, 'photo_frame_std', 'teak_table_candlelight',
   '{"min_longest_mm": 120, "max_longest_mm": 220}'::jsonb,
   '[{"sku": "acrylic_4x6", "qty": 1}]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'constrained',
   '{"accepts": ["emboss_text", "motif"], "anchors": ["base_front", "border"], "hero_volume": false, "max_text_chars": 24}'::jsonb,
   false, 70),

  ('keycap', 'Kunji', 'Keycap', 'One key, yours',
   'A Cherry MX keycap with your emblem or letter on top.',
   'carrier', 'next', 'desk_tech', 11, 'keycap_mx', 'desk_oak',
   '{"min_longest_mm": 18, "max_longest_mm": 20}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'strict',
   '{"accepts": ["relief_image", "emboss_text"], "anchors": ["top"], "hero_volume": false, "max_text_chars": 3}'::jsonb,
   false, 80),

  ('pendant', 'Jhumka', 'Pendant & earrings', 'Wear your idea',
   'A small pendant or earring pair with a bail for a jump ring.',
   'carrier', 'next', 'gifting', 6, 'pendant_disc', 'studio',
   '{"min_longest_mm": 20, "max_longest_mm": 40}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'constrained',
   '{"accepts": ["relief_image", "emboss_text", "motif"], "anchors": ["face"], "hero_volume": false, "max_text_chars": 8}'::jsonb,
   false, 90),

  ('cake_topper', 'Mithai', 'Cake topper', 'Top the celebration',
   'A flat silhouette with two picks; only the picks touch the cake.',
   'carrier', 'next', 'kitchen', 14, 'cake_topper_flat', 'kitchen_marble',
   '{"min_longest_mm": 100, "max_longest_mm": 150}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'any',
   '{"accepts": ["emboss_text", "motif", "relief_image"], "anchors": ["face"], "hero_volume": false, "max_text_chars": 24}'::jsonb,
   false, 100),

  ('cookie_cutter', 'Saancha', 'Cookie cutter & stamp', 'Bake your idea',
   'A cutter outline from your silhouette, with an optional stamp plate.',
   'carrier', 'next', 'kitchen', 7, 'cookie_cutter', 'kitchen_marble',
   '{"min_longest_mm": 50, "max_longest_mm": 100}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'strict',
   '{"accepts": ["relief_image"], "anchors": ["outline"], "hero_volume": false}'::jsonb,
   false, 110),

  ('coaster', 'Aasan', 'Coaster set', 'Rest your cup on it',
   'A set of four heat-safe coasters with your motif or monogram.',
   'carrier', 'next', 'kitchen', 14, 'ajrakh_coaster_set', 'kitchen_marble',
   '{"min_longest_mm": 90, "max_longest_mm": 100}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": true, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'constrained',
   '{"accepts": ["motif", "emboss_text"], "anchors": ["face"], "hero_volume": false, "max_text_chars": 3}'::jsonb,
   false, 120),

  ('pin', 'Billa', 'Pin & badge', 'Pin your idea on',
   'A small badge with a butterfly clutch on the back.',
   'carrier', 'next', 'keychains_charms', 14, 'pin_badge', 'studio',
   '{"min_longest_mm": 20, "max_longest_mm": 40}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'any',
   '{"accepts": ["relief_image", "emboss_text", "motif"], "anchors": ["face"], "hero_volume": false, "max_text_chars": 8}'::jsonb,
   false, 130),

  ('bookmark', 'Nishaan', 'Bookmark', 'Mark your page',
   'A slim plate with your relief and a tassel hole.',
   'carrier', 'next', 'gifting', 14, 'bookmark_plate', 'studio',
   '{"min_longest_mm": 120, "max_longest_mm": 160}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'any',
   '{"accepts": ["relief_image", "emboss_text", "motif"], "anchors": ["face"], "hero_volume": false, "max_text_chars": 20}'::jsonb,
   false, 140),

  ('phone_stand', 'Sahara', 'Phone stand', 'A window for your phone',
   'A leaning stand with a cable slot; the back rest carries your text or motif.',
   'object', 'launch', 'desk_tech', 3, 'jharokha_phone_stand', 'desk_oak',
   '{"min_longest_mm": 80, "max_longest_mm": 160}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'strict',
   '{"accepts": ["emboss_text", "motif"], "anchors": ["side_left", "side_right", "back"], "hero_volume": false, "max_text_chars": 16}'::jsonb,
   true, 200),

  ('headphone_stand', 'Sur', 'Headphone stand', 'Rest your music',
   'A pillar or arch for over-ear headphones.',
   'object', 'next', 'desk_tech', 3, 'pillar_headphone_stand', 'desk_oak',
   '{"min_longest_mm": 200, "max_longest_mm": 300}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'strict',
   '{"accepts": ["emboss_text", "motif"], "anchors": ["base_front", "pillar"], "hero_volume": false, "max_text_chars": 16}'::jsonb,
   false, 210),

  ('planter', 'Gamla', 'Planter', 'Room to grow',
   'A fluted or faceted pot with a drainage tray.',
   'object', 'next', 'home_decor', 8, 'fluted_planter', 'balcony_daylight',
   '{"min_longest_mm": 80, "max_longest_mm": 160}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'constrained',
   '{"accepts": ["emboss_text", "motif"], "anchors": ["band", "rim"], "hero_volume": false, "max_text_chars": 16}'::jsonb,
   false, 220),

  ('bookend', 'Kitaab', 'Bookends', 'Hold the shelf together',
   'A pair of bookends with a weighted cavity.',
   'object', 'later', 'home_decor', 8, 'elephant_bookends', 'teak_table_candlelight',
   '{"min_longest_mm": 120, "max_longest_mm": 200}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'constrained',
   '{"accepts": ["emboss_text", "motif"], "anchors": ["face"], "hero_volume": false, "max_text_chars": 12}'::jsonb,
   false, 230),

  ('table_lamp', 'Deepak', 'Table lamp', 'Light with a pattern',
   'A lotus or jaali shade over a bought-in lamp module; LED only.',
   'object', 'later', 'home_decor', 8, 'lotus_lamp', 'teak_table_candlelight',
   '{"min_longest_mm": 150, "max_longest_mm": 250}'::jsonb,
   '[{"sku": "led_base_usb", "qty": 1}]'::jsonb,
   '{"heat_safe_only": true, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'constrained',
   '{"accepts": ["emboss_text", "motif"], "anchors": ["base_front", "shade_band"], "hero_volume": false, "max_text_chars": 16}'::jsonb,
   false, 240),

  ('diya_holder', 'Diya', 'Diya holder', 'For the festival of lights',
   'Single, five-wick or floating diya holders in a heat-safe material.',
   'object', 'later', 'gifting', 8, 'diya_holder', 'teak_table_candlelight',
   '{"min_longest_mm": 60, "max_longest_mm": 160}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": true, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'strict',
   '{"accepts": ["motif"], "anchors": ["rim"], "hero_volume": false}'::jsonb,
   false, 250),

  ('pooja_accessory', 'Aradhana', 'Pooja accessory', 'A place for the everyday ritual',
   'Modular shelf, bell hook or agarbatti stand. Respectful-content policy applies.',
   'object', 'later', 'home_decor', 8, 'pooja_shelf', 'teak_table_candlelight',
   '{"min_longest_mm": 80, "max_longest_mm": 250}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'strict',
   '{"accepts": ["motif", "emboss_text"], "anchors": ["face"], "hero_volume": false, "max_text_chars": 16}'::jsonb,
   false, 260),

  ('wall_hook', 'Khoonti', 'Wall hook', 'Hang it with style',
   'Paisley, lotus or arrow hooks with a shown load rating.',
   'object', 'later', 'home_decor', 8, 'paisley_hook', 'studio',
   '{"min_longest_mm": 60, "max_longest_mm": 120}'::jsonb,
   '[{"sku": "nameplate_screws", "qty": 1}]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'strict',
   '{"accepts": ["motif"], "anchors": ["face"], "hero_volume": false}'::jsonb,
   false, 270),

  ('desk_organizer', 'Tarteeb', 'Desk organizer', 'Everything in its place',
   'A tray with a phone slot or a pen cup on a modular grid.',
   'object', 'next', 'desk_tech', 3, 'desk_tray', 'desk_oak',
   '{"min_longest_mm": 100, "max_longest_mm": 250}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": false, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'strict',
   '{"accepts": ["emboss_text", "motif"], "anchors": ["front"], "hero_volume": false, "max_text_chars": 16}'::jsonb,
   false, 280),

  ('car_accessory', 'Safar', 'Car accessory', 'For the road',
   'Vent phone mount or dashboard tray; heat-safe materials only.',
   'object', 'later', 'desk_tech', 8, 'vent_mount', 'dashboard',
   '{"min_longest_mm": 60, "max_longest_mm": 200}'::jsonb,
   '[]'::jsonb,
   '{"heat_safe_only": true, "allowed": null, "excluded_finish_classes": []}'::jsonb,
   'strict',
   '{"accepts": ["motif"], "anchors": ["face"], "hero_volume": false}'::jsonb,
   false, 290);

-- Shop items name the family of their template; the six seeded SKUs are backfilled by template id.
ALTER TABLE catalog_items ADD COLUMN family_id varchar(40) REFERENCES template_families (id);
UPDATE catalog_items SET family_id = CASE template_id
    WHEN 'jharokha_phone_stand' THEN 'phone_stand'
    WHEN 'ajrakh_coaster_set' THEN 'coaster'
    WHEN 'fluted_planter' THEN 'planter'
    WHEN 'kantha_nameplate' THEN 'nameplate'
    WHEN 'elephant_bookends' THEN 'bookend'
    WHEN 'pillar_headphone_stand' THEN 'headphone_stand'
    END
WHERE template_id IN ('jharokha_phone_stand', 'ajrakh_coaster_set', 'fluted_planter', 'kantha_nameplate', 'elephant_bookends', 'pillar_headphone_stand');
CREATE INDEX catalog_items_family_idx ON catalog_items (family_id) WHERE family_id IS NOT NULL;

-- Designs may start from a customer's own model file (source upload, family raw_print) and remember their family;
-- a version carries the bought-in parts packed with it ([{sku, qty}], from the geometry result or the family default).
ALTER TABLE designs DROP CONSTRAINT designs_source_check;
ALTER TABLE designs ADD CONSTRAINT designs_source_check CHECK (source IN ('shop', 'create', 'remix', 'upload'));
ALTER TABLE designs ADD COLUMN family_id varchar(40) REFERENCES template_families (id);
ALTER TABLE design_versions ADD COLUMN hardware jsonb NOT NULL DEFAULT '[]'::jsonb;

-- Customer uploads (photos for relief, model files for raw prints and plinths), owned like designs and carts; flagged
-- content lands in content_reviews for staff. The API entities arrive with the upload path (plan §4).
CREATE TABLE uploads (
    id            uuid          PRIMARY KEY,
    owner_id      uuid,
    guest_id      uuid,
    kind          varchar(10)   NOT NULL CHECK (kind IN ('image', 'model')),
    format        varchar(8)    NOT NULL,
    storage_key   varchar(300)  NOT NULL,
    url           varchar(500),
    content_type  varchar(80)   NOT NULL,
    bytes         bigint        NOT NULL CHECK (bytes >= 0),
    sha256        char(64)      NOT NULL,
    origin        varchar(10)   NOT NULL DEFAULT 'upload' CHECK (origin IN ('upload', 'generated')),
    provider      varchar(40),
    status        varchar(20)   NOT NULL DEFAULT 'ready' CHECK (status IN ('ready', 'pending_review', 'rejected')),
    created_at    timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX uploads_owner_idx ON uploads (owner_id) WHERE owner_id IS NOT NULL;
CREATE INDEX uploads_guest_idx ON uploads (guest_id) WHERE guest_id IS NOT NULL;

CREATE TABLE content_reviews (
    id              uuid          PRIMARY KEY,
    upload_id       uuid          NOT NULL REFERENCES uploads (id) ON DELETE CASCADE,
    design_id       uuid,
    reason          varchar(200)  NOT NULL,
    status          varchar(10)   NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    decision_note   varchar(200),
    reviewer_email  varchar(254),
    created_at      timestamptz   NOT NULL DEFAULT now(),
    decided_at      timestamptz
);
CREATE INDEX content_reviews_upload_idx ON content_reviews (upload_id);
CREATE INDEX content_reviews_pending_idx ON content_reviews (created_at) WHERE status = 'pending';

-- ADR-0008: the policy that knows about hardware markup and per-family rules becomes the active version; the values
-- mirror aakar.pricing.* (application.yml) and materials.json → pricing_policy. Nothing already priced moves.
UPDATE pricing_policies SET active = false WHERE active;
INSERT INTO pricing_policies (id, version, active, policy, created_at, created_by)
VALUES (gen_random_uuid(), '2026-10-carriers', true, '{
  "version": "2026-10-carriers",
  "machine_rate_paise_per_hour": 20000,
  "finishing_fee_paise": {"matte": 8000, "silk": 12000},
  "packaging_fee_paise": 0,
  "margin_pct": 0,
  "round_to_rupees_ending_in": 9,
  "shipping_flat_paise": 7900,
  "free_shipping_above_paise": 99900,
  "shipping_label": "Shipping · Delhivery, 4 days",
  "hardware_markup_pct": 30,
  "family_rules": {
    "keychain": {"minimum_subtotal_paise": 24900},
    "fridge_magnet": {"minimum_subtotal_paise": 29900},
    "ornament": {"minimum_subtotal_paise": 34900},
    "nameplate": {"minimum_subtotal_paise": 59900},
    "raw_print": {"minimum_subtotal_paise": 34900, "setup_fee_paise": 9900}
  }
}'::jsonb, now(), 'seed');
