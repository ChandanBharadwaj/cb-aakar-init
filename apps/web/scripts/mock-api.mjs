#!/usr/bin/env node
// A tiny stand-in for services/api so the storefront can be exercised before Spring Boot lands.
// Serves the Phase 0 contract shapes (designs, jobs, SSE) and the Phase 1 customer loop (ADR-0013):
// phone OTP with a dev code, guest → user attach, addresses, cart, serviceability, checkout, a mock
// payment gateway completed from the placeholder pay page, orders that advance a stage every few
// seconds, and an order SSE stream. Everything is in memory.
//
// Usage: node scripts/mock-api.mjs [port=8080] [--fail]
//   --fail                    every third generation job fails
//   AAKAR_WEB_URL             where pay_url points (default http://localhost:3000)
//   AAKAR_ORDER_STAGE_MS      ms between order stages after payment (default 8000)
//   Dev OTP code is always 123456. Pincodes starting with 9 are not serviceable.
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
const WEB_URL = (process.env.AAKAR_WEB_URL ?? "http://localhost:3000").replace(/\/$/, "");
const ORDER_STAGE_MS = Number(process.env.AAKAR_ORDER_STAGE_MS ?? 8000);
const DEV_CODE = "123456";
const OTP_TTL_S = 300;
const STUDIO = "Bengaluru";

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
  { slug: "jharokha-phone-stand", name: "Jharokha Phone Stand", category: "desk_tech", template_id: "jharokha_phone_stand", default_params: { width_mm: 92, depth_mm: 78, height_mm: 120, tilt_deg: 70, lip_height_mm: 12, wall_mm: 3.2, arch_cusps: 5 }, default_material: "terracotta_silk", base_price_paise: 49900, specs_line: "Fits phones to 6.9″ · 92 × 78 × 120 mm · 64 g", environment: "desk_oak", description: "A cusped jharokha arch, printed in one piece, that holds any phone at a comfortable tilt.", available: true },
  { slug: "ajrakh-coasters", name: "Ajrakh Coasters · set of 4", category: "kitchen", template_id: "jharokha_phone_stand", default_params: {}, default_material: "indigo_matte", base_price_paise: 64900, specs_line: "100 mm · heat-safe to 90 °C · 28 g each", environment: "kitchen_marble", available: false },
  { slug: "fluted-planter", name: "Fluted Planter · drainage tray", category: "home_decor", template_id: "fluted_planter", default_params: { diameter_mm: 140, height_mm: 130, flutes: 24, tray: true }, default_material: "terracotta_matte", base_price_paise: 89900, specs_line: "140 mm pot · fits 4″ nursery plants · 210 g", environment: "balcony_daylight", available: true },
  { slug: "kantha-nameplate", name: "Nameplate · Kantha border", category: "nameplates", template_id: "jharokha_phone_stand", default_params: {}, default_material: "polished_brass", base_price_paise: 119900, specs_line: "300 × 110 mm · raised letters · screws included", environment: "studio", available: false },
  { slug: "elephant-bookends", name: "Elephant Bookends", category: "gifting", template_id: "jharokha_phone_stand", default_params: {}, default_material: "sandalwood_silk", base_price_paise: 134900, specs_line: "180 mm tall · weighted · holds 6 kg of books", environment: "teak_table_candlelight", available: false },
  { slug: "headphone-stand-pillar", name: "Headphone Stand · Pillar", category: "desk_tech", template_id: "jharokha_phone_stand", default_params: {}, default_material: "basic_white", base_price_paise: 57900, specs_line: "270 mm tall · weighted base · 180 g", environment: "desk_oak", available: false },
];

// ---- Phase 0 state: designs, versions, jobs -------------------------------------------------
const designs = new Map();
const versions = new Map();
const jobs = new Map();
const streams = new Map(); // jobId -> Set<res>
let jobCount = 0;

// ---- Phase 1 state: identity, addresses, carts, orders, payments -----------------------------
const users = new Map(); // id -> User
const usersByPhone = new Map(); // phone -> id
const tokens = new Map(); // access_token -> userId
const otps = new Map(); // request_id -> { phone, code, expires_at }
const otpRate = new Map(); // phone -> [ms timestamps]
const addresses = new Map(); // userId -> Address[]
const carts = new Map(); // ownerKey ("user:<id>" | "guest:<id>") -> { id, owner, items, updated_at }
const orders = new Map(); // orderId -> order (+ owner)
const payments = new Map(); // paymentId -> Payment
const orderStreams = new Map(); // orderId -> Set<res>
let orderSeq = 0;
let invoiceSeq = 0;

const policy = materials.pricing_policy;
const now = () => new Date().toISOString();
const materialOf = (id) => materials.materials.find((x) => x.id === id);

function price(estimate, materialId) {
  const m = materialOf(materialId) ?? materials.materials[0];
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

const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "content-type, last-event-id, authorization, x-aakar-guest", "Access-Control-Allow-Methods": "GET, POST, PATCH, PUT, DELETE, OPTIONS" };
function problem(res, status, code, detail) {
  res.writeHead(status, { "Content-Type": "application/problem+json", ...CORS });
  res.end(JSON.stringify({ type: "about:blank", title: code.replace(/_/g, " "), status, detail, code }));
}
function json(res, status, body) {
  if (status === 204) {
    res.writeHead(204, CORS);
    return res.end();
  }
  res.writeHead(status, { "Content-Type": "application/json", ...CORS });
  res.end(JSON.stringify(body));
}
function sse(res) {
  res.writeHead(200, { "Content-Type": "text/event-stream", "Cache-Control": "no-cache", Connection: "keep-alive", ...CORS });
}
const fail = (status, code, message) => Object.assign(new Error(message), { status, code });

// ---- Identity -------------------------------------------------------------------------------
/** Who is calling: a user (bearer), a guest (X-Aakar-Guest), or nobody. A stale bearer is a 401. */
function identity(req) {
  const auth = req.headers.authorization;
  if (typeof auth === "string" && auth.startsWith("Bearer ")) {
    const userId = tokens.get(auth.slice(7).trim());
    if (!userId) throw fail(401, "unauthenticated", "This session is no longer valid. Sign in again.");
    return { kind: "user", id: userId, key: `user:${userId}` };
  }
  const guest = req.headers["x-aakar-guest"];
  if (typeof guest === "string" && guest) return { kind: "guest", id: guest, key: `guest:${guest}` };
  return null;
}
function requireUser(req) {
  const who = identity(req);
  if (!who || who.kind !== "user") throw fail(401, "unauthenticated", "Sign in to continue.");
  return who;
}

// ---- Cart -----------------------------------------------------------------------------------
function cartFor(key) {
  let c = carts.get(key);
  if (!c) {
    c = { id: randomUUID(), owner: key.startsWith("user:") ? "user" : "guest", items: [], updated_at: now() };
    carts.set(key, c);
  }
  return c;
}
function specsLine(v, m, unit) {
  const b = v.geometry?.bounds_mm ?? v.printability?.geometry?.bounds_mm;
  const dims = Array.isArray(b) ? `${b[0]} × ${b[1]} × ${b[2]} mm` : undefined;
  return [m.name, dims, `${Math.round(unit.mass_g)} g`].filter(Boolean).join(" · ");
}
function cartItemView(it) {
  const v = versions.get(it.version_id);
  const d = designs.get(v.design_id);
  const m = materialOf(it.material) ?? materials.materials[0];
  const unit = v.status === "ready" ? price(v.print_estimate, m.id) : { ...price(completed.print_estimate, m.id), subtotal_paise: 0, total_paise: 0 };
  return {
    id: it.id, design_id: v.design_id, version_id: v.id, version_no: v.version_no, title: d?.title ?? "Your piece",
    specs_line: specsLine(v, m, unit), material_id: m.id, material_name: m.name, qty: it.qty,
    unit_price: unit, line_total_paise: unit.subtotal_paise * it.qty, thumbnail_url: null,
    purchasable: v.status === "ready" && v.printability?.passed !== false,
    repriced: it.policy_version !== policy.version,
  };
}
function cartView(c) {
  const list = c.items.map(cartItemView);
  const subtotal = list.reduce((n, i) => n + i.line_total_paise, 0);
  const shipping = list.length === 0 ? 0 : subtotal >= policy.free_shipping_above_paise ? 0 : policy.shipping_flat_paise;
  return { id: c.id, owner: c.owner, items: list, subtotal_paise: subtotal, shipping_paise: shipping, shipping_label: policy.shipping_label, total_paise: subtotal + shipping, policy_version: policy.version, updated_at: c.updated_at };
}
function cartFrom(req) {
  const who = identity(req);
  if (!who) throw fail(400, "bad_request", "Send X-Aakar-Guest or sign in.");
  return cartFor(who.key);
}

/** Move a guest's designs and cart items to the user (POST /api/auth/otp/verify). */
function attachGuest(guestKey, userKey) {
  let designsMoved = 0;
  for (const d of designs.values()) if (d.owner === guestKey) { d.owner = userKey; designsMoved += 1; }
  let itemsMoved = 0;
  const guestCart = carts.get(guestKey);
  if (guestCart && guestCart.items.length > 0) {
    const userCart = cartFor(userKey);
    for (const it of guestCart.items) {
      const same = userCart.items.find((x) => x.version_id === it.version_id && x.material === it.material);
      if (same) same.qty = Math.min(20, same.qty + it.qty);
      else userCart.items.push(it);
      itemsMoved += 1;
    }
    userCart.updated_at = now();
    carts.delete(guestKey);
  }
  return { designs: designsMoved, cart_items: itemsMoved };
}

// ---- Orders and payments --------------------------------------------------------------------
const PINCODE_RE = /^[1-9][0-9]{5}$/;
const serviceable = (pincode) => !pincode.startsWith("9");
const addDays = (n) => { const d = new Date(); d.setDate(d.getDate() + n); return d.toISOString().slice(0, 10); };

function orderSummary(o) {
  return { id: o.id, number: o.number, status: o.status, stage: o.stage, title: o.title, total_paise: o.total_paise, items_count: o.items.reduce((n, i) => n + i.qty, 0), eta: o.eta, placed_at: o.placed_at };
}
function orderView(o) {
  return { ...orderSummary(o), items: o.items, address: o.address, subtotal_paise: o.subtotal_paise, shipping_paise: o.shipping_paise, shipping_label: o.shipping_label, policy_version: o.policy_version, payment: payments.get(o.payment_id), ...(o.shipment ? { shipment: o.shipment } : {}), events: o.events, notify_whatsapp: o.notify_whatsapp, note: o.note ?? null };
}
function newPayment(o) {
  const p = { id: randomUUID(), order_id: o.id, gateway: "mock", gateway_ref: `mock_order_${o.number.toLowerCase()}`, status: "created", method: null, amount_paise: o.total_paise, currency: "INR", pay_url: "", invoice_number: null, created_at: now(), finished_at: null };
  p.pay_url = `${WEB_URL}/checkout/pay/${p.id}`;
  payments.set(p.id, p);
  o.payment_id = p.id;
  return p;
}
function emitOrder(o, status, stage, message, detail) {
  o.status = status;
  o.stage = stage;
  const ev = { sequence: o.events.length + 1, status, stage, message, ...(detail ? { detail } : {}), at: now() };
  o.events.push(ev);
  const final = stage === "delivered" || stage === "cancelled";
  for (const res of orderStreams.get(o.id) ?? []) {
    res.write(`id: ${ev.sequence}\nevent: stage\ndata: ${JSON.stringify(ev)}\n\n`);
    if (final) res.end();
  }
  if (final) orderStreams.delete(o.id);
  return ev;
}
/** After payment: confirmed(queued) → queued → slicing → printing → finishing/qc/packed (sanding) → shipped → delivered, one every ORDER_STAGE_MS. */
function advanceOrder(o) {
  const bay = "Bay 07";
  const base = { studio: STUDIO };
  const steps = [
    ["queued", "queued", `In the queue. ${bay} is being set aside for your piece.`, { ...base, printer_bay: bay, queue_position: 2 }],
    ["slicing", "slicing", "Planning 480 layers at 0.16 mm before the first one is laid.", { ...base, printer_bay: bay, layer_height_mm: 0.16, layers_total: 480 }],
    ["printing", "printing", "Layer 212 of 480. Your five-second time-lapse is on its way to WhatsApp.", { ...base, printer_bay: bay, layer_height_mm: 0.16, layer: 212, layers_total: 480 }],
    ["finishing", "sanding", "Off the bed. Hand-sanding and sealing the silk finish.", { ...base, printer_bay: bay }],
    ["qc", "sanding", "Quality check: every dimension within 0.3 mm of your design.", { ...base }],
    ["packed", "sanding", "Packed in kraft with marigold tape and your card: Designed by you. Crafted by Aakar.", { ...base }],
    ["shipped", "shipped", "Handed to Delhivery. Tracking details are on this page.", { carrier: "mock-delhivery" }],
    ["delivered", "delivered", "Delivered. We hope it feels exactly like the one on your screen.", {}],
  ];
  let t = ORDER_STAGE_MS;
  for (const [status, stage, message, detail] of steps) {
    setTimeout(() => {
      if (o.status === "cancelled") return;
      if (status === "shipped") {
        o.shipment = { id: randomUUID(), carrier: "mock-delhivery", awb: `MOCK${String(orderSeq).padStart(4, "0")}${Math.floor(Math.random() * 9000 + 1000)}`, status: "in_transit", eta: o.eta, tracking_url: null, events: [{ status: "picked_up", message: `Picked up from the ${STUDIO} studio`, at: now() }] };
        detail.awb = o.shipment.awb;
      }
      if (status === "delivered" && o.shipment) {
        o.shipment.status = "delivered";
        o.shipment.events.push({ status: "delivered", message: "Delivered", at: now() });
      }
      emitOrder(o, status, stage, message, detail);
    }, t);
    t += ORDER_STAGE_MS;
  }
}

// ---- Generation jobs (Phase 0) --------------------------------------------------------------
function emit(job, stage, message, percent, extra = {}) {
  job.sequence += 1;
  job.stage = stage; job.message = message;
  const ev = { job_id: job.id, sequence: job.sequence, stage, message, percent, version_id: job.version_id ?? null, error_code: null, at: now(), ...extra };
  job.events.push(ev);
  for (const res of streams.get(job.id) ?? []) res.write(`id: ${ev.sequence}\nevent: stage\ndata: ${JSON.stringify(ev)}\n\n`);
  if (stage === "ready" || stage === "failed") for (const res of streams.get(job.id) ?? []) res.end();
}

function startJob(design, spec, parentVersionId) {
  const template = templates.find((t) => t.id === spec.template.split("@")[0]) ?? templates[0];
  for (const [k, v] of Object.entries(spec.params)) {
    const p = template.params[k];
    if (p && typeof v === "number" && ((p.min !== undefined && v < p.min) || (p.max !== undefined && v > p.max))) {
      throw fail(422, "param_out_of_range", `${p.label} must be between ${p.min} and ${p.max} ${p.unit}`);
    }
  }
  const versionNo = design.versions_count + 1;
  const version = {
    id: randomUUID(), design_id: design.id, version_no: versionNo, parent_version_id: parentVersionId ?? null, status: "generating",
    spec, template: { id: template.id, version: template.version }, created_by: "user", created_at: now(),
  };
  const job = { id: randomUUID(), design_id: design.id, version_id: version.id, version_no: versionNo, type: "generate", status: "queued", stage: "queued", message: "Queued", error_code: null, attempts: 1, created_at: now(), started_at: null, finished_at: null, sequence: 0, events: [] };
  version.job_id = job.id;
  versions.set(version.id, version);
  jobs.set(job.id, job);
  design.versions_count = versionNo;
  design.status = "generating";
  design.latest_version = version;
  jobCount += 1;
  const failThis = FAIL_EVERY_THIRD && jobCount % 3 === 0;

  const scale = (spec.params.height_mm ?? 120) / 120;
  const steps = [
    ["understanding", "Reading the template", 10],
    ["sculpting", "Weaving your design", 40],
    ["checking", "Checking physics", 70],
    ["pricing", "Pricing", 90],
  ];
  let t = STAGE_MS;
  job.status = "running"; job.started_at = now();
  for (const [stage, message, percent] of steps) {
    setTimeout(() => emit(job, stage, message, percent), t);
    t += STAGE_MS;
  }
  setTimeout(() => {
    if (failThis) {
      job.status = "failed"; job.error_code = "geometry_failed"; job.finished_at = now();
      version.status = "failed"; design.status = "failed";
      emit(job, "failed", "The arch collapsed at this width. Try a smaller tilt.", 100, { error_code: "geometry_failed" });
      return;
    }
    const est = { ...completed.print_estimate, print_seconds: Math.round(completed.print_estimate.print_seconds * scale), extruded_volume_cm3: +(completed.print_estimate.extruded_volume_cm3 * scale).toFixed(1) };
    const bounds = [spec.params.width_mm ?? spec.params.diameter_mm ?? 92, spec.params.depth_mm ?? spec.params.diameter_mm ?? 78, spec.params.height_mm ?? 120];
    Object.assign(version, {
      status: "ready",
      assets: completed.assets,
      geometry: { bounds_mm: bounds, volume_cm3: completed.printability.geometry.volume_cm3, surface_cm2: completed.printability.geometry.surface_cm2, triangles: completed.printability.geometry.triangles },
      printability: { ...completed.printability, geometry: { ...completed.printability.geometry, bounds_mm: bounds } },
      print_estimate: est,
      price: price(est, spec.material),
      karigar_note: versionNo === 1 ? completed.karigar_note : `Version ${versionNo}: I re-sculpted the ${template.name.toLowerCase()} at ${spec.params.height_mm ?? 120} mm tall and kept the walls at ${spec.params.wall_mm ?? 3.2} mm.`,
    });
    job.status = "succeeded"; job.finished_at = now();
    design.status = "ready";
    emit(job, "ready", "Ready", 100);
  }, t);
  return { design_id: design.id, version_no: versionNo, job_id: job.id, events_url: `/api/jobs/${job.id}/events` };
}

// ---- Router ---------------------------------------------------------------------------------
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const p = url.pathname;
  const method = req.method;
  if (method === "OPTIONS") {
    res.writeHead(204, CORS);
    return res.end();
  }
  let m;
  try {
    // ---- catalog, templates, designs, jobs (Phase 0) ----
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
    if (p === "/api/designs" && method === "POST") {
      const who = identity(req);
      const body = JSON.parse(await readBody(req));
      if (body.prompt || body.source === "create") return problem(res, 422, "not_yet_available", "Describing a piece in words arrives in Phase 2.");
      let template, params, material, title, slug = null;
      if (body.catalog_item_slug) {
        const item = items.find((i) => i.slug === body.catalog_item_slug);
        if (!item) return problem(res, 404, "not_found", "No piece with that name.");
        if (item.available === false) return problem(res, 422, "template_not_available", "This piece is still being finished.");
        template = templates.find((t) => t.id === item.template_id);
        params = { ...defaults(template), ...item.default_params, ...(body.params ?? {}) };
        material = body.material ?? item.default_material; title = item.name; slug = item.slug;
      } else if (body.template_id) {
        template = templates.find((t) => t.id === body.template_id);
        if (!template) return problem(res, 404, "not_found", "No such template.");
        params = { ...defaults(template), ...(body.params ?? {}) };
        material = body.material ?? template.materials[0]; title = body.title ?? template.name;
      } else return problem(res, 400, "bad_request", "Send catalog_item_slug or template_id.");
      const design = { id: randomUUID(), source: body.source, catalog_item_slug: slug, title, status: "generating", created_at: now(), versions_count: 0 };
      Object.defineProperty(design, "owner", { value: who?.key ?? null, writable: true, enumerable: false });
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
    if ((m = p.match(/^\/api\/versions\/([^/]+)\/params$/)) && method === "POST") {
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
      if (!materialOf(mat)) return problem(res, 404, "not_found", "No such finish.");
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
      sse(res);
      const last = Number(req.headers["last-event-id"] ?? 0);
      const replay = j.events.filter((e) => e.sequence > last);
      const current = replay.length ? replay : j.events.slice(-1);
      for (const ev of current) res.write(`id: ${ev.sequence}\nevent: stage\ndata: ${JSON.stringify(ev)}\n\n`);
      if (!current.length) res.write(`id: 0\nevent: stage\ndata: ${JSON.stringify({ job_id: j.id, sequence: 0, stage: "queued", message: "Queued", percent: 0, version_id: j.version_id, error_code: null, at: now() })}\n\n`);
      if (j.stage === "ready" || j.stage === "failed") return res.end();
      if (!streams.has(j.id)) streams.set(j.id, new Set());
      streams.get(j.id).add(res);
      req.on("close", () => streams.get(j.id)?.delete(res));
      return;
    }

    // ---- auth (mock OTP sender: the code is always 123456 and comes back as dev_code) ----
    if (p === "/api/auth/otp/request" && method === "POST") {
      const body = JSON.parse(await readBody(req));
      const phone = String(body.phone ?? "");
      if (!/^\+91[6-9][0-9]{9}$/.test(phone)) return problem(res, 400, "bad_request", "Send an Indian mobile as +91XXXXXXXXXX.");
      const t = Date.now();
      const recent = (otpRate.get(phone) ?? []).filter((x) => t - x < 60_000);
      if (recent.length >= 5) return problem(res, 429, "otp_rate_limited", "Too many codes for this number. Wait a minute.");
      recent.push(t);
      otpRate.set(phone, recent);
      const request_id = randomUUID();
      otps.set(request_id, { phone, code: DEV_CODE, expires_at: t + OTP_TTL_S * 1000 });
      console.log(`[otp] ${phone} → ${DEV_CODE} (request ${request_id})`);
      return json(res, 202, { request_id, expires_in_s: OTP_TTL_S, dev_code: DEV_CODE });
    }
    if (p === "/api/auth/otp/verify" && method === "POST") {
      const body = JSON.parse(await readBody(req));
      const otp = otps.get(String(body.request_id ?? ""));
      if (!otp) return problem(res, 401, "otp_invalid", "That code isn't right.");
      if (Date.now() > otp.expires_at) {
        otps.delete(body.request_id);
        return problem(res, 401, "otp_expired", "That code has expired. Ask for a new one.");
      }
      if (String(body.code ?? "").trim() !== otp.code) return problem(res, 401, "otp_invalid", "That code isn't right.");
      otps.delete(body.request_id);
      let userId = usersByPhone.get(otp.phone);
      if (!userId) {
        userId = randomUUID();
        users.set(userId, { id: userId, phone: otp.phone, name: null, email: null, created_at: now() });
        usersByPhone.set(otp.phone, userId);
      }
      const access_token = `mock.${randomUUID()}`;
      tokens.set(access_token, userId);
      const guest = req.headers["x-aakar-guest"];
      const attached = typeof guest === "string" && guest ? attachGuest(`guest:${guest}`, `user:${userId}`) : { designs: 0, cart_items: 0 };
      return json(res, 200, { access_token, token_type: "Bearer", expires_in_s: 30 * 86400, user: users.get(userId), attached });
    }
    if (p === "/api/auth/me" && method === "GET") {
      const who = requireUser(req);
      return json(res, 200, users.get(who.id));
    }
    if (p === "/api/auth/me" && method === "PATCH") {
      const who = requireUser(req);
      const body = JSON.parse(await readBody(req));
      const u = users.get(who.id);
      if (body.name !== undefined) u.name = body.name;
      if (body.email !== undefined) u.email = body.email;
      return json(res, 200, u);
    }
    if (p === "/api/auth/logout" && method === "POST") {
      const auth = req.headers.authorization;
      if (typeof auth === "string" && auth.startsWith("Bearer ")) tokens.delete(auth.slice(7).trim());
      return json(res, 204);
    }

    // ---- addresses ----
    if (p === "/api/me/addresses" && method === "GET") {
      const who = requireUser(req);
      return json(res, 200, addresses.get(who.id) ?? []);
    }
    if (p === "/api/me/addresses" && method === "POST") {
      const who = requireUser(req);
      const a = validateAddress(JSON.parse(await readBody(req)));
      const list = addresses.get(who.id) ?? [];
      if (a.is_default) for (const x of list) x.is_default = false;
      const saved = { id: randomUUID(), ...a };
      list.push(saved);
      addresses.set(who.id, list);
      return json(res, 201, saved);
    }
    if ((m = p.match(/^\/api\/me\/addresses\/([^/]+)$/))) {
      const who = requireUser(req);
      const list = addresses.get(who.id) ?? [];
      const idx = list.findIndex((x) => x.id === m[1]);
      if (idx < 0) return problem(res, 404, "not_found", "No such address.");
      if (method === "PUT") {
        const a = validateAddress(JSON.parse(await readBody(req)));
        if (a.is_default) for (const x of list) x.is_default = false;
        list[idx] = { id: m[1], ...a };
        return json(res, 200, list[idx]);
      }
      if (method === "DELETE") {
        list.splice(idx, 1);
        return json(res, 204);
      }
    }

    // ---- cart ----
    if (p === "/api/cart" && method === "GET") return json(res, 200, cartView(cartFrom(req)));
    if (p === "/api/cart" && method === "DELETE") {
      const c = cartFrom(req);
      c.items = []; c.updated_at = now();
      return json(res, 200, cartView(c));
    }
    if (p === "/api/cart/items" && method === "POST") {
      const c = cartFrom(req);
      const body = JSON.parse(await readBody(req));
      const v = versions.get(String(body.version_id ?? ""));
      if (!v) return problem(res, 404, "not_found", "No such version.");
      if (!materialOf(body.material)) return problem(res, 422, "validation_failed", "Unknown finish.");
      const qty = body.qty === undefined ? 1 : Number(body.qty);
      if (!Number.isInteger(qty) || qty < 1 || qty > 20) return problem(res, 422, "validation_failed", "Quantity must be between 1 and 20.");
      if (v.status !== "ready") return problem(res, 409, "version_not_ready", "Still sculpting; add it once it's ready.");
      if (v.printability?.passed === false) return problem(res, 409, "not_printable", "This version failed the stability check.");
      const same = c.items.find((x) => x.version_id === v.id && x.material === body.material);
      if (same) same.qty = Math.min(20, same.qty + qty);
      else c.items.push({ id: randomUUID(), version_id: v.id, material: body.material, qty, policy_version: policy.version, added_at: now() });
      c.updated_at = now();
      return json(res, 201, cartView(c));
    }
    if ((m = p.match(/^\/api\/cart\/items\/([^/]+)$/))) {
      const c = cartFrom(req);
      const it = c.items.find((x) => x.id === m[1]);
      if (!it) return problem(res, 404, "not_found", "That piece isn't in your cart.");
      if (method === "PATCH") {
        const body = JSON.parse(await readBody(req));
        if (body.qty !== undefined) {
          const qty = Number(body.qty);
          if (!Number.isInteger(qty) || qty < 1 || qty > 20) return problem(res, 422, "validation_failed", "Quantity must be between 1 and 20.");
          it.qty = qty;
        }
        if (body.material !== undefined) {
          if (!materialOf(body.material)) return problem(res, 422, "validation_failed", "Unknown finish.");
          it.material = body.material;
        }
        c.updated_at = now();
        return json(res, 200, cartView(c));
      }
      if (method === "DELETE") {
        c.items = c.items.filter((x) => x !== it);
        c.updated_at = now();
        return json(res, 200, cartView(c));
      }
    }

    // ---- shipping ----
    if (p === "/api/shipping/serviceability") {
      const pincode = url.searchParams.get("pincode") ?? "";
      if (!PINCODE_RE.test(pincode)) return problem(res, 400, "bad_request", "pincode must be six digits.");
      return json(res, 200, serviceable(pincode) ? { pincode, serviceable: true, carrier: "mock-delhivery", eta_days: 4, cod_available: false } : { pincode, serviceable: false, carrier: "mock-delhivery" });
    }

    // ---- checkout ----
    if (p === "/api/checkout" && method === "POST") {
      const who = requireUser(req);
      const body = JSON.parse(await readBody(req));
      if (!body.address_id) return problem(res, 400, "bad_request", "address_id is required.");
      const address = (addresses.get(who.id) ?? []).find((a) => a.id === body.address_id);
      if (!address) return problem(res, 404, "not_found", "No such address.");
      const c = cartView(cartFor(who.key));
      if (c.items.length === 0) return problem(res, 409, "cart_empty", "Your cart is empty.");
      if (c.items.some((i) => !i.purchasable)) return problem(res, 409, "not_printable", "A piece in your cart can't be printed as it is.");
      if (!serviceable(address.pincode)) return problem(res, 422, "not_serviceable", `We can't deliver to ${address.pincode} yet.`);
      orderSeq += 1;
      const first = c.items[0];
      const order = {
        id: randomUUID(), number: `AK-${String(orderSeq).padStart(6, "0")}`, status: "pending_payment", stage: "payment",
        title: c.items.length > 1 ? `${first.title} + ${c.items.length - 1} more` : first.title,
        items: c.items.map((i) => ({ id: randomUUID(), design_id: i.design_id, version_id: i.version_id, version_no: i.version_no, title: i.title, specs_line: i.specs_line, material_id: i.material_id, material_name: i.material_name, qty: i.qty, unit_price: i.unit_price, line_total_paise: i.line_total_paise, assets: versions.get(i.version_id)?.assets ?? {} })),
        address: { ...address }, subtotal_paise: c.subtotal_paise, shipping_paise: c.shipping_paise, shipping_label: c.shipping_label, total_paise: c.total_paise, policy_version: c.policy_version,
        eta: null, placed_at: now(), notify_whatsapp: body.notify_whatsapp !== false, note: body.note ?? null, events: [], owner: who.id, payment_id: null, shipment: undefined,
      };
      orders.set(order.id, order);
      const payment = newPayment(order);
      emitOrder(order, "pending_payment", "payment", "Awaiting payment. Your pieces are reserved.");
      return json(res, 201, { order_id: order.id, order_number: order.number, payment });
    }

    // ---- orders ----
    if (p === "/api/orders" && method === "GET") {
      const who = requireUser(req);
      const mine = [...orders.values()].filter((o) => o.owner === who.id).sort((a, b) => (a.placed_at < b.placed_at ? 1 : -1));
      return json(res, 200, mine.map(orderSummary));
    }
    if ((m = p.match(/^\/api\/orders\/([^/]+)$/)) && method === "GET") {
      const who = requireUser(req);
      const o = orders.get(m[1]);
      if (!o || o.owner !== who.id) return problem(res, 404, "not_found", "No such order.");
      return json(res, 200, orderView(o));
    }
    if ((m = p.match(/^\/api\/orders\/([^/]+)\/events$/)) && method === "GET") {
      const who = requireUser(req);
      const o = orders.get(m[1]);
      if (!o || o.owner !== who.id) return problem(res, 404, "not_found", "No such order.");
      sse(res);
      const last = Number(req.headers["last-event-id"] ?? 0);
      const replay = o.events.filter((e) => e.sequence > last);
      const current = replay.length ? replay : o.events.slice(-1);
      for (const ev of current) res.write(`id: ${ev.sequence}\nevent: stage\ndata: ${JSON.stringify(ev)}\n\n`);
      if (o.stage === "delivered" || o.stage === "cancelled") return res.end();
      if (!orderStreams.has(o.id)) orderStreams.set(o.id, new Set());
      orderStreams.get(o.id).add(res);
      const keepAlive = setInterval(() => res.write(": keep-alive\n\n"), 15_000);
      req.on("close", () => {
        clearInterval(keepAlive);
        orderStreams.get(o.id)?.delete(res);
      });
      return;
    }
    if ((m = p.match(/^\/api\/orders\/([^/]+)\/payments$/)) && method === "POST") {
      const who = requireUser(req);
      const o = orders.get(m[1]);
      if (!o || o.owner !== who.id) return problem(res, 404, "not_found", "No such order.");
      if (o.status !== "pending_payment") return problem(res, 409, "order_not_payable", "This order isn't waiting for payment.");
      const previous = payments.get(o.payment_id);
      if (previous && (previous.status === "created" || previous.status === "pending")) {
        previous.status = "failed"; previous.finished_at = now();
      }
      return json(res, 201, newPayment(o));
    }

    // ---- payments ----
    if ((m = p.match(/^\/api\/payments\/([^/]+)$/)) && method === "GET") {
      const who = requireUser(req);
      const pay = payments.get(m[1]);
      if (!pay || orders.get(pay.order_id)?.owner !== who.id) return problem(res, 404, "not_found", "No such payment.");
      return json(res, 200, pay);
    }
    if ((m = p.match(/^\/api\/payments\/([^/]+)\/mock\/complete$/)) && method === "POST") {
      const who = requireUser(req);
      const pay = payments.get(m[1]);
      const o = pay && orders.get(pay.order_id);
      if (!pay || !o || o.owner !== who.id) return problem(res, 404, "not_found", "No such payment.");
      if (pay.gateway !== "mock" || pay.status === "succeeded" || pay.status === "failed" || pay.status === "refunded") return problem(res, 409, "payment_final", "This payment is already final.");
      const body = JSON.parse(await readBody(req));
      if (body.outcome !== "success" && body.outcome !== "failure") return problem(res, 400, "bad_request", "outcome must be success or failure.");
      pay.method = ["upi", "card", "netbanking"].includes(body.method) ? body.method : "upi";
      pay.finished_at = now();
      if (body.outcome === "failure") {
        pay.status = "failed";
        emitOrder(o, "pending_payment", "payment", "A payment attempt failed. Your pieces are still reserved.");
        return json(res, 200, pay);
      }
      pay.status = "succeeded";
      invoiceSeq += 1;
      pay.invoice_number = `AK-INV-${new Date().getFullYear()}-${String(invoiceSeq).padStart(5, "0")}`;
      pay.gateway_ref = `mock_pay_${pay.id.slice(0, 8)}`;
      o.eta = addDays(4);
      // Same path a real gateway's webhook would take: confirm, empty the cart, hand to the studio.
      emitOrder(o, "confirmed", "queued", `Order confirmed. Your piece is in the queue at our ${STUDIO} studio.`, { studio: STUDIO });
      const c = carts.get(who.key);
      if (c) { c.items = []; c.updated_at = now(); }
      advanceOrder(o);
      return json(res, 200, pay);
    }

    return problem(res, 404, "not_found", `No route for ${method} ${p}`);
  } catch (err) {
    if (err instanceof SyntaxError) return problem(res, 400, "bad_request", "Body must be JSON.");
    if (!err.status) console.error(err);
    return problem(res, err.status ?? 500, err.code ?? "internal", err.message);
  }
});

function validateAddress(body) {
  const required = ["name", "phone", "line1", "city", "state", "pincode"];
  for (const k of required) if (!body[k] || typeof body[k] !== "string" || !body[k].trim()) throw fail(422, "validation_failed", `${k} is required.`);
  if (!PINCODE_RE.test(body.pincode)) throw fail(422, "validation_failed", "pincode must be six digits.");
  return { label: body.label ?? undefined, name: body.name.trim(), phone: body.phone.trim(), line1: body.line1.trim(), line2: body.line2?.trim() || undefined, city: body.city.trim(), state: body.state.trim(), pincode: body.pincode, is_default: Boolean(body.is_default) };
}
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

server.listen(PORT, () => console.log(`Aakar mock API on http://localhost:${PORT}${FAIL_EVERY_THIRD ? " (every third job fails)" : ""} · pay pages on ${WEB_URL} · dev OTP ${DEV_CODE} · order stage every ${ORDER_STAGE_MS} ms`));
