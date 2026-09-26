# @aakar/design-tokens

Brand tokens and digital-material presets from the design boards (PLAN §1.2, §7.10, §8.4).

| File | Consumed by |
|---|---|
| `tokens.json` | Everything below; the source of truth |
| `materials.json` | Web viewer (PBR presets), API materials seed (`services/api` Flyway `V3__seed_materials.sql`), pricing policy placeholders |
| `css/tokens.css` | CSS variables, `data-surface="stage"` switches paper → indigo stage |
| `tailwind.preset.cjs` | `apps/web` Tailwind config |
| `src/index.ts` | Typed access, `formatPaise`, `formatPrintTime` |

The API seeds materials from a copy of `materials.json`; `services/api` has a test that fails if the two drift.
