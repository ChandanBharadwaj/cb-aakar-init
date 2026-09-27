# aakar-geometry

Template registry, CAD, exports, build pipeline, HTTP API and RabbitMQ worker (PLAN §7).
Turns a **Design Spec** (`packages/contracts/schemas/design-spec.v1.json`) into printable
geometry, exports GLB / 3MF / STL to storage, runs the inspect service and returns a
`design.completed` payload. No executable code ever crosses the spec boundary.

## CAD path

Templates are built with **build123d 0.13** (OCCT via `cadquery-ocp-novtk`): each template
describes itself as extruded 2D profiles (shapely) plus rigid transforms and booleans, and
`aakar_geometry/cad.py` is the only module that touches the kernel. Solids are tessellated
(0.05 mm / 0.3 rad) into a merged, watertight `trimesh.Trimesh` in mm, Z-up, sitting on Z = 0.

## Templates

`aakar_geometry/templates/` — `Template` base class with `descriptor()` (validated against
`template-descriptor.v1.json`), `validate(params)` (defaults filled, every out-of-range key
reported at once as `param_out_of_range`, unknown keys as `invalid_spec`; **never clamped**; then
`validate_combination`), `build(params, features=(), fetcher=None) -> trimesh.Trimesh`,
`karigar_note(params)`. The recipe for a new carrier is in [Features (Chhaap)](#features-chhaap).
Every template is registered in `templates/__init__.py`; `register()` refuses a family that is not
in `families.json`.

| Template | Family (codename) | Params: range (default) | Anchors: default printable size | Hardware |
|---|---|---|---|---|
| `jharokha_phone_stand@1` | `phone_stand` (Sahara) | see below | `side_left`, `side_right`, `back` | — |
| `keychain_tag@1` | `keychain` (Saathi) | `shape` rect · rounded · circle · heart (rounded); `width_mm` 30–60 (45), longest side with the loop; `thickness_mm` 2.4–4 (3); `hole_d_mm` 4–6 (4.2) | `face` (+Z) and `back` (−Z): 32 × 23.5 mm | `split_ring_25` ×1 |
| `fridge_magnet@1` | `fridge_magnet` (Chumbak) | `shape` rect · rounded · circle (rounded); `size_mm` 40–70 (55), longest side; `thickness_mm` 4.5–6 (5); `magnet_count` 1–2 (1) | `face` (+Z): 47.2 × 35 mm | `magnet_d10x3` × `magnet_count` |
| `hanging_ornament@1` | `ornament` (Jhoomar) | `silhouette` disc · star · bauble (disc); `diameter_mm` 50–90 (70), longest side; `thickness_mm` 3–5 (4); `border_mm` 0–6 (3), 0 or ≥ 1.5 | `face_front` (+Z) and `face_back` (−Z): 48.7 × 36.6 mm | `cord_200` ×1 |
| `desk_nameplate@1` | `nameplate` (Pehchaan) | `width_mm` 120–250 (180); `height_mm` 40–100 (60), face up the slope; `tilt_deg` 60–80 (70); `base_depth_mm` 30–60 (40); `thickness_mm` 4–8 (5) | `face` (tilted): 176 × 56 mm, text ≤ 36 mm; `base_front` (−Y): 178 × 8 mm | `adhesive_pads` ×1 |
| `raw_print@1` | `raw_print` (Swaroop) | none: size and orientation live on the `hero_mesh` feature | `body` (volume): 240 × 240 × 240 mm | — |

All five carriers publish `min_feature_mm` 0.8, constraints min wall 1.2 / overhang 55° / bed 250³,
and (until the text release, PR 3b) `features_supported: ["relief_image"]` with every surface
anchor taking a photo relief up to `max_relief_mm` 1.5; `raw_print` takes one `hero_mesh`.
Descriptors publish anchor sizes at the default parameters; `anchor_frame(anchor_id, params)` gives
the exact frame for any parameters.

### `jharokha_phone_stand@1` (family `phone_stand`)

| Param | Range | Default | Handle |
|---|---|---|---|
| `width_mm` | 70–110 | 92 | yes |
| `depth_mm` | 60–100 | 78 | yes |
| `height_mm` | 90–150 | 120 | yes |
| `tilt_deg` (back rest from horizontal) | 60–78 | 70 | |
| `lip_height_mm` | 8–18 | 12 | |
| `wall_mm` | 2.4–4.0 | 3.2 | |
| `arch_cusps` (integer) | 3–7 | 5 | |

Base plate; back rest leaning back by `tilt_deg` with a Mughal multifoil (cusped) arch window
cut through it; a parallel front lip; two side gussets joining lip, base and back rest; a 12 mm
cable notch through the lip and base. Overall bounds equal width × depth × height (within
kernel rounding). Anchors `side_left`, `side_right`, `back` (planar). Constraints min wall 1.2,
max overhang 55°, bed 250³. Materials: the six ids from `packages/design-tokens/materials.json`.
`features_supported` is empty (emboss/motif arrive with the text release, PR 3b: a spec with
features fails with `unsupported_feature`, as does a non-`none` style).

One coupled rule: a tall, shallow, far-leaning combination whose back rest would leave less than
12 mm of phone slot at the base fails with `param_out_of_range` on `depth_mm, height_mm, tilt_deg`.

Example (`packages/contracts/examples/jharokha-phone-stand.spec.json`): 92 × 78 × 120 mm,
44.6 cm³, 680 triangles, walls 3.2 mm, COG 2 mm inside the base, 37 mm tipping margin,
≈55 g of PLA, 3 h 33 by the heuristic estimator.

### The flat carriers: `keychain_tag@1`, `fridge_magnet@1`, `hanging_ornament@1`

Each lies face up: X across (the viewer's right), Y up the picture, the back on the bed at Z = 0 and
the face at Z = thickness, bounding box centred in X and Y. Outlines are shapely polygons extruded
by `cad.prism`; shared helpers live in `templates/plates.py` (circles and circumscribed holes, the
heart / star / bauble outlines, `symmetric_rect` for the printable area, the skin rule). The
printable rectangle is the one holding the largest 4:3 picture (phone photos) inside the face,
`ANCHOR_MARGIN_MM` 1.5 from the edge and clear of any hole and its rim (bleed 1 mm). A back anchor
is the same rectangle seen with the piece turned over left to right (u = −X, v = +Y, normal −Z),
so it stays right-handed and the loop stays at the top of the picture.

* **Skin rule** (`validate_content`): cut-in (deboss) content must leave 1.2 mm of plastic behind
  it; cut-ins on opposite faces add up; over a magnet pocket the pocket depth counts too. Raised
  (emboss) content never counts. Refused with `param_out_of_range` naming the feature keys and
  `thickness_mm`, before anything is built.
* **Keychain**: the outline is scaled so that the longest side *including the loop* is exactly
  `width_mm` (the family envelope, 30–60 mm). The ring hole is cut `hole_d_mm` + 0.2 mm (printed
  holes close up) as a circumscribed polygon, in a round loop above the plate (in the heart's
  notch) that keeps exactly 2 mm of plastic all round (a 25 mm split ring's 1.2 mm wire). Default:
  45 × 35.4 × 3 mm, 3.6 cm³, ≈4 g.
* **Magnet**: pockets 10.2 × 3.2 mm (a 10 × 3 mm magnet + 0.2 mm) open on the back, one in the
  middle or two at ±`size_mm`/4, each ≥ 2 mm from the edge and each other; 1.3–2.8 mm of plastic
  over them. `hardware_for(params)` packs `magnet_count` magnets, so the completed payload carries
  the right quantity. Default: 55 × 41.25 × 5 mm, ≈13 g.
* **Ornament**: `diameter_mm` is the longest side of the silhouette (star and bauble are scaled to
  it); the hanging hole (3.5 mm + 0.2 mm) sits as high as a 2 mm rim allows; the raised border
  stands 1 mm proud of the front only, so the back prints flat; a border between 0 and 1.5 mm is
  refused (it would print as a hair-thin line), as is a border that leaves less than 8 mm of
  picture. Default: 70 × 70 × 5 mm, ≈20 g.

### `desk_nameplate@1` (family `nameplate`)

A 10 mm foot (`width_mm` × `base_depth_mm`) with a `thickness_mm` plate leaning back at `tilt_deg`,
built like the Jharokha back rest: the face starts 3 mm behind the foot's front edge and its lower
back edge is buried in the foot. `face` frame: normal perpendicular to the plate, towards the reader
(`(0, −sin t, cos t)`), v up the slope. `base_front`: the foot's front strip. One coupled rule: the
(Y, Z) cross-section is the same along the width, so its centroid is the centre of gravity; a
combination whose centre of gravity sits less than 6.5 mm from the back of the foot (the inspect
service's 5 mm tipping threshold + 1.5 mm for content) is refused on `base_depth_mm, height_mm,
tilt_deg` (of the 32 parameter corners, only the four with a 100 mm face at 60° on a 30 mm foot).
Width stops at 250 mm, the bed, although the Pehchaan family envelope reaches 300 mm. Default:
180 × 40 × 66.4 mm, 18 mm tipping margin, ≈93 g.

### `raw_print@1` (family `raw_print`, Swaroop)

The customer's own model file printed as it is (ADR-0014): no params, `build_body` is empty, one
volume anchor `body` (the bed limited by the family envelope: 240³, seated on Z = 0 at the centre),
`features_supported: ["hero_mesh"]`, no hardware. `validate_content` refuses a spec without its
model (`invalid_spec`: "Add your model file to print it as it is", even when `features` is empty),
insists on `fit: longest` (so a raw print is never silently bed-sized), and refuses a `longest_mm`
outside the family envelope 20–240 mm (`param_out_of_range` on `features[i].longest_mm`, never
clamped; the contract itself allows 5–250). A model with walls under the printability threshold
still completes, with `printability.passed=false`.

## Pipeline (`build_design`)

`aakar_geometry/pipeline.py`: validate the request against `design.generate.v1.json` (and the
spec against `design-spec.v1.json`) → resolve `id@version` → check family / features / style /
material → validate params → `Template.validate_content(params, features)` → progress
`understanding` → progress `sculpting` → CAD body + features (with a timeout,
`AAKAR_BUILD_TIMEOUT_S`, default 120 s) → refuse an empty result (`build_error`, nothing is
exported) → export + store → progress `checking` → inspect (in-process by default, HTTP when
`AAKAR_INSPECT_URL` is set; the STL is then always exported) → assemble and validate the
`design.completed` payload, whose `hardware` is `Template.hardware_for(params)`. Failures return a
`design.failed` payload with the right `code`; nothing raises out of `build_design`.

* `understanding`/`sculpting`/`checking` progress envelopes (per `envelope.v1.json`, increasing
  `sequence`) are POSTed to `callback_url` when present. Callback failures are logged, never fatal.
  `pricing` and `ready` are emitted by the API service.
* A design whose checks fail (thin wall, tipping) still completes with `printability.passed=false`
  so the agent and the stability card see the details; only a non-manifold result is a
  `not_printable` failure.
* `outputs` `usdz` and `thumb` are accepted but not produced in Phase 0 (logged and skipped).

## Features (Chhaap)

Personal content on a template's anchors (plan §2), `aakar_geometry/features/`. It runs inside
`Template.build(params, features, fetcher)` after the body, in spec order:

1. **validate** (`features/validate.py`, also called up front by the pipeline): the type is in the
   template's `features_supported` and the anchor's `accepts`; photos and text go on surface anchors,
   a customer's own form (`hero_mesh`, Roop) only on volume anchors; one photo or form per anchor;
   `relief_mm` / `depth_mm` within the anchor's `max_relief_mm` (lithophane mode excepted); text within
   the family's `max_text_chars`. Contract defaults are filled in and echoed in the completed spec.
   Then the template's own `validate_content(params, features)` (skin under cut-ins, the raw print's
   model and size).
2. **fetch** (`features/fetch.py`): `ContentFetcher.fetch(source) -> bytes`. `HttpFetcher` streams
   `content_source.url` with timeouts and caps (photos 15 MB, models 200 MB) and refuses HTML pages;
   `LocalFileFetcher` reads `file://` URLs or a URL's file name inside a folder (tests, CLI
   `--content-dir`). The format comes from `source.format`, then the URL suffix, then magic bytes.
3. **relief / hero**: `relief_image` (Chhavi, `features/relief_image.py`): Pillow decode (HEIC
   through `pi-heif`, decode-only; `pillow-heif`, whose wheels are GPLv2, is a dev dependency that only
   encodes the test photo) → luminance × alpha → fitted into `size_mm − 2·bleed_mm` at ≈5 px/mm →
   smoothed → a watertight heightfield solid (`features/heightfield.py`) in the anchor frame, bright
   pixels highest (emboss) or cut deepest (deboss, mirrored so it reads right from outside);
   `lithophane` mode hands the heightmap to `Template.lithophane`. `hero_mesh` (Roop,
   `features/hero_mesh.py`): load with the inspect loaders → repair → decimate above 300k faces →
   orient (`lay_flat`, `yaw_deg`) → scale (`contain` into `bounds_mm`, or `longest` to `longest_mm`,
   never clamped) → seat on the anchor's bottom plane; alone when the body is empty (`raw_print`).
4. **booleans** (`features/booleans.py`): trimesh with the `manifold` engine; every result must be a
   closed, consistently wound volume. A boolean that fails is the content's problem, reported as
   `content_unusable` with a customer-safe message (photo, model file; never mesh or STL).

Content on the underside (a raised photo on a keychain's back) can reach below the bed; `build` lifts
the finished piece back onto Z = 0.

**Families** (`aakar_geometry/families.py`) load `packages/design-tokens/families.json`
(`AAKAR_FAMILIES_FILE` in the image; a built-in copy of the launch rows otherwise), validated against
`template-family.v1.json`. The registry refuses a template whose family is unknown;
`Template.materials()` applies the family's `material_rules` (`allowed`, `heat_safe_only`,
`excluded_finish_classes`); the content slot (`accepts`, `anchors`, `max_text_chars`) bounds the
features; `size_envelope(family)` is the longest-side range that the templates' tests hold them to
and that `raw_print` enforces; `default_hardware(family)` is what the family promises the customer
(a test holds every template to it).

**Recipe hook points** for a new carrier (`templates/<template_id>.py`, copied from
`keychain_tag.py` or `jharokha_phone_stand.py`):

| Hook | What it does |
|---|---|
| class attributes | `id`, `version`, `family`, `name`, `description`, `environment`, `params` (with `group`), `anchors` (`kind`, `size_mm`/`bounds_mm`, `bleed_mm`, `accepts`, `max_relief_mm`), `constraints`, `features_supported` ⊆ the family's `accepts` |
| `hardware` / `hardware_for(params)` | the bought-in parts cut for (`[{sku, qty}]`); override `hardware_for` when a parameter changes quantities (magnets) |
| `min_feature_mm` | smallest printable stroke (0.8 on a 0.4 mm nozzle) |
| `validate_combination(params)` | coupled parameter limits (a phone slot, a printable area, tipping) |
| `validate_content(params, features)` | limits coupling parameters and content, before any CAD (skin under a cut-in, a raw print's model) |
| `build_body(params)` | watertight mesh in mm, Z up, on Z = 0, centred in X/Y (empty only for `raw_print`) |
| `anchor_frame(anchor_id, params)` | a right-handed `AnchorFrame` (u = the viewer's right, v = up the picture, normal out of the body; origin on the skin, or the bottom centre of a volume) |
| `lithophane(...)` | only for templates whose plate *is* the photo |
| `karigar_note(params)` | one or two lines in the craft register |

Then register it in `templates/__init__.py` and add `tests/test_templates_<id>.py` using the shared
checks in `tests/carrier_checks.py` (descriptor vs family, envelope, frames on the skin, holes).

## Exports and storage

* **GLB**: scaled mm → metres (0.001) and rotated Z-up → Y-up (the stand's front faces +Z),
  neutral grey PBR material embedded. For three.js / `<model-viewer>`.
* **STL**: binary, mm. **3MF**: trimesh's writer (`unit="millimeter"`), with a minimal
  `[Content_Types].xml` / `_rels/.rels` / `3D/3dmodel.model` fallback writer.
* Keys `designs/{design_id}/v{n}/model.{glb,3mf,stl}`. `AAKAR_STORAGE=local` writes under
  `AAKAR_ASSET_DIR` and the API serves `GET /assets/{key}` with `AAKAR_PUBLIC_URL` as base;
  `AAKAR_STORAGE=s3` uses boto3 (`AAKAR_S3_BUCKET`, `AAKAR_S3_ENDPOINT` for MinIO,
  `AAKAR_S3_PUBLIC_URL`, standard `AWS_*` credentials; presigned URLs when no public URL).

## Environment

| Variable | Default | Purpose |
|---|---|---|
| `AAKAR_CONTRACTS_DIR` | `../../packages/contracts/schemas` | JSON Schemas (Dockerfile: `/contracts/schemas`) |
| `AAKAR_MATERIALS_FILE` | `../../packages/design-tokens/materials.json` | Material ids for descriptors (falls back to the built-in six) |
| `AAKAR_FAMILIES_FILE` | `../../packages/design-tokens/families.json` | Families (Avatars): registry, material rules, content slots, envelopes (falls back to the built-in launch rows) |
| `AAKAR_STORAGE` | `local` | `local` or `s3` |
| `AAKAR_ASSET_DIR` | `./.aakar-assets` | Local asset root |
| `AAKAR_PUBLIC_URL` | `http://localhost:8081` | URL base for local assets |
| `AAKAR_CORS_ORIGINS` | `http://localhost:3000` | Comma-separated browser origins allowed to fetch `/assets` (local storage only) |
| `AAKAR_S3_BUCKET`, `AAKAR_S3_ENDPOINT`, `AAKAR_S3_PUBLIC_URL`, `AWS_*` | — | S3 / MinIO |
| `AAKAR_INSPECT_URL` | unset (in-process) | Remote inspect service, e.g. `http://inspect:8082` |
| `AAKAR_AMQP_URL` | `amqp://aakar:aakar@localhost:5672/` | RabbitMQ for the worker |
| `AAKAR_BUILD_TIMEOUT_S` | `120` | CAD step timeout → `timeout` failure |

## Run

```sh
cd services/geometry
uv sync                                   # installs build123d (+ ../inspect as an editable path dep)
uv run pytest                             # tests
uv run aakar-geometry templates           # descriptors as JSON
uv run aakar-geometry build ../../packages/contracts/examples/jharokha-phone-stand.spec.json --out out/slice
uv run aakar-geometry serve               # http://localhost:8081
uv run aakar-geometry worker              # consumes geometry.design.generate
curl -s localhost:8081/v1/templates | jq '.[0].id'
curl -s -X POST localhost:8081/v1/build -H 'content-type: application/json' \
  -d '{"job_id":"6f1c1a2e-9d0b-4a8e-8c1e-2f3d4e5f6a7b","design_id":"0b8e7c6d-5a4b-4c3d-9e2f-1a0b9c8d7e6f","version_no":1,"spec":'"$(cat ../../packages/contracts/examples/jharokha-phone-stand.spec.json)"'}'
```

`build` writes `model.glb`, `model.3mf`, `model.stl` and `result.json` into `--out` and prints
one line: template, bounds, volume, triangles, passed, grams (spec material density, PLA 1.24),
print time and hardware.

With customer content, `--content-dir` serves the photos and model files named in the spec's
`content_source` URLs (matched by file name) instead of fetching them over HTTP:

```sh
# a keychain spec whose relief_image source URL ends in photo.png, with photo.png in ./content
uv run aakar-geometry build keychain.spec.json --content-dir ./content --out out/keychain
```

The committed `raw-print.spec.json` builds as it is (`--content-dir` holding a model file named
like its source URL). `keychain-photo.spec.json` also carries an `emboss_text` feature, which fails
with `unsupported_feature` until the text release (PR 3b); `tests/test_cli.py` builds it with its
photo only.

## Worker

`aakar_geometry/worker.py` (pika): declares the durable topic exchange `aakar.design` and the
durable queue `geometry.design.generate` bound with `design.generate`; for each envelope runs
`build_design` and publishes `design.progress` / `design.completed` / `design.failed` envelopes
to the same exchange with routing key = type. Acks after the result is published; nacks without
requeue when the message is not a valid `design.generate` envelope. Invalid specs inside a valid
envelope are answered with `design.failed` (so the API learns about them) and acked.

## Heuristic vs real

* Real: the CAD (OCCT), watertightness, bounds, exports, photo reliefs and customer forms with
  manifold booleans, the inspect checks listed in `services/inspect/README.md`.
* Heuristic: the print estimate (see inspect README), `supports_required`.
* Not yet: emboss/motif features (PR 3b), cut-out silhouettes, style variants, USDZ and thumbnails
  (Phase 1–2), overhang and load checks (Phase 2–3).
