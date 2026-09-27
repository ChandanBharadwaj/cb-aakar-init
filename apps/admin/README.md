# @aakar/admin — the Aakar management portal

Staff-only Next.js 15 (App Router) · React 19 · TypeScript strict · Tailwind CSS 3.4 · zustand. Port **3100**.

The portal owns operational configuration and fulfilment ops (ADR-0012): the orders queue with stage advancement,
QC photos and print packs (printing is outsourced, ADR-0004), versioned pricing policies (ADR-0008), materials,
catalog items, live templates, the messages log of the mock sender (ADR-0013) and the audit trail. It talks to the
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
| `/pricing` | Active policy card (every field), versions table (newest first, active badge, note, who/when), **Publish new version** form prefilled from the active policy with finishing fees per finish class and a live **preview** of a sample piece (material, extruded volume, print seconds). Owner-only publish | `GET /pricing/policies`, `POST /pricing/policies`, `POST /pricing/preview`, `GET /materials` |
| `/materials` | Table (swatch from `pbr.color`, filament, density, finish class, ₹/g, heat safe, available, sort order, updated) with an edit drawer for every `AdminMaterialInput` field including the PBR numbers, and **Add material**. Owner-only writes | `GET/POST /materials`, `PUT /materials/{id}` |
| `/catalog` | Table (name, slug, category, template and its live state, default material, base price, available switch, specs line) with an edit drawer for `CatalogItemInput` (default params as JSON, media as `kind URL` lines) and **Add item**. Owner-only writes | `GET/POST /catalog/items`, `PUT /catalog/items/{slug}`, `GET /materials`, `GET /templates` |
| `/templates` | Cards per template with a live switch and the catalog items using it | `GET /templates`, `PUT /templates/{id}`, `GET /catalog/items` |
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
| `CatalogPage`, `CatalogDrawer` | `src/components/catalog/` | |
| `TemplatesPage`, `MessagesPage`, `AuditPage` | `src/components/{templates,messages,audit}/` | |

### Libraries and state

| File | What |
|---|---|
| `src/lib/api/schema.d.ts` | Generated by `openapi-typescript` from `aakar-admin.v1.yaml` (do not edit by hand) |
| `src/lib/api/types.ts` | Aliases over the generated types (`AdminOrder`, `PricingPolicyVersion`, `AdminMaterialInput`, `Dashboard`, …) plus `itemAssets()` |
| `src/lib/api/client.ts` | `api.*` typed fetch wrapper with the bearer token, `ApiError`/`toProblem()`, binary `download()` + `saveBlob()`, multipart upload |
| `src/lib/orders.ts` | Status labels, tones, filter groups, action verbs, production statuses, timeline order |
| `src/lib/format.ts`, `src/lib/catalog.ts`, `src/lib/diff.ts`, `src/lib/profile.ts` | Dates in IST, rupees ⇄ paise, categories, audit diff, local-profile flag |
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
- Pricing: the seed policy from `packages/design-tokens/materials.json` (active) plus one older version; `preview`
  and the seeded prices use the PLAN §7.10 formula (material + machine time + finishing [+ packaging], margin, rounding
  to a rupee ending in N, flat or free shipping) and reproduce the Checkout board's ₹1,249.
- Six materials, six catalog items (one available), one template (`jharokha_phone_stand`), a messages log and an
  audit log seeded with configuration changes.

## Notes and deviations

- The portal fetches everything from the browser (the token lives in `localStorage`), so pages are client-rendered
  inside a server-rendered shell; nothing is prerendered with data.
- `next_actions` is optional on `AdminOrderSummary` in the contract; the queue treats a missing list as "no actions".
- `PUT /admin/api/templates/{id}` returns `200` with no schema; the portal updates its local copy and does not read the body.
- The seeded second (studio-role) account is a mock-server convenience for exercising role gating; the contract does not
  name any accounts.
- `Dockerfile`: none, matching `apps/web`, which has no Dockerfile either.
