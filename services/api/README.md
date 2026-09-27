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
or a local server. Flyway creates the schema and seeds the six Shop items on their six shelves, six materials, the 25 outcome
families (Avatars) with their seven bought-in hardware items, the pricing policy, the seven viewer backdrops (Mahaul) and the five
experiences (Duniya) on start.
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
service is stubbed with WireMock (`support/GeometryStub`): six descriptors from `fixtures/templates.json` (the Jharokha
stand, the four planar carriers `keychain_tag`, `fridge_magnet`, `hanging_ornament`, `desk_nameplate` and `raw_print` with its
volume anchor; the five are copies of `packages/contracts/examples/template-descriptors.json`, guarded by
`TemplateFixturesTest`) and a `POST /v1/build` that answers with `packages/contracts/examples/design.completed.example.json`
(ids rewritten from the request; `arch_cusps` 3 = slow build, 4 = ready but `printability.passed=false`, 7 = failed build).
Builds keyed on `$.spec.template` (`keychain_tag@1`, `fridge_magnet@1`, `raw_print@1`) echo the request's spec (features
included) with the template's `hardware` (the magnet reports `magnet_count` magnets) and a small estimate; a `hero_mesh`
(`$.spec.features[0].type`) whose URL contains `broken` fails with `content_unusable`. `support/SampleFiles` makes small
genuine files of every upload format.
Tests that read monorepo files (`packages/contracts`, `packages/design-tokens/materials.json`, `families.json` and
`experiences.json`) skip with a message when those files are missing; `-Daakar.repo.root=…` overrides the monorepo location.

| Test | Covers |
|---|---|
| `ModularityTests` | Spring Modulith `verify()` (module boundaries, no cycles) and module docs → `build/spring-modulith-docs` |
| `pricing/PriceCalculatorTest` | PLAN §7.10: board example (84 g · 3 h 40 m · Terracotta Silk → ₹389 + ₹733 + ₹120 = ₹1,249, unchanged under the carriers policy without a context), rounding to a rupee ending in 9, free-shipping threshold, matte vs silk, explicit policy, schema validation; plan §5: one hardware line at cost + markup, the setup line, the family minimum lifting a small piece (`minimum_subtotal_paise`) |
| `pricing/internal/DbPricingPolicyStoreTest` | ADR-0008: active policy from the table, 30 s cache, `publish` deactivates/activates, duplicate version → 409, empty table seeds from `aakar.pricing` |
| `identity/internal/SessionServiceTest` | JWT issue → parse → revoke; tampered, foreign-key, expired, unknown-session and wrong-user tokens rejected |
| `identity/internal/OtpServiceTest` | 6-digit code, hashed at rest, `dev_code` exposure, 5-minute expiry (`otp_expired`), 5 requests / 15 min (`otp_rate_limited`), 5 wrong attempts (`otp_invalid`), single use |
| `cart/internal/CartMergeTest` · `CartPricingTest` | Sign-in merge (same version+material adds up, cap 20), purchasable rules, re-price trigger, specs line, line totals |
| `order/OrderNumbersTest` · `OrderStatusTest` · `internal/OrderTransitionsTest` | `AK-000001`, status → stage mapping, the §11.1 transition table (hold, reprint, cancel, finals) |
| `order/internal/OrderLifecycleTest` | `advance`: events, defaults, 409 `invalid_transition`, 404, shipment at `packed`, tracking at `shipped`/`delivered`; payment success → confirmed + queued; payment failure event |
| `payment/internal/InvoiceNumbersTest` · `MockPaymentGatewayTest` | `INV-2026-000001`, mock `pay_url` and `mock_` refs |
| `shipping/internal/MockDelhiveryTest` | Serviceability rules and `MOCK` + 10-digit AWBs |
| `shared/ProductionGuardTest` | `aakar.profile=production` refuses every mock adapter, the exposed dev code, the dev JWT secret and `aakar.uploads.scanner=noop` (the default terms scanner passes) |
| `templates/internal/TemplateParamValidatorTest` | `param_out_of_range` with every offending key listed |
| `studio/internal/EnvelopeMapperTest` | `design.generate` envelope, payload parsing, Rabbit topology (no broker) |
| `DesignFlowIntegrationTest` | Shop → job → ready version (assets, printability, price, spec valid), params edit (422 / new version), prompt → `not_yet_available`, failed build, live SSE + idempotent callbacks, Problem Details codes |
| `CustomerLoopIntegrationTest` | The whole loop above end to end, failed payment + retry, unserviceable / empty / unprintable checkout, cart rules and re-pricing on a policy change, 401 / owner-only 404s, addresses, OTP validation and rate limit, OpenAPI paths |
| `AdminLoopIntegrationTest` | Management API (ADR-0012): seeded owner sign-in, staff vs customer tokens (401 both ways), dashboard, queue filters and `next_actions`, queued → delivered with events, messages (`printing_timelapse`, `shipped`, `delivered`) and audit, print pack zip (WireMock serves the model files), QC photo upload (+ 413), packaging card 409 → `%PDF` and the share code, pricing publish (409 / 422) and preview, materials (hidden from the storefront), catalog, template live switch (422 `template_not_available`), messages and audit pages, `studio` role 403 on configuration |
| `admin/internal/StaffTokensTest` · `identity/internal/JwtTokensTest` | Staff tokens are `typ: staff`; the customer parser refuses them and the staff parser refuses customer tokens |
| `admin/internal/StaffPrincipalTest` · `DashboardServiceTest` · `PrintSheetTest` · `PrintPackTest` · `ShareCodesTest` · `PackagingCardTest` | Owner-only gating (403), studio-day windows and `awaiting_action`, print-sheet text with Hardware and Content rows (labels, never a file URL), zip contents, missing-file notes and the packing list adding up hardware × quantity, 8-char base32 codes with collision retry, PDF bytes start with `%PDF` |
| `MaterialsSeedTest` | `V3__seed_materials.sql`, `aakar.pricing` (incl. `hardware_markup_pct`, `family_rules`) and the seeded policy row (`2026-10-carriers`, `V9`) match `packages/design-tokens/materials.json`; the `V4` row reads back without rules |
| `FamiliesSeedTest` | `V9__families_hardware_uploads.sql`: `shelves`, `hardware_items`, every `template_families` row (JSONB columns included) and the Shop items' `family_id` backfill match `packages/design-tokens/families.json` |
| `CatalogFamiliesIntegrationTest` | Shelves in order (storefront and portal), `GET /api/families` lists only available + ready families (`kind` filter, 400), family detail with hardware names and live descriptors (anchor `kind`/`size_mm`/`bounds_mm`/`accepts`, `hardware`, `min_feature_mm` round-trip; old fixtures unchanged), unavailable families resolve by id but are flagged, 404 `unknown_family`, responses validate against `template-family.v1.json` and `template-descriptor.v1.json`, Shop items refuse an unknown shelf (422 listing the shelves) and an unknown family (422 `unknown_family`) |
| `UploadsIntegrationTest` | `POST /api/uploads` for every image and model format (sha256, served URL, owner folder without the guest id), 413 over 15 MB, 422 `unsupported_format` for a `.txt`, a kind mismatch or a renamed file, 400 / 401 / 415, owner-only `GET` (404 otherwise), guest uploads moving to the user on sign-in; a flagged file name → `pending_review`, a design with it → 409 `upload_not_ready`, the queue with owner and reason, a `studio` reviewer approves → `ready` → the design starts; a rejection → the note as `message`, 422 `upload_rejected`; 409 `review_already_decided`, queue filters, audit `review.decide` |
| `CarrierDesignFlowIntegrationTest` | Walk (1): a guest's photos on both faces of a keychain (`family_id` + `features`), the spec carrying each photo by its internal URL (never the client's), the build request WireMock saw, the version's named split ring, the price with the hardware line and the ₹249 keychain minimum, cart and checkout snapshots, the print pack's Hardware/Content rows and `packing-list.txt`; the magnet's reported `magnet_count` hardware, edits that keep (`features` absent), replace or clear the content, stranger uploads 404; `unknown_family`, `family_not_available`, template of another family, a name on the keychain's back (accepted since the PR 3b descriptors) but not a motif or a 3D form (customer labels), relief depth cap, model-as-photo, 8-feature cap, default template/finish, a prompt as the working title, family material rules |
| `RawPrintIntegrationTest` | Walk (2), Swaroop: `source: upload` + `raw_print` + one `hero_mesh` (fit longest 80) → ready with the `setup` line; resize by feature, finish swap keeps the form; missing form, 10 / 245 mm (never clamped), no fit, template params, a photo as the model, a relief on the body, wrong source → 4xx; a model the geometry stub cannot repair → job `failed` with `content_unusable` |
| `templates/internal/FeatureValidatorTest` | Every feature rule (type supported, anchor exists and accepts, surface vs volume, one photo or form per anchor, `max_relief_mm` with the lithophane exemption, family text length counting Devanagari without marks ("नमस्ते" = 4), the raw family's single form inside 20–240 mm, contract shapes and ranges) and customer-worded details |
| `media/internal/UploadFormatsTest` · `TermsContentScannerTest` · `templates/TemplateFixturesTest` | Magic-byte sniffing of every format and renamed or damaged files; flagged terms ignoring case, spaces and punctuation; fixtures equal the exported descriptors |
| `AdminFamiliesIntegrationTest` | Owner creates and updates hardware and families (201/200; 409 `hardware_exists` / `family_exists`; 422 for an unknown shelf, backdrop, hardware SKU or allowed material, a `min > max` envelope, bad enums), the path id wins on PUT, an available family without a live template stays off the storefront, the pricing preview with a `family_id` (hardware line, minimum, setup) and `family_rules` checks, `studio` role 403, audit `family.create|update` and `hardware.create|update` with before/after |
| `ExperiencesSeedTest` | `V11__experiences_environments.sql`: every `environments` row and every `experiences` row (JSONB `surface`, `motif_pack`, `collections`, `season` included) with its ordered `experience_avatars` and `experience_items` match `packages/design-tokens/experiences.json`; `catalog_items`, `template_families` and `experiences` reference `environments`; `designs.experience_id` exists |
| `ExperiencesIntegrationTest` | `GET /api/experiences` lists Utsav, Adda, Yaadein, Masti in order (Katha is unavailable) with only their orderable avatars expanded in order (lithophane, figurine, keycap… have no live template in the stub, so they are left out there and kept in the portal rows), curated items, `price_from_paise` = the lowest avatar floor, rows valid against `experience.v1.json`; slugs resolve any experience (Katha flagged), 404 `unknown_experience`; `GET /api/environments` and the portal copy; owner CRUD (201/200, 409 `experience_exists` / `slug_exists`, 422 for an unknown backdrop naming the known ones, unknown family, unknown item, repeats, mixed or backwards or impossible season windows, bad style, slug or accent), path id wins, a reorder, a new slug and the switch off leave the Shop, defaults, `studio` 403, audit `experience.create|update` before/after; families and Shop items accept the comic rooftop and refuse an unknown backdrop; designs keep `experience_id` (Shop and Avatar paths, 422 `unknown_experience`) |

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
`X-Aakar-Guest` identity: designs and uploads with that `guest_id` get `owner_id`, the guest cart merges into the user cart (same
version + material → quantities add up, capped at 20); `attached.designs` / `attached.cart_items` report the counts.

Public routes: `/api/catalog/**`, `/api/families/**`, `/api/experiences/**`, `/api/environments/**`, `/api/templates/**`,
`/api/designs/**` (designs stay readable by their unguessable id),
`/api/versions/**`, `/api/jobs/**`, `/api/cart/**` and `/api/uploads/**` (guest or user; 401 with neither header), `/api/auth/otp/*`,
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
preview, content reviews) and reads configuration — writes to pricing, materials, catalog, families, hardware, experiences and
templates answer 403 `forbidden`.

**Endpoints.**

| Method | Path | Notes |
|---|---|---|
| POST | `/admin/api/auth/login` | 200 `StaffSession`; 401 `unauthenticated` |
| GET | `/admin/api/auth/me` | `Staff` |
| GET | `/admin/api/dashboard` | `orders_by_status` (every status), `orders_today`, `revenue_today_paise`, `revenue_month_paise` (non-cancelled orders with a succeeded payment, Asia/Kolkata days), `awaiting_action` = queued + finishing + qc |
| GET | `/admin/api/orders?status&q&page&size` | Newest first; `status` comma-separated; `q` = number prefix (`AK-0001`, `000012`) or phone digits; `{items, page, size, total}`, `size` ≤ 100; items carry `customer`, `materials`, `next_actions` |
| GET | `/admin/api/orders/{orderId}` | `AdminOrder`: the customer `Order` + `customer`, `next_actions` (transition table), `qc_photos`, `notifications` |
| POST | `/admin/api/orders/{orderId}/advance` | `{status, message?, detail?}` → `AdminOrder`; defaults "Slicing your piece", "Printing", "Hand sanding & sealing", "Quality check", "Packed", "Shipped", "Delivered"; 409 `invalid_transition`; records `printing_timelapse` / `shipped` / `delivered` messages; audit `order.advance` |
| GET | `/admin/api/orders/{orderId}/print-pack` | `application/zip`, `Content-Disposition: attachment; filename="AK-000001-print-pack.zip"`; per item `item-<n>/print-sheet.txt` + `model.3mf` + `model.stl`, and `packing-list.txt` at the root |
| POST | `/admin/api/orders/{orderId}/qc-photos` | multipart `file` (≤ 10 MB, else 413 `payload_too_large`) + `note` → 201 `MediaAsset`; audit `order.qc_photo` |
| GET | `/admin/api/orders/{orderId}/packaging-card.pdf` | One-page A6 PDF; 409 `order_not_packed` before `packed` |
| GET · POST | `/admin/api/pricing/policies` | History (newest first, with policy body and note); publish `{version, policy, note?}` → 201 (owner; 409 `policy_version_exists`, 422 `validation_failed`, 422 `unknown_family` when `policy.family_rules` names a family that is not in the catalog); audit `pricing.publish` (before = previous active). The policy body may carry `hardware_markup_pct` and `family_rules` |
| GET | `/admin/api/pricing/policies/active` | The active version |
| POST | `/admin/api/pricing/preview` | `{policy, material, extruded_volume_cm3, print_seconds, family_id?}` → `PriceBreakdown` with `policy_version: preview`; a `family_id` (422 `unknown_family` when not seeded) prices a piece of that family: its default hardware at the draft's markup, its setup fee and its minimum |
| GET · POST | `/admin/api/materials` | All incl. unavailable; create (owner; 409 `material_exists`) |
| PUT | `/admin/api/materials/{materialId}` | Replace (owner; 404); `available: false` hides it from `GET /api/catalog/materials` (existing carts and orders keep pricing) |
| GET · POST | `/admin/api/catalog/items` | All items; create (owner; 409 `slug_exists`, 422 `validation_failed` for a `category` that is not a shelf or an `environment` that is not a backdrop, 422 `unknown_family` / `unknown_material`) |
| PUT | `/admin/api/catalog/items/{slug}` | Replace (owner; 404) |
| GET | `/admin/api/catalog/shelves` | Shelves in display order: the valid `category` (items) and `shelf` (families) values |
| GET · POST | `/admin/api/families` | Every outcome family (Avatar) with `ready` and `template_ids`; create (owner; 409 `family_exists`; 422 `validation_failed` for an unknown shelf or backdrop (`environment`, `studio` when absent) or a `min > max` envelope; 422 `unknown_hardware` / `unknown_material` for unknown SKUs or `material_rules.allowed` ids); audit `family.create` |
| PUT | `/admin/api/families/{familyId}` | Replace copy, tier, shelf, envelope, hardware, rules, content slot, availability (owner; the path id wins; 404 `unknown_family`); audit `family.update` |
| GET · POST | `/admin/api/hardware` | Bought-in hardware items with cost and weight; create (owner; 409 `hardware_exists`); audit `hardware.create` |
| PUT | `/admin/api/hardware/{sku}` | Replace (owner; 404); audit `hardware.update` |
| GET · POST | `/admin/api/experiences` | Every experience (Duniya) as stored — `avatars` family ids and `items` slugs in order, available or not, `updated_at`; create (owner; 409 `experience_exists` / `slug_exists`; 422 `validation_failed` for an unknown backdrop (naming the known ones) or Shop item, a repeated avatar, item, motif or collection, a season window that is not two dates in order or two month-days; 422 `unknown_family`); audit `experience.create` |
| PUT | `/admin/api/experiences/{experienceId}` | Replace copy, slug, backdrop, style, motif pack, avatars (order), items, collections, seasons, availability, sort order (owner; the path id wins; 404 `unknown_experience`; 409 `slug_exists` when another experience has the slug); audit `experience.update` |
| GET | `/admin/api/environments` | The viewer backdrops (Mahaul), read-only: the valid `environment` values |
| GET | `/admin/api/templates` | Geometry descriptors merged with `template_flags` (`live` default true) and the slugs using each |
| PUT | `/admin/api/templates/{templateId}` | `{live}` (owner; 404); `live: false` hides it from `GET /api/templates` and makes `POST /api/designs` answer 422 `template_not_available` |
| GET | `/admin/api/uploads?status&limit` | Customer uploads newest first (`status` ready · pending_review · rejected, 422 otherwise; `limit` 1–200, default 50) as `AdminUpload`: the `Upload` fields with a `url` staff can open in any status, `owner {user_id, guest_id, phone}`, `origin` and the latest `review {id, reason, status, decision_note, reviewer_email, decided_at}` |
| POST | `/admin/api/content-reviews/{reviewId}` | `{decision: approved|rejected, note?}` (any staff role) → `AdminUpload`; approved → upload `ready`, rejected → `rejected` with the note as the customer's `message`; 404; 409 `review_already_decided`; audit `review.decide` (before/after review and upload status) |
| GET | `/admin/api/notifications?order_id&page&size` | Messages log (`NotificationRecord` with `order_id`, `rendered_text`), newest first, `size` ≤ 200 |
| GET | `/admin/api/audit?page&size` | `AuditEntry` rows (`staff_email`, `action`, `target`, `before`, `after`), newest first |

Schema violations on admin bodies answer **422** `validation_failed` (the storefront API uses 400). Every write records an
`audit_log` row (`order.advance`, `order.qc_photo`, `pricing.publish`, `material.create|update`, `catalog.create|update`, `family.create|update`,
`hardware.create|update`, `experience.create|update`, `template.live`, `review.decide`).

**Print pack.** Built in memory: for each order item a `print-sheet.txt` (order number, piece and version, template `id@version`
and params from the version spec, the **content** it carries by label — `photo relief (Chhavi) on face · text (Naam) "Asha" on
back`, never a file URL —, material and filament, finish class, quantity, the **hardware** packed with each piece —
`split_ring_25 × 1 · Steel split ring 25 mm` —, bounds, mass and estimated time from the snapshot, specs line, notes, and the
list of files) plus `model.3mf` and `model.stl` downloaded from the version's `assets` URLs; a file that cannot be fetched is
skipped and noted on the sheet. `packing-list.txt` at the root lists the pieces and adds up each SKU's per-piece count × the
item quantity across the order ("Hardware: none" when nothing is bought in). Printing is outsourced (ADR-0004).

**Packaging card.** PDFBox + zxing: "Aakar", "Designed by You. Crafted by Aakar.", the piece, its finish, "Printed in
Bengaluru · <date>", the order number and the reprint / remix link `{aakar.web.url}/k/<code>` as text and QR.

**Share codes.** `share_codes` (8-character base32 code, order, design, version) — minted once per order at the first card
request. The public storefront page for `/k/{code}` arrives later; the row already names what to open.

**Media.** QC photos and customer uploads go through the `media` module's `MediaStore`: files under `aakar.media.dir`
(default `./.aakar-media`) served publicly by their unguessable key at `{aakar.api.public-url}/media/{key}`; `media_assets`
keeps kind, order, key, URL, type, size and note of QC photos, `uploads` the customer files. The geometry service fetches
customer files at `{aakar.media.internal-base-url}/media/{key}` (defaults to the public URL). An S3 store is a drop-in
implementation (presigned URLs later).

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
| Content scanning | `media.ContentScanner` | `aakar.uploads.scanner` (`terms`) | Not a mock: flags an upload whose file name mentions one of `aakar.uploads.flag-terms` (empty by default; the seed of the trademark guardrail) into the review queue. `noop` flags nothing. A malware or image-moderation scanner is a drop-in `ContentScanner` (`ScanResult.malware` refuses the file, 422 `upload_rejected`) |

**Production guard.** `aakar.profile` is `local` by default. With `aakar.profile=production` the context refuses to start
(`shared/ProductionGuard`) while any adapter above is `mock`/`log`, `expose-dev-code` is `true`, the JWT secret is the
dev default, the staff seed password is the default `aakar-studio`, or `aakar.uploads.scanner` is `noop` — the message
names every offending property.

## Outcome families (Avatars)

The outcome a customer's idea becomes — a keychain, a fridge magnet, a nameplate, a phone stand, or their own model printed
as it is — is a **template family** promoted to data (`docs/research/outcome-categories/implementation-plan.md` §1). The
`catalog` module owns three tables seeded from [`packages/design-tokens/families.json`](../../packages/design-tokens/families.json)
(contract `packages/contracts/schemas/template-family.v1.json`; `FamiliesSeedTest` fails on drift):

| Table | Holds |
|---|---|
| `shelves` | Shop shelves (`id`, `label`, `sort_order`); `catalog_items.category` and `template_families.shelf` reference them, so `category` is validated against the table instead of a regex |
| `hardware_items` | Bought-in parts by `sku` (`name`, `unit_cost_paise`, `weight_g`, `supplier`, `url`, `notes`, `available`) |
| `template_families` | One row per family: `id` (= `family` in specs and descriptors, never renamed), brand copy (`codename` "Saathi", `name` "Keychain & bag charm", `tagline`, `description`), `kind` carrier · object · raw, `tier` launch · next · later, `shelf`, `demand_rank`, `default_template_id`, `environment`, and JSONB `size_envelope`, `hardware` (`[{sku, qty}]`), `material_rules`, `content_slot` (the Chhaap: `accepts`, `anchors`, `hero_volume`, `max_text_chars`), `shape_tolerance`, `available`, `sort_order` |

`Catalog.families()`/`family()` merge each row with the **live** descriptors of the templates module: `ready` is true when at
least one live template names the family, `templates[]` are those descriptors (the storefront picker) and `template_ids[]`
their ids (the portal); `hardware[].name` is filled from `hardware_items`. `GET /api/families` lists only families that are
`available` **and** `ready`, so switching a family on in the portal shows nothing until the geometry service publishes its
template (and hiding a template in the portal takes its family off the picker). Because readiness needs the descriptors, the
family endpoints answer 503 `geometry_unavailable` like `/api/templates` when the geometry service is down. `price_from_paise`
is the family's minimum subtotal under the active pricing policy (₹249 for keychains; null for a family without one): a floor
for "from ₹…" copy, not a quote. `TemplateDescriptor` mirrors the descriptor contract's new fields: anchor `kind`
(surface | volume), `size_mm`, `bleed_mm`, `bounds_mm`, `accepts`, `max_relief_mm`, plus `hardware[]` and `min_feature_mm`.
`catalog → templates` (readiness) and `catalog → pricing` (the floor) are its module edges; `catalog.internal` stays private
(`ModularityTests`). `catalog_items.family_id` is backfilled for the six seeded items.

## Experiences (Duniya) and viewer environments (Mahaul)

An **experience** is a theme on the Shop — a stage backdrop, a default style, a motif pack, ordered Avatars, curated Shop items
and seasons — that adds no geometry (`docs/research/outcome-categories/implementation-plan.md` §7–§8). The `catalog` module owns
four tables seeded from [`packages/design-tokens/experiences.json`](../../packages/design-tokens/experiences.json) by
`V11__experiences_environments.sql` (contract `packages/contracts/schemas/experience.v1.json`; `ExperiencesSeedTest` fails on drift):

| Table | Holds |
|---|---|
| `environments` | The backdrops every `environment` names: `id`, `label`, `surface` stage · paper, `preset_key` (the storefront viewer preset that renders it — presets are code, so a new backdrop is engineering plus a row), `palette` (JSONB swatches, backdrop colour first), `sort_order`. The six storefront backdrops plus `comic_rooftop_night` (its preset lands with Katha, PR 12). `template_families.environment`, `catalog_items.environment` and `experiences.environment` reference it, so the portal checks backdrops against the table (422 `validation_failed` naming the known ids) instead of a hard-coded list |
| `experiences` | `id` (snake_case, never renamed: `festive`, `desk_gaming`, `memories`, `kids_party`, `comics`), brand copy (`codename` "Utsav", `title` "Festive & gifting", `tagline`, `description`), unique `slug` (the URL `/duniya/<slug>`), `environment`, `style` (a design-spec style, incl. `comic_pop`), JSONB `surface` `{accent, paper_tint, hero_media}`, `motif_pack` (motif or pack ids), `collections` `[{id, title, licence_ref}]` and `season` `[{starts_on, ends_on, label}]` (two ISO dates in order, or two month-days `--10-01` that recur and may wrap the year), `available`, `sort_order` |
| `experience_avatars` · `experience_items` | The families an experience shows and the Shop items it curates, each with `sort_order`; a write replaces the whole ordered list |

`designs.experience_id` (nullable) remembers the experience a design started from: `POST /api/designs` takes an optional
`experience_id` (any known experience, on the Shop or not; 422 `unknown_experience` otherwise) and `Design.experience_id` echoes it,
so the karigar's note and packaging card can name the theme.

`GET /api/experiences` lists the `available` experiences by `sort_order` in the storefront shape: `avatars` are the experience's
families that can be ordered today (`available` and `ready`, the `GET /api/families` view with templates and `price_from_paise`)
in the experience's order, `items` its curated Shop items in order (unavailable ones keep their Coming soon state), and
`price_from_paise` the lowest floor among those avatars. `GET /api/experiences/{slug}` resolves any experience, available or not
(404 `unknown_experience`), so `/duniya/katha` can say "coming soon". Both need the live descriptors, like the family endpoints
(503 `geometry_unavailable` without the geometry service). `GET /api/environments` lists the backdrops. The portal edits the
rows through `/admin/api/experiences` with ids instead of expansions, so it round-trips what it shows. Seeded: Utsav, Adda,
Yaadein and Masti available; Katha (`comic_rooftop_night`, `comic_pop`, motif pack `comic_bursts`, no collections until a licence
exists) unavailable until PR 12 ships its backdrop preset.

## Content on designs (the Chhaap), uploads and Swaroop (plan §2, §4, §5; ADR-0014)

**Uploads.** `POST /api/uploads` (multipart `file` + `kind` image · model) needs a user or guest identity. The format is
taken from the extension and confirmed by the first bytes (png, jpg/jpeg, webp, heic; stl binary or ASCII, glb, 3mf zip,
obj, ply, off, gltf): anything else, a kind mismatch or a renamed file is 422 `unsupported_format`; photos over
`aakar.uploads.max-image-bytes` (15 MB) and models over `max-model-bytes` (50 MB) are 413 `payload_too_large`. The file is
hashed (sha256), passed through the `ContentScanner` and stored at `uploads/<owner>/<upload id>.<format>`, where `<owner>` is
a one-way digest of the identity (a guest id is a guest's only credential, so it never appears in a URL). A clean file is
`ready` with its browser `url`; a flagged one is `pending_review` (no `url`) with a `content_reviews` row for the portal.
`GET /api/uploads/{id}` is owner-only (404 for anyone else); after a rejection `message` carries the reviewer's note. Signing
in moves a guest's uploads to the user (`Uploads.attachGuest`, beside designs and the cart).

**Designs with content.** `POST /api/designs` takes `family_id` (+ optional `template_id`, else the family's
`default_template_id`) and `features` (at most 8, `design-spec.v1.json#/$defs/feature`). The family must exist (404
`unknown_family`) and be `available` and `ready` (422 `family_not_available`); a `template_id` must belong to it (400). The
finish must be one the template offers and pass the family's `material_rules` (allowed list, heat-safe only, excluded finish
classes; 422 `unknown_material` naming the rule); without one the first allowed finish is used. `Templates.validateFeatures`
(`templates/internal/FeatureValidator`, a mirror of the geometry service's `features/validate.py`) checks and normalises the
features with the contract defaults — type in `features_supported`, anchor exists and `accepts` it, text/motif/photo on
surfaces and a customer's form on volumes, one photo or form per anchor, relief and emboss depth within `max_relief_mm`
(a lithophane relief is exempt), text within the family's `max_text_chars` counted without marks, never clamped: 422
`unsupported_feature`, `param_out_of_range` (with `params: ["features[i].field"]`) or `validation_failed`, worded with
customer labels ("photo relief (Chhavi)"). Each `content_source.upload_id` must be the caller's upload (404), `ready` (409
`upload_not_ready`, 422 `upload_rejected`) and of the right kind (422 `unsupported_format`); the API then writes the internal
`url`, `format` and `origin` from the upload, whatever the request said. `POST /api/versions/{v}/params` takes the same
`features` as a full replacement (`[]` clears; absent keeps the parent's); uploads already on the parent stay usable by
whoever edits it, new ones must be the caller's. A `prompt` without `family_id` is still 422 `not_yet_available`; beside a
family it becomes the working title. `designs.family_id` and `Design.family_id` / `DesignVersion.family_id` name the family.

**Swaroop** (ADR-0014): `source: upload` is only for `family_id: raw_print` and that family only for it (400 otherwise). The
template has no params; the one `hero_mesh` on `body` carries the size (`fit: longest`, `longest_mm` inside the family
envelope 20–240 mm, 422 `param_out_of_range` outside — never clamped) and `orientation`. A file the geometry service cannot
repair fails the job with `error_code` `content_unusable`.

**Hardware.** A version's `hardware` (`[{sku, qty}]` per piece) starts as the template descriptor's (else the family
default) and is replaced by the `design.completed` result's `hardware` when it reports any (a magnet with two pockets
reports two magnets). Responses name each part from `hardware_items`.

## Pricing policies (ADR-0008)

`pricing_policies` holds versioned policies; exactly one is active. `V4__pricing_policies.sql` seeds version
`2026-09-phase0` from the same figures as `aakar.pricing.*` (which is now only the seed and a fallback when the table
has no active row); `V9` deactivates it and seeds `2026-10-carriers`, which adds `hardware_markup_pct` (applied to
bought-in hardware unit costs) and `family_rules` (per family id: `minimum_subtotal_paise`, `setup_fee_paise`,
`qty_breaks`); policies published before those fields read back with a 0 markup and no rules
(`PricingPolicy.familyRuleFor(id)` returns an empty rule). `PriceCalculator.price(estimate, material, policy, context)` adds,
for a piece of a family (`PriceInputs.Context(familyId, hardware)`, assembled by `Designs.priceContext(version)` for version
prices, cart lines and checkout snapshots), one `hardware` line — Σ qty × unit cost × (1 + markup), labelled
"Steel split ring 25 mm · 1" — and a `setup` line ("Studio setup", the raw print's repair and orientation fee); the subtotal
is then lifted to the family minimum (rounded like any subtotal) and `minimum_subtotal_paise` says so. Every breakdown names
its `family_id`. The overloads without a context price the print alone, so earlier numbers never moved (quantity breaks wait
for a cart discount line). `PricingPolicyStore.active()` (Caffeine, 30 s) feeds `PriceCalculator`; `publish(policy, createdBy)`
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
| `AAKAR_MEDIA_DIR` | `aakar.media.dir` | `./.aakar-media` | Local media store (QC photos, customer uploads), served at `/media/{key}` |
| `AAKAR_MEDIA_INTERNAL_BASE_URL` | `aakar.media.internal-base-url` | blank = `aakar.api.public-url` | The API origin the geometry service fetches customer uploads from (`content_source.url`), e.g. `http://api:8080` in Compose while the public URL is the browser's |
| `AAKAR_UPLOADS_SCANNER` | `aakar.uploads.scanner` | `terms` | `terms` or `noop` (refused in production) |
| `AAKAR_UPLOADS_FLAG_TERMS` | `aakar.uploads.flag-terms` | empty | Comma-separated words that send an upload whose file name mentions them to the review queue |
| | `aakar.uploads.max-image-bytes` · `max-model-bytes` | `15MB` · `50MB` | Upload limits (`spring.servlet.multipart.max-file-size` / `max-request-size` are 52 MB) |
| `AAKAR_CORS_ORIGINS` | `aakar.cors.origins` | `http://localhost:3000,http://localhost:3100` | Allowed browser origins (storefront, portal) |
| `AAKAR_AMQP_URL` | `spring.rabbitmq.addresses` | `amqp://aakar:aakar@localhost:5672/` | RabbitMQ (`rabbit` profile only) |
| `AAKAR_TEST_JDBC_URL` | | `jdbc:postgresql://127.0.0.1:5432/aakar_test` | Integration-test database (`AAKAR_TEST_DB_USER` / `AAKAR_TEST_DB_PASSWORD` optional) |
| `SPRING_PROFILES_ACTIVE` | | `direct` | `direct` or `rabbit` |

`aakar.pricing.*` in `application.yml` is the pricing-policy **seed** (copied from `packages/design-tokens/materials.json →
pricing_policy`, including `hardware-markup-pct` and `family-rules`, whose family keys are bracketed so Boot keeps their underscores).

## Endpoints

| Method | Path | Notes |
|---|---|---|
| GET | `/api/catalog/items?category&q` | Shop items (available first) |
| GET | `/api/catalog/items/{slug}` | 404 `not_found` |
| GET | `/api/catalog/materials` | Available digital materials with PBR presets and rates (materials paused in the portal are hidden) |
| GET | `/api/catalog/shelves` | Shop shelves (catalog categories) in display order; the valid `category` values |
| GET | `/api/families?kind` | Outcome families (Avatars) that are `available` and `ready` (≥ 1 live template), in display order, each with its live template descriptors and hardware names; `kind` = carrier · object · raw (400 otherwise). Needs the geometry service, like `/api/templates` |
| GET | `/api/families/{id}` | Any seeded family with its `available` / `ready` flags (a deep link can say "coming soon"); 404 `unknown_family` |
| GET | `/api/templates` · `/api/templates/{id}` | Descriptors cached from geometry for 60 s; the list hides templates switched off in the portal |
| GET | `/media/{key}` | A stored media file (QC photos, customer uploads) |
| POST | `/api/uploads` | Multipart `file` + `kind` (image · model) → 201 `Upload` (`ready`, or `pending_review` when flagged); 401 without an identity; 413 `payload_too_large`; 422 `unsupported_format`; 400 bad `kind` or empty file |
| GET | `/api/uploads/{id}` | The caller's own upload (`message` explains a rejection); 404 for anybody else |
| POST | `/api/designs` | 202 `DesignAccepted`; owned by the bearer's user or the `X-Aakar-Guest` id. `source=shop` + `catalog_item_slug`; `source=create|remix` + `template_id` (+ `params`, `material`); `family_id` (+ `template_id`) + `features` (the Chhaap); `source=upload` + `family_id: raw_print` + one `hero_mesh` (Swaroop); 404 `unknown_family`, 422 `family_not_available`, 409 `upload_not_ready`, 422 `upload_rejected` / `unsupported_feature` / `param_out_of_range`; a `prompt` without `family_id` → 422 `not_yet_available` |
| GET | `/api/designs/{id}` · `/api/designs/{id}/versions` | Design with latest version (status derived from it); history newest first |
| GET | `/api/versions/{versionId}` | Version, with `family_id`, named `hardware` and `price` for its own material once ready |
| POST | `/api/versions/{versionId}/params` | New version + job; optional `features` replaces the content (`[]` clears, absent keeps); 422 `param_out_of_range` lists keys, never clamps |
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
`payload_too_large`, `unknown_family`, `family_not_available`, `family_exists`, `hardware_exists`, `unknown_hardware`,
`unsupported_format`, `unsupported_feature`, `upload_not_ready`, `upload_rejected`, `review_already_decided`, `internal_error`;
a job's `error_code` may also be `content_unusable` (the geometry service could not repair a customer's model).

## Modules (`studio.aakar.api.*`)

`catalog` · `templates` · `design` · `studio` · `pricing` · `media` · `identity` · `cart` · `order` · `payment` ·
`shipping` · `notification` · `admin` · `shared` (open). Public API lives in each module's root package; everything under
`internal` is private and enforced by `ModularityTests`. Cross-module calls go through public services
(`Designs`, `Carts`, `Orders`, `Payments`, `Shipments`, `Notifications`, `Users`, `Addresses`, `PricingPolicyStore`,
`ShippingCarrier`) or application events, and the graph is acyclic:

- `design → studio` starts jobs; `studio` publishes `GenerationCompleted` / `GenerationFailed` which `design` applies in the same transaction.
- `identity → design, cart, media` for the sign-in hand-over (`Designs.attachGuest`, `Uploads.attachGuest`, `Carts.mergeGuestCart`).
- `design → media` resolves content sources through `Uploads`; `media` depends on nothing above `shared`.
- `cart → design, catalog, pricing`; it empties itself on `payment.PaymentSucceeded` and prices lines with `Designs.priceContext`.
- `catalog → templates` for family readiness (the live descriptors) and `catalog → pricing` for `price_from_paise`; `templates` and `pricing` depend on nothing above `shared` (family limits reach the feature checks as `templates.FamilyLimits`).
- `order → cart, identity, design, payment, shipping, notification`; it listens for `PaymentSucceeded` / `PaymentFailed` from `payment`, which depends on nothing above `shared`.
- `admin → order, design, payment, shipping, notification, pricing, catalog, templates, media, identity`; nothing depends on `admin`.
- `shared` holds the per-request `Identity`, the generic `SseHub<E>` behind both the job and the order streams, Problem Details (and the filter-chain `ProblemResponses`), CORS, the clock and the production guard.

## Schema

Flyway `V1` (catalog, materials, designs, versions, jobs, job events, outbox), `V2`/`V3` seeds, `V4` `pricing_policies`
(+ seed row), `V5` `users`, `otp_requests`, `sessions`, `addresses`, `designs.guest_id`, `V6` `carts`, `cart_items`,
`V7` `orders`, `order_items`, `order_events`, `payments`, `shipments`, `notifications` and the `order_number_seq` /
`invoice_number_seq` sequences, `V8` `staff_accounts`, `audit_log`, `media_assets`, `template_flags`, `share_codes`,
`materials.available` / `updated_at`, `notifications.order_id` / `rendered_text`, `pricing_policies.note`, `V9` `shelves`
(+ `catalog_items.category` FK), `hardware_items`, `template_families` (all seeded from `families.json`),
`catalog_items.family_id` (backfilled), `designs.source` gains `upload` + `designs.family_id`, `design_versions.hardware`,
`uploads`, `content_reviews`, and the active pricing policy `2026-10-carriers`.

## Docker

```sh
docker build -f services/api/Dockerfile -t aakar-api .     # from the monorepo root
```
