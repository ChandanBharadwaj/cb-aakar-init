# @aakar/web — the Aakar storefront

Next.js 15 (App Router) · React 19 · TypeScript strict · Tailwind CSS 3.4 · React Three Fiber · zustand.

Phase 0 (PLAN §4, §8): Home, Shop, item detail, the 3D design studio with finishes, size sliders, live price and
stability. Phase 1 (ADR-0013) completes the customer loop: phone OTP sign-in with a mock code, cart, checkout
(Review · Stability · Pay · Track), a clearly labelled mock payment gateway page, orders and live order tracking over
SSE. AR and Create-from-prompt are still stubbed ("arrives in Phase 2").

## Run

```sh
pnpm install                       # at the repo root (pnpm workspace)
pnpm --filter @aakar/web dev       # http://localhost:3000
pnpm --filter @aakar/web build
pnpm --filter @aakar/web start
```

The storefront talks to `services/api` (Spring Boot) on `http://localhost:8080`. Until that is running, a small
in-memory stand-in serves the same contract: designs, jobs and the SSE job stream, a 422 for out-of-range params,
and the whole Phase 1 loop (OTP with dev code `123456`, guest → user attach, addresses, cart, serviceability with
`9xxxxx` pincodes not serviceable, checkout, a mock payment gateway, orders that advance a stage every ~8 s, and the
order SSE stream):

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
| `/shop` | paper | Category pills, SKU cards ("Add to Cart" disabled until Phase 1, "Modify with AI" → `/design/new?item=`); "Coming soon" ribbon for `available=false` | `GET /api/catalog/items` (streamed in a Suspense boundary) |
| `/shop/[slug]` | paper | Item detail; "Start with this piece" → `POST /api/designs {source:"shop"}` → `/design/{id}?job=` | `GET /api/catalog/items/{slug}` (404 → not-found page) |
| `/design/new` | stage | `?item=slug` or `?template=id` → `POST /api/designs` (source `shop` / `remix`) → redirect | — |
| `/design/[id]` | stage | **The studio**: karigar's note, param sliders + Sculpt, R3F viewer, finish chips, stats, stability card, price breakdown, version stepper, Mandala overlay while a job runs | `GET /api/designs/{id}`, `/versions`, `GET /api/templates/{id}`, `GET /api/catalog/materials`, `GET /api/versions/{v}/price?material=`, `POST /api/versions/{v}/params`, SSE `GET /api/jobs/{job}/events` |
| `/create` | stage | Prompt echo + "Describing a piece in words arrives in Phase 2" card, template list with Start → `/design/new?template=` | `GET /api/templates` |
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
| `SkuCard` | `src/components/ui/SkuCard.tsx` | Name, `formatPaise` price, `specs_line`, dual actions, "Coming soon" ribbon |
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
| `src/lib/api/types.ts` | Friendly aliases (`CatalogItem`, `DesignVersion`, `TemplateDescriptor`, …) plus `glbUrl()`, `boundsMm()`, `isAvailable()` |
| `src/lib/api/client.ts` | `api.*` typed fetch wrapper (catalog, templates, designs, versions, jobs, auth, addresses, cart, shipping, checkout, orders, payments); adds the identity headers; `ApiError` carries the Problem Details and `code`; `toProblem()` for rendering |
| `src/lib/identity.ts` | Guest id (`aakar_guest`, UUID, created lazily) and access token (`aakar_token`) in `localStorage`; `identityHeaders()`; `IDENTITY_EVENT` fired on token changes; `formatPhone()`, `safeNext()` |
| `src/lib/useEventStream.ts` | Generic SSE hook over `fetch` (so the bearer token travels; `EventSource` can't send headers): parses `id:`/`event:`/`data:`, reconnects with `Last-Event-ID`, falls back to a `poll()` until the terminal event; returns `{ live }` |
| `src/lib/orders.ts` | Stage order and copy for the tracking board, `formatArrives()` ("Thu, 2 Oct"), `formatPlaced()`, `liveDetail()` reading `studio` / `printer_bay` / `layer_height_mm` from `OrderEvent.detail` |
| `src/lib/viewer/materials.ts` | `physicalMaterialFrom(pbr)` → `MeshPhysicalMaterial` (color, roughness, metalness, clearcoat, sheen); fallback materials from the tokens package |
| `src/lib/viewer/environments.ts` | Template environment → drei preset (`desk_oak`→apartment, `teak_table_candlelight`→night, `studio`→studio, `dashboard`→warehouse, `kitchen_marble`→lobby, `balcony_daylight`→sunset) |
| `src/store/design.ts` | zustand store for the studio: design, versions, active version, materials, selected finish, price state, job progress, param draft |
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
