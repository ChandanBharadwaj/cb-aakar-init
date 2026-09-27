import type { Shelf } from "@/lib/api/types";
import { humanize } from "@/lib/format";

/**
 * Shop shelves (catalog categories) come from GET /admin/api/catalog/shelves; nothing is hard-coded here any more.
 * `shelfLabel` looks an id up in the fetched list and falls back to a humanised id while the list loads or for a
 * value the API no longer knows.
 */
export function shelfLabel(shelves: readonly Shelf[] | undefined, id: string | null | undefined): string {
  if (!id) return "—";
  return shelves?.find((s) => s.id === id)?.label ?? humanize(id);
}

/** Shelves in display order (`sort_order`, then label). */
export function sortShelves(shelves: readonly Shelf[]): Shelf[] {
  return [...shelves].sort((a, b) => a.sort_order - b.sort_order || a.label.localeCompare(b.label));
}

/** Environments known to the storefront viewer (design-tokens `environments`). */
export const ENVIRONMENTS = ["studio", "teak_table_candlelight", "desk_oak", "dashboard", "kitchen_marble", "balcony_daylight"] as const;

/** "teak_table_candlelight" → "Teak table candlelight". */
export function environmentLabel(id: string): string {
  return humanize(id);
}
