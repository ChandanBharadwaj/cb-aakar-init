#!/usr/bin/env node
// A tiny stand-in for services/api so the storefront can be exercised before Spring Boot lands.
// Serves the Phase 0 contract shapes (packages/contracts) with in-memory designs, jobs and an SSE stream.
// Usage: node scripts/mock-api.mjs [port=8080] [--fail]   (--fail makes every third job fail)
import http from "node:http";
import { randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../../..");
const materials = JSON.parse(readFileSync(path.join(root, "packages/design-tokens/materials.json"), "utf8"));
const completed = JSON.parse(readFileSync(path.join(root, "packages/contracts/examples/design.completed.example.json"), "utf8"));

const PORT = Number(process.argv[2] && !process.argv[2].startsWith("--") ? process.argv[2] : 8080);
const FAIL_EVERY_THIRD = process.argv.includes("--fail");
const STAGE_MS = 700;

const templates = [
  {
    id: "jharokha_phone_stand", version: 1, family: "phone_stand", name: "Jharokha Phone Stand",
    description: "A cusped Rajasthani arch that holds a phone at a comfortable tilt.", environment: "desk_oak",
    params: {
      width_mm: { type: "number", label: "Width", unit: "mm", default: 92, min: 70, max: 120, step: 1, group: "Size" },
      depth_mm: { type: "number", label: "Depth", unit: "mm", default: 78, min: 60, max: 110, step: 1, group: "Size" },
      height_mm: { type: "number", label: "Height", unit: "mm", default: 120, min: 90, max: 160, step: 1, group: "Size" },
      tilt_deg: { type: "number", label: "Tilt", unit: "deg", default: 70, min: 55, max: 80, step: 1, group: "Shape" },
      lip_height_mm: { type: "number", label: "Lip", unit: "mm", default: 12, min: 6, max: 20, step: 0.5, group: "Shape" },
      wall_mm: { type: "number", label: "Wall", unit: "mm", default: 3.2, min: 1.6, max: 4, step: 0.1, group: "Details" },
      arch_cusps: { type: "integer", label: "Arch cusps", unit: "count", default: 5, min: 3, max: 9, step: 2, group: "Details" },
    },
    anchors: [{ id: "back_panel", label: "Back panel", projection: "planar", max_text_height_mm: 18 }],
    constraints: { min_wall_mm: 1.2, max_overhang_deg: 55, bed_mm: [250, 250, 250] },
    materials: ["basic_white", "terracotta_matte", "terracotta_silk", "polished_brass", "sandalwood_silk", "indigo_matte"],
    style_variants: [], features_supported: ["emboss_text"],
  },
  {
    id: "fluted_planter", version: 1, family: "planter", name: "Fluted Planter",
    description: "Vertical flutes, a drainage tray, sized for nursery pots.", environment: "balcony_daylight",
    params: {
      diameter_mm: { type: "number", label: "Diameter", unit: "mm", default: 140, min: 100, max: 200, step: 5, group: "Size" },
      height_mm: { type: "number", label: "Height", unit: "mm", default: 130, min: 90, max: 200, step: 5, group: "Size" },
      flutes: { type: "integer", label: "Flutes", unit: "count", default: 24, min: 12, max: 48, step: 2, group: "Shape" },
      tray: { type: "boolean", label: "Drainage tray", default: true, group: "Details" },
    },
    anchors: [], constraints: { min_wall_mm: 1.6, max_overhang_deg: 50, bed_mm: [250, 250, 250] },
    materials: ["basic_white", "terracotta_matte", "sandalwood_silk", "indigo_matte"], style_variants: [], features_supported: [],
  },
];

const items = [
  { slug: "jharokha-phone-stand", name: "Jharokha Phone Stand", category: "desk_tech", template_id: "jharokha_phone_stand", default_params: { width_mm: 92, depth_mm: 78, height_mm: 120, tilt_deg: 70, lip_height_mm: 12, wall_mm: 3.2, arch_cusps: 5 }, default_material: "terracotta_silk", base_price_paise: 49900, specs_line: "Fits phones to 6.9″ · 92 × 78 × 120 mm · 64 g", environment: "desk_oak", description: "A cusped jharokha arch, printed in one piece, that holds any phone at a comfortable tilt." },
  { slug: "ajrakh-coasters", name: "Ajrakh Coasters · set of 4", category: "kitchen", template_id: "jharokha_phone_stand", default_params: {}, default_material: "indigo_matte", base_price_paise: 64900, specs_line: "100 mm · heat-safe to 90 °C · 28 g each", environment: "kitchen_marble", available: false },
  { slug: "fluted-planter", name: "Fluted Planter · drainage tray", category: "home_decor", template_id: "fluted_planter", default_params: { diameter_mm: 140, height_mm: 130, flutes: 24, tray: true }, default_material: "terracotta_matte", base_price_paise: 89900, specs_line: "140 mm pot · fits 4″ nursery plants · 210 g", environment: "balcony_daylight" },
  { slug: "kantha-nameplate", name: "Nameplate · Kantha border", category: "nameplates", template_id: "jharokha_phone_stand", default_params: {}, default_material: "polished_brass", base_price_paise: 119900, specs_line: "300 × 110 mm · raised letters · screws included", environment: "studio", available: false },
  { slug: "elephant-bookends", name: "Elephant Bookends", category: "gifting", template_id: "jharokha_phone_stand", default_params: {}, default_material: "sandalwood_silk", base_price_paise: 134900, specs_line: "180 mm tall · weighted · holds 6 kg of books", environment: "teak_table_candlelight", available: false },
  { slug: "headphone-stand-pillar", name: "Headphone Stand · Pillar", category: "desk_tech", template_id: "jharokha_phone_stand", default_params: {}, default_material: "basic_white", base_price_paise: 57900, specs_line: "270 mm tall · weighted base · 180 g", environment: "desk_oak", available: false },
];

const designs = new Map();
const versions = new Map();
const jobs = new Map();
const streams = new Map(); // jobId -> Set<res>
let jobCount = 0;

const policy = materials.pricing_policy;
function price(estimate, materialId) {
  const m = materials.materials.find((x) => x.id === materialId) ?? materials.materials[0];
  const mass = +(estimate.extruded_volume_cm3 * m.density_g_cm3).toFixed(1);
  const material = Math.round(mass * m.rate_per_g_paise);
  const machine = Math.round((estimate.print_seconds / 3600) * policy.machine_rate_paise_per_hour);
  const finishing = policy.finishing_fee_paise[m.finish_class];
  const raw = material + machine + finishing;
  const subtotal = (Math.ceil(raw / 100 / 10) * 10 - 1) * 100;
  const shipping = subtotal >= policy.free_shipping_above_paise ? 0 : policy.shipping_flat_paise;
  const h = Math.floor(estimate.print_seconds / 3600), min = Math.round((estimate.print_seconds % 3600) / 60);
  return {
    currency: "INR", material_id: m.id, mass_g: mass, print_seconds: estimate.print_seconds,
    lines: [
      { code: "material", label: `Material · ${Math.round(mass)} g ${m.name}`, amount_paise: material },
      { code: "machine_time", label: `Print time · ${h} h ${String(min).padStart(2, "0")} m`, amount_paise: machine },
      { code: "finishing", label: m.finish_class === "silk" ? "Hand sanding & sealing" : "Hand finishing", amount_paise: finishing },
    ],
    subtotal_paise: subtotal, shipping_paise: shipping, shipping_label: policy.shipping_label, total_paise: subtotal + shipping, policy_version: policy.version,
  };
}

function problem(res, status, code, detail) {
  res.writeHead(status, { "Content-Type": "application/problem+json", "Access-Control-Allow-Origin": "*" });
  res.end(JSON.stringify({ type: "about:blank", title: code.replace(/_/g, " "), status, detail, code }));
}
function json(res, status, body) {
  res.writeHead(status, { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" });
  res.end(JSON.stringify(body));
}

function emit(job, stage, message, percent, extra = {}) {
  job.sequence += 1;
  job.stage = stage; job.message = message;
  const ev = { job_id: job.id, sequence: job.sequence, stage, message, percent, version_id: job.version_id ?? null, error_code: null, at: new Date().toISOString(), ...extra };
  job.events.push(ev);
  for (const res of streams.get(job.id) ?? []) res.write(`id: ${ev.sequence}\nevent: stage\ndata: ${JSON.stringify(ev)}\n\n`);
  if (stage === "ready" || stage === "failed") for (const res of streams.get(job.id) ?? []) res.end();
}

function startJob(design, spec, parentVersionId) {
  const template = templates.find((t) => t.id === spec.template.split("@")[0]) ?? templates[0];
  for (const [k, v] of Object.entries(spec.params)) {
    const p = template.params[k];
    if (p && typeof v === "number" && ((p.min !== undefined && v < p.min) || (p.max !== undefined && v > p.max))) {
      throw Object.assign(new Error(`${p.label} must be between ${p.min} and ${p.max} ${p.unit}`), { code: "param_out_of_range", status: 422 });
    }
  }
  const versionNo = design.versions_count + 1;
  const version = {
    id: randomUUID(), design_id: design.id, version_no: versionNo, parent_version_id: parentVersionId ?? null, status: "generating",
    spec, template: { id: template.id, version: template.version }, created_by: "user", created_at: new Date().toISOString(),
  };
  const job = { id: randomUUID(), design_id: design.id, version_id: version.id, version_no: versionNo, type: "generate", status: "queued", stage: "queued", message: "Queued", error_code: null, attempts: 1, created_at: new Date().toISOString(), started_at: null, finished_at: null, sequence: 0, events: [] };
  version.job_id = job.id;
  versions.set(version.id, version);
  jobs.set(job.id, job);
  design.versions_count = versionNo;
  design.status = "generating";
  design.latest_version = version;
  jobCount += 1;
  const fail = FAIL_EVERY_THIRD && jobCount % 3 === 0;

  const scale = (spec.params.height_mm ?? 120) / 120;
  const steps = [
    ["understanding", "Reading the template", 10],
    ["sculpting", "Weaving your design", 40],
    ["checking", "Checking physics", 70],
    ["pricing", "Pricing", 90],
  ];
  let t = STAGE_MS;
  job.status = "running"; job.started_at = new Date().toISOString();
  for (const [stage, message, percent] of steps) {
    setTimeout(() => emit(job, stage, message, percent), t);
    t += STAGE_MS;
  }
  setTimeout(() => {
    if (fail) {
      job.status = "failed"; job.error_code = "geometry_failed"; job.finished_at = new Date().toISOString();
      version.status = "failed"; design.status = "failed";
      emit(job, "failed", "The arch collapsed at this width. Try a smaller tilt.", 100, { error_code: "geometry_failed" });
      return;
    }
    const est = { ...completed.print_estimate, print_seconds: Math.round(completed.print_estimate.print_seconds * scale), extruded_volume_cm3: +(completed.print_estimate.extruded_volume_cm3 * scale).toFixed(1) };
    Object.assign(version, {
      status: "ready",
      assets: completed.assets,
      geometry: { bounds_mm: [spec.params.width_mm ?? 92, spec.params.depth_mm ?? 78, spec.params.height_mm ?? 120], volume_cm3: completed.printability.geometry.volume_cm3, surface_cm2: completed.printability.geometry.surface_cm2, triangles: completed.printability.geometry.triangles },
      printability: { ...completed.printability, geometry: { ...completed.printability.geometry, bounds_mm: [spec.params.width_mm ?? 92, spec.params.depth_mm ?? 78, spec.params.height_mm ?? 120] } },
      print_estimate: est,
      price: price(est, spec.material),
      karigar_note: versionNo === 1 ? completed.karigar_note : `Version ${versionNo}: I re-sculpted the ${template.name.toLowerCase()} at ${spec.params.height_mm ?? 120} mm tall and kept the walls at ${spec.params.wall_mm ?? 3.2} mm.`,
    });
    job.status = "succeeded"; job.finished_at = new Date().toISOString();
    design.status = "ready";
    emit(job, "ready", "Ready", 100);
  }, t);
  return { design_id: design.id, version_no: versionNo, job_id: job.id, events_url: `/api/jobs/${job.id}/events` };
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const p = url.pathname;
  if (req.method === "OPTIONS") {
    res.writeHead(204, { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "content-type, last-event-id", "Access-Control-Allow-Methods": "GET, POST, OPTIONS" });
    return res.end();
  }
  let m;
  try {
    if (p === "/api/catalog/items") {
      const cat = url.searchParams.get("category");
      return json(res, 200, cat ? items.filter((i) => i.category === cat) : items);
    }
    if ((m = p.match(/^\/api\/catalog\/items\/([^/]+)$/))) {
      const item = items.find((i) => i.slug === decodeURIComponent(m[1]));
      return item ? json(res, 200, item) : problem(res, 404, "not_found", "No piece with that name.");
    }
    if (p === "/api/catalog/materials") return json(res, 200, materials.materials);
    if (p === "/api/templates") return json(res, 200, templates);
    if ((m = p.match(/^\/api\/templates\/([^/]+)$/))) {
      const t = templates.find((x) => x.id === decodeURIComponent(m[1]));
      return t ? json(res, 200, t) : problem(res, 404, "not_found", "No such template.");
    }
    if (p === "/api/designs" && req.method === "POST") {
      const body = JSON.parse(await readBody(req));
      if (body.prompt || body.source === "create") return problem(res, 422, "not_yet_available", "Describing a piece in words arrives in Phase 2.");
      let template, params, material, title, slug = null;
      if (body.catalog_item_slug) {
        const item = items.find((i) => i.slug === body.catalog_item_slug);
        if (!item) return problem(res, 404, "not_found", "No piece with that name.");
        template = templates.find((t) => t.id === item.template_id);
        params = { ...defaults(template), ...item.default_params, ...(body.params ?? {}) };
        material = body.material ?? item.default_material; title = item.name; slug = item.slug;
      } else if (body.template_id) {
        template = templates.find((t) => t.id === body.template_id);
        if (!template) return problem(res, 404, "not_found", "No such template.");
        params = { ...defaults(template), ...(body.params ?? {}) };
        material = body.material ?? template.materials[0]; title = body.title ?? template.name;
      } else return problem(res, 400, "bad_request", "Send catalog_item_slug or template_id.");
      const design = { id: randomUUID(), source: body.source, catalog_item_slug: slug, title, status: "generating", created_at: new Date().toISOString(), versions_count: 0 };
      designs.set(design.id, design);
      const spec = { spec_version: "1.0", family: template.family, template: `${template.id}@${template.version}`, params, features: [], style: "none", material, constraints: template.constraints };
      return json(res, 202, startJob(design, spec));
    }
    if ((m = p.match(/^\/api\/designs\/([^/]+)$/))) {
      const d = designs.get(m[1]);
      return d ? json(res, 200, d) : problem(res, 404, "not_found", "No such design.");
    }
    if ((m = p.match(/^\/api\/designs\/([^/]+)\/versions$/))) {
      const d = designs.get(m[1]);
      if (!d) return problem(res, 404, "not_found", "No such design.");
      return json(res, 200, [...versions.values()].filter((v) => v.design_id === d.id).sort((a, b) => b.version_no - a.version_no));
    }
    if ((m = p.match(/^\/api\/versions\/([^/]+)$/))) {
      const v = versions.get(m[1]);
      return v ? json(res, 200, v) : problem(res, 404, "not_found", "No such version.");
    }
    if ((m = p.match(/^\/api\/versions\/([^/]+)\/params$/)) && req.method === "POST") {
      const v = versions.get(m[1]);
      if (!v) return problem(res, 404, "not_found", "No such version.");
      const body = JSON.parse(await readBody(req));
      const spec = { ...v.spec, params: { ...v.spec.params, ...body.params }, material: body.material ?? v.spec.material };
      return json(res, 202, startJob(designs.get(v.design_id), spec, v.id));
    }
    if ((m = p.match(/^\/api\/versions\/([^/]+)\/printability$/))) {
      const v = versions.get(m[1]);
      if (!v) return problem(res, 404, "not_found", "No such version.");
      return v.status === "ready" ? json(res, 200, v.printability) : problem(res, 409, "version_not_ready", "Still sculpting.");
    }
    if ((m = p.match(/^\/api\/versions\/([^/]+)\/price$/))) {
      const v = versions.get(m[1]);
      if (!v) return problem(res, 404, "not_found", "No such version.");
      if (v.status !== "ready") return problem(res, 409, "version_not_ready", "Still sculpting.");
      const mat = url.searchParams.get("material");
      if (!materials.materials.some((x) => x.id === mat)) return problem(res, 404, "not_found", "No such finish.");
      return json(res, 200, price(v.print_estimate, mat));
    }
    if ((m = p.match(/^\/api\/jobs\/([^/]+)$/))) {
      const j = jobs.get(m[1]);
      if (!j) return problem(res, 404, "not_found", "No such job.");
      return json(res, 200, Object.fromEntries(Object.entries(j).filter(([k]) => k !== "events" && k !== "sequence")));
    }
    if ((m = p.match(/^\/api\/jobs\/([^/]+)\/events$/))) {
      const j = jobs.get(m[1]);
      if (!j) return problem(res, 404, "not_found", "No such job.");
      res.writeHead(200, { "Content-Type": "text/event-stream", "Cache-Control": "no-cache", Connection: "keep-alive", "Access-Control-Allow-Origin": "*" });
      const last = Number(req.headers["last-event-id"] ?? 0);
      const replay = j.events.filter((e) => e.sequence > last);
      const current = replay.length ? replay : j.events.slice(-1);
      for (const ev of current) res.write(`id: ${ev.sequence}\nevent: stage\ndata: ${JSON.stringify(ev)}\n\n`);
      if (!current.length) res.write(`id: 0\nevent: stage\ndata: ${JSON.stringify({ job_id: j.id, sequence: 0, stage: "queued", message: "Queued", percent: 0, version_id: j.version_id, error_code: null, at: new Date().toISOString() })}\n\n`);
      if (j.stage === "ready" || j.stage === "failed") return res.end();
      if (!streams.has(j.id)) streams.set(j.id, new Set());
      streams.get(j.id).add(res);
      req.on("close", () => streams.get(j.id)?.delete(res));
      return;
    }
    return problem(res, 404, "not_found", `No route for ${req.method} ${p}`);
  } catch (err) {
    return problem(res, err.status ?? 500, err.code ?? "internal", err.message);
  }
});

function defaults(template) {
  return Object.fromEntries(Object.entries(template.params).map(([k, v]) => [k, v.default]));
}
function readBody(req) {
  return new Promise((resolve) => {
    let s = "";
    req.on("data", (c) => (s += c));
    req.on("end", () => resolve(s || "{}"));
  });
}

server.listen(PORT, () => console.log(`Aakar mock API on http://localhost:${PORT}${FAIL_EVERY_THIRD ? " (every third job fails)" : ""}`));
