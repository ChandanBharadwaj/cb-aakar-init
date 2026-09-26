import type { CatalogCategory } from "./api/types";

export interface CategoryPill {
  id: CatalogCategory | "all";
  label: string;
}

/** Category pills in board order (02 · Shop). */
export const CATEGORIES: readonly CategoryPill[] = [
  { id: "all", label: "All pieces" },
  { id: "home_decor", label: "Home & decor" },
  { id: "nameplates", label: "Nameplates" },
  { id: "kitchen", label: "Kitchen" },
  { id: "desk_tech", label: "Desk & tech" },
  { id: "gifting", label: "Gifting" },
];

export function categoryLabel(id: string): string {
  return CATEGORIES.find((c) => c.id === id)?.label ?? id.replace(/_/g, " ");
}

/** Example prompts shown under the Home prompt bar. */
export const EXAMPLE_PROMPTS = [
  "a lotus lamp for the bedside",
  "a fluted planter for the balcony",
  "a jharokha stand for my phone",
] as const;
