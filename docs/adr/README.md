# Architecture Decision Records

One file per decision, numbered, never edited after acceptance (supersede instead). Status is `Proposed`, `Accepted`, `Superseded by ADR-nnnn` or `Rejected`.

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-two-surfaces-one-palette.md) | Two surfaces, one palette: paper for browsing, indigo stage for the viewer | Proposed |
| [0002](0002-navigation-labels.md) | Navigation says Shop · Create · Remix; Bazaar · Canvas · Karigar are codenames | Proposed |
| [0003](0003-launch-embossing-scripts.md) | Launch embossing scripts: Latin, Devanagari, Telugu | Proposed |
| [0004](0004-open-firmware-printers.md) | Open-firmware printers (Klipper/Moonraker) for the studio farm | Proposed |
| [0005](0005-generative-provider-adapter.md) | Freeform generation behind an adapter; provider chosen at Phase 2 exit | Proposed |
| [0006](0006-hosting-aws-mumbai.md) | Host on AWS ap-south-1 (Mumbai) | Proposed |
| [0007](0007-retire-kalaforge-naming.md) | Retire "KalaForge"; order numbers use the `AK-` prefix | Proposed |
| [0008](0008-shipping-threshold-and-margin.md) | Free shipping above ₹999, flat ₹79 below; margin held in rates for Phase 0 | Proposed |
| [0009](0009-parametric-first-geometry.md) | Parametric-first geometry; the LLM emits a Design Spec, never code | Accepted |
| [0010](0010-job-dispatch-rabbitmq-with-direct-profile.md) | Job dispatch over RabbitMQ, with a direct HTTP profile for local development | Accepted |
| [0011](0011-contract-first-schemas.md) | Contract-first: JSON Schemas and OpenAPI in `packages/contracts` lead every change | Accepted |

Template: [0000-template.md](0000-template.md).
