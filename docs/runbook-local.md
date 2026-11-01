# Running Aakar locally

Two ways: everything in Docker, or infra in Docker (or a local Postgres) with services run natively. The vertical slice needs only Postgres, the geometry service and the API; the storefront is the fourth process.

## Prerequisites

| Tool | Version | Used by |
|---|---|---|
| Java | 21 | `services/api` |
| Python + uv | 3.11, uv ≥ 0.8 | `services/geometry`, `services/inspect` |
| Node + pnpm | 22, pnpm 10 | `apps/web`, `packages/*` |
| Docker Compose | v2+ | optional, `infra/docker-compose.yml` |
| PostgreSQL | 16 | if not using Docker |

The geometry service's CAD kernel (`cadquery-ocp`) needs OpenGL runtime libraries on Linux: `libgl1 libglu1-mesa libxrender1 libxext6 libsm6 libxi6 libfontconfig1`.

## Option A — everything in Docker

```sh
docker compose -f infra/docker-compose.yml up --build
```

Then open http://localhost:3000. Services: API :8080 (`/swagger-ui.html`), geometry :8081, inspect :8082, RabbitMQ console :15672 (aakar / aakar), MinIO console :9001 (aakar / aakar-secret). The API runs with the `rabbit` profile; assets land in the `aakar-assets` bucket.

## Option B — native services

### 1. Database

Either `make infra` (Docker Postgres on 5432 with `aakar` and `aakar_test` databases), or on a local Postgres:

If 5432 is already taken by a Postgres of your own, publish the container on another host port and point the API at it:

```sh
AAKAR_PG_PORT=5440 make infra
export AAKAR_JDBC_URL=jdbc:postgresql://127.0.0.1:5440/aakar AAKAR_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:5440/aakar_test
```

```sql
CREATE ROLE aakar LOGIN PASSWORD 'aakar';
CREATE DATABASE aakar OWNER aakar;
CREATE DATABASE aakar_test OWNER aakar;
```

### 2. Geometry service (port 8081)

```sh
cd services/geometry
uv sync
uv run pytest -q
uv run aakar-geometry serve           # local asset storage in ./.aakar-assets, served at /assets
# Real print-time and filament figures (instead of the heuristic) need PrusaSlicer; the geometry process runs the inspect
# estimator in-process, so point it at the console binary before `serve`:
#   Linux/macOS: export AAKAR_SLICER_BIN=prusa-slicer          (apt/brew/AppImage; `prusa-slicer` on PATH)
#   Windows:     $env:AAKAR_SLICER_BIN='C:\Program Files\Prusa3D\PrusaSlicer\prusa-slicer-console.exe'  (winget install Prusa3D.PrusaSlicer)
# Without it the service logs nothing and estimates heuristically; `print_estimate.method` in a design payload says which ran.
```

Quick check: `curl -s localhost:8081/v1/templates | head -c 400`.

### 3. API (port 8080)

```sh
cd services/api
./gradlew test                         # uses jdbc:postgresql://127.0.0.1:5432/aakar_test (AAKAR_TEST_JDBC_URL overrides)
./gradlew bootRun                      # direct profile: calls geometry over HTTP
```

Quick check: `curl -s localhost:8080/api/catalog/items | head -c 400`, Swagger at http://localhost:8080/swagger-ui.html.

### 4. Storefront (port 3000) and portal (port 3100)

```sh
pnpm install
pnpm --filter @aakar/web dev      # customers
pnpm --filter @aakar/admin dev    # staff; seeded account studio@aakar.local / aakar-studio (local profile only)
```

The customer loop runs entirely on mocks in the local profile (ADR-0013): the sign-in page shows the OTP dev code, the "Pay" step lands on a placeholder gateway page where you choose success or failure, shipping is a mock carrier, and messages are logged to the portal's Messages page instead of being sent.

The Shop cards show a render of each piece once it has been photographed: with the API and geometry running, `make shoot-catalogue` builds every Shop item and attaches its thumbnail (and GLB) as the item's media. Re-run it after a fresh database, after clearing `.aakar-assets`, or after changing an item's template, parameters or finish; until then a card shows the clay stand-in.

Open http://localhost:3000/shop, click **Modify with AI** on the Jharokha Phone Stand, watch the mandala run through *Understanding → Weaving → Checking → Pricing → Ready*, then swap finishes and drag the sliders.

### The slice from the command line

```sh
# 1. build geometry straight from the example spec
make slice                             # → out/slice/model.glb, model.3mf, model.stl, result.json

# 2. drive the API
curl -s -X POST localhost:8080/api/designs -H 'content-type: application/json' \
  -d '{"source":"shop","catalog_item_slug":"jharokha-phone-stand"}'
# → {"design_id":"…","version_no":1,"job_id":"…","events_url":"/api/jobs/…/events"}

curl -N localhost:8080/api/jobs/<job_id>/events          # SSE: stage events until ready
curl -s localhost:8080/api/designs/<design_id> | jq .latest_version.printability.checks
curl -s "localhost:8080/api/versions/<version_id>/price?material=polished_brass" | jq .
```

## Environment variables

See `infra/.env.example`. Defaults work for the native setup on one machine.

## Troubleshooting

- **`ImportError: libGL.so.1`** when the geometry service starts: install the OpenGL runtime libraries listed above.
- **API tests fail to connect**: check `AAKAR_TEST_JDBC_URL` and that the `aakar_test` database exists.
- **Viewer shows "Couldn't reach the studio"**: the API is not running on `NEXT_PUBLIC_API_URL` (default `http://localhost:8080`), or CORS is blocking a non-localhost origin.
- **Model loads but looks grey**: the material list failed to load; check `GET /api/catalog/materials`.
