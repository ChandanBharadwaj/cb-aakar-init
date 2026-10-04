# Architecture Decision Records

One file per decision, numbered, never edited after acceptance (supersede instead). Status is `Proposed`, `Accepted`, `Superseded by ADR-nnnn` or `Rejected`.

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-two-surfaces-one-palette.md) | Two surfaces, one palette: paper for browsing, indigo stage for the viewer | Accepted |
| [0002](0002-navigation-labels.md) | Navigation says Shop · Create · Remix; Bazaar · Canvas · Karigar are codenames | Accepted |
| [0003](0003-launch-embossing-scripts.md) | Launch embossing scripts: all seven (Latin, Devanagari, Telugu, Tamil, Kannada, Bengali, Gujarati) | Accepted |
| [0004](0004-open-firmware-printers.md) | Studio printers: Klipper/Moonraker and Bambu Lab, two farm-agent bridges (printing outsourced for now) | Deferred |
| [0005](0005-generative-provider-adapter.md) | Freeform generation behind an adapter; provider chosen at Phase 2 exit | Proposed |
| [0006](0006-hosting-aws-mumbai.md) | Hosting: local Docker only for now; production deferred | Deferred |
| [0007](0007-retire-kalaforge-naming.md) | Retire "KalaForge"; order numbers use the `AK-` prefix | Accepted |
| [0008](0008-shipping-threshold-and-margin.md) | Pricing, shipping and margin are versioned configuration managed in the portal | Accepted |
| [0009](0009-parametric-first-geometry.md) | Parametric-first geometry; the LLM emits a Design Spec, never code | Accepted |
| [0010](0010-job-dispatch-rabbitmq-with-direct-profile.md) | Job dispatch over RabbitMQ, with a direct HTTP profile for local development | Accepted |
| [0011](0011-contract-first-schemas.md) | Contract-first: JSON Schemas and OpenAPI in `packages/contracts` lead every change | Accepted |
| [0012](0012-management-portal.md) | A separate management portal (`apps/admin`) owns operational configuration and fulfilment ops | Accepted |
| [0013](0013-mock-external-providers.md) | Complete the customer loop with external providers mocked behind adapters and placeholder pages | Accepted |
| [0014](0014-raw-print-path.md) | One raw print family (Swaroop) may carry customer geometry as the body; same spec, same gate, same pricing | Proposed |

Template: [0000-template.md](0000-template.md).
