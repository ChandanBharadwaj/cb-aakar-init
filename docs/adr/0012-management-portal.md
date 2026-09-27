# ADR-0012: A separate management portal owns operational configuration

- Status: Accepted (decided by the product owner on 2026-09-27)
- Date: 2026-09-27
- Deciders: Chandan Bharadwaj (product owner)

## Context
The plan had an internal ops console under `/studio` in the storefront for fulfilment stages. The owner asked for pricing, shipping and margin to be "managed by a separate management portal", and the same need applies to materials, catalog availability and printer settings.

## Decision
Build **`apps/admin`**, a separate Next.js application (same design tokens, paper surface, role-gated), backed by an `admin` module in the API with its own authentication (staff accounts, not customer sign-in). It owns:
- Pricing policies (ADR-0008): rates, fees, margin, shipping rules; publish a new version.
- Materials and finishes: filament mapping, density, rates, PBR presets, availability.
- Catalog: items, categories, default parameters, `available`, media.
- Templates: which template versions are live.
- Fulfilment ops (the former `/studio` console): print queue, printer assignment, stage advancement, QC, reprints, content-review queue.
- Printers: registered printers per bridge (ADR-0004), status, material loaded.

## Consequences
- One more app in the monorepo and an `admin` API module; Phase 1 gains the pricing-policy and catalog screens as prerequisites for checkout.
- Configuration changes are audited (who, when, old and new value).
- The storefront never carries staff-only screens; `/studio` routes in the storefront are dropped from the plan.
