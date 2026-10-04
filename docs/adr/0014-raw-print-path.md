# ADR-0014: One raw print family ("Swaroop") may carry customer geometry as the body

- Status: Proposed
- Date: 2026-09-27
- Deciders: Chandan Bharadwaj (product owner)

## Context
ADR-0009 makes every design parametric: geometry comes from our own templates, the agent only writes a Design Spec, and freeform generation (ADR-0005) is confined to bounded regions of a parametric body. Market research (`docs/research/outcome-categories/report.md`) shows two things that stance does not cover: customers arrive with a finished model of their own ("just print this"), and, once the generative provider lands, a generated form needs somewhere to go even when no carrier fits. Every surviving upload-and-print service exposes the same five choices (material, colour, size, quantity, speed), hides slicer settings, validates before quoting and prices at a floor. Treating this as an unbounded exception would erode the printable-by-construction promise; forbidding it loses a real, if thin-margin, use case and the acquisition funnel it brings.

## Decision
Exactly **one** family, `raw_print` (kind `raw`, brand name Swaroop, "print as it is"), may carry customer geometry as the body of the piece. Everything else stays parametric.

- It is still a Design Spec: `raw_print@1` is a real template whose `build_body` is empty and whose single volume anchor `body` takes one `hero_mesh` feature (`packages/contracts/schemas/design-spec.v1.json`). No executable geometry crosses the boundary; the customer's file is content, fetched by `upload_id`.
- The same pipeline applies: repair (watertight or reject with `content_unusable`), decimation, scale to `longest_mm` inside the family envelope, seat on the bed, export, the full printability gate, slicer estimate and the pricing engine. A version with `printability.passed=false` completes but cannot be bought, exactly like any other version.
- Only size (`longest_mm`), orientation (`as_uploaded` | `lay_flat`), finish and quantity are editable; layer height, infill, supports and hollowing are studio decisions. The product does not promise "make the back 18 mm wider" on a raw print, and the copy says so.
- Uploads pass a `ContentScanner` adapter (mock default, ADR-0013 pattern) and a human review queue when flagged; the customer warrants rights to uploaded content.
- Pricing adds a per-family minimum and a setup fee (`family_rules.raw_print` in the versioned pricing policy, ADR-0008) because labour, not plastic, sets the floor for small parts.
- Generated meshes (ADR-0005, Phase 3) enter through the same door: `content_source.origin = generated` into `raw_print@1`, or into a carrier's volume anchor such as the figurine plinth.

## Consequences
- Customers with their own model can order without a template, and the geometry service gains a repair-and-gate path every carrier's `hero_mesh` slot reuses.
- The gate is a policy knob, not code: thresholds (minimum wall, floater volume, envelope) live in the template constraints and the family row, editable in the portal.
- Support cost is real: raw prints fail the wall-thickness check more often than parametric pieces; the studio copy nudges towards a larger size, and a partner-fulfilment adapter (Slant 3D, Craftcloud, Indian bureaus) is the natural follow-up for materials the studio does not run.
- Any second "raw" family would need a new ADR; the `kind` CHECK constraint on `template_families` makes that a deliberate change.
