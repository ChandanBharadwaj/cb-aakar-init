# ADR-0009: Parametric-first geometry; the LLM emits a Design Spec, never code

- Status: Accepted
- Date: 2026-09-26
- Deciders: Chandan Bharadwaj (product owner)

## Context
Freeform text-to-3D output is often non-manifold, thin-walled and impossible to edit precisely. The product promises that the shipped piece matches the on-screen model and that edits like "make the back 18 mm wider" are honoured.

## Decision
Geometry comes from our own **parametric templates** (build123d) with typed parameters, constraints and anchors. The co-designer agent may only produce a **Design Spec** (`packages/contracts/schemas/design-spec.v1.json`); our code turns it into geometry. Freeform generation, when it arrives, is confined to bounded regions of a parametric body and passes the same printability gate. No executable geometry code crosses the agent boundary.

## Consequences
Every new product family is engineering work (a template), not a prompt. In exchange every design is printable by construction, priceable from slicer output, reproducible and safe. The vertical slice (Jharokha phone stand) is the first template.
