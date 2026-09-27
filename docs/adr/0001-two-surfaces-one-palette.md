# ADR-0001: Two surfaces, one palette

- Status: Accepted (decided by the product owner on 2026-09-27)
- Date: 2026-09-26
- Deciders: Chandan Bharadwaj (product owner)

## Context
The product brief asks for deep indigo backgrounds throughout. The exported design boards use cream pages with indigo as an accent. Both use the same indigo, marigold and terracotta. Lit 3D objects read best against a dark backdrop; long-form browsing and checkout read best on a light one.

## Decision
the **paper** surface (cream `#F5F0E6`, paper cards) for Home, Shop, Checkout and Orders; the **stage** surface (deep indigo `#1B2238`) for the viewer, Create and AR. Both are defined in `packages/design-tokens` and switched with `data-surface="stage"`. Phase 0 is built this way; flipping the default is a one-line token change.

## Consequences
Two surfaces to test for contrast and accessibility. Marketing imagery can use either. If the owner prefers indigo throughout, only the default surface changes.
