# ADR-0008: Shipping threshold and margin

- Status: Proposed
- Date: 2026-09-26
- Deciders: Chandan Bharadwaj (product owner)

## Context
The checkout board shows free Delhivery shipping on a ₹1,249 order and prices that already include margin. Real filament, machine and finishing costs will be measured during Phase 1.

## Decision
Recommend for Phase 0 and 1: **free shipping at or above ₹999, flat ₹79 below**; margin held inside the material and machine rates (`margin_pct` 0) so the board figures reproduce exactly. Revisit monthly against measured costs; move to an explicit margin once real costs are known.

## Consequences
The pricing policy lives in one place (`packages/design-tokens/materials.json` mirrored in the API config) and is versioned (`policy_version`). Price snapshots make later changes safe for existing carts.
