# Plan: Modular / Hybrid products ("Jod"): a bought-in base + a 3D-printed pluggable Chhaap

## Context

Aakar's research and the shipped outcome-categories work (`docs/research/outcome-categories/`, PROGRESS.md) established that **labour and postage, not plastic, set the floor** on small prints, and that **large everyday objects are slow and expensive to print whole** (a 300 g headphone stand is ~9 printer-hours at the placeholder rates in `packages/design-tokens/materials.json`). The owner wants a third product shape next to "fully printed" and "print as it is": a **mass-produced or fixed base item** (steel headphone stand, key-hook plate, LED lamp base, dashboard mount, steel bottle, acrylic soap box, terracotta planter) plus a **small 3D-printed Chhaap that plugs into it** through a standard connector. Hybrids must be promoted heavily, while the fully-printed path stays alive.

What the codebase already gives us (reuse, don't rebuild):

- **Avatars are data.** `template_families` (`V9__families_hardware_uploads.sql`, contract `packages/contracts/schemas/template-family.v1.json`) carry `kind ∈ {carrier, object, raw}`, `shape_tolerance`, a `hardware[]` BOM referencing `hardware_items` (`sku, unit_cost_paise, weight_g, supplier`), `material_rules`, `size_envelope_mm`, `content_slot`. Staff edit all of it in `apps/admin` (Avatars, Hardware pages).
- **Anchors are the attachment contract.** `template-descriptor.v1.json#/$defs/anchor` has `kind surface|volume`, `size_mm/bounds_mm`, `accepts`, `modes`, `required`; `services/geometry/aakar_geometry/templates/base.py` enforces them; `features/` fuses Naam, Buti, Chhavi (relief) and Roop (hero_mesh).
- **Bought-in parts and pockets already exist**: `fridge_magnet.py` cuts a `10.2 × 3.2 mm` pocket for `magnet_d10x3`; `lithophane_plate.py` cuts a `70 + 0.6 mm` cradle for the `led_base_usb` puck (Roshni is already a hybrid in all but name); `plates.py` holds `HOLE_ALLOWANCE_MM = 0.2`, `RIM_MM = 2.0`, `MIN_SKIN_MM = 1.2`.
- **Pricing already has `hardware` and `setup` lines**, `hardware_markup_pct`, per-family `family_rules` (`minimum_subtotal_paise`, `setup_fee_paise`) in a versioned policy (`pricing_policies`, ADR-0008); `price-breakdown.v1.json` snapshots into cart and order.
- **Duniyas curate families** (`experiences.json` → `experience_avatars`), so a hybrid family joins a world by data alone.
- **The viewer loads one GLB** (`apps/web/src/components/viewer/Model.tsx` fits the longest side to 1 unit; `DesignViewer.tsx` frames it); materials come from `lib/viewer/materials.ts`; the geometry service exports Z-up → Y-up GLB (`exports.py`).
- **Print pack and packaging card** already list hardware (`admin/internal/PrintSheet.java`, `PrintPack.java` `packing-list.txt`).
- **The original data model wanted this**: PLAN.md §9 lists `designs … base_item_id`, never implemented; the planned `night_light_base@1` was never registered either (the puck base is fused into `lithophane_plate`). The kind enum is hard-coded in four places that must widen together: `template_families.kind` CHECK, `FamilyDto.KINDS` (`catalog/FamilyDto.java:51`), `FamilyInput` regex `^(carrier|object|raw)$` (`catalog/FamilyInput.java:24`), and `KIND_RANK` in `apps/web/src/lib/families.ts:98`.
- **Inspect judges one mesh and never sees the material** (`services/inspect/aakar_inspect/settings.py` `Constraints` L18 carries only min wall, overhang, bed, tipping margin; `core.py inspect_mesh` L30). The only customer-exposed fit parameter today is `keycap_mx.stem_slop_mm` (0.3 resin / 0.4 FDM), a precedent we deliberately do not repeat: Kadi compensation is staff-calibrated data, not a slider.

Intended outcome: a **data-driven hybrid product line** where a family of kind `hybrid` names a standard **Kadi** connector, the bought-in **base items** declare which Kadi they present and carry their own 3D model, stock and cost, the geometry service builds only the Chhaap (with a connector compensated for the chosen material and verified by a new printability check), the price shows the base, the print and the assembly as honest separate lines, and the storefront shows the fixed base and the editable Chhaap as two visibly different things, all without touching the fully-printed path.

Naming (same rules as before: code ids are English snake_case, brand words are portal data): kind `hybrid` → **Jod** (जोड़, the join); the standard connector → **Kadi** (कड़ी, link); the bought-in product → **Buniyaad** (base) in copy, `base_items` in code; the printed part stays **Chhaap**. Open decision 1 below.

---

## Research inputs

- Report: [`report.md`](report.md) (mechanical research: the four Kadi standards, per-use-case mapping, tolerance guidelines and the fit-coupon loop; UX: viewer separation, discovery patterns, Duniya placement).
- Prior work this builds on: `docs/research/outcome-categories/` (carriers, hardware BOM, pricing floors, engineering standards), ADR-0008 (versioned pricing), ADR-0009 (parametric-first), ADR-0014 (the raw print exception).
- Codebase exploration, 4 Oct 2026: catalog and pricing modules, inspect checks, geometry templates, storefront viewer and Duniya pages (file references throughout).

---

## 3. Architecture and data model

### 3.1 Catalog structure (contracts first, then `V15`, then the `catalog` module, then the portal)

**Contracts** (`packages/contracts`, all additive, `spec_version` stays `"1.0"`):

- `template-family.v1.json`: `kind` enum adds `hybrid`; family gains `connector {kind: socket|dovetail|magnet|thread|rim_clip, nominal: string, fit: slide|press_ribbed|snap|pocket}`, `bases: [{sku, default}]`, `assembly_minutes`; new `$defs/base_item` and top-level `base_items[]` in `families.json`.
- `template-descriptor.v1.json`: optional `connector {kind, nominal, fit, axis, seat_mm, flange_d_mm}` and `keep_clear` volumes (the saddle).
- `design-spec.v1.json`: optional `base {sku}` so the geometry service can check compatibility and the karigar's note can name the base; `events/design.completed.v1.json` echoes `base` and `connector_fit`; `design.failed` enum adds `incompatible_base`.
- `printability-report.v1.json`: optional `checks.connector_fit`.
- `price-breakdown.v1.json`: `lines[].code` adds `base`, `assembly`; optional `base_sku`, `shipping_weight_g`.
- OpenAPI (`aakar-api.v1.yaml`, `aakar-admin.v1.yaml`): `GET /api/bases`, `GET /api/bases/{sku}`, `GET /api/families/{id}` expands `bases[]` with price and stock state; `POST /api/designs` and `POST /api/versions/{v}/params` accept `base_sku`; admin CRUD `/admin/api/bases`, `/admin/api/bases/{sku}/stock`, `/admin/api/fit-tests`.

**Migration `V15__hybrid_bases.sql`** (same style as `V9`):

```sql
ALTER TABLE template_families DROP CONSTRAINT template_families_kind_check;
ALTER TABLE template_families ADD CONSTRAINT template_families_kind_check CHECK (kind IN ('carrier','object','raw','hybrid'));
ALTER TABLE template_families ADD COLUMN connector jsonb, ADD COLUMN assembly_minutes integer;

CREATE TABLE base_items (
  sku varchar(40) PRIMARY KEY, name varchar(120) NOT NULL, description text,
  category varchar(20) NOT NULL CHECK (category IN ('desk','home','lighting','auto','kids','bath','plants')),
  base_material varchar(20) NOT NULL,                 -- steel | aluminium | wood | plastic | acrylic | terracotta
  dimensions_mm jsonb NOT NULL, weight_g numeric(8,1) NOT NULL,
  unit_cost_paise bigint NOT NULL CHECK (unit_cost_paise >= 0),
  retail_price_paise bigint,                          -- staff override; null → cost × (1 + base_markup_pct)
  supplier varchar(120), supplier_sku varchar(80), url varchar(500), lead_days integer NOT NULL DEFAULT 7,
  stock_qty integer NOT NULL DEFAULT 0, reserved_qty integer NOT NULL DEFAULT 0, reorder_level integer NOT NULL DEFAULT 5,
  interface jsonb NOT NULL,                           -- {kind, nominal, tolerance_class, measured_mm:{...}, adapter_sku}
  mount_frame jsonb NOT NULL,                         -- {origin_mm:[x,y,z], up:[..], forward:[..]}
  preview_glb_key varchar(300), preview_glb_url varchar(500), photos jsonb NOT NULL DEFAULT '[]',
  finish_pbr jsonb NOT NULL, hsn_code varchar(8), compliance_notes varchar(300),
  available boolean NOT NULL DEFAULT false, sort_order integer NOT NULL DEFAULT 100,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now());

CREATE TABLE family_bases (family_id varchar(40) REFERENCES template_families(id), base_sku varchar(40) REFERENCES base_items(sku),
  is_default boolean NOT NULL DEFAULT false, sort_order integer NOT NULL DEFAULT 100, PRIMARY KEY (family_id, base_sku));

CREATE TABLE fit_tests (id uuid PRIMARY KEY, base_sku varchar(40) REFERENCES base_items(sku), material_id varchar(40) REFERENCES materials(id),
  template_id varchar(80) NOT NULL, coupon_version integer NOT NULL, measured jsonb NOT NULL,   -- socket mouth/floor Ø, rib crest, pull-off N
  passed boolean NOT NULL, note varchar(300), tested_by varchar(254), tested_at timestamptz NOT NULL DEFAULT now());

ALTER TABLE design_versions ADD COLUMN base_sku varchar(40) REFERENCES base_items(sku);
ALTER TABLE cart_items ADD COLUMN base_sku varchar(40);          -- snapshot alongside price_snapshot
ALTER TABLE order_items ADD COLUMN base_sku varchar(40), ADD COLUMN base_snapshot jsonb;
ALTER TABLE materials ADD COLUMN xy_hole_comp_mm numeric(4,2), ADD COLUMN xy_outer_comp_mm numeric(4,2), ADD COLUMN elephant_foot_mm numeric(4,2),
  ADD COLUMN shrink_pct numeric(4,2), ADD COLUMN max_strain_pct numeric(4,2), ADD COLUMN creep_class varchar(8);
CREATE TABLE base_stock_movements (id uuid PRIMARY KEY, base_sku varchar(40) NOT NULL, kind varchar(12) NOT NULL
  CHECK (kind IN ('receipt','reserve','release','consume','adjust')), qty integer NOT NULL, order_id uuid, note varchar(200),
  created_at timestamptz NOT NULL DEFAULT now(), created_by varchar(254));
-- new active pricing policy '2026-11-jod' derived from the active one (V14 pattern) with base_markup_pct, assembly fees, shipping tiers
```

Seed rows (`families.json` → `V15`; the `BasesSeedTest` drift test waits for Phase 3.5): bases `headphone_stand_steel_12`, `hook_plate_steel_4`, `led_lamp_base_e27`, `dash_mount_magnetic`, `soap_box_acrylic_120x80`; hardware `dowel_nylon_12x30`, `steel_disc_40`, `vhb_pad_25x40`, `rail_petg_40`, `insert_brass_1420`; hybrid families from 1.3 with `available=false` except `headphone_topper` after the PoC.

**Catalog module** (`studio.aakar.api.catalog`, the owner of families, shelves, hardware and experiences; Modulith graph stays acyclic): `BaseItemEntity/Repository`, `FamilyBaseEntity`, `FitTestEntity`, public `BaseItemDto`, `Catalog.bases()/base(sku)/basesFor(familyId)/compatible(family, base)`; `FamilyDto.KINDS` and the `FamilyInput` regex add `hybrid`; `CatalogService.validateFamily` (L263-286, today checks hardware SKUs, envelope and materials) also checks the connector and base refs; `FamilyDto.orderable()` (`FamilyDto.java:64`, today `available && ready`) requires at least one available base for kind `hybrid`, which is what `GET /api/experiences` uses to expand a Duniya's avatars; `priceFrom` (`CatalogService.java:312`, today `PriceCalculator.minimumSubtotal`) adds the default base's price so "from ₹…" is honest. `ProblemCodes` add `UNKNOWN_BASE`, `BASE_NOT_AVAILABLE`, `INCOMPATIBLE_BASE`, `BASE_OUT_OF_STOCK`, `BASE_REQUIRED`. `design/internal/DesignService.create/editParams` resolve `base_sku` (default from `family_bases.is_default`) and refuse a family of kind `hybrid` without a base; `expectedHardware` (L433-442) adds the base's `interface.adapter_sku`. `order`: stock reserve on `paid`, consume on `packed`, release on payment failure or cancel; `OrderStatus` (`order/OrderStatus.java`) gains `assembling(OrderStage.sanding)` between `finishing` and `qc`, `OrderTransitions` allows it only for orders with a base, and the storefront `OrderStageTimeline`/`StagePill` and the portal advance panel learn the new status.

**Print pack**: `PrintPack.sheet()` (L85-101) already re-reads the version; it adds the base and adapter to `PrintSheet.Item` (new `base` field, rows "Base" and "Kadi"), `packing-list.txt` (L107-133) totals bases per SKU beside hardware, and a new `assembly-sheet.txt` per item carries the fit-test id and the karigar's assembly sentence (today that sentence never reaches the sheet). `PackagingCard.Content` gets an optional "with a steel stand" line.

**Portal (`apps/admin`)**: new **Bases** page (`/bases`, `BasesPage`/`BaseDrawer`: name, category, material, dimensions, weight, cost, retail override, supplier, lead days, stock with a receipt form and the movements ledger, interface editor with tolerance class and measured dims, mount-frame editor with a live preview against a Chhaap, GLB and photo upload, finish PBR picker, HSN, compliance notes, `available`); **Avatars** drawer gains kind `hybrid`, connector picker, base multi-select with default, assembly minutes; **Fit tests** tab on a base (coupon measurements, pass/fail → unlocks `(base, material)`); **Pricing** policy form gains `base_markup_pct`, per-family `assembly_fee_paise`, `shipping_tiers`; **Orders** detail shows the assembly checklist; print pack gains `assembly-sheet.txt` (base sku, adapter, Kadi size, fit-test id, torque/press note, QC photo of the assembled piece). Every write is owner-only and audited (`AdminCatalogController` pattern, actions `base.*`, `fit_test.*`, `stock.*`).

### 3.2 Dynamic pricing engine

Extend `pricing/PriceCalculator` (today: material + machine_time + finishing + [packaging] + [hardware] + [setup] → ×(1 + margin) at L80-82 → round up to a 9 → family minimum at L84-88) with two lines and weight-tiered shipping. `PriceInputs.Context(familyId, hardware)` gains `base` (sku, unit cost, retail override, weight) and `assembly_minutes`; `Context.NONE` stays, so every current number is unchanged (the existing `PriceCalculatorTest` board example keeps passing untouched). The context is assembled in `design/internal/VersionMapper.context()` (L84-97, which already resolves hardware SKUs against `hardware_items`) and reaches the cart through `Designs.priceContext` (`DesignService.java:191-193`); both read `design_versions.base_sku`. Shipping today is recomputed on the cart subtotal in `CartService.render` (L204-207) with `CartPricing.lineTotal = subtotal × qty`; the tiered version sums `shipping_weight_g × qty` across lines instead.

```
inputs: estimate(grams, print_seconds), material, family_rules[family_id], hardware[{sku, qty}], base?, policy

material_paise   = grams × rate_per_g[material]
machine_paise    = print_seconds / 3600 × machine_rate_paise_per_hour
finishing_paise  = finishing_fee[finish_class]
hardware_paise   = Σ qty × unit_cost × (1 + hardware_markup_pct/100)          -- dowel, steel disc, VHB, magnets
base_paise       = base.retail_price_paise ?? base.unit_cost × (1 + base_markup_pct/100)   -- shown as its own line "Steel stand"
assembly_paise   = family_rules[family].assembly_fee_paise ?? assembly_minutes × labour_rate_paise_per_minute
setup_paise      = family_rules[family].setup_fee_paise ?? 0
packaging_paise  = packaging_fee_paise + (base ? base_packaging_fee_paise : 0)    -- a box, not a mailer

printed_subtotal = round9((material + machine + finishing + hardware + assembly + setup + packaging) × (1 + margin_pct/100))
printed_subtotal = max(printed_subtotal, family_rules[family].minimum_subtotal_paise)
subtotal         = printed_subtotal + base_paise          -- the base is never marked up again by margin or lifted by the minimum

shipping_weight_g = grams + base.weight_g + Σ hardware.weight_g + packaging_g
shipping_paise    = subtotal ≥ free_shipping_above_paise ? 0 : tier(shipping_tiers, shipping_weight_g, volumetric(dimensions))
total             = subtotal + shipping
availability      = base ? (stock_qty − reserved_qty > 0 ? "in stock" : lead_days ? "ships in N days" : unavailable) : n/a
```

Policy additions (`materials.json → pricing_policy`, `PricingPolicy`, `PricingPolicyInput`, admin schema, `application.yml aakar.pricing`): `base_markup_pct` (35 placeholder), `labour_rate_paise_per_minute` (500), `base_packaging_fee_paise` (2500), `shipping_tiers: [{max_g: 500, paise: 7900}, {max_g: 1000, paise: 12900}, {max_g: 2000, paise: 17900}]`, `family_rules.<hybrid>.assembly_fee_paise`. Snapshots (`cart_items.price_snapshot`, `order_items`) already store the whole `PriceBreakdown`, so the new lines ride along; `base_sku` and `base_snapshot` are stored beside them so a later price or stock change never moves an order.

Worked example at today's placeholder rates (Terracotta Silk ₹4.63/g, ₹200/h, silk finishing ₹120; base cost and labour are placeholders):

| Line | Fully printed `headphone_stand` (≈300 g, 9 h) | `headphone_topper` Jod (≈45 g, 1.6 h) + steel stand |
|---|---|---|
| Material | ₹1,389 | ₹208 |
| Print time | ₹1,800 | ₹320 |
| Finishing | ₹120 | ₹120 |
| Hardware (dowel ₹8 × 1.3) | — | ₹10 |
| Assembly (6 min × ₹5) | — | ₹30 |
| Printed subtotal (round to 9) | ₹3,309 | ₹689 |
| Base (cost ₹320 × 1.35) | — | ₹432 |
| **Subtotal** | **₹3,309** | **₹1,121** |
| Printer hours per order | 9.0 | 1.6 |

GST: a Jod line mixes goods under different HSN codes (the base vs a custom-printed plastic part), so invoicing per line is part of the ADR-0008 chartered-accountant review; `base_items.hsn_code` is there for it.

### 3.3 Geometry service

- New package `services/geometry/aakar_geometry/connectors/`: `base.py` (`Connector` dataclass: kind, nominal, fit, axis, seat; `descriptor()`; `cut_into(flange_part, material_comp)`; `keep_clear()`), `socket.py` (bore + 3 crush ribs + chamfers + optional bead groove), `dovetail.py`, `magnet_register.py` (reuses the fridge-magnet pocket code moved into `plates.py`), `thread.py` (ISO/UNC profile sweep in build123d; E27/E14 collar as a plain hole with a clamping lip), `rim_clip.py` (PETG-gated), `compensation.py` (per-material table from `materials.json`, loaded like `families.py`), `coupon.py` (`kadi_coupon@1`).
- `templates/base.py`: `Template.connector: ClassVar[Connector | None]`; `descriptor()` publishes it; `build()` cuts the connector after `build_body` and before features; `validate_content` gets `keep_clear` enforcement for volume anchors.
- New templates: `headphone_topper@1` (saddle body from `plinth_round` + Kadi-S-12), `hook_plaque@1` (from `desk_nameplate` + Kadi-D or Kadi-M), `lamp_shade_e27@1` (jaali shell + E27 collar), `dash_idol@1`, `drain_lid@1`, `bottle_cap_cover@1` (gated).
- `pipeline.py` `build_design`: pass `spec.base` to the template for compatibility (`incompatible_base`), echo `base` and the connector descriptor into the payload so inspect can run `connector_fit`; the GLB stays the Chhaap alone (the base GLB is a static asset).

---

---

## 4. Implementation roadmap

Every phase ends with a commit and push to `ccr-2ba8e715-ihp5b0` and a `PROGRESS.md` entry naming the next step (the rules that survived the last plan: `wip(...)` checkpoints, at most two agents at a time, commit after every green step).

**Testing policy for this feature (owner's call, 4 Oct 2026): no new unit or integration tests while the phases are moving.** The shapes of the connector module, the base tables and the price lines will change between Phase 1 and Phase 3, and tests written early would be rewritten each time. Each phase is instead verified by: `make contracts` (schema validation of the new examples), CLI builds of the new templates, `make descriptors` drift, typecheck · lint · build for both apps, **the existing suites staying green** (update an existing test only when the change would otherwise break it, e.g. `MaterialsSeedTest` for the new material columns; never extend it), and the live smoke walks listed per phase. New tests for the whole feature land in **one hardening pass (Phase 3.5)** once the surfaces have settled.

### Phase 0 — Decide and record (done 4 Oct 2026)

1. Commit this plan as `docs/research/hybrid-products/implementation-plan.md` with a `README.md` and `report.md` (the research in §1–§2), add **ADR-0015 "Hybrid products: bought-in bases with a standard Kadi interface"** (Proposed), add the row to `docs/adr/README.md`, add an "Hybrid products (Jod)" phase table to `PROGRESS.md`, note it in `CHANGELOG.md`.
2. Owner decisions (below) recorded in the plan.

### Phase 1 — Proof of concept (≈ 2–3 weeks, geometry + inspect + bench only; nothing customer-facing)

| Step | Work | Done when |
|---|---|---|
| 1.1 | `connectors/` package: `Connector`, `socket.py` (Kadi-S), `compensation.py` reading six PLA rows from `materials.json`, `coupon.py` + `kadi_coupon@1` | `uv run aakar-geometry build` of the coupon spec emits watertight STLs for Ø 8/12/16; a one-off CLI measurement script prints as-modelled socket radii = nominal + compensation and rib angles at 120°; existing geometry suite (665) still green |
| 1.2 | `headphone_topper@1`: saddle body, volume anchor `front` with `keep_clear` crown, surface anchor `face`, Kadi-S-12 under the flange; family row `headphone_topper` kind `hybrid`, `available=false`; CLI example `examples/headphone-topper.spec.json` | `uv run aakar-geometry build …` emits GLB/3MF/STL at the default and corner params; a spec with a hero mesh above the crown is refused with the saddle sentence (checked by hand from the CLI); `make descriptors` drift clean |
| 1.3 | inspect: `connector_fit` check + contract field; wall sampling restricted to the connector cylinder | existing inspect suite (21) still green; the CLI run of 1.2 shows `connector_fit` pass on the default topper and fail on a hand-edited 1.4 mm socket wall |
| 1.4 | Bench: source 20 steel pillar stands and 50 nylon dowels; caliper the tube IDs; print coupons in all six finishes; assemble, pull-off, 24 h creep at 45 °C, drop test from desk height with 400 g headphones on the saddle | ≥ 18/20 bases accept the dowel; 10/10 coupons seat with 20–60 N and hold ≥ 3× weight; compensation table updated from measurements and committed |
| 1.5 | Write up: `docs/research/hybrid-products/fit-tests.md` with the measurements; `families.json` compensation values | PROGRESS updated; go/no-go for Phase 2 |

### Phase 2 — Core platform update (≈ 3–4 weeks, additive; fully printed flows untouched)

| PR | Scope | Verification (no new tests; see the policy above) |
|---|---|---|
| 2.1 Contracts | §3.1 schema and OpenAPI changes, examples, `validate.mjs`, regenerated `schema.d.ts` ×2 | `make contracts`; `pnpm --filter @aakar/web gen:api && pnpm --filter @aakar/admin gen:api`; `make test-web` (typecheck · lint · build) |
| 2.2 Seed + `V15` + catalog | `families.json` base items and hybrid families, `V15__hybrid_bases.sql`, `BaseItemEntity`/DTOs, `Catalog.bases*`, `GET /api/bases`, compatibility rule, `materials` compensation columns | Flyway migrates on a fresh `make infra` database; `curl /api/bases` and `/api/families/headphone_topper` return the seed; `./gradlew test` stays green (only `MaterialsSeedTest`/`FamiliesSeedTest` touched, to match the new JSON) |
| 2.3 Design + pricing + order | `DesignService` base resolution and 422s, `DesignSpecs` writes `spec.base`, `PriceCalculator` base/assembly lines and shipping tiers, `VersionMapper.context` and `Designs.priceContext` carry the base, `CartService`/`OrderService.checkout` snapshots (`cart_items.base_sku`, `order_items.base_snapshot`), stock reserve/consume/release, `OrderStatus.assembling` + `OrderTransitions`, `PrintSheet` Base/Kadi rows, `PrintPack` `assembly-sheet.txt` | Live walk against the local stack (`make smoke-jod`, a script like `make smoke-avatars`): create a topper design with the steel base → price shows Base and Assembly lines and the ~800 g shipping tier → cart → OTP → checkout → mock pay → stock 20 → 19 → advance through `assembling` → print pack zip contains `assembly-sheet.txt`; `./gradlew test` still green (the board example in `PriceCalculatorTest` unchanged) |
| 2.4 Admin API + portal | `/admin/api/bases`, stock, fit tests; Bases page, Avatars kind `hybrid`, Pricing fields, Orders assembly checklist, mock admin API | `make test-web`; portal walk in Chromium: create a base, receive stock, record a fit test, flip `available`, preview the price with and without the base |
| 2.5 Geometry wiring | `pipeline.py` base compatibility + payload echo; `features/validate` keep-clear; descriptors re-exported (`make descriptors`) | CLI build with a mismatched `spec.base` fails with `incompatible_base`; `make descriptors` drift clean; existing geometry suite green |

### Phase 3 — UX rollout (≈ 3–4 weeks, behind `available` flags per family and base)

| PR | Scope |
|---|---|
| 3.1 Viewer | `HybridModel.tsx`, `DesignViewer` `base` prop, fixed treatment, lift-off toggle, legend; stability card `connector_fit` row |
| 3.2 Create + studio | `FamilyPicker` "Jod" ribbon and "from ₹…" including the base; `ContentComposer` base variant picker; `DesignStudio` "Whole or plug-in?" compare for paired families; `api.bases`, `lib/bases.ts`, mock API |
| 3.3 Shop + Home + Duniya | `/shop/base/[sku]` "Swap the top" page with snap-on; Shop filter chips; Home "Jod of the week" hero; `promote` on experiences; Ghar and Safar seeds (`V16`), Duniya page ribbon |
| 3.4 Cart, tracking, share | cross-sell card; tracking shows the Assembling stage; `/k/{code}` shows base + Chhaap and "another top for this base" |
| 3.5 Hardening (the one test pass) | Now that the surfaces are settled, write the feature's tests in one go, following the repo's existing patterns: geometry `test_connectors.py`, `test_templates_headphone_topper.py`, `test_coupon.py`; inspect `test_connector_fit.py`; API `BasesSeedTest`, `HybridDesignFlowIntegrationTest` (WireMock stub keyed on `$.spec.template == 'headphone_topper@1'`), `PriceCalculatorTest` Jod case, `StockIntegrationTest`, `PrintPackTest` assembly sheet, `ModularityTests`; `make smoke-jod` kept as the live walk |
| 3.6 Launch | flip `headphone_topper` and `headphone_stand_steel_12` to `available=true`; `make smoke-jod` passes live; metrics: hybrid share of orders, printer-hours per order, attach rate, fit complaints |

### Phase 4 — Scale (after launch)

Kadi-D, Kadi-T and Kadi-C templates; a PETG finish (unlocks clips, outdoor and bath); `hook_plaque`, `lamp_shade_e27`, `drain_lid`, `bottle_cap_cover`; `dash_idol` once a heat-safe material exists; supplier purchase orders and the stock ledger UI; the kids compliance track; `trellis_clip`; partner fulfilment for bases shipped direct from the supplier.

---

---

## Verification (end to end, after Phase 3)

During Phases 1–3 the checks below run with the **existing** suites only (regression, not coverage); the new tests arrive in Phase 3.5.

1. `make contracts` → `gen:api` ×2 → `cd services/inspect && uv run pytest -q` → `cd services/geometry && uv sync && uv run pytest -q` → `cd services/api && ./gradlew test` → `make test-web`.
2. Local stack (`make infra geometry api web admin`): Create → "Sur Jod · Headphone topper" → choose steel base → add a hero form above the crown → refused with the saddle sentence → place it in front → Sculpt → viewer shows the muted steel stand with a padlock and the terracotta topper; Apart view shows the 12 mm Kadi → price lists Material, Print time, Finishing, Hardware, Assembly, **Steel stand**, shipping tier for ~800 g → add to cart → checkout (mock pay) → portal shows stock 20 → 19 reserved, stage Assembling appears after Finishing → print pack zip has `assembly-sheet.txt` naming the dowel and fit test → packaging card QR → `/k/{code}` shows both parts.
3. Portal: set the base `available=false` → the family hides its base and, having no other base, disappears from the picker; change `base_markup_pct` → preview price moves only on the base line; a fit test marked failed for Indigo Matte → that finish chip disables for the topper.
4. Regression: `make smoke-avatars` and `make smoke-katha` still pass; the Jharokha stand prices to the paise as before (`PriceCalculatorTest` board example).

## Open decisions for the owner

| # | Item | Recommendation |
|---|---|---|
| 1 | Names: kind `hybrid` → Jod; connector → Kadi; base → Buniyaad; new worlds Ghar, Safar (slugs are URLs) | Adopt; codenames stay portal data |
| 2 | Add one PETG finish (clips, bath, outdoor, snap beads; also the research's keychain recommendation) | Add `petg_slate` in Phase 2 seed, `available=false` until calibrated |
| 3 | Dashboard products need a heat-safe material (PLA sags at 70 °C) | Keep `dash_idol` tier `later`; revisit with ASA/PETG |
| 4 | Bases: stock them (margin, control, 2-day promise) vs dropship from the supplier | Stock the PoC base; dropship flag on `base_items` in Phase 4 |
| 5 | Placeholder economics (base cost, markup 35 %, labour ₹5/min, shipping tiers) | Owner sets real values in the portal (ADR-0008) after the bench |
| 6 | Customer-owned bases (Kadi-C "fits what you already own") carry fit risk | Ship with a paper gauge, a reprint-first fit guarantee, and the class picker |
| 7 | GST per line (base HSN vs printed part) | Part of the ADR-0008 CA review before launch |

## Resume state

Sessions hit limits, so every phase ends with a commit and push plus a `PROGRESS.md` entry naming the next step; a new session starts by reading this plan and `PROGRESS.md`.

| Phase | Status | Resume pointer |
|---|---|---|
| 0 Decide and record | Done (4 Oct 2026): this folder, ADR-0015, PROGRESS and CHANGELOG entries | Owner decisions 1–7 above |
| 1 Proof of concept | Not started | Start at step 1.1: `services/geometry/aakar_geometry/connectors/` (`Connector`, `socket.py`, `compensation.py`, `coupon.py`); then `headphone_topper@1` |
| 2 Core platform | Not started | PR 2.1 contracts first (`make contracts`) |
| 3 UX rollout | Not started | PR 3.1 `HybridModel.tsx` |
| 4 Scale | Not started | after launch |
