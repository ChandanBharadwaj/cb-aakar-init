# ADR-0005: Freeform generation behind an adapter

- Status: Proposed
- Date: 2026-09-26
- Deciders: Chandan Bharadwaj (product owner)

## Context
Freeform text-to-3D quality and cost are moving quickly; hosted vendors and self-hosted open models trade off differently on latency, cost, licensing and data residency.

## Decision
Recommend: implement `GenerativeProvider` as an interface in Phase 3 with a hosted vendor adapter first; evaluate a self-hosted open model on a GPU spot pool against it; decide the default provider at Phase 2 exit based on printability pass rate after repair, cost per design and licence terms.

## Consequences
No vendor lock-in at the geometry boundary. Phase 3 carries an evaluation task before the default is set.
