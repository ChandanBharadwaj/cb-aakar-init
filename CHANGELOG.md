# Changelog

All notable changes to Aakar. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions track plan phases until the first public release.

## [Unreleased]

### Decisions
- ADR-0001 accepted: cream pages with an indigo stage.
- ADR-0002 accepted: navigation reads Shop · Create · Remix.
- ADR-0003 accepted with a wider scope than recommended: all seven embossing scripts at launch.
- ADR-0004 accepted: the studio runs Klipper/Moonraker and Bambu Lab printers; the farm agent ships two bridges.
- ADR-0006 deferred: local Docker Compose is the only deployment target for now; production hosting decided later.
- ADR-0007 accepted: Aakar everywhere, `AK-000001` order numbers.
- ADR-0008 accepted in a new form: pricing, shipping and margin are versioned configuration edited in the management portal; board figures are the seed.
- ADR-0012 added and accepted: a separate management portal (`apps/admin`) owns operational configuration and fulfilment ops; the storefront's planned `/studio` console is dropped.

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
