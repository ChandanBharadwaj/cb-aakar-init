"""``aakar-inspect`` console script: ``serve`` (uvicorn, port 8082) and ``check <mesh>``."""

from __future__ import annotations

import argparse
import json
import logging
import sys

from .core import inspect_mesh
from .loaders import MeshLoadError, load_mesh_path
from .settings import Constraints, SlicingSettings


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="aakar-inspect", description="Aakar printability + print estimate")
    sub = parser.add_subparsers(dest="cmd", required=True)
    serve = sub.add_parser("serve", help="run the HTTP API")
    serve.add_argument("--host", default="0.0.0.0")
    serve.add_argument("--port", type=int, default=8082)
    check = sub.add_parser("check", help="inspect a local mesh file (mm, Z-up) and print the report")
    check.add_argument("mesh")
    check.add_argument("--min-wall", type=float, default=1.2)
    check.add_argument("--density", type=float, default=1.24, help="g/cm³ used for the mass hint (PLA 1.24)")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")

    if args.cmd == "serve":
        import uvicorn

        uvicorn.run("aakar_inspect.api:app", host=args.host, port=args.port)
        return 0

    try:
        mesh = load_mesh_path(args.mesh)
    except MeshLoadError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    report, estimate = inspect_mesh(mesh, Constraints(min_wall_mm=args.min_wall), SlicingSettings())
    print(json.dumps({"printability": report, "print_estimate": estimate}, indent=2))
    grams = estimate["extruded_volume_cm3"] * args.density
    h, m = divmod(estimate["print_seconds"] // 60, 60)
    print(f"passed={report['passed']} ~{grams:.0f} g at {args.density} g/cm³, {h}:{m:02d} print", file=sys.stderr)
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
