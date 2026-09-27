# Aakar — Progress

Living status of the plan in [PLAN.md](PLAN.md). Update this file with every merged change; the [CHANGELOG](CHANGELOG.md) records *what* changed, this file records *where we are*.

| | |
|---|---|
| Last updated | 2026-09-27 (outcome categories: contracts landed, phases 1–5 in flight) |
| Current phase | **Phase 1** — the loop is complete locally (ADR-0013); now building **outcome categories (Avatars) + Swaroop** per `docs/research/outcome-categories/implementation-plan.md` |
| Completed | Phase 0 — Foundations |
| Roadmap position | ~2 of 28 scheduled weeks (5 phases; 1 done) |

## Phases

| Phase | Weeks | Status | Exit criterion | Evidence |
|---|---|---|---|---|
| 0 — Foundations | 1–2 | ✅ Done | One template renders, prices and exports print-ready files from a fresh clone | `make slice` builds the Jharokha stand in ~3.5 s; API + geometry + storefront verified live; 110 automated tests |
| 1 — Shop + Remix-lite MVP | 3–10 | 🟨 In progress | Loop on the local stack with sandbox providers; 50 end-to-end orders; ≥ 95% fidelity; p95 preview ≤ 15 s | Customer loop and staff portal run end to end on mocks (Groups A–D done); templates, embossing and real adapters remain |
| 2 — Create + Co-Designer | 11–18 | ⬜ Not started | ≥ 70% of Create sessions reach a priced design; intent accuracy ≥ 90% | |
| 3 — Freeform and immersive | 19–28 | ⬜ Not started | Freeform designs ship at the same fidelity bar; AR used in ≥ 20% of mobile sessions | |
| 4 — Scale | 29+ | ⬜ Not started | | |

## Customer journey coverage (brief §3)

| Touchpoint | State |
|---|---|
| 01 Grand entrance (Home, prompt bar) | ✅ Built; prompt submits to Create, which explains Phase 2 |
| 02 Shop (catalog, "Modify with AI") | 🟨 Built with Add to Cart; 1 of 6 SKUs buildable |
| 03 Create (natural-language generation) | 🟨 Template picker only; agent arrives in Phase 2 |
| 04 Remix (AI modifications) | 🟨 Parameter sliders only; language edits arrive in Phase 2 |
| 05 Checkout + physics check | ✅ Cart, four-step checkout with stability, address and serviceability, mock payment |
| 06 Order tracking + WhatsApp time-lapse | 🟨 Live tracking board with studio card; time-lapse is a placeholder card and a logged message |
| 07 Unboxing | ✅ Packaging card PDF with QR from the portal; /k/{code} page reprints or remixes |

## Phase 0 — Foundations (done)

- [x] Monorepo layout (PLAN §18.1), pnpm workspace, Makefile, editorconfig
- [x] `packages/contracts`: Design Spec, template descriptor, printability, print estimate, price schemas; event envelopes; OpenAPI for API, geometry, inspect; validator
- [x] `packages/design-tokens`: brand tokens, two surfaces, six material PBR presets, pricing policy, CSS variables, Tailwind preset
- [x] `design/`: concept boards, logo and loader unpacked; legacy zip removed
- [x] `services/inspect`: printability report v1, heuristic estimator, PrusaSlicer CLI backend (parser tested), FastAPI. 21 tests
- [x] `services/geometry`: template registry, Jharokha phone stand (build123d), GLB/3MF/STL exports, local/S3 storage, build pipeline with progress callbacks, FastAPI, RabbitMQ worker, CLI. 57 tests
- [x] `services/api`: Spring Boot 3.5 modulith, Flyway schema + seeds, catalog, templates, designs/versions, jobs with `direct` and `rabbit` dispatch, SSE, pricing. 32 tests
- [x] `apps/web`: Home, Shop, item detail, Create template picker, design viewer (R3F) with finishes, sliders, stages, stability card, price. Typecheck, lint, build clean
- [x] `infra/docker-compose.yml`, env example, Postgres init
- [x] CI: contracts, api, python-services, web workflows
- [x] ADRs 0001–0011; runbook; README with screenshots
- [x] End-to-end run: design → six SSE stages → priced version → parameter edit → version 2, verified in the browser

## Phase 1 — complete the loop (in progress)

Order of work agreed on 27 Sep 2026: finish the customer loop with mocks, feature by feature, end-to-end tests batched per group.

**Group A — Identity and cart**
- [x] Contracts: auth, cart endpoints
- [x] API `identity`: phone OTP (mock sender returns the code in local profile), JWT sessions, guest → user attach
- [x] API `cart`: items (version × material × qty) with price snapshots, guest header cart, merge on sign-in
- [x] Web: sign-in page with dev code, cart page, "Add to Cart" enabled in the studio, Shop cards and item page

**Group B — Checkout and orders**
- [x] Contracts: checkout, orders, payments, shipments
- [x] API `pricing`: versioned `pricing_policies` table (ADR-0008), active policy used by price and cart
- [x] API `order`: `AK-000001` numbers, state machine, order events, SSE tracking
- [x] API `payment`: `PaymentGateway` interface + mock; webhook-style confirmation; invoice numbers
- [x] API `shipping`: `ShippingCarrier` interface + mock serviceability, ETA, AWB, events
- [x] Web: checkout (Review → Stability → Pay → Track), mock pay page, orders list, tracking page with stages
- [x] API: checkout consumes the cart (found in the live run: a second checkout before paying created a duplicate order)

**Group C — Fulfilment and management portal**
- [x] API `admin`: staff auth, orders queue, stage advancement, QC photo, print pack (3MF/STL + print sheet), packaging card PDF with QR, pricing policy editor, materials and catalog availability, template live flags, messages, audit log
- [x] `apps/admin`: sign-in, dashboard, orders queue and detail with advance panel, print pack, QC photos, packaging card, pricing policies with preview, materials, catalog, templates, messages log, audit (verified against the real API)
- [x] API `notification`: `MessageSender` interface + logging mock; templates order_confirmed, printing_timelapse, shipped, delivered

**Group D — Unboxing and polish**
- [x] Share links `/k/{code}` → reprint or remix (public API + storefront page)
- [x] Packaging card PDF at PACKED (PDFBox + QR, share code minted once)
- [x] Time-lapse placeholder at PRINTING stage (tracking card + logged `printing_timelapse` message)
- [x] Startup guard: mock adapters refused in a production profile (`ProductionGuard`)

## Phase 1 — later backlog

Grouped by workstream (PLAN §15). Tick items as they merge.

**Geometry and inspect**
- [ ] Eleven remaining templates: headphone stand, planter, coaster set, nameplate, bookend, table lamp, diya holder, pooja accessory, wall hook, desk organizer, car accessory
- [ ] Text embossing: HarfBuzz shaping, Noto fonts for all seven scripts (ADR-0003), planar + cylindrical projection, minimum stroke rule
- [ ] Golden-image tests per script; native-speaker review sign-off
- [ ] PrusaSlicer installed on dev and CI; `AAKAR_SLICER_BIN` replaces the heuristic
- [ ] Material calibration photos for the six finishes

**API**
- [ ] Identity: phone OTP + Google sign-in, guest design ownership transfer
- [ ] Cart with guest merge
- [ ] Orders with `AK-000001` numbers, state machine, order events, SSE tracking
- [ ] Payments: Razorpay orders, webhook verification, reconciliation, invoices
- [ ] Shipping: Delhivery serviceability, rates, AWB, tracking webhooks
- [ ] Notifications: WhatsApp templates + email fallback, opt-in registry
- [ ] Fulfilment: print jobs, printer assignment, stage transitions, QC, reprint
- [ ] Outbox relay for unpublished events
- [ ] `admin` module: staff auth, versioned `pricing_policies` table replacing `application.yml` values, materials/catalog/template/printer CRUD with audit (ADR-0008, ADR-0012)
- [ ] `available` flag driven by the geometry template registry instead of the seed

**Storefront**
- [ ] Cart and checkout (Review → Stability → Pay → Track) with UPI/cards/net banking
- [ ] Orders page with live stages
- [ ] Text-emboss UI on anchors; motif picker (library seeded in Phase 2)
- [ ] Remaining environments; material chips with calibration swatches

**Studio ops**
- [ ] `services/farm-agent` (deferred while printing is outsourced, ADR-0004): `PrinterBridge` with Moonraker and Bambu Lab bridges
- [ ] `apps/admin` management portal: staff sign-in, pricing policies, materials and rates, catalog items and availability, live templates, printers, fulfilment ops, audit log (ADR-0012)
- [ ] Packaging card PDF at PACKED

**Non-functional**
- [ ] OpenTelemetry traces across Java → RabbitMQ → Python
- [ ] Rate limits and per-user generation quotas
- [ ] GST and consumer-law review with a chartered accountant (ADR-0008 follow-up)

## Outcome categories (Avatars) — in progress

Plan: [`docs/research/outcome-categories/implementation-plan.md`](docs/research/outcome-categories/implementation-plan.md) (research in the same folder). Every phase ends with a commit on `claude/3d-print-categories-ypc6xw`; a new session resumes from the table below and the plan's phase table.

| Phase | Scope | Status | Evidence / resume pointer |
|---|---|---|---|
| 0 | Research report, notes, plan committed | ✅ Done | `docs/research/outcome-categories/` |
| 1 | PR 1 contracts · PR 2 seed + `V9` + catalog module (families, shelves, hardware, policy fields) | 🟨 PR 1 done (`5235c50`); PR 2 in progress | `packages/contracts/schemas/template-family.v1.json`, `packages/design-tokens/families.json` |
| 2 | PR 3a geometry features framework · PR 3b text + motif | 🟨 3a in progress | `services/geometry/aakar_geometry/features/` |
| 3 | PR 4 carriers `keychain_tag`, `fridge_magnet`, `hanging_ornament`, `desk_nameplate`, `raw_print` | ⬜ | after 3a |
| 4 | PR 5 API uploads, features, family resolution, hardware/setup pricing, print pack, ADR-0014 | 🟨 ADR-0014 written (`0ab8f09`); code after PR 2 | `docs/adr/0014-raw-print-path.md` |
| 5 | PR 6 storefront · PR 7 portal | 🟨 in progress against the mock APIs | `apps/web/src/app/(stage)/create/`, `apps/admin/src/app/(portal)/avatars/` |
| 6 | PR 8 lithophane, plinth, pet tag, photo frame, keycap | ⬜ | |
| 7 | PRs 9–12 Duniya experiences + Katha | ⬜ | |

## Decisions

| ADR | Decision | Status |
|---|---|---|
| 0001 | Cream pages + indigo stage | Accepted 2026-09-27 |
| 0002 | Shop · Create · Remix labels | Accepted 2026-09-27 |
| 0003 | All seven embossing scripts at launch | Accepted 2026-09-27 |
| 0004 | Klipper/Moonraker and Bambu Lab bridges; deferred while printing is outsourced | Deferred 2026-09-27 |
| 0005 | Generative provider behind an adapter | Proposed |
| 0006 | Local Docker only for now; production deferred | Deferred 2026-09-27 |
| 0007 | Retire KalaForge; `AK-000001` order numbers | Accepted 2026-09-27 |
| 0008 | Pricing, shipping, margin are versioned config in the portal | Accepted 2026-09-27 |
| 0009 | Parametric-first geometry | Accepted |
| 0010 | RabbitMQ dispatch with direct profile | Accepted |
| 0011 | Contract-first schemas | Accepted |
| 0012 | Separate management portal `apps/admin` | Accepted 2026-09-27 |
| 0013 | Complete the loop with mocked external providers | Accepted 2026-09-27 |

## Verification snapshot

| Suite | Count | Last run |
|---|---|---|
| `services/inspect` pytest | 21 passed | 2026-09-26 |
| `services/geometry` pytest | 57 passed | 2026-09-26 |
| `services/api` Gradle test | 127 passed | 2026-09-27 |
| `apps/web` typecheck · lint · build | clean | 2026-09-27 |
| `apps/admin` typecheck · lint · build | clean | 2026-09-27 |
| End-to-end slice (live services) | passed | 2026-09-26 |
| Customer loop, live (guest design → cart → OTP → checkout → mock pay fail/retry/succeed → order queued → SSE) | passed, 1 finding (duplicate checkout) | 2026-09-27 |
| Customer loop in the browser (sign-in → cart → checkout → mock pay → tracking) | passed, no page errors | 2026-09-27 |
| Studio loop, live (staff login → queue → slicing → printing → finishing → qc → packed → print pack zip → packaging card PDF → share code → /k/{code}) | passed, no page errors | 2026-09-27 |

## Known gaps carried into Phase 1

- Print time and filament are heuristic until a slicer runs.
- Pricing rates are board-derived placeholders.
- Overhang and load checks are `skipped` by design (Phases 2 and 3).
- RabbitMQ path exercised only in unit tests and the Compose stack; the build container has no broker.
- Docker images defined, not yet built in CI.
- drei HDRI presets load from a CDN at runtime; the viewer falls back to plain lights when offline.
