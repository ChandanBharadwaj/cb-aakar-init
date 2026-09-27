# @aakar/contracts

Contract-first definitions shared by every Aakar service. Change a contract here first; then change the services.

| Path | What |
|---|---|
| `schemas/design-spec.v1.json` | The Design Spec: the only thing the agent or a viewer control may hand to the geometry service |
| `schemas/template-descriptor.v1.json` | What a parametric template publishes (params, ranges, anchors incl. surface/volume content slots, handles, constraints, materials, hardware) |
| `schemas/template-family.v1.json` | The outcome-category catalogue (Avatars): shelves, bought-in hardware and families with copy, tier, envelope, material rules, shape tolerance and content slot. Instance: `packages/design-tokens/families.json` |
| `schemas/printability-report.v1.json` | Stability and wall report rendered on the checkout stability card |
| `schemas/print-estimate.v1.json` | Material-independent slicing estimate; mass per material is derived by the API |
| `schemas/price-breakdown.v1.json` | Price lines in integer paise, snapshotted into cart and order |
| `schemas/events/*.json` | RabbitMQ envelope and payloads: `design.generate`, `design.progress`, `design.completed`, `design.failed` |
| `openapi/aakar-api.v1.yaml` | Storefront API (Spring Boot): catalog, designs, jobs, identity, cart, checkout, orders, payments |
| `openapi/aakar-admin.v1.yaml` | Management API for `apps/admin` (staff auth, orders queue, stage advancement, print pack, pricing policies, materials, catalog, templates, messages, audit) |
| `openapi/geometry.v1.yaml` | Geometry service (Python) |
| `openapi/inspect.v1.yaml` | Inspect service (Python) |
| `examples/` | Worked examples used by the validator and by service tests |

## Conventions

- Money is integer **paise**. Lengths are **mm**, volumes **cm³**, mass **g**, time **seconds**.
- IDs are UUIDs. Enum values are `snake_case`.
- `design.generate` payload == request body of `POST /v1/build`; `design.completed` payload == its response body. One shape whether the call goes over RabbitMQ or HTTP.
- Out-of-range parameters are rejected (`param_out_of_range`), never silently clamped.
- Code identifiers (family ids, feature types, line codes) are English `snake_case` and never change; brand names (Avatar codenames such as `Saathi`, the Chhaap content types Naam / Buti / Chhavi / Roop) are data fields edited in the portal.
- Content features (`relief_image`, `hero_mesh`) reference customer uploads by `upload_id`; the API fills the fetchable `url` before the spec reaches the geometry service.

## Validate

```sh
pnpm install --filter @aakar/contracts
pnpm --filter @aakar/contracts validate
```
