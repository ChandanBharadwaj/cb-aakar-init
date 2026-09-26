# @aakar/contracts

Contract-first definitions shared by every Aakar service. Change a contract here first; then change the services.

| Path | What |
|---|---|
| `schemas/design-spec.v1.json` | The Design Spec: the only thing the agent or a viewer control may hand to the geometry service |
| `schemas/template-descriptor.v1.json` | What a parametric template publishes (params, ranges, anchors, handles, constraints, materials) |
| `schemas/printability-report.v1.json` | Stability and wall report rendered on the checkout stability card |
| `schemas/print-estimate.v1.json` | Material-independent slicing estimate; mass per material is derived by the API |
| `schemas/price-breakdown.v1.json` | Price lines in integer paise, snapshotted into cart and order |
| `schemas/events/*.json` | RabbitMQ envelope and payloads: `design.generate`, `design.progress`, `design.completed`, `design.failed` |
| `openapi/aakar-api.v1.yaml` | Storefront API (Spring Boot) |
| `openapi/geometry.v1.yaml` | Geometry service (Python) |
| `openapi/inspect.v1.yaml` | Inspect service (Python) |
| `examples/` | Worked examples used by the validator and by service tests |

## Conventions

- Money is integer **paise**. Lengths are **mm**, volumes **cm³**, mass **g**, time **seconds**.
- IDs are UUIDs. Enum values are `snake_case`.
- `design.generate` payload == request body of `POST /v1/build`; `design.completed` payload == its response body. One shape whether the call goes over RabbitMQ or HTTP.
- Out-of-range parameters are rejected (`param_out_of_range`), never silently clamped.

## Validate

```sh
pnpm install --filter @aakar/contracts
pnpm --filter @aakar/contracts validate
```
