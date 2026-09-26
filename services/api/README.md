# aakar-api

Storefront API for Aakar (PLAN §6, §10): a Spring Boot **modulith** that serves the catalog, starts
designs from templates, hands generation jobs to the geometry service, streams progress over SSE and
prices every version per material. Java 21 · Spring Boot 3.5.16 · Spring Modulith 1.4.13 ·
springdoc 2.8.17 · PostgreSQL 16 · Flyway.

The contracts in [`packages/contracts`](../../packages/contracts) are the law: request/response shapes
follow `openapi/aakar-api.v1.yaml`, JSONB documents are the `schemas/*.json` shapes stored and served
verbatim, and geometry messages are `schemas/events/*.json`. Money is integer paise.

## Run

```sh
cd services/api
./gradlew bootRun                       # direct profile, http://localhost:8080
```

Needs a PostgreSQL 16 with database `aakar` (user/password `aakar`) — `docker compose -f infra/docker-compose.yml up postgres`
or a local server. Flyway creates the schema and seeds the six Shop items and six materials on start.
Point `AAKAR_GEOMETRY_URL` at a running geometry service (`services/geometry`, default `http://localhost:8081`);
the catalog and everything except templates/generation works without it.

- Swagger UI: <http://localhost:8080/swagger-ui.html> (OpenAPI JSON at `/v3/api-docs`)
- Health: <http://localhost:8080/actuator/health>

## Test

```sh
./gradlew test        # unit + integration tests
./gradlew build       # compile, test, package build/libs/aakar-api-*.jar
```

Integration tests run against a real PostgreSQL database **`aakar_test`** (no Docker/Testcontainers):
the schema is dropped and re-migrated when the test context starts (`TestFlywayConfig`). The geometry
service is stubbed with WireMock (`support/GeometryStub`): one `jharokha_phone_stand` descriptor and a
`POST /v1/build` that answers with `packages/contracts/examples/design.completed.example.json`
(ids rewritten from the request). Tests that read monorepo files (`packages/contracts`,
`packages/design-tokens/materials.json`) skip with a message when those files are missing;
`-Daakar.repo.root=…` overrides the monorepo location.

| Test | Covers |
|---|---|
| `ModularityTests` | Spring Modulith `verify()` (module boundaries, no cycles) and module docs → `build/spring-modulith-docs` |
| `pricing/PriceCalculatorTest` | PLAN §7.10: board example (84 g · 3 h 40 m · Terracotta Silk → ₹389 + ₹733 + ₹120 = ₹1,249), rounding to a rupee ending in 9, free-shipping threshold, matte vs silk, schema validation |
| `templates/internal/TemplateParamValidatorTest` | `param_out_of_range` with every offending key listed |
| `studio/internal/EnvelopeMapperTest` | `design.generate` envelope, payload parsing, Rabbit topology (no broker) |
| `DesignFlowIntegrationTest` | Shop → job → ready version (assets, printability, price, spec valid), params edit (422 / new version), prompt → `not_yet_available`, failed build, live SSE + idempotent callbacks, Problem Details codes |
| `MaterialsSeedTest` | `V3__seed_materials.sql` and `aakar.pricing` match `packages/design-tokens/materials.json` |

## Profiles

| Profile | Dispatch | When |
|---|---|---|
| `direct` (default) | `POST {aakar.geometry.url}/v1/build` on a dedicated executor; geometry posts progress to `POST {aakar.api.public-url}/internal/jobs/{jobId}/callback`; the synchronous 200/422/500 body is applied as `design.completed` / `design.failed`. Rabbit auto-configuration is excluded. | Local dev, the vertical slice, tests |
| `rabbit` | Publishes the envelope to topic exchange `aakar.design` (routing key `design.generate`) and consumes `design.progress` / `design.completed` / `design.failed` from the durable queue `api.design.results`. Exchange, queue and bindings are declared on start. | `infra/docker-compose.yml`, production |

`SPRING_PROFILES_ACTIVE=rabbit ./gradlew bootRun` switches. Result handling is shared and idempotent: a
job row lock serialises deliveries, envelopes are de-duplicated on `event_id`, terminal jobs ignore late messages.

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `AAKAR_JDBC_URL` | `jdbc:postgresql://127.0.0.1:5432/aakar` | Database |
| `AAKAR_DB_USER` / `AAKAR_DB_PASSWORD` | `aakar` / `aakar` | Database credentials |
| `AAKAR_GEOMETRY_URL` | `http://localhost:8081` | Geometry service (templates; builds in `direct`) |
| `AAKAR_API_PUBLIC_URL` | `http://localhost:8080` | How geometry reaches this API for callbacks |
| `AAKAR_AMQP_URL` | `amqp://aakar:aakar@localhost:5672/` | RabbitMQ (`rabbit` profile only) |
| `AAKAR_TEST_JDBC_URL` | `jdbc:postgresql://127.0.0.1:5432/aakar_test` | Integration-test database (`AAKAR_TEST_DB_USER` / `AAKAR_TEST_DB_PASSWORD` optional) |
| `SPRING_PROFILES_ACTIVE` | `direct` | `direct` or `rabbit` |

Pricing policy (`aakar.pricing.*` in `application.yml`) copies `packages/design-tokens/materials.json → pricing_policy`;
CORS allows `http://localhost:3000` (`aakar.cors.allowed-origins`).

## Endpoints

| Method | Path | Notes |
|---|---|---|
| GET | `/api/catalog/items?category&q` | Shop items (available first) |
| GET | `/api/catalog/items/{slug}` | 404 `not_found` |
| GET | `/api/catalog/materials` | Digital materials with PBR presets and rates |
| GET | `/api/templates` · `/api/templates/{id}` | Descriptors cached from geometry for 60 s |
| POST | `/api/designs` | 202 `DesignAccepted`. `source=shop` + `catalog_item_slug`; `source=create|remix` + `template_id` (+ `params`, `material`); any `prompt` → 422 `not_yet_available` |
| GET | `/api/designs/{id}` · `/api/designs/{id}/versions` | Design with latest version (status derived from it); history newest first |
| GET | `/api/versions/{versionId}` | Version, with `price` for its own material once ready |
| POST | `/api/versions/{versionId}/params` | New version + job; 422 `param_out_of_range` lists keys, never clamps |
| GET | `/api/versions/{versionId}/printability` | 409 `version_not_ready` while generating or failed |
| GET | `/api/versions/{versionId}/price?material=` | 409 while not ready; 404 `unknown_material` |
| GET | `/api/jobs/{jobId}` | Job status |
| GET | `/api/jobs/{jobId}/events` | SSE: `event: stage`, `id` = sequence, `data` = `JobStageEvent`; replays after `Last-Event-ID`, `: keep-alive` every 15 s, closes after `ready`/`failed` |
| POST | `/internal/jobs/{jobId}/callback` | Direct-profile callback (`envelope.v1.json`); not for public exposure |

Errors are RFC 9457 Problem Details (`application/problem+json`) with a stable `code`:
`not_found`, `validation_failed`, `not_yet_available`, `param_out_of_range`, `template_not_available`,
`version_not_ready`, `unknown_material`, `geometry_unavailable`, `internal_error`.

## Modules (`studio.aakar.api.*`)

`catalog` · `templates` · `design` · `studio` · `pricing` · `media` · `shared` (open). Public API lives in
each module's root package; everything under `internal` is private and enforced by `ModularityTests`.
`design → studio` starts jobs; `studio` publishes `GenerationCompleted` / `GenerationFailed` application
events that `design` applies inside the same transaction, so no cycle exists.

## Docker

```sh
docker build -f services/api/Dockerfile -t aakar-api .     # from the monorepo root
```
