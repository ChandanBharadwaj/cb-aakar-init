// Vocabulary for outcome families (Avatars) and the content slot (Chhaap).
// Code ids stay snake_case English; the brand words are the labels staff see next to them
// (docs/research/outcome-categories/implementation-plan.md, "Naming convention").
import type { AdminFamily, AdminFamilyInput, FamilyKind, FamilyTier, FeatureType, ShapeTolerance } from "@/lib/api/types";
import type { Tone } from "@/lib/orders";

/** Chhaap types with their brand names. The code always travels with the brand word (ADR-0002's clarity rule). */
export const FEATURE_TYPES: readonly { id: FeatureType; codename: string; label: string }[] = [
  { id: "emboss_text", codename: "Naam", label: "Name, text" },
  { id: "motif", codename: "Buti", label: "Textile motif" },
  { id: "relief_image", codename: "Chhavi", label: "Photo relief" },
  { id: "hero_mesh", codename: "Roop", label: "Your own 3D form" },
];

export function featureInfo(id: string): { id: string; codename: string; label: string } {
  return FEATURE_TYPES.find((f) => f.id === id) ?? { id, codename: id, label: id.replace(/_/g, " ") };
}

export const FAMILY_KINDS: readonly { id: FamilyKind; label: string; hint: string }[] = [
  { id: "carrier", label: "Avatar (carrier)", hint: "Carries the customer's idea: keychain, magnet, ornament, plaque" },
  { id: "object", label: "Object", hint: "A parametric object family (phone stand, planter) with content on its anchors" },
  { id: "raw", label: "Raw (Swaroop)", hint: "Prints the customer's own model as it is; only raw_print" },
  { id: "hybrid", label: "Jod (hybrid)", hint: "A printed Chhaap that plugs into a bought-in base through a Kadi connector; only the top is printed" },
];

export const FAMILY_TIERS: readonly { id: FamilyTier; label: string }[] = [
  { id: "launch", label: "Launch" },
  { id: "next", label: "Next" },
  { id: "later", label: "Later" },
];

export const SHAPE_TOLERANCES: readonly { id: ShapeTolerance; label: string; hint: string }[] = [
  { id: "any", label: "Any", hint: "A plate or plinth absorbs whatever shape the content has" },
  { id: "constrained", label: "Constrained", hint: "Content must satisfy a rule: attachment region, flat top, shell" },
  { id: "strict", label: "Strict", hint: "The functional body is parametric; content only decorates it" },
];

export const KIND_TONE: Record<FamilyKind, Tone> = { carrier: "accent", object: "info", raw: "warning", hybrid: "success" };
export const TIER_TONE: Record<FamilyTier, Tone> = { launch: "success", next: "info", later: "neutral" };

/** "Saathi · Keychain & bag charm": the codename always paired with the plain descriptor. */
export function familyTitle(f: Pick<AdminFamilyInput, "codename" | "name">): string {
  return f.codename && f.name ? `${f.codename} · ${f.name}` : f.codename || f.name;
}

/** Row → input body: the read-only state (`ready`, `template_ids`, `updated_at`) must not travel back on PUT. */
export function familyInput(f: AdminFamily): AdminFamilyInput {
  return {
    id: f.id,
    codename: f.codename,
    name: f.name,
    ...(f.tagline !== undefined ? { tagline: f.tagline } : {}),
    ...(f.description !== undefined ? { description: f.description } : {}),
    kind: f.kind,
    tier: f.tier,
    shelf: f.shelf,
    ...(f.demand_rank !== undefined ? { demand_rank: f.demand_rank } : {}),
    default_template_id: f.default_template_id,
    ...(f.environment !== undefined ? { environment: f.environment } : {}),
    ...(f.size_envelope_mm ? { size_envelope_mm: { ...f.size_envelope_mm } } : {}),
    ...(f.hardware ? { hardware: f.hardware.map((h) => ({ sku: h.sku, qty: h.qty })) } : {}),
    ...(f.material_rules ? { material_rules: { ...f.material_rules, ...(f.material_rules.allowed ? { allowed: [...f.material_rules.allowed] } : {}) } } : {}),
    shape_tolerance: f.shape_tolerance,
    content_slot: { ...f.content_slot, accepts: [...f.content_slot.accepts], ...(f.content_slot.anchors ? { anchors: [...f.content_slot.anchors] } : {}) },
    available: f.available,
    sort_order: f.sort_order,
  };
}

/** Families in display order (`sort_order`, then codename). */
export function sortFamilies<T extends Pick<AdminFamilyInput, "sort_order" | "codename">>(list: readonly T[]): T[] {
  return [...list].sort((a, b) => a.sort_order - b.sort_order || a.codename.localeCompare(b.codename));
}
