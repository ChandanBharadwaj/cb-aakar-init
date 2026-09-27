-- Experiences (Duniya) and viewer environments (Mahaul), docs/research/outcome-categories/implementation-plan.md §7-§8: the
-- backdrops every `environment` field names become a reference table, and experiences curate them with a default style, a
-- motif pack, ordered Avatars (families) and curated Shop items. Seeded from packages/design-tokens/experiences.json
-- (ExperiencesSeedTest fails when this drifts from the JSON); experiences are edited afterwards in the management portal,
-- environments stay read-only reference data until backdrop presets become uploadable. Designs remember the experience
-- they started from, so the karigar's note and packaging card can name the theme.

-- One row per backdrop. preset_key names the storefront viewer preset that renders it (presets are code: adding a
-- backdrop is engineering plus a row here); palette holds the portal's swatches, the backdrop colour first.
CREATE TABLE environments (
    id          varchar(40)  PRIMARY KEY,
    label       varchar(80)  NOT NULL,
    surface     varchar(10)  NOT NULL DEFAULT 'stage' CHECK (surface IN ('stage', 'paper')),
    preset_key  varchar(40)  NOT NULL,
    palette     jsonb,
    sort_order  integer      NOT NULL DEFAULT 100
);

-- The six storefront backdrops (tokens.json environments) and Katha's comic rooftop, whose preset lands with PR 12.
INSERT INTO environments (id, label, surface, preset_key, palette, sort_order)
VALUES
  ('studio', 'Studio', 'stage', 'studio', '["#1B2238"]'::jsonb, 10),
  ('teak_table_candlelight', 'Chettinad teak · candlelight', 'stage', 'teak_table_candlelight', '["#1A1512"]'::jsonb, 20),
  ('desk_oak', 'Oak desk', 'stage', 'desk_oak', '["#2B241F"]'::jsonb, 30),
  ('dashboard', 'Car dashboard', 'stage', 'dashboard', '["#15171B"]'::jsonb, 40),
  ('kitchen_marble', 'Kitchen marble', 'stage', 'kitchen_marble', '["#3A3835"]'::jsonb, 50),
  ('balcony_daylight', 'Balcony · daylight', 'stage', 'balcony_daylight', '["#4B5C6B"]'::jsonb, 60),
  ('comic_rooftop_night', 'Comic rooftop · night', 'stage', 'comic_rooftop_night', '["#1B2238", "#34426B", "#D8AE5B"]'::jsonb, 70);

-- Every backdrop reference now points at the table. The seeded families and Shop items use the six storefront ids;
-- the portal checks new values against the table (422 validation_failed naming the known ids).
ALTER TABLE template_families ADD CONSTRAINT template_families_environment_fk FOREIGN KEY (environment) REFERENCES environments (id);
ALTER TABLE catalog_items ADD CONSTRAINT catalog_items_environment_fk FOREIGN KEY (environment) REFERENCES environments (id);

-- One row per experience; id is snake_case English and never renamed, codename/title/tagline are brand copy, slug is the
-- URL (/duniya/<slug>). JSONB columns hold the experience.v1.json objects verbatim: surface {accent, paper_tint,
-- hero_media}, motif_pack [motif or pack ids], collections [{id, title, licence_ref}], season [{starts_on, ends_on, label}].
CREATE TABLE experiences (
    id           varchar(40)   PRIMARY KEY,
    codename     varchar(40)   NOT NULL,
    slug         varchar(60)   NOT NULL,
    title        varchar(80)   NOT NULL,
    tagline      varchar(120),
    description  text,
    environment  varchar(40)   NOT NULL REFERENCES environments (id),
    surface      jsonb         NOT NULL,
    style        varchar(20)   NOT NULL DEFAULT 'none'
                               CHECK (style IN ('none', 'jaipur_heritage', 'modern_zen', 'cyber_desi', 'warli_line', 'comic_pop')),
    motif_pack   jsonb         NOT NULL DEFAULT '[]'::jsonb,
    collections  jsonb         NOT NULL DEFAULT '[]'::jsonb,
    season       jsonb         NOT NULL DEFAULT '[]'::jsonb,
    available    boolean       NOT NULL DEFAULT false,
    sort_order   integer       NOT NULL DEFAULT 100,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT experiences_slug_uq UNIQUE (slug)
);

-- The Avatars an experience shows and the Shop items it curates, each in display order (sort_order ascending).
CREATE TABLE experience_avatars (
    experience_id  varchar(40)  NOT NULL REFERENCES experiences (id) ON DELETE CASCADE,
    family_id      varchar(40)  NOT NULL REFERENCES template_families (id),
    sort_order     integer      NOT NULL,
    PRIMARY KEY (experience_id, family_id)
);

CREATE TABLE experience_items (
    experience_id      varchar(40)  NOT NULL REFERENCES experiences (id) ON DELETE CASCADE,
    catalog_item_slug  varchar(80)  NOT NULL REFERENCES catalog_items (slug),
    sort_order         integer      NOT NULL,
    PRIMARY KEY (experience_id, catalog_item_slug)
);

-- Utsav, Adda, Yaadein and Masti are live; Katha waits for its comic backdrop, style and motif pack (PR 12).
INSERT INTO experiences (id, codename, slug, title, tagline, description, environment, surface, style, motif_pack, collections, season,
                         available, sort_order)
VALUES
  ('festive', 'Utsav', 'utsav', 'Festive & gifting', 'Gifts that glow for every festival',
   'For Diwali, Christmas, Rakhi and Valentine''s: your photo, name or motif on a gift made for the occasion. A night light, an ornament, a nameplate, a keychain or a magnet, shown on a teak table by candlelight.',
   'teak_table_candlelight', '{"accent": "#D8AE5B", "paper_tint": null, "hero_media": null}'::jsonb, 'jaipur_heritage',
   '["star_rangoli", "lotus", "paisley"]'::jsonb, '[]'::jsonb,
   '[{"starts_on": "--10-01", "ends_on": "--11-30", "label": "Diwali"}, {"starts_on": "--12-01", "ends_on": "--12-31", "label": "Christmas"}, {"starts_on": "--08-01", "ends_on": "--08-31", "label": "Rakhi"}, {"starts_on": "--02-01", "ends_on": "--02-14", "label": "Valentine''s"}]'::jsonb,
   true, 10),

  ('desk_gaming', 'Adda', 'adda', 'Desk & gaming', 'Kit out your corner',
   'A keycap with your emblem, stands for your phone and headphones, a nameplate for the desk and a charm for your keys: pieces for the place where you work and play.',
   'desk_oak', '{"accent": "#34426B", "paper_tint": null, "hero_media": null}'::jsonb, 'cyber_desi',
   '["jaali_lattice"]'::jsonb, '[]'::jsonb,
   '[]'::jsonb,
   true, 20),

  ('memories', 'Yaadein', 'yaadein', 'Memories & keepsakes', 'Moments you can hold',
   'A photo that glows as a night light, a loved one''s form on a plinth, a frame for the day it happened and a magnet for the fridge: keepsakes made from your memories.',
   'studio', '{"accent": "#7E9A7B", "paper_tint": null, "hero_media": null}'::jsonb, 'modern_zen',
   '["lotus", "paisley"]'::jsonb, '[]'::jsonb,
   '[]'::jsonb,
   true, 30),

  ('kids_party', 'Masti', 'masti', 'Kids & party', 'Made for play and parties',
   'Name charms for school bags, magnets for the fridge gallery, ornaments for the party room and, soon, a topper for the cake: bright, sturdy pieces with their name on.',
   'balcony_daylight', '{"accent": "#B56E52", "paper_tint": null, "hero_media": null}'::jsonb, 'warli_line',
   '["warli_dancer", "star_rangoli"]'::jsonb, '[]'::jsonb,
   '[]'::jsonb,
   true, 40),

  ('comics', 'Katha', 'katha', 'Comics & heroes', 'Your hero, your story',
   'Bring your own hero into a comic-book world: a keychain, a keycap with their emblem, a figurine on a plinth, a nameplate, a magnet or a night light. Original heroes only; the hero and the story are yours.',
   'comic_rooftop_night', '{"accent": "#B24B3F", "paper_tint": null, "hero_media": null}'::jsonb, 'comic_pop',
   '["comic_bursts"]'::jsonb, '[]'::jsonb,
   '[]'::jsonb,
   false, 50);

INSERT INTO experience_avatars (experience_id, family_id, sort_order)
VALUES
  ('festive', 'lithophane', 1),
  ('festive', 'ornament', 2),
  ('festive', 'nameplate', 3),
  ('festive', 'keychain', 4),
  ('festive', 'fridge_magnet', 5),
  ('desk_gaming', 'keycap', 1),
  ('desk_gaming', 'keychain', 2),
  ('desk_gaming', 'nameplate', 3),
  ('desk_gaming', 'phone_stand', 4),
  ('desk_gaming', 'desk_organizer', 5),
  ('desk_gaming', 'headphone_stand', 6),
  ('memories', 'lithophane', 1),
  ('memories', 'figurine_base', 2),
  ('memories', 'photo_frame', 3),
  ('memories', 'fridge_magnet', 4),
  ('kids_party', 'keychain', 1),
  ('kids_party', 'fridge_magnet', 2),
  ('kids_party', 'ornament', 3),
  ('kids_party', 'cake_topper', 4),
  ('comics', 'keychain', 1),
  ('comics', 'keycap', 2),
  ('comics', 'figurine_base', 3),
  ('comics', 'nameplate', 4),
  ('comics', 'fridge_magnet', 5),
  ('comics', 'lithophane', 6);

INSERT INTO experience_items (experience_id, catalog_item_slug, sort_order)
VALUES
  ('festive', 'kantha-nameplate', 1),
  ('festive', 'ajrakh-coasters', 2),
  ('desk_gaming', 'jharokha-phone-stand', 1),
  ('desk_gaming', 'pillar-headphone-stand', 2);

-- A design may name the experience the customer started from.
ALTER TABLE designs ADD COLUMN experience_id varchar(40) REFERENCES experiences (id);
CREATE INDEX designs_experience_idx ON designs (experience_id) WHERE experience_id IS NOT NULL;
