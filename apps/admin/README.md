# @aakar/admin — the Aakar management portal

Staff-only Next.js 15 (App Router) · React 19 · TypeScript strict · Tailwind CSS 3.4 · zustand. Port **3100**.

The portal owns operational configuration and fulfilment ops (ADR-0012): the orders queue with stage advancement,
QC photos and print packs (printing is outsourced, ADR-0004), versioned pricing policies (ADR-0008) including the
hardware markup and per-Avatar rules, materials, catalog items and shelves, outcome families (**Avatars**: codename,
copy, tier, shelf, envelope, hardware bill of materials, material rules, content slot), **Duniya** experiences (the
Shop's themes: backdrop, accent, style, motif pack, ordered Avatars, curated items, collections, seasons,
availability), the read-only **Backgrounds** (Mahaul) reference, bought-in **hardware**, the content **review** queue
for flagged customer uploads, the **content rules** (the names the studio won't print: Katha's trademark guardrail), live
templates, the messages log of the mock sender (ADR-0013) and the audit trail. Brand words follow the naming convention in
`docs/research/outcome-categories/implementation-plan.md`: an Avatar is always shown as "Codename · Name"
(Saathi · Keychain & bag charm), a Duniya as "Codename · Title" (Utsav · Festive & gifting), and the content slot
(Chhaap) types are labelled Naam (`emboss_text`), Buti (`motif`), Chhavi (`relief_image`) and Roop (`hero_mesh`) with
the code alongside. It talks to the
**management API** described in `packages/contracts/openapi/aakar-admin.v1.yaml` (`/admin/api/*`, served by
`services/api` with its own staff authentication; customers never sign in here).

Same brand as the storefront: the paper surface (cream, jaali lattice, terracotta accent) from `@aakar/design-tokens`,
the Bloom mark, and the same card, chip and price-breakdown idioms. Copy is plain and operational.

## Run

```sh
pnpm install                          # at the repo root (pnpm workspace)
pnpm --filter @aakar/admin dev        # http://localhost:3100
pnpm --filter @aakar/admin build
pnpm --filter @aakar/admin start      # next start -p 3100
```

The `admin` module of `services/api` is built after this app, so a dependency-free Node stand-in implements the whole
contract with realistic in-memory data:

```sh
pnpm --filter @aakar/admin mock:api   # or: node scripts/mock-admin-api.mjs 8080
```

Sign in with the seeded **owner** account `studio@aakar.local` / `aakar-studio` (the sign-in page shows this hint in the
local profile). A second seeded account, `karigar@aakar.local` / `aakar-karigar`, has the **studio** role and shows how
the portal disables configuration writes for it. Both are mock-only; the production API refuses seeded accounts.

### Checks

```sh
pnpm --filter @aakar/admin typecheck  # tsc --noEmit
pnpm --filter @aakar/admin lint       # eslint (next/core-web-vitals + next/typescript, flat config)
pnpm --filter @aakar/admin gen:api    # regenerate src/lib/api/schema.d.ts from the contract (commit the result)
```

`gen:api` runs `openapi-typescript` on `aakar-admin.v1.yaml`. The contract's cross-file `$ref`s into
`aakar-api.v1.yaml` (`OrderStatus`, `Order`, `CatalogItem`, `Material.pbr`, …) and `schemas/price-breakdown.v1.json`
are resolved by the generator itself (it bundles with Redocly's core), so no separate bundling step is needed. CI
(`.github/workflows/web.yml`) regenerates the file and fails if it differs from the committed one.

## Environment variables

| Variable | Default | Used by |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` | Browser calls to the management API (inlined at build time) |
| `API_URL` | unset | Optional server-only override (the portal fetches from the browser, so this is rarely needed) |
| `NEXT_PUBLIC_AAKAR_PROFILE` | `local` | `local` shows mock-only hints (seeded credentials, mock-sender note); `production` hides them |

Copy `.env.example` to `.env.local` to change them. Fonts (Cormorant Garamond, Manrope) load at runtime from
`tokens.font.google_fonts_url`; the build never touches `fonts.googleapis.com`.

## Authentication and roles

- `POST /admin/api/auth/login` returns a staff bearer token, kept in `localStorage` under `aakar_staff_token` and sent
  as `Authorization: Bearer` on every call (`src/lib/api/client.ts`).
- Every page except `/signin` sits under `src/app/(portal)/layout.tsx`, whose `PortalShell` restores the session with
  `GET /admin/api/auth/me` and redirects to `/signin?next=…` when there is no token. Any **401** from the API clears the
  token and sends the browser back to sign-in.
- Roles come from the contract: **owner** (everything) and **studio** (fulfilment plus read-only configuration). The
  portal hides or disables writes for `studio` with an "Owner only" hint (`useCanWrite()` in `src/store/session.ts`);
  the API enforces the same rule with 403 `forbidden`.

## Page map

| Route | Renders | API |
|---|---|---|
| `/signin` | Email + password form; seeded-account hint in the local profile | `POST /admin/api/auth/login` |
| `/` | Dashboard: orders today, revenue today and this month, awaiting action; orders-by-status bars and queue shortcuts, all linking into the queue | `GET /admin/api/dashboard` |
| `/orders` | The queue: status-group pills (Awaiting payment · Queued · In production · Packed · Shipped · Delivered · On hold · Cancelled), search by number or phone, paginated table with customer, items/materials, stage pill, placed, ETA, total and inline **next action** buttons (`next_actions`; disruptive moves ask to confirm) | `GET /admin/api/orders`, `POST …/{id}/advance` |
| `/orders/[id]` | Header (number, status, customer stage, customer, placed, ETA), **Print pack** and **Packaging card** downloads (card enabled from packed), stage timeline, **Advance** panel (one button per allowed status, optional customer message, production detail form for slicing/printing/finishing/QC/reprint; 409 `invalid_transition` shown inline), items with 3MF/STL/GLB links, QC photos with multipart upload, events log; aside with customer, address, payment, shipment and this order's messages | `GET …/{id}`, `POST …/advance`, `GET …/print-pack`, `GET …/packaging-card.pdf`, `POST …/qc-photos` |
| `/pricing` | Active policy card (every field, incl. hardware markup and per-Avatar rules), versions table (newest first, active badge, note, who/when), **Publish new version** form prefilled from the active policy with finishing fees per finish class, **Hardware markup %** and a **Per-Avatar rules** table (Avatar, minimum subtotal ₹, setup fee ₹; serialised to `family_rules` keyed by family id, empty values omitted; older policies without the fields read as 0 / none), and a live **preview** of a sample piece (material, Avatar, extruded volume, print seconds; the Avatar adds `family_id` so hardware, setup and minimum show up). Owner-only publish | `GET /pricing/policies`, `POST /pricing/policies`, `POST /pricing/preview`, `GET /materials`, `GET /families` |
| `/materials` | Table (swatch from `pbr.color`, filament, density, finish class, ₹/g, heat safe, available, sort order, updated) with an edit drawer for every `AdminMaterialInput` field including the PBR numbers, and **Add material**. Owner-only writes | `GET/POST /materials`, `PUT /materials/{id}` |
| `/catalog` | Table (name, slug, shelf, Avatar, template and its live state, default material, base price, available switch, specs line) with an edit drawer for `CatalogItemInput` (shelf select from the API, Avatar select writing `family_id`, backdrop select from `GET /environments` with pending presets marked, default params as JSON, media as `kind URL` lines) and **Add item**. Owner-only writes; 422 `validation_failed` / `unknown_family` shown inline | `GET/POST /catalog/items`, `PUT /catalog/items/{slug}`, `GET /catalog/shelves`, `GET /families`, `GET /materials`, `GET /templates`, `GET /environments` |
| `/avatars` | Outcome families sorted by `sort_order`: "Codename · Name", kind and tier badges, shelf, Chhaap types accepted, **Ready** badge (live templates of the family in its title), **available** switch (owner; studio sees it read-only) and an edit drawer for every `AdminFamilyInput` field: naming and copy, kind, tier, shelf, demand rank, default template, environment, size envelope, hardware bill of materials (sku + qty rows), material rules (heat-safe only, allowed materials or any, excluded finish classes), shape tolerance, content slot (Naam/Buti/Chhavi/Roop checkboxes, anchors, hero volume, max text chars), sort order. The environment select comes from `GET /environments`. **New Avatar** creates; ids are fixed after that. 409 `family_exists`, 404 `unknown_family`, 422 `validation_failed` shown inline | `GET/POST /families`, `PUT /families/{id}`, `GET /catalog/shelves`, `GET /hardware`, `GET /materials`, `GET /templates`, `GET /environments` |
| `/experiences` | **Duniya · Experiences** sorted by `sort_order`: "Codename · Title" with its accent dot, id and `/duniya/<slug>`, tagline; the backdrop with its palette (and "Preset pending" when the storefront hasn't built it); style; season chips (the one in season today, in IST, marked); Avatars (count, and how many are open: available with a live template); curated items; an **available** switch (owner; studio sees it read-only) and an edit drawer for every `AdminExperienceInput` field: codename, slug (with the URL it makes), title, tagline, description; the backdrop as radio cards with palette swatches; the accent and paper tint as brand palette swatches (a hex set elsewhere shows as "custom" and stays), hero media; style (none, Jaipur heritage, Modern zen, Cyber desi, Warli line, Comic pop); the motif pack in order (library tiles to add, pack or motif ids by hand; ids the library doesn't know yet, like `comic_bursts`, stay as removable "Pending" chips); Avatars and curated items as ordered lists (drag, or ↑ ↓ ✕) with add selects showing which are open or coming soon; collections rows (id, title, licence reference); season windows (label, every year as day + month, which may wrap the new year, or once as two dates, with a live "in season today"); available; sort order. **New Duniya** creates; ids are fixed after that. 409 `experience_exists` / `slug_exists` (shown on the id / slug fields, and caught before sending when the list already has them), 404 `unknown_experience`, 422 `unknown_family` / `validation_failed` shown inline | `GET/POST /experiences`, `PUT /experiences/{id}`, `GET /environments`, `GET /families`, `GET /catalog/items`, `GET /motifs` |
| `/environments` | **Backgrounds · Mahaul**, read-only: each backdrop's label and id, surface, viewer preset key (**Built**, or **Preset pending** when the storefront has no preset with that key yet, so it shows as the studio; built keys are the design tokens' `environments`), palette swatches with their hex values, and which Duniya, Avatars and Shop items use it (linked to their pages) | `GET /environments`, `GET /experiences`, `GET /families`, `GET /catalog/items` |
| `/hardware` | Bought-in parts (sku, name, unit cost in ₹ stored as paise, weight, supplier link, notes, which Avatars use it, available switch) with an edit drawer and **Add hardware**. Owner-only writes; 409 `hardware_exists` shown inline | `GET/POST /hardware`, `PUT /hardware/{sku}`, `GET /families` |
| `/reviews` | Content review queue: flagged customer uploads as cards (image preview when the upload is an image with a URL, otherwise a model-file card with format and size), owner or guest, the scanner's reason, **Approve** / **Reject** with an optional note (a "standard note" button fills the Katha guardrail copy: "We can't print copyrighted heroes, but your own hero is welcome"); the list refreshes after a decision; filter chips switch to approved (`ready`) and `rejected` uploads. Studio and owner may decide | `GET /uploads?status=`, `POST /content-reviews/{id}` |
| `/content-rules` | **Content rules** (under Reviews in the sidebar): the names the studio won't print, Katha's trademark guardrail. A short explanation of what the rules do (an upload whose file name mentions an active term waits in Reviews with the term in the reason; a text (Naam) that mentions one is refused with the customer copy "We can't print copyrighted heroes or their names, but your own hero is welcome…", and customers never see the list; case, spaces, punctuation and accents never count; terms of six letters or fewer only match as whole words, so DC doesn't catch Adcock; switch off, never delete or rename). Kind filter chips with counts (All · Trademarks · Characters · Other) and a search by term or reason; a table of term, kind pill, how it matches (`normalised_term` and "Whole word only" / "Anywhere in a name"), reason, an **active** switch (owner; studio sees it read-only) and the last update; **Add term** and an edit drawer (term fixed once created, with a live "Matches as … · whole word only / anywhere" preview and a duplicate check against the list before sending; kind with hints; reason; active). 409 `content_term_exists`, 422 `validation_failed` and 403 shown inline | `GET/POST /content-terms`, `PUT /content-terms/{id}` |
| `/templates` | Cards per template with a live switch, its family (linked to the Avatar), the Chhaap types it supports (Naam/Buti/Chhavi/Roop chips, code in the title), the hardware it is cut for and the catalog items using it | `GET /templates`, `PUT /templates/{id}`, `GET /catalog/items`, `GET /families`, `GET /hardware` |
| `/messages` | Notifications log (when, channel, template, to, status, rendered text, order link), filter by `?order_id=`, with the mock-sender explanation | `GET /admin/api/notifications` |
| `/audit` | Paginated entries (when, who, action, target link) with a before/after diff toggle per row | `GET /admin/api/audit` |

## Component map

| Component | File | Notes |
|---|---|---|
| `BloomMark`, `BloomLoader` | `src/components/brand/` | Copied from `apps/web` |
| `Sidebar`, `TopBar`, `PortalShell` | `src/components/nav/` | Left navigation (collapses to a strip on small screens), staff name/role + sign-out, session guard |
| `ProblemCard` | `src/components/ui/ProblemCard.tsx` | RFC 9457 card; `compact` variant for inline 409/422/403 |
| `Tile`, `Stat`, `PriceBreakdown`, `Pill`/`StatusPill`, `Switch`, `Drawer`, `Field`, `Pagination`, `MockNotice`, `OwnerOnlyHint`, `EmptyState`, `Loading`, `PageHeader` | `src/components/ui/` | Shared building blocks; `Tile` follows the storefront's stability-card tiles |
| `Dashboard` | `src/components/dashboard/` | |
| `OrderQueue`, `OrderDetail`, `AdvancePanel`, `OrderTimeline`, `OrderItemsTable`, `QcPhotos`, `EventsLog` | `src/components/orders/` | |
| `PolicyCard`, `PublishPolicyForm`, `PricingPage` | `src/components/pricing/` | Form works in rupees, posts integer paise |
| `MaterialsPage`, `MaterialDrawer`, `Swatch` | `src/components/materials/` | |
| `CatalogPage`, `CatalogDrawer` | `src/components/catalog/` | Shelf and Avatar selects come from the API |
| `AvatarsPage`, `AvatarDrawer` | `src/components/avatars/` | Same list + drawer pattern as Materials; the drawer keeps blank-able numbers as strings and serialises on submit |
| `ExperiencesPage`, `ExperienceDrawer` | `src/components/experiences/` | The Duniya list and its drawer (ordered lists with drag and ↑ ↓ ✕, palette radios, motif pack chips, collection and season rows); owner-only writes |
| `EnvironmentsPage`, `PaletteSwatches`, `EnvironmentSelect`, `EnvironmentPicker` | `src/components/environments/` | The read-only Backgrounds page, and the backdrop swatches, select (Catalog and Avatar drawers) and radio-card picker (Duniya drawer) fed by `GET /admin/api/environments` |
| `HardwarePage`, `HardwareDrawer` | `src/components/hardware/` | Unit cost in rupees, posted as integer paise |
| `ReviewsPage` | `src/components/reviews/` | Cards with approve / reject; exports the `REJECTION_PRESET` copy; links to the content rules |
| `ContentRulesPage`, `ContentTermDrawer` | `src/components/content-rules/` | The content rules list (kind chips, search, active switch) with the "what the rules do" card, and the add/edit drawer; owner-only writes |
| `TemplatesPage`, `MessagesPage`, `AuditPage` | `src/components/{templates,messages,audit}/` | |

### Libraries and state

| File | What |
|---|---|
| `src/lib/api/schema.d.ts` | Generated by `openapi-typescript` from `aakar-admin.v1.yaml` (do not edit by hand) |
| `src/lib/api/types.ts` | Aliases over the generated types (`AdminOrder`, `PricingPolicyVersion`, `AdminMaterialInput`, `AdminFamily`, `AdminFamilyInput`, `AdminHardware`, `AdminUpload`, `AdminExperience`, `AdminExperienceInput`, `Environment`, `Motif`, `SeasonWindow`, `Shelf`, `FamilyRule`, `FeatureType`, `ContentTerm`, `ContentTermInput`, `ContentTermKind`, …) plus `itemAssets()` |
| `src/lib/api/client.ts` | `api.*` typed fetch wrapper with the bearer token (`catalog.shelves`, `families`, `experiences.list/create/update`, `environments.list`, `motifs.list`, `hardware`, `uploads`, `reviews`, `contentTerms.list/create/update` groups included), `ApiError`/`toProblem()`, binary `download()` + `saveBlob()`, multipart upload |
| `src/lib/experiences.ts` | Duniya vocabulary: `STYLES`, `experienceTitle()`, `experienceInput()` (row → PUT body), the brand palette as `ACCENTS` / `TINTS` (from the design tokens), `packEntry()` (motif, pack id or pending), season windows (`windowProblem()`, `formatWindow()`, `inSeason()` in IST, month-day helpers) |
| `src/lib/contentTerms.ts` | Content-rule vocabulary: `TERM_KINDS` (labels, plurals, hints), `KIND_TONE`, `REFUSAL_COPY` (what customers read), `WHOLE_WORD_MAX`, `normaliseTerm()` / `isWholeWord()` (a preview of the API's matcher; the API's `normalised_term` and `whole_word` win), `contentTermInput()` (row → PUT body), `sortTerms()` |
| `src/lib/environments.ts` | Backdrops from the API: `sortEnvironments()`, `environmentLabel(list, id)`, `presetBuilt()` (the storefront's preset keys are the design tokens' `environments`) — the hard-coded `ENVIRONMENTS` list is gone |
| `src/lib/orders.ts` | Status labels, tones, filter groups, action verbs, production statuses, timeline order |
| `src/lib/families.ts` | Avatar / Chhaap vocabulary: `FEATURE_TYPES` (Naam · Buti · Chhavi · Roop), kinds, tiers, shape tolerances, tones, `familyTitle()`, `familyInput()` |
| `src/lib/format.ts`, `src/lib/catalog.ts`, `src/lib/diff.ts`, `src/lib/profile.ts` | Dates in IST, rupees ⇄ paise, shelf labels from the API (no hard-coded categories), audit diff, local-profile flag |
| `src/lib/useQuery.ts` | Minimal client data hook (`data`, `problem`, `loading`, `reload`, `setData`) |
| `src/store/session.ts` | zustand store: token restore, sign-in, sign-out, `useCanWrite()` |

## Mock server (`scripts/mock-admin-api.mjs`)

Node, no dependencies, CORS enabled. Implements every path of `aakar-admin.v1.yaml`:

- Two staff accounts (above); every other route needs `Authorization: Bearer` (401 `unauthenticated` otherwise);
  configuration writes need the owner role (403 `forbidden`).
- 14 orders across every status (`pending_payment` … `delivered`, `on_hold`, `reprint`, `cancelled`) with events,
  addresses, payments, shipments, item assets (placeholder 3MF/STL/GLB served under `/mock-assets/…`) and notifications.
- `advance` applies the contract's transition rules (linear path; any active status ⇄ `on_hold`; `qc → reprint →
  printing`; anything before shipped → `cancelled`), answers 409 `invalid_transition` otherwise, appends an event,
  creates the shipment at `packed` (mock Delhivery AWB, 4-day ETA), adds carrier events at `shipped`/`delivered`, logs
  the customer message and writes an audit entry.
- `print-pack` returns a real (store-only) zip with `print-sheet.txt`, `model.3mf` and `model.stl` per item;
  `packaging-card.pdf` returns a one-page PDF from `packed` onwards (409 `order_not_packed` before).
- `qc-photos` parses the multipart body (413 above 10 MB) and serves the upload back so the photo grid shows it.
- Pricing: the seed policy from `packages/design-tokens/materials.json` (active, incl. `hardware_markup_pct` and
  `family_rules`) plus one older version; `preview` and the seeded prices use the PLAN §7.10 formula (material +
  machine time + finishing [+ packaging] [+ hardware: default BOM × unit cost × markup] [+ setup], margin, rounding to a
  rupee ending in N, the family minimum, flat or free shipping) and reproduce the Checkout board's ₹1,249. `preview`
  accepts `family_id` (422 `unknown_family` otherwise); published policies keep the new fields.
- Shelves, hardware items and outcome families from `packages/design-tokens/families.json`: `GET /catalog/shelves`,
  `GET/POST/PUT /families` (409 `family_exists`, 404 `unknown_family`, 422 for unknown shelves or hardware skus;
  `ready` and `template_ids` computed from the template list), `GET/POST/PUT /hardware` (409 `hardware_exists`).
  Mutations are in memory; writes are owner-only and audited as `family.*` / `hardware.*`. Catalog `category` must be a
  shelf id and `family_id` a known family (422 `unknown_family`).
- Duniya and backdrops from `packages/design-tokens/experiences.json`, motifs from `packages/design-tokens/motifs/`:
  `GET/POST/PUT /experiences` (in memory; owner-only, audited as `experience.create` / `experience.update`; 409
  `experience_exists` then `slug_exists`, 404 `unknown_experience`, 422 `unknown_family` for an avatar that is not a
  family and `validation_failed` for a schema violation, an unknown backdrop (the detail names the known ones), an
  unknown Shop item slug, a repeated avatar, item, motif or collection, or a season window that mixes a date and a
  month-day, runs backwards or names a day that doesn't exist; the id in the path wins on PUT), `GET /environments`
  (the seed's seven backdrops, `comic_rooftop_night` included), `GET /motifs` (the library with `svg_url`) and the
  artwork itself at `GET /api/motifs/{id}.svg` (no token needed). Catalog items and families are validated against the
  same backdrop list. The catalog's headphone stand uses the API seed's slug, `pillar-headphone-stand`, so Adda's
  curated items resolve.
- Uploads and reviews: five customer uploads (a pending image with an inline SVG placeholder held for "Batman", a pending
  STL held for "Iron Man", each with the API's reason wording `File name mentions "Iron Man", a protected character · …`,
  one approved, one rejected, one clean). `GET /uploads?status=` filters, `POST /content-reviews/{id}` approves or rejects (studio or owner; 409
  `review_already_decided` on a second decision) and is audited as `review.decide`.
- Content rules: the API's 24-term seed (`V13__content_terms.sql`: 4 trademarks, 20 characters) with the same
  `normalised_term` and `whole_word`; `GET/POST /content-terms` and `PUT /content-terms/{id}` in memory (owner-only writes,
  audited as `content_term.create` / `content_term.update` with the term as target; 409 `content_term_exists` for another
  spelling of an existing term, 422 `validation_failed` for a rename, a bad kind or a term without two letters or digits, 404).
- Six materials, seven catalog items (two available), three templates (`jharokha_phone_stand`, `keychain_tag`,
  `raw_print`, each with `features_supported` and `hardware`), a messages log and an audit log seeded with
  configuration changes.

## Notes and deviations

- The portal fetches everything from the browser (the token lives in `localStorage`), so pages are client-rendered
  inside a server-rendered shell; nothing is prerendered with data.
- `next_actions` is optional on `AdminOrderSummary` in the contract; the queue treats a missing list as "no actions".
- `PUT /admin/api/templates/{id}` returns `200` with no schema; the portal updates its local copy and does not read the body.
- The template rows of `GET /admin/api/templates` list only `id`, `version`, `family`, `name`, `live` and
  `catalog_items` in the contract; `features_supported` and `hardware` from the descriptor are open properties, so
  `AdminTemplate` in `types.ts` adds them as optional and the Templates page shows chips only when the API sends them.
- `PricingPolicy.hardware_markup_pct` and `family_rules` are optional: the publish form omits them when 0 / empty so a
  policy without Avatar rules serialises exactly as before.
- The seeded second (studio-role) account is a mock-server convenience for exercising role gating; the contract does not
  name any accounts.
- `Dockerfile`: none, matching `apps/web`, which has no Dockerfile either.
