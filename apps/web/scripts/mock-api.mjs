#!/usr/bin/env node
// A tiny stand-in for services/api so the storefront can be exercised before Spring Boot lands.
// Serves the Phase 0 contract shapes (designs, jobs, SSE) and the Phase 1 customer loop (ADR-0013):
// phone OTP with a dev code, guest → user attach, addresses, cart, serviceability, checkout, a mock
// payment gateway completed from the placeholder pay page, orders that advance a stage every few
// seconds, and an order SSE stream. Outcome categories (Avatars) come from packages/design-tokens/families.json:
// shelves, families with their live templates, customer uploads (multipart, kept in memory and served back under
// /media/uploads/), features (the Chhaap) on designs and param edits, hardware and setup price lines and family
// minimums. Duniya experiences and viewer backdrops come from packages/design-tokens/experiences.json and the Buti
// motif library from packages/design-tokens/motifs/ (index.json + the SVGs, served back under /api/motifs/{id}.svg).
// Everything is in memory.
//
// Usage: node scripts/mock-api.mjs [port=8080] [--fail]
//   --fail                    every third generation job fails
//   AAKAR_WEB_URL             where pay_url points (default http://localhost:3000)
//   AAKAR_ORDER_STAGE_MS      ms between order stages after payment (default 8000)
//   AAKAR_REVIEW_MS           ms before a file held for review is decided (default 10000)
//   Dev OTP code is always 123456. Pincodes starting with 9 are not serviceable.
//   Uploads whose file name contains "review" answer pending_review and are cleared after AAKAR_REVIEW_MS; "reject"
//   ones are held the same way and then turned down with a reviewer's message. GET /api/uploads/{id} answers only the
//   identity that uploaded the file (a guest's uploads move to the user on sign-in). A model file named "*broken*"
//   fails its job with content_unusable; a raw print under 30 mm completes with printability.passed=false (thin walls).
//   Swaroop (raw_print@1) has no template params: its size and orientation come from its one hero_mesh feature.
//   A spot takes a photo or a form on its own, or a name and a motif side by side; a photo with a name or a motif on the
//   same anchor is refused as crowded (422 validation_failed), like the geometry service does. Like the API, content
//   must take a mode its anchor lists (`modes`: 422 unsupported_feature, `field` = features[i].mode), every anchor marked
//   `required` needs content (422 validation_failed: the night light's photo), and a text (Naam) stays within its
//   family's max_text_chars, counted as a reader counts letters (422 param_out_of_range). A design started from a Duniya
//   experience takes its style as spec.style when the template offers it (style_variants); under comic_pop a text or
//   motif without a mode is raised where its anchor allows it, and a raised one without a depth stands 1.5 mm proud
//   (capped at the anchor's max_relief_mm). A new version keeps its parent's style.
import http from "node:http";
import { createHash, randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../../..");
const materials = JSON.parse(readFileSync(path.join(root, "packages/design-tokens/materials.json"), "utf8"));
const completed = JSON.parse(readFileSync(path.join(root, "packages/contracts/examples/design.completed.example.json"), "utf8"));
const familiesSeed = JSON.parse(readFileSync(path.join(root, "packages/design-tokens/families.json"), "utf8"));
const hardwareItems = new Map(familiesSeed.hardware_items.map((h) => [h.sku, h]));
const experiencesSeed = JSON.parse(readFileSync(path.join(root, "packages/design-tokens/experiences.json"), "utf8"));
const motifsDir = path.join(root, "packages/design-tokens/motifs");
const motifLibrary = JSON.parse(readFileSync(path.join(motifsDir, "index.json"), "utf8"));
/** The artwork of every motif, read once: served at /api/motifs/{id}.svg. */
const motifArt = new Map(motifLibrary.motifs.map((m) => [m.id, readFileSync(path.join(motifsDir, m.file))]));

const PORT = Number(process.argv[2] && !process.argv[2].startsWith("--") ? process.argv[2] : 8080);
const FAIL_EVERY_THIRD = process.argv.includes("--fail");
const STAGE_MS = 700;
const WEB_URL = (process.env.AAKAR_WEB_URL ?? "http://localhost:3000").replace(/\/$/, "");
const ORDER_STAGE_MS = Number(process.env.AAKAR_ORDER_STAGE_MS ?? 8000);
const REVIEW_MS = Number(process.env.AAKAR_REVIEW_MS ?? 10000);
const DEV_CODE = "123456";
const OTP_TTL_S = 300;
const STUDIO = "Bengaluru";

// The live template descriptors exported from the geometry service (`make descriptors`), one source for every mock.
const templates = JSON.parse(readFileSync(path.join(root, "packages/contracts/examples/template-descriptors.json"), "utf8"));

// ---- Outcome families (Avatars) from the seed: shelves, hardware, families + their live templates ----
const shelves = [...familiesSeed.shelves].sort((a, b) => (a.sort_order ?? 100) - (b.sort_order ?? 100));
const familyById = (id) => familiesSeed.families.find((f) => f.id === id);
/** Customer copy never shows a codename alone: "Saathi · Keychain & bag charm". */
const familyLabel = (f) => `${f.codename} · ${f.name}`;
/** Placeholder price rules from the plan (open decision 3): family minimums, a setup fee for raw prints, hardware markup. */
const FAMILY_RULES = {
  keychain: { minimum_subtotal_paise: 24900 },
  fridge_magnet: { minimum_subtotal_paise: 29900 },
  ornament: { minimum_subtotal_paise: 34900 },
  nameplate: { minimum_subtotal_paise: 59900 },
  raw_print: { minimum_subtotal_paise: 34900, setup_fee_paise: 9900 },
};
const HARDWARE_MARKUP = 0.3;
const withName = (h) => ({ sku: h.sku, qty: h.qty, ...(hardwareItems.get(h.sku) ? { name: hardwareItems.get(h.sku).name } : {}) });
const familyReady = (f) => templates.some((t) => t.id === f.default_template_id);
function familyView(f) {
  return {
    ...f,
    hardware: (f.hardware ?? []).map(withName),
    ready: familyReady(f),
    templates: templates.filter((t) => t.family === f.id),
    price_from_paise: FAMILY_RULES[f.id]?.minimum_subtotal_paise ?? null,
  };
}
/** The hardware packed with a version: the template's list when it has one, else the family default. */
function hardwareFor(template, family) {
  const list = template?.hardware?.length ? template.hardware : (family?.hardware ?? []);
  return list.map(withName);
}
function materialAllowed(family, materialId) {
  const rules = family?.material_rules;
  const m = materialOf(materialId);
  if (!rules || !m) return true;
  if (rules.allowed && !rules.allowed.includes(m.id)) return false;
  if (rules.heat_safe_only && !m.heat_safe) return false;
  if ((rules.excluded_finish_classes ?? []).includes(m.finish_class)) return false;
  return true;
}

const items = [
  { slug: "saathi-photo-keychain", name: "Saathi · Photo keychain", category: "keychains_charms", family_id: "keychain", template_id: "keychain_tag", default_params: { shape: "rounded", width_mm: 45, thickness_mm: 3, hole_d_mm: 4.2 }, default_material: "indigo_matte", base_price_paise: 24900, specs_line: "45 mm · steel split ring · 9 g", environment: "studio", description: "A palm-sized charm with a steel ring. Your photo or name sits in relief on the face; the ring loop is part of the print.", available: true },
  { slug: "jharokha-phone-stand", name: "Jharokha Phone Stand", category: "desk_tech", family_id: "phone_stand", template_id: "jharokha_phone_stand", default_params: { width_mm: 92, depth_mm: 78, height_mm: 120, tilt_deg: 70, lip_height_mm: 12, wall_mm: 3.2, arch_cusps: 5 }, default_material: "terracotta_silk", base_price_paise: 49900, specs_line: "Fits phones to 6.9″ · 92 × 78 × 120 mm · 64 g", environment: "desk_oak", description: "A cusped jharokha arch, printed in one piece, that holds any phone at a comfortable tilt.", available: true },
  { slug: "ajrakh-coasters", name: "Ajrakh Coasters · set of 4", category: "kitchen", family_id: "coaster", template_id: "jharokha_phone_stand", default_params: {}, default_material: "indigo_matte", base_price_paise: 64900, specs_line: "100 mm · heat-safe to 90 °C · 28 g each", environment: "kitchen_marble", available: false },
  { slug: "fluted-planter", name: "Fluted Planter · drainage tray", category: "home_decor", family_id: "planter", template_id: "fluted_planter", default_params: { diameter_mm: 140, height_mm: 130, flutes: 24, tray: true }, default_material: "terracotta_matte", base_price_paise: 89900, specs_line: "140 mm pot · fits 4″ nursery plants · 210 g", environment: "balcony_daylight", available: true },
  { slug: "kantha-nameplate", name: "Nameplate · Kantha border", category: "nameplates", family_id: "nameplate", template_id: "jharokha_phone_stand", default_params: {}, default_material: "polished_brass", base_price_paise: 119900, specs_line: "300 × 110 mm · raised letters · screws included", environment: "studio", available: false },
  { slug: "elephant-bookends", name: "Elephant Bookends", category: "gifting", family_id: "bookend", template_id: "jharokha_phone_stand", default_params: {}, default_material: "sandalwood_silk", base_price_paise: 134900, specs_line: "180 mm tall · weighted · holds 6 kg of books", environment: "teak_table_candlelight", available: false },
  { slug: "pillar-headphone-stand", name: "Headphone Stand · Pillar", category: "desk_tech", family_id: "headphone_stand", template_id: "jharokha_phone_stand", default_params: {}, default_material: "basic_white", base_price_paise: 57900, specs_line: "270 mm tall · weighted base · 180 g", environment: "desk_oak", available: false },
];

// ---- Duniya (experiences), Mahaul (viewer backdrops) and the Buti library -------------------------------------------
const environments = [...experiencesSeed.environments].sort((a, b) => (a.sort_order ?? 100) - (b.sort_order ?? 100) || a.id.localeCompare(b.id));
const experiences = [...experiencesSeed.experiences].sort((a, b) => (a.sort_order ?? 100) - (b.sort_order ?? 100) || a.id.localeCompare(b.id));
const experienceById = (id) => experiences.find((e) => e.id === id);
/** "Utsav · Festive & gifting". */
const experienceLabel = (e) => `${e.codename} · ${e.title}`;
/**
 * As GET /api/experiences answers: avatars expanded to the families orderable today (available and backed by a live
 * template), in the experience's order; items expanded to the curated Shop items in order (Coming soon ones kept); the
 * lowest family floor as price_from_paise.
 */
function experienceView(e) {
  const avatars = (e.avatars ?? []).map(familyById).filter((f) => f && f.available && familyReady(f)).map(familyView);
  const curated = (e.items ?? []).map((slug) => items.find((i) => i.slug === slug)).filter(Boolean);
  const floors = avatars.map((a) => a.price_from_paise).filter((n) => typeof n === "number");
  return {
    ...e,
    style: e.style ?? "none",
    motif_pack: e.motif_pack ?? [],
    avatars,
    items: curated,
    collections: e.collections ?? [],
    season: e.season ?? [],
    sort_order: e.sort_order ?? 100,
    price_from_paise: floors.length > 0 ? Math.min(...floors) : null,
  };
}
const motifById = (id) => motifLibrary.motifs.find((m) => m.id === id);
/** A library row as GET /api/motifs answers: `svg_url` is a browser URL on this server. */
const motifView = (m, host) => ({ id: m.id, label: m.label, tags: m.tags ?? [], min_scale: m.min_scale, svg_url: `http://${host}/api/motifs/${m.id}.svg` });
const percent = (scale) => `${Math.round(scale * 100)}%`;

// ---- Phase 0 state: designs, versions, jobs -------------------------------------------------
const designs = new Map();
const versions = new Map();
const jobs = new Map();
const streams = new Map(); // jobId -> Set<res>
let jobCount = 0;
const uploads = new Map(); // uploadId -> { meta: Upload, data: Buffer, contentType, filename, owner }

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

const round9 = (paise) => (Math.ceil(paise / 100 / 10) * 10 - 1) * 100;
/** `ctx` = { familyId, hardware } from the version: adds the hardware and setup lines and the family minimum. */
function price(estimate, materialId, ctx = {}) {
  const m = materialOf(materialId) ?? materials.materials[0];
  const mass = +(estimate.extruded_volume_cm3 * m.density_g_cm3).toFixed(1);
  const material = Math.round(mass * m.rate_per_g_paise);
  const machine = Math.round((estimate.print_seconds / 3600) * policy.machine_rate_paise_per_hour);
  const finishing = policy.finishing_fee_paise[m.finish_class];
  const h = Math.floor(estimate.print_seconds / 3600), min = Math.round((estimate.print_seconds % 3600) / 60);
  const lines = [
    { code: "material", label: `Material · ${Math.round(mass)} g ${m.name}`, amount_paise: material },
    { code: "machine_time", label: `Print time · ${h} h ${String(min).padStart(2, "0")} m`, amount_paise: machine },
    { code: "finishing", label: m.finish_class === "silk" ? "Hand sanding & sealing" : "Hand finishing", amount_paise: finishing },
  ];
  const hw = (ctx.hardware ?? []).map((x) => ({ ...x, item: hardwareItems.get(x.sku) })).filter((x) => x.item);
  if (hw.length > 0) {
    lines.push({
      code: "hardware",
      label: `Hardware · ${hw.map((x) => (x.qty > 1 ? `${x.qty} × ${x.item.name}` : x.item.name)).join(", ")}`,
      detail: "Bought-in parts packed with the piece",
      amount_paise: Math.round(hw.reduce((n, x) => n + x.qty * x.item.unit_cost_paise * (1 + HARDWARE_MARKUP), 0)),
    });
  }
  const rule = FAMILY_RULES[ctx.familyId];
  if (rule?.setup_fee_paise) lines.push({ code: "setup", label: "Checking and setting up your file", amount_paise: rule.setup_fee_paise });
  const raw = lines.reduce((n, l) => n + l.amount_paise, 0);
  let subtotal = round9(raw);
  let minimum;
  if (rule?.minimum_subtotal_paise && subtotal < rule.minimum_subtotal_paise) {
    minimum = round9(rule.minimum_subtotal_paise);
    subtotal = minimum;
  }
  const shipping = subtotal >= policy.free_shipping_above_paise ? 0 : policy.shipping_flat_paise;
  return {
    currency: "INR", material_id: m.id, mass_g: mass, print_seconds: estimate.print_seconds, lines,
    subtotal_paise: subtotal, shipping_paise: shipping, shipping_label: policy.shipping_label, total_paise: subtotal + shipping, policy_version: policy.version,
    ...(ctx.familyId ? { family_id: ctx.familyId } : {}),
    ...(minimum ? { minimum_subtotal_paise: minimum } : {}),
  };
}
const priceCtx = (v) => ({ familyId: v.family_id ?? undefined, hardware: v.hardware ?? [] });

const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "content-type, last-event-id, authorization, x-aakar-guest", "Access-Control-Allow-Methods": "GET, POST, PATCH, PUT, DELETE, OPTIONS" };
/** Problem Details; `extra` adds the API's properties (template_id, feature, field, anchor, …) beside the code. */
function problem(res, status, code, detail, extra = {}) {
  res.writeHead(status, { "Content-Type": "application/problem+json", ...CORS });
  res.end(JSON.stringify({ type: "about:blank", title: code.replace(/_/g, " "), status, detail, code, ...extra }));
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
const fail = (status, code, message, extra) => Object.assign(new Error(message), { status, code, extra });

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
  const unit = v.status === "ready" ? price(v.print_estimate, m.id, priceCtx(v)) : { ...price(completed.print_estimate, m.id, priceCtx(v)), subtotal_paise: 0, total_paise: 0 };
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
  for (const up of uploads.values()) if (up.owner === guestKey) up.owner = userKey;
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
  const family = familyById(design.family_id ?? template.family);
  const versionNo = design.versions_count + 1;
  const version = {
    id: randomUUID(), design_id: design.id, version_no: versionNo, parent_version_id: parentVersionId ?? null, status: "generating",
    spec, template: { id: template.id, version: template.version }, family_id: family?.id ?? null, hardware: hardwareFor(template, family),
    created_by: "user", created_at: now(),
  };
  const job = { id: randomUUID(), design_id: design.id, version_id: version.id, version_no: versionNo, type: "generate", status: "queued", stage: "queued", message: "Queued", error_code: null, attempts: 1, created_at: now(), started_at: null, finished_at: null, sequence: 0, events: [] };
  version.job_id = job.id;
  versions.set(version.id, version);
  jobs.set(job.id, job);
  design.versions_count = versionNo;
  design.status = "generating";
  design.latest_version = version;
  jobCount += 1;
  const hero = (spec.features ?? []).find((f) => f.type === "hero_mesh");
  const heroFile = hero ? uploads.get(hero.source?.upload_id)?.filename ?? "" : "";
  const contentUnusable = hero && /broken/i.test(heroFile);
  const failThis = (FAIL_EVERY_THIRD && jobCount % 3 === 0) || contentUnusable;
  // The hero form carries its own size: `longest` scales to longest_mm, `contain` fills the volume anchor.
  const heroBounds = hero ? template.anchors.find((a) => a.id === hero.anchor)?.bounds_mm : undefined;
  const longest = hero ? Number(hero.fit === "longest" && hero.longest_mm ? hero.longest_mm : Math.min(...(heroBounds ?? [80]))) : undefined;
  const orientation = hero?.orientation ?? "as_uploaded";

  const scale = hero ? longest / 120 : template.id === "keychain_tag" ? (spec.params.width_mm ?? 45) / 120 : (spec.params.height_mm ?? 120) / 120;
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
      const code = contentUnusable ? "content_unusable" : "geometry_failed";
      job.status = "failed"; job.error_code = code; job.finished_at = now();
      version.status = "failed"; design.status = "failed";
      emit(job, "failed", contentUnusable ? "We couldn't repair this file. Try another export from your 3D program." : "The arch collapsed at this width. Try a smaller tilt.", 100, { error_code: code });
      return;
    }
    const est = { ...completed.print_estimate, print_seconds: Math.round(completed.print_estimate.print_seconds * scale), extruded_volume_cm3: +(completed.print_estimate.extruded_volume_cm3 * scale).toFixed(1) };
    const bounds = hero
      ? [longest, +(longest * 0.6).toFixed(1), +(longest * 0.8).toFixed(1)]
      : template.id === "keychain_tag"
        ? [spec.params.width_mm ?? 45, +((spec.params.width_mm ?? 45) * 1.15).toFixed(1), spec.params.thickness_mm ?? 3]
        : [spec.params.width_mm ?? spec.params.diameter_mm ?? 92, spec.params.depth_mm ?? spec.params.diameter_mm ?? 78, spec.params.height_mm ?? 120];
    // A raw print under 30 mm comes out with walls thinner than the 1.2 mm floor: completes, but is not purchasable.
    const thin = hero && template.family === "raw_print" && longest < 30;
    const printability = {
      ...completed.printability,
      passed: thin ? false : completed.printability.passed,
      geometry: { ...completed.printability.geometry, bounds_mm: bounds },
      checks: thin
        ? { ...completed.printability.checks, thinnest_wall: { status: "fail", summary: `0.8 mm at ${longest} mm · below 1.2 mm`, value: 0.8, unit: "mm", threshold: 1.2 } }
        : completed.printability.checks,
    };
    const anchorLabel = (id) => template.anchors.find((a) => a.id === id)?.label?.toLowerCase() ?? "piece";
    const content = (spec.features ?? []).map((f) =>
      f.type === "emboss_text" ? `your text “${f.text}” on the ${anchorLabel(f.anchor)}`
        : f.type === "relief_image" ? `your photo ${f.mode === "deboss" ? "cut into" : "raised on"} the ${anchorLabel(f.anchor)}`
          : f.type === "hero_mesh" ? `your own 3D form at ${longest} mm`
            : `a ${(motifById(f.motif_id)?.label ?? f.motif_id.replace(/_/g, " ")).toLowerCase()} motif ${f.mode === "emboss" ? "raised on" : "cut into"} the ${anchorLabel(f.anchor)}`);
    const contentNote = content.length ? ` I set ${content.length > 1 ? `${content.slice(0, -1).join(", ")} and ${content[content.length - 1]}` : content[0]}.` : "";
    // The design remembers the Duniya it started from, so the note (and the packaging card) can name the theme.
    const theme = design.experience_id ? experienceById(design.experience_id) : undefined;
    const themeNote = theme && versionNo === 1 ? ` Made for ${experienceLabel(theme)}.` : "";
    const styleNote = spec.style === "comic_pop"
      ? " Comic pop style: the lettering and motifs stand bold and raised, like inked panel art; they read best in a high-contrast finish such as Basic White or Indigo Matte."
      : "";
    Object.assign(version, {
      status: "ready",
      assets: completed.assets,
      geometry: { bounds_mm: bounds, volume_cm3: completed.printability.geometry.volume_cm3, surface_cm2: completed.printability.geometry.surface_cm2, triangles: completed.printability.geometry.triangles },
      printability,
      print_estimate: est,
      price: price(est, spec.material, priceCtx(version)),
      karigar_note: hero && template.family === "raw_print"
        ? `Your file, repaired and set at ${longest} mm on its longest side, ${orientation === "lay_flat" ? "laid flat on its widest face" : "printed as you sent it"}.${thin ? " The thinnest wall came out under 1.2 mm; a larger size fixes that." : ""}`
        : versionNo === 1
          ? (template.id === "keychain_tag" ? `A ${spec.params.shape ?? "rounded"} Saathi tag, ${spec.params.width_mm ?? 45} mm wide with a ${spec.params.hole_d_mm ?? 4.2} mm ring hole.` : completed.karigar_note) + contentNote + themeNote + styleNote
          : `Version ${versionNo}: I re-sculpted the ${template.name.toLowerCase()} at ${spec.params.height_mm ?? spec.params.width_mm ?? 120} mm and kept the walls at ${spec.params.wall_mm ?? spec.params.thickness_mm ?? 3.2} mm.${contentNote}${styleNote}`,
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
    if (p === "/api/catalog/shelves") return json(res, 200, shelves);
    // ---- outcome families (Avatars): available with a live template; the raw family included ----
    if (p === "/api/families") {
      const kind = url.searchParams.get("kind");
      const list = familiesSeed.families.filter((f) => f.available && familyReady(f) && (!kind || f.kind === kind)).sort((a, b) => (a.sort_order ?? 100) - (b.sort_order ?? 100));
      return json(res, 200, list.map(familyView));
    }
    if ((m = p.match(/^\/api\/families\/([^/]+)$/))) {
      const f = familyById(decodeURIComponent(m[1]));
      return f ? json(res, 200, familyView(f)) : problem(res, 404, "unknown_family", "We don't make that kind of piece.");
    }
    // ---- Duniya: open experiences (avatars and items expanded), any experience by slug, the backdrops ----
    if (p === "/api/experiences" && method === "GET") return json(res, 200, experiences.filter((e) => e.available).map(experienceView));
    if ((m = p.match(/^\/api\/experiences\/([^/]+)$/)) && method === "GET") {
      const slug = decodeURIComponent(m[1]);
      const e = experiences.find((x) => x.slug === slug);
      return e ? json(res, 200, experienceView(e)) : problem(res, 404, "unknown_experience", `There's no Duniya at /duniya/${slug}.`);
    }
    if (p === "/api/environments" && method === "GET") return json(res, 200, environments.map((e) => ({ ...e, palette: e.palette ?? [], sort_order: e.sort_order ?? 100 })));
    // ---- the Buti library and its artwork ----
    if (p === "/api/motifs" && method === "GET") return json(res, 200, motifLibrary.motifs.map((mm) => motifView(mm, req.headers.host ?? `localhost:${PORT}`)));
    if ((m = p.match(/^\/api\/motifs\/([^/]+)\.svg$/)) && method === "GET") {
      const art = motifArt.get(decodeURIComponent(m[1]));
      if (!art) return problem(res, 404, "unknown_motif", "That motif isn't in the library.");
      res.writeHead(200, { "Content-Type": "image/svg+xml", "Content-Length": art.length, "Cache-Control": "public, max-age=3600", ...CORS });
      return res.end(art);
    }
    // ---- uploads: multipart file + kind, kept in memory and served back under /media/uploads/ ----
    if (p === "/api/uploads" && method === "POST") {
      const who = identity(req);
      if (!who) return problem(res, 400, "bad_request", "Send X-Aakar-Guest or sign in.");
      const ct = String(req.headers["content-type"] ?? "");
      const boundary = /boundary=(?:"([^"]+)"|([^;]+))/i.exec(ct);
      if (!ct.startsWith("multipart/form-data") || !boundary) return problem(res, 400, "bad_request", "Send multipart/form-data with file and kind.");
      const parts = parseMultipart(await readRawBody(req), (boundary[1] ?? boundary[2]).trim());
      const file = parts.find((x) => x.name === "file");
      const kind = parts.find((x) => x.name === "kind")?.data.toString("utf8").trim();
      if (!file || !["image", "model"].includes(kind)) return problem(res, 400, "bad_request", "Send file and kind (image | model).");
      const ext = String(file.filename ?? "").toLowerCase().split(".").pop() ?? "";
      const format = ext === "jpeg" ? "jpg" : ext;
      const allowed = kind === "image" ? UPLOAD_FORMATS.image : UPLOAD_FORMATS.model;
      if (!allowed.includes(format)) return problem(res, 422, "unsupported_format", `${kind === "image" ? "Photos" : "Model files"} can be ${allowed.join(", ")}.`);
      const max = kind === "image" ? 15 * 1024 * 1024 : 50 * 1024 * 1024;
      if (file.data.length > max) return problem(res, 413, "payload_too_large", `Files can be up to ${Math.round(max / 1024 / 1024)} MB.`);
      const id = randomUUID();
      const pending = /review|reject/i.test(file.filename ?? "");
      const mediaUrl = `http://${req.headers.host ?? `localhost:${PORT}`}/media/uploads/${id}.${format}`;
      const meta = {
        id, kind, format, bytes: file.data.length, sha256: createHash("sha256").update(file.data).digest("hex"),
        status: pending ? "pending_review" : "ready", url: pending ? null : mediaUrl, created_at: now(),
      };
      uploads.set(id, { meta, data: file.data, contentType: UPLOAD_MIME[format] ?? "application/octet-stream", filename: file.filename ?? `${id}.${format}`, owner: who.key });
      // The stand-in reviewer: "review" files are cleared, "reject" files turned down with a reviewer's message.
      if (pending) {
        setTimeout(() => {
          const up = uploads.get(id);
          if (!up || up.meta.status !== "pending_review") return;
          if (/reject/i.test(up.filename)) Object.assign(up.meta, { status: "rejected", url: null, message: "We can't print copyrighted heroes, but your own hero is welcome." });
          else Object.assign(up.meta, { status: "ready", url: mediaUrl });
        }, REVIEW_MS);
      }
      return json(res, 201, meta);
    }
    if ((m = p.match(/^\/api\/uploads\/([^/]+)$/)) && method === "GET") {
      // Only the identity that uploaded the file may read it; anyone else gets the same 404 as an unknown id.
      const who = identity(req);
      const up = uploads.get(m[1]);
      return up && who && up.owner === who.key ? json(res, 200, up.meta) : problem(res, 404, "not_found", "No such upload.");
    }
    if ((m = p.match(/^\/media\/uploads\/([^/.]+)\.[a-z0-9]+$/)) && method === "GET") {
      const up = uploads.get(m[1]);
      if (!up || up.meta.status !== "ready") return problem(res, 404, "not_found", "No such file.");
      res.writeHead(200, { "Content-Type": up.contentType, "Content-Length": up.data.length, "Cache-Control": "private, max-age=3600", ...CORS });
      return res.end(up.data);
    }
    if (p === "/api/templates") return json(res, 200, templates);
    if ((m = p.match(/^\/api\/templates\/([^/]+)$/))) {
      const t = templates.find((x) => x.id === decodeURIComponent(m[1]));
      return t ? json(res, 200, t) : problem(res, 404, "not_found", "No such template.");
    }
    if (p === "/api/designs" && method === "POST") {
      const who = identity(req);
      const body = JSON.parse(await readBody(req));
      // Prompt-only Create still waits for the co-designer; an Avatar (family_id) or template is a real start.
      if (!body.catalog_item_slug && !body.family_id && !body.template_id) {
        if (body.prompt || body.source === "create") return problem(res, 422, "not_yet_available", "Describing a piece in words arrives in Phase 2.");
        return problem(res, 400, "bad_request", "Send catalog_item_slug, family_id or template_id.");
      }
      let template, params, material, title, slug = null, family = null;
      if (body.catalog_item_slug) {
        const item = items.find((i) => i.slug === body.catalog_item_slug);
        if (!item) return problem(res, 404, "not_found", "No piece with that name.");
        if (item.available === false) return problem(res, 422, "template_not_available", "This piece is still being finished.");
        template = templates.find((t) => t.id === item.template_id);
        params = { ...defaults(template), ...item.default_params, ...(body.params ?? {}) };
        material = body.material ?? item.default_material; title = item.name; slug = item.slug;
        family = familyById(item.family_id ?? template.family) ?? null;
      } else if (body.family_id) {
        family = familyById(body.family_id);
        if (!family) return problem(res, 404, "unknown_family", "We don't make that kind of piece.");
        if (!family.available) return problem(res, 422, "family_not_available", `${familyLabel(family)} isn't open yet.`);
        const templateId = body.template_id ?? family.default_template_id;
        template = templates.find((t) => t.id === templateId && t.family === family.id);
        if (!template) return problem(res, 422, "template_not_available", `${familyLabel(family)} is still being finished in the studio.`);
        params = { ...defaults(template), ...(body.params ?? {}) };
        material = body.material ?? template.materials[0]; title = body.title ?? familyLabel(family);
      } else {
        template = templates.find((t) => t.id === body.template_id);
        if (!template) return problem(res, 404, "not_found", "No such template.");
        params = { ...defaults(template), ...(body.params ?? {}) };
        material = body.material ?? template.materials[0]; title = body.title ?? template.name;
        family = familyById(template.family) ?? null;
      }
      if (family && !materialAllowed(family, material)) return problem(res, 422, "validation_failed", `${materialOf(material)?.name ?? material} isn't offered for ${familyLabel(family)}.`);
      // Any path may name the Duniya the customer came from; the design keeps it (422 unknown_experience otherwise).
      const experienceId = typeof body.experience_id === "string" && body.experience_id.trim() ? body.experience_id.trim() : null;
      if (body.experience_id !== undefined && body.experience_id !== null && typeof body.experience_id !== "string") return problem(res, 400, "bad_request", "experience_id must be a string.");
      if (experienceId && (experienceId.length > 40 || !experienceById(experienceId))) return problem(res, 422, "unknown_experience", `experience_id '${experienceId}' is not an experience (see GET /api/experiences).`);
      // The design takes its Duniya's style when the template offers it (style_variants), else none.
      const experienceStyle = experienceId ? experienceById(experienceId)?.style : undefined;
      const style = experienceStyle && (template.style_variants ?? []).includes(experienceStyle) ? experienceStyle : "none";
      const features = resolveFeatures(body.features ?? [], template, who, style);
      const design = { id: randomUUID(), source: body.source, catalog_item_slug: slug, family_id: family?.id ?? null, experience_id: experienceId, title, status: "generating", created_at: now(), versions_count: 0 };
      Object.defineProperty(design, "owner", { value: who?.key ?? null, writable: true, enumerable: false });
      designs.set(design.id, design);
      const spec = { spec_version: "1.0", family: template.family, template: `${template.id}@${template.version}`, params, features, style, material, constraints: template.constraints };
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
      const template = templates.find((t) => t.id === v.spec.template.split("@")[0]) ?? templates[0];
      // The new version keeps its parent's style (and so its comic defaults for new content).
      const features = body.features === undefined || body.features === null ? (v.spec.features ?? []) : resolveFeatures(body.features, template, identity(req), v.spec.style);
      const spec = { ...v.spec, params: { ...v.spec.params, ...body.params }, material: body.material ?? v.spec.material, features };
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
      return json(res, 200, price(v.print_estimate, mat, priceCtx(v)));
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
    return problem(res, err.status ?? 500, err.code ?? "internal", err.message, err.extra ?? {});
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

// ---- Features (the Chhaap): schemas/design-spec.v1.json $defs, validated against the template descriptor ----
const FEATURE_TYPES = ["emboss_text", "motif", "relief_image", "hero_mesh"];
const UPLOAD_FORMATS = { image: ["png", "jpg", "webp", "heic"], model: ["stl", "glb", "3mf", "obj", "ply", "off", "gltf"] };
const UPLOAD_MIME = { png: "image/png", jpg: "image/jpeg", webp: "image/webp", heic: "image/heic", stl: "model/stl", glb: "model/gltf-binary", gltf: "model/gltf+json", "3mf": "model/3mf", obj: "model/obj", ply: "application/octet-stream", off: "application/octet-stream" };
const SCRIPTS = [["devanagari", /[\u0900-\u097F]/], ["bengali", /[\u0980-\u09FF]/], ["gujarati", /[\u0A80-\u0AFF]/], ["tamil", /[\u0B80-\u0BFF]/], ["telugu", /[\u0C00-\u0C7F]/], ["kannada", /[\u0C80-\u0CFF]/]];
const detectScript = (text) => SCRIPTS.find(([, re]) => re.test(text))?.[0] ?? "latin";
/** Customer copy for a feature type: plain words first, the codename after, never the code id. */
const FEATURE_WORDS = { emboss_text: "text (Naam)", motif: "a motif (Buti)", relief_image: "a photo relief (Chhavi)", hero_mesh: "your own 3D form (Roop)" };
const capitalise = (text) => text.charAt(0).toUpperCase() + text.slice(1);
/** Types that hold a spot alone (a photo relief, a customer's form); a name and a motif may share one. */
const SOLO_TYPES = ["relief_image", "hero_mesh"];
const MARK_LABELS = { emboss_text: "text (Naam)", motif: "motif (Buti)" };
/**
 * What one spot may hold, as the geometry service and the API rule it: at most one photo or form, at most one text and at
 * most one motif; a text and a motif sit side by side, but a photo never shares its spot with either (refused as
 * crowded, whichever came first). Codes and wording follow the API's FeatureValidator.
 */
function checkSpot(held, type, anchor) {
  const spot = anchor.label.toLowerCase();
  if (SOLO_TYPES.includes(type) && held.some((t) => SOLO_TYPES.includes(t))) throw fail(422, "validation_failed", `Only one photo or 3D form can go on the ${spot}.`);
  if (!SOLO_TYPES.includes(type) && held.includes(type)) throw fail(422, "validation_failed", `Only one ${MARK_LABELS[type]} can go on the ${spot}; put the other one on another spot.`);
  const crowding = type === "relief_image" ? held.find((t) => t in MARK_LABELS) : type in MARK_LABELS && held.includes("relief_image") ? type : undefined;
  if (crowding) {
    const other = MARK_LABELS[crowding];
    throw fail(422, "validation_failed", `The ${spot} is too crowded for a photo relief (Chhavi) and ${crowding === "motif" ? "a " : ""}${other} together; put the ${other} on another spot.`);
  }
}
/**
 * Type ∈ features_supported, anchor exists and accepts it (a hero form only on a volume anchor, everything else on a
 * surface), what one spot may hold (`checkSpot`), a motif from the library at a printable scale and depth, upload owned
 * by the caller and ready → the url filled in. Messages are customer copy.
 */
// ---- Content rules the API shares with the geometry service: anchor modes, required anchors, letters, comic_pop -----
const MODE_ORDER = ["emboss", "deboss", "lithophane"];
const MODE_DEFAULT = { emboss_text: "emboss", motif: "deboss", relief_image: "emboss" };
const MODE_STATE = { emboss: "raised", deboss: "cut in", lithophane: "the glowing plate itself" };
const MODE_CHOICE = { emboss: "raised", deboss: "cut-in", lithophane: "the night-light photo" };
const PLURAL_ORDER = ["emboss_text", "motif", "relief_image", "hero_mesh"];
const PLURALS = { emboss_text: "names", motif: "motifs", relief_image: "photos", hero_mesh: "3D forms" };
const NEEDS_ORDER = ["relief_image", "emboss_text", "motif", "hero_mesh"];
const NEEDS_ONE = { relief_image: "the photo", emboss_text: "the name", motif: "a motif", hero_mesh: "your model file" };
const NEEDS_ANY = { relief_image: "a photo", emboss_text: "a name", motif: "a motif", hero_mesh: "your model file" };
const COMIC_RAISED_DEPTH_MM = 1.5;
/** "a", "a and b", "a, b and c". */
const joined = (words, conjunction) => (words.length <= 1 ? words.join("") : `${words.slice(0, -1).join(", ")} ${conjunction} ${words[words.length - 1]}`);
const labelOf = (anchor) => anchor.label || anchor.id;
const acceptsOf = (template, anchor) => anchor.accepts ?? template.features_supported ?? [];
const allowsMode = (anchor, mode) => !Array.isArray(anchor.modes) || anchor.modes.length === 0 || anchor.modes.includes(mode);
/** Letters as a reader counts them (like the API and the geometry service): marks and joiners do not add. */
const textLength = (text) => [...String(text)].filter((ch) => !/[\p{M}\p{Cf}]/u.test(ch)).length;
/** "On the Top rail names and motifs are cut in, not raised; choose cut-in", as the API words it. */
function modeDetail(template, anchor, type, mode) {
  const place = `On the ${labelOf(anchor)} `;
  let allowed = MODE_ORDER.filter((m) => anchor.modes.includes(m));
  if (allowed.length === 0) allowed = anchor.modes;
  if (type === "relief_image" && allowed.length === 1 && allowed[0] === "lithophane") return `${place}your photo becomes the glowing plate itself; choose the night-light photo`;
  const nouns = PLURAL_ORDER.filter((t) => acceptsOf(template, anchor).includes(t)).map((t) => PLURALS[t]);
  const holds = nouns.length ? joined(nouns, "and") : (PLURALS[type] ?? "pieces");
  const swapped = allowed.length === 1 && ["emboss", "deboss"].includes(mode) && ["emboss", "deboss"].includes(allowed[0]);
  const states = allowed.map((m) => MODE_STATE[m] ?? m).join(" or ");
  return `${place}${holds} are ${states}${swapped ? `, not ${MODE_STATE[mode]}` : ""}; choose ${allowed.map((m) => MODE_CHOICE[m] ?? m).join(" or ")}`;
}
/** An anchor that lists `modes` takes content only in those; a feature's mode is its type's default when left out. */
function checkMode(template, anchor, index, feature) {
  const mode = feature.mode;
  if (typeof mode !== "string" || allowsMode(anchor, mode)) return;
  throw fail(422, "unsupported_feature", modeDetail(template, anchor, feature.type, mode), {
    template_id: template.id, feature: index, field: `features[${index}].mode`, anchor: anchor.id, modes: anchor.modes,
  });
}
/** Every anchor marked `required` needs content (checked after everything else, as the API does). */
function checkRequired(template, features) {
  const filled = new Set(features.map((f) => f.anchor));
  for (const anchor of template.anchors ?? []) {
    if (anchor.required !== true || filled.has(anchor.id)) continue;
    const accepts = acceptsOf(template, anchor);
    const place = ` for the ${labelOf(anchor)}`;
    const any = NEEDS_ORDER.filter((t) => accepts.includes(t)).map((t) => NEEDS_ANY[t]);
    const detail = accepts.length === 1 ? `Add ${NEEDS_ONE[accepts[0]] ?? "something"}${place}` : `Add ${joined(any, "or") || "something"}${place}`;
    throw fail(422, "validation_failed", detail, { template_id: template.id, anchor: anchor.id, accepts });
  }
}
/** comic_pop: a text or motif without a mode is raised where its anchor allows it; a raised one without a depth stands 1.5 mm proud. */
function comicDefaults(style, anchor, f, out) {
  if (style !== "comic_pop" || (f.type !== "emboss_text" && f.type !== "motif")) return;
  if (f.mode == null && allowsMode(anchor, "emboss")) out.mode = "emboss";
  if (f.depth_mm == null && out.mode === "emboss") out.depth_mm = Math.min(COMIC_RAISED_DEPTH_MM, anchor.max_relief_mm ?? COMIC_RAISED_DEPTH_MM);
}

function resolveFeatures(list, template, who, style = "none") {
  if (!Array.isArray(list)) throw fail(400, "bad_request", "Send features as a list.");
  if (list.length > 8) throw fail(422, "validation_failed", "A piece can carry at most 8 things.");
  if (template.family === "raw_print") checkRawForm(list);
  const held = new Map(); // anchor id -> feature types on it so far
  const maxChars = familyById(template.family)?.content_slot?.max_text_chars;
  const features = list.map((f, index) => {
    if (!f || typeof f !== "object" || !FEATURE_TYPES.includes(f.type)) throw fail(422, "validation_failed", "That isn't something a piece can carry.");
    const words = FEATURE_WORDS[f.type];
    if (!(template.features_supported ?? []).includes(f.type)) throw fail(422, "unsupported_feature", `${template.name} can't carry ${words} yet.`);
    const anchor = template.anchors.find((a) => a.id === f.anchor);
    if (!anchor) throw fail(422, "validation_failed", `That spot isn't on ${template.name}.`);
    const spot = `The ${anchor.label.toLowerCase()}`;
    const volume = (anchor.kind ?? "surface") === "volume";
    if (!(anchor.accepts ?? template.features_supported ?? []).includes(f.type) || volume !== (f.type === "hero_mesh")) {
      throw fail(422, "unsupported_feature", `${spot} can't carry ${words} yet.`);
    }
    const onSpot = held.get(anchor.id) ?? [];
    checkSpot(onSpot, f.type, anchor);
    held.set(anchor.id, [...onSpot, f.type]);
    const out = { ...f };
    comicDefaults(style, anchor, f, out);
    if (f.type in MODE_DEFAULT) checkMode(template, anchor, index, { ...out, mode: out.mode ?? MODE_DEFAULT[f.type] });
    if (f.type === "emboss_text") {
      if (typeof f.text !== "string" || !f.text.trim() || f.text.length > 40) throw fail(422, "validation_failed", "Your text (Naam) can be 1 to 40 characters.");
      if (maxChars && textLength(f.text) > maxChars) {
        throw fail(422, "param_out_of_range", `Text (Naam) on the ${labelOf(anchor)} can be at most ${maxChars} characters; ${textLength(f.text)} were given`, {
          template_id: template.id, feature: index, field: `features[${index}].text`,
        });
      }
      out.script ??= detectScript(f.text); out.depth_mm ??= Math.min(1.2, anchor.max_relief_mm ?? 1.2); out.projection ??= "planar"; out.mode ??= "emboss";
      if (anchor.max_relief_mm && out.depth_mm > anchor.max_relief_mm) throw fail(422, "param_out_of_range", `Letters on the ${anchor.label.toLowerCase()} can be at most ${anchor.max_relief_mm} mm deep.`);
    } else if (f.type === "motif") {
      if (typeof f.motif_id !== "string" || !f.motif_id) throw fail(422, "validation_failed", "Choose a motif (Buti).");
      const motif = motifById(f.motif_id);
      if (!motif) throw fail(422, "validation_failed", `We don't have a motif (Buti) called “${f.motif_id}”; choose one from the motif library.`);
      out.scale ??= 1; out.depth_mm ??= Math.min(1, anchor.max_relief_mm ?? 1); out.mode ??= "deboss";
      if (typeof out.scale !== "number" || out.scale < 0.2 - 1e-9 || out.scale > 1 + 1e-9) {
        throw fail(422, "param_out_of_range", `The scale of the motif (Buti) can be 0.2–1; ${typeof out.scale === "number" ? out.scale : "that"} was asked.`);
      }
      if (out.scale < motif.min_scale - 1e-9) {
        throw fail(422, "param_out_of_range", `The ${motif.label} motif (Buti) can't be printed smaller than scale ${motif.min_scale} (${percent(motif.min_scale)} of the spot); choose a larger scale.`);
      }
      if (typeof out.depth_mm !== "number" || out.depth_mm < 0.4 || out.depth_mm > 3) throw fail(422, "param_out_of_range", "A motif (Buti) can be 0.4 to 3 mm deep.");
      if (anchor.max_relief_mm && out.depth_mm > anchor.max_relief_mm + 1e-9) throw fail(422, "param_out_of_range", `A motif (Buti) on the ${anchor.label.toLowerCase()} can be at most ${anchor.max_relief_mm} mm deep.`);
      if (!["emboss", "deboss"].includes(out.mode)) throw fail(422, "validation_failed", "A motif (Buti) is raised (emboss) or cut in (deboss).");
    } else {
      const up = uploads.get(f.source?.upload_id);
      // The real API resolves uploads with Uploads.findOwned: someone else's file reads as unknown.
      if (!up || !who || up.owner !== who.key) throw fail(404, "not_found", "That file isn't known to the studio.");
      if (up.meta.status === "pending_review") throw fail(409, "upload_not_ready", "The studio is still checking this file.");
      if (up.meta.status === "rejected") throw fail(422, "upload_rejected", up.meta.message ?? "We can't print this one; try a different file.");
      const expect = f.type === "relief_image" ? "image" : "model";
      if (up.meta.kind !== expect) throw fail(422, "validation_failed", `${capitalise(words)} needs ${expect === "image" ? "a photo" : "a model file"}.`);
      out.source = { upload_id: up.meta.id, url: up.meta.url, format: up.meta.format, origin: "upload" };
      if (f.type === "relief_image") {
        out.mode ??= "emboss"; out.relief_mm ??= Math.min(0.6, anchor.max_relief_mm ?? 0.6); out.fit ??= "contain"; out.invert ??= false; out.cutout ??= "none";
        if (out.relief_mm < 0.2 || out.relief_mm > 3) throw fail(422, "param_out_of_range", "Relief must be between 0.2 and 3 mm.");
        if (anchor.max_relief_mm && out.relief_mm > anchor.max_relief_mm) throw fail(422, "param_out_of_range", `Relief on the ${anchor.label.toLowerCase()} can be at most ${anchor.max_relief_mm} mm.`);
      } else {
        out.fit ??= "contain"; out.yaw_deg ??= 0; out.orientation ??= "as_uploaded";
        if (out.longest_mm !== undefined && (out.longest_mm < 5 || out.longest_mm > 250)) throw fail(422, "param_out_of_range", "The longest side must be between 5 and 250 mm.");
      }
    }
    return out;
  });
  checkRequired(template, features);
  return features;
}
/** Swaroop, like the geometry template: exactly one form, with an explicit size inside the family envelope. */
function checkRawForm(list) {
  const hero = list.find((f) => f && f.type === "hero_mesh");
  if (!hero) throw fail(422, "invalid_spec", "Add your model file to print it as it is.");
  const env = familyById("raw_print")?.size_envelope_mm ?? { min_longest_mm: 20, max_longest_mm: 240 };
  const lo = env.min_longest_mm, hi = env.max_longest_mm;
  if (hero.fit !== "longest" || typeof hero.longest_mm !== "number") throw fail(422, "invalid_spec", `Choose how long your model should be on its longest side (${lo}–${hi} mm).`);
  if (hero.longest_mm < lo || hero.longest_mm > hi) throw fail(422, "param_out_of_range", `The longest side can be ${lo}–${hi} mm.`);
}
/** Minimal multipart/form-data parser: [{ name, filename, type, data: Buffer }]. */
function parseMultipart(buf, boundary) {
  const delim = Buffer.from(`--${boundary}`);
  const parts = [];
  let cursor = buf.indexOf(delim);
  while (cursor !== -1) {
    cursor += delim.length;
    if (buf.subarray(cursor, cursor + 2).toString() === "--") break;
    if (buf[cursor] === 13 && buf[cursor + 1] === 10) cursor += 2;
    const headerEnd = buf.indexOf("\r\n\r\n", cursor);
    if (headerEnd === -1) break;
    const headers = buf.subarray(cursor, headerEnd).toString("utf8");
    const next = buf.indexOf(delim, headerEnd + 4);
    if (next === -1) break;
    let end = next;
    if (buf[end - 2] === 13 && buf[end - 1] === 10) end -= 2;
    parts.push({
      name: /name="([^"]*)"/i.exec(headers)?.[1],
      filename: /filename="([^"]*)"/i.exec(headers)?.[1],
      type: /content-type:\s*([^\r\n]+)/i.exec(headers)?.[1],
      data: buf.subarray(headerEnd + 4, end),
    });
    cursor = next;
  }
  return parts;
}
function readRawBody(req) {
  return new Promise((resolve) => {
    const chunks = [];
    req.on("data", (c) => chunks.push(c));
    req.on("end", () => resolve(Buffer.concat(chunks)));
  });
}
function readBody(req) {
  return new Promise((resolve) => {
    let s = "";
    req.on("data", (c) => (s += c));
    req.on("end", () => resolve(s || "{}"));
  });
}

server.listen(PORT, () => console.log(`Aakar mock API on http://localhost:${PORT}${FAIL_EVERY_THIRD ? " (every third job fails)" : ""} · ${familiesSeed.families.filter((f) => f.available && familyReady(f)).length} Avatars live · ${experiences.filter((e) => e.available).length} Duniya open · ${motifLibrary.motifs.length} motifs · pay pages on ${WEB_URL} · dev OTP ${DEV_CODE} · order stage every ${ORDER_STAGE_MS} ms`));
