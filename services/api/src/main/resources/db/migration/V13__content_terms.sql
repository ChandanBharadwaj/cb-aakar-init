-- Content rules: the trademark guardrail behind Katha (Comics & heroes), docs/research/outcome-categories/implementation-plan.md
-- §8 and open decision 16. Marvel, DC and their characters are licensed IP, so Katha prints the customer's own hero, never
-- theirs. A term here holds an upload whose file name mentions it in the review queue, and refuses a design whose text (Naam)
-- mentions it (422 protected_term; customers never see the list). Staff edit the list in the portal (Content rules): any
-- staff role reads, the owner adds terms and changes their kind, reason and active switch (audited content_term.*); a term is
-- never renamed or deleted, it is switched off.
--
-- normalised_term is the term as the matcher compares it: folded (accents, full-width letters), lowercase, letters and digits
-- only, so "Spider-Man", "spider man" and "SPIDERMAN" are one rule (the unique index). The API computes it (Unicode letters and
-- digits, which PostgreSQL's locale-dependent classes cannot reproduce); the seed below carries it literally. Terms of six
-- letters or digits or fewer (DC, Thor, Batman) only match as whole words of the text, so "DC" never catches "Adcock".
CREATE TABLE content_terms (
    id               uuid          PRIMARY KEY,
    term             varchar(80)   NOT NULL,
    normalised_term  varchar(80)   NOT NULL CHECK (normalised_term <> ''),
    kind             varchar(20)   NOT NULL CHECK (kind IN ('trademark', 'character', 'other')),
    reason           varchar(200),
    active           boolean       NOT NULL DEFAULT true,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX content_terms_normalised_term_uq ON content_terms (normalised_term);

-- A modest seed the owner edits from here: the publishers and brands, the best-known characters of both universes and the
-- Indian comic heroes customers ask for most.
INSERT INTO content_terms (id, term, normalised_term, kind, reason)
VALUES
  (gen_random_uuid(), 'Marvel', 'marvel', 'trademark', 'Marvel Comics brand (Disney)'),
  (gen_random_uuid(), 'Marvel Studios', 'marvelstudios', 'trademark', 'Marvel Studios brand (Disney)'),
  (gen_random_uuid(), 'DC', 'dc', 'trademark', 'DC Comics brand (Warner Bros. Discovery)'),
  (gen_random_uuid(), 'DC Comics', 'dccomics', 'trademark', 'DC Comics brand (Warner Bros. Discovery)'),

  (gen_random_uuid(), 'Spider-Man', 'spiderman', 'character', 'Marvel character (Disney)'),
  (gen_random_uuid(), 'Iron Man', 'ironman', 'character', 'Marvel character (Disney)'),
  (gen_random_uuid(), 'Captain America', 'captainamerica', 'character', 'Marvel character (Disney)'),
  (gen_random_uuid(), 'Thor', 'thor', 'character', 'Marvel character (Disney)'),
  (gen_random_uuid(), 'Hulk', 'hulk', 'character', 'Marvel character (Disney)'),
  (gen_random_uuid(), 'Black Panther', 'blackpanther', 'character', 'Marvel character (Disney)'),
  (gen_random_uuid(), 'Wolverine', 'wolverine', 'character', 'Marvel character (Disney)'),
  (gen_random_uuid(), 'Deadpool', 'deadpool', 'character', 'Marvel character (Disney)'),

  (gen_random_uuid(), 'Batman', 'batman', 'character', 'DC character (Warner Bros. Discovery)'),
  (gen_random_uuid(), 'Superman', 'superman', 'character', 'DC character (Warner Bros. Discovery)'),
  (gen_random_uuid(), 'Wonder Woman', 'wonderwoman', 'character', 'DC character (Warner Bros. Discovery)'),
  (gen_random_uuid(), 'Joker', 'joker', 'character', 'DC character (Warner Bros. Discovery)'),
  (gen_random_uuid(), 'Harley Quinn', 'harleyquinn', 'character', 'DC character (Warner Bros. Discovery)'),
  (gen_random_uuid(), 'Aquaman', 'aquaman', 'character', 'DC character (Warner Bros. Discovery)'),
  (gen_random_uuid(), 'The Flash', 'theflash', 'character', 'DC character (Warner Bros. Discovery)'),
  (gen_random_uuid(), 'Green Lantern', 'greenlantern', 'character', 'DC character (Warner Bros. Discovery)'),

  (gen_random_uuid(), 'Chacha Chaudhary', 'chachachaudhary', 'character', 'Diamond Comics character'),
  (gen_random_uuid(), 'Nagraj', 'nagraj', 'character', 'Raj Comics character'),
  (gen_random_uuid(), 'Super Commando Dhruv', 'supercommandodhruv', 'character', 'Raj Comics character'),
  (gen_random_uuid(), 'Doga', 'doga', 'character', 'Raj Comics character');
