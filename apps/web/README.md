# @aakar/web — the Aakar storefront

Next.js 15 (App Router) · React 19 · TypeScript strict · Tailwind CSS 3.4 · React Three Fiber · zustand.

Phase 0 scope (PLAN §4, §8): Home, Shop, item detail, the 3D design studio with finishes, size sliders, live price and
stability, plus placeholder pages so every nav link resolves. Cart, checkout, orders, AR and Create-from-prompt are
deliberately stubbed ("arrives in Phase 1/2").

## Run

```sh
pnpm install                       # at the repo root (pnpm workspace)
pnpm --filter @aakar/web dev       # http://localhost:3000
pnpm --filter @aakar/web build
pnpm --filter @aakar/web start
```

The storefront talks to `services/api` (Spring Boot) on `http://localhost:8080`. Until that is running, a small
in-memory stand-in serves the same contract, including the SSE job stream and a 422 for out-of-range params:

```sh
pnpm --filter @aakar/web mock:api            # or: node scripts/mock-api.mjs 8080 [--fail]
```

Without any API the pages still render: Shop, item and Create pages show a "Couldn't reach the studio" card, the
studio shows the Problem Details it received.

### Checks

```sh
pnpm --filter @aakar/web typecheck   # tsc --noEmit
pnpm --filter @aakar/web lint        # eslint (next/core-web-vitals + next/typescript, flat config)
pnpm --filter @aakar/web gen:api     # regenerate src/lib/api/schema.d.ts from packages/contracts (commit the result)
```

## Environment variables

| Variable | Default | Used by |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` | Browser and server calls to the Aakar API (inlined at build time for the browser) |
| `API_URL` | unset | Optional server-only override, e.g. a docker-compose hostname |

Copy `.env.example` to `.env.local` to change them. Fonts (Cormorant Garamond, Manrope) are loaded at runtime from the
`tokens.font.google_fonts_url` in `@aakar/design-tokens`, so the build never needs `fonts.googleapis.com`.

## Page map

| Route | Surface | Renders | Data |
|---|---|---|---|
| `/` | paper | Nav, hero ("Things you imagine, made *real*."), prompt bar with example chips, three entry cards | — |
| `/shop` | paper | Category pills, SKU cards ("Add to Cart" disabled until Phase 1, "Modify with AI" → `/design/new?item=`); "Coming soon" ribbon for `available=false` | `GET /api/catalog/items` (streamed in a Suspense boundary) |
| `/shop/[slug]` | paper | Item detail; "Start with this piece" → `POST /api/designs {source:"shop"}` → `/design/{id}?job=` | `GET /api/catalog/items/{slug}` (404 → not-found page) |
| `/design/new` | stage | `?item=slug` or `?template=id` → `POST /api/designs` (source `shop` / `remix`) → redirect | — |
| `/design/[id]` | stage | **The studio**: karigar's note, param sliders + Sculpt, R3F viewer, finish chips, stats, stability card, price breakdown, version stepper, Mandala overlay while a job runs | `GET /api/designs/{id}`, `/versions`, `GET /api/templates/{id}`, `GET /api/catalog/materials`, `GET /api/versions/{v}/price?material=`, `POST /api/versions/{v}/params`, SSE `GET /api/jobs/{job}/events` |
| `/create` | stage | Prompt echo + "Describing a piece in words arrives in Phase 2" card, template list with Start → `/design/new?template=` | `GET /api/templates` |
| `/orders`, `/remix` | paper | "Arrives in Phase 1 / 2" cards | — |

`src/app/(paper)` and `src/app/(stage)` are route groups: the stage layout sets `data-surface="stage"`, which flips the
CSS variables from `@aakar/design-tokens/css/tokens.css` to the deep-indigo palette.

## Component map

| Component | File | Notes |
|---|---|---|
| `BloomMark` | `src/components/brand/BloomMark.tsx` | Logo; exact paths from `design/source/Aakar Logo.dc.html`, stroke weight steps up at small sizes |
| `BloomLoader` | `src/components/brand/BloomLoader.tsx` | Route/page loader: stroke-dash draw-on of the ring then the lifted petal |
| `MandalaSpinner` | `src/components/brand/MandalaSpinner.tsx` | Generation spinner (`animate-mandala`), stage copy from `tokens.stage_copy`, optional progress bar |
| `SiteNav` / `StageNav` | `src/components/nav/` | Paper header (Shop · Create · Remix · Orders · Cart · 0) and the compact stage header |
| `PromptBar` | `src/components/ui/PromptBar.tsx` | Submits to `/create?prompt=` |
| `SkuCard` | `src/components/ui/SkuCard.tsx` | Name, `formatPaise` price, `specs_line`, dual actions, "Coming soon" ribbon |
| `FinishChips` | `src/components/ui/FinishChips.tsx` | Radio group of finishes with swatches; arrow keys move, Space/Enter select |
| `ParamSliders` | `src/components/ui/ParamSliders.tsx` | Range inputs from the template descriptor (min/max/step/unit), toggles for booleans, selects for enums |
| `StageTimeline` | `src/components/ui/StageTimeline.tsx` | understanding → sculpting → checking → pricing → ready |
| `StabilityCard` | `src/components/ui/StabilityCard.tsx` | Tiles for `printability.checks` with pass / warn / fail / skipped styling |
| `PriceBreakdown` | `src/components/ui/PriceBreakdown.tsx` | Lines + shipping ("Free" when 0) + total |
| `ProblemCard`, `PhaseCard`, `Stat` | `src/components/ui/` | RFC 9457 error card, "arrives in Phase N" card, label/value row |
| `ShopGrid`, `StartWithPiece` | `src/components/shop/` | Client-side category filter; the POST-and-redirect button |
| `DesignStudio` | `src/components/design/DesignStudio.tsx` | The viewer page: loading, SSE, price refetch, sculpt, version stepping, layout |
| `useJobStream` | `src/components/design/useJobStream.ts` | `EventSource` on `event: stage`; closes on `ready`/`failed`; polls `GET /api/jobs/{id}` if the stream is closed early |
| `NewDesignRunner` | `src/components/design/NewDesignRunner.tsx` | `/design/new` logic |
| `DesignViewer` | `src/components/viewer/DesignViewer.tsx` | R3F `Canvas`, `OrbitControls` with limits (double-click resets), `ContactShadows`, ACES tone mapping |
| `Model` | `src/components/viewer/Model.tsx` | drei `useGLTF` (Draco-aware), fits the piece to the stage, swaps every mesh material for the finish |
| `StageEnvironment` | `src/components/viewer/StageEnvironment.tsx` | drei `Environment` preset from the template's environment, falls back to lights if the HDRI can't load |
| `PlaceholderForm` | `src/components/viewer/PlaceholderForm.tsx` | Lathe-turned stand-in while a GLB loads or when none exists |
| `ErrorBoundary` | `src/components/viewer/ErrorBoundary.tsx` | Works inside the Canvas and in the DOM |

### Libraries and state

| File | What |
|---|---|
| `src/lib/api/schema.d.ts` | Generated by `openapi-typescript` from `packages/contracts/openapi/aakar-api.v1.yaml` (do not edit by hand) |
| `src/lib/api/types.ts` | Friendly aliases (`CatalogItem`, `DesignVersion`, `TemplateDescriptor`, …) plus `glbUrl()`, `boundsMm()`, `isAvailable()` |
| `src/lib/api/client.ts` | `api.*` typed fetch wrapper; `ApiError` carries the Problem Details and `code`; `toProblem()` for rendering |
| `src/lib/viewer/materials.ts` | `physicalMaterialFrom(pbr)` → `MeshPhysicalMaterial` (color, roughness, metalness, clearcoat, sheen); fallback materials from the tokens package |
| `src/lib/viewer/environments.ts` | Template environment → drei preset (`desk_oak`→apartment, `teak_table_candlelight`→night, `studio`→studio, `dashboard`→warehouse, `kitchen_marble`→lobby, `balcony_daylight`→sunset) |
| `src/store/design.ts` | zustand store for the studio: design, versions, active version, materials, selected finish, price state, job progress, param draft |

## Notes and deviations

- `CatalogItem` has no `available` field in the contract; the UI reads an optional `available` and treats a missing
  value as available (`isAvailable()` in `src/lib/api/types.ts`).
- drei HDRI presets are fetched at runtime from the drei-assets CDN. When that host is unreachable the viewer keeps its
  key/fill lights and simply has no image-based reflections.
- `three-stdlib` types are reached through drei (`ComponentRef<typeof OrbitControls>`), so it is not a direct dependency.
- Reduced motion: `tokens.css` zeroes the motion durations; `globals.css` also stops the Bloom draw-on, the mandala spin
  and the fade-ins under `prefers-reduced-motion: reduce`.
