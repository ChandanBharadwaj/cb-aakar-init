// Consumer copy for outcome families (Avatars): hardware and size lines, plain-language size hints,
// picker ordering and the material rules that trim the finish chips. Codenames, names and taglines are
// data from the API (portal-editable); code ids such as `raw_print` never change.
import type { Family, HardwareRef, Material, TemplateDescriptor } from "@/lib/api/types";
import { joinList } from "@/lib/format";

/** The one family that prints a customer's own model file as it is (Swaroop). */
export const RAW_FAMILY_ID = "raw_print";

export function isRawFamily(family: Pick<Family, "id" | "kind">): boolean {
  return family.kind === "raw" || family.id === RAW_FAMILY_ID;
}

/** `desk_tech` → "desk tech". */
export function humanise(id: string): string {
  return id.replace(/_/g, " ");
}

/** Customer-facing name of a bought-in part; the API fills `name` from hardware_items, the sku is the fallback. */
export function hardwareName(part: HardwareRef): string {
  return part.name ?? humanise(part.sku);
}

/**
 * Parts with their customer-facing names. A template's list (and an older version's) carries only sku and qty,
 * so a missing name comes from the family's list, which the API fills from hardware_items; only a part the family
 * doesn't name either falls back to its humanised sku (in `hardwareName`).
 */
export function namedHardware(parts: readonly HardwareRef[] | undefined, family?: Pick<Family, "hardware">): HardwareRef[] {
  const names = new Map((family?.hardware ?? []).flatMap((h) => (h.name ? [[h.sku, h.name] as const] : [])));
  return (parts ?? []).map((h) => {
    const name = h.name ?? names.get(h.sku);
    return name ? { ...h, name } : { ...h };
  });
}

/** What goes in the box with this template: its own list when it has one (its pockets are cut for those parts), else the family default. */
export function packedHardware(family: Pick<Family, "hardware">, template?: Pick<TemplateDescriptor, "hardware">): HardwareRef[] {
  return namedHardware(template?.hardware?.length ? template.hardware : family.hardware, family);
}

/** "Comes with a steel split ring 25 mm", "Comes with 2 × neodymium disc magnet 10 × 3 mm": the picker's hardware line. */
export function hardwareSentence(hardware: readonly HardwareRef[] | undefined): string | undefined {
  const list = (hardware ?? []).filter((h) => h.qty > 0);
  if (list.length === 0) return undefined;
  const parts = list.map((h) => {
    const name = hardwareName(h);
    const pair = /,\s*pair$/i.test(name);
    const base = name.replace(/,\s*pair$/i, "");
    const lower = base.charAt(0).toLowerCase() + base.slice(1);
    if (h.qty > 1) return `${h.qty} × ${lower}`;
    if (pair) return `a pair of ${lower}`;
    return `${/^[aeiou]/i.test(lower) ? "an" : "a"} ${lower}`;
  });
  return `Comes with ${joinList(parts)}`;
}

/** "Steel split ring 25 mm · 2 × Neodymium disc magnet 10 × 3 mm": the value behind a "Comes with" label. */
export function hardwareNames(hardware: readonly HardwareRef[] | undefined): string | undefined {
  const list = (hardware ?? []).filter((h) => h.qty > 0);
  if (list.length === 0) return undefined;
  return list.map((h) => (h.qty > 1 ? `${h.qty} × ${hardwareName(h)}` : hardwareName(h))).join(" · ");
}

const fmtMm = (n: number) => String(Math.round(n));

/** Palm · hand · desk · shelf, by the longest side a family allows. */
export function sizeClass(maxLongestMm: number): string {
  if (maxLongestMm <= 75) return "Palm-sized";
  if (maxLongestMm <= 160) return "Hand-sized";
  if (maxLongestMm <= 250) return "Desk-sized";
  return "Shelf-sized";
}

/** "Palm-sized · 30–60 mm": the picker's envelope line. */
export function envelopeLine(family: Pick<Family, "size_envelope_mm">): string | undefined {
  const env = family.size_envelope_mm;
  if (!env?.max_longest_mm) return undefined;
  const range = env.min_longest_mm !== undefined ? `${fmtMm(env.min_longest_mm)}–${fmtMm(env.max_longest_mm)} mm` : `up to ${fmtMm(env.max_longest_mm)} mm`;
  return `${sizeClass(env.max_longest_mm)} · ${range}`;
}

/** Plain-language size for the Swaroop slider: a matchbox around 40 mm, a mug around 100, a shoebox around 200. */
export function sizeHint(longestMm: number): string {
  if (longestMm < 70) return "about the size of a matchbox";
  if (longestMm < 150) return "about the size of a mug";
  return "about the size of a shoebox";
}

/** "from ₹249" when the API knows the lowest price. */
export function priceFromLabel(family: Pick<Family, "price_from_paise">, formatPaise: (paise: number) => string): string | undefined {
  return typeof family.price_from_paise === "number" ? `from ${formatPaise(family.price_from_paise)}` : undefined;
}

const KIND_RANK: Record<Family["kind"], number> = { carrier: 0, object: 1, raw: 2 };

/** Picker order: Avatars (carriers), then object families, then Swaroop last; `sort_order` within each. */
export function sortFamilies(families: readonly Family[]): Family[] {
  return [...families].sort(
    (a, b) => KIND_RANK[a.kind] - KIND_RANK[b.kind] || (a.sort_order ?? 100) - (b.sort_order ?? 100) || a.name.localeCompare(b.name),
  );
}

/**
 * Finish ids a family allows: `material_rules.allowed` (null means all), `heat_safe_only`,
 * `excluded_finish_classes`, then the template's own list when one is given.
 */
export function allowedMaterialIds(
  materials: readonly Material[],
  family?: Pick<Family, "material_rules">,
  template?: Pick<TemplateDescriptor, "materials">,
): string[] {
  const rules = family?.material_rules;
  return materials
    .filter((m) => {
      if (rules?.allowed && !rules.allowed.includes(m.id)) return false;
      if (rules?.heat_safe_only && !m.heat_safe) return false;
      if (rules?.excluded_finish_classes?.includes(m.finish_class)) return false;
      if (template && template.materials.length > 0 && !template.materials.includes(m.id)) return false;
      return true;
    })
    .map((m) => m.id);
}

/** The descriptor a family uses when the request names no template. */
export function defaultTemplate(family: Pick<Family, "templates" | "default_template_id">, templateId?: string): TemplateDescriptor | undefined {
  return family.templates.find((t) => t.id === templateId) ?? family.templates.find((t) => t.id === family.default_template_id) ?? family.templates[0];
}
