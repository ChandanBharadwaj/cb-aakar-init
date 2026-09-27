# ADR-0007: Retire KalaForge naming and use the AK- order prefix

- Status: Accepted (decided by the product owner on 2026-09-27)
- Date: 2026-09-26
- Deciders: Chandan Bharadwaj (product owner)

## Context
The design export was filed under the working name KalaForge and the order-tracking board shows the ID `KF-20931`. The product is Aakar.

## Decision
remove every remaining KalaForge reference; order numbers are `AK-` followed by a zero-padded six-digit sequence (`AK-000001`, `AK-020931`). Phase 0 already stores the design export under `design/` with the legacy zip removed.

## Consequences
Order-number generation is a Phase 1 task in the order module. Board mock-ups are updated when next edited.
