# Changelog

All notable changes to Aakar. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions track plan phases until the first public release.

## [Unreleased]

### Added — outcome categories (Avatars) and the Swaroop raw-print path (in progress)
- **Research**: `docs/research/outcome-categories/` — market report on how a customer's model becomes an orderable object (fixed carriers with 2–3 parameters are the pattern that survived; demand ranking; per-category engineering specs; raw-print partners; unit economics), five sourced notes, and the phased implementation plan (`implementation-plan.md`, resumable from git).
- **Naming**: Avatar (carrier family), Chhaap (content slot: Naam text, Buti motif, Chhavi photo relief, Roop own 3D form), Duniya (experience), Swaroop (print as it is), Mahaul (backdrop). Code ids stay snake_case English; codenames are portal data.
- **Contracts** (PR 1): `template-family.v1.json` (shelves, hardware items, families with kind carrier|object|raw, tier, codename + plain name, envelope, hardware BOM, material rules, shape tolerance, content slot) with its instance `packages/design-tokens/families.json` (6 shelves, 7 hardware items, 25 families); `relief_image` and `hero_mesh` features with a shared `content_source`; anchors with `kind` surface|volume, `size_mm`, `bleed_mm`, `bounds_mm`, `accepts`, `max_relief_mm`; descriptor `hardware` and `min_feature_mm`; price lines `hardware` and `setup`, `family_id`, `minimum_subtotal_paise`; `design.completed.hardware`; `design.failed` `content_unusable`. Storefront API: shelves, families, uploads; designs accept `family_id`, `features`, source `upload`. Admin API: families, hardware, shelves, uploads, content reviews; `PricingPolicy.hardware_markup_pct` and `family_rules`; preview `family_id`. Generated types for both apps.

### Decisions
- ADR-0014 proposed: exactly one raw print family (Swaroop) may carry customer geometry as the body; same Design Spec, same printability gate, same pricing engine with a per-family minimum and setup fee.

### Added — the customer loop, locally, on mocks (ADR-0013)
- **Contracts**: identity (bearer + guest header), addresses, cart, shipping serviceability, checkout, orders with status/stage enums and SSE events, payments with a mock-gateway completion path and retry; a separate management API contract for the portal.
- **API**: Spring Security stateless chain; phone OTP with a mock sender (dev code in the local profile); JWT sessions with revocation; guest designs and carts attach on sign-in; cart with price snapshots and re-pricing; versioned `pricing_policies`; orders with `AK-000001` numbers, transition table, events and SSE; `PaymentGateway`, `ShippingCarrier`, `MessageSender`, `OtpSender` adapters with mock defaults; production guard. 103 tests.
- **Storefront**: sign-in with the mock OTP chip, cart, four-step checkout with addresses and serviceability, the clearly labelled mock pay page, orders list and the live tracking board; Add to Cart in the studio, Shop and item pages; SSE over fetch with bearer auth.
- **Portal** (`apps/admin`): staff sign-in, dashboard, orders queue and detail with stage advancement, print pack, QC photos, packaging card, pricing policies with live preview, materials, catalog, templates, messages log, audit; built against the management contract with its own mock (API module in progress).
- **Management API** (`services/api` `admin` module): staff sign-in with separate staff tokens, dashboard, orders queue and detail with next actions, stage advancement with customer messages, print pack zip (print sheet + 3MF + STL), QC photo upload to a local media store, packaging card PDF with QR that mints the share code, pricing policy history/publish/preview, materials and catalog CRUD with availability, template live flags, messages log, audit log. Flyway V8. 127 tests.
- **Share page**: public `GET /api/share/{code}` and the storefront `/k/[code]` page ("Designed by you. Crafted by Aakar.") with Reprint into the cart and Remix.
- **Infra**: Dockerfiles for both web apps; portal in Compose and CI.

### Verified
- Live loop against real services: guest design → cart → OTP → attach → address → checkout `AK-000001` → payment failure, retry, success with invoice → order queued → cart empty → SSE replay; and the same loop driven through the browser with screenshots.
- Live studio loop: staff login → queue → advance through slicing, printing, finishing, QC and packed → print pack zip → packaging card PDF → share code resolves on the public endpoint and the storefront page; customer tracking board shows the studio card and AWB.

### Fixed
- A second checkout before paying created a duplicate pending order; checkout now consumes the cart (contract and API).

### Decisions
- ADR-0001 accepted: cream pages with an indigo stage.
- ADR-0002 accepted: navigation reads Shop · Create · Remix.
- ADR-0003 accepted with a wider scope than recommended: all seven embossing scripts at launch.
- ADR-0004 accepted: the studio runs Klipper/Moonraker and Bambu Lab printers; the farm agent ships two bridges.
- ADR-0006 deferred: local Docker Compose is the only deployment target for now; production hosting decided later.
- ADR-0007 accepted: Aakar everywhere, `AK-000001` order numbers.
- ADR-0008 accepted in a new form: pricing, shipping and margin are versioned configuration edited in the management portal; board figures are the seed.
- ADR-0012 added and accepted: a separate management portal (`apps/admin`) owns operational configuration and fulfilment ops; the storefront's planned `/studio` console is dropped.
- ADR-0004 moved to deferred: printing is outsourced for now, so no farm agent; staff get a print pack per order.
- ADR-0013 added and accepted: Phase 1 completes the customer loop locally with payments, shipping, messaging and OTP mocked behind adapters and placeholder pages.

### Changed
- PLAN.md: `admin` API module, `apps/admin` in the layout, farm agent with two bridges, Phase 1 exit criterion reinterpreted for the local stack, infra row reflects local-only hosting.

## [0.1.0] — 2026-09-27 — Phase 0: Foundations

Commit `bbaa597`. Not tagged yet: the session's git proxy rejects tag pushes, so run `git tag -a v0.1.0 bbaa597 -m "Phase 0: Foundations" && git push origin v0.1.0` from a machine with tag rights.

### Added
- **Monorepo**: layout from PLAN §18.1, pnpm workspace, `Makefile`, editorconfig, gitignore.
- **Contracts** (`packages/contracts`): JSON Schemas for the Design Spec, template descriptor, printability report, print estimate and price breakdown; RabbitMQ event envelope and `design.generate` / `design.progress` / `design.completed` / `design.failed` payloads; OpenAPI 3.1 for the storefront API, geometry and inspect services; worked examples and a validator (`pnpm --filter @aakar/contracts validate`).
- **Design tokens** (`packages/design-tokens`): brand palette and type from the boards, `paper` and `stage` surfaces, six digital-material PBR presets mapped to filaments with density and rates, pricing policy placeholders, CSS variables and a Tailwind preset.
- **Design assets** (`design/`): concept boards, Bloom logo variants and the animated loader, unpacked from the export.
- **Inspect service** (`services/inspect`, Python): printability report v1 (manifold, bed fit, thinnest wall, centre of gravity, tipping margin; overhang and load marked skipped), heuristic print estimator, PrusaSlicer CLI backend with a tested G-code footer parser, FastAPI `POST /v1/inspect`, CLI.
- **Geometry service** (`services/geometry`, Python, build123d): template registry with descriptors, parameter validation without silent clamping, the **Jharokha phone stand** template (width, depth, height, tilt, lip, wall, arch cusps; cusped-arch window, lip with cable notch, side gussets), GLB (metres, Y-up) / 3MF / STL exports, local and S3/MinIO storage, build pipeline with progress callbacks and failure codes, FastAPI `GET /v1/templates` and `POST /v1/build`, RabbitMQ worker, CLI (`aakar-geometry build|templates|serve|worker`).
- **Storefront API** (`services/api`, Java 21, Spring Boot 3.5.16, Spring Modulith 1.4.13): modules `catalog`, `templates`, `design`, `studio`, `pricing`, `media`, `shared`; Flyway schema with JSONB columns and seeds for six Shop SKUs and six materials; endpoints for catalog, materials, templates, designs, immutable versions, parameter edits, printability, price per material, jobs and an SSE stage stream with replay; `direct` (HTTP) and `rabbit` (AMQP) job dispatch; price calculator in integer paise with rounding to a rupee ending in 9; RFC 9457 Problem Details with stable codes; springdoc UI.
- **Storefront** (`apps/web`, Next.js 15.5, React 19, React Three Fiber 9, Tailwind 3.4): Home, Shop with category pills and "Coming soon" ribbons, item detail, Create template picker, Orders and Remix placeholders, and the design viewer: GLB on an indigo stage with environment presets, six finish chips swapping a physical material live, parameter sliders that sculpt a new version, SSE stage overlay with the mandala spinner, version stepper, stats, stability card and price breakdown. Brand components: Bloom mark, Bloom loader, mandala spinner. Typed API client generated from the OpenAPI contract; a no-dependency mock API for offline UI work.
- **Infra**: `infra/docker-compose.yml` (Postgres, RabbitMQ, MinIO with bucket init, inspect, geometry, geometry worker, API in `rabbit` profile, web), Postgres test-database init, `.env.example`; Dockerfiles for every service.
- **CI**: GitHub Actions for contracts, API (Postgres service), Python services (matrix, OCCT libs, slice smoke build) and web (generated types must be current).
- **Docs**: `PLAN.md` (product and engineering plan with an implementation-status section), eleven ADRs, local runbook, README with screenshots.

### Changed
- Product name and identifiers use **Aakar** everywhere; the "KalaForge" design export zip was replaced by its unpacked contents.
- `CatalogItem` gained `available` in the OpenAPI contract so the Shop can show unbuildable SKUs as coming soon.

### Fixed (found by the end-to-end run)
- Geometry `/assets` now sends CORS headers for the storefront origin (`AAKAR_CORS_ORIGINS`), so the browser can load GLBs from local storage.
- Viewer camera frames the measured bounding sphere of the mounted model rather than fixed constants.
- Studio layout is viewport-height on large screens with independently scrolling side panels; the stage no longer grows to the tallest column.

### Verified
- 21 inspect tests, 57 geometry tests, 32 API tests; web typecheck, lint and production build clean.
- Live run with all three services: create design → six SSE stages → version 1 with assets, printability and karigar's note → price per finish → parameter edit → version 2 (100 mm, 7 cusps) → 422 on out-of-range params and on prompt-based create.

## [0.0.1] — 2026-09-26

### Added
- `PLAN.md`: the product and engineering plan.
- Design concept export (boards, logo, loader) as uploaded.

[Unreleased]: https://github.com/ChandanBharadwaj/cb-aakar-init/compare/bbaa597...HEAD
[0.1.0]: https://github.com/ChandanBharadwaj/cb-aakar-init/compare/b6aab75...bbaa597
[0.0.1]: https://github.com/ChandanBharadwaj/cb-aakar-init/commit/b6aab75
