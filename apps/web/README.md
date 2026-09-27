# @aakar/web — the Aakar storefront

Next.js 15 (App Router) · React 19 · TypeScript strict · Tailwind CSS 3.4 · React Three Fiber · zustand.

Phase 0 (PLAN §4, §8): Home, Shop, item detail, the 3D design studio with finishes, size sliders, live price and
stability. Phase 1 (ADR-0013) completes the customer loop: phone OTP sign-in with a mock code, cart, checkout
(Review · Stability · Pay · Track), a clearly labelled mock payment gateway page, orders and live order tracking over
SSE. Outcome categories (`docs/research/outcome-categories/implementation-plan.md`, PR 6): Create opens with the
Avatar picker ("Give your idea an Avatar"), each Avatar has a composer for its Chhaap (Naam · text, Chhavi · photo
relief, Roop · your own 3D form; Buti · motifs arrive later), Swaroop prints a customer's own model file as it is, and
the studio edits the Chhaap beside the sliders. Shop shelves come from the API. AR and Create-from-prompt are still
stubbed ("arrives in Phase 2").

Copy rules: "Craft, not CAD" — never mesh, vertex or STL in customer copy (the file-extension list is the one
exception; the noun is "model file"); a codename is always paired with its plain descriptor ("Saathi · Keychain &
bag charm", "Naam · Name or text"); codenames, names and taglines are data from the API, never hard-coded.

## Run

```sh
pnpm install                       # at the repo root (pnpm workspace)
pnpm --filter @aakar/web dev       # http://localhost:3000
pnpm --filter @aakar/web build
pnpm --filter @aakar/web start
```

The storefront talks to `services/api` (Spring Boot) on `http://localhost:8080`. Until that is running, a small
in-memory stand-in serves the same contract: designs, jobs and the SSE job stream, a 422 for out-of-range params,
the whole Phase 1 loop (OTP with dev code `123456`, guest → user attach, addresses, cart, serviceability with
`9xxxxx` pincodes not serviceable, checkout, a mock payment gateway, orders that advance a stage every ~8 s, and the
order SSE stream), and the outcome-category endpoints: `GET /api/catalog/shelves` and `GET /api/families[/{id}]`
read `packages/design-tokens/families.json` at startup (`ready` when the family's `default_template_id` is one of the
mock's descriptors: Jharokha, `keychain_tag@1`, `raw_print@1`), `POST /api/uploads` parses the multipart body, keeps
the bytes in memory and serves them back under `/media/uploads/{id}.{ext}` (`GET /api/uploads/{id}` for the record),
`POST /api/designs` takes `family_id` / `features` / `source: upload` and validates features against the descriptor
(anchor, `accepts`, `max_relief_mm`, upload state) before echoing them into the version `spec`, `POST
/api/versions/{id}/params` replaces `features` when present, and prices carry `hardware` and `setup` lines, a
`family_id` and `minimum_subtotal_paise` when the family minimum lifted the subtotal. Dev affordances: a file named
`*review*` answers `pending_review`, a model file named `*broken*` fails its job with `content_unusable`, and a raw
print under 30 mm completes with `printability.passed=false`.

```sh
pnpm --filter @aakar/web mock:api            # or: node scripts/mock-api.mjs 8080 [--fail]
AAKAR_ORDER_STAGE_MS=2000 AAKAR_WEB_URL=http://localhost:3000 node scripts/mock-api.mjs   # faster stages, pay_url base
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
| `/shop` | paper | Shelf pills from the API ("All pieces" first), SKU cards ("Add to Cart", "Modify with AI" → `/design/new?item=`, "Make it yours · add a photo or your name" → `/create/{family}?item=` when the item's template takes a Chhaap); "Coming soon" ribbon for `available=false`; the `raw_print` family is never listed | `GET /api/catalog/items`, `GET /api/catalog/shelves`, `GET /api/templates` (streamed in a Suspense boundary; shelves and templates are best-effort) |
| `/shop/[slug]` | paper | Item detail with the shelf label from the API and the "Make it yours" link; "Start with this piece" → `POST /api/designs {source:"shop"}` → `/design/{id}?job=` | `GET /api/catalog/items/{slug}` (404 → not-found page), `GET /api/catalog/shelves`, `GET /api/templates/{id}` |
| `/design/new` | stage | `?item=slug` or `?template=id` → `POST /api/designs` (source `shop` / `remix`) → redirect | — |
| `/design/[id]` | stage | **The studio**: eyebrow "Codename · Name" once the family is known, karigar's note, param sliders, "Your Chhaap" (`ContentSlotPanel`) when the template has anchors that take content, one Sculpt for sliders + Chhaap, R3F viewer, finish chips, stats incl. "Comes with" hardware, stability card (+ "The studio needs walls of at least 1.2 mm here; try a larger size" for a failed raw print), price breakdown with `hardware` / `setup` lines and the family-minimum footnote, version stepper, Mandala overlay while a job runs | `GET /api/designs/{id}`, `/versions`, `GET /api/templates/{id}`, `GET /api/families/{id}`, `GET /api/catalog/materials`, `GET /api/versions/{v}/price?material=`, `POST /api/versions/{v}/params` (`{params, material, features}`), `POST /api/uploads`, SSE `GET /api/jobs/{job}/events` |
| `/create` | stage | Prompt bar (or the echoed prompt), "Give your idea an Avatar": `FamilyPicker` cards (codename, name, tagline, "Comes with a steel split ring 25 mm", "Palm-sized · 30–60 mm", what it takes, "from ₹…") with a quiet "Swaroop · Print as it is" card last; templates no Avatar covers keep "Start" → `/design/new?template=` | `GET /api/families`, `GET /api/templates` |
| `/create/[family]` | stage | **Avatar composer** (`ContentComposer`): template chooser when the family has several, the Chhaap panel per anchor (Naam with a live count against `max_text_chars`, Chhavi photo dropzone → raised/cut-in + depth up to the anchor's `max_relief_mm`, Roop model-file dropzone on volume anchors with a size slider inside the envelope, Buti disabled "motifs arrive soon"), finish chips trimmed by `material_rules`, "Sculpt" → `POST /api/designs {source:"create", family_id, template_id, features, material, title}` → the studio. For `kind: raw` the **Swaroop composer** (`RawPrintComposer`): model-file dropzone, size slider with a plain-language hint (matchbox · mug · shoebox), "As uploaded · Lay flat", finish, "Check and price" → `POST /api/designs {source:"upload", family_id:"raw_print", features:[hero_mesh], params:{longest_mm, orientation}, material}`. `?item=` seeds the composer from a Shop item, `?prompt=` becomes the working title | `GET /api/families/{id}` (404 → not-found), `GET /api/catalog/materials`, `GET /api/catalog/items/{slug}`, `POST /api/uploads` |
| `/signin` | paper | +91 phone → OTP → token. Shows the "Mock OTP · dev code" chip when the API returns `dev_code`; inline `otp_invalid` / `otp_expired` / `otp_rate_limited`; toast with what was attached ("Your 1 design and cart came with you"); redirects to `?next=` (same-site paths only) | `POST /api/auth/otp/request`, `POST /api/auth/otp/verify` |
| `/cart` | paper | Items (finish tile, title, specs, finish, qty 1–20, remove), `repriced` and `purchasable=false` notices, subtotal · shipping · total, "Continue to checkout" (→ `/signin?next=/checkout` for guests); empty state links to Shop and Create | `GET /api/cart`, `PATCH`/`DELETE /api/cart/items/{id}` |
| `/checkout` | paper, sign-in required | Stepper **Review** (editable qty) → **Stability** (`StabilityCard` per version, blocks on a fail) → **Pay** (address picker + add/edit form with live serviceability "Delhivery · 4 days" / "Not serviceable", UPI · Card · Net banking chips, WhatsApp toggle, "Pay ₹…" → `window.location` to `payment.pay_url` with `?method=`) → Track (the order page). Inline `cart_empty`, `not_printable`, `not_serviceable` | `GET /api/versions/{v}/printability`, `GET`/`POST`/`PUT /api/me/addresses*`, `GET /api/shipping/serviceability`, `POST /api/checkout` |
| `/checkout/pay/[paymentId]` | paper, sign-in required | **Mock payment gateway · local only** (ADR-0013): amount, order number, method chips, "Simulate successful payment" → `/orders/{id}?placed=1`, "Simulate failed payment" → "Try again" (new attempt → its `pay_url`) / "Back to cart". Says so when the payment is already final or not a mock | `GET /api/payments/{id}`, `POST /api/payments/{id}/mock/complete`, `POST /api/orders/{id}/payments` |
| `/orders` | paper, sign-in required | `OrderSummary` cards: number, title, stage pill, total, "Arrives Thu, 2 Oct", placed date | `GET /api/orders` |
| `/orders/[id]` | paper, sign-in required | The tracking board (06): order number, title, "Arrives …", Queued · Slicing · Printing · Sanding · Shipped · Delivered timeline with a LIVE badge while streaming, live card (studio · printer bay · layer height from `event.detail`), time-lapse placeholder at Printing, items, address, payment status + invoice number, history; `?placed=1` shows "Designed by you. Crafted by Aakar."; "Complete payment" while `pending_payment` | `GET /api/orders/{id}`, SSE `GET /api/orders/{id}/events` (poll fallback) |
| `/remix` | paper | "Arrives in Phase 2" card | — |

`src/app/(paper)` and `src/app/(stage)` are route groups: the stage layout sets `data-surface="stage"`, which flips the
CSS variables from `@aakar/design-tokens/css/tokens.css` to the deep-indigo palette.

## Component map

| Component | File | Notes |
|---|---|---|
| `BloomMark` | `src/components/brand/BloomMark.tsx` | Logo; exact paths from `design/source/Aakar Logo.dc.html`, stroke weight steps up at small sizes |
| `BloomLoader` | `src/components/brand/BloomLoader.tsx` | Route/page loader: stroke-dash draw-on of the ring then the lifted petal |
| `MandalaSpinner` | `src/components/brand/MandalaSpinner.tsx` | Generation spinner (`animate-mandala`), stage copy from `tokens.stage_copy`, optional progress bar |
| `SiteNav` / `StageNav` | `src/components/nav/` | Paper header (Shop · Create · Remix · Orders · Cart · n · Sign in / account menu) and the compact stage header |
| `NavIdentity`, `CartLink` | `src/components/nav/NavIdentity.tsx` | "Sign in" (guest) or the name/phone with a menu (Orders · Cart · Sign out); "Cart · n" live from the cart store |
| `IdentityBoot` | `src/components/identity/IdentityBoot.tsx` | Mounted once in the root layout: creates the guest id, loads session and cart, reloads both on token changes |
| `RequireSignIn` | `src/components/identity/RequireSignIn.tsx` | Renders children for a user; sends guests to `/signin?next=` |
| `SignIn` | `src/components/identity/SignIn.tsx` | The OTP flow |
| `Toaster` | `src/components/ui/Toaster.tsx` | Bottom-centre toasts (`toast()` from `src/store/toast.ts`), e.g. "… is in your cart" with "View cart" |
| `AddToCartButton` | `src/components/cart/AddToCartButton.tsx` | Shop cards and item page: `POST /api/designs` → follow the job → `POST /api/cart/items` with the default finish; label runs Preparing… → Sculpting… → Adding… → Added ✓ |
| `CartView`, `CartItemRow`, `CartTotals`, `QtyStepper`, `FinishTile` | `src/components/cart/` | The cart page and the row/summary pieces reused by checkout and orders |
| `Checkout`, `CheckoutSteps`, `AddressPicker`, `AddressForm`, `PaymentMethodChips` | `src/components/checkout/` | The three interactive checkout steps, the board's stepper, "Deliver to" cards, the address form with serviceability, UPI · Card · Net banking |
| `MockPayPage` | `src/components/checkout/MockPayPage.tsx` | The placeholder gateway page |
| `OrdersList`, `OrderTracking`, `OrderStageTimeline`, `StagePill` | `src/components/orders/` | Orders list and the tracking board |
| `useOrderStream` | `src/components/orders/useOrderStream.ts` | `useEventStream` on `/api/orders/{id}/events`; closes on delivered/cancelled; polls `GET /api/orders/{id}` when the stream is gone |
| `PromptBar` | `src/components/ui/PromptBar.tsx` | Submits to `/create?prompt=` |
| `SkuCard` | `src/components/ui/SkuCard.tsx` | Name, `formatPaise` price, `specs_line`, optional "Make it yours" line, dual actions, "Coming soon" ribbon |
| `FamilyPicker` | `src/components/design/FamilyPicker.tsx` | Avatar cards from `GET /api/families` (carriers, then object families, Swaroop last); not-ready families show "Coming soon" |
| `ContentComposer` | `src/components/design/ContentComposer.tsx` | The Avatar composer: template pick, Chhaap, finish, title, Sculpt; "What you'll get" summary aside |
| `RawPrintComposer` | `src/components/design/RawPrintComposer.tsx` | The Swaroop composer: model file → size → orientation → finish → "Check and price" |
| `ContentSlotPanel` | `src/components/design/ContentSlotPanel.tsx` | "Your Chhaap": one card per anchor that takes content, tabs Naam · Chhavi · Roop (· Buti disabled); controlled by the studio store or the composer |
| `Dropzone` | `src/components/ui/Dropzone.tsx` | Drag-and-drop / keyboard file target → `POST /api/uploads`; format and size checked in the browser first |
| `Segmented`, `RangeField` | `src/components/ui/` | Chip radio group ("Raised · Cut in") and a labelled slider with a readout and hint |
| `FinishChips` | `src/components/ui/FinishChips.tsx` | Radio group of finishes with swatches; arrow keys move, Space/Enter select |
| `ParamSliders` | `src/components/ui/ParamSliders.tsx` | Range inputs from the template descriptor (min/max/step/unit), toggles for booleans, selects for enums |
| `StageTimeline` | `src/components/ui/StageTimeline.tsx` | understanding → sculpting → checking → pricing → ready |
| `StabilityCard` | `src/components/ui/StabilityCard.tsx` | Tiles for `printability.checks` with pass / warn / fail / skipped styling |
| `PriceBreakdown` | `src/components/ui/PriceBreakdown.tsx` | Lines + shipping ("Free" when 0) + total |
| `ProblemCard`, `PhaseCard`, `Stat` | `src/components/ui/` | RFC 9457 error card, "arrives in Phase N" card, label/value row |
| `ShopGrid`, `StartWithPiece` | `src/components/shop/` | Client-side category filter; the POST-and-redirect button |
| `DesignStudio` | `src/components/design/DesignStudio.tsx` | The viewer page: loading, SSE, price refetch, sculpt, version stepping, layout |
| `useJobStream` | `src/components/design/useJobStream.ts` | `useEventStream` on `/api/jobs/{id}/events`; closes on `ready`/`failed`; polls `GET /api/jobs/{id}` if the stream is closed early |
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
| `src/lib/api/types.ts` | Friendly aliases (`CatalogItem`, `Shelf`, `Family`, `Upload`, `HardwareRef`, `DesignVersion`, `TemplateDescriptor`, `TemplateAnchor`, …) plus `glbUrl()`, `boundsMm()`, `isAvailable()` |
| `src/lib/api/client.ts` | `api.*` typed fetch wrapper (catalog incl. `shelves`, `families`, `uploads` via `FormData`, templates, designs, versions, jobs, auth, addresses, cart, shipping, checkout, orders, payments); adds the identity headers; `ApiError` carries the Problem Details and `code`; `toProblem()` for rendering |
| `src/lib/features.ts` | Hand-written `Feature = EmbossText \| Motif \| ReliefImage \| HeroMesh` mirroring `design-spec.v1.json` `$defs`; `featureLabel()` (Naam · Buti · Chhavi · Roop), `familyLabel()` ("Codename · Name"), `detectScript()`, anchor helpers (`anchorAccepts`, `contentAnchors`, relief/depth/size ranges), `featuresFromSpec()`, `sameFeatures()` |
| `src/lib/families.ts` | Avatar copy: `hardwareSentence()` / `hardwareNames()`, `envelopeLine()`, `sizeHint()`, `sortFamilies()`, `allowedMaterialIds()` from `material_rules`, `defaultTemplate()`, `RAW_FAMILY_ID` |
| `src/lib/uploads.ts` | Accepted formats, size caps, `accept` lists and browser-side `fileProblem()` for `POST /api/uploads` |
| `src/lib/catalog.ts` | `shelfPills()`, `categoryLabel(id, shelves)`, `shelvesFromItems()` fallback, `shopItems()` (drops `raw_print`), `makeItYours()`, `EXAMPLE_PROMPTS` |
| `src/lib/identity.ts` | Guest id (`aakar_guest`, UUID, created lazily) and access token (`aakar_token`) in `localStorage`; `identityHeaders()`; `IDENTITY_EVENT` fired on token changes; `formatPhone()`, `safeNext()` |
| `src/lib/useEventStream.ts` | Generic SSE hook over `fetch` (so the bearer token travels; `EventSource` can't send headers): parses `id:`/`event:`/`data:`, reconnects with `Last-Event-ID`, falls back to a `poll()` until the terminal event; returns `{ live }` |
| `src/lib/orders.ts` | Stage order and copy for the tracking board, `formatArrives()` ("Thu, 2 Oct"), `formatPlaced()`, `liveDetail()` reading `studio` / `printer_bay` / `layer_height_mm` from `OrderEvent.detail` |
| `src/lib/viewer/materials.ts` | `physicalMaterialFrom(pbr)` → `MeshPhysicalMaterial` (color, roughness, metalness, clearcoat, sheen); fallback materials from the tokens package |
| `src/lib/viewer/environments.ts` | Template environment → drei preset (`desk_oak`→apartment, `teak_table_candlelight`→night, `studio`→studio, `dashboard`→warehouse, `kitchen_marble`→lobby, `balcony_daylight`→sunset) |
| `src/store/design.ts` | zustand store for the studio: design, versions, active version, materials, selected finish, price state, job progress, param draft, features draft (`setFeature`, `resetFeatures`) |
| `src/store/session.ts` | `useSession()`: `status: loading \| guest \| user`, `user`, `signIn(session)`, `signOut()`; `load()` asks `GET /api/auth/me` when a token exists |
| `src/store/cart.ts` | `cart`, `count` (optimistic), `pending` per item, `refresh()`, `add()`, `update()`, `remove()`, `clear()` |
| `src/store/toast.ts` | `toast({ message, action?, tone? })` |

### Identity plumbing (ADR-0013)

- Every browser request carries `X-Aakar-Guest: <uuid>` (created on first use) and, once signed in, `Authorization: Bearer <token>`.
  Server components call the API anonymously.
- A 401 while holding a token forgets the token (the OTP endpoints are exempt: they answer 401 for a wrong code). `IdentityBoot`
  listens for token changes and reloads the session and the cart, so the nav, guards and cart count follow.
- `POST /api/auth/otp/verify` attaches the guest's designs and cart to the user; the sign-in page shows the counts in a toast.
- Guarded pages (`/checkout`, `/checkout/pay/*`, `/orders*`) redirect guests to `/signin?next=…`; `next` must be a same-site path.

### Mock surfaces

Everything a real provider would own is labelled as a mock in the UI: the "Mock OTP · dev code" chip on `/signin` (only when
the API returns `dev_code`), the "Mock payment gateway · local only" banner on `/checkout/pay/*`, and "(mock)" after the
carrier when serviceability comes back from `mock-delhivery`. None of these pages call a real provider.

## Notes and deviations

- `CatalogItem.available` is required in the contract; the UI still treats a missing value as available
  (`isAvailable()` in `src/lib/api/types.ts`).
- Features are typed by hand in `src/lib/features.ts` because the contract types request features as open objects
  and marks every defaulted response field required; `content_source.url` is optional on the client (the API fills
  it from `upload_id`). One feature per anchor is a UI rule (the panel's tabs), not a contract rule.
- `GET /api/families` is documented as "available with a live template"; the picker still hides `available=false`
  and shows `ready=false` as "Coming soon" defensively. The studio reads `family_id` from the design, the version or
  the spec's `family` and fetches the family lazily for its codename, rules and envelope.
- `GET /api/orders/{id}/events` requires the bearer token, which `EventSource` cannot send, so all SSE streams are read
  with `fetch` + a small parser (`src/lib/useEventStream.ts`). No token is ever put in a URL.
- Payment method chips are cosmetic until a real gateway shows its own sheet; the choice travels to the mock pay page as
  `?method=` appended to `payment.pay_url` and is recorded by `POST /api/payments/{id}/mock/complete`.
- "Add to Cart" in the studio shows the piece price (`price.subtotal_paise`); shipping is added at cart level.
- `Payment` has no order number, so the mock pay page also fetches `GET /api/orders/{order_id}` for it (falls back to the id).
- drei HDRI presets are fetched at runtime from the drei-assets CDN. When that host is unreachable the viewer keeps its
  key/fill lights and simply has no image-based reflections.
- `three-stdlib` types are reached through drei (`ComponentRef<typeof OrbitControls>`), so it is not a direct dependency.
- Reduced motion: `tokens.css` zeroes the motion durations; `globals.css` also stops the Bloom draw-on, the mandala spin
  and the fade-ins under `prefers-reduced-motion: reduce`.
