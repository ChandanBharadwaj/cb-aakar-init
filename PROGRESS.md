# Aakar — Progress

Living status of the plan in [PLAN.md](PLAN.md). Update this file with every merged change; the [CHANGELOG](CHANGELOG.md) records *what* changed, this file records *where we are*.

| | |
|---|---|
| Last updated | 2026-09-27 |
| Current phase | **Phase 1 — Shop + Remix-lite MVP** (starting) |
| Completed | Phase 0 — Foundations |
| Roadmap position | ~2 of 28 scheduled weeks (5 phases; 1 done) |

## Phases

| Phase | Weeks | Status | Exit criterion | Evidence |
|---|---|---|---|---|
| 0 — Foundations | 1–2 | ✅ Done | One template renders, prices and exports print-ready files from a fresh clone | `make slice` builds the Jharokha stand in ~3.5 s; API + geometry + storefront verified live; 110 automated tests |
| 1 — Shop + Remix-lite MVP | 3–10 | ⬜ Not started | 50 paid orders shipped; ≥ 95% fidelity; p95 preview ≤ 15 s | |
| 2 — Create + Co-Designer | 11–18 | ⬜ Not started | ≥ 70% of Create sessions reach a priced design; intent accuracy ≥ 90% | |
| 3 — Freeform and immersive | 19–28 | ⬜ Not started | Freeform designs ship at the same fidelity bar; AR used in ≥ 20% of mobile sessions | |
| 4 — Scale | 29+ | ⬜ Not started | | |

## Customer journey coverage (brief §3)

| Touchpoint | State |
|---|---|
| 01 Grand entrance (Home, prompt bar) | ✅ Built; prompt submits to Create, which explains Phase 2 |
| 02 Shop (catalog, "Modify with AI") | 🟨 Built; 1 of 6 SKUs buildable, cart disabled |
| 03 Create (natural-language generation) | 🟨 Template picker only; agent arrives in Phase 2 |
| 04 Remix (AI modifications) | 🟨 Parameter sliders only; language edits arrive in Phase 2 |
| 05 Checkout + physics check | 🟨 Stability report and price exist; no cart, payment or address |
| 06 Order tracking + WhatsApp time-lapse | ⬜ |
| 07 Unboxing | ⬜ |

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

## Phase 1 — Shop + Remix-lite MVP (backlog)

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
- [ ] Orders with `AK-` numbers, state machine, order events, SSE tracking
- [ ] Payments: Razorpay orders, webhook verification, reconciliation, invoices
- [ ] Shipping: Delhivery serviceability, rates, AWB, tracking webhooks
- [ ] Notifications: WhatsApp templates + email fallback, opt-in registry
- [ ] Fulfilment: print jobs, printer assignment, stage transitions, QC, reprint
- [ ] Outbox relay for unpublished events
- [ ] `available` flag driven by the geometry template registry instead of the seed

**Storefront**
- [ ] Cart and checkout (Review → Stability → Pay → Track) with UPI/cards/net banking
- [ ] Orders page with live stages
- [ ] Text-emboss UI on anchors; motif picker (library seeded in Phase 2)
- [ ] Remaining environments; material chips with calibration swatches

**Studio ops**
- [ ] `services/farm-agent`: Moonraker/OctoPrint bridge, snapshots, stage posts
- [ ] Ops console under `/studio`
- [ ] Packaging card PDF at PACKED

**Non-functional**
- [ ] OpenTelemetry traces across Java → RabbitMQ → Python
- [ ] Rate limits and per-user generation quotas
- [ ] GST and consumer-law review with a chartered accountant (ADR-0008 follow-up)

## Decisions

| ADR | Decision | Status |
|---|---|---|
| 0001 | Cream pages + indigo stage | Accepted 2026-09-27 |
| 0002 | Shop · Create · Remix labels | Accepted 2026-09-27 |
| 0003 | All seven embossing scripts at launch | Accepted 2026-09-27 |
| 0004 | Open-firmware printers | Proposed |
| 0005 | Generative provider behind an adapter | Proposed |
| 0006 | AWS ap-south-1 | Proposed |
| 0007 | Retire KalaForge; `AK-` order prefix | Proposed |
| 0008 | Free shipping ≥ ₹999; margin in rates | Proposed |
| 0009 | Parametric-first geometry | Accepted |
| 0010 | RabbitMQ dispatch with direct profile | Accepted |
| 0011 | Contract-first schemas | Accepted |

## Verification snapshot

| Suite | Count | Last run |
|---|---|---|
| `services/inspect` pytest | 21 passed | 2026-09-26 |
| `services/geometry` pytest | 57 passed | 2026-09-26 |
| `services/api` Gradle test | 32 passed | 2026-09-26 |
| `apps/web` typecheck · lint · build | clean | 2026-09-26 |
| End-to-end slice (live services) | passed | 2026-09-26 |

## Known gaps carried into Phase 1

- Print time and filament are heuristic until a slicer runs.
- Pricing rates are board-derived placeholders.
- Overhang and load checks are `skipped` by design (Phases 2 and 3).
- RabbitMQ path exercised only in unit tests and the Compose stack; the build container has no broker.
- Docker images defined, not yet built in CI.
- drei HDRI presets load from a CDN at runtime; the viewer falls back to plain lights when offline.
