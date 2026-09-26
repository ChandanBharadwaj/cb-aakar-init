# aakar-inspect

Printability Report v1 and Print Estimate v1 for a mesh (PLAN §7.9, §7.10). A library first,
a FastAPI service second. The geometry service calls it **in-process** by default; the HTTP
surface (`openapi/inspect.v1.yaml`, port 8082) exists so it can be scaled separately later.

```python
from aakar_inspect import inspect_mesh, Constraints, SlicingSettings
report, estimate = inspect_mesh(mesh, Constraints(min_wall_mm=1.2), SlicingSettings())
```

Both dicts validate against `packages/contracts/schemas/printability-report.v1.json` and
`print-estimate.v1.json` before they are returned. Meshes are **mm, Z-up, sitting on Z = 0**
(the GLBs the geometry service exports are metres/Y-up for viewers; inspect the STL or 3MF).

## Checks

| Check | Method | Status rules |
|---|---|---|
| `manifold` | `is_watertight` + `is_winding_consistent`; if not, a copy is repaired (`merge_vertices`, `fix_winding`, `fill_holes`, `fix_normals`) and re-checked | pass as delivered · warn if only watertight after repair · fail otherwise |
| `fits_bed` | sorted extents ≤ sorted `bed_mm` (any axis permutation) | pass / fail |
| `thinnest_wall` | 3000 area-weighted face centroids cast rays inward along the inverted normal (`trimesh.ray`); thickness = distance to the exit; value = 5th percentile | fail below `min_wall_mm` · warn within 20 % above it · "Solid · N mm" when nothing is thinner than 40 % of the smallest extent |
| `centre_of_gravity` | uniform-density `center_mass`; contact patch = vertices within 0.3 mm of min Z; support polygon = its 2D convex hull; value = horizontal offset from the polygon centroid | pass when the projection lies inside the hull |
| `tipping_margin` | signed distance from the projection to the nearest hull edge | fail if negative · warn below `min_tipping_margin_mm` (default 5) |
| `overhangs` | — | `skipped` "Overhang analysis arrives in Phase 2" |
| `load_capacity` | — | `skipped` "Load estimate arrives in Phase 3" |

`passed` is true when no check has status `fail`. Summaries are customer-facing in the checkout
board's register: "Inside base · 3 mm", "3.2 mm · safe", "Fits 250 × 250 × 250 mm bed",
"Stable · 21.5 mm margin".

## Estimate backends

`SlicerBackend` protocol, chosen by `default_backend()`:

* **`HeuristicEstimator`** (default, `method: heuristic`). Closed-form:
  walls = surface area × walls × line width (capped at the solid volume); infill = remaining
  interior × `infill_pct`; top/bottom skins ≈ near-horizontal area × 4 layers.
  Time = walls / 3.6 mm³/s + (infill + skins) / 8.0 mm³/s + 1.2 s per layer + 90 s setup.
  The single "≈5.5 mm³/s average" from the plan was split into a perimeter rate and an infill
  rate because a flat 5.5 gave 2 h 20 for the 92 × 78 × 120 mm phone stand; the split lands it
  at 3 h 33 / 55 g of PLA (sanity window 3–5 h, 45–70 g). `supports_required` is a coarse
  hint: true when more than 5 cm² of faces exceed `max_overhang_deg` above the bed.
* **`PrusaSlicerCli`** (`method: prusaslicer_cli`) when `AAKAR_SLICER_BIN` points at an existing
  PrusaSlicer-compatible binary: writes the mesh + a generated `config.ini`, runs
  `--export-gcode`, parses `; estimated printing time (normal mode)` and `; filament used [cm3]`
  from the G-code footer (`parse_gcode_footer`, unit-tested on a sample footer; the CLI itself is
  not installed here). A slicer failure logs and falls back to the heuristic.

## Environment

| Variable | Default | Purpose |
|---|---|---|
| `AAKAR_CONTRACTS_DIR` | `../../packages/contracts/schemas` (repo-relative) | JSON Schemas; Dockerfile sets `/contracts/schemas` |
| `AAKAR_SLICER_BIN` | unset | Path to `prusa-slicer` (or compatible) to enable real slicing |

## Run

```sh
cd services/inspect
uv sync                      # creates .venv
uv run pytest                # 21 tests
uv run aakar-inspect serve   # http://localhost:8082 (/healthz, POST /v1/inspect)
uv run aakar-inspect check path/to/model.stl
```

`POST /v1/inspect` takes `{"mesh": {"path": …} | {"url": …, "format": "stl"}, "constraints": {…}, "slicing": {…}}`
and answers `{"printability": …, "print_estimate": …}`; 422 when the mesh cannot be loaded or a
setting is out of range (never clamped). `mesh.path` reads the service's own filesystem: it is
meant for the in-cluster geometry service, not for the public internet.

## Heuristic vs real

* Real: watertightness, bounds, volume/area, wall thickness sampling, COG and tipping geometry.
* Heuristic: the print estimate (unless a slicer binary is configured), the `supports_required`
  hint, wall thickness being a 5th-percentile sample (very small thin spots under ~5 % of the
  surface can slip through), and the vertex-centroid COG fallback for open meshes.
* Stubbed until later phases: `overhangs` (Phase 2), `load_capacity` (Phase 3).
