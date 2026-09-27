# @aakar/design-tokens

Brand tokens and digital-material presets from the design boards (PLAN §1.2, §7.10, §8.4).

| File | Consumed by |
|---|---|
| `tokens.json` | Everything below; the source of truth |
| `materials.json` | Web viewer (PBR presets), API materials seed (`services/api` Flyway `V3__seed_materials.sql`), pricing policy placeholders (incl. `hardware_markup_pct`, `family_rules`; the active policy row is `V9`) |
| `families.json` | Outcome families (Avatars), Shop shelves and bought-in hardware: API seed (`services/api` Flyway `V9__families_hardware_uploads.sql`), storefront Create picker and portal Avatars page through the API |
| `experiences.json` | Experiences (Duniya) and viewer backdrops (Mahaul): API seed (`services/api` Flyway `V11__experiences_environments.sql`), storefront Shop persona and experience pages, portal Duniya and Backgrounds pages through the API |
| `css/tokens.css` | CSS variables, `data-surface="stage"` switches paper → indigo stage |
| `tailwind.preset.cjs` | `apps/web` Tailwind config |
| `src/index.ts` | Typed access, `formatPaise`, `formatPrintTime` |

The API seeds materials from a copy of `materials.json`; `services/api` has a test (`MaterialsSeedTest`) that fails if the two drift.

## Families (Avatars)

`families.json` is the catalogue of outcome categories (`docs/research/outcome-categories/implementation-plan.md`): six Shop
`shelves`, seven `hardware_items` (split rings, magnets, cords, LED bases… with placeholder costs in paise) and 25 `families`.
A family is the form a customer's idea takes — a `carrier` (Avatar) such as `keychain` · Saathi or `fridge_magnet` · Chumbak, one
of the parametric `object` families such as `phone_stand` · Sahara, or the single `raw` family `raw_print` · Swaroop. Each row
carries its brand copy (`codename`, `name`, `tagline`, `description` — data, edited in the portal), roadmap `tier`, `shelf`,
`default_template_id`, size envelope, default hardware, material rules, `shape_tolerance` and the content slot (Chhaap: which of
`emboss_text`, `motif`, `relief_image`, `hero_mesh` it accepts, on which anchors). Ids are snake_case English and never change;
`available` is the staff switch. The file validates against `packages/contracts/schemas/template-family.v1.json`
(`pnpm --filter @aakar/contracts validate`); the API seeds it in Flyway `V9` and `services/api` has a drift test
(`FamiliesSeedTest`) that fails when the SQL and the JSON diverge.

## Experiences (Duniya) and environments (Mahaul)

`experiences.json` holds the viewer backdrops and the themes built on them (`docs/research/outcome-categories/implementation-plan.md`
§7–§8). `environments` are the backdrops every `environment` field names (families, template descriptors, Shop items,
experiences): `id`, `label`, `surface` (stage · paper), `preset_key` — the storefront viewer preset that renders it, keyed like
`tokens.json` `environments` — and a `palette` of swatches (backdrop colour first). The six storefront backdrops mirror
`tokens.json`; `comic_rooftop_night` waits for its preset (Katha, PR 12). `experiences` are the
five seeded themes — Utsav · Festive & gifting, Adda · Desk & gaming, Yaadein · Memories & keepsakes, Masti · Kids & party and
Katha · Comics & heroes (unavailable until its backdrop lands) — each with brand copy (`codename`, `title`, `tagline`,
`description`), a URL `slug`, a backdrop, page theming (`surface.accent` from the brand colours; the cream paper stays, so
`paper_tint` is null; `hero_media` arrives when shot), a default `style`, a `motif_pack` (motif ids from `motifs/`, or a pack id
such as `comic_bursts`), ordered `avatars` (family ids), curated Shop `items`, `collections` (empty until a licence exists) and
`season` windows (ISO dates, or month-days such as `--10-01` that recur every year). Ids are snake_case English and never change;
`available` is the staff switch. The file validates against `packages/contracts/schemas/experience.v1.json`, and
`pnpm --filter @aakar/contracts validate` also checks that every experience names a known backdrop, families from
`families.json` and motifs (or motif tags) from `motifs/index.json`, and that every preset key is a `tokens.json` environment;
`comic_rooftop_night` and `comic_bursts` are listed there as pending until Katha's artwork lands; the API seeds it in Flyway `V11` and
`services/api` has a drift test (`ExperiencesSeedTest`).
