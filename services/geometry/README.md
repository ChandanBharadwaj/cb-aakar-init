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
reported at once as `param_out_of_range`, unknown keys as `invalid_spec`; **never clamped**),
`build(params) -> trimesh.Trimesh`, `karigar_note(params)`.

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
`features_supported` is empty (emboss/motif arrive in Phase 1: a spec with features fails with
`unsupported_feature`, as does a non-`none` style).

One coupled rule: a tall, shallow, far-leaning combination whose back rest would leave less than
12 mm of phone slot at the base fails with `param_out_of_range` on `depth_mm, height_mm, tilt_deg`.

Example (`packages/contracts/examples/jharokha-phone-stand.spec.json`): 92 × 78 × 120 mm,
44.6 cm³, 680 triangles, walls 3.2 mm, COG 2 mm inside the base, 37 mm tipping margin,
≈55 g of PLA, 3 h 33 by the heuristic estimator.

## Pipeline (`build_design`)

`aakar_geometry/pipeline.py`: validate the request against `design.generate.v1.json` (and the
spec against `design-spec.v1.json`) → resolve `id@version` → check family / features / style /
material → validate params → progress `understanding` → progress `sculpting` → CAD (with a
timeout, `AAKAR_BUILD_TIMEOUT_S`, default 120 s) → export + store → progress `checking` →
inspect (in-process by default, HTTP when `AAKAR_INSPECT_URL` is set; the STL is then always
exported) → assemble and validate the `design.completed` payload. Failures return a
`design.failed` payload with the right `code`; nothing raises out of `build_design`.

* `understanding`/`sculpting`/`checking` progress envelopes (per `envelope.v1.json`, increasing
  `sequence`) are POSTed to `callback_url` when present. Callback failures are logged, never fatal.
  `pricing` and `ready` are emitted by the API service.
* A design whose checks fail (thin wall, tipping) still completes with `printability.passed=false`
  so the agent and the stability card see the details; only a non-manifold result is a
  `not_printable` failure.
* `outputs` `usdz` and `thumb` are accepted but not produced in Phase 0 (logged and skipped).

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
one line: template, bounds, volume, triangles, passed, grams (spec material density, PLA 1.24)
and print time.

## Worker

`aakar_geometry/worker.py` (pika): declares the durable topic exchange `aakar.design` and the
durable queue `geometry.design.generate` bound with `design.generate`; for each envelope runs
`build_design` and publishes `design.progress` / `design.completed` / `design.failed` envelopes
to the same exchange with routing key = type. Acks after the result is published; nacks without
requeue when the message is not a valid `design.generate` envelope. Invalid specs inside a valid
envelope are answered with `design.failed` (so the API learns about them) and acked.

## Heuristic vs real

* Real: the CAD (OCCT), watertightness, bounds, exports, the inspect checks listed in
  `services/inspect/README.md`.
* Heuristic: the print estimate (see inspect README), `supports_required`.
* Not yet: emboss/motif features, style variants, USDZ and thumbnails (Phase 1–2), overhang and
  load checks (Phase 2–3).
