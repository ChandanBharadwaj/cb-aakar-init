# aakar-api

Storefront API for Aakar (PLAN §6, §10, §12; ADR-0008, ADR-0012, ADR-0013): a Spring Boot **modulith** that serves the
catalog, starts designs from templates, hands generation jobs to the geometry service, streams progress over
SSE, prices every version per material, and — since Phase 1 — signs customers in by phone OTP, keeps guest and
user carts, takes checkout, records payments, tracks orders through the studio stages over SSE, books shipments
and logs customer messages. It also serves the **management API** behind the portal (`apps/admin`) under
`/admin/api/*` with its own staff accounts. Java 21 · Spring Boot 3.5.16 · Spring Modulith 1.4.13 · Spring Security 6.5 ·
springdoc 2.8.17 · jjwt 0.12.6 · PDFBox 3.0.8 · zxing 3.5.4 · PostgreSQL 16 · Flyway.

The contracts in [`packages/contracts`](../../packages/contracts) are the law: request/response shapes
follow `openapi/aakar-api.v1.yaml` (storefront) and `openapi/aakar-admin.v1.yaml` (management API), JSONB documents
are the `schemas/*.json` shapes stored and served verbatim, and geometry messages are `schemas/events/*.json`. Money is integer paise.

## Run

```sh
cd services/api
./gradlew bootRun                       # direct profile, http://localhost:8080
```

Needs a PostgreSQL 16 with database `aakar` (user/password `aakar`) — `docker compose -f infra/docker-compose.yml up postgres`
or a local server. Flyway creates the schema and seeds the six Shop items, six materials and the first pricing policy on start.
Point `AAKAR_GEOMETRY_URL` at a running geometry service (`services/geometry`, default `http://localhost:8081`);
the catalog, sign-in, cart, addresses, serviceability and everything except templates/generation works without it.

- Swagger UI: <http://localhost:8080/swagger-ui.html> (OpenAPI JSON at `/v3/api-docs`)
- Health: <http://localhost:8080/actuator/health>

### The customer loop with curl (ADR-0013 — every external provider is a mock)

```sh
API=http://localhost:8080; GUEST=$(uuidgen)
# 1. as a guest: start a Shop design (needs the geometry service), wait for ready, add it to the cart
curl -s -X POST $API/api/designs -H "X-Aakar-Guest: $GUEST" -H 'Content-Type: application/json' \
     -d '{"source":"shop","catalog_item_slug":"jharokha-phone-stand"}'          # → design_id, job_id
curl -s $API/api/designs/<design_id>                                            # latest_version.id once status=ready
curl -s -X POST $API/api/cart/items -H "X-Aakar-Guest: $GUEST" -H 'Content-Type: application/json' \
     -d '{"version_id":"<version_id>","material":"terracotta_silk","qty":1}'    # 201 Cart
# 2. sign in: the mock OTP sender returns the code as dev_code; verify attaches the guest's designs and cart
curl -s -X POST $API/api/auth/otp/request -H 'Content-Type: application/json' -d '{"phone":"+919876543210"}'
curl -s -X POST $API/api/auth/otp/verify -H "X-Aakar-Guest: $GUEST" -H 'Content-Type: application/json' \
     -d '{"request_id":"<request_id>","code":"<dev_code>"}'                     # → access_token, attached {designs:1, cart_items:1}
TOKEN=<access_token>; AUTH="Authorization: Bearer $TOKEN"
# 3. address, serviceability, checkout
curl -s -X POST $API/api/me/addresses -H "$AUTH" -H 'Content-Type: application/json' \
     -d '{"label":"Home","name":"Asha Rao","phone":"+919876543210","line1":"12 MG Road","city":"Bengaluru","state":"Karnataka","pincode":"560001"}'
curl -s "$API/api/shipping/serviceability?pincode=560001"                       # mock-delhivery, 4 days (pincodes 9xxxxx are not serviceable)
curl -s -X POST $API/api/checkout -H "$AUTH" -H 'Content-Type: application/json' -d '{"address_id":"<address_id>"}'
#    → order_id, order_number AK-000001, payment.pay_url = http://localhost:3000/checkout/pay/<payment_id>
# 4. the placeholder pay page reports the outcome (a real gateway would hit its webhook instead)
curl -s -X POST $API/api/payments/<payment_id>/mock/complete -H "$AUTH" -H 'Content-Type: application/json' \
     -d '{"outcome":"success","method":"upi"}'                                  # → succeeded, invoice_number INV-2026-000001
# 5. the order is confirmed and queued, the cart is empty, the confirmation message is logged; follow the stages
curl -s $API/api/orders/<order_id> -H "$AUTH"                                   # status queued, events 1..3, payment, address, items
curl -s $API/api/cart -H "$AUTH"                                                # items: []
curl -N $API/api/orders/<order_id>/events -H "$AUTH" -H 'Last-Event-ID: 2'      # SSE: replays 3, then live stage events
curl -s $API/api/orders -H "$AUTH"                                              # newest first
curl -s -X POST $API/api/auth/logout -H "$AUTH"                                 # 204; the token is revoked
```

`{"outcome":"failure"}` leaves the order `pending_payment` with a "Payment failed" event; `POST /api/orders/{id}/payments`
starts a fresh attempt. Studio stage changes are staff operations on the management API (below);
`Orders.advance(orderId, status, message, detail)` is the domain entry point.

### The studio side with curl (management API, ADR-0012)

```sh
API=http://localhost:8080
# 1. staff sign-in: the seed owner (password from aakar.admin.seed-password, default aakar-studio)
curl -s -X POST $API/admin/api/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"studio@aakar.local","password":"aakar-studio"}'              # → access_token (typ=staff, 12 h), staff {role: owner}
STAFF="Authorization: Bearer <access_token>"
curl -s $API/admin/api/auth/me -H "$STAFF"
curl -s $API/admin/api/dashboard -H "$STAFF"                                    # orders_by_status, orders_today, revenue_*_paise, awaiting_action
# 2. the queue: comma-separated statuses, q = order-number prefix or phone digits; page object
curl -s "$API/admin/api/orders?status=queued,on_hold&q=AK-0000&page=0&size=25" -H "$STAFF"   # items[].next_actions e.g. [slicing, on_hold, cancelled]
curl -s $API/admin/api/orders/<order_id> -H "$STAFF"                            # AdminOrder: Order + customer, next_actions, qc_photos, notifications
# 3. advance through the studio; message defaults per status, detail lands on the event
curl -s -X POST $API/admin/api/orders/<order_id>/advance -H "$STAFF" -H 'Content-Type: application/json' \
     -d '{"status":"slicing","detail":{"printer_bay":"B2","layer_height_mm":0.2,"layers_total":480}}'
curl -s -X POST $API/admin/api/orders/<order_id>/advance -H "$STAFF" -H 'Content-Type: application/json' -d '{"status":"printing"}'
# 4. hand the job to the outsourced printer
curl -s -OJ $API/admin/api/orders/<order_id>/print-pack -H "$STAFF"             # AK-000001-print-pack.zip: item-1/print-sheet.txt, model.3mf, model.stl
# … finishing → qc → packed (books the shipment) → the packaging card → shipped → delivered
curl -s -X POST $API/admin/api/orders/<order_id>/qc-photos -H "$STAFF" -F file=@front.jpg -F note='Arch edges checked'   # 201 MediaAsset
curl -s -OJ $API/admin/api/orders/<order_id>/packaging-card.pdf -H "$STAFF"     # 409 order_not_packed before packed
```

## Test

```sh
./gradlew test        # unit + integration tests
./gradlew build       # compile, test, package build/libs/aakar-api-*.jar
```

Integration tests run against a real PostgreSQL database **`aakar_test`** (no Docker/Testcontainers):
the schema is dropped and re-migrated when the test context starts (`TestFlywayConfig`). The geometry
service is stubbed with WireMock (`support/GeometryStub`): one `jharokha_phone_stand` descriptor and a
`POST /v1/build` that answers with `packages/contracts/examples/design.completed.example.json`
(ids rewritten from the request; `arch_cusps` 3 = slow build, 4 = ready but `printability.passed=false`, 7 = failed build).
Tests that read monorepo files (`packages/contracts`, `packages/design-tokens/materials.json`) skip with a message
when those files are missing; `-Daakar.repo.root=…` overrides the monorepo location.

| Test | Covers |
|---|---|
| `ModularityTests` | Spring Modulith `verify()` (module boundaries, no cycles) and module docs → `build/spring-modulith-docs` |
| `pricing/PriceCalculatorTest` | PLAN §7.10: board example (84 g · 3 h 40 m · Terracotta Silk → ₹389 + ₹733 + ₹120 = ₹1,249), rounding to a rupee ending in 9, free-shipping threshold, matte vs silk, explicit policy, schema validation |
| `pricing/internal/DbPricingPolicyStoreTest` | ADR-0008: active policy from the table, 30 s cache, `publish` deactivates/activates, duplicate version → 409, empty table seeds from `aakar.pricing` |
| `identity/internal/SessionServiceTest` | JWT issue → parse → revoke; tampered, foreign-key, expired, unknown-session and wrong-user tokens rejected |
| `identity/internal/OtpServiceTest` | 6-digit code, hashed at rest, `dev_code` exposure, 5-minute expiry (`otp_expired`), 5 requests / 15 min (`otp_rate_limited`), 5 wrong attempts (`otp_invalid`), single use |
| `cart/internal/CartMergeTest` · `CartPricingTest` | Sign-in merge (same version+material adds up, cap 20), purchasable rules, re-price trigger, specs line, line totals |
| `order/OrderNumbersTest` · `OrderStatusTest` · `internal/OrderTransitionsTest` | `AK-000001`, status → stage mapping, the §11.1 transition table (hold, reprint, cancel, finals) |
| `order/internal/OrderLifecycleTest` | `advance`: events, defaults, 409 `invalid_transition`, 404, shipment at `packed`, tracking at `shipped`/`delivered`; payment success → confirmed + queued; payment failure event |
| `payment/internal/InvoiceNumbersTest` · `MockPaymentGatewayTest` | `INV-2026-000001`, mock `pay_url` and `mock_` refs |
| `shipping/internal/MockDelhiveryTest` | Serviceability rules and `MOCK` + 10-digit AWBs |
| `shared/ProductionGuardTest` | `aakar.profile=production` refuses every mock adapter, the exposed dev code and the dev JWT secret |
| `templates/internal/TemplateParamValidatorTest` | `param_out_of_range` with every offending key listed |
| `studio/internal/EnvelopeMapperTest` | `design.generate` envelope, payload parsing, Rabbit topology (no broker) |
| `DesignFlowIntegrationTest` | Shop → job → ready version (assets, printability, price, spec valid), params edit (422 / new version), prompt → `not_yet_available`, failed build, live SSE + idempotent callbacks, Problem Details codes |
| `CustomerLoopIntegrationTest` | The whole loop above end to end, failed payment + retry, unserviceable / empty / unprintable checkout, cart rules and re-pricing on a policy change, 401 / owner-only 404s, addresses, OTP validation and rate limit, OpenAPI paths |
| `AdminLoopIntegrationTest` | Management API (ADR-0012): seeded owner sign-in, staff vs customer tokens (401 both ways), dashboard, queue filters and `next_actions`, queued → delivered with events, messages (`printing_timelapse`, `shipped`, `delivered`) and audit, print pack zip (WireMock serves the model files), QC photo upload (+ 413), packaging card 409 → `%PDF` and the share code, pricing publish (409 / 422) and preview, materials (hidden from the storefront), catalog, template live switch (422 `template_not_available`), messages and audit pages, `studio` role 403 on configuration |
| `admin/internal/StaffTokensTest` · `identity/internal/JwtTokensTest` | Staff tokens are `typ: staff`; the customer parser refuses them and the staff parser refuses customer tokens |
| `admin/internal/StaffPrincipalTest` · `DashboardServiceTest` · `PrintSheetTest` · `PrintPackTest` · `ShareCodesTest` · `PackagingCardTest` | Owner-only gating (403), studio-day windows and `awaiting_action`, print-sheet text, zip contents and missing-file notes, 8-char base32 codes with collision retry, PDF bytes start with `%PDF` |
| `MaterialsSeedTest` | `V3__seed_materials.sql`, `aakar.pricing` and the `V4` policy row match `packages/design-tokens/materials.json` |

## Identity

Two ways to be somebody (see `shared/Identity`, resolved once per request by the identity module's filter and
injectable into any controller method):

| Header | Identity | Who |
|---|---|---|
| `Authorization: Bearer <JWT>` | `user(userId)` | Signed-in customer. HS256 token (`aakar.identity.jwt-secret`, 7-day TTL); its `jti` is a row in `sessions`, so `POST /api/auth/logout` revokes it. A malformed, expired or revoked token is answered 401 `unauthenticated` on every path — never silently downgraded to a guest. |
| `X-Aakar-Guest: <uuid>` | `guest(guestId)` | Browser-generated guest. Owns designs and a cart until sign-in. |
| neither | `anonymous` | Public reads only. |

Sign-in is phone OTP (`^\+91[6-9][0-9]{9}$`): `POST /api/auth/otp/request` → 6-digit code, 5-minute expiry, at most 5
requests per phone per 15 minutes (429 `otp_rate_limited`), at most 5 wrong attempts (401 `otp_invalid`), 401 `otp_expired`
after expiry. `POST /api/auth/otp/verify` creates the user on first sign-in, issues the token and **attaches** the
`X-Aakar-Guest` identity: designs with that `guest_id` get `owner_id`, the guest cart merges into the user cart (same
version + material → quantities add up, capped at 20); `attached.designs` / `attached.cart_items` report the counts.

Public routes: `/api/catalog/**`, `/api/templates/**`, `/api/designs/**` (designs stay readable by their unguessable id),
`/api/versions/**`, `/api/jobs/**`, `/api/cart/**` (guest or user; 401 with neither header), `/api/auth/otp/*`,
`/api/shipping/**`, `/internal/**`, actuator, Swagger. Signed-in only: `/api/auth/me`, `/api/auth/logout`, `/api/me/**`,
`/api/checkout`, `/api/orders/**`, `/api/payments/**` (401 Problem `unauthenticated`; other people's orders and payments 404).
CORS allows `http://localhost:3000` with the `Authorization` and `X-Aakar-Guest` request headers (`aakar.cors.allowed-origins`).

## Management API (ADR-0012)

The portal (`apps/admin`, port 3100) talks to `/admin/api/*`. Contract: `packages/contracts/openapi/aakar-admin.v1.yaml`;
module `admin` (`studio.aakar.api.admin`), which drives the other modules through their public APIs only.

**Staff auth.** `staff_accounts` (email, name, role, bcrypt hash). On the first start with an empty table the module
seeds the owner **`studio@aakar.local`** with `aakar.admin.seed-password` (default `aakar-studio`; the production guard
refuses the default). `POST /admin/api/auth/login {email, password}` → `StaffSession` with a **staff token**: HS256 with
the identity secret but claim `typ: staff` (`sub` = account id, `email`, `role`), `aakar.admin.token-ttl` = 12 h.
Customer tokens are `typ: customer`; each parser refuses the other type, so a customer token on `/admin/api/**` and a
staff token on `/api/**` both answer 401 `unauthenticated`. `/admin/api/**` has its own Spring Security chain (order 10,
before the customer chain); `GET /admin/api/auth/me` returns the account. Further accounts: `StaffAccounts.create(...)`
(no endpoint yet).

**Roles.** `owner` may do everything; `studio` runs fulfilment (orders, advance, print pack, QC photos, packaging card,
preview) and reads configuration — writes to pricing, materials, catalog and templates answer 403 `forbidden`.

**Endpoints.**

| Method | Path | Notes |
|---|---|---|
| POST | `/admin/api/auth/login` | 200 `StaffSession`; 401 `unauthenticated` |
| GET | `/admin/api/auth/me` | `Staff` |
| GET | `/admin/api/dashboard` | `orders_by_status` (every status), `orders_today`, `revenue_today_paise`, `revenue_month_paise` (non-cancelled orders with a succeeded payment, Asia/Kolkata days), `awaiting_action` = queued + finishing + qc |
| GET | `/admin/api/orders?status&q&page&size` | Newest first; `status` comma-separated; `q` = number prefix (`AK-0001`, `000012`) or phone digits; `{items, page, size, total}`, `size` ≤ 100; items carry `customer`, `materials`, `next_actions` |
| GET | `/admin/api/orders/{orderId}` | `AdminOrder`: the customer `Order` + `customer`, `next_actions` (transition table), `qc_photos`, `notifications` |
| POST | `/admin/api/orders/{orderId}/advance` | `{status, message?, detail?}` → `AdminOrder`; defaults "Slicing your piece", "Printing", "Hand sanding & sealing", "Quality check", "Packed", "Shipped", "Delivered"; 409 `invalid_transition`; records `printing_timelapse` / `shipped` / `delivered` messages; audit `order.advance` |
| GET | `/admin/api/orders/{orderId}/print-pack` | `application/zip`, `Content-Disposition: attachment; filename="AK-000001-print-pack.zip"`; per item `item-<n>/print-sheet.txt` + `model.3mf` + `model.stl` |
| POST | `/admin/api/orders/{orderId}/qc-photos` | multipart `file` (≤ 10 MB, else 413 `payload_too_large`) + `note` → 201 `MediaAsset`; audit `order.qc_photo` |
| GET | `/admin/api/orders/{orderId}/packaging-card.pdf` | One-page A6 PDF; 409 `order_not_packed` before `packed` |
| GET · POST | `/admin/api/pricing/policies` | History (newest first, with policy body and note); publish `{version, policy, note?}` → 201 (owner; 409 `policy_version_exists`, 422 `validation_failed`); audit `pricing.publish` (before = previous active) |
| GET | `/admin/api/pricing/policies/active` | The active version |
| POST | `/admin/api/pricing/preview` | `{policy, material, extruded_volume_cm3, print_seconds}` → `PriceBreakdown` with `policy_version: preview` |
| GET · POST | `/admin/api/materials` | All incl. unavailable; create (owner; 409 `material_exists`) |
| PUT | `/admin/api/materials/{materialId}` | Replace (owner; 404); `available: false` hides it from `GET /api/catalog/materials` (existing carts and orders keep pricing) |
| GET · POST | `/admin/api/catalog/items` | All items; create (owner; 409 `slug_exists`, 422 `unknown_material`) |
| PUT | `/admin/api/catalog/items/{slug}` | Replace (owner; 404) |
| GET | `/admin/api/templates` | Geometry descriptors merged with `template_flags` (`live` default true) and the slugs using each |
| PUT | `/admin/api/templates/{templateId}` | `{live}` (owner; 404); `live: false` hides it from `GET /api/templates` and makes `POST /api/designs` answer 422 `template_not_available` |
| GET | `/admin/api/notifications?order_id&page&size` | Messages log (`NotificationRecord` with `order_id`, `rendered_text`), newest first, `size` ≤ 200 |
| GET | `/admin/api/audit?page&size` | `AuditEntry` rows (`staff_email`, `action`, `target`, `before`, `after`), newest first |

Schema violations on admin bodies answer **422** `validation_failed` (the storefront API uses 400). Every write records an
`audit_log` row (`order.advance`, `order.qc_photo`, `pricing.publish`, `material.create|update`, `catalog.create|update`, `template.live`).

**Print pack.** Built in memory: for each order item a `print-sheet.txt` (order number, piece and version, template `id@version`
and params from the version spec, material and filament, finish class, quantity, bounds, mass and estimated time from the
snapshot, specs line, notes, and the list of files) plus `model.3mf` and `model.stl` downloaded from the version's `assets`
URLs; a file that cannot be fetched is skipped and noted on the sheet. Printing is outsourced (ADR-0004).

**Packaging card.** PDFBox + zxing: "Aakar", "Designed by You. Crafted by Aakar.", the piece, its finish, "Printed in
Bengaluru · <date>", the order number and the reprint / remix link `{aakar.web.url}/k/<code>` as text and QR.

**Share codes.** `share_codes` (8-character base32 code, order, design, version) — minted once per order at the first card
request. The public storefront page for `/k/{code}` arrives later; the row already names what to open.

**Media.** QC photos go through the `media` module's `MediaStore`: files under `aakar.media.dir` (default `./.aakar-media`)
served publicly at `{aakar.api.public-url}/media/{key}`; `media_assets` keeps kind, order, key, URL, type, size and note.
An S3 store is a drop-in implementation.

CORS: `aakar.cors.origins` (default `http://localhost:3000,http://localhost:3100`) applies to `/api/**`, `/admin/api/**` and
`/media/**`; `Content-Disposition` is exposed so the portal can name downloads.

## Mock adapters (ADR-0013) and how to swap them

Every external provider sits behind an interface in its module's root package; the implementation is chosen by a property
and the default is the mock. A real adapter is a new `@Component` implementing the interface, guarded by
`@ConditionalOnProperty(name = "<property>", havingValue = "<name>")` — callers never change.

| Dependency | Interface | Property (default) | Mock behaviour |
|---|---|---|---|
| OTP / SMS | `identity.OtpSender` | `aakar.identity.otp.sender` (`mock`) | Logs the code; with `aakar.identity.otp.expose-dev-code=true` (default) the code is returned as `dev_code` |
| Payments | `payment.PaymentGateway` | `aakar.payments.gateway` (`mock`) | `pay_url` = `{aakar.web.url}/checkout/pay/{paymentId}`, `gateway_ref` = `mock_…`; the pay page confirms through `POST /api/payments/{id}/mock/complete` (409 `payment_final` for a non-mock or already final payment). A real gateway's webhook handler calls the same `Payments.confirm(paymentId, outcome, method, gatewayRef)` |
| Shipping | `shipping.ShippingCarrier` | `aakar.shipping.carrier` (`mock`) | `mock-delhivery`: every 6-digit pincode is serviceable in 4 days without COD, except pincodes starting with `9`; shipments get `MOCK` + 10 digits, no tracking URL |
| Messaging | `notification.MessageSender` | `aakar.messaging.sender` (`log`) | Records the row in `notifications` with status `logged` instead of sending (template `order_confirmed` on payment success, channel `whatsapp` or `sms` per `notify_whatsapp`) |

**Production guard.** `aakar.profile` is `local` by default. With `aakar.profile=production` the context refuses to start
(`shared/ProductionGuard`) while any adapter above is `mock`/`log`, `expose-dev-code` is `true`, the JWT secret is the
dev default, or the staff seed password is the default `aakar-studio` — the message names every offending property.

## Pricing policies (ADR-0008)

`pricing_policies` holds versioned policies; exactly one is active. `V4__pricing_policies.sql` seeds version
`2026-09-phase0` from the same figures as `aakar.pricing.*` (which is now only the seed and a fallback when the table
has no active row). `PricingPolicyStore.active()` (Caffeine, 30 s) feeds `PriceCalculator`; `publish(policy, createdBy)`
activates a new version (409 `policy_version_exists` for a reused version) and is what the admin module's endpoints will
call. Every price breakdown, cart line and order carries its `policy_version`; `GET /api/cart` re-prices lines whose
snapshot is older than the active policy and marks them `repriced: true` once.

## Orders

`POST /api/checkout` needs a non-empty cart (409 `cart_empty`) of purchasable lines (409 `not_printable`), one of the
customer's addresses (404) in a serviceable pincode (422 `not_serviceable`). It snapshots lines (with the version's
`assets`), prices, address and policy version into `orders` / `order_items`, sets `eta` = today + carrier ETA + 5
production days, writes event 1 "Awaiting payment" and creates the payment. Payment success (event from the payment
module) moves the order `pending_payment → confirmed → queued` ("Order confirmed · payment received", "Queued at studio"
with `{studio: "Bengaluru"}`), empties the cart and records the confirmation message; failure appends "Payment failed"
and leaves the order payable through `POST /api/orders/{id}/payments` (409 `order_not_payable` otherwise).

Statuses collapse onto the board's stages: `pending_payment→payment`; `confirmed`, `queued`, `on_hold→queued`;
`slicing`; `printing`, `reprint→printing`; `finishing`, `qc`, `packed→sanding`; `shipped`; `delivered`; `cancelled`.
`Orders.advance` enforces the §11.1 transition table (409 `invalid_transition`), books the shipment at `packed` and adds
tracking events at `shipped`/`delivered`. `GET /api/orders/{id}/events` streams `order_events` exactly like the job
stream (`event: stage`, `id` = sequence, replay after `Last-Event-ID`, `: keep-alive` every 15 s, closes after
`delivered`/`cancelled`).

## Profiles

| Profile | Dispatch | When |
|---|---|---|
| `direct` (default) | `POST {aakar.geometry.url}/v1/build` on a dedicated executor; geometry posts progress to `POST {aakar.api.public-url}/internal/jobs/{jobId}/callback`; the synchronous 200/422/500 body is applied as `design.completed` / `design.failed`. Rabbit auto-configuration is excluded. | Local dev, the vertical slice, tests |
| `rabbit` | Publishes the envelope to topic exchange `aakar.design` (routing key `design.generate`) and consumes `design.progress` / `design.completed` / `design.failed` from the durable queue `api.design.results`. Exchange, queue and bindings are declared on start. | `infra/docker-compose.yml`, production |

`SPRING_PROFILES_ACTIVE=rabbit ./gradlew bootRun` switches. Exactly one of the two must be active (`direct` is the
default when no profile is set; add it explicitly alongside other profiles, e.g. `direct,dev`) — without a dispatcher the
application refuses to start. Result handling is shared and idempotent: a job row lock serialises deliveries, envelopes
are de-duplicated on `event_id`, terminal jobs ignore late messages.

## Environment variables and properties

| Variable | Property | Default | Purpose |
|---|---|---|---|
| `AAKAR_JDBC_URL` | `spring.datasource.url` | `jdbc:postgresql://127.0.0.1:5432/aakar` | Database |
| `AAKAR_DB_USER` / `AAKAR_DB_PASSWORD` | | `aakar` / `aakar` | Database credentials |
| `AAKAR_PROFILE` | `aakar.profile` | `local` | `production` refuses mock adapters (see above) |
| `AAKAR_GEOMETRY_URL` | `aakar.geometry.url` | `http://localhost:8081` | Geometry service (templates; builds in `direct`) |
| `AAKAR_API_PUBLIC_URL` | `aakar.api.public-url` | `http://localhost:8080` | How geometry reaches this API for callbacks |
| `AAKAR_WEB_URL` | `aakar.web.url` | `http://localhost:3000` | Storefront; base of the mock gateway's `pay_url` |
| `AAKAR_JWT_SECRET` | `aakar.identity.jwt-secret` | fixed dev string | HS256 key, at least 32 characters |
| `AAKAR_TOKEN_TTL` | `aakar.identity.token-ttl` | `7d` | Access-token lifetime |
| `AAKAR_OTP_SENDER` | `aakar.identity.otp.sender` | `mock` | OTP delivery adapter |
| `AAKAR_OTP_EXPOSE_DEV_CODE` | `aakar.identity.otp.expose-dev-code` | `true` | Return the code as `dev_code` (forced off in production) |
| | `aakar.identity.otp.ttl` · `max-requests-per-window` · `window` · `max-attempts` | `5m` · `5` · `15m` · `5` | OTP rules |
| `AAKAR_PAYMENTS_GATEWAY` | `aakar.payments.gateway` | `mock` | Payment adapter |
| `AAKAR_SHIPPING_CARRIER` | `aakar.shipping.carrier` | `mock` | Carrier adapter |
| `AAKAR_MESSAGING_SENDER` | `aakar.messaging.sender` | `log` | Messaging adapter |
| `AAKAR_ADMIN_SEED_PASSWORD` | `aakar.admin.seed-password` | `aakar-studio` | Password of the seed owner `studio@aakar.local` (forced off the default in production) |
| `AAKAR_ADMIN_TOKEN_TTL` | `aakar.admin.token-ttl` | `12h` | Staff-token lifetime |
| `AAKAR_MEDIA_DIR` | `aakar.media.dir` | `./.aakar-media` | Local media store (QC photos), served at `/media/{key}` |
| `AAKAR_CORS_ORIGINS` | `aakar.cors.origins` | `http://localhost:3000,http://localhost:3100` | Allowed browser origins (storefront, portal) |
| `AAKAR_AMQP_URL` | `spring.rabbitmq.addresses` | `amqp://aakar:aakar@localhost:5672/` | RabbitMQ (`rabbit` profile only) |
| `AAKAR_TEST_JDBC_URL` | | `jdbc:postgresql://127.0.0.1:5432/aakar_test` | Integration-test database (`AAKAR_TEST_DB_USER` / `AAKAR_TEST_DB_PASSWORD` optional) |
| `SPRING_PROFILES_ACTIVE` | | `direct` | `direct` or `rabbit` |

`aakar.pricing.*` in `application.yml` is the pricing-policy **seed** (copied from `packages/design-tokens/materials.json →
pricing_policy`).

## Endpoints

| Method | Path | Notes |
|---|---|---|
| GET | `/api/catalog/items?category&q` | Shop items (available first) |
| GET | `/api/catalog/items/{slug}` | 404 `not_found` |
| GET | `/api/catalog/materials` | Available digital materials with PBR presets and rates (materials paused in the portal are hidden) |
| GET | `/api/templates` · `/api/templates/{id}` | Descriptors cached from geometry for 60 s; the list hides templates switched off in the portal |
| GET | `/media/{key}` | A stored media file (QC photos) |
| POST | `/api/designs` | 202 `DesignAccepted`; owned by the bearer's user or the `X-Aakar-Guest` id. `source=shop` + `catalog_item_slug`; `source=create|remix` + `template_id` (+ `params`, `material`); any `prompt` → 422 `not_yet_available` |
| GET | `/api/designs/{id}` · `/api/designs/{id}/versions` | Design with latest version (status derived from it); history newest first |
| GET | `/api/versions/{versionId}` | Version, with `price` for its own material once ready |
| POST | `/api/versions/{versionId}/params` | New version + job; 422 `param_out_of_range` lists keys, never clamps |
| GET | `/api/versions/{versionId}/printability` | 409 `version_not_ready` while generating or failed |
| GET | `/api/versions/{versionId}/price?material=` | 409 while not ready; 404 `unknown_material` |
| GET | `/api/jobs/{jobId}` · `/api/jobs/{jobId}/events` | Job status; SSE `stage` stream with replay after `Last-Event-ID` |
| POST | `/api/auth/otp/request` | 202 `{request_id, expires_in_s, dev_code?}`; 429 `otp_rate_limited` |
| POST | `/api/auth/otp/verify` | 200 `Session` (token + `attached`); 401 `otp_invalid` / `otp_expired` |
| GET · PATCH | `/api/auth/me` | Current user; update `name` / `email` |
| POST | `/api/auth/logout` | 204; revokes the token's session |
| GET · POST | `/api/me/addresses` | List (default first); 201 create — the first address becomes the default |
| PUT · DELETE | `/api/me/addresses/{addressId}` | 404 for another customer's address; deleting the default promotes the oldest |
| GET · DELETE | `/api/cart` | The identity's cart, re-priced against the active policy; empty it |
| POST | `/api/cart/items` | 201; 404 unknown version, 409 `version_not_ready` / `not_printable`, 422 `unknown_material` |
| PATCH · DELETE | `/api/cart/items/{itemId}` | Change `qty` and/or `material` (re-prices; folds into an existing line for that finish); remove |
| GET | `/api/shipping/serviceability?pincode=` | `{pincode, serviceable, carrier, eta_days, cod_available}`; 400 for a malformed pincode |
| POST | `/api/checkout` | 201 `CheckoutResult`; 409 `cart_empty` / `not_printable`, 422 `not_serviceable` |
| GET | `/api/orders` · `/api/orders/{orderId}` | The customer's orders newest first; one order (404 unless theirs) |
| GET | `/api/orders/{orderId}/events` | SSE `stage` stream of `OrderEvent`s; closes after `delivered` / `cancelled` |
| POST | `/api/orders/{orderId}/payments` | 201 new `Payment` while `pending_payment`; 409 `order_not_payable` |
| GET | `/api/payments/{paymentId}` | 404 unless the customer's |
| POST | `/api/payments/{paymentId}/mock/complete` | `{outcome: success|failure, method?}` → confirmed `Payment`; 409 `payment_final` |
| POST | `/internal/jobs/{jobId}/callback` | Direct-profile callback (`envelope.v1.json`); not for public exposure |

Errors are RFC 9457 Problem Details (`application/problem+json`) with a stable `code`:
`not_found`, `validation_failed`, `not_yet_available`, `param_out_of_range`, `template_not_available`,
`version_not_ready`, `unknown_material`, `geometry_unavailable`, `unauthenticated`, `forbidden`, `otp_invalid`,
`otp_expired`, `otp_rate_limited`, `not_printable`, `cart_empty`, `not_serviceable`, `payment_final`,
`order_not_payable`, `invalid_transition`, `policy_version_exists`, `order_not_packed`, `material_exists`, `slug_exists`,
`payload_too_large`, `internal_error`.

## Modules (`studio.aakar.api.*`)

`catalog` · `templates` · `design` · `studio` · `pricing` · `media` · `identity` · `cart` · `order` · `payment` ·
`shipping` · `notification` · `admin` · `shared` (open). Public API lives in each module's root package; everything under
`internal` is private and enforced by `ModularityTests`. Cross-module calls go through public services
(`Designs`, `Carts`, `Orders`, `Payments`, `Shipments`, `Notifications`, `Users`, `Addresses`, `PricingPolicyStore`,
`ShippingCarrier`) or application events, and the graph is acyclic:

- `design → studio` starts jobs; `studio` publishes `GenerationCompleted` / `GenerationFailed` which `design` applies in the same transaction.
- `identity → design, cart` for the sign-in hand-over (`Designs.attachGuest`, `Carts.mergeGuestCart`).
- `cart → design, catalog, pricing`; it empties itself on `payment.PaymentSucceeded`.
- `order → cart, identity, design, payment, shipping, notification`; it listens for `PaymentSucceeded` / `PaymentFailed` from `payment`, which depends on nothing above `shared`.
- `admin → order, design, payment, shipping, notification, pricing, catalog, templates, media, identity`; nothing depends on `admin`.
- `shared` holds the per-request `Identity`, the generic `SseHub<E>` behind both the job and the order streams, Problem Details (and the filter-chain `ProblemResponses`), CORS, the clock and the production guard.

## Schema

Flyway `V1` (catalog, materials, designs, versions, jobs, job events, outbox), `V2`/`V3` seeds, `V4` `pricing_policies`
(+ seed row), `V5` `users`, `otp_requests`, `sessions`, `addresses`, `designs.guest_id`, `V6` `carts`, `cart_items`,
`V7` `orders`, `order_items`, `order_events`, `payments`, `shipments`, `notifications` and the `order_number_seq` /
`invoice_number_seq` sequences, `V8` `staff_accounts`, `audit_log`, `media_assets`, `template_flags`, `share_codes`,
`materials.available` / `updated_at`, `notifications.order_id` / `rendered_text`, `pricing_policies.note`.

## Docker

```sh
docker build -f services/api/Dockerfile -t aakar-api .     # from the monorepo root
```
