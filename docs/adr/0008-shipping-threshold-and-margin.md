# ADR-0008: Pricing, shipping and margin are configuration managed in the management portal

- Status: Accepted (decided by the product owner on 2026-09-27; replaces the fixed-threshold recommendation)
- Date: 2026-09-27
- Deciders: Chandan Bharadwaj (product owner)

## Context
The checkout board shows free Delhivery shipping on a ₹1,249 order and prices that already include margin. Real filament, machine and finishing costs will be measured over time, and the owner wants these levers in their own hands rather than in code or config files.

## Decision
Material rates, machine rate, finishing fees, packaging fee, margin, shipping flat rate, free-shipping threshold and the shipping label are **data**, versioned as pricing policies and edited in the **management portal** (ADR-0012). The values in `packages/design-tokens/materials.json` are the **seed** for the first policy only. Every price breakdown records the `policy_version` it was computed with; carts and orders keep their snapshot.

## Consequences
- The API's `PricingPolicy` moves from `application.yml` into a `pricing_policies` table with an active version; a change creates a new version, never edits the old one.
- The seed reproduces the board figures (free shipping at or above ₹999, ₹79 below, margin held in rates) until the owner changes them in the portal.
- Materials and their rates are editable in the portal too; the design-tokens copy remains the design-side reference and a test warns when the seed and the file diverge.
