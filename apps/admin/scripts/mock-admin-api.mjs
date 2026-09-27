#!/usr/bin/env node
// A stand-in for the `admin` module of services/api so the management portal can be built and exercised
// before Spring Boot lands. Implements every path in packages/contracts/openapi/aakar-admin.v1.yaml with
// in-memory data: staff accounts, ~14 orders across every status with events, pricing policy versions,
// the six materials, catalog items, three templates, the shelves / hardware / outcome families (Avatars) from
// packages/design-tokens/families.json, the Duniya experiences and viewer backdrops from
// packages/design-tokens/experiences.json, the Buti motif library from packages/design-tokens/motifs/, a few customer
// uploads with content reviews, a messages log and an audit log.
//
// Usage: node scripts/mock-admin-api.mjs [port=8080]
//
// Mock-only (ADR-0013): credentials below are seeded for local use; the "sender" logs messages instead of
// sending them; print packs contain placeholder model bytes; the packaging card is a one-page PDF.
import http from "node:http";
import { randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../../..");
const tokensFile = JSON.parse(readFileSync(path.join(root, "packages/design-tokens/materials.json"), "utf8"));
const familiesFile = JSON.parse(readFileSync(path.join(root, "packages/design-tokens/families.json"), "utf8"));
const experiencesFile = JSON.parse(readFileSync(path.join(root, "packages/design-tokens/experiences.json"), "utf8"));
const motifsDir = path.join(root, "packages/design-tokens/motifs");
const motifsFile = JSON.parse(readFileSync(path.join(motifsDir, "index.json"), "utf8"));

const PORT = Number(process.argv[2] && !process.argv[2].startsWith("--") ? process.argv[2] : 8080);
const BASE = `http://localhost:${PORT}`;
const MAX_UPLOAD = 10 * 1024 * 1024;
const HOUR = 3600_000;
const DAY = 24 * HOUR;
const now = () => new Date();
const iso = (d) => new Date(d).toISOString();
const dateOnly = (d) => new Date(d).toISOString().slice(0, 10);
const ago = (ms) => new Date(Date.now() - ms);

// ---------------------------------------------------------------------------------------------------------
// Staff (seeded, mock-only)
// ---------------------------------------------------------------------------------------------------------
const staff = [
  { id: randomUUID(), email: "studio@aakar.local", password: "aakar-studio", name: "Aakar Studio", role: "owner" },
  { id: randomUUID(), email: "karigar@aakar.local", password: "aakar-karigar", name: "Karigar Desk", role: "studio" },
];
const sessions = new Map(); // token -> staff

// ---------------------------------------------------------------------------------------------------------
// Pricing policies (seed from design-tokens, ADR-0008) and the pricing engine
// ---------------------------------------------------------------------------------------------------------
const seedPolicy = { ...tokensFile.pricing_policy };
delete seedPolicy.$comment;
const seedVersion = seedPolicy.version;
delete seedPolicy.version;

const policies = [
  {
    version: seedVersion,
    active: true,
    policy: seedPolicy,
    note: "Seed from packages/design-tokens/materials.json; reproduces the Checkout board (₹1,249, free shipping from ₹999).",
    created_at: iso(ago(12 * DAY)),
    created_by: "system",
  },
  {
    version: "2026-09-draft0",
    active: false,
    policy: { ...seedPolicy, machine_rate_paise_per_hour: 18000, finishing_fee_paise: { matte: 6000, silk: 10000 }, shipping_flat_paise: 9900, free_shipping_above_paise: 149900, shipping_label: "Shipping · Delhivery, 5 days" },
    note: "First guess before the board figures were agreed; superseded the same day.",
    created_at: iso(ago(13 * DAY)),
    created_by: "studio@aakar.local",
  },
];
const activePolicy = () => policies.find((p) => p.active);

// ---------------------------------------------------------------------------------------------------------
// Materials
// ---------------------------------------------------------------------------------------------------------
const materials = tokensFile.materials.map((m, i) => ({ ...m, available: true, sort_order: (i + 1) * 10, updated_at: iso(ago(12 * DAY)) }));
const materialById = (id) => materials.find((m) => m.id === id);

function roundToEnding(paise, ending) {
  let rupees = Math.ceil(paise / 100);
  const e = Math.min(9, Math.max(0, Math.round(ending)));
  while (rupees % 10 !== e) rupees += 1;
  return rupees * 100;
}

/**
 * The pricing formula (PLAN §7.10) over a policy, a material and a print estimate. With a family in the context the
 * lines gain hardware (default BOM × unit cost × markup) and setup, and the family's minimum lifts the subtotal.
 */
function priceWith(policy, policyVersion, material, extrudedVolumeCm3, printSeconds, context = {}) {
  const mass = +(extrudedVolumeCm3 * material.density_g_cm3).toFixed(1);
  const materialPaise = Math.round(mass * material.rate_per_g_paise);
  const machine = Math.round((printSeconds / 3600) * policy.machine_rate_paise_per_hour);
  const finishing = policy.finishing_fee_paise?.[material.finish_class] ?? 0;
  const h = Math.floor(printSeconds / 3600);
  const min = Math.round((printSeconds % 3600) / 60);
  const lines = [
    { code: "material", label: `Material · ${Math.round(mass)} g ${material.name}`, detail: `${material.filament} at ₹${(material.rate_per_g_paise / 100).toFixed(2)}/g`, amount_paise: materialPaise },
    { code: "machine_time", label: `Print time · ${h} h ${String(min).padStart(2, "0")} m`, detail: `₹${policy.machine_rate_paise_per_hour / 100}/hour machine rate`, amount_paise: machine },
    { code: "finishing", label: material.finish_class === "silk" ? "Hand sanding & sealing" : "Hand finishing", detail: `${material.finish_class} finish class`, amount_paise: finishing },
  ];
  if (policy.packaging_fee_paise > 0) lines.push({ code: "packaging", label: "Kraft box & card", amount_paise: policy.packaging_fee_paise });
  const family = context.family;
  const rule = family ? policy.family_rules?.[family.id] : undefined;
  if (family) {
    const markup = 1 + (policy.hardware_markup_pct ?? 0) / 100;
    const parts = (family.hardware ?? []).map((ref) => ({ ref, item: hardwareBySku(ref.sku) })).filter((x) => x.item);
    const hardwarePaise = parts.reduce((s, x) => s + Math.round(x.ref.qty * x.item.unit_cost_paise * markup), 0);
    if (hardwarePaise > 0) {
      lines.push({
        code: "hardware",
        label: `Hardware · ${parts.map((x) => `${x.item.name}${x.ref.qty > 1 ? ` × ${x.ref.qty}` : ""}`).join(", ")}`,
        detail: `Bought-in parts at cost${policy.hardware_markup_pct ? ` + ${policy.hardware_markup_pct}% markup` : ""}`,
        amount_paise: hardwarePaise,
      });
    }
    if (rule?.setup_fee_paise > 0) lines.push({ code: "setup", label: family.kind === "raw" ? "Studio setup · repair & orientation" : "Studio setup", detail: `${family.codename} · ${family.name}`, amount_paise: rule.setup_fee_paise });
  }
  const raw = lines.reduce((s, l) => s + l.amount_paise, 0);
  const withMargin = Math.round(raw * (1 + (policy.margin_pct ?? 0) / 100));
  const computed = roundToEnding(withMargin, policy.round_to_rupees_ending_in);
  const minimum = rule?.minimum_subtotal_paise > 0 ? roundToEnding(rule.minimum_subtotal_paise, policy.round_to_rupees_ending_in) : 0;
  const subtotal = Math.max(computed, minimum);
  const shipping = subtotal >= policy.free_shipping_above_paise ? 0 : policy.shipping_flat_paise;
  return {
    currency: "INR",
    material_id: material.id,
    mass_g: mass,
    print_seconds: printSeconds,
    lines,
    subtotal_paise: subtotal,
    shipping_paise: shipping,
    shipping_label: policy.shipping_label,
    total_paise: subtotal + shipping,
    policy_version: policyVersion,
    ...(family ? { family_id: family.id } : {}),
    ...(minimum > computed ? { minimum_subtotal_paise: minimum } : {}),
  };
}

// ---------------------------------------------------------------------------------------------------------
// Templates and catalog
// ---------------------------------------------------------------------------------------------------------
// Rows carry the descriptor's family, features_supported (Chhaap types) and hardware so the portal can show them.
// Derived from the live descriptors exported by the geometry service (`make descriptors`).
const templates = JSON.parse(readFileSync(path.join(root, "packages/contracts/examples/template-descriptors.json"), "utf8")).map((d) => ({
  id: d.id, version: d.version, family: d.family, name: d.name, live: true,
  features_supported: d.features_supported ?? [], hardware: d.hardware ?? [],
}));

// ---------------------------------------------------------------------------------------------------------
// Shelves, hardware and outcome families (Avatars) seeded from packages/design-tokens/families.json.
// POST/PUT mutate these arrays in memory; a restart reloads the seed.
// ---------------------------------------------------------------------------------------------------------
const FAMILY_KINDS = ["carrier", "object", "raw"];
const FAMILY_TIERS = ["launch", "next", "later"];
const SHAPE_TOLERANCES = ["any", "constrained", "strict"];
const FEATURE_TYPES = ["emboss_text", "motif", "relief_image", "hero_mesh"];
/** Viewer backdrops (Mahaul), read-only reference data: the valid `environment` values everywhere. */
const environments = experiencesFile.environments
  .map((e) => ({ id: e.id, label: e.label, surface: e.surface, preset_key: e.preset_key, palette: e.palette ?? [], sort_order: e.sort_order ?? 100 }))
  .sort((a, b) => a.sort_order - b.sort_order || a.id.localeCompare(b.id));
const ENVIRONMENTS = environments.map((e) => e.id);
const FINISH_CLASSES = ["matte", "silk"];
const ID_RE = /^[a-z][a-z0-9_]*$/;

const shelves = familiesFile.shelves.map((sh) => ({ id: sh.id, label: sh.label, sort_order: sh.sort_order ?? 100 })).sort((a, b) => a.sort_order - b.sort_order);
const shelfById = (id) => shelves.find((sh) => sh.id === id);
const hardwareItems = familiesFile.hardware_items.map((h) => ({ ...normaliseHardware(h), updated_at: iso(ago(12 * DAY)) }));
const hardwareBySku = (sku) => hardwareItems.find((h) => h.sku === sku);
const families = familiesFile.families.map((f) => ({ ...normaliseFamily(f), updated_at: iso(ago(12 * DAY)) }));
const familyById = (id) => families.find((f) => f.id === id);
/** `ready` and `template_ids` are derived from the template registry (live templates of the family), never stored. */
function familyView(f) {
  const own = templates.filter((t) => t.family === f.id);
  return { ...f, ready: own.some((t) => t.live), template_ids: own.map((t) => t.id) };
}

const catalog = [
  { slug: "jharokha-phone-stand", name: "Jharokha Phone Stand", category: "desk_tech", family_id: "phone_stand", template_id: "jharokha_phone_stand", default_params: { width_mm: 92, depth_mm: 78, height_mm: 120, tilt_deg: 70, lip_height_mm: 12, wall_mm: 3.2, arch_cusps: 5 }, default_material: "terracotta_silk", base_price_paise: 49900, specs_line: "Fits phones to 6.9″ · 92 × 78 × 120 mm · 64 g", environment: "desk_oak", description: "A cusped jharokha arch, printed in one piece, that holds any phone at a comfortable tilt.", available: true, media: [] },
  { slug: "saathi-photo-keychain", name: "Saathi · Photo Keychain", category: "keychains_charms", family_id: "keychain", template_id: "keychain_tag", default_params: { shape: "rounded", width_mm: 45, thickness_mm: 3, hole_d_mm: 5 }, default_material: "basic_white", base_price_paise: 24900, specs_line: "45 mm · steel split ring · 9 g", environment: "studio", description: "Your photo in relief on a palm-sized plate; the ring loop is part of the print.", available: true, media: [] },
  { slug: "ajrakh-coasters", name: "Ajrakh Coasters · set of 4", category: "kitchen", family_id: "coaster", template_id: "ajrakh_coasters", default_params: { diameter_mm: 100, count: 4 }, default_material: "indigo_matte", base_price_paise: 64900, specs_line: "100 mm · heat-safe to 90 °C · 28 g each", environment: "kitchen_marble", description: "Four coasters with an Ajrakh block-print relief.", available: false, media: [] },
  { slug: "fluted-planter", name: "Fluted Planter · drainage tray", category: "home_decor", family_id: "planter", template_id: "fluted_planter", default_params: { diameter_mm: 140, height_mm: 130, flutes: 24, tray: true }, default_material: "terracotta_matte", base_price_paise: 89900, specs_line: "140 mm pot · fits 4″ nursery plants · 210 g", environment: "balcony_daylight", description: "Vertical flutes, a drainage tray, sized for nursery pots.", available: false, media: [] },
  { slug: "kantha-nameplate", name: "Nameplate · Kantha border", category: "nameplates", family_id: "nameplate", template_id: "kantha_nameplate", default_params: { width_mm: 300, height_mm: 110 }, default_material: "polished_brass", base_price_paise: 119900, specs_line: "300 × 110 mm · raised letters · screws included", environment: "studio", description: "Raised letters in any of seven scripts inside a running Kantha stitch border.", available: false, media: [] },
  { slug: "elephant-bookends", name: "Elephant Bookends", category: "gifting", family_id: "bookend", template_id: "elephant_bookends", default_params: { height_mm: 180 }, default_material: "sandalwood_silk", base_price_paise: 134900, specs_line: "180 mm tall · weighted · holds 6 kg of books", environment: "teak_table_candlelight", description: "A pair of elephants, trunks raised, weighted at the base.", available: false, media: [] },
  { slug: "pillar-headphone-stand", name: "Headphone Stand · Pillar", category: "desk_tech", family_id: "headphone_stand", template_id: "headphone_stand_pillar", default_params: { height_mm: 270 }, default_material: "basic_white", base_price_paise: 57900, specs_line: "270 mm tall · weighted base · 180 g", environment: "desk_oak", description: "A fluted pillar with a soft saddle for over-ear headphones.", available: false, media: [] },
];

const templateView = (t) => ({ ...t, catalog_items: catalog.filter((c) => c.template_id === t.id).map((c) => c.slug) });

// ---------------------------------------------------------------------------------------------------------
// Duniya experiences (seeded from experiences.json; POST/PUT mutate in memory) and the Buti motif library
// ---------------------------------------------------------------------------------------------------------
const STYLES = ["none", "jaipur_heritage", "modern_zen", "cyber_desi", "warli_line", "comic_pop"];
const SLUG_RE = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const HEX_RE = /^#[0-9A-Fa-f]{6}$/;
const DATE_RE = /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/;
const MONTH_DAY_RE = /^--([0-9]{2})-([0-9]{2})$/;
const experiences = experiencesFile.experiences.map((e) => ({ ...normaliseExperienceSeed(e), updated_at: iso(ago(8 * DAY)) }));
const experienceById = (id) => experiences.find((e) => e.id === id);
const sortedExperiences = () => [...experiences].sort((a, b) => a.sort_order - b.sort_order || a.id.localeCompare(b.id));
function normaliseExperienceSeed(e) {
  return {
    id: e.id, codename: e.codename, slug: e.slug, title: e.title,
    ...(e.tagline !== undefined ? { tagline: e.tagline } : {}),
    ...(e.description !== undefined ? { description: e.description } : {}),
    environment: e.environment,
    surface: { accent: e.surface.accent, paper_tint: e.surface.paper_tint ?? null, hero_media: e.surface.hero_media ?? null },
    style: e.style ?? "none", motif_pack: [...(e.motif_pack ?? [])], avatars: [...e.avatars], items: [...(e.items ?? [])],
    collections: (e.collections ?? []).map((c) => ({ id: c.id, title: c.title, licence_ref: c.licence_ref ?? null })),
    season: (e.season ?? []).map((w) => ({ starts_on: w.starts_on, ends_on: w.ends_on, label: w.label })),
    available: e.available, sort_order: e.sort_order ?? 100,
  };
}
/** Motif rows as GET /admin/api/motifs answers; the artwork is served at /api/motifs/{id}.svg (no staff token needed). */
const motifs = motifsFile.motifs.map((m) => ({ id: m.id, label: m.label, tags: m.tags ?? [], min_scale: m.min_scale, svg_url: `${BASE}/api/motifs/${m.id}.svg` }));
const motifArt = new Map(motifsFile.motifs.map((m) => [m.id, readFileSync(path.join(motifsDir, m.file))]));

// ---------------------------------------------------------------------------------------------------------
// Orders
// ---------------------------------------------------------------------------------------------------------
const LINEAR = ["confirmed", "queued", "slicing", "printing", "finishing", "qc", "packed", "shipped", "delivered"];
const STAGE = { pending_payment: "payment", confirmed: "queued", queued: "queued", slicing: "slicing", printing: "printing", finishing: "sanding", qc: "sanding", packed: "sanding", shipped: "shipped", delivered: "delivered", cancelled: "cancelled", reprint: "printing" };
const ORDER_STATUSES = ["pending_payment", ...LINEAR, "cancelled", "on_hold", "reprint"];
const DEFAULT_MESSAGE = {
  pending_payment: "Awaiting payment",
  confirmed: "Order confirmed. Your piece joins the studio queue.",
  queued: "In the queue. The karigar picks it up next.",
  slicing: "Slicing: turning the design into printer paths.",
  printing: "Printing has started.",
  finishing: "Off the printer; hand sanding and sealing.",
  qc: "Quality check: dimensions, finish and fit.",
  packed: "Packed in a kraft box with your card.",
  shipped: "Handed to the carrier.",
  delivered: "Delivered. Enjoy your piece.",
  cancelled: "Order cancelled.",
  on_hold: "Paused by the studio.",
  reprint: "Reprinting after QC.",
};

function stageFor(order, status = order.status) {
  if (status === "on_hold") return STAGE[order.hold_from ?? "queued"];
  return STAGE[status];
}

/** Allowed transitions, exactly as the contract describes them. */
function nextActions(order) {
  const s = order.status;
  const out = [];
  if (s === "on_hold") out.push(order.hold_from ?? "queued");
  else if (s === "reprint") out.push("printing");
  else {
    const i = LINEAR.indexOf(s);
    if (i >= 0 && i < LINEAR.length - 1) out.push(LINEAR[i + 1]);
  }
  if (s === "qc") out.push("reprint");
  if (!["delivered", "cancelled", "on_hold", "pending_payment"].includes(s)) out.push("on_hold");
  const effective = s === "on_hold" ? order.hold_from : s;
  if (!["shipped", "delivered", "cancelled"].includes(effective) && s !== "cancelled") out.push("cancelled");
  return out;
}

const customers = [
  { id: randomUUID(), phone: "+91 98480 12345", name: "Ananya Rao", email: "ananya@example.in", city: "Hyderabad", state: "Telangana", pincode: "500034", line1: "12, Road No. 10, Banjara Hills" },
  { id: randomUUID(), phone: "+91 99000 22110", name: "Rohan Mehta", email: "rohan.m@example.in", city: "Bengaluru", state: "Karnataka", pincode: "560038", line1: "4th Cross, Indiranagar" },
  { id: randomUUID(), phone: "+91 98200 55667", name: "Priya Nair", email: null, city: "Mumbai", state: "Maharashtra", pincode: "400050", line1: "Pali Hill, Bandra West" },
  { id: randomUUID(), phone: "+91 98110 77889", name: null, email: null, city: "New Delhi", state: "Delhi", pincode: "110016", line1: "Hauz Khas Village" },
  { id: randomUUID(), phone: "+91 98410 33445", name: "Karthik Iyer", email: "karthik@example.in", city: "Chennai", state: "Tamil Nadu", pincode: "600004", line1: "Luz Church Road, Mylapore" },
  { id: randomUUID(), phone: "+91 98230 66778", name: "Sneha Kulkarni", email: "sneha.k@example.in", city: "Pune", state: "Maharashtra", pincode: "411004", line1: "Prabhat Road, Deccan Gymkhana" },
];

const notifications = [];
const audit = [];
let auditId = 0;
const uploads = new Map(); // id -> { bytes, contentType }

function logNotification({ order, template, channel, to, text, at, status = "logged", user_id = null }) {
  const rec = {
    id: randomUUID(),
    order_id: order?.id ?? null,
    user_id: order?.customer.id ?? user_id,
    channel,
    template,
    to,
    payload: order ? { order_number: order.number, status: order.status, title: order.title, total_paise: order.total_paise } : {},
    rendered_text: text,
    status,
    created_at: iso(at ?? now()),
  };
  notifications.unshift(rec);
  if (order) order.notifications.unshift(rec);
  return rec;
}

function notifyFor(order, status, at) {
  const first = order.customer.name?.split(" ")[0] ?? "there";
  const tmpl = { confirmed: "order_confirmed", printing: "printing_timelapse", shipped: "shipped", delivered: "delivered" }[status];
  if (!tmpl) return;
  const text = {
    order_confirmed: `Namaste ${first}! Order ${order.number} (${order.title}) is confirmed. Total ${rupees(order.total_paise)}. We'll message you as it moves through the studio.`,
    printing_timelapse: `${order.title} is printing right now. Layer 212 of 480. Watch it take shape: ${BASE.replace("8080", "3000")}/orders/${order.id}`,
    shipped: `Your Aakar piece is on its way with ${order.shipment?.carrier ?? "Delhivery"} (AWB ${order.shipment?.awb ?? "—"}). Expected ${order.eta ?? "in 4 days"}.`,
    delivered: `Delivered! Unbox, enjoy, and if you'd like another or a remix, scan the card in the box.`,
  }[tmpl];
  if (order.notify_whatsapp) logNotification({ order, template: tmpl, channel: "whatsapp", to: order.customer.phone, text, at });
  if (order.customer.email) logNotification({ order, template: tmpl, channel: "email", to: order.customer.email, text, at });
}

function recordAudit(who, action, target, before, after, at) {
  audit.unshift({ id: ++auditId, at: iso(at ?? now()), staff_email: who, action, target, before, after });
}

const rupees = (paise) => `₹${(paise / 100).toLocaleString("en-IN")}`;

let orderSeq = 100;
const orders = new Map();

/** Build an order that has already walked to `status`, with events, payment, shipment and notifications. */
function seedOrder({ status, placedMsAgo, customer, items: itemSpecs, holdFrom, notify = true, note = null }) {
  orderSeq += 1;
  const placedAt = ago(placedMsAgo);
  const policy = activePolicy();
  const items = itemSpecs.map(({ slug, material: matId, qty, volume, seconds, versionNo = 1 }) => {
    const cat = catalog.find((c) => c.slug === slug);
    const mat = materialById(matId);
    const unit = priceWith(policy.policy, policy.version, mat, volume, seconds);
    const versionId = randomUUID();
    return {
      id: randomUUID(),
      design_id: randomUUID(),
      version_id: versionId,
      version_no: versionNo,
      title: cat.name,
      specs_line: `${mat.name} · ${cat.specs_line.split(" · ").slice(-2).join(" · ")}`,
      material_id: mat.id,
      material_name: mat.name,
      qty,
      unit_price: unit,
      line_total_paise: unit.subtotal_paise * qty,
      assets: {
        glb: { key: `versions/${versionId}/model.glb`, url: `${BASE}/mock-assets/versions/${versionId}/model.glb`, bytes: 184_320, content_type: "model/gltf-binary" },
        "3mf": { key: `versions/${versionId}/model.3mf`, url: `${BASE}/mock-assets/versions/${versionId}/model.3mf`, bytes: 96_512, content_type: "application/vnd.ms-package.3dmanufacturing-3dmodel+xml" },
        stl: { key: `versions/${versionId}/model.stl`, url: `${BASE}/mock-assets/versions/${versionId}/model.stl`, bytes: 1_254_884, content_type: "model/stl" },
      },
      _estimate: { volume, seconds },
    };
  });
  const subtotal = items.reduce((s, i) => s + i.line_total_paise, 0);
  const shipping = subtotal >= policy.policy.free_shipping_above_paise ? 0 : policy.policy.shipping_flat_paise;
  const paid = status !== "pending_payment";
  const order = {
    id: randomUUID(),
    number: `AK-${String(orderSeq).padStart(6, "0")}`,
    status: "pending_payment",
    stage: "payment",
    title: items.length === 1 ? items[0].title : `${items[0].title} + ${items.length - 1} more`,
    total_paise: subtotal + shipping,
    items_count: items.reduce((s, i) => s + i.qty, 0),
    eta: null,
    placed_at: iso(placedAt),
    items,
    address: { id: randomUUID(), label: "Home", name: customer.name ?? "Customer", phone: customer.phone, line1: customer.line1, line2: "", city: customer.city, state: customer.state, pincode: customer.pincode, is_default: true },
    subtotal_paise: subtotal,
    shipping_paise: shipping,
    shipping_label: policy.policy.shipping_label,
    policy_version: policy.version,
    payment: {
      id: randomUUID(),
      order_id: null,
      gateway: "mock",
      gateway_ref: paid ? `mockpay_${Math.random().toString(36).slice(2, 10)}` : null,
      status: paid ? "succeeded" : "pending",
      method: paid ? ["upi", "card", "netbanking"][orderSeq % 3] : null,
      amount_paise: subtotal + shipping,
      currency: "INR",
      pay_url: paid ? null : `${BASE.replace("8080", "3000")}/checkout/pay/mock`,
      invoice_number: paid ? `INV-2026-${String(orderSeq).padStart(5, "0")}` : null,
      created_at: iso(placedAt),
      finished_at: paid ? iso(new Date(placedAt.getTime() + 3 * 60_000)) : null,
    },
    shipment: undefined,
    events: [],
    notify_whatsapp: notify,
    note,
    customer: { id: customer.id, phone: customer.phone, name: customer.name, email: customer.email },
    qc_photos: [],
    notifications: [],
    hold_from: undefined,
  };
  order.payment.order_id = order.id;
  orders.set(order.id, order);

  // Walk the history.
  let t = placedAt.getTime();
  pushEvent(order, "pending_payment", DEFAULT_MESSAGE.pending_payment, {}, t);
  if (status === "pending_payment") return order;
  const targetIdx = status === "on_hold" ? LINEAR.indexOf(holdFrom) : status === "reprint" ? LINEAR.indexOf("qc") : status === "cancelled" ? LINEAR.indexOf("queued") : LINEAR.indexOf(status);
  const step = Math.max(HOUR, Math.floor((placedMsAgo - HOUR) / (targetIdx + 2)));
  for (let i = 0; i <= targetIdx; i += 1) {
    t += i === 0 ? 3 * 60_000 : step;
    applyTransition(order, LINEAR[i], null, productionDetail(LINEAR[i]), new Date(t), "system");
  }
  if (status === "on_hold") applyTransition(order, "on_hold", "Waiting for a fresh spool of the chosen filament.", {}, new Date(t + step / 2), "karigar@aakar.local");
  if (status === "reprint") applyTransition(order, "reprint", "Small layer shift on the arch; reprinting.", { studio: "Aakar Studio · Hyderabad", printer_bay: "Bay 1" }, new Date(t + step / 2), "karigar@aakar.local");
  if (status === "cancelled") applyTransition(order, "cancelled", "Cancelled at the customer's request before slicing.", {}, new Date(t + step / 2), "studio@aakar.local");
  return order;
}

function productionDetail(status) {
  const base = { studio: "Aakar Studio · Hyderabad" };
  if (status === "slicing") return { ...base, layer_height_mm: 0.2, layers_total: 480 };
  if (status === "printing") return { ...base, printer_bay: "Bay 2", layer_height_mm: 0.2, layers_total: 480, layer: 212 };
  if (status === "finishing" || status === "qc") return { ...base, printer_bay: "Bay 2" };
  return {};
}

function pushEvent(order, status, message, detail, at) {
  order.events.push({ sequence: order.events.length + 1, status, stage: stageFor(order, status), message, detail: detail ?? {}, at: iso(at) });
}

/** Apply a transition that has already been validated. Side effects follow the contract's description. */
function applyTransition(order, status, message, detail, at, who) {
  const before = order.status;
  if (status === "on_hold") order.hold_from = before;
  if (before === "on_hold" && status !== "cancelled") order.hold_from = undefined;
  order.status = status;
  order.stage = stageFor(order, status);
  if (status === "packed") {
    const eta = new Date(at.getTime() + 4 * DAY);
    order.shipment = {
      id: randomUUID(),
      carrier: "Delhivery (mock)",
      awb: `MOCK${String(Math.floor(Math.random() * 1e10)).padStart(10, "0")}`,
      status: "created",
      eta: dateOnly(eta),
      tracking_url: `${BASE}/mock-tracking/${order.number}`,
      events: [{ status: "created", message: "Shipment created; pickup requested.", at: iso(at) }],
    };
    order.eta = dateOnly(eta);
  }
  if (status === "shipped" && order.shipment) {
    order.shipment.status = "picked_up";
    order.shipment.events.push({ status: "picked_up", message: "Picked up from Aakar Studio, Hyderabad.", at: iso(at) });
  }
  if (status === "delivered" && order.shipment) {
    order.shipment.status = "delivered";
    order.shipment.events.push({ status: "delivered", message: `Delivered in ${order.address.city}.`, at: iso(at) });
  }
  if (status === "confirmed") order.eta = dateOnly(new Date(order.payment.created_at).getTime() + 7 * DAY);
  pushEvent(order, status, message || DEFAULT_MESSAGE[status], detail, at);
  notifyFor(order, status, at);
  if (who !== "system") recordAudit(who, "order.advance", order.number, { status: before }, { status, message: message || null, detail: detail ?? {} }, at);
  return order;
}

function summaryView(o) {
  return {
    id: o.id,
    number: o.number,
    status: o.status,
    stage: o.stage,
    title: o.title,
    total_paise: o.total_paise,
    items_count: o.items_count,
    eta: o.eta,
    placed_at: o.placed_at,
    customer: { id: o.customer.id, phone: o.customer.phone, name: o.customer.name },
    materials: [...new Set(o.items.map((i) => i.material_id))],
    next_actions: nextActions(o),
  };
}

function omit(obj, ...keys) {
  return Object.fromEntries(Object.entries(obj).filter(([k]) => !keys.includes(k)));
}
function orderView(o) {
  return {
    ...omit(o, "hold_from"),
    items: o.items.map((item) => omit(item, "_estimate")),
    shipment: o.shipment ?? undefined,
    next_actions: nextActions(o),
    qc_photos: o.qc_photos,
    notifications: o.notifications,
  };
}

// Seed ~14 orders across every status.
const [c1, c2, c3, c4, c5, c6] = customers;
seedOrder({ status: "delivered", placedMsAgo: 10 * DAY, customer: c1, items: [{ slug: "jharokha-phone-stand", material: "terracotta_silk", qty: 1, volume: 67.7, seconds: 13200 }] });
seedOrder({ status: "delivered", placedMsAgo: 8 * DAY, customer: c2, items: [{ slug: "jharokha-phone-stand", material: "polished_brass", qty: 2, volume: 67.7, seconds: 13200 }], note: "Gift wrap please" });
seedOrder({ status: "cancelled", placedMsAgo: 7 * DAY, customer: c3, items: [{ slug: "jharokha-phone-stand", material: "basic_white", qty: 1, volume: 60.2, seconds: 11800 }], notify: false });
seedOrder({ status: "shipped", placedMsAgo: 5 * DAY, customer: c4, items: [{ slug: "jharokha-phone-stand", material: "indigo_matte", qty: 1, volume: 71.4, seconds: 14100 }] });
seedOrder({ status: "packed", placedMsAgo: 4 * DAY, customer: c5, items: [{ slug: "jharokha-phone-stand", material: "sandalwood_silk", qty: 1, volume: 67.7, seconds: 13200 }] });
seedOrder({ status: "qc", placedMsAgo: 3 * DAY + 4 * HOUR, customer: c6, items: [{ slug: "jharokha-phone-stand", material: "terracotta_matte", qty: 1, volume: 64.0, seconds: 12600 }] });
seedOrder({ status: "reprint", placedMsAgo: 3 * DAY, customer: c1, items: [{ slug: "jharokha-phone-stand", material: "terracotta_silk", qty: 1, volume: 81.9, seconds: 15900, versionNo: 3 }] });
seedOrder({ status: "finishing", placedMsAgo: 2 * DAY + 10 * HOUR, customer: c2, items: [{ slug: "jharokha-phone-stand", material: "polished_brass", qty: 1, volume: 67.7, seconds: 13200 }] });
seedOrder({ status: "on_hold", placedMsAgo: 2 * DAY + 6 * HOUR, customer: c3, holdFrom: "printing", items: [{ slug: "jharokha-phone-stand", material: "sandalwood_silk", qty: 1, volume: 67.7, seconds: 13200 }] });
seedOrder({ status: "printing", placedMsAgo: 2 * DAY, customer: c4, items: [{ slug: "jharokha-phone-stand", material: "terracotta_silk", qty: 1, volume: 67.7, seconds: 13200 }, { slug: "jharokha-phone-stand", material: "indigo_matte", qty: 1, volume: 55.3, seconds: 10900, versionNo: 2 }] });
seedOrder({ status: "slicing", placedMsAgo: 1 * DAY + 2 * HOUR, customer: c5, items: [{ slug: "jharokha-phone-stand", material: "basic_white", qty: 1, volume: 60.2, seconds: 11800 }] });
seedOrder({ status: "queued", placedMsAgo: 6 * HOUR, customer: c6, items: [{ slug: "jharokha-phone-stand", material: "terracotta_matte", qty: 3, volume: 67.7, seconds: 13200 }] });
seedOrder({ status: "confirmed", placedMsAgo: 2 * HOUR, customer: c1, items: [{ slug: "jharokha-phone-stand", material: "terracotta_silk", qty: 1, volume: 67.7, seconds: 13200 }] });
seedOrder({ status: "pending_payment", placedMsAgo: 25 * 60_000, customer: c2, items: [{ slug: "jharokha-phone-stand", material: "polished_brass", qty: 1, volume: 67.7, seconds: 13200 }] });

// Seeded audit trail for configuration and two OTP entries in the messages log.
recordAudit("system", "pricing.publish", "2026-09-draft0", null, policies[1].policy, ago(13 * DAY));
recordAudit("studio@aakar.local", "pricing.publish", seedVersion, policies[1].policy, seedPolicy, ago(12 * DAY));
recordAudit("studio@aakar.local", "material.update", "terracotta_silk", { rate_per_g_paise: 450 }, { rate_per_g_paise: 463 }, ago(11 * DAY));
recordAudit("studio@aakar.local", "catalog.update", "jharokha-phone-stand", { available: false }, { available: true }, ago(11 * DAY));
recordAudit("studio@aakar.local", "template.live", "jharokha_phone_stand", { live: false }, { live: true }, ago(11 * DAY));
logNotification({ template: "otp", channel: "sms", to: c1.phone, text: "Your Aakar sign-in code is 482913. Valid for 5 minutes.", at: ago(10 * DAY + HOUR), user_id: c1.id });
logNotification({ template: "otp", channel: "sms", to: c2.phone, text: "Your Aakar sign-in code is 105577. Valid for 5 minutes.", at: ago(30 * 60_000), user_id: c2.id });
recordAudit("studio@aakar.local", "family.update", "keychain", { available: false }, { available: true }, ago(6 * DAY));
recordAudit("studio@aakar.local", "hardware.update", "magnet_d10x3", { unit_cost_paise: 1200 }, { unit_cost_paise: 1500 }, ago(5 * DAY));
recordAudit("studio@aakar.local", "experience.update", "festive", { tagline: "Gifts for every festival" }, { tagline: "Gifts that glow for every festival" }, ago(4 * DAY));

// ---------------------------------------------------------------------------------------------------------
// Customer uploads and content reviews (the Reviews queue). Flagged uploads wait as pending_review until staff decide.
// ---------------------------------------------------------------------------------------------------------
const UPLOAD_STATUSES = ["ready", "pending_review", "rejected"];
const customerUploads = [];
const fakeSha = () => Array.from({ length: 64 }, () => "0123456789abcdef"[Math.floor(Math.random() * 16)]).join("");
/** A placeholder "photo": an inline SVG so the review card shows a real preview without any file on disk. */
function placeholderImage(label, fill) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="480" height="320" viewBox="0 0 480 320"><rect width="480" height="320" fill="${fill}"/><circle cx="240" cy="130" r="64" fill="#2a2f3a" opacity=".85"/><path d="M96 300c22-68 74-104 144-104s122 36 144 104z" fill="#2a2f3a" opacity=".85"/><text x="240" y="40" text-anchor="middle" font-family="Manrope, sans-serif" font-size="18" fill="#2a2f3a">${label}</text></svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}
function seedUpload({ kind, format, bytes, status, url = null, owner, origin = "upload", review = null, at }) {
  const u = {
    id: randomUUID(),
    kind,
    format,
    bytes,
    sha256: fakeSha(),
    status,
    url,
    created_at: iso(at),
    owner,
    origin,
    review: review ? { id: randomUUID(), reason: review.reason, status: review.status ?? "pending", decision_note: review.decision_note ?? null, reviewer_email: review.reviewer_email ?? null, decided_at: review.decided_at ? iso(review.decided_at) : null } : null,
  };
  customerUploads.push(u);
  return u;
}
const REJECTION_NOTE = "We can't print copyrighted heroes, but your own hero is welcome";
seedUpload({ kind: "image", format: "png", bytes: 812_344, status: "pending_review", url: placeholderImage("Uploaded photo (mock) · batman_cake.png", "#e9d8a6"), owner: { user_id: c1.id, guest_id: null, phone: c1.phone }, review: { reason: "trademark_terms: batman" }, at: ago(3 * HOUR) });
seedUpload({ kind: "model", format: "stl", bytes: 2_418_776, status: "pending_review", owner: { user_id: null, guest_id: randomUUID(), phone: null }, review: { reason: "filename_terms: marvel_ironman.stl" }, at: ago(40 * 60_000) });
seedUpload({ kind: "image", format: "jpg", bytes: 1_204_113, status: "ready", url: placeholderImage("Family portrait (mock) · approved", "#cfe3d4"), owner: { user_id: c2.id, guest_id: null, phone: c2.phone }, review: { reason: "trademark_terms: superman", status: "approved", decision_note: "The customer's own costume photo, not the trademark.", reviewer_email: "studio@aakar.local", decided_at: ago(DAY) }, at: ago(DAY + 2 * HOUR) });
seedUpload({ kind: "model", format: "obj", bytes: 5_104_220, status: "rejected", owner: { user_id: c3.id, guest_id: null, phone: c3.phone }, review: { reason: "trademark_terms: pikachu", status: "rejected", decision_note: REJECTION_NOTE, reviewer_email: "karigar@aakar.local", decided_at: ago(2 * DAY) }, at: ago(2 * DAY + 3 * HOUR) });
const cleanModel = seedUpload({ kind: "model", format: "3mf", bytes: 934_112, status: "ready", owner: { user_id: c5.id, guest_id: null, phone: c5.phone }, at: ago(5 * HOUR) });
cleanModel.url = `${BASE}/mock-assets/customer-uploads/${cleanModel.id}`;
recordAudit("karigar@aakar.local", "review.decide", customerUploads[3].review.id, { status: "pending_review", review_status: "pending" }, { status: "rejected", review_status: "rejected", note: REJECTION_NOTE }, ago(2 * DAY));
recordAudit("studio@aakar.local", "review.decide", customerUploads[2].review.id, { status: "pending_review", review_status: "pending" }, { status: "ready", review_status: "approved", note: "The customer's own costume photo, not the trademark." }, ago(DAY));

notifications.sort((a, b) => (a.created_at < b.created_at ? 1 : -1));
audit.sort((a, b) => (a.at < b.at ? 1 : -1));

// ---------------------------------------------------------------------------------------------------------
// Binary helpers: a store-only zip writer and a one-page PDF (no dependencies)
// ---------------------------------------------------------------------------------------------------------
const CRC_TABLE = new Int32Array(256).map((_, n) => {
  let c = n;
  for (let k = 0; k < 8; k += 1) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c;
});
function crc32(buf) {
  let c = -1;
  for (let i = 0; i < buf.length; i += 1) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}
function zip(files) {
  const d = now();
  const dosTime = (d.getHours() << 11) | (d.getMinutes() << 5) | Math.floor(d.getSeconds() / 2);
  const dosDate = ((d.getFullYear() - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate();
  const locals = [];
  const centrals = [];
  let offset = 0;
  for (const { name, data } of files) {
    const nameBuf = Buffer.from(name, "utf8");
    const crc = crc32(data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6);
    local.writeUInt16LE(0, 8);
    local.writeUInt16LE(dosTime, 10);
    local.writeUInt16LE(dosDate, 12);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(data.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBuf.length, 26);
    local.writeUInt16LE(0, 28);
    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(0x0800, 8);
    central.writeUInt16LE(0, 10);
    central.writeUInt16LE(dosTime, 12);
    central.writeUInt16LE(dosDate, 14);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(data.length, 20);
    central.writeUInt32LE(data.length, 24);
    central.writeUInt16LE(nameBuf.length, 28);
    central.writeUInt16LE(0, 30);
    central.writeUInt16LE(0, 32);
    central.writeUInt16LE(0, 34);
    central.writeUInt16LE(0, 36);
    central.writeUInt32LE(0, 38);
    central.writeUInt32LE(offset, 42);
    locals.push(local, nameBuf, data);
    centrals.push(central, nameBuf);
    offset += local.length + nameBuf.length + data.length;
  }
  const centralBuf = Buffer.concat(centrals);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(0, 4);
  end.writeUInt16LE(0, 6);
  end.writeUInt16LE(files.length, 8);
  end.writeUInt16LE(files.length, 10);
  end.writeUInt32LE(centralBuf.length, 12);
  end.writeUInt32LE(offset, 16);
  end.writeUInt16LE(0, 20);
  return Buffer.concat([...locals, centralBuf, end]);
}

function pdf(lines) {
  const esc = (s) => s.replace(/[^\x20-\x7e]/g, (ch) => (ch === "₹" ? "Rs " : ch === "·" ? "-" : "?")).replace(/[\\()]/g, (c) => `\\${c}`);
  const content = lines.map((l, i) => `BT /F1 ${i === 0 ? 20 : i === 1 ? 13 : 11} Tf 64 ${740 - i * 24} Td (${esc(l)}) Tj ET`).join("\n");
  const objects = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 420 595] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
    `<< /Length ${Buffer.byteLength(content, "latin1")} >>\nstream\n${content}\nendstream`,
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
  ];
  let out = "%PDF-1.4\n";
  const offsets = [];
  objects.forEach((o, i) => {
    offsets.push(Buffer.byteLength(out, "latin1"));
    out += `${i + 1} 0 obj\n${o}\nendobj\n`;
  });
  const xref = Buffer.byteLength(out, "latin1");
  out += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n${offsets.map((o) => `${String(o).padStart(10, "0")} 00000 n \n`).join("")}`;
  out += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(out, "latin1");
}

function parseMultipart(buf, contentType) {
  const m = /boundary=(?:"([^"]+)"|([^;]+))/i.exec(contentType ?? "");
  if (!m) return [];
  const boundary = Buffer.from(`--${(m[1] ?? m[2]).trim()}`);
  const parts = [];
  let pos = buf.indexOf(boundary);
  while (pos !== -1) {
    pos += boundary.length;
    if (buf.slice(pos, pos + 2).toString() === "--") break;
    pos += 2; // CRLF
    const headerEnd = buf.indexOf("\r\n\r\n", pos);
    if (headerEnd === -1) break;
    const headers = buf.slice(pos, headerEnd).toString();
    const bodyStart = headerEnd + 4;
    const next = buf.indexOf(boundary, bodyStart);
    if (next === -1) break;
    const body = buf.slice(bodyStart, next - 2);
    const name = /name="([^"]+)"/i.exec(headers)?.[1];
    const filename = /filename="([^"]*)"/i.exec(headers)?.[1];
    const type = /content-type:\s*([^\r\n]+)/i.exec(headers)?.[1]?.trim();
    parts.push({ name, filename, type, body });
    pos = next;
  }
  return parts;
}

// ---------------------------------------------------------------------------------------------------------
// HTTP plumbing
// ---------------------------------------------------------------------------------------------------------
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Expose-Headers": "Content-Disposition, Content-Type",
};
function problem(res, status, code, detail) {
  res.writeHead(status, { "Content-Type": "application/problem+json", ...CORS });
  res.end(JSON.stringify({ type: "about:blank", title: code.replace(/_/g, " "), status, detail, code }));
}
function json(res, status, body) {
  res.writeHead(status, { "Content-Type": "application/json", ...CORS });
  res.end(JSON.stringify(body));
}
function binary(res, contentType, buf, filename) {
  res.writeHead(200, { "Content-Type": contentType, "Content-Length": buf.length, "Content-Disposition": `attachment; filename="${filename}"`, ...CORS });
  res.end(buf);
}
function readBody(req, limit = MAX_UPLOAD + 64 * 1024) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (c) => {
      size += c.length;
      if (size > limit) {
        reject(Object.assign(new Error("Body larger than 10 MB."), { status: 413, code: "payload_too_large" }));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on("end", () => resolve(Buffer.concat(chunks)));
    req.on("error", reject);
  });
}
async function readJson(req) {
  const buf = await readBody(req);
  if (!buf.length) return {};
  try {
    return JSON.parse(buf.toString("utf8"));
  } catch {
    throw Object.assign(new Error("Body is not valid JSON."), { status: 400, code: "bad_request" });
  }
}
function paginate(list, url, defaultSize, maxSize) {
  const page = Math.max(0, Number(url.searchParams.get("page") ?? 0) || 0);
  const size = Math.min(maxSize, Math.max(1, Number(url.searchParams.get("size") ?? defaultSize) || defaultSize));
  return { items: list.slice(page * size, page * size + size), page, size, total: list.length };
}
function requireOwner(who) {
  if (who.role !== "owner") throw Object.assign(new Error("Only the owner account can change configuration."), { status: 403, code: "forbidden" });
}
function validate(cond, detail) {
  if (!cond) throw Object.assign(new Error(detail), { status: 422, code: "validation_failed" });
}

// ---------------------------------------------------------------------------------------------------------
// Routes
// ---------------------------------------------------------------------------------------------------------
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, BASE);
  const p = url.pathname;
  const method = req.method;
  if (method === "OPTIONS") {
    res.writeHead(204, CORS);
    return res.end();
  }
  let m;
  try {
    // Public mock asset endpoints (model files, QC photos, tracking page).
    if ((m = p.match(/^\/mock-assets\/versions\/([^/]+)\/model\.(glb|3mf|stl)$/))) {
      const type = { glb: "model/gltf-binary", "3mf": "application/vnd.ms-package.3dmanufacturing-3dmodel+xml", stl: "model/stl" }[m[2]];
      return binary(res, type, Buffer.from(`Aakar mock ${m[2].toUpperCase()} placeholder for version ${m[1]}.\nThe geometry service produces the real file.\n`), `model.${m[2]}`);
    }
    if ((m = p.match(/^\/mock-assets\/uploads\/([^/]+)$/))) {
      const up = uploads.get(m[1]);
      if (!up) return problem(res, 404, "not_found", "No such upload.");
      res.writeHead(200, { "Content-Type": up.contentType, "Content-Length": up.bytes.length, ...CORS });
      return res.end(up.bytes);
    }
    if ((m = p.match(/^\/mock-assets\/customer-uploads\/([^/]+)$/))) {
      const up = customerUploads.find((u) => u.id === m[1]);
      if (!up) return problem(res, 404, "not_found", "No such upload.");
      return binary(res, "application/octet-stream", Buffer.from(`Aakar mock ${up.format.toUpperCase()} placeholder for customer upload ${up.id}.\nThe real API serves the stored file from its object store.\n`), `upload.${up.format}`);
    }
    if ((m = p.match(/^\/api\/motifs\/([^/]+)\.svg$/)) && method === "GET") {
      const art = motifArt.get(decodeURIComponent(m[1]));
      if (!art) return problem(res, 404, "unknown_motif", "That motif isn't in the library.");
      res.writeHead(200, { "Content-Type": "image/svg+xml", "Content-Length": art.length, "Cache-Control": "public, max-age=3600", ...CORS });
      return res.end(art);
    }
    if ((m = p.match(/^\/mock-tracking\/(.+)$/))) {
      res.writeHead(200, { "Content-Type": "text/plain; charset=utf-8", ...CORS });
      return res.end(`Mock carrier tracking page for ${m[1]}. A real carrier would show scans here.\n`);
    }

    if (p === "/admin/api/auth/login" && method === "POST") {
      const body = await readJson(req);
      const who = staff.find((s) => s.email.toLowerCase() === String(body.email ?? "").toLowerCase() && s.password === body.password);
      if (!who) return problem(res, 401, "unauthenticated", "That email and password don't match a staff account.");
      const token = `mock-staff-${randomUUID()}`;
      sessions.set(token, who);
      return json(res, 200, { access_token: token, token_type: "Bearer", expires_in_s: 8 * 3600, staff: omit(who, "password") });
    }

    if (!p.startsWith("/admin/api/")) return problem(res, 404, "not_found", `No route for ${method} ${p}`);

    const auth = req.headers.authorization ?? "";
    const token = auth.startsWith("Bearer ") ? auth.slice(7) : null;
    const who = token ? sessions.get(token) : undefined;
    if (!who) return problem(res, 401, "unauthenticated", "Sign in with a staff account to use the management API.");

    if (p === "/admin/api/auth/me") return json(res, 200, omit(who, "password"));

    if (p === "/admin/api/dashboard") {
      const all = [...orders.values()];
      const todayIst = new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" });
      const monthIst = todayIst.slice(0, 7);
      const dayOf = (o) => new Date(o.placed_at).toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" });
      const paid = (o) => o.payment.status === "succeeded" && o.status !== "cancelled";
      const by = {};
      for (const o of all) by[o.status] = (by[o.status] ?? 0) + 1;
      return json(res, 200, {
        orders_by_status: by,
        orders_today: all.filter((o) => dayOf(o) === todayIst).length,
        revenue_today_paise: all.filter((o) => paid(o) && dayOf(o) === todayIst).reduce((s, o) => s + o.total_paise, 0),
        revenue_month_paise: all.filter((o) => paid(o) && dayOf(o).startsWith(monthIst)).reduce((s, o) => s + o.total_paise, 0),
        awaiting_action: all.filter((o) => ["queued", "finishing", "qc"].includes(o.status)).length,
      });
    }

    if (p === "/admin/api/orders" && method === "GET") {
      const statuses = (url.searchParams.get("status") ?? "").split(",").map((s) => s.trim()).filter(Boolean);
      const q = (url.searchParams.get("q") ?? "").trim().toLowerCase().replace(/\s+/g, "");
      let list = [...orders.values()].sort((a, b) => (a.placed_at < b.placed_at ? 1 : -1));
      if (statuses.length) list = list.filter((o) => statuses.includes(o.status));
      if (q) list = list.filter((o) => o.number.toLowerCase().includes(q) || o.customer.phone.replace(/\s+/g, "").includes(q) || (o.customer.name ?? "").toLowerCase().replace(/\s+/g, "").includes(q));
      const page = paginate(list, url, 25, 100);
      return json(res, 200, { ...page, items: page.items.map(summaryView) });
    }
    if ((m = p.match(/^\/admin\/api\/orders\/([^/]+)$/)) && method === "GET") {
      const o = orders.get(m[1]) ?? [...orders.values()].find((x) => x.number === m[1]);
      return o ? json(res, 200, orderView(o)) : problem(res, 404, "not_found", "No order with that id.");
    }
    if ((m = p.match(/^\/admin\/api\/orders\/([^/]+)\/advance$/)) && method === "POST") {
      const o = orders.get(m[1]);
      if (!o) return problem(res, 404, "not_found", "No order with that id.");
      const body = await readJson(req);
      validate(ORDER_STATUSES.includes(body.status), `status must be one of ${ORDER_STATUSES.join(", ")}.`);
      validate(body.message === undefined || (typeof body.message === "string" && body.message.length <= 200), "message must be at most 200 characters.");
      const allowed = nextActions(o);
      if (!allowed.includes(body.status)) {
        return problem(res, 409, "invalid_transition", `${o.number} is ${o.status.replace(/_/g, " ")}; it can move to ${allowed.map((a) => a.replace(/_/g, " ")).join(", ") || "nothing"}, not ${body.status.replace(/_/g, " ")}.`);
      }
      applyTransition(o, body.status, body.message?.trim() || null, body.detail && typeof body.detail === "object" ? body.detail : {}, now(), who.email);
      return json(res, 200, orderView(o));
    }
    if ((m = p.match(/^\/admin\/api\/orders\/([^/]+)\/print-pack$/)) && method === "GET") {
      const o = orders.get(m[1]);
      if (!o) return problem(res, 404, "not_found", "No order with that id.");
      const files = [];
      o.items.forEach((item, i) => {
        const mat = materialById(item.material_id);
        const dir = `${o.number}/item-${i + 1}-${item.title.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "")}`;
        const sheet = [
          `AAKAR PRINT SHEET · ${o.number} · item ${i + 1} of ${o.items.length}`,
          `Piece:            ${item.title} (design version ${item.version_no ?? 1})`,
          `Material:         ${mat?.name ?? item.material_id} · ${mat?.filament ?? "—"}`,
          `Finish class:     ${mat?.finish_class ?? "—"}`,
          `Quantity:         ${item.qty}`,
          `Specs:            ${item.specs_line ?? "—"}`,
          `Mass (each):      ${item.unit_price.mass_g} g`,
          `Estimated time:   ${Math.floor(item.unit_price.print_seconds / 3600)} h ${String(Math.round((item.unit_price.print_seconds % 3600) / 60)).padStart(2, "0")} m`,
          `Notes:            ${o.note ?? "—"}`,
          "",
          "Files: model.3mf (sliceable), model.stl (mesh). Mock server: placeholder bytes stand in for the geometry service's exports.",
          "Deliver finished pieces to Aakar Studio, Hyderabad, for QC and packing.",
        ].join("\n");
        files.push({ name: `${dir}/print-sheet.txt`, data: Buffer.from(sheet + "\n", "utf8") });
        files.push({ name: `${dir}/model.3mf`, data: Buffer.from(`Aakar mock 3MF placeholder for ${item.version_id}\n`) });
        files.push({ name: `${dir}/model.stl`, data: Buffer.from(`solid aakar_mock\nendsolid aakar_mock\n`) });
      });
      return binary(res, "application/zip", zip(files), `${o.number}-print-pack.zip`);
    }
    if ((m = p.match(/^\/admin\/api\/orders\/([^/]+)\/qc-photos$/)) && method === "POST") {
      const o = orders.get(m[1]);
      if (!o) return problem(res, 404, "not_found", "No order with that id.");
      const buf = await readBody(req);
      const parts = parseMultipart(buf, req.headers["content-type"]);
      const file = parts.find((x) => x.name === "file");
      validate(file && file.body.length > 0, "A file part named 'file' is required.");
      if (file.body.length > MAX_UPLOAD) return problem(res, 413, "payload_too_large", "Photos must be 10 MB or smaller.");
      const note = parts.find((x) => x.name === "note")?.body.toString("utf8").trim() || null;
      const id = randomUUID();
      const contentType = file.type || "application/octet-stream";
      uploads.set(id, { bytes: file.body, contentType });
      const asset = { id, kind: "qc_photo", url: `${BASE}/mock-assets/uploads/${id}`, content_type: contentType, bytes: file.body.length, note, created_at: iso(now()) };
      o.qc_photos.push(asset);
      recordAudit(who.email, "order.qc_photo", o.number, null, { id, bytes: asset.bytes, note });
      return json(res, 201, asset);
    }
    if ((m = p.match(/^\/admin\/api\/orders\/([^/]+)\/packaging-card\.pdf$/)) && method === "GET") {
      const o = orders.get(m[1]);
      if (!o) return problem(res, 404, "not_found", "No order with that id.");
      if (!["packed", "shipped", "delivered"].includes(o.status)) return problem(res, 409, "order_not_packed", `${o.number} is ${o.status.replace(/_/g, " ")}; the packaging card is generated once the order is packed.`);
      const code = o.number.replace("AK-", "").replace(/^0+/, "");
      const lines = [
        "Designed by You. Crafted by Aakar.",
        o.title,
        `Finish: ${o.items.map((i) => i.material_name).join(", ")}`,
        `Studio: Aakar Studio · Hyderabad`,
        `Packed: ${new Date().toLocaleDateString("en-IN", { day: "numeric", month: "long", year: "numeric" })}`,
        `Order: ${o.number}`,
        "",
        `Reprint or remix: aakar.studio/k/${code}`,
        "(QR code: rendered by the real API; mock server prints the link only)",
      ];
      return binary(res, "application/pdf", pdf(lines), `${o.number}-packaging-card.pdf`);
    }

    if (p === "/admin/api/pricing/policies" && method === "GET") return json(res, 200, [...policies].sort((a, b) => (a.created_at < b.created_at ? 1 : -1)));
    if (p === "/admin/api/pricing/policies/active") return json(res, 200, activePolicy());
    if (p === "/admin/api/pricing/policies" && method === "POST") {
      requireOwner(who);
      const body = await readJson(req);
      validate(typeof body.version === "string" && /^[A-Za-z0-9._-]{3,40}$/.test(body.version), "version must be 3–40 characters of letters, digits, dot, underscore or dash.");
      validatePolicy(body.policy);
      if (policies.some((x) => x.version === body.version)) return problem(res, 409, "policy_version_exists", `Version ${body.version} already exists; pick a new id.`);
      const before = activePolicy();
      for (const x of policies) x.active = false;
      const created = { version: body.version, active: true, policy: normalisePolicy(body.policy), note: body.note?.trim() || null, created_at: iso(now()), created_by: who.email };
      policies.unshift(created);
      recordAudit(who.email, "pricing.publish", created.version, before?.policy ?? null, created.policy);
      return json(res, 201, created);
    }
    if (p === "/admin/api/pricing/preview" && method === "POST") {
      const body = await readJson(req);
      validatePolicy(body.policy);
      const mat = materialById(body.material);
      validate(mat, `Unknown material '${body.material}'.`);
      validate(typeof body.extruded_volume_cm3 === "number" && body.extruded_volume_cm3 > 0, "extruded_volume_cm3 must be a positive number.");
      validate(Number.isInteger(body.print_seconds) && body.print_seconds > 0, "print_seconds must be a positive integer.");
      let family;
      if (body.family_id !== undefined && body.family_id !== null && body.family_id !== "") {
        family = familyById(body.family_id);
        if (!family) return problem(res, 422, "unknown_family", `Unknown family '${body.family_id}'.`);
      }
      return json(res, 200, priceWith(normalisePolicy(body.policy), "preview", mat, body.extruded_volume_cm3, body.print_seconds, { family }));
    }

    if (p === "/admin/api/materials" && method === "GET") return json(res, 200, [...materials].sort((a, b) => a.sort_order - b.sort_order));
    if (p === "/admin/api/materials" && method === "POST") {
      requireOwner(who);
      const input = normaliseMaterial(await readJson(req));
      if (materialById(input.id)) return problem(res, 409, "material_exists", `Material '${input.id}' already exists.`);
      const created = { ...input, updated_at: iso(now()) };
      materials.push(created);
      recordAudit(who.email, "material.create", created.id, null, input);
      return json(res, 201, created);
    }
    if ((m = p.match(/^\/admin\/api\/materials\/([^/]+)$/)) && method === "PUT") {
      requireOwner(who);
      const existing = materialById(decodeURIComponent(m[1]));
      if (!existing) return problem(res, 404, "not_found", "No such material.");
      const input = normaliseMaterial(await readJson(req));
      validate(input.id === existing.id, "The id in the body must match the path.");
      const before = { ...existing };
      delete before.updated_at;
      Object.assign(existing, input, { updated_at: iso(now()) });
      recordAudit(who.email, "material.update", existing.id, before, input);
      return json(res, 200, existing);
    }

    if (p === "/admin/api/catalog/items" && method === "GET") return json(res, 200, catalog);
    if (p === "/admin/api/catalog/items" && method === "POST") {
      requireOwner(who);
      const input = normaliseCatalogItem(await readJson(req));
      if (catalog.some((c) => c.slug === input.slug)) return problem(res, 409, "slug_exists", `An item with slug '${input.slug}' already exists.`);
      catalog.push(input);
      recordAudit(who.email, "catalog.create", input.slug, null, input);
      return json(res, 201, input);
    }
    if ((m = p.match(/^\/admin\/api\/catalog\/items\/([^/]+)$/)) && method === "PUT") {
      requireOwner(who);
      const existing = catalog.find((c) => c.slug === decodeURIComponent(m[1]));
      if (!existing) return problem(res, 404, "not_found", "No such catalog item.");
      const input = normaliseCatalogItem(await readJson(req));
      validate(input.slug === existing.slug, "The slug in the body must match the path.");
      const before = { ...existing };
      Object.assign(existing, input);
      recordAudit(who.email, "catalog.update", existing.slug, before, input);
      return json(res, 200, existing);
    }

    if (p === "/admin/api/catalog/shelves" && method === "GET") return json(res, 200, shelves);

    if (p === "/admin/api/families" && method === "GET") return json(res, 200, [...families].sort((a, b) => a.sort_order - b.sort_order).map(familyView));
    if (p === "/admin/api/families" && method === "POST") {
      requireOwner(who);
      const input = normaliseFamily(await readJson(req));
      if (familyById(input.id)) return problem(res, 409, "family_exists", `Family '${input.id}' already exists.`);
      const created = { ...input, updated_at: iso(now()) };
      families.push(created);
      recordAudit(who.email, "family.create", created.id, null, input);
      return json(res, 201, familyView(created));
    }
    if ((m = p.match(/^\/admin\/api\/families\/([^/]+)$/)) && method === "PUT") {
      requireOwner(who);
      const existing = familyById(decodeURIComponent(m[1]));
      if (!existing) return problem(res, 404, "unknown_family", "No such family.");
      const input = normaliseFamily({ ...(await readJson(req)), id: existing.id }); // the id in the path wins
      const before = omit(existing, "updated_at");
      Object.assign(existing, input, { updated_at: iso(now()) });
      recordAudit(who.email, "family.update", existing.id, before, input);
      return json(res, 200, familyView(existing));
    }

    if (p === "/admin/api/hardware" && method === "GET") return json(res, 200, hardwareItems);
    if (p === "/admin/api/hardware" && method === "POST") {
      requireOwner(who);
      const input = normaliseHardware(await readJson(req));
      if (hardwareBySku(input.sku)) return problem(res, 409, "hardware_exists", `Hardware '${input.sku}' already exists.`);
      const created = { ...input, updated_at: iso(now()) };
      hardwareItems.push(created);
      recordAudit(who.email, "hardware.create", created.sku, null, input);
      return json(res, 201, created);
    }
    if ((m = p.match(/^\/admin\/api\/hardware\/([^/]+)$/)) && method === "PUT") {
      requireOwner(who);
      const existing = hardwareBySku(decodeURIComponent(m[1]));
      if (!existing) return problem(res, 404, "not_found", "No such hardware item.");
      const input = normaliseHardware(await readJson(req));
      validate(input.sku === existing.sku, "The sku in the body must match the path.");
      const before = omit(existing, "updated_at");
      Object.assign(existing, input, { updated_at: iso(now()) });
      recordAudit(who.email, "hardware.update", existing.sku, before, input);
      return json(res, 200, existing);
    }

    if (p === "/admin/api/experiences" && method === "GET") return json(res, 200, sortedExperiences());
    if (p === "/admin/api/experiences" && method === "POST") {
      requireOwner(who);
      const body = await readJson(req);
      const draft = normaliseExperience(body);
      if (experienceById(draft.id)) return problem(res, 409, "experience_exists", `Experience '${draft.id}' already exists; update it with PUT /admin/api/experiences/${draft.id}.`);
      if (experiences.some((x) => x.slug === draft.slug)) return problem(res, 409, "slug_exists", `Another experience already lives at /duniya/${draft.slug}.`);
      validateExperienceRefs(draft);
      const created = { ...draft, updated_at: iso(now()) };
      experiences.push(created);
      recordAudit(who.email, "experience.create", created.id, null, draft);
      return json(res, 201, created);
    }
    if ((m = p.match(/^\/admin\/api\/experiences\/([^/]+)$/)) && method === "PUT") {
      requireOwner(who);
      const existing = experienceById(decodeURIComponent(m[1]));
      if (!existing) return problem(res, 404, "unknown_experience", `Experience ${decodeURIComponent(m[1])} was not found.`);
      const draft = normaliseExperience({ ...(await readJson(req)), id: existing.id }); // the id in the path wins
      if (experiences.some((x) => x.id !== existing.id && x.slug === draft.slug)) return problem(res, 409, "slug_exists", `Another experience already lives at /duniya/${draft.slug}.`);
      validateExperienceRefs(draft);
      const before = omit(existing, "updated_at");
      Object.assign(existing, draft, { updated_at: iso(now()) });
      recordAudit(who.email, "experience.update", existing.id, before, draft);
      return json(res, 200, existing);
    }
    if (p === "/admin/api/environments" && method === "GET") return json(res, 200, environments);
    if (p === "/admin/api/motifs" && method === "GET") return json(res, 200, motifs);

    if (p === "/admin/api/uploads" && method === "GET") {
      const status = url.searchParams.get("status");
      validate(!status || UPLOAD_STATUSES.includes(status), `status must be one of ${UPLOAD_STATUSES.join(", ")}.`);
      const limit = Math.min(200, Math.max(1, Number(url.searchParams.get("limit") ?? 50) || 50));
      const list = customerUploads.filter((u) => !status || u.status === status).sort((a, b) => (a.created_at < b.created_at ? 1 : -1));
      return json(res, 200, list.slice(0, limit));
    }
    if ((m = p.match(/^\/admin\/api\/content-reviews\/([^/]+)$/)) && method === "POST") {
      // Studio or owner may decide; no requireOwner here.
      const upload = customerUploads.find((u) => u.review?.id === m[1]);
      if (!upload) return problem(res, 404, "not_found", "No content review with that id.");
      if (upload.review.status !== "pending") return problem(res, 409, "review_already_decided", `This upload was ${upload.review.status} by ${upload.review.reviewer_email ?? "staff"} already.`);
      const body = await readJson(req);
      validate(body.decision === "approved" || body.decision === "rejected", "decision must be approved or rejected.");
      validate(body.note === undefined || (typeof body.note === "string" && body.note.length <= 200), "note must be at most 200 characters.");
      const before = { status: upload.status, review_status: upload.review.status };
      upload.review = { ...upload.review, status: body.decision, decision_note: body.note?.trim() || null, reviewer_email: who.email, decided_at: iso(now()) };
      upload.status = body.decision === "approved" ? "ready" : "rejected";
      if (upload.status === "ready" && !upload.url) upload.url = `${BASE}/mock-assets/customer-uploads/${upload.id}`;
      recordAudit(who.email, "review.decide", upload.review.id, before, { status: upload.status, review_status: upload.review.status, note: upload.review.decision_note });
      return json(res, 200, upload);
    }

    if (p === "/admin/api/templates" && method === "GET") return json(res, 200, templates.map(templateView));
    if ((m = p.match(/^\/admin\/api\/templates\/([^/]+)$/)) && method === "PUT") {
      requireOwner(who);
      const t = templates.find((x) => x.id === decodeURIComponent(m[1]));
      if (!t) return problem(res, 404, "not_found", "No such template.");
      const body = await readJson(req);
      validate(typeof body.live === "boolean", "live must be a boolean.");
      const before = { live: t.live };
      t.live = body.live;
      recordAudit(who.email, "template.live", t.id, before, { live: t.live });
      return json(res, 200, templateView(t));
    }

    if (p === "/admin/api/notifications") {
      const orderId = url.searchParams.get("order_id");
      const list = orderId ? notifications.filter((n) => n.order_id === orderId) : notifications;
      return json(res, 200, paginate(list, url, 50, 200));
    }
    if (p === "/admin/api/audit") return json(res, 200, paginate(audit, url, 50, 200));

    return problem(res, 404, "not_found", `No route for ${method} ${p}`);
  } catch (err) {
    return problem(res, err.status ?? 500, err.code ?? "internal", err.message);
  }
});

function validatePolicy(policy) {
  validate(policy && typeof policy === "object", "policy is required.");
  for (const k of ["machine_rate_paise_per_hour", "packaging_fee_paise", "shipping_flat_paise", "free_shipping_above_paise"]) {
    validate(Number.isInteger(policy[k]) && policy[k] >= 0, `${k} must be a non-negative integer (paise).`);
  }
  validate(typeof policy.margin_pct === "number" && policy.margin_pct >= 0 && policy.margin_pct <= 500, "margin_pct must be between 0 and 500.");
  validate(Number.isInteger(policy.round_to_rupees_ending_in) && policy.round_to_rupees_ending_in >= 0 && policy.round_to_rupees_ending_in <= 9, "round_to_rupees_ending_in must be 0–9.");
  validate(typeof policy.shipping_label === "string" && policy.shipping_label.length <= 60, "shipping_label must be at most 60 characters.");
  validate(policy.finishing_fee_paise && typeof policy.finishing_fee_paise === "object" && Object.values(policy.finishing_fee_paise).every((v) => Number.isInteger(v) && v >= 0), "finishing_fee_paise must map finish classes to non-negative integers.");
  if (policy.hardware_markup_pct !== undefined) validate(typeof policy.hardware_markup_pct === "number" && policy.hardware_markup_pct >= 0 && policy.hardware_markup_pct <= 500, "hardware_markup_pct must be between 0 and 500.");
  if (policy.family_rules !== undefined) {
    validate(policy.family_rules && typeof policy.family_rules === "object" && !Array.isArray(policy.family_rules), "family_rules must map family ids to rule objects.");
    for (const [familyId, rule] of Object.entries(policy.family_rules)) {
      if (!familyById(familyId)) throw Object.assign(new Error(`family_rules names unknown family '${familyId}'.`), { status: 422, code: "unknown_family" });
      validate(rule && typeof rule === "object", `family_rules.${familyId} must be an object.`);
      for (const k of ["minimum_subtotal_paise", "setup_fee_paise"]) if (rule[k] !== undefined) validate(Number.isInteger(rule[k]) && rule[k] >= 0, `family_rules.${familyId}.${k} must be a non-negative integer (paise).`);
      if (rule.qty_breaks !== undefined) validate(Array.isArray(rule.qty_breaks) && rule.qty_breaks.every((b) => Number.isInteger(b.min_qty) && b.min_qty >= 2 && typeof b.discount_pct === "number" && b.discount_pct >= 0 && b.discount_pct <= 90), `family_rules.${familyId}.qty_breaks must list {min_qty ≥ 2, discount_pct 0–90}.`);
    }
  }
}
function normalisePolicy(p) {
  const out = {
    machine_rate_paise_per_hour: p.machine_rate_paise_per_hour,
    finishing_fee_paise: { ...p.finishing_fee_paise },
    packaging_fee_paise: p.packaging_fee_paise,
    margin_pct: p.margin_pct,
    round_to_rupees_ending_in: p.round_to_rupees_ending_in,
    shipping_flat_paise: p.shipping_flat_paise,
    free_shipping_above_paise: p.free_shipping_above_paise,
    shipping_label: p.shipping_label,
  };
  if (p.hardware_markup_pct !== undefined) out.hardware_markup_pct = p.hardware_markup_pct;
  if (p.family_rules !== undefined) {
    out.family_rules = Object.fromEntries(
      Object.entries(p.family_rules).map(([id, r]) => [
        id,
        {
          ...(r.minimum_subtotal_paise !== undefined ? { minimum_subtotal_paise: r.minimum_subtotal_paise } : {}),
          ...(r.setup_fee_paise !== undefined ? { setup_fee_paise: r.setup_fee_paise } : {}),
          ...(r.qty_breaks !== undefined ? { qty_breaks: r.qty_breaks.map((b) => ({ min_qty: b.min_qty, discount_pct: b.discount_pct })) } : {}),
        },
      ]),
    );
  }
  return out;
}
function normaliseMaterial(b) {
  validate(typeof b.id === "string" && /^[a-z][a-z0-9_]*$/.test(b.id), "id must be snake_case starting with a letter.");
  validate(typeof b.name === "string" && b.name.trim() && b.name.length <= 40, "name is required (max 40 characters).");
  validate(typeof b.filament === "string" && b.filament.trim() && b.filament.length <= 80, "filament is required (max 80 characters).");
  validate(typeof b.density_g_cm3 === "number" && b.density_g_cm3 >= 0.5 && b.density_g_cm3 <= 3, "density_g_cm3 must be between 0.5 and 3.");
  validate(b.finish_class === "matte" || b.finish_class === "silk", "finish_class must be matte or silk.");
  validate(Number.isInteger(b.rate_per_g_paise) && b.rate_per_g_paise >= 0, "rate_per_g_paise must be a non-negative integer.");
  validate(b.pbr && typeof b.pbr.color === "string" && typeof b.pbr.roughness === "number" && typeof b.pbr.metalness === "number", "pbr needs color, roughness and metalness.");
  return {
    id: b.id,
    name: b.name.trim(),
    filament: b.filament.trim(),
    density_g_cm3: b.density_g_cm3,
    finish_class: b.finish_class,
    rate_per_g_paise: b.rate_per_g_paise,
    heat_safe: Boolean(b.heat_safe ?? false),
    available: b.available ?? true,
    sort_order: Number.isInteger(b.sort_order) ? b.sort_order : 100,
    pbr: {
      color: b.pbr.color,
      roughness: b.pbr.roughness,
      metalness: b.pbr.metalness,
      clearcoat: b.pbr.clearcoat ?? 0,
      clearcoat_roughness: b.pbr.clearcoat_roughness ?? 0,
      sheen: b.pbr.sheen ?? 0,
      sheen_color: b.pbr.sheen_color ?? "#FFFFFF",
    },
  };
}
function normaliseCatalogItem(b) {
  validate(typeof b.slug === "string" && /^[a-z0-9-]{3,60}$/.test(b.slug), "slug must be 3–60 lowercase letters, digits or dashes.");
  validate(typeof b.name === "string" && b.name.trim() && b.name.length <= 80, "name is required (max 80 characters).");
  validate(typeof b.category === "string" && shelfById(b.category), `category must be a shelf id: ${shelves.map((sh) => sh.id).join(", ")}.`);
  if (b.family_id !== undefined && b.family_id !== null) {
    validate(typeof b.family_id === "string", "family_id must be a string or null.");
    if (!familyById(b.family_id)) throw Object.assign(new Error(`Unknown family '${b.family_id}'.`), { status: 422, code: "unknown_family" });
  }
  validate(typeof b.template_id === "string" && b.template_id.trim(), "template_id is required.");
  validate(b.default_params && typeof b.default_params === "object" && !Array.isArray(b.default_params), "default_params must be an object.");
  validate(typeof b.default_material === "string" && materialById(b.default_material), "default_material must be a known material id.");
  validate(Number.isInteger(b.base_price_paise) && b.base_price_paise >= 0, "base_price_paise must be a non-negative integer.");
  validate(typeof b.specs_line === "string" && b.specs_line.length <= 120, "specs_line is required (max 120 characters).");
  validate(typeof b.available === "boolean", "available must be a boolean.");
  if (b.environment !== undefined && b.environment !== null) validate(ENVIRONMENTS.includes(b.environment), `environment '${b.environment}' is not a backdrop; the backdrops are: ${ENVIRONMENTS.join(", ")}.`);
  return {
    slug: b.slug,
    name: b.name.trim(),
    category: b.category,
    family_id: b.family_id ?? null,
    description: typeof b.description === "string" ? b.description.slice(0, 500) : "",
    template_id: b.template_id.trim(),
    default_params: b.default_params,
    default_material: b.default_material,
    base_price_paise: b.base_price_paise,
    specs_line: b.specs_line,
    environment: typeof b.environment === "string" ? b.environment : "studio",
    available: b.available,
    media: Array.isArray(b.media) ? b.media : [],
  };
}

function normaliseHardware(b) {
  validate(typeof b.sku === "string" && ID_RE.test(b.sku) && b.sku.length <= 40, "sku must be snake_case starting with a letter (max 40).");
  validate(typeof b.name === "string" && b.name.trim() && b.name.length <= 120, "name is required (max 120 characters).");
  validate(Number.isInteger(b.unit_cost_paise) && b.unit_cost_paise >= 0, "unit_cost_paise must be a non-negative integer.");
  if (b.weight_g !== undefined) validate(typeof b.weight_g === "number" && b.weight_g >= 0, "weight_g must be a non-negative number.");
  for (const [k, max] of [["supplier", 120], ["url", 500], ["notes", 200]]) if (b[k] !== undefined) validate(typeof b[k] === "string" && b[k].length <= max, `${k} must be a string of at most ${max} characters.`);
  return {
    sku: b.sku,
    name: b.name.trim(),
    unit_cost_paise: b.unit_cost_paise,
    ...(b.weight_g !== undefined ? { weight_g: b.weight_g } : {}),
    ...(b.supplier ? { supplier: b.supplier } : {}),
    ...(b.url ? { url: b.url } : {}),
    ...(b.notes ? { notes: b.notes } : {}),
    available: b.available ?? true,
  };
}
function normaliseFamily(b) {
  validate(typeof b.id === "string" && ID_RE.test(b.id) && b.id.length <= 40, "id must be snake_case starting with a letter (max 40).");
  validate(typeof b.codename === "string" && b.codename.trim() && b.codename.length <= 40, "codename is required (max 40 characters).");
  validate(typeof b.name === "string" && b.name.trim() && b.name.length <= 80, "name is required (max 80 characters).");
  if (b.tagline !== undefined) validate(typeof b.tagline === "string" && b.tagline.length <= 120, "tagline must be at most 120 characters.");
  if (b.description !== undefined) validate(typeof b.description === "string" && b.description.length <= 500, "description must be at most 500 characters.");
  validate(FAMILY_KINDS.includes(b.kind), `kind must be one of ${FAMILY_KINDS.join(", ")}.`);
  validate(FAMILY_TIERS.includes(b.tier), `tier must be one of ${FAMILY_TIERS.join(", ")}.`);
  validate(typeof b.shelf === "string" && shelfById(b.shelf), `shelf must be a shelf id: ${shelves.map((sh) => sh.id).join(", ")}.`);
  if (b.demand_rank !== undefined) validate(Number.isInteger(b.demand_rank) && b.demand_rank >= 1, "demand_rank must be an integer of at least 1.");
  validate(typeof b.default_template_id === "string" && ID_RE.test(b.default_template_id), "default_template_id must be snake_case starting with a letter.");
  if (b.environment !== undefined) validate(ENVIRONMENTS.includes(b.environment), `environment '${b.environment}' is not a backdrop; the backdrops are: ${ENVIRONMENTS.join(", ")}.`);
  if (b.size_envelope_mm !== undefined) {
    const e = b.size_envelope_mm;
    validate(e && typeof e.min_longest_mm === "number" && e.min_longest_mm > 0 && typeof e.max_longest_mm === "number" && e.max_longest_mm > 0, "size_envelope_mm needs positive min_longest_mm and max_longest_mm.");
    validate(e.min_longest_mm <= e.max_longest_mm, "size_envelope_mm.min_longest_mm must not exceed max_longest_mm.");
  }
  if (b.hardware !== undefined) {
    validate(Array.isArray(b.hardware) && b.hardware.every((h) => h && typeof h.sku === "string" && Number.isInteger(h.qty) && h.qty >= 1), "hardware must list {sku, qty ≥ 1}.");
    for (const h of b.hardware) validate(hardwareBySku(h.sku), `hardware sku '${h.sku}' is not a known hardware item.`);
  }
  if (b.material_rules !== undefined) {
    const r = b.material_rules;
    validate(r && typeof r === "object", "material_rules must be an object.");
    if (r.heat_safe_only !== undefined) validate(typeof r.heat_safe_only === "boolean", "material_rules.heat_safe_only must be a boolean.");
    if (r.allowed !== undefined && r.allowed !== null) validate(Array.isArray(r.allowed) && r.allowed.every((id) => typeof id === "string" && ID_RE.test(id)), "material_rules.allowed must be a list of material ids or null.");
    if (r.excluded_finish_classes !== undefined) validate(Array.isArray(r.excluded_finish_classes) && r.excluded_finish_classes.every((c) => FINISH_CLASSES.includes(c)), "material_rules.excluded_finish_classes must list matte or silk.");
  }
  validate(SHAPE_TOLERANCES.includes(b.shape_tolerance), `shape_tolerance must be one of ${SHAPE_TOLERANCES.join(", ")}.`);
  const slot = b.content_slot;
  validate(slot && typeof slot === "object" && Array.isArray(slot.accepts) && slot.accepts.every((f) => FEATURE_TYPES.includes(f)), `content_slot.accepts must list feature types from ${FEATURE_TYPES.join(", ")}.`);
  validate(new Set(slot.accepts).size === slot.accepts.length, "content_slot.accepts must not repeat a feature type.");
  if (slot.anchors !== undefined) validate(Array.isArray(slot.anchors) && slot.anchors.every((a) => typeof a === "string" && a.trim()), "content_slot.anchors must be a list of anchor ids.");
  if (slot.hero_volume !== undefined) validate(typeof slot.hero_volume === "boolean", "content_slot.hero_volume must be a boolean.");
  if (slot.max_text_chars !== undefined) validate(Number.isInteger(slot.max_text_chars) && slot.max_text_chars >= 1 && slot.max_text_chars <= 40, "content_slot.max_text_chars must be 1–40.");
  validate(typeof b.available === "boolean", "available must be a boolean.");
  return {
    id: b.id,
    codename: b.codename.trim(),
    name: b.name.trim(),
    ...(b.tagline !== undefined ? { tagline: b.tagline } : {}),
    ...(b.description !== undefined ? { description: b.description } : {}),
    kind: b.kind,
    tier: b.tier,
    shelf: b.shelf,
    ...(b.demand_rank !== undefined ? { demand_rank: b.demand_rank } : {}),
    default_template_id: b.default_template_id,
    environment: b.environment ?? "studio",
    ...(b.size_envelope_mm !== undefined ? { size_envelope_mm: { min_longest_mm: b.size_envelope_mm.min_longest_mm, max_longest_mm: b.size_envelope_mm.max_longest_mm } } : {}),
    hardware: (b.hardware ?? []).map((h) => ({ sku: h.sku, qty: h.qty })),
    material_rules: { heat_safe_only: b.material_rules?.heat_safe_only ?? false, allowed: b.material_rules?.allowed ?? null, excluded_finish_classes: [...(b.material_rules?.excluded_finish_classes ?? [])] },
    shape_tolerance: b.shape_tolerance,
    content_slot: {
      accepts: [...slot.accepts],
      anchors: [...(slot.anchors ?? [])],
      hero_volume: slot.hero_volume ?? false,
      ...(slot.max_text_chars !== undefined ? { max_text_chars: slot.max_text_chars } : {}),
    },
    available: b.available,
    sort_order: Number.isInteger(b.sort_order) ? b.sort_order : 100,
  };
}

/** Shape of an experience (experience.v1.json#/$defs/experience): 422 validation_failed with the field in the detail. */
function normaliseExperience(b) {
  validate(b && typeof b === "object", "Send an experience object.");
  validate(typeof b.id === "string" && ID_RE.test(b.id) && b.id.length <= 40, "id must be snake_case starting with a letter (max 40).");
  validate(typeof b.codename === "string" && b.codename.trim() && b.codename.length <= 40, "codename is required (max 40 characters).");
  validate(typeof b.slug === "string" && SLUG_RE.test(b.slug) && b.slug.length >= 2 && b.slug.length <= 60, "slug must be 2–60 lowercase letters and digits in words joined by single dashes.");
  validate(typeof b.title === "string" && b.title.trim() && b.title.length <= 80, "title is required (max 80 characters).");
  if (b.tagline !== undefined && b.tagline !== null) validate(typeof b.tagline === "string" && b.tagline.length <= 120, "tagline must be at most 120 characters.");
  if (b.description !== undefined && b.description !== null) validate(typeof b.description === "string" && b.description.length <= 500, "description must be at most 500 characters.");
  validate(typeof b.environment === "string" && b.environment.trim(), "environment is required.");
  const sfc = b.surface;
  validate(sfc && typeof sfc === "object" && typeof sfc.accent === "string" && HEX_RE.test(sfc.accent), "surface.accent must be an sRGB hex such as #D8AE5B.");
  if (sfc.paper_tint !== undefined && sfc.paper_tint !== null) validate(typeof sfc.paper_tint === "string" && HEX_RE.test(sfc.paper_tint), "surface.paper_tint must be an sRGB hex or null.");
  if (sfc.hero_media !== undefined && sfc.hero_media !== null) validate(typeof sfc.hero_media === "string" && sfc.hero_media.length <= 500, "surface.hero_media must be a URL or path of at most 500 characters, or null.");
  const style = b.style ?? "none";
  validate(STYLES.includes(style), `style must be one of ${STYLES.join(", ")}.`);
  const list = (name, value, max, check, what) => {
    if (value === undefined || value === null) return [];
    validate(Array.isArray(value) && value.length <= max, `${name} must be a list of at most ${max}.`);
    for (const v of value) validate(check(v), `${name} lists '${typeof v === "string" ? v : JSON.stringify(v)}', which is not ${what}.`);
    return value;
  };
  const motifPack = list("motif_pack", b.motif_pack, 24, (v) => typeof v === "string" && ID_RE.test(v), "a motif or pack id");
  validate(Array.isArray(b.avatars), "avatars must be a list of family ids.");
  const avatars = list("avatars", b.avatars, 24, (v) => typeof v === "string" && v.trim(), "a family id");
  const items = list("items", b.items, 24, (v) => typeof v === "string" && /^[a-z0-9-]{3,60}$/.test(v), "a Shop item slug");
  const collections = list("collections", b.collections, 24, (c) => c && typeof c === "object" && typeof c.id === "string" && ID_RE.test(c.id) && c.id.length <= 40 && typeof c.title === "string" && c.title.trim() && c.title.length <= 80 && (c.licence_ref === undefined || c.licence_ref === null || (typeof c.licence_ref === "string" && c.licence_ref.length <= 120)), "a collection {id, title, licence_ref}");
  const season = list("season", b.season, 12, (w) => w && typeof w === "object" && typeof w.label === "string" && w.label.trim() && w.label.length <= 40 && typeof w.starts_on === "string" && typeof w.ends_on === "string", "a season window {starts_on, ends_on, label}");
  for (const [name, values] of [["avatars", avatars], ["items", items], ["motif_pack", motifPack], ["collections", collections.map((c) => c.id)]]) {
    const seen = new Set();
    for (const v of values) {
      validate(!seen.has(v), `${name} lists '${v}' more than once.`);
      seen.add(v);
    }
  }
  season.forEach((w, i) => checkSeasonWindow(i, w));
  validate(typeof b.available === "boolean", "available must be a boolean.");
  if (b.sort_order !== undefined) validate(Number.isInteger(b.sort_order), "sort_order must be an integer.");
  return {
    id: b.id, codename: b.codename.trim(), slug: b.slug, title: b.title.trim(),
    ...(typeof b.tagline === "string" ? { tagline: b.tagline } : {}),
    ...(typeof b.description === "string" ? { description: b.description } : {}),
    environment: b.environment.trim(),
    surface: { accent: sfc.accent, paper_tint: sfc.paper_tint ?? null, hero_media: sfc.hero_media ?? null },
    style, motif_pack: [...motifPack], avatars: avatars.map((a) => a.trim()), items: [...items],
    collections: collections.map((c) => ({ id: c.id, title: c.title.trim(), licence_ref: c.licence_ref ?? null })),
    season: season.map((w) => ({ starts_on: w.starts_on, ends_on: w.ends_on, label: w.label.trim() })),
    available: b.available, sort_order: Number.isInteger(b.sort_order) ? b.sort_order : 100,
  };
}
/** Both ends dates (in order) or both month-days (a recurring window, which may wrap the new year), every day real. */
function checkSeasonWindow(i, w) {
  const where = `season[${i}] (${w.label})`;
  const a = MONTH_DAY_RE.exec(w.starts_on);
  const b = MONTH_DAY_RE.exec(w.ends_on);
  if (a || b) {
    validate(a && b, `${where} mixes a date and a month-day; use two dates (2026-10-20) or two month-days (--10-01).`);
    for (const md of [a, b]) {
      const month = Number(md[1]), day = Number(md[2]);
      validate(month >= 1 && month <= 12 && day >= 1 && day <= [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1], `${where} names a day that does not exist (${w.starts_on} to ${w.ends_on}).`);
    }
    return;
  }
  validate(DATE_RE.test(w.starts_on) && DATE_RE.test(w.ends_on), `${where} needs two dates (2026-10-20) or two month-days (--10-01).`);
  const real = (d) => !Number.isNaN(Date.parse(`${d}T00:00:00Z`)) && new Date(`${d}T00:00:00Z`).toISOString().slice(0, 10) === d;
  validate(real(w.starts_on) && real(w.ends_on), `${where} names a day that does not exist (${w.starts_on} to ${w.ends_on}).`);
  validate(w.starts_on <= w.ends_on, `${where} ends (${w.ends_on}) before it starts (${w.starts_on}).`);
}
/** What the schema cannot say: the backdrop exists, every avatar is a family (422 unknown_family), every item a Shop item. */
function validateExperienceRefs(x) {
  validate(ENVIRONMENTS.includes(x.environment), `environment '${x.environment}' is not a backdrop; the backdrops are: ${ENVIRONMENTS.join(", ")}.`);
  for (const id of x.avatars) {
    if (!familyById(id)) throw Object.assign(new Error(`avatars names '${id}', which is not a family (see GET /admin/api/families).`), { status: 422, code: "unknown_family" });
  }
  for (const slug of x.items) validate(catalog.some((c) => c.slug === slug), `items names '${slug}', which is not a Shop item (see GET /admin/api/catalog/items).`);
}

server.listen(PORT, () => {
  console.log(`Aakar mock management API on ${BASE}`);
  console.log(`  owner  : studio@aakar.local / aakar-studio`);
  console.log(`  studio : karigar@aakar.local / aakar-karigar   (read-only configuration)`);
  console.log(`  ${orders.size} orders, ${materials.length} materials, ${catalog.length} catalog items, ${templates.length} templates, ${policies.length} pricing policy versions`);
  console.log(`  ${shelves.length} shelves, ${families.length} families (Avatars), ${hardwareItems.length} hardware items, ${customerUploads.filter((u) => u.status === "pending_review").length} uploads pending review`);
  console.log(`  ${experiences.length} Duniya experiences (${experiences.filter((e) => e.available).length} available), ${environments.length} backgrounds, ${motifs.length} motifs`);
});
