import type { CatalogCategory } from "@/lib/api/types";

export const CATEGORIES: readonly { id: CatalogCategory; label: string }[] = [
  { id: "home_decor", label: "Home & decor" },
  { id: "nameplates", label: "Nameplates" },
  { id: "kitchen", label: "Kitchen" },
  { id: "desk_tech", label: "Desk & tech" },
  { id: "gifting", label: "Gifting" },
];

export function categoryLabel(id: string): string {
  return CATEGORIES.find((c) => c.id === id)?.label ?? id.replace(/_/g, " ");
}

/** Environments known to the storefront viewer (design-tokens `environments`). */
export const ENVIRONMENTS = ["studio", "teak_table_candlelight", "desk_oak", "dashboard", "kitchen_marble", "balcony_daylight"] as const;
