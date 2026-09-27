import type { CatalogItem, Shelf, TemplateDescriptor } from "./api/types";
import { RAW_FAMILY_ID, humanise } from "./families";

export interface ShelfPill {
  id: string;
  label: string;
}

export const ALL_PIECES: ShelfPill = { id: "all", label: "All pieces" };

/** "All pieces" first, then the shelves in the API's display order (GET /api/catalog/shelves). */
export function shelfPills(shelves: readonly Shelf[]): ShelfPill[] {
  return [ALL_PIECES, ...[...shelves].sort((a, b) => a.sort_order - b.sort_order).map((s) => ({ id: s.id, label: s.label }))];
}

/** Shelf label for a category id; a shelf the API hasn't told us about reads as its humanised id. */
export function categoryLabel(id: string, shelves: readonly Pick<Shelf, "id" | "label">[] = []): string {
  return shelves.find((s) => s.id === id)?.label ?? humanise(id);
}

/** When `GET /api/catalog/shelves` is unavailable, the shelves the items themselves name, in first-seen order. */
export function shelvesFromItems(items: readonly CatalogItem[]): Shelf[] {
  const seen = new Map<string, Shelf>();
  for (const item of items) {
    if (!seen.has(item.category)) seen.set(item.category, { id: item.category, label: humanise(item.category), sort_order: seen.size * 10 });
  }
  return [...seen.values()];
}

/** The Shop never lists the raw family: Swaroop lives on Create. */
export function shopItems(items: readonly CatalogItem[]): CatalogItem[] {
  return items.filter((i) => i.family_id !== RAW_FAMILY_ID);
}

export interface MakeItYours {
  href: string;
  label: string;
}

/**
 * "Make it yours · add a photo or your name" → /create/{family}?item={slug}, shown when the item's
 * template takes personal content (a non-empty `features_supported`) and the item knows its family.
 */
export function makeItYours(item: CatalogItem, template: TemplateDescriptor | undefined): MakeItYours | undefined {
  if (!item.family_id || item.family_id === RAW_FAMILY_ID || !template || template.features_supported.length === 0) return undefined;
  const supported = new Set(template.features_supported);
  const what = supported.has("relief_image") ? "add a photo or your name" : supported.has("emboss_text") ? "add your name" : "add a motif";
  return { href: `/create/${encodeURIComponent(item.family_id)}?item=${encodeURIComponent(item.slug)}`, label: `Make it yours · ${what}` };
}

/** Example prompts shown under the Home prompt bar. */
export const EXAMPLE_PROMPTS = [
  "a lotus lamp for the bedside",
  "a fluted planter for the balcony",
  "a jharokha stand for my phone",
] as const;
