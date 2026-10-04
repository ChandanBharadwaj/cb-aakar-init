"""``build_design``: design.generate payload -> design.completed (or design.failed) payload.

Steps (PLAN §7.11): validate request + spec → resolve template → check family / features (anchor modes,
required content) / style / material → validate params → normalise (a style the template offers may put its own
content defaults first) → ``Template.validate_content`` (limits coupling params and content) →
progress ``understanding`` → progress ``sculpting`` → CAD body + features (Chhaap: names and motifs
shaped and set in, photo reliefs and hero forms fetched through the ``ContentFetcher``) → refuse an
empty result → export + store → progress ``checking`` → inspect → assemble and validate the completed
payload (with the template's ``hardware_for(params)`` and a karigar's note that names the lettering). Every failure becomes a ``design.failed`` payload with the matching code;
nothing raises out of this function.
"""

from __future__ import annotations

import concurrent.futures
import logging
import os
import time
import traceback
import uuid
from typing import Any, Mapping, Sequence

import trimesh

from .contracts import ContractError, validate
from .errors import (
    BuildError,
    BuildTimeout,
    GeometryError,
    InvalidSpec,
    NotPrintable,
    UnsupportedFeature,
)
from .events import CallbackSink, NullSink, ProgressSink, progress_payload
from .exports import SUPPORTED_OUTPUTS, export_all
from .features import validate as feature_validation
from .features.fetch import ContentFetcher, HttpFetcher
from .inspection import HttpInspector, Inspector, inspector_from_env
from .storage import AssetRecord, Storage, asset_key, storage_from_env
from .templates import Template, get_template

log = logging.getLogger("aakar.geometry.pipeline")

NIL_UUID = "00000000-0000-0000-0000-000000000000"
DEFAULT_OUTPUTS = ["glb", "3mf", "stl"]
DEFAULT_BUILD_TIMEOUT_S = 120.0
DEFAULT_SLICING = {"layer_height_mm": 0.2, "infill_pct": 15, "nozzle_mm": 0.4, "walls": 3}
HTTP_STATUS_BY_CODE = {
    "invalid_spec": 422,
    "unknown_template": 422,
    "unsupported_feature": 422,
    "param_out_of_range": 422,
    "content_unusable": 422,
    "not_printable": 422,
    "build_error": 500,
    "storage_error": 500,
    "timeout": 500,
}


def _uuid_or_nil(value: Any) -> str:
    try:
        return str(uuid.UUID(str(value)))
    except (ValueError, TypeError, AttributeError):
        return NIL_UUID


def failed_payload(job_id: Any, design_id: Any, code: str, message: str, detail: Mapping[str, Any] | None = None) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "job_id": _uuid_or_nil(job_id),
        "design_id": _uuid_or_nil(design_id),
        "code": code,
        "message": message,
    }
    if detail:
        payload["detail"] = dict(detail)
    validate("design.failed", payload)
    return payload


def is_completed(payload: Mapping[str, Any]) -> bool:
    return "assets" in payload and "printability" in payload


def http_status_for(payload: Mapping[str, Any]) -> int:
    if is_completed(payload):
        return 200
    return HTTP_STATUS_BY_CODE.get(str(payload.get("code")), 500)


def normalise_spec(template: type[Template], spec: Mapping[str, Any], params: Mapping[str, Any]) -> dict[str, Any]:
    constraints = template.constraints.descriptor()
    constraints.update({k: v for k, v in (spec.get("constraints") or {}).items() if v is not None})
    style = spec.get("style") or "none"
    out: dict[str, Any] = {
        "spec_version": "1.0",
        "family": template.family,
        "template": template.ref(),
        "params": dict(params),
        # a style the template offers may put its own content defaults first (comic_pop: raised, bolder names)
        "features": feature_validation.normalise_features(spec.get("features") or [], template=template, style=style),
        "style": style,
        "constraints": constraints,
    }
    if spec.get("material"):
        out["material"] = spec["material"]
    validate("design-spec", out)
    return out


def _check_spec_against_template(template: type[Template], spec: Mapping[str, Any]) -> None:
    if spec.get("family") != template.family:
        raise InvalidSpec(
            f"Template {template.ref()} belongs to family {template.family}, not {spec.get('family')}",
            {"family": spec.get("family"), "template_family": template.family},
        )
    # Feature semantics: type supported, anchor exists and accepts it, the modes it lists, one photo/form per
    # anchor, relief depth within the anchor's cap, text length within the family's cap, required anchors filled.
    feature_validation.check(template, spec)
    style = spec.get("style") or "none"
    if style != "none" and style not in template.style_variants:
        raise UnsupportedFeature(
            f"{template.name} does not support the {style} style yet",
            {"style": style, "style_variants": list(template.style_variants)},
        )
    material = spec.get("material")
    if material and material not in template.materials():
        from .materials import load_materials

        names = {m["id"]: m.get("name") or m["id"] for m in load_materials()}
        allowed = template.materials()
        if material in names:  # a real finish that this family's material rules leave out (Roshni: white only)
            raise InvalidSpec(
                f"{template.name} comes in {', '.join(names.get(m, m) for m in allowed)} only",
                {"material": material, "materials": allowed},
            )
        raise InvalidSpec(f"Unknown material {material}", {"material": material, "materials": allowed})


def _build_with_timeout(
    template: type[Template],
    params: Mapping[str, Any],
    timeout_s: float,
    features: Sequence[Mapping[str, Any]] = (),
    fetcher: ContentFetcher | None = None,
    material: str | None = None,
) -> trimesh.Trimesh:
    executor = concurrent.futures.ThreadPoolExecutor(max_workers=1, thread_name_prefix="aakar-cad")
    # only a template with a Kadi needs the material (its socket is modelled with that material's compensation);
    # every other template gets the exact call it always got
    extra: dict[str, Any] = {"material": material} if material and getattr(template, "connector", None) is not None else {}
    if features:
        future = executor.submit(template.build, params, features=list(features), fetcher=fetcher, **extra)
    else:  # no content: identical call to the pre-features pipeline
        future = executor.submit(template.build, params, **extra)
    try:
        return future.result(timeout=timeout_s)
    except concurrent.futures.TimeoutError as exc:
        raise BuildTimeout(f"Building {template.name} took longer than {timeout_s:g} s", {"timeout_s": timeout_s}) from exc
    except BuildError:
        raise
    except Exception as exc:
        raise GeometryError(
            "The template could not build this geometry",
            {"error": f"{type(exc).__name__}: {exc}", "trace": traceback.format_exc()[-4000:]},
        ) from exc
    finally:
        executor.shutdown(wait=False)


def build_design(
    request: Mapping[str, Any],
    *,
    sink: ProgressSink | None = None,
    storage: Storage | None = None,
    inspector: Inspector | None = None,
    fetcher: ContentFetcher | None = None,
    build_timeout_s: float | None = None,
    slicing: Mapping[str, Any] | None = None,
) -> dict[str, Any]:
    """Run the full build for a ``design.generate`` payload and return the completed/failed payload.

    ``fetcher`` fetches ``content_source`` files behind relief_image / hero_mesh features
    (default ``HttpFetcher``; tests and the CLI pass a ``LocalFileFetcher``).
    """
    started = time.perf_counter()
    job_id = request.get("job_id") if isinstance(request, Mapping) else None
    design_id = request.get("design_id") if isinstance(request, Mapping) else None
    if sink is None:
        callback_url = request.get("callback_url") if isinstance(request, Mapping) else None
        sink = CallbackSink(callback_url, _uuid_or_nil(job_id), _uuid_or_nil(design_id)) if callback_url else NullSink()
    timeout_s = build_timeout_s or float(os.environ.get("AAKAR_BUILD_TIMEOUT_S", DEFAULT_BUILD_TIMEOUT_S))
    slicing = dict(slicing or DEFAULT_SLICING)

    try:
        try:
            validate("design.generate", request)
        except ContractError as exc:
            raise InvalidSpec("The design request does not match the design.generate contract", {"errors": exc.as_detail()}) from exc

        spec = request["spec"]
        template = get_template(spec["template"])
        _check_spec_against_template(template, spec)
        params = template.validate(spec.get("params"))
        normalised = normalise_spec(template, spec, params)
        # limits that couple params and content (skin under a cut-in photo, a raw print's model), before any CAD
        template.validate_content(params, normalised["features"])
        outputs = list(dict.fromkeys(request.get("outputs") or DEFAULT_OUTPUTS))
        skipped = [o for o in outputs if o not in SUPPORTED_OUTPUTS]
        if skipped:
            log.info("outputs %s are not produced in Phase 0; skipping", skipped)
        outputs = [o for o in outputs if o in SUPPORTED_OUTPUTS]

        storage = storage or storage_from_env()
        inspector = inspector or inspector_from_env()
        if isinstance(inspector, HttpInspector) and "stl" not in outputs:
            outputs.append("stl")
        fetcher = fetcher or HttpFetcher()

        sink.emit("design.progress", progress_payload("understanding", 10))
        sink.emit("design.progress", progress_payload("sculpting", 35))
        material = normalised.get("material")
        mesh = _build_with_timeout(template, params, timeout_s, normalised["features"], fetcher, material)
        if not isinstance(mesh, trimesh.Trimesh) or mesh.is_empty or len(mesh.faces) == 0:
            raise GeometryError(
                "Something went wrong while shaping this design: it came out empty",
                {"template": template.ref(), "reason": "the build returned no geometry"},
            )

        files = export_all(mesh, outputs, name=f"{template.id}_v{template.version}")
        normalised_design_id = _uuid_or_nil(design_id)
        version_no = int(request["version_no"])
        assets: dict[str, AssetRecord] = {}
        for kind, file in files.items():
            assets[kind] = storage.put(asset_key(normalised_design_id, version_no, kind), file.data, file.content_type)

        sink.emit("design.progress", progress_payload("checking", 70))
        # a Kadi template reports its socket so inspect can run connector_fit; others keep the five-argument call
        connector = template.connector_report(params, material) if getattr(template, "connector", None) is not None else None
        inspect_kwargs: dict[str, Any] = {"connector": connector} if connector else {}
        try:
            report, estimate = inspector.inspect(mesh, normalised["constraints"], slicing, assets, storage, **inspect_kwargs)
        except BuildError:
            raise
        except Exception as exc:
            raise GeometryError("Printability check failed", {"error": f"{type(exc).__name__}: {exc}"}) from exc
        if report["checks"]["manifold"]["status"] == "fail":
            raise NotPrintable(
                "The geometry is not watertight and cannot be sliced",
                {"manifold": report["checks"]["manifold"], "template": template.ref()},
            )

        payload: dict[str, Any] = {
            "job_id": _uuid_or_nil(job_id),
            "design_id": normalised_design_id,
            "version_no": version_no,
            "template": {"id": template.id, "version": template.version},
            "spec": normalised,
            "hardware": template.hardware_for(params),
            "assets": {kind: rec.to_dict() for kind, rec in assets.items()},
            "printability": report,
            "print_estimate": estimate,
            "karigar_note": template.karigar_note_for(params, normalised["features"], style=normalised["style"]),
            "build_ms": int(round((time.perf_counter() - started) * 1000)),
        }
        try:
            validate("design.completed", payload)
        except ContractError as exc:
            raise GeometryError("Internal error: completed payload violates the contract", {"errors": exc.as_detail()}) from exc
        return payload

    except BuildError as exc:
        log.warning("build failed for job %s: %s (%s)", job_id, exc.code, exc.message)
        return failed_payload(job_id, design_id, exc.code, exc.message, exc.detail)
    except Exception as exc:  # last line of defence: never raise out of the pipeline
        log.exception("unexpected error building job %s", job_id)
        return failed_payload(
            job_id, design_id, "build_error", "Something went wrong while building this design",
            {"error": f"{type(exc).__name__}: {exc}", "trace": traceback.format_exc()[-4000:]},
        )
