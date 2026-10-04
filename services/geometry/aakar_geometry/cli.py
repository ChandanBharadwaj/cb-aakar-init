"""``aakar-geometry`` console script: templates | build | coupon | serve | worker."""

from __future__ import annotations

import argparse
import json
import logging
import sys
import uuid
from pathlib import Path

from .materials import density_g_cm3


def _cmd_templates(args: argparse.Namespace) -> int:
    from .templates import list_templates

    print(json.dumps([t.descriptor() for t in list_templates()], indent=2, ensure_ascii=False))
    return 0


class FlatLocalStorage:
    """Local storage that drops the key prefix so the CLI writes model.{glb,3mf,stl} straight into --out."""

    kind = "local"

    def __init__(self, out_dir: Path):
        from .storage import LocalStorage

        self.inner = LocalStorage(out_dir, f"file://{out_dir.resolve()}")
        self.out_dir = out_dir

    def path_for(self, key: str) -> Path:
        return self.out_dir / Path(key).name

    def url_for(self, key: str) -> str:
        return self.path_for(key).resolve().as_uri()

    def put(self, key: str, data: bytes, content_type: str):
        from .storage import AssetRecord
        import hashlib

        path = self.path_for(key)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return AssetRecord(key, self.url_for(key), len(data), content_type, hashlib.sha256(data).hexdigest())


def _cmd_build(args: argparse.Namespace) -> int:
    from .events import CollectingSink
    from .pipeline import build_design, is_completed

    spec_path = Path(args.spec)
    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    design_id = args.design_id or str(uuid.uuid4())
    job_id = args.job_id or str(uuid.uuid4())
    request = {"job_id": job_id, "design_id": design_id, "version_no": args.version, "spec": spec, "outputs": ["glb", "3mf", "stl"]}
    sink = CollectingSink(job_id, design_id)
    fetcher = None
    if args.content_dir:
        from .features.fetch import LocalFileFetcher

        fetcher = LocalFileFetcher(args.content_dir)  # content_source URLs resolve by file name inside this folder
    result = build_design(request, sink=sink, storage=FlatLocalStorage(out_dir), fetcher=fetcher)
    (out_dir / "result.json").write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    if not is_completed(result):
        print(f"FAILED {result['code']}: {result['message']}", file=sys.stderr)
        if result.get("detail"):
            print(json.dumps(result["detail"], indent=2)[:2000], file=sys.stderr)
        return 1
    geo = result["printability"]["geometry"]
    est = result["print_estimate"]
    material = result["spec"].get("material") or "basic_white"
    density = args.density if args.density else density_g_cm3(material, 1.24)
    grams = est["extruded_volume_cm3"] * density
    hours, minutes = divmod(est["print_seconds"] // 60, 60)
    w, d, h = geo["bounds_mm"]
    hardware = " · ".join(f"{item['sku']} ×{item['qty']}" for item in result.get("hardware") or [])
    print(
        f"{result['template']['id']}@{result['template']['version']} · bounds {w:g} × {d:g} × {h:g} mm · "
        f"{geo['volume_cm3']:.1f} cm³ · {geo['triangles']} triangles · "
        f"{'passed' if result['printability']['passed'] else 'NOT passed'} · "
        f"~{grams:.0f} g ({density:g} g/cm³) · {hours}:{minutes:02d} print · "
        + (f"hardware {hardware} · " if hardware else "")
        + f"wrote {', '.join(sorted(result['assets']))} + result.json to {out_dir}"
    )
    return 0


def _cmd_coupon(args: argparse.Namespace) -> int:
    """Build Kadi fit coupons: a flange with the socket only, one STL per size × finish, each run through inspect's
    ``connector_fit`` and measured back off the mesh (bore and crest as modelled, rib angles). Prints one line per
    coupon and writes ``coupons.json`` beside the STLs; the bench measures the prints against these numbers."""
    from aakar_inspect import Constraints, inspect_mesh

    from .connectors import compensation, socket
    from .exports import export_all
    from .materials import material_ids
    from .templates.kadi_coupon import KadiCoupon

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    nominals = list(socket.NOMINALS_MM) if args.all else [float(args.nominal)]
    materials = material_ids() if args.all or not args.material else [args.material]
    rows: list[dict] = []
    worst = "pass"
    for nominal in nominals:
        for material in materials:
            params = KadiCoupon.validate({"nominal_mm": f"{nominal:g}", "wall_mm": args.wall})
            mesh = KadiCoupon.build(params, material=material)
            comp = compensation.for_material(material)
            dims = KadiCoupon.connector_for(params).dims(comp)
            frame = KadiCoupon.connector_frame(params)
            report = KadiCoupon.connector_report(params, material)
            printability, _ = inspect_mesh(mesh, Constraints.from_dict(KadiCoupon.constraints.descriptor()), connector=report)
            fit = printability["checks"]["connector_fit"]
            measured = socket.measure(mesh, frame, dims)
            name = f"kadi_coupon_s{nominal:g}_{material}_w{args.wall:g}"
            (out_dir / f"{name}.stl").write_bytes(export_all(mesh, ["stl"], name=name)["stl"].data)
            rows.append({
                "file": f"{name}.stl", "nominal_mm": nominal, "material": material, "process": comp.process, "wall_mm": args.wall,
                "modelled": {"bore_d_mm": round(dims.bore_d_model_mm, 3), "crest_d_mm": round(dims.crest_d_model_mm, 3), "depth_mm": dims.depth_mm},
                "expected_printed": {"bore_d_mm": round(dims.bore_d_printed_mm, 3), "crest_d_mm": round(dims.crest_d_printed_mm, 3),
                                     "clearance_mm": round(dims.clearance_printed_mm, 3), "rib_bite_mm": round(dims.interference_printed_mm, 3),
                                     "crush_fraction": round(dims.crush_fraction, 3)},
                "measured_from_mesh": measured, "connector_fit": fit, "watertight": bool(mesh.is_watertight),
            })
            if fit["status"] == "fail" or (fit["status"] == "warn" and worst == "pass"):
                worst = fit["status"]
            print(
                f"Ø{nominal:g} {material} ({comp.process}) wall {args.wall:g}: bore modelled {dims.bore_d_model_mm:.2f} "
                f"(prints ≈{dims.bore_d_printed_mm:.2f}, pin {nominal:g}, clearance {dims.clearance_printed_mm:.2f}) · "
                f"{socket.RIB_COUNT} ribs crest {dims.crest_d_model_mm:.2f} (≈{dims.crest_d_printed_mm:.2f}, bite "
                f"{dims.interference_printed_mm:.2f}/rib, crush {dims.crush_fraction:.2f}) · mesh reads bore {measured.get('bore_d_mm')} "
                f"crest {measured.get('crest_d_mm')} ribs at {measured.get('rib_spacing_deg')}° · "
                f"{'watertight' if mesh.is_watertight else 'NOT watertight'} · connector_fit {fit['status']}: {fit['summary']}"
            )
    (out_dir / "coupons.json").write_text(json.dumps(rows, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {len(rows)} coupon(s) + coupons.json to {out_dir}")
    return 1 if worst == "fail" else 0


def _cmd_serve(args: argparse.Namespace) -> int:
    import uvicorn

    uvicorn.run("aakar_geometry.api:app", host=args.host, port=args.port, workers=1)
    return 0


def _cmd_worker(args: argparse.Namespace) -> int:
    from .worker import run

    run(args.amqp_url)
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="aakar-geometry", description="Aakar geometry service")
    sub = parser.add_subparsers(dest="cmd", required=True)

    sub.add_parser("templates", help="print every template descriptor as JSON").set_defaults(fn=_cmd_templates)

    build = sub.add_parser("build", help="build a design spec into model.glb/3mf/stl + result.json")
    build.add_argument("spec", help="path to a design-spec.v1 JSON file")
    build.add_argument("--out", required=True, help="output directory")
    build.add_argument("--design-id", default=None)
    build.add_argument("--job-id", default=None)
    build.add_argument("--version", type=int, default=1, help="version_no (default 1)")
    build.add_argument("--density", type=float, default=None, help="g/cm³ for the mass hint (default: the spec material's density, PLA 1.24)")
    build.add_argument(
        "--content-dir",
        default=None,
        help="folder holding the photos / model files named in the spec's content sources (their URLs resolve by file name here)",
    )
    build.set_defaults(fn=_cmd_build)

    coupon = sub.add_parser("coupon", help="build Kadi fit coupons (STL per size × finish) with their connector_fit verdicts")
    coupon.add_argument("--out", required=True, help="output directory")
    coupon.add_argument("--nominal", type=float, default=12.0, help="Kadi-S pin diameter: 8, 12 or 16 (default 12)")
    coupon.add_argument("--material", default=None, help="one finish id (default: every finish)")
    coupon.add_argument("--wall", type=float, default=3.0, help="plastic round the socket in mm (default 3; 1.4 shows a connector_fit failure)")
    coupon.add_argument("--all", action="store_true", help="every size in every finish")
    coupon.set_defaults(fn=_cmd_coupon)

    serve = sub.add_parser("serve", help="run the HTTP API (uvicorn)")
    serve.add_argument("--host", default="0.0.0.0")
    serve.add_argument("--port", type=int, default=8081)
    serve.set_defaults(fn=_cmd_serve)

    worker = sub.add_parser("worker", help="consume design.generate from RabbitMQ")
    worker.add_argument("--amqp-url", default=None, help="overrides AAKAR_AMQP_URL")
    worker.set_defaults(fn=_cmd_worker)

    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    return int(args.fn(args))


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
